package dev.dheaven.skcine.core.path

import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.core.interpolation.RotationInterpolation
import dev.dheaven.skcine.core.math.Angles
import dev.dheaven.skcine.core.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * A moving or static point the camera can look at or follow. Implementations resolve live positions
 * (for example an entity's eye location) and return null once the target is gone.
 */
fun interface TargetRef {
    fun resolve(): Vec3?

    companion object {
        fun fixed(v: Vec3) = TargetRef { v }
    }
}

/** A time-bounded camera behaviour. Later-added shots take priority while active. */
sealed interface CameraShot {
    val start: Double
    val end: Double

    /** Ticks over which this shot blends in from whatever the camera showed before it. */
    val blendIn: Double
    val blendEasing: Easing

    fun evaluate(time: Double): Pose?
}

class PathShot(val path: PosePath, override val blendIn: Double = 0.0, override val blendEasing: Easing = Easing.SMOOTHSTEP) : CameraShot {
    override val start get() = path.startTime
    override val end get() = path.endTime

    override fun evaluate(time: Double) = path.evaluate(time)
}

/** Circles [center] at [radius], sweeping [degrees] over the shot while looking at the center. */
class OrbitShot(
    val center: TargetRef,
    val radius: Double,
    val height: Double,
    val startAngle: Double,
    val degrees: Double,
    override val start: Double,
    override val end: Double,
    val easing: Easing = Easing.LINEAR,
    override val blendIn: Double = 0.0,
    override val blendEasing: Easing = Easing.SMOOTHSTEP,
) : CameraShot {
    override fun evaluate(time: Double): Pose? {
        val c = center.resolve() ?: return null
        val span = end - start
        val u = if (span <= 0.0) 1.0 else easing.apply((time - start) / span)
        val a = (startAngle + degrees * u) * Angles.DEG
        val pos = c + Vec3(cos(a) * radius, height, sin(a) * radius)
        val (yaw, pitch) = Angles.lookAt(pos, c)
        return Pose(pos, yaw, pitch)
    }
}

/** Keeps a fixed offset from a moving target, optionally looking at it. */
class FollowShot(
    val target: TargetRef,
    val offset: Vec3,
    val lookAtTarget: Boolean,
    val fixedYaw: Double,
    val fixedPitch: Double,
    override val start: Double,
    override val end: Double,
    override val blendIn: Double = 0.0,
    override val blendEasing: Easing = Easing.SMOOTHSTEP,
) : CameraShot {
    override fun evaluate(time: Double): Pose? {
        val t = target.resolve() ?: return null
        val pos = t + offset
        if (!lookAtTarget) return Pose(pos, fixedYaw, fixedPitch)
        val (yaw, pitch) = Angles.lookAt(pos, t, fixedYaw)
        return Pose(pos, yaw, pitch)
    }
}

/** A static camera; if [lookAt] is given it pans to track that (possibly moving) target. */
class FixedShot(
    val position: Vec3,
    val yaw: Double,
    val pitch: Double,
    val lookAt: TargetRef?,
    override val start: Double,
    override val end: Double,
    override val blendIn: Double = 0.0,
    override val blendEasing: Easing = Easing.SMOOTHSTEP,
) : CameraShot {
    override fun evaluate(time: Double): Pose {
        val target = lookAt?.resolve() ?: return Pose(position, yaw, pitch)
        val (y, p) = Angles.lookAt(position, target, yaw)
        return Pose(position, y, p)
    }
}

/** Post-processing applied to an evaluated pose (shake, dolly zoom). */
sealed interface CameraModifier {
    val start: Double
    val end: Double

    fun apply(pose: Pose, time: Double): Pose
}

/**
 * Smooth pseudo-random shake built from layered sines with seeded phases. Intensity decays to zero at
 * [end] so the shake never ends with a visible snap.
 */
class ShakeModifier(override val start: Double, override val end: Double, val intensity: Double, val frequency: Double = 1.0, seed: Long = 0L) : CameraModifier {
    private val phases = DoubleArray(9) { (((seed + it * 7919L) * 2654435761L) % 6283L) / 1000.0 }

    override fun apply(pose: Pose, time: Double): Pose {
        if (time < start || time > end || intensity <= 0.0) return pose
        val span = (end - start).coerceAtLeast(1e-6)
        val fade = 1.0 - (time - start) / span
        val amp = intensity * fade * fade
        val w = time * frequency

        fun n(i: Int) = sin(w * 1.7 + phases[i]) * 0.6 + sin(w * 3.1 + phases[i + 3]) * 0.3 + sin(w * 5.3 + phases[i + 6]) * 0.1
        val offset = Vec3(n(0), n(1), n(2)) * (amp * 0.15)
        return Pose(pose.position + offset, pose.yaw + n(1) * amp * 1.5, Angles.clampPitch(pose.pitch + n(2) * amp))
    }
}

/** Moves the camera forward along its view direction (a "dolly zoom" FOV approximation). */
class ZoomModifier(override val start: Double, override val end: Double, val distance: Double, val easing: Easing = Easing.SMOOTHSTEP, val holdAfter: Boolean = true) :
    CameraModifier {
    override fun apply(pose: Pose, time: Double): Pose {
        if (time < start) return pose
        if (time > end && !holdAfter) return pose
        val span = (end - start).coerceAtLeast(1e-6)
        val u = easing.apply((time - start) / span)
        return Pose(pose.position + pose.direction() * (distance * u), pose.yaw, pose.pitch)
    }
}

/**
 * Mutable camera track builder. Compile to [CompiledCameraTrack] for evaluation; the scene runtime recompiles
 * lazily whenever the builder changes, so live edits during playback are supported.
 */
class CameraTrack {
    private val keyframes = ArrayList<PoseKeyframe>()
    private val shots = ArrayList<CameraShot>()
    private val modifiers = ArrayList<CameraModifier>()

    var interpolation: Interpolation = Interpolation.BEZIER
        set(value) {
            field = value
            revision++
        }
    var rotationInterpolation: RotationInterpolation = RotationInterpolation.EULER
        set(value) {
            field = value
            revision++
        }

    /** Incremented on every mutation, used for cache invalidation. */
    var revision = 0
        private set

    val keyframeCount get() = keyframes.size
    val shotCount get() = shots.size

    fun keyframes(): List<PoseKeyframe> = keyframes.sortedBy { it.time }

    fun addKeyframe(k: PoseKeyframe) {
        keyframes.removeAll { it.time == k.time }
        keyframes.add(k)
        revision++
    }

    fun removeKeyframeAt(time: Double): Boolean = keyframes.removeAll { it.time == time }.also { if (it) revision++ }

    fun clearKeyframes() {
        keyframes.clear()
        revision++
    }

    fun addShot(shot: CameraShot) {
        shots.add(shot)
        revision++
    }

    fun addModifier(m: CameraModifier) {
        modifiers.add(m)
        revision++
    }

    /** Drops modifiers that ended before [time]; keeps long-running scenes from accumulating shakes. */
    fun pruneModifiers(time: Double) {
        if (modifiers.removeAll { it.end < time && it !is ZoomModifier }) revision++
    }

    fun isEmpty() = keyframes.isEmpty() && shots.isEmpty()

    fun copy(): CameraTrack {
        val c = CameraTrack()
        c.keyframes.addAll(keyframes)
        c.shots.addAll(shots)
        c.modifiers.addAll(modifiers)
        c.interpolation = interpolation
        c.rotationInterpolation = rotationInterpolation
        return c
    }

    fun compile(): CompiledCameraTrack {
        val layers = ArrayList<CameraShot>(shots.size + 1)
        if (keyframes.isNotEmpty()) layers.add(PathShot(PosePath(keyframes, interpolation, rotationInterpolation)))
        layers.addAll(shots)
        return CompiledCameraTrack(layers, modifiers.toList(), revision)
    }
}

class CompiledCameraTrack(private val layers: List<CameraShot>, private val modifiers: List<CameraModifier>, val revision: Int) {
    val isEmpty get() = layers.isEmpty()

    /** Latest time any layer ends; used for automatic scene duration. */
    val endTime get() = layers.maxOfOrNull { it.end } ?: 0.0

    fun evaluate(time: Double): Pose? {
        var pose = evaluateLayers(time, layers.size) ?: return null
        for (m in modifiers) pose = m.apply(pose, time)
        return if (pose.isFinite()) pose else null
    }

    /** Evaluates using only layers with index < [limit]. */
    private fun evaluateLayers(time: Double, limit: Int): Pose? {
        if (limit <= 0) return null
        var active = -1
        for (i in limit - 1 downTo 0) {
            val l = layers[i]
            if (time >= l.start && time <= l.end) {
                active = i
                break
            }
        }
        if (active < 0) return holdPose(time, limit)
        val layer = layers[active]
        val pose = layer.evaluate(time) ?: return evaluateLayers(time, active)
        if (layer.blendIn > 0.0 && time < layer.start + layer.blendIn) {
            val under = evaluateLayers(time, active) ?: return pose
            val u = layer.blendEasing.apply((time - layer.start) / layer.blendIn)
            return under.blend(pose, u)
        }
        return pose
    }

    /** No active layer: hold the most recently ended one, or the earliest upcoming one. */
    private fun holdPose(time: Double, limit: Int): Pose? {
        var best: CameraShot? = null
        for (i in 0 until limit) {
            val l = layers[i]
            if (l.end < time && (best == null || l.end >= best.end)) best = l
        }
        if (best != null) return best.evaluate(best.end)
        var first: CameraShot? = null
        for (i in 0 until limit) {
            val l = layers[i]
            if (first == null || l.start < first.start) first = l
        }
        return first?.evaluate(first.start)
    }
}
