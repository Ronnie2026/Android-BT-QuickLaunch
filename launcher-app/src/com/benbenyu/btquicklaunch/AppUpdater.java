package com.benbenyu.btquicklaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Checks GitHub Releases (default) or Gitee Releases (manual selection only) for newer APKs,
 * downloads them, and invokes the system installer. No automatic fallback between sources.
 */
public class AppUpdater {
  static final String DEFAULT_GITEE_REPO = "Ronnie2026/Android-BT-QuickLaunch";
  static final String GITHUB_RELEASES_API =
      "https://api.github.com/repos/Ronnie2026/Android-BT-QuickLaunch/releases/latest";
  static final String GITHUB_RELEASES_PAGE =
      "https://github.com/Ronnie2026/Android-BT-QuickLaunch/releases";
  static final String PREF_AUTO_CHECK = "auto_update_check";
  static final String PREF_LAST_CHECK = "auto_update_last_check";
  static final String PREF_MIRROR = "update_mirror";
  static final String PREF_GITEE_REPO = "update_gitee_repo";
  static final String PREF_SOURCE_MODE = "update_source_mode";
  static final int SOURCE_GITHUB = 0;
  static final int SOURCE_GITEE = 1;
  static final long AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;

  static final class ReleaseInfo {
    final String source;
    final String version;
    final String notes;
    final String apkUrl;
    final long sizeBytes;

    ReleaseInfo(String source, String version, String notes, String apkUrl, long sizeBytes) {
      this.source = source;
      this.version = version;
      this.notes = notes;
      this.apkUrl = apkUrl;
      this.sizeBytes = sizeBytes;
    }
  }

  static String currentVersion(Context context) {
    try {
      PackageInfo info =
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      return info.versionName != null ? info.versionName : "0.0.0";
    } catch (Exception e) {
      return "0.0.0";
    }
  }

  static int sourceMode(SharedPreferences p) {
    if (p == null) return SOURCE_GITHUB;
    return p.getInt(PREF_SOURCE_MODE, SOURCE_GITHUB) == SOURCE_GITEE ? SOURCE_GITEE : SOURCE_GITHUB;
  }

  static String sourceLabel(SharedPreferences p) {
    return sourceMode(p) == SOURCE_GITEE ? "Gitee（手动）" : "GitHub（默认）";
  }

  static String giteeRepo(SharedPreferences p) {
    if (p == null) return DEFAULT_GITEE_REPO;
    String repo = p.getString(PREF_GITEE_REPO, DEFAULT_GITEE_REPO).trim();
    return repo.isEmpty() ? DEFAULT_GITEE_REPO : repo;
  }

  static void autoCheckIfDue(Activity act, SharedPreferences p) {
    if (act == null || p == null || !p.getBoolean(PREF_AUTO_CHECK, true)) return;
    long now = System.currentTimeMillis();
    long last = p.getLong(PREF_LAST_CHECK, 0L);
    if (now - last < AUTO_CHECK_INTERVAL_MS) return;
    if (!hasNetwork(act)) return;
    p.edit().putLong(PREF_LAST_CHECK, now).apply();
    check(act, p, true);
  }

  static void check(Activity act, SharedPreferences p, boolean silent) {
    if (act == null || act.isDestroyed() || act.isFinishing()) return;
    int mode = sourceMode(p);
    String msg =
        mode == SOURCE_GITEE
            ? "正在从 Gitee 检测最新版本…"
            : "正在从 GitHub 检测最新版本…";
    AlertDialog checkingDialog =
        silent
            ? null
            : new AlertDialog.Builder(act)
                .setTitle("检查更新")
                .setMessage(msg)
                .setCancelable(true)
                .show();
    AtomicBoolean cancelled = new AtomicBoolean(false);
    if (checkingDialog != null) {
      checkingDialog.setOnCancelListener(d -> cancelled.set(true));
    }

    new Thread(
            () -> {
              String error = null;
              ReleaseInfo release = null;
              try {
                release = fetchLatestWithFallback(p);
              } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
              }
              final ReleaseInfo found = release;
              final String err = error;
              act.runOnUiThread(
                  () -> {
                    if (act.isDestroyed() || act.isFinishing()) return;
                    if (checkingDialog != null && checkingDialog.isShowing()) {
                      checkingDialog.dismiss();
                    }
                    if (cancelled.get()) return;
                    if (err != null || found == null) {
                      if (!silent) {
                        new AlertDialog.Builder(act)
                            .setTitle("检查更新失败")
                            .setMessage(
                                "无法从 "
                                    + (sourceMode(p) == SOURCE_GITEE ? "Gitee" : "GitHub")
                                    + " 获取最新版本：\n"
                                    + (err != null ? err : "未知错误")
                                    + "\n\n请检查网络连接或 VPN 代理配置。")
                            .setPositiveButton("知道了", null)
                            .show();
                      }
                      return;
                    }
                    String localVer = currentVersion(act);
                    if (compareVersions(found.version, localVer) > 0) {
                      showUpdatePrompt(act, p, found, localVer);
                    } else if (!silent) {
                      new AlertDialog.Builder(act)
                          .setTitle("已是最新版本")
                          .setMessage(
                              "当前版本：v"
                                  + localVer
                                  + "\n远端最新（"
                                  + found.source
                                  + "）：v"
                                  + found.version)
                          .setPositiveButton("好的", null)
                          .show();
                    }
                  });
            },
            "app-update-check")
        .start();
  }

  private static void showUpdatePrompt(
      Activity act, SharedPreferences p, ReleaseInfo found, String localVer) {
    StringBuilder msg = new StringBuilder();
    msg.append("更新源：").append(found.source).append("\n");
    msg.append("当前版本：v").append(localVer).append("\n");
    msg.append("最新版本：v").append(found.version).append("\n");
    if (found.sizeBytes > 0) {
      msg.append(String.format(Locale.ROOT, "安装包大小：%.2f MB\n", found.sizeBytes / 1048576.0));
    }
    if (found.notes != null && !found.notes.trim().isEmpty()) {
      msg.append("\n更新内容：\n").append(found.notes.trim());
    }
    new AlertDialog.Builder(act)
        .setTitle("发现新版本 v" + found.version)
        .setMessage(msg.toString())
        .setPositiveButton("立即下载安装", (d, w) -> downloadAndInstall(act, p, found))
        .setNegativeButton("稍后再说", null)
        .show();
  }

  private static void downloadAndInstall(
      Activity act, SharedPreferences p, ReleaseInfo release) {
    int pad = Math.round(20 * act.getResources().getDisplayMetrics().density);
    LinearLayout box = new LinearLayout(act);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(pad, pad / 2, pad, pad / 2);

    TextView status = new TextView(act);
    status.setText("正在连接 " + release.source + " 下载服务器…");
    status.setTextSize(14);
    box.addView(status);

    ProgressBar bar =
        new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100);
    bar.setIndeterminate(true);
    LinearLayout.LayoutParams bp =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    bp.topMargin = pad / 2;
    box.addView(bar, bp);

    AtomicBoolean cancelled = new AtomicBoolean(false);
    AlertDialog progressDialog =
        new AlertDialog.Builder(act)
            .setTitle("正在下载 v" + release.version)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("取消", (d, w) -> cancelled.set(true))
            .show();

    new Thread(
            () -> {
              String error = null;
              try {
                downloadApkToCache(act, p, release, cancelled, bar, status);
              } catch (Exception primaryErr) {
                error = cancelled.get() ? "已取消下载" : msgOf(primaryErr);
              }
              final String err = error;
              act.runOnUiThread(
                  () -> {
                    if (act.isDestroyed() || act.isFinishing()) return;
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    if (cancelled.get()) return;
                    if (err != null) {
                      boolean isGitee = "Gitee".equals(release.source);
                      String pageUrl =
                          isGitee
                              ? "https://gitee.com/" + giteeRepo(p) + "/releases"
                              : GITHUB_RELEASES_PAGE;
                      String tip =
                          isGitee
                              ? "下载失败：" + err + "\n\n请检查网络，或前往 Gitee Releases 页面手动下载更新：\n" + pageUrl
                              : "GitHub 下载失败："
                                  + err
                                  + "\n\n请确认网络或代理状态，也可以前往 GitHub Releases 页面手动下载最新安装包：\n"
                                  + pageUrl;
                      new AlertDialog.Builder(act)
                          .setTitle("下载失败")
                          .setMessage(tip)
                          .setPositiveButton("知道了", null)
                          .setNeutralButton(
                              "打开 Release 页面",
                              (d, w) -> {
                                try {
                                  act.startActivity(
                                      new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)));
                                } catch (Exception ignored) {
                                }
                              })
                          .show();
                      return;
                    }
                    installDownloadedApk(act);
                  });
            },
            "app-update-download")
        .start();
  }

  private static void downloadApkToCache(
      Activity act,
      SharedPreferences p,
      ReleaseInfo release,
      AtomicBoolean cancelled,
      ProgressBar bar,
      TextView status)
      throws Exception {
    String downloadUrl =
        "GitHub".equals(release.source)
            ? applyMirror(release.apkUrl, p.getString(PREF_MIRROR, ""))
            : release.apkUrl;
    File target = UpdateApkProvider.apkFile(act);
    File parent = target.getParentFile();
    if (parent != null) parent.mkdirs();
    File part = new File(parent, target.getName() + ".part");
    HttpURLConnection conn = null;
    try {
      conn = openConnection(downloadUrl, 15000, 30000, null);
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IllegalStateException("HTTP " + code);
      }
      long total = conn.getContentLengthLong();
      if (total <= 0) total = release.sizeBytes;
      final long totalBytes = total;
      long downloaded = 0L;
      byte[] buf = new byte[16384];
      try (InputStream in = conn.getInputStream();
          FileOutputStream out = new FileOutputStream(part)) {
        int n;
        long lastUi = 0L;
        while ((n = in.read(buf)) != -1) {
          if (cancelled.get()) throw new IllegalStateException("已取消下载");
          out.write(buf, 0, n);
          downloaded += n;
          long now = System.currentTimeMillis();
          if (now - lastUi >= 150) {
            lastUi = now;
            final long done = downloaded;
            act.runOnUiThread(
                () -> {
                  if (act.isDestroyed() || act.isFinishing()) return;
                  if (totalBytes > 0) {
                    int pct = (int) Math.min(100L, (done * 100L) / totalBytes);
                    bar.setIndeterminate(false);
                    bar.setProgress(pct);
                    status.setText(
                        String.format(
                            Locale.ROOT,
                            "[%s] 已下载 %.2f / %.2f MB (%d%%)",
                            release.source,
                            done / 1048576.0,
                            totalBytes / 1048576.0,
                            pct));
                  } else {
                    bar.setIndeterminate(true);
                    status.setText(
                        String.format(
                            Locale.ROOT,
                            "[%s] 已下载 %.2f MB",
                            release.source,
                            done / 1048576.0));
                  }
                });
          }
        }
        out.flush();
      }
      if (part.length() <= 0) throw new IllegalStateException("下载文件为空");
      PackageInfo pkgInfo =
          act.getPackageManager().getPackageArchiveInfo(part.getAbsolutePath(), 0);
      if (pkgInfo == null || !act.getPackageName().equals(pkgInfo.packageName)) {
        throw new IllegalStateException("下载的不是有效的安装包（可能被代理页面拦截）");
      }
      if (target.exists() && !target.delete()) {
        throw new IllegalStateException("无法替换旧安装包缓存");
      }
      if (!part.renameTo(target)) {
        throw new IllegalStateException("无法保存安装包");
      }
    } catch (Exception e) {
      if (part.exists()) part.delete();
      throw e;
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  static void installDownloadedApk(Activity act) {
    if (act == null || act.isDestroyed() || act.isFinishing()) return;
    if (!act.getPackageManager().canRequestPackageInstalls()) {
      new AlertDialog.Builder(act)
          .setTitle("需要允许安装应用")
          .setMessage("安装包已下载完成。请先在系统设置中允许 BT QuickLaunch 安装未知应用，授权后返回再点击安装即可。")
          .setPositiveButton(
              "去授权",
              (d, w) -> {
                try {
                  act.startActivity(
                      new Intent(
                          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                          Uri.parse("package:" + act.getPackageName())));
                } catch (Exception ignored) {
                }
              })
          .setNegativeButton("取消", null)
          .show();
      return;
    }
    Uri uri = UpdateApkProvider.contentUri();
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, "application/vnd.android.package-archive");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      act.startActivity(intent);
    } catch (Exception e) {
      new AlertDialog.Builder(act)
          .setTitle("无法拉起安装器")
          .setMessage(e.getMessage() != null ? e.getMessage() : "系统未响应安装请求")
          .setPositiveButton("知道了", null)
          .show();
    }
  }

  private static ReleaseInfo fetchLatestWithFallback(SharedPreferences p) throws Exception {
    if (sourceMode(p) == SOURCE_GITEE) {
      String giteeApi = "https://gitee.com/api/v5/repos/" + giteeRepo(p) + "/releases/latest";
      return fetchReleaseFromApi(giteeApi, "Gitee", false);
    }
    return fetchReleaseFromApi(GITHUB_RELEASES_API, "GitHub", true);
  }

  private static ReleaseInfo fetchReleaseFromApi(
      String apiUrl, String sourceName, boolean githubAccept) throws Exception {
    HttpURLConnection conn = null;
    try {
      conn =
          openConnection(
              apiUrl, 8000, 10000, githubAccept ? "application/vnd.github+json" : "application/json");
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IllegalStateException("HTTP " + code);
      }
      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
      }
      JSONObject json = new JSONObject(sb.toString());
      String tag = json.optString("tag_name", "").trim();
      if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1);
      if (tag.isEmpty()) throw new IllegalStateException("Release 缺少版本号标签");
      String body = json.optString("body", "");
      JSONArray assets = json.optJSONArray("assets");
      String apkUrl = null;
      long apkSize = 0L;
      if (assets != null) {
        for (int i = 0; i < assets.length(); i++) {
          JSONObject asset = assets.optJSONObject(i);
          if (asset == null) continue;
          String name = asset.optString("name", "");
          if (name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            apkUrl = asset.optString("browser_download_url", "");
            apkSize = asset.optLong("size", 0L);
            break;
          }
        }
      }
      if (apkUrl == null || apkUrl.isEmpty()) {
        throw new IllegalStateException("最新 Release 中未找到 .apk 安装包附件");
      }
      return new ReleaseInfo(sourceName, tag, body, apkUrl, apkSize);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static HttpURLConnection openConnection(
      String urlStr, int connectMs, int readMs, String acceptHeader) throws Exception {
    String current = urlStr;
    for (int redirects = 0; redirects < 5; redirects++) {
      HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
      conn.setInstanceFollowRedirects(false);
      conn.setConnectTimeout(connectMs);
      conn.setReadTimeout(readMs);
      conn.setRequestProperty("User-Agent", "BT-QuickLaunch-Updater");
      if (acceptHeader != null) conn.setRequestProperty("Accept", acceptHeader);
      int code = conn.getResponseCode();
      if (code == HttpURLConnection.HTTP_MOVED_PERM
          || code == HttpURLConnection.HTTP_MOVED_TEMP
          || code == 307
          || code == 308) {
        String location = conn.getHeaderField("Location");
        conn.disconnect();
        if (location == null || location.isEmpty()) {
          throw new IllegalStateException("重定向目标为空");
        }
        current = location;
        continue;
      }
      return conn;
    }
    throw new IllegalStateException("重定向次数过多");
  }

  private static String msgOf(Exception e) {
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }

  static String applyMirror(String rawUrl, String mirror) {
    if (mirror == null) return rawUrl;
    String m = mirror.trim();
    if (m.isEmpty()) return rawUrl;
    if (!m.endsWith("/")) m += "/";
    return m + rawUrl;
  }

  static int compareVersions(String remote, String local) {
    int[] r = parseVersion(remote);
    int[] l = parseVersion(local);
    int len = Math.max(r.length, l.length);
    for (int i = 0; i < len; i++) {
      int rv = i < r.length ? r[i] : 0;
      int lv = i < l.length ? l[i] : 0;
      if (rv != lv) return Integer.compare(rv, lv);
    }
    return 0;
  }

  private static int[] parseVersion(String v) {
    if (v == null || v.trim().isEmpty()) return new int[] {0};
    String clean = v.trim();
    int dash = clean.indexOf('-');
    if (dash > 0) clean = clean.substring(0, dash);
    String[] parts = clean.split("\\.");
    int[] nums = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      try {
        nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
      } catch (Exception ignored) {
        nums[i] = 0;
      }
    }
    return nums;
  }

  private static boolean hasNetwork(Context context) {
    try {
      ConnectivityManager cm =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) return false;
      Network net = cm.getActiveNetwork();
      if (net == null) return false;
      NetworkCapabilities caps = cm.getNetworkCapabilities(net);
      return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    } catch (Exception e) {
      return false;
    }
  }
}
