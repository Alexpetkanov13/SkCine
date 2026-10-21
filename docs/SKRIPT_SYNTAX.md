# SkCine Skript syntax — conventions and overview

This page explains how SkCine syntax is organized and the rules every element follows. The per-element reference is split by kind:

| Kind | Reference |
|---|---|
| Structures (`cine scene`, `cine sequence`) and sections (`at`, `after`, `repeat every`, `present cine choice`, `a new cine scene:`) | [CUSTOM_SECTIONS.md](CUSTOM_SECTIONS.md) |
| Effects | [EFFECTS.md](EFFECTS.md) |
| Expressions | [EXPRESSIONS.md](EXPRESSIONS.md) |
| Conditions | [CONDITIONS.md](CONDITIONS.md) |
| Events | [EVENTS.md](EVENTS.md) |
| Types (`cinescene`, `cineplayback`, `cineactor`, `cineeffect`) | [CUSTOM_TYPES.md](CUSTOM_TYPES.md) |
| Every registered pattern, generated from Skript's live registry | [SYNTAX_INDEX.md](SYNTAX_INDEX.md) |

## Properties shared by every element

Unless an entry says otherwise:

- **Skript version:** 2.16.0 or newer (tested 2.16.2 and 2.17.0).
- **Minecraft/Paper:** 1.21.4 or newer, including 26.x (tested matrix in [COMPATIBILITY.md](COMPATIBILITY.md)).
- **Required addons:** none. PacketEvents is optional and unused by the syntax.
- **Thread:** all syntax runs on the server main thread. SkCine never calls Skript code from another thread.
- **Null safety:** unset values and expired handles never throw. Effects do nothing and report a diagnostic where useful, expressions return nothing, and conditions are false (or true when negated).
- **Error reporting:** runtime problems are logged once per 10 seconds per message as `[SkCine] [<script>.sk, line N] Scene '<id>': <problem>`. Parse problems are Skript errors shown by `/sk reload`.

## Naming

Every pattern contains the word `cine`, so SkCine never overrides Skript core or other addons. For example, it registers `move cine actor`, not `move entity`. Time values are Skript timespans (`2 seconds`, `10 ticks`, `1.5 seconds`) and are converted to ticks with millisecond precision.

## The scene context

Code inside `cine scene` timelines, sequences, timed sections and choice sections runs in a **scene context**. In that context:

- `event-player` is the scene's first viewer, and in a choice section it is the player who chose.
- `event-cineplayback` / `cine playback` is the playing instance.
- Content syntax may omit the scene: `add cine camera keyframe at {_l} at 2 seconds` targets the current scene.

Outside a scene context, name the target explicitly: `... to {_scene}`, `... in {_scene}`, `... of player`. Syntax that needs a scene and has none fails at parse time with a clear error, for example:

```
'add cine camera keyframe' must be used inside a cine scene timeline/sequence, or given a scene with 'in {_scene}'
```

## Definitions vs. playbacks

| | `cinescene` (definition) | `cineplayback` (instance) |
|---|---|---|
| Created by | `cine scene "id":` structure, `a new cine scene` | `play cine scene ...` |
| Holds | settings + timeline code + callbacks | clock, viewers, actors, effects, variables |
| Editing | affects **future** playbacks only | affects that playback immediately |

Content syntax accepts either. Given a definition, the content is added to every future playback. Given a playback, it is added live.

## Local variables

- The `timeline:` body of a `cine scene` sees the locals of the code that ran `play cine scene` ("caller locals"), so scenes work as parameterized templates.
- A `a new cine scene:` body sees the locals from where the scene was created.
- Each timed section gets its own copy of the locals that existed when it was declared, so changes inside one section do not leak into another.
- `play cine sequence` shares locals with the caller in both directions, like an inline function.
- Use `cine variable "name"` for state that must be shared across sections and callbacks.

## Easings and interpolation names

Accepted names are case-insensitive, and `_` or spaces work in place of `-`.

- Easings: `linear`, `smoothstep`, `smootherstep`, and `ease-in-X` / `ease-out-X` / `ease-in-out-X` for X in `sine quad cubic quart quint expo circ back elastic bounce`. Aliases include `ease-in`, `ease-out`, `ease-in-out` (cubic), `smooth`, `X-in-out`, `in-out-X`, and camel case such as `easeInOutSine`.
- Interpolation: `bezier` (default, time-aware), `catmull-rom` (`smooth`, `spline`), `hermite`, `linear`, `step`.
- Rotation interpolation: `euler` (default), `quaternion` (`slerp`).

The live lists are available from `cine easings`, `cine interpolations`, `cine shapes` and `cine presets`.

## Particles

Wherever a particle is expected you can use Skript's own particle values (`flame particle`, `end rod particle`, ...) or a name string (`"end_rod"`, `"soul fire flame"`). Particles that need data (dust and similar) get it from `with colour` or sensible defaults.

## Text

All text accepts MiniMessage (`<gold>`, `<gradient:red:blue>`) or legacy `&` codes, and mixing them is allowed.
