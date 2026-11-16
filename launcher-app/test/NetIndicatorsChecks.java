package com.benbenyu.btquicklaunch;

import android.telephony.TelephonyManager;

public final class NetIndicatorsChecks {
  public static void main(String[] args) {
    check(NetIndicators.clampLevel(-1) == 0, "clamp negative signal level");
    check(NetIndicators.clampLevel(0) == 0, "clamp zero signal level");
    check(NetIndicators.clampLevel(3) == 3, "keep valid signal level");
    check(NetIndicators.clampLevel(9) == 4, "clamp upper signal level");

    check("5G".equals(NetIndicators.mapNetworkType(TelephonyManager.NETWORK_TYPE_NR)), "NR maps to 5G");
    check("4G".equals(NetIndicators.mapNetworkType(TelephonyManager.NETWORK_TYPE_LTE)), "LTE maps to 4G");
    check("4G".equals(NetIndicators.mapNetworkType(19)), "LTE_CA maps to 4G");
    check("3G".equals(NetIndicators.mapNetworkType(TelephonyManager.NETWORK_TYPE_HSPA)), "HSPA maps to 3G");
    check("2G".equals(NetIndicators.mapNetworkType(TelephonyManager.NETWORK_TYPE_EDGE)), "EDGE maps to 2G");
    check("".equals(NetIndicators.mapNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN)), "UNKNOWN maps to empty");

    check("".equals(NetIndicators.resolveGeneration(null, null, false, "5G", true)), "out of service hides generation");
    check("5G".equals(NetIndicators.resolveGeneration(null, null, true, "5G", false)), "display info generation preserved");
    check("".equals(NetIndicators.resolveGeneration(null, null, true, "", false)), "missing permission without display info returns empty");

    check(NetIndicators.computeRatePerSec(-1L, 1000L, 1000L) == 0L, "negative baseline returns zero");
    check(NetIndicators.computeRatePerSec(2000L, 1000L, 1000L) == 0L, "counter reset returns zero");
    check(NetIndicators.computeRatePerSec(1000L, 3048L, 1000L) == 2048L, "compute bytes per second");
    check("0B/s".equals(NetIndicators.formatSpeed(0L)), "format zero speed");
    check("512B/s".equals(NetIndicators.formatSpeed(512L)), "format byte speed");
    check("1.5K/s".equals(NetIndicators.formatSpeed(1536L)), "format low KB speed");
    check("256K/s".equals(NetIndicators.formatSpeed(256L * 1024L)), "format high KB speed");
    check("2.5M/s".equals(NetIndicators.formatSpeed(2560L * 1024L)), "format MB speed");

    System.out.println("PASS NetIndicators level clamping, network type mapping, service gating, and traffic speed");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
