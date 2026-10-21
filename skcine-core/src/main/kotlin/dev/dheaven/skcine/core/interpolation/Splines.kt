package dev.dheaven.skcine.core.interpolation

import dev.dheaven.skcine.core.math.Vec3
import kotlin.math.pow

/** Scalar and vector spline primitives. All functions are pure and allocation-light. */
object Splines {
    /** Cubic Hermite basis: positions p0,p1 and tangents m0,m1 over u ∈ [0,1]. */
    fun hermite(p0: Double, m0: Double, p1: Double, m1: Double, u: Double): Double {
        val u2 = u * u
        val u3 = u2 * u
        val h00 = 2 * u3 - 3 * u2 + 1
        val h10 = u3 - 2 * u2 + u
        val h01 = -2 * u3 + 3 * u2
        val h11 = u3 - u2
        return h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1
    }

    fun hermite(p0: Vec3, m0: Vec3, p1: Vec3, m1: Vec3, u: Double) = Vec3(
        hermite(p0.x, m0.x, p1.x, m1.x, u),
        hermite(p0.y, m0.y, p1.y, m1.y, u),
        hermite(p0.z, m0.z, p1.z, m1.z, u),
    )

    /** Cubic Bézier with control points b0..b3. */
    fun bezier(b0: Double, b1: Double, b2: Double, b3: Double, u: Double): Double {
        val v = 1 - u
        return v * v * v * b0 + 3 * v * v * u * b1 + 3 * v * u * u * b2 + u * u * u * b3
    }

    fun bezier(b0: Vec3, b1: Vec3, b2: Vec3, b3: Vec3, u: Double) = Vec3(
        bezier(b0.x, b1.x, b2.x, b3.x, u),
        bezier(b0.y, b1.y, b2.y, b3.y, u),
        bezier(b0.z, b1.z, b2.z, b3.z, u),
    )

    /**
     * Centripetal Catmull–Rom (alpha = 0.5) through p1→p2 using neighbours p0 and p3.
     * Centripetal parameterization avoids cusps and self-intersections on uneven keyframe spacing.
     */
    fun catmullRom(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, u: Double, alpha: Double = 0.5): Vec3 {
        val t0 = 0.0
        val t1 = t0 + knot(p0, p1, alpha)
        val t2 = t1 + knot(p1, p2, alpha)
        val t3 = t2 + knot(p2, p3, alpha)
        if (t2 - t1 < 1e-9) return p1.lerp(p2, u)
        val t = t1 + (t2 - t1) * u
        val a1 = blend(p0, p1, t0, t1, t)
        val a2 = blend(p1, p2, t1, t2, t)
        val a3 = blend(p2, p3, t2, t3, t)
        val b1 = blend(a1, a2, t0, t2, t)
        val b2 = blend(a2, a3, t1, t3, t)
        return blend(b1, b2, t1, t2, t)
    }

    private fun knot(a: Vec3, b: Vec3, alpha: Double): Double {
        val d = a.distance(b)
        // Duplicate neighbours get a tiny knot interval so the recursion stays well-defined.
        return if (d < 1e-9) 1e-4 else d.pow(alpha)
    }

    private fun blend(a: Vec3, b: Vec3, ta: Double, tb: Double, t: Double): Vec3 {
        val span = tb - ta
        if (span < 1e-12) return a
        return a * ((tb - t) / span) + b * ((t - ta) / span)
    }

    /** Uniform Catmull-Rom for scalar channels (angles), equivalent to Hermite with cardinal tangents. */
    fun catmullRomScalar(p0: Double, p1: Double, p2: Double, p3: Double, u: Double, tension: Double = 0.0): Double {
        val s = (1 - tension) / 2
        return hermite(p1, s * (p2 - p0), p2, s * (p3 - p1), u)
    }
}

/** How positions (and angle channels) are interpolated between keyframes. */
enum class Interpolation(val id: String) {
    /** Hold the previous keyframe value until the next keyframe. */
    STEP("step"),
    LINEAR("linear"),

    /** Centripetal Catmull–Rom spline through all keyframes (C1 continuous). */
    CATMULL_ROM("catmull-rom"),

    /** Cubic Bézier with handles derived from neighbouring keyframes, or explicit tangents. */
    BEZIER("bezier"),

    /** Cubic Hermite using per-keyframe tangents (cardinal tangents when unspecified). */
    HERMITE("hermite"),
    ;

    companion object {
        private val lookup =
            buildMap<String, Interpolation> {
                Interpolation.entries.forEach { put(it.id, it) }
                put("smooth", CATMULL_ROM)
                put("spline", CATMULL_ROM)
                put("catmullrom", CATMULL_ROM)
                put("cubic-bezier", BEZIER)
                put("cubic", BEZIER)
                put("constant", STEP)
                put("none", STEP)
            }

        fun byName(name: String): Interpolation? = lookup[Easing.normalize(name)]

        fun ids(): List<String> = entries.map { it.id }
    }
}

/** How rotation is interpolated between keyframes. */
enum class RotationInterpolation(val id: String) {
    /** Independent yaw/pitch channels interpolated with the position interpolation (yaw unwrapped). */
    EULER("euler"),

    /** Quaternion slerp between orientations: constant angular velocity on the shortest arc. */
    QUATERNION("quaternion"),
    ;

    companion object {
        fun byName(name: String): RotationInterpolation? = when (Easing.normalize(name)) {
            "euler", "angles", "yaw-pitch" -> EULER
            "quaternion", "slerp" -> QUATERNION
            else -> null
        }
    }
}
