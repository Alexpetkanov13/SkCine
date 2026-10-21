package dev.dheaven.skcine.commands

import dev.dheaven.skcine.platform.SkCineRuntime
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

/**
 * Maintenance and diagnostics only. Scenes are never created or edited through commands — that happens in Skript.
 */
class SkCineCommand(private val rt: SkCineRuntime) : TabExecutor {
    private val subcommands = listOf("version", "info", "capabilities", "debug", "profiler", "health", "help")

    private fun CommandSender.line(label: String, value: Any?) =
        sendMessage(Component.text("  $label: ", NamedTextColor.GRAY).append(Component.text(value.toString(), NamedTextColor.WHITE)))

    private fun CommandSender.header(text: String) = sendMessage(Component.text("SkCine » ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.YELLOW)))

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase() ?: "help") {
            "version" -> sender.header("SkCine ${rt.version} by DHeaven — Bring your Skripts to life.")
            "info" -> {
                if (args.getOrNull(1).equals("syntax", ignoreCase = true)) {
                    val file = java.io.File(rt.plugin.dataFolder, "syntax-index.md")
                    file.parentFile.mkdirs()
                    file.writeText(dev.dheaven.skcine.skript.SyntaxDump.markdown())
                    sender.header("Wrote the registered syntax index to ${file.path}")
                    return true
                }
                val c = rt.capabilities
                sender.header("Info")
                sender.line("Version", rt.version)
                sender.line("Minecraft", c.serverVersion)
                sender.line("Skript", c.skriptVersion)
                sender.line("Java", c.javaVersion)
                sender.line("Camera backend", if (rt.camera.isAvailable()) rt.camera.id else "none")
                sender.line("Registered scenes", rt.registry.ids().size)
                sender.line("Playing scenes", rt.manager.activeCount)
                sender.line(
                    "Quality",
                    rt.settings.quality.name
                        .lowercase(),
                )
            }
            "capabilities" -> {
                sender.header("Capabilities")
                rt.capabilities.report().forEach { (k, v) -> sender.line(k, v) }
            }
            "debug" -> {
                val enabled = !rt.diagnostics.debugEnabled
                rt.updateSettings(rt.settings.copy(debug = enabled))
                sender.header(
                    "Debug logging ${if (enabled) "enabled" else "disabled"} (until restart; set 'debug' in config.yml to persist).",
                )
            }
            "profiler" -> {
                val s = rt.snapshot()
                sender.header("Profiler (last ${s.sampledTicks} ticks with SkCine work)")
                sender.line(
                    "Tick time avg/p50/p95/p99/max (µs)",
                    "%.1f / %.1f / %.1f / %.1f / %.1f".format(
                        s.averageTickMicros,
                        s.p50TickMicros,
                        s.p95TickMicros,
                        s.p99TickMicros,
                        s.maxTickMicros,
                    ),
                )
                sender.line("Scenes / cameras / actors", "${s.activeScenes} / ${s.activeCameras} / ${s.activeActors}")
                sender.line("Effects / animations / dialogue lines", "${s.activeEffects} / ${s.activeAnimations} / ${s.activeDialogues}")
                sender.line("Particles sent / dropped (last second)", "${s.particlesSentLastSecond} / ${s.particlesDroppedLastSecond}")
                sender.line("Camera updates / text updates (last second)", "${s.cameraUpdatesLastSecond} / ${s.textUpdatesLastSecond}")
                sender.line("Scheduler running", s.schedulerRunning)
            }
            "health" -> {
                val s = rt.snapshot()
                val problems = ArrayList<String>()
                if (!rt.camera.isAvailable()) problems += "camera control unavailable"
                if (s.cleanupFailures > 0) problems += "${s.cleanupFailures} cleanup failure(s) since start"
                if (rt.diagnostics.errorCount > 0) problems += "${rt.diagnostics.errorCount} error(s) logged since start (see console)"
                if (s.p95TickMicros > 5000) problems += "p95 tick time above 5 ms"
                sender.header(if (problems.isEmpty()) "Healthy." else "Attention needed:")
                problems.forEach { sender.sendMessage(Component.text("  • $it", NamedTextColor.RED)) }
            }
            else -> {
                sender.header("Commands (maintenance only — scenes are written in Skript)")
                subcommands.forEach { sender.sendMessage(Component.text("  /skcine $it", NamedTextColor.GRAY)) }
                sender.sendMessage(Component.text("  Docs: docs/GETTING_STARTED.md in the SkCine distribution.", NamedTextColor.DARK_GRAY))
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        if (args.size == 1) subcommands.filter { it.startsWith(args[0].lowercase()) } else emptyList()
}
