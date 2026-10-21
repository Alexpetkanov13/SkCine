package dev.dheaven.skcine.core.timeline

import dev.dheaven.skcine.core.path.CameraTrack

/**
 * How a one-shot timeline event behaves when playback does not pass through it normally.
 *
 * | policy      | normal playback | seek/skip over it          | rewind past it, then replay |
 * |-------------|-----------------|----------------------------|-----------------------------|
 * | ONCE        | fires           | dropped                    | does not fire again         |
 * | GUARANTEED  | fires           | fires once, at skip time   | does not fire again         |
 * | COSMETIC    | fires           | dropped                    | fires again                 |
 *
 * Looping starts a fresh iteration, so every event may fire once per iteration.
 */
enum class EventPolicy {
    ONCE,
    GUARANTEED,
    COSMETIC,
}

/** Why an event action is being executed. */
enum class FireReason {
    /** Playback reached the event's time. */
    REACHED,

    /** A GUARANTEED event executed because playback skipped or seeked past it. */
    SKIPPED_OVER,
}

fun interface TimelineAction {
    fun fire(reason: FireReason)
}

class TimedEvent internal constructor(val id: Int, val time: Double, val policy: EventPolicy, val label: String, val action: TimelineAction)

/** Fires [action] every [interval] ticks from [start] until [end] (exclusive; null = until the scene ends). */
class Repeater internal constructor(val id: Int, val start: Double, val interval: Double, val end: Double?, val label: String, val action: TimelineAction)

data class Marker(val name: String, val time: Double)

/**
 * Mutable timeline content for one scene definition or instance.
 *
 * All times are logical ticks (1 tick = 50 ms at normal speed). Events are kept sorted by time, then insertion order,
 * so dispatch order is deterministic.
 */
class Timeline(val limits: TimelineLimits = TimelineLimits.DEFAULT) {
    private var nextId = 0
    private val _events = ArrayList<TimedEvent>()
    private val _repeaters = ArrayList<Repeater>()
    private val _markers = LinkedHashMap<String, Marker>()

    val camera = CameraTrack()

    /** Explicit duration in ticks, or null to derive it from content. */
    var explicitDuration: Double? = null
        set(value) {
            require(value == null || (value.isFinite() && value >= 0.0)) { "duration must be a finite, non-negative tick count" }
            require(
                value == null || value <= limits.maxDurationTicks,
            ) { "duration exceeds the configured maximum of ${limits.maxDurationTicks} ticks" }
            field = value
            revision++
        }

    /** Incremented on every structural change; players use it to refresh caches. */
    var revision = 0
        private set

    val events: List<TimedEvent> get() = _events
    val repeaters: List<Repeater> get() = _repeaters
    val markers: Collection<Marker> get() = _markers.values

    fun addEvent(time: Double, policy: EventPolicy, label: String, action: TimelineAction): TimedEvent {
        requireTime(time)
        check(_events.size < limits.maxEvents) { "timeline already has the maximum of ${limits.maxEvents} events" }
        val e = TimedEvent(nextId++, time, policy, label, action)
        // Insert after all events with time <= e.time so equal-time events keep insertion order.
        var lo = 0
        var hi = _events.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (_events[mid].time <= time) lo = mid + 1 else hi = mid
        }
        _events.add(lo, e)
        revision++
        return e
    }

    fun removeEvent(id: Int): Boolean = _events.removeAll { it.id == id }.also { if (it) revision++ }

    fun addRepeater(start: Double, interval: Double, end: Double?, label: String, action: TimelineAction): Repeater {
        requireTime(start)
        require(interval.isFinite() && interval >= limits.minRepeatInterval) {
            "repeat interval must be at least ${limits.minRepeatInterval} tick(s)"
        }
        if (end != null) {
            requireTime(end)
            require(end >= start) { "repeat end must not be before its start" }
        }
        check(_repeaters.size < limits.maxRepeaters) { "timeline already has the maximum of ${limits.maxRepeaters} repeating sections" }
        val r = Repeater(nextId++, start, interval, end, label, action)
        _repeaters.add(r)
        revision++
        return r
    }

    fun addMarker(name: String, time: Double) {
        requireTime(time)
        require(name.isNotBlank()) { "marker name must not be blank" }
        check(_markers.size < limits.maxMarkers || _markers.containsKey(name)) {
            "timeline already has the maximum of ${limits.maxMarkers} markers"
        }
        _markers[name] = Marker(name, time)
        revision++
    }

    fun marker(name: String): Marker? = _markers[name]

    fun removeMarker(name: String) = (_markers.remove(name) != null).also { if (it) revision++ }

    /** Effective duration: explicit, or the latest point any content reaches. */
    fun duration(): Double {
        explicitDuration?.let { return it }
        var d = camera.compile().endTime
        _events.lastOrNull()?.let { d = maxOf(d, it.time) }
        _markers.values.forEach { d = maxOf(d, it.time) }
        _repeaters.forEach { r -> r.end?.let { d = maxOf(d, it) } ?: run { d = maxOf(d, r.start) } }
        return d.coerceAtMost(limits.maxDurationTicks)
    }

    private fun requireTime(time: Double) {
        require(time.isFinite() && time >= 0.0) { "time must be a finite, non-negative number of ticks" }
        require(time <= limits.maxDurationTicks) { "time $time exceeds the maximum scene duration of ${limits.maxDurationTicks} ticks" }
    }
}

/** Hard resource bounds, protecting the server from runaway or malicious scripts. */
data class TimelineLimits(
    val maxDurationTicks: Double = 20.0 * 60 * 30,
    val maxEvents: Int = 2_000,
    val maxRepeaters: Int = 128,
    val maxMarkers: Int = 256,
    val maxEventsPerTick: Int = 64,
    val maxRepeatFiresPerTick: Int = 4,
    val minRepeatInterval: Double = 1.0,
    val maxSpeed: Double = 10.0,
    val maxLoops: Int = 10_000,
) {
    companion object {
        val DEFAULT = TimelineLimits()
    }
}
