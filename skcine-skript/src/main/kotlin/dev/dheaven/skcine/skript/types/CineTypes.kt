package dev.dheaven.skcine.skript.types

import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.classes.Parser
import ch.njol.skript.lang.ParseContext
import ch.njol.skript.registrations.Classes
import dev.dheaven.skcine.platform.actor.CineActor
import dev.dheaven.skcine.platform.effect.RunningEffect
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneInstance
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.skriptlang.skript.lang.converter.Converter
import org.skriptlang.skript.lang.converter.Converters

/**
 * SkCine handle types. They are runtime handles (not saved to variable files): storing one in a global variable keeps
 * it until the server restarts, and every syntax checks handles for validity, so expired values are harmless.
 */
object CineTypes {
    private class HandleParser<T>(private val describe: (T) -> String) : Parser<T>() {
        override fun canParse(context: ParseContext) = false

        override fun parse(s: String, context: ParseContext): T? = null

        override fun toString(o: T, flags: Int) = describe(o)

        override fun toVariableNameString(o: T) = describe(o)
    }

    /**
     * Actors convert to their entity (and location), so every entity syntax — SkCine's and Skript's own
     * (`teleport`, `glowing`, `location of`...) — accepts actors directly.
     */
    fun registerConverters() {
        Converters.registerConverter(CineActor::class.java, Entity::class.java, Converter { it.entity() })
        Converters.registerConverter(CineActor::class.java, Location::class.java, Converter { it.location() })
    }

    fun register() {
        Classes.registerClass(
            ClassInfo(SceneDefinition::class.java, "cinescene")
                .user("cine ?scenes?")
                .name("Cine Scene")
                .description(
                    "A reusable scene definition: either declared with a `cine scene \"id\":` structure or created at runtime with `a new cine scene`.",
                    "Playing a scene creates a separate cine playback; editing a definition never affects playbacks that already started.",
                ).examples("set {_scene} to cine scene \"welcome\"", "play cine scene {_scene} for player")
                .since("1.0.0")
                .parser(HandleParser<SceneDefinition> { "cine scene '${it.id}'" }),
        )
        Classes.registerClass(
            ClassInfo(SceneInstance::class.java, "cineplayback")
                .user("cine ?playbacks?", "cine ?scene ?instances?")
                .name("Cine Playback")
                .description(
                    "A playing (or finished) instance of a cine scene, with its own timeline clock, viewers, actors and variables.",
                    "Handles stay safe after the playback ends: state checks return 'completed'/'cancelled' and control effects do nothing.",
                ).examples("set {_p} to cine playback of player", "pause cine playback {_p}")
                .since("1.0.0")
                .parser(HandleParser<SceneInstance> { "cine playback of '${it.sceneId}' #${it.number}" }),
        )
        Classes.registerClass(
            ClassInfo(CineActor::class.java, "cineactor")
                .user("cine ?actors?")
                .name("Cine Actor")
                .description(
                    "An entity controlled by SkCine. Actors created by SkCine are removed when their scene ends;",
                    "actors wrapping existing entities (`cine actor of {_entity}`) are never removed by cleanup.",
                ).examples("set {_npc} to a new cine actor of villager at {_loc} named \"guide\"")
                .since("1.0.0")
                .parser(HandleParser<CineActor> { "cine actor '${it.name}'" }),
        )
        Classes.registerClass(
            ClassInfo(RunningEffect::class.java, "cineeffect")
                .user("cine ?effects?")
                .name("Cine Effect")
                .description("A running visual effect (preset or emitter). Stop it early with `stop cine effect`.")
                .examples("play cine preset \"golden-aura\" at player lasting 5 seconds", "set {_fx} to last cine effect")
                .since("1.0.0")
                .parser(HandleParser<RunningEffect> { "cine effect '${it.presetId}' #${it.id}" }),
        )
    }
}
