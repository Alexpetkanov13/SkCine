package dev.dheaven.skcine.core.math

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minecraft rotation conventions.
 *
 * Yaw 0 faces +Z (south), 90 faces -X (west). Pitch is positive when looking down, range [-90, 90].
 */
object Angles {
    const val DEG = Math.PI / 180.0
    const val RAD = 180.0 / Math.PI

    /** Wraps any angle in degrees into (-180, 180]. */
    fun wrapDegrees(angle: Double): Double {
        var a = angle % 360.0
        if (a <= -180.0) a += 360.0
        if (a > 180.0) a -= 360.0
        return a
    }

    /** Signed shortest delta from [from] to [to], in degrees. */
    fun shortestDelta(from: Double, to: Double): Double = wrapDegrees(to - from)

    /** Interpolates yaw along the shortest arc, avoiding the jump at the ±180° boundary. */
    fun lerpYaw(from: Double, to: Double, t: Double): Double = wrapDegrees(from + shortestDelta(from, to) * t)

    fun clampPitch(pitch: Double): Double = pitch.coerceIn(-90.0, 90.0)

    /**
     * Converts a sequence of yaws into a continuous ("unwrapped") sequence where consecutive values never
     * differ by more than 180°. Splines can then interpolate without wrap discontinuities.
     */
    fun unwrapYaws(yaws: DoubleArray): DoubleArray {
        if (yaws.isEmpty()) return yaws
        val out = DoubleArray(yaws.size)
        out[0] = yaws[0]
        for (i in 1 until yaws.size) {
            out[i] = out[i - 1] + shortestDelta(out[i - 1], yaws[i])
        }
        return out
    }

    /** Unit direction vector for a yaw/pitch pair. */
    fun direction(yaw: Double, pitch: Double): Vec3 {
        val y = yaw * DEG
        val p = pitch * DEG
        val cp = cos(p)
        return Vec3(-sin(y) * cp, -sin(p), cos(y) * cp)
    }

    /** Yaw (degrees) that faces along [dir]; returns [fallback] for vertical/zero vectors. */
    fun yawOf(dir: Vec3, fallback: Double = 0.0): Double {
        if (dir.x * dir.x + dir.z * dir.z < Vec3.EPSILON) return fallback
        return wrapDegrees(atan2(-dir.x, dir.z) * RAD)
    }

    fun pitchOf(dir: Vec3): Double {
        val len = dir.length()
        if (len < Vec3.EPSILON) return 0.0
        return clampPitch(asin((-dir.y / len).coerceIn(-1.0, 1.0)) * RAD)
    }

    /** Yaw/pitch needed to look from [from] towards [to]. */
    fun lookAt(from: Vec3, to: Vec3, fallbackYaw: Double = 0.0): Pair<Double, Double> {
        val d = to - from
        return yawOf(d, fallbackYaw) to pitchOf(d)
    }

    fun horizontalDistance(d: Vec3) = sqrt(d.x * d.x + d.z * d.z)
}
