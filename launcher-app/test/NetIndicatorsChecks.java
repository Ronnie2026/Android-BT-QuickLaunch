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

    System.out.println("PASS NetIndicators level clamping, network type mapping, and service gating");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
