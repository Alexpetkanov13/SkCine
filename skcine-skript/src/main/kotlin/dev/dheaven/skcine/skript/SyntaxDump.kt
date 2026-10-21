package dev.dheaven.skcine.skript

import ch.njol.skript.Skript
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos
import org.skriptlang.skript.registration.SyntaxInfo
import org.skriptlang.skript.registration.SyntaxRegistry

/**
 * Builds a Markdown index of every SkCine syntax pattern *as registered in Skript's live registry*, so the
 * published syntax index can never drift from what the server actually understands.
 */
object SyntaxDump {
    private fun ours(info: SyntaxInfo<*>) = info.type().name.startsWith("dev.dheaven.skcine")

    fun markdown(): String {
        val reg = Skript.instance().syntaxRegistry()
        val sections =
            listOf(
                "Structures" to reg.syntaxes(SyntaxRegistry.STRUCTURE),
                "Sections" to reg.syntaxes(SyntaxRegistry.SECTION),
                "Effects" to reg.syntaxes(SyntaxRegistry.EFFECT),
                "Conditions" to reg.syntaxes(SyntaxRegistry.CONDITION),
                "Expressions" to reg.syntaxes(SyntaxRegistry.EXPRESSION),
                "Events" to reg.syntaxes(BukkitSyntaxInfos.Event.KEY),
            )
        val out = StringBuilder()
        out.append("# SkCine syntax index\n\n")
        out.append("Generated from Skript's live syntax registry by `/skcine info syntax` (SkCine ").append(SkCineRuntimeVersion.get()).append(").\n")
        out.append("Every pattern below is registered and parsed by Skript; see the feature guides for descriptions and examples.\n\n")
        out.append("Pattern notation: `[optional]`, `(a|b)` alternatives, `%type%` expressions (`%-type%` = optional value), `1:`/`tag:` parse marks.\n\n")
        var total = 0
        for ((title, infos) in sections) {
            val mine = infos.filter { ours(it) }.sortedBy { it.type().simpleName }
            if (mine.isEmpty()) continue
            out.append("## ").append(title).append(" (").append(mine.size).append(")\n\n")
            for (info in mine) {
                val name = (info as? BukkitSyntaxInfos.Event<*>)?.name() ?: info.type().simpleName
                out.append("### ").append(name).append("\n\n")
                out.append("```\n")
                info.patterns().forEach {
                    out.append(it).append('\n')
                    total++
                }
                out.append("```\n\n")
            }
        }
        out.append("---\nTotal patterns: ").append(total).append('\n')
        return out.toString()
    }
}

private object SkCineRuntimeVersion {
    fun get(): String = dev.dheaven.skcine.platform.SkCineRuntime.getOrNull()?.version ?: "?"
}
