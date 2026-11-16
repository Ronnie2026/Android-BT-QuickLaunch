package com.benbenyu.btquicklaunch;

import android.content.*;
import android.os.*;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Locale;

/** Displays live battery current, voltage, wattage, and temperature while foregrounded. */
public final class PowerIndicator extends TextView {
  private static final long SAMPLE_INTERVAL_MS = 2000L;
  static final int HIGH_TEMP_DECI_C = 420;
  private final BatteryManager battery;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final boolean paper;
  private boolean started;
  private boolean showPower = true;
  private boolean showTemp = true;
  private int voltageMv;
  private int tempDeciC;

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!started) return;
          refresh();
          if (showPower) handler.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
      };

  public PowerIndicator(Context context, boolean paper) {
    super(context);
    this.paper = paper;
    battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    setTextSize(8.5f);
    setLineSpacing(0f, 1.05f);
    setTextColor(paper ? 0xff454545 : 0xffb5bdc9);
    setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    setText("0.00W\n0mA 0.00V");
  }

  public void setDisplayOptions(boolean showPower, boolean showTemp) {
    this.showPower = showPower;
    this.showTemp = showTemp;
    setVisibility(showPower || showTemp ? VISIBLE : GONE);
    if (started) {
      handler.removeCallbacks(tick);
      if (showPower) handler.post(tick);
      else if (showTemp) refresh();
    }
  }

  public void updateBattery(int mv, int tempDeciC) {
    if (mv > 0) this.voltageMv = mv;
    if (tempDeciC > 0) this.tempDeciC = tempDeciC;
    if (started && (showPower || showTemp)) refresh();
  }

  public void start() {
    if (started) return;
    if (!showPower && !showTemp) return;
    started = true;
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  public void stop() {
    started = false;
    handler.removeCallbacksAndMessages(null);
  }

  private void refresh() {
    int currentUa = 0;
    if (showPower && battery != null) {
      try {
        int raw = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        if (raw != Integer.MIN_VALUE) currentUa = raw;
      } catch (Exception ignored) {
      }
    }
    if (voltageMv <= 0 || tempDeciC <= 0) {
      try {
        Intent sticky =
            getContext()
                .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
          int mv = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
          if (mv > 0) voltageMv = mv;
          int t = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
          if (t > 0) tempDeciC = t;
        }
      } catch (Exception ignored) {
      }
    }
    boolean hot = showTemp && tempDeciC >= HIGH_TEMP_DECI_C;
    setTextColor(
        hot
            ? (paper ? 0xffc93b3b : 0xfff06b66)
            : (paper ? 0xff454545 : 0xffb5bdc9));
    setText(formatDisplay(currentUa, voltageMv, tempDeciC, showPower, showTemp));
    StringBuilder desc = new StringBuilder();
    if (showPower) {
      desc.append("功耗 ")
          .append(formatWatts(currentUa, voltageMv))
          .append(" 电流 ")
          .append(Math.round(Math.abs(currentUa == Integer.MIN_VALUE ? 0 : currentUa) / 1000.0))
          .append(" 毫安 电压 ")
          .append(formatVolts(voltageMv));
    }
    if (showTemp) {
      if (desc.length() > 0) desc.append(' ');
      desc.append("温度 ").append(formatTemp(tempDeciC));
    }
    setContentDescription(desc.toString());
  }

  static double computeWatts(int currentUa, int voltageMv) {
    if (voltageMv <= 0 || currentUa == Integer.MIN_VALUE) return 0.0;
    return Math.abs(currentUa / 1_000_000.0) * (voltageMv / 1000.0);
  }

  static String formatWatts(int currentUa, int voltageMv) {
    return String.format(Locale.US, "%.2fW", computeWatts(currentUa, voltageMv));
  }

  static String formatVolts(int voltageMv) {
    if (voltageMv <= 0) return "0.00V";
    return String.format(Locale.US, "%.2fV", voltageMv / 1000.0);
  }

  static String formatTemp(int tempDeciC) {
    if (tempDeciC <= 0) return "--°C";
    return String.format(Locale.US, "%.1f°C", tempDeciC / 10.0);
  }

  static String formatPower(int currentUa, int voltageMv) {
    int safeUa = currentUa == Integer.MIN_VALUE ? 0 : currentUa;
    long ma = Math.round(Math.abs(safeUa) / 1000.0);
    return formatWatts(safeUa, voltageMv) + "\n" + ma + "mA " + formatVolts(voltageMv);
  }

  static String formatDisplay(
      int currentUa, int voltageMv, int tempDeciC, boolean showPower, boolean showTemp) {
    if (!showPower && !showTemp) return "";
    if (!showPower) return formatTemp(tempDeciC);
    if (!showTemp) return formatPower(currentUa, voltageMv);
    int safeUa = currentUa == Integer.MIN_VALUE ? 0 : currentUa;
    long ma = Math.round(Math.abs(safeUa) / 1000.0);
    return formatWatts(safeUa, voltageMv)
        + "  "
        + formatTemp(tempDeciC)
        + "\n"
        + ma
        + "mA "
        + formatVolts(voltageMv);
  }
}
