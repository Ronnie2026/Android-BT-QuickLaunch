# Architecture

All code lives in `launcher-app/src/com/benbenyu/btquicklaunch`.

| Class | Role |
|---|---|
| `MainActivity` | Landscape home screen, four app slots, ordering, wallpaper, themes, brightness, settings |
| `ButtonService` | Accessibility key filter for the configured keyboard; double-press, three-key chord, voice button detection |
| `ProxyGuard` | Local network, VPN interface, Clash process and loopback-port checks; no external probes |
| `HidConnectionManager` | Handles HID/ACL broadcasts and notification sounds, polls state every second, and reconnects on a separate executor |
| `RootHidProvider` / `RootHidConnect` | Restricted root bridge that performs the HID connect; provider is not exported and checks caller UID |
| `IdlePower` | Dims the home window after 2 minutes idle; resets on touch, HID, media playback |
| `NetIndicators` / `StatusGlyph` | Wi-Fi / mobile / battery / Bluetooth status icons |
| `ConfigBackup` | ZIP config export / import with typed validation and limits |
| `AppLog` / `HomeApplication` | Daily logs, 14-day cleanup, crash and previous exit reasons |
| `OfflineVoiceAlerts` | Bounded offline audio queue, connection-event priority, playback timeout and lifecycle cleanup |
| `DeviceConfig` | Device-specific constants (keyboard address) |

`phone-config/92-btquicklaunch.sh` is a Magisk `service.d` script that restores the default home, battery whitelist and accessibility service at boot.

`phone-config/grant-home.sh` is a record of a device-specific Magisk grant (it hardcodes a UID); do not reuse it as is.