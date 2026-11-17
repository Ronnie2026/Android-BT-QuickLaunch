# Project status

[简体中文](STATUS.zh-CN.md)

Version 1.8.1, package `com.benbenyu.btquicklaunch`. Target device: OnePlus 7T (Android 11, Magisk).

## v1.8.1 update
- Added **Keep screen on only while charging** (`keep_screen_on_charging_only`, default on) under Power Saving settings: clears `FLAG_KEEP_SCREEN_ON` when unplugged so Android's screen-off timeout puts the device to sleep on battery while staying awake on car USB power.

## v1.8.0 release summary
- Improved `NetIndicators` so cellular signal bars (0–4) and network generation (`5G`/`4G`/`3G`/`2G`) stay visible whenever a SIM is ready and airplane mode is off, including alongside Wi-Fi and on data-only registration (PS `IN_SERVICE` even when CS voice is `OUT_OF_SERVICE`).
- Added Android 11 `TelephonyDisplayInfo` (5G NSA/SA) and `ServiceState.getNetworkRegistrationInfoList()` fallbacks, active default-data subscription resolution, an out-of-service `×` indicator, and live uplink/downlink traffic speed (`TrafficStats.getTotalTxBytes()` / `getTotalRxBytes()`, sampled every 1 s while visible).
- Added first-launch `READ_PHONE_STATE` runtime permission request and callback refresh in `MainActivity`.
- Changed the top-right settings gear tap in `MainActivity` to open a 2-option menu with icons (**系统经典桌面** / **应用设置**) for quick access to the classic launcher.
- Added `WifiBeaconGuard` and grouped power-saving strategies under **General Settings -> 省电策略**: (1) 2-minute idle dimming, and (2) Wi-Fi SSID beacon auto-sleep (60 s foreground Wi-Fi scan without connecting; selectable nearby SSID list; 2/3/5-minute timeout; 5-second countdown confirmation dialog before `KEYCODE_SLEEP`; non-intrusive during active media or recent interaction; preferences included in `ConfigBackup`).
- Added `PowerIndicator` next to the battery icon to display live wattage (`W`), current (`mA`), voltage (`V`), and temperature (`°C` with ≥42.0 °C highlight), plus `PowerIndicatorChecks`.
- Added top-right status badges for VPN/Clash readiness, `ButtonService` accessibility binding, and Bluetooth audio (`A2DP`/`SCO`) connection.
- Added a **状态展示** section in General Settings with 11 toggle switches (`show_mobile_signal`, `show_wifi`, `show_net_speed`, `show_vpn_status`, `show_accessibility_status`, `show_bt_audio`, `show_bluetooth`, `show_hid_battery`, `show_power`, `show_temperature`, `show_memory`), stopping hidden indicators' polling and synced in `ConfigBackup`.
- Added `WifiBeaconChecks` and `NetIndicatorsChecks` (passed alongside `ProcessStateChecks`); verified on OnePlus 7T (China Broadnet `46015` 4G, 4/4 bars matching `dumpsys telephony.registry`; after adding `cbnet` APN, `ping` avg 33.0 ms with 0% loss, ~2.78 MB/s HTTP download, and live `↑`/`↓` speed display confirmed via screenshot).

## v1.7.9 update
- Updated cleanup success overlay caption to `Apps Cleared`.

## v1.7.8 update
- Updated cleanup success overlay caption to `后台清理成功`.

## v1.7.7 update
- Shortened Patrick Star cleanup success overlay from 3,000 ms (+200 ms fade) to a quick 700 ms flash (+150 ms fade) alongside the confirmation tone.

## v1.7.6 update
- Removed the hardcoded boot-time auto-launch of ChatGPT (`boot_count` check in `MainActivity.onCreate`) so rebooting stays on the home launcher instead of jumping into ChatGPT without user input; `ensureProxyOnStart()` still runs when enabled.

## v1.7.5 release summary
- Added in-app auto-update (`AppUpdater.java` + `UpdateApkProvider.java`) in **General Settings -> Software Update**, defaulting to **GitHub Releases** (`Ronnie2026/Android-BT-QuickLaunch`) with manual source selection (no automatic fallback on failure).
- Startup auto-check is deferred by 6 seconds on a background thread (at most once every 6 hours) so app launch speed is unaffected.
- Downloaded APKs are verified via `PackageManager.getPackageArchiveInfo` before launching the system package installer, and failures guide the user to the GitHub Releases page with a one-tap link.
- Hardened `HidConnectionManager` low-battery hysteresis and `su` output redirection, added lifecycle guards to `MainActivity` background UI callbacks, and fixed light e-book theme text contrast.

## v1.7.0 validation
- Bundled static XML wallpapers default to black; system image selection remains available. Corrected the gallery tile index that previously opened the picker for Graphite.
- Added black and normal eye-comfort e-book themes, preserving the previous paper-theme preference. Theme selections include backup/restore support.
- Added a chip icon with whole-device RAM usage, refreshed every ten seconds while visible and interactive; polling stops on screen-off and activity pause.
- Doubao and Gemini wait for stable button bounds, attempt at most three clicks, and confirm call controls before stopping. Current Doubao hangup and Gemini Live labels are recognized.
- Device UI tests confirmed both e-book modes, memory display, image-picker opening/cancel, and screen-off recovery.
- Device launch tests confirmed Doubao cold/warm call entry and Gemini landscape Live entry; already-active calls are recognized without repeating the click.
- VoiceTargetChecks, ProcessStateChecks, ReminderEventChecks, SystemToneUriChecks, DeviceChecks, HidStateChecks and ReminderChecks passed against the release classes. Backup checks include the black e-book setting.
- Grok Bot is a separate app and has no automatic voice-entry integration in this release.

## Verified on device
- Build, signing, install over existing version
- Default home survives a full reboot; accessibility service and boot script recover
- Double-press launch and three-key clear (input-level simulation)
- Config export / import round trip, invalid file rejected, size limit
- Crash capture and previous-exit-reason logging
- Root HID bridge connection accepted by the system

## v1.6.0 validation
- Added per-event editors with matching production definitions, saved summaries, draft preview, Save and Cancel.
- Orange active-state rings are attached to app icon views and matched by package, replacing layout-sensitive underlines.
- ReminderEventChecks cover independent event choices, actual asset associations, legacy shared choices and master-switch behavior.
- Backup checks include per-event media and tone fields.
- ProcessStateChecks, ReminderEventChecks, DeviceChecks, ReminderChecks and HidStateChecks passed on device.
- Actual UI draft preview played the chosen tone; Cancel left the existing disconnection setting unchanged.
- Device screenshots confirmed an orange ring on background Doubao and no ring after successful cleanup.

## v1.5.0 validation
- Eight AOSP OGG sounds are bundled in the APK; runtime playback uses only these assets, with no MediaStore or external file dependency.
- SystemToneUriChecks passed fixed-ID validation and external/path/network rejection; DeviceChecks passed sound-selection backup round-trip and invalid external selection rejection.
- ReminderChecks parsed all eight bundled sound files. SystemToneInstrumentation passed actual selected-sound playback, default fallback and cleanup in the application process.
- The selection dialog was checked on device with preview/save/cancel controls.
- Doubao phone-button automation passed: the observed resource ID is com.larus.nova:id/real_time_call, and the device log confirmed VOICE_CLICKED for com.larus.nova. Account quota exhaustion is an accepted proof of entry; a completed call is not claimed.
- VoiceTargetChecks passed Doubao control matching, package isolation, and existing GPT/Gemini trigger checks.
- Device cleanup returned exit code 0 and displayed the success overlay; physical three-key input also triggered this path.
- The success overlay now shows Patrick Star and the native Chinese caption 搞定了 with an optional system confirmation tone, enabled by default. Failure keeps the existing error message and never shows success.

- Home and settings app icons now use one circular display mask; Amap was visually checked on device.
- Cleanup success tone was observed in device logs; the new sound switch passed the configuration backup round-trip.
- VoiceTargetChecks, DeviceChecks, HidStateChecks and ReminderChecks passed with the final build.

## v1.3.0 validation
- APK build and v2/v3 signature verification passed; update installed on the target device.
- Device-side HidStateChecks cover initial attachment, intermediate states, HID/ACL broadcasts, exhausted retry budgets, low-battery boundaries and shutdown cleanup.
- An ACL disconnect was processed in approximately 1 ms while the reconnect executor was blocked; this measures software event handling, not keyboard power-off detection.
- End-to-end notification within 3–5 seconds of physical keyboard power-off remains unverified and depends on the Bluetooth stack detecting link loss.

- ReminderChecks passed phone threshold/charging rules, deduplication, master and individual switches, audio parsing and queue cleanup.
- VoiceInstrumentation played all four bundled clips to completion inside the target app process.
- Config backup round-trip passed with proxy app lists and all reminder settings; malformed settings remain rejected.
- v1.3.0 adds four bundled offline voice clips, default-on voice reminders with tone selection, individual switches and an adaptive launcher icon.
- Physical keyboard cycling confirmed audible reminders after enabling ring mode; speech uses media audio with an explicit ring-mode gate and vibration fallback.
- Latest device log: controller disconnect reason 8 (connection timeout), ACL broadcast at 23:26:36.101 and speech start at 23:26:36.511 (410 ms application response). Physical power-off to notification still takes about 10 seconds on this keyboard.
- Magisk watchdog now matches a real ServiceRecord rather than stale ConnectionRecord text; test teardown must confirm the accessibility service is bound.
- Long-run playback and battery drain still require manual validation.

## Not yet fully verified
- Multi-hour stability, battery and heat; Bluetooth drop statistics
- Keyboard reconnect after sleep / power-off / out of range
- Idle dim (2 min) and restore by touch / HID / media events
- ChatGPT flow under real network loss, proxy failure, VPN switching
- Reboot and force-stop recovery with the final build
- Wi-Fi / SIM signal states with a real SIM
- Other Android versions and ROMs

## Important note on the package rename
Releases before this repository used package `com.yijia.home`. The renamed app installs as a **new app**: set it as default home, enable its accessibility service and grant Root again.
