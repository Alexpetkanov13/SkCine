package dev.dheaven.skcine.platform.actor

import dev.dheaven.skcine.api.event.ActorAction
import dev.dheaven.skcine.api.event.CineActorEvent
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.platform.Diagnostics
import dev.dheaven.skcine.platform.SkCineSettings
import dev.dheaven.skcine.platform.profiler.Profiler
import dev.dheaven.skcine.platform.scene.SceneInstance
import dev.dheaven.skcine.platform.scene.SceneResource
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * A scene-controlled entity. Owned actors were spawned by SkCine and are removed when their scene (or the plugin)
 * ends; wrapped actors reference pre-existing entities and are never removed by cleanup.
 */
class CineActor internal constructor(
    val name: String,
    entity: Entity,
    val owned: Boolean,
    val instance: SceneInstance?,
    /** Players allowed to see this actor, or null if visible to everyone. */
    val audience: Set<UUID>?,
) {
    val entityId: UUID = entity.uniqueId
    private var ref: Entity = entity
    var removed = false
        internal set

    /** The live entity, or null if it no longer exists. */
    fun entity(): Entity? {
        if (removed) return null
        if (ref.isValid) return ref
        val found = Bukkit.getEntity(entityId) ?: return null
        ref = found
        return found.takeIf { it.isValid }
    }

    val isValid get() = entity() != null

    fun location(): Location? = entity()?.location

    override fun toString() = "cine actor '$name'"

    override fun equals(other: Any?) = other is CineActor && other.entityId == entityId

    override fun hashCode() = entityId.hashCode()
}

class ActorManager(private val plugin: Plugin, private val settings: () -> SkCineSettings, private val diagnostics: Diagnostics, private val profiler: Profiler) {
    val actorKey = NamespacedKey(plugin, "actor")

    private val actors = LinkedHashMap<UUID, CineActor>()
    private val names = HashMap<String, CineActor>()
    private var counter = 0L

    /** Hook so the animator can drop animations of removed actors. */
    var onRemoved: (CineActor) -> Unit = {}

    val count get() = actors.size

    fun all(): Collection<CineActor> = actors.values

    fun of(instance: SceneInstance): List<CineActor> = actors.values.filter { it.instance === instance }

    private fun nameKey(instance: SceneInstance?, name: String) = (instance?.uniqueId?.toString() ?: "global") + ":" + name.lowercase()

    fun byName(name: String, instance: SceneInstance?): CineActor? = names[nameKey(instance, name)]?.takeIf { !it.removed }

    fun byEntity(entity: Entity): CineActor? = actors[entity.uniqueId]

    fun isActorEntity(entity: Entity) = entity.persistentDataContainer.has(actorKey, PersistentDataType.BYTE)

    /** Checks budgets before spawning. Returns an error message or null. */
    fun checkBudget(instance: SceneInstance?): String? {
        val s = settings()
        if (actors.size >= s.maxActors) return "the server-wide actor limit (${s.maxActors}) is reached"
        if (instance != null &&
            of(instance).size >= s.maxActorsPerScene
        ) {
            return "this scene already has the maximum of ${s.maxActorsPerScene} actors"
        }
        return null
    }

    /**
     * Prepares an entity inside a spawn consumer (before it is sent to clients): non-persistent, tagged, and hidden
     * from everyone when the actor is private.
     */
    fun preSpawn(entity: Entity, private: Boolean) {
        entity.isPersistent = false
        entity.persistentDataContainer.set(actorKey, PersistentDataType.BYTE, 1)
        if (private) entity.isVisibleByDefault = false
    }

    /** Spawns a native entity class as an owned actor. */
    fun <T : Entity> spawn(location: Location, type: Class<T>, name: String?, instance: SceneInstance?, audience: Collection<Player>?, configure: (T) -> Unit = {}): CineActor {
        val world: World = location.world ?: throw IllegalArgumentException("actor location has no world")
        val entity =
            world.spawn(location, type) { e ->
                preSpawn(e, audience != null)
                configure(e)
            }
        return register(entity, name, owned = true, instance = instance, audience = audience)
    }

    /** Registers an already spawned entity as an actor. */
    fun register(entity: Entity, name: String?, owned: Boolean, instance: SceneInstance?, audience: Collection<Player>?): CineActor {
        actors[entity.uniqueId]?.let { return it }
        val actorName = name?.takeIf { it.isNotBlank() } ?: "actor-${++counter}"
        val actor = CineActor(actorName, entity, owned, instance, audience?.map { it.uniqueId }?.toSet())
        actors[entity.uniqueId] = actor
        names[nameKey(instance, actorName)] = actor
        audience?.forEach { it.showEntity(plugin, entity) }
        instance?.own(ActorResource(actor))
        Bukkit.getPluginManager().callEvent(CineActorEvent(instance, actorName, entity, ActorAction.SPAWN))
        return actor
    }

    /** Wraps an existing entity (not owned: never removed by cleanup). */
    fun wrap(entity: Entity, name: String?, instance: SceneInstance?): CineActor =
        actors[entity.uniqueId] ?: register(entity, name, owned = false, instance = instance, audience = null)

    fun setVisible(actor: CineActor, visible: Boolean, players: Collection<Player>?) {
        val e = actor.entity() ?: return
        if (players == null) {
            e.isVisibleByDefault = visible
            if (visible) actor.audience?.forEach { Bukkit.getPlayer(it)?.showEntity(plugin, e) }
            return
        }
        players.forEach { if (visible) it.showEntity(plugin, e) else it.hideEntity(plugin, e) }
    }

    fun remove(actor: CineActor) {
        if (actor.removed) return
        val entity = actor.entity()
        actor.removed = true
        actors.remove(actor.entityId)
        names.entries.removeIf { it.value === actor }
        onRemoved(actor)
        try {
            if (actor.owned) entity?.remove()
        } catch (t: Throwable) {
            profiler.cleanupFailures++
            diagnostics.error("Could not remove actor '${actor.name}'", "actor-remove", t)
        }
        Bukkit.getPluginManager().callEvent(CineActorEvent(actor.instance, actor.name, entity, ActorAction.DESPAWN))
    }

    /** Drops actors whose entities disappeared (killed, unloaded) so handles never dangle. */
    fun tick() {
        if (actors.isEmpty()) return
        val gone = actors.values.filter { it.entity() == null }
        gone.forEach {
            diagnostics.debug { "actor '${it.name}' disappeared unexpectedly" }
            remove(it)
        }
    }

    fun removeAll() {
        actors.values.toList().forEach { remove(it) }
        names.clear()
    }

    /** Removes stray SkCine actor entities (non-persistent, so normally none survive restarts). */
    fun removeStrayEntities(worlds: Collection<World>): Int {
        var n = 0
        for (w in worlds) {
            for (e in w.entities) {
                if (isActorEntity(e) && !actors.containsKey(e.uniqueId)) {
                    e.remove()
                    n++
                }
            }
        }
        return n
    }

    private inner class ActorResource(val actor: CineActor) : SceneResource {
        override fun dispose(reason: EndReason) = remove(actor)
    }
}
