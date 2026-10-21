package dev.dheaven.skcine.platform.profiler

/** Tick timing ring buffer and per-second counters. Main-thread only. */
class Profiler(private val capacity: Int = 400) {
    private val samples = LongArray(capacity)
    private var count = 0
    private var cursor = 0

    val particlesSent = Counter()
    val particlesDropped = Counter()
    val cameraUpdates = Counter()
    val textUpdates = Counter()
    var cleanupFailures = 0L

    fun record(nanos: Long) {
        samples[cursor] = nanos
        cursor = (cursor + 1) % capacity
        if (count < capacity) count++
    }

    fun sampleCount() = count

    fun stats(): Stats {
        if (count == 0) return Stats(0.0, 0.0, 0.0, 0.0, 0.0)
        val sorted = samples.copyOf(count).also { it.sort() }

        fun pct(p: Double) = sorted[((count - 1) * p).toInt()] / 1000.0
        return Stats(sorted.average() / 1000.0, sorted.last() / 1000.0, pct(0.5), pct(0.95), pct(0.99))
    }

    fun reset() {
        count = 0
        cursor = 0
    }

    /** Called once per tick to roll the per-second windows. */
    fun tick(serverTick: Long) {
        particlesSent.roll(serverTick)
        particlesDropped.roll(serverTick)
        cameraUpdates.roll(serverTick)
        textUpdates.roll(serverTick)
    }

    data class Stats(val avg: Double, val max: Double, val p50: Double, val p95: Double, val p99: Double)

    /** Counts events and exposes the total of the last completed 20-tick window. */
    class Counter {
        private var current = 0L
        var lastSecond = 0L
            private set
        private var windowStart = 0L

        fun add(n: Long = 1) {
            current += n
        }

        fun roll(tick: Long) {
            if (tick - windowStart >= 20) {
                lastSecond = current
                current = 0
                windowStart = tick
            }
        }
    }
}
