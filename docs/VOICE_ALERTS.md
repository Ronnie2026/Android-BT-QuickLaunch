# Offline reminders

[简体中文](VOICE_ALERTS.zh-CN.md)

All four WAV clips are bundled in `launcher-app/assets/`. They were synthesized locally with the Windows Microsoft Huihui Desktop Chinese voice. Playback needs neither an Android TTS engine nor network access.

| Asset | Spoken text |
|---|---|
| `keyboard-connected.wav` | 键盘已连接。 |
| `keyboard-disconnected.wav` | 键盘断开。 |
| `keyboard-low-battery.wav` | 蓝牙键盘电量过低，请及时充电。 |
| `phone-low-battery.wav` | 手机电量过低，请及时充电。 |

Settings are stored in the `home` preferences. `reminder_enabled` and the four `reminder_keyboard_connected`, `reminder_keyboard_disconnected`, `reminder_keyboard_low`, and `reminder_phone_low` switches default to true. `reminder_media` defaults to `voice`; the other supported value is `tone`. These settings are included in configuration backups.

Audio is allowed only in normal ring mode. Silent and vibrate modes use a 220 ms vibration instead. Speech uses media volume; tone reminders use notification volume; the app never changes system volume or ring mode. Changing the reminder settings or leaving ring mode cancels queued/active audio.

Keyboard low-battery reminders use a strict threshold of less than 30%. Connection warnings wait for a valid battery reading; disconnect warnings use the last known valid reading. Duplicate state/battery events do not repeat a warning. Phone warnings are suppressed while charging and repeat only after the battery first recovers to at least 30%.

HID and ACL broadcasts drive connection feedback. One-second polling is a fallback. A powered-off keyboard can remain apparently connected until the Bluetooth controller's link timeout; a device test showed about 10 seconds from power-off, despite only 410 ms between the ACL disconnect broadcast and speech start. No 3–5 second physical power-off detection guarantee is made.

`OfflineVoiceAlerts` serializes playback, prioritizes the latest connection event, limits queued clips, and releases playback resources on completion, error, timeout and shutdown. GATT battery reads have a 10-second timeout and ignore stale callbacks after disconnect.

Device checks: `HidStateChecks`, `DeviceChecks`, and `ReminderChecks`. `VoiceInstrumentation` belongs only in a separately signed test APK and plays each clip inside the target application process. After instrumentation, remove the test APK, restore the enabled accessibility service if needed, and verify that it appears under **Bound services**, with no entry under **Crashed services**. A stale activity-service ConnectionRecord alone does not establish service health.

## Selectable bundled system sounds

Eight unchanged AOSP notification OGG files are packaged in assets/system-tones. The selection dialog lists this fixed catalog, supports preview/save/cancel, and never queries MediaStore or offers external imports. The source revision and Apache 2.0 license are in THIRD_PARTY_NOTICES.md and bundled with the APK.

Preferences reminder_tone and clean_success_tone store only stable catalog IDs (empty/default, adara, alya, ceres, cobalt, helium, procyon, tejat). Backups reject all other values, including system/content URIs and external paths. Preview stops on dialog dismissal, navigation and activity pause. Playback is bounded to five seconds and uses a built-in short tone if decoding fails.

## Event-oriented settings (v1.6.0)

ReminderEvent is the shared definition for the settings title, summary, spoken asset and production event. The four Bluetooth/battery events independently store <event-key>_media and <event-key>_tone. Existing reminder_media/reminder_tone preferences remain compatibility defaults. Cleanup keeps its independent clean_success_sound and clean_success_tone settings.

Each event opens one draft editor: Off, Voice (when supported), or Tone; a spoken-text label or bundled-sound dropdown; one Preview button; Save and Cancel. Preview uses the draft without changing preferences. Saving updates the actual event and its summary. Cancel discards edits. The Bluetooth/battery master does not disable cleanup feedback.
