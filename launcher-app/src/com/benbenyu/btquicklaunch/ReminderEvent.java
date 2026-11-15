package com.benbenyu.btquicklaunch;

import android.content.SharedPreferences;

/** One definition per event, shared by settings, previews and real playback. */
final class ReminderEvent {
  static final ReminderEvent CONNECTED = new ReminderEvent("reminder_keyboard_connected", "键盘已连接", "keyboard-connected.wav", "键盘已连接。");
  static final ReminderEvent DISCONNECTED = new ReminderEvent("reminder_keyboard_disconnected", "键盘断开", "keyboard-disconnected.wav", "键盘断开。");
  static final ReminderEvent KEYBOARD_LOW = new ReminderEvent("reminder_keyboard_low", "键盘电量过低", "keyboard-low-battery.wav", "蓝牙键盘电量过低，请及时充电。");
  static final ReminderEvent PHONE_LOW = new ReminderEvent("reminder_phone_low", "手机电量过低", "phone-low-battery.wav", "手机电量过低，请及时充电。");
  static final ReminderEvent CLEAN = new ReminderEvent("clean_success_sound", "清理后台成功", null, null);
  static final ReminderEvent[] ALL = {CONNECTED, DISCONNECTED, KEYBOARD_LOW, PHONE_LOW, CLEAN};
  final String key, title, asset, speech;

  ReminderEvent(String key, String title, String asset, String speech) {
    this.key = key; this.title = title; this.asset = asset; this.speech = speech;
  }

  static ReminderEvent find(String key) {
    for (ReminderEvent event : ALL) if (event.key.equals(key)) return event;
    throw new IllegalArgumentException("Unknown reminder event");
  }

  boolean enabled(SharedPreferences p) {
    return p.getBoolean(key, true) && (this == CLEAN || p.getBoolean("reminder_enabled", true));
  }

  String mediaKey() { return key + "_media"; }
  String toneKey() { return this == CLEAN ? "clean_success_tone" : key + "_tone"; }
  String media(SharedPreferences p) {
    return this == CLEAN ? "tone" : p.getString(mediaKey(), p.getString("reminder_media", "voice"));
  }
  String tone(SharedPreferences p) {
    return p.getString(toneKey(), this == CLEAN ? "" : p.getString("reminder_tone", ""));
  }
  String summary(SharedPreferences p) {
    if (!enabled(p)) return this == CLEAN ? "提示音关闭 · 仍显示派大星" :
        !p.getBoolean("reminder_enabled", true) ? "总开关已关闭" : "已关闭";
    if (media(p).equals("voice")) return "语音：" + speech;
    return "提示音：" + SystemTonePlayer.title(null, tone(p));
  }
}
