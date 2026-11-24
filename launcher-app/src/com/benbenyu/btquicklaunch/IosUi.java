package com.benbenyu.btquicklaunch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** iOS Inset-Grouped Settings UI helpers. */
final class IosUi {
  static int dp(Context c, int v) {
    return (int) (v * c.getResources().getDisplayMetrics().density);
  }

  static int bg(boolean paper) {
    return paper ? 0xfff2f2f7 : 0xff000000;
  }

  static int cardColor(boolean paper) {
    return paper ? 0xffffffff : 0xff1c1c1e;
  }

  static int pillColor(boolean paper) {
    return paper ? 0xffe5e5ea : 0xff2c2c2e;
  }

  static int textPrimary(boolean paper) {
    return paper ? 0xff1c1c1e : 0xffffffff;
  }

  static int textSecondary(boolean paper) {
    return paper ? 0xff6c6c70 : 0xff8e8e93;
  }

  static int dividerColor(boolean paper) {
    return paper ? 0xffd1d1d6 : 0xff38383a;
  }

  static int accent(boolean paper) {
    return paper ? 0xff007aff : 0xff0a84ff;
  }

  static GradientDrawable roundRect(int color, int radiusDp, Context c) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radiusDp));
    return d;
  }

  static TextView header(Context c, String title, boolean paper) {
    TextView tv = new TextView(c);
    tv.setText(title);
    tv.setTextSize(13);
    tv.setTypeface(Typeface.DEFAULT_BOLD);
    tv.setTextColor(textSecondary(paper));
    tv.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 6));
    return tv;
  }

  static TextView footer(Context c, String text, boolean paper) {
    TextView tv = new TextView(c);
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(textSecondary(paper));
    tv.setLineSpacing(dp(c, 2), 1f);
    tv.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 4));
    return tv;
  }

  static LinearLayout card(Context c, boolean paper) {
    LinearLayout box = new LinearLayout(c);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setBackground(roundRect(cardColor(paper), 14, c));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(c, 4), 0, dp(c, 4), 0);
    box.setLayoutParams(lp);
    return box;
  }

  static View divider(Context c, boolean paper) {
    View v = new View(c);
    v.setBackgroundColor(dividerColor(paper));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1) / 2));
    lp.setMargins(dp(c, 16), 0, 0, 0);
    v.setLayoutParams(lp);
    return v;
  }

  static void styleSwitch(Context c, Switch sw, boolean paper) {
    sw.setText("");
    GradientDrawable onTrack = new GradientDrawable();
    onTrack.setColor(paper ? 0xff34c759 : 0xff30d158);
    onTrack.setCornerRadius(dp(c, 15));
    onTrack.setSize(dp(c, 48), dp(c, 28));
    GradientDrawable offTrack = new GradientDrawable();
    offTrack.setColor(paper ? 0xffe5e5ea : 0xff39393d);
    offTrack.setCornerRadius(dp(c, 15));
    offTrack.setSize(dp(c, 48), dp(c, 28));
    StateListDrawable track = new StateListDrawable();
    track.addState(new int[] {android.R.attr.state_checked}, onTrack);
    track.addState(new int[] {}, offTrack);
    sw.setTrackDrawable(track);

    GradientDrawable thumb = new GradientDrawable();
    thumb.setShape(GradientDrawable.OVAL);
    thumb.setColor(Color.WHITE);
    thumb.setSize(dp(c, 24), dp(c, 24));
    thumb.setStroke(dp(c, 2), Color.TRANSPARENT);
    sw.setThumbDrawable(thumb);
    sw.setSwitchMinWidth(dp(c, 48));
  }

  static LinearLayout switchRow(
      Context c,
      String title,
      boolean checked,
      boolean paper,
      Runnable onInfo,
      android.widget.CompoundButton.OnCheckedChangeListener listener) {
    LinearLayout row = new LinearLayout(c);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(c, 50));
    row.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
    if (onInfo != null) {
      TextView info = new TextView(c);
      info.setText("ⓘ");
      info.setTextSize(17);
      info.setTextColor(accent(paper));
      info.setPadding(0, dp(c, 4), dp(c, 10), dp(c, 4));
      info.setContentDescription(title + "说明");
      info.setOnClickListener(v -> onInfo.run());
      row.addView(info);
    }
    TextView label = new TextView(c);
    label.setText(title);
    label.setTextSize(16);
    label.setTextColor(textPrimary(paper));
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    Switch sw = new Switch(c);
    styleSwitch(c, sw, paper);
    sw.setChecked(checked);
    sw.setOnCheckedChangeListener(listener);
    row.addView(sw);
    return row;
  }

  static LinearLayout disclosureRow(
      Context c, String title, String value, boolean paper, boolean blueTitle, Runnable action) {
    LinearLayout row = new LinearLayout(c);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(dp(c, 50));
    row.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
    TextView label = new TextView(c);
    label.setText(title);
    label.setTextSize(16);
    label.setTextColor(blueTitle ? accent(paper) : textPrimary(paper));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
    row.addView(label, lp);
    if (value != null && !value.isEmpty()) {
      TextView val = new TextView(c);
      val.setText(value);
      val.setTextSize(14);
      val.setGravity(Gravity.END);
      val.setMaxWidth(c.getResources().getDisplayMetrics().widthPixels * 46 / 100);
      val.setEllipsize(android.text.TextUtils.TruncateAt.END);
      val.setTextColor(textSecondary(paper));
      val.setPadding(dp(c, 8), 0, dp(c, 6), 0);
      row.addView(val);
    }
    TextView chev = new TextView(c);
    chev.setText("›");
    chev.setTextSize(20);
    chev.setTextColor(textSecondary(paper));
    row.addView(chev);
    row.setOnClickListener(v -> action.run());
    return row;
  }
}
