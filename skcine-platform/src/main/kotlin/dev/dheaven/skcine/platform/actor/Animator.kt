package dev.dheaven.skcine.platform.actor

import dev.dheaven.skcine.api.event.ActorAction
import dev.dheaven.skcine.api.event.CineActorEvent
import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.math.Angles
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.PosePath
import dev.dheaven.skcine.core.path.TargetRef
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/** Animation channels: starting an animation replaces any running animation on the same entity and channel. */
enum class Channel { POSITION, ROTATION, TRANSFORM, SCALE, ATTACH }

/**
 * A running animation, advanced once per tick by [Animator]. Animations hold entity UUIDs, not entity references,
 * and finish gracefully when their entity disappears.
 */
abstract class Animation(val entityId: UUID, val channel: Channel, val owner: SceneInstance?) {
    var age = 0
        internal set

    /** Advances one tick. Returns false when finished. */
    abstract fun tick(entity: Entity): Boolean

    open fun onCancel(entity: Entity?) {}
}

class Animator(private val settings: () -> SkCineSettings, private val diagnostics: Diagnostics, private val actorName: (UUID) -> String?) {
    private val running = LinkedHashMap<Pair<UUID, Channel>, Animation>()

    val count get() = running.size

    fun start(anim: Animation): Boolean {
        if (running.size >= settings().maxAnimations && !running.containsKey(anim.entityId to anim.channel)) {
            diagnostics.warn("Animation limit (${settings().maxAnimations}) reached; animation ignored", "anim-limit")
            return false
        }
        running.put(anim.entityId to anim.channel, anim)?.onCancel(Bukkit.getEntity(anim.entityId))
        anim.owner?.own(AnimationResource(anim))
        return true
    }

    fun cancel(entityId: UUID, channel: Channel? = null) {
        val keys = running.keys.filter { it.first == entityId && (channel == null || it.second == channel) }
        keys.forEach { running.remove(it)?.onCancel(Bukkit.getEntity(entityId)) }
    }

    fun isAnimating(entityId: UUID, channel: Channel? = null) = running.keys.any { it.first == entityId && (channel == null || it.second == channel) }

    fun tick() {
        if (running.isEmpty()) return
        val it = running.entries.iterator()
        val finished = ArrayList<Animation>()
        while (it.hasNext()) {
            val anim = it.next().value
            val entity = Bukkit.getEntity(anim.entityId)
            if (entity == null || !entity.isValid) {
                it.remove()
                continue
            }
            val keep =
                try {
                    anim.age++
                    anim.tick(entity)
                } catch (t: Throwable) {
                    diagnostics.error("Animation on ${entity.type} failed and was stopped", "anim-error:${anim.javaClass.simpleName}", t)
                    false
                }
            if (!keep) {
                it.remove()
                finished += anim
            }
        }
        for (a in finished) {
            val name = actorName(a.entityId) ?: continue
            Bukkit.getPluginManager().callEvent(CineActorEvent(a.owner, name, Bukkit.getEntity(a.entityId), ActorAction.ANIMATION_FINISH))
        }
    }

    fun cancelAll() {
        running.values.toList().forEach { it.onCancel(Bukkit.getEntity(it.entityId)) }
        running.clear()
    }

    private inner class AnimationResource(val anim: Animation) : SceneResource {
        override fun dispose(reason: EndReason) {
            val key = anim.entityId to anim.channel
            if (running[key] === anim) {
                running.remove(key)
                anim.onCancel(Bukkit.getEntity(anim.entityId))
            }
        }
    }

    companion object {
        /** Display teleport interpolation accepts 0..59 ticks on current versions. */
        const val MAX_TELEPORT_DURATION = 59
        const val DEFAULT_STEP = 2

        fun progress(age: Int, duration: Int) = if (duration <= 0) 1.0 else (age.toDouble() / duration).coerceIn(0.0, 1.0)
    }
}

/** Moves an entity to a location over [duration] ticks. Display entities use client-side teleport smoothing. */
class MoveAnimation(entity: Entity, owner: SceneInstance?, private val to: Location, private val duration: Int, private val easing: Easing, private val faceMovement: Boolean) :
    Animation(entity.uniqueId, Channel.POSITION, owner) {
    private val from = entity.location.clone()
    private val step = if (entity is Display) Animator.DEFAULT_STEP else 1

    init {
        if (entity is Display) entity.teleportDuration = step
    }

    override fun tick(entity: Entity): Boolean {
        val u = easing.apply(Animator.progress(age, duration))
        if (age % step != 0 && u < 1.0) return true
        val l = lerp(from, to, u)
        if (faceMovement) {
            val dir = Vec3(to.x - from.x, to.y - from.y, to.z - from.z)
            l.yaw = Angles.yawOf(dir, from.yaw.toDouble()).toFloat()
        }
        entity.teleport(l)
        return u < 1.0
    }

    companion object {
        fun lerp(a: Location, b: Location, t: Double) = Location(
            b.world ?: a.world,
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t,
            Angles.lerpYaw(a.yaw.toDouble(), b.yaw.toDouble(), t).toFloat(),
            (a.pitch + (b.pitch - a.pitch) * t).toFloat(),
        )
    }
}

/**
 * Follows a keyframe path driven by the owning scene's clock, so pausing, speed changes and seeking stay in sync
 * with the camera and timeline.
 */
class TimelinePathAnimation(entity: Entity, owner: SceneInstance, private val path: PosePath) : Animation(entity.uniqueId, Channel.POSITION, owner) {
    private val world = entity.world

    init {
        if (entity is Display) entity.teleportDuration = 1
    }

    override fun tick(entity: Entity): Boolean {
        val inst = owner ?: return false
        val t = inst.position
        val pose = path.evaluate(t) ?: return false
        entity.teleport(Location(world, pose.position.x, pose.position.y, pose.position.z, pose.yaw.toFloat(), pose.pitch.toFloat()))
        return !inst.state.isTerminal
    }
}

class RotateAnimation(entity: Entity, owner: SceneInstance?, private val toYaw: Float, private val toPitch: Float, private val duration: Int, private val easing: Easing) :
    Animation(entity.uniqueId, Channel.ROTATION, owner) {
    private val fromYaw = entity.location.yaw
    private val fromPitch = entity.location.pitch

    override fun tick(entity: Entity): Boolean {
        val u = easing.apply(Animator.progress(age, duration))
        val yaw = Angles.lerpYaw(fromYaw.toDouble(), toYaw.toDouble(), u).toFloat()
        val pitch = (fromPitch + (toPitch - fromPitch) * u).toFloat()
        entity.setRotation(yaw, pitch)
        return u < 1.0
    }
}

/** Keeps an entity facing a (possibly moving) target for [duration] ticks (-1 = until replaced). */
class LookAtAnimation(entity: Entity, owner: SceneInstance?, private val target: TargetRef, private val duration: Int) : Animation(entity.uniqueId, Channel.ROTATION, owner) {
    override fun tick(entity: Entity): Boolean {
        val t = target.resolve() ?: return false
        val eye = if (entity is LivingEntity) entity.eyeLocation else entity.location
        val (yaw, pitch) = Angles.lookAt(Vec3(eye.x, eye.y, eye.z), t, eye.yaw.toDouble())
        if (entity is Mob) {
            entity.lookAt(t.x, t.y, t.z)
        }
        entity.setRotation(yaw.toFloat(), pitch.toFloat())
        return duration < 0 || age < duration
    }
}

/** Walks a mob to a destination with the Paper pathfinder (real walking animation). */
class WalkAnimation(entity: Mob, owner: SceneInstance?, private val destination: Location, private val speed: Double, private val timeout: Int) :
    Animation(entity.uniqueId, Channel.POSITION, owner) {
    private var started = false

    override fun tick(entity: Entity): Boolean {
        val mob = entity as? Mob ?: return false
        if (!started) {
            started = mob.pathfinder.moveTo(destination, speed)
            if (!started) {
                entity.teleport(destination)
                return false
            }
        }
        if (mob.location.distanceSquared(destination) < 1.0) return false
        if (age >= timeout) {
            mob.pathfinder.stopPathfinding()
            return false
        }
        // Re-issue occasionally in case another goal interrupted the path.
        if (age % 20 == 0 && !mob.pathfinder.hasPath()) mob.pathfinder.moveTo(destination, speed)
        return true
    }

    override fun onCancel(entity: Entity?) {
        (entity as? Mob)?.pathfinder?.stopPathfinding()
    }
}

/**
 * Animates a display's transformation. Linear animations use one client-side interpolation; eased animations are
 * approximated by short interpolated steps so the curve shape is preserved.
 */
class TransformAnimation(display: Display, owner: SceneInstance?, private val target: Transformation, private val duration: Int, private val easing: Easing) :
    Animation(display.uniqueId, Channel.TRANSFORM, owner) {
    private val from = display.transformation
    private val step = if (easing == Easing.LINEAR) duration.coerceAtLeast(1) else Animator.DEFAULT_STEP

    override fun tick(entity: Entity): Boolean {
        val d = entity as? Display ?: return false
        val stepIndex = age - 1
        if (stepIndex % step == 0) {
            val nextAge = (stepIndex + step).coerceAtMost(duration)
            val u = easing.apply(Animator.progress(nextAge, duration))
            d.interpolationDelay = 0
            d.interpolationDuration = (nextAge - stepIndex).coerceAtLeast(0)
            d.transformation = lerp(from, target, u)
        }
        return age < duration
    }

    companion object {
        fun lerp(a: Transformation, b: Transformation, t: Double): Transformation {
            val f = t.toFloat()
            return Transformation(
                Vector3f(a.translation).lerp(b.translation, f),
                Quaternionf(a.leftRotation).slerp(b.leftRotation, f),
                Vector3f(a.scale).lerp(b.scale, f),
                Quaternionf(a.rightRotation).slerp(b.rightRotation, f),
            )
        }
    }
}

/** Spins a display around an axis by any angle (multiple turns allowed). */
class SpinAnimation(display: Display, owner: SceneInstance?, private val axis: Vector3f, private val degrees: Double, private val duration: Int, private val easing: Easing) :
    Animation(display.uniqueId, Channel.TRANSFORM, owner) {
    private val base = display.transformation
    private val step = Animator.DEFAULT_STEP

    override fun tick(entity: Entity): Boolean {
        val d = entity as? Display ?: return false
        val stepIndex = age - 1
        if (stepIndex % step == 0) {
            val nextAge = (stepIndex + step).coerceAtMost(if (duration < 0) Int.MAX_VALUE else duration)
            val u = if (duration < 0) nextAge / 20.0 else easing.apply(Animator.progress(nextAge, duration))
            val angle = Math.toRadians(degrees * u).toFloat()
            val rot = Quaternionf().rotateAxis(angle, Vector3f(axis).normalize()).mul(base.leftRotation)
            d.interpolationDelay = 0
            d.interpolationDuration = step
            d.transformation = Transformation(base.translation, rot, base.scale, base.rightRotation)
        }
        return duration < 0 || age < duration
    }
}

/**
 * Rotates a set of entities around a world-space pivot (vertical axis), keeping their relative layout — the group
 * transform. Each member also turns by the same yaw so the composition rotates rigidly.
 */
class GroupRotateAnimation(
    entity: Entity,
    owner: SceneInstance?,
    private val pivot: Location,
    private val degrees: Double,
    private val duration: Int,
    private val easing: Easing,
) : Animation(entity.uniqueId, Channel.POSITION, owner) {
    private val start = entity.location.clone()
    private val step = if (entity is Display) Animator.DEFAULT_STEP else 1

    init {
        if (entity is Display) entity.teleportDuration = step
    }

    override fun tick(entity: Entity): Boolean {
        val u = easing.apply(Animator.progress(age, duration))
        if (age % step != 0 && u < 1.0) return true
        val a = Math.toRadians(degrees * u)
        val dx = start.x - pivot.x
        val dz = start.z - pivot.z
        // Minecraft yaw increases clockwise when seen from above; rotate offsets the same way.
        val rx = dx * cos(a) - dz * sin(a)
        val rz = dx * sin(a) + dz * cos(a)
        val l = Location(start.world, pivot.x + rx, start.y, pivot.z + rz, (start.yaw + degrees * u).toFloat(), start.pitch)
        entity.teleport(l)
        return u < 1.0
    }
}

/** Keeps a child at a local offset from a parent (rotated by the parent's yaw) until cancelled or the parent is gone. */
class AttachAnimation(child: Entity, owner: SceneInstance?, private val parentId: UUID, private val offset: Vec3, private val inheritYaw: Boolean) :
    Animation(child.uniqueId, Channel.ATTACH, owner) {
    init {
        if (child is Display) child.teleportDuration = 1
    }

    override fun tick(entity: Entity): Boolean {
        val parent = Bukkit.getEntity(parentId)?.takeIf { it.isValid } ?: return false
        val p = parent.location
        val a = Math.toRadians(p.yaw.toDouble())
        // Local offset: +z forward, +x left, matching the parent's facing.
        val rx = offset.x * cos(a) - offset.z * sin(a)
        val rz = offset.x * sin(a) + offset.z * cos(a)
        val l = Location(p.world, p.x + rx, p.y + offset.y, p.z + rz, if (inheritYaw) p.yaw else entity.location.yaw, entity.location.pitch)
        if (entity.world != p.world || entity.location.distanceSquared(l) > 1e-6) entity.teleport(l)
        return true
    }
}

/** Animates the generic scale attribute of a living entity (1.20.5+ servers). */
class LivingScaleAnimation(
    entity: LivingEntity,
    owner: SceneInstance?,
    private val attribute: Attribute,
    private val to: Double,
    private val duration: Int,
    private val easing: Easing,
) : Animation(entity.uniqueId, Channel.SCALE, owner) {
    private val from = entity.getAttribute(attribute)?.baseValue ?: 1.0

    override fun tick(entity: Entity): Boolean {
        val inst = (entity as? LivingEntity)?.getAttribute(attribute) ?: return false
        val u = easing.apply(Animator.progress(age, duration))
        inst.baseValue = (from + (to - from) * u).coerceIn(0.0625, 16.0)
        return u < 1.0
    }
}
