# Android-BT-QuickLaunch

[中文说明](README.zh-CN.md)

A minimal Android home screen (launcher) that turns a **Bluetooth keyboard / keypad** into a hardware shortcut panel for launching the apps you choose.

## Background

Old Android phones are cheap, capable and usually sit in a drawer. This project turns one into a **smart in-car terminal** for voice chat and AI-agent use: a dedicated device ("an agent's agent client") that you control with a few physical buttons instead of a touchscreen while driving.

The phone runs this launcher as its default home screen. A paired Bluetooth keypad then opens the right app with one press: an AI voice assistant, a navigation app, a music app and so on, with no menus in between.

## Features

- Fixed landscape home screen with four configurable app slots; long-press to swap slots
- Bluetooth keypad shortcuts: double-press key 1/2/3 to open the first three slots; press all three keys to clear background apps and return home
- ChatGPT / Gemini voice launch with local network and VPN/proxy checks; starts Clash Meta and re-checks when needed (no external connectivity probes)
- Successful background cleanup displays Patrick Star with a large “搞定了” caption and an optional system confirmation tone; triggered by holding/double-tapping X or pressing all three keyboard keys.
- Doubao automatically clicks its phone control after launch, using accessibility nodes in its portrait interface. Account quota limits still apply.
- Background app indicator, battery / Wi-Fi / mobile / Bluetooth status icons (including BLE keyboard battery level when reported via GATT)
- Color and e-paper style themes, custom wallpapers and icons
- Eight bundled Android AOSP notification sounds support selection and preview for general tone reminders and cleanup success independently; offline app assets only, with no external imports or device media-provider dependency.
- App icons use a uniform circular display mask, including custom icons; original image files stay unchanged.
- Adaptive launcher icon with a car/Bluetooth mark
- Idle dimming after 2 minutes to reduce OLED burn-in
- Fast HID reconnect on disconnect (3 retries, then passive wait during keyboard sleep)
- Keyboard connection/disconnection sounds follow HID/ACL broadcasts, with 1-second polling as fallback; root reconnect runs separately. Sudden power-off detection still depends on the Bluetooth stack timeout.
- Reminder audio requires ring mode; silent/vibrate modes use vibration only. Enable ring mode and check media volume to hear speech.
- Offline reminders for keyboard connected/disconnected, keyboard battery below 30% at connection/disconnection, and phone battery below 30% while not charging. Settings provide a master switch, individual switches and tone/voice selection; enabled with voice by default.
- Config export / import (ZIP), daily log export, crash logging, and one-tap entry to the classic system launcher in Settings

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
adb install -r dist/BT-QuickLaunch-v1.5.0.apk
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
| `CONTRIBUTING.md` | Contribution rules (READMEs must stay in sync) |

## License

Source code: [MIT](LICENSE). Third-party assets: [notices and attribution](THIRD_PARTY_NOTICES.md).