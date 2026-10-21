package dev.dheaven.skcine.platform.compatibility

import dev.dheaven.skcine.api.capability.Capability
import dev.dheaven.skcine.api.capability.CapabilityService
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute

/** Parsed `major.minor.patch` Minecraft version (handles both 1.21.x and 26.x schemes). */
data class ServerVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ServerVersion> {
    override fun compareTo(other: ServerVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(s: String): ServerVersion? {
            val parts = Regex("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(s)?.groupValues ?: return null
            return ServerVersion(parts[1].toInt(), parts[2].toIntOrNull() ?: 0, parts[3].toIntOrNull() ?: 0)
        }
    }
}

/**
 * Detects what the running server supports. Detection uses class/method presence rather than version numbers where
 * possible, so forks and future versions are handled gracefully.
 */
class CapabilityRegistry : CapabilityService {
    private val detected = HashMap<Capability, Boolean>()

    override val serverVersion: String = runCatching { Bukkit.getMinecraftVersion() }.getOrElse { Bukkit.getBukkitVersion() }
    override val skriptVersion: String =
        Bukkit
            .getPluginManager()
            .getPlugin("Skript")
            ?.pluginMeta
            ?.version ?: "not installed"
    override val javaVersion: String = System.getProperty("java.version") ?: "unknown"

    val parsedServerVersion: ServerVersion? = ServerVersion.parse(serverVersion)

    /** Resolved scale attribute (renamed across 1.21.x), or null if unsupported. */
    val scaleAttribute: Attribute? =
        runCatching {
            Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale")) ?: Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"))
        }.getOrNull()

    init {
        detected[Capability.DISPLAY_ENTITIES] = classExists("org.bukkit.entity.Display")
        detected[Capability.DISPLAY_TELEPORT_INTERPOLATION] =
            methodExists("org.bukkit.entity.Display", "setTeleportDuration", Int::class.javaPrimitiveType!!)
        detected[Capability.SPECTATOR_CAMERA] =
            methodExists("org.bukkit.entity.Player", "setSpectatorTarget", org.bukkit.entity.Entity::class.java)
        detected[Capability.LOCKED_SPECTATING] = classExists("com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent")
        detected[Capability.PER_PLAYER_ENTITY_VISIBILITY] =
            methodExists("org.bukkit.entity.Entity", "setVisibleByDefault", Boolean::class.javaPrimitiveType!!)
        detected[Capability.MOB_PATHFINDING] = classExists("com.destroystokyo.paper.entity.Pathfinder")
        detected[Capability.CLICK_CALLBACKS] = classExists("net.kyori.adventure.text.event.ClickCallback")
        detected[Capability.MINI_MESSAGE] = classExists("net.kyori.adventure.text.minimessage.MiniMessage")
        detected[Capability.PACKET_EVENTS] = Bukkit.getPluginManager().getPlugin("packetevents")?.isEnabled == true
        detected[Capability.ENTITY_SCALE] = scaleAttribute != null
        detected[Capability.PLAYER_ACTORS] = classExists("org.bukkit.entity.Mannequin")
    }

    override fun has(capability: Capability) = detected[capability] == true

    override fun report(): Map<String, String> = Capability.entries.associate { c ->
        c.id to if (has(c)) "available" else "unavailable — ${fallbackFor(c)}"
    }

    /** Documented degradation when a capability is missing. */
    fun fallbackFor(c: Capability): String = when (c) {
        Capability.DISPLAY_ENTITIES -> "display actors and the display camera cannot be used"
        Capability.DISPLAY_TELEPORT_INTERPOLATION -> "camera moves without client smoothing (more visible stepping)"
        Capability.SPECTATOR_CAMERA -> "camera tracks are ignored; scenes play without camera control"
        Capability.LOCKED_SPECTATING -> "players can leave the camera with sneak; SkCine re-attaches next tick"
        Capability.PER_PLAYER_ENTITY_VISIBILITY -> "private actors are visible to everyone"
        Capability.MOB_PATHFINDING -> "walking actors are moved by interpolation instead"
        Capability.CLICK_CALLBACKS -> "choices accept numbered chat replies only"
        Capability.MINI_MESSAGE -> "text uses legacy colour codes only"
        Capability.PACKET_EVENTS -> "not required; all core features use the Paper API"
        Capability.ENTITY_SCALE -> "actor scaling is limited to display entities"
        Capability.PLAYER_ACTORS -> "player actors need Minecraft 1.21.9+; use other humanoid mobs or wrap an NPC plugin's entity"
    }

    companion object {
        fun classExists(name: String) = runCatching { Class.forName(name, false, CapabilityRegistry::class.java.classLoader) }.isSuccess

        fun methodExists(className: String, method: String, vararg params: Class<*>) =
            runCatching { Class.forName(className, false, CapabilityRegistry::class.java.classLoader).getMethod(method, *params) }.isSuccess
    }
}
