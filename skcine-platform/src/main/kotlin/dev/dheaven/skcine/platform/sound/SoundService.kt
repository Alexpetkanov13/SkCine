package dev.dheaven.skcine.platform.sound

import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Plays vanilla or resource-pack sounds by namespaced key. Sounds played for a scene are tracked so they can be
 * stopped when the scene is interrupted. Minecraft cannot change the volume of a sound that is already playing, so
 * SkCine offers no volume fades or mixing; layer sounds with timed sections and `stop cine sounds` instead.
 */
class SoundService(private val diagnostics: Diagnostics) {
    private val playedByScene = HashMap<UUID, MutableSet<Pair<Key, Sound.Source>>>()

    /** Parses `minecraft:entity.wither.spawn`, `entity.wither.spawn` or `myserver:cinematics/boss_intro`. */
    fun parseKey(raw: String, error: (String) -> Unit): Key? {
        val s = raw.trim().lowercase()
        return try {
            Key.key(s)
        } catch (e: InvalidKeyException) {
            error("'$raw' is not a valid sound key (use e.g. \"entity.ender_dragon.growl\" or \"myserver:cinematics/intro\")")
            null
        }
    }

    fun parseSource(raw: String?): Sound.Source = raw?.let { r -> Sound.Source.entries.firstOrNull { it.name.equals(r.trim().replace(' ', '_'), ignoreCase = true) } }
        ?: Sound.Source.MASTER

    /**
     * Plays a sound. With [at] the sound is positional; otherwise it plays at each listener (non-positional feel).
     */
    fun play(key: Key, source: Sound.Source, volume: Float, pitch: Float, at: Location?, listeners: Collection<Player>, owner: SceneInstance?) {
        val v = volume.coerceIn(0f, 10f)
        val p = pitch.coerceIn(0.5f, 2f)
        val sound = Sound.sound(key, source, v, p)
        for (player in listeners) {
            if (at != null) {
                if (at.world != player.world) continue
                player.playSound(sound, at.x, at.y, at.z)
            } else {
                player.playSound(sound, Sound.Emitter.self())
            }
        }
        if (owner != null) {
            val set = playedByScene.getOrPut(owner.uniqueId) { HashSet() }
            if (set.isEmpty()) owner.own(StopResource(owner))
            set.add(key to source)
        }
    }

    /** Stops every sound the scene played, for its viewers. */
    fun stopSceneSounds(instance: SceneInstance) {
        val set = playedByScene.remove(instance.uniqueId) ?: return
        val viewers = instance.viewers
        for ((key, source) in set) {
            val stop = SoundStop.namedOnSource(key, source)
            viewers.forEach { it.stopSound(stop) }
        }
    }

    fun stop(key: Key?, players: Collection<Player>) {
        val stop = if (key == null) SoundStop.all() else SoundStop.named(key)
        players.forEach { it.stopSound(stop) }
    }

    fun clear() = playedByScene.clear()

    private inner class StopResource(val instance: SceneInstance) : SceneResource {
        override fun dispose(reason: EndReason) {
            // Completed scenes let their final sounds ring out; interrupted scenes are silenced.
            if (reason != EndReason.COMPLETED) stopSceneSounds(instance) else playedByScene.remove(instance.uniqueId)
        }
    }
}
