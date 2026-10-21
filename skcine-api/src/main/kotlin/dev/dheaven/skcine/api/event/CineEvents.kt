package dev.dheaven.skcine.api.event

import dev.dheaven.skcine.api.scene.SceneHandle
import dev.dheaven.skcine.core.scene.EndReason
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/*
 * Bukkit events fired by SkCine, always synchronously on the server main thread.
 * Skript scripts listen to them through `on cine ...` events; other plugins can use normal Bukkit listeners.
 */

enum class ScenePhase(val id: String) {
    PREPARE("prepare"),
    START("start"),
    PAUSE("pause"),
    RESUME("resume"),
    LOOP("loop"),

    /** Reached the end normally. */
    FINISH("finish"),

    /** Skipped by a viewer or script. */
    SKIP("skip"),

    /** Stopped or interrupted (see reason). */
    CANCEL("cancel"),

    /** An error ended the scene. */
    FAIL("fail"),

    /** Fired last for every ended scene, whatever the outcome. */
    END("end"),
}

class CineSceneEvent(val scene: SceneHandle, val phase: ScenePhase, val reason: EndReason? = null) : Event() {
    /** First viewer, if any. */
    val player: Player? get() = scene.viewers.firstOrNull()

    override fun getHandlers() = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}

class CineMarkerEvent(val scene: SceneHandle, val marker: String, val time: Double) : Event() {
    val player: Player? get() = scene.viewers.firstOrNull()

    override fun getHandlers() = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}

enum class CameraAction(val id: String) {
    ATTACH("attach"),
    DETACH("detach"),
    TRANSITION("transition"),
    PATH_COMPLETE("path complete"),
}

class CineCameraEvent(val scene: SceneHandle?, val player: Player, val action: CameraAction) : Event() {
    override fun getHandlers() = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}

enum class ActorAction(val id: String) {
    SPAWN("spawn"),
    DESPAWN("despawn"),
    ANIMATION_FINISH("animation finish"),
}

class CineActorEvent(val scene: SceneHandle?, val actorId: String, val entity: Entity?, val action: ActorAction) : Event() {
    override fun getHandlers() = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}

class CineDialogueEvent(val scene: SceneHandle?, val player: Player, val speaker: String?, val text: String, val finished: Boolean) : Event() {
    override fun getHandlers() = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}

/**
 * A choice was resolved. For per-player choices [player] is the selecting player; for votes it is null and
 * [option] is the winning option. [timedOut] is true when the timeout picked [option] (the default, possibly null).
 */
class CineChoiceEvent(val scene: SceneHandle?, val player: Player?, val choiceId: String, val option: String?, val timedOut: Boolean) : Event() {
    override fun getHandlers() = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }
}
