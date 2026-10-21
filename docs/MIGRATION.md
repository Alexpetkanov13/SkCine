# Migration guide

## Policy

- SkCine follows semantic versioning from 1.0.0. **Patch** and **minor** releases never remove or change the meaning of existing Skript syntax. New syntax may be added.
- When syntax must change in a **major** release, the old pattern keeps working for at least one major version and logs a one-time deprecation warning with the replacement and the script line.
- Removed or renamed syntax is listed here with before/after examples.
- `config.yml` keys are only ever added. Unknown keys are ignored, and missing keys use defaults, so old configs keep working.
- Recovery files are versioned by their fields and read leniently (unknown fields are ignored).

## Checking your scripts after an update

1. Run `/sk reload <folder>/` on your cinematic scripts and look for parse errors.
2. Run `/skcine info syntax` and compare `plugins/SkCine/syntax-index.md` with the previous version to see added patterns.
3. Watch the console for `[SkCine] ... deprecated` warnings.

## Version history

### 1.0.0

Initial release. No migrations required.
