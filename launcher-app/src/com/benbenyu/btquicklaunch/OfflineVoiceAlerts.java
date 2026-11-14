package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import java.util.ArrayDeque;

/** Bounded, serialized offline playback. No TTS engine or network is required. */
final class OfflineVoiceAlerts {
  final Context context;
  final Handler handler;
  final ArrayDeque<String> pending = new ArrayDeque<>();
  MediaPlayer player;
  String active;
  boolean closed;
  int completed;
  final Runnable timeout = () -> finish(player);
  final Runnable begin = this::beginPlayback;

  OfflineVoiceAlerts(Context context, Handler handler) {
    this.context = context;
    this.handler = handler;
  }

  synchronized void play(String asset) {
    if (closed || asset.equals(active) || pending.contains(asset)) return;
    if (!asset.matches("keyboard-connected.wav|keyboard-disconnected.wav|keyboard-low-battery.wav|phone-low-battery.wav"))
      throw new IllegalArgumentException("Unknown voice asset");
    boolean connectionEvent = asset.equals("keyboard-connected.wav") || asset.equals("keyboard-disconnected.wav");
    if (connectionEvent) {
      pending.remove("keyboard-connected.wav");
      pending.remove("keyboard-disconnected.wav");
      if (player != null) {
        // Connection feedback must not wait behind several low-battery sentences.
        if (active != null && active.endsWith("low-battery.wav") && !pending.contains(active))
          pending.addLast(active);
        pending.addFirst(asset);
        finish(player);
        return;
      }
    }
    if (pending.size() == 4) pending.removeFirst();
    if (connectionEvent) pending.addFirst(asset);
    else pending.addLast(asset);
    if (player == null) {
      handler.removeCallbacks(begin);
      handler.postDelayed(begin, 350);
    }
  }

  synchronized void beginPlayback() {
    if (closed || player != null || pending.isEmpty()) return;
    if (!ReminderAudio.ringing(context)) {
      pending.clear();
      ReminderAudio.vibrate(context);
      return;
    }
    active = pending.removeFirst();
    try (AssetFileDescriptor asset = context.getAssets().openFd(active)) {
      MediaPlayer next = new MediaPlayer();
      player = next;
      next.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
      next.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
      next.setOnPreparedListener(ready -> {
        synchronized (OfflineVoiceAlerts.this) {
          if (closed || player != ready) return;
          try {
            ready.start();
            AppLog.write("VOICE_ALERT started asset=" + active, null);
          } catch (RuntimeException error) {
            AppLog.write("VOICE_ALERT_START_FAILED", error);
            finish(ready);
          }
        }
      });
      next.setOnCompletionListener(done -> {
        synchronized (OfflineVoiceAlerts.this) {
          if (player != done) return;
          completed++;
          AppLog.write("VOICE_ALERT completed asset=" + active, null);
          finish(done);
        }
      });
      next.setOnErrorListener((failed, what, extra) -> {
        AppLog.write("VOICE_ALERT error=" + what + "/" + extra, null);
        finish(failed);
        return true;
      });
      next.prepareAsync();
      handler.postDelayed(timeout, 15000);
    } catch (Exception error) {
      AppLog.write("VOICE_ALERT_FAILED asset=" + active, error);
      finish(player);
    }
  }

  synchronized void finish(MediaPlayer expected) {
    if (expected != player) return;
    handler.removeCallbacks(timeout);
    if (player != null) {
      MediaPlayer previous = player;
      player = null;
      previous.setOnPreparedListener(null);
      previous.setOnCompletionListener(null);
      previous.setOnErrorListener(null);
      previous.release();
    }
    active = null;
    if (!closed && !pending.isEmpty()) handler.post(begin);
  }

  synchronized void close() {
    closed = true;
    pending.clear();
    handler.removeCallbacks(begin);
    finish(player);
  }
}
