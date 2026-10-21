<div align="center">

<img src="branding/skcine-icon-pixel.png" alt="SkCine" width="128">

# SkCine

**Bring your Skripts to life.**

Cinematic cutscenes for Minecraft, written in plain Skript:<br>
cameras, timelines, actors, particles, sound, dialogue and choices.

![Paper](https://img.shields.io/badge/Paper-1.21.4%2B%20%7C%2026.x-blue)
![Skript](https://img.shields.io/badge/Skript-2.16%2B-orange)
![Java](https://img.shields.io/badge/Java-21%20%7C%2025-red)
![License](https://img.shields.io/badge/license-GPL--3.0-green)

</div>

---

## Why SkCine?

Most cutscene tools give you an editor, a config format or a pile of commands. SkCine gives you **Skript syntax**. A scene is an ordinary `.sk` file: it reloads with `/sk reload`, lives in version control with the rest of your scripts, and can use every variable, function and condition you already have.

```skript
cine scene "rpg:welcome":
    skippable: true
    timeline:
        set {_c} to location of event-player
        add cine camera keyframe at {_c} ~ vector(0, 12, -14) at 0 seconds looking at {_c}
        add cine camera keyframe at {_c} ~ vector(12, 7, 0) at 4 seconds with easing "ease-in-out-sine" looking at {_c}
        add cine camera keyframe at {_c} ~ vector(0, 3, 6) at 8 seconds with easing "ease-in-out-sine" looking at {_c}
        at 1 second:
            show cine title "<gold><bold>Welcome!" with subtitle "<gray>Sneak to skip" for 3 seconds
            play cine sound "entity.player.levelup"
    on finish:
        send "&aWelcome aboard!" to event-player

command /welcome:
    trigger:
        play cine scene "rpg:welcome" for player
```

## Features

<table>
<tr><td width="50%" valign="top">

### 🎥 Cameras
- Keyframe paths: Bézier, Catmull–Rom, Hermite, linear, step
- 33 easings, smooth quaternion rotation
- Orbit, follow, track and hold shots with blending
- Shake, dolly zoom, full-screen fades and flashes, all without a resource pack

### ⏱️ Timelines
- `at 3 seconds:`, `after`, `at marker`, `repeat every`
- Pause, resume, seek, speed, reverse, loops
- Rewards fire **exactly once**, even when players skip or seek
- Reusable, nestable, parameterised sequences

### 🧍 Actors
- Any mob, player-looking actors with skins (1.21.9+), item/block/text displays
- Visible only to the scene's viewers
- Move, pathfind, rotate, look at, keyframed paths, parenting, group rotation, scale
- Cleaned up automatically

</td><td width="50%" valign="top">

### ✨ Visual effects
- 19 particle shapes, beams, lightning, emitters
- 22 themeable presets
- Per-player budgets and adaptive quality

### 💬 Dialogue and choices
- Typewriter or word-by-word text in chat, title, subtitle, action bar, boss bar or floating text
- MiniMessage, typing sounds, manual advance
- Choices via clickable chat, typed replies or an **inventory menu**, per player or by vote

### 👥 Multiplayer and safety
- Individual or shared playback, late joining, skip voting
- Game mode, location and flight always restored, even after a crash
- One scheduler that idles to zero, a built-in profiler, errors reported with script and line

</td></tr>
</table>

## 👑 Showcase: *Echoes of the Shattered Crown*

[`showcase/shattered-crown/`](showcase/shattered-crown) is a complete cinematic campaign of about **10,000 lines of Skript** across 29 files, built only on SkCine. It includes:

- a prologue, five chapters, six interludes, a finale, **four endings** and personalised credits;
- choices remembered across chapters, karma, and a secret ending;
- playable trials and five guardians with telegraphed attacks and their own cinematics;
- parties of up to four that watch together and vote on choices;
- a lore codex, 27 achievements, a scene gallery, side stories, a post-game gauntlet with leaderboards, shrines and ambient world events.

Drop the folder into `plugins/Skript/scripts/` and type `/crown`. It needs Minecraft 1.21.9+; see its [README](showcase/shattered-crown/README.md).

For something smaller, [`examples/`](examples) has 20 focused scripts, from a first camera path to branching stories and group cinematics. [`examples/showcase/starforge.sk`](examples/showcase/starforge.sk) packs most features into one 40-second scene.

## Requirements

| | |
|---|---|
| Server | Paper **1.21.4+**, including 26.x. Spigot and Folia are not supported. |
| Skript | **2.16.0+** (tested on 2.16.2 and 2.17.0) |
| Java | 21 for Paper 1.21.x, 25 for Paper 26.x |
| Optional | PacketEvents: detected, but not required for any feature |

## Installation

1. Download `SkCine-1.0.0.jar` from the Releases page.
2. Put it in `plugins/` next to Skript and restart the server.
3. Copy an example into `plugins/Skript/scripts/`, run `/sk reload <name>` and try it.

👉 Next steps: [Getting started](docs/GETTING_STARTED.md) · [Installation guide](docs/INSTALLATION.md)

## Documentation

| | |
|---|---|
| **Start here** | [Getting started](docs/GETTING_STARTED.md) · [Syntax conventions](docs/SKRIPT_SYNTAX.md) · [Full syntax index](docs/SYNTAX_INDEX.md) |
| **Reference** | [Effects](docs/EFFECTS.md) · [Expressions](docs/EXPRESSIONS.md) · [Conditions](docs/CONDITIONS.md) · [Events](docs/EVENTS.md) · [Types](docs/CUSTOM_TYPES.md) · [Structures and sections](docs/CUSTOM_SECTIONS.md) |
| **Guides** | [Cameras](docs/CAMERAS.md) · [Timelines](docs/TIMELINES.md) · [Actors](docs/ACTORS.md) · [Animations](docs/ANIMATIONS.md) · [Visual effects](docs/VISUAL_EFFECTS.md) · [Dialogue](docs/DIALOGUE.md) · [Multiplayer](docs/MULTIPLAYER.md) |
| **Operations** | [Performance](docs/PERFORMANCE.md) · [Compatibility](docs/COMPATIBILITY.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Testing](docs/TESTING.md) · [Migration](docs/MIGRATION.md) |
| **Plugin developers** | [Kotlin API](docs/DEVELOPER_API.md) |

## Commands

Scenes are written in Skript, so SkCine's only command is for maintenance:

```text
/skcine version | info | capabilities | debug | profiler | health | help
```

Permission: `skcine.admin` (op by default).

## Building from source

```powershell
.\gradlew.bat clean build
```

Linux and macOS use `./gradlew clean build`.

The plugin JAR is written to `build/libs/SkCine-1.0.0.jar`. A build compiles Java 21 bytecode, runs ktlint and the unit tests, and bundles a private, relocated Kotlin runtime. ProGuard then strips the Kotlin classes SkCine never uses (shrinking only, no renaming or optimisation), which keeps the JAR at about 1.2 MB without needing any downloads at runtime. Paper, Skript and Adventure are never bundled.

| Module | |
|---|---|
| `skcine-core` | Pure-Kotlin engine: maths, splines, easing, timeline, state machine |
| `skcine-api` | Public API for other plugins |
| `skcine-platform` | Paper runtime: cameras, actors, effects, dialogue, safety |
| `skcine-skript` | All Skript syntax |
| `skcine-plugin` | Plugin bootstrap, config, commands, listeners |

## Testing

SkCine is tested on real servers with real Minecraft clients, not only with unit tests. The suite covers:

- **152 unit tests** in the engine;
- **parse tests**: every example loads, and bad scripts fail with helpful errors;
- **in-game suites**: test players watch scenes, skip, vote, disconnect and click menus, while the suite checks packets, entities and restored state;
- a **crash test**: the server is killed mid-scene, then players are checked to be restored on restart;
- **benchmarks**: about 2.7 ms of SkCine work per tick with 50 viewers in heavy scenes.

Tested combinations include Paper 1.21.4, 1.21.11 and 26.1.2 with Skript 2.16.2 and 2.17.0. See [COMPATIBILITY.md](docs/COMPATIBILITY.md) and [TESTING.md](docs/TESTING.md).

## Known limitations

- No camera roll, FOV animation or shaders. The vanilla client can't do these, so zoom is a dolly and fades are a per-viewer overlay.
- Player-looking actors need 1.21.9+ and glide instead of walking.
- No volume fades for sounds that are already playing.
- Only the built-in camera backend exists. PacketEvents is detected but not used.

## Contributing

Bug reports and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). When reporting a bug, please include your Paper and Skript versions and the output of `/skcine health`.

## License

[GPL-3.0](LICENSE). SkCine extends Skript's GPL-3.0 classes directly. The bundled Kotlin runtime and kotlinx-serialization are Apache-2.0.

<div align="center"><sub>Made by <b>DHeaven</b> · Built with Skript and SkCine</sub></div>
