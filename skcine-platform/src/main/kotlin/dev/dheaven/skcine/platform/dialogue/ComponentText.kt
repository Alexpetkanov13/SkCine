package dev.dheaven.skcine.platform.dialogue

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/** Text parsing and typewriter slicing for Adventure components. */
object ComponentText {
    private val mini = MiniMessage.miniMessage()
    private val legacyAmp = LegacyComponentSerializer.legacyAmpersand()
    private val legacySection = LegacyComponentSerializer.legacySection()
    private val legacyTag = Regex("[&§][0-9a-fk-orx]", RegexOption.IGNORE_CASE)

    /**
     * Parses script text. MiniMessage tags (`<gold>`, `<gradient:...>`) are used when present; strings that only use
     * legacy `&`/`§` codes are parsed as legacy text. Plain text passes through unchanged.
     */
    fun parse(text: String): Component {
        val hasLegacy = legacyTag.containsMatchIn(text)
        val hasMini = text.contains('<') && text.contains('>')
        return when {
            hasLegacy && !hasMini -> if (text.contains('§')) legacySection.deserialize(text) else legacyAmp.deserialize(text)
            hasMini -> runCatching { mini.deserialize(if (hasLegacy) legacyToMini(text) else text) }.getOrElse { Component.text(text) }
            else -> Component.text(text)
        }
    }

    private val legacyToTag =
        mapOf(
            '0' to "<black>",
            '1' to "<dark_blue>",
            '2' to "<dark_green>",
            '3' to "<dark_aqua>",
            '4' to "<dark_red>",
            '5' to "<dark_purple>",
            '6' to "<gold>",
            '7' to "<gray>",
            '8' to "<dark_gray>",
            '9' to "<blue>",
            'a' to "<green>",
            'b' to "<aqua>",
            'c' to "<red>",
            'd' to "<light_purple>",
            'e' to "<yellow>",
            'f' to "<white>",
            'k' to "<obfuscated>",
            'l' to "<bold>",
            'm' to "<strikethrough>",
            'n' to "<underlined>",
            'o' to "<italic>",
            'r' to "<reset>",
        )

    /** Converts legacy codes to MiniMessage tags so mixed strings parse consistently. */
    fun legacyToMini(text: String): String = legacyTag.replace(text) { m -> legacyToTag[m.value[1].lowercaseChar()] ?: m.value }

    fun plain(c: Component): String = PlainTextComponentSerializer.plainText().serialize(c)

    /** Number of revealable characters (non-text components count as one unit). */
    fun visibleLength(c: Component): Int {
        val own = if (c is TextComponent) c.content().length else 1
        return own + c.children().sumOf { visibleLength(it) }
    }

    /** Returns a copy of [c] showing only the first [count] visible characters, keeping all styling. */
    fun slice(c: Component, count: Int): Component {
        val remaining = intArrayOf(count.coerceAtLeast(0))
        return sliceInto(c, remaining) ?: Component.empty()
    }

    private fun sliceInto(c: Component, remaining: IntArray): Component? {
        if (remaining[0] <= 0) return null
        val head: Component =
            if (c is TextComponent) {
                val content = c.content()
                val take = minOf(content.length, remaining[0])
                remaining[0] -= take
                c.content(content.substring(0, take)).children(emptyList())
            } else {
                remaining[0] -= 1
                c.children(emptyList())
            }
        val kids = ArrayList<Component>()
        for (child in c.children()) {
            if (remaining[0] <= 0) break
            sliceInto(child, remaining)?.let { kids += it }
        }
        return head.children(kids)
    }
}
