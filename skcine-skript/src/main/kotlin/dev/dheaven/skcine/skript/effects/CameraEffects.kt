package dev.dheaven.skcine.skript.effects

import ch.njol.skript.Skript
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.util.Color
import ch.njol.skript.util.Timespan
import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.core.interpolation.RotationInterpolation
import dev.dheaven.skcine.core.math.Angles
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.FixedShot
import dev.dheaven.skcine.core.path.FollowShot
import dev.dheaven.skcine.core.path.OrbitShot
import dev.dheaven.skcine.core.path.PoseKeyframe
import dev.dheaven.skcine.core.path.ShakeModifier
import dev.dheaven.skcine.core.path.ZoomModifier
import dev.dheaven.skcine.platform.dialogue.ComponentText
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.skript.CineBuildEvent
import dev.dheaven.skcine.skript.CineEffect
import dev.dheaven.skcine.skript.Ctx
import dev.dheaven.skcine.skript.applyToTarget
import dev.dheaven.skcine.skript.audienceOf
import dev.dheaven.skcine.skript.easingOf
import dev.dheaven.skcine.skript.effect
import dev.dheaven.skcine.skript.expr
import dev.dheaven.skcine.skript.instancesOf
import dev.dheaven.skcine.skript.opt
import dev.dheaven.skcine.skript.report
import dev.dheaven.skcine.skript.resolveTarget
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.sections.requireScene
import dev.dheaven.skcine.skript.targetOf
import dev.dheaven.skcine.skript.ticks
import dev.dheaven.skcine.skript.vec
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.util.Vector
import org.skriptlang.skript.registration.SyntaxRegistry
import java.time.Duration

/**
 * ```
 * add cine camera keyframe at {_loc} at 2 seconds [with easing "ease-in-out-sine"] [looking at {_target}]
 * add cine camera keyframe at {_loc} to {_scene} at 0 seconds
 * ```
 * The location's yaw/pitch is the camera rotation unless `looking at` overrides it.
 */
class EffCameraKeyframe : CineEffect() {
    private lateinit var loc: Expression<Location>
    private lateinit var time: Expression<Timespan>
    private var easing: Expression<String>? = null
    private var look: Expression<*>? = null
    private var target: Expression<*>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        loc = e.expr(0)
        if (pattern == 0) {
            time = e.expr(1)
            easing = e.opt(2)
            look = e[3]
            target = e[4]
        } else {
            target = e[1]
            time = e.expr(2)
            easing = e.opt(3)
            look = e[4]
        }
        return requireScene(target, "add cine camera keyframe")
    }

    override fun execute(event: Event) {
        val l = loc.getSingle(event) ?: return report(where, "camera keyframe location is not set")
        if (l.world == null) return report(where, "invalid camera keyframe: location has no world")
        val t = time.getSingle(event)?.ticks() ?: return report(where, "camera keyframe time is not set")
        val ease = easingOf(easing?.getSingle(event), where)
        val lookAt = look?.getSingle(event)?.let { o -> targetOf(o)?.resolve() }
        val tgt = resolveTarget(event, target, where) ?: return
        applyToTarget(tgt, where) { inst, offset ->
            inst.addCameraKeyframe(l, offset + t) { pos, yaw, pitch ->
                val (y, p) = if (lookAt != null) Angles.lookAt(pos, lookAt, yaw) else yaw to pitch
                PoseKeyframe(offset + t, pos, y, p, ease)
            }
        }
    }

    override val description = "add cine camera keyframe"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffCameraKeyframe::class.java,
            ::EffCameraKeyframe,
            "add [a] cine camera (keyframe|point) at %location% at %timespan% [with easing %-string%] [looking at %-location/entity%] [(to|in) %-cinescene/cineplayback%]",
            "add [a] cine camera (keyframe|point) at %location% (to|in) %cinescene/cineplayback% at %timespan% [with easing %-string%] [looking at %-location/entity%]",
        )
    }
}

/** `set cine camera [rotation] interpolation [of {_s}] to "catmull-rom"` */
class EffCameraInterpolation : CineEffect() {
    private lateinit var value: Expression<String>
    private var target: Expression<*>? = null
    private var rotation = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        target = e[0]
        value = e.expr(1)
        rotation = result.hasTag("rotation")
        return requireScene(target, "set cine camera interpolation")
    }

    override fun execute(event: Event) {
        val v = value.getSingle(event) ?: return
        val tgt = resolveTarget(event, target, where) ?: return
        if (rotation) {
            val mode =
                RotationInterpolation.byName(v) ?: return report(where, "unknown rotation interpolation '$v' (use euler or quaternion)")
            applyToTarget(tgt, where) { inst, _ ->
                inst.timeline.camera.rotationInterpolation = mode
                null
            }
        } else {
            val mode =
                Interpolation.byName(v) ?: return report(where, "unknown interpolation '$v'; valid: ${Interpolation.ids().joinToString()}")
            applyToTarget(tgt, where) { inst, _ ->
                inst.timeline.camera.interpolation = mode
                null
            }
        }
    }

    override val description = "set cine camera interpolation"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffCameraInterpolation::class.java,
            ::EffCameraInterpolation,
            "set [the] cine camera [rotation:rotation] interpolation [of %-cinescene/cineplayback%] to %string%",
        )
    }
}

/**
 * Camera shots over a time window, overriding keyframes while active and blending in smoothly:
 * orbit, follow, track (fixed position looking at a target) and hold.
 */
class EffCameraShot : CineEffect() {
    private lateinit var subject: Expression<*>
    private var from: Expression<Location>? = null
    private lateinit var start: Expression<Timespan>
    private lateinit var end: Expression<Timespan>
    private var radius: Expression<Number>? = null
    private var height: Expression<Number>? = null
    private var degrees: Expression<Number>? = null
    private var offset: Expression<Vector>? = null
    private var blend: Expression<Timespan>? = null
    private var target: Expression<*>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        subject = e[0]
        when (pattern) {
            0 -> {
                start = e.expr(1)
                end = e.expr(2)
                radius = e.opt(3)
                height = e.opt(4)
                degrees = e.opt(5)
                blend = e.opt(6)
                target = e[7]
            }
            1 -> {
                start = e.expr(1)
                end = e.expr(2)
                offset = e.opt(3)
                blend = e.opt(4)
                target = e[5]
            }
            2 -> {
                from = e.expr(1)
                start = e.expr(2)
                end = e.expr(3)
                blend = e.opt(4)
                target = e[5]
            }
            else -> {
                start = e.expr(1)
                end = e.expr(2)
                blend = e.opt(3)
                target = e[4]
            }
        }
        return requireScene(target, "make the cine camera ...")
    }

    override fun execute(event: Event) {
        val s = start.getSingle(event)?.ticks() ?: return
        val en = end.getSingle(event)?.ticks() ?: return
        if (en < s) return report(where, "camera shot ends before it starts")
        val b = blend?.getSingle(event)?.ticks() ?: 0.0
        val subj = subject.getSingle(event) ?: return report(where, "camera subject is not set")
        val world =
            when (subj) {
                is Entity -> subj.world
                is Location -> subj.world ?: return report(where, "camera location has no world")
                else -> return
            }
        val r = radius?.getSingle(event)?.toDouble() ?: 6.0
        val h = height?.getSingle(event)?.toDouble() ?: 2.0
        val deg = degrees?.getSingle(event)?.toDouble() ?: 360.0
        val off = offset?.getSingle(event)?.let { Vec3(it.x, it.y, it.z) } ?: Vec3(0.0, 2.0, -4.0)
        val fromLoc = from?.getSingle(event)
        if (!r.isFinite() || r < 0 || r > 256 || !h.isFinite() || !deg.isFinite()) return report(where, "invalid orbit radius/height/angle")
        val subjectRef = targetOf(subj) ?: return report(where, "invalid camera subject")
        val tgt = resolveTarget(event, target, where) ?: return
        applyToTarget(tgt, where) { inst, offsetTicks ->
            inst.claimCameraWorld(world)?.let { return@applyToTarget it }
            val a = offsetTicks + s
            val z = offsetTicks + en
            val shot =
                when (pattern) {
                    0 -> OrbitShot(subjectRef, r, h, 90.0, deg, a, z, Easing.LINEAR, b)
                    1 -> {
                        // Follow behind the subject: offset is in the subject's local space at shot creation.
                        val yaw = (subj as? Entity)?.location?.yaw?.toDouble() ?: 0.0
                        val rad = Math.toRadians(yaw)
                        val world =
                            Vec3(off.x * Math.cos(rad) - off.z * Math.sin(rad), off.y, off.x * Math.sin(rad) + off.z * Math.cos(rad))
                        FollowShot(subjectRef, world, true, yaw, 0.0, a, z, b)
                    }
                    2 -> {
                        val f = fromLoc ?: return@applyToTarget "camera position is not set"
                        if (f.world != world) return@applyToTarget "the camera position and its target must be in the same world"
                        FixedShot(f.vec(), f.yaw.toDouble(), f.pitch.toDouble(), subjectRef, a, z, b)
                    }
                    else -> {
                        val l = subj as? Location ?: return@applyToTarget "hold needs a location"
                        FixedShot(l.vec(), l.yaw.toDouble(), l.pitch.toDouble(), null, a, z, b)
                    }
                }
            inst.timeline.camera.addShot(shot)
            null
        }
    }

    override val description = "make cine camera shot"

    companion object {
        private const val WINDOW = "between %timespan% and %timespan%"
        private const val TAIL = "[blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]"

        fun register(r: SyntaxRegistry) = r.effect(
            EffCameraShot::class.java,
            ::EffCameraShot,
            "make [the] cine camera orbit [around] %location/entity% $WINDOW [with radius %-number%] [[and] height %-number%] [[and] (sweeping|by) %-number% degrees] $TAIL",
            "make [the] cine camera follow %entity% $WINDOW [with offset %-vector%] $TAIL",
            "make [the] cine camera (watch|track|look at) %location/entity% from %location% $WINDOW $TAIL",
            "make [the] cine camera (hold|stay) at %location% $WINDOW $TAIL",
        )
    }
}

/** Resolves when a camera modifier starts: explicit time (+ sequence offset), or "now" in a playing scene. */
private fun modifierStart(event: Event, inst: SceneInstance, at: Timespan?): Double {
    val base = if (event is CineBuildEvent && Ctx.instance(event) === inst) event.offset else 0.0
    return if (at != null) {
        base + at.ticks()
    } else if (event is CineBuildEvent) {
        base
    } else {
        inst.position
    }
}

/** `shake cine camera [of player] [with intensity 1.5] for 1 second [at 4 seconds]` */
class EffShake : CineEffect() {
    private var targets: Expression<*>? = null
    private var intensity: Expression<Number>? = null
    private lateinit var duration: Expression<Timespan>
    private var at: Expression<Timespan>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        targets = e[0]
        intensity = e.opt(1)
        duration = e.expr(2)
        at = e.opt(3)
        if (targets == null && !Ctx.inSceneContext()) {
            Skript.error("Name whose camera to shake (e.g. 'shake cine camera of player') outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val d = duration.getSingle(event)?.ticks() ?: return
        val i = (intensity?.getSingle(event)?.toDouble() ?: 1.0)
        if (!i.isFinite() || i < 0 || i > 20) return report(where, "shake intensity must be between 0 and 20")
        val atv = at?.getSingle(event)
        for (inst in instancesOf(event, targets)) {
            val s = modifierStart(event, inst, atv)
            inst.timeline.camera.addModifier(ShakeModifier(s, s + d, i, 1.0, inst.number * 31 + s.toLong()))
        }
    }

    override val description = "shake cine camera"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffShake::class.java,
            ::EffShake,
            "shake [the] cine camera [of %-players/cineplaybacks%] [with intensity %-number%] for %timespan% [at %-timespan%]",
        )
    }
}

/** `zoom cine camera [of player] by 3 blocks over 2 seconds` — dolly forward (FOV changes are not possible). */
class EffZoom : CineEffect() {
    private var targets: Expression<*>? = null
    private lateinit var distance: Expression<Number>
    private lateinit var duration: Expression<Timespan>
    private var easing: Expression<String>? = null
    private var at: Expression<Timespan>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        targets = e[0]
        distance = e.expr(1)
        duration = e.expr(2)
        easing = e.opt(3)
        at = e.opt(4)
        if (targets == null && !Ctx.inSceneContext()) {
            Skript.error("Name whose camera to zoom outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val dist = distance.getSingle(event)?.toDouble() ?: return
        if (!dist.isFinite() || kotlin.math.abs(dist) > 64) return report(where, "zoom distance must be between -64 and 64 blocks")
        val d = duration.getSingle(event)?.ticks() ?: return
        val ease = easingOf(easing?.getSingle(event), where, Easing.SMOOTHSTEP)
        val atv = at?.getSingle(event)
        for (inst in instancesOf(event, targets)) {
            val s = modifierStart(event, inst, atv)
            inst.timeline.camera.addModifier(ZoomModifier(s, s + d, dist, ease))
        }
    }

    override val description = "zoom cine camera"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffZoom::class.java,
            ::EffZoom,
            "zoom [the] cine camera [of %-players/cineplaybacks%] [in] by %number% [blocks] over %timespan% [with easing %-string%] [at %-timespan%]",
        )
    }
}

/**
 * `fade cine screen out [with colour black] [for player] [over 1 second]` / `fade cine screen in ...`
 * `flash cine screen [with colour white] [for player] [over 10 ticks]`
 */
class EffScreen : CineEffect() {
    private var color: Expression<Color>? = null
    private var audience: Expression<*>? = null
    private var duration: Expression<Timespan>? = null
    private var mode = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        mode = if (pattern == 1) 3 else result.mark
        color = e.opt(0)
        audience = e[1]
        duration = e.opt(2)
        if (audience == null && !Ctx.inSceneContext()) {
            Skript.error("Name the players (e.g. 'for player') outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val c = color?.getSingle(event)?.asBukkitColor() ?: if (mode == 3) org.bukkit.Color.WHITE else org.bukkit.Color.BLACK
        val ticks = duration?.getSingle(event)?.ticks()?.toInt() ?: if (mode == 3) 12 else 20
        val owner = Ctx.instance(event)
        val ov = rt().overlays
        for (p in audienceOf(event, audience)) {
            val inst = owner ?: rt().manager.current(p)
            when (mode) {
                1 -> ov.fade(p, c, 1.0, ticks, Easing.SMOOTHSTEP, inst)
                2 -> ov.fade(p, c, 0.0, ticks, Easing.SMOOTHSTEP, inst)
                else -> ov.flash(p, c, ticks, inst)
            }
        }
    }

    override val description = "cine screen transition"

    companion object {
        private const val TAIL = "[with colo[u]r %-color%] [for %-players/cineplaybacks%] [over %-timespan%]"

        fun register(r: SyntaxRegistry) = r.effect(
            EffScreen::class.java,
            ::EffScreen,
            "fade [the] cine (screen|view) (1:out|1:to black|2:in|2:from black) $TAIL",
            "flash [the] cine (screen|view) $TAIL",
        )
    }
}

/** `show cine title "<gold>Welcome!" [with subtitle "..."] [to player] [for 3 seconds] [with fade 10 ticks]` */
class EffCineTitle : CineEffect() {
    private lateinit var title: Expression<String>
    private var subtitle: Expression<String>? = null
    private var audience: Expression<*>? = null
    private var stay: Expression<Timespan>? = null
    private var fade: Expression<Timespan>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        title = e.expr(0)
        subtitle = e.opt(1)
        audience = e[2]
        stay = e.opt(3)
        fade = e.opt(4)
        if (audience == null && !Ctx.inSceneContext()) {
            Skript.error("Name the players ('to player') outside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val t = title.getSingle(event)?.let { ComponentText.parse(it) } ?: return
        val st = subtitle?.getSingle(event)?.let { ComponentText.parse(it) } ?: Component.empty()
        val stayMs = (stay?.getSingle(event)?.ticks() ?: 60.0) * 50
        val fadeMs = (fade?.getSingle(event)?.ticks() ?: 10.0) * 50
        val times =
            Title.Times.times(
                Duration.ofMillis(fadeMs.toLong()),
                Duration.ofMillis(stayMs.toLong()),
                Duration.ofMillis(fadeMs.toLong()),
            )
        val tt = Title.title(t, st, times)
        audienceOf(event, audience).forEach { it.showTitle(tt) }
    }

    override val description = "show cine title"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffCineTitle::class.java,
            ::EffCineTitle,
            "show [a] cine title %string% [with subtitle %-string%] [to %-players/cineplaybacks%] [for %-timespan%] [with fade %-timespan%]",
        )
    }
}
