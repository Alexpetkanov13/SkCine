package dev.dheaven.skcine.platform.scene

import dev.dheaven.skcine.api.scene.SceneRegistry
import dev.dheaven.skcine.core.scene.SceneId

/**
 * Registered scene definitions by id.
 *
 * Namespace/duplicate policy:
 *  - Ids are normalized (see [SceneId]); `RPG:Intro` and `rpg:intro` are the same id.
 *  - The same owner (script) may re-register an id — that is a reload and replaces the old definition.
 *  - A different owner registering an existing id is rejected with an error naming both scripts.
 */
class SceneRegistryImpl : SceneRegistry {
    private val byId = LinkedHashMap<String, SceneDefinition>()

    sealed interface Result {
        data object Registered : Result

        data class Replaced(val previous: SceneDefinition) : Result

        data class Duplicate(val existing: SceneDefinition) : Result
    }

    fun register(def: SceneDefinition): Result {
        val existing = byId[def.id]
        if (existing != null && existing !== def) {
            if (existing.owner == null || existing.owner != def.owner) return Result.Duplicate(existing)
            existing.registered = false
            byId[def.id] = def
            def.registered = true
            return Result.Replaced(existing)
        }
        byId[def.id] = def
        def.registered = true
        return Result.Registered
    }

    fun unregister(def: SceneDefinition): Boolean {
        if (byId[def.id] !== def) return false
        byId.remove(def.id)
        def.registered = false
        return true
    }

    /** Removes everything a script registered; returns the removed definitions. */
    fun unregisterOwner(owner: SceneOwner): List<SceneDefinition> {
        val removed = byId.values.filter { it.owner == owner }
        removed.forEach {
            byId.remove(it.id)
            it.registered = false
        }
        return removed
    }

    override fun get(id: String): SceneDefinition? = SceneId.parse(id)?.let { byId[it.value] }

    override fun ids(): Set<String> = byId.keys.toSet()

    fun all(): Collection<SceneDefinition> = byId.values.toList()

    fun clear() {
        byId.values.forEach { it.registered = false }
        byId.clear()
    }
}
