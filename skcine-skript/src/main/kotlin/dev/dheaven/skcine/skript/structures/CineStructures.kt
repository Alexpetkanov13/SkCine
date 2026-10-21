package dev.dheaven.skcine.skript.structures

import ch.njol.skript.ScriptLoader
import ch.njol.skript.Skript
import ch.njol.skript.config.SectionNode
import ch.njol.skript.lang.Literal
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.lang.Trigger
import ch.njol.skript.lang.util.SimpleEvent
import ch.njol.skript.util.Timespan
import dev.dheaven.skcine.api.event.ScenePhase
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.SceneId
import dev.dheaven.skcine.platform.ReloadPolicy
import dev.dheaven.skcine.platform.SkCineRuntime
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneOwner
import dev.dheaven.skcine.platform.scene.SceneRegistryImpl
import dev.dheaven.skcine.platform.scene.SceneSettings
import dev.dheaven.skcine.platform.scene.SkipMode
import dev.dheaven.skcine.skript.CallerLocals
import dev.dheaven.skcine.skript.CineBuildEvent
import dev.dheaven.skcine.skript.CineCallbackEvent
import dev.dheaven.skcine.skript.LocalsSnapshot
import dev.dheaven.skcine.skript.runSection
import dev.dheaven.skcine.skript.ticks
import org.bukkit.event.Event
import org.skriptlang.skript.lang.entry.EntryContainer
import org.skriptlang.skript.lang.entry.EntryValidator
import org.skriptlang.skript.lang.entry.util.LiteralEntryData
import org.skriptlang.skript.lang.script.Script
import org.skriptlang.skript.lang.structure.Structure
import org.skriptlang.skript.registration.DefaultSyntaxInfos
import org.skriptlang.skript.registration.SyntaxRegistry

/** Stable owner key for a script (its path), shared by structures and the unload hook. */
fun ownerOf(script: Script?): SceneOwner? {
    script ?: return null
    val cfg = script.config
    val path = runCatching { cfg.path?.toString() }.getOrNull() ?: cfg.fileName
    return SceneOwner(path.replace('\\', '/'))
}

/** Compiles a section node as code running with [eventClass] as the current event. */
internal fun compileSection(node: SectionNode, name: String, eventClass: Class<out Event>, script: Script): Trigger {
    val parser =
        ch.njol.skript.lang.parser.ParserInstance
            .get()
    parser.setCurrentEvent(name, eventClass)
    try {
        return Trigger(script, name, SimpleEvent(), ScriptLoader.loadItems(node))
    } finally {
        parser.deleteCurrentEvent()
    }
}

/**
 * ```
 * cine scene "rpg:intro":
 *     duration: 10 seconds        # optional; otherwise derived from the timeline
 *     skippable: true             # sneak to skip
 *     lock input: true            # block interactions/inventory while watching
 *     freeze: false               # block walking (for scenes without a camera)
 *     invulnerable: true
 *     restore location: true      # teleport viewers back afterwards
 *     speed: 1
 *     loops: 0                    # extra iterations; -1 = until stopped
 *     skip mode: individual       # group scenes: individual | anyone | vote
 *     timeline:
 *         # Skript code run each time the scene starts, building this playback's timeline
 *     on start:
 *     on finish:
 *     on skip:
 *     on cancel:                  # stopped, interrupted or failed
 *     on end:                     # always, after the outcome-specific entry
 * ```
 */
class StructCineScene : Structure() {
    private var id: SceneId? = null
    private var entries: EntryContainer? = null
    private var definition: SceneDefinition? = null
    private var line: Int? = null

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult, entryContainer: EntryContainer?): Boolean {
        val raw = args[0].single as? String ?: return false
        id = SceneId.parse(raw) { Skript.error("Invalid cine scene id: $it") } ?: return false
        entries = entryContainer
        line = entryContainer?.source?.line
        return true
    }

    override fun load(): Boolean {
        val sceneId = id ?: return false
        val container = entries ?: return false
        val script = parser.currentScript
        val owner = ownerOf(script)

        val duration = container.getOptional("duration", Timespan::class.java, false)
        val speed = container.getOptional("speed", Number::class.java, false)?.toDouble() ?: 1.0
        val loops = container.getOptional("loops", Number::class.java, false)?.toInt() ?: 0
        val skipModeRaw = container.getOptional("skip mode", String::class.java, false)
        val skipMode =
            skipModeRaw?.let {
                SkipMode.parse(it)
                    ?: return false.also { _ -> Skript.error("Unknown skip mode '$skipModeRaw' (use individual, anyone or vote)") }
            }
                ?: SkipMode.INDIVIDUAL
        if (!speed.isFinite() || speed <= 0 || speed > 10) {
            Skript.error("Scene speed must be between 0 and 10")
            return false
        }
        if (duration != null && duration.ticks() > SkCineRuntime.get().settings.maxSceneDurationSeconds * 20) {
            Skript.error("Scene duration exceeds the configured maximum of ${SkCineRuntime.get().settings.maxSceneDurationSeconds} seconds")
            return false
        }

        fun flag(key: String, def: Boolean) = container.getOptional(key, Boolean::class.javaObjectType, false) ?: def

        val settings =
            SceneSettings(
                durationTicks = duration?.ticks(),
                skippable = flag("skippable", true),
                lockInput = flag("lock input", true),
                freezeMovement = flag("freeze", false),
                invulnerable = flag("invulnerable", true),
                restoreLocation = flag("restore location", true),
                speed = speed,
                loops = loops.coerceIn(-1, 10_000),
                skipMode = skipMode,
            )
        val def = SceneDefinition(sceneId, owner, settings)
        def.sourceLine = line

        container.getOptional("timeline", SectionNode::class.java, false)?.let { node ->
            val trigger = compileSection(node, "cine scene ${sceneId.value} timeline", CineBuildEvent::class.java, script)
            def.addStep { inst -> runSection(trigger, CineBuildEvent(inst, 0.0), CallerLocals.current) }
        }
        val callbacks =
            listOf(
                "on start" to listOf(ScenePhase.START),
                "on finish" to listOf(ScenePhase.FINISH),
                "on skip" to listOf(ScenePhase.SKIP),
                "on cancel" to listOf(ScenePhase.CANCEL, ScenePhase.FAIL),
                "on end" to listOf(ScenePhase.END),
            )
        for ((key, phases) in callbacks) {
            val node = container.getOptional(key, SectionNode::class.java, false) ?: continue
            val trigger = compileSection(node, "cine scene ${sceneId.value} $key", CineCallbackEvent::class.java, script)
            phases.forEach { phase ->
                def.on(phase) { inst, reason -> runSection(trigger, CineCallbackEvent(inst, reason), LocalsSnapshot.EMPTY) }
            }
        }

        when (val r = SkCineRuntime.get().registry.register(def)) {
            is SceneRegistryImpl.Result.Duplicate -> {
                Skript.error(
                    "Duplicate cine scene id '${sceneId.value}': already defined by ${r.existing.sourceScript ?: "another source"}" +
                        (r.existing.sourceLine?.let { ", line $it" } ?: "") + ". Use a namespaced id such as 'myscript:${sceneId.name}'.",
                )
                return false
            }
            else -> Unit
        }
        definition = def
        return true
    }

    override fun unload() {
        val def = definition ?: return
        definition = null
        val rt = SkCineRuntime.getOrNull() ?: return
        rt.registry.unregister(def)
        if (rt.settings.reloadPolicy == ReloadPolicy.STOP) {
            rt.manager
                .active()
                .filter { it.definition === def }
                .forEach { rt.manager.end(it, EndReason.SCRIPT_UNLOADED) }
        }
    }

    override fun toString(event: Event?, debug: Boolean) = "cine scene \"${id?.value}\""

    companion object {
        private val VALIDATOR: EntryValidator =
            EntryValidator
                .builder()
                .addEntryData(LiteralEntryData("duration", null, true, Timespan::class.java))
                .addEntryData(LiteralEntryData("skippable", null, true, Boolean::class.javaObjectType))
                .addEntryData(LiteralEntryData("lock input", null, true, Boolean::class.javaObjectType))
                .addEntryData(LiteralEntryData("freeze", null, true, Boolean::class.javaObjectType))
                .addEntryData(LiteralEntryData("invulnerable", null, true, Boolean::class.javaObjectType))
                .addEntryData(LiteralEntryData("restore location", null, true, Boolean::class.javaObjectType))
                .addEntryData(LiteralEntryData("speed", null, true, Number::class.java))
                .addEntryData(LiteralEntryData("loops", null, true, Number::class.java))
                .addEntry("skip mode", null, true)
                .addSection("timeline", true)
                .addSection("on start", true)
                .addSection("on finish", true)
                .addSection("on skip", true)
                .addSection("on cancel", true)
                .addSection("on end", true)
                .build()

        fun register(registry: SyntaxRegistry) {
            registry.register(
                SyntaxRegistry.STRUCTURE,
                DefaultSyntaxInfos.Structure
                    .builder(StructCineScene::class.java)
                    .supplier { StructCineScene() }
                    .entryValidator(VALIDATOR)
                    .addPattern("cine scene %string%")
                    .build(),
            )
        }
    }
}

/** Registered reusable sequences: name → compiled body. */
object SequenceRegistry {
    class Sequence(val name: String, val owner: SceneOwner?, val trigger: Trigger)

    private val byName = HashMap<String, Sequence>()

    fun get(name: String) = byName[name.trim().lowercase()]

    fun register(seq: Sequence): Sequence? {
        val key = seq.name.lowercase()
        val existing = byName[key]
        if (existing != null && existing.owner != seq.owner) return existing
        byName[key] = seq
        return null
    }

    fun unregister(seq: Sequence) {
        if (byName[seq.name.lowercase()] === seq) byName.remove(seq.name.lowercase())
    }

    fun names(): Set<String> = byName.keys.toSet()

    fun clear() = byName.clear()
}

/**
 * ```
 * cine sequence "item-reveal":
 *     # Skript code; runs inside the calling scene's timeline with `play cine sequence "item-reveal" at 2 seconds`.
 *     # It sees the caller's local variables, so they act as parameters.
 * ```
 */
class StructCineSequence : Structure() {
    private var name: String? = null
    private var node: SectionNode? = null
    private var registered: SequenceRegistry.Sequence? = null

    override fun init(args: Array<out Literal<*>>, matchedPattern: Int, parseResult: ParseResult, entryContainer: EntryContainer?): Boolean {
        val raw = (args[0].single as? String)?.trim()?.lowercase() ?: return false
        if (raw.isEmpty() || raw.length > 64 || !raw.matches(Regex("[a-z0-9_.:/-]+"))) {
            Skript.error("Invalid cine sequence name '$raw' (use a-z, 0-9, _ - . : /)")
            return false
        }
        name = raw
        node = entryContainer?.source
        return true
    }

    override fun load(): Boolean {
        val n = name ?: return false
        val section = node ?: return false
        val script = parser.currentScript
        val trigger = compileSection(section, "cine sequence $n", CineBuildEvent::class.java, script)
        val seq = SequenceRegistry.Sequence(n, ownerOf(script), trigger)
        SequenceRegistry.register(seq)?.let {
            Skript.error("Duplicate cine sequence '$n': already defined by ${it.owner?.displayName ?: "another source"}")
            return false
        }
        registered = seq
        return true
    }

    override fun unload() {
        registered?.let { SequenceRegistry.unregister(it) }
        registered = null
    }

    override fun toString(event: Event?, debug: Boolean) = "cine sequence \"$name\""

    companion object {
        fun register(registry: SyntaxRegistry) {
            registry.register(
                SyntaxRegistry.STRUCTURE,
                DefaultSyntaxInfos.Structure
                    .builder(StructCineSequence::class.java)
                    .supplier { StructCineSequence() }
                    .addPattern("cine sequence %string%")
                    .build(),
            )
        }
    }
}
