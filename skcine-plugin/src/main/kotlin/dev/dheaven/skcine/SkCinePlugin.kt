package dev.dheaven.skcine

import dev.dheaven.skcine.commands.SkCineCommand
import dev.dheaven.skcine.config.ConfigLoader
import dev.dheaven.skcine.listeners.CineListeners
import dev.dheaven.skcine.platform.SkCineRuntime
import dev.dheaven.skcine.skript.SkCineSkriptIntegration
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/**
 * SkCine — cinematic cameras, timelines, actors, effects and dialogue for Skript.
 *
 * All scene content is authored in Skript scripts; this plugin provides the syntax and runtime.
 */
class SkCinePlugin : JavaPlugin() {
    private var runtime: SkCineRuntime? = null

    override fun onEnable() {
        val skript = server.pluginManager.getPlugin("Skript")
        if (skript == null || !skript.isEnabled) {
            logger.severe("Skript is not installed or failed to enable. SkCine requires Skript 2.16 or newer; disabling.")
            server.pluginManager.disablePlugin(this)
            return
        }
        saveDefaultConfig()
        val settings = ConfigLoader.load(this)
        val rt = SkCineRuntime(this, settings)
        SkCineRuntime.install(rt)
        runtime = rt

        val failure =
            try {
                SkCineSkriptIntegration.register(SkCinePlugin::class.java)
            } catch (t: Throwable) {
                logger.log(java.util.logging.Level.SEVERE, "Failed to register SkCine syntax with Skript ${skript.pluginMeta.version}", t)
                "incompatible Skript version (${skript.pluginMeta.version}): ${t.javaClass.simpleName}: ${t.message}"
            }
        if (failure != null) {
            logger.severe("SkCine could not register its Skript syntax: $failure. Disabling.")
            SkCineRuntime.install(null)
            runtime = null
            server.pluginManager.disablePlugin(this)
            return
        }

        server.pluginManager.registerEvents(CineListeners(rt), this)
        getCommand("skcine")?.let {
            val cmd = SkCineCommand(rt)
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }

        // Safety nets for /reload and crashes: restore players left in a scene and clear stray entities.
        Bukkit.getScheduler().runTask(
            this,
            Runnable {
                Bukkit.getOnlinePlayers().forEach { rt.states.recoverIfNeeded(it) }
                val worlds = Bukkit.getWorlds()
                val removed = rt.camera.removeStrayEntities(worlds) + rt.actors.removeStrayEntities(worlds)
                if (removed > 0) logger.info("Removed $removed leftover SkCine entities.")
            },
        )

        val caps = rt.capabilities
        logger.info(
            "SkCine ${rt.version} by DHeaven enabled — Minecraft ${caps.serverVersion}, Skript ${caps.skriptVersion}, Java ${caps.javaVersion}, " +
                "camera backend: ${if (rt.camera.isAvailable()) rt.camera.id else "none"}.",
        )
    }

    override fun onDisable() {
        runtime?.let {
            it.shutdown()
            SkCineSkriptIntegration.shutdown()
        }
        SkCineRuntime.install(null)
        runtime = null
    }

    /** Re-reads config.yml (used by `/skcine debug` toggling and future reload support). */
    fun reloadSettings() {
        reloadConfig()
        runtime?.updateSettings(ConfigLoader.load(this))
    }
}
