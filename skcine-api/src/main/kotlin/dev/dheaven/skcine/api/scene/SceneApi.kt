package dev.dheaven.skcine.api.scene

import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.SceneState
import dev.dheaven.skcine.core.timeline.Timeline
import org.bukkit.entity.Player
import java.util.UUID

/** A reusable scene definition (from a `cine scene` structure or created at runtime). */
interface SceneDefinitionHandle {
    val id: String

    /** Script file that owns this definition, or null for definitions not bound to a script. */
    val sourceScript: String?
    val registered: Boolean
}

/** A playing (or finished) scene instance. All methods must be called on the server main thread. */
interface SceneHandle {
    val uniqueId: UUID
    val definition: SceneDefinitionHandle
    val state: SceneState
    val endReason: EndReason?
    val viewers: List<Player>

    /** Position in ticks. */
    val position: Double
    val duration: Double
    var speed: Double

    /**
     * Live timeline. Integrations may add events, repeaters or markers to extend playback with custom tracks.
     */
    val timeline: Timeline

    fun pause(): Boolean

    fun resume(): Boolean

    fun stop(): Boolean

    fun skip(): Boolean

    fun seek(ticks: Double)
}

interface SceneRegistry {
    fun get(id: String): SceneDefinitionHandle?

    fun ids(): Set<String>
}

interface SceneRuntime {
    /** Plays a registered scene for each player individually. */
    fun play(sceneId: String, players: Collection<Player>): List<SceneHandle>

    fun current(player: Player): SceneHandle?

    fun active(): Collection<SceneHandle>
}
