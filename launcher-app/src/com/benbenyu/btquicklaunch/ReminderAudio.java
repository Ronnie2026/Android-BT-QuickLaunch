package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.media.AudioManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Honor the hardware ringer mode without changing the user's volume or sound settings. */
final class ReminderAudio {
  static boolean ringing(Context context) {
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    return audio != null && allowsSound(audio.getRingerMode());
  }

  static boolean allowsSound(int mode) { return mode == AudioManager.RINGER_MODE_NORMAL; }

  static void vibrate(Context context) {
    try {
      Vibrator motor = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
      if (motor != null && motor.hasVibrator())
        motor.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE));
      AppLog.write("REMINDER vibration_only", null);
    } catch (RuntimeException error) {
      AppLog.write("REMINDER_VIBRATION_FAILED", error);
    }
  }
}
