package com.benbenyu.btquicklaunch;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.*;
import android.os.*;
import java.util.*;

/** Foreground Wi-Fi SSID beacon watcher that puts the device to sleep when absent. */
final class WifiBeaconGuard {
  static final String PREF_ENABLED = "wifi_beacon_sleep";
  static final String PREF_SSID = "wifi_beacon_ssid";
  static final String PREF_TIMEOUT_MIN = "wifi_beacon_timeout";
  static final long SCAN_INTERVAL_MS = 60_000L;
  static final long INITIAL_DELAY_MS = 5_000L;
  static final int COUNTDOWN_SECONDS = 5;

  final MainActivity activity;
  final WifiManager wifi;
  final AudioManager audio;
  final LocationManager location;
  final Handler handler = new Handler(Looper.getMainLooper());
  final java.util.concurrent.ExecutorService sleepExecutor =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  boolean started;
  boolean receiverRegistered;
  long lastSeenAtMs;
  long lastSleepAtMs;
  AlertDialog countdownDialog;
  int countdownRemaining;
  String countdownTarget = "";
  long countdownMissingMs;

  final Runnable countdownTick =
      new Runnable() {
        public void run() {
          if (!started || countdownDialog == null || !countdownDialog.isShowing()) return;
          countdownRemaining--;
          if (countdownRemaining <= 0) {
            String target = countdownTarget;
            long missing = countdownMissingMs;
            dismissCountdown();
            lastSleepAtMs = SystemClock.elapsedRealtime();
            lastSeenAtMs = lastSleepAtMs;
            triggerSystemSleep(target, missing);
            return;
          }
          countdownDialog.setMessage(formatCountdownMessage(countdownTarget, countdownRemaining));
          handler.postDelayed(this, 1000L);
        }
      };

  final BroadcastReceiver scanReceiver =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
          if (!started) return;
          evaluate(false);
        }
      };

  final Runnable tick =
      new Runnable() {
        public void run() {
          if (!started) return;
          evaluate(true);
          handler.postDelayed(this, SCAN_INTERVAL_MS);
        }
      };

  WifiBeaconGuard(MainActivity a) {
    activity = a;
    wifi = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
    location = (LocationManager) a.getSystemService(Context.LOCATION_SERVICE);
  }

  void start() {
    if (started) return;
    started = true;
    lastSeenAtMs = SystemClock.elapsedRealtime();
    if (!receiverRegistered) {
      try {
        activity.registerReceiver(
            scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        receiverRegistered = true;
      } catch (Exception ignored) {
      }
    }
    handler.removeCallbacks(tick);
    handler.postDelayed(tick, INITIAL_DELAY_MS);
  }

  void stop() {
    if (!started) return;
    started = false;
    dismissCountdown();
    handler.removeCallbacksAndMessages(null);
    if (receiverRegistered) {
      try {
        activity.unregisterReceiver(scanReceiver);
      } catch (Exception ignored) {
      }
      receiverRegistered = false;
    }
  }

  void destroy() {
    stop();
    sleepExecutor.shutdownNow();
  }

  void dismissCountdown() {
    handler.removeCallbacks(countdownTick);
    if (countdownDialog != null) {
      try {
        if (countdownDialog.isShowing()) countdownDialog.dismiss();
      } catch (Exception ignored) {
      }
      countdownDialog = null;
    }
  }

  void resetTimer() {
    lastSeenAtMs = SystemClock.elapsedRealtime();
  }

  boolean hasLocationPermission() {
    return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
  }

  boolean isLocationEnabled() {
    if (location == null) return false;
    try {
      return location.isLocationEnabled();
    } catch (Exception ignored) {
      return false;
    }
  }

  void evaluate(boolean requestScan) {
    if (!started || activity.p == null) return;
    boolean enabled = activity.p.getBoolean(PREF_ENABLED, false);
    String target = normalizeSsid(activity.p.getString(PREF_SSID, ""));
    long now = SystemClock.elapsedRealtime();
    if (!enabled || target.isEmpty()) {
      lastSeenAtMs = now;
      return;
    }

    if (requestScan && wifi != null && hasLocationPermission()) {
      try {
        wifi.startScan();
      } catch (Exception ignored) {
      }
    }

    boolean seen = isSsidVisible(target);
    if (seen) {
      lastSeenAtMs = now;
      return;
    }

    boolean mediaActive = audio != null && audio.isMusicActive();
    long idleMs =
        activity.idlePower != null ? Math.max(0L, now - activity.idlePower.lastActivity) : 0L;
    int timeoutMin = clampTimeoutMinutes(activity.p.getInt(PREF_TIMEOUT_MIN, 3));
    long timeoutMs = timeoutMin * 60_000L;
    long missingMs = Math.max(0L, now - lastSeenAtMs);

    if (shouldSleep(enabled, target, false, mediaActive, idleMs, missingMs, timeoutMs)
        && (countdownDialog == null || !countdownDialog.isShowing())
        && now - lastSleepAtMs >= 30_000L) {
      showSleepCountdown(target, missingMs);
    }
  }

  void showSleepCountdown(String target, long missingMs) {
    if (activity.isDestroyed() || activity.isFinishing()) return;
    dismissCountdown();
    if (activity.idlePower != null) activity.idlePower.setDim(false);
    countdownTarget = target;
    countdownMissingMs = missingMs;
    countdownRemaining = COUNTDOWN_SECONDS;
    countdownDialog =
        new AlertDialog.Builder(activity)
            .setTitle("即将进入锁屏休眠")
            .setMessage(formatCountdownMessage(target, countdownRemaining))
            .setCancelable(true)
            .setPositiveButton(
                "保持亮屏",
                (d, w) -> {
                  dismissCountdown();
                  resetTimer();
                  if (activity.idlePower != null) activity.idlePower.activity("beacon_cancel");
                })
            .setNegativeButton(
                "立即休眠",
                (d, w) -> {
                  dismissCountdown();
                  lastSleepAtMs = SystemClock.elapsedRealtime();
                  lastSeenAtMs = lastSleepAtMs;
                  triggerSystemSleep(target, missingMs);
                })
            .setOnCancelListener(
                d -> {
                  dismissCountdown();
                  resetTimer();
                  if (activity.idlePower != null) activity.idlePower.activity("beacon_cancel");
                })
            .create();
    countdownDialog.show();
    handler.postDelayed(countdownTick, 1000L);
  }

  static String formatCountdownMessage(String target, int seconds) {
    return "未检测到目标 Wi-Fi（" + target + "），" + Math.max(1, seconds) + " 秒无响应将自动进入锁屏休眠模式。";
  }

  boolean isSsidVisible(String target) {
    if (wifi == null || target.isEmpty()) return false;
    try {
      WifiInfo info = wifi.getConnectionInfo();
      if (info != null && target.equals(normalizeSsid(info.getSSID()))) return true;
    } catch (Exception ignored) {
    }
    if (!hasLocationPermission()) return false;
    try {
      List<ScanResult> results = wifi.getScanResults();
      if (results != null) {
        for (ScanResult r : results) {
          if (r != null && target.equals(normalizeSsid(r.SSID))) return true;
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  List<String> scanNearbySsids() {
    LinkedHashMap<String, Integer> bestRssi = new LinkedHashMap<>();
    if (wifi != null) {
      try {
        WifiInfo info = wifi.getConnectionInfo();
        String connected = info != null ? normalizeSsid(info.getSSID()) : "";
        if (!connected.isEmpty()) bestRssi.put(connected, info.getRssi());
      } catch (Exception ignored) {
      }
      if (hasLocationPermission()) {
        try {
          wifi.startScan();
          List<ScanResult> results = wifi.getScanResults();
          if (results != null) {
            for (ScanResult r : results) {
              if (r == null) continue;
              String ssid = normalizeSsid(r.SSID);
              if (ssid.isEmpty()) continue;
              Integer prev = bestRssi.get(ssid);
              if (prev == null || r.level > prev) bestRssi.put(ssid, r.level);
            }
          }
        } catch (Exception ignored) {
        }
      }
    }
    List<String> sorted = new ArrayList<>(bestRssi.keySet());
    Collections.sort(sorted, (a, b) -> Integer.compare(bestRssi.get(b), bestRssi.get(a)));
    return sorted;
  }

  void triggerSystemSleep(String target, long missingMs) {
    AppLog.write(
        "WIFI_BEACON_SLEEP ssid=" + target + " missing_ms=" + missingMs, null);
    try {
      sleepExecutor.execute(
          () -> {
            java.lang.Process p = null;
            try {
              p =
                  new ProcessBuilder("su", "-c", "input keyevent KEYCODE_SLEEP")
                      .redirectErrorStream(true)
                      .start();
              if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroy();
            } catch (Exception e) {
              AppLog.write("WIFI_BEACON_SLEEP_FAILED", e);
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException ignored) {
    }
  }

  static int clampTimeoutMinutes(int raw) {
    return Math.max(1, Math.min(30, raw));
  }

  static String normalizeSsid(String raw) {
    if (raw == null) return "";
    String s = raw.trim();
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      s = s.substring(1, s.length() - 1).trim();
    }
    if ("<unknown ssid>".equalsIgnoreCase(s)) return "";
    return s;
  }

  static boolean shouldSleep(
      boolean enabled,
      String targetSsid,
      boolean seen,
      boolean mediaActive,
      long idleMs,
      long missingMs,
      long timeoutMs) {
    if (!enabled || targetSsid == null || targetSsid.isEmpty()) return false;
    if (seen || mediaActive) return false;
    if (timeoutMs <= 0 || idleMs < timeoutMs) return false;
    return missingMs >= timeoutMs;
  }
}
