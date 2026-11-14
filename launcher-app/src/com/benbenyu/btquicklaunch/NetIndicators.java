package com.benbenyu.btquicklaunch;

import android.content.*;
import android.graphics.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.telephony.*;
import android.view.*;

public class NetIndicators extends View {
  final ConnectivityManager cm;
  final TelephonyManager tm;
  final Handler handler = new Handler();
  final Paint p = new Paint(3);
  boolean wifi, mobile, started;
  int level = 3;
  String generation = "";
  final ConnectivityManager.NetworkCallback callback =
      new ConnectivityManager.NetworkCallback() {
        public void onAvailable(Network n) {
          handler.post(() -> refresh());
        }

        public void onLost(Network n) {
          handler.post(() -> refresh());
        }

        public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
          handler.post(() -> refresh());
        }
      };
  final PhoneStateListener phone =
      new PhoneStateListener() {
        public void onSignalStrengthsChanged(SignalStrength strength) {
          level = strength.getLevel();
          invalidate();
        }
      };

  public NetIndicators(Context c) {
    super(c);
    cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
    setVisibility(GONE);
  }

  public void start() {
    if (started) return;
    started = true;
    try {
      cm.registerNetworkCallback(
          new NetworkRequest.Builder()
              .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
              .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
              .build(),
          callback);
    } catch (Exception ignored) {
    }
    try {
      tm.listen(phone, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    } catch (Exception ignored) {
    }
    refresh();
  }

  public void stop() {
    started = false;
    try {
      cm.unregisterNetworkCallback(callback);
    } catch (Exception ignored) {
    }
    try {
      tm.listen(phone, PhoneStateListener.LISTEN_NONE);
    } catch (Exception ignored) {
    }
    handler.removeCallbacksAndMessages(null);
  }

  void refresh() {
    if (!started) return;
    wifi = false;
    mobile = false;
    try {
      for (Network n : cm.getAllNetworks()) {
        NetworkCapabilities cap = cm.getNetworkCapabilities(n);
        NetworkInfo info = cm.getNetworkInfo(n);
        if (cap == null || info == null || !info.isConnected()) continue;
        if (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
        if (cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            && tm.getSimState() == TelephonyManager.SIM_STATE_READY) mobile = true;
      }
    } catch (Exception ignored) {
    }
    try {
      int t = tm.getDataNetworkType();
      generation = t == TelephonyManager.NETWORK_TYPE_LTE ? "4G" : t == 20 ? "5G" : "";
    } catch (Exception ignored) {
      generation = "";
    }
    int count = (wifi ? 1 : 0) + (mobile ? 1 : 0);
    setVisibility(count == 0 ? GONE : VISIBLE);
    if (getLayoutParams() != null) {
      getLayoutParams().width = (int) (count * 36 * getResources().getDisplayMetrics().density);
      requestLayout();
    }
    setContentDescription((wifi ? "Wi-Fi已连接 " : "") + (mobile ? generation + "移动网络已连接" : ""));
    invalidate();
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float unit = getResources().getDisplayMetrics().density;
    c.save();
    c.scale(unit, unit);
    boolean paper = getContext().getSharedPreferences("home", 0).getBoolean("paper_theme", false);
    p.setColor(paper ? 0xff454545 : 0xffb5bdc9);
    p.setStrokeCap(Paint.Cap.ROUND);
    int slot = 0;
    if (wifi) {
      c.save();
      c.translate(slot++ * 36f, 0f);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2.0f);
      for (int i = 0; i < 3; i++) {
        float r = 5f + i * 4.5f;
        c.drawArc(18f - r, 24.5f - r, 18f + r, 24.5f + r, 225f, 90f, false, p);
      }
      p.setStyle(Paint.Style.FILL);
      c.drawCircle(18f, 25f, 1.6f, p);
      c.restore();
    }
    if (mobile) {
      c.save();
      c.translate(slot++ * 36f, 0f);
      p.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 4; i++) {
        p.setAlpha(i < Math.max(1, level) ? 255 : 60);
        c.drawRoundRect(7f + i * 4.5f, 26f - (i + 1) * 3.8f, 9.5f + i * 4.5f, 26f, 1f, 1f, p);
      }
      p.setAlpha(255);
      p.setTextSize(8f);
      c.drawText(generation, 23f, 16f, p);
      c.restore();
    }
    c.restore();
  }
}
