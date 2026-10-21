package dev.dheaven.skcine.platform

import dev.dheaven.skcine.core.timeline.TimelineLimits

/** Cosmetic quality level. Only affects particle density/update rates, never gameplay events. */
enum class QualityMode(val density: Double) {
    LOW(0.35),
    MEDIUM(0.65),
    HIGH(1.0),
    CINEMATIC(1.25),
    AUTO(1.0),
    ;

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: AUTO
    }
}

/** What happens to playing scenes when the script that defined them is unloaded or reloaded. */
enum class ReloadPolicy {
    /** End them (viewers are restored) — the safe default. */
    STOP,

    /** Let them finish with the previously compiled code. */
    KEEP,
    ;

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: STOP
    }
}

/** Global technical settings from config.yml. Scene content never lives here. */
data class SkCineSettings(
    val debug: Boolean = false,
    val quality: QualityMode = QualityMode.AUTO,
    val reloadPolicy: ReloadPolicy = ReloadPolicy.STOP,
    val recoveryEnabled: Boolean = true,
    val endSceneOnError: Boolean = false,
    val sneakToSkip: Boolean = true,
    // Camera
    val cameraSmoothingTicks: Int = 2,
    val cameraAttachDelayTicks: Int = 2,
    // Budgets
    val maxInstances: Int = 200,
    val maxViewersPerInstance: Int = 100,
    val maxActors: Int = 1000,
    val maxActorsPerScene: Int = 128,
    val maxEffects: Int = 300,
    val maxEffectsPerScene: Int = 48,
    val maxAnimations: Int = 2000,
    val maxDialogues: Int = 500,
    val maxChoices: Int = 200,
    val maxNestedSequences: Int = 8,
    val maxParticlesPerPlayerPerTick: Int = 600,
    val particleViewDistance: Double = 96.0,
    // Timeline
    val maxSceneDurationSeconds: Double = 1800.0,
    val maxEventsPerScene: Int = 2000,
    val maxEventsPerTick: Int = 64,
    val maxSpeed: Double = 10.0,
    // Text
    val titleUpdateInterval: Int = 2,
    val actionBarUpdateInterval: Int = 2,
    val bossBarUpdateInterval: Int = 2,
    val typewriterSoundInterval: Int = 2,
) {
    val timelineLimits: TimelineLimits
        get() =
            TimelineLimits(
                maxDurationTicks = maxSceneDurationSeconds * 20.0,
                maxEvents = maxEventsPerScene,
                maxEventsPerTick = maxEventsPerTick,
                maxSpeed = maxSpeed,
            )
}
