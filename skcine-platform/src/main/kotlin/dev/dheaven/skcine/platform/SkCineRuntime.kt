package dev.dheaven.skcine.platform

import dev.dheaven.skcine.api.SkCineAPI
import dev.dheaven.skcine.api.capability.Capability
import dev.dheaven.skcine.api.profiler.ProfilerService
import dev.dheaven.skcine.api.profiler.ProfilerSnapshot
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.actor.ActorManager
import dev.dheaven.skcine.platform.actor.Animator
import dev.dheaven.skcine.platform.camera.CameraManager
import dev.dheaven.skcine.platform.compatibility.CapabilityRegistry
import dev.dheaven.skcine.platform.dialogue.ChoiceService
import dev.dheaven.skcine.platform.dialogue.DialogueService
import dev.dheaven.skcine.platform.effect.EffectService
import dev.dheaven.skcine.platform.effect.ScreenOverlayService
import dev.dheaven.skcine.platform.player.PlayerStateService
import dev.dheaven.skcine.platform.profiler.Profiler
import dev.dheaven.skcine.platform.scene.SceneManager
import dev.dheaven.skcine.platform.scene.SceneOwner
import dev.dheaven.skcine.platform.scene.SceneRegistryImpl
import dev.dheaven.skcine.platform.sound.SoundService
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * Service container and centralized scheduler. Exactly one exists while the plugin is enabled; Skript syntax reaches
 * it through [SkCineRuntime.get].
 *
 * The scheduler is a single repeating task that runs only while there is work, with fixed phases per tick:
 * scenes (clocks, timeline events, cameras) → animations → effects → dialogue → choices → deferred cleanup.
 */
class SkCineRuntime(val plugin: Plugin, settings: SkCineSettings) :
    SkCineAPI,
    ProfilerService {
    @Volatile
    var settings: SkCineSettings = settings
        private set

    override val version: String = plugin.pluginMeta.version
    val diagnostics = Diagnostics(plugin.logger, settings.debug)
    override val capabilities = CapabilityRegistry()
    val profiler = Profiler()
    override val profiling: ProfilerService get() = this
    override val scenes = SceneRegistryImpl()
    val registry get() = scenes
    val states = PlayerStateService(plugin.dataFolder, diagnostics, settings.recoveryEnabled)
    val camera = CameraManager(plugin, { this.settings }, states, capabilities, diagnostics, profiler)
    val actors = ActorManager(plugin, { this.settings }, diagnostics, profiler)
    val animator = Animator({ this.settings }, diagnostics) { id -> actors.all().firstOrNull { it.entityId == id }?.name }
    override val effects = EffectService({ this.settings }, diagnostics, profiler)
    val sounds = SoundService(diagnostics)
    val overlays = ScreenOverlayService(plugin, { this.settings }, camera)
    val dialogue = DialogueService({ this.settings }, diagnostics, profiler)
    val choices = ChoiceService(plugin, { this.settings }, diagnostics, capabilities.has(Capability.CLICK_CALLBACKS))
    val manager = SceneManager(this)
    override val runtime get() = manager

    var serverTick = 0L
        private set
    private var task: BukkitTask? = null
    private var idleTicks = 0

    init {
        actors.onRemoved = { animator.cancel(it.entityId) }
        choices.onResolved = { c -> c.owner?.let { manager.onChoiceResolved(it) } }
    }

    fun updateSettings(new: SkCineSettings) {
        settings = new
        diagnostics.debugEnabled = new.debug
    }

    /** Starts the scheduler if it is not running. Cheap to call often. */
    fun ensureTicking() {
        idleTicks = 0
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
    }

    private fun hasWork() = manager.activeCount > 0 || animator.count > 0 || effects.count > 0 || dialogue.count > 0 || choices.count > 0 || overlays.count > 0

    private fun tick() {
        val start = System.nanoTime()
        serverTick++
        profiler.tick(serverTick)
        manager.tick()
        overlays.tick()
        animator.tick()
        if (serverTick % 20 == 0L) actors.tick()
        effects.tick()
        dialogue.tick()
        choices.tick()
        profiler.record(System.nanoTime() - start)
        if (hasWork()) {
            idleTicks = 0
        } else if (++idleTicks > 100) {
            // Zero idle overhead: stop until new work arrives.
            task?.cancel()
            task = null
        }
    }

    val schedulerRunning get() = task != null

    /** Handles a script unload/reload according to the reload policy. */
    fun onScriptUnloaded(owner: SceneOwner) {
        val removed = scenes.unregisterOwner(owner)
        if (settings.reloadPolicy == ReloadPolicy.STOP) {
            val ended = manager.endOwnedBy(owner, EndReason.SCRIPT_UNLOADED)
            if (ended > 0) diagnostics.info("Stopped $ended playing scene(s) because ${owner.displayName} was unloaded.")
        }
        diagnostics.debug { "unregistered ${removed.size} scene(s) from ${owner.displayName}" }
    }

    /** Ends everything and releases all resources (plugin disable). */
    fun shutdown() {
        runCatching { manager.endAll(EndReason.PLUGIN_DISABLED) }.onFailure { diagnostics.error("Error while stopping scenes", cause = it) }
        runCatching { animator.cancelAll() }
        runCatching { effects.stopAll() }
        runCatching { dialogue.finishAll() }
        runCatching { choices.cancelAll() }
        runCatching { actors.removeAll() }
        runCatching { overlays.removeAll() }
        runCatching { camera.disposeAll() }
        runCatching { sounds.clear() }
        task?.cancel()
        task = null
        states.shutdown()
        scenes.clear()
    }

    override fun snapshot(): ProfilerSnapshot {
        val st = profiler.stats()
        return ProfilerSnapshot(
            activeScenes = manager.activeCount,
            activeCameras = camera.activeCameraCount,
            activeActors = actors.count,
            activeEffects = effects.count,
            activeAnimations = animator.count,
            activeDialogues = dialogue.count,
            sampledTicks = profiler.sampleCount(),
            averageTickMicros = st.avg,
            maxTickMicros = st.max,
            p50TickMicros = st.p50,
            p95TickMicros = st.p95,
            p99TickMicros = st.p99,
            particlesSentLastSecond = profiler.particlesSent.lastSecond,
            particlesDroppedLastSecond = profiler.particlesDropped.lastSecond,
            cameraUpdatesLastSecond = profiler.cameraUpdates.lastSecond,
            textUpdatesLastSecond = profiler.textUpdates.lastSecond,
            cleanupFailures = profiler.cleanupFailures,
            schedulerRunning = schedulerRunning,
        )
    }

    companion object {
        @Volatile
        private var current: SkCineRuntime? = null

        /** The active runtime. Throws if SkCine is not enabled (syntax only runs while it is). */
        fun get(): SkCineRuntime = current ?: throw IllegalStateException("SkCine is not enabled")

        fun getOrNull(): SkCineRuntime? = current

        fun install(rt: SkCineRuntime?) {
            current = rt
            SkCineAPI.install(rt)
        }
    }
}
