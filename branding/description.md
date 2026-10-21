# SkCine
### Bring Your Skripts to Life

SkCine is a Skript addon for **cinematic cutscenes**: smooth cameras, timelines, actors, display animations, particles, sound, dialogue and interactive choices.

You write **everything in ordinary `.sk` files**. There's no in-game editor, no YAML or JSON scene files and no scene-building commands. Your scenes reload with `/sk reload` like any other script.

---

## Quick example

```
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

---

## Features

### 🎥 Smooth cameras
- Keyframe paths: Bézier, Catmull–Rom, Hermite, linear and step
- 33 easings and smooth (quaternion) rotation
- Orbit, follow, track and hold shots, with blending between them
- Camera shake and dolly zoom
- Full-screen colour fades and flashes, no resource pack needed

### ⏱️ Timelines
- Timed sections: `at 3 seconds:`, `after`, `at marker`, `repeat every`
- Pause, resume, seek, change speed, play in reverse and loop
- Important events (like rewards) fire exactly once, even when players skip or seek
- Reusable sequences that you can nest and give parameters

### 🧍 Actors
- Spawn any mob, player-looking actors with real skins (1.21.9+), or item/block/text displays
- Actors are visible only to the scene's viewers
- Move, walk with real pathfinding, rotate, look at things, follow keyframed paths
- Attach actors to each other, rotate groups around a point, and scale them
- Removed automatically when the scene ends

### ✨ Visual effects
- 19 particle shapes, beams, lightning and emitters (spinning, pulsing, expanding)
- 22 ready-made presets with themeable colours
- Per-player particle budgets, distance culling and quality that adapts to server load

### 💬 Dialogue and choices
- Typewriter or word-by-word text in chat, title, subtitle, action bar, boss bar or floating text
- MiniMessage and `&` colour codes, typing sounds, manual advance
- Choices through clickable chat, typed replies or an inventory menu
- Per-player answers or votes, with timeouts, defaults and scene pausing

### 👥 Multiplayer
- Individual or shared playback
- Players can join a scene that's already playing
- Skip modes: individual, anyone, or vote

### 🛡️ Player safety
- Game mode, location and flight are always restored when a scene ends
- Also restored after server crashes, disconnects, deaths and script reloads

### ⚡ Performance
- One central scheduler that uses nothing when no scenes are playing
- Resource limits for actors, effects and scenes
- Built-in profiler; error messages include the script name and line

---

## Requirements

| | |
|---|---|
| **Server** | Paper **1.21.4 or newer**, including 26.x (1.21.0–1.21.3 are not supported) |
| **Skript** | **2.16.0 or newer** (tested on 2.16.2 and 2.17.0) |
| **Java** | 21 for Paper 1.21.x, 25 for Paper 26.x |
| **Player-looking actors** | Minecraft 1.21.9 or newer |
| **Not supported** | Spigot, Folia |

## Installation

1. Put **SkCine-1.0.0.jar** in your `plugins` folder next to Skript.
2. Restart the server.
3. Write a scene in a `.sk` file and load it with `/sk reload <script>`.

## Commands

Maintenance only. Permission: `skcine.admin` (op by default).

| Command | What it does |
|---|---|
| `/skcine version` | Shows the SkCine version |
| `/skcine info` | Shows server, Skript and Java versions, scene counts and quality mode |
| `/skcine info syntax` | Writes every registered SkCine pattern to a file |
| `/skcine capabilities` | Lists which features your server supports |
| `/skcine debug` | Turns debug logging on or off until restart |
| `/skcine profiler` | Shows tick time, active scenes/actors/effects and particle counts |
| `/skcine health` | Quick check for errors, cleanup failures and slow ticks |
| `/skcine help` | Lists the commands |

## Known limitations

- No camera roll, FOV changes or shaders (the vanilla client can't do these)
- Player-looking actors glide instead of playing a walking animation
- No volume fades for sounds that are already playing (vanilla limit)

## Support

Found a bug or have a suggestion? Post in the discussion tab and include your Paper version, Skript version, and the output of `/skcine health`.

**License:** GPL-3.0
