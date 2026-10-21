package dev.dheaven.skcine.skript.effects

import ch.njol.skript.aliases.ItemType
import ch.njol.skript.entity.EntityData
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.util.Timespan
import dev.dheaven.skcine.api.capability.Capability
import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.PoseKeyframe
import dev.dheaven.skcine.core.path.PosePath
import dev.dheaven.skcine.core.scene.SceneState
import dev.dheaven.skcine.platform.actor.AttachAnimation
import dev.dheaven.skcine.platform.actor.Channel
import dev.dheaven.skcine.platform.actor.CineActor
import dev.dheaven.skcine.platform.actor.GroupRotateAnimation
import dev.dheaven.skcine.platform.actor.LivingScaleAnimation
import dev.dheaven.skcine.platform.actor.LookAtAnimation
import dev.dheaven.skcine.platform.actor.MoveAnimation
import dev.dheaven.skcine.platform.actor.PlayerActors
import dev.dheaven.skcine.platform.actor.RotateAnimation
import dev.dheaven.skcine.platform.actor.SpinAnimation
import dev.dheaven.skcine.platform.actor.TimelinePathAnimation
import dev.dheaven.skcine.platform.actor.TransformAnimation
import dev.dheaven.skcine.platform.actor.WalkAnimation
import dev.dheaven.skcine.platform.dialogue.ComponentText
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.skript.CineEffect
import dev.dheaven.skcine.skript.CineExpression
import dev.dheaven.skcine.skript.Ctx
import dev.dheaven.skcine.skript.easingOf
import dev.dheaven.skcine.skript.effect
import dev.dheaven.skcine.skript.expr
import dev.dheaven.skcine.skript.expression
import dev.dheaven.skcine.skript.onEnd
import dev.dheaven.skcine.skript.opt
import dev.dheaven.skcine.skript.report
import dev.dheaven.skcine.skript.rt
import dev.dheaven.skcine.skript.targetOf
import dev.dheaven.skcine.skript.ticks
import dev.dheaven.skcine.skript.vec
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.TextDisplay
import org.bukkit.event.Event
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import org.skriptlang.skript.registration.SyntaxRegistry
import java.util.UUID
import java.util.function.Consumer

/** Entities referenced by actors or plain entities. */
internal fun entitiesOf(event: Event, expr: Expression<*>): List<Entity> = expr.getArray(event).mapNotNull {
    when (it) {
        is CineActor -> it.entity()
        is Entity -> it.takeIf { e -> e.isValid }
        else -> null
    }
}

internal fun ownerFor(event: Event, entity: Entity): SceneInstance? = Ctx.instance(event) ?: rt().actors.byEntity(entity)?.instance

/**
 * Creates actors:
 * ```
 * set {_npc} to a new cine actor of villager at {_loc} named "guide"
 * set {_item} to a new cine item display of diamond sword at {_loc}
 * set {_block} to a new cine block display of beacon at {_loc}
 * set {_label} to a new cine text display with text "<gold>Legendary" at {_loc}
 * ```
 * Inside a scene, actors are visible only to its viewers (add `public` for everyone) and are removed when it ends.
 */
class ExprNewActor : CineExpression<CineActor>(CineActor::class.java) {
    private lateinit var what: Expression<*>
    private lateinit var loc: Expression<Location>
    private var name: Expression<String>? = null
    private var public = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        what = e[0]
        loc = e.expr(1)
        name = e.opt(2)
        public = result.hasTag("public")
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun values(event: Event): List<CineActor> {
        val rt = rt()
        val l = loc.getSingle(event) ?: return emptyList<CineActor>().also { report(where, "actor location is not set") }
        if (l.world == null) return emptyList<CineActor>().also { report(where, "actor location has no world") }
        val inst = Ctx.instance(event)?.takeIf { it.state.isAlive }
        rt.actors.checkBudget(inst)?.let {
            report(where, "could not create actor: $it", inst)
            return emptyList()
        }
        val audience = if (public || inst == null) null else inst.viewers
        val n = name?.getSingle(event)
        val w = what.getSingle(event) ?: return emptyList()
        val actor =
            try {
                when (pattern) {
                    0 -> {
                        val data = w as? EntityData<Entity> ?: return emptyList()
                        val entity =
                            data.spawn(
                                l,
                                Consumer { en ->
                                    rt.actors.preSpawn(en, audience != null)
                                    en.isInvulnerable = true
                                    if (en is Mob) {
                                        en.removeWhenFarAway = false
                                        runCatching { Bukkit.getMobGoals().removeAllGoals(en) }
                                    }
                                },
                            ) ?: return emptyList<CineActor>().also { report(where, "could not spawn $data at that location", inst) }
                        rt.actors.register(entity, n, owned = true, instance = inst, audience = audience)
                    }
                    1 -> {
                        val item = (w as? ItemType)?.random ?: return emptyList()
                        rt.actors.spawn(l, ItemDisplay::class.java, n, inst, audience) { it.setItemStack(item) }
                    }
                    2 -> {
                        val block: BlockData =
                            when (w) {
                                is BlockData -> w
                                is ItemType ->
                                    w.random
                                        ?.type
                                        ?.takeIf { it.isBlock }
                                        ?.createBlockData()
                                        ?: return emptyList<CineActor>().also { report(where, "$w is not a block") }
                                else -> return emptyList()
                            }
                        rt.actors.spawn(l, BlockDisplay::class.java, n, inst, audience) { it.block = block }
                    }
                    else -> {
                        val text = ComponentText.parse(w as String)
                        rt.actors.spawn(l, TextDisplay::class.java, n, inst, audience) {
                            it.text(text)
                            it.billboard = Display.Billboard.CENTER
                        }
                    }
                }
            } catch (t: Throwable) {
                report(where, "could not create actor: ${t.message}", inst)
                return emptyList()
            }
        return listOf(actor)
    }

    override val description = "a new cine actor"

    companion object {
        private const val PUB = "[public:(public|shared)] "

        fun register(r: SyntaxRegistry) = r.expression(
            ExprNewActor::class.java,
            CineActor::class.java,
            ::ExprNewActor,
            "[a] new ${PUB}cine actor (of|as) %entitydata% at %location% [named %-string%]",
            "[a] new ${PUB}cine item display [actor] (of|with) %itemtype% at %location% [named %-string%]",
            "[a] new ${PUB}cine block display [actor] (of|with) %blockdata/itemtype% at %location% [named %-string%]",
            "[a] new ${PUB}cine text display [actor] (with text|of) %string% at %location% [named %-string%]",
        )
    }
}

/**
 * `set {_npc} to a new cine player actor at {_loc} [named "guide"] [with skin of player] [with name tag "<gold>Guide"]`
 *
 * A player-looking actor (vanilla Mannequin, Minecraft 1.21.9+). The skin comes from an online player's own profile
 * or a player name (resolved through Mojang's profile service); without one the default skin is used.
 */
class ExprNewPlayerActor : CineExpression<CineActor>(CineActor::class.java) {
    private lateinit var loc: Expression<Location>
    private var name: Expression<String>? = null
    private var skin: Expression<*>? = null
    private var nameTag: Expression<String>? = null
    private var public = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        if (!rt().capabilities.has(Capability.PLAYER_ACTORS)) {
            ch.njol.skript.Skript.error(
                "Player actors need Minecraft 1.21.9 or newer (Mannequin entities). Use another humanoid mob, or wrap an NPC plugin's entity with 'cine actor of'.",
            )
            return false
        }
        loc = e.expr(0)
        name = e.opt(1)
        skin = e[2]
        nameTag = e.opt(3)
        public = result.hasTag("public")
        return true
    }

    override fun values(event: Event): List<CineActor> {
        val rt = rt()
        val l = loc.getSingle(event) ?: return emptyList<CineActor>().also { report(where, "actor location is not set") }
        if (l.world == null) return emptyList<CineActor>().also { report(where, "actor location has no world") }
        val inst = Ctx.instance(event)?.takeIf { it.state.isAlive }
        rt.actors.checkBudget(inst)?.let {
            report(where, "could not create actor: $it", inst)
            return emptyList()
        }
        val skinSource =
            when (val s = skin?.getSingle(event)) {
                null -> PlayerActors.Skin.Default
                is org.bukkit.entity.Player -> PlayerActors.Skin.OfPlayer(s)
                is String -> {
                    PlayerActors.validateName(s)?.let { msg ->
                        report(where, msg, inst)
                        return emptyList()
                    }
                    PlayerActors.Skin.ByName(s)
                }
                else -> PlayerActors.Skin.Default
            }
        val audience = if (public || inst == null) null else inst.viewers
        return try {
            listOf(
                PlayerActors.spawn(
                    rt.actors,
                    l,
                    name?.getSingle(event),
                    nameTag?.getSingle(event)?.let { ComponentText.parse(it) },
                    skinSource,
                    inst,
                    audience,
                ),
            )
        } catch (t: Throwable) {
            report(where, "could not create player actor: ${t.message}", inst)
            emptyList()
        }
    }

    override val description = "a new cine player actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprNewPlayerActor::class.java,
            CineActor::class.java,
            ::ExprNewPlayerActor,
            "[a] new [public:(public|shared)] cine player actor at %location% [named %-string%] [with skin of %-player/string%] [with (name tag|nametag) %-string%]",
        )
    }
}

/** `cine actor of {_entity}` (wraps, never removed by cleanup) / `cine actor named "guide" [in {_playback}]` */
class ExprActorOf : CineExpression<CineActor>(CineActor::class.java) {
    private lateinit var arg: Expression<*>
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        arg = e[0]
        inst = e.opt(1)
        return true
    }

    override fun values(event: Event): List<CineActor> {
        val v = arg.getSingle(event) ?: return emptyList()
        return when (pattern) {
            0 -> listOf(rt().actors.wrap(v as Entity, null, Ctx.instance(event)))
            else -> {
                val i = inst?.getSingle(event) ?: Ctx.instance(event)
                listOfNotNull(rt().actors.byName(v as String, i) ?: rt().actors.byName(v, null))
            }
        }
    }

    override val description = "cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.expression(
            ExprActorOf::class.java,
            CineActor::class.java,
            ::ExprActorOf,
            "[the] cine actor (of|for) %entity%",
            "[the] cine actor (named|called) %string% [(of|in) %-cineplayback%]",
        )
    }
}

/** `move cine actor {_a} to {_loc} over 2 seconds [with easing "ease-out-cubic"] [facing the movement]` */
class EffActorMove : CineEffect() {
    private lateinit var actors: Expression<*>
    private lateinit var to: Expression<Location>
    private lateinit var duration: Expression<Timespan>
    private var easing: Expression<String>? = null
    private var facing = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        to = e.expr(1)
        duration = e.expr(2)
        easing = e.opt(3)
        facing = result.hasTag("facing")
        return true
    }

    override fun execute(event: Event) {
        val dest = to.getSingle(event) ?: return
        val d = duration.getSingle(event)?.ticks()?.toInt() ?: return
        val ease = easingOf(easing?.getSingle(event), where)
        for (en in entitiesOf(event, actors)) {
            if (dest.world != en.world) {
                report(where, "cannot move an actor to another world")
                continue
            }
            rt().animator.start(MoveAnimation(en, ownerFor(event, en), dest.clone(), d, ease, facing))
        }
    }

    override val description = "move cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorMove::class.java,
            ::EffActorMove,
            "move [the] cine actor[s] %cineactors/entities% to %location% (over|in) %timespan% [with easing %-string%] [facing:facing [the] movement]",
        )
    }
}

/** `make cine actor {_a} walk to {_loc} [at speed 1.2]` — real pathfinding for mobs. */
class EffActorWalk : CineEffect() {
    private lateinit var actors: Expression<*>
    private lateinit var to: Expression<Location>
    private var speed: Expression<Number>? = null
    private var timeout: Expression<Timespan>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        to = e.expr(1)
        speed = e.opt(2)
        timeout = e.opt(3)
        return true
    }

    override fun execute(event: Event) {
        val dest = to.getSingle(event) ?: return
        val sp = (speed?.getSingle(event)?.toDouble() ?: 1.0).coerceIn(0.1, 5.0)
        val limit = timeout?.getSingle(event)?.ticks()?.toInt() ?: (20 * 30)
        val pathfinding = rt().capabilities.has(Capability.MOB_PATHFINDING)
        for (en in entitiesOf(event, actors)) {
            val owner = ownerFor(event, en)
            if (en is Mob && pathfinding) {
                rt().animator.start(WalkAnimation(en, owner, dest.clone(), sp, limit))
            } else {
                val ticks = (en.location.distance(dest) / (0.2 * sp)).toInt().coerceIn(1, limit)
                rt().animator.start(MoveAnimation(en, owner, dest.clone(), ticks, Easing.LINEAR, true))
            }
        }
    }

    override val description = "make cine actor walk"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorWalk::class.java,
            ::EffActorWalk,
            "make [the] cine actor[s] %cineactors/entities% walk to %location% [at speed %-number%] [(giving up|timing out) after %-timespan%]",
        )
    }
}

/** `rotate cine actor {_a} to yaw 90 [and pitch 0] over 1 second` */
class EffActorRotate : CineEffect() {
    private lateinit var actors: Expression<*>
    private lateinit var yaw: Expression<Number>
    private var pitch: Expression<Number>? = null
    private lateinit var duration: Expression<Timespan>
    private var easing: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        yaw = e.expr(1)
        pitch = e.opt(2)
        duration = e.expr(3)
        easing = e.opt(4)
        return true
    }

    override fun execute(event: Event) {
        val y = yaw.getSingle(event)?.toFloat() ?: return
        val d = duration.getSingle(event)?.ticks()?.toInt() ?: return
        val ease = easingOf(easing?.getSingle(event), where)
        for (en in entitiesOf(event, actors)) {
            val p = pitch?.getSingle(event)?.toFloat() ?: en.location.pitch
            rt().animator.start(RotateAnimation(en, ownerFor(event, en), y, p.coerceIn(-90f, 90f), d, ease))
        }
    }

    override val description = "rotate cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorRotate::class.java,
            ::EffActorRotate,
            "(rotate|turn) [the] cine actor[s] %cineactors/entities% to yaw %number% [and pitch %-number%] over %timespan% [with easing %-string%]",
        )
    }
}

/** `make cine actor {_a} look at player [for 3 seconds]` */
class EffActorLook : CineEffect() {
    private lateinit var actors: Expression<*>
    private lateinit var target: Expression<*>
    private var duration: Expression<Timespan>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        target = e[1]
        duration = e.opt(2)
        return true
    }

    override fun execute(event: Event) {
        val ref = targetOf(target.getSingle(event)) ?: return
        val d = duration?.getSingle(event)?.ticks()?.toInt() ?: -1
        for (en in entitiesOf(event, actors)) rt().animator.start(LookAtAnimation(en, ownerFor(event, en), ref, d))
    }

    override val description = "make cine actor look at"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorLook::class.java,
            ::EffActorLook,
            "make [the] cine actor[s] %cineactors/entities% (look at|face) %location/entity% [for %-timespan%]",
        )
    }
}

/** `hide cine actor {_a} [from player]` / `show cine actor {_a} [to player]` */
class EffActorVisibility : CineEffect() {
    private lateinit var actors: Expression<CineActor>
    private var players: Expression<org.bukkit.entity.Player>? = null
    private var show = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e.expr(0)
        players = e.opt(1)
        show = result.mark == 2
        return true
    }

    override fun execute(event: Event) {
        val ps = players?.getArray(event)?.toList()
        actors.getArray(event).forEach { rt().actors.setVisible(it, show, ps) }
    }

    override val description = "change cine actor visibility"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorVisibility::class.java,
            ::EffActorVisibility,
            "(1:hide|2:show) [the] cine actor[s] %cineactors% [(from|to|for) %-players%]",
        )
    }
}

/** `remove cine actor {_a}` — despawns owned actors; wrapped actors are only released. */
class EffActorRemove : CineEffect() {
    private lateinit var actors: Expression<CineActor>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e.expr(0)
        return true
    }

    override fun execute(event: Event) = actors.getArray(event).forEach { rt().actors.remove(it) }

    override val description = "remove cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(EffActorRemove::class.java, ::EffActorRemove, "(remove|despawn) [the] cine actor[s] %cineactors%")
    }
}

/** `attach cine actor {_hat} to {_npc} [with offset vector(0, 2, 0)] [and rotate with it]` / `detach cine actor {_hat}` */
class EffActorAttach : CineEffect() {
    private lateinit var actors: Expression<*>
    private var parentExpr: Expression<*>? = null
    private var offset: Expression<Vector>? = null
    private var rotate = false

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        if (pattern == 0) {
            parentExpr = e[1]
            offset = e.opt(2)
            rotate = result.hasTag("rotate")
        }
        return true
    }

    override fun execute(event: Event) {
        val children = entitiesOf(event, actors)
        if (pattern == 1) {
            children.forEach { rt().animator.cancel(it.uniqueId, Channel.ATTACH) }
            return
        }
        val p = parentExpr?.let { entitiesOf(event, it).firstOrNull() } ?: return report(where, "attach target is not set")
        val off = offset?.getSingle(event)?.let { Vec3(it.x, it.y, it.z) } ?: Vec3.ZERO
        for (c in children) {
            if (c == p) continue
            rt().animator.start(AttachAnimation(c, ownerFor(event, c), p.uniqueId, off, rotate))
        }
    }

    override val description = "attach cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorAttach::class.java,
            ::EffActorAttach,
            "attach [the] cine actor[s] %cineactors/entities% to %cineactor/entity% [with offset %-vector%] [rotate:and rotate with it]",
            "detach [the] cine actor[s] %cineactors/entities%",
        )
    }
}

/** Keyframed actor paths driven by the scene clock (seek/pause/speed aware). */
internal object ActorTracks {
    private val tracks = HashMap<UUID, HashMap<UUID, ArrayList<PoseKeyframe>>>()

    fun add(inst: SceneInstance, entity: Entity, k: PoseKeyframe) {
        val byEntity =
            tracks.getOrPut(inst.uniqueId) {
                inst.onEnd { tracks.remove(inst.uniqueId) }
                HashMap()
            }
        val list = byEntity.getOrPut(entity.uniqueId) { ArrayList() }
        list.removeAll { it.time == k.time }
        list.add(k)
        rt().animator.start(TimelinePathAnimation(entity, inst, PosePath(list.toList())))
    }
}

/** `add cine actor keyframe for {_npc} at {_loc} at 3 seconds [with easing "ease-in-out-sine"]` */
class EffActorKeyframe : CineEffect() {
    private lateinit var actor: Expression<*>
    private lateinit var loc: Expression<Location>
    private lateinit var time: Expression<Timespan>
    private var easing: Expression<String>? = null
    private var inst: Expression<SceneInstance>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actor = e[0]
        loc = e.expr(1)
        time = e.expr(2)
        easing = e.opt(3)
        inst = e.opt(4)
        if (inst == null && !Ctx.inSceneContext()) {
            ch.njol.skript.Skript
                .error("Actor keyframes need a cine scene: use them in a timeline or add 'in {_playback}'")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val i = inst?.getSingle(event) ?: Ctx.instance(event) ?: return
        if (i.state.isTerminal || i.state == SceneState.CLEANING_UP) return
        val en = entitiesOf(event, actor).firstOrNull() ?: return report(where, "actor is not set or no longer exists", i)
        val l = loc.getSingle(event) ?: return
        if (l.world != en.world) return report(where, "actor keyframes must be in the actor's world", i)
        val t = (if (event is dev.dheaven.skcine.skript.CineBuildEvent) event.offset else 0.0) + (time.getSingle(event)?.ticks() ?: return)
        val k =
            runCatching { PoseKeyframe(t, l.vec(), l.yaw.toDouble(), l.pitch.toDouble(), easingOf(easing?.getSingle(event), where)) }
                .getOrElse { return report(where, "invalid actor keyframe: ${it.message}", i) }
        ActorTracks.add(i, en, k)
    }

    override val description = "add cine actor keyframe"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorKeyframe::class.java,
            ::EffActorKeyframe,
            "add [a] cine actor keyframe (for|to) %cineactor/entity% at %location% at %timespan% [with easing %-string%] [(in|of) %-cineplayback%]",
        )
    }
}

/** `scale cine actor {_a} to 2 over 1 second` — display transform scale, or the scale attribute for mobs. */
class EffActorScale : CineEffect() {
    private lateinit var actors: Expression<*>
    private lateinit var scale: Expression<Number>
    private lateinit var duration: Expression<Timespan>
    private var easing: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        scale = e.expr(1)
        duration = e.expr(2)
        easing = e.opt(3)
        return true
    }

    override fun execute(event: Event) {
        val s = scale.getSingle(event)?.toDouble() ?: return
        if (!s.isFinite() || s <= 0 || s > 64) return report(where, "scale must be between 0 and 64")
        val d = duration.getSingle(event)?.ticks()?.toInt() ?: return
        val ease = easingOf(easing?.getSingle(event), where)
        val attr = rt().capabilities.scaleAttribute
        for (en in entitiesOf(event, actors)) {
            val owner = ownerFor(event, en)
            when {
                en is Display -> {
                    val t = en.transformation
                    val target =
                        Transformation(t.translation, t.leftRotation, Vector3f(s.toFloat(), s.toFloat(), s.toFloat()), t.rightRotation)
                    rt().animator.start(TransformAnimation(en, owner, target, d, ease))
                }
                en is LivingEntity && attr != null ->
                    rt().animator.start(
                        LivingScaleAnimation(en, owner, attr, s.coerceIn(0.0625, 16.0), d, ease),
                    )
                else -> report(where, "${en.type} cannot be scaled on this server")
            }
        }
    }

    override val description = "scale cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffActorScale::class.java,
            ::EffActorScale,
            "scale [the] cine actor[s] %cineactors/entities% to %number% over %timespan% [with easing %-string%]",
        )
    }
}

/** `stop animating cine actor {_a}` */
class EffActorStop : CineEffect() {
    private lateinit var actors: Expression<*>

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        return true
    }

    override fun execute(event: Event) = entitiesOf(event, actors).forEach { rt().animator.cancel(it.uniqueId) }

    override val description = "stop animating cine actor"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(EffActorStop::class.java, ::EffActorStop, "stop animating [the] cine actor[s] %cineactors/entities%")
    }
}

/** Group transform: `rotate cine actors {_parts::*} around {_pivot} by 90 degrees over 2 seconds` */
class EffGroupRotate : CineEffect() {
    private lateinit var actors: Expression<*>
    private lateinit var pivot: Expression<Location>
    private lateinit var degrees: Expression<Number>
    private lateinit var duration: Expression<Timespan>
    private var easing: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        actors = e[0]
        pivot = e.expr(1)
        degrees = e.expr(2)
        duration = e.expr(3)
        easing = e.opt(4)
        return true
    }

    override fun execute(event: Event) {
        val p = pivot.getSingle(event) ?: return
        val deg = degrees.getSingle(event)?.toDouble()?.takeIf { it.isFinite() } ?: return
        val d = duration.getSingle(event)?.ticks()?.toInt() ?: return
        val ease = easingOf(easing?.getSingle(event), where)
        for (en in entitiesOf(event, actors)) {
            if (en.world != p.world) continue
            rt().animator.start(GroupRotateAnimation(en, ownerFor(event, en), p.clone(), deg, d, ease))
        }
    }

    override val description = "rotate cine actors around pivot"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffGroupRotate::class.java,
            ::EffGroupRotate,
            "rotate [the] cine actors %cineactors/entities% around %location% by %number% degrees over %timespan% [with easing %-string%]",
        )
    }
}

/**
 * Display transforms in local space:
 * `animate cine display {_d} to translation vector(0, 1, 0) and scale 2 and rotation vector(0, 180, 0) over 2 seconds`
 * Rotation is Euler degrees (x = pitch, y = yaw, z = roll) applied as the display's left rotation.
 */
class EffDisplayAnimate : CineEffect() {
    private lateinit var displays: Expression<*>
    private var translation: Expression<Vector>? = null
    private var scale: Expression<*>? = null
    private var rotation: Expression<Vector>? = null
    private lateinit var duration: Expression<Timespan>
    private var easing: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        displays = e[0]
        translation = e.opt(1)
        scale = e[2]
        rotation = e.opt(3)
        duration = e.expr(4)
        easing = e.opt(5)
        if (translation == null && scale == null && rotation == null) {
            ch.njol.skript.Skript
                .error("Say what to animate: translation, scale and/or rotation")
            return false
        }
        return true
    }

    override fun execute(event: Event) {
        val d = duration.getSingle(event)?.ticks()?.toInt() ?: return
        val ease = easingOf(easing?.getSingle(event), where)
        val tr = translation?.getSingle(event)
        val sc =
            when (val s = scale?.getSingle(event)) {
                is Number -> Vector3f(s.toFloat(), s.toFloat(), s.toFloat())
                is Vector -> Vector3f(s.x.toFloat(), s.y.toFloat(), s.z.toFloat())
                else -> null
            }
        val rot = rotation?.getSingle(event)
        if (sc != null &&
            (!sc.isFinite || sc.x() !in 0f..64f || sc.y() !in 0f..64f || sc.z() !in 0f..64f)
        ) {
            return report(where, "display scale must be between 0 and 64")
        }
        for (en in entitiesOf(event, displays)) {
            val disp = en as? Display ?: continue
            val cur = disp.transformation
            val target =
                Transformation(
                    tr?.let { Vector3f(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) } ?: cur.translation,
                    rot?.let {
                        Quaternionf().rotationYXZ(
                            Math.toRadians(it.y).toFloat(),
                            Math.toRadians(it.x).toFloat(),
                            Math.toRadians(it.z).toFloat(),
                        )
                    }
                        ?: cur.leftRotation,
                    sc ?: cur.scale,
                    cur.rightRotation,
                )
            rt().animator.start(TransformAnimation(disp, ownerFor(event, en), target, d, ease))
        }
    }

    override val description = "animate cine display"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffDisplayAnimate::class.java,
            ::EffDisplayAnimate,
            "animate [the] cine display[s] %cineactors/entities% [to] [translation %-vector%] [[and] scale %-number/vector%] [[and] rotation %-vector%] over %timespan% [with easing %-string%]",
        )
    }
}

/** `spin cine display {_d} around vector(0, 1, 0) by 720 degrees over 4 seconds` / `... continuously at 90 degrees per second` */
class EffDisplaySpin : CineEffect() {
    private lateinit var displays: Expression<*>
    private lateinit var axis: Expression<Vector>
    private lateinit var amount: Expression<Number>
    private var duration: Expression<Timespan>? = null
    private var easing: Expression<String>? = null

    override fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean {
        displays = e[0]
        axis = e.expr(1)
        amount = e.expr(2)
        if (pattern == 0) {
            duration = e.opt(3)
            easing = e.opt(4)
        }
        return true
    }

    override fun execute(event: Event) {
        val ax = axis.getSingle(event) ?: return
        if (ax.lengthSquared() < 1e-9) return report(where, "spin axis must not be zero")
        val deg = amount.getSingle(event)?.toDouble()?.takeIf { it.isFinite() } ?: return
        val d = if (pattern == 0) duration?.getSingle(event)?.ticks()?.toInt() ?: return else -1
        val ease = easingOf(easing?.getSingle(event), where)
        // Continuous mode: degrees per second → SpinAnimation interprets "degrees" per 20 ticks when duration < 0.
        for (en in entitiesOf(event, displays)) {
            val disp = en as? Display ?: continue
            rt().animator.start(
                SpinAnimation(disp, ownerFor(event, en), Vector3f(ax.x.toFloat(), ax.y.toFloat(), ax.z.toFloat()), deg, d, ease),
            )
        }
    }

    override val description = "spin cine display"

    companion object {
        fun register(r: SyntaxRegistry) = r.effect(
            EffDisplaySpin::class.java,
            ::EffDisplaySpin,
            "spin [the] cine display[s] %cineactors/entities% around [axis] %vector% by %number% degrees over %timespan% [with easing %-string%]",
            "spin [the] cine display[s] %cineactors/entities% around [axis] %vector% (continuously|forever) at %number% degrees per second",
        )
    }
}
