# Troubleshooting

Start with `/skcine health`, `/skcine capabilities` and the console. SkCine errors look like:

```
[SkCine] [cinematics.sk, line 14] Scene 'rpg:intro': unknown easing 'wobble'; using 'linear'. Valid easings: ...
```
Each message names the script, line, scene and cause. Identical messages are shown at most once per 10 seconds with a repeat count.

## Parse errors (`/sk reload`)

| Message | Cause / fix |
|---|---|
| `'add cine camera keyframe' must be used inside a cine scene timeline/sequence, or given a scene with 'in {_scene}'` | Content syntax outside a scene. Move it into `timeline:` or a timed section, or add `to {_scene}` / `in {_scene}`. |
| `Invalid cine scene id: ...` | Use `a-z 0-9 _ - . /` and at most one `namespace:` prefix (64 characters max). |
| `Duplicate cine scene id 'x': already defined by other.sk, line 3` | Two scripts register the same ID. Rename one, ideally with a namespace (`myscript:x`). |
| `Unexpected entry 'colour: red'` | Unknown `cine scene` entry; see [CUSTOM_SECTIONS.md](CUSTOM_SECTIONS.md#cine-scene). |
| `Unknown skip mode 'x'` | Use `individual`, `anyone` or `vote`. |
| `This needs an indented section with the code to run` | `at`/`after`/`repeat every` need a body. |
| `Can't understand this condition/effect: draw a cine circle of flame at ...` | Skript particle literals are named `flame particle`; or use a string, `"flame"`. |
| `Can't understand ... show cine dialogue ... in "action bar"` | Channels are literal words: `in action bar`, `via chat`, `in boss bar`. |
| `a types.cineeffect cannot be saved` (warning) | Runtime handles can't go in saved global variables. Use `{_local}` or memory-only `{-var}` variables. |
| `set {_p} to cine playback` does nothing useful outside a scene | Outside a scene that line parses as the *type* "cine playback". Use `cine playback of player`. |

## Runtime problems

| Symptom | Check |
|---|---|
| Scene doesn't start | Console: unknown scene ID (the message lists registered IDs), no online viewers, limits (`limits.max-playing-scenes`), or an error inside `timeline:` (the scene then ends as *failed* without touching viewers). |
| Camera doesn't move / player not in spectator | `/skcine capabilities` must show `spectator-camera` and `display-entities` available. The scene needs camera content (keyframes or shots). Camera content must stay in one world. |
| Camera stutters | Increase `camera.smoothing-ticks` to 3. Check server TPS: the camera updates once per server tick. |
| Player kicked for flying / anti-cheat flags | Exempt players watching a scene (`player is watching a cine scene`) or spectators in your anti-cheat. |
| Player left in spectator after a crash | Recovery files restore them on next join. Make sure `safety.recovery-files: true` and that `plugins/SkCine/recovery/` is writable. |
| Sneak doesn't skip | `skippable: true`? `input.sneak-to-skip: true`? In shared scenes check `skip mode` (`individual` removes only you; `vote` needs a majority). |
| Actors visible to other players | Actors created *outside* a scene, or with `public`, are visible to everyone by design. |
| Actor stands still instead of walking | `walk` needs a mob, a reachable path and `mob-pathfinding`. Use `move ... over` for non-mobs. |
| Particles missing | Distance culling (`particle-view-distance`), per-player budget (watch `dropped` in `/skcine profiler`), AUTO quality under load, or a particle that needs block/item data. |
| Typewriter too fast / slow | `with typewriter at 20 characters per second`. Titles update every `dialogue.title-update-interval` ticks. |
| Choice never resolves | The players must be online and in the audience. Typed replies must be the number or the option text. Add `with timeout` and `defaulting to`. |
| Scene stopped when I reloaded | Default `scripts.reload-policy: STOP` ends a script's scenes when it reloads, restoring viewers. Set `KEEP` to let them finish. |
| Global variable holding a playback is "expired" | Playbacks are runtime handles; check `{_p} is running` before using one. |

## Debugging

- `/skcine debug` toggles verbose logging (full stack traces for script errors) until restart.
- `/skcine profiler` shows tick-time percentiles and particle/camera/text packet rates.
- `/skcine info syntax` writes the list of registered patterns to `plugins/SkCine/syntax-index.md`.
- Print state from Skript: `send "%cine state of {_p}% %cine elapsed time of {_p}% %cine progress of {_p}%"`.
