package dev.dheaven.skcine.core.path

import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.core.interpolation.RotationInterpolation
import dev.dheaven.skcine.core.interpolation.Splines
import dev.dheaven.skcine.core.math.Angles
import dev.dheaven.skcine.core.math.Quaternion
import dev.dheaven.skcine.core.math.Vec3

/** A position plus Minecraft yaw/pitch. Immutable. */
data class Pose(val position: Vec3, val yaw: Double, val pitch: Double) {
    fun direction(): Vec3 = Angles.direction(yaw, pitch)

    fun isFinite() = position.isFinite() && yaw.isFinite() && pitch.isFinite()

    /** Blends towards [other]; yaw takes the shortest arc. */
    fun blend(other: Pose, t: Double) = Pose(
        position.lerp(other.position, t),
        Angles.lerpYaw(yaw, other.yaw, t),
        pitch + (other.pitch - pitch) * t,
    )
}

/**
 * A keyframe on a pose path.
 *
 * @param time logical time in ticks (fractional allowed).
 * @param easing easing applied to the segment that *ends* at this keyframe.
 * @param tangent optional explicit tangent (blocks per segment) used by [Interpolation.HERMITE].
 */
data class PoseKeyframe(val time: Double, val position: Vec3, val yaw: Double, val pitch: Double, val easing: Easing = Easing.LINEAR, val tangent: Vec3? = null) {
    init {
        require(time.isFinite() && time >= 0.0) { "keyframe time must be a finite, non-negative number of ticks" }
        require(position.isFinite()) { "keyframe position must be finite" }
        require(yaw.isFinite() && pitch.isFinite()) { "keyframe rotation must be finite" }
        require(tangent?.isFinite() ?: true) { "keyframe tangent must be finite" }
    }
}

/**
 * Immutable, precomputed keyframe path. Evaluation is O(log n) (binary search) and allocation-light.
 *
 * Keyframes are sorted by time; keyframes sharing the same time keep only the last one added.
 */
class PosePath(
    keyframes: List<PoseKeyframe>,
    val interpolation: Interpolation = Interpolation.BEZIER,
    val rotationInterpolation: RotationInterpolation = RotationInterpolation.EULER,
    val tension: Double = 0.0,
) {
    val keyframes: List<PoseKeyframe> =
        keyframes
            .sortedBy { it.time }
            .let { sorted -> sorted.filterIndexed { i, k -> i == sorted.lastIndex || sorted[i + 1].time != k.time } }

    private val times = DoubleArray(this.keyframes.size) { this.keyframes[it].time }
    private val positions = Array(this.keyframes.size) { this.keyframes[it].position }

    // Yaw unwrapped so splines never cross the ±180° discontinuity; stored with pitch as a 2-channel vector.
    private val angles: Array<Vec3> =
        run {
            val unwrapped = Angles.unwrapYaws(DoubleArray(this.keyframes.size) { this.keyframes[it].yaw })
            Array(this.keyframes.size) { Vec3(unwrapped[it], this.keyframes[it].pitch, 0.0) }
        }
    private val rotations = Array(this.keyframes.size) { Quaternion.fromYawPitch(this.keyframes[it].yaw, this.keyframes[it].pitch) }

    val isEmpty get() = keyframes.isEmpty()
    val startTime get() = if (times.isEmpty()) 0.0 else times.first()
    val endTime get() = if (times.isEmpty()) 0.0 else times.last()

    /** Evaluates the pose at [time] ticks. Times outside the path clamp to the first/last keyframe. */
    fun evaluate(time: Double): Pose? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1 || time <= times[0]) return poseAt(0)
        if (time >= times.last()) return poseAt(keyframes.lastIndex)
        val i = segmentIndex(time)
        val span = times[i + 1] - times[i]
        val raw = if (span <= 0.0) 1.0 else (time - times[i]) / span
        val u = keyframes[i + 1].easing.apply(raw)
        val pos = interpolate(positions, i, u, centripetal = true)
        val (yaw, pitch) =
            when (rotationInterpolation) {
                RotationInterpolation.QUATERNION -> {
                    val q = Quaternion.slerp(rotations[i], rotations[i + 1], u)
                    q.toYawPitch(keyframes[i].yaw)
                }
                RotationInterpolation.EULER -> {
                    val a = interpolate(angles, i, u, centripetal = false)
                    Angles.wrapDegrees(a.x) to Angles.clampPitch(a.y)
                }
            }
        return Pose(pos, yaw, pitch)
    }

    private fun poseAt(i: Int) = Pose(positions[i], Angles.wrapDegrees(keyframes[i].yaw), Angles.clampPitch(keyframes[i].pitch))

    /** Index i such that times[i] <= time < times[i+1]. */
    private fun segmentIndex(time: Double): Int {
        var lo = 0
        var hi = times.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (times[mid] <= time) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun interpolate(pts: Array<Vec3>, i: Int, u: Double, centripetal: Boolean): Vec3 {
        val p1 = pts[i]
        val p2 = pts[i + 1]
        return when (interpolation) {
            Interpolation.STEP -> if (u >= 1.0) p2 else p1
            Interpolation.LINEAR -> p1.lerp(p2, u)
            Interpolation.CATMULL_ROM -> {
                val p0 = if (i > 0) pts[i - 1] else p1 * 2.0 - p2
                val p3 = if (i + 2 < pts.size) pts[i + 2] else p2 * 2.0 - p1
                Splines.catmullRom(p0, p1, p2, p3, u, if (centripetal) 0.5 else 0.0)
            }
            Interpolation.HERMITE -> {
                val m1 = tangentFor(pts, i)
                val m2 = tangentFor(pts, i + 1)
                Splines.hermite(p1, m1, p2, m2, u)
            }
            Interpolation.BEZIER -> {
                // Time-aware handles: velocity (units per tick) is continuous across keyframes even when
                // keyframe spacing is uneven, which avoids visible camera speed jumps.
                val dt = times[i + 1] - times[i]
                val v1 = velocityAt(pts, i)
                val v2 = velocityAt(pts, i + 1)
                Splines.bezier(p1, p1 + v1 * (dt * (1 - tension) / 3.0), p2 - v2 * (dt * (1 - tension) / 3.0), p2, u)
            }
        }
    }

    private fun tangentFor(pts: Array<Vec3>, i: Int): Vec3 {
        if (pts === positions) keyframes[i].tangent?.let { return it }
        val prev = if (i > 0) pts[i - 1] else pts[i]
        val next = if (i + 1 < pts.size) pts[i + 1] else pts[i]
        val scale = if (i > 0 && i + 1 < pts.size) 0.5 else 1.0
        return (next - prev) * ((1 - tension) * scale)
    }

    private fun velocityAt(pts: Array<Vec3>, i: Int): Vec3 {
        val prevI = if (i > 0) i - 1 else i
        val nextI = if (i + 1 < pts.size) i + 1 else i
        val dt = times[nextI] - times[prevI]
        if (dt <= 0.0) return Vec3.ZERO
        return (pts[nextI] - pts[prevI]) / dt
    }
}
