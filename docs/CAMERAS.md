# Cameras

## How the camera works

SkCine uses the **spectator + display-entity backend** (`spectator-display`), built only on the Paper API:

1. When a scene with camera content starts, each viewer's state is captured and they are switched to spectator.
2. One invisible item display (the camera *rig*) is spawned per playback, visible only to its viewers, and all viewers spectate it.
3. Every tick the rig is teleported to the evaluated camera pose. A small **teleport duration** (`camera.smoothing-ticks`, default 2) makes the client interpolate position *and rotation* between updates. Motion is smooth at the client's frame rate with one packet per tick instead of one per frame; the trade-off is about 1 tick of latency.
4. Viewers who lose the camera are re-attached automatically, whatever the cause: sneaking without the locking capability, game-mode changes by other plugins, or the rig being removed by `/kill`.
5. When the scene ends, the camera is released, the rig removed, and game mode, location and flight are restored.

The camera is verified in game by the integration tests: the client receives the camera packet for the display entity, the camera moves along the path, and the player is restored to their exact starting position.

### What the server cannot do (approximations)

| Wanted | Reality | SkCine approach |
|---|---|---|
| Camera roll | Not renderable in vanilla | Roll is discarded; quaternion slerp is used for yaw/pitch only. |
| FOV animation | Server cannot set client FOV | `zoom the cine camera` dollies forward. |
| Letterbox, post-processing, blur, depth of field | Not available without client mods/shaders | Not offered. |
| Fade to black/white | No server-side screen tint | A per-viewer text display with an opaque background in front of the camera (`fade cine screen ...`). |
| Cross-dimension paths | Spectator cameras cannot cross worlds | Camera content is locked to the first keyframe's world; other worlds are rejected with an error. |
| 120 FPS server updates | Server ticks at 20 Hz | Client-side interpolation via the display teleport duration. |

## Keyframe paths

```skript
add cine camera keyframe at {_a} at 0 seconds
add cine camera keyframe at {_b} at 3 seconds with easing "ease-in-out-sine"
add cine camera keyframe at {_c} at 8 seconds looking at {_target}
```

- Rotation comes from the location's yaw/pitch, or from `looking at` (resolved when the keyframe is added).
- Yaw is **unwrapped** so paths crossing ±180° never spin the long way.
- `with easing` shapes the segment that *arrives* at that keyframe.
- Before the first keyframe the camera holds the first pose; after the last, it holds the last.

### Interpolation modes

| Mode | Behaviour | Use for |
|---|---|---|
| `bezier` (default) | Time-aware cubic Bézier. Handles are scaled by segment duration, so **velocity is continuous** even with uneven keyframe spacing. | Smooth dolly moves; no speed jumps at keyframes. |
| `catmull-rom` | Centripetal Catmull–Rom through all keyframes (no cusps or self-intersections). | Natural curves through many points. |
| `hermite` | Cubic Hermite with cardinal tangents. | Classic spline behaviour. |
| `linear` | Straight segments. | Mechanical moves. |
| `step` | Jump cuts between keyframes. | Shot cuts. |

Rotation: `euler` (default; interpolates yaw/pitch with the same curve) or `quaternion` (constant angular velocity on the shortest arc).

```skript
set cine camera interpolation to "catmull-rom"
set cine camera rotation interpolation to "quaternion"
```

## Shots

Shots are time windows that override the keyframe path. A later shot overrides an earlier one while both are active. `blending over` cross-fades from the previous view.

```skript
make the cine camera orbit around {_boss} between 2 seconds and 8 seconds with radius 8 and height 3 sweeping 270 degrees blending over 1 second
make the cine camera follow player between 8 seconds and 12 seconds with offset vector(0, 2, -5)
make the cine camera watch {_npc} from {_spot} between 12 seconds and 15 seconds blending over 10 ticks
make the cine camera hold at {_spot} between 15 seconds and 18 seconds
```

- **orbit**: circles a location or a moving entity while always looking at it. The start angle places the camera south of the subject.
- **follow**: keeps a fixed offset from a moving entity (offset in the subject's local space at shot creation) and looks at it.
- **watch / track / look at**: a fixed camera position that pans to follow a moving target.
- **hold**: a fixed pose.

When every shot has ended, the camera holds the pose of the most recently ended one, so it never jumps back to an old keyframe.

## Modifiers

```skript
shake the cine camera with intensity 1.5 for 1 second
zoom the cine camera by 3 blocks over 2 seconds with easing "ease-in-out-sine"
```
Shake is smooth layered noise that fades out, so it never ends with a snap. Zoom moves along the view direction and holds the result.

## Transitions

| Transition | Syntax |
|---|---|
| Cut | Keyframes with `step` interpolation, or `hold` shots without blending. |
| Smooth blend | `blending over <time>` on any shot. |
| Fade to/from black (or any colour) | `fade cine screen out [with colour <c>] over <t>` / `fade cine screen in over <t>` |
| Flash | `flash cine screen [with colour <c>] over <t>` |
| Camera sweep / orbit / focus | orbit, watch, and keyframes with `looking at` |
| Slow motion | `set cine speed of cine playback to 0.3` (slows the whole timeline) |

## Safety

- Viewers in spectator cannot use the spectator hotbar to teleport, and cannot leave the camera; sneaking is a skip request.
- Damage is cancelled (`invulnerable: true`), and interactions/inventories are blocked (`lock input: true`).
- Disconnect, death, world change by another plugin, script reload, plugin disable and server crash all restore the player. Crash recovery uses a small journal file applied on the next join.
