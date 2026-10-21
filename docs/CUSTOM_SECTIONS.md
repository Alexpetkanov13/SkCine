# Structures and sections

Shared properties (Skript 2.16+, Paper 1.21.4+, main thread, null-safe) are described in [SKRIPT_SYNTAX.md](SKRIPT_SYNTAX.md).

---

## cine scene

```
cine scene %string%:
```

Declares a named, reusable scene at the top level of a script. The ID is validated (`a-z 0-9 _ - . /` plus one optional `namespace:` prefix, at most 64 characters) and lower-cased.

| Entry | Type | Default | Meaning |
|---|---|---|---|
| `duration:` | timespan | derived | Fixed length. Without it, the scene ends at its latest keyframe, shot end, event or marker. Capped by `limits.max-scene-duration-seconds`. |
| `skippable:` | boolean | `true` | Viewers may skip by sneaking (or via `request a cine skip`). |
| `lock input:` | boolean | `true` | Blocks interactions, item use, drops, hand swaps and inventories while watching. |
| `freeze:` | boolean | `false` | Blocks walking. Only relevant to scenes **without** a camera; camera scenes always put the viewer in spectator. |
| `invulnerable:` | boolean | `true` | Cancels damage to viewers. |
| `restore location:` | boolean | `true` | Teleports viewers back to where they started. `cine end location` overrides it. |
| `speed:` | number | `1` | Playback speed, from more than 0 up to 10. |
| `loops:` | number | `0` | Extra iterations; `-1` loops until stopped (still bounded by the duration limit). |
| `skip mode:` | text | `individual` | For shared scenes: `individual` (the skipper leaves), `anyone` (one skip ends it for all), `vote` (majority). |
| `timeline:` | section | — | Skript code run **each time the scene starts** to build that playback's timeline. |
| `on start:` | section | — | Runs after viewers are attached. |
| `on finish:` | section | — | Runs when the scene completes normally. |
| `on skip:` | section | — | Runs when the scene is skipped. |
| `on cancel:` | section | — | Runs when stopped, interrupted (disconnect, script unload, ...) or failed. |
| `on end:` | section | — | Always runs last, whatever the outcome. |

Callbacks run **after** viewers are restored, so they can safely teleport players or give items. Inside them, `event-player` is the first viewer and `cine end reason of cine playback` tells you why the scene ended.

**Errors (parse time):** invalid ID, unknown skip mode, speed out of range, a duration above the configured maximum, unknown entries, and a duplicate ID owned by another script (the message names both scripts). The same script may re-register its own ID on reload.

**Reload:** when the script is unloaded or reloaded, its definitions are unregistered. With the default `scripts.reload-policy: STOP`, its playing scenes end and viewers are restored.

```skript
cine scene "rpg:intro":
    duration: 10 seconds
    skip mode: vote
    timeline:
        set {_c} to location of event-player
        add cine camera keyframe at {_c} ~ vector(0, 8, -8) at 0 seconds looking at {_c}
    on end:
        send "Intro over." to event-player
```

---

## cine sequence

```
cine sequence %string%:
```

A reusable block of timeline code (the body is plain Skript code, not entries). Insert it into a playing or building scene with `play cine sequence "<name>" [at <time>]`. All times inside are offset by the insertion time. The sequence shares local variables with the caller, which act as parameters, and may nest other sequences up to `limits.max-nested-sequences` (default 8).

```skript
cine sequence "fx:burst":
    at 0 seconds:
        play cine preset "fire-burst" at {_at}

cine scene "demo":
    timeline:
        set {_at} to location of event-player
        play cine sequence "fx:burst" at 2 seconds
```

**Errors:** invalid name (parse), duplicate name from another script (parse), unknown sequence or excessive nesting (runtime, reported with line).

---

## at / after / at marker

```
at %timespan% [(of|in) %-cinescene/cineplayback%] [(1:even if skipped|2:cosmetically)]
after %timespan% [(of|in) %-cinescene/cineplayback%] [(1:even if skipped|2:cosmetically)]
at [cine] marker %string% [(of|in) %-cinescene/cineplayback%] [(1:even if skipped|2:cosmetically)]
```

Schedules the indented code on the timeline.

- `at` is absolute scene time, plus the sequence offset inside sequences.
- `after` is relative to *now* during playback (in a `timeline:` body it behaves like `at`).
- `at marker` uses a marker's time; add the marker first.
- **Policies:** the default runs **once** when reached, and is dropped if playback skips or seeks past it. `even if skipped` runs exactly once even when skipped or seeked past. `cosmetically` may run again after rewinding. See [TIMELINES.md](TIMELINES.md#event-policies).

Inside the section: `event-player`, `event-cineplayback`, and a fresh copy of the locals from the declaration point.

**Errors:** used outside a scene without `in {_scene}` (parse); a missing body (parse); an unknown marker or a time beyond the scene limit (runtime).

```skript
at 3 seconds:
    show cine title "<red>Danger!"
at 9 seconds even if skipped:
    give player 1 diamond
```

---

## repeat every

```
repeat every %timespan% [from %-timespan%] [(until|to) %-timespan%] [(of|in) %-cinescene/cineplayback%]
```

Runs the body periodically (cosmetic: repeats are not executed when skipping). The minimum interval is 1 tick. A lagging repeat never builds a backlog; at most 4 catch-up runs happen per tick, then it resynchronizes.

```skript
repeat every 10 ticks from 1 second until 5 seconds:
    draw a cine ring of "end_rod" at {_c} with radius 2
```

---

## present cine choice

```
present [a] cine choice %string% (to|for) %players/cineplaybacks% with options %strings% [with timeout %-timespan%] [defaulting to %-string%] [vote:as a vote] [menu:(in|using|via) [a|an] [inventory] menu] [pause:pausing [the] scene]
```

Shows the prompt and numbered, clickable options in chat (up to 9), or with `using a menu` a one-row inventory menu titled with the prompt (works while the viewer is spectating the camera). Players click an option or type its number or label. Closing the menu without choosing sends the chat options instead. Selections are validated on the server against the audience and the option list.

- Per player (default): the body runs once **per player** as they answer, with `event-player` and `cine choice` set to the selected option's plain text.
- `as a vote`: the body runs **once** with the majority option. Ties go to the earlier option.
- `with timeout`: unanswered players get the default option (or none). Use `cine choice timed out` to detect it.
- `pausing the scene`: timeline time stops (state `waiting for input`) until everyone has answered or the timeout fires.

**Errors (runtime):** no options, more than 9 options, no online players, a default option that is not one of the options, or the active-choice limit reached.

```skript
present cine choice "Join the guild?" to cine viewers with options "Yes" and "No" with timeout 20 seconds defaulting to "No" pausing the scene:
    if cine choice is "Yes":
        jump the cine scene to marker "joined"
```

---

## a new cine scene (section expression)

```
[a] new cine scene [(named|with id) %-string%]
```

Creates a scene **definition** at runtime. With an indented body, the body is the scene's timeline code: it runs every time the scene plays, with a copy of the locals from where it was created. Without an ID, the scene gets a unique `dynamic:scene-N` ID and is not registered (use `register cine scene`).

```skript
set {_scene} to a new cine scene:
    add cine camera keyframe at {_loc} at 0 seconds
play cine scene {_scene} for player
```

---

## Why sections, not nested custom grammar

SkCine nests normal Skript sections, so all of Skript (conditions, loops, functions, variables) works inside them and Skript does all the parsing. There are no parser hacks. Nested definitions use Skript's structure/entry API, which works on every supported Skript version (2.16+), so no fallback format is needed.
