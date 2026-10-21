package dev.dheaven.skcine.platform.effect

import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.camera.CameraManager
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

/**
 * Full-view colour fades and flashes without a resource pack.
 *
 * A text display with an opaque background is kept just in front of each viewer's view point (the camera rig while a
 * scene controls the camera, otherwise the player's eyes) and only that viewer can see it. Its background alpha is
 * stepped every tick. Limitations: entities closer than the overlay distance can appear in front of it, and the
 * colour is uniform (no gradients or vignettes).
 */
class ScreenOverlayService(private val plugin: Plugin, private val settings: () -> SkCineSettings, private val camera: CameraManager) {
    private val key = NamespacedKey(plugin, "overlay")

    private class Overlay(
        val player: UUID,
        var entity: UUID?,
        var color: Color,
        var fromAlpha: Double,
        var toAlpha: Double,
        var duration: Int,
        var age: Int,
        var easing: Easing,
        var removeWhenClear: Boolean,
        val owner: SceneInstance?,
    ) {
        fun alpha(): Double {
            val u = if (duration <= 0) 1.0 else (age.toDouble() / duration).coerceIn(0.0, 1.0)
            return fromAlpha + (toAlpha - fromAlpha) * easing.apply(u)
        }
    }

    private val overlays = HashMap<UUID, Overlay>()

    val count get() = overlays.size

    /** Current overlay opacity for a player (0 = clear). */
    fun opacity(player: Player) = overlays[player.uniqueId]?.alpha() ?: 0.0

    /** Fades a player's view to [toAlpha] (0..1) over [ticks]. */
    fun fade(player: Player, color: Color, toAlpha: Double, ticks: Int, easing: Easing, owner: SceneInstance?) {
        val existing = overlays[player.uniqueId]
        val from = existing?.alpha() ?: 0.0
        if (existing != null) {
            existing.color = color
            existing.fromAlpha = from
            existing.toAlpha = toAlpha
            existing.duration = ticks
            existing.age = 0
            existing.easing = easing
            existing.removeWhenClear = toAlpha <= 0.0
            return
        }
        if (toAlpha <= 0.0) return
        val o = Overlay(player.uniqueId, null, color, from, toAlpha.coerceIn(0.0, 1.0), ticks, 0, easing, false, owner)
        overlays[player.uniqueId] = o
        owner?.own(SceneResource { remove(o) })
        update(o)
    }

    /** Instant full-colour flash fading out over [ticks]. */
    fun flash(player: Player, color: Color, ticks: Int, owner: SceneInstance?) {
        fade(player, color, 1.0, 0, Easing.LINEAR, owner)
        overlays[player.uniqueId]?.let {
            it.fromAlpha = 1.0
            it.toAlpha = 0.0
            it.duration = ticks
            it.age = 0
            it.easing = Easing.byName("ease-out-quad")!!
            it.removeWhenClear = true
        }
    }

    fun clear(player: Player) = overlays[player.uniqueId]?.let { remove(it) }

    fun tick() {
        if (overlays.isEmpty()) return
        for (o in overlays.values.toList()) {
            o.age++
            update(o)
            if (o.removeWhenClear && o.age >= o.duration && o.alpha() <= 0.001) remove(o)
        }
    }

    private fun viewPoint(p: Player): Location = camera.poseOf(p) ?: p.eyeLocation

    private fun update(o: Overlay) {
        val p = Bukkit.getPlayer(o.player) ?: return remove(o)
        val view = viewPoint(p)
        val dir = view.direction
        val pos = view.clone().add(dir.multiply(DISTANCE))
        val alpha = (o.alpha() * 255).toInt().coerceIn(0, 255)
        val argb = Color.fromARGB(alpha, o.color.red, o.color.green, o.color.blue)
        var e = o.entity?.let { Bukkit.getEntity(it) as? TextDisplay }?.takeIf { it.isValid }
        if (e == null || e.world != pos.world) {
            e?.remove()
            e =
                pos.world.spawn(pos, TextDisplay::class.java) { d ->
                    d.isPersistent = false
                    d.isVisibleByDefault = false
                    d.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
                    d.text(Component.text("    "))
                    d.isDefaultBackground = false
                    d.isSeeThrough = false
                    d.isShadowed = false
                    d.billboard = Display.Billboard.CENTER
                    d.brightness = Display.Brightness(15, 15)
                    d.teleportDuration = settings().cameraSmoothingTicks.coerceIn(0, 59)
                    // A short line of spaces scaled up so its background fills the whole field of view.
                    d.transformation =
                        Transformation(Vector3f(0f, -SIZE * 0.125f, 0f), Quaternionf(), Vector3f(SIZE, SIZE, SIZE), Quaternionf())
                    d.backgroundColor = argb
                }
            p.showEntity(plugin, e)
            o.entity = e.uniqueId
        } else {
            e.teleport(pos)
            if (e.backgroundColor != argb) e.backgroundColor = argb
        }
    }

    private fun remove(o: Overlay) {
        overlays.remove(o.player, o)
        o.entity?.let { Bukkit.getEntity(it)?.remove() }
        o.entity = null
    }

    fun removeAll() = overlays.values.toList().forEach { remove(it) }

    companion object {
        const val DISTANCE = 0.3
        const val SIZE = 8f
    }
}
