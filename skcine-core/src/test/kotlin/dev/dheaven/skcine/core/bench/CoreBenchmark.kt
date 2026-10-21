package dev.dheaven.skcine.core.bench

import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.OrbitShot
import dev.dheaven.skcine.core.path.PoseKeyframe
import dev.dheaven.skcine.core.path.ShakeModifier
import dev.dheaven.skcine.core.path.TargetRef
import dev.dheaven.skcine.core.timeline.AdvanceResult
import dev.dheaven.skcine.core.timeline.EventPolicy
import dev.dheaven.skcine.core.timeline.Timeline
import dev.dheaven.skcine.core.timeline.TimelineListener
import dev.dheaven.skcine.core.timeline.TimelinePlayer
import java.lang.management.ManagementFactory

/**
 * Engine micro-benchmark: per-tick cost of advancing timelines and evaluating cameras, independent of the server.
 * Each simulated scene is 30 s with 12 camera keyframes, an orbit shot, a shake, 60 one-shot events, 4 repeaters
 * and 5 markers, i.e. a busy real-world cutscene. Run with: gradlew :skcine-core:benchmark
 */
object CoreBenchmark {
    private fun scene(seed: Int): TimelinePlayer {
        val t = Timeline()
        t.explicitDuration = 600.0
        for (i in 0 until 12) {
            val a = i * 0.5 + seed
            t.camera.addKeyframe(PoseKeyframe(i * 50.0, Vec3(Math.cos(a) * 20, 70.0 + i, Math.sin(a) * 20), i * 30.0, 10.0, Easing.byName("ease-in-out-sine")!!))
        }
        t.camera.addShot(OrbitShot(TargetRef.fixed(Vec3(0.0, 64.0, 0.0)), 8.0, 3.0, 0.0, 360.0, 200.0, 300.0, blendIn = 20.0))
        t.camera.addModifier(ShakeModifier(320.0, 360.0, 1.0, seed = seed.toLong()))
        var sink = 0
        for (i in 0 until 60) t.addEvent(i * 10.0, EventPolicy.ONCE, "e$i") { sink++ }
        for (i in 0 until 4) t.addRepeater(i * 5.0, 5.0, 500.0, "r$i") { sink++ }
        for (i in 0 until 5) t.addMarker("m$i", i * 100.0)
        return TimelinePlayer(t)
    }

    private val listener = object : TimelineListener {}

    @JvmStatic
    fun main(args: Array<String>) {
        val counts = listOf(1, 10, 50, 100, 500)
        val ticks = 600
        val runtime = ManagementFactory.getRuntimeMXBean()
        println("SkCine core benchmark — JVM ${runtime.vmName} ${runtime.vmVersion}, ${Runtime.getRuntime().availableProcessors()} cores")
        // Warm-up so the JIT compiles the hot paths.
        repeat(3) { run(200, ticks) }
        println("| scenes | ticks | avg µs/tick (all scenes) | µs per scene-tick | p99 µs/tick |")
        println("|---|---|---|---|---|")
        for (n in counts) {
            val samples = run(n, ticks)
            samples.sort()
            val avg = samples.average() / 1000.0
            val p99 = samples[(samples.size * 0.99).toInt().coerceAtMost(samples.size - 1)] / 1000.0
            println("| $n | $ticks | %.1f | %.2f | %.1f |".format(avg, avg / n, p99))
        }
    }

    private fun run(n: Int, ticks: Int): LongArray {
        val players = List(n) { scene(it) }
        val samples = LongArray(ticks)
        var poseSink = 0.0
        for (tick in 0 until ticks) {
            val start = System.nanoTime()
            for (p in players) {
                if (p.advance(listener) == AdvanceResult.RUNNING || tick == 0) {
                    p.camera().evaluate(p.position)?.let { poseSink += it.position.x }
                }
            }
            samples[tick] = System.nanoTime() - start
        }
        if (poseSink == 42.4242) println()
        return samples
    }
}
