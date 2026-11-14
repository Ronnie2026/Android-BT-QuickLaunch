package com.benbenyu.btquicklaunch;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only APK entry point; runs the real offline player in the target app process. */
public final class VoiceInstrumentation extends Instrumentation {
  public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

  public void onStart() {
    HandlerThread worker = new HandlerThread("VoiceQA");
    worker.start();
    Handler handler = new Handler(worker.getLooper());
    OfflineVoiceAlerts voices = new OfflineVoiceAlerts(getTargetContext(), handler);
    Bundle results = new Bundle();
    try {
      for (String asset : new String[] {"keyboard-connected.wav", "keyboard-disconnected.wav",
          "keyboard-low-battery.wav", "phone-low-battery.wav"}) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        handler.post(() -> {
          final int expected = voices.completed + 1;
          voices.play(asset);
          handler.postDelayed(new Runnable() {
            boolean sawPlayer;
            public void run() {
              synchronized (voices) {
                sawPlayer |= voices.player != null;
                if (voices.player == null && voices.pending.isEmpty() && voices.active == null) {
                  if (!sawPlayer || voices.completed != expected) failure.set(new AssertionError("Playback did not complete: " + asset));
                  done.countDown();
                } else handler.postDelayed(this, 50);
              }
            }
          }, 50);
        });
        if (!done.await(12, TimeUnit.SECONDS)) throw new AssertionError("Playback timeout: " + asset);
        if (failure.get() != null) throw new AssertionError(failure.get());
        results.putString(asset, "PASS offline playback completed");
      }
      finish(Activity.RESULT_OK, results);
    } catch (Throwable error) {
      results.putString("error", error.toString());
      finish(Activity.RESULT_CANCELED, results);
    } finally {
      voices.close();
      worker.quitSafely();
    }
  }
}
