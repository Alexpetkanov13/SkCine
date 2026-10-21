package dev.dheaven.skcine.skript.effects

import ch.njol.skript.Skript
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.util.Color
import ch.njol.skript.util.Timespan
import com.destroystokyo.paper.ParticleBuilder
import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.geometry.ShapeParams
import dev.dheaven.skcine.platform.dialogue.ComponentText
import dev.dheaven.skcine.platform.dialogue.DialogueChannel
import dev.dheaven.skcine.platform.dialogue.DialogueOptions
import dev.dheaven.skcine.platform.dialogue.RevealMode
import dev.dheaven.skcine.platform.effect.AudienceSource
import dev.dheaven.skcine.platform.effect.BuiltInPresets
import dev.dheaven.skcine.platform.effect.RunningEffect
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.skript.CineEffect
import dev.dheaven.skcine.skript.CineExpression
import dev.dheaven.skcine.skript.Ctx
import dev.dheaven.skcine.skript.audienceOf
import dev.dheaven.skcine.skript.effect
import dev.dheaven.skcine.skript.expr
import dev.dheaven.skcine.skript.expression
import dev.dheaven.skcine.skript.instancesOf
import dev.dheaven.skcine.skript.opt
import dev.dheaven.skcine.skript.report
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.ticks
import dev.dheaven.skcine.skript.vec
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.skriptlang.skript.registration.SyntaxRegistry

/** Accepts Skript particle values (which are Paper ParticleBuilders) or particle names such as "end_rod". */
internal fun particleOf(o: Any?): Pair<Particle, Any?>? = when (o) {
    is ParticleBuilder -> o.particle() to o.data()
    is Particle -> o to null
    is String -> {
        val n =
            o
                .trim()
                .lowercase()
                .removePrefix("minecraft:")
                .replace(' ', '_')
        Particle.values().firstOrNull { it.key.key == n || it.name.equals(n, ignoreCase = true) }?.let { it to null }
    }
    else -> null
}

/** Audience for an effect: explicit players/playbacks, the current scene's viewers, or nearby players in the world. */
internal fun effectAudience(event: Event, expr: Expression<*>?): AudienceSource {
    if (expr != null) return AudienceSource.of(audienceOf(event, expr))
    val inst = Ctx.instance(event) ?: return AudienceSource.WORLD
    return AudienceSource { inst.viewers }
}

internal object LastEffect {
    var value: RunningEffect? = null
}

/** Pattern alternation over every shape, using parse marks (mark = ordinal + 1). */
internal val SHAPES_PATTERN: String =
    Shape.entries.joinToString("|", "(", ")") { s ->
        val words = if (s.id.contains('-')) "(${s.id.replace('-', ' ')}|${s.id})" else s.id
        "${s.ordinal + 1}:$words"
    }

private fun shapeFromMark(mark: Int) = Shape.entries.getOrNull(mark - 1)

/** `play cine preset "legendary-reveal" at {_loc} [to player] [with colour gold] [and accent white] [with scale 1.5] [lasting 4 seconds]` */
class EffPreset : CineEffect() {
    private lateinit var preset: Expression<String>
    private lateinit var at: Expression<*>
    private var audience: Expression<*>? = null
    private var color: Expression<Color>? = null
    private var accent: Expression<Color>? = null
    private var scale: Expression<Number>? = null
    private var lasting: Expression<Timespan>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        preset = e.expr(0)
        at = e[1]
        audience = e[2]
        color = e.opt(3)
        accent = e.opt(4)
        scale = e.opt(5)
        lasting = e.opt(6)
        return true
    }

    override fun execute(event: Event) {
        val id = preset.getSingle(event) ?: return
        val anchor = at.getSingle(event) ?: return report(where, "effect location is not set")
        LastEffect.value =
            rt().effects.play(
                id,
                anchor as? Location,
                anchor as? Entity,
                lasting?.getSingle(event)?.ticks()?.toInt(),
                color?.getSingle(event)?.asBukkitColor(),
                accent?.getSingle(event)?.asBukkitColor(),
                scale?.getSingle(event)?.toDouble() ?: 1.0,
                effectAudience(event, audience),
                Ctx.instance(event),
            ) { report(where, it, Ctx.instance(event)) }
    }

    override val description = "play cine preset"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffPreset::class.java,
            ::EffPreset,
            "play [the] cine (preset|vfx) %string% at %location/entity% [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%] [and accent %-color%] [with scale %-number%] [lasting %-timespan%]",
        )
    }
}

/** `draw a cine circle of flame at player [with radius 2] [and height 3] [and 40 points] [rotated by 45 degrees] [to player] [with colour red]` */
class EffDrawShape : CineEffect() {
    private var shape = Shape.CIRCLE
    private lateinit var particle: Expression<*>
    private lateinit var at: Expression<*>
    private var radius: Expression<Number>? = null
    private var height: Expression<Number>? = null
    private var points: Expression<Number>? = null
    private var rotation: Expression<Number>? = null
    private var audience: Expression<*>? = null
    private var color: Expression<Color>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        shape = shapeFromMark(result.mark) ?: return false
        particle = e[0]
        at = e[1]
        radius = e.opt(2)
        height = e.opt(3)
        points = e.opt(4)
        rotation = e.opt(5)
        audience = e[6]
        color = e.opt(7)
        return true
    }

    override fun execute(event: Event) {
        val (p, data) = particleOf(particle.getSingle(event)) ?: return report(where, "unknown particle '${particle.getSingle(event)}'")
        val loc =
            when (val a = at.getSingle(event)) {
                is Entity -> a.location
                is Location -> a
                else -> return
            }
        if (loc.world == null) return report(where, "location has no world")
        val params =
            runCatching {
                ShapeParams(
                    radius = radius?.getSingle(event)?.toDouble() ?: 1.0,
                    height = height?.getSingle(event)?.toDouble() ?: 2.0,
                    points = points?.getSingle(event)?.toInt() ?: 32,
                )
            }.getOrElse { return report(where, "invalid shape: ${it.message}") }
        val c = color?.getSingle(event)?.asBukkitColor() ?: org.bukkit.Color.WHITE
        val resolved = data ?: rt().effects.resolveData(p, c, c)
        rt().effects.drawOnce(
            shape,
            params,
            p,
            resolved,
            loc,
            rotation?.getSingle(event)?.toDouble() ?: 0.0,
            effectAudience(event, audience),
            c,
        )
    }

    override val description = "draw cine shape"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffDrawShape::class.java,
            ::EffDrawShape,
            "draw [a] cine $SHAPES_PATTERN (of|with) %particle/string% at %location/entity% [with radius %-number%] [[and] height %-number%] [[and] %-number% points] " +
                "[rotated [by] %-number% degrees] [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]",
        )
    }
}

/**
 * `start a cine emitter drawing a helix of end rod at player with radius 1 [spinning at 6 degrees per tick] [expanding by 0.05 per tick] [pulsing] [every 2 ticks] lasting 5 seconds`
 */
class EffEmitter : CineEffect() {
    private var shape = Shape.CIRCLE
    private lateinit var particle: Expression<*>
    private lateinit var at: Expression<*>
    private var radius: Expression<Number>? = null
    private var height: Expression<Number>? = null
    private var points: Expression<Number>? = null
    private var spin: Expression<Number>? = null
    private var expand: Expression<Number>? = null
    private var every: Expression<Timespan>? = null
    private lateinit var lasting: Expression<Timespan>
    private var audience: Expression<*>? = null
    private var color: Expression<Color>? = null
    private var pulse = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        shape = shapeFromMark(result.mark) ?: return false
        particle = e[0]
        at = e[1]
        radius = e.opt(2)
        height = e.opt(3)
        points = e.opt(4)
        spin = e.opt(5)
        expand = e.opt(6)
        every = e.opt(7)
        lasting = e.expr(8)
        audience = e[9]
        color = e.opt(10)
        pulse = result.hasTag("pulse")
        return true
    }

    override fun execute(event: Event) {
        val (p, data) = particleOf(particle.getSingle(event)) ?: return report(where, "unknown particle '${particle.getSingle(event)}'")
        val anchor = at.getSingle(event) ?: return
        val params =
            runCatching {
                ShapeParams(
                    radius = radius?.getSingle(event)?.toDouble() ?: 1.0,
                    height = height?.getSingle(event)?.toDouble() ?: 2.0,
                    points = points?.getSingle(event)?.toInt() ?: 32,
                )
            }.getOrElse { return report(where, "invalid shape: ${it.message}") }
        val c = color?.getSingle(event)?.asBukkitColor()
        val preset =
            BuiltInPresets.emitter(
                shape,
                params,
                p,
                data ?: rt().effects.resolveData(p, c, c ?: org.bukkit.Color.WHITE),
                every?.getSingle(event)?.ticks()?.toInt() ?: 1,
                spin?.getSingle(event)?.toDouble() ?: 0.0,
                if (pulse) 0.25 else 0.0,
                expand?.getSingle(event)?.toDouble() ?: 0.0,
            )
        LastEffect.value =
            rt().effects.start(
                "emitter:${shape.id}",
                preset,
                anchor as? Location,
                anchor as? Entity,
                lasting.getSingle(event)?.ticks()?.toInt() ?: 20,
                c,
                null,
                1.0,
                effectAudience(event, audience),
                Ctx.instance(event),
            ) { report(where, it, Ctx.instance(event)) }
    }

    override val description = "start cine emitter"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffEmitter::class.java,
            ::EffEmitter,
            "start [a] cine emitter [drawing] [a] $SHAPES_PATTERN (of|with) %particle/string% at %location/entity% " +
                "[with radius %-number%] [[and] height %-number%] [[and] %-number% points] " +
                "[spinning at %-number% degrees per tick] [expanding [by] %-number% per tick] [pulse:pulsing] [every %-timespan%] " +
                "lasting %timespan% [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]",
        )
    }
}

/** `draw a cine beam of end rod from {_a} to {_b}` / `draw a cine lightning bolt of electric spark from {_a} to {_b}` */
class EffDrawLine : CineEffect() {
    private lateinit var particle: Expression<*>
    private lateinit var from: Expression<Location>
    private lateinit var to: Expression<Location>
    private var spacing: Expression<Number>? = null
    private var audience: Expression<*>? = null
    private var color: Expression<Color>? = null
    private var lightning = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        lightning = result.mark == 2
        particle = e[0]
        from = e.expr(1)
        to = e.expr(2)
        spacing = e.opt(3)
        audience = e[4]
        color = e.opt(5)
        return true
    }

    override fun execute(event: Event) {
        val (p, data) = particleOf(particle.getSingle(event)) ?: return report(where, "unknown particle")
        val a = from.getSingle(event) ?: return
        val b = to.getSingle(event) ?: return
        if (a.world == null || a.world != b.world) return report(where, "line endpoints must be in the same world")
        if (a.distance(b) > 256) return report(where, "lines longer than 256 blocks are not drawn")
        val sp = (spacing?.getSingle(event)?.toDouble() ?: 0.25).coerceIn(0.05, 8.0)
        val c = color?.getSingle(event)?.asBukkitColor() ?: org.bukkit.Color.WHITE
        val pts =
            if (lightning) {
                Shape.lightning(
                    a.vec(),
                    b.vec(),
                    10,
                    a.distance(b) * 0.06,
                    System.nanoTime(),
                )
            } else {
                Shape.line(a.vec(), b.vec(), sp)
            }
        val d = data ?: rt().effects.resolveData(p, c, c)
        val aud = effectAudience(event, audience)
        val l = a.clone()
        for (v in pts) {
            l.set(v.x, v.y, v.z)
            rt().effects.drawOnce(Shape.POINT, ShapeParams(points = 1), p, d, l, 0.0, aud, c)
        }
    }

    override val description = "draw cine line"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffDrawLine::class.java,
            ::EffDrawLine,
            "draw [a] cine (1:line|1:beam|2:lightning [bolt]) of %particle/string% from %location% to %location% [with spacing %-number%] [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]",
        )
    }
}

/** `stop cine effect {_fx}` / `stop all cine effects [of {_playback}]` */
class EffStopEffect : CineEffect() {
    private var effects: Expression<RunningEffect>? = null
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        if (pattern == 0) effects = e.expr(0) else inst = e.opt(0)
        return true
    }

    override fun execute(event: Event) {
        val fx = rt().effects
        if (pattern == 0) {
            effects?.getArray(event)?.forEach { fx.stop(it) }
            return
        }
        val owner = inst?.getSingle(event)
        if (owner == null && inst != null) return
        // Without a scene, stops every effect on the server.
        rt().effects.stopWhere { owner == null || it.owner === owner }
    }

    override val description = "stop cine effect"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffStopEffect::class.java,
            ::EffStopEffect,
            "stop [the] cine effect[s] %cineeffects%",
            "stop all cine effects [of %-cineplayback%]",
        )
    }
}

class ExprLastEffect : CineExpression<RunningEffect>(RunningEffect::class.java) {
    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult) = true

    override fun values(event: Event) = listOfNotNull(LastEffect.value)

    override val description = "last cine effect"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(ExprLastEffect::class.java, RunningEffect::class.java, ::ExprLastEffect, "[the] last [started] cine effect")
    }
}

/** `play cine sound "entity.wither.spawn" [at {_loc}] [with volume 2] [and pitch 0.8] [in category "music"] [to player]` */
class EffCineSound : CineEffect() {
    private lateinit var sound: Expression<String>
    private var at: Expression<*>? = null
    private var volume: Expression<Number>? = null
    private var pitch: Expression<Number>? = null
    private var category: Expression<String>? = null
    private var audience: Expression<*>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        sound = e.expr(0)
        at = e[1]
        volume = e.opt(2)
        pitch = e.opt(3)
        category = e.opt(4)
        audience = e[5]
        if (audience == null && at == null && !Ctx.inSceneContext()) {
            Skript.error("Say who hears the sound ('to player') or where it plays ('at {_loc}') outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val s = rt().sounds
        val key = s.parseKey(sound.getSingle(event) ?: return) { report(where, it) } ?: return
        val loc =
            when (val a = at?.getSingle(event)) {
                is Entity -> a.location
                is Location -> a
                else -> null
            }
        val listeners: List<Player> =
            when {
                audience != null || Ctx.instance(event) != null -> audienceOf(event, audience)
                loc != null -> loc.world?.players?.filter { it.location.distanceSquared(loc) < 64.0 * 64.0 } ?: emptyList()
                else -> emptyList()
            }
        val v = (volume?.getSingle(event)?.toDouble() ?: 1.0).toFloat()
        val p = (pitch?.getSingle(event)?.toDouble() ?: 1.0).toFloat()
        s.play(key, s.parseSource(category?.getSingle(event)), v, p, loc, listeners, Ctx.instance(event))
    }

    override val description = "play cine sound"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffCineSound::class.java,
            ::EffCineSound,
            "play [the] cine sound %string% [at %-location/entity%] [with volume %-number%] [[and|with] pitch %-number%] [in [the] category %-string%] [(to|for) %-players/cineplaybacks%]",
        )
    }
}

/** `stop cine sounds of player` / `stop cine sound "x" for player` / `stop cine sounds` (current scene) */
class EffStopSound : CineEffect() {
    private var sound: Expression<String>? = null
    private var targets: Expression<*>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        sound = e.opt(0)
        targets = e[1]
        if (targets == null && !Ctx.inSceneContext()) {
            Skript.error("Say whose sounds to stop outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val s = rt().sounds
        val name = sound?.getSingle(event)
        if (name == null && targets == null) {
            instancesOf(event, null).forEach { s.stopSceneSounds(it) }
            return
        }
        val key = name?.let { s.parseKey(it) { msg -> report(where, msg) } ?: return }
        s.stop(key, audienceOf(event, targets))
    }

    override val description = "stop cine sounds"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(EffStopSound::class.java, ::EffStopSound, "stop [the] cine sound[s] [%-string%] [(of|for) %-players/cineplaybacks%]")
    }
}

/**
 * ```
 * show cine dialogue "Welcome, traveller." from "<gold>Guide" [to player] [in action bar] [with typewriter [at 30 characters per second]]
 *     [for 3 seconds | until advanced] [with typing sound "block.note_block.hat"]
 * ```
 * Channels: chat, title, subtitle (default), action bar, boss bar, text display (with `on {_textActor}`).
 */
class EffDialogue : CineEffect() {
    private lateinit var text: Expression<String>
    private var speaker: Expression<String>? = null
    private var audience: Expression<*>? = null
    private var channelMark = 0
    private var cps: Expression<Number>? = null
    private var wps: Expression<Number>? = null
    private var hold: Expression<Timespan>? = null
    private var fade: Expression<Timespan>? = null
    private var sound: Expression<String>? = null
    private var display: Expression<*>? = null
    private var typewriter = false
    private var words = false
    private var fadeIn = false
    private var untilAdvanced = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        text = e.expr(0)
        speaker = e.opt(1)
        audience = e[2]
        channelMark = result.mark
        display = e[3]
        cps = e.opt(4)
        wps = e.opt(5)
        hold = e.opt(6)
        fade = e.opt(7)
        sound = e.opt(8)
        typewriter = result.hasTag("typewriter")
        words = result.hasTag("words")
        fadeIn = result.hasTag("fade")
        untilAdvanced = result.hasTag("until")
        if (typewriter && words) {
            Skript.error("Choose either a typewriter (letters) or word by word, not both")
            return false
        }
        if (audience == null && !Ctx.inSceneContext()) {
            Skript.error("Say who sees the dialogue ('to player') outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val t = text.getSingle(event) ?: return
        val ch =
            DialogueChannel.entries.getOrNull(channelMark - 1)
                ?: if (display != null) DialogueChannel.TEXT_DISPLAY else DialogueChannel.SUBTITLE
        val speed =
            when {
                typewriter -> (cps?.getSingle(event)?.toDouble() ?: 30.0).coerceIn(1.0, 400.0)
                words -> (wps?.getSingle(event)?.toDouble() ?: 4.0).coerceIn(0.5, 100.0)
                else -> 0.0
            }
        val fadeTicks = if (fadeIn) (fade?.getSingle(event)?.ticks()?.toInt() ?: 10).coerceIn(0, 200) else 0
        val holdTicks = if (untilAdvanced) -1 else (hold?.getSingle(event)?.ticks()?.toInt() ?: 60)
        val typingKey = sound?.getSingle(event)?.let { rt().sounds.parseKey(it) { msg -> report(where, msg) } }
        val textDisplay = display?.let { entitiesOf(event, it).firstOrNull()?.uniqueId }
        if (ch == DialogueChannel.TEXT_DISPLAY &&
            textDisplay == null
        ) {
            return report(where, "the text display channel needs a text display actor ('on {_actor}')")
        }
        rt().dialogue.show(
            speaker?.getSingle(event)?.let { ComponentText.parse(it) },
            ComponentText.parse(t),
            DialogueOptions(
                ch,
                speed,
                if (words) RevealMode.WORD else RevealMode.LETTER,
                fadeTicks,
                holdTicks,
                typingKey,
                textDisplay = textDisplay,
            ),
            audienceOf(event, audience),
            Ctx.instance(event),
        )
    }

    override val description = "show cine dialogue"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffDialogue::class.java,
            ::EffDialogue,
            "show [a] cine (dialogue|line) %string% [(from|by|spoken by) %-string%] [to %-players/cineplaybacks%] " +
                "[(in|via|as) [the] (1:chat|2:title|3:subtitle|4:action[ ]bar|5:boss[ ]bar|6:text display)] [on %-cineactor/entity%] " +
                "[typewriter:with [a] typewriter [effect] [at %-number% (characters|chars|letters) per second]] " +
                "[words:word by word [at %-number% words per second]] [for %-timespan%] [until:until advanced] " +
                "[fade:fading in [over %-timespan%]] [with typing sound %-string%]",
        )
    }
}

/** `advance cine dialogue of player` / `clear cine dialogue of player` */
class EffDialogueControl : CineEffect() {
    private lateinit var players: Expression<Player>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        players = e.expr(0)
        return true
    }

    override fun execute(event: Event) {
        players.getArray(event).forEach { if (pattern == 0) rt().dialogue.advance(it) else rt().dialogue.cancel(it) }
    }

    override val description = "control cine dialogue"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffDialogueControl::class.java,
            ::EffDialogueControl,
            "advance [the] cine dialogue (of|for) %players%",
            "(clear|cancel) [the] cine dialogue (of|for) %players%",
        )
    }
}
