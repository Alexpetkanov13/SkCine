package dev.dheaven.skcine.core.geometry

import dev.dheaven.skcine.core.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Parameters for a particle shape. Point counts are the *maximum* density; LOD may scale them down. */
data class ShapeParams(val radius: Double = 1.0, val height: Double = 2.0, val points: Int = 32, val sides: Int = 5, val turns: Double = 2.0, val angle: Double = 360.0) {
    init {
        require(radius.isFinite() && radius >= 0 && radius <= 256) { "radius must be between 0 and 256" }
        require(height.isFinite() && abs(height) <= 256) { "height must be between -256 and 256" }
        require(points in 1..4096) { "point count must be between 1 and 4096" }
        require(sides in 3..64) { "polygon sides must be between 3 and 64" }
        require(turns.isFinite() && turns > 0 && turns <= 64) { "turns must be between 0 and 64" }
        require(angle.isFinite() && abs(angle) <= 3600) { "angle must be between -3600 and 3600 degrees" }
    }
}

/**
 * Pure geometry generators returning point offsets relative to the shape origin (Y up).
 * Results are immutable lists and are cached by [ShapeCache], so hot paths never recompute geometry.
 */
enum class Shape(val id: String, private val gen: (ShapeParams) -> List<Vec3>) {
    POINT("point", { listOf(Vec3.ZERO) }),
    CIRCLE("circle", { p -> ring(p.radius, p.points, 0.0, p.angle) }),
    RING("ring", { p -> ring(p.radius, p.points / 2, 0.0, 360.0) + ring(p.radius * 0.8, p.points / 2, 0.0, 360.0) }),
    ARC("arc", { p -> ring(p.radius, p.points, 0.0, if (p.angle == 360.0) 180.0 else p.angle) }),
    SPHERE("sphere", { p -> fibonacciSphere(p.radius, p.points, 1.0) }),
    HEMISPHERE("hemisphere", { p -> fibonacciSphere(p.radius, p.points, 0.0) }),
    CYLINDER("cylinder", { p ->
        val rings = max(2, (p.points / 16))
        (0 until rings).flatMap { i -> ring(p.radius, p.points / rings, p.height * i / (rings - 1), 360.0) }
    }),
    CONE("cone", { p ->
        val rings = max(2, p.points / 12)
        (0 until rings).flatMap { i ->
            val f = i / (rings - 1.0)
            ring(p.radius * (1 - f), max(1, ((p.points / rings) * (1 - f)).roundToInt()), p.height * f, 360.0)
        }
    }),
    SPIRAL("spiral", { p ->
        (0 until p.points).map { i ->
            val f = i / max(1.0, p.points - 1.0)
            val a = f * p.turns * 2 * PI
            Vec3(cos(a) * p.radius * f, 0.0, sin(a) * p.radius * f)
        }
    }),
    HELIX("helix", { p -> helix(p, 0.0) }),
    DOUBLE_HELIX("double-helix", { p -> helix(p.copy(points = p.points / 2), 0.0) + helix(p.copy(points = p.points / 2), PI) }),
    VORTEX("vortex", { p ->
        (0 until p.points).map { i ->
            val f = i / max(1.0, p.points - 1.0)
            val a = f * p.turns * 2 * PI
            val r = p.radius * (0.15 + 0.85 * f)
            Vec3(cos(a) * r, p.height * f, sin(a) * r)
        }
    }),
    LINE("line", { p -> (0 until p.points).map { i -> Vec3(0.0, 0.0, p.radius * i / max(1.0, p.points - 1.0)) } }),
    WAVE("wave", { p ->
        (0 until p.points).map { i ->
            val f = i / max(1.0, p.points - 1.0)
            Vec3((f - 0.5) * 2 * p.radius, sin(f * p.turns * 2 * PI) * p.height * 0.25, 0.0)
        }
    }),
    TORUS("torus", { p ->
        val major = max(3, sqrt(p.points.toDouble() * 2).roundToInt())
        val minor = max(3, p.points / major)
        val tube = p.radius * 0.3
        (0 until major).flatMap { i ->
            val u = i * 2 * PI / major
            (0 until minor).map { j ->
                val v = j * 2 * PI / minor
                Vec3((p.radius + tube * cos(v)) * cos(u), tube * sin(v), (p.radius + tube * cos(v)) * sin(u))
            }
        }
    }),
    POLYGON("polygon", { p -> polygon(p.radius, p.sides, p.points, 0.0) }),
    STAR("star", { p ->
        val n = p.sides
        val verts =
            (0 until n * 2).map { i ->
                val r = if (i % 2 == 0) p.radius else p.radius * 0.45
                val a = i * PI / n - PI / 2
                Vec3(cos(a) * r, 0.0, sin(a) * r)
            }
        outline(verts, p.points)
    }),
    HEART("heart", { p ->
        (0 until p.points).map { i ->
            val t = i * 2 * PI / p.points
            val x = 16 * sin(t).pow(3)
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            Vec3(x / 16.0 * p.radius, y / 16.0 * p.radius, 0.0)
        }
    }),
    CUBE("cube", { p ->
        val r = p.radius
        val c = listOf(-r, r)
        val corners = c.flatMap { x -> c.flatMap { y -> c.map { z -> Vec3(x, y, z) } } }
        val edges = corners.flatMap { a -> corners.filter { b -> (a - b).length().let { abs(it - 2 * r) < 1e-6 } }.map { a to it } }
        val per = max(2, p.points / 12)
        edges.flatMap { (a, b) -> (0 until per).map { i -> a.lerp(b, i / (per - 1.0)) } }.distinct()
    }),
    ;

    fun generate(params: ShapeParams): List<Vec3> = gen(params)

    companion object {
        private val lookup = entries.associateBy { it.id }

        fun byName(name: String): Shape? = lookup[
            name
                .trim()
                .lowercase()
                .replace('_', '-')
                .replace(' ', '-'),
        ]

        fun ids() = entries.map { it.id }

        fun ring(radius: Double, points: Int, y: Double, degrees: Double): List<Vec3> {
            val n = max(1, points)
            val full = abs(degrees) >= 360.0
            val step = degrees * PI / 180.0 / (if (full) n else max(1, n - 1))
            return (0 until n).map { i -> Vec3(cos(i * step) * radius, y, sin(i * step) * radius) }
        }

        fun fibonacciSphere(radius: Double, points: Int, lowerExtent: Double): List<Vec3> {
            val n = max(1, points)
            val golden = PI * (3 - sqrt(5.0))
            return (0 until n).map { i ->
                // y from 1 down to -lowerExtent
                val y = 1 - (i / max(1.0, n - 1.0)) * (1 + lowerExtent)
                val r = sqrt(max(0.0, 1 - y * y))
                val theta = golden * i
                Vec3(cos(theta) * r * radius, y * radius, sin(theta) * r * radius)
            }
        }

        fun helix(p: ShapeParams, phase: Double): List<Vec3> = (0 until max(1, p.points)).map { i ->
            val f = i / max(1.0, p.points - 1.0)
            val a = f * p.turns * 2 * PI + phase
            Vec3(cos(a) * p.radius, p.height * f, sin(a) * p.radius)
        }

        fun polygon(radius: Double, sides: Int, points: Int, y: Double): List<Vec3> {
            val verts = (0 until sides).map { i -> (i * 2 * PI / sides - PI / 2).let { Vec3(cos(it) * radius, y, sin(it) * radius) } }
            return outline(verts, points)
        }

        /** Distributes [points] evenly along a closed polyline. */
        fun outline(verts: List<Vec3>, points: Int): List<Vec3> {
            val per = max(1, points / verts.size)
            return verts.indices.flatMap { i ->
                val a = verts[i]
                val b = verts[(i + 1) % verts.size]
                (0 until per).map { j -> a.lerp(b, j / per.toDouble()) }
            }
        }

        /** Straight line between two absolute points with [spacing] blocks between particles. */
        fun line(from: Vec3, to: Vec3, spacing: Double): List<Vec3> {
            val len = from.distance(to)
            val n = min(4096, max(2, (len / max(0.05, spacing)).roundToInt() + 1))
            return (0 until n).map { i -> from.lerp(to, i / (n - 1.0)) }
        }

        /** Jagged lightning path with deterministic jitter from [seed]. */
        fun lightning(from: Vec3, to: Vec3, segments: Int, jitter: Double, seed: Long): List<Vec3> {
            val rnd = java.util.Random(seed)
            val n = segments.coerceIn(2, 64)
            val joints =
                (0..n).map { i ->
                    val base = from.lerp(to, i / n.toDouble())
                    if (i == 0 || i == n) {
                        base
                    } else {
                        base + Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.5, rnd.nextGaussian()) * jitter
                    }
                }
            return joints.zipWithNext().flatMap { (a, b) -> line(a, b, 0.25) }
        }
    }
}

/** Small bounded cache for generated shapes, keyed by shape + parameters. Main-thread only. */
class ShapeCache(private val capacity: Int = 256) {
    private val map =
        object : LinkedHashMap<Pair<Shape, ShapeParams>, List<Vec3>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Shape, ShapeParams>, List<Vec3>>?) = size > capacity
        }

    var hits = 0L
        private set
    var misses = 0L
        private set

    fun get(shape: Shape, params: ShapeParams): List<Vec3> {
        val key = shape to params
        map[key]?.let {
            hits++
            return it
        }
        misses++
        return shape.generate(params).also { map[key] = it }
    }

    fun size() = map.size
}
