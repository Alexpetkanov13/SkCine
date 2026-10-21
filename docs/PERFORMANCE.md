# Performance

## Architecture

- **One scheduler.** A single repeating task runs only while there is work and stops after 5 idle seconds, so idle overhead is zero. Each tick runs fixed phases: scene clocks and timeline events → camera rigs → screen overlays → animations → actor validation (once a second) → effects → dialogue → choices.
- **No per-keyframe, per-particle or per-animation tasks.**
- **Immutable, precomputed geometry.** Camera paths are compiled into arrays (O(log n) lookup) and recompiled only when content changes. Shapes are generated once and cached (LRU).
- **Bounded work per tick.** At most 64 timeline events per scene per tick (the rest deferred in order), at most 4 catch-up runs per repeat, and per-player particle budgets.
- **Packet economy.** The camera sends one teleport per tick per playback, and the client interpolates frames in between. Linear display transforms use a single client-side interpolation. Text updates are rate-limited. Particles are distance-culled per player.
- **Quality modes** (`performance.quality`) scale only particle density: LOW 0.35×, MEDIUM 0.65×, HIGH 1×, CINEMATIC 1.25×, AUTO (adapts to MSPT). Gameplay events are never skipped.

## Measured results

All numbers below were measured, not estimated. Hardware: **AMD Ryzen 5 5500 (6 cores/12 threads), 16 GB RAM, Windows 11 Pro**, Paper 1.21.11 build 132 on Java 21.0.12 (server) / JetBrains Runtime 21.0.11 (engine benchmark), Skript 2.16.2. These are benchmark scenarios, not production capacity guarantees.

### Engine benchmark (`./gradlew :skcine-core:benchmark`)

Pure timeline and camera evaluation, independent of the server. Each scene: 30 s, 12 eased keyframes, an orbit shot with blend, a shake, 60 one-shot events, 4 repeaters, 5 markers.

| scenes | avg µs per tick (all scenes) | µs per scene-tick | p99 µs per tick |
|---|---|---|---|
| 1 | 0.5 | 0.49 | 1.8 |
| 10 | 7.6 | 0.76 | 68.6 |
| 50 | 31.4 | 0.63 | 327.2 |
| 100 | 72.6 | 0.73 | 973.8 |
| 500 | 642.1 | 1.28 | 2516.9 |

### Server load test (`integration-tests/bot/skcine-bench.mjs`)

N real connected players (headless clients), each watching **their own** deliberately busy scene (`integration-tests/scripts/skcine-bench.sk`):

- a camera path with an orbit shot
- 3 actors (villager, item display, text display), one spinning
- a 32-point helix emitter every tick
- a 24-point circle every 5 ticks
- a preset, sound and actor move every 2 s
- typewriter dialogue every 3 s

"SkCine µs/tick" is SkCine's own profiler (all SkCine work, including the Skript code its sections run). "MSPT" is Paper's whole-server tick time (5 s/10 s/1 min averages), which includes the players themselves.

| viewers | playing scenes | actors | SkCine avg µs/tick | p95 | p99 | particles sent/s | dropped/s | Paper MSPT avg (5 s / 10 s / 1 min) |
|---|---|---|---|---|---|---|---|---|
| idle | 0 | 0 | 0 (scheduler stopped) | – | – | 0 | 0 | 1.8 / 1.8 / 1.8 |
| 1 | 1 | 3 | 1116 † | 1690 | 2542 | 1 242 | 0 | 2.8 / 3.0 / 9.2 |
| 10 | 10 | 30 | 983 | 2587 | 4178 | 13 106 | 0 | 6.0 / 6.1 / 9.1 |
| 25 | 25 | 75 | 1916 | 3772 | 5603 | 28 364 | 0 | 12.9 / 12.8 / 12.3 |
| 50 | 50 | 150 | 2714 | 5325 | 7585 | 55 012 | 0 | 21.9 / 22.6 / 21.1 |

† The 1-viewer row includes JIT warm-up of a freshly started server (its max sample was 58 ms on the very first scene start).

**Interpretation.** With 50 simultaneous heavy cinematics, SkCine took about 2.7 ms of the 50 ms tick budget, and the server stayed at about 22 ms MSPT including 50 connected players and 150 entities. Per-scene cost falls as the JIT warms up and is dominated by particle packets (55 k/s at 50 viewers). Lower the density with `performance.quality`, or cap it with `max-particles-per-player-per-tick`, if particles dominate on your server.

### Memory and lifecycle

- Repeated script reloads (25× in a row) keep the registry size constant, produce no errors, and re-run the script's load assertions each time (`run_parse_tests.py`).
- Every actor and camera entity is non-persistent and tagged, so none can be saved to disk. The integration tests confirm that the entity count returns to its baseline after scenes end.
- Local-variable snapshots for timed sections are held only by the playback's timeline and are released with it. Nothing is stored in Skript's global locals map.
- Long-run heap profiling (hours of continuous scene churn) has **not** been performed yet. The design avoids global caches that grow with scene count (the shape cache is LRU-bounded at 256 entries).

## Tuning checklist

| Symptom | Setting |
|---|---|
| Many particles, high MSPT | `performance.quality: MEDIUM` or `LOW`; lower `max-particles-per-player-per-tick`. |
| Players far away receive effects | Lower `performance.particle-view-distance`. |
| Camera feels laggy / too floaty | `camera.smoothing-ticks` (1 = tighter, 3 = smoother). |
| Runaway scripts | `limits.*` (actors, effects, animations, events, duration). |

Use `/skcine profiler` for live numbers and `/skcine health` for a quick check.
