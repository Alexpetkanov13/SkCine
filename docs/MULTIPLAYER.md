# Multiplayer

## Individual vs. shared playback

```skript
play cine scene "intro" for all players               # one independent playback per player
play cine scene "intro" for all players together      # one shared playback (one timeline) for everyone
```

| | Individual | Shared (`together`) |
|---|---|---|
| Timeline | Separate clock per player | One clock |
| `timeline:` code | Runs per player (`event-player` = that player) | Runs once (`event-player` = first viewer) |
| Camera | Own rig per player | One rig, spectated by every viewer |
| Actors / effects / sounds | Per playback (private to that player) | Shared by the group, created once |
| Skip | Ends only that player's playback | Depends on `skip mode` |
| Disconnect | That playback ends | The player leaves; others continue. The scene ends when nobody is left. |

Definitions are never modified by playing them, so any number of players can watch the same scene with different locations, variables, choices and timing.

## Skip modes (shared scenes)

| `skip mode:` | Sneaking/`request a cine skip` does |
|---|---|
| `individual` (default) | The requesting viewer leaves and is restored; the rest keep watching. |
| `anyone` | Skips the scene for everyone. |
| `vote` | Counts votes. More than half of the current viewers skip it for everyone. Viewers see `Name wants to skip (n/m)`. |

Change it at runtime with `set cine skip mode of cine scene {_s} to "vote"`.

## Late joining

```skript
add player to cine playback (cine playback of {_host})
```
The player joins at the current time. Their state is captured and their camera attaches immediately. The playback's `timeline:` code does **not** run again; content already built is shared.

## Audiences

- Actors created in a shared scene are visible to **its viewers only**, and late joiners can see them too.
- Effects/sounds default to the scene's viewers. Pass `to <players>` for other audiences, for example `to all players in radius 50 around {_c}`.
- `cine viewers` gives the current audience for loops and messages.

## Group events

```skript
on cine scene finish of "raid:intro":
    loop cine viewers of event-cineplayback:
        send "&aThe raid begins!" to loop-player
```

## Tested behaviour

The integration suite runs with real connected clients and verifies that two players' independent playbacks don't affect each other when one skips, that a shared `vote` scene needs a majority to skip, that both viewers are restored afterwards, and that a disconnect ends a single-viewer scene and restores the player on rejoin. See [TESTING.md](TESTING.md).
