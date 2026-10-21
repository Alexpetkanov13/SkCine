package dev.dheaven.skcine.api.profiler

/** Snapshot of engine load. Times are in microseconds. */
data class ProfilerSnapshot(
    val activeScenes: Int,
    val activeCameras: Int,
    val activeActors: Int,
    val activeEffects: Int,
    val activeAnimations: Int,
    val activeDialogues: Int,
    val sampledTicks: Int,
    val averageTickMicros: Double,
    val maxTickMicros: Double,
    val p50TickMicros: Double,
    val p95TickMicros: Double,
    val p99TickMicros: Double,
    val particlesSentLastSecond: Long,
    val particlesDroppedLastSecond: Long,
    val cameraUpdatesLastSecond: Long,
    val textUpdatesLastSecond: Long,
    val cleanupFailures: Long,
    val schedulerRunning: Boolean,
)

interface ProfilerService {
    fun snapshot(): ProfilerSnapshot
}
