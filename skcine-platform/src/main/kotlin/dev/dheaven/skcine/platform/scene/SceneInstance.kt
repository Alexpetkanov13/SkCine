package dev.dheaven.skcine.platform.scene

import dev.dheaven.skcine.api.scene.SceneHandle
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.PoseKeyframe
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.SceneState
import dev.dheaven.skcine.core.scene.SceneStateMachine
import dev.dheaven.skcine.core.timeline.Timeline
import dev.dheaven.skcine.core.timeline.TimelineLimits
import dev.dheaven.skcine.core.timeline.TimelinePlayer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Something an instance owns and must release when it ends (actors, effects, cameras, dialogues...). */
fun interface SceneResource {
    fun dispose(reason: EndReason)
}

/**
 * A single playback of a [SceneDefinition] for one viewer (individual) or several viewers sharing one timeline
 * (group). All mutation happens on the server main thread.
 */
class SceneInstance internal constructor(
    private val manager: SceneManager,
    override val definition: SceneDefinition,
    viewers: Collection<Player>,
    val group: Boolean,
    limits: TimelineLimits,
) : SceneHandle {
    override val uniqueId: UUID = UUID.randomUUID()

    /** Short, human-friendly handle number for diagnostics (`#12`). */
    val number: Long = COUNTER.incrementAndGet()

    val stateMachine = SceneStateMachine()
    override val state get() = stateMachine.state
    override val endReason get() = stateMachine.endReason

    override val timeline = Timeline(limits)
    val player = TimelinePlayer(timeline)

    var settings: SceneSettings = definition.settings

    private val viewerIds = LinkedHashSet<UUID>().apply { viewers.forEach { add(it.uniqueId) } }
    override val viewers: List<Player> get() = viewerIds.mapNotNull { Bukkit.getPlayer(it) }
    val viewerUuids: Set<UUID> get() = viewerIds

    /** Script-visible scene variables (`cine variable "x" of scene`). */
    val variables = HashMap<String, Any>()

    private val resources = ArrayList<SceneResource>()

    /** World all camera content lives in; fixed by the first camera keyframe/shot. */
    var cameraWorld: World? = null
        internal set

    /** Destination for viewers when the scene ends (overrides `restore location`). */
    var endLocation: Location? = null

    val skipVotes = HashSet<UUID>()

    /** Server tick at which playback started (0 before start). */
    var startedAtTick = 0L
        internal set

    /** Depth of nested sequence/build execution, bounded to stop runaway recursion. */
    var buildDepth = 0

    /** Number of unresolved choices that paused this scene. */
    var pendingChoices = 0

    override val position get() = player.position
    override val duration get() = player.duration()
    override var speed: Double
        get() = player.speed
        set(value) {
            player.speed = value
        }

    val sceneId get() = definition.id
    val primaryViewer: Player? get() = viewers.firstOrNull()

    fun hasViewer(uuid: UUID) = uuid in viewerIds

    internal fun addViewerId(uuid: UUID) = viewerIds.add(uuid)

    internal fun removeViewerId(uuid: UUID) = viewerIds.remove(uuid)

    /** Registers a resource released when the scene ends. If the scene already ended it is released immediately. */
    fun own(resource: SceneResource) {
        if (state.isTerminal || state == SceneState.CLEANING_UP) {
            resource.dispose(endReason ?: EndReason.STOPPED)
            return
        }
        resources.add(resource)
    }

    fun disown(resource: SceneResource) = resources.remove(resource)

    internal fun drainResources(): List<SceneResource> = resources.reversed().also { resources.clear() }

    /**
     * Adds a camera keyframe. The first camera content fixes [cameraWorld]; keyframes in other worlds are rejected
     * because a spectator camera cannot follow a path across dimensions.
     *
     * @return null on success, or an error message.
     */
    fun addCameraKeyframe(location: Location, timeTicks: Double, build: (Vec3, Double, Double) -> PoseKeyframe): String? {
        val world = location.world ?: return "camera keyframe location has no world"
        val err = claimCameraWorld(world)
        if (err != null) return err
        val v = Vec3(location.x, location.y, location.z)
        if (!v.isFinite()) return "camera keyframe location is not a finite position"
        if (!timeTicks.isFinite() || timeTicks < 0) return "camera keyframe time must be a non-negative duration"
        if (timeline.camera.keyframeCount >= MAX_CAMERA_KEYFRAMES) return "too many camera keyframes (max $MAX_CAMERA_KEYFRAMES)"
        timeline.camera.addKeyframe(build(v, location.yaw.toDouble(), location.pitch.toDouble()))
        return null
    }

    fun claimCameraWorld(world: World): String? {
        val current = cameraWorld
        if (current == null) {
            cameraWorld = world
            return null
        }
        if (current !=
            world
        ) {
            return "camera content must stay in world '${current.name}' (got '${world.name}'); cross-world camera paths are not supported"
        }
        return null
    }

    override fun pause() = manager.pause(this)

    override fun resume() = manager.resume(this)

    override fun stop() = manager.end(this, EndReason.STOPPED)

    override fun skip() = manager.skip(this)

    override fun seek(ticks: Double) = manager.seek(this, ticks)

    override fun toString() = "cine scene '${definition.id}' #$number"

    companion object {
        const val MAX_CAMERA_KEYFRAMES = 2000
        private val COUNTER = AtomicLong()
    }
}
