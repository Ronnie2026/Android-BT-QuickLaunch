# Project status

Version 1.3.0, package `com.benbenyu.btquicklaunch`. Target device: OnePlus 7T (Android 11, Magisk).

## Verified on device
- Build, signing, install over existing version
- Default home survives a full reboot; accessibility service and boot script recover
- Double-press launch and three-key clear (input-level simulation)
- Config export / import round trip, invalid file rejected, size limit
- Crash capture and previous-exit-reason logging
- Root HID bridge connection accepted by the system

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