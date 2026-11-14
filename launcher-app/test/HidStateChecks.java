package com.benbenyu.btquicklaunch;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Device-side regression checks; no real connections, settings writes, or audible tones. */
public final class HidStateChecks {
  public static void main(String[] args) throws Exception {
    Looper.prepareMainLooper();
    SharedPreferences silent = (SharedPreferences) Proxy.newProxyInstance(
        SharedPreferences.class.getClassLoader(), new Class<?>[] {SharedPreferences.class},
        (proxy, method, values) -> {
          if (method.getName().equals("getBoolean")) return false;
          throw new UnsupportedOperationException(method.getName());
        });
    HidConnectionManager manager = new HidConnectionManager(new ContextWrapper(null) {
      public SharedPreferences getSharedPreferences(String name, int mode) { return silent; }
    });
    try {
      manager.updateState(BluetoothProfile.STATE_CONNECTED, "test_initial");
      check(manager.announcedConnected, "initial connected state");
      manager.updateState(BluetoothProfile.STATE_CONNECTING, "test_intermediate");
      manager.updateState(BluetoothProfile.STATE_DISCONNECTED, "test_disconnect");
      check(!manager.announcedConnected, "disconnect survives intermediate connecting state");
      manager.updateState(BluetoothProfile.STATE_CONNECTED, "test_reconnect");
      check(manager.announcedConnected, "reconnect");
      manager.updateState(BluetoothProfile.STATE_DISCONNECTING, "test_intermediate");
      manager.updateState(BluetoothProfile.STATE_DISCONNECTED, "test_disconnect");
      check(!manager.announcedConnected, "disconnecting transition");

      manager.worker.start();
      manager.h = new Handler(manager.worker.getLooper());
      manager.started = true;
      manager.binding = true; // Prevent test polling from binding a real HID service.
      CountDownLatch occupied = new CountDownLatch(1);
      manager.connector.execute(() -> {
        occupied.countDown();
        try { new CountDownLatch(1).await(); }
        catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
      });
      check(occupied.await(2, TimeUnit.SECONDS), "connector occupied");
      manager.updateState(BluetoothProfile.STATE_CONNECTED, "test_before_acl");
      long began = android.os.SystemClock.elapsedRealtime();
      manager.onBluetoothEvent(BluetoothDevice.ACTION_ACL_DISCONNECTED, -1);
      FutureTask<Void> checked = new FutureTask<>(() -> {
        check(HidConnectionManager.currentState == BluetoothProfile.STATE_DISCONNECTED,
            "ACL disconnect handled while connector is blocked");
        check(!manager.announcedConnected, "disconnect announcement recorded");
        return null;
      });
      manager.h.post(checked);
      checked.get(1500, TimeUnit.MILLISECONDS);
      System.out.println("PASS ACL disconnect handled in "
          + (android.os.SystemClock.elapsedRealtime() - began) + "ms with blocked connector");
      manager.onBluetoothEvent(HidConnectionManager.ACTION_HID_STATE,
          BluetoothProfile.STATE_CONNECTED);
      FutureTask<Void> reconnected = new FutureTask<>(() -> {
        check(manager.announcedConnected, "HID connection broadcast handled immediately");
        return null;
      });
      manager.h.post(reconnected);
      reconnected.get(1500, TimeUnit.MILLISECONDS);
      System.out.println("PASS initial state, intermediate states, disconnect and reconnect broadcasts");
    } finally {
      manager.binding = false;
      manager.stop();
    }
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
