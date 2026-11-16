package com.benbenyu.btquicklaunch;

public final class WifiBeaconChecks {
  public static void main(String[] args) {
    check("MyPhone".equals(WifiBeaconGuard.normalizeSsid("\"MyPhone\"")), "strip quoted SSID");
    check("".equals(WifiBeaconGuard.normalizeSsid("<unknown ssid>")), "ignore unknown ssid");
    check("".equals(WifiBeaconGuard.normalizeSsid("   ")), "blank ssid normalizes to empty");

    check(WifiBeaconGuard.clampTimeoutMinutes(0) == 1, "clamp min timeout");
    check(WifiBeaconGuard.clampTimeoutMinutes(3) == 3, "keep valid timeout");
    check(WifiBeaconGuard.clampTimeoutMinutes(60) == 30, "clamp max timeout");

    long timeout = 180_000L;
    check(!WifiBeaconGuard.shouldSleep(false, "MyPhone", false, false, timeout, timeout, timeout), "disabled does not sleep");
    check(!WifiBeaconGuard.shouldSleep(true, "", false, false, timeout, timeout, timeout), "empty ssid does not sleep");
    check(!WifiBeaconGuard.shouldSleep(true, "MyPhone", true, false, timeout, timeout, timeout), "visible ssid does not sleep");
    check(!WifiBeaconGuard.shouldSleep(true, "MyPhone", false, true, timeout, timeout, timeout), "active media does not sleep");
    check(!WifiBeaconGuard.shouldSleep(true, "MyPhone", false, false, 60_000L, timeout, timeout), "recent user activity does not sleep");
    check(!WifiBeaconGuard.shouldSleep(true, "MyPhone", false, false, timeout, 120_000L, timeout), "below missing timeout does not sleep");
    check(WifiBeaconGuard.shouldSleep(true, "MyPhone", false, false, timeout, timeout, timeout), "absent and idle past timeout triggers sleep");

    System.out.println("PASS WifiBeaconGuard SSID normalization, timeout bounds, and sleep gating");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
