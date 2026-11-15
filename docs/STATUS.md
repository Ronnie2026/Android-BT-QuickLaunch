# Project status

[简体中文](STATUS.zh-CN.md)

Version 1.7.6, package `com.benbenyu.btquicklaunch`. Target device: OnePlus 7T (Android 11, Magisk).

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
