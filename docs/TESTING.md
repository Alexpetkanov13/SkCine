# Testing

SkCine is tested at three levels. Every number below comes from an actual run on 2026-10-20 (hardware in [PERFORMANCE.md](PERFORMANCE.md)).

## 1. Unit tests (JUnit 5): 152 tests, all passing

`.\gradlew.bat build` runs them, together with ktlint.

| Area | Covered |
|---|---|
| Easing | All 33 easings hit exact endpoints; clamping and NaN input; non-overshooting easings stay in range; back/elastic overshoot; aliases and normalization. |
| Angles / quaternions | Yaw wrapping, shortest arc, unwrapping, Minecraft direction conventions, yaw/pitch ↔ quaternion round trips, slerp endpoints and shortest path. |
| Splines and paths | Hermite/Bézier/centripetal Catmull–Rom; every interpolation mode passes through every keyframe; no teleport jumps; velocity continuity of the time-aware Bézier with uneven spacing; ±180° yaw crossing never spins the long way (Euler and quaternion); easing per segment; clamping; duplicate keyframe times; invalid keyframes rejected. |
| Camera track | Shot priority and holding; continuous blend-in; orbit radius/height/look-at; follow tracking and vanished targets; shake decay; dolly zoom. |
| Timeline | Deterministic order and ties; t = 0 events; derived durations; markers; skip semantics (once/guaranteed/cosmetic); backward seek never duplicates gameplay; forward seek; loops; bounded catch-up with nothing lost; repeaters without backlog; speed; reverse playback; live insertion; limits; bounded infinite loops. |
| Lifecycle | Normal state flow; repeated stop calls; cancel before start; invalid transitions; every end reason reaches a terminal state. |
| Misc | Scene ID validation/normalization; all 19 shapes finite and bounded; shape cache LRU; lines and lightning. |
| Platform (no server needed) | Word-by-word reveal boundaries; MiniMessage/legacy/mixed parsing; typewriter slicing keeps styling (incl. gradients); registry duplicate/reload/owner rules; definition copies; diagnostic rate limiting; version and setting parsers. |

## 2. Real-server Skript tests

The harness lives in `integration-tests/`. It runs a real Paper server with Skript and SkCine and drives it over RCON.

| Suite | What it does | Result (Paper 1.21.11, Skript 2.16.2) |
|---|---|---|
| `scripts/skcine-syntax-test.sk` | Uses **every** registered pattern in a valid context (the 1.21.9+ player-actor pattern lives in `scripts/optional/`); 24 load-time assertions (lookups, easing math, validation conditions, runtime register/copy/unregister, info lists). | Loads with *no errors*; 24/24 |
| `run_parse_tests.py` | 12 negative scripts (each must fail with a specific, helpful message), duplicate IDs across scripts (rejected, then freed when the owner unloads), runtime diagnostics name `script.sk, line N` and are rate-limited, 25 consecutive reloads (stable registry, no errors). | 20/20 |
| All 20 example scripts | Loaded as a folder. | No errors or warnings |

## 3. In-game tests with real clients

Headless Minecraft clients ([mineflayer](https://github.com/PrismarineJS/mineflayer)) join the offline-mode test server as players. Assertions combine the server log with what the **client actually receives**.

### `bot/skcine-bot-test.mjs`: 65 checks, all passing

- **Camera:** the client switches to spectator, receives a camera packet for the item-display rig, the rig moves (9.5 blocks in 1.5 s) along the path, and afterwards the player is back in survival at the **exact** starting position.
- **Callbacks and events:** prepare/start/finish/skip/cancel/end, markers, dialogue start/finish, actor spawn/despawn, camera attach/detach, structure callbacks.
- **Skip:** sneak skips a skippable scene; a non-skippable scene ignores it.
- **Full syntax scene:** the timeline is built; sequences are inlined; 4 actors spawn and are removed afterwards (entity count back to baseline); pause freezes the clock; resume; no SkCine errors during playback.
- **Runtime-built scenes,** seek, and stop.
- **Choices:** prompt and clickable options arrive in the client's chat; the scene waits for input; a typed reply resolves the section with the right option; the scene resumes and ends with the stored variable.
- **Multiplayer:** two independent playbacks where one viewer's skip does not end the other's; a shared `vote` scene where one of two votes does not skip and a majority does; both viewers restored.
- **Reload during playback:** the scene is cancelled, the viewer restored, and the reloaded scene plays again.
- **Late join:** the late joiner enters the running shared playback and sees its private actors.
- **Menu choices:** the inventory menu opens for a viewer who is spectating the camera, has one item per option, a click resolves the choice (validated on the server), the menu closes, and the scene resumes.
- **Disconnect:** the scene ends; the player rejoins in survival at the original location.

### `bot/skcine-player-actor-test.mjs`: player actors, 5 checks

On 1.21.9+ servers: the syntax loads, the client receives three `mannequin` entities (skin copied from a player, skin by name, default skin), they are registered as cine actors, there are no errors, and they are removed when the scene ends. On older servers it checks for the clear parse-time error instead.

### `bot/skcine-crash-test.mjs`: crash recovery, passing

Start a scene → `save-all` (the saved player data is now *spectator at the camera position*) → hard-kill the server process → restart → rejoin. Result: "Restoring CrashBot after an interrupted scene", survival, distance 0.00 from the original position, and the journal file deleted.

### `bot/skcine-examples-test.mjs`: every example, 30/30

Runs all 30 example commands in game (answering choices where needed) and requires each scene to end with the player restored and no SkCine/Skript errors or exceptions in the log.

## Version matrix

See [COMPATIBILITY.md](COMPATIBILITY.md).

## Issues found and fixed by these tests

- Sneak-to-skip didn't work on 1.21.4 → added input-event and stop-spectating fallbacks.
- A pausing choice at the very end of a timeline let the scene finish before the answer → the choice now pauses immediately.
- Late joiners couldn't see private actors → now revealed on join.
- Dialogue channels required quoted strings → now literal words (`in action bar`).
- `play cine sound ... with pitch` without a volume didn't parse → pattern fixed.
- `jump cine playback {_p} to marker ...` didn't parse → pattern added.

Harness problems found and fixed while making the matrix reliable (none were SkCine bugs):
- The welcome-tour example plays on first join and replaced the scene under test → the suites stop it first.
- Paper writes its log asynchronously, so a "latest status line" could be stale → every status request now carries a unique token.
- Fixed waits were too short for slow log flushes → the checks poll instead.
- The crash test hard-coded the 1.21.11 client protocol → it uses the configured version.
- The post-crash restart overwrote the first server log → it now logs to `server-after-crash.log`.
- Skript 2.17 parsed `skcinetest-control stopall` as a player argument → the test script uses a dedicated command.

## Not covered by automated tests

- **Visual quality** (how smooth the camera looks at high client FPS, how presets look) needs a human with a real client. The tests prove packets, entities, movement and state, not aesthetics.
- **Clicking** a chat option. Click callbacks are server-side Adventure features; the test uses the typed-number path, which shares the same selection code.
- Paper 26.3 in game (client protocol not available to the test client); multi-hour soak tests.

## Running the tests yourself

```powershell
# unit tests + build
.\gradlew.bat clean build
# real server tests (downloads Paper + Skript into integration-tests/work, needs Node.js and Python 3)
powershell -ExecutionPolicy Bypass -File integration-tests\run-integration.ps1
```
