package dev.dheaven.skcine.core

import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.core.interpolation.Splines
import dev.dheaven.skcine.core.math.Angles
import dev.dheaven.skcine.core.math.Quaternion
import dev.dheaven.skcine.core.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs

class InterpolationTests {
    companion object {
        @JvmStatic
        fun easings() = Easing.ids()
    }

    @ParameterizedTest
    @MethodSource("easings")
    fun `every easing hits exact endpoints`(id: String) {
        val e = Easing.byName(id)!!
        assertEquals(0.0, e.apply(0.0), 1e-9, id)
        assertEquals(1.0, e.apply(1.0), 1e-9, id)
        // Out-of-range and NaN input is clamped instead of producing garbage.
        assertEquals(0.0, e.apply(-5.0), 1e-9)
        assertEquals(1.0, e.apply(5.0), 1e-9)
        assertEquals(0.0, e.apply(Double.NaN), 1e-9)
    }

    @ParameterizedTest
    @MethodSource("easings")
    fun `non-overshooting easings stay in range and are finite`(id: String) {
        val e = Easing.byName(id)!!
        for (i in 0..1000) {
            val v = e.apply(i / 1000.0)
            assertTrue(v.isFinite(), "$id produced non-finite value")
            if (!e.overshoots) assertTrue(v >= -1e-9 && v <= 1 + 1e-9, "$id out of range at ${i / 1000.0}: $v")
        }
    }

    @Test
    fun `overshooting families really overshoot`() {
        val back = Easing.byName("ease-out-back")!!
        assertTrue((0..100).any { back.apply(it / 100.0) > 1.0 })
        assertTrue(Easing.byName("elastic-out")!!.overshoots)
    }

    @Test
    fun `aliases and normalization`() {
        assertEquals("ease-in-out-cubic", Easing.byName("ease-in-out")!!.id)
        assertEquals("ease-in-out-cubic", Easing.byName("EASE_IN_OUT_CUBIC")!!.id)
        assertEquals("ease-in-out-sine", Easing.byName("easeInOutSine")!!.id)
        assertEquals("ease-out-bounce", Easing.byName("bounce out")!!.id)
        assertEquals("smoothstep", Easing.byName("smooth")!!.id)
        assertNull(Easing.byName("wobbly"))
        assertEquals(0.5, Easing.SMOOTHSTEP.apply(0.5), 1e-12)
        assertEquals(0.5, Easing.byName("ease-in-out-quad")!!.apply(0.5), 1e-12)
    }

    @Test
    fun `interpolation names`() {
        assertEquals(Interpolation.CATMULL_ROM, Interpolation.byName("Catmull Rom"))
        assertEquals(Interpolation.BEZIER, Interpolation.byName("cubic-bezier"))
        assertEquals(Interpolation.HERMITE, Interpolation.byName("hermite"))
        assertNull(Interpolation.byName("teleport"))
    }

    @Test
    fun `yaw wrapping and shortest arc`() {
        assertEquals(-170.0, Angles.wrapDegrees(190.0), 1e-9)
        assertEquals(180.0, Angles.wrapDegrees(-180.0), 1e-9)
        assertEquals(20.0, Angles.shortestDelta(170.0, -170.0), 1e-9)
        // Lerp across the boundary goes through 180, not through 0.
        assertEquals(180.0, abs(Angles.lerpYaw(170.0, -170.0, 0.5)), 1e-9)
        val unwrapped = Angles.unwrapYaws(doubleArrayOf(170.0, -170.0, -150.0))
        assertEquals(190.0, unwrapped[1], 1e-9)
        assertEquals(210.0, unwrapped[2], 1e-9)
    }

    @Test
    fun `direction and look-at agree with Minecraft conventions`() {
        val south = Angles.direction(0.0, 0.0)
        assertEquals(1.0, south.z, 1e-9)
        val west = Angles.direction(90.0, 0.0)
        assertEquals(-1.0, west.x, 1e-9)
        val down = Angles.direction(0.0, 90.0)
        assertEquals(-1.0, down.y, 1e-9)
        val (yaw, pitch) = Angles.lookAt(Vec3(0.0, 0.0, 0.0), Vec3(-5.0, 0.0, 0.0))
        assertEquals(90.0, yaw, 1e-9)
        assertEquals(0.0, pitch, 1e-9)
    }

    @Test
    fun `quaternion round-trips yaw and pitch`() {
        for (yaw in listOf(-179.0, -90.0, 0.0, 45.0, 135.0, 180.0)) {
            for (pitch in listOf(-80.0, -30.0, 0.0, 30.0, 80.0)) {
                val (y, p) = Quaternion.fromYawPitch(yaw, pitch).toYawPitch()
                assertEquals(0.0, Angles.shortestDelta(yaw, y), 1e-6, "yaw $yaw/$pitch")
                assertEquals(pitch, p, 1e-6)
                val dir = Quaternion.fromYawPitch(yaw, pitch).rotate(Vec3.Z)
                val expected = Angles.direction(yaw, pitch)
                assertTrue(dir.distance(expected) < 1e-9)
            }
        }
    }

    @Test
    fun `slerp endpoints, midpoint and shortest path`() {
        val a = Quaternion.fromYawPitch(170.0, 0.0)
        val b = Quaternion.fromYawPitch(-170.0, 0.0)
        assertEquals(0.0, Quaternion.angleBetween(Quaternion.slerp(a, b, 0.0), a), 1e-6)
        assertEquals(0.0, Quaternion.angleBetween(Quaternion.slerp(a, b, 1.0), b), 1e-6)
        val (midYaw, _) = Quaternion.slerp(a, b, 0.5).toYawPitch()
        assertEquals(180.0, abs(midYaw), 1e-6)
        assertEquals(20.0, Quaternion.angleBetween(a, b), 1e-6)
    }

    @Test
    fun `hermite and bezier basics`() {
        assertEquals(0.0, Splines.hermite(0.0, 1.0, 1.0, 1.0, 0.0), 1e-12)
        assertEquals(1.0, Splines.hermite(0.0, 1.0, 1.0, 1.0, 1.0), 1e-12)
        assertEquals(0.5, Splines.hermite(0.0, 1.0, 1.0, 1.0, 0.5), 1e-12)
        assertEquals(0.5, Splines.bezier(0.0, 1.0 / 3, 2.0 / 3, 1.0, 0.5), 1e-12)
    }

    @Test
    fun `centripetal catmull-rom passes through control points and handles duplicates`() {
        val p0 = Vec3(0.0, 0.0, 0.0)
        val p1 = Vec3(1.0, 0.0, 0.0)
        val p2 = Vec3(2.0, 1.0, 0.0)
        val p3 = Vec3(3.0, 1.0, 0.0)
        assertTrue(Splines.catmullRom(p0, p1, p2, p3, 0.0).distance(p1) < 1e-9)
        assertTrue(Splines.catmullRom(p0, p1, p2, p3, 1.0).distance(p2) < 1e-9)
        val dup = Splines.catmullRom(p1, p1, p2, p2, 0.5)
        assertTrue(dup.isFinite())
        assertNotNull(dup)
    }
}
