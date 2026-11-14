package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs on device; decodes packaged audio silently and uses only isolated QA preferences. */
public final class ReminderChecks {
  public static void main(String[] args) throws Exception {
    Looper.prepareMainLooper();
    Class<?> activityThread = Class.forName("android.app.ActivityThread");
    Object thread = activityThread.getMethod("systemMain").invoke(null);
    Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
    Context app = system.createPackageContext("com.benbenyu.btquicklaunch", 0);
    SharedPreferences prefs = app.getSharedPreferences("reminder_qa", 0);
    prefs.edit().clear().commit();
    HidConnectionManager manager = new HidConnectionManager(new ContextWrapper(app) {
      public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
    });
    try {
      check(ReminderAudio.allowsSound(android.media.AudioManager.RINGER_MODE_NORMAL), "ring mode allows sound");
      check(!ReminderAudio.allowsSound(android.media.AudioManager.RINGER_MODE_SILENT), "silent mode vibrates only");
      check(!ReminderAudio.allowsSound(android.media.AudioManager.RINGER_MODE_VIBRATE), "vibrate mode vibrates only");
      System.out.println("PASS ring, silent and vibrate reminder policy");
      manager.worker.start();
      manager.h = new Handler(manager.worker.getLooper());
      manager.started = true;
      AtomicInteger warnings = new AtomicInteger();
      manager.phoneWarning = warnings::incrementAndGet;
      check(manager.reminderEnabled("reminder_phone_low"), "reminders enabled by default");
      manager.onPhoneBattery(30, false);
      drain(manager);
      check(warnings.get() == 0, "30 percent does not warn");
      manager.onPhoneBattery(29, true);
      drain(manager);
      check(warnings.get() == 0, "charging does not warn");
      manager.onPhoneBattery(29, false);
      manager.onPhoneBattery(28, false);
      manager.onPhoneBattery(-1, false);
      drain(manager);
      check(warnings.get() == 1, "low battery warns once");
      manager.onPhoneBattery(30, false);
      manager.onPhoneBattery(29, false);
      drain(manager);
      check(warnings.get() == 2, "recovery rearms warning");
      prefs.edit().putBoolean("phone_low_announced", false).putBoolean("reminder_phone_low", false).commit();
      manager.onPhoneBattery(20, false);
      drain(manager);
      check(warnings.get() == 2, "individual switch suppresses reminder");
      prefs.edit().putBoolean("reminder_phone_low", true).putBoolean("reminder_enabled", false).commit();
      manager.onPhoneBattery(20, false);
      drain(manager);
      check(warnings.get() == 2, "master switch suppresses reminder");
      prefs.edit().putBoolean("reminder_enabled", true).commit();
      manager.onPhoneBattery(20, false);
      drain(manager);
      check(warnings.get() == 3, "enabling allows pending low warning");
      System.out.println("PASS phone threshold, charging, deduplication, rearming and switches");

      FutureTask<Void> queue = new FutureTask<>(() -> {
        OfflineVoiceAlerts voices = new OfflineVoiceAlerts(app, manager.h);
        voices.play("keyboard-connected.wav");
        voices.play("keyboard-connected.wav");
        voices.play("keyboard-low-battery.wav");
        check(voices.pending.size() == 2, "voice queue deduplicates");
        voices.play("keyboard-disconnected.wav");
        check(voices.pending.size() == 2 && voices.pending.peekFirst().equals("keyboard-disconnected.wav"), "newest connection event has priority");
        voices.close();
        check(voices.pending.isEmpty() && voices.player == null, "voice queue released");
        voices.play("keyboard-disconnected.wav");
        check(voices.pending.isEmpty(), "closed player ignores requests");
        return null;
      });
      manager.h.post(queue);
      queue.get(2, TimeUnit.SECONDS);
      for (String asset : new String[] {"keyboard-connected.wav", "keyboard-disconnected.wav",
          "keyboard-low-battery.wav", "phone-low-battery.wav"}) {
        MediaExtractor player = new MediaExtractor();
        try (AssetFileDescriptor file = app.getAssets().openFd(asset)) {
          player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
          check(player.getTrackCount() > 0, "audio track: " + asset);
          MediaFormat format = player.getTrackFormat(0);
          check(format.getString(MediaFormat.KEY_MIME).startsWith("audio/"), "audio MIME: " + asset);
          long duration = format.getLong(MediaFormat.KEY_DURATION) / 1000;
          check(duration > 0, "audio duration: " + asset);
          System.out.println("PASS offline audio parse " + asset + " duration=" + duration + "ms");
        } finally { player.release(); }
      }
      for (SystemTonePlayer.Entry tone : SystemTonePlayer.list(app)) {
        MediaExtractor decoder = new MediaExtractor();
        try (AssetFileDescriptor file = app.getAssets().openFd(SystemTonePlayer.asset(tone.id))) {
          decoder.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
          check(decoder.getTrackCount() > 0, "bundled tone audio track");
          MediaFormat format = decoder.getTrackFormat(0);
          check(format.getString(MediaFormat.KEY_MIME).startsWith("audio/"), "bundled tone MIME");
          check(format.getLong(MediaFormat.KEY_DURATION) > 0, "bundled tone duration");
          System.out.println("PASS bundled system tone " + tone.title);
        } finally { decoder.release(); }
      }
      System.out.println("PASS bounded voice queue and shutdown");
    } finally {
      manager.stop();
      prefs.edit().clear().commit();
    }
    System.exit(0);
  }

  static void drain(HidConnectionManager manager) throws Exception {
    FutureTask<Void> barrier = new FutureTask<>(() -> null);
    manager.h.post(barrier);
    barrier.get(2, TimeUnit.SECONDS);
  }

  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
