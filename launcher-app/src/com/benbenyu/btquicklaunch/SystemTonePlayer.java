package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Handler;
import java.util.Arrays;
import android.content.res.AssetFileDescriptor;
import java.util.List;

/** Curated AOSP notification sounds bundled in the APK; no system provider or external files. */
final class SystemTonePlayer {
  static final class Entry {
    final String title, id;
    Entry(String title, String id) { this.title = title; this.id = id; }
  }

  static final List<Entry> CATALOG = java.util.Collections.unmodifiableList(Arrays.asList(
      new Entry("系统短音 · Argon（默认）", ""), new Entry("Adara", "adara"),
      new Entry("Alya", "alya"), new Entry("Ceres", "ceres"), new Entry("Cobalt", "cobalt"),
      new Entry("Helium", "helium"), new Entry("Procyon", "procyon"), new Entry("Tejat", "tejat")));

  static boolean allowed(String id) {
    for (Entry tone : CATALOG) if (tone.id.equals(id)) return true;
    return false;
  }

  static String asset(String id) {
    return "system-tones/" + (allowed(id) && !id.isEmpty() ? id : "argon") + ".ogg";
  }

  static List<Entry> list(Context ignored) { return CATALOG; }

  static String title(Context ignored, String id) {
    for (Entry tone : CATALOG) if (tone.id.equals(id)) return tone.title;
    return CATALOG.get(0).title;
  }

  final Context context;
  final Handler handler;
  MediaPlayer player;
  ToneGenerator generated;
  boolean closed;
  int started;
  final Runnable timeout = this::stop;

  SystemTonePlayer(Context context, Handler handler) { this.context = context; this.handler = handler; }

  synchronized void play(String id, int fallbackTone) {
    if (closed) return;
    stop();
    if (!ReminderAudio.ringing(context)) { ReminderAudio.vibrate(context); return; }
    final String assetName = asset(id);
    try (AssetFileDescriptor file = context.getAssets().openFd(assetName)) {
      MediaPlayer next = new MediaPlayer();
      player = next;
      next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
      next.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
      next.setOnPreparedListener(ready -> {
        synchronized (SystemTonePlayer.this) {
          if (closed || player != ready) return;
          if (!ReminderAudio.ringing(context)) { stop(); ReminderAudio.vibrate(context); return; }
          try { ready.start(); started++; AppLog.write("SYSTEM_TONE started asset=" + assetName, null); }
          catch (RuntimeException error) { fallback(ready, fallbackTone, error); }
        }
      });
      next.setOnCompletionListener(done -> {
        synchronized (SystemTonePlayer.this) { if (player == done) stop(); }
      });
      next.setOnErrorListener((failed, what, extra) -> {
        fallback(failed, fallbackTone, new IllegalStateException("Media error " + what + "/" + extra));
        return true;
      });
      next.prepareAsync();
      handler.postDelayed(timeout, 5000);
    } catch (Exception error) { fallback(player, fallbackTone, error); }
  }

  synchronized void fallback(MediaPlayer expected, int tone, Exception error) {
    if (closed || player != expected) return;
    AppLog.write("SYSTEM_TONE_FALLBACK", error);
    stop();
    if (ReminderAudio.ringing(context)) builtIn(tone);
  }

  synchronized void builtIn(int tone) {
    try {
      generated = new ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80);
      generated.startTone(tone, 180);
      handler.postDelayed(timeout, 500);
      AppLog.write("SYSTEM_TONE built_in=" + tone, null);
    } catch (RuntimeException error) { stop(); AppLog.write("SYSTEM_TONE_FAILED", error); }
  }

  synchronized void stop() {
    handler.removeCallbacks(timeout);
    if (player != null) {
      MediaPlayer previous = player;
      player = null;
      previous.setOnPreparedListener(null);
      previous.setOnCompletionListener(null);
      previous.setOnErrorListener(null);
      previous.release();
    }
    if (generated != null) { generated.release(); generated = null; }
  }

  synchronized void close() { closed = true; stop(); }
}
