package com.benbenyu.btquicklaunch;

import android.app.Activity;
import android.app.Instrumentation;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only APK: enumerates and auditions real bundled sounds in a registered app process. */
public final class SystemToneInstrumentation extends Instrumentation {
  public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

  public void onStart() {
    HandlerThread worker = new HandlerThread("SystemToneQA");
    worker.start();
    Handler handler = new Handler(worker.getLooper());
    SystemTonePlayer player = new SystemTonePlayer(getTargetContext(), handler);
    Bundle results = new Bundle();
    try {
      List<SystemTonePlayer.Entry> entries = SystemTonePlayer.list(getTargetContext());
      if (entries.size() < 2) throw new AssertionError("No bundled system tones found");
      SystemTonePlayer.Entry selected = entries.get(1);
      for (SystemTonePlayer.Entry tone : entries) {
        if (!SystemTonePlayer.allowed(tone.id)) throw new AssertionError("External tone in list");
        if (tone.title.equalsIgnoreCase("Adara")) selected = tone;
      }
      final String uri = selected.id;
      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      handler.post(() -> {
        player.play(uri, ToneGenerator.TONE_PROP_ACK);
        handler.postDelayed(new Runnable() {
          public void run() {
            synchronized (player) {
              if (player.player == null && player.generated == null) {
                if (player.started != 1) failure.set(new AssertionError("Selected system tone did not start"));
                player.play("file:///sdcard/not-allowed.mp3", ToneGenerator.TONE_PROP_ACK);
                if (!SystemTonePlayer.asset("file:///sdcard/not-allowed.mp3").equals("system-tones/argon.ogg"))
                  failure.set(new AssertionError("Unsupported resource did not resolve to bundled default"));
                player.close();
                if (player.player != null || player.generated != null)
                  failure.set(new AssertionError("Playback resources were not released"));
                done.countDown();
              } else handler.postDelayed(this, 50);
            }
          }
        }, 50);
      });
      if (!done.await(8, TimeUnit.SECONDS)) throw new AssertionError("Tone playback timeout");
      if (failure.get() != null) throw new AssertionError(failure.get());
      results.putString("system_tones", "PASS " + entries.size() + " bundled sounds; selected playback, fallback and cleanup");
      finish(Activity.RESULT_OK, results);
    } catch (Throwable error) {
      results.putString("error", error.toString());
      finish(Activity.RESULT_CANCELED, results);
    } finally { player.close(); worker.quitSafely(); }
  }
}
