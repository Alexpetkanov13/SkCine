package dev.dheaven.skcine.api.capability

/** Runtime feature detection. Every advanced feature declares a [Capability] it depends on. */
enum class Capability(val id: String, val description: String) {
    DISPLAY_ENTITIES("display-entities", "Block/item/text display entities"),
    DISPLAY_TELEPORT_INTERPOLATION("display-teleport-interpolation", "Client-side smoothing of display entity teleports"),
    SPECTATOR_CAMERA("spectator-camera", "Camera control by spectating a server entity"),
    LOCKED_SPECTATING("locked-spectating", "Cancellable PlayerStopSpectatingEntityEvent keeps the camera locked"),
    PER_PLAYER_ENTITY_VISIBILITY("per-player-visibility", "Entities visible only to selected players"),
    MOB_PATHFINDING("mob-pathfinding", "Paper pathfinder API for walking actors"),
    CLICK_CALLBACKS("click-callbacks", "Adventure click callbacks for chat choices"),
    MINI_MESSAGE("minimessage", "MiniMessage text formatting"),
    PACKET_EVENTS("packetevents", "PacketEvents is installed (packet-level features)"),
    ENTITY_SCALE("entity-scale", "Generic scale attribute for living actors"),
    PLAYER_ACTORS("player-actors", "Mannequin entities: player-looking actors with skins (Minecraft 1.21.9+)"),
}

interface CapabilityService {
    fun has(capability: Capability): Boolean

    /** Human-readable report: capability id → status. */
    fun report(): Map<String, String>

    val serverVersion: String
    val skriptVersion: String
    val javaVersion: String
}
