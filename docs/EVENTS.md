# Events

SkCine events fire **synchronously on the server main thread**, exactly once per occurrence. Lifecycle events are deduplicated by the scene state machine: a scene can only end once. They are also normal Bukkit events (`dev.dheaven.skcine.api.event.*`) for other plugins.

## Scene lifecycle

```
on cine scene (prepare|start|pause|resume|loop|finish|skip|cancel|fail|end) [of %-strings%]:
```

| Phase | When | Notes |
|---|---|---|
| `prepare` | Before the timeline code runs | Viewers are not attached yet. |
| `start` | After viewers are attached | |
| `pause` / `resume` | Playback paused/resumed | Waiting for a choice does not fire pause. |
| `loop` | A new iteration begins | |
| `finish` | Completed normally | Viewers already restored. |
| `skip` | Skipped | `even if skipped` sections have run. |
| `cancel` | Stopped or interrupted | Disconnect, script unload, plugin disable, time limit, replaced by another scene. |
| `fail` | Ended by an error | |
| `end` | Always, after the outcome event | |

`of "id", "other:id"` filters by scene ID (case-insensitive). Aliases: `preparation`, `complete`, `stop`, `failure`.

**Event values:** `event-cineplayback`, `event-cinescene`, `event-player` (first viewer), `event-string` (scene ID). `cine end reason of event-cineplayback` gives the reason.

```skript
on cine scene finish of "rpg:intro":
    give event-player 1 golden apple
```

## Markers

```
on cine marker [%-strings%]:
```
Fires when playback **passes** a marker (not when seeking over it). Values: `event-cineplayback`, `event-player`, `event-string` (marker name).

## Camera

```
on cine camera (attach|detach):
```
Values: `event-player`, `event-cineplayback`.

## Actors

```
on cine actor (spawn|despawn|animation (finish|end)):
```
Values: `event-string` (actor name), `event-entity`, `event-cineactor`, `event-cineplayback`.

## Dialogue

```
on cine dialogue (start|(finish|end)):
```
Fires per viewer. Values: `event-player`, `event-string` (plain text), `event-cineplayback`.

## Choices

```
on cine choice [time[ ]out]:
```
Fires for every resolved selection, or only for timeouts with `timeout`. Values: `event-player` (none for votes), `event-string` (selected option, or none if it timed out without a default), `event-cineplayback`.

## Context event values

Inside SkCine sections (timeline, sequences, `at`/`after`/`repeat`, callbacks, choice sections), these are available: `event-cineplayback`, `event-cinescene` and `event-player` (first viewer, or the choosing player in choice sections). Choice sections also provide `event-string`.

## For plugin developers

```kotlin
@EventHandler
fun onScene(e: CineSceneEvent) {
    if (e.phase == ScenePhase.FINISH) logger.info("${e.scene.definition.id} finished")
}
```
Classes: `CineSceneEvent`, `CineMarkerEvent`, `CineCameraEvent`, `CineActorEvent`, `CineDialogueEvent`, `CineChoiceEvent`.
