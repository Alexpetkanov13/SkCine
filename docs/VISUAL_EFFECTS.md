# Visual effects

## Shapes

`draw a cine <shape> of <particle> at <location/entity> [with radius r] [and height h] [and n points] [rotated by d degrees] [to players] [with colour c]`

| Shape | Notes |
|---|---|
| point, circle, ring, arc | Rings are two concentric circles. Arc defaults to 180°. |
| sphere, hemisphere | Fibonacci distribution (even coverage). |
| cylinder, cone | Stacked rings. |
| spiral, helix, double helix, vortex | `height` and turns shape them. Vortex widens as it rises. |
| line, wave | Along the local axis. |
| torus, polygon, star, heart, cube | Polygon/star use 5 sides. Cube draws edges. |

Plus `draw a cine beam of ... from A to B` and `draw a cine lightning bolt of ... from A to B` (jagged, deterministic per call).

Geometry is generated once and **cached** (LRU, 256 entries) by shape and parameters. Points are capped at 4096 per shape, radius at 256 and height at ±256. Invalid values are rejected with an error.

## Emitters (custom composite effects)

```skript
start a cine emitter drawing a ring of "dust" at player with radius 1.2 and 30 points spinning at 6 degrees per tick expanding by 0.02 per tick pulsing every 2 ticks lasting 10 seconds with colour gold
set {-fx} to last cine effect
stop cine effect {-fx}
```
An emitter redraws a shape every `every` ticks (default 1), optionally spinning, expanding and pulsing. When attached to an entity it follows it, and it stops when the entity disappears. Combine several emitters, presets and timed sections to compose any effect.

## Presets

`play cine preset "<id>" at <location/entity> [to players] [with colour c] [and accent c2] [with scale s] [lasting t]`

| Preset | Default length | Description |
|---|---|---|
| magic-reveal | 3 s | Shrinking rune circle, enchant helix, totem burst at the end. |
| legendary-reveal | 4 s | Rising double helix, accent ring, firework sphere, totem + flash finale. |
| secret-reveal | 2.5 s | Collapsing smoke sphere, witch sparks, reverse-portal burst. |
| summoning-circle | 5 s | Rotating circles + star, soul flames, closing vortex. |
| teleport | 1.5 s | Opposing portal rings sweeping up and down, flashes. |
| portal-opening | 4 s | Growing vortex disc with ring and reverse-portal particles. |
| energy-beam | 2 s | Vertical dust beam with spiralling end rods and spark rings. |
| fire-burst | 1 s | Flame and lava burst with an expanding fire ring. |
| shockwave | 1.25 s | Expanding cloud + dust rings, explosion at start. |
| level-up | 2 s | Rising happy-villager helix and ring, totem/firework finish. |
| boss-entrance | 5 s | Wide ritual circle, smoke vortex, souls, explosion finale. |
| victory | 3 s | Orbiting firework spheres in alternating colours. |
| meteor-impact | 2.5 s | Falling flaming meteor, impact explosion, spreading smoke. |
| lightning-burst | 1 s | Repeated lightning bolts with flashes. |
| smoke-cloud | 3 s | Growing smoke sphere. |
| ice-storm | 4 s | Snowflake vortex, snowball debris, icy ring. |
| runes | 5 s | Six orbiting polygon runes with enchant glyphs. |
| golden-aura | 4 s | Rising dust rings and wax sparkles. |
| power-up | 3 s | Collapsing charged sphere, flash and end-rod burst. |
| explosion | 0.75 s | Explosion emitter, flames, expanding smoke. |
| vortex | 4 s | Twin counter-rotating vortices. |
| heart-burst | 1.5 s | Growing heart outlines and heart particles. |

Every preset reads **colour**, **accent** and **scale** from your script, so it fits any theme. Plugins can register more presets through the API ([DEVELOPER_API.md](DEVELOPER_API.md)).

## Particles

Use Skript particle values (`flame particle`, `end rod particle`) or names (`"end_rod"`, `"soul fire flame"`). Particles that need data get it automatically:

| Data type | Supplied |
|---|---|
| Dust | `with colour`, or the effect colour (size 1.2) |
| Dust transition | colour → whitened colour |
| Colour (e.g. entity effect, flash on newer versions) | the colour |
| Float / integer | 0 |
| Block/item data | not supplied (a warning is logged and the particle is skipped) |

## Audience and culling

- Explicit players (`to player`), otherwise the scene's viewers, otherwise every player in the world.
- **Always distance-culled:** players farther than `performance.particle-view-distance` (96) get nothing.
- Particles are sent per player with `force` so long-range cinematic views work.

## Budgets and quality

| Setting | Default | Effect |
|---|---|---|
| `performance.max-particles-per-player-per-tick` | 600 | Extra particles for that player are dropped and counted in the profiler. |
| `performance.quality` | AUTO | LOW 0.35×, MEDIUM 0.65×, HIGH 1×, CINEMATIC 1.25× point density. AUTO lowers density smoothly when MSPT rises above 30/40/45 ms. |
| `limits.max-effects` / `max-effects-per-scene` | 300 / 48 | New effects beyond the limit are refused with an error. |

Quality scaling only affects particle density. Timeline events and gameplay are never skipped to save performance.
