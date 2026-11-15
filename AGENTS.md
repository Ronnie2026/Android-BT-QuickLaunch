# Agent instructions

## Required reading before development

- Before planning or modifying this project, read this file, [CONTRIBUTING.md](CONTRIBUTING.md), [project context](docs/PROJECT_CONTEXT.md), [architecture](docs/ARCHITECTURE.md), and [current status](docs/STATUS.md). Their `.zh-CN.md` counterparts are equivalent reading options. Read the relevant feature documentation before changing that feature.
- Check the current implementation and working tree before assuming a requested behavior already exists. Preserve the product goals, compatibility limits, saved preferences, and previously authorized scope described in the project context.
- If code and documentation disagree, verify the implementation and correct both language versions; do not describe intended behavior as implemented.

## Mandatory bilingual documentation

- English is the default: `docs/NAME.md`; Simplified Chinese is its independent counterpart: `docs/NAME.zh-CN.md`.
- Every maintained Markdown document in `docs/` must have both versions, reciprocal language links, and matching technical meaning. Create, modify, rename, or remove both in the same commit. Update affected documentation when behavior changes.
- Keep project context up to date when architecture, supported targets, recognition rules, requirements, or reliability limits change. Never leave Chinese readers with an older design or omit limitations from either language.

- **README sync is mandatory.** Whenever `README.md` changes, update `README.zh-CN.md` in the same commit, and vice versa. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Repository constraints

- Never commit `.keys/`, `.tools/`, `*.jks`, APKs, or phone logs/backups.
- Package name is `com.benbenyu.btquicklaunch`; keyboard address lives in `DeviceConfig.java`.
