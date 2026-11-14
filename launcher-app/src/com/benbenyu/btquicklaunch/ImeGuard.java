package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Toast;

/** Enables or completely disables Android soft input methods via Root (`ime disable` / `enable`). */
final class ImeGuard {
  static final String FALLBACK_IME = "com.baidu.input_yijia/.ImeService";

  static void apply(Context c, SharedPreferences p, boolean disable, boolean notify) {
    new Thread(
            () -> {
              boolean ok = false;
              try {
                String enabled =
                    Settings.Secure.getString(c.getContentResolver(), "enabled_input_methods");
                String def =
                    Settings.Secure.getString(c.getContentResolver(), "default_input_method");
                StringBuilder cmd = new StringBuilder();
                if (disable) {
                  if (enabled != null && !enabled.isEmpty()) {
                    p.edit()
                        .putString("saved_imes", enabled)
                        .putString("saved_default_ime", def == null ? "" : def)
                        .apply();
                    for (String raw : enabled.split(":")) {
                      String id = cleanId(raw);
                      if (!id.isEmpty()) cmd.append("ime disable ").append(id).append("; ");
                    }
                  }
                  cmd.append("settings --user 0 put secure show_ime_with_hard_keyboard 0");
                } else {
                  String saved = p.getString("saved_imes", FALLBACK_IME);
                  if (saved.isEmpty()) saved = FALLBACK_IME;
                  String savedDef = p.getString("saved_default_ime", "");
                  String first = "";
                  for (String raw : saved.split(":")) {
                    String id = cleanId(raw);
                    if (!id.isEmpty()) {
                      if (first.isEmpty()) first = id;
                      cmd.append("ime enable ").append(id).append("; ");
                    }
                  }
                  String targetDef = cleanId(savedDef);
                  if (targetDef.isEmpty()) targetDef = first;
                  if (!targetDef.isEmpty()) cmd.append("ime set ").append(targetDef).append("; ");
                  cmd.append("settings --user 0 put secure show_ime_with_hard_keyboard 1");
                }
                java.lang.Process proc =
                    new ProcessBuilder("su", "-c", cmd.toString()).start();
                ok =
                    proc.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)
                        && proc.exitValue() == 0;
                AppLog.write("IME_GUARD disable=" + disable + " ok=" + ok, null);
              } catch (Exception e) {
                AppLog.write("IME_GUARD_FAILED", e);
              }
              final boolean success = ok;
              if (notify) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(
                        () ->
                            Toast.makeText(
                                    c,
                                    success
                                        ? (disable
                                            ? "已完全禁用输入法（纯语音模式）"
                                            : "已恢复输入法，可正常打字")
                                        : "输入法切换失败，请检查 Root 授权",
                                    0)
                                .show());
              }
            })
        .start();
  }

  static String cleanId(String raw) {
    if (raw == null) return "";
    int semi = raw.indexOf(';');
    String id = (semi >= 0 ? raw.substring(0, semi) : raw).trim();
    return id.matches("[A-Za-z0-9_./]+") ? id : "";
  }
}
