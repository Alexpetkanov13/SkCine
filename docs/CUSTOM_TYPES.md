# Types

SkCine registers four Skript types. They are **runtime handles**: they work in local variables, lists, functions and memory-only (`{-...}`) variables, but they cannot be saved to disk. Skript warns if you store them in a normal global variable. Every syntax checks handles for validity, so keeping an expired handle is harmless.

| Type (code name) | User names | Represents |
|---|---|---|
| `cinescene` | cine scene(s) | A reusable scene **definition** (structure or runtime-created). |
| `cineplayback` | cine playback(s), cine scene instance(s) | One **playback** of a definition: its clock, viewers, actors and variables. |
| `cineactor` | cine actor(s) | An entity controlled by SkCine. **Converts to entity and location**, so Skript's own syntax accepts actors (`teleport`, `glowing`, `location of`...). |
| `cineeffect` | cine effect(s) | A running particle effect (preset or emitter). |

## Definition vs. playback

```skript
set {_def} to cine scene "rpg:intro"      # definition
play cine scene {_def} for player
set {_run} to cine playback of player     # playback
pause cine playback {_run}
```
A definition can be played any number of times, by anyone, concurrently. Each play creates an independent playback, or one shared playback with `together`. Editing a definition never changes playbacks that already started.

## Lifetime and safety

| Handle | Becomes invalid when | Behaviour afterwards |
|---|---|---|
| cinescene | Its script unloads (registered ones are unregistered) | Can still be played if you hold it; it is just no longer findable by ID. |
| cineplayback | It ends | State reads `completed`/`cancelled`/`failed`; control effects do nothing. |
| cineactor | Its scene ends, `remove cine actor`, or the entity disappears | `cine actor entity of` returns nothing and `is valid` is false. |
| cineeffect | Its lifetime ends or it is stopped | `is active` is false. |

## Values deliberately expressed as text

Easings, interpolation modes, shapes, presets, channels and skip modes are names (text or literal words) rather than separate types. This keeps scripts readable (`with easing "ease-out-back"`), lets them be validated with conditions (`cine easing {_e} is valid`), and makes them discoverable with list expressions (`cine easings`). Keyframes, markers, tracks and timelines are content of a playback, not stand-alone values. They are created and queried with the syntax in [EFFECTS.md](EFFECTS.md) and [EXPRESSIONS.md](EXPRESSIONS.md).
