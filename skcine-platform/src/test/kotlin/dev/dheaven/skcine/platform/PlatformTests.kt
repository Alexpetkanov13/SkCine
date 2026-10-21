package dev.dheaven.skcine.platform

import dev.dheaven.skcine.core.scene.SceneId
import dev.dheaven.skcine.platform.compatibility.ServerVersion
import dev.dheaven.skcine.platform.dialogue.ComponentText
import dev.dheaven.skcine.platform.dialogue.DialogueChannel
import dev.dheaven.skcine.platform.dialogue.DialogueLine
import dev.dheaven.skcine.platform.dialogue.DialogueOptions
import dev.dheaven.skcine.platform.dialogue.RevealMode
import dev.dheaven.skcine.platform.scene.SceneDefinition
import dev.dheaven.skcine.platform.scene.SceneOwner
import dev.dheaven.skcine.platform.scene.SceneRegistryImpl
import dev.dheaven.skcine.platform.scene.SkipMode
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

class PlatformTests {
    @Test
    fun `text parsing handles minimessage, legacy and mixed input`() {
        assertEquals("Hello", ComponentText.plain(ComponentText.parse("<gold>Hello")))
        assertEquals(NamedTextColor.GOLD, ComponentText.parse("<gold>Hello").children().firstOrNull()?.color() ?: ComponentText.parse("<gold>Hello").color())
        assertEquals("Hello world", ComponentText.plain(ComponentText.parse("&aHello &lworld")))
        assertEquals("Mixed text", ComponentText.plain(ComponentText.parse("&a<bold>Mixed</bold> text")))
        assertEquals("plain", ComponentText.plain(ComponentText.parse("plain")))
        assertEquals("<gold>x<bold>y", ComponentText.legacyToMini("&6x&ly"))
    }

    @Test
    fun `typewriter slicing keeps styling and counts visible characters`() {
        val c =
            Component.text().append(Component.text("Hel", NamedTextColor.RED)).append(
                Component.text("lo!", NamedTextColor.BLUE).decorate(TextDecoration.BOLD),
            ).build()
        assertEquals(6, ComponentText.visibleLength(c))
        val sliced = ComponentText.slice(c, 4)
        assertEquals("Hell", ComponentText.plain(sliced))
        val styled = sliced.children().last()
        assertEquals(NamedTextColor.BLUE, styled.color())
        assertTrue(styled.hasDecoration(TextDecoration.BOLD))
        assertEquals("", ComponentText.plain(ComponentText.slice(c, 0)))
        assertEquals("Hello!", ComponentText.plain(ComponentText.slice(c, 99)))
        val gradient = ComponentText.parse("<gradient:red:blue>Gradient</gradient>")
        assertEquals("Grad", ComponentText.plain(ComponentText.slice(gradient, 4)))
    }

    @Test
    fun `word-by-word reveal snaps to word boundaries`() {
        val text = ComponentText.parse("<gold>Hello</gold>  brave new world")
        val line = DialogueLine(null, text, DialogueOptions(charsPerSecond = 4.0, revealMode = RevealMode.WORD), emptyList(), null)
        assertEquals(0, line.charsFor(0))
        assertEquals("Hello", ComponentText.plain(ComponentText.slice(text, line.charsFor(1))))
        assertEquals("Hello  brave", ComponentText.plain(ComponentText.slice(text, line.charsFor(2))))
        assertEquals(line.length, line.charsFor(4))
        assertEquals(line.length, line.charsFor(99))
        val letters = DialogueLine(null, text, DialogueOptions(charsPerSecond = 10.0), emptyList(), null)
        assertEquals(3, letters.charsFor(3))
    }

    @Test
    fun `registry rejects duplicates from other scripts and allows reloads`() {
        val reg = SceneRegistryImpl()
        val a = SceneOwner("scripts/a.sk")
        val b = SceneOwner("scripts/b.sk")
        val first = SceneDefinition(SceneId.parse("rpg:intro")!!, a)
        assertSame(SceneRegistryImpl.Result.Registered, reg.register(first))
        assertTrue(first.registered)
        val clash = reg.register(SceneDefinition(SceneId.parse("RPG:Intro")!!, b))
        assertInstanceOf(SceneRegistryImpl.Result.Duplicate::class.java, clash)
        val reload = SceneDefinition(SceneId.parse("rpg:intro")!!, a)
        assertInstanceOf(SceneRegistryImpl.Result.Replaced::class.java, reg.register(reload))
        assertFalse(first.registered)
        assertSame(reload, reg.get("rpg:intro"))
        assertEquals(listOf(reload), reg.unregisterOwner(a))
        assertNull(reg.get("rpg:intro"))
        assertTrue(reg.ids().isEmpty())
        assertNull(reg.get("not a valid id!"))
    }

    @Test
    fun `definition copy is independent`() {
        val d = SceneDefinition(SceneId.parse("x")!!, null)
        d.addStep { }
        val c = d.copy(SceneId.parse("y")!!)
        c.addStep { }
        assertEquals(1, d.steps.size)
        assertEquals(2, c.steps.size)
        assertEquals("y", c.id)
    }

    @Test
    fun `diagnostics rate-limit repeated messages and report the count`() {
        val logger = Logger.getAnonymousLogger()
        logger.useParentHandlers = false
        val records = ArrayList<String>()
        logger.addHandler(
            object : Handler() {
                override fun publish(record: LogRecord) {
                    records += record.message
                }

                override fun flush() {}

                override fun close() {}
            },
        )
        var now = 0L
        val d = Diagnostics(logger, false, { now }, 1000)
        repeat(5) { d.error("boom") }
        assertEquals(1, records.size)
        now = 1500
        d.error("boom")
        assertEquals(2, records.size)
        assertTrue(records[1].contains("repeated 4 more times"), records[1])
        d.warn("other")
        assertEquals(3, records.size)
        assertEquals(6L, d.errorCount)
        assertEquals("Scene 'a' (b.sk): msg", Diagnostics.scene("a", "b.sk", "msg"))
        assertEquals("msg", Diagnostics.scene(null, null, "msg"))
    }

    @Test
    fun `version and setting parsers`() {
        assertEquals(ServerVersion(1, 21, 11), ServerVersion.parse("1.21.11"))
        assertEquals(ServerVersion(26, 3, 0), ServerVersion.parse("26.3"))
        assertTrue(ServerVersion.parse("26.1.2")!! > ServerVersion.parse("1.21.11")!!)
        assertEquals(SkipMode.VOTE, SkipMode.parse(" Vote "))
        assertNull(SkipMode.parse("sideways"))
        assertEquals(DialogueChannel.ACTION_BAR, DialogueChannel.parse("actionbar"))
        assertEquals(DialogueChannel.BOSS_BAR, DialogueChannel.parse("boss_bar"))
        assertEquals(QualityMode.LOW, QualityMode.parse("low"))
        assertEquals(QualityMode.AUTO, QualityMode.parse(null))
        assertEquals(ReloadPolicy.KEEP, ReloadPolicy.parse("keep"))
        assertEquals(ReloadPolicy.STOP, ReloadPolicy.parse("nonsense"))
        assertEquals(36000.0, SkCineSettings().timelineLimits.maxDurationTicks)
    }
}
