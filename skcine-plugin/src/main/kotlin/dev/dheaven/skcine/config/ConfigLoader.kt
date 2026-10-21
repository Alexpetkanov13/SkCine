package dev.dheaven.skcine.config

import dev.dheaven.skcine.platform.QualityMode
import dev.dheaven.skcine.platform.ReloadPolicy
import dev.dheaven.skcine.platform.SkCineSettings
import org.bukkit.plugin.java.JavaPlugin

/** Reads technical settings from config.yml with bounds checking. Scene content never lives in config.yml. */
object ConfigLoader {
    fun load(plugin: JavaPlugin): SkCineSettings {
        val c = plugin.config
        val d = SkCineSettings()

        fun int(path: String, def: Int, min: Int, max: Int): Int {
            val v = c.getInt(path, def)
            if (v !in min..max) plugin.logger.warning("config.yml: $path=$v is outside $min..$max; using ${v.coerceIn(min, max)}")
            return v.coerceIn(min, max)
        }

        fun dbl(path: String, def: Double, min: Double, max: Double): Double {
            val v = c.getDouble(path, def)
            if (!v.isFinite() || v < min || v > max) plugin.logger.warning("config.yml: $path=$v is outside $min..$max; clamping")
            return if (v.isFinite()) v.coerceIn(min, max) else def
        }
        return SkCineSettings(
            debug = c.getBoolean("debug", d.debug),
            quality = QualityMode.parse(c.getString("performance.quality")),
            reloadPolicy = ReloadPolicy.parse(c.getString("scripts.reload-policy")),
            recoveryEnabled = c.getBoolean("safety.recovery-files", d.recoveryEnabled),
            endSceneOnError = c.getBoolean("safety.end-scene-on-error", d.endSceneOnError),
            sneakToSkip = c.getBoolean("input.sneak-to-skip", d.sneakToSkip),
            cameraSmoothingTicks = int("camera.smoothing-ticks", d.cameraSmoothingTicks, 0, 10),
            cameraAttachDelayTicks = int("camera.attach-delay-ticks", d.cameraAttachDelayTicks, 1, 20),
            maxInstances = int("limits.max-playing-scenes", d.maxInstances, 1, 10_000),
            maxViewersPerInstance = int("limits.max-viewers-per-scene", d.maxViewersPerInstance, 1, 1000),
            maxActors = int("limits.max-actors", d.maxActors, 0, 20_000),
            maxActorsPerScene = int("limits.max-actors-per-scene", d.maxActorsPerScene, 0, 2000),
            maxEffects = int("limits.max-effects", d.maxEffects, 0, 10_000),
            maxEffectsPerScene = int("limits.max-effects-per-scene", d.maxEffectsPerScene, 0, 1000),
            maxAnimations = int("limits.max-animations", d.maxAnimations, 0, 50_000),
            maxDialogues = int("limits.max-dialogue-lines", d.maxDialogues, 0, 10_000),
            maxChoices = int("limits.max-active-choices", d.maxChoices, 0, 10_000),
            maxNestedSequences = int("limits.max-nested-sequences", d.maxNestedSequences, 1, 64),
            maxParticlesPerPlayerPerTick = int("performance.max-particles-per-player-per-tick", d.maxParticlesPerPlayerPerTick, 0, 20_000),
            particleViewDistance = dbl("performance.particle-view-distance", d.particleViewDistance, 8.0, 512.0),
            maxSceneDurationSeconds = dbl("limits.max-scene-duration-seconds", d.maxSceneDurationSeconds, 1.0, 86_400.0),
            maxEventsPerScene = int("limits.max-timeline-events", d.maxEventsPerScene, 1, 100_000),
            maxEventsPerTick = int("limits.max-events-per-tick", d.maxEventsPerTick, 1, 10_000),
            maxSpeed = dbl("limits.max-speed", d.maxSpeed, 1.0, 100.0),
            titleUpdateInterval = int("dialogue.title-update-interval", d.titleUpdateInterval, 1, 20),
            actionBarUpdateInterval = int("dialogue.action-bar-update-interval", d.actionBarUpdateInterval, 1, 20),
            bossBarUpdateInterval = int("dialogue.boss-bar-update-interval", d.bossBarUpdateInterval, 1, 20),
            typewriterSoundInterval = int("dialogue.typing-sound-interval", d.typewriterSoundInterval, 1, 20),
        )
    }
}
