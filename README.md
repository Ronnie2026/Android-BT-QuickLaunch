# Android-BT-QuickLaunch

[中文说明](README.zh-CN.md)

A minimal Android home screen (launcher) that turns a **Bluetooth keyboard / keypad** into a hardware shortcut panel for launching the apps you choose.

## Background

Old Android phones are cheap, capable and usually sit in a drawer. This project turns one into a **smart in-car terminal** for voice chat and AI-agent use: a dedicated device ("an agent's agent client") that you control with a few physical buttons instead of a touchscreen while driving.

The phone runs this launcher as its default home screen. A paired Bluetooth keypad then opens the right app with one press: an AI voice assistant, a navigation app, a music app and so on, with no menus in between.

## Features

- Fixed landscape home screen with four configurable app slots; long-press to swap slots
- Bluetooth keypad shortcuts: double-press key 1/2/3 to open the first three slots; press all three keys to clear background apps and return home
- Special ChatGPT flow: checks Google/ChatGPT connectivity, optionally starts a proxy (Clash) and re-checks, then starts voice mode
- Background app indicator, battery / Wi-Fi / mobile / Bluetooth status icons
- Color and e-paper style themes, custom wallpapers and icons
- Idle dimming after 2 minutes to reduce OLED burn-in
- Active HID reconnect to the paired keyboard
- Config export / import (ZIP) and daily log export; crash logging

## Requirements and limitations

> This project is tuned for one device and is **not** a general-purpose launcher yet.

- Developed and tested on a OnePlus 7T (HD1900), Android 11, rooted with Magisk
- Root is required for clearing background apps and for actively reconnecting the HID keyboard (uses a hidden Android 11 Bluetooth HID interface)
- The accessibility service is used to read key events from the keyboard and to find ChatGPT voice buttons, so ChatGPT UI updates may need adaptation
- The keyboard address is set in [`DeviceConfig.java`](launcher-app/src/com/benbenyu/btquicklaunch/DeviceConfig.java); change it to your own keyboard
- Most UI strings are currently Chinese; localization is planned

See [docs/STATUS.md](docs/STATUS.md) for what has and has not been verified.

## Build

Requirements: Windows PowerShell, JDK 17, Android SDK platform 30 and build-tools (or the local `.tools` cache).

```powershell
.\build-home.ps1
```

Output: `dist/BT-QuickLaunch-v<version>.apk`. A local signing key is generated in `.keys/` on first build. Keep it private and reuse it so updates can install over each other.

## Install

```bash
adb install -r dist/BT-QuickLaunch-v1.1.0.apk
adb shell cmd package set-home-activity com.benbenyu.btquicklaunch/.MainActivity
```

Then enable the accessibility service, pair the keyboard, and optionally install `phone-config/92-btquicklaunch.sh` to `/data/adb/service.d/` (mode 755) for boot-time recovery.

## Repository layout

| Path | Content |
|---|---|
| `launcher-app/` | Android app sources, manifest, resources, device checks |
| `phone-config/` | Magisk boot script and device QA scripts |
| `build-home.ps1` | Build and sign script |
| `docs/` | Architecture and project status |

## License

[MIT](LICENSE)