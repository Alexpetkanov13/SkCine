package dev.dheaven.skcine.platform.effect

import dev.dheaven.skcine.api.effect.VfxFrame
import dev.dheaven.skcine.api.effect.VfxPreset
import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.geometry.ShapeParams
import dev.dheaven.skcine.core.math.Vec3
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Universal cinematic presets. Every preset reads its colour, accent, scale and lifetime from the script, so the same
 * preset adapts to any server theme. All are built only from vanilla particles.
 */
object BuiltInPresets {
    private fun VfxFrame.at(dx: Double = 0.0, dy: Double = 0.0, dz: Double = 0.0): Location = origin.clone().add(dx * scale, dy * scale, dz * scale)

    private fun VfxFrame.pts(n: Int) = max(1, (n * density).roundToInt())

    private fun p(radius: Double, points: Int, height: Double = 2.0, turns: Double = 2.0, sides: Int = 5) = ShapeParams(
        radius = radius.coerceIn(0.0, 256.0),
        points = points.coerceIn(1, 4096),
        height = height.coerceIn(-256.0, 256.0),
        turns = turns,
        sides = sides,
    )

    fun registerAll(r: EffectService) {
        r.registerPreset("magic-reveal", 60, magicReveal)
        r.registerPreset("legendary-reveal", 80, legendaryReveal)
        r.registerPreset("secret-reveal", 50, secretReveal)
        r.registerPreset("summoning-circle", 100, summoningCircle)
        r.registerPreset("teleport", 30, teleport)
        r.registerPreset("portal-opening", 80, portalOpening)
        r.registerPreset("energy-beam", 40, energyBeam)
        r.registerPreset("fire-burst", 20, fireBurst)
        r.registerPreset("shockwave", 25, shockwave)
        r.registerPreset("level-up", 40, levelUp)
        r.registerPreset("boss-entrance", 100, bossEntrance)
        r.registerPreset("victory", 60, victory)
        r.registerPreset("meteor-impact", 50, meteorImpact)
        r.registerPreset("lightning-burst", 20, lightningBurst)
        r.registerPreset("smoke-cloud", 60, smokeCloud)
        r.registerPreset("ice-storm", 80, iceStorm)
        r.registerPreset("runes", 100, runes)
        r.registerPreset("golden-aura", 80, goldenAura)
        r.registerPreset("power-up", 60, powerUp)
        r.registerPreset("explosion", 15, explosion)
        r.registerPreset("vortex", 80, vortex)
        r.registerPreset("heart-burst", 30, heartBurst)
    }

    val magicReveal =
        VfxPreset { f ->
            val r = 1.5 * f.scale * (1 - f.progress * 0.6)
            f.draw(Shape.CIRCLE, p(r, f.pts(24)), Particle.DUST, f.at(dy = 0.1), f.age * 9.0, f.dust())
            f.draw(Shape.HELIX, p(r * 0.8, f.pts(16), height = 2.0 * f.scale, turns = 1.5), Particle.ENCHANT, f.origin, f.age * -12.0)
            if (f.age % 5 == 0) f.spawn(Particle.END_ROD, f.at(dy = 1.0), 3, Vec3(0.3, 0.5, 0.3), 0.02)
            if (f.age == f.lifetime - 1) f.spawn(Particle.TOTEM_OF_UNDYING, f.at(dy = 1.0), f.pts(60), Vec3(0.4, 0.6, 0.4), 0.4)
            true
        }

    val legendaryReveal =
        VfxPreset { f ->
            val build = (f.progress / 0.8).coerceAtMost(1.0)
            f.draw(
                Shape.DOUBLE_HELIX,
                p(1.2 * f.scale, f.pts(24), height = 3.0 * f.scale * build, turns = 2.0),
                Particle.DUST,
                f.origin,
                f.age * 10.0,
                f.dust(f.color, 1.4f),
            )
            f.draw(Shape.RING, p(2.0 * f.scale, f.pts(32)), Particle.DUST, f.at(dy = 0.05), -f.age * 6.0, f.dust(f.accent, 1.0f))
            if (f.age % 4 == 0) f.spawn(Particle.END_ROD, f.at(dy = 1.5), 2, Vec3(0.6, 1.0, 0.6), 0.01)
            if (f.progress >= 0.8 && f.age % 3 == 0) {
                f.draw(Shape.SPHERE, p(2.5 * f.scale * (f.progress - 0.6), f.pts(40)), Particle.FIREWORK, f.at(dy = 1.5))
            }
            if (f.age == f.lifetime - 1) {
                f.spawn(Particle.TOTEM_OF_UNDYING, f.at(dy = 1.5), f.pts(120), Vec3(0.5, 0.8, 0.5), 0.6)
                f.spawn(Particle.FLASH, f.at(dy = 1.5), 1)
            }
            true
        }

    val secretReveal =
        VfxPreset { f ->
            f.draw(Shape.SPHERE, p(1.5 * f.scale * (1 - f.progress), f.pts(30)), Particle.SMOKE, f.at(dy = 1.0))
            if (f.age % 3 == 0) f.spawn(Particle.WITCH, f.at(dy = 1.0), 4, Vec3(0.4, 0.4, 0.4), 0.0)
            if (f.age == f.lifetime - 1) f.spawn(Particle.REVERSE_PORTAL, f.at(dy = 1.0), f.pts(80), Vec3(0.3, 0.3, 0.3), 0.6)
            true
        }

    val summoningCircle =
        VfxPreset { f ->
            val r = 2.5 * f.scale
            val appear = (f.progress * 3).coerceAtMost(1.0)
            f.draw(Shape.CIRCLE, p(r, f.pts((48 * appear).toInt().coerceAtLeast(1))), Particle.DUST, f.at(dy = 0.05), f.age * 2.0, f.dust())
            f.draw(Shape.CIRCLE, p(r * 0.7, f.pts(32)), Particle.DUST, f.at(dy = 0.05), -f.age * 3.0, f.dust(f.accent, 1.0f))
            f.draw(Shape.STAR, p(r * 0.7, f.pts(40), sides = 5), Particle.DUST, f.at(dy = 0.06), f.age * 2.0, f.dust())
            if (f.age % 6 == 0) f.spawn(Particle.SOUL_FIRE_FLAME, f.at(dy = 0.2), 6, Vec3(r * 0.4, 0.05, r * 0.4), 0.01)
            if (f.progress >
                0.75
            ) {
                f.draw(
                    Shape.VORTEX,
                    p(r * 0.6, f.pts(20), height = 3.0 * f.scale, turns = 3.0),
                    Particle.PORTAL,
                    f.origin,
                    f.age * 15.0,
                )
            }
            true
        }

    val teleport =
        VfxPreset { f ->
            val y = 2.0 * f.scale * f.progress
            f.draw(Shape.CIRCLE, p(0.8 * f.scale, f.pts(18)), Particle.PORTAL, f.at(dy = y), f.age * 20.0)
            f.draw(Shape.CIRCLE, p(0.8 * f.scale, f.pts(18)), Particle.REVERSE_PORTAL, f.at(dy = 2.0 * f.scale - y), -f.age * 20.0)
            if (f.age == 0 || f.age == f.lifetime - 1) f.spawn(Particle.FLASH, f.at(dy = 1.0), 1)
            true
        }

    val portalOpening =
        VfxPreset { f ->
            val r = 2.0 * f.scale * (f.progress * 1.5).coerceAtMost(1.0)
            f.draw(Shape.VORTEX, p(r, f.pts(40), height = 0.2, turns = 4.0), Particle.PORTAL, f.at(dy = 1.5), f.age * 18.0)
            f.draw(Shape.CIRCLE, p(r, f.pts(36)), Particle.DUST, f.at(dy = 1.5), -f.age * 5.0, f.dust())
            if (f.age % 2 == 0) f.spawn(Particle.REVERSE_PORTAL, f.at(dy = 1.5), 6, Vec3(r * 0.3, r * 0.3, r * 0.3), 0.05)
            true
        }

    val energyBeam =
        VfxPreset { f ->
            val height = 12.0 * f.scale
            val pts = Shape.line(Vec3.ZERO, Vec3(0.0, height, 0.0), 0.35 / f.density.coerceAtLeast(0.2))
            val base = f.origin
            for (v in pts) f.spawn(Particle.DUST, base.clone().add(v.x, v.y, v.z), 1, Vec3.ZERO, 0.0, f.dust(f.color, 1.6f))
            f.draw(Shape.HELIX, p(0.6 * f.scale, f.pts(24), height = height, turns = 4.0), Particle.END_ROD, base, f.age * 25.0)
            if (f.age % 4 == 0) f.draw(Shape.RING, p(1.2 * f.scale, f.pts(20)), Particle.ELECTRIC_SPARK, base, f.age * 7.0)
            true
        }

    val fireBurst =
        VfxPreset { f ->
            if (f.age == 0) {
                f.spawn(Particle.FLAME, f.at(dy = 0.8), f.pts(80), Vec3(0.2, 0.2, 0.2), 0.25)
                f.spawn(Particle.LAVA, f.at(dy = 0.8), f.pts(12), Vec3(0.5, 0.3, 0.5), 0.0)
            }
            f.draw(Shape.CIRCLE, p(3.0 * f.scale * f.progress, f.pts(32)), Particle.FLAME, f.at(dy = 0.1), 0.0)
            true
        }

    val shockwave =
        VfxPreset { f ->
            val r = 6.0 * f.scale * f.progress
            f.draw(Shape.CIRCLE, p(r, f.pts((16 + r * 10).toInt())), Particle.CLOUD, f.at(dy = 0.1), 0.0)
            f.draw(Shape.CIRCLE, p(r * 0.92, f.pts((12 + r * 6).toInt())), Particle.DUST, f.at(dy = 0.3), 0.0, f.dust(f.color, 1.5f))
            if (f.age == 0) f.spawn(Particle.EXPLOSION, f.at(dy = 0.5), 1)
            true
        }

    val levelUp =
        VfxPreset { f ->
            f.draw(
                Shape.HELIX,
                p(0.9 * f.scale, f.pts(14), height = 2.5 * f.scale * f.progress, turns = 1.0),
                Particle.HAPPY_VILLAGER,
                f.origin,
                f.age * 18.0,
            )
            f.draw(Shape.CIRCLE, p(1.0 * f.scale, f.pts(18)), Particle.DUST, f.at(dy = 2.5 * f.scale * f.progress), f.age * -10.0, f.dust())
            if (f.age == f.lifetime - 1) {
                f.spawn(Particle.TOTEM_OF_UNDYING, f.at(dy = 2.0), f.pts(70), Vec3(0.3, 0.3, 0.3), 0.5)
                f.spawn(Particle.FIREWORK, f.at(dy = 2.2), f.pts(30), Vec3(0.2, 0.2, 0.2), 0.15)
            }
            true
        }

    val bossEntrance =
        VfxPreset { f ->
            val r = 4.0 * f.scale
            f.draw(Shape.CIRCLE, p(r, f.pts(48)), Particle.DUST, f.at(dy = 0.1), f.age * 3.0, f.dust(f.color, 2.0f))
            if (f.age % 2 ==
                0
            ) {
                f.draw(Shape.VORTEX, p(r, f.pts(30), height = 5.0 * f.scale, turns = 2.0), Particle.LARGE_SMOKE, f.origin, f.age * 12.0)
            }
            if (f.age % 10 == 0) f.spawn(Particle.SOUL, f.at(dy = 1.0), 10, Vec3(r * 0.4, 0.5, r * 0.4), 0.02)
            if (f.age == f.lifetime - 1) {
                f.spawn(Particle.EXPLOSION_EMITTER, f.at(dy = 1.0), 1)
                f.draw(Shape.SPHERE, p(3.0 * f.scale, f.pts(80)), Particle.DUST, f.at(dy = 1.5), 0.0, f.dust(f.accent, 2.0f))
            }
            true
        }

    val victory =
        VfxPreset { f ->
            if (f.age % 8 == 0) {
                val a = f.age * 0.7
                val pos = f.at(dx = cos(a) * 3.0, dy = 3.0 + sin(a * 2) * 1.0, dz = sin(a) * 3.0)
                f.draw(
                    Shape.SPHERE,
                    p(1.2 * f.scale, f.pts(36)),
                    Particle.DUST,
                    pos,
                    0.0,
                    f.dust(
                        if ((f.age / 8) % 2 ==
                            0
                        ) {
                            f.color
                        } else {
                            f.accent
                        },
                        1.3f,
                    ),
                )
                f.spawn(Particle.FIREWORK, pos, f.pts(20), Vec3(0.2, 0.2, 0.2), 0.12)
            }
            if (f.age % 3 == 0) f.spawn(Particle.TOTEM_OF_UNDYING, f.at(dy = 0.5), 4, Vec3(1.5, 0.5, 1.5), 0.2)
            true
        }

    val meteorImpact =
        VfxPreset { f ->
            val fall = (f.progress / 0.6).coerceAtMost(1.0)
            if (fall < 1.0) {
                val pos = f.at(dx = 6.0 * (1 - fall), dy = 15.0 * (1 - fall), dz = 3.0 * (1 - fall))
                f.spawn(Particle.FLAME, pos, f.pts(10), Vec3(0.3, 0.3, 0.3), 0.02)
                f.spawn(Particle.LARGE_SMOKE, pos, f.pts(4), Vec3(0.3, 0.3, 0.3), 0.01)
            } else {
                val t = (f.progress - 0.6) / 0.4
                if (t < 0.05) {
                    f.spawn(Particle.EXPLOSION_EMITTER, f.at(dy = 0.5), 1)
                    f.spawn(Particle.LAVA, f.at(dy = 0.5), f.pts(20), Vec3(1.0, 0.5, 1.0), 0.0)
                }
                f.draw(Shape.CIRCLE, p(5.0 * f.scale * t, f.pts(36)), Particle.CAMPFIRE_COSY_SMOKE, f.at(dy = 0.2), 0.0)
            }
            true
        }

    val lightningBurst =
        VfxPreset { f ->
            if (f.age % 4 == 0) {
                val seed = f.age.toLong() * 31
                val top = Vec3(0.0, 10.0 * f.scale, 0.0)
                val path = Shape.lightning(top, Vec3.ZERO, 8, 0.8 * f.scale, seed)
                for (v in path) f.spawn(Particle.ELECTRIC_SPARK, f.origin.clone().add(v.x, v.y, v.z), 1)
                f.spawn(Particle.FLASH, f.at(dy = 1.0), 1)
            }
            true
        }

    val smokeCloud =
        VfxPreset { f ->
            if (f.age % 2 ==
                0
            ) {
                f.draw(
                    Shape.SPHERE,
                    p(2.0 * f.scale * (0.5 + f.progress), f.pts(30)),
                    Particle.LARGE_SMOKE,
                    f.at(dy = 1.0),
                    f.age * 4.0,
                )
            }
            true
        }

    val iceStorm =
        VfxPreset { f ->
            f.draw(
                Shape.VORTEX,
                p(3.5 * f.scale, f.pts(36), height = 4.0 * f.scale, turns = 3.0),
                Particle.SNOWFLAKE,
                f.origin,
                f.age * 14.0,
            )
            if (f.age % 3 == 0) f.spawn(Particle.ITEM_SNOWBALL, f.at(dy = 2.0), 6, Vec3(2.5, 1.5, 2.5), 0.05)
            f.draw(
                Shape.CIRCLE,
                p(3.5 * f.scale, f.pts(24)),
                Particle.DUST,
                f.at(dy = 0.1),
                -f.age * 4.0,
                f.dust(Color.fromRGB(170, 220, 255), 1.2f),
            )
            true
        }

    val runes =
        VfxPreset { f ->
            val count = 6
            for (i in 0 until count) {
                val a = f.age * 0.05 + i * 2 * PI / count
                val pos = f.at(dx = cos(a) * 2.0, dy = 1.5 + sin(f.age * 0.1 + i) * 0.3, dz = sin(a) * 2.0)
                f.draw(
                    Shape.POLYGON,
                    p(0.35 * f.scale, f.pts(12), sides = 3 + i % 3),
                    Particle.DUST,
                    pos,
                    f.age * 8.0,
                    f.dust(
                        if (i % 2 ==
                            0
                        ) {
                            f.color
                        } else {
                            f.accent
                        },
                        0.8f,
                    ),
                )
                if (f.age % 5 == 0) f.spawn(Particle.ENCHANT, pos, 3, Vec3(0.2, 0.2, 0.2), 0.5)
            }
            true
        }

    val goldenAura =
        VfxPreset { f ->
            f.draw(Shape.CIRCLE, p(0.9 * f.scale, f.pts(16)), Particle.DUST, f.at(dy = 0.1 + (f.age % 20) / 10.0), f.age * 9.0, f.dust())
            if (f.age % 4 == 0) f.spawn(Particle.WAX_ON, f.at(dy = 1.0), 3, Vec3(0.4, 0.6, 0.4), 0.0)
            true
        }

    val powerUp =
        VfxPreset { f ->
            val r = 2.0 * f.scale * (1 - f.progress)
            f.draw(Shape.SPHERE, p(r.coerceAtLeast(0.1), f.pts(40)), Particle.DUST, f.at(dy = 1.0), f.age * 10.0, f.dust())
            if (f.age % 2 == 0) f.spawn(Particle.ELECTRIC_SPARK, f.at(dy = 1.0), 4, Vec3(r * 0.5, r * 0.5, r * 0.5), 0.0)
            if (f.age == f.lifetime - 1) {
                f.spawn(Particle.FLASH, f.at(dy = 1.0), 1)
                f.draw(Shape.SPHERE, p(2.5 * f.scale, f.pts(60)), Particle.END_ROD, f.at(dy = 1.0))
            }
            true
        }

    val explosion =
        VfxPreset { f ->
            if (f.age == 0) {
                f.spawn(Particle.EXPLOSION_EMITTER, f.origin, 1)
                f.spawn(Particle.FLAME, f.origin, f.pts(50), Vec3(0.3, 0.3, 0.3), 0.3)
            }
            f.draw(Shape.SPHERE, p(4.0 * f.scale * f.progress, f.pts(50)), Particle.LARGE_SMOKE, f.origin)
            true
        }

    val vortex =
        VfxPreset { f ->
            f.draw(
                Shape.VORTEX,
                p(2.0 * f.scale, f.pts(40), height = 3.0 * f.scale, turns = 3.0),
                Particle.DUST,
                f.origin,
                f.age * 16.0,
                f.dust(),
            )
            f.draw(Shape.VORTEX, p(1.6 * f.scale, f.pts(24), height = 3.0 * f.scale, turns = 3.0), Particle.PORTAL, f.origin, f.age * -10.0)
            true
        }

    val heartBurst =
        VfxPreset { f ->
            if (f.age % 6 ==
                0
            ) {
                f.draw(
                    Shape.HEART,
                    p(1.0 * f.scale * (1 + f.progress), f.pts(30)),
                    Particle.DUST,
                    f.at(dy = 2.0),
                    0.0,
                    f.dust(f.color, 1.1f),
                )
            }
            if (f.age == 0) f.spawn(Particle.HEART, f.at(dy = 2.0), 8, Vec3(0.6, 0.4, 0.6), 0.0)
            true
        }

    /**
     * Builds a scripted emitter preset: a cached shape drawn every [interval] ticks, optionally spinning, pulsing and
     * expanding over its lifetime.
     */
    fun emitter(shape: Shape, params: ShapeParams, particle: Particle, data: Any?, interval: Int, spinPerTick: Double, pulse: Double, expandPerTick: Double): VfxPreset =
        VfxPreset { f ->
            if (f.age % interval.coerceAtLeast(1) == 0) {
                val pulseFactor = if (pulse > 0) 1 + sin(f.age * 0.3) * pulse else 1.0
                val r = ((params.radius + expandPerTick * f.age) * pulseFactor * f.scale).coerceIn(0.0, 256.0)
                f.draw(shape, params.copy(radius = r), particle, f.origin, f.age * spinPerTick, data)
            }
            true
        }
}
