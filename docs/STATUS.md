# Project status

Version 1.2.1, package `com.benbenyu.btquicklaunch`. Target device: OnePlus 7T (Android 11, Magisk).

## Verified on device
- Build, signing, install over existing version
- Default home survives a full reboot; accessibility service and boot script recover
- Double-press launch and three-key clear (input-level simulation)
- Config export / import round trip, invalid file rejected, size limit
- Crash capture and previous-exit-reason logging
- Root HID bridge connection accepted by the system

## v1.2.1 validation
- APK build and v2/v3 signature verification passed.
- Device-side HidStateChecks passed for initial attachment, intermediate states, and HID/ACL broadcasts.
- An ACL disconnect was processed in approximately 1 ms while the reconnect executor was blocked; this measures software event handling, not keyboard power-off detection.
- End-to-end notification within 3–5 seconds of physical keyboard power-off remains unverified and depends on the Bluetooth stack detecting link loss.

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