package dev.dheaven.skcine.api.effect

import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.geometry.ShapeParams
import dev.dheaven.skcine.core.math.Vec3
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player

/**
 * One tick of a running visual effect. Implementations draw particles through [spawn]/[draw], which apply
 * audience filtering, distance culling, quality scaling and per-player particle budgets.
 */
interface VfxFrame {
    /** Current origin (follows an entity if the effect is attached to one). */
    val origin: Location

    /** Ticks since the effect started. */
    val age: Int

    /** Total lifetime in ticks. */
    val lifetime: Int

    /** Normalized progress 0..1. */
    val progress: Double get() = if (lifetime <= 0) 1.0 else (age.toDouble() / lifetime).coerceIn(0.0, 1.0)

    /** Primary color supplied by the script (or the preset default). */
    val color: Color

    /** Secondary color supplied by the script (or derived). */
    val accent: Color

    /** Size multiplier supplied by the script. */
    val scale: Double

    /** Quality multiplier in (0, 1.25]; scale point counts by this. */
    val density: Double

    val audience: Collection<Player>

    fun spawn(particle: Particle, at: Location, count: Int = 1, offset: Vec3 = Vec3.ZERO, speed: Double = 0.0, data: Any? = null)

    /** Draws a cached shape around [at], rotated by [yawDegrees] around the vertical axis. */
    fun draw(shape: Shape, params: ShapeParams, particle: Particle, at: Location = origin, yawDegrees: Double = 0.0, data: Any? = null)

    /** Dust options in [color] at [size]. */
    fun dust(color: Color = this.color, size: Float = 1.2f): Any
}

/** A visual effect program. [tick] returns false to finish early. */
fun interface VfxPreset {
    fun tick(frame: VfxFrame): Boolean
}

interface EffectRegistry {
    /**
     * Registers a preset usable from Skript (`play cine preset "<id>" ...`). Ids are lower-case; registering an id
     * that already exists replaces it.
     */
    fun registerPreset(id: String, defaultDurationTicks: Int, preset: VfxPreset)

    fun unregisterPreset(id: String): Boolean

    fun presets(): Set<String>
}
