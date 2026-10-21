# Expressions

Shared properties are described in [SKRIPT_SYNTAX.md](SKRIPT_SYNTAX.md). "Context" means the expression may omit its playback inside a scene context.

## Scenes and playbacks

| Syntax | Returns | Notes |
|---|---|---|
| `[the] [registered] cine scene (named\|with id\|called) %string%`<br>`[the] cine scene %string%` | cinescene | Registered definition, or nothing. |
| `[a] new cine scene [(named\|with id) %-string%]` (+ optional section) | cinescene | Runtime definition; see [CUSTOM_SECTIONS.md](CUSTOM_SECTIONS.md#a-new-cine-scene-section-expression). |
| `[the] [current] cine playback [of %-player%]` | cineplayback | The current scene in context, or the playback a player is watching. Outside a scene, `cine playback` alone parses as Skript's *type literal*, so always write `of <player>` there. |
| `[the] last (started\|played) cine playback` | cineplayback | Most recent playback started on the server. |
| `[all [of]] [the] (active\|running\|playing) cine playbacks` | cineplaybacks | |
| `[the] cine viewers [of %-cineplayback%]` | players | Context. |
| `[the] cine (1:duration\|2:(elapsed time\|position)\|3:remaining time) [of %-cineplayback%]` | timespan | Context. `set` elapsed time → seek. `set`/`reset` duration → change or clear the explicit duration. |
| `[the] [cine] marker time of %string% [(in\|of) %-cineplayback%]` | timespan | Context. |
| `[the] cine (1:progress\|2:speed\|3:loop (count\|iteration)\|4:skip votes) [of %-cineplayback%]` | number | Context. Progress 0–1. `set`/`reset` speed (−10..10; negative plays in reverse without firing events). |
| `[the] cine (1:state\|2:[scene] id\|3:end reason) of %cineplayback/cinescene%` | text | States: created, preparing, ready, playing, paused, waiting for input, cleaning up, completed, cancelled, failed. End reasons: completed, skipped, stopped, no viewers, viewer left, replaced by another scene, script unloaded, plugin disabled, time limit reached, error. |
| `[the] cine variable %string% [of %-cineplayback%]` | object | Context. Per-playback storage; `set`/`delete`. Names are case-insensitive. Max 1024 per playback. |
| `[the] cine end location [of %-cineplayback%]` | location | Context. Where viewers go when the scene ends (overrides `restore location`); `set`/`delete`. |

```skript
set {_p} to cine playback of player
send "%cine id of {_p}% is %cine state of {_p}% at %cine elapsed time of {_p}%"
set cine speed of {_p} to 0.5
set cine elapsed time of {_p} to 4 seconds
```

## Easing helpers

| Syntax | Returns |
|---|---|
| `[the] cine ease[d value of] %number% (using\|with) %string%` | number: the eased progress for input 0–1 (clamped). Back and elastic easings overshoot outside 0–1 by design. |
| `[the] cine lerp from %number% to %number% at %number% [(using\|with) %-string%]` | number |

```skript
set {_y} to cine lerp from 64 to 80 at 0.25 using "ease-out-cubic"
```

## Actors

| Syntax | Returns | Notes |
|---|---|---|
| `[a] new [public:(public\|shared)] cine actor (of\|as) %entitydata% at %location% [named %-string%]` | cineactor | Any Skript entity type. Mobs get no AI goals (they stand still until you animate them), cannot be damaged, and are never saved to disk. |
| `[a] new [public:(public\|shared)] cine item display [actor] (of\|with) %itemtype% at %location% [named %-string%]` | cineactor | |
| `[a] new [public:(public\|shared)] cine block display [actor] (of\|with) %blockdata/itemtype% at %location% [named %-string%]` | cineactor | |
| `[a] new [public:(public\|shared)] cine text display [actor] (with text\|of) %string% at %location% [named %-string%]` | cineactor | Billboard centre. |
| `[a] new [public:(public\|shared)] cine player actor at %location% [named %-string%] [with skin of %-player/string%] [with (name tag\|nametag) %-string%]` | cineactor | Player-looking actor (Mannequin, Minecraft 1.21.9+); see [ACTORS.md](ACTORS.md#player-like-actors-minecraft-1219). Parse error on older servers. |
| `[the] cine actor (of\|for) %entity%` | cineactor | Wraps an existing entity. It is **never** removed by cleanup. |
| `[the] cine actor (named\|called) %string% [(of\|in) %-cineplayback%]` | cineactor | Looks the actor up in the scene first, then among global actors. |
| `[all [of]] [the] cine actors [of %-cineplayback%]` | cineactors | All actors of the playback, or every actor on the server. |
| `[the] [cine] actor entity of %cineactor%`<br>`[the] entity of [the] cine actor %cineactor%` | entity | |
| `[the] [cine] actor name of %cineactor%` | text | |

Inside a scene, new actors are **private** to its viewers and owned by the scene, so they are removed when it ends. Outside a scene (or with `public`), they are visible to everyone and live until `remove cine actor`, plugin disable, or server stop. **Errors:** the actor budget is reached, or the location has no world (both reported with line).

## Cameras, effects and diagnostics

| Syntax | Returns |
|---|---|
| `[the] cine camera location of %player%` | location: the current camera pose while a scene controls the player's camera. |
| `[the] last [started] cine effect` | cineeffect |
| `[all [of]] [the] cine (1:easings\|2:presets\|3:shapes\|4:interpolations\|5:capabilities\|6:scene ids\|7:sequences\|8:camera backend\|9:version)` | texts |
| `[the] skcine (9:version)` | text |
| `[the] (number\|amount\|count) of (active\|running) cine (1:(playbacks\|scenes)\|2:actors\|3:effects\|4:cameras\|5:animations)` | number |

## Choice sections

| Syntax | Returns |
|---|---|
| `[the] (selected\|chosen) cine (choice\|option)`<br>`[the] cine choice` | text: the chosen option's plain text (only inside `present cine choice`). |
