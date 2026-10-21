package dev.dheaven.skcine.platform.dialogue

import dev.dheaven.skcine.api.event.CineDialogueEvent
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.profiler.Profiler
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import java.time.Duration
import java.util.UUID

/** Where dialogue text is shown. These are output mechanisms only — scenes are still authored in Skript. */
enum class DialogueChannel(val id: String) {
    CHAT("chat"),
    TITLE("title"),
    SUBTITLE("subtitle"),
    ACTION_BAR("action bar"),
    BOSS_BAR("boss bar"),
    TEXT_DISPLAY("text display"),
    ;

    companion object {
        fun parse(s: String): DialogueChannel? {
            val n =
                s
                    .trim()
                    .lowercase()
                    .replace(
                        '_',
                        ' ',
                    ).replace('-', ' ')
                    .replace("actionbar", "action bar")
                    .replace("bossbar", "boss bar")
            return entries.firstOrNull { it.id == n }
        }
    }
}

/** How a line is revealed over time. */
enum class RevealMode {
    /** Letter by letter (typewriter). */
    LETTER,

    /** Whole words at a time. */
    WORD,
}

/** Options for one dialogue line. */
data class DialogueOptions(
    val channel: DialogueChannel = DialogueChannel.SUBTITLE,
    /** Reveal speed in units (letters or words, see [revealMode]) per second; 0 shows the whole line at once. */
    val charsPerSecond: Double = 0.0,
    val revealMode: RevealMode = RevealMode.LETTER,
    /** Title/subtitle channels: client-side fade-in when the line first appears, in ticks. */
    val fadeInTicks: Int = 0,
    /** Ticks to keep the full line visible; -1 = until advanced. */
    val holdTicks: Int = 60,
    val typingSound: Key? = null,
    val bossBarColor: BossBar.Color = BossBar.Color.WHITE,
    val textDisplay: UUID? = null,
)

class DialogueLine internal constructor(val speaker: Component?, val text: Component, val options: DialogueOptions, val audience: List<UUID>, val owner: SceneInstance?) {
    val length = ComponentText.visibleLength(text)
    var age = 0
        internal set
    var revealed = if (options.charsPerSecond <= 0) length else 0
        internal set
    var holdAge = 0
        internal set
    var finished = false
        internal set
    var lastShown = -1
        internal set
    internal var bossBar: BossBar? = null
    internal var shownOnce = false

    /** Visible-character offsets at which each word ends (used by word-by-word reveal). */
    internal val wordEnds: IntArray =
        run {
            val plain = ComponentText.plain(text)
            // Fall back to letter reveal if the plain text does not line up with visible characters.
            if (plain.length != length) return@run IntArray(0)
            val ends = ArrayList<Int>()
            var i = 0
            while (i < plain.length) {
                while (i < plain.length && plain[i].isWhitespace()) i++
                if (i >= plain.length) break
                while (i < plain.length && !plain[i].isWhitespace()) i++
                ends += i
            }
            ends.toIntArray()
        }

    /** Characters to show after [units] letters or words have been revealed. */
    internal fun charsFor(units: Int): Int {
        if (options.revealMode == RevealMode.WORD && wordEnds.isNotEmpty()) {
            return if (units <= 0) {
                0
            } else if (units >= wordEnds.size) {
                length
            } else {
                wordEnds[units - 1]
            }
        }
        return units.coerceAtMost(length)
    }

    val typing get() = revealed < length
}

class DialogueService(private val settings: () -> SkCineSettings, private val diagnostics: Diagnostics, private val profiler: Profiler) {
    private val lines = ArrayList<DialogueLine>()
    private val byPlayerChannel = HashMap<Pair<UUID, DialogueChannel>, DialogueLine>()

    val count get() = lines.size

    fun current(player: Player, channel: DialogueChannel? = null): DialogueLine? = if (channel != null) {
        byPlayerChannel[player.uniqueId to channel]
    } else {
        byPlayerChannel.entries.firstOrNull { it.key.first == player.uniqueId }?.value
    }

    /** Starts a line. Any line already on the same channel for the same player is replaced. */
    fun show(speaker: Component?, text: Component, options: DialogueOptions, audience: Collection<Player>, owner: SceneInstance?): DialogueLine? {
        if (lines.size >= settings().maxDialogues) {
            diagnostics.warn("Dialogue limit (${settings().maxDialogues}) reached; line skipped", "dialogue-limit")
            return null
        }
        val line = DialogueLine(speaker, text, options, audience.map { it.uniqueId }, owner)
        for (p in audience) {
            byPlayerChannel.put(p.uniqueId to options.channel, line)?.let { old -> if (old !== line) detach(old, p.uniqueId) }
        }
        lines += line
        owner?.own(LineResource(line))
        audience.forEach {
            Bukkit.getPluginManager().callEvent(
                CineDialogueEvent(
                    owner,
                    it,
                    ComponentText.plain(speaker ?: Component.empty()).ifEmpty {
                        null
                    },
                    ComponentText.plain(text),
                    false,
                ),
            )
        }
        if (options.channel == DialogueChannel.CHAT) {
            val msg = compose(line, line.length, ": ")
            audience.forEach { it.sendMessage(msg) }
        }
        render(line, force = true)
        return line
    }

    /** Completes the typewriter, or ends the hold if already complete. */
    fun advance(player: Player): Boolean {
        val line = byPlayerChannel.entries.firstOrNull { it.key.first == player.uniqueId }?.value ?: return false
        if (line.typing) {
            line.revealed = line.length
            render(line, force = true)
        } else {
            finish(line, cleared = true)
        }
        return true
    }

    fun cancel(player: Player) {
        byPlayerChannel.entries.filter { it.key.first == player.uniqueId }.map { it.value }.distinct().forEach {
            finish(
                it,
                cleared = true,
            )
        }
    }

    fun tick() {
        if (lines.isEmpty()) return
        for (line in lines.toList()) {
            if (line.finished) continue
            line.age++
            if (line.typing) {
                val before = line.revealed
                line.revealed = line.charsFor(((line.age * line.options.charsPerSecond) / 20.0).toInt())
                if (line.revealed != before) typingSound(line, before)
                render(line, force = false)
                continue
            }
            line.holdAge++
            if (line.options.holdTicks >= 0 && line.holdAge >= line.options.holdTicks) {
                finish(line, cleared = true)
                continue
            }
            // Keep transient channels visible while holding.
            if (line.options.channel == DialogueChannel.ACTION_BAR && line.holdAge % 30 == 0) render(line, force = true)
            if (line.options.channel == DialogueChannel.BOSS_BAR) updateBossProgress(line)
        }
        lines.removeAll { it.finished }
    }

    private fun typingSound(line: DialogueLine, before: Int) {
        val key = line.options.typingSound ?: return
        if (line.age % settings().typewriterSoundInterval.coerceAtLeast(1) != 0) return
        val plain = ComponentText.plain(line.text)
        val newChars = plain.substring(before.coerceAtMost(plain.length), line.revealed.coerceAtMost(plain.length))
        if (newChars.isBlank()) return
        val sound = Sound.sound(key, Sound.Source.MASTER, 0.6f, 1.0f + (line.revealed % 5) * 0.04f)
        players(line).forEach { it.playSound(sound, Sound.Emitter.self()) }
    }

    private fun interval(channel: DialogueChannel) = when (channel) {
        DialogueChannel.TITLE, DialogueChannel.SUBTITLE -> settings().titleUpdateInterval
        DialogueChannel.ACTION_BAR -> settings().actionBarUpdateInterval
        DialogueChannel.BOSS_BAR -> settings().bossBarUpdateInterval
        else -> 1
    }.coerceAtLeast(1)

    /** Sends the current reveal state; rate-limited per channel so typing never floods packets. */
    private fun render(line: DialogueLine, force: Boolean) {
        val ch = line.options.channel
        if (ch == DialogueChannel.CHAT) return
        if (!force && line.revealed == line.lastShown) return
        if (!force && line.revealed < line.length && line.age % interval(ch) != 0) return
        line.lastShown = line.revealed
        val players = players(line).filter { byPlayerChannel[it.uniqueId to ch] === line }
        profiler.textUpdates.add(players.size.toLong())
        when (ch) {
            DialogueChannel.TITLE, DialogueChannel.SUBTITLE -> {
                val remainingTicks = ((line.length - line.revealed) * 20.0 / line.options.charsPerSecond.coerceAtLeast(1.0)).toLong()
                val stay = remainingTicks + (if (line.options.holdTicks < 0) 20L * 60 * 10 else line.options.holdTicks.toLong()) + 5
                // Fade in only on the first send; later typewriter updates must not re-fade the text.
                val fadeIn = if (!line.shownOnce) Duration.ofMillis(line.options.fadeInTicks.coerceIn(0, 200) * 50L) else Duration.ZERO
                line.shownOnce = true
                val times = Title.Times.times(fadeIn, Duration.ofMillis(stay * 50), Duration.ofMillis(500))
                val shown = ComponentText.slice(line.text, line.revealed)
                val title =
                    if (ch == DialogueChannel.TITLE) {
                        Title.title(shown, line.speaker ?: Component.empty(), times)
                    } else {
                        Title.title(Component.empty(), compose(line, line.revealed, ": "), times)
                    }
                players.forEach { it.showTitle(title) }
            }
            DialogueChannel.ACTION_BAR -> {
                val msg = compose(line, line.revealed, ": ")
                players.forEach { it.sendActionBar(msg) }
            }
            DialogueChannel.BOSS_BAR -> {
                val bar =
                    line.bossBar
                        ?: BossBar.bossBar(Component.empty(), 1f, line.options.bossBarColor, BossBar.Overlay.PROGRESS).also { b ->
                            line.bossBar =
                                b
                        }
                bar.name(compose(line, line.revealed, ": "))
                players.forEach { it.showBossBar(bar) }
            }
            DialogueChannel.TEXT_DISPLAY -> {
                val display = line.options.textDisplay?.let { Bukkit.getEntity(it) } as? TextDisplay
                display?.text(compose(line, line.revealed, "\n"))
            }
            DialogueChannel.CHAT -> Unit
        }
    }

    private fun updateBossProgress(line: DialogueLine) {
        val bar = line.bossBar ?: return
        val hold = line.options.holdTicks
        if (hold > 0) bar.progress((1f - line.holdAge.toFloat() / hold).coerceIn(0f, 1f))
    }

    private fun compose(line: DialogueLine, count: Int, separator: String): Component {
        val body = ComponentText.slice(line.text, count)
        val speaker = line.speaker ?: return body
        val styled = if (speaker.color() == null) speaker.color(NamedTextColor.YELLOW) else speaker
        return Component
            .text()
            .append(styled)
            .append(Component.text(separator, NamedTextColor.GRAY))
            .append(body)
            .build()
    }

    private fun players(line: DialogueLine) = line.audience.mapNotNull { Bukkit.getPlayer(it) }

    /** Removes one player from a line that was replaced for them. */
    private fun detach(line: DialogueLine, uuid: UUID) {
        Bukkit.getPlayer(uuid)?.let { p ->
            line.bossBar?.let { p.hideBossBar(it) }
            fireFinish(line, p)
        }
    }

    fun finish(line: DialogueLine, cleared: Boolean) {
        if (line.finished) return
        line.finished = true
        for (p in players(line)) {
            val key = p.uniqueId to line.options.channel
            if (byPlayerChannel[key] !== line) continue
            byPlayerChannel.remove(key)
            if (cleared) {
                when (line.options.channel) {
                    DialogueChannel.ACTION_BAR -> p.sendActionBar(Component.empty())
                    DialogueChannel.BOSS_BAR -> line.bossBar?.let { p.hideBossBar(it) }
                    DialogueChannel.TITLE, DialogueChannel.SUBTITLE -> p.clearTitle()
                    else -> Unit
                }
            }
            fireFinish(line, p)
        }
    }

    private fun fireFinish(line: DialogueLine, p: Player) {
        Bukkit.getPluginManager().callEvent(
            CineDialogueEvent(line.owner, p, line.speaker?.let { ComponentText.plain(it) }, ComponentText.plain(line.text), true),
        )
    }

    fun finishAll() {
        lines.toList().forEach { finish(it, cleared = true) }
        lines.clear()
        byPlayerChannel.clear()
    }

    private inner class LineResource(val line: DialogueLine) : SceneResource {
        override fun dispose(reason: EndReason) = finish(line, cleared = true)
    }
}
