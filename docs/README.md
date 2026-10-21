# SkCine documentation

**Start here:** [INSTALLATION.md](INSTALLATION.md) → [GETTING_STARTED.md](GETTING_STARTED.md) → the [examples](../examples).

## Reference
- [SKRIPT_SYNTAX.md](SKRIPT_SYNTAX.md): conventions, scene context, locals, names
- [SYNTAX_INDEX.md](SYNTAX_INDEX.md): every registered pattern (generated from Skript's live registry)
- [CUSTOM_SECTIONS.md](CUSTOM_SECTIONS.md): `cine scene`, `cine sequence`, `at`/`after`/`repeat every`, choices, `a new cine scene:`
- [EFFECTS.md](EFFECTS.md) · [EXPRESSIONS.md](EXPRESSIONS.md) · [CONDITIONS.md](CONDITIONS.md) · [EVENTS.md](EVENTS.md) · [CUSTOM_TYPES.md](CUSTOM_TYPES.md)

## Guides
- [CAMERAS.md](CAMERAS.md): backend, paths, interpolation, shots, shake, zoom, fades, limitations
- [TIMELINES.md](TIMELINES.md): clock, event policies, markers, seeking, loops
- [ACTORS.md](ACTORS.md): creating, moving and parenting actors; visibility
- [ANIMATIONS.md](ANIMATIONS.md): display transforms, the animator, reusable sequences
- [VISUAL_EFFECTS.md](VISUAL_EFFECTS.md): shapes, emitters, presets, budgets
- [DIALOGUE.md](DIALOGUE.md): typewriter dialogue, choices, branching
- [MULTIPLAYER.md](MULTIPLAYER.md): individual vs. shared playback, skip modes, late joining

## Operations
- [PERFORMANCE.md](PERFORMANCE.md): architecture and measured benchmarks
- [COMPATIBILITY.md](COMPATIBILITY.md): tested version matrix and capabilities
- [TESTING.md](TESTING.md): test suites and results
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- [MIGRATION.md](MIGRATION.md)
- [DEVELOPER_API.md](DEVELOPER_API.md): Kotlin/Java integration API

## Example index

| File | Shows | Command |
|---|---|---|
| basic/first-cinematic.sk | Named scene, keyframes, title, sound, dialogue, callbacks | `/welcome` |
| basic/camera-path.sk | Runtime scene: high start, orbit, move in, return (Example 2) | `/flyover` |
| basic/camera-orbit.sk | Orbit around a moving player | `/orbitme` |
| basic/camera-shake.sk | Shake, flash, dolly zoom | `/quake` |
| animation/display-animation.sk | Item/block/text displays, group rotation, keyframes, scaling, emitters, parenting (Example 7) | `/displays` |
| animation/particle-effects.sk | Presets, shapes, persistent emitters | `/vfx <preset>`, `/vfxshapes`, `/aura` |
| animation/actor-animation.sk | Pathfinding walk, look-at, rotation, keyframed flight, hiding | `/actors` |
| gameplay/boss-introduction.sk | Any entity as boss, no duplication, vote skip, gameplay callback (Example 3) | `/testboss [type]` |
| gameplay/cinematic-teleport.sk | Fade-out warp with an end location | `/warpto x y z` |
| gameplay/reward-reveal.sk | Parameterized item reveal; reward stays in your code (Example 4) | `/testreveal` |
| gameplay/dialogue-choices.sk | NPC dialogue with a pausing choice | `/quest` |
| gameplay/player-welcome.sk | First-join tour | `/tour` |
| gameplay/victory-cinematic.sk | Winner celebration for nearby players | `/testvictory` |
| multiplayer/group-cinematic.sk | Shared timeline, vote skip, group completion (Example 6) | `/groupscene` |
| multiplayer/synchronized-scene.sk | Individual vs. shared, late joining | `/solo`, `/together`, `/joinscene` |
| advanced/branching-story.sk | NPC approach, Accept/Decline branches on one timeline (Example 5) | `/story` |
| advanced/cinematic-timeline.sk | Markers, speed, seeking, loops, event policies | `/tl ...` |
| advanced/reusable-sequences.sk | Parameterized, nested sequences | `/reveal` |
| generic/progression-moments.sk | Level-up, rank unlock, rebirth, egg hatching/pet reveal | `/testprogress ...` |
| generic/story-moments.sk | Quest intro, dungeon intro, horror, two-character cutscene | `/teststory ...` |

Install any example by copying it to `plugins/Skript/scripts/` and running `/sk reload <name>`.

The examples are deliberately server-agnostic: no world names, coordinates, plugins, economies or permissions are assumed. `generic/progression-moments.sk` (egg hatching, pet reveals, rebirths, rank unlocks) suits simulator-style servers such as ChickFarm. Call its functions from your own progression code; nothing in SkCine is specific to any server.
