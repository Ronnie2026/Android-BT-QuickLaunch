package com.benbenyu.btquicklaunch;

import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

/** Device-side checks for voice target isolation and the observed Doubao phone control. */
public final class VoiceTargetChecks {
  public static void main(String[] args) {
    Looper.prepareMainLooper();
    ButtonService service = new ButtonService() {
      public android.content.res.Resources getResources() {
        final android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        metrics.setToDefaults();
        metrics.widthPixels = 1080;
        metrics.heightPixels = 2400;
        return new android.content.res.Resources(android.content.res.Resources.getSystem().getAssets(),
            metrics, new android.content.res.Configuration()) {
          public android.util.DisplayMetrics getDisplayMetrics() { return metrics; }
        };
      }
    };
    AccessibilityNodeInfo phone = AccessibilityNodeInfo.obtain();
    try {
      ButtonService.voicePkg = "com.larus.nova";
      check(service.matchesVoiceApp("com.larus.nova"), "Doubao target accepted");
      check(!service.matchesVoiceApp("com.openai.chatgpt"), "other app rejected");
      phone.setViewIdResourceName("com.larus.nova:id/real_time_call");
      check(service.isVoiceTrigger(phone, ""), "observed top phone resource ID");
      phone.setViewIdResourceName(null);
      phone.setBoundsInScreen(new android.graphics.Rect(20, 20, 100, 100));
      check(service.isVoiceTrigger(phone, "打电话"), "accessible description fallback");
      phone.setBoundsInScreen(new android.graphics.Rect(20, 10000, 100, 10100));
      check(!service.isVoiceTrigger(phone, "打电话"), "bottom suggestion is not the top phone control");
      check(!service.isVoiceTrigger(phone, "文本输入"), "text input rejected");
      check(service.isActiveDesc("挂断"), "active call is recognized");
      check(service.isActiveDesc("挂断通话"), "current Doubao hangup label recognized");
      ButtonService.voicePkg = "com.openai.chatgpt";
      check(service.matchesVoiceApp("com.openai.chatgpt"), "GPT target preserved");
      check(!service.matchesVoiceApp("com.larus.nova"), "Doubao rejected for GPT request");
      check(service.isVoiceTrigger(phone, "语音模式"), "GPT voice trigger preserved");
      ButtonService.voicePkg = "com.google.android.apps.bard";
      check(service.matchesVoiceApp("com.google.android.googlequicksearchbox"), "Gemini host preserved");
      check(service.isVoiceTrigger(phone, "打开 Gemini Live"), "Gemini voice trigger preserved");
      check(service.isActiveDesc("结束 Live 模式"), "current Gemini Live end label recognized");
      check(service.isActiveDesc("打开/关闭麦克风"), "current Gemini Live microphone recognized");
      System.out.println("PASS Doubao phone target, package isolation, GPT and Gemini voice triggers");
    } finally {
      phone.recycle();
      ButtonService.voicePkg = "";
      service.handler.removeCallbacksAndMessages(null);
      service.actions.shutdownNow();
    }
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
