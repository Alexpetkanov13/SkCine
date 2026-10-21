package dev.dheaven.skcine.skript.expressions

import ch.njol.skript.Skript
import ch.njol.skript.classes.Changer.ChangeMode
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.util.Timespan
import dev.dheaven.skcine.api.capability.Capability
import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.platform.actor.CineActor
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.skript.CineChoiceSectionEvent
import dev.dheaven.skcine.skript.CineExpression
import dev.dheaven.skcine.skript.Ctx
import dev.dheaven.skcine.skript.expr
import dev.dheaven.skcine.skript.expression
import dev.dheaven.skcine.skript.opt
import dev.dheaven.skcine.skript.report
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.structures.SequenceRegistry
import dev.dheaven.skcine.skript.ticks
import dev.dheaven.skcine.skript.ticksToTimespan
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.skriptlang.skript.registration.SyntaxRegistry

/** `cine scene "welcome"` — a registered definition. */
class ExprSceneById : CineExpression<SceneDefinition>(SceneDefinition::class.java) {
    private lateinit var id: Expression<String>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        id = e.expr(0)
        return true
    }

    override fun values(event: Event) = listOfNotNull(id.getSingle(event)?.let { rt().registry.get(it) })

    override val description = "cine scene by id"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprSceneById::class.java,
            SceneDefinition::class.java,
            ::ExprSceneById,
            "[the] [registered] cine scene (named|with id|called) %string%",
            "[the] cine scene %string%",
        )
    }
}

/** `cine playback` (current scene in a timeline/callback) / `cine playback of player` / `last started cine playback` / `all active cine playbacks` */
class ExprPlayback : CineExpression<SceneInstance>(SceneInstance::class.java) {
    private var player: Expression<Player>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        if (pattern == 0) {
            player = e.opt(0)
            if (player == null && !Ctx.inSceneContext()) {
                Skript.error("'cine playback' without 'of <player>' only works inside a cine scene")
                return false
            }
        }
        return true
    }

    override fun values(event: Event): List<SceneInstance> = when (pattern) {
        0 -> listOfNotNull(player?.getSingle(event)?.let { rt().manager.current(it) } ?: Ctx.instance(event).takeIf { player == null })
        1 -> listOfNotNull(rt().manager.lastStarted)
        else -> rt().manager.active().toList()
    }

    override fun isSingle() = pattern != 2

    override val description = "cine playback"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprPlayback::class.java,
            SceneInstance::class.java,
            ::ExprPlayback,
            "[the] [current] cine playback [of %-player%]",
            "[the] last (started|played) cine playback",
            "[all [of]] [the] (active|running|playing) cine playbacks",
        )
    }
}

/** `cine viewers [of {_playback}]` */
class ExprViewers : CineExpression<Player>(Player::class.java, single = false) {
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        inst = e.opt(0)
        if (inst == null && !Ctx.inSceneContext()) {
            Skript.error("'cine viewers' needs 'of {_playback}' outside a cine scene")
            return false
        }
        return true
    }

    override fun values(event: Event) = (inst?.getSingle(event) ?: Ctx.instance(event))?.viewers ?: emptyList()

    override val description = "cine viewers"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(ExprViewers::class.java, Player::class.java, ::ExprViewers, "[the] cine viewers [of %-cineplayback%]")
    }
}

/** Time properties of a playback: duration, elapsed time (settable → seek), remaining time, marker time. */
class ExprPlaybackTime : CineExpression<Timespan>(Timespan::class.java) {
    private var inst: Expression<SceneInstance>? = null
    private var marker: Expression<String>? = null
    private var prop = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        if (pattern == 0) {
            prop = result.mark
            inst = e.opt(0)
        } else {
            prop = 4
            marker = e.expr(0)
            inst = e.opt(1)
        }
        if (inst == null && !Ctx.inSceneContext()) {
            Skript.error("Name the cine playback ('of {_playback}') outside a cine scene")
            return false
        }
        return true
    }

    private fun target(event: Event) = inst?.getSingle(event) ?: Ctx.instance(event)

    override fun values(event: Event): List<Timespan> {
        val i = target(event) ?: return emptyList()
        val t =
            when (prop) {
                1 -> i.duration
                2 -> i.position
                3 -> (i.duration - i.position).coerceAtLeast(0.0)
                else -> marker?.getSingle(event)?.let { i.timeline.marker(it)?.time } ?: return emptyList()
            }
        return listOf(ticksToTimespan(t))
    }

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? = when {
        prop == 2 && mode == ChangeMode.SET -> arrayOf(Timespan::class.java)
        prop == 1 && (mode == ChangeMode.SET || mode == ChangeMode.RESET) -> arrayOf(Timespan::class.java)
        else -> null
    }

    override fun change(event: Event, delta: Array<out Any?>?, mode: ChangeMode) {
        val i = target(event) ?: return
        val ts = delta?.firstOrNull() as? Timespan
        when (prop) {
            2 -> ts?.let { rt().manager.seek(i, it.ticks()) }
            1 ->
                runCatching { i.timeline.explicitDuration = if (mode == ChangeMode.RESET) null else ts?.ticks() }
                    .onFailure { report(where, it.message ?: "invalid duration", i) }
        }
    }

    override val description = "cine playback time"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprPlaybackTime::class.java,
            Timespan::class.java,
            ::ExprPlaybackTime,
            "[the] cine (1:duration|2:(elapsed time|position)|3:remaining time) [of %-cineplayback%]",
            "[the] [cine] marker time of %string% [(in|of) %-cineplayback%]",
        )
    }
}

/** Numeric properties: progress (0..1), speed (settable), loop iteration, skip votes. */
class ExprPlaybackNumber : CineExpression<Number>(Number::class.java) {
    private var inst: Expression<SceneInstance>? = null
    private var prop = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        prop = result.mark
        inst = e.opt(0)
        if (inst == null && !Ctx.inSceneContext()) {
            Skript.error("Name the cine playback ('of {_playback}') outside a cine scene")
            return false
        }
        return true
    }

    private fun target(event: Event) = inst?.getSingle(event) ?: Ctx.instance(event)

    override fun values(event: Event): List<Number> {
        val i = target(event) ?: return emptyList()
        return listOf(
            when (prop) {
                1 -> i.player.progress
                2 -> i.speed
                3 -> i.player.iteration.toLong()
                else -> i.skipVotes.size.toLong()
            },
        )
    }

    override fun getReturnType(): Class<out Number> = if (prop == 1 ||
        prop == 2
    ) {
        Double::class.javaObjectType
    } else {
        Long::class.javaObjectType
    }

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? = if (prop == 2 &&
        (mode == ChangeMode.SET || mode == ChangeMode.RESET)
    ) {
        arrayOf(Number::class.java)
    } else {
        null
    }

    override fun change(event: Event, delta: Array<out Any?>?, mode: ChangeMode) {
        val i = target(event) ?: return
        val v = if (mode == ChangeMode.RESET) 1.0 else (delta?.firstOrNull() as? Number)?.toDouble() ?: return
        if (!v.isFinite()) return report(where, "speed must be a finite number", i)
        i.speed = v
    }

    override val description = "cine playback number"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprPlaybackNumber::class.java,
            Number::class.java,
            ::ExprPlaybackNumber,
            "[the] cine (1:progress|2:speed|3:loop (count|iteration)|4:skip votes) [of %-cineplayback%]",
        )
    }
}

/** Text properties: state, scene id, end reason. */
class ExprPlaybackText : CineExpression<String>(String::class.java) {
    private lateinit var inst: Expression<*>
    private var prop = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        prop = result.mark
        inst = e[0]
        return true
    }

    override fun values(event: Event): List<String> {
        val v = inst.getSingle(event) ?: return emptyList()
        return listOfNotNull(
            when (v) {
                is SceneInstance ->
                    when (prop) {
                        1 -> v.state.id
                        2 -> v.sceneId
                        else -> v.endReason?.id
                    }
                is SceneDefinition -> if (prop == 2) v.id else null
                else -> null
            },
        )
    }

    override val description = "cine playback text"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprPlaybackText::class.java,
            String::class.java,
            ::ExprPlaybackText,
            "[the] cine (1:state|2:[scene] id|3:end reason) of %cineplayback/cinescene%",
        )
    }
}

/** `cine variable "boss" [of {_playback}]` — per-playback storage (set/delete). */
class ExprSceneVariable : CineExpression<Any>(Any::class.java) {
    private lateinit var name: Expression<String>
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        name = e.expr(0)
        inst = e.opt(1)
        if (inst == null && !Ctx.inSceneContext()) {
            Skript.error("Name the cine playback ('of {_playback}') outside a cine scene")
            return false
        }
        return true
    }

    private fun target(event: Event) = inst?.getSingle(event) ?: Ctx.instance(event)

    override fun values(event: Event): List<Any> = listOfNotNull(name.getSingle(event)?.let { target(event)?.variables?.get(it.lowercase()) })

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? = when (mode) {
        ChangeMode.SET -> arrayOf(Any::class.java)
        ChangeMode.DELETE, ChangeMode.RESET -> arrayOf()
        else -> null
    }

    override fun change(event: Event, delta: Array<out Any?>?, mode: ChangeMode) {
        val i = target(event) ?: return
        val n = name.getSingle(event)?.lowercase() ?: return
        if (mode == ChangeMode.SET) {
            val v = delta?.firstOrNull() ?: return
            if (i.variables.size >= 1024 && !i.variables.containsKey(n)) return report(where, "too many cine variables (max 1024)", i)
            i.variables[n] = v
        } else {
            i.variables.remove(n)
        }
    }

    override val description = "cine variable"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprSceneVariable::class.java,
            Any::class.java,
            ::ExprSceneVariable,
            "[the] cine variable %string% [of %-cineplayback%]",
        )
    }
}

/**
 * `cine ease 0.5 using "ease-in-out-cubic"` → eased progress.
 * `cine lerp from 0 to 10 at 0.25 [using "ease-out-quad"]` → interpolated value.
 */
class ExprEase : CineExpression<Number>(Number::class.java) {
    private lateinit var a: Expression<Number>
    private var b: Expression<Number>? = null
    private var t: Expression<Number>? = null
    private var easing: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        if (pattern == 0) {
            t = e.expr(0)
            easing = e.expr(1)
        } else {
            a = e.expr(0)
            b = e.expr(1)
            t = e.expr(2)
            easing = e.opt(3)
        }
        return true
    }

    override fun values(event: Event): List<Number> {
        val name = easing?.getSingle(event)
        val ease =
            if (name ==
                null
            ) {
                Easing.LINEAR
            } else {
                Easing.byName(name) ?: return emptyList<Number>().also { report(where, "unknown easing '$name'") }
            }
        val x = t?.getSingle(event)?.toDouble()?.takeIf { it.isFinite() } ?: return emptyList()
        val u = ease.apply(x)
        if (pattern == 0) return listOf(u)
        val from = a.getSingle(event)?.toDouble() ?: return emptyList()
        val to = b?.getSingle(event)?.toDouble() ?: return emptyList()
        return listOf(from + (to - from) * u)
    }

    override fun getReturnType(): Class<out Number> = Double::class.javaObjectType

    override val description = "cine ease"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprEase::class.java,
            Number::class.java,
            ::ExprEase,
            "[the] cine ease[d value of] %number% (using|with) %string%",
            "[the] cine lerp from %number% to %number% at %number% [(using|with) %-string%]",
        )
    }
}

/** Actor properties: entity, name, and the actors of a playback. */
class ExprActorEntity : CineExpression<Entity>(Entity::class.java) {
    private lateinit var actor: Expression<CineActor>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actor = e.expr(0)
        return true
    }

    override fun values(event: Event) = listOfNotNull(actor.getSingle(event)?.entity())

    override val description = "cine actor entity"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprActorEntity::class.java,
            Entity::class.java,
            ::ExprActorEntity,
            "[the] [cine] actor entity of %cineactor%",
            "[the] entity of [the] cine actor %cineactor%",
        )
    }
}

class ExprActorName : CineExpression<String>(String::class.java) {
    private lateinit var actor: Expression<CineActor>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actor = e.expr(0)
        return true
    }

    override fun values(event: Event) = listOfNotNull(actor.getSingle(event)?.name)

    override val description = "cine actor name"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(ExprActorName::class.java, String::class.java, ::ExprActorName, "[the] [cine] actor name of %cineactor%")
    }
}

class ExprActors : CineExpression<CineActor>(CineActor::class.java, single = false) {
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        inst = e.opt(0)
        return true
    }

    override fun values(event: Event): List<CineActor> {
        val i = inst?.getSingle(event) ?: Ctx.instance(event)
        return if (i != null) rt().actors.of(i) else rt().actors.all().toList()
    }

    override val description = "cine actors"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(ExprActors::class.java, CineActor::class.java, ::ExprActors, "[all [of]] [the] cine actors [of %-cineplayback%]")
    }
}

/** `cine camera location of player` — the camera pose while a scene controls their camera. */
class ExprCameraLocation : CineExpression<Location>(Location::class.java) {
    private lateinit var player: Expression<Player>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        player = e.expr(0)
        return true
    }

    override fun values(event: Event) = listOfNotNull(player.getSingle(event)?.let { rt().camera.poseOf(it) })

    override val description = "cine camera location"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprCameraLocation::class.java,
            Location::class.java,
            ::ExprCameraLocation,
            "[the] cine camera location of %player%",
        )
    }
}

/** `cine end location of {_playback}` — where viewers are sent when the scene ends (settable). */
class ExprEndLocation : CineExpression<Location>(Location::class.java) {
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        inst = e.opt(0)
        if (inst == null && !Ctx.inSceneContext()) {
            Skript.error("Name the cine playback outside a cine scene")
            return false
        }
        return true
    }

    private fun target(event: Event) = inst?.getSingle(event) ?: Ctx.instance(event)

    override fun values(event: Event) = listOfNotNull(target(event)?.endLocation)

    override fun acceptChange(mode: ChangeMode): Array<Class<*>>? =
        if (mode == ChangeMode.SET || mode == ChangeMode.DELETE || mode == ChangeMode.RESET) arrayOf(Location::class.java) else null

    override fun change(event: Event, delta: Array<out Any?>?, mode: ChangeMode) {
        target(event)?.endLocation = if (mode == ChangeMode.SET) (delta?.firstOrNull() as? Location)?.clone() else null
    }

    override val description = "cine end location"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprEndLocation::class.java,
            Location::class.java,
            ::ExprEndLocation,
            "[the] cine end location [of %-cineplayback%]",
        )
    }
}

/** Discovery and diagnostics lists. */
class ExprCineInfo : CineExpression<String>(String::class.java, single = false) {
    private var kind = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        kind = result.mark
        return true
    }

    override fun values(event: Event): List<String> {
        val rt = rt()
        return when (kind) {
            1 -> Easing.ids()
            2 -> rt.effects.presets().sorted()
            3 -> Shape.ids()
            4 -> Interpolation.ids()
            5 -> Capability.entries.filter { rt.capabilities.has(it) }.map { it.id }
            6 -> rt.registry.ids().sorted()
            7 -> SequenceRegistry.names().sorted()
            8 -> listOf(if (rt.camera.isAvailable()) rt.camera.id else "none")
            else -> listOf(rt.version)
        }
    }

    override fun isSingle() = kind == 8 || kind == 9

    override val description = "cine info"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprCineInfo::class.java,
            String::class.java,
            ::ExprCineInfo,
            "[all [of]] [the] cine (1:easings|2:presets|3:shapes|4:interpolations|5:capabilities|6:scene ids|7:sequences|8:camera backend|9:version)",
            "[the] skcine (9:version)",
        )
    }
}

/** `number of active cine playbacks` and resource usage counters. */
class ExprCineCount : CineExpression<Number>(Number::class.java) {
    private var kind = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        kind = result.mark
        return true
    }

    override fun values(event: Event): List<Number> {
        val s = rt().snapshot()
        return listOf(
            when (kind) {
                1 -> s.activeScenes
                2 -> s.activeActors
                3 -> s.activeEffects
                4 -> s.activeCameras
                else -> s.activeAnimations
            }.toLong(),
        )
    }

    override fun getReturnType(): Class<out Number> = Long::class.javaObjectType

    override val description = "cine count"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprCineCount::class.java,
            Number::class.java,
            ::ExprCineCount,
            "[the] (number|amount|count) of (active|running) cine (1:(playbacks|scenes)|2:actors|3:effects|4:cameras|5:animations)",
        )
    }
}

/** In a choice section: `cine choice` (selected option) and whether it timed out (`cine choice timed out` condition). */
class ExprChoice : CineExpression<String>(String::class.java) {
    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        if (!parser.isCurrentEvent(CineChoiceSectionEvent::class.java)) {
            Skript.error("'cine choice' can only be used inside a 'present cine choice' section")
            return false
        }
        return true
    }

    override fun values(event: Event) = listOfNotNull((event as? CineChoiceSectionEvent)?.option)

    override val description = "cine choice"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprChoice::class.java,
            String::class.java,
            ::ExprChoice,
            "[the] (selected|chosen) cine (choice|option)",
            "[the] cine choice",
        )
    }
}
