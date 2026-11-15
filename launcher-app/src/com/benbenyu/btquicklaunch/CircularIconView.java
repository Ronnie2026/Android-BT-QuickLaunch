package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** A consistent circular display mask; source icons and imported image files stay unchanged. */
final class CircularIconView extends ImageView {
  boolean appRunning, stateKnown = true;
  final android.graphics.Paint statePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  android.animation.ValueAnimator pulse;
  float ringAlpha = 1f;

  void stopPulse() {
    if (pulse != null) { pulse.cancel(); pulse = null; }
    ringAlpha = 1f;
    invalidate();
  }

  void setAppState(boolean running, boolean known, boolean breathe) {
    appRunning = running;
    stateKnown = known;
    if (!running || !known || !breathe) stopPulse();
    else if (pulse == null) {
      pulse = android.animation.ValueAnimator.ofFloat(.75f, 1f);
      pulse.setDuration(1800);
      pulse.setRepeatCount(-1);
      pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
      pulse.addUpdateListener(a -> { ringAlpha = (Float) a.getAnimatedValue(); invalidate(); });
      pulse.start();
    }
    invalidate();
  }

  protected void onDetachedFromWindow() { stopPulse(); super.onDetachedFromWindow(); }

  CircularIconView(Context context) {
    super(context);
    setBackgroundColor(0xfff7f7f7);
    setOutlineProvider(new ViewOutlineProvider() {
      public void getOutline(View view, Outline outline) {
        int size = Math.min(view.getWidth(), view.getHeight());
        int left = (view.getWidth() - size) / 2;
        int top = (view.getHeight() - size) / 2;
        outline.setOval(left, top, left + size, top + size);
      }
    });
    setClipToOutline(true);
  }

  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    invalidateOutline();
  }

  protected void onDraw(Canvas canvas) {
    Drawable icon = getDrawable();
    int size = Math.min(getWidth(), getHeight());
    if (icon == null || size <= 0) return;
    int width = Math.max(1, icon.getIntrinsicWidth());
    int height = Math.max(1, icon.getIntrinsicHeight());
    float scale = Math.max(size / (float) width, size / (float) height);
    Rect bounds = new Rect(icon.getBounds());
    int saved = canvas.save();
    try {
      canvas.translate((getWidth() - width * scale) / 2f, (getHeight() - height * scale) / 2f);
      canvas.scale(scale, scale);
      icon.setBounds(0, 0, width, height);
      icon.draw(canvas);
    } finally {
      icon.setBounds(bounds);
      canvas.restoreToCount(saved);
    }
    if (appRunning || !stateKnown) {
      float stroke = 4f * getResources().getDisplayMetrics().density;
      statePaint.setStyle(android.graphics.Paint.Style.STROKE);
      statePaint.setStrokeWidth(stroke);
      statePaint.setColor(stateKnown ? 0xffffa340 : 0xff8a939f);
      statePaint.setAlpha((int)(255 * ringAlpha));
      canvas.drawCircle(getWidth()/2f, getHeight()/2f, size/2f - stroke/2f - 1, statePaint);
    }
  }
}
