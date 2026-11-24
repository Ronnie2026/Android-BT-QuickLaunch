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

/**
 * Checks local Clash Meta VPN/proxy state and, when exit-region guard is enabled, probes exit IP
 * country via the local Clash proxy port (with direct fallback for pure TUN setups).
 */
final class ProxyGuard {
  static final String CLASH = "com.github.metacubex.clash.meta";
  static final String ACTION_START = CLASH + ".action.START_CLASH";
  static final String PREF_REQUIRE_JP = "proxy_require_jp";
  static final String PREF_ALLOWED_REGIONS = "proxy_allowed_regions";
  static final String[][] REGION_OPTIONS = {
    {"JP", "日本 (JP)"},
    {"US", "美国 (US)"},
    {"SG", "新加坡 (SG)"},
    {"TW", "台湾 (TW)"},
    {"HK", "香港 (HK)"},
    {"KR", "韩国 (KR)"},
    {"GB", "英国 (GB)"},
    {"DE", "德国 (DE)"}
  };
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

  static int findOpenLocalPort() {
    for (int port : LOCAL_PORTS) {
      try (java.net.Socket s = new java.net.Socket()) {
        s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
        return port;
      } catch (Exception ignored) {
      }
    }
    return -1;
  }

  static boolean localPortOpen() {
    return findOpenLocalPort() > 0;
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
    return openClashUi(c);
  }

  static boolean openClashUi(Context c) {
    try {
      Intent in = c.getPackageManager().getLaunchIntentForPackage(CLASH);
      if (in == null) return false;
      c.startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  static final class ExitIpResult {
    final boolean reachable;
    final String country;
    final String ip;
    final String error;

    ExitIpResult(boolean reachable, String country, String ip, String error) {
      this.reachable = reachable;
      this.country = country != null ? country : "";
      this.ip = ip != null ? ip : "";
      this.error = error != null ? error : "";
    }
  }

  static LinkedHashSet<String> allowedRegions(SharedPreferences p) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    String raw = p != null ? p.getString(PREF_ALLOWED_REGIONS, "JP") : "JP";
    for (String part : raw.split(",")) {
      String cc = part.trim().toUpperCase(java.util.Locale.ROOT);
      if (!cc.isEmpty()) set.add(cc);
    }
    if (set.isEmpty()) set.add("JP");
    return set;
  }

  static void setAllowedRegions(SharedPreferences p, LinkedHashSet<String> regions) {
    if (p == null) return;
    if (regions == null || regions.isEmpty()) {
      p.edit().putString(PREF_ALLOWED_REGIONS, "JP").apply();
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (String r : regions) {
      if (sb.length() > 0) sb.append(',');
      sb.append(r);
    }
    p.edit().putString(PREF_ALLOWED_REGIONS, sb.toString()).apply();
  }

  static boolean isAllowedRegion(SharedPreferences p, String country) {
    if (country == null || country.isEmpty()) return false;
    return allowedRegions(p).contains(country.trim().toUpperCase(java.util.Locale.ROOT));
  }

  static String allowedRegionsSummary(SharedPreferences p) {
    LinkedHashSet<String> set = allowedRegions(p);
    StringBuilder sb = new StringBuilder();
    for (String code : set) {
      if (sb.length() > 0) sb.append(" / ");
      String label = code;
      for (String[] opt : REGION_OPTIONS) {
        if (opt[0].equals(code)) {
          label = opt[1];
          break;
        }
      }
      sb.append(label);
    }
    return sb.toString();
  }

  static String countryDisplayName(String code) {
    if (code == null || code.isEmpty()) return "";
    String c = code.trim().toUpperCase(java.util.Locale.ROOT);
    if ("JP".equals(c)) return "日本";
    if ("US".equals(c)) return "美国";
    if ("GB".equals(c) || "UK".equals(c)) return "英国";
    if ("SG".equals(c)) return "新加坡";
    if ("TW".equals(c)) return "台湾";
    if ("HK".equals(c)) return "香港";
    if ("KR".equals(c)) return "韩国";
    if ("DE".equals(c)) return "德国";
    if ("CA".equals(c)) return "加拿大";
    if ("AU".equals(c)) return "澳大利亚";
    if ("FR".equals(c)) return "法国";
    if ("NL".equals(c)) return "荷兰";
    if ("CN".equals(c)) return "中国";
    return c;
  }

  static ExitIpResult checkExitCountry() {
    int proxyPort = findOpenLocalPort();
    String[] traceUrls = {
      "https://chatgpt.com/cdn-cgi/trace",
      "https://www.cloudflare.com/cdn-cgi/trace"
    };
    String lastErr = "";
    for (String url : traceUrls) {
      try {
        String body = httpGet(url, proxyPort);
        String loc = parseTraceValue(body, "loc=");
        String ip = parseTraceValue(body, "ip=");
        if (loc.length() == 2) {
          AppLog.write("PROXY_EXIT_IP reachable=true country=" + loc, null);
          return new ExitIpResult(true, loc.toUpperCase(java.util.Locale.ROOT), ip, "");
        }
        lastErr = "未返回国家代码";
      } catch (Exception e) {
        lastErr = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      }
    }
    try {
      String cc = httpGet("https://ipinfo.io/country", proxyPort).trim();
      if (cc.length() == 2) {
        AppLog.write("PROXY_EXIT_IP reachable=true country=" + cc, null);
        return new ExitIpResult(true, cc.toUpperCase(java.util.Locale.ROOT), "", "");
      }
    } catch (Exception e) {
      lastErr = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
    AppLog.write("PROXY_EXIT_IP reachable=false err=" + lastErr, null);
    return new ExitIpResult(false, "", "", lastErr);
  }

  static String parseTraceValue(String body, String prefix) {
    if (body == null || prefix == null) return "";
    for (String line : body.split("\n")) {
      if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
    }
    return "";
  }

  private static String httpGet(String urlStr, int localProxyPort) throws Exception {
    if (localProxyPort > 0) {
      try {
        java.net.Proxy proxy =
            new java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                new java.net.InetSocketAddress("127.0.0.1", localProxyPort));
        return httpGetOnce(urlStr, proxy);
      } catch (Exception proxyErr) {
        // Fall back to default route if local port is SOCKS-only or rejects HTTP CONNECT.
        try {
          return httpGetOnce(urlStr, null);
        } catch (Exception ignored) {
          throw proxyErr;
        }
      }
    }
    return httpGetOnce(urlStr, null);
  }

  private static String httpGetOnce(String urlStr, java.net.Proxy proxy) throws Exception {
    java.net.HttpURLConnection conn = null;
    try {
      java.net.URL url = new java.net.URL(urlStr);
      conn =
          (java.net.HttpURLConnection)
              (proxy != null ? url.openConnection(proxy) : url.openConnection());
      conn.setConnectTimeout(2500);
      conn.setReadTimeout(2500);
      conn.setRequestProperty("User-Agent", "BT-QuickLaunch");
      conn.setRequestProperty("Connection", "close");
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
      StringBuilder sb = new StringBuilder();
      try (java.io.BufferedReader r =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
      }
      return sb.toString();
    } finally {
      if (conn != null) conn.disconnect();
    }
  }
}
