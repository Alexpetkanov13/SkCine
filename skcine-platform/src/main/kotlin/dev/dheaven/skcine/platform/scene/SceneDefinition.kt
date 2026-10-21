package dev.dheaven.skcine.platform.scene

import dev.dheaven.skcine.api.event.ScenePhase
import dev.dheaven.skcine.api.scene.SceneDefinitionHandle
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.SceneId

/** How a skip request from one viewer of a shared (group) scene is handled. */
enum class SkipMode(val id: String) {
    /** The requesting viewer leaves the shared scene; others keep watching. */
    INDIVIDUAL("individual"),

    /** Any viewer skips the scene for everyone. */
    ANYONE("anyone"),

    /** The scene is skipped once more than half of the viewers asked. */
    VOTE("vote"),
    ;

    companion object {
        fun parse(s: String): SkipMode? = entries.firstOrNull { it.id.equals(s.trim(), ignoreCase = true) }
    }
}

/** Per-scene options. Instances copy these at creation and may change their own copy at runtime. */
data class SceneSettings(
    val durationTicks: Double? = null,
    val skippable: Boolean = true,
    val lockInput: Boolean = true,
    val freezeMovement: Boolean = false,
    val invulnerable: Boolean = true,
    val restoreLocation: Boolean = true,
    val speed: Double = 1.0,
    val loops: Int = 0,
    val skipMode: SkipMode = SkipMode.INDIVIDUAL,
)

/** Identifies what registered a definition, so it can be unregistered when that owner goes away. */
data class SceneOwner(val scriptPath: String) {
    val displayName: String get() = scriptPath.substringAfterLast('/').substringAfterLast('\\')
}

/**
 * Populates a fresh instance's timeline. Steps run once per playback, in order, on the main thread — this is where
 * Skript `timeline:` sections and imperative build effects plug in.
 */
fun interface BuildStep {
    fun build(instance: SceneInstance)
}

fun interface SceneCallback {
    fun invoke(instance: SceneInstance, reason: EndReason?)
}

/**
 * A reusable scene definition. Definitions from `cine scene` structures are registered under their id; runtime
 * definitions from `a new cine scene` stay anonymous unless registered explicitly.
 *
 * Playing a definition snapshots its settings and steps into a new [SceneInstance], so editing a definition never
 * affects scenes that are already playing.
 */
class SceneDefinition(val sceneId: SceneId, val owner: SceneOwner?, settings: SceneSettings = SceneSettings(), steps: List<BuildStep> = emptyList()) : SceneDefinitionHandle {
    var settings: SceneSettings = settings

    private val _steps = ArrayList(steps)
    val steps: List<BuildStep> get() = _steps

    private val callbacks = HashMap<ScenePhase, MutableList<SceneCallback>>()

    override val id get() = sceneId.value
    override val sourceScript get() = owner?.displayName

    override var registered = false
        internal set

    /** Line in the owning script where this definition starts, when known. */
    var sourceLine: Int? = null

    fun addStep(step: BuildStep) {
        check(_steps.size < MAX_STEPS) { "scene '$id' already has the maximum of $MAX_STEPS build steps" }
        _steps.add(step)
    }

    fun on(phase: ScenePhase, callback: SceneCallback) {
        callbacks.getOrPut(phase) { ArrayList() }.add(callback)
    }

    fun callbacks(phase: ScenePhase): List<SceneCallback> = callbacks[phase] ?: emptyList()

    /** Independent copy with a new id (used by `copy cine scene`). */
    fun copy(newId: SceneId, newOwner: SceneOwner? = owner): SceneDefinition {
        val c = SceneDefinition(newId, newOwner, settings, _steps)
        callbacks.forEach { (phase, list) -> list.forEach { c.on(phase, it) } }
        c.sourceLine = sourceLine
        return c
    }

    override fun toString() = "cine scene '$id'"

    companion object {
        const val MAX_STEPS = 4096
    }
}
