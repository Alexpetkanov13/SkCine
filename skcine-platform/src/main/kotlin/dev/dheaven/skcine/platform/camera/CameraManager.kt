package dev.dheaven.skcine.platform.camera

import dev.dheaven.skcine.api.capability.Capability
import dev.dheaven.skcine.api.event.CameraAction
import dev.dheaven.skcine.api.event.CineCameraEvent
import dev.dheaven.skcine.core.path.Pose
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.compatibility.CapabilityRegistry
import dev.dheaven.skcine.platform.player.PlayerStateService
import dev.dheaven.skcine.platform.profiler.Profiler
import dev.dheaven.skcine.platform.scene.SceneInstance
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Camera backend contract. Backends are selected by capability; when none is usable, scenes still play (timeline,
 * actors, effects, dialogue) without camera control.
 */
interface CameraBackend {
    val id: String

    fun isAvailable(): Boolean
}

/**
 * Spectator + display-entity camera backend (Paper API only, no packets).
 *
 * Each scene instance gets one invisible [ItemDisplay] "rig" that all its viewers spectate. Moving the rig every tick
 * with a small teleport duration lets the client interpolate position *and* rotation between server updates, which
 * gives smooth motion at the client's frame rate without per-frame packets. Limitations (documented): no camera roll,
 * no FOV control, no cross-world paths, and the camera inherits spectator-mode rendering.
 */
class CameraManager(
    private val plugin: Plugin,
    private val settings: () -> SkCineSettings,
    private val states: PlayerStateService,
    private val capabilities: CapabilityRegistry,
    private val diagnostics: Diagnostics,
    private val profiler: Profiler,
) : CameraBackend {
    override val id = "spectator-display"

    override fun isAvailable() = capabilities.has(Capability.SPECTATOR_CAMERA) && capabilities.has(Capability.DISPLAY_ENTITIES)

    val cameraKey = NamespacedKey(plugin, "camera")

    private val rigs = HashMap<UUID, CameraRig>()
    private val rigByPlayer = HashMap<UUID, CameraRig>()

    val activeCameraCount get() = rigs.size
    val attachedPlayerCount get() = rigByPlayer.size

    fun rigOf(player: Player): CameraRig? = rigByPlayer[player.uniqueId]

    fun isCameraEntity(entity: Entity) = entity.persistentDataContainer.has(cameraKey, PersistentDataType.BYTE)

    fun isControlled(player: Player) = rigByPlayer.containsKey(player.uniqueId)

    /** Current camera pose for a player, if a scene controls their camera. */
    fun poseOf(player: Player): Location? = rigByPlayer[player.uniqueId]?.entity?.takeIf { it.isValid }?.location

    /**
     * Starts camera control of [player] for [instance] at [pose]. Returns false when unsupported (the scene then plays
     * without camera control).
     */
    fun attach(instance: SceneInstance, player: Player, pose: Pose): Boolean {
        if (!isAvailable()) {
            diagnostics.warn(
                Diagnostics.scene(
                    instance.sceneId,
                    instance.definition.sourceScript,
                    "camera control is unavailable on this server; ${capabilities.fallbackFor(Capability.SPECTATOR_CAMERA)}",
                ),
                "camera-unavailable",
            )
            return false
        }
        val world = instance.cameraWorld ?: return false
        val rig = rigs.getOrPut(instance.uniqueId) { CameraRig(instance, world) }
        rigByPlayer[player.uniqueId]?.takeIf { it !== rig }?.let { detach(it.instance, player) }
        rig.ensureEntity(pose)
        val loc = pose.toLocation(world)
        if (player.world != world || player.location.distanceSquared(loc) > 32.0 * 32.0) {
            states.teleportInternal(player, loc)
        }
        states.enterSpectator(player)
        rig.entity?.let { player.showEntity(plugin, it) }
        rig.pending[player.uniqueId] = settings().cameraAttachDelayTicks.coerceIn(1, 20)
        rigByPlayer[player.uniqueId] = rig
        Bukkit.getPluginManager().callEvent(CineCameraEvent(instance, player, CameraAction.ATTACH))
        return true
    }

    /** Releases [player]'s camera. Player state restoration is handled by the scene manager. */
    fun detach(instance: SceneInstance, player: Player) {
        val rig = rigs[instance.uniqueId] ?: return
        if (rigByPlayer[player.uniqueId] !== rig) return
        rigByPlayer.remove(player.uniqueId)
        rig.pending.remove(player.uniqueId)
        rig.detaching.add(player.uniqueId)
        try {
            if (player.isOnline && player.spectatorTarget != null) player.spectatorTarget = null
        } catch (t: Throwable) {
            profiler.cleanupFailures++
            diagnostics.error("Could not release camera of ${player.name}", "detach:${player.uniqueId}", t)
        } finally {
            rig.detaching.remove(player.uniqueId)
        }
        Bukkit.getPluginManager().callEvent(CineCameraEvent(instance, player, CameraAction.DETACH))
    }

    /** True while SkCine itself is releasing [player]'s camera (listeners must not block it). */
    fun isDetaching(player: Player) = rigs.values.any { player.uniqueId in it.detaching }

    /** Moves the instance's camera; called once per tick by the scene manager. */
    fun update(instance: SceneInstance, pose: Pose) {
        val rig = rigs[instance.uniqueId] ?: return
        rig.ensureEntity(pose)
        val e = rig.entity ?: return
        val loc = pose.toLocation(rig.world)
        if (rig.lastPose != pose) {
            e.teleport(loc)
            rig.lastPose = pose
            profiler.cameraUpdates.add()
        }
        // Attach pending viewers once the client has had time to receive the rig entity, and re-attach anyone who
        // lost the camera (sneak without the locking capability, game mode changes by other plugins...).
        val it = rig.pending.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value > 1) {
                entry.setValue(entry.value - 1)
                continue
            }
            it.remove()
            Bukkit.getPlayer(entry.key)?.let { p -> target(p, e) }
        }
        for ((uuid, r) in rigByPlayer) {
            if (r !== rig || uuid in rig.pending) continue
            val p = Bukkit.getPlayer(uuid) ?: continue
            if (p.spectatorTarget != e) target(p, e)
        }
    }

    private fun target(p: Player, e: Entity) {
        if (p.gameMode != GameMode.SPECTATOR) states.enterSpectator(p)
        if (p.world != e.world) states.teleportInternal(p, e.location)
        p.spectatorTarget = e
    }

    /** Removes the rig of an ended instance. Viewers must already be detached. */
    fun dispose(instance: SceneInstance) {
        val rig = rigs.remove(instance.uniqueId) ?: return
        rigByPlayer.entries.removeIf { (uuid, r) ->
            if (r === rig) {
                Bukkit.getPlayer(uuid)?.let { p -> if (p.spectatorTarget == rig.entity) p.spectatorTarget = null }
                true
            } else {
                false
            }
        }
        rig.removeEntity()
    }

    /** Emergency cleanup on disable. */
    fun disposeAll() {
        rigs.values.toList().forEach { dispose(it.instance) }
    }

    /** Removes stray camera entities left in loaded worlds (they are non-persistent, so this is a safety net). */
    fun removeStrayEntities(worlds: Collection<World>): Int {
        var n = 0
        val live = rigs.values.mapNotNull { it.entity?.uniqueId }.toSet()
        for (w in worlds) {
            for (e in w.getEntitiesByClass(ItemDisplay::class.java)) {
                if (isCameraEntity(e) && e.uniqueId !in live) {
                    e.remove()
                    n++
                }
            }
        }
        return n
    }

    inner class CameraRig(val instance: SceneInstance, val world: World) {
        var entity: ItemDisplay? = null
            private set
        var lastPose: Pose? = null
        val pending = HashMap<UUID, Int>()
        val detaching = HashSet<UUID>()

        fun ensureEntity(pose: Pose) {
            val current = entity
            if (current != null && current.isValid) return
            if (current != null) {
                diagnostics.debug { "camera rig of $instance was removed externally; respawning" }
            }
            val loc = pose.toLocation(world)
            entity =
                world.spawn(loc, ItemDisplay::class.java) { d ->
                    d.isPersistent = false
                    d.isVisibleByDefault = false
                    d.setGravity(false)
                    d.isInvulnerable = true
                    d.isSilent = true
                    if (capabilities.has(Capability.DISPLAY_TELEPORT_INTERPOLATION)) {
                        d.teleportDuration = settings().cameraSmoothingTicks.coerceIn(0, 59)
                    }
                    d.persistentDataContainer.set(cameraKey, PersistentDataType.BYTE, 1)
                }
            lastPose = pose
            entity?.let { e ->
                for ((uuid, r) in rigByPlayer) {
                    if (r !== this) continue
                    Bukkit.getPlayer(uuid)?.let { p ->
                        p.showEntity(plugin, e)
                        pending[uuid] = settings().cameraAttachDelayTicks.coerceIn(1, 20)
                    }
                }
            }
        }

        fun removeEntity() {
            entity?.let { if (it.isValid) it.remove() }
            entity = null
        }
    }
}

fun Pose.toLocation(world: World) = Location(world, position.x, position.y, position.z, yaw.toFloat(), pitch.toFloat())
