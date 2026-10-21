package dev.dheaven.skcine.platform.player

import dev.dheaven.skcine.platform.Diagnostics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The subset of player state SkCine may change during a scene. Only these fields are restored, so unrelated
 * changes made by other plugins (health, inventory, effects...) are never overwritten.
 */
@Serializable
data class PlayerSnapshot(
    val uuid: String,
    val gameMode: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val allowFlight: Boolean,
    val flying: Boolean,
    val flySpeed: Float,
    val walkSpeed: Float,
    val sceneId: String,
    val savedAtMillis: Long,
) {
    fun location(): Location? = Bukkit.getWorld(world)?.let { Location(it, x, y, z, yaw, pitch) }
}

/**
 * Captures and restores player state with ownership awareness.
 *
 * A small JSON recovery record is written per watching player (off the main thread). If the server crashes or the
 * player disconnects mid-scene, the record is applied when the player is next seen, then deleted. Recovery files are
 * never a scene authoring mechanism.
 */
class PlayerStateService(dataFolder: File, private val diagnostics: Diagnostics, private val journalEnabled: Boolean) {
    private val dir = File(dataFolder, "recovery")
    private val json = Json { ignoreUnknownKeys = true }
    private val io: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "SkCine-Recovery").apply { isDaemon = true } }
    private val snapshots = HashMap<UUID, PlayerSnapshot>()

    /** UUIDs that SkCine is teleporting right now, so listeners can ignore its own teleports. */
    val internalTeleports = HashSet<UUID>()

    fun hasSnapshot(uuid: UUID) = snapshots.containsKey(uuid)

    fun snapshot(uuid: UUID) = snapshots[uuid]

    /** Captures state if not already captured (nested/replaced scenes keep the original pre-cinematic state). */
    fun capture(player: Player, sceneId: String): PlayerSnapshot {
        snapshots[player.uniqueId]?.let { return it }
        val l = player.location
        val snap =
            PlayerSnapshot(
                player.uniqueId.toString(),
                player.gameMode.name,
                l.world.name,
                l.x,
                l.y,
                l.z,
                l.yaw,
                l.pitch,
                player.allowFlight,
                player.isFlying,
                player.flySpeed,
                player.walkSpeed,
                sceneId,
                System.currentTimeMillis(),
            )
        snapshots[player.uniqueId] = snap
        writeJournal(snap)
        return snap
    }

    fun teleportInternal(player: Player, location: Location): Boolean {
        internalTeleports.add(player.uniqueId)
        try {
            return player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
        } finally {
            internalTeleports.remove(player.uniqueId)
        }
    }

    /** Puts the player into spectator mode for camera control. */
    fun enterSpectator(player: Player) {
        if (player.gameMode != GameMode.SPECTATOR) player.gameMode = GameMode.SPECTATOR
    }

    /**
     * Restores the captured state and forgets it.
     *
     * @param restoreLocation teleport back to where the player was when the scene started.
     * @param endLocation alternative destination (takes precedence when non-null).
     */
    fun restore(player: Player, restoreLocation: Boolean, endLocation: Location? = null) {
        val snap = snapshots.remove(player.uniqueId) ?: return
        try {
            if (player.spectatorTarget != null) player.spectatorTarget = null
            // Ownership-aware: only undo the spectator mode SkCine set. If another plugin changed the game mode
            // mid-scene, respect that change.
            val original = runCatching { GameMode.valueOf(snap.gameMode) }.getOrDefault(GameMode.SURVIVAL)
            if (player.gameMode == GameMode.SPECTATOR && original != GameMode.SPECTATOR) player.gameMode = original
            player.allowFlight = snap.allowFlight || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR
            if (player.allowFlight) player.isFlying = snap.flying
            player.flySpeed = snap.flySpeed.coerceIn(-1f, 1f)
            player.walkSpeed = snap.walkSpeed.coerceIn(-1f, 1f)
            val dest =
                endLocation ?: if (restoreLocation) {
                    snap.location() ?: player.world.spawnLocation
                } else {
                    null
                }
            if (dest != null) teleportInternal(player, dest)
            player.fallDistance = 0f
        } catch (t: Throwable) {
            diagnostics.error("Could not fully restore ${player.name} after a scene", "restore:${player.uniqueId}", t)
        } finally {
            deleteJournal(player.uniqueId)
        }
    }

    /** Forgets in-memory state without touching the player (e.g. on quit when restoring is impossible). */
    fun forget(uuid: UUID) {
        snapshots.remove(uuid)
    }

    /** Applies a recovery record left by a crash or disconnect. Call on join and on enable. */
    fun recoverIfNeeded(player: Player): Boolean {
        if (!journalEnabled || snapshots.containsKey(player.uniqueId)) return false
        val file = journalFile(player.uniqueId)
        if (!file.isFile) return false
        val snap =
            runCatching { json.decodeFromString(PlayerSnapshot.serializer(), file.readText()) }
                .onFailure { diagnostics.warn("Ignoring unreadable recovery file ${file.name}: ${it.message}") }
                .getOrNull()
        if (snap == null) {
            deleteJournal(player.uniqueId)
            return false
        }
        snapshots[player.uniqueId] = snap
        diagnostics.info("Restoring ${player.name} after an interrupted scene '${snap.sceneId}'.")
        restore(player, restoreLocation = true)
        return true
    }

    private fun journalFile(uuid: UUID) = File(dir, "$uuid.json")

    private fun writeJournal(snap: PlayerSnapshot) {
        if (!journalEnabled) return
        val text = json.encodeToString(PlayerSnapshot.serializer(), snap)
        io.execute {
            runCatching {
                dir.mkdirs()
                val tmp = File(dir, "${snap.uuid}.json.tmp")
                tmp.writeText(text)
                val target = File(dir, "${snap.uuid}.json")
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }.onFailure { diagnostics.warn("Could not write recovery file for ${snap.uuid}: ${it.message}") }
        }
    }

    private fun deleteJournal(uuid: UUID) {
        if (!journalEnabled) return
        io.execute { journalFile(uuid).delete() }
    }

    /** Flushes pending journal writes; called on disable. */
    fun shutdown() {
        io.shutdown()
        runCatching { io.awaitTermination(3, TimeUnit.SECONDS) }
    }
}
