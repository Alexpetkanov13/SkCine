# Echoes of the Shattered Crown

A complete cinematic campaign for Minecraft, written entirely in Skript on top of **SkCine**: about 10,000 lines across 29 scripts, with 37 scenes, 61 sequences and 264 functions.

A queen's crown was shattered nine hundred years ago. You recover its five shards from five guardians, decide what happens to each of them, and then decide what the crown becomes. Your choices are remembered, change later scenes, and lead to one of four endings.

## What's inside

| | |
|---|---|
| **Story** | Prologue, five shard chapters (each with an intro, a trial, a guardian and an outro), six library interludes, a finale and **four endings**, then rolling credits personalised with your choices. |
| **Choices** | An oath, five mercy-or-power decisions and a final choice, all in inventory menus. Karma decides between two endings. A secret fourth ending appears only after five acts of mercy. |
| **Trials** | Waves of enemies around the stage, a HUD, leashing, and "downed" instead of death. They scale with party size, can fail and be retried, and track best times. |
| **Guardians** | Five bosses with telegraphed abilities: every attack is announced by a ring on the ground. Each has an intro, a rage phase at half health and a fall cinematic. |
| **Parties** | Up to four players watch every chapter as one shared playback. Choices are voted on and skips need a majority. |
| **Codex** | 25 lore pages that unlock through the story and ambient events, readable in chat or read aloud as a small cinematic. |
| **Achievements** | 27, with toasts that wait until a scene is over. |
| **Gallery and tales** | Replay any scene you've seen (nothing is granted twice), plus six unlockable side stories. |
| **Post-game** | The Trial of Echoes (all five guardians back to back) with server leaderboards, and wearable auras. |
| **World** | Shrines admins can place, Echo sightings, the Crown Comet, a Night of Bells server event, and a camera "photo mode". |
| **Quality of life** | Per-player text speed and dialogue channel, a "previously on…" recap after three days away, guardian tips, and an admin toolkit. |

## Requirements

- Paper **1.21.9 or newer** (the campaign uses player-looking actors) and Java 21+
- Skript **2.16+**
- SkCine 1.0.0
- Trials need difficulty **Easy** or higher. On Peaceful they are skipped and the story continues.

## Install

1. Copy this whole folder to `plugins/Skript/scripts/shattered-crown/`.
2. Run `/sk reload scripts` (or restart).
3. Optionally, pin chapters to a location: stand somewhere open and run `/crown admin stage set default`. Sets are built in front of you, towards +z (south). Without a pinned stage, each chapter plays where the party leader stands.
4. Players type **`/crown`**.

## Commands

| Players | |
|---|---|
| `/crown` | Hub menu |
| `/crown play` | Continue the story |
| `/crown status` · `/crown journal` | Progress, and every choice you made |
| `/crown codex [id]` · `/crown codex read <id>` | Lore, in chat or read aloud |
| `/crown gallery [n\|menu]` · `/crown tales` | Replay scenes and side stories |
| `/crown achievements` · `/crown cosmetics` | What you've earned, and auras to wear |
| `/crown party invite\|accept\|deny\|leave\|kick\|disband\|list\|chat` | Play together |
| `/crown gauntlet` · `/crown leaderboard [trial]` | Post-game |
| `/crown settings [speed\|channel\|ambient] [value]` | How dialogue is shown |
| `/crown recap` · `/crown photo [epic\|calm\|sky]` · `/crown tips <shard>` | Extras |
| `/crown reset confirm` | Start over |

Admins (`crown.admin`, also from the console): `/crown admin` followed by one of `info`, `reset`, `chapter`, `karma`, `choose`, `shards`, `unlockall`, `stage`, `shrine`, `bells`, `run`, `outro`, `ending`, `replay`, `trial`, `win`, `endtrial` or `speed`. Each is documented at the top of `40-commands.sk`.

## Hooking it into your server

Edit **`37-server-hooks.sk`**. It is called when a chapter, trial, ending or achievement is completed, so you can give your own rewards. The campaign itself never gives items or money, apart from what you add there.

## Files

| File | |
|---|---|
| `00-core.sk` | Profiles, progression, karma, settings, formatting |
| `01-party.sk` | Parties |
| `02-fx-library.sk` · `03-camera-library.sk` | 57 reusable effect and camera sequences |
| `04-dialogue.sk` | The cast, voices and the `say` sequence |
| `05-trials.sk` · `20-bosses.sk` | Trials and guardians |
| `06-codex.sk` · `07-achievements.sk` | Lore and achievements |
| `09-flow.sk` | How chapters start, chain and replay |
| `10-…17-` | Prologue, chapters, finale and endings |
| `18-tales.sk` · `19-interludes.sk` | Side stories and library interludes |
| `30-menus.sk` | Hub, gallery, settings, status, journal, help |
| `31-ambient.sk` · `32-shrines.sk` · `34-photo-and-events.sk` | The world |
| `33-gauntlet.sk` · `35-recap-and-cosmetics.sk` · `36-tips.sk` | Post-game and extras |
| `37-server-hooks.sk` | **Your** rewards |
| `40-commands.sk` | `/crown` |

## How it was tested

`integration-tests/bot/crown-test.mjs` plays the campaign with a real Minecraft client on a real server. It answers the menus, fights through a trial with the admin tools, and checks that progress, karma, shards, endings, achievements and the leaderboard are recorded correctly, and that the server log has no warnings or errors. It does **not** judge how scenes look; that still needs a human with a game client.
