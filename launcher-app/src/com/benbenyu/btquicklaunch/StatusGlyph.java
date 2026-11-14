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
    canvas.scale(size / 48, size / 48);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.2f);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setColor(paper ? 0xff454545 : 0xffb5bdc9);
    if (type == 3) {
      canvas.scale(.72f, .72f, 24, 24);
      p.setStrokeWidth(1.8f);
      p.setColor(paper ? 0xff454545 : 0xffb5bdc9);
      canvas.drawRoundRect(1, 17, 29, 31, 3, 3, p);
      p.setStyle(Paint.Style.FILL);
      canvas.drawRoundRect(31, 21, 33, 27, 1, 1, p);
      p.setColor(
          paper ? 0xff454545 : level <= 20 ? 0xfff06b66 : charging ? 0xff63dba1 : 0xffb5bdc9);
      canvas.drawRoundRect(4, 20, 4 + 22 * Math.max(0, Math.min(level, 100)) / 100f, 28, 1, 1, p);
      if (charging) {
        p.setColor(paper ? 0xffe5e2d9 : 0xff101318);
        Path bolt = new Path();
        bolt.moveTo(17, 19);
        bolt.lineTo(12, 25);
        bolt.lineTo(16, 25);
        bolt.lineTo(14, 30);
        bolt.lineTo(20, 23);
        bolt.lineTo(16, 23);
        bolt.close();
        canvas.drawPath(bolt, p);
      }
      p.setStyle(Paint.Style.STROKE);
      p.setColor(paper ? 0xff454545 : connected ? 0xff63dba1 : 0xfff06b66);
      Path bt = new Path();
      bt.moveTo(39, 15);
      bt.lineTo(46, 20);
      bt.lineTo(35, 29);
      bt.moveTo(39, 34);
      bt.lineTo(46, 29);
      bt.lineTo(35, 20);
      bt.moveTo(39, 15);
      bt.lineTo(39, 34);
      canvas.drawPath(bt, p);
    } else if (type == 0) {
      p.setColor(level <= 20 ? 0xfff06b66 : 0xffb5bdc9);
      canvas.drawRoundRect(5, 16, 40, 33, 4, 4, p);
      p.setStyle(Paint.Style.FILL);
      canvas.drawRoundRect(42, 21, 44, 28, 1, 1, p);
      p.setColor(charging ? 0xff63dba1 : level <= 20 ? 0xfff06b66 : 0xffb5bdc9);
      canvas.drawRoundRect(8, 19, 8 + 29 * Math.max(0, Math.min(level, 100)) / 100f, 30, 2, 2, p);
      if (charging) {
        p.setColor(0xff101318);
        Path bolt = new Path();
        bolt.moveTo(25, 18);
        bolt.lineTo(19, 25);
        bolt.lineTo(24, 25);
        bolt.lineTo(21, 31);
        bolt.lineTo(29, 23);
        bolt.lineTo(24, 23);
        bolt.close();
        canvas.drawPath(bolt, p);
      }
    } else if (type == 1) {
      p.setColor(connected ? 0xff63dba1 : 0xfff06b66);
      Path path = new Path();
      path.moveTo(23, 9);
      path.lineTo(32, 17);
      path.lineTo(16, 32);
      path.moveTo(23, 39);
      path.lineTo(32, 31);
      path.lineTo(16, 16);
      path.moveTo(23, 9);
      path.lineTo(23, 39);
      canvas.drawPath(path, p);
    } else {
      canvas.scale(.62f, .62f, 24, 24);
      canvas.drawCircle(24, 24, 11, p);
      canvas.drawCircle(24, 24, 4, p);
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI * i / 4;
        canvas.drawLine(
            24 + (float) Math.cos(angle) * 12,
            24 + (float) Math.sin(angle) * 12,
            24 + (float) Math.cos(angle) * 15,
            24 + (float) Math.sin(angle) * 15,
            p);
      }
    }
    canvas.restore();
  }
}
