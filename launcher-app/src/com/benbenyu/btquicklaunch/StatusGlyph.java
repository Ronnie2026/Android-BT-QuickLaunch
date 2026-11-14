package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class StatusGlyph extends View {
  public int level = 0;
  public boolean charging = false, connected = false, paper = false;
  final int type;
  final Paint p = new Paint(3);

  public StatusGlyph(Context c, int t) {
    super(c);
    type = t;
  }

  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float size = Math.min(getWidth(), getHeight());
    canvas.save();
    canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
    canvas.scale(size / 36f, size / 36f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.0f);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setColor(paper ? 0xff454545 : 0xffb5bdc9);
    if (type == 0) {
      int outline =
          paper ? 0xff454545 : level <= 20 ? 0xfff06b66 : charging ? 0xff63dba1 : 0xffb5bdc9;
      p.setColor(outline);
      canvas.drawRoundRect(4f, 10f, 29f, 26f, 2.5f, 2.5f, p);
      p.setStyle(Paint.Style.FILL);
      canvas.drawRoundRect(30.5f, 14.5f, 32.5f, 21.5f, 1f, 1f, p);
      float fillWidth = 20f * Math.max(0, Math.min(level, 100)) / 100f;
      if (fillWidth > 0.5f) {
        canvas.drawRoundRect(6.5f, 12.5f, 6.5f + fillWidth, 23.5f, 1.2f, 1.2f, p);
      }
      if (charging) {
        Path bolt = new Path();
        bolt.moveTo(17.5f, 11.5f);
        bolt.lineTo(12.5f, 18.5f);
        bolt.lineTo(16.5f, 18.5f);
        bolt.lineTo(14.5f, 24.5f);
        bolt.lineTo(20.5f, 17.5f);
        bolt.lineTo(16.5f, 17.5f);
        bolt.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setColor(paper ? 0xffe5e2d9 : 0xff101318);
        canvas.drawPath(bolt, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(paper ? 0xff252525 : 0xffffffff);
        canvas.drawPath(bolt, p);
      }
    } else if (type == 1) {
      p.setColor(paper ? (connected ? 0xff252525 : 0xff8a8a8a) : (connected ? 0xff63dba1 : 0xfff06b66));
      Path path = new Path();
      path.moveTo(18f, 8f);
      path.lineTo(24f, 13.5f);
      path.lineTo(12.5f, 22.5f);
      path.moveTo(18f, 28f);
      path.lineTo(24f, 22.5f);
      path.lineTo(12.5f, 13.5f);
      path.moveTo(18f, 8f);
      path.lineTo(18f, 28f);
      canvas.drawPath(path, p);
    } else {
      canvas.drawCircle(18f, 18f, 6.8f, p);
      canvas.drawCircle(18f, 18f, 2.6f, p);
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI * i / 4;
        canvas.drawLine(
            18f + (float) Math.cos(angle) * 7.2f,
            18f + (float) Math.sin(angle) * 7.2f,
            18f + (float) Math.cos(angle) * 10f,
            18f + (float) Math.sin(angle) * 10f,
            p);
      }
    }
    canvas.restore();
  }
}
