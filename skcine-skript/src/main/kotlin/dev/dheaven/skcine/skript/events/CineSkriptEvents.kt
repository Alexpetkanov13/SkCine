package dev.dheaven.skcine.skript.events

import ch.njol.skript.lang.Literal
import ch.njol.skript.lang.SkriptEvent
import ch.njol.skript.lang.SkriptParser.ParseResult
import dev.dheaven.skcine.api.event.ActorAction
import dev.dheaven.skcine.api.event.CameraAction
import dev.dheaven.skcine.api.event.CineActorEvent
import dev.dheaven.skcine.api.event.CineCameraEvent
import dev.dheaven.skcine.api.event.CineChoiceEvent
import dev.dheaven.skcine.api.event.CineDialogueEvent
import dev.dheaven.skcine.api.event.CineMarkerEvent
import dev.dheaven.skcine.api.event.CineSceneEvent
import dev.dheaven.skcine.api.event.ScenePhase
import dev.dheaven.skcine.platform.actor.CineActor
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.skript.CineBuildEvent
import dev.dheaven.skcine.skript.CineCallbackEvent
import dev.dheaven.skcine.skript.CineChoiceSectionEvent
import dev.dheaven.skcine.skript.CineContextEvent
import dev.dheaven.skcine.skript.CineTimelineEvent
import dev.dheaven.skcine.skript.rt
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos
import org.skriptlang.skript.registration.SyntaxRegistry

private fun matchesScene(ids: Literal<*>?, event: Event, sceneId: String?): Boolean {
    if (ids == null) return true
    sceneId ?: return false
    return ids.getArray(event).any { (it as? String)?.trim()?.equals(sceneId, ignoreCase = true) == true }
}

/** `on cine scene start [of "intro"]:` — prepare, start, pause, resume, loop, finish, skip, cancel, fail, end. */
class EvtCineScene : SkriptEvent() {
    private var phase = ScenePhase.START
    private var ids: Literal<*>? = null

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult): Boolean {
        phase = ScenePhase.entries[parseResult.mark]
        ids = args.getOrNull(0)
        return true
    }

    override fun check(event: Event): Boolean {
        val e = event as? CineSceneEvent ?: return false
        return e.phase == phase && matchesScene(ids, event, e.scene.definition.id)
    }

    override fun toString(event: Event?, debug: Boolean) = "cine scene ${phase.id}"

    companion object {
        val PATTERN =
            "cine scene (" +
                ScenePhase.entries.joinToString("|") { p ->
                    val words =
                        when (p) {
                            ScenePhase.PREPARE -> "prepar(e|ation)"
                            ScenePhase.FINISH -> "(finish|complete)"
                            ScenePhase.CANCEL -> "(cancel|stop)"
                            ScenePhase.FAIL -> "fail[ure]"
                            else -> p.id
                        }
                    "${p.ordinal}:$words"
                } + ") [of %-strings%]"

        fun register(r: SyntaxRegistry) {
            r.register(
                BukkitSyntaxInfos.Event.KEY,
                BukkitSyntaxInfos.Event
                    .builder(EvtCineScene::class.java, "Cine Scene Lifecycle")
                    .supplier { EvtCineScene() }
                    .addEvent(CineSceneEvent::class.java)
                    .addPatterns(PATTERN)
                    .addDescription(
                        "Called when a cine scene prepares, starts, pauses, resumes, loops, finishes, is skipped, cancelled, fails, or ends (always last).",
                    ).addExample("on cine scene finish of \"rpg:intro\":\n\tsend \"Welcome!\" to event-player")
                    .addSince("1.0.0")
                    .build(),
            )
        }
    }
}

/** `on cine marker ["climax"]:` */
class EvtCineMarker : SkriptEvent() {
    private var names: Literal<*>? = null

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult): Boolean {
        names = args.getOrNull(0)
        return true
    }

    override fun check(event: Event): Boolean {
        val e = event as? CineMarkerEvent ?: return false
        val n = names ?: return true
        return n.getArray(event).any { (it as? String)?.equals(e.marker, ignoreCase = true) == true }
    }

    override fun toString(event: Event?, debug: Boolean) = "cine marker"

    companion object {
        fun register(r: SyntaxRegistry) {
            r.register(
                BukkitSyntaxInfos.Event.KEY,
                BukkitSyntaxInfos.Event
                    .builder(EvtCineMarker::class.java, "Cine Marker")
                    .supplier { EvtCineMarker() }
                    .addEvent(CineMarkerEvent::class.java)
                    .addPatterns("cine marker [%-strings%]")
                    .addDescription("Called when playback passes a timeline marker (not when seeking over it).")
                    .addSince("1.0.0")
                    .build(),
            )
        }
    }
}

/** `on cine camera attach:` / `on cine camera detach:` */
class EvtCineCamera : SkriptEvent() {
    private var attach = true

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult): Boolean {
        attach = parseResult.mark == 1
        return true
    }

    override fun check(event: Event) = (event as? CineCameraEvent)?.action == if (attach) CameraAction.ATTACH else CameraAction.DETACH

    override fun toString(event: Event?, debug: Boolean) = "cine camera " + if (attach) "attach" else "detach"

    companion object {
        fun register(r: SyntaxRegistry) = r.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event
                .builder(EvtCineCamera::class.java, "Cine Camera")
                .supplier { EvtCineCamera() }
                .addEvent(CineCameraEvent::class.java)
                .addPatterns("cine camera (1:attach|2:detach)")
                .addDescription("Called when a scene takes or releases a player's camera.")
                .addSince("1.0.0")
                .build(),
        )
    }
}

class EvtCineActor : SkriptEvent() {
    private var mark = 0

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult): Boolean {
        mark = parseResult.mark
        return true
    }

    override fun check(event: Event): Boolean {
        val a = (event as? CineActorEvent)?.action ?: return false
        return a ==
            when (mark) {
                1 -> ActorAction.SPAWN
                2 -> ActorAction.DESPAWN
                else -> ActorAction.ANIMATION_FINISH
            }
    }

    override fun toString(event: Event?, debug: Boolean) = "cine actor event"

    companion object {
        fun register(r: SyntaxRegistry) = r.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event
                .builder(EvtCineActor::class.java, "Cine Actor")
                .supplier { EvtCineActor() }
                .addEvent(CineActorEvent::class.java)
                .addPatterns("cine actor (1:spawn|2:despawn|3:animation (finish|end))")
                .addDescription("Called when a cine actor spawns, despawns, or finishes an animation.")
                .addSince("1.0.0")
                .build(),
        )
    }
}

class EvtCineDialogue : SkriptEvent() {
    private var finished = false

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult): Boolean {
        finished = parseResult.mark == 2
        return true
    }

    override fun check(event: Event) = (event as? CineDialogueEvent)?.finished == finished

    override fun toString(event: Event?, debug: Boolean) = "cine dialogue"

    companion object {
        fun register(r: SyntaxRegistry) = r.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event
                .builder(EvtCineDialogue::class.java, "Cine Dialogue")
                .supplier { EvtCineDialogue() }
                .addEvent(CineDialogueEvent::class.java)
                .addPatterns("cine dialogue (1:start|2:(finish|end))")
                .addDescription("Called for each viewer when a dialogue line starts or finishes. event-string is the plain text.")
                .addSince("1.0.0")
                .build(),
        )
    }
}

class EvtCineChoice : SkriptEvent() {
    private var onlyTimeout = false

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult): Boolean {
        onlyTimeout = parseResult.hasTag("timeout")
        return true
    }

    override fun check(event: Event): Boolean {
        val e = event as? CineChoiceEvent ?: return false
        return !onlyTimeout || e.timedOut
    }

    override fun toString(event: Event?, debug: Boolean) = "cine choice"

    companion object {
        fun register(r: SyntaxRegistry) = r.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event
                .builder(EvtCineChoice::class.java, "Cine Choice")
                .supplier { EvtCineChoice() }
                .addEvent(CineChoiceEvent::class.java)
                .addPatterns("cine choice [timeout:time[ ]out]")
                .addDescription("Called when a cine choice is answered (event-string is the option) or times out.")
                .addSince("1.0.0")
                .build(),
        )
    }
}

/** Event values for every SkCine event and context event. */
object CineEventValues {
    private fun SceneInstance?.def(): SceneDefinition? = this?.definition

    fun register(reg: EventValueRegistry) {
        fun <E : Event, V> value(e: Class<E>, v: Class<V>, getter: (E) -> V?) = reg.register(EventValue.builder(e, v).getter { getter(it) }.build())

        // Bukkit events.
        value(CineSceneEvent::class.java, SceneInstance::class.java) { it.scene as? SceneInstance }
        value(CineSceneEvent::class.java, SceneDefinition::class.java) { (it.scene as? SceneInstance).def() }
        value(CineSceneEvent::class.java, Player::class.java) { it.player }
        value(CineSceneEvent::class.java, String::class.java) { it.scene.definition.id }

        value(CineMarkerEvent::class.java, SceneInstance::class.java) { it.scene as? SceneInstance }
        value(CineMarkerEvent::class.java, Player::class.java) { it.player }
        value(CineMarkerEvent::class.java, String::class.java) { it.marker }

        value(CineCameraEvent::class.java, SceneInstance::class.java) { it.scene as? SceneInstance }
        value(CineCameraEvent::class.java, Player::class.java) { it.player }

        value(CineActorEvent::class.java, SceneInstance::class.java) { it.scene as? SceneInstance }
        value(CineActorEvent::class.java, Entity::class.java) { it.entity }
        value(CineActorEvent::class.java, String::class.java) { it.actorId }
        value(CineActorEvent::class.java, CineActor::class.java) { e -> e.entity?.let { rt().actors.byEntity(it) } }

        value(CineDialogueEvent::class.java, SceneInstance::class.java) { it.scene as? SceneInstance }
        value(CineDialogueEvent::class.java, Player::class.java) { it.player }
        value(CineDialogueEvent::class.java, String::class.java) { it.text }

        value(CineChoiceEvent::class.java, SceneInstance::class.java) { it.scene as? SceneInstance }
        value(CineChoiceEvent::class.java, Player::class.java) { it.player }
        value(CineChoiceEvent::class.java, String::class.java) { it.option }

        // Context events (code inside SkCine sections).
        val contexts: List<Class<out CineContextEvent>> =
            listOf(
                CineBuildEvent::class.java,
                CineTimelineEvent::class.java,
                CineCallbackEvent::class.java,
                CineChoiceSectionEvent::class.java,
            )
        for (c in contexts) {
            @Suppress("UNCHECKED_CAST")
            val cls = c as Class<CineContextEvent>
            value(cls, SceneInstance::class.java) { it.instance }
            value(cls, SceneDefinition::class.java) { it.instance?.definition }
            if (c != CineChoiceSectionEvent::class.java) value(cls, Player::class.java) { it.instance?.primaryViewer }
        }
        value(CineChoiceSectionEvent::class.java, Player::class.java) { it.player ?: it.instance?.primaryViewer }
        value(CineChoiceSectionEvent::class.java, String::class.java) { it.option }
    }
}
