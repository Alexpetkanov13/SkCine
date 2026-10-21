package dev.dheaven.skcine.core

import dev.dheaven.skcine.core.timeline.AdvanceResult
import dev.dheaven.skcine.core.timeline.EventPolicy
import dev.dheaven.skcine.core.timeline.FireReason
import dev.dheaven.skcine.core.timeline.Marker
import dev.dheaven.skcine.core.timeline.Repeater
import dev.dheaven.skcine.core.timeline.TimedEvent
import dev.dheaven.skcine.core.timeline.Timeline
import dev.dheaven.skcine.core.timeline.TimelineLimits
import dev.dheaven.skcine.core.timeline.TimelineListener
import dev.dheaven.skcine.core.timeline.TimelinePlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineTests {
    private class Recorder : TimelineListener {
        val log = ArrayList<String>()

        override fun onEvent(event: TimedEvent, reason: FireReason) {
            log += if (reason == FireReason.REACHED) event.label else "${event.label}!skip"
        }

        override fun onRepeat(repeater: Repeater) {
            log += repeater.label
        }

        override fun onMarker(marker: Marker) {
            log += "marker:${marker.name}"
        }

        override fun onLoop(iteration: Int) {
            log += "loop:$iteration"
        }
    }

    private fun timeline(vararg events: Pair<Double, EventPolicy>): Timeline {
        val t = Timeline()
        events.forEachIndexed { i, (time, policy) -> t.addEvent(time, policy, "e$i") {} }
        return t
    }

    private fun playAll(p: TimelinePlayer, r: Recorder, maxTicks: Int = 10_000): Int {
        var n = 0
        while (p.advance(r) == AdvanceResult.RUNNING && n < maxTicks) n++
        return n
    }

    @Test
    fun `events fire in time order with stable ordering for ties`() {
        val t = Timeline()
        t.addEvent(10.0, EventPolicy.ONCE, "b") {}
        t.addEvent(0.0, EventPolicy.ONCE, "a") {}
        t.addEvent(10.0, EventPolicy.ONCE, "c") {}
        t.addEvent(20.0, EventPolicy.ONCE, "end") {}
        val r = Recorder()
        playAll(TimelinePlayer(t), r)
        assertEquals(listOf("a", "b", "c", "end"), r.log)
    }

    @Test
    fun `event at time zero fires on first tick and duration derives from content`() {
        val t = timeline(0.0 to EventPolicy.ONCE, 40.0 to EventPolicy.ONCE)
        val p = TimelinePlayer(t)
        val r = Recorder()
        p.advance(r)
        assertEquals(listOf("e0"), r.log)
        assertEquals(40.0, p.duration())
        val ticks = playAll(p, r)
        assertEquals(38, ticks)
        assertEquals(listOf("e0", "e1"), r.log)
    }

    @Test
    fun `explicit duration and markers`() {
        val t = Timeline()
        t.explicitDuration = 100.0
        t.addMarker("climax", 50.0)
        val r = Recorder()
        val p = TimelinePlayer(t)
        playAll(p, r)
        assertEquals(listOf("marker:climax"), r.log)
        assertEquals(100.0, p.position)
    }

    @Test
    fun `skip drops ONCE events but runs GUARANTEED ones exactly once`() {
        val t =
            timeline(
                10.0 to EventPolicy.ONCE,
                20.0 to EventPolicy.GUARANTEED,
                30.0 to EventPolicy.COSMETIC,
                40.0 to EventPolicy.GUARANTEED,
            )
        val p = TimelinePlayer(t)
        val r = Recorder()
        repeat(25) { p.advance(r) }
        assertEquals(listOf("e0", "e1"), r.log)
        p.skipToEnd(r)
        p.skipToEnd(r)
        assertEquals(listOf("e0", "e1", "e3!skip"), r.log)
    }

    @Test
    fun `seeking backward never duplicates gameplay events`() {
        val t = timeline(10.0 to EventPolicy.ONCE, 10.0 to EventPolicy.GUARANTEED, 10.0 to EventPolicy.COSMETIC)
        t.explicitDuration = 40.0
        val p = TimelinePlayer(t)
        val r = Recorder()
        repeat(15) { p.advance(r) }
        assertEquals(listOf("e0", "e1", "e2"), r.log)
        p.seek(0.0, r)
        repeat(15) { p.advance(r) }
        // Only the cosmetic event replays.
        assertEquals(listOf("e0", "e1", "e2", "e2"), r.log)
    }

    @Test
    fun `seeking forward runs guaranteed events once and drops the rest`() {
        val t = timeline(10.0 to EventPolicy.ONCE, 15.0 to EventPolicy.GUARANTEED, 50.0 to EventPolicy.ONCE)
        t.explicitDuration = 60.0
        t.addMarker("m", 12.0)
        val p = TimelinePlayer(t)
        val r = Recorder()
        p.seek(30.0, r)
        assertEquals(listOf("e1!skip"), r.log)
        p.seek(5.0, r)
        p.seek(30.0, r)
        assertEquals(listOf("e1!skip"), r.log)
        playAll(p, r)
        assertEquals(listOf("e1!skip", "e2"), r.log)
    }

    @Test
    fun `looping replays every event once per iteration`() {
        val t = timeline(0.0 to EventPolicy.ONCE, 5.0 to EventPolicy.GUARANTEED)
        t.explicitDuration = 10.0
        val p = TimelinePlayer(t)
        p.loops = 2
        val r = Recorder()
        playAll(p, r)
        assertEquals(listOf("e0", "e1", "loop:1", "e0", "e1", "loop:2", "e0", "e1"), r.log)
    }

    @Test
    fun `catch-up is bounded per tick and nothing is lost`() {
        val t = Timeline(TimelineLimits(maxEventsPerTick = 3))
        repeat(10) { t.addEvent(0.0, EventPolicy.ONCE, "e$it") {} }
        t.explicitDuration = 1.0
        val p = TimelinePlayer(t)
        val r = Recorder()
        p.advance(r)
        assertEquals(3, r.log.size)
        playAll(p, r)
        assertEquals((0 until 10).map { "e$it" }, r.log)
    }

    @Test
    fun `repeaters fire on schedule and never build a backlog`() {
        val t = Timeline()
        t.addRepeater(0.0, 5.0, 20.0, "tick") {}
        t.explicitDuration = 30.0
        val p = TimelinePlayer(t)
        val r = Recorder()
        playAll(p, r)
        assertEquals(4, r.log.count { it == "tick" })

        val fast = Timeline()
        fast.addRepeater(0.0, 1.0, null, "f") {}
        fast.explicitDuration = 1000.0
        val fp = TimelinePlayer(fast)
        fp.speed = 10.0
        val fr = Recorder()
        fp.advance(fr)
        // 10 occurrences were due but the per-tick cap applies, then it resynchronises.
        assertEquals(TimelineLimits.DEFAULT.maxRepeatFiresPerTick, fr.log.size)
    }

    @Test
    fun `speed scales logical time and pausing is just not advancing`() {
        val t = timeline(20.0 to EventPolicy.ONCE)
        t.explicitDuration = 40.0
        val p = TimelinePlayer(t)
        p.speed = 2.0
        val r = Recorder()
        repeat(10) { p.advance(r) }
        assertEquals(20.0, p.position)
        assertEquals(0, r.log.size)
        p.advance(r)
        assertEquals(listOf("e0"), r.log)
        p.speed = 1000.0
        assertEquals(TimelineLimits.DEFAULT.maxSpeed, p.speed)
    }

    @Test
    fun `reverse playback finishes at zero without firing events`() {
        val t = timeline(5.0 to EventPolicy.ONCE)
        t.explicitDuration = 10.0
        val p = TimelinePlayer(t)
        p.seek(10.0, Recorder())
        p.speed = -1.0
        val r = Recorder()
        val ticks = playAll(p, r)
        assertEquals(9, ticks)
        assertTrue(r.log.isEmpty())
    }

    @Test
    fun `events added during playback in the past fire next tick`() {
        val t = Timeline()
        t.explicitDuration = 20.0
        val p = TimelinePlayer(t)
        val r = Recorder()
        repeat(5) { p.advance(r) }
        t.addEvent(1.0, EventPolicy.ONCE, "late") {}
        p.advance(r)
        assertEquals(listOf("late"), r.log)
    }

    @Test
    fun `limits reject invalid input`() {
        val t = Timeline(TimelineLimits(maxEvents = 1, maxDurationTicks = 100.0))
        t.addEvent(1.0, EventPolicy.ONCE, "a") {}
        assertThrows(IllegalStateException::class.java) { t.addEvent(2.0, EventPolicy.ONCE, "b") {} }
        assertThrows(IllegalArgumentException::class.java) { Timeline().addEvent(Double.NaN, EventPolicy.ONCE, "x") {} }
        assertThrows(IllegalArgumentException::class.java) { t.explicitDuration = 101.0 }
        assertThrows(IllegalArgumentException::class.java) { Timeline().addRepeater(0.0, 0.0, null, "r") {} }
    }

    @Test
    fun `infinite loops are bounded by the total duration limit`() {
        val t = Timeline(TimelineLimits(maxDurationTicks = 100.0))
        t.explicitDuration = 10.0
        val p = TimelinePlayer(t)
        p.loops = -1
        val ticks = playAll(p, Recorder())
        assertTrue(ticks in 95..101, "ticks=$ticks")
    }
}
