package dev.dheaven.skcine.core.interpolation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An easing curve mapping normalized time `t ∈ [0, 1]` to progress.
 *
 * Every easing satisfies `f(0) = 0` and `f(1) = 1`. Back and elastic families intentionally overshoot
 * outside `[0, 1]` between the endpoints; [overshoots] reports this so callers that need bounded output
 * (for example display scale) can clamp.
 */
class Easing private constructor(val id: String, val overshoots: Boolean, private val fn: (Double) -> Double) {
    /** Evaluates the curve. Input is clamped to `[0, 1]`; NaN input is treated as 0. */
    fun apply(t: Double): Double {
        val c = if (t.isNaN()) 0.0 else t.coerceIn(0.0, 1.0)
        if (c <= 0.0) return 0.0
        if (c >= 1.0) return 1.0
        return fn(c)
    }

    override fun toString() = id

    override fun equals(other: Any?) = other is Easing && other.id == id

    override fun hashCode() = id.hashCode()

    companion object {
        private const val C1 = 1.70158
        private const val C2 = C1 * 1.525
        private const val C3 = C1 + 1.0
        private const val C4 = (2 * PI) / 3
        private const val C5 = (2 * PI) / 4.5

        private val registry = LinkedHashMap<String, Easing>()
        private val aliases = HashMap<String, String>()

        val LINEAR = register("linear") { it }
        val SMOOTHSTEP = register("smoothstep") { it * it * (3 - 2 * it) }
        val SMOOTHERSTEP = register("smootherstep") { it * it * it * (it * (it * 6 - 15) + 10) }

        init {
            family("sine", false, { 1 - cos(it * PI / 2) }, { sin(it * PI / 2) }, { -(cos(PI * it) - 1) / 2 })
            power("quad", 2.0)
            power("cubic", 3.0)
            power("quart", 4.0)
            power("quint", 5.0)
            family(
                "expo",
                false,
                { 2.0.pow(10 * it - 10) },
                { 1 - 2.0.pow(-10 * it) },
                { if (it < 0.5) 2.0.pow(20 * it - 10) / 2 else (2 - 2.0.pow(-20 * it + 10)) / 2 },
            )
            family(
                "circ",
                false,
                { 1 - sqrt(1 - it * it) },
                { sqrt(1 - (it - 1) * (it - 1)) },
                { if (it < 0.5) (1 - sqrt(1 - (2 * it).pow(2))) / 2 else (sqrt(1 - (-2 * it + 2).pow(2)) + 1) / 2 },
            )
            family(
                "back",
                true,
                { C3 * it * it * it - C1 * it * it },
                { 1 + C3 * (it - 1).pow(3) + C1 * (it - 1).pow(2) },
                {
                    if (it < 0.5) {
                        ((2 * it).pow(2) * ((C2 + 1) * 2 * it - C2)) / 2
                    } else {
                        ((2 * it - 2).pow(2) * ((C2 + 1) * (it * 2 - 2) + C2) + 2) / 2
                    }
                },
            )
            family(
                "elastic",
                true,
                { -(2.0.pow(10 * it - 10)) * sin((it * 10 - 10.75) * C4) },
                { 2.0.pow(-10 * it) * sin((it * 10 - 0.75) * C4) + 1 },
                {
                    if (it < 0.5) {
                        -(2.0.pow(20 * it - 10) * sin((20 * it - 11.125) * C5)) / 2
                    } else {
                        (2.0.pow(-20 * it + 10) * sin((20 * it - 11.125) * C5)) / 2 + 1
                    }
                },
            )
            family("bounce", false, { 1 - bounceOut(1 - it) }, ::bounceOut, {
                if (it < 0.5) (1 - bounceOut(1 - 2 * it)) / 2 else (1 + bounceOut(2 * it - 1)) / 2
            })
            // Generic names map to the cubic family, the most common cinematic choice.
            alias("ease-in", "ease-in-cubic")
            alias("ease-out", "ease-out-cubic")
            alias("ease-in-out", "ease-in-out-cubic")
            alias("ease", "ease-in-out-cubic")
            alias("smooth", "smoothstep")
            alias("smoother", "smootherstep")
            alias("none", "linear")
        }

        private fun bounceOut(t: Double): Double {
            val n1 = 7.5625
            val d1 = 2.75
            return when {
                t < 1 / d1 -> n1 * t * t
                t < 2 / d1 -> {
                    val x = t - 1.5 / d1
                    n1 * x * x + 0.75
                }
                t < 2.5 / d1 -> {
                    val x = t - 2.25 / d1
                    n1 * x * x + 0.9375
                }
                else -> {
                    val x = t - 2.625 / d1
                    n1 * x * x + 0.984375
                }
            }
        }

        private fun power(name: String, p: Double) = family(
            name,
            false,
            { it.pow(p) },
            { 1 - (1 - it).pow(p) },
            { if (it < 0.5) 2.0.pow(p - 1) * it.pow(p) else 1 - (-2 * it + 2).pow(p) / 2 },
        )

        private fun family(name: String, overshoots: Boolean, easeIn: (Double) -> Double, easeOut: (Double) -> Double, easeInOut: (Double) -> Double) {
            register("ease-in-$name", overshoots, easeIn)
            register("ease-out-$name", overshoots, easeOut)
            register("ease-in-out-$name", overshoots, easeInOut)
            alias("$name-in", "ease-in-$name")
            alias("$name-out", "ease-out-$name")
            alias("$name-in-out", "ease-in-out-$name")
            alias("in-$name", "ease-in-$name")
            alias("out-$name", "ease-out-$name")
            alias("in-out-$name", "ease-in-out-$name")
        }

        private fun register(id: String, overshoots: Boolean = false, fn: (Double) -> Double): Easing {
            val e = Easing(id, overshoots, fn)
            registry[id] = e
            return e
        }

        private fun alias(alias: String, target: String) {
            aliases[alias] = target
        }

        /** Normalizes user input: case-insensitive, accepts spaces, underscores and camel case separators. */
        fun normalize(name: String): String = name
            .trim()
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
            .replace('_', '-')
            .replace(' ', '-')
            .replace(Regex("-+"), "-")

        /** Looks up an easing by id or alias; returns null if unknown. */
        fun byName(name: String): Easing? {
            val n = normalize(name)
            return registry[n] ?: aliases[n]?.let { registry[it] }
        }

        /** All canonical easing ids, in registration order. */
        fun ids(): List<String> = registry.keys.toList()
    }
}
