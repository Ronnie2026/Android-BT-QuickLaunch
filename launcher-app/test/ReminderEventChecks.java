package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

public final class ReminderEventChecks {
  public static void main(String[] args) throws Exception {
    Looper.prepareMainLooper();
    Class<?> at = Class.forName("android.app.ActivityThread");
    Object thread = at.getMethod("systemMain").invoke(null);
    Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
    SharedPreferences p = system.createPackageContext("com.benbenyu.btquicklaunch", 0)
        .getSharedPreferences("event_qa", 0);
    try {
      p.edit().clear().putString("reminder_media", "tone").putString("reminder_tone", "alya").commit();
      check(ReminderEvent.CONNECTED.media(p).equals("tone"), "legacy media preserved");
      check(ReminderEvent.DISCONNECTED.tone(p).equals("alya"), "legacy sound preserved");
      p.edit().putString(ReminderEvent.CONNECTED.mediaKey(), "tone")
          .putString(ReminderEvent.CONNECTED.toneKey(), "helium")
          .putString(ReminderEvent.DISCONNECTED.mediaKey(), "voice")
          .putBoolean(ReminderEvent.PHONE_LOW.key, false).commit();
      check(ReminderEvent.CONNECTED.tone(p).equals("helium"), "connection sound independent");
      check(ReminderEvent.DISCONNECTED.media(p).equals("voice"), "disconnection voice independent");
      check(ReminderEvent.KEYBOARD_LOW.tone(p).equals("alya"), "other sound unchanged");
      check(!ReminderEvent.PHONE_LOW.enabled(p), "individual off");
      check(ReminderEvent.DISCONNECTED.summary(p).contains("键盘断开"), "summary matches spoken event");
      check(ReminderEvent.CONNECTED.summary(p).contains("Helium"), "summary matches selected sound");
      check(ReminderEvent.find("reminder_keyboard_connected").asset.equals("keyboard-connected.wav"), "actual connection asset");
      check(ReminderEvent.find("reminder_keyboard_disconnected").asset.equals("keyboard-disconnected.wav"), "actual disconnection asset");
      p.edit().putBoolean("reminder_enabled", false).commit();
      check(!ReminderEvent.CONNECTED.enabled(p), "master off");
      check(ReminderEvent.CLEAN.enabled(p), "cleanup independent of Bluetooth master");
      check(ReminderEvent.CLEAN.media(p).equals("tone"), "cleanup remains tone only");
      System.out.println("PASS per-event sound/voice isolation, matching summaries/assets and legacy preferences");
    } finally { p.edit().clear().commit(); }
    System.exit(0);
  }

  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
