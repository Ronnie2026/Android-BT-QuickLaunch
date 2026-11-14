package com.benbenyu.btquicklaunch;

import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

/** Device-side checks for voice target isolation and the observed Doubao phone control. */
public final class VoiceTargetChecks {
  public static void main(String[] args) {
    Looper.prepareMainLooper();
    ButtonService service = new ButtonService();
    AccessibilityNodeInfo phone = AccessibilityNodeInfo.obtain();
    try {
      ButtonService.voicePkg = "com.larus.nova";
      check(service.matchesVoiceApp("com.larus.nova"), "Doubao target accepted");
      check(!service.matchesVoiceApp("com.openai.chatgpt"), "other app rejected");
      phone.setViewIdResourceName("com.larus.nova:id/real_time_call");
      check(service.isVoiceTrigger(phone, ""), "observed top phone resource ID");
      phone.setViewIdResourceName(null);
      check(service.isVoiceTrigger(phone, "打电话"), "accessible description fallback");
      check(!service.isVoiceTrigger(phone, "文本输入"), "text input rejected");
      check(service.isActiveDesc("挂断"), "active call is recognized");
      ButtonService.voicePkg = "com.openai.chatgpt";
      check(service.matchesVoiceApp("com.openai.chatgpt"), "GPT target preserved");
      check(!service.matchesVoiceApp("com.larus.nova"), "Doubao rejected for GPT request");
      check(service.isVoiceTrigger(phone, "语音模式"), "GPT voice trigger preserved");
      ButtonService.voicePkg = "com.google.android.apps.bard";
      check(service.matchesVoiceApp("com.google.android.googlequicksearchbox"), "Gemini host preserved");
      check(service.isVoiceTrigger(phone, "打开 Gemini Live"), "Gemini voice trigger preserved");
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
