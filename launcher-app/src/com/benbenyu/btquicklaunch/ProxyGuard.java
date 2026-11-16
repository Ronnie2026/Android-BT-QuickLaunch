package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** Detects locally whether the Clash Meta VPN/proxy is active without making external requests. */
final class ProxyGuard {
  static final String CLASH = "com.github.metacubex.clash.meta";
  static final String ACTION_START = CLASH + ".action.START_CLASH";
  /** Apps that require VPN/proxy by default; can be customized in settings. */
  static final List<String> OVERSEAS =
      Arrays.asList(
          "com.openai.chatgpt",
          "ai.x.grok",
          "ai.x.grok.bot",
          "com.google.android.apps.bard");
  static final int[] LOCAL_PORTS = {7890, 7891};

  static LinkedHashSet<String> configuredApps(SharedPreferences p) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    if (!p.contains("proxy_apps")) {
      set.addAll(OVERSEAS);
      return set;
    }
    String raw = p.getString("proxy_apps", "");
    for (String part : raw.split(",")) {
      String pkg = part.trim();
      if (!pkg.isEmpty()) set.add(pkg);
    }
    return set;
  }

  static boolean shouldGuard(SharedPreferences p, String pkg) {
    return pkg != null && configuredApps(p).contains(pkg);
  }

  static void setGuarded(SharedPreferences p, String pkg, boolean on) {
    if (pkg == null || pkg.isEmpty()) return;
    LinkedHashSet<String> set = configuredApps(p);
    if (on) set.add(pkg);
    else set.remove(pkg);
    StringBuilder sb = new StringBuilder();
    for (String s : set) {
      if (sb.length() > 0) sb.append(',');
      sb.append(s);
    }
    p.edit().putString("proxy_apps", sb.toString()).apply();
  }

  static final class Status {
    boolean baseNetwork; // Wi-Fi / cellular connected
    boolean tunnel; // VPN network or tun interface up
    boolean localPort; // Clash local proxy port accepts connections
    boolean clashProcess; // Clash background process alive (root check)

    boolean proxyRunning() {
      return tunnel || (clashProcess && localPort);
    }

    boolean ready() {
      return baseNetwork && proxyRunning();
    }

    String summary() {
      return "基础网络=" + yes(baseNetwork) + "  VPN隧道=" + yes(tunnel)
          + "  本地端口=" + yes(localPort) + "  Clash进程=" + yes(clashProcess);
    }

    static String yes(boolean b) {
      return b ? "是" : "否";
    }
  }

  /** Local-only check (no external HTTPS requests); safe to call from a worker thread. */
  static Status check(Context c) {
    return check(c, true);
  }

  static Status check(Context c, boolean log) {
    Status s = new Status();
    s.baseNetwork = baseNetwork(c);
    s.tunnel = tunnelUp(c);
    s.localPort = localPortOpen();
    s.clashProcess = s.tunnel && s.localPort || clashProcess();
    if (log) AppLog.write("PROXY_CHECK " + s.summary(), null);
    return s;
  }

  static boolean baseNetwork(Context c) {
    try {
      ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
      for (Network n : cm.getAllNetworks()) {
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        if (nc != null
            && !nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
      }
    } catch (Exception e) {
      AppLog.write("PROXY_CHECK base_network_failed=" + e.getClass().getSimpleName(), null);
      return true;
    }
    return false;
  }

  static boolean tunnelUp(Context c) {
    try {
      ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
      for (Network n : cm.getAllNetworks()) {
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
      }
    } catch (Exception ignored) {
    }
    try {
      java.util.Enumeration<java.net.NetworkInterface> all =
          java.net.NetworkInterface.getNetworkInterfaces();
      while (all != null && all.hasMoreElements()) {
        java.net.NetworkInterface ni = all.nextElement();
        if (ni.getName().startsWith("tun") && ni.isUp()) return true;
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  static boolean clashProcess() {
    java.lang.Process proc = null;
    try {
      proc = new ProcessBuilder("su", "-c", "pidof " + CLASH + ":background").start();
      if (!proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) return false;
      return proc.exitValue() == 0;
    } catch (Exception e) {
      return false;
    } finally {
      if (proc != null) proc.destroy();
    }
  }

  static boolean localPortOpen() {
    for (int port : LOCAL_PORTS) {
      try (java.net.Socket s = new java.net.Socket()) {
        s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
        return true;
      } catch (Exception ignored) {
      }
    }
    return false;
  }

  static boolean installed(Context c) {
    try {
      c.getPackageManager().getPackageInfo(CLASH, 0);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Asks Clash Meta to start its service; falls back to opening the app. */
  static boolean start(Context c) {
    AppLog.write("PROXY_START", null);
    try {
      c.startActivity(
          new Intent(ACTION_START).setPackage(CLASH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (Exception e) {
      AppLog.write("PROXY_START intent_failed=" + e.getClass().getSimpleName(), null);
    }
    try {
      Intent in = c.getPackageManager().getLaunchIntentForPackage(CLASH);
      if (in == null) return false;
      c.startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
