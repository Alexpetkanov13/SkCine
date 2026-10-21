# Developer API (Kotlin/Java)

Scenes are authored in Skript. The API is for **integrations**: querying and controlling playback, listening to events, and registering custom VFX presets or custom timeline content. It lives in the `dev.dheaven.skcine.api` package (module `skcine-api`).

## Getting the API

```kotlin
val skcine = SkCineAPI.get()            // throws if SkCine is disabled
val maybe = SkCineAPI.getOrNull()
```
Declare `depend: [SkCine]` or `softdepend: [SkCine]` in your `plugin.yml`. Every call must happen on the server main thread.

## Services

| Property | Type | Purpose |
|---|---|---|
| `version` | String | Plugin version |
| `scenes` | `SceneRegistry` | `get(id)`, `ids()`: registered definitions (`SceneDefinitionHandle`: `id`, `sourceScript`, `registered`) |
| `runtime` | `SceneRuntime` | `play(id, players)`, `current(player)`, `active()` |
| `capabilities` | `CapabilityService` | `has(Capability.X)`, `report()`, server/Skript/Java versions |
| `effects` | `EffectRegistry` | `registerPreset(id, defaultTicks, preset)`, `unregisterPreset`, `presets()` |
| `profiling` | `ProfilerService` | `snapshot()`: counts, tick percentiles, packet rates |

## Scene handles

```kotlin
val handle: SceneHandle = skcine.runtime.current(player) ?: return
handle.state          // SceneState
handle.position       // ticks
handle.speed = 0.5
handle.pause(); handle.resume(); handle.seek(100.0); handle.skip(); handle.stop()
```

### Custom timeline content (custom tracks)

`SceneHandle.timeline` is the live core `Timeline`. Add one-shot events, repeaters or markers to extend a playback with your own track logic. They run with the same policies, ordering and budgets as Skript sections:

```kotlin
handle.timeline.addEvent(60.0, EventPolicy.GUARANTEED, "my-plugin:unlock") { reason ->
    unlockDoor()   // runs once, even if the scene is skipped
}
handle.timeline.addRepeater(0.0, 10.0, null, "my-plugin:pulse") { pulseLights() }
```

## Custom VFX presets

```kotlin
skcine.effects.registerPreset("my-plugin:sparkle", 40) { f ->
    f.draw(Shape.CIRCLE, ShapeParams(radius = 1.5 * f.scale, points = (24 * f.density).toInt().coerceAtLeast(1)),
        Particle.DUST, f.origin, f.age * 8.0, f.dust())
    if (f.age == f.lifetime - 1) f.spawn(Particle.FLASH, f.origin)
    true   // return false to finish early
}
```
Scripts can then use `play cine preset "my-plugin:sparkle" at player with colour gold`. `VfxFrame` gives you the origin (following an attached entity), age, lifetime, progress, colour/accent/scale from the script, a density multiplier, and `spawn`/`draw`, which apply audience filtering, distance culling and particle budgets.

## Events

Bukkit events, fired synchronously: `CineSceneEvent` (`phase`: prepare, start, pause, resume, loop, finish, skip, cancel, fail, end; plus `reason`), `CineMarkerEvent`, `CineCameraEvent`, `CineActorEvent`, `CineDialogueEvent`, `CineChoiceEvent`.

## Core math (no server dependency)

`skcine-core` is pure Kotlin and usable anywhere: `Easing`, `Interpolation`, `Splines`, `Quaternion`, `Angles`, `PosePath`, `CameraTrack`, `Timeline`/`TimelinePlayer`, `SceneStateMachine`, `Shape`/`ShapeCache`.

## Stability

`dev.dheaven.skcine.api.*` and the core classes above follow semantic versioning from 1.0.0. Everything under `dev.dheaven.skcine.platform` and `dev.dheaven.skcine.skript` is internal. The shaded Kotlin runtime is relocated to `dev.dheaven.skcine.libs.kotlin`, so **compile against `skcine-api` with your own Kotlin stdlib**. Java callers are unaffected.
