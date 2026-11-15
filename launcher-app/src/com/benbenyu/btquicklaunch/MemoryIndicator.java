package com.benbenyu.btquicklaunch;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Locale;

/** Whole-device RAM pressure, with no root process or background polling. */
public final class MemoryIndicator extends TextView {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean started;
  private final Runnable tick = new Runnable() {
    public void run() {
      if (!started) return;
      PowerManager power = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
      if (power == null || !power.isInteractive()) return;
      refresh();
      handler.postDelayed(this, 10000);
    }
  };
  private final BroadcastReceiver screen = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      handler.removeCallbacks(tick);
      if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) handler.post(tick);
    }
  };

  public MemoryIndicator(Context context, boolean paper) {
    super(context);
    setTextSize(13);
    setTextColor(paper ? 0xff333333 : 0xffb9c4d1);
    setGravity(Gravity.CENTER_VERTICAL);
    android.graphics.drawable.Drawable chip = new android.graphics.drawable.Drawable() {
      final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      public void draw(android.graphics.Canvas canvas) {
        android.graphics.Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        paint.setColor(getCurrentTextColor());
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        canvas.drawRoundRect(5, 5, 19, 19, 2, 2, paint);
        canvas.drawRoundRect(9, 9, 15, 15, 1, 1, paint);
        for (int pin = 8; pin <= 16; pin += 4) {
          canvas.drawLine(pin, 2, pin, 5, paint);
          canvas.drawLine(pin, 19, pin, 22, paint);
          canvas.drawLine(2, pin, 5, pin, paint);
          canvas.drawLine(19, pin, 22, pin, paint);
        }
        canvas.restore();
      }
      public void setAlpha(int alpha) {}
      public void setColorFilter(android.graphics.ColorFilter filter) {}
      public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    };
    float density = getResources().getDisplayMetrics().density;
    chip.setBounds(0, 0, Math.round(19 * density), Math.round(19 * density));
    setCompoundDrawables(chip, null, null, null);
    setCompoundDrawablePadding(Math.round(4 * density));
    setText("--%");
    setOnClickListener(v -> {
      refresh();
      android.widget.Toast.makeText(context, getContentDescription(), android.widget.Toast.LENGTH_LONG).show();
    });
  }

  public void start() {
    if (started) return;
    started = true;
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    getContext().registerReceiver(screen, filter);
    handler.post(tick);
  }

  public void stop() {
    if (!started) return;
    started = false;
    handler.removeCallbacks(tick);
    getContext().unregisterReceiver(screen);
  }

  private void refresh() {
    ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
    if (manager == null) return;
    manager.getMemoryInfo(info);
    if (info.totalMem <= 0) return;
    long used = Math.max(0, Math.min(info.totalMem, info.totalMem - info.availMem));
    int percent = (int) Math.round(used * 100.0 / info.totalMem);
    setText(percent + "%");
    setContentDescription(String.format(Locale.CHINA,
        "整机内存占用 %d%%，已用 %.1f GB / 总计 %.1f GB；可用内存包含可回收缓存",
        percent, used / 1073741824.0, info.totalMem / 1073741824.0));
  }
}
