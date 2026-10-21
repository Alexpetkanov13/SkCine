# Compatibility

SkCine 1.0.0 is a single JAR (Java 21 bytecode) for every row below. "Tested" means that exact combination was run on a real server with the suites in [TESTING.md](TESTING.md).

## Tested matrix

Every row ran the complete suite through `integration-tests/run-integration.ps1 -Crash` on a fresh world (2026-10-20):

- load: plugin enabled, all scripts and examples parse, 24 load assertions;
- parse: 20 parse/diagnostics/reload checks;
- in-game: 65 checks with connected clients;
- examples: 30 example commands run in game;
- crash: a hard-kill crash-recovery test.

| Paper (build) | Java | Skript | Load | Parse | In-game | Examples | Player actors | Crash | Notes |
|---|---|---|---|---|---|---|---|---|---|
| 1.21.11 (#132) | 21.0.12 | 2.16.2 | ✅ | ✅ 20/20 | ✅ 65/65 | ✅ 30/30 | ✅ 5/5 | ✅ | Primary target; **final JAR** |
| 26.1.2 (#74) | 25.0.4 | 2.17.0 | ✅ | ✅ 20/20 | ✅ 65/65 | ✅ 30/30 | ✅ 5/5 | ✅ | Java 25; **final JAR** |
| 1.21.11 (#132) | 21.0.12 | 2.17.0 | ✅ | ✅ 20/20 | ✅ 65/65 | ✅ 30/30 | – | ✅ | † |
| 1.21.4 (#232) | 21.0.12 | 2.16.2 | ✅ | ✅ 20/20 | ✅ 65/65 | ✅ 30/30 | ✅ clear parse error (needs 1.21.9) | ✅ | Oldest supported (Skript 2.16 floor) † |
| 1.21.11 (#132) | 21.0.12 | 2.17.0 + PacketEvents 2.14.0 | ✅ | – | ✅ 53/53 | – | – | – | Earlier build; PacketEvents detected, no conflicts |
| 26.3 (#207) | 25.0.4 | 2.17.0 | ✅ | – | ⚠️ not run | – | – | – | Earlier build; the test client (mineflayer 4.39) cannot connect to 26.3 |

† Full suite run with the JAR immediately before player actors were added. The final JAR adds only the new player-actor expression and its capability. The player-actor test itself ran on 1.21.11, 26.1.2 and 1.21.4.

## Supported range and why

| Range | Status |
|---|---|
| Paper 1.21.4 – 26.3 | Supported. Covered by Skript 2.16 (1.21.4–26.2) and 2.17 (1.21.5–26.3). |
| Paper 1.21.0 – 1.21.3 | **Not supported.** Skript 2.16+ does not run there, and SkCine uses Skript 2.16 APIs (modern addon modules, `EventValueRegistry`, section expressions). |
| Paper > 26.3 | Expected to work (capability detection, no NMS), but untested. |
| Spigot / CraftBukkit | Not supported (Paper APIs required). |
| Folia | Not supported (single global scheduler and main-thread entity access). |

| Skript | Status |
|---|---|
| 2.16.x | Supported (built against 2.16.2) |
| 2.17.x | Supported (tested 2.17.0) |
| < 2.16 | Not supported. A separate compatibility module was judged not worthwhile: Skript < 2.16 only matters for 1.21.0–1.21.3 servers. |

| Java | Status |
|---|---|
| 21 | Required minimum; Paper 1.21.x |
| 25 | Tested; required by Paper 26.x |

A Java 21 runtime cannot start Paper 26.x; that is a Paper requirement, not a SkCine limitation.

## A version-specific behaviour found by testing

On **1.21.4** the server does not deliver `PlayerToggleSneakEvent` for a spectating viewer, so sneak-to-skip initially failed there. SkCine therefore treats three signals as one debounced skip request: toggle-sneak, Paper's `PlayerInputEvent` (sneak pressed), and the spectator stop-spectating event. Verified on 1.21.4, 1.21.11 and 26.1.2.

### Skript 2.17 command arguments (affects your own commands, not SkCine)

Skript 2.17 turns script commands into Brigadier commands. With two optional arguments such as `command /x [<text>] [<player>]`, a lone word (`/x stopall`) can be parsed as the *player* argument ("No player was found"). This broke a command in SkCine's own test script, not SkCine itself. If your cinematic trigger commands use several optional arguments, test them on 2.17 or give each action its own command.

## Capabilities

`/skcine capabilities` (or `cine capability "<id>" is available` in Skript) reports:

| Capability | Used for | If missing |
|---|---|---|
| `display-entities` | Camera rig, display actors | Display actors and camera control unavailable |
| `display-teleport-interpolation` | Smooth camera/display motion | Camera moves without client smoothing |
| `spectator-camera` | Camera control | Scenes play without camera control (timeline, actors, effects, dialogue still work) |
| `locked-spectating` | Keeping viewers on the camera | Viewers may leave the camera; SkCine re-attaches next tick |
| `per-player-visibility` | Private actors | Actors visible to everyone |
| `mob-pathfinding` | `walk` | Interpolated movement instead |
| `click-callbacks` | Clickable choices | Numbered chat replies only |
| `minimessage` | Text formatting | Legacy codes only |
| `packetevents` | Informational | Not required by any feature |
| `entity-scale` | Scaling living actors | Only displays can be scaled |
| `player-actors` | `a new cine player actor` (Mannequin) | Parse error with guidance; available from Minecraft 1.21.9 |

Every listed capability was available on all tested versions.

## Third-party addons and plugins

SkCine registers only patterns containing the word `cine`, so it does not override Skript core or other addons such as SkBee, skript-reflect, Skript-GUI or DiSky. Integrations are generic:

- **NPC plugins (Citizens etc.) and model engines:** wrap their entities with `cine actor of {_entity}` to animate, attach, make look or walk, or film them. SkCine never removes wrapped entities.
- **PlaceholderAPI / economy / permissions:** use them in your own Skript code (titles and dialogue accept any text). SkCine requires no economy and grants no permissions.
- **WorldGuard:** SkCine teleports and spectator-switches viewers only with plugin causes; regions that block teleports can interfere with `restore location`.
- **Resource packs:** custom sounds work through namespaced keys (`myserver:cinematics/intro`). No pack is required.

## Anti-cheat

Viewers are put in spectator and moved with the camera by the server itself. Anti-cheat plugins that flag spectator movement or server teleports should exempt players for whom `player is watching a cine scene` is true, or the spectator game mode generally.
