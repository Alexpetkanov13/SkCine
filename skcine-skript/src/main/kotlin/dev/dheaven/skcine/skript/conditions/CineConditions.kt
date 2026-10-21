package dev.dheaven.skcine.skript.conditions

import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import dev.dheaven.skcine.api.capability.Capability
import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.core.scene.SceneState
import dev.dheaven.skcine.platform.actor.CineActor
import dev.dheaven.skcine.platform.effect.RunningEffect
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.skript.CineChoiceSectionEvent
import dev.dheaven.skcine.skript.CineCondition
import dev.dheaven.skcine.skript.condition
import dev.dheaven.skcine.skript.expr
import dev.dheaven.skcine.skript.opt
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.structures.SequenceRegistry
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.skriptlang.skript.registration.SyntaxRegistry

/*
 * All conditions are null-safe: unset or expired handles simply evaluate to false (or true when negated).
 */

/** `player is watching a cine scene` / `player is not watching [the] cine scene "intro"` */
class CondWatching : CineCondition() {
    private lateinit var players: Expression<Player>
    private var scene: Expression<*>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        players = e.expr(0)
        scene = e[1]
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean {
        val wanted = scene?.getSingle(event)
        return players.check(event, { p ->
            val cur = rt().manager.current(p) ?: return@check false
            when (wanted) {
                null -> true
                is SceneDefinition -> cur.definition === wanted || cur.sceneId == wanted.id
                is String -> cur.sceneId.equals(wanted.trim(), ignoreCase = true)
                else -> false
            }
        }, isNegated)
    }

    override val description = "is watching cine scene"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondWatching::class.java,
            ::CondWatching,
            "%players% (is|are) watching [a|the] cine scene [%-cinescene/string%]",
            "%players% (isn't|is not|aren't|are not) watching [a|the] cine scene [%-cinescene/string%]",
        )
    }
}

/** `{_p} is playing|paused|finished|cancelled|running|waiting for input|skippable` */
class CondPlaybackState : CineCondition() {
    private lateinit var playbacks: Expression<SceneInstance>
    private var state = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        playbacks = e.expr(0)
        state = result.mark
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean = playbacks.check(event, { i ->
        when (state) {
            1 -> i.state == SceneState.PLAYING
            2 -> i.state == SceneState.PAUSED
            3 -> i.state == SceneState.COMPLETED
            4 -> i.state == SceneState.CANCELLED || i.state == SceneState.FAILED
            5 -> i.state.isActive
            6 -> i.state == SceneState.WAITING_FOR_INPUT
            else -> i.settings.skippable && i.state.isActive
        }
    }, isNegated)

    override val description = "cine playback state"

    companion object {
        private const val STATES = "(1:playing|2:paused|3:(finished|completed)|4:(cancelled|stopped)|5:(running|active)|6:waiting for input|7:skippable)"

        fun register(r: SyntaxRegistry) = r.condition(
            CondPlaybackState::class.java,
            ::CondPlaybackState,
            "%cineplaybacks% (is|are) $STATES",
            "%cineplaybacks% (isn't|is not|aren't|are not) $STATES",
        )
    }
}

/** `cine scene "intro" exists` / `cine sequence "reveal" exists` */
class CondSceneExists : CineCondition() {
    private lateinit var id: Expression<String>
    private var sequence = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        id = e.expr(0)
        sequence = result.hasTag("sequence")
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean = id.check(event, { if (sequence) SequenceRegistry.get(it) != null else rt().registry.get(it) != null }, isNegated)

    override val description = "cine scene exists"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondSceneExists::class.java,
            ::CondSceneExists,
            "cine (scene|sequence:sequence) %string% (exists|is registered)",
            "cine (scene|sequence:sequence) %string% (doesn't|does not) exist",
        )
    }
}

/** `{_actor} is valid` / `{_actor} is visible to player` */
class CondActor : CineCondition() {
    private lateinit var actors: Expression<*>
    private var player: Expression<Player>? = null
    private var mode = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        player = e.opt(1)
        mode = result.mark
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean {
        val p = player?.getSingle(event)
        return actors.check(event, { value ->
            // Plain entities are accepted too, so this pattern never shadows Skript's own
            // "%entities% is alive" when a variable holds an ordinary mob.
            val en =
                when (value) {
                    is CineActor -> value.entity()
                    is Entity -> value
                    else -> null
                } ?: return@check false
            when (mode) {
                1 -> en.isValid && !en.isDead
                else -> if (p != null) p.canSee(en) else en.isVisibleByDefault
            }
        }, isNegated)
    }

    override val description = "cine actor state"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondActor::class.java,
            ::CondActor,
            "%cineactors/entities% (is|are) (1:(valid|alive|spawned)|2:visible [to %-player%])",
            "%cineactors/entities% (isn't|is not|aren't|are not) (1:(valid|alive|spawned)|2:visible [to %-player%])",
        )
    }
}

/** `cine capability "packetevents" is available` */
class CondCapability : CineCondition() {
    private lateinit var id: Expression<String>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        id = e.expr(0)
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean = id.check(event, { s ->
        Capability.entries
            .firstOrNull {
                it.id.equals(s.trim(), true)
            }?.let { rt().capabilities.has(it) } ?: false
    }, isNegated)

    override val description = "cine capability available"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondCapability::class.java,
            ::CondCapability,
            "cine capability %string% is (available|supported)",
            "cine capability %string% (isn't|is not) (available|supported)",
        )
    }
}

/** `cine easing "ease-out-back" is valid` (also presets, shapes, interpolations) */
class CondKnown : CineCondition() {
    private lateinit var id: Expression<String>
    private var kind = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        id = e.expr(0)
        kind = result.mark
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean = id.check(event, { s ->
        when (kind) {
            1 -> Easing.byName(s) != null
            2 -> rt().effects.hasPreset(s)
            3 -> Shape.byName(s) != null
            else -> Interpolation.byName(s) != null
        }
    }, isNegated)

    override val description = "cine name valid"

    companion object {
        private const val KINDS = "(1:easing|2:preset|3:shape|4:interpolation)"

        fun register(r: SyntaxRegistry) = r.condition(
            CondKnown::class.java,
            ::CondKnown,
            "cine $KINDS %string% is (valid|known|supported)",
            "cine $KINDS %string% (isn't|is not) (valid|known|supported)",
        )
    }
}

/** `player has an active cine camera` / `player can skip the cine scene` */
class CondPlayerCamera : CineCondition() {
    private lateinit var players: Expression<Player>
    private var skip = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        players = e.expr(0)
        skip = pattern >= 2
        isNegated = pattern == 1 || pattern == 3
        return true
    }

    override fun check(event: Event): Boolean = players.check(event, { p ->
        if (skip) {
            rt().manager.current(p)?.let { it.settings.skippable && it.state.isActive } ?: false
        } else {
            rt().camera.isControlled(p)
        }
    }, isNegated)

    override val description = "cine camera/skip state"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondPlayerCamera::class.java,
            ::CondPlayerCamera,
            "%players% (has|have) [an] active cine camera",
            "%players% (doesn't|does not|don't|do not) have [an] active cine camera",
            "%players% can skip [the] [current] cine scene",
            "%players% (can't|cannot|can not) skip [the] [current] cine scene",
        )
    }
}

/** `{_p} has cine marker "climax"` */
class CondMarker : CineCondition() {
    private lateinit var playbacks: Expression<SceneInstance>
    private lateinit var marker: Expression<String>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        playbacks = e.expr(0)
        marker = e.expr(1)
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean {
        val m = marker.getSingle(event) ?: return isNegated
        return playbacks.check(event, { it.timeline.marker(m) != null }, isNegated)
    }

    override val description = "cine playback has marker"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondMarker::class.java,
            ::CondMarker,
            "%cineplaybacks% (has|have|contains) [a] [cine] marker %string%",
            "%cineplaybacks% (doesn't|does not|don't|do not) (have|contain) [a] [cine] marker %string%",
        )
    }
}

/** `{_fx} is active` */
class CondEffectActive : CineCondition() {
    private lateinit var effects: Expression<RunningEffect>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        effects = e.expr(0)
        isNegated = pattern == 1
        return true
    }

    override fun check(event: Event): Boolean = effects.check(event, { it.isActive }, isNegated)

    override val description = "cine effect active"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondEffectActive::class.java,
            ::CondEffectActive,
            "%cineeffects% (is|are) (active|running)",
            "%cineeffects% (isn't|is not|aren't|are not) (active|running)",
        )
    }
}

/** In a choice section: `cine choice timed out` */
class CondChoiceTimedOut : CineCondition() {
    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        isNegated = pattern == 1
        if (!parser.isCurrentEvent(CineChoiceSectionEvent::class.java)) {
            ch.njol.skript.Skript
                .error("'cine choice timed out' can only be used inside a 'present cine choice' section")
            return false
        }
        return true
    }

    override fun check(event: Event): Boolean = ((event as? CineChoiceSectionEvent)?.timedOut ?: false) != isNegated

    override val description = "cine choice timed out"

    companion object {
        fun register(r: SyntaxRegistry) = r.condition(
            CondChoiceTimedOut::class.java,
            ::CondChoiceTimedOut,
            "[the] cine choice timed out",
            "[the] cine choice (didn't|did not) time out",
        )
    }
}
