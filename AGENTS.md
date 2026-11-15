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
- **Privacy:** All commits and tags must use `Ronnie2026@users.noreply.github.com` (never commit personal emails).

## Dual-remote & release policy (GitHub primary, Gitee low-frequency)

- This repository maintains `origin` (GitHub, primary) and `gitee` (Gitee, low-frequency backup).
- **Default release target is GitHub (`origin`):** Whenever releasing a new version (`vX.Y.Z`), automatically:
  1. Bump `android:versionCode` and `android:versionName` in `launcher-app/AndroidManifest.xml`.
  2. Build and sign `dist\BT-QuickLaunch-vX.Y.Z.apk` locally via `build-home.ps1` (must use `.keys\btquicklaunch.jks`).
  3. Push `main` and annotated tag `vX.Y.Z` to `origin` (GitHub) and publish the GitHub Release with `dist\BT-QuickLaunch-vX.Y.Z.apk` attached (`.\release.ps1` automates steps 2–3).
- **Gitee (`gitee`) is low-frequency / on-demand:** Only push or publish Gitee Releases when explicitly requested by the user (pass `-IncludeGitee` to `.\release.ps1`). In-app `AppUpdater.java` checks **GitHub Releases first** and falls back to **Gitee Releases**.
