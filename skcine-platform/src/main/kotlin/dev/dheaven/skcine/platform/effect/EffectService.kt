package dev.dheaven.skcine.platform.effect

import dev.dheaven.skcine.api.effect.EffectRegistry
import dev.dheaven.skcine.api.effect.VfxFrame
import dev.dheaven.skcine.api.effect.VfxPreset
import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.geometry.ShapeCache
import dev.dheaven.skcine.core.geometry.ShapeParams
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.QualityMode
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.profiler.Profiler
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Who sees an effect. Resolved every tick so viewers joining/leaving are respected. */
fun interface AudienceSource {
    fun players(): Collection<Player>

    companion object {
        fun of(players: Collection<Player>): AudienceSource {
            val ids = players.map { it.uniqueId }
            return AudienceSource { ids.mapNotNull { Bukkit.getPlayer(it) } }
        }

        /** Everyone in the origin's world (still distance-culled). */
        val WORLD = AudienceSource { emptyList() }
    }
}

/** Handle to a running effect (Skript type `cine effect`). */
class RunningEffect internal constructor(
    val id: Long,
    val presetId: String,
    internal val preset: VfxPreset,
    private val anchorLocation: Location?,
    private val anchorEntity: UUID?,
    val lifetime: Int,
    val color: Color,
    val accent: Color,
    val scale: Double,
    val audience: AudienceSource,
    val owner: SceneInstance?,
) {
    var age = 0
        internal set
    var stopped = false
        internal set

    /** Current origin; null when an attached entity is gone. */
    fun origin(): Location? {
        if (anchorEntity != null) {
            val e = Bukkit.getEntity(anchorEntity)?.takeIf { it.isValid } ?: return null
            return e.location
        }
        return anchorLocation?.clone()
    }

    val isActive get() = !stopped && age < lifetime

    override fun toString() = "cine effect '$presetId' #$id"
}

class EffectService(private val settings: () -> SkCineSettings, private val diagnostics: Diagnostics, private val profiler: Profiler) : EffectRegistry {
    private data class PresetEntry(val defaultDuration: Int, val preset: VfxPreset)

    private val presets = LinkedHashMap<String, PresetEntry>()
    private val running = LinkedHashMap<Long, RunningEffect>()
    private var nextId = 0L
    val shapes = ShapeCache()

    /** Particles sent to each player during the current tick (budget accounting). */
    private val sentThisTick = HashMap<UUID, Int>()

    /** Adaptive density for AUTO quality, updated from server tick time. */
    var autoDensity = 1.0
        private set

    val count get() = running.size

    init {
        BuiltInPresets.registerAll(this)
    }

    override fun registerPreset(id: String, defaultDurationTicks: Int, preset: VfxPreset) {
        require(id.isNotBlank()) { "preset id must not be blank" }
        require(defaultDurationTicks in 1..72_000) { "default duration must be 1..72000 ticks" }
        presets[normalize(id)] = PresetEntry(defaultDurationTicks, preset)
    }

    override fun unregisterPreset(id: String) = presets.remove(normalize(id)) != null

    override fun presets(): Set<String> = presets.keys.toSet()

    fun hasPreset(id: String) = presets.containsKey(normalize(id))

    fun defaultDuration(id: String) = presets[normalize(id)]?.defaultDuration

    private fun normalize(id: String) = id
        .trim()
        .lowercase()
        .replace('_', '-')
        .replace(' ', '-')

    fun density(): Double {
        val q = settings().quality
        return if (q == QualityMode.AUTO) autoDensity else q.density
    }

    /**
     * Starts a preset at a location or attached to an entity. Returns null with [error] invoked when the preset is
     * unknown or a budget is exhausted.
     */
    fun play(
        presetId: String,
        at: Location?,
        attachTo: Entity?,
        durationTicks: Int?,
        color: Color?,
        accent: Color?,
        scale: Double,
        audience: AudienceSource,
        owner: SceneInstance?,
        error: (String) -> Unit,
    ): RunningEffect? {
        val entry =
            presets[normalize(presetId)]
                ?: return null.also { error("unknown cine preset '$presetId'; available: ${presets.keys.joinToString()}") }
        return start(
            presetId,
            entry.preset,
            at,
            attachTo,
            durationTicks ?: entry.defaultDuration,
            color,
            accent,
            scale,
            audience,
            owner,
            error,
        )
    }

    fun start(
        id: String,
        preset: VfxPreset,
        at: Location?,
        attachTo: Entity?,
        durationTicks: Int,
        color: Color?,
        accent: Color?,
        scale: Double,
        audience: AudienceSource,
        owner: SceneInstance?,
        error: (String) -> Unit,
    ): RunningEffect? {
        val s = settings()
        if (running.size >= s.maxEffects) return null.also { error("the server-wide effect limit (${s.maxEffects}) is reached") }
        if (owner != null && running.values.count { it.owner === owner } >= s.maxEffectsPerScene) {
            return null.also { error("this scene already runs the maximum of ${s.maxEffectsPerScene} effects") }
        }
        if (at == null && attachTo == null) return null.also { error("an effect needs a location or an entity") }
        if (at != null && at.world == null) return null.also { error("effect location has no world") }
        if (!scale.isFinite() || scale <= 0 || scale > 64) return null.also { error("effect scale must be between 0 and 64") }
        val c = color ?: Color.fromRGB(255, 215, 90)
        val fx =
            RunningEffect(
                ++nextId,
                id,
                preset,
                at?.clone(),
                attachTo?.uniqueId,
                durationTicks.coerceIn(1, 72_000),
                c,
                accent ?: c.mixColors(Color.WHITE),
                scale,
                audience,
                owner,
            )
        running[fx.id] = fx
        owner?.own(EffectResource(fx))
        return fx
    }

    fun stop(fx: RunningEffect) {
        fx.stopped = true
        running.remove(fx.id)
    }

    fun stopWhere(predicate: (RunningEffect) -> Boolean) {
        running.values.filter(predicate).forEach { stop(it) }
    }

    fun stopAll() {
        running.values.forEach { it.stopped = true }
        running.clear()
    }

    fun tick() {
        sentThisTick.clear()
        updateAutoDensity()
        if (running.isEmpty()) return
        val it = running.values.iterator()
        while (it.hasNext()) {
            val fx = it.next()
            if (fx.stopped || fx.age >= fx.lifetime) {
                fx.stopped = true
                it.remove()
                continue
            }
            val origin = fx.origin()
            if (origin == null) {
                fx.stopped = true
                it.remove()
                continue
            }
            val keep =
                try {
                    fx.preset.tick(Frame(fx, origin))
                } catch (t: Throwable) {
                    diagnostics.error("Effect '${fx.presetId}' failed and was stopped", "fx-error:${fx.presetId}", t)
                    false
                }
            fx.age++
            if (!keep) {
                fx.stopped = true
                it.remove()
            }
        }
    }

    private fun updateAutoDensity() {
        if (settings().quality != QualityMode.AUTO) return
        val mspt = runCatching { Bukkit.getServer().averageTickTime }.getOrDefault(20.0)
        val target =
            when {
                mspt > 45 -> 0.3
                mspt > 40 -> 0.5
                mspt > 30 -> 0.75
                else -> 1.0
            }
        // Smooth changes so density does not flicker.
        autoDensity += (target - autoDensity) * 0.05
    }

    /** Draws a shape once (no running effect). Returns the number of particles sent. */
    fun drawOnce(shape: Shape, params: ShapeParams, particle: Particle, data: Any?, at: Location, yaw: Double, audience: AudienceSource, color: Color = Color.WHITE): Int {
        val frame = OneShotFrame(at, audience, color)
        frame.draw(shape, params, particle, at, yaw, data)
        return frame.sent
    }

    /** Sends one particle burst to the audience, applying culling and budgets. Returns particles actually sent. */
    internal fun send(audience: AudienceSource, particle: Particle, at: Location, count: Int, offset: Vec3, speed: Double, data: Any?): Int {
        val world = at.world ?: return 0
        val s = settings()
        val maxDist = s.particleViewDistance * s.particleViewDistance
        val explicit = audience.players()
        val receivers = if (audience === AudienceSource.WORLD) world.players else explicit
        val resolvedData = resolveData(particle, data) ?: if (particle.dataType != Void::class.java) return 0 else null
        var sent = 0
        for (p in receivers) {
            if (p.world != world) continue
            if (p.location.distanceSquared(at) > maxDist) continue
            val used = sentThisTick[p.uniqueId] ?: 0
            if (used + count > s.maxParticlesPerPlayerPerTick) {
                profiler.particlesDropped.add(count.toLong())
                continue
            }
            sentThisTick[p.uniqueId] = used + count
            p.spawnParticle(particle, at.x, at.y, at.z, count, offset.x, offset.y, offset.z, speed, resolvedData, true)
            sent += count
        }
        profiler.particlesSent.add(sent.toLong())
        return sent
    }

    /**
     * Supplies sensible default data for particles that require it, or validates provided data.
     * Returns null when the particle needs data that cannot be provided.
     */
    fun resolveData(particle: Particle, data: Any?, color: Color = Color.WHITE): Any? {
        val type = particle.dataType
        if (type == Void::class.java) return null
        if (data != null && type.isInstance(data)) return data
        val c = (data as? Color) ?: color
        return when (type) {
            Particle.DustOptions::class.java -> Particle.DustOptions(c, 1.2f)
            Particle.DustTransition::class.java -> Particle.DustTransition(c, c.mixColors(Color.WHITE), 1.2f)
            Color::class.java -> c
            Float::class.javaObjectType -> 0f
            Int::class.javaObjectType -> 0
            else -> {
                diagnostics.warn(
                    "Particle '${particle.key.key}' needs ${type.simpleName} data, which this effect cannot supply; skipped",
                    "particle-data:${particle.name}",
                )
                null
            }
        }
    }

    private open inner class BaseFrame(
        override val origin: Location,
        override val audience: Collection<Player>,
        private val source: AudienceSource,
        override val color: Color,
        override val accent: Color,
        override val scale: Double,
    ) : VfxFrame {
        var sent = 0
        override val age: Int get() = 0
        override val lifetime: Int get() = 1
        override val density: Double get() = density()

        override fun spawn(particle: Particle, at: Location, count: Int, offset: Vec3, speed: Double, data: Any?) {
            sent += send(source, particle, at, count.coerceIn(0, 1000), offset, speed, data ?: resolveData(particle, null, color))
        }

        override fun draw(shape: Shape, params: ShapeParams, particle: Particle, at: Location, yawDegrees: Double, data: Any?) {
            val scaled = params.copy(points = max(1, (params.points * density).roundToInt()).coerceAtMost(4096))
            val pts = shapes.get(shape, scaled)
            val a = Math.toRadians(yawDegrees)
            val ca = cos(a)
            val sa = sin(a)
            val d = data ?: resolveData(particle, null, color)
            val loc = at.clone()
            for (p in pts) {
                loc.set(at.x + p.x * ca - p.z * sa, at.y + p.y, at.z + p.x * sa + p.z * ca)
                sent += send(source, particle, loc, 1, Vec3.ZERO, 0.0, d)
            }
        }

        override fun dust(color: Color, size: Float): Any = Particle.DustOptions(color, size.coerceIn(0.01f, 4f))
    }

    private inner class Frame(val fx: RunningEffect, origin: Location) : BaseFrame(origin, fx.audience.players(), fx.audience, fx.color, fx.accent, fx.scale) {
        override val age get() = fx.age
        override val lifetime get() = fx.lifetime
    }

    private inner class OneShotFrame(origin: Location, source: AudienceSource, color: Color) : BaseFrame(origin, source.players(), source, color, color, 1.0)

    private inner class EffectResource(val fx: RunningEffect) : SceneResource {
        override fun dispose(reason: EndReason) = stop(fx)
    }
}
