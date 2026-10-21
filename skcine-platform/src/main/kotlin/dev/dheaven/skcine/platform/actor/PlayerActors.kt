package dev.dheaven.skcine.platform.actor

import dev.dheaven.skcine.platform.scene.SceneInstance
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player

/**
 * Player-looking actors built on the vanilla Mannequin entity (Minecraft 1.21.9+). They are real server entities,
 * so every actor feature (movement, look-at, attachment, visibility, cleanup) applies unchanged.
 *
 * Only touch this class after checking the `player-actors` capability: it references classes that do not exist on
 * older servers.
 */
object PlayerActors {
    /** Where a mannequin's skin comes from. */
    sealed interface Skin {
        /** Copy an online player's own profile (no external download by SkCine). */
        data class OfPlayer(val player: Player) : Skin

        /** A profile by player name, resolved through Mojang's official profile service. */
        data class ByName(val name: String) : Skin

        data object Default : Skin
    }

    private val NAME = Regex("^[A-Za-z0-9_]{1,16}$")

    /** Validates a skin name; returns an error message or null. */
    fun validateName(name: String): String? = if (NAME.matches(name)) null else "'$name' is not a valid Minecraft player name for a skin"

    fun spawn(actors: ActorManager, location: Location, name: String?, nameTag: Component?, skin: Skin, instance: SceneInstance?, audience: Collection<Player>?): CineActor =
        actors.spawn(location, Mannequin::class.java, name, instance, audience) { m ->
            m.isInvulnerable = true
            m.isImmovable = true
            // Hide the default "NPC" description line under the name.
            m.description = Component.empty()
            when (skin) {
                is Skin.OfPlayer -> m.profile = ResolvableProfile.resolvableProfile(skin.player.playerProfile)
                is Skin.ByName -> m.profile = ResolvableProfile.resolvableProfile().name(skin.name).build()
                Skin.Default -> Unit
            }
            if (nameTag != null) {
                m.customName(nameTag)
                m.isCustomNameVisible = true
            }
        }
}
