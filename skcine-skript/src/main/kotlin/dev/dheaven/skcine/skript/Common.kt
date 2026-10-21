package dev.dheaven.skcine.skript

import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.Trigger
import ch.njol.skript.lang.TriggerItem
import ch.njol.skript.lang.parser.ParserInstance
import ch.njol.skript.util.Timespan
import ch.njol.skript.variables.Variables
import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.TargetRef
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.timeline.FireReason
import dev.dheaven.skcine.platform.SkCineRuntime
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/*
 * Context events. These are never fired through Bukkit; they are the event objects Skript code runs with inside
 * SkCine sections, so `event-cineplayback`, `event-player` and implicit-scene syntax work there.
 */

abstract class CineContextEvent(val instance: SceneInstance?) : Event() {
    override fun getHandlers(): HandlerList = throw IllegalStateException("SkCine context events are not Bukkit events")
}

/** Runs a scene's `timeline:` body (or a sequence) while an instance is being built. [offset] is in ticks. */
class CineBuildEvent(instance: SceneInstance, val offset: Double) : CineContextEvent(instance)

/** Runs the body of a timed section (`at 3 seconds:`, `after ...`, `repeat every ...`). */
class CineTimelineEvent(instance: SceneInstance, val reason: FireReason) : CineContextEvent(instance)

/** Runs `on start:` / `on finish:` ... entries of a `cine scene` structure. */
class CineCallbackEvent(instance: SceneInstance, val reason: EndReason?) : CineContextEvent(instance)

/** Runs the body of a `present cine choice` section when an option is selected. */
class CineChoiceSectionEvent(instance: SceneInstance?, val player: Player?, val option: String?, val timedOut: Boolean) : CineContextEvent(instance)

/** Temporary carrier used only to copy local-variable maps. */
private class LocalsCarrier : Event() {
    override fun getHandlers(): HandlerList = throw IllegalStateException()
}

/**
 * A frozen copy of a trigger's local variables. Each [applyTo] gives the target event its own fresh copy, so timed
 * sections that fire repeatedly never share or leak local state. Nothing is held in Skript's global locals map.
 */
class LocalsSnapshot private constructor(private val map: Any?) {
    fun applyTo(event: Event) {
        if (map == null) return
        val carrier = LocalsCarrier()
        Variables.setLocalVariables(carrier, map)
        Variables.setLocalVariables(event, Variables.copyLocalVariables(carrier))
        Variables.setLocalVariables(carrier, null)
    }

    companion object {
        val EMPTY = LocalsSnapshot(null)

        fun of(event: Event) = LocalsSnapshot(Variables.copyLocalVariables(event))
    }
}

/**
 * Locals of the trigger that called `play cine scene`, made visible to the scene's `timeline:` body. This lets a
 * scene act as a parameterized template: `set {_boss} to ...` then `play cine scene "boss-intro" for all players`.
 * Main thread only.
 */
object CallerLocals {
    var current: LocalsSnapshot = LocalsSnapshot.EMPTY
}

/** Runs a compiled section body with [locals], cleaning up afterwards. Delays inside the body are supported. */
fun runSection(trigger: Trigger, event: Event, locals: LocalsSnapshot) {
    locals.applyTo(event)
    try {
        TriggerItem.walk(trigger, event)
    } finally {
        Variables.setLocalVariables(event, null)
    }
}

/** Parse-time helpers. */
object Ctx {
    val contextEvents =
        arrayOf(
            CineBuildEvent::class.java,
            CineTimelineEvent::class.java,
            CineCallbackEvent::class.java,
            CineChoiceSectionEvent::class.java,
        )

    fun inSceneContext(): Boolean = ParserInstance.get().isCurrentEvent(*contextEvents)

    fun inBuildContext(): Boolean = ParserInstance.get().isCurrentEvent(CineBuildEvent::class.java)

    /** "cinematics.sk, line 12" for the line being parsed, when Skript provides it. */
    fun here(): String? {
        val p = ParserInstance.get()
        val node = p.node ?: return null
        val script = runCatching { p.currentScript.config.fileName }.getOrNull()
        return if (script != null) "$script, line ${node.line}" else "line ${node.line}"
    }

    fun instance(e: Event): SceneInstance? = (e as? CineContextEvent)?.instance

    /** Time base for `at`: the sequence offset while building, otherwise 0 (absolute scene time). */
    fun offset(e: Event): Double = (e as? CineBuildEvent)?.offset ?: 0.0
}

fun rt(): SkCineRuntime = SkCineRuntime.get()

fun Timespan.ticks(): Double = getAs(Timespan.TimePeriod.MILLISECOND) / 50.0

fun ticksToTimespan(ticks: Double): Timespan = Timespan(Timespan.TimePeriod.MILLISECOND, (ticks.coerceAtLeast(0.0) * 50.0).toLong())

fun Location.vec() = Vec3(x, y, z)

/** Live target for cameras/look-at: an entity's eye (or center) or a fixed location. */
fun targetOf(o: Any?): TargetRef? = when (o) {
    is Entity -> {
        val id = o.uniqueId
        val w = o.world
        TargetRef {
            val e =
                org.bukkit.Bukkit
                    .getEntity(id)
                    ?.takeIf { it.isValid && it.world == w } ?: return@TargetRef null
            val l = if (e is LivingEntity) e.eyeLocation else e.location
            l.vec()
        }
    }
    is Location -> if (o.world == null) null else TargetRef.fixed(o.vec())
    else -> null
}

fun Any.entityOrNull(): Entity? = when (this) {
    is Entity -> this
    is dev.dheaven.skcine.platform.actor.CineActor -> this.entity()
    else -> null
}

/** Reports a runtime problem through SkCine diagnostics, prefixed with the script location. */
fun report(where: String?, message: String, instance: SceneInstance? = null) {
    val rt = SkCineRuntime.getOrNull() ?: return
    val scene = instance?.let { "Scene '${it.sceneId}': " } ?: ""
    val text = (if (where != null) "[$where] " else "") + scene + message
    rt.diagnostics.error(text, key = (where ?: "") + "|" + message)
}

/** Parses an easing name; reports and falls back to linear when unknown. */
fun easingOf(name: String?, where: String?, default: Easing = Easing.LINEAR): Easing {
    if (name == null) return default
    return Easing.byName(name) ?: default.also {
        report(where, "unknown easing '$name'; using '${default.id}'. Valid easings: ${Easing.ids().joinToString()}")
    }
}

/** Where content-adding syntax applies: a definition (deferred build step) or a live instance. */
sealed interface SceneTarget {
    data class Definition(val def: SceneDefinition) : SceneTarget

    data class Instance(val inst: SceneInstance, val offset: Double) : SceneTarget
}

/**
 * Resolves the scene a statement targets: the explicit expression if given, otherwise the scene whose timeline is
 * being built or played. Returns null (after reporting) when there is none.
 */
fun resolveTarget(e: Event, explicit: Expression<*>?, where: String?): SceneTarget? {
    if (explicit != null) {
        return when (val v = explicit.getSingle(e)) {
            is SceneDefinition -> SceneTarget.Definition(v)
            is SceneInstance ->
                if (v.state.isTerminal) {
                    report(where, "the cine playback has already ended", v)
                    null
                } else {
                    SceneTarget.Instance(v, 0.0)
                }
            null -> {
                report(where, "the given cine scene is not set")
                null
            }
            else -> null
        }
    }
    val inst = Ctx.instance(e)
    if (inst == null) {
        report(where, "no cine scene here: use this inside a cine scene/sequence or name the scene explicitly")
        return null
    }
    return SceneTarget.Instance(inst, Ctx.offset(e))
}

/**
 * Applies [op] to the target. For definitions the operation runs for every future playback (its arguments were
 * already evaluated); for instances it runs now. [op] returns an error message or null.
 */
fun applyToTarget(target: SceneTarget, where: String?, op: (SceneInstance, Double) -> String?) {
    when (target) {
        is SceneTarget.Definition ->
            target.def.addStep { inst ->
                op(inst, 0.0)?.let { report(where, it, inst) }
            }
        is SceneTarget.Instance -> op(target.inst, target.offset)?.let { report(where, it, target.inst) }
    }
}

/** Players referenced by an expression that may yield players or playbacks; defaults to the context viewers. */
fun audienceOf(e: Event, expr: Expression<*>?): List<Player> {
    if (expr == null) return Ctx.instance(e)?.viewers ?: emptyList()
    return expr
        .getArray(e)
        .flatMap {
            when (it) {
                is Player -> listOf(it)
                is SceneInstance -> it.viewers
                else -> emptyList()
            }
        }.distinct()
}

/** Scene instances referenced by players (their current scene) or playback values. */
fun instancesOf(e: Event, expr: Expression<*>?): List<SceneInstance> {
    if (expr == null) return listOfNotNull(Ctx.instance(e))
    return expr
        .getArray(e)
        .mapNotNull {
            when (it) {
                is SceneInstance -> it
                is Player -> rt().manager.current(it)
                else -> null
            }
        }.distinct()
}

/** Owns arbitrary cleanup on an instance. */
fun SceneInstance.onEnd(block: (EndReason) -> Unit) = own(SceneResource { block(it) })
