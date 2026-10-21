# Animations and sequences

## The animator

All actor and display animations run in **one central animator**, ticked by the SkCine scheduler. There are no per-animation tasks. Each animation:

- belongs to a **channel** on its entity: position, rotation, transform, scale or attachment. Starting a new animation on the same channel replaces the old one, so animations never fight each other.
- holds entity **UUIDs**, not references, and ends gracefully if the entity disappears.
- is **owned** by the scene that started it and is cancelled when the scene ends.
- fires `on cine actor animation finish` when it completes on its own.

The server-wide limit is `limits.max-animations` (2000).

## Display transforms

```skript
animate cine display {_gem} to translation vector(0, 0.5, 0) and scale 1.5 and rotation vector(0, 180, 0) over 2 seconds with easing "ease-out-back"
spin cine display {_sword} around vector(0, 1, 0) by 720 degrees over 4 seconds
spin cine display {_coin} around vector(0, 1, 0) continuously at 120 degrees per second
```

- Transforms are in the display's **local space** (translation, left rotation, scale). Positions are moved with actor movement in world space. The two are never mixed.
- **Linear** transforms use one client-side interpolation over the whole duration, which costs a single packet.
- **Eased** transforms are sent as 2-tick interpolated steps that follow the easing curve. Back and elastic overshoot works.
- Spins of any size (multiple turns) are composed from the start orientation.
- Display **teleport** interpolation is limited to 0–59 ticks by Minecraft. SkCine never sends longer values; long moves are stepped.

## Composition

Build composites with ordinary Skript: create several actors, parent them with `attach`, rotate groups around a pivot, and drive everything from timed sections. See `examples/animation/display-animation.sk`.

## Sequences: reusable building blocks

```skript
cine sequence "fx:pillar":
    at 0 seconds:
        start a cine emitter drawing a helix of "dust" at {_at} with radius 0.8 and height 3 lasting 2 seconds with colour {_colour}
    at 2 seconds:
        play cine preset "shockwave" at {_at} with colour {_colour}

cine sequence "fx:triple":
    set {_base} to {_at}
    set {_at} to {_base} ~ vector(-3, 0, 0)
    play cine sequence "fx:pillar"
    set {_at} to {_base} ~ vector(3, 0, 0)
    play cine sequence "fx:pillar" at 1 second
```

| Concept | How |
|---|---|
| Sequential | Different `at` times, or `play cine sequence ... at <offset>`. |
| Parallel | Same time. |
| Delay | `at <offset>`. |
| Loop | `repeat every`, or `loops:` on the scene. |
| Chaining / nesting | Sequences calling sequences (depth limit `limits.max-nested-sequences`). |
| Parameters | Caller local variables (`{_at}`, `{_colour}` above). |
| Cancellation | Owned by the playback, so stopping the scene cancels everything. |

A reward-reveal composite (camera focus, levitation, rotation, particle swirl, sound build-up, flash, title, camera exit) is in `examples/gameplay/reward-reveal.sk`. Each step is a separate statement you can move into its own sequence.
