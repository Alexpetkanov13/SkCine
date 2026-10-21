package dev.dheaven.skcine.core

import dev.dheaven.skcine.core.interpolation.Easing
import dev.dheaven.skcine.core.interpolation.Interpolation
import dev.dheaven.skcine.core.interpolation.RotationInterpolation
import dev.dheaven.skcine.core.math.Angles
import dev.dheaven.skcine.core.math.Vec3
import dev.dheaven.skcine.core.path.CameraTrack
import dev.dheaven.skcine.core.path.FixedShot
import dev.dheaven.skcine.core.path.FollowShot
import dev.dheaven.skcine.core.path.OrbitShot
import dev.dheaven.skcine.core.path.PoseKeyframe
import dev.dheaven.skcine.core.path.PosePath
import dev.dheaven.skcine.core.path.ShakeModifier
import dev.dheaven.skcine.core.path.TargetRef
import dev.dheaven.skcine.core.path.ZoomModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.math.abs

class CameraTests {
    private fun kf(t: Double, x: Double, y: Double = 0.0, z: Double = 0.0, yaw: Double = 0.0, pitch: Double = 0.0, easing: Easing = Easing.LINEAR) =
        PoseKeyframe(t, Vec3(x, y, z), yaw, pitch, easing)

    @ParameterizedTest
    @EnumSource(Interpolation::class)
    fun `path passes through every keyframe exactly`(mode: Interpolation) {
        val keys = listOf(kf(0.0, 0.0), kf(20.0, 10.0, 5.0), kf(60.0, 10.0, 5.0, 20.0), kf(70.0, -3.0))
        val path = PosePath(keys, mode)
        for (k in keys) {
            val p = path.evaluate(k.time)!!
            assertTrue(p.position.distance(k.position) < 1e-6, "$mode at ${k.time}: ${p.position}")
        }
    }

    @ParameterizedTest
    @EnumSource(Interpolation::class, names = ["LINEAR", "CATMULL_ROM", "BEZIER", "HERMITE"])
    fun `continuous paths have no teleport jumps`(mode: Interpolation) {
        val keys = listOf(kf(0.0, 0.0), kf(10.0, 4.0, 2.0), kf(50.0, 30.0, 2.0, 8.0), kf(55.0, 31.0, 2.0, 9.0))
        val path = PosePath(keys, mode)
        var prev = path.evaluate(0.0)!!.position
        var t = 0.0
        while (t <= 55.0) {
            val p = path.evaluate(t)!!.position
            assertTrue(p.distance(prev) < 1.5, "$mode jump at $t: ${p.distance(prev)}")
            prev = p
            t += 0.05
        }
    }

    @Test
    fun `bezier keeps velocity continuous across uneven keyframe spacing`() {
        // Uniform motion along X sampled at irregular times should stay uniform.
        val keys = listOf(kf(0.0, 0.0), kf(10.0, 10.0), kf(40.0, 40.0), kf(45.0, 45.0))
        val path = PosePath(keys, Interpolation.BEZIER)
        for (i in 0..450) {
            val t = i / 10.0
            assertEquals(t, path.evaluate(t)!!.position.x, 1e-6, "at $t")
        }
    }

    @Test
    fun `yaw crossing 180 degrees never spins the long way`() {
        val keys = listOf(kf(0.0, 0.0, yaw = 170.0), kf(20.0, 1.0, yaw = -170.0))
        for (mode in RotationInterpolation.entries) {
            val path = PosePath(keys, Interpolation.LINEAR, mode)
            var prevYaw = 170.0
            for (i in 0..20) {
                val yaw = path.evaluate(i.toDouble())!!.yaw
                assertTrue(abs(Angles.shortestDelta(prevYaw, yaw)) <= 1.01, "$mode jumped at $i: $prevYaw -> $yaw")
                prevYaw = yaw
            }
            assertEquals(180.0, abs(path.evaluate(10.0)!!.yaw), 1e-6)
        }
    }

    @Test
    fun `easing applies to the segment ending at a keyframe`() {
        val path = PosePath(listOf(kf(0.0, 0.0), kf(10.0, 10.0, easing = Easing.byName("ease-in-quad")!!)), Interpolation.LINEAR)
        assertEquals(2.5, path.evaluate(5.0)!!.position.x, 1e-9)
    }

    @Test
    fun `clamps outside range, single keyframe and empty path`() {
        val path = PosePath(listOf(kf(10.0, 1.0), kf(20.0, 2.0)), Interpolation.LINEAR)
        assertEquals(1.0, path.evaluate(-5.0)!!.position.x)
        assertEquals(2.0, path.evaluate(500.0)!!.position.x)
        assertEquals(3.0, PosePath(listOf(kf(5.0, 3.0))).evaluate(0.0)!!.position.x)
        assertNull(PosePath(emptyList()).evaluate(0.0))
    }

    @Test
    fun `duplicate keyframe times keep the last one`() {
        val path = PosePath(listOf(kf(0.0, 0.0), kf(10.0, 1.0), kf(10.0, 7.0)), Interpolation.LINEAR)
        assertEquals(2, path.keyframes.size)
        assertEquals(7.0, path.evaluate(10.0)!!.position.x)
    }

    @Test
    fun `invalid keyframes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { kf(Double.NaN, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { kf(-1.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { kf(0.0, Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `later shots override the keyframe path, then hold`() {
        val track = CameraTrack()
        track.addKeyframe(kf(0.0, 0.0))
        track.addKeyframe(kf(20.0, 20.0))
        track.addShot(FixedShot(Vec3(100.0, 0.0, 0.0), 0.0, 0.0, null, 10.0, 30.0))
        val c = track.compile()
        assertEquals(5.0, c.evaluate(5.0)!!.position.x, 1e-6)
        assertEquals(100.0, c.evaluate(15.0)!!.position.x, 1e-6)
        // After every layer ends, the most recently ended layer holds.
        assertEquals(100.0, c.evaluate(60.0)!!.position.x, 1e-6)
        assertEquals(30.0, c.endTime)
    }

    @Test
    fun `blend-in transitions are continuous`() {
        val track = CameraTrack()
        track.addKeyframe(kf(0.0, 0.0))
        track.addKeyframe(kf(40.0, 40.0))
        track.addShot(FixedShot(Vec3(0.0, 50.0, 0.0), 0.0, 0.0, null, 10.0, 40.0, blendIn = 10.0))
        val c = track.compile()
        val before = c.evaluate(9.999)!!.position
        val at = c.evaluate(10.0)!!.position
        assertTrue(before.distance(at) < 0.01)
        assertEquals(50.0, c.evaluate(20.0)!!.position.y, 1e-6)
    }

    @Test
    fun `orbit looks at its center at the requested radius`() {
        val center = Vec3(5.0, 64.0, 5.0)
        val shot = OrbitShot(TargetRef.fixed(center), 8.0, 3.0, 0.0, 360.0, 0.0, 100.0)
        for (t in listOf(0.0, 25.0, 50.0, 99.0)) {
            val pose = shot.evaluate(t)!!
            val flat = Vec3(pose.position.x - center.x, 0.0, pose.position.z - center.z)
            assertEquals(8.0, flat.length(), 1e-9)
            assertEquals(67.0, pose.position.y, 1e-9)
            val toCenter = (center - pose.position).normalized()
            assertTrue(pose.direction().distance(toCenter) < 1e-6)
        }
    }

    @Test
    fun `follow shot tracks a moving target and drops a vanished one`() {
        var pos: Vec3? = Vec3(0.0, 0.0, 0.0)
        val shot = FollowShot({ pos }, Vec3(0.0, 2.0, -4.0), true, 0.0, 0.0, 0.0, 100.0)
        assertEquals(-4.0, shot.evaluate(1.0)!!.position.z, 1e-9)
        pos = Vec3(10.0, 0.0, 0.0)
        assertEquals(10.0, shot.evaluate(2.0)!!.position.x, 1e-9)
        pos = null
        assertNull(shot.evaluate(3.0))
    }

    @Test
    fun `shake decays to nothing and zoom moves forward`() {
        val base = PosePath(listOf(kf(0.0, 0.0, yaw = 0.0))).evaluate(0.0)!!
        val shake = ShakeModifier(0.0, 20.0, 2.0, seed = 42)
        val mid = shake.apply(base, 5.0)
        assertTrue(mid.position.distance(base.position) > 0.0 || mid.yaw != base.yaw)
        assertEquals(base, shake.apply(base, 21.0))
        assertTrue(shake.apply(base, 20.0).position.distance(base.position) < 1e-9)
        val zoom = ZoomModifier(0.0, 10.0, 4.0)
        assertEquals(4.0, zoom.apply(base, 10.0).position.z, 1e-9)
    }
}
