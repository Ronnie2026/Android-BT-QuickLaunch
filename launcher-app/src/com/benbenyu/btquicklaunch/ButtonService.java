package com.benbenyu.btquicklaunch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

public class ButtonService extends AccessibilityService {
  HidConnectionManager hid;
  static ButtonService instance;
  static long voiceUntil;
  static String voicePkg = "";
  final android.graphics.Rect voiceButtonBounds = new android.graphics.Rect();
  long voiceButtonStableSince, voiceButtonLastClick;
  int voiceClickAttempts;
  android.os.Handler handler = new android.os.Handler();

  public static void cancelVoice() {
    voiceUntil = 0;
    voicePkg = "";
    if (instance != null) {
      instance.handler.removeCallbacks(instance.retry);
      instance.resetVoiceClick();
    }
  }

  public static void requestVoice() {
    requestVoice("com.openai.chatgpt");
  }

  public static void requestVoice(String pkg) {
    cancelVoice();
    voicePkg = pkg == null ? "" : pkg;
    voiceUntil = android.os.SystemClock.elapsedRealtime() + 15000;
    AppLog.write("VOICE_REQUEST pkg=" + voicePkg, null);
    if (instance != null) instance.handler.post(instance::voice);
  }

  android.content.BroadcastReceiver bluetoothEvents;

  protected void onServiceConnected() {
    instance = this;
    if(hid==null){hid=new HidConnectionManager(this);hid.start();}
    if (bluetoothEvents == null) {
      bluetoothEvents =
          new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context c, Intent in) {android.bluetooth.BluetoothDevice device=in.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);if(device!=null&&!DeviceConfig.KEYBOARD_MAC.equals(device.getAddress()))return;String act=in.getAction();if(Intent.ACTION_BATTERY_CHANGED.equals(act)){handlePhoneBattery(in);return;}if(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION.equals(act)){if(hid!=null&&!ReminderAudio.ringing(ButtonService.this))hid.remindersChanged();return;}if("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED".equals(act)){int lvl=in.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL",-1);if(lvl>=0&&lvl<=100){if(hid!=null)hid.onBatteryLevel(lvl,"broadcast");}return;}if(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(act))onInterrupt();
              if (hid != null && (device != null || android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED.equals(act))) {
                hid.onBluetoothEvent(act, in.getIntExtra(
                    HidConnectionManager.ACTION_HID_STATE.equals(act)
                        ? android.bluetooth.BluetoothProfile.EXTRA_STATE
                        : android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1));
              }
              AppLog.write(
                  "BT_EVENT "
                      + in.getAction()
                      + " state="
                      + in.getIntExtra(HidConnectionManager.ACTION_HID_STATE.equals(act)
                          ? android.bluetooth.BluetoothProfile.EXTRA_STATE
                          : android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1),
                  null);
              try {
                new java.io.File(getFilesDir(), "hid-wake").createNewFile();
                new java.io.File(getFilesDir(), "hid-wake")
                    .setLastModified(System.currentTimeMillis());
              } catch (Exception ignored) {
              }
            }
          };
      android.content.IntentFilter filter =
          new android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
      filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
      filter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
      filter.addAction(HidConnectionManager.ACTION_HID_STATE);
      filter.addAction(Intent.ACTION_BATTERY_CHANGED);
      filter.addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION);
      filter.addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED");
      registerReceiver(bluetoothEvents, filter);
    }
  }

  static void remindersChanged() {
    ButtonService service = instance;
    if (service != null && service.hid != null) service.hid.remindersChanged();
  }

  static void refreshPhoneBattery() {
    ButtonService service = instance;
    if (service != null) service.handler.post(() -> {
      Intent battery = service.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      if (battery != null) service.handlePhoneBattery(battery);
    });
  }

  void handlePhoneBattery(Intent battery) {
    int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
    int scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
    if (hid != null && level >= 0 && scale > 0 && level <= scale)
      hid.onPhoneBattery((int) (level * 100L / scale),
          battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0);
  }

  public boolean onUnbind(Intent intent) {
    if (instance == this) instance = null;
    return super.onUnbind(intent);
  }

  public void onDestroy() {
    cancelVoice();
    if(hid!=null)hid.stop();
    actions.shutdownNow();
    instance = null;
    if (bluetoothEvents != null) unregisterReceiver(bluetoothEvents);
    super.onDestroy();
  }

  static boolean isBound(android.content.Context c) {
    if (instance == null) return false;
    try {
      android.view.accessibility.AccessibilityManager am =
          (android.view.accessibility.AccessibilityManager)
              c.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
      if (am == null) return true;
      java.util.List<android.accessibilityservice.AccessibilityServiceInfo> list =
          am.getEnabledAccessibilityServiceList(
              android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
      String pkg = c.getPackageName();
      for (android.accessibilityservice.AccessibilityServiceInfo info : list) {
        if (info.getResolveInfo() != null
            && info.getResolveInfo().serviceInfo != null
            && pkg.equals(info.getResolveInfo().serviceInfo.packageName)) {
          return true;
        }
      }
      return false;
    } catch (Exception ignored) {
      return instance != null;
    }
  }

  int visited;
  long lastImeDismiss;

  boolean isGeminiTarget() {
    return "com.google.android.apps.bard".equals(voicePkg);
  }

  boolean isDoubaoTarget() {
    return "com.larus.nova".equals(voicePkg);
  }

  boolean matchesVoiceApp(CharSequence pkg) {
    if (pkg == null) return false;
    String p = pkg.toString();
    if (isDoubaoTarget()) return p.equals("com.larus.nova");
    if (isGeminiTarget())
      return p.equals("com.google.android.googlequicksearchbox")
          || p.equals("com.google.android.apps.bard");
    return p.equals("com.openai.chatgpt");
  }

  boolean hasImeWindow() {
    try {
      for (android.view.accessibility.AccessibilityWindowInfo w : getWindows())
        if (w.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD)
          return true;
    } catch (Exception ignored) {
    }
    return false;
  }

  void voice() {
    visited = 0;
    long now = android.os.SystemClock.elapsedRealtime();
    if (now > voiceUntil) {
      if (!voicePkg.isEmpty()) AppLog.write("VOICE_TIMEOUT pkg=" + voicePkg + " attempts=" + voiceClickAttempts, null);
      cancelVoice();
      return;
    }
    if (isGeminiTarget() && hasImeWindow()) {
      if (now - lastImeDismiss > 800) {
        lastImeDismiss = now;
        AppLog.write("VOICE_DISMISS_IME pkg=" + voicePkg, null);
        performGlobalAction(GLOBAL_ACTION_BACK);
      }
      handler.removeCallbacks(retry);
      handler.postDelayed(retry, 450);
      return;
    }
    android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
    try {
      if (root != null && matchesVoiceApp(root.getPackageName())) {
        if (find(root, true)) {
          AppLog.write("VOICE_ALREADY_ACTIVE pkg=" + voicePkg, null);
          cancelVoice();
          return;
        }
        visited = 0;
        if ((!(isDoubaoTarget() || isGeminiTarget()) || (voiceClickAttempts < 3 && now - voiceButtonLastClick >= 1500))
            && find(root, false)) {
          if (isDoubaoTarget() || isGeminiTarget()) {
            handler.removeCallbacks(retry);
            handler.postDelayed(retry, 600);
            return;
          }
          AppLog.write("VOICE_CLICKED pkg=" + voicePkg, null);
          cancelVoice();
          return;
        }
      }
    } finally {
      if (root != null) root.recycle();
    }
    handler.removeCallbacks(retry);
    handler.postDelayed(retry, 600);
  }

  Runnable retry = () -> voice();

  void resetVoiceClick() {
    voiceButtonBounds.setEmpty();
    voiceButtonStableSince = 0;
    voiceButtonLastClick = 0;
    voiceClickAttempts = 0;
  }

  boolean isActiveDesc(String desc) {
    if (isDoubaoTarget())
      return desc.equals("挂断") || desc.equals("挂断电话") || desc.equals("结束通话")
          || desc.equals("挂断通话")
          || desc.equalsIgnoreCase("End call");
    if (isGeminiTarget()) {
      return desc.equals("结束 Live 模式")
          || desc.equals("结束Live模式")
          || desc.equals("开启/关闭摄像头")
          || desc.equals("打开/关闭麦克风")
          || desc.equalsIgnoreCase("End Live mode")
          || desc.equals("结束实时对话")
          || desc.equals("结束")
          || desc.equals("暂停")
          || desc.equals("开启摄像头")
          || desc.equals("打开摄像头")
          || desc.equalsIgnoreCase("End Live")
          || desc.equalsIgnoreCase("End")
          || desc.equalsIgnoreCase("Pause")
          || desc.equalsIgnoreCase("Turn on camera");
    }
    return desc.equals("结束")
        || desc.equals("关闭麦克风")
        || desc.equalsIgnoreCase("End")
        || desc.equalsIgnoreCase("Mute microphone");
  }

  /**
   * Match accessibility view resource IDs or content descriptions, not visible node text.
   * Bounds are read after identification for eligibility and gesture delivery; no fixed-coordinate
   * or screenshot/OCR recognition is used. See docs/PROJECT_CONTEXT.md for adapter limitations.
   */
  boolean isVoiceTrigger(android.view.accessibility.AccessibilityNodeInfo node, String desc) {
    if (isDoubaoTarget()) {
      String id = node.getViewIdResourceName();
      if ("com.larus.nova:id/real_time_call".equals(id)
          || "com.larus.nova:id/real_time_call_ic_container".equals(id)) return true;
      android.graphics.Rect bounds = new android.graphics.Rect();
      node.getBoundsInScreen(bounds);
      return desc.equals("打电话") && bounds.top >= 0
          && bounds.bottom <= getResources().getDisplayMetrics().heightPixels / 3;
    }
    if (isGeminiTarget()) {
      String id = node.getViewIdResourceName();
      if ("com.google.android.googlequicksearchbox:id/assistant_robin_input_voice_chat_button_compose"
          .equals(id)) return true;
      return desc.equals("打开 Gemini Live")
          || desc.equals("开启 Gemini Live")
          || desc.equalsIgnoreCase("Open Gemini Live")
          || desc.equalsIgnoreCase("Start Gemini Live")
          || desc.equalsIgnoreCase("Gemini Live");
    }
    return desc.equals("语音模式")
        || desc.equals("启动语音模式")
        || desc.equals("开始语音对话")
        || desc.equalsIgnoreCase("Voice mode")
        || desc.equalsIgnoreCase("Start voice mode")
        || desc.equals("语音")
        || desc.equalsIgnoreCase("Start voice conversation");
  }

  boolean find(android.view.accessibility.AccessibilityNodeInfo node, boolean active) {
    if (++visited > 512) return false;
    String desc = String.valueOf(node.getContentDescription());
    if (active && node.isVisibleToUser()
        && (isActiveDesc(desc) || (isDoubaoTarget()
            && ("com.larus.nova:id/realtime_call_root".equals(node.getViewIdResourceName())
                || "com.larus.nova:id/voice_call_hangup".equals(node.getViewIdResourceName()))))) return true;
    if (!active && isVoiceTrigger(node, desc) && node.isVisibleToUser()) {
      if (isDoubaoTarget() || isGeminiTarget()) return clickVoiceButton(node);
      android.view.accessibility.AccessibilityNodeInfo target = node;
      while (target != null) {
        if (target.isClickable() && target.performAction(16)) return true;
        target = target.getParent();
      }
      android.graphics.Rect r = new android.graphics.Rect();
      node.getBoundsInScreen(r);
      if (r.width() > 0 && r.height() > 0) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(r.centerX(), r.centerY());
        return dispatchGesture(
            new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build(),
            null,
            null);
      }
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
      if (child != null) {
        try {
          if (find(child, active)) return true;
        } finally {
          child.recycle();
        }
      }
    }
    return false;
  }

  boolean clickVoiceButton(android.view.accessibility.AccessibilityNodeInfo node) {
    android.graphics.Rect bounds = new android.graphics.Rect();
    node.getBoundsInScreen(bounds);
    if (bounds.isEmpty() || !node.isEnabled()) return false;
    long now = android.os.SystemClock.elapsedRealtime();
    // Rotation from the landscape launcher can expose a transient, stale button location.
    if (!bounds.equals(voiceButtonBounds)) {
      voiceButtonBounds.set(bounds);
      voiceButtonStableSince = now;
      return false;
    }
    if (now - voiceButtonStableSince < 350) return false;
    boolean accepted = false;
    // Alternate semantic click and a gesture at the currently observed button, never fixed coordinates.
    if (!isGeminiTarget() && voiceClickAttempts != 1) {
      android.view.accessibility.AccessibilityNodeInfo target = node;
      try {
        for (int depth = 0; target != null && depth < 5; depth++) {
          if (target.isEnabled() && target.isClickable()
              && target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
            accepted = true;
            break;
          }
          android.view.accessibility.AccessibilityNodeInfo parent = target.getParent();
          if (target != node) target.recycle();
          target = parent;
        }
      } finally {
        if (target != null && target != node) target.recycle();
      }
    }
    if (!accepted) {
      android.graphics.Path path = new android.graphics.Path();
      path.moveTo(bounds.centerX(), bounds.centerY());
      accepted = dispatchGesture(new GestureDescription.Builder()
          .addStroke(new GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null);
    }
    voiceButtonLastClick = now;
    voiceClickAttempts++;
    AppLog.write("VOICE_CLICK_ATTEMPT pkg=" + voicePkg + " attempt=" + voiceClickAttempts
        + " accepted=" + accepted + " bounds=" + bounds.toShortString(), null);
    return accepted;
  }

  Map<Integer, Set<Integer>> held = new HashMap<>();
  Map<Integer, long[]> taps = new HashMap<>();
  Set<Integer> chords = new HashSet<>();

  public void onAccessibilityEvent(AccessibilityEvent e) {
    if (voiceUntil > android.os.SystemClock.elapsedRealtime()) voice();
  }

  public void onInterrupt() {
    held.clear();
    taps.clear();
    chords.clear();
  }

  public boolean onKeyEvent(KeyEvent e) {
    if(e.getDevice()!=null&&e.getDevice().isExternal()&&e.getAction()==KeyEvent.ACTION_DOWN)IdlePower.externalInput();
    InputDevice d = e.getDevice();
    int k = e.getKeyCode() - KeyEvent.KEYCODE_1;
    if (d == null
        || !d.isExternal()
        || !"COIDEA KM".equalsIgnoreCase(d.getName())
        || k < 0
        || k > 2) return false;
    int id = e.getDeviceId();
    Set<Integer> keys = held.computeIfAbsent(id, x -> new HashSet<>());
    long[] last = taps.computeIfAbsent(id, x -> new long[3]);
    if (e.getAction() == KeyEvent.ACTION_DOWN) {
      if (e.getRepeatCount() > 0) return true;
      keys.add(k);
      if (keys.size() == 3 && !chords.contains(id)) {
        chords.add(id);
        Arrays.fill(last, 0);
        fire(3);
      }
      return true;
    }
    if (e.getAction() == KeyEvent.ACTION_UP) {
      keys.remove(k);
      if (chords.contains(id)) {
        if (keys.isEmpty()) chords.remove(id);
        return true;
      }
      if (e.isCanceled()) {
        last[k] = 0;
        return true;
      }
      long now = e.getEventTime();
      if (last[k] > 0 && now - last[k] < 450) {
        last[k] = 0;
        fire(k);
      } else last[k] = now;
      return true;
    }
    return true;
  }

  final java.util.concurrent.ThreadPoolExecutor actions=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
  void fire(int n) {
    AppLog.write("HID_ACTION " + n, null);
    actions.execute(
            () -> {
              try {
                java.lang.Process p =
                    new ProcessBuilder(
                            "su",
                            "-c",
                            "input keyevent KEYCODE_WAKEUP; am start -n"
                                + " com.benbenyu.btquicklaunch/.MainActivity --ei button_action "
                                + n)
                        .start();
                if(!p.waitFor(8,java.util.concurrent.TimeUnit.SECONDS)){p.destroy();throw new Exception("Root command timeout");}if(p.exitValue()!=0)throw new Exception("Root command failed");
              } catch (Exception ex) {
                startActivity(
                    new Intent(this, MainActivity.class)
                        .putExtra("button_action", n)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
              }
            });
  }
}
