# Effects

Shared properties (Skript 2.16+, Paper 1.21.4+, main thread, null-safe, diagnostics with script and line) are described in [SKRIPT_SYNTAX.md](SKRIPT_SYNTAX.md). Patterns are exactly as registered (see [SYNTAX_INDEX.md](SYNTAX_INDEX.md)).

In the patterns below, `%-cinescene/cineplayback%` at the end means "optional target". Inside a scene context it defaults to the current scene.

## Playback

### Play scene
```
(play|start) [the] cine scene %cinescene/string% (for|to) %players% [together:(together|as [a] group)]
```
Starts a definition, given as a value or a registered ID. Without `together`, every player gets an **independent** playback. With `together`, all players share **one** timeline. A player already watching a scene is moved to the new one; their original pre-cinematic state is kept. The scene's `timeline:` code can read the caller's local variables.
**Errors:** unknown ID (the message lists registered IDs), no online viewers, the scene/viewer limits reached, or a failure while building the timeline (the scene ends as *failed* and viewers are untouched).
```skript
play cine scene "rpg:intro" for all players in radius 30 around player together
```

### Control playback
```
(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart) [the] cine (scene|playback)[s] (of|for) %players/cineplaybacks%
(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart) [the] cine playback[s] %cineplaybacks%
(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart) [this|the current] cine (scene|playback)
```
- **skip** runs every not-yet-run `even if skipped` section once, then ends as *skipped*.
- **stop** ends as *cancelled*.
- **restart** begins a fresh iteration from 0.

Calling these on a finished or paused playback is safe and does nothing where it doesn't apply.

### Stop all
```
stop all [running] cine (scenes|playbacks)
```

### Seek / jump
```
(seek|jump) [the] cine (scene|playback)[s] [(of|for) %-players/cineplaybacks%] to %timespan%
(seek|jump) [the] cine (scene|playback)[s] [(of|for) %-players/cineplaybacks%] to [the] [cine] marker %string%
(seek|jump) [the] cine playback[s] %cineplaybacks% to %timespan%
(seek|jump) [the] cine playback[s] %cineplaybacks% to [the] [cine] marker %string%
```
Moves the playhead. Forward seeks drop normal sections and run `even if skipped` ones once. Backward seeks re-arm only `cosmetically` sections, so gameplay actions are never duplicated.
**Errors:** unknown marker (runtime).

### Viewers (late join / leave)
```
add %players% to [the] cine (playback|scene) [%-cineplayback%]
remove %players% from (their|his|her|its) cine (scene|playback)
restore [the] [normal] camera of %players%
```
Adding a viewer to a running shared playback attaches their camera at the current time. Removing a viewer restores them; a playback with no viewers left ends.

### Request skip
```
request [a] cine skip for %players%
```
Behaves exactly like the viewer sneaking: it honours `skippable` and the group skip mode. Use it to build custom skip controls.

### Register / unregister
```
register [the] cine scene %cinescene% [as %-string%]
unregister [the] cine scene %cinescene/string%
```
`register ... as "id"` registers an independent **copy** under the new ID (copy scene). Registered runtime scenes belong to the script that registered them and are removed when it unloads.
**Errors:** the ID is owned by another script.

### Markers
```
add [a] cine marker %string% at %timespan% [(to|in|of) %-cinescene/cineplayback%]
```

### Scene flags
```
make [the] cine (scene|playback) [%-cinescene/cineplayback%] [not:not] skippable
set [the] cine skip mode of [the] cine (scene|playback) [%-cinescene/cineplayback%] to %string%
```

### Play sequence
```
play [the] cine sequence %string% [at %-timespan%]
```
Scene context only. See [CUSTOM_SECTIONS.md](CUSTOM_SECTIONS.md#cine-sequence).

## Camera

### Camera keyframe
```
add [a] cine camera (keyframe|point) at %location% at %timespan% [with easing %-string%] [looking at %-location/entity%] [(to|in) %-cinescene/cineplayback%]
add [a] cine camera (keyframe|point) at %location% (to|in) %cinescene/cineplayback% at %timespan% [with easing %-string%] [looking at %-location/entity%]
```
The location's yaw/pitch is the camera rotation unless `looking at` overrides it. The easing applies to the segment that **ends** at this keyframe. The first camera content fixes the scene's camera world; keyframes in other worlds are rejected because spectator cameras cannot cross dimensions.
**Errors:** location without a world, other world, negative time, more than 2000 keyframes.
**Performance:** evaluation is O(log n) per tick with precomputed arrays.

### Interpolation
```
set [the] cine camera [rotation:rotation] interpolation [of %-cinescene/cineplayback%] to %string%
```
Position: `bezier` (default), `catmull-rom`, `hermite`, `linear`, `step`. Rotation: `euler` (default) or `quaternion`.

### Camera shots
```
make [the] cine camera orbit [around] %location/entity% between %timespan% and %timespan% [with radius %-number%] [[and] height %-number%] [[and] (sweeping|by) %-number% degrees] [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
make [the] cine camera follow %entity% between %timespan% and %timespan% [with offset %-vector%] [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
make [the] cine camera (watch|track|look at) %location/entity% from %location% between %timespan% and %timespan% [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
make [the] cine camera (hold|stay) at %location% between %timespan% and %timespan% [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
```
Shots override keyframes while active, and later shots override earlier ones. `blending over` interpolates smoothly from whatever the camera showed before. After the last shot ends, the camera holds its final pose. Orbit defaults: radius 6, height 2, 360°. Follow offsets are in the subject's local space (default 2 up, 4 behind). See [CAMERAS.md](CAMERAS.md).

### Shake
```
shake [the] cine camera [of %-players/cineplaybacks%] [with intensity %-number%] for %timespan% [at %-timespan%]
```
Smooth layered-sine shake that decays to zero. Intensity runs from 0 to 20 (default 1).

### Zoom (dolly)
```
zoom [the] cine camera [of %-players/cineplaybacks%] [in] by %number% [blocks] over %timespan% [with easing %-string%] [at %-timespan%]
```
Moves the camera forward along its view direction (−64..64 blocks). The server cannot change the client's field of view, so this is a dolly.

### Screen fades and flashes
```
fade [the] cine (screen|view) (1:out|1:to black|2:in|2:from black) [with colo[u]r %-color%] [for %-players/cineplaybacks%] [over %-timespan%]
flash [the] cine (screen|view) [with colo[u]r %-color%] [for %-players/cineplaybacks%] [over %-timespan%]
```
Full-view colour overlay with no resource pack. A text display with an opaque background is kept just in front of each viewer's camera and visible only to them; its alpha is stepped every tick. Defaults: fade 20 ticks black, flash 12 ticks white. **Limitation:** entities closer than 0.3 blocks to the camera can appear in front of it.

### Title
```
show [a] cine title %string% [with subtitle %-string%] [to %-players/cineplaybacks%] [for %-timespan%] [with fade %-timespan%]
```
MiniMessage/legacy text. Defaults to the scene's viewers.

## Actors

Actor creation lives in expressions (`a new cine actor ...`, see [EXPRESSIONS.md](EXPRESSIONS.md#actors)). Cine actors convert to entities automatically, so Skript's own entity syntax works with them too.

```
move [the] cine actor[s] %cineactors/entities% to %location% (over|in) %timespan% [with easing %-string%] [facing:facing [the] movement]
make [the] cine actor[s] %cineactors/entities% walk to %location% [at speed %-number%] [(giving up|timing out) after %-timespan%]
(rotate|turn) [the] cine actor[s] %cineactors/entities% to yaw %number% [and pitch %-number%] over %timespan% [with easing %-string%]
make [the] cine actor[s] %cineactors/entities% (look at|face) %location/entity% [for %-timespan%]
(1:hide|2:show) [the] cine actor[s] %cineactors% [(from|to|for) %-players%]
(remove|despawn) [the] cine actor[s] %cineactors%
attach [the] cine actor[s] %cineactors/entities% to %cineactor/entity% [with offset %-vector%] [rotate:and rotate with it]
detach [the] cine actor[s] %cineactors/entities%
add [a] cine actor keyframe (for|to) %cineactor/entity% at %location% at %timespan% [with easing %-string%] [(in|of) %-cineplayback%]
scale [the] cine actor[s] %cineactors/entities% to %number% over %timespan% [with easing %-string%]
stop animating [the] cine actor[s] %cineactors/entities%
rotate [the] cine actors %cineactors/entities% around %location% by %number% degrees over %timespan% [with easing %-string%]
```
- **walk** uses Paper's mob pathfinder, which plays the real walking animation. Non-mobs, or servers without pathfinding, interpolate instead. The default timeout is 30 seconds.
- **look at** without `for` lasts until replaced.
- **actor keyframes** are driven by the scene clock, so they stay in sync with pause, speed and seek.
- **scale** changes the transform scale on displays and the scale attribute on living entities (0.0625–16).
- **rotate ... around** is a rigid group rotation: positions orbit the pivot and yaw turns by the same angle.

Starting an animation replaces any running animation on the same channel (position, rotation, transform, scale or attachment). See [ACTORS.md](ACTORS.md) and [ANIMATIONS.md](ANIMATIONS.md).

## Display entities
```
animate [the] cine display[s] %cineactors/entities% [to] [translation %-vector%] [[and] scale %-number/vector%] [[and] rotation %-vector%] over %timespan% [with easing %-string%]
spin [the] cine display[s] %cineactors/entities% around [axis] %vector% by %number% degrees over %timespan% [with easing %-string%]
spin [the] cine display[s] %cineactors/entities% around [axis] %vector% (continuously|forever) at %number% degrees per second
```
Local-space transforms. Rotation is Euler degrees (x = pitch, y = yaw, z = roll). Linear animations use a single client-side interpolation; eased ones are stepped every 2 ticks so the curve keeps its shape. Spins of any angle (multiple turns) are supported. Scale runs from 0 to 64.

## Visual effects
```
play [the] cine (preset|vfx) %string% at %location/entity% [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%] [and accent %-color%] [with scale %-number%] [lasting %-timespan%]
draw [a] cine <shape> (of|with) %particle/string% at %location/entity% [with radius %-number%] [[and] height %-number%] [[and] %-number% points] [rotated [by] %-number% degrees] [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]
start [a] cine emitter [drawing] [a] <shape> (of|with) %particle/string% at %location/entity% [with radius %-number%] [[and] height %-number%] [[and] %-number% points] [spinning at %-number% degrees per tick] [expanding [by] %-number% per tick] [pulse:pulsing] [every %-timespan%] lasting %timespan% [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]
draw [a] cine (1:line|1:beam|2:lightning [bolt]) of %particle/string% from %location% to %location% [with spacing %-number%] [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]
stop [the] cine effect[s] %cineeffects%
stop all cine effects [of %-cineplayback%]
```
`<shape>` is one of: point, circle, ring, arc, sphere, hemisphere, cylinder, cone, spiral, helix, double helix, vortex, line, wave, torus, polygon, star, heart, cube.

Audience: explicit players, else the scene's viewers, else nearby players in the world (always distance-culled). Effects attached to an entity follow it and stop when it disappears. See [VISUAL_EFFECTS.md](VISUAL_EFFECTS.md) for presets and budgets.

## Sound
```
play [the] cine sound %string% [at %-location/entity%] [with volume %-number%] [[and|with] pitch %-number%] [in [the] category %-string%] [(to|for) %-players/cineplaybacks%]
stop [the] cine sound[s] [%-string%] [(of|for) %-players/cineplaybacks%]
```
Sounds are namespaced keys: vanilla (`entity.wither.spawn`) or resource-pack (`myserver:cinematics/intro`). Without `at`, the sound plays at each listener. Sounds played by a scene are stopped if the scene is interrupted; a completed scene lets its final sounds ring out. Volume 0–10, pitch 0.5–2, category = Adventure source (`master`, `music`, `ambient`, ...).

## Dialogue
```
show [a] cine (dialogue|line) %string% [(from|by|spoken by) %-string%] [to %-players/cineplaybacks%] [(in|via|as) [the] (1:chat|2:title|3:subtitle|4:action[ ]bar|5:boss[ ]bar|6:text display)] [on %-cineactor/entity%] [typewriter:with [a] typewriter [effect] [at %-number% (characters|chars|letters) per second]] [words:word by word [at %-number% words per second]] [for %-timespan%] [until:until advanced] [fade:fading in [over %-timespan%]] [with typing sound %-string%]
advance [the] cine dialogue (of|for) %players%
(clear|cancel) [the] cine dialogue (of|for) %players%
```
The default channel is subtitle, and the default hold is 3 seconds after the text is fully shown. The typewriter defaults to 30 characters per second; `word by word` reveals whole words (default 4 per second). `fading in` uses the client's title fade-in (title/subtitle channels; default 10 ticks). `on {_textActor}` writes into a text display. See [DIALOGUE.md](DIALOGUE.md).
