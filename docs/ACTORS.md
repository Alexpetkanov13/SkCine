# Actors

An actor is an entity SkCine controls. Two kinds:

| | Owned actor | Wrapped actor |
|---|---|---|
| Created by | `a new cine actor ...`, `a new cine item/block/text display ...` | `cine actor of {_entity}` |
| Removed by cleanup | **yes**: when its scene ends, on `remove cine actor`, on plugin disable | **never** (only released) |
| Saved to disk | never (non-persistent and tagged) | unchanged |

## Creating actors

```skript
set {_npc} to a new cine actor of villager at {_loc} named "guide"
set {_sword} to a new cine item display of diamond sword at {_loc}
set {_block} to a new cine block display of beacon at {_loc}
set {_label} to a new cine text display with text "<gold>Legendary" at {_loc}
set {_boss} to cine actor of last spawned entity
```

- **Visibility:** inside a scene, new actors are visible **only to that scene's viewers** (Paper per-player visibility). Add `public` to show them to everyone.
- **Mobs** get no AI goals, so they stand still and never attack until you animate them. They are invulnerable and never despawn by distance. Pathfinding (`walk`) still works.
- **Budgets:** `limits.max-actors` server-wide and `limits.max-actors-per-scene` per scene. Exceeding a budget reports an error with the script line and returns nothing.

## Using actors anywhere

Cine actors **convert to entities and locations**, so Skript's own syntax works with them:

```skript
teleport {_npc} to {_somewhere}
set {_where} to location of {_npc}
make cine actor {_npc} look at player
play cine preset "golden-aura" at {_npc}
```

## Movement and rotation

```skript
move cine actor {_npc} to {_door} over 2 seconds with easing "ease-in-out-quad" facing the movement
make cine actor {_npc} walk to {_door} at speed 1.2            # real pathfinding + walk animation
rotate cine actor {_npc} to yaw 90 and pitch 0 over 1 second
make cine actor {_npc} look at player for 3 seconds          # tracks a moving target
add cine actor keyframe for {_bird} at {_p1} at 0 seconds      # clock-driven, seekable path
add cine actor keyframe for {_bird} at {_p2} at 4 seconds with easing "ease-in-out-sine"
```

Display entities are moved with client-side teleport interpolation, so motion is smooth. Other entities are teleported every tick, and the client smooths their movement.

## Parenting and groups

```skript
attach cine actor {_label} to {_sword} with offset vector(0, 1.1, 0)
attach cine actor {_hat} to {_npc} with offset vector(0, 2, 0) and rotate with it
detach cine actor {_label}
rotate cine actors {_parts::*} around {_pivot} by 90 degrees over 2 seconds
```
- Offsets are in the parent's local space (+z forward, +x left, +y up). Attachment ends when the parent disappears.
- Group rotation is rigid: members orbit the pivot (vertical axis, world space) and turn by the same yaw. Skript lists are the "named groups".

## Visibility and scale

```skript
hide cine actor {_ghost}
show cine actor {_ghost} to player
scale cine actor {_npc} to 2 over 1 second          # attribute scale on mobs, transform scale on displays
```

## Player-like actors (Minecraft 1.21.9+)

```skript
set {_twin} to a new cine player actor at {_loc} named "twin" with skin of player with name tag "<gold>Your Twin"
set {_notch} to a new cine player actor at {_loc} with skin of "Notch"
set {_extra} to a new public cine player actor at {_loc}
```

These use the vanilla **Mannequin** entity: a real server entity that renders like a player. All actor features apply unchanged (move, look at, keyframes, attach, hide/show, scale, cleanup).

- `with skin of <player>` copies that online player's own profile. SkCine downloads nothing.
- `with skin of "<name>"` sets a profile by player name, which the game resolves through Mojang's official profile service. Names are validated (1–16 characters: letters, digits, `_`).
- Without a skin, the default skin is used.
- `with name tag "<text>"` shows a name above the actor. The default "NPC" description line is hidden.
- Mannequins are not mobs, so `walk` falls back to smooth interpolated movement.

On servers older than 1.21.9 the syntax fails at **parse time** with a clear message. There, use other humanoid mobs (villagers, illagers, piglins, zombies, armor stands) or wrap an NPC plugin's entity with `cine actor of {_entity}`.

## Events

`on cine actor spawn`, `on cine actor despawn`, `on cine actor animation finish`. See [EVENTS.md](EVENTS.md#actors).
