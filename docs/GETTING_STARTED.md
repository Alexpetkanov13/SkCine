# Getting started

This tutorial builds a cinematic step by step. Every snippet uses syntax that the integration tests parse and run on a real server (`...` marks omitted lines).

## 1. A scene is a Skript structure

```skript
cine scene "tutorial:hello":
    timeline:
        set {_c} to location of event-player
        add cine camera keyframe at {_c} ~ vector(0, 10, -10) at 0 seconds looking at {_c}
        add cine camera keyframe at {_c} ~ vector(10, 5, 0) at 5 seconds looking at {_c}

command /hello:
    trigger:
        play cine scene "tutorial:hello" for player
```

- `cine scene "<id>":` registers a reusable scene. IDs are lower-case and may have a namespace (`rpg:intro`).
- The `timeline:` block is **ordinary Skript code that runs each time the scene starts**, with the viewer as `event-player`. Because it runs per playback, the camera path can be built from the viewer's position.
- When the last keyframe is reached, the scene ends and the player gets their camera, game mode and location back.

## 2. Things that happen at a time

```skript
        at 1 second:
            show cine title "<gold>Hello!" for 2 seconds
            play cine sound "entity.player.levelup"
        at 3 seconds:
            show cine dialogue "Nice to meet you." from "<aqua>Guide" with typewriter for 2 seconds
```

The indented code of an `at` section runs when playback reaches that time. It sees a *copy* of the local variables from where it was written, such as `{_c}` above. Use `after 2 seconds:` for "two seconds from now" inside other timed sections.

## 3. Settings

```skript
cine scene "tutorial:hello":
    duration: 8 seconds        # otherwise: the latest keyframe/event
    skippable: true            # viewers sneak to skip
    restore location: true     # teleport back afterwards (default)
    timeline:
        ...
    on finish:
        send "Thanks for watching!" to event-player
    on skip:
        send "Skipped." to event-player
```

All entries are listed in [CUSTOM_SECTIONS.md](CUSTOM_SECTIONS.md#cine-scene).

## 4. Actors and effects

```skript
        set {_npc} to a new cine actor of villager at {_c} ~ vector(2, 0, 2) named "guide"
        at 2 seconds:
            make cine actor {_npc} walk to {_c} ~ vector(0, 0, 1)
            play cine preset "magic-reveal" at {_c} with colour gold
```

Actors created inside a scene are visible only to its viewers and are removed automatically when the scene ends.

## 5. Passing data in

The `timeline:` code can read the local variables of the code that played the scene:

```skript
command /reveal <item>:
    trigger:
        set {_reward} to arg-1
        play cine scene "tutorial:reveal" for player

cine scene "tutorial:reveal":
    timeline:
        set {_display} to a new cine item display of {_reward} at (location of event-player) ~ vector(0, 1.5, 2)
        ...
```

## 6. Building scenes at runtime

```skript
command /flyover:
    trigger:
        set {_c} to location of player
        set {_scene} to a new cine scene:
            add cine camera keyframe at {_c} ~ vector(0, 20, 0.1) at 0 seconds looking at {_c}
            make the cine camera orbit around {_c} between 2 seconds and 8 seconds with radius 10 and height 6
        play cine scene {_scene} for player
```

You can also add to a scene variable from outside: `add cine camera keyframe at {_loc} to {_scene} at 3 seconds`, `at 5 ticks in {_scene}:`, `add cine marker "x" at 2 seconds in {_scene}`.

## 7. Controlling playback

```skript
pause cine scene of player
resume cine scene of player
seek cine scene of player to 3 seconds
jump cine scene of player to marker "climax"
set cine speed of (cine playback of player) to 0.5
skip cine scene of player
stop cine scene of player
```

## Next steps

- [CAMERAS.md](CAMERAS.md): shots, interpolation, shake, zoom and fades.
- [TIMELINES.md](TIMELINES.md): event policies, markers, loops and seeking.
- [DIALOGUE.md](DIALOGUE.md): typewriter text and choices.
- [examples/](../examples): 20 complete scripts.
