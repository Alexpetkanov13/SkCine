# Installation

## Requirements

| Component | Requirement | Notes |
|---|---|---|
| Server software | **Paper** 1.21.4 or newer, including 26.x | Spigot/CraftBukkit are not supported: SkCine uses Paper APIs (spectator locking, pathfinding, Adventure). |
| Java | 21 for Paper 1.21.x · 25 for Paper 26.x | SkCine itself is compiled to Java 21 bytecode and runs on both. |
| Skript | **2.16.0 or newer** | Tested with 2.16.2 and 2.17.0. Skript 2.16 supports Paper 1.21.4–26.2; Skript 2.17 supports Paper 1.21.5–26.3. |
| PacketEvents | Optional | Detected and reported; no SkCine feature requires it. |

Paper 1.21.0–1.21.3 cannot run Skript 2.16, so SkCine does not support them.

## Steps

1. Stop the server.
2. Put `SkCine-1.0.0.jar` in `plugins/`. Skript must also be there.
3. Start the server. The console should show:
   ```
   [SkCine] SkCine 1.0.0 by DHeaven enabled — Minecraft 1.21.11, Skript 2.16.2, Java 21..., camera backend: spectator-display.
   ```
4. Check the environment with `/skcine capabilities` (permission `skcine.admin`, ops by default).

## Your first scene

1. Copy `examples/basic/first-cinematic.sk` to `plugins/Skript/scripts/`.
2. Run `/sk reload first-cinematic`.
3. Type `/welcome` in game.

All example scripts in `examples/` can be copied the same way:

```
plugins/Skript/scripts/first-cinematic.sk
plugins/Skript/scripts/boss-introduction.sk
...
```

Reload a single script with `/sk reload <script-name>`, or a folder with `/sk reload <folder>/`.

## Configuration

`plugins/SkCine/config.yml` holds technical settings only: quality, budgets, camera smoothing, reload policy and safety switches. Scenes are never configured there. Every option is described in the file and in [PERFORMANCE.md](PERFORMANCE.md).

## Files SkCine creates

| Path | Purpose |
|---|---|
| `plugins/SkCine/config.yml` | Technical settings. |
| `plugins/SkCine/recovery/<uuid>.json` | Temporary per-player recovery records while a scene plays. These are deleted when the player is restored. They are not a scene format. |
| `plugins/SkCine/syntax-index.md` | Written on demand by `/skcine info syntax`. |

## Updating

Replace the JAR and restart. Your scripts keep working across patch and minor versions; see [MIGRATION.md](MIGRATION.md).

## Building from source

```powershell
.\gradlew.bat clean build
```

Output: `build/libs/SkCine-1.0.0.jar`.
