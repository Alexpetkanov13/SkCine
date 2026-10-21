package dev.dheaven.skcine.core

import dev.dheaven.skcine.core.geometry.Shape
import dev.dheaven.skcine.core.geometry.ShapeCache
import dev.dheaven.skcine.core.geometry.ShapeParams
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.scene.EndReason
import dev.dheaven.skcine.core.scene.IllegalSceneTransition
import dev.dheaven.skcine.core.scene.SceneId
import dev.dheaven.skcine.core.scene.SceneState
import dev.dheaven.skcine.core.scene.SceneStateMachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LifecycleTests {
    @Test
    fun `normal lifecycle`() {
        val sm = SceneStateMachine()
        val seen = ArrayList<SceneState>()
        sm.onTransition { _, to -> seen += to }
        sm.transition(SceneState.PREPARING)
        sm.transition(SceneState.READY)
        sm.transition(SceneState.PLAYING)
        sm.transition(SceneState.PAUSED)
        sm.transition(SceneState.PLAYING)
        assertTrue(sm.beginEnd(EndReason.COMPLETED))
        sm.finishEnd()
        assertEquals(SceneState.COMPLETED, sm.state)
        assertEquals(
            listOf(
                SceneState.PREPARING,
                SceneState.READY,
                SceneState.PLAYING,
                SceneState.PAUSED,
                SceneState.PLAYING,
                SceneState.CLEANING_UP,
                SceneState.COMPLETED,
            ),
            seen,
        )
    }

    @Test
    fun `repeated stop calls are harmless`() {
        val sm = SceneStateMachine()
        sm.transition(SceneState.PREPARING)
        sm.transition(SceneState.READY)
        sm.transition(SceneState.PLAYING)
        assertTrue(sm.beginEnd(EndReason.STOPPED))
        assertFalse(sm.beginEnd(EndReason.SKIPPED))
        sm.finishEnd()
        assertFalse(sm.beginEnd(EndReason.STOPPED))
        sm.finishEnd()
        assertEquals(SceneState.CANCELLED, sm.state)
        assertEquals(EndReason.STOPPED, sm.endReason)
    }

    @Test
    fun `cancelling before start still cleans up`() {
        val sm = SceneStateMachine()
        assertTrue(sm.beginEnd(EndReason.NO_VIEWERS))
        assertEquals(SceneState.CLEANING_UP, sm.state)
        sm.finishEnd()
        assertEquals(SceneState.CANCELLED, sm.state)
    }

    @Test
    fun `invalid transitions are rejected`() {
        val sm = SceneStateMachine()
        assertFalse(sm.tryTransition(SceneState.PLAYING))
        assertThrows(IllegalSceneTransition::class.java) { sm.transition(SceneState.COMPLETED) }
        assertEquals(SceneState.CREATED, sm.state)
    }

    @ParameterizedTest
    @EnumSource(EndReason::class)
    fun `every end reason reaches a terminal state`(reason: EndReason) {
        val sm = SceneStateMachine()
        sm.transition(SceneState.PREPARING)
        sm.transition(SceneState.READY)
        sm.transition(SceneState.PLAYING)
        sm.beginEnd(reason)
        sm.finishEnd()
        assertTrue(sm.state.isTerminal)
        assertEquals(reason.terminal, sm.state)
        SceneState.entries.forEach { assertFalse(sm.canTransition(it)) }
    }

    @Test
    fun `scene ids are validated and normalized`() {
        assertEquals("rpg:intro", SceneId.parse("  RPG:Intro ")!!.value)
        assertEquals("rpg", SceneId.parse("rpg:intro")!!.namespace)
        assertEquals("welcome", SceneId.parse("welcome")!!.name)
        assertNull(SceneId.parse("welcome")!!.namespace)
        var error = ""
        assertNull(SceneId.parse("bad id!") { error = it })
        assertTrue(error.contains("may only contain"))
        assertNull(SceneId.parse(""))
        assertNull(SceneId.parse("a:b:c"))
        assertNull(SceneId.parse("x".repeat(65)))
        assertNotEquals(SceneId.anonymous(), SceneId.anonymous())
    }

    @ParameterizedTest
    @EnumSource(Shape::class)
    fun `shapes produce finite, bounded geometry`(shape: Shape) {
        val pts = shape.generate(ShapeParams(radius = 3.0, height = 4.0, points = 64))
        assertTrue(pts.isNotEmpty())
        assertTrue(pts.size <= 4096)
        pts.forEach {
            assertTrue(it.isFinite())
            assertTrue(it.length() <= 3.0 * 1.5 + 4.0 + 1e-6, "$shape point $it too far")
        }
    }

    @Test
    fun `circle points lie on the radius and cache reuses geometry`() {
        Shape.CIRCLE.generate(ShapeParams(radius = 2.0, points = 16)).forEach { assertEquals(2.0, it.length(), 1e-9) }
        val cache = ShapeCache(2)
        val a = cache.get(Shape.SPHERE, ShapeParams())
        val b = cache.get(Shape.SPHERE, ShapeParams())
        assertSame(a, b)
        assertEquals(1L, cache.hits)
        cache.get(Shape.CIRCLE, ShapeParams())
        cache.get(Shape.HELIX, ShapeParams())
        assertEquals(2, cache.size())
        assertThrows(IllegalArgumentException::class.java) { ShapeParams(radius = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { ShapeParams(points = 100_000) }
    }

    @Test
    fun `line and lightning helpers`() {
        val line = Shape.line(Vec3.ZERO, Vec3(10.0, 0.0, 0.0), 1.0)
        assertEquals(11, line.size)
        val bolt = Shape.lightning(Vec3.ZERO, Vec3(0.0, 20.0, 0.0), 8, 1.0, 7L)
        assertTrue(bolt.first().distance(Vec3.ZERO) < 1e-9)
        assertTrue(bolt.last().distance(Vec3(0.0, 20.0, 0.0)) < 1e-9)
        assertEquals(bolt, Shape.lightning(Vec3.ZERO, Vec3(0.0, 20.0, 0.0), 8, 1.0, 7L))
    }
}
