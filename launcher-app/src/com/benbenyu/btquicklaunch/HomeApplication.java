package com.benbenyu.btquicklaunch;

public class HomeApplication extends android.app.Application {
  public void onCreate() {
    super.onCreate();
    AppLog.init(new java.io.File(getFilesDir(), "logs"), getApplicationInfo().uid);
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          AppLog.write("FATAL thread=" + thread.getName(), error);
          if (previous != null) previous.uncaughtException(thread, error);
          else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
          }
        });
    AppLog.write("APP_START version=1.2.1", null);
    try {
      getPackageManager().getApplicationInfo("com.larus.nova", 0);
      android.content.SharedPreferences slots = getSharedPreferences("home", 0);
      for (int i = 0; i < 4; i++)
        if (slots
            .getString(i + "pkg", i == 3 ? "com.tencent.qqmusic" : "")
            .equals("com.tencent.qqmusic"))
          slots
              .edit()
              .putString(i + "pkg", "com.larus.nova")
              .putString(i + "name", "豆包")
              .putBoolean(i + "voice", false)
              .remove(i + "icon")
              .putString(i + "before", "")
              .apply();
    } catch (Exception ignored) {
    }
    if (android.os.Build.VERSION.SDK_INT >= 30)
      try {
        android.app.ActivityManager am =
            (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        java.util.List<android.app.ApplicationExitInfo> exits =
            am.getHistoricalProcessExitReasons(getPackageName(), 0, 5);
        android.content.SharedPreferences p = getSharedPreferences("diagnostics", 0);
        long last = p.getLong("last_exit", 0), latest = last;
        for (android.app.ApplicationExitInfo e : exits)
          if (e.getTimestamp() > last) {
            AppLog.write(
                "PREVIOUS_EXIT reason="
                    + e.getReason()
                    + " status="
                    + e.getStatus()
                    + " at="
                    + e.getTimestamp(),
                null);
            latest = Math.max(latest, e.getTimestamp());
            if (e.getReason() == android.app.ApplicationExitInfo.REASON_ANR)
              try (java.io.InputStream in = e.getTraceInputStream()) {
                if (in != null) {
                  byte[] data = new byte[16384];
                  int n = in.read(data);
                  if (n > 0) AppLog.write("ANR_TRACE " + new String(data, 0, n, "UTF-8"), null);
                }
              } catch (Exception ignored) {
              }
          }
        p.edit().putLong("last_exit", latest).apply();
      } catch (Exception ignored) {
      }
  }
}
