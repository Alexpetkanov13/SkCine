package dev.dheaven.skcine.core.timeline

import dev.dheaven.skcine.core.path.CompiledCameraTrack
import kotlin.math.ceil
import kotlin.math.floor

/** Receives everything a playback step produced, in deterministic order. */
interface TimelineListener {
    fun onEvent(event: TimedEvent, reason: FireReason) = event.action.fire(reason)

    fun onRepeat(repeater: Repeater) = repeater.action.fire(FireReason.REACHED)

    fun onMarker(marker: Marker) {}

    fun onLoop(iteration: Int) {}

    /**
     * Checked before each event. Returning false (for example because an event paused or ended the scene) stops
     * dispatch for this step; undispatched events stay pending and fire on the next advance.
     */
    fun canContinue(): Boolean = true
}

/** Outcome of [TimelinePlayer.advance]. */
enum class AdvanceResult { RUNNING, FINISHED }

/**
 * Logical clock and event dispatcher for one playing timeline.
 *
 * Time only moves when [advance] is called (once per server tick by the scheduler), so a lag spike never produces
 * unbounded catch-up: at most [TimelineLimits.maxEventsPerTick] events run per step and the remainder are deferred
 * to the next step in order.
 */
class TimelinePlayer(val timeline: Timeline) {
    private val limits = timeline.limits

    var position = 0.0
        private set

    var speed = 1.0
        set(value) {
            require(value.isFinite()) { "speed must be finite" }
            field = value.coerceIn(-limits.maxSpeed, limits.maxSpeed)
        }

    /** Number of extra iterations to play; -1 loops until stopped (still bounded by the scene duration limit). */
    var loops = 0
        set(value) {
            field = value.coerceIn(-1, limits.maxLoops)
        }

    var iteration = 0
        private set

    /** Total logical ticks played across iterations; bounded by [TimelineLimits.maxDurationTicks]. */
    var totalTicks = 0.0
        private set

    // Event ids are small sequential ints per timeline, so bit sets avoid boxing in the hot path.
    private val fired = java.util.BitSet()
    private val everFired = java.util.BitSet()

    /** Events before this index are all fired or skipped; scanning starts here. */
    private var scanStart = 0
    private var scanRevision = -1
    private var sortedMarkers: List<Marker> = emptyList()
    private val markersPassed = HashSet<String>()
    private val nextRepeat = HashMap<Int, Double>()
    private var cachedDuration = -1.0
    private var cachedRevision = -1
    private var cachedCameraRevision = -1
    private var compiledCamera: CompiledCameraTrack? = null

    fun duration(): Double {
        refreshCaches()
        return cachedDuration
    }

    fun camera(): CompiledCameraTrack {
        refreshCaches()
        return compiledCamera!!
    }

    private fun refreshCaches() {
        if (cachedRevision != timeline.revision || cachedCameraRevision != timeline.camera.revision || compiledCamera == null) {
            compiledCamera = timeline.camera.compile()
            cachedRevision = timeline.revision
            cachedCameraRevision = timeline.camera.revision
            cachedDuration = timeline.duration()
        }
    }

    val progress: Double get() = duration().let { if (it <= 0.0) 1.0 else (position / it).coerceIn(0.0, 1.0) }

    /**
     * Advances the clock by [ticks] × [speed] and dispatches due events, repeaters and markers.
     */
    fun advance(listener: TimelineListener, ticks: Double = 1.0): AdvanceResult {
        val delta = ticks * speed
        val end = duration()
        totalTicks += kotlin.math.abs(delta)
        if (totalTicks > limits.maxDurationTicks) return AdvanceResult.FINISHED
        if (delta < 0) {
            // Reverse playback re-evaluates continuous tracks only; one-shot events never fire backwards.
            position = (position + delta).coerceAtLeast(0.0)
            return if (position <= 0.0) AdvanceResult.FINISHED else AdvanceResult.RUNNING
        }
        val target = position + delta
        if (target >= end) {
            val complete = dispatchUntil(listener, end, inclusive = true)
            if (!complete) {
                // Deferred events remain: finish them on following ticks before completing.
                position = end
                return AdvanceResult.RUNNING
            }
            position = end
            if (loops != 0 && end > 0.0) {
                if (loops > 0) loops--
                iteration++
                resetIteration()
                position = (target - end).coerceIn(0.0, end)
                listener.onLoop(iteration)
                return AdvanceResult.RUNNING
            }
            return AdvanceResult.FINISHED
        }
        dispatchUntil(listener, target, inclusive = false)
        position = target
        return AdvanceResult.RUNNING
    }

    /** @return false if the per-tick event budget was exhausted and events were deferred. */
    private fun dispatchUntil(listener: TimelineListener, target: Double, inclusive: Boolean): Boolean {
        if (scanRevision != timeline.revision) {
            // Structure changed (events/markers added): rescan from the start once and refresh caches.
            scanRevision = timeline.revision
            scanStart = 0
            sortedMarkers = timeline.markers.sortedBy { it.time }
        }
        val events = timeline.events
        while (scanStart < events.size && fired.get(events[scanStart].id)) scanStart++
        // Snapshot due events first: actions may add events while we dispatch. Events are sorted by time.
        var due: ArrayList<TimedEvent>? = null
        var i = scanStart
        while (i < events.size) {
            val e = events[i]
            if (if (inclusive) e.time > target else e.time >= target) break
            if (!fired.get(e.id)) (due ?: ArrayList<TimedEvent>().also { due = it }).add(e)
            i++
        }
        var budget = limits.maxEventsPerTick
        var complete = true
        due?.let {
            for (e in it) {
                if (!listener.canContinue()) return false
                if (budget-- <= 0) {
                    complete = false
                    break
                }
                fired.set(e.id)
                everFired.set(e.id)
                listener.onEvent(e, FireReason.REACHED)
            }
        }
        for (m in sortedMarkers) {
            if (if (inclusive) m.time > target else m.time >= target) break
            if (markersPassed.add(m.name)) listener.onMarker(m)
        }
        val repeaters = timeline.repeaters
        val count = repeaters.size
        for (k in 0 until count) {
            val r = repeaters[k]
            var next = nextRepeat.getOrPut(r.id) { r.start }
            var fires = 0
            while (next < target && (r.end == null || next < r.end) && fires < limits.maxRepeatFiresPerTick) {
                listener.onRepeat(r)
                next += r.interval
                fires++
            }
            // Never accumulate a backlog: if we fell behind, resynchronise to the next future occurrence.
            if (next < target) next = alignRepeat(r, target)
            nextRepeat[r.id] = next
        }
        return complete
    }

    private fun alignRepeat(r: Repeater, time: Double): Double {
        if (time <= r.start) return r.start
        val n = ceil((time - r.start) / r.interval)
        return r.start + n * r.interval
    }

    private fun resetIteration() {
        scanStart = 0
        fired.clear()
        markersPassed.clear()
        nextRepeat.clear()
        everFired.clear()
    }

    /**
     * Moves the playhead to [time].
     *
     * Forward seeks drop ONCE/COSMETIC events in between and execute GUARANTEED ones exactly once. Backward seeks
     * re-arm COSMETIC events only, so rewards and other gameplay actions are never duplicated.
     */
    fun seek(time: Double, listener: TimelineListener) {
        require(time.isFinite()) { "seek time must be finite" }
        val target = time.coerceIn(0.0, duration())
        if (target >= position) {
            for (e in timeline.events.filter { it.time < target && !fired.get(it.id) }) {
                fired.set(e.id)
                if (e.policy == EventPolicy.GUARANTEED && !everFired.get(e.id)) {
                    everFired.set(e.id)
                    listener.onEvent(e, FireReason.SKIPPED_OVER)
                }
            }
            timeline.markers.filter { it.time < target }.forEach { markersPassed.add(it.name) }
        } else {
            for (e in timeline.events) {
                if (e.time >= target && e.policy == EventPolicy.COSMETIC) fired.clear(e.id)
            }
            timeline.markers.filter { it.time >= target }.forEach { markersPassed.remove(it.name) }
        }
        timeline.repeaters.forEach { nextRepeat[it.id] = alignRepeat(it, target) }
        scanStart = 0
        position = target
    }

    /** Skips to the end: runs every not-yet-executed GUARANTEED event once, in timeline order. */
    fun skipToEnd(listener: TimelineListener) {
        val end = duration()
        for (e in timeline.events) {
            if (fired.get(e.id)) continue
            fired.set(e.id)
            if (e.policy == EventPolicy.GUARANTEED && !everFired.get(e.id)) {
                everFired.set(e.id)
                listener.onEvent(e, FireReason.SKIPPED_OVER)
            }
        }
        loops = 0
        position = end
    }

    /** Restarts from zero as a fresh iteration (all events may fire again). */
    fun restart() {
        resetIteration()
        position = 0.0
        iteration = 0
    }

    companion object {
        fun ticksToSeconds(ticks: Double) = ticks / 20.0

        fun secondsToTicks(seconds: Double) = seconds * 20.0

        fun wholeTick(ticks: Double) = floor(ticks).toLong()
    }
}
