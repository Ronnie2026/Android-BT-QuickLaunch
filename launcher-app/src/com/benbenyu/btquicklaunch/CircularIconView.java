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
  }
}
