# Contributing to SkCine

Thanks for helping! SkCine has one rule above all: **scenes are authored in Skript.** Contributions must not add scene editors, scene files (YAML/JSON), or scene-building commands.

## Project layout

| Module | Contents | Depends on |
|---|---|---|
| `skcine-core` | Pure Kotlin: math, easing, splines, camera tracks, timelines, state machine, shapes. No server classes. | — |
| `skcine-api` | Public integration API and Bukkit events. | core |
| `skcine-platform` | Paper implementations: scenes, cameras, actors, animator, effects, sound, dialogue, choices, player state, profiler. | api, Paper |
| `skcine-skript` | Skript syntax: types, structures, sections, effects, expressions, conditions, events. | platform, Skript |
| `skcine-plugin` | Plugin bootstrap, config, listeners, commands; produces the shaded JAR. | all |

## Building

```powershell
.\gradlew.bat clean build                # compile, ktlint, unit tests, shaded JAR
.\gradlew.bat ktlintFormat               # auto-format
.\gradlew.bat :skcine-core:benchmark     # engine benchmark
```

## Rules for new syntax

1. Every pattern contains `cine`, so nothing in Skript core or other addons is shadowed.
2. Register through the modern API (`SyntaxRegistry`, `SyntaxInfo`, `EventValueRegistry`), never deprecated `Skript.register*`.
3. Validate at parse time where possible (with clear `Skript.error` messages). At runtime, report through `report(where, ...)` so the message includes the script and line.
4. Be null-safe: unset or expired values must never throw.
5. Add the pattern to `integration-tests/scripts/skcine-syntax-test.sk`. Add a negative case to `integration-tests/scripts/negative/` if it has parse-time validation, and a bot scenario if it has in-game behaviour.
6. Document it in the matching `docs/*.md` page and regenerate `docs/SYNTAX_INDEX.md` with `/skcine info syntax`.

## Engine rules

- Main thread only for Bukkit/Skript. Never `Thread.sleep`, and never call Skript triggers from async threads.
- No per-object scheduler tasks; work goes into the central tick phases.
- Anything a scene creates must be owned by the scene (`instance.own(...)`) so cleanup is automatic.
- Spawned entities are non-persistent and tagged.
- Never claim untested compatibility. Update `docs/COMPATIBILITY.md` only with real results.

## Testing on a real server

See [docs/TESTING.md](docs/TESTING.md). `integration-tests/run-integration.ps1` downloads Paper and Skript, starts a server, and runs all suites.
