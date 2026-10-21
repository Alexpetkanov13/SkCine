package dev.dheaven.skcine.skript

import ch.njol.skript.ScriptLoader
import ch.njol.skript.Skript
import dev.dheaven.skcine.platform.SkCineRuntime
import dev.dheaven.skcine.skript.conditions.CondActor
import dev.dheaven.skcine.skript.conditions.CondCapability
import dev.dheaven.skcine.skript.conditions.CondChoiceTimedOut
import dev.dheaven.skcine.skript.conditions.CondEffectActive
import dev.dheaven.skcine.skript.conditions.CondKnown
import dev.dheaven.skcine.skript.conditions.CondMarker
import dev.dheaven.skcine.skript.conditions.CondPlaybackState
import dev.dheaven.skcine.skript.conditions.CondPlayerCamera
import dev.dheaven.skcine.skript.conditions.CondSceneExists
import dev.dheaven.skcine.skript.conditions.CondWatching
import dev.dheaven.skcine.skript.effects.EffActorAttach
import dev.dheaven.skcine.skript.effects.EffActorKeyframe
import dev.dheaven.skcine.skript.effects.EffActorLook
import dev.dheaven.skcine.skript.effects.EffActorMove
import dev.dheaven.skcine.skript.effects.EffActorRemove
import dev.dheaven.skcine.skript.effects.EffActorRotate
import dev.dheaven.skcine.skript.effects.EffActorScale
import dev.dheaven.skcine.skript.effects.EffActorStop
import dev.dheaven.skcine.skript.effects.EffActorVisibility
import dev.dheaven.skcine.skript.effects.EffActorWalk
import dev.dheaven.skcine.skript.effects.EffCameraInterpolation
import dev.dheaven.skcine.skript.effects.EffCameraKeyframe
import dev.dheaven.skcine.skript.effects.EffCameraShot
import dev.dheaven.skcine.skript.effects.EffCineSound
import dev.dheaven.skcine.skript.effects.EffCineTitle
import dev.dheaven.skcine.skript.effects.EffControl
import dev.dheaven.skcine.skript.effects.EffDialogue
import dev.dheaven.skcine.skript.effects.EffDialogueControl
import dev.dheaven.skcine.skript.effects.EffDisplayAnimate
import dev.dheaven.skcine.skript.effects.EffDisplaySpin
import dev.dheaven.skcine.skript.effects.EffDrawLine
import dev.dheaven.skcine.skript.effects.EffDrawShape
import dev.dheaven.skcine.skript.effects.EffEmitter
import dev.dheaven.skcine.skript.effects.EffGroupRotate
import dev.dheaven.skcine.skript.effects.EffMarker
import dev.dheaven.skcine.skript.effects.EffPlayScene
import dev.dheaven.skcine.skript.effects.EffPreset
import dev.dheaven.skcine.skript.effects.EffRegister
import dev.dheaven.skcine.skript.effects.EffRequestSkip
import dev.dheaven.skcine.skript.effects.EffSceneFlag
import dev.dheaven.skcine.skript.effects.EffScreen
import dev.dheaven.skcine.skript.effects.EffSeek
import dev.dheaven.skcine.skript.effects.EffShake
import dev.dheaven.skcine.skript.effects.EffStopAll
import dev.dheaven.skcine.skript.effects.EffStopEffect
import dev.dheaven.skcine.skript.effects.EffStopSound
import dev.dheaven.skcine.skript.effects.EffViewers
import dev.dheaven.skcine.skript.effects.EffZoom
import dev.dheaven.skcine.skript.effects.ExprActorOf
import dev.dheaven.skcine.skript.effects.ExprLastEffect
import dev.dheaven.skcine.skript.effects.ExprNewActor
import dev.dheaven.skcine.skript.effects.ExprNewPlayerActor
import dev.dheaven.skcine.skript.events.CineEventValues
import dev.dheaven.skcine.skript.events.EvtCineActor
import dev.dheaven.skcine.skript.events.EvtCineCamera
import dev.dheaven.skcine.skript.events.EvtCineChoice
import dev.dheaven.skcine.skript.events.EvtCineDialogue
import dev.dheaven.skcine.skript.events.EvtCineMarker
import dev.dheaven.skcine.skript.events.EvtCineScene
import dev.dheaven.skcine.skript.expressions.ExprActorEntity
import dev.dheaven.skcine.skript.expressions.ExprActorName
import dev.dheaven.skcine.skript.expressions.ExprActors
import dev.dheaven.skcine.skript.expressions.ExprCameraLocation
import dev.dheaven.skcine.skript.expressions.ExprChoice
import dev.dheaven.skcine.skript.expressions.ExprCineCount
import dev.dheaven.skcine.skript.expressions.ExprCineInfo
import dev.dheaven.skcine.skript.expressions.ExprEase
import dev.dheaven.skcine.skript.expressions.ExprEndLocation
import dev.dheaven.skcine.skript.expressions.ExprPlayback
import dev.dheaven.skcine.skript.expressions.ExprPlaybackNumber
import dev.dheaven.skcine.skript.expressions.ExprPlaybackText
import dev.dheaven.skcine.skript.expressions.ExprPlaybackTime
import dev.dheaven.skcine.skript.expressions.ExprSceneById
import dev.dheaven.skcine.skript.expressions.ExprSceneVariable
import dev.dheaven.skcine.skript.expressions.ExprViewers
import dev.dheaven.skcine.skript.sections.EffPlaySequence
import dev.dheaven.skcine.skript.sections.ExprSecNewScene
import dev.dheaven.skcine.skript.sections.SecAt
import dev.dheaven.skcine.skript.sections.SecChoice
import dev.dheaven.skcine.skript.sections.SecRepeat
import dev.dheaven.skcine.skript.structures.SequenceRegistry
import dev.dheaven.skcine.skript.structures.StructCineScene
import dev.dheaven.skcine.skript.structures.StructCineSequence
import dev.dheaven.skcine.skript.structures.ownerOf
import dev.dheaven.skcine.skript.types.CineTypes
import org.skriptlang.skript.addon.AddonModule
import org.skriptlang.skript.addon.SkriptAddon
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry

/** All SkCine syntax, registered through Skript's modern addon/module API. */
object CineModule : AddonModule {
    override fun init(addon: SkriptAddon) {
        CineTypes.register()
        CineTypes.registerConverters()
    }

    override fun load(addon: SkriptAddon) {
        register(
            addon,
            // Structures and sections
            StructCineScene::register,
            StructCineSequence::register,
            SecAt::register,
            SecRepeat::register,
            SecChoice::register,
            ExprSecNewScene::register,
            EffPlaySequence::register,
            // Scene control
            EffPlayScene::register,
            EffControl::register,
            EffStopAll::register,
            EffSeek::register,
            EffViewers::register,
            EffRequestSkip::register,
            EffRegister::register,
            EffMarker::register,
            EffSceneFlag::register,
            // Camera
            EffCameraKeyframe::register,
            EffCameraInterpolation::register,
            EffCameraShot::register,
            EffShake::register,
            EffZoom::register,
            EffScreen::register,
            EffCineTitle::register,
            // Actors and displays
            ExprNewActor::register,
            ExprNewPlayerActor::register,
            ExprActorOf::register,
            EffActorMove::register,
            EffActorWalk::register,
            EffActorRotate::register,
            EffActorLook::register,
            EffActorVisibility::register,
            EffActorRemove::register,
            EffActorAttach::register,
            EffActorKeyframe::register,
            EffActorScale::register,
            EffActorStop::register,
            EffGroupRotate::register,
            EffDisplayAnimate::register,
            EffDisplaySpin::register,
            // VFX, sound, dialogue
            EffPreset::register,
            EffDrawShape::register,
            EffEmitter::register,
            EffDrawLine::register,
            EffStopEffect::register,
            ExprLastEffect::register,
            EffCineSound::register,
            EffStopSound::register,
            EffDialogue::register,
            EffDialogueControl::register,
            // Expressions
            ExprSceneById::register,
            ExprPlayback::register,
            ExprViewers::register,
            ExprPlaybackTime::register,
            ExprPlaybackNumber::register,
            ExprPlaybackText::register,
            ExprSceneVariable::register,
            ExprEase::register,
            ExprActorEntity::register,
            ExprActorName::register,
            ExprActors::register,
            ExprCameraLocation::register,
            ExprEndLocation::register,
            ExprCineInfo::register,
            ExprCineCount::register,
            ExprChoice::register,
            // Conditions
            CondWatching::register,
            CondPlaybackState::register,
            CondSceneExists::register,
            CondActor::register,
            CondCapability::register,
            CondKnown::register,
            CondPlayerCamera::register,
            CondMarker::register,
            CondEffectActive::register,
            CondChoiceTimedOut::register,
            // Events
            EvtCineScene::register,
            EvtCineMarker::register,
            EvtCineCamera::register,
            EvtCineActor::register,
            EvtCineDialogue::register,
            EvtCineChoice::register,
        )
        CineEventValues.register(addon.registry(EventValueRegistry::class.java))
    }

    override fun name() = "cinematics"
}

object SkCineSkriptIntegration {
    private var hookRegistered = false

    /**
     * Registers SkCine with Skript. Must run during plugin enable, while Skript still accepts registrations.
     * @return null on success, or a reason why registration is impossible.
     */
    fun register(source: Class<*>): String? {
        if (!Skript.isAcceptRegistrations()) return "Skript no longer accepts syntax registrations (was SkCine loaded too late, e.g. by a plugin manager?)"
        val addon = Skript.instance().registerAddon(source, "SkCine")
        addon.loadModules(CineModule)
        if (!hookRegistered) {
            hookRegistered = true
            ScriptLoader.eventRegistry().register(
                ScriptLoader.ScriptUnloadEvent::class.java,
                ScriptLoader.ScriptUnloadEvent { _, script ->
                    val owner = ownerOf(script) ?: return@ScriptUnloadEvent
                    SkCineRuntime.getOrNull()?.onScriptUnloaded(owner)
                },
            )
        }
        return null
    }

    /** Clears Skript-side state on disable. */
    fun shutdown() {
        SequenceRegistry.clear()
        CallerLocals.current = LocalsSnapshot.EMPTY
    }
}
