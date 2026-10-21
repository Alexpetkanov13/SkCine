package dev.dheaven.skcine.core.math

import kotlin.math.sqrt

/** Immutable 3D vector in world space (Minecraft axes: +X east, +Y up, +Z south). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)

    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)

    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun lengthSquared() = x * x + y * y + z * z

    fun length() = sqrt(lengthSquared())

    fun distance(o: Vec3) = (this - o).length()

    fun normalized(): Vec3 {
        val len = length()
        return if (len < EPSILON) ZERO else this / len
    }

    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    fun lerp(to: Vec3, t: Double) = Vec3(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t)

    companion object {
        const val EPSILON = 1.0e-9
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UP = Vec3(0.0, 1.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
    }
}
