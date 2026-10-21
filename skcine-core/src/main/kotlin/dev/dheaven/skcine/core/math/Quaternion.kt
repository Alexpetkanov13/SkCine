package dev.dheaven.skcine.core.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable unit quaternion (w + xi + yj + zk) used for rotation interpolation. */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {
    fun norm() = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val n = norm()
        return if (n < Vec3.EPSILON) IDENTITY else Quaternion(w / n, x / n, y / n, z / n)
    }

    operator fun times(o: Quaternion) = Quaternion(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun dot(o: Quaternion) = w * o.w + x * o.x + y * o.y + z * o.z

    /** Rotates [v] by this quaternion. */
    fun rotate(v: Vec3): Vec3 {
        val p = this * Quaternion(0.0, v.x, v.y, v.z) * conjugate()
        return Vec3(p.x, p.y, p.z)
    }

    /**
     * Converts back to Minecraft yaw/pitch by rotating the forward axis. Roll is discarded because the
     * vanilla client cannot render camera roll.
     */
    fun toYawPitch(fallbackYaw: Double = 0.0): Pair<Double, Double> {
        val dir = rotate(Vec3.Z)
        return Angles.yawOf(dir, fallbackYaw) to Angles.pitchOf(dir)
    }

    fun isFinite() = w.isFinite() && x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        fun axisAngle(axis: Vec3, degrees: Double): Quaternion {
            val n = axis.normalized()
            if (n == Vec3.ZERO) return IDENTITY
            val half = degrees * Angles.DEG / 2.0
            val s = sin(half)
            return Quaternion(cos(half), n.x * s, n.y * s, n.z * s)
        }

        /** Rotation that maps +Z to the Minecraft direction described by yaw/pitch (yaw about -Y, then pitch about +X). */
        fun fromYawPitch(yaw: Double, pitch: Double): Quaternion = axisAngle(Vec3(0.0, -1.0, 0.0), yaw) * axisAngle(Vec3.X, pitch)

        /** Euler angles in degrees, applied as Y (yaw), then X (pitch), then Z (roll). */
        fun fromEulerDegrees(pitchX: Double, yawY: Double, rollZ: Double): Quaternion = axisAngle(Vec3.UP, yawY) * axisAngle(Vec3.X, pitchX) * axisAngle(Vec3.Z, rollZ)

        /** Spherical linear interpolation along the shortest path. */
        fun slerp(a: Quaternion, b: Quaternion, t: Double): Quaternion {
            var bb = b
            var cosTheta = a.dot(b)
            if (cosTheta < 0.0) {
                bb = Quaternion(-b.w, -b.x, -b.y, -b.z)
                cosTheta = -cosTheta
            }
            if (cosTheta > 0.9995) {
                return Quaternion(
                    a.w + (bb.w - a.w) * t,
                    a.x + (bb.x - a.x) * t,
                    a.y + (bb.y - a.y) * t,
                    a.z + (bb.z - a.z) * t,
                ).normalized()
            }
            val theta = acos(cosTheta.coerceIn(-1.0, 1.0))
            val sinTheta = sin(theta)
            val wa = sin((1 - t) * theta) / sinTheta
            val wb = sin(t * theta) / sinTheta
            return Quaternion(
                a.w * wa + bb.w * wb,
                a.x * wa + bb.x * wb,
                a.y * wa + bb.y * wb,
                a.z * wa + bb.z * wb,
            ).normalized()
        }

        /** Angle between two rotations in degrees (0..180). */
        fun angleBetween(a: Quaternion, b: Quaternion): Double {
            val d = abs(a.normalized().dot(b.normalized())).coerceAtMost(1.0)
            return 2.0 * acos(d) * Angles.RAD
        }
    }
}
