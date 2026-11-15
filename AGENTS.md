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

## Dual-remote & release synchronization (GitHub + Gitee)

- This repository maintains two remotes: `origin` (GitHub) and `gitee` (Gitee).
- **Code & tag sync is mandatory:** Whenever commits or tags are pushed to `origin`, push the same branch and tags to `gitee` (`git push gitee main --tags`) so both repositories remain identical.
- **Release sync is mandatory:** In-app auto-update (`AppUpdater.java`) checks **Gitee Releases first** and falls back to **GitHub Releases**. Whenever releasing a new version (`vX.Y.Z`):
  1. Bump `android:versionCode` and `android:versionName` in `launcher-app/AndroidManifest.xml`.
  2. Build and sign `dist\BT-QuickLaunch-vX.Y.Z.apk` locally via `build-home.ps1` (must use the existing `.keys\btquicklaunch.jks` signing key).
  3. Push `main` and annotated tag `vX.Y.Z` to **both** `origin` and `gitee`.
  4. Publish the `vX.Y.Z` Release **with `dist\BT-QuickLaunch-vX.Y.Z.apk` attached** on **both** GitHub Releases and Gitee Releases (you can run `.\release.ps1` to automate steps 2–4).
