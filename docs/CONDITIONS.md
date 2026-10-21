# Conditions

All conditions are null-safe: unset or expired handles evaluate to false (true when negated) and never throw. Shared properties are described in [SKRIPT_SYNTAX.md](SKRIPT_SYNTAX.md).

| Syntax | True when |
|---|---|
| `%players% (is\|are) watching [a\|the] cine scene [%-cinescene/string%]`<br>`%players% (isn't\|is not\|aren't\|are not) watching [a\|the] cine scene [%-cinescene/string%]` | The player watches any scene, or the given definition/ID. |
| `%cineplaybacks% (is\|are) (1:playing\|2:paused\|3:(finished\|completed)\|4:(cancelled\|stopped)\|5:(running\|active)\|6:waiting for input\|7:skippable)` (and the negated form) | `running` = playing, paused or waiting for input. `cancelled` includes failed. `skippable` requires the playback to be active. |
| `cine (scene\|sequence:sequence) %string% (exists\|is registered)`<br>`cine (scene\|sequence:sequence) %string% (doesn't\|does not) exist` | |
| `%cineactors/entities% (is\|are) (1:(valid\|alive\|spawned)\|2:visible [to %-player%])` (and the negated form) | The actor's entity (or a plain entity) still exists and is not dead / is visible (to the player, or by default). Plain entities are accepted so this never shadows Skript's own `is alive` for a variable that holds an ordinary mob. |
| `cine capability %string% is (available\|supported)` (and the negated form) | See `/skcine capabilities` or [COMPATIBILITY.md](COMPATIBILITY.md#capabilities). |
| `cine (1:easing\|2:preset\|3:shape\|4:interpolation) %string% is (valid\|known\|supported)` (and the negated form) | |
| `%players% (has\|have) [an] active cine camera` (and the negated form) | A scene currently controls the player's camera. |
| `%players% can skip [the] [current] cine scene` (and the negated form) | Watching a skippable, active scene. |
| `%cineplaybacks% (has\|have\|contains) [a] [cine] marker %string%` (and the negated form) | |
| `%cineeffects% (is\|are) (active\|running)` (and the negated form) | |
| `[the] cine choice timed out` / `[the] cine choice (didn't\|did not) time out` | Inside `present cine choice` only. |

```skript
if player is watching a cine scene:
    send "Please wait until the cutscene ends."
if cine capability "packetevents" is available:
    send "PacketEvents detected."
```
