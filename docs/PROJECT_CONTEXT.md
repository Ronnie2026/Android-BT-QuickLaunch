# Project context and iteration guide

[简体中文](PROJECT_CONTEXT.zh-CN.md)

Implementation baseline: v1.7.5. This document describes the shipped code, not a promise about future third-party app versions.

## Purpose and operating environment

Android-BT-QuickLaunch reuses an Android phone as a dedicated in-car voice terminal. A small paired Bluetooth keyboard provides physical shortcuts to AI assistants and other selected apps, reducing the touchscreen steps needed to open them. The launcher is the phone's default home screen, with four configurable slots in landscape.

The priorities are predictable wake-and-launch behavior, visible feedback, offline reminders, and limited background work. The target device is a OnePlus 7T running Android 11 with Magisk root. This is a device-tuned project, not a universally compatible Android launcher. Root access does not guarantee that Android or the vendor will never stop a service.

Package: `com.benbenyu.btquicklaunch`. Device-specific keyboard configuration lives in `DeviceConfig.java`. Do not copy a device UID from a grant script to another installation without checking it.

## Input, launching and voice entry

1. `ButtonService` is an Android accessibility service. It filters number keys 1/2/3 from the configured external keyboard; a double press selects the corresponding first three slots. Pressing all three keys requests cleanup.
2. The service wakes the phone and delivers `button_action` to `MainActivity`. Slot actions follow saved configuration, including target package, optional prerequisite app, delay and automatic voice preference.
3. For guarded apps, `ProxyGuard` checks local network/VPN/Clash state. It can start Clash and recheck, without contacting an external test server. These checks do not prove the target service is reachable or the account has quota.
4. If automatic voice entry is enabled for a supported target, the activity starts a bounded accessibility search while launching it. GPT also uses the package-scoped `https://chatgpt.com/voice` intent.
5. Voice matching is limited to the target app's package. Gemini is launched through `com.google.android.apps.bard`, but its visible UI may belong to `com.google.android.googlequicksearchbox`.

The integrated voice targets are GPT, Doubao and Gemini. Grok (`ai.x.grok`) and Grok Bot (`ai.x.grok.bot`) are separate apps; neither has an automatic voice-entry adapter in this baseline. Installing an app or including it in the proxy list does not add such an adapter.

## What “button recognition” means here

An accessibility node exposes several different properties:

| Property | Android API | Used by current voice matching? |
|---|---|---|
| View resource ID (often called accessibility/control ID in discussion) | `getViewIdResourceName()` | Yes, where the app exposes a known ID |
| Accessibility description | `getContentDescription()` | Yes, for entry and active-call controls |
| Visible node text | `getText()` | No, not in the current voice-button matcher |
| Visibility, enabled state and bounds | `isVisibleToUser()`, `isEnabled()`, `getBoundsInScreen()` | Yes, as eligibility/position information |

“Accessibility ID” and “control ID” are not two independent recognition engines in this project. The implemented identifier is the view resource ID exposed through accessibility.

The correct description is **resource-ID and accessibility-description matching**. It would be incorrect to document the implementation as “IDs only” or “no descriptions.” A description can contain readable words, but it is a different property from visible text. Reading either property from the accessibility tree is not OCR.

| Target | Entry identification | Activation and completion behavior |
|---|---|---|
| GPT | Accessibility descriptions such as 语音模式 / Voice mode; package-scoped voice URL on launch | Prefer node/ancestor click, then a gesture at detected bounds if necessary. An accepted click currently ends the search; existing active-call descriptions can also stop it. It does not have the same post-click confirmation loop as Doubao/Gemini. |
| Doubao | `com.larus.nova:id/real_time_call` or `com.larus.nova:id/real_time_call_ic_container`; description 打电话 as a fallback restricted to the upper third of the display | Node/ancestor click first, gesture fallback/retry at detected bounds. Confirm via visible call-root/hangup IDs or active-call descriptions including 挂断通话. |
| Gemini | `com.google.android.googlequicksearchbox:id/assistant_robin_input_voice_chat_button_compose` or descriptions such as 打开 Gemini Live / Open Gemini Live | Gesture at detected button bounds. Confirm via visible Live descriptions such as 结束 Live 模式 and 打开/关闭麦克风. A detected input-method window is dismissed before searching. |

The ID and description checks are alternatives, not a requirement that both match simultaneously. See `ButtonService.isVoiceTrigger()`, `isActiveDesc()`, `find()` and `clickVoiceButton()` for the actual rules.

### Coordinates activate an identified control

The code does not identify a button by a fixed pixel coordinate, screenshot template or OCR. After identifying an eligible accessibility node, it reads that node's current bounds and can dispatch a touch at its center. This uses coordinates to perform a click, not to discover which button to click.

For Doubao and Gemini, bounds must remain unchanged across observations for at least 350 ms. The search lasts at most 15 seconds, scans at most 512 nodes per traversal, and attempts at most three clicks separated by at least 1500 ms. Typical retries occur every 600 ms; accessibility window events can trigger earlier observations. An accepted click request is not sufficient to stop these two adapters: a subsequent scan must find a recognized visible call control.

These controls establish UI entry, not a working audio connection or completed server-side task. Broad active-state descriptions are heuristics and should be revisited when adapting new UI versions.

### Compatibility limits

Moving a button generally remains compatible when its ID/description and accessibility exposure survive. A redesign that changes IDs, localization, descriptions, package hosting or accessibility visibility may require an adapter update. The Doubao fallback also assumes its phone control remains in the upper third. Bounds stability reduces rotation races but cannot eliminate every transition race.

Image recognition, OCR and fixed/proportional coordinate matching are not implemented. Official supported voice-launch APIs/deep links are worth investigating before adding more UI heuristics. A proposed improvement must be documented as a proposal until implemented and tested.

## Bluetooth, reminders and recovery

- `HidConnectionManager` receives HID/ACL/adapter broadcasts and polls state every second as a fallback. Root reconnection uses a separate executor so a blocked connect attempt does not block state handling.
- Sudden keyboard power loss can remain undetected until Android's Bluetooth stack declares the link disconnected. App-side polling cannot guarantee a 3–5 second warning from the physical power-off event.
- `RootHidProvider` / `RootHidConnect` provide the restricted root HID bridge. The provider is not exported and validates the caller.
- `phone-config/92-btquicklaunch.sh` restores the default home, Bluetooth, battery whitelist and accessibility enablement at boot. It checks the service approximately every 30 seconds and preserves other enabled accessibility services when repairing this service.
- Confirm actual binding with `dumpsys accessibility`: an enabled setting or stale service connection record alone is not evidence of a working service.
- Reminders use bundled Chinese voice WAVs or bundled AOSP tone assets. Keyboard connection/disconnection, keyboard low battery and phone low battery have independent choices; cleanup success has its own tone setting.
- Low-battery reminders use a threshold below 30%, with deduplication; phone warnings are suppressed while charging. Sound requires ringing mode. Silent/vibrate mode uses vibration instead, and voice/tone volumes follow their respective audio streams.

## Desktop feedback and resource use

- Home icons use a uniform circular mask. An orange ring represents a detected running package/process, not verified app health or an active voice session. Matching follows the package when slots move.
- Cleanup force-stops configured/default target apps and Via, excluding this launcher and Clash. It is not a universal kill of every Android background process. Successful command completion displays Patrick Star, 搞定了 and an optional bundled tone.
- Wallpapers are packaged static XML resources, with pure black as the default and optional user-selected image files. E-book themes override the wallpaper: normal mode uses a paper background; black mode uses pure black and dimmed grayscale rendering.
- The RAM chip shows whole-device usage from `ActivityManager.MemoryInfo`, calculated as `(totalMem - availMem) / totalMem`, not the launcher's own memory. Available RAM includes reclaimable memory. It refreshes every ten seconds while resumed and interactive; screen-off/pause stops polling.
- `IdlePower` dims the launcher after two minutes of inactivity and responds to input, playback and other local events. Static resources and bounded polling reduce unnecessary work; they do not imply zero memory or zero power use.
- `NetIndicators` shows Wi-Fi and cellular signal status in the top-right bar. When airplane mode is off and a ready SIM (or active cellular network) is detected, cellular signal bars (0–4) and generation (`5G`/`4G`/`3G`/`2G`) remain visible even while Wi-Fi is connected or when only packet-switched data is registered (`NetworkRegistrationInfo.isRegistered()`). Out-of-service state renders zero lit bars with an `×` mark. On Android 11+, `READ_PHONE_STATE` is requested once on normal launcher startup so `TelephonyDisplayInfo` (including 5G NSA) and active data subscription info can be read.
- On older Android 11 ROMs (such as OnePlus 7T stock firmware), built-in APN records (`content://telephony/carriers`) already cover the three major carriers out of the box—China Unicom (`46001`: `3gnet` `default,supl` + `ims`), China Telecom (`46003` / `46011`: `ctnet` `default,hipri,ia,supl` + `ims`), and China Mobile (`46000`: `cmnet` `default,net,supl` + `ims`)—so those SIM cards work without manual APN setup. Because this Android 11 firmware predates China Broadnet's (`46015`) 2022 commercial launch, `46015` is missing from the factory APN/IMS tables, which can leave voice `OUT_OF_SERVICE` and cellular data stuck at `DATA_DISCONNECTED` despite full 4G bars; adding a manual APN (`Name: 中国广电`, `APN: cbnet`, `Type: default,supl`) in system Mobile Network settings resolves data connectivity.

Device-only changes such as wallpaper-provider removal or installing a firewall are not launcher source features and are not automatically reproduced by installing this APK.

## Source map and evidence

Start with [ARCHITECTURE.md](ARCHITECTURE.md), [STATUS.md](STATUS.md) and [VOICE_ALERTS.md](VOICE_ALERTS.md).

| Area | Main source files |
|---|---|
| Home, settings, slot configuration, launch cancellation | `MainActivity.java` |
| Physical key handling, voice target isolation, matching, clicks and retries | `ButtonService.java` |
| Network/proxy prerequisite checks | `ProxyGuard.java` |
| Bluetooth state and recovery | `HidConnectionManager.java`, `RootHidProvider.java`, `RootHidConnect.java` |
| Event settings and offline audio | `ReminderEvent.java`, `ReminderAudio.java`, `OfflineVoiceAlerts.java`, `SystemTonePlayer.java` |
| Memory and idle lifecycle | `MemoryIndicator.java`, `IdlePower.java` |
| Backup validation and diagnostics | `ConfigBackup.java`, `AppLog.java`, `HomeApplication.java` |

Useful local log events include `HID_ACTION`, `LAUNCH`, `VOICE_REQUEST`, `VOICE_CLICK_ATTEMPT`, `VOICE_ALREADY_ACTIVE` and `VOICE_TIMEOUT`. For Doubao/Gemini, correlate attempts with recognized call controls and observed UI; do not equate `accepted=true` with successful call entry. Earlier `VOICE_CLICKED` events only establish that a click request was accepted.

The v1.7.0 device checks cover voice target isolation/current labels, process-state matching, event settings, bundled tone IDs, backup validation, HID state handling and reminder policy. Manual UI tests supplement them; matcher tests alone cannot establish compatibility with a third-party release.

## Rules for the next iteration

1. Reproduce failures with the installed app version, slot package/voice setting, service binding and relevant local log events.
2. Inspect the target's current accessibility tree. Record IDs, descriptions, host package, visibility and bounds without committing private conversations or phone dumps.
3. Prefer stable resource IDs and scoped accessibility descriptions. Distinguish microphone dictation from full voice conversation. Avoid replacing scoped rules with generic words such as “voice” across arbitrary apps.
4. Keep launch cancellation, bounded traversal, finite retries and already-active detection. Preserve the separate behavior of each adapter unless the change is intentional.
5. Check cold start, warm start, already-in-call behavior, rotation and interrupted launch. Verify GPT, Doubao and Gemini after shared voice-flow changes. End test sessions after verification.
6. Update backup validation for new settings, preserve existing user preferences, and release listeners/timers on lifecycle exit.
7. Read and follow [CONTRIBUTING.md](../CONTRIBUTING.md) and [AGENTS.md](../AGENTS.md) before development. Keep both READMEs and all English/Chinese documentation pairs synchronized in the same commit. Never commit keys, tool caches, APKs, phone logs or backups.
8. Build with the existing signing key, increment version identifiers consistently, verify APK signing, and publish APKs as Release assets rather than source files. On Windows, the older resource packager may require an ASCII drive mapping for a non-ASCII checkout path.

Do not silently describe an intended design as implemented behavior. Update this context when matching rules or supported targets change.
