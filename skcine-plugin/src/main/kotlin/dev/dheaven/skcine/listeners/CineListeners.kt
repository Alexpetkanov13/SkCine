package dev.dheaven.skcine.listeners

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.SkCineRuntime
import dev.dheaven.skcine.platform.dialogue.ChoiceMenuHolder
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.util.UUID

/**
 * Player safety and input handling. Every handler first checks whether the player is watching a scene, so players
 * outside cinematics are never affected.
 */
class CineListeners(private val rt: SkCineRuntime) : Listener {
    private val lastSkip = HashMap<UUID, Long>()

    private fun watching(p: Player) = rt.manager.current(p)

    private fun lock(p: Player, e: Cancellable) {
        if (watching(p)?.settings?.lockInput == true) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(e: PlayerJoinEvent) {
        rt.states.recoverIfNeeded(e.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onQuit(e: PlayerQuitEvent) {
        val p = e.player
        rt.dialogue.cancel(p)
        rt.overlays.clear(p)
        watching(p)?.let { rt.manager.removeViewer(it, p, EndReason.VIEWER_LEFT) }
        lastSkip.remove(p.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(e: PlayerDeathEvent) {
        watching(e.player)?.let { rt.manager.removeViewer(it, e.player, EndReason.VIEWER_LEFT) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        val p = e.player
        val inst = watching(p) ?: return
        if (p.uniqueId in rt.states.internalTeleports) return
        // Moved to another world by something else: the camera cannot follow, so release the player.
        if (inst.cameraWorld != null && inst.cameraWorld != p.world) rt.manager.removeViewer(inst, p, EndReason.VIEWER_LEFT)
    }

    @EventHandler(ignoreCancelled = true)
    fun onTeleport(e: PlayerTeleportEvent) {
        // Spectator hotbar teleports would yank the player out of the camera.
        if (e.cause == PlayerTeleportEvent.TeleportCause.SPECTATE && watching(e.player) != null) e.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onStopSpectating(e: PlayerStopSpectatingEntityEvent) {
        val p = e.player
        if (rt.camera.isControlled(p) && !rt.camera.isDetaching(p)) {
            e.isCancelled = true
            // The server stops spectating when the viewer presses shift: treat it as a skip request.
            skipRequested(p)
        }
    }

    /*
     * Sneak reaches the server differently across 1.21.x versions and game modes (toggle-sneak event, the
     * player-input packet, or the spectator shift handled above). All three feed one debounced skip request.
     */
    @EventHandler(ignoreCancelled = true)
    fun onSneak(e: PlayerToggleSneakEvent) {
        if (e.isSneaking) skipRequested(e.player)
    }

    @EventHandler
    fun onInput(e: PlayerInputEvent) {
        if (e.input.isSneak) skipRequested(e.player)
    }

    private fun skipRequested(p: Player) {
        if (!rt.settings.sneakToSkip || watching(p) == null) return
        val now = System.currentTimeMillis()
        if (now - (lastSkip[p.uniqueId] ?: 0L) < 500) return
        lastSkip[p.uniqueId] = now
        // Defer: skipping releases the camera, which must not happen inside another event handler.
        Bukkit.getScheduler().runTask(rt.plugin, Runnable { if (p.isOnline) rt.manager.requestSkip(p) })
    }

    @EventHandler(ignoreCancelled = true)
    fun onMove(e: PlayerMoveEvent) {
        val p = e.player
        val inst = watching(p) ?: return
        if (!inst.settings.freezeMovement || rt.camera.isControlled(p)) return
        val to = e.to
        if (to.x != e.from.x || to.y != e.from.y || to.z != e.from.z) {
            e.to =
                e.from.clone().apply {
                    yaw = to.yaw
                    pitch = to.pitch
                }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        if (watching(p)?.settings?.invulnerable == true) e.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(e: PlayerInteractEvent) = lock(e.player, e)

    @EventHandler(ignoreCancelled = true)
    fun onInteractEntity(e: PlayerInteractEntityEvent) = lock(e.player, e)

    @EventHandler(ignoreCancelled = true)
    fun onDrop(e: PlayerDropItemEvent) = lock(e.player, e)

    @EventHandler(ignoreCancelled = true)
    fun onSwap(e: PlayerSwapHandItemsEvent) = lock(e.player, e)

    @EventHandler(ignoreCancelled = true)
    fun onConsume(e: PlayerItemConsumeEvent) = lock(e.player, e)

    @EventHandler(ignoreCancelled = true)
    fun onInventory(e: InventoryOpenEvent) {
        // SkCine's own choice menus are allowed while input is locked.
        if (e.inventory.holder is ChoiceMenuHolder) return
        (e.player as? Player)?.let { lock(it, e) }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onMenuClick(e: InventoryClickEvent) {
        val holder = e.view.topInventory.holder as? ChoiceMenuHolder ?: return
        e.isCancelled = true
        val p = e.whoClicked as? Player ?: return
        if (e.clickedInventory === e.view.topInventory) rt.choices.handleMenuClick(p, holder, e.rawSlot)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onMenuDrag(e: InventoryDragEvent) {
        if (e.view.topInventory.holder is ChoiceMenuHolder) e.isCancelled = true
    }

    @EventHandler
    fun onMenuClose(e: InventoryCloseEvent) {
        val holder = e.inventory.holder as? ChoiceMenuHolder ?: return
        (e.player as? Player)?.let { rt.choices.handleMenuClose(it, holder) }
    }

    /** Numbered/typed replies to cine choices. Runs async; the choice service hops to the main thread. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChat(e: AsyncChatEvent) {
        val text = PlainTextComponentSerializer.plainText().serialize(e.message())
        if (rt.choices.handleChatReply(e.player, text)) e.isCancelled = true
    }
}
