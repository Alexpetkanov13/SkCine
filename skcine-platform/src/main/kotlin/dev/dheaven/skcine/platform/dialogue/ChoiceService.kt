package dev.dheaven.skcine.platform.dialogue

import dev.dheaven.skcine.api.event.CineChoiceEvent
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.UUID

data class ChoiceOption(val id: String, val label: Component)

/** Result delivered to the script callback. [player] is null for vote results. */
data class ChoiceResult(val choice: Choice, val player: Player?, val option: ChoiceOption?, val timedOut: Boolean)

class Choice internal constructor(
    val id: String,
    val prompt: Component,
    val options: List<ChoiceOption>,
    val audience: Set<UUID>,
    val timeoutTicks: Int,
    val defaultOption: ChoiceOption?,
    val vote: Boolean,
    val owner: SceneInstance?,
    val pausesScene: Boolean,
    /** Show options in an inventory menu instead of chat. */
    val menu: Boolean,
    internal val callback: (ChoiceResult) -> Unit,
) {
    val selections = LinkedHashMap<UUID, ChoiceOption>()
    var age = 0
        internal set
    var resolved = false
        internal set
}

/** Identifies SkCine choice menus; maps menu slots to option indexes. */
class ChoiceMenuHolder internal constructor(val choice: Choice, val slotToOption: Map<Int, Int>) : InventoryHolder {
    internal lateinit var menu: Inventory

    override fun getInventory(): Inventory = menu
}

/**
 * Interactive choices. Players pick with clickable chat entries (Adventure click callbacks, validated server-side)
 * or by typing the option number/label in chat. All resolution happens on the main thread; every selection is
 * checked against the choice's audience and option list, so clients cannot forge choices.
 */
class ChoiceService(private val plugin: Plugin, private val settings: () -> SkCineSettings, private val diagnostics: Diagnostics, private val clickCallbacks: Boolean) {
    private val choices = ArrayList<Choice>()

    /** Thread-safe view of option ids per waiting player, read by the async chat listener. */
    private val awaiting = java.util.concurrent.ConcurrentHashMap<UUID, List<String>>()

    val count get() = choices.size

    fun pending(player: Player): Choice? = choices.firstOrNull {
        !it.resolved &&
            player.uniqueId in it.audience &&
            player.uniqueId !in it.selections
    }

    fun present(
        id: String,
        prompt: Component,
        options: List<ChoiceOption>,
        audience: Collection<Player>,
        timeoutTicks: Int,
        defaultOption: String?,
        vote: Boolean,
        owner: SceneInstance?,
        pauseScene: Boolean,
        callback: (ChoiceResult) -> Unit,
        error: (String) -> Unit,
        menu: Boolean = false,
    ): Choice? {
        if (options.isEmpty()) return null.also { error("a choice needs at least one option") }
        if (options.size > 9) return null.also { error("a choice can have at most 9 options") }
        if (audience.isEmpty()) return null.also { error("a choice needs at least one online player") }
        if (choices.size >=
            settings().maxChoices
        ) {
            return null.also { error("the active choice limit (${settings().maxChoices}) is reached") }
        }
        val def =
            defaultOption?.let { d ->
                options.firstOrNull { it.id.equals(d, ignoreCase = true) }
                    ?: return null.also { error("default option '$d' is not one of the options") }
            }
        val choice =
            Choice(
                id,
                prompt,
                options,
                audience.map { it.uniqueId }.toSet(),
                timeoutTicks,
                def,
                vote,
                owner,
                pauseScene && owner != null,
                menu,
                callback,
            )
        choices += choice
        owner?.own(ChoiceResource(choice))
        if (choice.pausesScene) {
            owner?.let {
                it.pendingChoices++
                // Pause immediately so later events in this same tick (or the timeline end) wait for the answer.
                it.stateMachine.tryTransition(dev.dheaven.skcine.core.scene.SceneState.WAITING_FOR_INPUT)
            }
        }
        audience.forEach {
            awaiting[it.uniqueId] = options.map { o -> o.id }
            if (menu) openMenu(it, choice) else send(it, choice)
        }
        return choice
    }

    /** Opens the inventory menu for one player. Options are centred in a single row (up to 9). */
    fun openMenu(player: Player, choice: Choice) {
        val n = choice.options.size
        val first = (9 - n) / 2
        val slots = (0 until n).associateBy { first + it }
        val holder = ChoiceMenuHolder(choice, slots)
        val inv = Bukkit.createInventory(holder, 9, choice.prompt)
        holder.menu = inv
        for ((slot, index) in slots) {
            val opt = choice.options[index]
            val item = ItemStack(Material.PAPER)
            item.editMeta { meta ->
                meta.displayName(opt.label.decoration(TextDecoration.ITALIC, false))
                meta.lore(listOf(Component.text("Click to choose", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            }
            inv.setItem(slot, item)
        }
        player.openInventory(inv)
    }

    /** A click in a choice menu (always cancelled by the listener). Validated against the live choice. */
    fun handleMenuClick(player: Player, holder: ChoiceMenuHolder, rawSlot: Int) {
        val index = holder.slotToOption[rawSlot] ?: return
        if (holder.choice.resolved || player.uniqueId !in holder.choice.audience || player.uniqueId in holder.choice.selections) return
        select(player, holder.choice, index)
        player.closeInventory()
    }

    /** Menu closed without choosing: fall back to chat so the player can still answer. */
    fun handleMenuClose(player: Player, holder: ChoiceMenuHolder) {
        val c = holder.choice
        if (c.resolved || player.uniqueId !in c.audience || player.uniqueId in c.selections) return
        Bukkit.getScheduler().runTask(plugin, Runnable { if (player.isOnline && !c.resolved && player.uniqueId !in c.selections) send(player, c) })
    }

    private fun closeMenus(choice: Choice) {
        for (uuid in choice.audience) {
            val p = Bukkit.getPlayer(uuid) ?: continue
            val holder = p.openInventory.topInventory.holder
            if (holder is ChoiceMenuHolder && holder.choice === choice) p.closeInventory()
        }
    }

    private fun send(player: Player, choice: Choice) {
        player.sendMessage(choice.prompt)
        choice.options.forEachIndexed { i, opt ->
            var line =
                Component
                    .text()
                    .append(Component.text(" [${i + 1}] ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                    .append(opt.label)
                    .build()
            if (clickCallbacks) {
                val lifetime = Duration.ofMillis(((if (choice.timeoutTicks > 0) choice.timeoutTicks else 20 * 600) + 200L) * 50)
                line =
                    line
                        .clickEvent(
                            ClickEvent.callback(
                                { audience -> (audience as? Player)?.let { p -> runSync { select(p, choice, i) } } },
                                ClickCallback.Options
                                    .builder()
                                    .uses(1)
                                    .lifetime(lifetime)
                                    .build(),
                            ),
                        ).hoverEvent(HoverEvent.showText(Component.text("Click to choose", NamedTextColor.GRAY)))
            }
            player.sendMessage(line)
        }
        player.sendMessage(
            Component.text(
                if (clickCallbacks) "Click an option or type its number." else "Type the number of your choice.",
                NamedTextColor.DARK_GRAY,
            ),
        )
    }

    private fun runSync(r: () -> Unit) {
        if (Bukkit.isPrimaryThread()) r() else Bukkit.getScheduler().runTask(plugin, Runnable { r() })
    }

    /**
     * Handles a typed chat reply. Called from the (async) chat listener; returns true if the message was consumed.
     * The actual selection is applied on the main thread.
     */
    fun handleChatReply(player: Player, message: String): Boolean {
        val options = awaiting[player.uniqueId] ?: return false
        val trimmed = message.trim()
        val index =
            trimmed.toIntOrNull()?.minus(1)?.takeIf { it in options.indices }
                ?: options.indexOfFirst { it.equals(trimmed, ignoreCase = true) }.takeIf { it >= 0 }
                ?: return false
        // Re-validated on the main thread against the live choice.
        runSync { pending(player)?.let { select(player, it, index) } }
        return true
    }

    fun select(player: Player, choice: Choice, index: Int) {
        if (choice.resolved || player.uniqueId !in choice.audience || player.uniqueId in choice.selections) return
        val option = choice.options.getOrNull(index) ?: return
        choice.selections[player.uniqueId] = option
        awaiting.remove(player.uniqueId)
        player.sendMessage(Component.text("You chose: ", NamedTextColor.GRAY).append(option.label))
        if (!choice.vote) deliver(choice, player, option, false)
        if (choice.selections.keys.containsAll(choice.audience.filter { Bukkit.getPlayer(it) != null })) complete(choice, false)
    }

    fun tick() {
        if (choices.isEmpty()) return
        for (c in choices.toList()) {
            if (c.resolved) continue
            c.age++
            // Players who left can no longer answer.
            val online = c.audience.filter { Bukkit.getPlayer(it) != null }
            if (online.isEmpty()) {
                complete(c, true)
                continue
            }
            if (c.timeoutTicks > 0 && c.age >= c.timeoutTicks) {
                if (!c.vote) {
                    for (uuid in online) if (uuid !in c.selections) Bukkit.getPlayer(uuid)?.let { deliver(c, it, c.defaultOption, true) }
                }
                complete(c, true)
            }
        }
        choices.removeAll { it.resolved }
    }

    private fun deliver(choice: Choice, player: Player?, option: ChoiceOption?, timedOut: Boolean) {
        Bukkit.getPluginManager().callEvent(CineChoiceEvent(choice.owner, player, choice.id, option?.id, timedOut))
        try {
            choice.callback(ChoiceResult(choice, player, option, timedOut))
        } catch (t: Throwable) {
            diagnostics.error("Choice '${choice.id}' callback failed", "choice-cb:${choice.id}", t)
        }
    }

    private fun complete(choice: Choice, timedOut: Boolean) {
        if (choice.resolved) return
        choice.resolved = true
        choice.audience.forEach { awaiting.remove(it) }
        if (choice.menu) closeMenus(choice)
        if (choice.vote) {
            val winner =
                choice.selections.values
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<ChoiceOption, Int>> { it.value }.thenBy { choice.options.indexOf(it.key) })
                    .firstOrNull()
                    ?.key ?: choice.defaultOption
            deliver(choice, null, winner, timedOut && choice.selections.isEmpty())
        }
        if (choice.pausesScene) choice.owner?.let { it.pendingChoices = (it.pendingChoices - 1).coerceAtLeast(0) }
        onResolved(choice)
    }

    /** Set by the scene manager to resume scenes paused for input. */
    var onResolved: (Choice) -> Unit = {}

    fun cancelAll() {
        choices.forEach { it.resolved = true }
        choices.clear()
        awaiting.clear()
    }

    private inner class ChoiceResource(val choice: Choice) : SceneResource {
        override fun dispose(reason: EndReason) {
            choice.resolved = true
            choice.audience.forEach { awaiting.remove(it) }
            if (choice.menu) closeMenus(choice)
            choices.remove(choice)
        }
    }
}
