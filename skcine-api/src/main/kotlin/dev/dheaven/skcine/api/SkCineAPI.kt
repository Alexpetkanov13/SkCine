package dev.dheaven.skcine.api

import dev.dheaven.skcine.api.capability.CapabilityService
import dev.dheaven.skcine.api.effect.EffectRegistry
import dev.dheaven.skcine.api.profiler.ProfilerService
import dev.dheaven.skcine.api.scene.SceneRegistry
import dev.dheaven.skcine.api.scene.SceneRuntime

/**
 * Entry point for other plugins.
 *
 * Scenes are authored in Skript; this API exists for integrations (querying/controlling playback, registering
 * custom VFX presets, reading diagnostics). Obtain it with [SkCineAPI.get] after SkCine has enabled.
 */
interface SkCineAPI {
    val version: String
    val scenes: SceneRegistry
    val runtime: SceneRuntime
    val capabilities: CapabilityService
    val effects: EffectRegistry
    val profiling: ProfilerService

    companion object {
        @Volatile
        private var instance: SkCineAPI? = null

        /** @throws IllegalStateException if SkCine is not enabled. */
        @JvmStatic
        fun get(): SkCineAPI = instance ?: throw IllegalStateException("SkCine is not enabled")

        @JvmStatic
        fun getOrNull(): SkCineAPI? = instance

        /** Internal: called by the SkCine plugin on enable/disable. */
        @JvmStatic
        fun install(api: SkCineAPI?) {
            instance = api
        }
    }
}
