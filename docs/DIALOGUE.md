# Dialogue and choices

## Showing lines

```skript
show cine dialogue "Welcome, traveller." from "<gold>Guide" with typewriter for 3 seconds
show cine dialogue "Halt!" from "<gray>Guard" in action bar with typewriter at 50 characters per second
show cine dialogue "Press on." in boss bar for 4 seconds
show cine dialogue "Read the sign." via chat
show cine dialogue "<rainbow>Ancient words" on {_textActor} with typewriter
show cine dialogue "Take your time." until advanced
show cine dialogue "Every word lands." in title word by word at 5 words per second fading in over 10 ticks
```

| Channel | Output | Typewriter | Notes |
|---|---|---|---|
| subtitle (default) | Subtitle: `Speaker: text` | yes | Title area stays empty. |
| title | Title: text, subtitle: speaker | yes | |
| action bar | `Speaker: text` | yes | Refreshed while holding (the action bar fades by itself otherwise). |
| boss bar | Boss bar name | yes | The bar empties as the hold time runs out. |
| chat | One chat message | no | Chat lines cannot be edited after they are sent, so the whole line is sent at once. |
| text display | The text display actor's text | yes | Use `on {_actor}`. |

- **Speaker** names without a colour are shown in yellow. Text supports MiniMessage and legacy `&` codes, and the typewriter keeps all styling (gradients, decorations, hover).
- **Typewriter** reveals characters at the given speed (default 30/s). Updates are rate-limited per channel (`dialogue.*-update-interval`, default every 2 ticks), so a line never sends a packet per character.
- **Word by word**: `word by word [at N words per second]` reveals whole words (default 4/s), keeping styling.
- **Fade in**: `fading in [over <time>]` uses the client's title fade-in on the title and subtitle channels. Other channels have no client fade, so it has no effect there.
- **Typing sound**: `with typing sound "block.note_block.hat"` plays at most every `dialogue.typing-sound-interval` ticks, and only when new non-space characters appear.
- **Hold**: `for <time>` after the line is fully shown (default 3 s). `until advanced` holds until `advance cine dialogue of player`.
- **Manual progression**: `advance cine dialogue of player` completes the typewriter, or ends the hold if the line is already complete. `clear cine dialogue of player` removes the line.
- A new line on the same channel replaces the previous one for that player.
- Lines started by a scene are cleared when the scene ends.

### Dialogue sequences

Write dialogue as consecutive timed sections, or with `wait` inside a section:

```skript
at 1 second:
    show cine dialogue "First line." from "<aqua>Mira" with typewriter for 2 seconds
at 4 seconds:
    show cine dialogue "Second line." from "<red>Rook" with typewriter for 2 seconds
```

Events: `on cine dialogue start` / `on cine dialogue finish` (per viewer).

### Localization

Choose the text in Skript. For example, look it up in your own variables or language files with the viewer's locale (`player's language` in Skript) before showing it. SkCine shows whatever text you give it.

## Choices

```skript
present cine choice "<yellow>Help the trader?" to cine viewers with options "Accept" and "Decline" with timeout 20 seconds defaulting to "Decline" pausing the scene:
    if cine choice is "Accept":
        jump the cine scene to marker "accept"
    else:
        jump the cine scene to marker "decline"
```

- **Menu:** add `using a menu` to show the options as items in a one-row inventory menu instead of chat. It works while viewers are spectating the camera. Clicks are cancelled and validated on the server, the menu closes after choosing, and closing it without choosing falls back to the chat options.
- **Selection:** clickable `[1]`/`[2]` entries in chat (Adventure click callbacks: single use, expire after the timeout) or typing the number or the option text. Typed replies are consumed and do not appear in chat.
- **Security:** every selection is validated on the server against the choice's audience and options. A player can choose once. Nothing runs commands, and branching happens only in your Skript section.
- **Per-player** (default): the section runs per answer. Use `event-player` and `cine choice`.
- **Votes** (`as a vote`): the section runs once with the majority option (ties go to the earlier option). There is no `event-player`.
- **Timeout:** unanswered players receive the default (or none) with `cine choice timed out` true.
- **Pausing:** `pausing the scene` stops timeline time until the choice is resolved. Without it, the scene keeps playing.
- **Branching** patterns: jump to markers on one timeline (`examples/advanced/branching-story.sk`), set `cine variable`s that later sections check, or play another scene.

Events: `on cine choice`, `on cine choice timeout`. Up to 9 options; at most `limits.max-active-choices` (200) open at once.

### Other selection UIs

Beyond chat and the built-in menu, any custom UI built with Skript or a GUI addon can drive a scene: call `seek`, `jump`, `set cine variable` or `resume cine playback` from its click handlers.
