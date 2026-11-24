package com.benbenyu.btquicklaunch;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reliable dual-channel SMS forwarder (Bark + Webhook, independently or simultaneously active):
 * 1) Manifest high-priority SMS_RECEIVED broadcast receiver,
 * 2) ContentObserver + periodic inbox reconciliation inside persistent process,
 * 3) Per-channel persistent disk retry queue + Partial WakeLock + direct/proxy dual-path delivery.
 */
final class SmsBarkForwarder {
  static final String PREF_ENABLED = "sms_bark_enabled";
  static final String PREF_ENDPOINT = "sms_bark_endpoint";
  static final String PREF_WEBHOOK_ENABLED = "sms_webhook_enabled";
  static final String PREF_WEBHOOK_URL = "sms_webhook_url";
  static final String PREF_SOURCE = "sms_bark_source";
  static final String PREF_TTL = "sms_bark_ttl";
  static final String PREF_LAST_INBOX_ID = "sms_bark_last_inbox_id";
  static final String PREF_LAST_INBOX_TS = "sms_bark_last_inbox_ts";
  static final String DEFAULT_SOURCE = "来自安卓助理的通知";
  static final int DEFAULT_TTL = 600;

  private static final long DEDUP_WINDOW_MS = 10 * 60_000L;
  private static final long QUEUE_EXPIRE_MS = 24L * 60L * 60L * 1000L;
  private static final long WATCHDOG_INTERVAL_MS = 60_000L;
  private static final int MAX_QUEUE_SIZE = 50;
  private static final int MAX_SEEN_SIZE = 80;
  private static final long[] RETRY_DELAYS_MS = {4_000L, 12_000L, 30_000L, 90_000L};

  private static final Object LOCK = new Object();
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
  private static volatile Handler mainHandler;
  private static volatile Context appContext;
  private static boolean watching;
  private static boolean retryScheduled;
  private static ContentObserver smsObserver;
  private static ConnectivityManager.NetworkCallback netCallback;
  private static LinkedHashMap<String, Long> seenCache;

  private static Handler mainHandler() {
    Handler h = mainHandler;
    if (h == null) {
      synchronized (LOCK) {
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        h = mainHandler;
      }
    }
    return h;
  }

  private static final Runnable WATCHDOG_TICK =
      new Runnable() {
        public void run() {
          Context c = appContext;
          if (c != null) {
            WORKER.execute(
                () -> {
                  scanInboxMissed(c);
                  flushQueue(c);
                });
            mainHandler().postDelayed(this, WATCHDOG_INTERVAL_MS);
          }
        }
      };

  static final class QueueItem {
    final String id;
    final String sender;
    final String body;
    final long timestampMs;
    int attempts;
    long nextAttemptAtMs;
    boolean barkDone;
    boolean webhookDone;

    QueueItem(
        String id,
        String sender,
        String body,
        long timestampMs,
        int attempts,
        long nextAttemptAtMs,
        boolean barkDone,
        boolean webhookDone) {
      this.id = id;
      this.sender = sender;
      this.body = body;
      this.timestampMs = timestampMs;
      this.attempts = attempts;
      this.nextAttemptAtMs = nextAttemptAtMs;
      this.barkDone = barkDone;
      this.webhookDone = webhookDone;
    }

    JSONObject toJson() throws Exception {
      JSONObject o = new JSONObject();
      o.put("id", id);
      o.put("sender", sender);
      o.put("body", body);
      o.put("ts", timestampMs);
      o.put("attempts", attempts);
      o.put("next", nextAttemptAtMs);
      o.put("barkDone", barkDone);
      o.put("webhookDone", webhookDone);
      return o;
    }

    static QueueItem fromJson(JSONObject o) {
      return new QueueItem(
          o.optString("id", UUID.randomUUID().toString()),
          o.optString("sender", "未知号码"),
          o.optString("body", ""),
          o.optLong("ts", System.currentTimeMillis()),
          o.optInt("attempts", 0),
          o.optLong("next", 0L),
          o.optBoolean("barkDone", false),
          o.optBoolean("webhookDone", true));
    }
  }

  static boolean isBarkReady(SharedPreferences p) {
    return p != null
        && p.getBoolean(PREF_ENABLED, false)
        && !normalizeEndpoint(p.getString(PREF_ENDPOINT, "")).isEmpty();
  }

  static boolean isWebhookReady(SharedPreferences p) {
    return p != null
        && p.getBoolean(PREF_WEBHOOK_ENABLED, false)
        && !normalizeWebhookUrl(p.getString(PREF_WEBHOOK_URL, "")).isEmpty();
  }

  static boolean isAnyChannelReady(SharedPreferences p) {
    return isBarkReady(p) || isWebhookReady(p);
  }

  static void ensureWatching(Context context) {
    if (context == null) return;
    final Context app = context.getApplicationContext();
    appContext = app;
    synchronized (LOCK) {
      SharedPreferences p = app.getSharedPreferences("home", 0);
      if (!p.contains(PREF_LAST_INBOX_TS)) {
        p.edit().putLong(PREF_LAST_INBOX_TS, System.currentTimeMillis()).apply();
      }
      if (watching) return;
      watching = true;
    }

    mainHandler()
        .post(
            () -> {
              try {
                if (smsObserver == null) {
                  smsObserver =
                      new ContentObserver(mainHandler()) {
                        public void onChange(boolean selfChange) {
                          WORKER.execute(
                              () -> {
                                scanInboxMissed(app);
                                flushQueue(app);
                              });
                        }
                      };
                  app.getContentResolver()
                      .registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
                }
              } catch (Exception e) {
                AppLog.write("SMS_OBSERVER_REGISTER_ERR", e);
              }

              try {
                ConnectivityManager cm =
                    (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null && netCallback == null) {
                  netCallback =
                      new ConnectivityManager.NetworkCallback() {
                        public void onAvailable(Network network) {
                          WORKER.execute(() -> flushQueue(app));
                        }
                      };
                  cm.registerNetworkCallback(
                      new NetworkRequest.Builder()
                          .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                          .build(),
                      netCallback);
                }
              } catch (Exception ignored) {
              }

              mainHandler().removeCallbacks(WATCHDOG_TICK);
              mainHandler().postDelayed(WATCHDOG_TICK, 15_000L);
            });

    WORKER.execute(
        () -> {
          scanInboxMissed(app);
          flushQueue(app);
        });
  }

  static void onPreferencesUpdated(Context context) {
    if (context == null) return;
    Context app = context.getApplicationContext();
    SharedPreferences p = app.getSharedPreferences("home", 0);
    if (p.getBoolean(PREF_ENABLED, false) || p.getBoolean(PREF_WEBHOOK_ENABLED, false)) {
      if (!p.contains(PREF_LAST_INBOX_TS) || p.getLong(PREF_LAST_INBOX_TS, 0L) <= 0L) {
        p.edit().putLong(PREF_LAST_INBOX_TS, System.currentTimeMillis()).apply();
      }
      ensureWatching(app);
      WORKER.execute(() -> flushQueue(app));
    }
  }

  static void onSmsReceived(
      Context context, String sender, String body, long timestampMs, Runnable done) {
    if (context == null) {
      if (done != null) done.run();
      return;
    }
    final Context app = context.getApplicationContext();
    ensureWatching(app);
    PowerManager.WakeLock wl = acquireWakeLock(app, 25_000L);
    WORKER.execute(
        () -> {
          try {
            SharedPreferences p = app.getSharedPreferences("home", 0);
            boolean barkOn = isBarkReady(p);
            boolean webhookOn = isWebhookReady(p);
            if (!barkOn && !webhookOn) return;
            long now = System.currentTimeMillis();
            long safeTs = timestampMs > 0 ? timestampMs : now;
            if (enqueueIfFresh(app, sender, body, safeTs, now, barkOn, webhookOn)) {
              long prevInbox = p.getLong(PREF_LAST_INBOX_TS, 0L);
              if (safeTs > prevInbox) {
                p.edit().putLong(PREF_LAST_INBOX_TS, safeTs).apply();
              }
              flushQueue(app);
            }
          } finally {
            releaseWakeLock(wl);
            if (done != null) done.run();
          }
        });
  }

  static void sendTestBark(Context context, java.util.function.Consumer<String> callback) {
    final Context app = context.getApplicationContext();
    WORKER.execute(
        () -> {
          SharedPreferences p = app.getSharedPreferences("home", 0);
          String endpoint = normalizeEndpoint(p.getString(PREF_ENDPOINT, ""));
          if (endpoint.isEmpty()) {
            mainHandler().post(() -> callback.accept("请先填入有效的 Bark 地址或 Key"));
            return;
          }
          String source = resolveSourceTag(p);
          int ttl = resolveTtl(p);
          QueueItem testItem =
              new QueueItem(
                  "test-bark",
                  "10000（测试）",
                  "这是一条 Bark 通道测试短信，转发链路正常。",
                  System.currentTimeMillis(),
                  0,
                  0L,
                  false,
                  true);
          try {
            deliverToBark(endpoint, source, ttl, testItem);
            AppLog.write("SMS_BARK_TEST ok", null);
            mainHandler().post(() -> callback.accept("Bark 测试推送成功！请查看苹果手机通知"));
          } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            AppLog.write("SMS_BARK_TEST fail=" + err, null);
            mainHandler().post(() -> callback.accept("Bark 推送失败：" + err));
          }
        });
  }

  static void sendTestWebhook(Context context, java.util.function.Consumer<String> callback) {
    final Context app = context.getApplicationContext();
    WORKER.execute(
        () -> {
          SharedPreferences p = app.getSharedPreferences("home", 0);
          String url = normalizeWebhookUrl(p.getString(PREF_WEBHOOK_URL, ""));
          if (url.isEmpty()) {
            mainHandler().post(() -> callback.accept("请先填入有效的 Webhook URL"));
            return;
          }
          String source = resolveSourceTag(p);
          QueueItem testItem =
              new QueueItem(
                  "test-webhook",
                  "10000（测试）",
                  "这是一条 Webhook 通道测试短信，转发链路正常。",
                  System.currentTimeMillis(),
                  0,
                  0L,
                  true,
                  false);
          try {
            deliverToWebhook(url, source, testItem);
            AppLog.write("SMS_WEBHOOK_TEST ok", null);
            mainHandler().post(() -> callback.accept("Webhook 测试推送成功！"));
          } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            AppLog.write("SMS_WEBHOOK_TEST fail=" + err, null);
            mainHandler().post(() -> callback.accept("Webhook 推送失败：" + err));
          }
        });
  }

  private static void scanInboxMissed(Context app) {
    SharedPreferences p = app.getSharedPreferences("home", 0);
    boolean barkOn = isBarkReady(p);
    boolean webhookOn = isWebhookReady(p);
    if (!barkOn && !webhookOn) return;
    if (app.checkSelfPermission(Manifest.permission.READ_SMS)
        != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    long lastId = p.getLong(PREF_LAST_INBOX_ID, -1L);
    if (lastId < 0L) {
      long maxExistingId = queryMaxInboxId(app);
      if (maxExistingId >= 0L) {
        p.edit().putLong(PREF_LAST_INBOX_ID, maxExistingId).apply();
      }
      return;
    }
    long now = System.currentTimeMillis();
    long maxIdSeen = lastId;
    boolean added = false;
    try (Cursor c =
        app.getContentResolver()
            .query(
                Uri.parse("content://sms/inbox"),
                new String[] {"_id", "address", "body", "date"},
                "_id > ?",
                new String[] {String.valueOf(lastId)},
                "_id ASC")) {
      if (c != null) {
        while (c.moveToNext()) {
          long id = c.getLong(0);
          String addr = c.getString(1);
          String body = c.getString(2);
          long date = c.getLong(3);
          if (id > maxIdSeen) maxIdSeen = id;
          if (enqueueIfFresh(app, addr, body, date, now, barkOn, webhookOn)) {
            added = true;
          }
        }
      }
    } catch (Exception e) {
      AppLog.write("SMS_INBOX_SCAN_ERR", e);
    }
    if (maxIdSeen > lastId) {
      p.edit().putLong(PREF_LAST_INBOX_ID, maxIdSeen).apply();
    }
    if (added) {
      AppLog.write("SMS_INBOX_RECONCILED last_id=" + maxIdSeen, null);
    }
  }

  private static long queryMaxInboxId(Context app) {
    try (Cursor c =
        app.getContentResolver()
            .query(
                Uri.parse("content://sms/inbox"),
                new String[] {"_id"},
                null,
                null,
                "_id DESC LIMIT 1")) {
      if (c != null && c.moveToFirst()) return c.getLong(0);
      return 0L;
    } catch (Exception e) {
      AppLog.write("SMS_MAX_ID_ERR", e);
      return -1L;
    }
  }

  private static boolean enqueueIfFresh(
      Context app,
      String rawSender,
      String rawBody,
      long msgTs,
      long nowMs,
      boolean sendBark,
      boolean sendWebhook) {
    if (!sendBark && !sendWebhook) return false;
    String sender = normalizeSender(rawSender);
    String body = rawBody != null ? rawBody.trim() : "";
    if (body.isEmpty()) return false;
    String key = dedupKey(sender, body);
    synchronized (LOCK) {
      LinkedHashMap<String, Long> seen = loadSeenLocked(app);
      Long prevAt = seen.get(key);
      if (prevAt != null && Math.abs(nowMs - prevAt) < DEDUP_WINDOW_MS) {
        return false;
      }
      seen.put(key, nowMs);
      trimAndSaveSeenLocked(app, seen, nowMs);

      List<QueueItem> queue = loadQueueLocked(app);
      if (queue.size() >= MAX_QUEUE_SIZE) {
        queue.remove(0);
      }
      queue.add(
          new QueueItem(
              UUID.randomUUID().toString(),
              sender,
              body,
              msgTs,
              0,
              0L,
              !sendBark,
              !sendWebhook));
      saveQueueLocked(app, queue);
      AppLog.write(
          "SMS_FORWARD_ENQUEUE sender="
              + maskSender(sender)
              + " bark="
              + sendBark
              + " webhook="
              + sendWebhook
              + " size="
              + queue.size(),
          null);
      return true;
    }
  }

  private static void flushQueue(Context app) {
    SharedPreferences p = app.getSharedPreferences("home", 0);
    boolean barkOn = isBarkReady(p);
    boolean webhookOn = isWebhookReady(p);
    String barkEndpoint = normalizeEndpoint(p.getString(PREF_ENDPOINT, ""));
    String webhookUrl = normalizeWebhookUrl(p.getString(PREF_WEBHOOK_URL, ""));
    String source = resolveSourceTag(p);
    int ttl = resolveTtl(p);

    List<QueueItem> snapshot;
    synchronized (LOCK) {
      snapshot = loadQueueLocked(app);
    }
    if (snapshot.isEmpty()) return;

    PowerManager.WakeLock wl = acquireWakeLock(app, 25_000L);
    try {
      long now = System.currentTimeMillis();
      boolean changed = false;
      long earliestNextWaitMs = Long.MAX_VALUE;
      List<QueueItem> remaining = new ArrayList<>();

      for (QueueItem item : snapshot) {
        if (now - item.timestampMs > QUEUE_EXPIRE_MS) {
          changed = true;
          AppLog.write("SMS_FORWARD_DROP_EXPIRED id=" + item.id, null);
          continue;
        }
        // If user turned off a channel after enqueue, mark that channel done so queue doesn't stall
        if (!item.barkDone && !barkOn) {
          item.barkDone = true;
          changed = true;
        }
        if (!item.webhookDone && !webhookOn) {
          item.webhookDone = true;
          changed = true;
        }
        if (item.barkDone && item.webhookDone) {
          changed = true;
          continue;
        }
        if (item.nextAttemptAtMs > now) {
          earliestNextWaitMs = Math.min(earliestNextWaitMs, item.nextAttemptAtMs - now);
          remaining.add(item);
          continue;
        }

        String failReason = null;
        if (!item.barkDone) {
          try {
            deliverToBark(barkEndpoint, source, ttl, item);
            item.barkDone = true;
            changed = true;
            AppLog.write("SMS_BARK_SENT sender=" + maskSender(item.sender), null);
          } catch (Exception e) {
            failReason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
          }
        }
        if (!item.webhookDone) {
          try {
            deliverToWebhook(webhookUrl, source, item);
            item.webhookDone = true;
            changed = true;
            AppLog.write("SMS_WEBHOOK_SENT sender=" + maskSender(item.sender), null);
          } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            failReason = failReason == null ? err : (failReason + "; " + err);
          }
        }

        if (!item.barkDone || !item.webhookDone) {
          item.attempts++;
          long backoff =
              RETRY_DELAYS_MS[Math.min(item.attempts - 1, RETRY_DELAYS_MS.length - 1)];
          item.nextAttemptAtMs = System.currentTimeMillis() + backoff;
          earliestNextWaitMs = Math.min(earliestNextWaitMs, backoff);
          remaining.add(item);
          changed = true;
          AppLog.write(
              "SMS_FORWARD_RETRY attempts=" + item.attempts + " err=" + failReason, null);
        } else {
          changed = true;
        }
      }

      if (changed) {
        synchronized (LOCK) {
          saveQueueLocked(app, remaining);
        }
      }
      if (!remaining.isEmpty() && earliestNextWaitMs < Long.MAX_VALUE) {
        scheduleRetry(app, Math.max(2_000L, Math.min(earliestNextWaitMs, WATCHDOG_INTERVAL_MS)));
      }
    } finally {
      releaseWakeLock(wl);
    }
  }

  private static void scheduleRetry(Context app, long delayMs) {
    synchronized (LOCK) {
      if (retryScheduled) return;
      retryScheduled = true;
    }
    mainHandler()
        .postDelayed(
            () -> {
              synchronized (LOCK) {
                retryScheduled = false;
              }
              WORKER.execute(() -> flushQueue(app));
            },
            delayMs);
  }

  static void deliverToBark(String endpoint, String sourceTag, int ttl, QueueItem item)
      throws Exception {
    Exception firstErr;
    try {
      deliverBarkOnce(endpoint, sourceTag, ttl, item, null);
      return;
    } catch (Exception e) {
      firstErr = e;
    }
    int localPort = ProxyGuard.findOpenLocalPort();
    if (localPort > 0) {
      try {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", localPort));
        deliverBarkOnce(endpoint, sourceTag, ttl, item, proxy);
        return;
      } catch (Exception ignored) {
      }
    }
    throw firstErr;
  }

  static void deliverToWebhook(String webhookUrl, String sourceTag, QueueItem item)
      throws Exception {
    String json =
        buildWebhookJson(webhookUrl, sourceTag, item.sender, item.body, item.timestampMs);
    Exception firstErr;
    try {
      httpPostJson(webhookUrl, json, null);
      return;
    } catch (Exception e) {
      firstErr = e;
    }
    int localPort = ProxyGuard.findOpenLocalPort();
    if (localPort > 0) {
      try {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", localPort));
        httpPostJson(webhookUrl, json, proxy);
        return;
      } catch (Exception ignored) {
      }
    }
    throw firstErr;
  }

  private static void deliverBarkOnce(
      String endpoint, String sourceTag, int ttl, QueueItem item, Proxy proxy) throws Exception {
    String title = buildBarkTitle(item.sender);
    String body = buildBarkBody(item.body, item.timestampMs, sourceTag);
    String query = "group=" + rfc3986Encode(sourceTag) + "&ttl=" + ttl;
    String postUrl = endpoint + (endpoint.contains("?") ? "&" : "?") + query;

    JSONObject payload = new JSONObject();
    payload.put("title", title);
    payload.put("subtitle", sourceTag);
    payload.put("body", body);
    payload.put("group", sourceTag);
    payload.put("ttl", ttl);

    try {
      httpPostJson(postUrl, payload.toString(), proxy);
      return;
    } catch (Exception postErr) {
      String getUrl =
          endpoint
              + "/"
              + rfc3986Encode(title)
              + "/"
              + rfc3986Encode(body)
              + (endpoint.contains("?") ? "&" : "?")
              + query;
      try {
        httpGet(getUrl, proxy);
      } catch (Exception ignored) {
        throw postErr;
      }
    }
  }

  static String buildWebhookJson(
      String webhookUrl, String sourceTag, String sender, String body, long timestampMs)
      throws Exception {
    String title = buildBarkTitle(sender);
    String formattedBody = buildBarkBody(body, timestampMs, sourceTag);
    String fullText = "【" + title + "】\n" + formattedBody;
    String lowerUrl = webhookUrl != null ? webhookUrl.toLowerCase(Locale.ROOT) : "";

    JSONObject root = new JSONObject();
    if (lowerUrl.contains("feishu.cn/") || lowerUrl.contains("larksuite.com/")) {
      root.put("msg_type", "text");
      JSONObject content = new JSONObject();
      content.put("text", fullText);
      root.put("content", content);
      return root.toString();
    }
    if (lowerUrl.contains("qyapi.weixin.qq.com/") || lowerUrl.contains("oapi.dingtalk.com/")) {
      root.put("msgtype", "text");
      JSONObject text = new JSONObject();
      text.put("content", fullText);
      root.put("text", text);
      return root.toString();
    }
    String tag = (sourceTag == null || sourceTag.trim().isEmpty()) ? DEFAULT_SOURCE : sourceTag.trim();
    root.put("source", tag);
    root.put("sender", normalizeSender(sender));
    root.put("title", title);
    root.put("body", formattedBody);
    root.put("text", fullText);
    root.put("content", fullText);
    root.put("timestamp", timestampMs > 0 ? timestampMs : System.currentTimeMillis());
    return root.toString();
  }

  private static void httpPostJson(String urlStr, String jsonBody, Proxy proxy) throws Exception {
    HttpURLConnection conn = null;
    try {
      URL url = new URL(urlStr);
      conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
      conn.setRequestMethod("POST");
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(6000);
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      conn.setRequestProperty("User-Agent", "BT-QuickLaunch");
      conn.setRequestProperty("Connection", "close");
      byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(bytes);
      }
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static void httpGet(String urlStr, Proxy proxy) throws Exception {
    HttpURLConnection conn = null;
    try {
      URL url = new URL(urlStr);
      conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(5000);
      conn.setReadTimeout(6000);
      conn.setRequestProperty("User-Agent", "BT-QuickLaunch");
      conn.setRequestProperty("Connection", "close");
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  static String normalizeEndpoint(String raw) {
    if (raw == null) return "";
    String s = raw.trim();
    if (s.isEmpty()) return "";
    int q = s.indexOf('?');
    if (q >= 0) s = s.substring(0, q).trim();
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1).trim();
    if (s.isEmpty()) return "";
    if (!s.startsWith("http://") && !s.startsWith("https://")) {
      String key = s.startsWith("/") ? s.substring(1) : s;
      int slash = key.indexOf('/');
      if (slash > 0) key = key.substring(0, slash);
      if (!key.matches("[A-Za-z0-9_-]{8,64}")) return "";
      return "https://api.day.app/" + key;
    }
    try {
      URL u = new URL(s);
      String protocol = u.getProtocol();
      if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) return "";
      String host = u.getHost();
      if (host == null || host.isEmpty()) return "";
      int port = u.getPort();
      String path = u.getPath() != null ? u.getPath() : "";
      String[] parts = path.split("/");
      String deviceKey = "";
      for (String p : parts) {
        if (!p.trim().isEmpty()) {
          deviceKey = p.trim();
          break;
        }
      }
      if (deviceKey.isEmpty()) return "";
      return protocol.toLowerCase(Locale.ROOT)
          + "://"
          + host
          + (port > 0 ? ":" + port : "")
          + "/"
          + deviceKey;
    } catch (Exception e) {
      return "";
    }
  }

  static String normalizeWebhookUrl(String raw) {
    if (raw == null) return "";
    String s = raw.trim();
    if (s.isEmpty()) return "";
    try {
      URL u = new URL(s);
      String protocol = u.getProtocol();
      if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) return "";
      if (u.getHost() == null || u.getHost().trim().isEmpty()) return "";
      return s;
    } catch (Exception e) {
      return "";
    }
  }

  static String maskEndpoint(String raw) {
    String norm = normalizeEndpoint(raw);
    if (norm.isEmpty()) return "未配置（点击填入）";
    int idx = norm.lastIndexOf('/');
    if (idx < 0 || idx + 1 >= norm.length()) return norm;
    String prefix = norm.substring(0, idx + 1);
    String key = norm.substring(idx + 1);
    if (key.length() <= 8) return prefix + "****";
    return prefix + key.substring(0, 4) + "..." + key.substring(key.length() - 4);
  }

  static String maskWebhookUrl(String raw) {
    String norm = normalizeWebhookUrl(raw);
    if (norm.isEmpty()) return "未配置（点击填入）";
    try {
      URL u = new URL(norm);
      String base =
          u.getProtocol().toLowerCase(Locale.ROOT)
              + "://"
              + u.getHost()
              + (u.getPort() > 0 ? ":" + u.getPort() : "");
      String tail = (u.getPath() != null ? u.getPath() : "") + (u.getQuery() != null ? "?" + u.getQuery() : "");
      if (tail.length() <= 12) return base + (tail.isEmpty() ? "" : "/****");
      return base + "/..." + tail.substring(tail.length() - 6);
    } catch (Exception e) {
      return "已配置";
    }
  }

  static String resolveSourceTag(SharedPreferences p) {
    String raw = p != null ? p.getString(PREF_SOURCE, DEFAULT_SOURCE) : DEFAULT_SOURCE;
    String s = raw != null ? raw.trim() : "";
    return s.isEmpty() ? DEFAULT_SOURCE : s;
  }

  static int resolveTtl(SharedPreferences p) {
    int v = p != null ? p.getInt(PREF_TTL, DEFAULT_TTL) : DEFAULT_TTL;
    return Math.max(60, Math.min(86400, v));
  }

  static String normalizeSender(String sender) {
    if (sender == null) return "未知号码";
    String s = sender.trim();
    return s.isEmpty() ? "未知号码" : s;
  }

  private static String maskSender(String sender) {
    if (sender == null || sender.length() <= 4) return "****";
    return sender.substring(0, 2) + "***" + sender.substring(sender.length() - 2);
  }

  static String dedupKey(String sender, String body) {
    return normalizeSender(sender) + "\n" + (body != null ? body.trim() : "");
  }

  static String buildBarkTitle(String sender) {
    return "短信 · " + normalizeSender(sender);
  }

  static String buildBarkBody(String body, long timestampMs, String sourceTag) {
    String timeStr =
        new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
            .format(new Date(timestampMs > 0 ? timestampMs : System.currentTimeMillis()));
    String tag = (sourceTag == null || sourceTag.trim().isEmpty()) ? DEFAULT_SOURCE : sourceTag.trim();
    return (body != null ? body.trim() : "") + "\n\n[" + tag + " · " + timeStr + "]";
  }

  static String rfc3986Encode(String s) {
    if (s == null || s.isEmpty()) return "";
    try {
      return URLEncoder.encode(s, "UTF-8")
          .replace("+", "%20")
          .replace("*", "%2A")
          .replace("%7E", "~");
    } catch (Exception e) {
      return "";
    }
  }

  private static PowerManager.WakeLock acquireWakeLock(Context c, long timeoutMs) {
    try {
      PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
      if (pm == null) return null;
      PowerManager.WakeLock wl =
          pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btquicklaunch:sms_bark");
      wl.setReferenceCounted(false);
      wl.acquire(timeoutMs);
      return wl;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void releaseWakeLock(PowerManager.WakeLock wl) {
    if (wl != null) {
      try {
        if (wl.isHeld()) wl.release();
      } catch (Exception ignored) {
      }
    }
  }

  private static LinkedHashMap<String, Long> loadSeenLocked(Context app) {
    if (seenCache != null) return seenCache;
    seenCache = new LinkedHashMap<>();
    File file = new File(app.getFilesDir(), "sms_bark_seen.json");
    if (!file.exists()) return seenCache;
    try {
      JSONObject obj = new JSONObject(readFileText(file));
      Iterator<String> keys = obj.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        seenCache.put(k, obj.optLong(k, 0L));
      }
    } catch (Exception ignored) {
    }
    return seenCache;
  }

  private static void trimAndSaveSeenLocked(
      Context app, LinkedHashMap<String, Long> seen, long nowMs) {
    Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Long> e = it.next();
      if (nowMs - e.getValue() > 30 * 60_000L) it.remove();
    }
    while (seen.size() > MAX_SEEN_SIZE && seen.entrySet().iterator().hasNext()) {
      Iterator<String> first = seen.keySet().iterator();
      first.next();
      first.remove();
    }
    try {
      JSONObject obj = new JSONObject();
      for (Map.Entry<String, Long> e : seen.entrySet()) {
        obj.put(e.getKey(), e.getValue());
      }
      writeFileAtomic(new File(app.getFilesDir(), "sms_bark_seen.json"), obj.toString());
    } catch (Exception ignored) {
    }
  }

  private static List<QueueItem> loadQueueLocked(Context app) {
    List<QueueItem> list = new ArrayList<>();
    File file = new File(app.getFilesDir(), "sms_bark_queue.json");
    if (!file.exists()) return list;
    try {
      JSONArray arr = new JSONArray(readFileText(file));
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o != null) list.add(QueueItem.fromJson(o));
      }
    } catch (Exception ignored) {
    }
    return list;
  }

  private static void saveQueueLocked(Context app, List<QueueItem> list) {
    try {
      JSONArray arr = new JSONArray();
      for (QueueItem item : list) arr.put(item.toJson());
      writeFileAtomic(new File(app.getFilesDir(), "sms_bark_queue.json"), arr.toString());
    } catch (Exception ignored) {
    }
  }

  private static String readFileText(File f) throws Exception {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) sb.append(line).append('\n');
    }
    return sb.toString();
  }

  private static void writeFileAtomic(File target, String content) throws Exception {
    File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
    try (FileOutputStream out = new FileOutputStream(tmp)) {
      out.write(content.getBytes(StandardCharsets.UTF_8));
      out.getFD().sync();
    }
    if (!tmp.renameTo(target)) {
      try (FileOutputStream out = new FileOutputStream(target)) {
        out.write(content.getBytes(StandardCharsets.UTF_8));
      }
      tmp.delete();
    }
  }
}
