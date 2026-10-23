# Changelog

All notable changes to SkCine are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/) and the project uses [semantic versioning](https://semver.org/).

## [1.0.1] — 2026-10-23

### Added
- Optional anonymous bStats metrics for server, player, Minecraft version, and plugin-version statistics.
- `metrics.enabled` and `metrics.plugin-id` configuration with graceful handling of missing IDs and initialization failures.

### Build
- Bundle and relocate bStats privately with Gradle Shadow.
- Preserve bStats classes through the ProGuard shrinking step; distribute the optimized `build/libs/SkCine-1.0.1.jar`, not the larger `-full.jar` intermediate.
- Update project version to `1.0.1`.

**For maintainers:** Set the registered SkCine bStats plugin ID in the distributed default configuration before release. An ID of `0` disables metrics reporting.

## [1.0.0] — 2026-10-20

First release.

### Authoring
- `cine scene "id":` structure with entries (duration, skippable, lock input, freeze, invulnerable, restore location, speed, loops, skip mode), a `timeline:` section and `on start/finish/skip/cancel/end` callbacks.
- `cine sequence "name":` reusable, nestable, parameterized timeline blocks; `play cine sequence`.
- Runtime scenes: `a new cine scene [named "id"]` with an optional body; content effects accept definitions or live playbacks.
- Timed sections: `at`, `after`, `at marker`, `repeat every`; policies `even if skipped` and `cosmetically`.
- Caller-local passing to `timeline:` bodies; per-section local snapshots; scene variables.

### Engine
- Logical tick clock with speed (−10..10), pause, seek, jump to marker, restart, loops; bounded catch-up; deterministic event policies.
- Validated scene state machine with end reasons; single central scheduler that idles to zero.
- Camera: time-aware Bézier, centripetal Catmull–Rom, Hermite, linear, step; Euler or quaternion rotation; 33 easings; orbit/follow/track/hold shots with blending; shake; dolly zoom.
- Spectator + display-entity camera backend with client-side interpolation, re-attach and locking.
- Full-view colour fades and flashes without a resource pack.
- Actors: any entity type, player-looking actors with skins (vanilla Mannequin, 1.21.9+), and item/block/text displays, private to viewers; move, pathfinding walk, rotate, look-at, scene-clock keyframes, parenting, group rotation, scale, visibility; automatic cleanup.
- Display transforms (translation/scale/rotation) with eased stepping; multi-turn spins.
- VFX: 19 cached shapes, beams, lightning, emitters, 22 themeable presets, per-player budgets, distance culling, quality modes (AUTO adapts to MSPT).
- Sound by namespaced key, positional or per-listener, stopped with interrupted scenes.
- Dialogue on chat/title/subtitle/action bar/boss bar/text display, with letter (typewriter) or word-by-word reveal, title fade-in, typing sounds and manual advance; MiniMessage and legacy text.
- Choices: clickable chat, typed replies, or an inventory menu (works while spectating); per-player or vote; timeouts, defaults, pausing.
- Multiplayer: individual or shared playback, late joining, skip modes individual/anyone/vote.
- Player safety: ownership-aware restoration; crash-recovery journal; handling of disconnects, deaths, world changes, reloads and plugin disable.
- Diagnostics with script and line, rate limiting; profiler; capability detection.

### Fixed during showcase testing
- `{_x} is alive` / `is valid` on a variable holding an ordinary (non-actor) entity no longer returns false: SkCine's actor condition now accepts plain entities, so it can't shadow Skript's own condition.

### Tooling
- Release JAR shrunk from 3.7 MB to about 1.2 MB: ProGuard removes unused classes of the bundled Kotlin runtime (shrink only; SkCine's own classes are kept unchanged, nothing is downloaded at runtime).
- Kotlin API (`skcine-api`) with events, custom presets and timeline extension.
- `/skcine version|info|capabilities|debug|profiler|health|help`, plus `/skcine info syntax` to dump registered patterns.
- Showcase campaign `showcase/shattered-crown/` (≈10,000 lines of Skript) with an in-game playthrough test.
- 152 unit tests, real-server Skript tests, in-game client tests, crash test, example smoke test, engine and load benchmarks.
