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
- Doubao waits for a stable phone-button location after rotation and confirms the call screen before stopping; up to three attempts alternate node clicks and gestures at the detected button.
- Gemini Live uses the detected button bounds for a gesture click and confirms current Live controls before stopping, with bounded retries; no fixed screen coordinates.
- Background app indicator, battery / Wi-Fi / mobile / Bluetooth status icons (including BLE keyboard battery level when reported via GATT)
- Color and e-paper style themes, custom wallpapers and icons
- Bundled static XML wallpapers default to pure black, require no decoded bitmap or animation, and remain available offline. System image selection is optional.
- Top-right whole-device RAM usage refreshes every 10 seconds while the home screen is visible and the display is on; tap for capacity details. Available RAM includes reclaimable memory.
- E-book themes offer black (monochrome dimmed icons on pure black) and normal eye-comfort (warm paper) modes, plus off. Previous paper-theme settings retain the normal mode. RAM is shown with a chip icon and percentage.
- Reminder settings are organized by event: each event shows its saved voice or tone, with selection and draft preview in one dialog. Cancel keeps the saved setting; legacy shared choices remain defaults until overridden.
- An orange ring around an app icon indicates its background process is running. State follows the package when slots are reordered; cleanup requests an immediate refresh.
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
adb install -r dist/BT-QuickLaunch-v1.7.0.apk
adb shell cmd package set-home-activity com.benbenyu.btquicklaunch/.MainActivity
```

Then enable the accessibility service, pair the keyboard, and optionally install `phone-config/92-btquicklaunch.sh` to `/data/adb/service.d/` (mode 755) for boot-time recovery.

## Repository layout

For project background, actual button-recognition rules, reliability limits and iteration guidance, read [Project context](docs/PROJECT_CONTEXT.md). Current matching uses accessibility resource IDs **and descriptions**, not OCR or fixed-coordinate recognition; visible node text is not currently used by the voice matcher.

| Path | Content |
|---|---|
| `launcher-app/` | Android app sources, manifest, resources, device checks |
| `phone-config/` | Magisk boot script and device QA scripts |
| `build-home.ps1` | Build and sign script |
| `docs/` | Architecture and project status |
| `CONTRIBUTING.md` | Contribution rules (READMEs must stay in sync) |

## License

Source code: [MIT](LICENSE). Third-party assets: [notices and attribution](THIRD_PARTY_NOTICES.md).
