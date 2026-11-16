package com.benbenyu.btquicklaunch;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.telephony.*;
import android.view.*;
import java.util.List;

public class NetIndicators extends View {
  final ConnectivityManager cm;
  final TelephonyManager tm;
  final SubscriptionManager sm;
  final Handler handler = new Handler(Looper.getMainLooper());
  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  TelephonyManager activeTm;
  int registeredListenFlags = PhoneStateListener.LISTEN_NONE;
  boolean wifi, mobile, started, inService;
  int level = 0;
  String generation = "";
  String displayGeneration = "";

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
          level = strength != null ? clampLevel(strength.getLevel()) : 0;
          invalidate();
        }

        public void onServiceStateChanged(ServiceState serviceState) {
          inService = isServiceInService(serviceState);
          if (!inService) {
            displayGeneration = "";
          }
          handler.post(() -> refresh());
        }

        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
          displayGeneration = mapDisplayInfo(displayInfo);
          handler.post(() -> refresh());
        }
      };

  public NetIndicators(Context c) {
    super(c);
    cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
    sm = (SubscriptionManager) c.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    activeTm = tm;
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
    registerPhoneListener();
    refresh();
  }

  public void stop() {
    started = false;
    try {
      cm.unregisterNetworkCallback(callback);
    } catch (Exception ignored) {
    }
    unregisterPhoneListener();
    handler.removeCallbacksAndMessages(null);
  }

  public void refreshFromPermission() {
    if (!started) return;
    unregisterPhoneListener();
    registerPhoneListener();
    refresh();
  }

  void registerPhoneListener() {
    activeTm = resolveActiveTelephonyManager();
    if (activeTm == null) return;
    int flags =
        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_SERVICE_STATE;
    if (Build.VERSION.SDK_INT >= 30 && hasPhoneStatePermission()) {
      flags |= PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED;
    }
    try {
      activeTm.listen(phone, flags);
      registeredListenFlags = flags;
    } catch (Exception ignored) {
      try {
        int fallback =
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_SERVICE_STATE;
        activeTm.listen(phone, fallback);
        registeredListenFlags = fallback;
      } catch (Exception ignored2) {
        registeredListenFlags = PhoneStateListener.LISTEN_NONE;
      }
    }
  }

  void unregisterPhoneListener() {
    if (activeTm != null && registeredListenFlags != PhoneStateListener.LISTEN_NONE) {
      try {
        activeTm.listen(phone, PhoneStateListener.LISTEN_NONE);
      } catch (Exception ignored) {
      }
    }
    registeredListenFlags = PhoneStateListener.LISTEN_NONE;
  }

  boolean hasPhoneStatePermission() {
    return getContext().checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
        == PackageManager.PERMISSION_GRANTED;
  }

  TelephonyManager resolveActiveTelephonyManager() {
    if (tm == null) return null;
    if (sm != null && hasPhoneStatePermission()) {
      try {
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
          List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
          if (list != null && !list.isEmpty()) subId = list.get(0).getSubscriptionId();
        }
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
          TelephonyManager subTm = tm.createForSubscriptionId(subId);
          if (subTm != null) return subTm;
        }
      } catch (Exception ignored) {
      }
    }
    return tm;
  }

  boolean isAirplaneModeOn() {
    try {
      return Settings.Global.getInt(
              getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
          != 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean hasReadySim() {
    if (tm == null) return false;
    try {
      if (tm.getSimState() == TelephonyManager.SIM_STATE_READY) return true;
      for (int slot = 0; slot < 2; slot++) {
        if (tm.getSimState(slot) == TelephonyManager.SIM_STATE_READY) return true;
      }
    } catch (Exception ignored) {
    }
    if (sm != null && hasPhoneStatePermission()) {
      try {
        List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
        return list != null && !list.isEmpty();
      } catch (Exception ignored) {
      }
    }
    return false;
  }

  void refresh() {
    if (!started) return;
    wifi = false;
    boolean cellularConnected = false;
    try {
      for (Network n : cm.getAllNetworks()) {
        NetworkCapabilities cap = cm.getNetworkCapabilities(n);
        NetworkInfo info = cm.getNetworkInfo(n);
        if (cap == null || info == null || !info.isConnected()) continue;
        if (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
        if (cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cellularConnected = true;
      }
    } catch (Exception ignored) {
    }

    TelephonyManager currentTm = resolveActiveTelephonyManager();
    if (currentTm != activeTm) {
      unregisterPhoneListener();
      registerPhoneListener();
    }

    boolean airplane = isAirplaneModeOn();
    mobile = !airplane && (hasReadySim() || cellularConnected);

    if (mobile && activeTm != null) {
      if (Build.VERSION.SDK_INT >= 28) {
        try {
          SignalStrength ss = activeTm.getSignalStrength();
          if (ss != null) level = clampLevel(ss.getLevel());
        } catch (Exception ignored) {
        }
      }
      ServiceState activeServiceState = null;
      if (Build.VERSION.SDK_INT >= 26 && hasPhoneStatePermission()) {
        try {
          activeServiceState = activeTm.getServiceState();
          if (activeServiceState != null) inService = isServiceInService(activeServiceState);
        } catch (Exception ignored) {
        }
      } else if (cellularConnected || level > 0) {
        inService = true;
      }
      generation =
          resolveGeneration(
              activeTm, activeServiceState, inService, displayGeneration, hasPhoneStatePermission());
    } else {
      inService = false;
      level = 0;
      generation = "";
      displayGeneration = "";
    }

    int count = (wifi ? 1 : 0) + (mobile ? 1 : 0);
    setVisibility(count == 0 ? GONE : VISIBLE);
    if (getLayoutParams() != null) {
      getLayoutParams().width = (int) (count * 36 * getResources().getDisplayMetrics().density);
      requestLayout();
    }
    String mobileDesc = "";
    if (mobile) {
      mobileDesc =
          inService
              ? ((generation.isEmpty() ? "移动网络" : generation) + "信号" + level + "/4格")
              : "移动网络无服务";
    }
    setContentDescription((wifi ? "Wi-Fi已连接 " : "") + mobileDesc);
    invalidate();
  }

  static int clampLevel(int raw) {
    return Math.max(0, Math.min(4, raw));
  }

  static boolean isServiceInService(ServiceState ss) {
    if (ss == null) return false;
    if (ss.getState() == ServiceState.STATE_IN_SERVICE) return true;
    if (Build.VERSION.SDK_INT >= 30) {
      try {
        List<NetworkRegistrationInfo> infos = ss.getNetworkRegistrationInfoList();
        if (infos != null) {
          for (NetworkRegistrationInfo info : infos) {
            if (info != null && info.isRegistered()) return true;
          }
        }
      } catch (Exception ignored) {
      }
    }
    return false;
  }

  static String resolveGeneration(
      TelephonyManager tm,
      ServiceState ss,
      boolean inService,
      String displayGen,
      boolean hasPermission) {
    if (!inService) return "";
    if (displayGen != null && !displayGen.isEmpty()) return displayGen;
    if (Build.VERSION.SDK_INT >= 30 && ss != null) {
      try {
        List<NetworkRegistrationInfo> infos = ss.getNetworkRegistrationInfoList();
        if (infos != null) {
          for (NetworkRegistrationInfo info : infos) {
            if (info != null && info.isRegistered()) {
              String psGen = mapNetworkType(info.getAccessNetworkTechnology());
              if (!psGen.isEmpty()) return psGen;
            }
          }
        }
      } catch (Exception ignored) {
      }
    }
    if (tm == null || !hasPermission) return "";
    try {
      int dataType = tm.getDataNetworkType();
      String mapped = mapNetworkType(dataType);
      if (!mapped.isEmpty()) return mapped;
      int voiceType = tm.getVoiceNetworkType();
      return mapNetworkType(voiceType);
    } catch (Exception ignored) {
      return "";
    }
  }

  static String mapDisplayInfo(TelephonyDisplayInfo info) {
    if (info == null || Build.VERSION.SDK_INT < 30) return "";
    int override = info.getOverrideNetworkType();
    if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
        || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
        || override >= 5) {
      return "5G";
    }
    if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA
        || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO) {
      return "4G";
    }
    return mapNetworkType(info.getNetworkType());
  }

  static String mapNetworkType(int type) {
    switch (type) {
      case TelephonyManager.NETWORK_TYPE_NR:
        return "5G";
      case TelephonyManager.NETWORK_TYPE_LTE:
      case 19: // NETWORK_TYPE_LTE_CA
        return "4G";
      case TelephonyManager.NETWORK_TYPE_UMTS:
      case TelephonyManager.NETWORK_TYPE_EVDO_0:
      case TelephonyManager.NETWORK_TYPE_EVDO_A:
      case TelephonyManager.NETWORK_TYPE_HSDPA:
      case TelephonyManager.NETWORK_TYPE_HSUPA:
      case TelephonyManager.NETWORK_TYPE_HSPA:
      case TelephonyManager.NETWORK_TYPE_EVDO_B:
      case TelephonyManager.NETWORK_TYPE_EHRPD:
      case TelephonyManager.NETWORK_TYPE_HSPAP:
      case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
        return "3G";
      case TelephonyManager.NETWORK_TYPE_GPRS:
      case TelephonyManager.NETWORK_TYPE_EDGE:
      case TelephonyManager.NETWORK_TYPE_CDMA:
      case TelephonyManager.NETWORK_TYPE_1xRTT:
      case TelephonyManager.NETWORK_TYPE_IDEN:
      case TelephonyManager.NETWORK_TYPE_GSM:
        return "2G";
      default:
        return "";
    }
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float unit = getResources().getDisplayMetrics().density;
    c.save();
    c.scale(unit, unit);
    SharedPreferences prefs = getContext().getSharedPreferences("home", 0);
    boolean paper = prefs.getBoolean("paper_theme", false) && !prefs.getBoolean("paper_dark", false);
    int baseColor = paper ? 0xff454545 : 0xffb5bdc9;
    p.setColor(baseColor);
    p.setStrokeCap(Paint.Cap.ROUND);
    int slot = 0;
    if (wifi) {
      c.save();
      c.translate(slot++ * 36f, 0f);
      p.setAlpha(255);
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
      int activeBars = inService ? clampLevel(level) : 0;
      for (int i = 0; i < 4; i++) {
        p.setAlpha(i < activeBars ? 255 : 60);
        c.drawRoundRect(8f + i * 5f, 26f - (i + 1) * 3.6f, 10.6f + i * 5f, 26f, 1f, 1f, p);
      }
      p.setAlpha(255);
      if (inService && !generation.isEmpty()) {
        p.setTextSize(8.5f);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(generation, 5.5f, 14f, p);
        p.setTypeface(Typeface.DEFAULT);
      } else if (!inService) {
        p.setColor(paper ? 0xff666666 : 0xfff06b66);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        c.drawLine(7f, 9f, 12f, 14f, p);
        c.drawLine(12f, 9f, 7f, 14f, p);
        p.setColor(baseColor);
      }
      c.restore();
    }
    c.restore();
  }
}
