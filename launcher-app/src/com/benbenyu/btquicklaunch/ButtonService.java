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
  android.os.Handler handler = new android.os.Handler();

  public static void cancelVoice() {
    voiceUntil = 0;
    if (instance != null) instance.handler.removeCallbacks(instance.retry);
  }

  public static void requestVoice() {
    voiceUntil = android.os.SystemClock.elapsedRealtime() + 15000;
    if (instance != null) instance.handler.post(instance::voice);
  }

  android.content.BroadcastReceiver bluetoothEvents;

  protected void onServiceConnected() {
    instance = this;
    if(hid==null){hid=new HidConnectionManager(this);hid.start();}
    if (bluetoothEvents == null) {
      bluetoothEvents =
          new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context c, Intent in) {android.bluetooth.BluetoothDevice device=in.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);if(device!=null&&!DeviceConfig.KEYBOARD_MAC.equals(device.getAddress()))return;String act=in.getAction();if(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(act))onInterrupt();
              if(hid!=null){if(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED.equals(act))hid.onAclConnected();else hid.signal();}
              AppLog.write(
                  "BT_EVENT "
                      + in.getAction()
                      + " state="
                      + in.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1),
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
      registerReceiver(bluetoothEvents, filter);
    }
  }

  public void onDestroy() {
    cancelVoice();
    if(hid!=null)hid.stop();
    actions.shutdownNow();
    instance = null;
    if (bluetoothEvents != null) unregisterReceiver(bluetoothEvents);
    super.onDestroy();
  }

  int visited;
  void voice() {
    visited=0;
    if (android.os.SystemClock.elapsedRealtime() > voiceUntil) return;
    android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
    try{if (root != null && "com.openai.chatgpt".contentEquals(root.getPackageName())) {
      if (find(root, true)) {
        voiceUntil = 0;
        return;
      }
      if (find(root, false)) {
        cancelVoice();
        return;
      }
    }
    }finally{if(root!=null)root.recycle();}
    handler.removeCallbacks(retry);
    handler.postDelayed(retry, 600);
  }

  Runnable retry = () -> voice();

  boolean find(android.view.accessibility.AccessibilityNodeInfo node, boolean active) {
    if(++visited>512)return false;
    String desc = String.valueOf(node.getContentDescription());
    if (active
        && (desc.equals("结束")
            || desc.equals("关闭麦克风")
            || desc.equalsIgnoreCase("End")
            || desc.equalsIgnoreCase("Mute microphone"))) return true;
    if (!active
        && (desc.equals("语音模式")
            || desc.equals("启动语音模式")
            || desc.equals("开始语音对话")
            || desc.equalsIgnoreCase("Voice mode")
            || desc.equalsIgnoreCase("Start voice mode")
            || desc.equals("语音")
            || desc.equalsIgnoreCase("Start voice conversation"))
        && node.isVisibleToUser()) {
      android.view.accessibility.AccessibilityNodeInfo target = node;
      while (target != null) {
        if (target.isClickable() && target.performAction(16)) return true;
        target = target.getParent();
      }
      android.graphics.Rect r = new android.graphics.Rect();
      node.getBoundsInScreen(r);
      android.graphics.Path path = new android.graphics.Path();
      path.moveTo(r.centerX(), r.centerY());
      dispatchGesture(
          new GestureDescription.Builder()
              .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
              .build(),
          null,
          null);
      return true;
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
      if(child!=null){try{if(find(child,active))return true;}finally{child.recycle();}}
    }
    return false;
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
