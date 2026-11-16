# Architecture

[简体中文](ARCHITECTURE.zh-CN.md)

All code lives in `launcher-app/src/com/benbenyu/btquicklaunch`.

| Class | Role |
|---|---|
| `MainActivity` | Landscape home screen, four app slots, ordering, wallpaper, themes, brightness, settings |
| `ButtonService` | Accessibility key filter for the configured keyboard; double-press, three-key chord, voice button detection |
| `ProxyGuard` | Local network, VPN interface, Clash process and loopback-port checks; no external probes |
| `HidConnectionManager` | Handles HID/ACL broadcasts and notification sounds, polls state every second, and reconnects on a separate executor |
| `RootHidProvider` / `RootHidConnect` | Restricted root bridge that performs the HID connect; provider is not exported and checks caller UID |
| `IdlePower` | Dims the home window after 2 minutes idle; resets on touch, HID, media playback |
| `WifiBeaconGuard` | Foreground 60 s Wi-Fi SSID beacon watcher; shows a 5-second countdown and sleeps via Root when absent past timeout |
| `ImeGuard` | Disables/restores system soft keyboards so IME popups do not block voice controls |
| `IosUi` | iOS-style grouped cards, switches, disclosure rows, dividers, and footers for Settings |
| `CircularIconView` | Uniform circular display mask for home and settings app icons |
| `MemoryIndicator` | Top-right whole-device RAM usage chip icon and percentage indicator |
| `PowerIndicator` | Top-right battery wattage, current (mA), and voltage (V) indicator sampled every 2 seconds while foregrounded |
| `NightWallpaper` | Bundled static XML dark and e-book wallpapers |
| `NetIndicators` / `StatusGlyph` | Wi-Fi / mobile / battery / Bluetooth status icons |
| `ReminderEvent` / `ReminderAudio` | Per-event reminder definitions, sound/voice routing, and summary formatting |
| `SystemTonePlayer` | Bundled AOSP OGG playback and draft preview inside Settings |
| `ConfigBackup` | ZIP config export / import with typed validation and limits |
| `AppLog` / `HomeApplication` | Daily logs, 14-day cleanup, crash and previous exit reasons |
| `OfflineVoiceAlerts` | Bounded offline audio queue, connection-event priority, playback timeout and lifecycle cleanup |
| `AppUpdater` / `UpdateApkProvider` | In-app GitHub Releases update check, APK download verification, and scoped `content://` installer bridge |
| `DeviceConfig` | Device-specific constants (keyboard address) |

`phone-config/92-btquicklaunch.sh` is a Magisk `service.d` script that restores the default home, battery whitelist and accessibility service at boot.

`phone-config/grant-home.sh` is a record of a device-specific Magisk grant (it hardcodes a UID); do not reuse it as is.
