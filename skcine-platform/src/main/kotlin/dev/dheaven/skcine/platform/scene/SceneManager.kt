package dev.dheaven.skcine.platform.scene

import dev.dheaven.skcine.api.event.CineMarkerEvent
import dev.dheaven.skcine.api.event.CineSceneEvent
import dev.dheaven.skcine.api.event.ScenePhase
import dev.dheaven.skcine.api.scene.SceneHandle
import dev.dheaven.skcine.api.scene.SceneRuntime
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.SceneState
import dev.dheaven.skcine.core.timeline.AdvanceResult
import dev.dheaven.skcine.core.timeline.FireReason
import dev.dheaven.skcine.core.timeline.Marker
import dev.dheaven.skcine.core.timeline.Repeater
import dev.dheaven.skcine.core.timeline.TimedEvent
import dev.dheaven.skcine.core.timeline.TimelineListener
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.SkCineRuntime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Owns all scene instances: creation, the per-tick clock, lifecycle transitions, viewer membership and cleanup.
 * Every method must run on the server main thread.
 */
class SceneManager(private val rt: SkCineRuntime) : SceneRuntime {
    private val instances = LinkedHashMap<UUID, SceneInstance>()
    private val byPlayer = HashMap<UUID, SceneInstance>()
    private val cameraActive = HashSet<UUID>()
    private val diagnostics get() = rt.diagnostics

    /** The most recently started instance, for `last started cine scene`. */
    var lastStarted: SceneInstance? = null
        private set

    val activeCount get() = instances.size

    override fun active(): Collection<SceneInstance> = instances.values.toList()

    override fun current(player: Player): SceneInstance? = byPlayer[player.uniqueId]

    fun isWatching(player: Player) = byPlayer.containsKey(player.uniqueId)

    override fun play(sceneId: String, players: Collection<Player>): List<SceneHandle> {
        val def = rt.registry.get(sceneId) ?: return emptyList()
        return play(def, players, together = false) { diagnostics.error(it) }
    }

    /**
     * Starts [definition] for [players]: one shared instance if [together], otherwise one instance per player.
     * Errors (offline players, budgets, build failures) are reported through [error].
     */
    fun play(definition: SceneDefinition, players: Collection<Player>, together: Boolean, error: (String) -> Unit): List<SceneInstance> {
        val online = players.filter { it.isOnline }.distinct()
        if (online.isEmpty()) {
            error(Diagnostics.scene(definition.id, definition.sourceScript, "could not start: no online viewers were given"))
            return emptyList()
        }
        val s = rt.settings
        val groups: List<List<Player>> = if (together) listOf(online) else online.map { listOf(it) }
        if (instances.size + groups.size > s.maxInstances) {
            error(
                Diagnostics.scene(
                    definition.id,
                    definition.sourceScript,
                    "could not start: the limit of ${s.maxInstances} simultaneous scenes is reached",
                ),
            )
            return emptyList()
        }
        if (together && online.size > s.maxViewersPerInstance) {
            error(
                Diagnostics.scene(definition.id, definition.sourceScript, "could not start: more than ${s.maxViewersPerInstance} viewers"),
            )
            return emptyList()
        }
        return groups.mapNotNull { start(definition, it, together, error) }
    }

    private fun start(definition: SceneDefinition, viewers: List<Player>, group: Boolean, error: (String) -> Unit): SceneInstance? {
        val inst = SceneInstance(this, definition, viewers, group, rt.settings.timelineLimits)
        inst.settings = definition.settings
        instances[inst.uniqueId] = inst
        val sm = inst.stateMachine
        sm.transition(SceneState.PREPARING)
        try {
            inst.settings.durationTicks?.let { inst.timeline.explicitDuration = it.coerceAtMost(inst.timeline.limits.maxDurationTicks) }
            inst.player.speed = inst.settings.speed
            inst.player.loops = inst.settings.loops
            fire(inst, ScenePhase.PREPARE)
            for (step in definition.steps) {
                if (!inst.state.isAlive) break
                step.build(inst)
            }
        } catch (t: Throwable) {
            error(
                Diagnostics.scene(
                    definition.id,
                    definition.sourceScript,
                    "failed while building its timeline: ${t.message ?: t.javaClass.simpleName}",
                ),
            )
            diagnostics.debug { t.stackTraceToString() }
            end(inst, EndReason.ERROR)
            return null
        }
        if (!inst.state.isAlive) return null
        sm.transition(SceneState.READY)
        // Claim viewers: a viewer can only watch one scene at a time.
        for (p in viewers) {
            byPlayer[p.uniqueId]?.takeIf { it !== inst }?.let { old -> removeViewer(old, p, EndReason.REPLACED, restore = false) }
            byPlayer[p.uniqueId] = inst
            rt.states.capture(p, definition.id)
        }
        sm.transition(SceneState.PLAYING)
        inst.startedAtTick = rt.serverTick
        lastStarted = inst
        attachCameraIfNeeded(inst)
        fire(inst, ScenePhase.START)
        rt.ensureTicking()
        return inst
    }

    /** Adds a late-joining viewer to a running scene (group playback). */
    fun addViewer(inst: SceneInstance, player: Player): Boolean {
        if (!inst.state.isActive || inst.hasViewer(player.uniqueId)) return false
        if (inst.viewerUuids.size >= rt.settings.maxViewersPerInstance) return false
        byPlayer[player.uniqueId]?.takeIf { it !== inst }?.let { removeViewer(it, player, EndReason.REPLACED, restore = false) }
        inst.addViewerId(player.uniqueId)
        byPlayer[player.uniqueId] = inst
        rt.states.capture(player, inst.sceneId)
        // Private actors were shown to the original audience at spawn; reveal them to the late joiner too.
        rt.actors.of(inst).forEach { a -> a.entity()?.let { player.showEntity(rt.plugin, it) } }
        if (inst.uniqueId in cameraActive) {
            inst.player
                .camera()
                .evaluate(inst.position)
                ?.let { rt.camera.attach(inst, player, it) }
        }
        return true
    }

    /** Removes one viewer, restoring them. Ends the scene if nobody is left. */
    fun removeViewer(inst: SceneInstance, player: Player, reason: EndReason, restore: Boolean = true) {
        if (!inst.removeViewerId(player.uniqueId)) return
        if (byPlayer[player.uniqueId] === inst) byPlayer.remove(player.uniqueId)
        inst.skipVotes.remove(player.uniqueId)
        rt.camera.detach(inst, player)
        if (restore && player.isOnline) rt.states.restore(player, inst.settings.restoreLocation, inst.endLocation)
        if (inst.viewerUuids.isEmpty() &&
            inst.state.isAlive
        ) {
            end(inst, if (reason == EndReason.SKIPPED) EndReason.SKIPPED else EndReason.NO_VIEWERS)
        }
    }

    private fun attachCameraIfNeeded(inst: SceneInstance) {
        if (inst.uniqueId in cameraActive) return
        val cam = inst.player.camera()
        if (cam.isEmpty || inst.cameraWorld == null) return
        val pose = cam.evaluate(inst.position) ?: return
        var any = false
        for (p in inst.viewers) any = rt.camera.attach(inst, p, pose) || any
        if (any) cameraActive.add(inst.uniqueId)
    }

    fun pause(inst: SceneInstance): Boolean {
        if (inst.state != SceneState.PLAYING) return false
        val ok = inst.stateMachine.tryTransition(SceneState.PAUSED)
        if (ok) fire(inst, ScenePhase.PAUSE)
        return ok
    }

    fun resume(inst: SceneInstance): Boolean {
        if (inst.state != SceneState.PAUSED && inst.state != SceneState.WAITING_FOR_INPUT) return false
        if (inst.pendingChoices > 0 && inst.state == SceneState.PAUSED) return inst.stateMachine.tryTransition(SceneState.WAITING_FOR_INPUT)
        val ok = inst.stateMachine.tryTransition(SceneState.PLAYING)
        if (ok) fire(inst, ScenePhase.RESUME)
        return ok
    }

    fun seek(inst: SceneInstance, ticks: Double) {
        if (!inst.state.isActive || !ticks.isFinite()) return
        inst.player.seek(ticks, Listener(inst))
    }

    fun restart(inst: SceneInstance) {
        if (!inst.state.isActive) return
        inst.player.restart()
        if (inst.state == SceneState.PAUSED) resume(inst)
    }

    /** Skips to the end: guaranteed events still run once, then the scene completes as skipped. */
    fun skip(inst: SceneInstance): Boolean {
        if (!inst.state.isActive) return false
        inst.player.skipToEnd(Listener(inst))
        return end(inst, EndReason.SKIPPED)
    }

    /**
     * A viewer asked to skip (sneak or script). Honors the scene's skippable flag and the group skip mode.
     * Returns true if anything happened.
     */
    fun requestSkip(player: Player): Boolean {
        val inst = byPlayer[player.uniqueId] ?: return false
        if (!inst.settings.skippable || !inst.state.isActive) return false
        if (!inst.group || inst.viewerUuids.size <= 1) return skip(inst)
        return when (inst.settings.skipMode) {
            SkipMode.ANYONE -> skip(inst)
            SkipMode.INDIVIDUAL -> {
                removeViewer(inst, player, EndReason.SKIPPED)
                true
            }
            SkipMode.VOTE -> {
                if (!inst.skipVotes.add(player.uniqueId)) return false
                val votes = inst.skipVotes.size
                val total = inst.viewerUuids.size
                if (votes * 2 > total) {
                    skip(inst)
                } else {
                    val msg = Component.text("${player.name} wants to skip ($votes/$total). Sneak to vote.", NamedTextColor.GRAY)
                    inst.viewers.forEach { it.sendMessage(msg) }
                }
                true
            }
        }
    }

    /** Ends [inst] for [reason]. Safe to call repeatedly; only the first call has an effect. */
    fun end(inst: SceneInstance, reason: EndReason): Boolean {
        if (!inst.stateMachine.beginEnd(reason)) return false
        val viewers = inst.viewers
        // 1. Release everything the scene owns (actors, effects, dialogue, choices, sounds, animations).
        for (r in inst.drainResources()) {
            try {
                r.dispose(reason)
            } catch (t: Throwable) {
                rt.profiler.cleanupFailures++
                diagnostics.error(
                    Diagnostics.scene(inst.sceneId, inst.definition.sourceScript, "cleanup step failed"),
                    "cleanup:${inst.sceneId}",
                    t,
                )
            }
        }
        // 2. Give cameras back and restore players.
        for (p in viewers) {
            try {
                rt.camera.detach(inst, p)
                if (byPlayer[p.uniqueId] === inst) byPlayer.remove(p.uniqueId)
                rt.states.restore(p, inst.settings.restoreLocation, inst.endLocation)
            } catch (t: Throwable) {
                rt.profiler.cleanupFailures++
                diagnostics.error("Could not restore ${p.name} after $inst", "restore-end:${p.uniqueId}", t)
            }
        }
        byPlayer.entries.removeIf { it.value === inst }
        rt.camera.dispose(inst)
        cameraActive.remove(inst.uniqueId)
        instances.remove(inst.uniqueId)
        inst.stateMachine.finishEnd()
        // 3. Outcome events and script callbacks, after players are back in control.
        val phase =
            when (reason) {
                EndReason.COMPLETED -> ScenePhase.FINISH
                EndReason.SKIPPED -> ScenePhase.SKIP
                EndReason.ERROR -> ScenePhase.FAIL
                else -> ScenePhase.CANCEL
            }
        fire(inst, phase, reason)
        fire(inst, ScenePhase.END, reason)
        return true
    }

    fun endAll(reason: EndReason) {
        instances.values.toList().forEach { end(it, reason) }
    }

    fun endOwnedBy(owner: SceneOwner, reason: EndReason): Int {
        val list = instances.values.filter { it.definition.owner == owner }
        list.forEach { end(it, reason) }
        return list.size
    }

    /** Called when a choice resolves, to continue scenes waiting for input. */
    fun onChoiceResolved(inst: SceneInstance) {
        if (inst.state == SceneState.WAITING_FOR_INPUT && inst.pendingChoices <= 0) {
            if (inst.stateMachine.tryTransition(SceneState.PLAYING)) fire(inst, ScenePhase.RESUME)
        }
    }

    /** Advances every playing scene by one tick. */
    fun tick() {
        if (instances.isEmpty()) return
        for (inst in instances.values.toList()) {
            try {
                tickInstance(inst)
            } catch (t: Throwable) {
                diagnostics.error(
                    Diagnostics.scene(inst.sceneId, inst.definition.sourceScript, "playback error: ${t.message ?: t.javaClass.simpleName}"),
                    "tick:${inst.sceneId}",
                    t,
                )
                if (rt.settings.endSceneOnError) end(inst, EndReason.ERROR)
            }
        }
    }

    private fun tickInstance(inst: SceneInstance) {
        if (inst.viewers.isEmpty()) {
            end(inst, EndReason.NO_VIEWERS)
            return
        }
        if (inst.state == SceneState.PLAYING && inst.pendingChoices > 0) inst.stateMachine.tryTransition(SceneState.WAITING_FOR_INPUT)
        if (inst.state == SceneState.PLAYING) {
            val result = inst.player.advance(Listener(inst))
            if (!inst.state.isAlive) return
            if (inst.player.totalTicks >= inst.timeline.limits.maxDurationTicks) {
                diagnostics.warn(
                    Diagnostics.scene(inst.sceneId, inst.definition.sourceScript, "reached the maximum scene duration and was stopped"),
                    "maxdur:${inst.sceneId}",
                )
                end(inst, EndReason.TIME_LIMIT)
                return
            }
            attachCameraIfNeeded(inst)
            if (result == AdvanceResult.FINISHED) {
                updateCamera(inst)
                end(inst, EndReason.COMPLETED)
                return
            }
            inst.timeline.camera.pruneModifiers(inst.position)
        }
        if (inst.state.isActive) updateCamera(inst)
    }

    private fun updateCamera(inst: SceneInstance) {
        if (inst.uniqueId !in cameraActive) return
        val pose = inst.player.camera().evaluate(inst.position) ?: return
        rt.camera.update(inst, pose)
    }

    private fun fire(inst: SceneInstance, phase: ScenePhase, reason: EndReason? = null) {
        try {
            Bukkit.getPluginManager().callEvent(CineSceneEvent(inst, phase, reason))
        } catch (t: Throwable) {
            diagnostics.error("A listener of cine scene ${phase.id} failed", "event:${phase.id}", t)
        }
        for (cb in inst.definition.callbacks(phase)) {
            try {
                cb.invoke(inst, reason)
            } catch (t: Throwable) {
                diagnostics.error(
                    Diagnostics.scene(inst.sceneId, inst.definition.sourceScript, "'on ${phase.id}' callback failed"),
                    "cb:${inst.sceneId}:${phase.id}",
                    t,
                )
            }
        }
    }

    /** Dispatches timeline output for one instance, isolating script errors. */
    private inner class Listener(val inst: SceneInstance) : TimelineListener {
        override fun canContinue() = inst.state == SceneState.PLAYING || inst.state == SceneState.CLEANING_UP

        override fun onEvent(event: TimedEvent, reason: FireReason) = guarded("timeline section '${event.label}'") { event.action.fire(reason) }

        override fun onRepeat(repeater: Repeater) = guarded("repeating section '${repeater.label}'") { repeater.action.fire(FireReason.REACHED) }

        override fun onMarker(marker: Marker) = guarded("marker '${marker.name}'") {
            Bukkit.getPluginManager().callEvent(CineMarkerEvent(inst, marker.name, marker.time))
        }

        override fun onLoop(iteration: Int) = fire(inst, ScenePhase.LOOP)

        private inline fun guarded(what: String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                diagnostics.error(
                    Diagnostics.scene(inst.sceneId, inst.definition.sourceScript, "$what failed: ${t.message ?: t.javaClass.simpleName}"),
                    "event:${inst.sceneId}:$what",
                    t,
                )
                if (rt.settings.endSceneOnError) end(inst, EndReason.ERROR)
            }
        }
    }
}
