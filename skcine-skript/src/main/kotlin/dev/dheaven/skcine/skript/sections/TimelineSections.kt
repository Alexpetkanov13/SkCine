package dev.dheaven.skcine.skript.sections

import ch.njol.skript.Skript
import ch.njol.skript.config.SectionNode
import ch.njol.skript.expressions.base.SectionExpression
import ch.njol.skript.lang.EffectSection
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.lang.Trigger
import ch.njol.skript.lang.TriggerItem
import ch.njol.skript.lang.util.SectionUtils
import ch.njol.skript.util.Timespan
import ch.njol.util.Kleenean
import dev.dheaven.skcine.core.scene.SceneId
import dev.dheaven.skcine.core.timeline.EventPolicy
import dev.dheaven.skcine.platform.dialogue.ChoiceOption
import dev.dheaven.skcine.platform.dialogue.ComponentText
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.skript.CineBuildEvent
import dev.dheaven.skcine.skript.CineChoiceSectionEvent
import dev.dheaven.skcine.skript.CineTimelineEvent
import dev.dheaven.skcine.skript.Ctx
import dev.dheaven.skcine.skript.LocalsSnapshot
import dev.dheaven.skcine.skript.applyToTarget
import dev.dheaven.skcine.skript.report
import dev.dheaven.skcine.skript.resolveTarget
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.runSection
import dev.dheaven.skcine.skript.structures.SequenceRegistry
import dev.dheaven.skcine.skript.structures.ownerOf
import dev.dheaven.skcine.skript.ticks
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.skriptlang.skript.registration.DefaultSyntaxInfos
import org.skriptlang.skript.registration.SyntaxInfo
import org.skriptlang.skript.registration.SyntaxRegistry

/** Shared parse-time validation for syntax that needs a scene (explicit or from context). */
internal fun requireScene(explicit: Expression<*>?, what: String): Boolean {
    if (explicit != null || Ctx.inSceneContext()) return true
    Skript.error("'$what' must be used inside a cine scene timeline/sequence, or given a scene with 'in {_scene}'")
    return false
}

private fun policyOf(mark: Int) = when (mark) {
    1 -> EventPolicy.GUARANTEED
    2 -> EventPolicy.COSMETIC
    else -> EventPolicy.ONCE
}

/**
 * `at 3 seconds:` / `after 2 seconds:` / `at marker "climax":` — run the indented code when playback reaches that
 * time. `even if skipped` guarantees it runs once even when the scene is skipped; `cosmetically` lets it run again
 * after rewinding. Code runs with a copy of the local variables from where the section was declared.
 */
class SecAt : EffectSection() {
    private lateinit var time: Expression<*>
    private var target: Expression<*>? = null
    private var mode = 0
    private var policy = EventPolicy.ONCE
    private var trigger: Trigger? = null
    private var where: String? = null

    override fun init(
        expressions: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
        sectionNode: SectionNode?,
        triggerItems: MutableList<TriggerItem>?,
    ): Boolean {
        mode = matchedPattern
        time = expressions[0]
        target = expressions[1]
        policy = policyOf(parseResult.mark)
        where = Ctx.here()
        if (!requireScene(target, "at")) return false
        if (sectionNode == null) {
            Skript.error("This needs an indented section with the code to run")
            return false
        }
        trigger =
            SectionUtils.loadLinkedCode(
                "cine timeline",
            ) { before, after -> loadCode(sectionNode, "cine timeline", before, after, CineTimelineEvent::class.java) }
        return trigger != null
    }

    override fun walk(event: Event): TriggerItem? {
        val trig = trigger
        val target = resolveTarget(event, this.target, where)
        if (trig != null && target != null) {
            val locals = LocalsSnapshot.of(event)
            val timeValue = time.getSingle(event)
            val building = event is CineBuildEvent
            applyToTarget(target, where) { inst, offset ->
                val t: Double =
                    when (mode) {
                        0 -> offset + ((timeValue as? Timespan)?.ticks() ?: return@applyToTarget "the time is not set")
                        1 ->
                            (if (building) offset else inst.position) +
                                ((timeValue as? Timespan)?.ticks() ?: return@applyToTarget "the time is not set")
                        else -> {
                            val name = timeValue as? String ?: return@applyToTarget "the marker name is not set"
                            inst.timeline.marker(name)?.time
                                ?: return@applyToTarget "unknown marker '$name' (add it first with 'add cine marker')"
                        }
                    }
                runCatching {
                    inst.timeline.addEvent(
                        t,
                        policy,
                        "line ${where ?: "?"}",
                    ) { reason -> runSection(trig, CineTimelineEvent(inst, reason), locals) }
                }.exceptionOrNull()?.message
            }
        }
        return super.walk(event, false)
    }

    override fun toString(event: Event?, debug: Boolean) = (if (mode == 1) "after " else "at ") + time.toString(event, debug)

    companion object {
        private const val FLAGS = " [(1:even if skipped|2:cosmetically)]"
        private const val TARGET = " [(of|in) %-cinescene/cineplayback%]"

        fun register(r: SyntaxRegistry) {
            r.register(
                SyntaxRegistry.SECTION,
                SyntaxInfo
                    .builder(SecAt::class.java)
                    .supplier { SecAt() }
                    .addPatterns(
                        "at %timespan%$TARGET$FLAGS",
                        "after %timespan%$TARGET$FLAGS",
                        "at [cine] marker %string%$TARGET$FLAGS",
                    ).build(),
            )
        }
    }
}

/** `repeat every 5 ticks [from 1 second] [until 4 seconds]:` — cosmetic repeating timeline code. */
class SecRepeat : EffectSection() {
    private lateinit var interval: Expression<Timespan>
    private var from: Expression<Timespan>? = null
    private var until: Expression<Timespan>? = null
    private var target: Expression<*>? = null
    private var trigger: Trigger? = null
    private var where: String? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
        sectionNode: SectionNode?,
        triggerItems: MutableList<TriggerItem>?,
    ): Boolean {
        interval = expressions[0] as Expression<Timespan>
        from = expressions[1] as Expression<Timespan>?
        until = expressions[2] as Expression<Timespan>?
        target = expressions[3]
        where = Ctx.here()
        if (!requireScene(target, "repeat every")) return false
        if (sectionNode == null) {
            Skript.error("This needs an indented section with the code to repeat")
            return false
        }
        trigger =
            SectionUtils.loadLinkedCode(
                "cine repeat",
            ) { before, after -> loadCode(sectionNode, "cine repeat", before, after, CineTimelineEvent::class.java) }
        return trigger != null
    }

    override fun walk(event: Event): TriggerItem? {
        val trig = trigger
        val target = resolveTarget(event, this.target, where)
        val iv = interval.getSingle(event)?.ticks()
        if (trig != null && target != null && iv != null) {
            val locals = LocalsSnapshot.of(event)
            val start = from?.getSingle(event)?.ticks()
            val end = until?.getSingle(event)?.ticks()
            val building = event is CineBuildEvent
            applyToTarget(target, where) { inst, offset ->
                val base =
                    if (building) {
                        offset
                    } else if (start == null) {
                        inst.position
                    } else {
                        0.0
                    }
                val s = base + (start ?: 0.0)
                val en = end?.let { base + it }
                runCatching {
                    inst.timeline.addRepeater(
                        s,
                        iv,
                        en,
                        "repeat ${where ?: ""}",
                    ) { reason -> runSection(trig, CineTimelineEvent(inst, reason), locals) }
                }.exceptionOrNull()?.message
            }
        }
        return super.walk(event, false)
    }

    override fun toString(event: Event?, debug: Boolean) = "repeat every " + interval.toString(event, debug)

    companion object {
        fun register(r: SyntaxRegistry) {
            r.register(
                SyntaxRegistry.SECTION,
                SyntaxInfo
                    .builder(SecRepeat::class.java)
                    .supplier { SecRepeat() }
                    .addPatterns("repeat every %timespan% [from %-timespan%] [(until|to) %-timespan%] [(of|in) %-cinescene/cineplayback%]")
                    .build(),
            )
        }
    }
}

/**
 * ```
 * present cine choice "Will you help?" to player with options "Accept" and "Decline" with timeout 15 seconds defaulting to "Decline" pausing the scene:
 *     if event-string is "Accept":
 *         ...
 * ```
 * The section runs once per selection (or once with the winning option for `as a vote`).
 */
class SecChoice : EffectSection() {
    private lateinit var prompt: Expression<String>
    private lateinit var players: Expression<*>
    private lateinit var options: Expression<String>
    private var timeout: Expression<Timespan>? = null
    private var default: Expression<String>? = null
    private var vote = false
    private var menu = false
    private var pause = false
    private var trigger: Trigger? = null
    private var where: String? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<out Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parseResult: ParseResult,
        sectionNode: SectionNode?,
        triggerItems: MutableList<TriggerItem>?,
    ): Boolean {
        prompt = expressions[0] as Expression<String>
        players = expressions[1]
        options = expressions[2] as Expression<String>
        timeout = expressions[3] as Expression<Timespan>?
        default = expressions[4] as Expression<String>?
        vote = parseResult.hasTag("vote")
        menu = parseResult.hasTag("menu")
        pause = parseResult.hasTag("pause")
        where = Ctx.here()
        if (pause && !Ctx.inSceneContext()) {
            Skript.error("'pausing the scene' can only be used inside a cine scene")
            return false
        }
        if (sectionNode != null) {
            trigger =
                SectionUtils.loadLinkedCode(
                    "cine choice",
                ) { before, after -> loadCode(sectionNode, "cine choice", before, after, CineChoiceSectionEvent::class.java) }
            if (trigger == null) return false
        }
        return true
    }

    override fun walk(event: Event): TriggerItem? {
        val inst = Ctx.instance(event)
        val audience =
            players
                .getArray(event)
                .flatMap {
                    when (it) {
                        is Player -> listOf(it)
                        is dev.dheaven.skcine.platform.scene.SceneInstance -> it.viewers
                        else -> emptyList()
                    }
                }.distinct()
        val promptText = prompt.getSingle(event)
        val opts = options.getArray(event).map { ChoiceOption(ComponentText.plain(ComponentText.parse(it)), ComponentText.parse(it)) }
        val locals = LocalsSnapshot.of(event)
        val trig = trigger
        if (promptText != null) {
            rt().choices.present(
                id = ComponentText.plain(ComponentText.parse(promptText)),
                prompt = ComponentText.parse(promptText),
                options = opts,
                audience = audience,
                timeoutTicks = timeout?.getSingle(event)?.ticks()?.toInt() ?: 0,
                defaultOption = default?.getSingle(event),
                vote = vote,
                owner = inst,
                pauseScene = pause,
                callback = { res ->
                    if (trig != null) runSection(trig, CineChoiceSectionEvent(inst, res.player, res.option?.id, res.timedOut), locals)
                },
                error = { report(where, it, inst) },
                menu = menu,
            )
        }
        return super.walk(event, false)
    }

    override fun toString(event: Event?, debug: Boolean) = "present cine choice " + prompt.toString(event, debug)

    companion object {
        fun register(r: SyntaxRegistry) {
            r.register(
                SyntaxRegistry.SECTION,
                SyntaxInfo
                    .builder(SecChoice::class.java)
                    .supplier { SecChoice() }
                    .addPatterns(
                        "present [a] cine choice %string% (to|for) %players/cineplaybacks% with options %strings% " +
                            "[with timeout %-timespan%] [defaulting to %-string%] [vote:as a vote] [menu:(in|using|via) [a|an] [inventory] menu] [pause:pausing [the] scene]",
                    ).build(),
            )
        }
    }
}

/**
 * `set {_s} to a new cine scene [named "id"]:` with an optional body. The body is the scene's timeline code: it runs
 * every time the scene plays, with a copy of the local variables from where the scene was created.
 */
class ExprSecNewScene : SectionExpression<SceneDefinition>() {
    private var id: Expression<String>? = null
    private var trigger: Trigger? = null
    private var owner: dev.dheaven.skcine.platform.scene.SceneOwner? = null
    private var where: String? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<out Expression<*>>,
        pattern: Int,
        delayed: Kleenean,
        result: ParseResult,
        node: SectionNode?,
        triggerItems: MutableList<TriggerItem>?,
    ): Boolean {
        id = expressions[0] as Expression<String>?
        owner = ownerOf(parser.currentScript)
        where = Ctx.here()
        if (node != null) {
            trigger =
                SectionUtils.loadLinkedCode(
                    "cine scene",
                ) { before, after -> loadCode(node, "cine scene", before, after, CineBuildEvent::class.java) }
            return trigger != null
        }
        return true
    }

    override fun get(event: Event): Array<SceneDefinition>? {
        val raw = id?.getSingle(event)
        val sceneId =
            if (raw == null) {
                SceneId.anonymous()
            } else {
                SceneId.parse(raw) { report(where, "invalid cine scene id: $it") } ?: return null
            }
        val def = SceneDefinition(sceneId, owner)
        val trig = trigger
        if (trig != null) {
            val locals = LocalsSnapshot.of(event)
            def.addStep { inst -> runSection(trig, CineBuildEvent(inst, 0.0), locals) }
        }
        return arrayOf(def)
    }

    override fun isSingle() = true

    override fun getReturnType() = SceneDefinition::class.java

    override fun toString(event: Event?, debug: Boolean) = "a new cine scene" + (id?.let { " named " + it.toString(event, debug) } ?: "")

    companion object {
        fun register(r: SyntaxRegistry) {
            r.register(
                SyntaxRegistry.EXPRESSION,
                DefaultSyntaxInfos.Expression
                    .builder(ExprSecNewScene::class.java, SceneDefinition::class.java)
                    .supplier { ExprSecNewScene() }
                    .addPatterns("[a] new cine scene [(named|with id) %-string%]")
                    .build(),
            )
        }
    }
}

/** `play cine sequence "reveal" [at 2 seconds]` — inlines a reusable sequence into the current timeline. */
class EffPlaySequence : ch.njol.skript.lang.Effect() {
    private lateinit var name: Expression<String>
    private var at: Expression<Timespan>? = null
    private var where: String? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(expressions: Array<out Expression<*>>, matchedPattern: Int, isDelayed: Kleenean, parseResult: ParseResult): Boolean {
        name = expressions[0] as Expression<String>
        at = expressions[1] as Expression<Timespan>?
        where = Ctx.here()
        if (!Ctx.inSceneContext()) {
            Skript.error("'play cine sequence' must be used inside a cine scene timeline, sequence or timed section")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val inst = Ctx.instance(event) ?: return
        val n = name.getSingle(event) ?: return
        val seq =
            SequenceRegistry.get(n)
                ?: return report(where, "unknown cine sequence '$n'; known: ${SequenceRegistry.names().joinToString()}", inst)
        val maxDepth = rt().settings.maxNestedSequences
        if (inst.buildDepth >= maxDepth) return report(where, "sequence nesting is deeper than $maxDepth (recursive sequence?)", inst)
        val base = if (event is CineBuildEvent) event.offset else inst.position
        val offset = base + (at?.getSingle(event)?.ticks() ?: 0.0)
        val child = CineBuildEvent(inst, offset)
        inst.buildDepth++
        try {
            ch.njol.skript.variables.Variables
                .withLocalVariables(event, child) { TriggerItem.walk(seq.trigger, child) }
        } finally {
            inst.buildDepth--
        }
    }

    override fun toString(event: Event?, debug: Boolean) = "play cine sequence " + name.toString(event, debug)

    companion object {
        fun register(r: SyntaxRegistry) {
            r.register(
                SyntaxRegistry.EFFECT,
                SyntaxInfo
                    .builder(EffPlaySequence::class.java)
                    .supplier { EffPlaySequence() }
                    .addPatterns("play [the] cine sequence %string% [at %-timespan%]")
                    .build(),
            )
        }
    }
}
