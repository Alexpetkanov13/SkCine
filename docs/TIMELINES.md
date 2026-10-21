# Timelines

Every playback has one timeline: a logical clock in **ticks** (fractional internally) that holds camera content, timed sections, repeating sections, markers and actor keyframe tracks.

## The clock

- The clock advances once per server tick by `speed` ticks (−10..10, configurable). It is a logical clock, not wall time, so a lag spike never causes a burst of catch-up actions.
- At most `limits.max-events-per-tick` (64) one-shot events run per tick; the rest are deferred in order to the next tick, so nothing is lost.
- A repeat that falls behind runs at most 4 times in one tick, then resynchronizes to its next future occurrence.
- **Pause** freezes the clock. **Waiting for input** (a pausing choice) also freezes it.
- **Negative speed** plays backwards: continuous content (camera, actor keyframes) reverses, and one-shot events never fire backwards. The scene ends at 0.
- **Duration:** the explicit `duration:`, or else the latest point any content reaches (camera, events, markers, finite repeats). It is capped by `limits.max-scene-duration-seconds` (default 30 min), which also bounds infinite loops.

## Event policies

| Policy | Syntax | Normal playback | Seek/skip **over** it | Rewind past it, then replay |
|---|---|---|---|---|
| once (default) | `at 3 seconds:` | runs | dropped | does **not** run again |
| guaranteed | `at 3 seconds even if skipped:` | runs | runs **once**, at skip time | does **not** run again |
| cosmetic | `at 3 seconds cosmetically:` | runs | dropped | runs again |

Looping starts a fresh iteration in which every event may run once more.

Put **rewards, economy and other gameplay** in `even if skipped` sections, or better, in your own code or the `on end:` callback. Put **visuals** in default or `cosmetically` sections.

Events at the same time run in the order they were added. Events added for a time that has already passed run on the next tick.

## Markers

```skript
add cine marker "climax" at 12 seconds
at marker "climax":
    shake the cine camera for 1 second
...
jump cine scene of player to marker "climax"
on cine marker "climax":
    ...
```
`on cine marker` fires when playback *passes* the marker, not when seeking over it.

## Controls

| Action | Syntax |
|---|---|
| pause / resume | `pause cine scene of player`, `resume cine playback {_p}` |
| seek | `seek cine scene of player to 5 seconds`, `set cine elapsed time of {_p} to 5 seconds` |
| jump to marker | `jump cine playback {_p} to marker "x"` |
| speed | `set cine speed of {_p} to 2` |
| restart | `restart cine scene of player` |
| loop | `loops: 2` entry (`-1` = until stopped) |
| skip | `skip cine scene of player` (runs guaranteed events once) |
| stop | `stop cine scene of player` |
| progress | `cine progress of {_p}` (0–1), `cine elapsed time`, `cine remaining time`, `cine duration`, `cine loop count` |

## Tracks

| Track | Created with | Seekable |
|---|---|---|
| Camera | keyframes, shots, modifiers | yes (pure function of time) |
| Actor movement/rotation | `add cine actor keyframe ...` | yes (driven by the scene clock) |
| One-shot events (title, sound, dialogue, gameplay, branching...) | `at`, `after`, `at marker` | per policy |
| Repeating (particles, ambience) | `repeat every` | resynchronizes |
| Nested sequences | `play cine sequence` | inlined at the offset |

Animations started *by* an event (`move cine actor ... over 2 seconds`) run in real time from when the event fired. They are not rewound by seeking; use actor keyframes when you need seekable motion.

## Live edits

Content can be added while a scene plays, from a timed section or from outside with `... in {_playback}`. The camera path is recompiled lazily on the next tick.
