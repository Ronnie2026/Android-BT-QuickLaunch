package com.benbenyu.btquicklaunch;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Original abstract artwork; no third-party images. */
public class NightWallpaper extends Drawable {
  final int style;
  final Paint paint = new Paint(3);

  public NightWallpaper(int s) {
    style = s;
  }

  public void draw(Canvas canvas) {
    Rect b = getBounds();
    float w = b.width(), h = b.height();
    canvas.save();
    canvas.translate(b.left, b.top);
    int[] tones = {0xff22272d, 0xff20202b, 0xff122b3c, 0xff30281e};
    int tone = tones[Math.floorMod(style, 4)];
    paint.setShader(
        new LinearGradient(
            0, 0, w, h, new int[] {0xff030405, tone, 0xff050607}, null, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, w, h, paint);
    for (int i = 0; i < 4; i++) {
      Path p = new Path();
      float shift = i * h * .16f;
      p.moveTo(-w * .2f, h * .8f + shift);
      p.cubicTo(w * .25f, -h * .2f + shift, w * .45f, h * 1.1f + shift, w * 1.2f, h * .1f + shift);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(h * (.16f - i * .025f));
      paint.setShader(
          new LinearGradient(
              0,
              h,
              w,
              0,
              new int[] {0x00000000, (tone & 0xffffff) | 0x50000000, 0x00000000},
              null,
              Shader.TileMode.CLAMP));
      canvas.drawPath(p, paint);
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(
        new RadialGradient(
            w * .5f,
            h * .5f,
            Math.max(w, h) * .7f,
            new int[] {0x00000000, 0xb0000000},
            null,
            Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, w, h, paint);
    paint.setShader(null);
    canvas.restore();
  }

  public void setAlpha(int a) {}

  public void setColorFilter(ColorFilter f) {}

  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }
}
