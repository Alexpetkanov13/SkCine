package dev.dheaven.skcine.skript.effects

import ch.njol.skript.Skript
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.util.Timespan
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.SceneId
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneRegistryImpl
import dev.dheaven.skcine.platform.scene.SkipMode
import dev.dheaven.skcine.skript.CallerLocals
import dev.dheaven.skcine.skript.CineEffect
import dev.dheaven.skcine.skript.Ctx
import dev.dheaven.skcine.skript.LocalsSnapshot
import dev.dheaven.skcine.skript.applyToTarget
import dev.dheaven.skcine.skript.effect
import dev.dheaven.skcine.skript.expr
import dev.dheaven.skcine.skript.instancesOf
import dev.dheaven.skcine.skript.opt
import dev.dheaven.skcine.skript.report
import dev.dheaven.skcine.skript.resolveTarget
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.sections.requireScene
import dev.dheaven.skcine.skript.ticks
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.skriptlang.skript.registration.SyntaxRegistry

/** `play cine scene "welcome" for player [together]` */
class EffPlayScene : CineEffect() {
    private lateinit var scene: Expression<*>
    private lateinit var players: Expression<Player>
    private var together = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        scene = e[0]
        players = e.expr(1)
        together = result.hasTag("together")
        return true
    }

    override fun execute(event: Event) {
        val def =
            when (val s = scene.getSingle(event)) {
                is SceneDefinition -> s
                is String ->
                    rt().registry.get(s)
                        ?: return report(
                            where,
                            "unknown cine scene '$s'. Registered scenes: ${rt().registry.ids().joinToString().ifEmpty { "none" }}",
                        )
                else -> return report(where, "the cine scene to play is not set")
            }
        val viewers = players.getArray(event).toList()
        val previous = CallerLocals.current
        CallerLocals.current = LocalsSnapshot.of(event)
        try {
            rt().manager.play(def, viewers, together) { report(where, it) }
        } finally {
            CallerLocals.current = previous
        }
    }

    override val description = "play cine scene"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffPlayScene::class.java,
            ::EffPlayScene,
            "(play|start) [the] cine scene %cinescene/string% (for|to) %players% [together:(together|as [a] group)]",
        )
    }
}

/** Pause/resume/stop/skip/restart playbacks. */
class EffControl : CineEffect() {
    private var targets: Expression<*>? = null
    private var action = 0

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        action = result.mark
        targets = if (pattern == 2) null else e[0]
        if (pattern == 2 && !Ctx.inSceneContext()) {
            Skript.error("Name the scene to control (e.g. 'stop cine scene of player') when not inside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val m = rt().manager
        for (inst in instancesOf(event, targets)) {
            when (action) {
                1 -> m.pause(inst)
                2 -> m.resume(inst)
                3 -> m.end(inst, EndReason.STOPPED)
                4 -> m.skip(inst)
                5 -> m.restart(inst)
            }
        }
    }

    override val description = "control cine scene"

    companion object {
        private const val ACTIONS = "(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart)"

        fun register(r: SyntaxRegistry) = r.effect(
            EffControl::class.java,
            ::EffControl,
            "$ACTIONS [the] cine (scene|playback)[s] (of|for) %players/cineplaybacks%",
            "$ACTIONS [the] cine playback[s] %cineplaybacks%",
            "$ACTIONS [this|the current] cine (scene|playback)",
        )
    }
}

/** `stop all cine scenes` */
class EffStopAll : CineEffect() {
    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult) = true

    override fun execute(event: Event) = rt().manager.endAll(EndReason.STOPPED)

    override val description = "stop all cine scenes"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(EffStopAll::class.java, ::EffStopAll, "stop all [running] cine (scenes|playbacks)")
    }
}

/** `seek cine scene of player to 5 seconds` / `jump cine scene to marker "climax"` */
class EffSeek : CineEffect() {
    private var targets: Expression<*>? = null
    private lateinit var to: Expression<*>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        targets = e[0]
        to = e[1]
        if (targets == null && !Ctx.inSceneContext()) {
            Skript.error("Name the scene to seek when not inside a cine scene")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val dest = to.getSingle(event) ?: return
        for (inst in instancesOf(event, targets)) {
            val ticks =
                when (dest) {
                    is Timespan -> dest.ticks()
                    is String -> inst.timeline.marker(dest)?.time ?: return report(where, "unknown marker '$dest'", inst)
                    else -> continue
                }
            rt().manager.seek(inst, ticks)
        }
    }

    override val description = "seek cine scene"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffSeek::class.java,
            ::EffSeek,
            "(seek|jump) [the] cine (scene|playback)[s] [(of|for) %-players/cineplaybacks%] to %timespan%",
            "(seek|jump) [the] cine (scene|playback)[s] [(of|for) %-players/cineplaybacks%] to [the] [cine] marker %string%",
            "(seek|jump) [the] cine playback[s] %cineplaybacks% to %timespan%",
            "(seek|jump) [the] cine playback[s] %cineplaybacks% to [the] [cine] marker %string%",
        )
    }
}

/** Late joining and leaving: `add player to cine playback {_p}` / `remove player from their cine scene`. */
class EffViewers : CineEffect() {
    private lateinit var players: Expression<Player>
    private var target: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        players = e.expr(0)
        target = e.opt(1)
        return true
    }

    override fun execute(event: Event) {
        val m = rt().manager
        if (pattern == 0) {
            val inst = target?.getSingle(event) ?: Ctx.instance(event) ?: return report(where, "no cine playback to add viewers to")
            players.getArray(event).forEach {
                if (!m.addViewer(inst, it)) report(where, "${it.name} could not join (scene not running, already watching or full)", inst)
            }
        } else {
            players.getArray(event).forEach { p -> m.current(p)?.let { m.removeViewer(it, p, EndReason.STOPPED) } }
        }
    }

    override val description = "change cine viewers"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffViewers::class.java,
            ::EffViewers,
            "add %players% to [the] cine (playback|scene) [%-cineplayback%]",
            "remove %players% from (their|his|her|its) cine (scene|playback)",
            "restore [the] [normal] camera of %players%",
        )
    }
}

/** `request cine skip for player` — honors skippable/skip mode, for custom skip controls. */
class EffRequestSkip : CineEffect() {
    private lateinit var players: Expression<Player>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        players = e.expr(0)
        return true
    }

    override fun execute(event: Event) = players.getArray(event).forEach { rt().manager.requestSkip(it) }

    override val description = "request cine skip"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(EffRequestSkip::class.java, ::EffRequestSkip, "request [a] cine skip for %players%")
    }
}

/** `register cine scene {_s} [as "id"]` / `unregister cine scene "id"` / `copy cine scene ... as "id"` */
class EffRegister : CineEffect() {
    private lateinit var scene: Expression<*>
    private var id: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        scene = e[0]
        id = e.opt(1)
        return true
    }

    override fun execute(event: Event) {
        val reg = rt().registry
        when (pattern) {
            0 -> {
                val def = scene.getSingle(event) as? SceneDefinition ?: return
                val newId = id?.getSingle(event)
                val toRegister =
                    if (newId != null) {
                        val sid = SceneId.parse(newId) { report(where, "invalid cine scene id: $it") } ?: return
                        def.copy(sid)
                    } else {
                        def
                    }
                if (reg.register(
                        toRegister,
                    ) is SceneRegistryImpl.Result.Duplicate
                ) {
                    report(
                        where,
                        "a different script already registered cine scene '${toRegister.id}'",
                    )
                }
            }
            1 ->
                when (val s = scene.getSingle(event)) {
                    is SceneDefinition -> reg.unregister(s)
                    is String -> reg.get(s)?.let { reg.unregister(it) }
                }
        }
    }

    override val description = "register cine scene"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffRegister::class.java,
            ::EffRegister,
            "register [the] cine scene %cinescene% [as %-string%]",
            "unregister [the] cine scene %cinescene/string%",
        )
    }
}

/** `add cine marker "climax" at 5 seconds [in {_scene}]` */
class EffMarker : CineEffect() {
    private lateinit var name: Expression<String>
    private lateinit var time: Expression<Timespan>
    private var target: Expression<*>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        name = e.expr(0)
        time = e.expr(1)
        target = e[2]
        return requireScene(target, "add cine marker")
    }

    override fun execute(event: Event) {
        val n = name.getSingle(event) ?: return
        val t = time.getSingle(event)?.ticks() ?: return
        val target = resolveTarget(event, target, where) ?: return
        applyToTarget(target, where) { inst, offset -> runCatching { inst.timeline.addMarker(n, offset + t) }.exceptionOrNull()?.message }
    }

    override val description = "add cine marker"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffMarker::class.java,
            ::EffMarker,
            "add [a] cine marker %string% at %timespan% [(to|in|of) %-cinescene/cineplayback%]",
        )
    }
}

/**
 * Scene settings from code: `set cine duration of {_s} to 10 seconds` is an expression; this effect covers flags:
 * `make cine scene {_s} [not] skippable`, `set cine skip mode of {_s} to "vote"`.
 */
class EffSceneFlag : CineEffect() {
    private var target: Expression<*>? = null
    private var value: Expression<String>? = null
    private var negated = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        target = e[0]
        if (pattern == 1) value = e.opt(1)
        negated = result.hasTag("not")
        return requireScene(target, "cine scene settings")
    }

    override fun execute(event: Event) {
        val t = resolveTarget(event, target, where) ?: return
        val mode =
            value?.getSingle(event)?.let {
                SkipMode.parse(it)
                    ?: return report(where, "unknown skip mode '$it' (individual, anyone, vote)")
            }
        when (t) {
            is dev.dheaven.skcine.skript.SceneTarget.Definition ->
                t.def.settings =
                    if (pattern ==
                        0
                    ) {
                        t.def.settings.copy(skippable = !negated)
                    } else {
                        t.def.settings.copy(skipMode = mode ?: t.def.settings.skipMode)
                    }
            is dev.dheaven.skcine.skript.SceneTarget.Instance ->
                t.inst.settings =
                    if (pattern ==
                        0
                    ) {
                        t.inst.settings.copy(skippable = !negated)
                    } else {
                        t.inst.settings.copy(skipMode = mode ?: t.inst.settings.skipMode)
                    }
        }
    }

    override val description = "set cine scene flag"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffSceneFlag::class.java,
            ::EffSceneFlag,
            "make [the] cine (scene|playback) [%-cinescene/cineplayback%] [not:not] skippable",
            "set [the] cine skip mode of [the] cine (scene|playback) [%-cinescene/cineplayback%] to %string%",
        )
    }
}
