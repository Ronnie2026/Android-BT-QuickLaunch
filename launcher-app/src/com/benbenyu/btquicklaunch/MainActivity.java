package com.benbenyu.btquicklaunch;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  FrameLayout desktopRoot, cleanFeedback;
  SystemTonePlayer cleanTone, tonePreview;
  final Handler feedbackHandler = new Handler();
  IdlePower idlePower;
  NetIndicators networkIndicators;
  View[] activityDots = new View[4];
  Handler statusHandler = new Handler();
  boolean desktopVisible = false, statusChecking = false;
  final Runnable statusTick = () -> refreshProcesses();

  void refreshProcesses() {
    if (!desktopVisible || statusChecking) return;
    statusChecking = true;
    String[] packages = new String[4];
    for (int i = 0; i < 4; i++) packages[i] = get(i, "pkg", pkgs[i]);
    new Thread(
            () -> {
              Set<String> running = java.util.concurrent.ConcurrentHashMap.newKeySet();
              boolean valid = false;
              java.lang.Process proc = null;
              try {
                proc =
                    new ProcessBuilder("su", "-c", "ps -A -o NAME")
                        .redirectErrorStream(true)
                        .start();
                final java.lang.Process child = proc;
                Thread reader =
                    new Thread(
                        () -> {
                          try {
                            java.io.BufferedReader r =
                                new java.io.BufferedReader(
                                    new java.io.InputStreamReader(child.getInputStream()));
                            String line;
                            while ((line = r.readLine()) != null) running.add(line.trim());
                          } catch (Exception ignored) {
                          }
                        });
                reader.start();
                if (proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                  reader.join(500);
                  valid = proc.exitValue() == 0;
                } else proc.destroy();
              } catch (Exception ignored) {
              }
              final boolean ok = valid;
              statusHandler.post(
                  () -> {
                    statusChecking = false;
                    if (!desktopVisible) return;
                    for (int i = 0; i < 4; i++) {
                      if (!packages[i].equals(get(i, "pkg", pkgs[i]))) continue;
                      boolean active = false;
                      if (ok)
                        for (String name : running)
                          if (name.equals(packages[i]) || name.startsWith(packages[i] + ":")) {
                            active = true;
                            break;
                          }
                      if (activityDots[i] != null) {
                        if (active
                            && p.getBoolean("breathing", true)
                            && activityDots[i].getAnimation() == null) {
                          android.view.animation.AlphaAnimation pulse =
                              new android.view.animation.AlphaAnimation(0.35f, 0.8f);
                          pulse.setDuration(2200);
                          pulse.setRepeatCount(-1);
                          pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
                          pulse.setInterpolator(
                              new android.view.animation.AccelerateDecelerateInterpolator());
                          activityDots[i].startAnimation(pulse);
                        } else if (!active || !p.getBoolean("breathing", true)) {
                          activityDots[i].clearAnimation();
                        }
                        if (!ok) {
                          activityDots[i].clearAnimation();
                          activityDots[i].setAlpha(0.35f);
                          activityDots[i].setBackgroundColor(0xff8a939f);
                          activityDots[i].setVisibility(View.VISIBLE);
                        } else {
                          activityDots[i].setAlpha(1f);
                          activityDots[i].setBackgroundColor(0xffffa340);
                          activityDots[i].setVisibility(active ? View.VISIBLE : View.INVISIBLE);
                        }
                        activityDots[i].setContentDescription(
                            !ok ? "后台状态检测失败" : active ? "应用后台进程运行中" : "应用未运行");
                      }
                    }
                    statusHandler.postDelayed(statusTick, idlePower!=null&&idlePower.dimmed?30000:5000);
                  });
            })
        .start();
  }

  String packagesIndex(String[] a, int i) {
    return a[i];
  }

  StatusGlyph batteryText;
  boolean batteryRegistered;
  final BroadcastReceiver batteryReceiver =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent in) {
          if (batteryText == null) return;
          int level = in.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1),
              scale = in.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
          int percent = level < 0 ? -1 : level * 100 / Math.max(1, scale);
          boolean charging = in.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
          batteryText.level = Math.max(0, percent);
          batteryText.charging = charging;
          batteryText.invalidate();
          batteryText.setContentDescription("电量 " + percent + "%" + (charging ? "，正在充电" : ""));
        }
      };
  StatusGlyph bluetoothIcon;
  android.hardware.input.InputManager inputManager;
  final android.hardware.input.InputManager.InputDeviceListener deviceListener =
      new android.hardware.input.InputManager.InputDeviceListener() {
        public void onInputDeviceAdded(int id) {
          updateBluetooth();
        }

        public void onInputDeviceRemoved(int id) {
          updateBluetooth();
        }

        public void onInputDeviceChanged(int id) {
          updateBluetooth();
        }
      };

  void updateBluetooth() {
    if (bluetoothIcon == null) return;
    boolean connected = false;
    int hidState = HidConnectionManager.currentState;
    if (hidState >= 0) {
      connected = hidState == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
    } else {
      for (int id : android.view.InputDevice.getDeviceIds()) {
        android.view.InputDevice d = android.view.InputDevice.getDevice(id);
        if (d != null && d.isExternal() && d.getName().equalsIgnoreCase("COIDEA KM")) {
          connected = true;
          break;
        }
      }
    }
    bluetoothIcon.connected = connected;
    bluetoothIcon.level = connected ? HidConnectionManager.batteryLevel : -1;
    bluetoothIcon.invalidate();
    bluetoothIcon.setContentDescription(
        connected
            ? ("蓝牙键盘已连接"
                + (bluetoothIcon.level >= 0 ? (" 电量 " + bluetoothIcon.level + "%") : ""))
            : "蓝牙键盘已断开");
  }

  protected void onResume() {
    super.onResume();
    if(idlePower==null)idlePower=new IdlePower(this);idlePower.start();
    if (networkIndicators != null) networkIndicators.start();
    desktopVisible = true;
    statusHandler.removeCallbacks(statusTick);
    statusHandler.post(statusTick);
    if (!batteryRegistered) {
      registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    inputManager = (android.hardware.input.InputManager) getSystemService(INPUT_SERVICE);
    inputManager.registerInputDeviceListener(deviceListener, new Handler());
    HidConnectionManager.onStateChanged = () -> runOnUiThread(this::updateBluetooth);
    updateBluetooth();
    getWindow().getDecorView().setSystemUiVisibility(5894);
    applyBrightness();
  }

  protected void onPause() {
    stopTonePreview();
    clearCleanFeedback();
    if(idlePower!=null)idlePower.stop();
    if (networkIndicators != null) networkIndicators.stop();
    desktopVisible = false;
    for (View bar : activityDots) if (bar != null) bar.clearAnimation();
    statusHandler.removeCallbacks(statusTick);
    if (batteryRegistered) {
      unregisterReceiver(batteryReceiver);
      batteryRegistered = false;
    }
    if (inputManager != null) inputManager.unregisterInputDeviceListener(deviceListener);
    HidConnectionManager.onStateChanged = null;
    super.onPause();
  }

  android.content.SharedPreferences p;
  Handler h = new Handler();
  final java.util.concurrent.ThreadPoolExecutor networkWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
  int launchGeneration = 0;
  boolean cleaning = false;
  int queuedSlot = -1;
  boolean busy = false;
  long lastCleanTap = 0;
  String[] names = {"GPT", "DIIPlay", "高德地图", "豆包"},
      pkgs = {"com.openai.chatgpt", "com.shihab.diplay", "com.autonavi.minimap", "com.larus.nova"};

  String get(int i, String k, String d) {
    return p.getString(i + k, d);
  }

  int dp(int x) {
    return (int) (x * getResources().getDisplayMetrics().density);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setFlags(1024, 1024);
    getWindow().addFlags(128);
    setShowWhenLocked(true);
    getWindow().getDecorView().setSystemUiVisibility(5894);
    p = getSharedPreferences("home", 0);
    if (b == null && p.getBoolean("disable_ime", true)) ImeGuard.apply(this, p, true, false);
    draw();
    if (getIntent().hasExtra("button_action")) button(getIntent());
    else {
      int boot = android.provider.Settings.Global.getInt(getContentResolver(), "boot_count", -1);
      if (b == null && p.getInt("auto_boot", -2) != boot) {
        p.edit().putInt("auto_boot", boot).apply();
        h.postDelayed(
            () -> {
              for (int i = 0; i < 4; i++)
                if (get(i, "pkg", pkgs[i]).equals("com.openai.chatgpt")) {
                  if (!needsProxy(i, "com.openai.chatgpt")) ensureProxyOnStart();
                  launch(i); // a guarded GPT launch already ensures the proxy
                  return;
                }
              ensureProxyOnStart();
            },
            800);
      } else if (b == null) ensureProxyOnStart();
    }
  }

  public void onBackPressed() {}

  protected void onNewIntent(Intent in) {
    super.onNewIntent(in);
    setIntent(in);
    if (in.hasExtra("button_action")) button(in);
    else cancelLaunch();
  }

  void button(Intent in) {
    cancelLaunch();
    int n = in.getIntExtra("button_action", -1);
    in.removeExtra("button_action");
    if (n == 3) clean();
    else if (n >= 0 && n < 3) launch(n);
  }

  TextView text(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(p != null && p.getBoolean("paper_theme", false) ? 0xff252525 : Color.WHITE);
    t.setPadding(dp(12), dp(12), dp(12), dp(12));
    return t;
  }

  public boolean dispatchTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN&&idlePower!=null)idlePower.activity("touch");return super.dispatchTouchEvent(e);}
  void draw() {
    clearCleanFeedback();
    if (networkIndicators != null) networkIndicators.stop();
    FrameLayout root = new FrameLayout(this);
    desktopRoot = root;
    root.setBackgroundColor(Color.BLACK);
    setContentView(root);
    background(root);
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER);
    row.setPadding(dp(16), dp(52), dp(16), dp(12));
    root.addView(row, new FrameLayout.LayoutParams(-1, -1));
    for (int i = 0; i < 5; i++) {
      final int n = i;
      FrameLayout cell = new FrameLayout(this);
      row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
      ImageView icon = i < 4 ? new CircularIconView(this) : new ImageView(this);
      icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      icon.setContentDescription(i == 4 ? "清后台" : get(i, "name", names[i]));
      try {
        if (i == 4) throw new Exception();
        icon.setImageDrawable(slotIcon(i));
      } catch (Exception e) {
        icon.setImageResource(
            i == 4
                ? android.R.drawable.ic_menu_close_clear_cancel
                : android.R.drawable.sym_def_app_icon);
      }
      if (p.getBoolean("paper_theme", false)) {
        android.graphics.ColorMatrix mono = new android.graphics.ColorMatrix();
        mono.setSaturation(0);
        icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(mono));
      }
      FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
      ip.setMargins(dp(18), dp(30), dp(18), dp(30));
      cell.addView(icon, ip);
      if (i < 4) {
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setCornerRadius(dp(2));
        dotBg.setColor(0xffffa340);
        dot.setBackground(dotBg);
        dot.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams dotPos =
            new FrameLayout.LayoutParams(dp(48), dp(3), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotPos.bottomMargin = dp(14);
        cell.addView(dot, dotPos);
        icon.addOnLayoutChangeListener(
            (v, l, t, r, b, ol, ot, orr, ob) -> {
              android.graphics.drawable.Drawable image = icon.getDrawable();
              if (image != null
                  && image.getIntrinsicWidth() > 0
                  && image.getIntrinsicHeight() > 0) {
                float bottom = icon.getTop()
                    + (icon.getHeight() + Math.min(icon.getWidth(), icon.getHeight())) / 2f;
                dot.setY(Math.min(cell.getHeight() - dp(8), bottom + dp(10)));
              }
            });
        activityDots[i] = dot;
      }
      cell.setOnLongClickListener(
          v -> {
            if (n < 4) reorder(n);
            else clean();
            return true;
          });
      cell.setOnClickListener(
          v -> {
            if (n == 4) {
              long now = android.os.SystemClock.elapsedRealtime();
              if (lastCleanTap != 0 && now - lastCleanTap < 450) {
                lastCleanTap = 0;
                clean();
              } else lastCleanTap = now;
            } else launch(n);
          });
    }
    LinearLayout statusBar = new LinearLayout(this);
    statusBar.setGravity(Gravity.CENTER_VERTICAL);
    FrameLayout.LayoutParams statusParams =
        new FrameLayout.LayoutParams(-2, dp(36), Gravity.TOP | Gravity.RIGHT);
    statusParams.topMargin = dp(10);
    statusParams.rightMargin = dp(12);
    root.addView(statusBar, statusParams);

    boolean paper = p.getBoolean("paper_theme", false);
    networkIndicators = new NetIndicators(this);
    statusBar.addView(networkIndicators, new LinearLayout.LayoutParams(0, dp(36)));

    batteryText = new StatusGlyph(this, 0);
    batteryText.paper = paper;
    LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(dp(36), dp(36));
    batteryParams.leftMargin = dp(4);
    statusBar.addView(batteryText, batteryParams);

    bluetoothIcon = new StatusGlyph(this, 1);
    bluetoothIcon.paper = paper;
    LinearLayout.LayoutParams btParams = new LinearLayout.LayoutParams(dp(36), dp(36));
    btParams.leftMargin = dp(4);
    statusBar.addView(bluetoothIcon, btParams);

    StatusGlyph gear = new StatusGlyph(this, 2);
    gear.paper = paper;
    gear.setContentDescription("桌面设置");
    LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(36), dp(36));
    gp.leftMargin = dp(4);
    statusBar.addView(gear, gp);
    if (desktopVisible) networkIndicators.start();
    Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (battery != null) batteryReceiver.onReceive(this, battery);
    updateBluetooth();
    View idleShade=new View(this);idleShade.setBackgroundColor(Color.BLACK);idleShade.setAlpha(0f);idleShade.setClickable(false);root.addView(idleShade,new FrameLayout.LayoutParams(-1,-1));if(idlePower==null)idlePower=new IdlePower(this);idlePower.attach(idleShade);
    gear.setOnClickListener(v -> settingsPanel());
    if (p.getBoolean("paper_theme", false)) {
      android.graphics.ColorMatrix gray = new android.graphics.ColorMatrix();
      gray.setSaturation(0);
      android.graphics.Paint filter = new android.graphics.Paint();
      filter.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
      root.setLayerType(View.LAYER_TYPE_HARDWARE, filter);
    }
  }

  int pendingIcon = -1;

  void detailPage(String title) {
    cancelLaunch();
    if (settingsDialog == null || !settingsDialog.isShowing()) settingsPanel();
    settingsContent.removeAllViews();
    boolean paper = p.getBoolean("paper_theme", false);
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(0, 0, 0, dp(8));
    TextView back = new TextView(this);
    back.setText("‹ 返回");
    back.setTextSize(16);
    back.setTextColor(IosUi.accent(paper));
    back.setPadding(dp(8), dp(8), dp(14), dp(8));
    back.setOnClickListener(v -> settingsPanel());
    header.addView(back);
    TextView titleView = new TextView(this);
    titleView.setText(title);
    titleView.setTextSize(18);
    titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    titleView.setTextColor(IosUi.textPrimary(paper));
    header.addView(titleView);
    settingsContent.addView(header);
  }

  void option(String name, Runnable action) {
    boolean paper = p.getBoolean("paper_theme", false);
    LinearLayout card = IosUi.card(this, paper);
    card.addView(IosUi.disclosureRow(this, name, "", paper, false, action));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(4), dp(4), dp(4), dp(4));
    settingsContent.addView(card, lp);
  }

  void iconMenu(int slot) {
    detailPage("位置 " + (slot + 1) + " · 图标外观");
    option(
        "从相册选择图标",
        () -> {
          pendingIcon = slot;
          try {
            startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                92);
          } catch (Exception e) {
            Toast.makeText(this, "无法打开相册", 0).show();
          }
        });
    option(
        "恢复应用默认图标",
        () -> {
          p.edit().remove(slot + "icon").apply();
          draw();
          settingsPanel();
        });
    option("应用启动设置", () -> configure(slot));
  }

  android.graphics.drawable.Drawable slotIcon(int slot) {
    String file = p.getString(slot + "icon", "");
    if (!file.isEmpty()) {
      android.graphics.drawable.Drawable custom =
          android.graphics.drawable.Drawable.createFromPath(file);
      if (custom != null) return custom;
    }
    try {
      return getPackageManager().getApplicationIcon(get(slot, "pkg", pkgs[slot]));
    } catch (Exception e) {
      return getResources().getDrawable(android.R.drawable.sym_def_app_icon);
    }
  }

  int settingsTab = 0;
  Dialog settingsDialog;
  LinearLayout settingsContent;

  void settingsPanel() {
    cancelLaunch();
    if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
    final Dialog dialog = new Dialog(this){public boolean dispatchTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN&&idlePower!=null)idlePower.activity("settings_touch");return super.dispatchTouchEvent(e);}};
    settingsDialog = dialog;
    boolean paper = p.getBoolean("paper_theme", false);
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(1);
    panel.setPadding(dp(16), dp(10), dp(16), dp(10));
    panel.setBackgroundColor(IosUi.bg(paper));

    LinearLayout topBar = new LinearLayout(this);
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setPadding(dp(4), dp(2), dp(4), dp(10));

    LinearLayout seg = new LinearLayout(this);
    seg.setPadding(dp(3), dp(3), dp(3), dp(3));
    seg.setBackground(IosUi.roundRect(IosUi.pillColor(paper), 12, this));
    String[] titles = {"启动应用", "壁纸", "通用设置"};
    for (int i = 0; i < 3; i++) {
      final int n = i;
      TextView tab = new TextView(this);
      tab.setText(titles[i]);
      tab.setTextSize(15);
      tab.setTypeface(i == settingsTab ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
      tab.setGravity(Gravity.CENTER);
      tab.setTextColor(i == settingsTab ? IosUi.textPrimary(paper) : IosUi.textSecondary(paper));
      if (i == settingsTab)
        tab.setBackground(IosUi.roundRect(paper ? 0xffffffff : 0xff48484a, 9, this));
      seg.addView(tab, new LinearLayout.LayoutParams(0, dp(36), 1));
      tab.setOnClickListener(
          v -> {
            stopTonePreview();
            settingsTab = n;
            for (int j = 0; j < 3; j++) {
              TextView t = (TextView) seg.getChildAt(j);
              t.setTypeface(j == n ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
              t.setTextColor(j == n ? IosUi.textPrimary(paper) : IosUi.textSecondary(paper));
              t.setBackground(
                  j == n ? IosUi.roundRect(paper ? 0xffffffff : 0xff48484a, 9, this) : null);
            }
            settingsContent.removeAllViews();
            if (n == 0) fillApps(settingsContent, dialog);
            else if (n == 1) fillWallpapers(settingsContent);
            else fillOther(settingsContent);
          });
    }
    topBar.addView(seg, new LinearLayout.LayoutParams(0, -2, 1));
    TextView done = new TextView(this);
    done.setText("完成");
    done.setTextSize(16);
    done.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    done.setTextColor(IosUi.accent(paper));
    done.setPadding(dp(18), dp(8), dp(6), dp(8));
    done.setOnClickListener(v -> dialog.dismiss());
    topBar.addView(done);
    panel.addView(topBar);

    settingsContent = new LinearLayout(this);
    settingsContent.setOrientation(1);
    panel.addView(settingsContent, new LinearLayout.LayoutParams(-1, 0, 1));
    if (settingsTab == 0) fillApps(settingsContent, dialog);
    else if (settingsTab == 1) fillWallpapers(settingsContent);
    else fillOther(settingsContent);
    dialog.setOnDismissListener(ignored -> stopTonePreview());
    dialog.setContentView(panel);
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    dialog.show();
    dialog.getWindow().setLayout(-1, -1);
    dialog.getWindow().getDecorView().setSystemUiVisibility(5894);
  }

  void fillApps(LinearLayout grid, Dialog dialog) {
    boolean paper = p.getBoolean("paper_theme", false);
    for (int row = 0; row < 2; row++) {
      LinearLayout line = new LinearLayout(this);
      grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
      for (int col = 0; col < 2; col++) {
        final int n = row * 2 + col;
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(10), dp(14), dp(10));
        card.setBackground(IosUi.roundRect(IosUi.cardColor(paper), 18, this));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        cp.setMargins(dp(5), dp(5), dp(5), dp(5));
        line.addView(card, cp);
        ImageView icon = new CircularIconView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
          icon.setImageDrawable(slotIcon(n));
        } catch (Exception ignored) {
          icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        if (paper) {
          android.graphics.ColorMatrix gray = new android.graphics.ColorMatrix();
          gray.setSaturation(0);
          icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
        }
        card.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(1);
        info.setPadding(dp(14), 0, dp(8), 0);
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView label = new TextView(this);
        label.setText("位置 " + (n + 1) + " · " + get(n, "name", names[n]));
        label.setTextSize(17);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setTextColor(IosUi.textPrimary(paper));
        info.addView(label);
        String pkgName = get(n, "pkg", pkgs[n]);
        TextView hint = new TextView(this);
        hint.setText(
            supportsVoice(pkgName) && p.getBoolean(n + "voice", true)
                ? (needsProxy(n, pkgName) ? "VPN 检测 · 自动语音" : "自动进入语音通话")
                : needsProxy(n, pkgName) ? "VPN 检测 · 点击更换" : "点击更换应用");
        hint.setTextSize(13);
        hint.setTextColor(IosUi.textSecondary(paper));
        hint.setPadding(0, dp(2), 0, 0);
        info.addView(hint);
        card.setOnClickListener(
            v -> {
              dialog.dismiss();
              pick(n, false);
            });
        TextView more = new TextView(this);
        more.setText("⋯");
        more.setTextSize(20);
        more.setGravity(Gravity.CENTER);
        more.setTextColor(IosUi.accent(paper));
        more.setBackground(IosUi.roundRect(IosUi.pillColor(paper), 16, this));
        more.setContentDescription("位置" + (n + 1) + "高级设置");
        more.setOnClickListener(
            v -> {
              dialog.dismiss();
              iconMenu(n);
            });
        card.addView(more, new LinearLayout.LayoutParams(dp(36), dp(36)));
      }
    }
  }

  void paperStyle(View view) {
    if (view instanceof TextView) {
      ((TextView) view).setTextColor(0xff252525);
      if (view.getBackground() != null) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xffd6d3cb);
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
      }
    } else if (view instanceof ImageView) {
      android.graphics.ColorMatrix gray = new android.graphics.ColorMatrix();
      gray.setSaturation(0);
      ((ImageView) view).setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
    } else if (view.getBackground() != null) {
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(0xffe5e2d9);
      bg.setCornerRadius(dp(18));
      view.setBackground(bg);
    }
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int j = 0; j < group.getChildCount(); j++) paperStyle(group.getChildAt(j));
    }
  }

  void openClassicHome() {
    if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
    Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    java.util.List<android.content.pm.ResolveInfo> homes =
        getPackageManager().queryIntentActivities(query, 0);
    for (android.content.pm.ResolveInfo info : homes) {
      String pkg = info.activityInfo.packageName;
      if (pkg.equals(getPackageName()) || pkg.equals("com.android.settings")) continue;
      Intent in =
          new Intent(Intent.ACTION_MAIN)
              .addCategory(Intent.CATEGORY_HOME)
              .setClassName(pkg, info.activityInfo.name)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        startActivity(in);
        return;
      } catch (Exception ignored) {
      }
    }
    try {
      startActivity(
          new Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (Exception e) {
      Toast.makeText(this, "未找到系统经典桌面", 1).show();
    }
  }

  void applyBrightness() {
    android.view.WindowManager.LayoutParams attrs = getWindow().getAttributes();
    int value = p.getInt("brightness", 0);
    attrs.screenBrightness = value == 0 ? -1f : Math.max(.05f, value / 100f);
    getWindow().setAttributes(attrs);
  }

  void fillOther(LinearLayout content) {
    boolean paper = p.getBoolean("paper_theme", false);
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(1);
    body.setPadding(0, 0, 0, dp(24));
    scroll.addView(body);
    content.addView(scroll);

    body.addView(IosUi.header(this, "外观与显示", paper));
    LinearLayout appCard = IosUi.card(this, paper);
    appCard.addView(
        IosUi.switchRow(
            this,
            "电子书纸白主题",
            paper,
            paper,
            null,
            (v, on) -> {
              p.edit().putBoolean("paper_theme", on).apply();
              draw();
              settingsPanel();
            }));
    appCard.addView(IosUi.divider(this, paper));
    appCard.addView(
        IosUi.switchRow(
            this,
            "2 分钟无操作自动调暗",
            p.getBoolean("idle_power", true),
            paper,
            null,
            (v, on) -> {
              p.edit().putBoolean("idle_power", on).apply();
              if (idlePower != null) {
                idlePower.setDim(false);
                idlePower.activity("setting");
              }
            }));
    appCard.addView(IosUi.divider(this, paper));
    appCard.addView(
        IosUi.switchRow(
            this,
            "后台横线呼吸动画",
            p.getBoolean("breathing", true),
            paper,
            null,
            (v, on) -> {
              p.edit().putBoolean("breathing", on).apply();
              draw();
            }));
    appCard.addView(IosUi.divider(this, paper));
    LinearLayout brightRow = new LinearLayout(this);
    brightRow.setOrientation(1);
    brightRow.setPadding(dp(16), dp(12), dp(16), dp(12));
    LinearLayout brightTop = new LinearLayout(this);
    brightTop.setGravity(Gravity.CENTER_VERTICAL);
    TextView brightTitle = new TextView(this);
    brightTitle.setText("桌面亮度");
    brightTitle.setTextSize(16);
    brightTitle.setTextColor(IosUi.textPrimary(paper));
    brightTop.addView(brightTitle, new LinearLayout.LayoutParams(0, -2, 1));
    int curB = p.getInt("brightness", 0);
    TextView brightVal = new TextView(this);
    brightVal.setText(curB == 0 ? "跟随系统" : curB + "%");
    brightVal.setTextSize(15);
    brightVal.setTextColor(IosUi.textSecondary(paper));
    brightTop.addView(brightVal);
    brightRow.addView(brightTop);
    SeekBar slider = new SeekBar(this);
    slider.setMax(100);
    slider.setProgress(curB);
    slider.setPadding(0, dp(8), 0, 0);
    brightRow.addView(slider);
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar v) {}

          public void onStopTrackingTouch(SeekBar v) {}

          public void onProgressChanged(SeekBar v, int n, boolean user) {
            if (user) {
              p.edit().putInt("brightness", n).apply();
              applyBrightness();
              brightVal.setText(n == 0 ? "跟随系统" : n + "%");
            }
          }
        });
    appCard.addView(brightRow);
    body.addView(appCard);
    body.addView(IosUi.footer(this, "底部横线状态：橙色表示运行中，灰色表示检测失败，无横线表示未运行。", paper));

    body.addView(IosUi.header(this, "输入法与语音", paper));
    LinearLayout imeCard = IosUi.card(this, paper);
    imeCard.addView(
        IosUi.switchRow(
            this,
            "完全禁用输入法（纯语音）",
            p.getBoolean("disable_ime", true),
            paper,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("完全禁用输入法")
                    .setMessage(
                        "开启后（默认开启）：\n"
                            + "• 自动禁用系统所有软键盘输入法，保持纯粹的语音交互体验。\n"
                            + "• 防止进入 Gemini / ChatGPT 等应用时自动弹出输入法，避免键盘遮挡底部的语音按钮，大幅提高自动模拟点击进入语音模式的成功率。\n\n"
                            + "需要打字时：\n"
                            + "• 临时关闭此开关即可自动恢复原输入法，进入应用后即可正常使用键盘。")
                    .setPositiveButton("知道了", null)
                    .show(),
            (v, on) -> {
              p.edit().putBoolean("disable_ime", on).apply();
              ImeGuard.apply(this, p, on, true);
            }));
    body.addView(imeCard);
    body.addView(IosUi.footer(this, "禁用输入法有助于提高按钮模拟点击的成功率，避免输入法弹出遮挡语音按钮。", paper));

    body.addView(IosUi.header(this, "清理后台反馈", paper));
    LinearLayout cleanCard = IosUi.card(this, paper);
    cleanCard.addView(IosUi.switchRow(this, "清理后台成功提示音", p.getBoolean("clean_success_sound", true), paper, null,
        (v, on) -> {
          p.edit().putBoolean("clean_success_sound", on).apply();
          if (!on) releaseCleanTone();
        }));
    addToneRows(cleanCard, "clean_success_tone", paper);
    body.addView(cleanCard);
    body.addView(IosUi.footer(this, "成功后显示派大星和“搞定了”，提示音使用系统短音。关闭提示音仍显示图片；静音／振动模式仅震动。", paper));

    body.addView(IosUi.header(this, "语音提醒功能", paper));
    body.addView(IosUi.footer(this, "语音播报需要开启手机响铃模式。静音／振动时仅震动，请先检查手机响铃开关。", paper));
    LinearLayout reminderCard = IosUi.card(this, paper);
    reminderCard.addView(IosUi.switchRow(this, "开启提醒", p.getBoolean("reminder_enabled", true), paper, null,
        (v, on) -> {
          p.edit().putBoolean("reminder_enabled", on).apply();
          ButtonService.remindersChanged();
          if (on) ButtonService.refreshPhoneBattery();
        }));
    reminderCard.addView(IosUi.divider(this, paper));
    LinearLayout mediaRow = IosUi.disclosureRow(this, "提醒媒体类型",
        "tone".equals(p.getString("reminder_media", "voice")) ? "提示音" : "提示语音", paper, false, () -> {});
    mediaRow.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("提醒媒体类型")
        .setSingleChoiceItems(new String[] {"提示音", "提示语音"},
            "tone".equals(p.getString("reminder_media", "voice")) ? 0 : 1,
            (dialog, selected) -> {
              p.edit().putString("reminder_media", selected == 0 ? "tone" : "voice").apply();
              ButtonService.remindersChanged();
              ((TextView) mediaRow.getChildAt(1)).setText(selected == 0 ? "提示音" : "提示语音");
              dialog.dismiss();
            }).setNegativeButton("取消", null).show());
    reminderCard.addView(mediaRow);
    addToneRows(reminderCard, "reminder_tone", paper);
    String[] reminderNames = {"键盘断开", "键盘已连接", "键盘电量过低", "手机电量过低"};
    String[] reminderKeys = {"reminder_keyboard_disconnected", "reminder_keyboard_connected",
        "reminder_keyboard_low", "reminder_phone_low"};
    for (int item = 0; item < reminderKeys.length; item++) {
      final String key = reminderKeys[item];
      reminderCard.addView(IosUi.divider(this, paper));
      reminderCard.addView(IosUi.switchRow(this, reminderNames[item], p.getBoolean(key, true), paper, null,
          (v, on) -> {
            p.edit().putBoolean(key, on).apply();
            ButtonService.remindersChanged();
            if (on && key.equals("reminder_phone_low")) ButtonService.refreshPhoneBattery();
          }));
    }
    body.addView(reminderCard);
    body.addView(IosUi.footer(this, "请开启手机响铃模式：只有响铃模式才播放提示语音／提示音；静音或振动模式下仅震动。默认开启提示语音，全部离线播放，语音跟随媒体音量，提示音跟随系统通知音量。低电量阈值为低于 30%；键盘连接读取电量后及断开时提醒，手机每轮低电量提醒一次，充电时不提醒。", paper));

    body.addView(IosUi.header(this, "网络与 VPN 代理", paper));
    LinearLayout netCard = IosUi.card(this, paper);
    netCard.addView(
        IosUi.switchRow(
            this,
            "启动应用前检测 VPN",
            p.getBoolean("proxy_guard", true),
            paper,
            null,
            (v, on) -> p.edit().putBoolean("proxy_guard", on).apply()));
    netCard.addView(IosUi.divider(this, paper));
    netCard.addView(
        IosUi.switchRow(
            this,
            "桌面启动时自动开启 Clash",
            p.getBoolean("proxy_on_boot", true),
            paper,
            null,
            (v, on) -> p.edit().putBoolean("proxy_on_boot", on).apply()));
    netCard.addView(IosUi.divider(this, paper));
    netCard.addView(
        IosUi.disclosureRow(
            this,
            "需检测 VPN 的应用",
            ProxyGuard.configuredApps(p).size() + " 项",
            paper,
            false,
            this::pickProxyApps));
    netCard.addView(IosUi.divider(this, paper));
    LinearLayout diagRow =
        IosUi.disclosureRow(this, "立即检测本地 VPN 状态", "点击检测", paper, true, () -> {});
    TextView diagVal = (TextView) diagRow.getChildAt(1);
    diagRow.setOnClickListener(v -> proxyDiagnose(diagVal));
    netCard.addView(diagRow);
    body.addView(netCard);
    body.addView(
        IosUi.footer(this, "默认支持 ChatGPT / Grok / Gemini，仅在本地检测 VPN 隧道与端口，不发起外网请求。", paper));

    body.addView(IosUi.header(this, "系统与备份", paper));
    LinearLayout sysCard = IosUi.card(this, paper);
    sysCard.addView(
        IosUi.disclosureRow(this, "进入系统经典桌面", "临时操作其他应用", paper, false, this::openClassicHome));
    sysCard.addView(IosUi.divider(this, paper));
    sysCard.addView(
        IosUi.disclosureRow(
            this,
            "导出运行与崩溃日志",
            "",
            paper,
            false,
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("text/plain")
                        .putExtra(
                            Intent.EXTRA_TITLE,
                            new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                                    .format(new java.util.Date())
                                + ".log")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    95)));
    sysCard.addView(IosUi.divider(this, paper));
    sysCard.addView(
        IosUi.disclosureRow(
            this,
            "导出配置备份",
            "",
            paper,
            false,
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, "SimpleHome-backup.zip")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    93)));
    sysCard.addView(IosUi.divider(this, paper));
    sysCard.addView(
        IosUi.disclosureRow(
            this,
            "导入配置备份",
            "",
            paper,
            false,
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("application/zip")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    94)));
    sysCard.addView(IosUi.divider(this, paper));
    sysCard.addView(IosUi.disclosureRow(this, "图片来源与许可", "派大星", paper, false,
        () -> new AlertDialog.Builder(this).setTitle("图片来源与许可")
            .setMessage("派大星图片：Nickelodeon / NickRewind\n来源：Wikimedia Commons，Patrick Star character.png\n许可：Creative Commons Attribution 3.0 Unported\n原始图片未修改，仅在界面中缩放显示。")
            .setPositiveButton("查看来源", (d, w) -> openCreditLink("https://commons.wikimedia.org/wiki/File:Patrick_Star_character.png"))
            .setNeutralButton("查看许可", (d, w) -> openCreditLink("https://creativecommons.org/licenses/by/3.0/"))
            .setNegativeButton("关闭", null).show()));
    sysCard.addView(IosUi.divider(this, paper));
    sysCard.addView(IosUi.disclosureRow(this, "系统音效来源与许可", "Android AOSP", paper, false,
        () -> new AlertDialog.Builder(this).setTitle("内置系统音效")
            .setMessage("8 段 Android AOSP 系统提示音已复制到应用资源中，全部离线播放，不访问其他手机的系统音频文件。\n许可：Apache License 2.0。\n原始 OGG 文件未修改，完整许可随 APK 提供。")
            .setPositiveButton("查看来源", (d, w) -> openCreditLink("https://android.googlesource.com/platform/frameworks/base/+/1cdfff555f4a21f71ccc978290e2e212e2f8b168/data/sounds/notifications/ogg/"))
            .setNeutralButton("查看许可", (d, w) -> openCreditLink("https://www.apache.org/licenses/LICENSE-2.0"))
            .setNegativeButton("关闭", null).show()));
    body.addView(sysCard);

    TextView ver = IosUi.footer(this, "BT QuickLaunch 1.5.0 · 双击 1 / 2 / 3 启动 · 三键清理", paper);
    ver.setGravity(Gravity.CENTER);
    ver.setPadding(0, dp(18), 0, dp(8));
    body.addView(ver);
  }

  void stopTonePreview() {
    if (tonePreview != null) { tonePreview.close(); tonePreview = null; }
  }

  void previewTone(String toneId) {
    if (tonePreview == null) tonePreview = new SystemTonePlayer(this, new Handler());
    tonePreview.play(toneId, android.media.ToneGenerator.TONE_PROP_ACK);
    if (!ReminderAudio.ringing(this)) Toast.makeText(this, "当前静音／振动模式，试听仅震动；请开启响铃模式", 1).show();
  }

  void addToneRows(LinearLayout card, String key, boolean paper) {
    card.addView(IosUi.divider(this, paper));
    LinearLayout row = IosUi.disclosureRow(this, "选择内置系统音效",
        SystemTonePlayer.title(this, p.getString(key, "")), paper, false, () -> {});
    row.setOnClickListener(v -> chooseSystemTone(key, (TextView) row.getChildAt(1)));
    card.addView(row);
    card.addView(IosUi.divider(this, paper));
    card.addView(IosUi.disclosureRow(this, "试听已选提示音", "应用内置系统音效", paper, false,
        () -> previewTone(p.getString(key, ""))));
  }

  void chooseSystemTone(String key, TextView value) {
    stopTonePreview();
    new Thread(() -> {
      java.util.List<SystemTonePlayer.Entry> tones = SystemTonePlayer.list(this);
      runOnUiThread(() -> {
        if (isDestroyed() || isFinishing() || !desktopVisible) return;
        String[] labels = new String[tones.size()];
        int current = 0;
        String selectedId = p.getString(key, "");
        for (int i = 0; i < tones.size(); i++) {
          labels[i] = tones.get(i).title;
          if (tones.get(i).id.equals(selectedId)) current = i;
        }
        final int[] selected = {current};
        AlertDialog picker = new AlertDialog.Builder(this).setTitle("选择内置系统音效（不支持外部导入）")
            .setSingleChoiceItems(labels, current, (d, which) -> selected[0] = which)
            .setPositiveButton("保存", (d, which) -> {
              SystemTonePlayer.Entry tone = tones.get(selected[0]);
              p.edit().putString(key, tone.id).apply();
              value.setText(tone.title);
              if (key.equals("reminder_tone")) ButtonService.remindersChanged();
            }).setNeutralButton("试听", null).setNegativeButton("取消", null).create();
        picker.setOnDismissListener(d -> stopTonePreview());
        picker.setOnShowListener(d -> picker.getButton(AlertDialog.BUTTON_NEUTRAL)
            .setOnClickListener(v -> previewTone(tones.get(selected[0]).id)));
        picker.show();
      });
    }, "SystemToneList").start();
  }

  void openCreditLink(String url) {
    try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
    catch (Exception error) { Toast.makeText(this, "未找到可用浏览器", 0).show(); }
  }

  void fillWallpapers(LinearLayout content) {
    String[] labels = {"纯黑", "纯白", "暗夜精灵", "午夜星雾", "幽谷微光", "石墨流光", "曜石丝绸", "深海弧影", "烟金暮色", "相册壁纸"};
    int[][] palettes = {
      {0xff000000, 0xff000000},
      {0xffffffff, 0xffffffff},
      {0xff010604, 0xff102a21, 0xff020705},
      {0xff03040a, 0xff1a1730, 0xff030710},
      {0xff010707, 0xff103034, 0xff020508}
    };
    ScrollView scroll = new ScrollView(this);
    LinearLayout gallery = new LinearLayout(this);
    gallery.setOrientation(1);
    scroll.addView(gallery);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    for (int r = 0; r < 4; r++) {
      LinearLayout row = new LinearLayout(this);
      gallery.addView(row, new LinearLayout.LayoutParams(-1, dp(150)));
      for (int c = 0; c < 3; c++) {
        int index = r * 3 + c;
        if (index >= labels.length) {
          row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1));
          continue;
        }
        final int n = index;
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(1);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
        size.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.addView(tile, size);
        FrameLayout preview = new FrameLayout(this);
        if (n < 5)
          preview.setBackground(
              new GradientDrawable(GradientDrawable.Orientation.TL_BR, palettes[n]));
        else if (n < 9) preview.setBackground(new NightWallpaper(n - 5));
        else {
          preview.setBackgroundColor(0xff252c35);
          String path = p.getString("wallpaper", "");
          if (!path.isEmpty())
            try {
              ImageView image = new ImageView(this);
              image.setImageURI(android.net.Uri.parse(path));
              image.setScaleType(ImageView.ScaleType.CENTER_CROP);
              image.setAlpha(0.32f);
              preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
            } catch (Exception ignored) {
            }
          else {
            TextView add = text("＋", 28);
            add.setGravity(Gravity.CENTER);
            preview.addView(add, new FrameLayout.LayoutParams(-1, -1));
          }
        }
        tile.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        boolean chosen =
            p.getString("wallpaper", "").isEmpty() ? p.getInt("background", 0) == n : n == 9;
        TextView label = text(labels[n] + (chosen ? "  ✓" : ""), 14);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(chosen ? 0xff55d796 : 0xffa9b5c4);
        label.setPadding(0, dp(4), 0, 0);
        tile.addView(label, new LinearLayout.LayoutParams(-1, dp(28)));
        tile.setOnClickListener(
            v -> {
              if (n == 5) {
                try {
                  startActivityForResult(
                      new Intent(Intent.ACTION_OPEN_DOCUMENT)
                          .setType("image/*")
                          .addCategory(Intent.CATEGORY_OPENABLE)
                          .addFlags(
                              Intent.FLAG_GRANT_READ_URI_PERMISSION
                                  | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                      91);
                } catch (Exception e) {
                  Toast.makeText(this, "无法打开相册", 0).show();
                }
              } else {
                p.edit().putInt("background", n).remove("wallpaper").apply();
                draw();
                settingsContent.removeAllViews();
                fillWallpapers(settingsContent);
              }
            });
      }
    }
  }

  boolean supportsVoice(String pkg) {
    return "com.openai.chatgpt".equals(pkg) || "com.google.android.apps.bard".equals(pkg)
        || "com.larus.nova".equals(pkg);
  }

  void open(String pkg, boolean voice) {
    if (voice && supportsVoice(pkg)) ButtonService.requestVoice(pkg);
    try {
      Intent in =
          voice && pkg.equals("com.openai.chatgpt")
              ? new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://chatgpt.com/voice"))
                  .setPackage(pkg)
              : getPackageManager().getLaunchIntentForPackage(pkg);
      if (in == null) throw new Exception();
      startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (Exception e) {
      Toast.makeText(this, "无法打开应用，请在右上角设置中重新选择", 1).show();
    }
  }

  void launch(int i) {
    if (cleaning) {
      queuedSlot = i;
      return;
    }
    AppLog.write("LAUNCH slot=" + i + " package=" + get(i, "pkg", pkgs[i]), null);
    cancelLaunch();
    busy = true;
    final int generation = launchGeneration;
    final String pkg = get(i, "pkg", pkgs[i]);
    if (needsProxy(i, pkg)) {
      guardedLaunch(i, generation, pkg, false, 0);
      return;
    }
    if (pkg.equals("com.openai.chatgpt")) {
      busy = false;
      open(pkg, p.getBoolean(i + "voice", true));
      return;
    }
    String before = get(i, "before", "");
    if (!before.isEmpty()) open(before, false);
    h.postDelayed(
        () -> {
          if (generation != launchGeneration) return;
          busy = false;
          open(pkg, supportsVoice(pkg) && p.getBoolean(i + "voice", true));
        },
        before.isEmpty() ? 0 : p.getInt(i + "delay", 3000));
  }

  /** Overseas apps (GPT / Grok / Gemini / user-configured) check the network first when the guard is on. */
  boolean needsProxy(int i, String pkg) {
    return p.getBoolean("proxy_guard", true) && ProxyGuard.shouldGuard(p, pkg);
  }

  static final int PROXY_RETRIES = 4, NETWORK_WAITS = 3;

  /**
   * Launch flow (local-only, no external HTTPS probes):
   * baseNetwork && proxyRunning -> open.
   * Device offline -> wait a few times.
   * VPN/proxy missing -> start Clash Meta once and re-check.
   * GPT never forces a voice call when VPN/network is down; other apps still open after a warning.
   */
  void guardedLaunch(int i, int generation, String pkg, boolean started, int attempt) {
    networkWorker.execute(
        () -> {
          ProxyGuard.Status s = ProxyGuard.check(this);
          h.post(
              () -> {
                if (generation != launchGeneration || isDestroyed()) return;
                boolean gpt = pkg.equals("com.openai.chatgpt");
                if (s.ready()) {
                  busy = false;
                  open(pkg, supportsVoice(pkg) && p.getBoolean(i + "voice", true));
                  return;
                }
                if (!s.baseNetwork) {
                  if (attempt < NETWORK_WAITS) {
                    if (attempt == 0) Toast.makeText(this, "等待网络连接…", 0).show();
                    retryGuarded(i, generation, pkg, started, attempt + 1, 2000);
                  } else failGuarded(pkg, gpt, "设备未联网，请检查 Wi-Fi 或移动数据");
                  return;
                }
                if (!started) {
                  if (!ProxyGuard.installed(this)) {
                    failGuarded(pkg, gpt, "未检测到 VPN 代理，且未安装 Clash Meta");
                    return;
                  }
                  Toast.makeText(this, "未检测到 VPN，正在启动 Clash Meta…", 0).show();
                  ProxyGuard.start(this);
                  retryGuarded(i, generation, pkg, true, 1, 1500);
                  return;
                }
                if (attempt < PROXY_RETRIES) {
                  retryGuarded(i, generation, pkg, true, attempt + 1, 1500);
                  return;
                }
                failGuarded(pkg, gpt, "未检测到 Clash Meta VPN 运行，请手动检查");
              });
        });
  }

  void retryGuarded(int i, int generation, String pkg, boolean started, int attempt, long delay) {
    h.postDelayed(
        () -> {
          if (generation == launchGeneration) guardedLaunch(i, generation, pkg, started, attempt);
        },
        delay);
  }

  void failGuarded(String pkg, boolean gpt, String reason) {
    AppLog.write("GUARD_NETWORK_FAILED pkg=" + pkg + " reason=" + reason, null);
    busy = false;
    if (gpt) {
      Toast.makeText(this, reason + "（未进入语音通话）", 1).show();
    } else {
      Toast.makeText(this, reason, 1).show();
      open(pkg, false);
    }
  }

  /** On desktop start: if the VPN/proxy is not running, start Clash Meta in the background. */
  void ensureProxyOnStart() {
    if (!p.getBoolean("proxy_on_boot", true) || !ProxyGuard.installed(this)) return;
    networkWorker.execute(
        () -> {
          ProxyGuard.Status s = ProxyGuard.check(this);
          h.post(
              () -> {
                if (isDestroyed() || s.proxyRunning() || !s.baseNetwork) return;
                AppLog.write("BOOT_PROXY_START", null);
                ProxyGuard.start(this);
              });
        });
  }

  void proxyDiagnose(TextView out) {
    out.setText("检测中…");
    new Thread(
            () -> {
              ProxyGuard.Status s = ProxyGuard.check(this);
              runOnUiThread(() -> out.setText(s.summary()));
            })
        .start();
  }

  protected void onDestroy() {
    stopTonePreview();
    cancelLaunch();
    networkWorker.shutdownNow();
    statusHandler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  void configure(int i) {
    detailPage("位置 " + (i + 1) + " · 启动设置");
    option("更换目标应用", () -> pick(i, false));
    String slotPkg = get(i, "pkg", pkgs[i]);
    boolean gpt = slotPkg.equals("com.openai.chatgpt");
    Switch proxy = new Switch(this);
    proxy.setText("启动前检测 VPN，未开启则自动启用 Clash Meta");
    proxy.setTextColor(Color.WHITE);
    proxy.setChecked(ProxyGuard.shouldGuard(p, slotPkg));
    settingsContent.addView(proxy);
    proxy.setOnCheckedChangeListener(
        (v, on) -> {
          ProxyGuard.setGuarded(p, slotPkg, on);
          draw();
        });
    if (!p.getBoolean("proxy_guard", true))
      settingsContent.addView(text("提示：“其他设置 → 网络与代理”总开关已关闭，此项暂不生效。", 13));
    if (gpt) {
      settingsContent.addView(text("GPT 特殊规则：先检查设备联网与 VPN；未开启则启动 Clash 并复检；复检失败不会强行进入通话。", 14));
    }
    if (supportsVoice(slotPkg)) {
      Switch voice = new Switch(this);
      voice.setText(gpt ? "自动进入语音通话" : "com.larus.nova".equals(slotPkg)
          ? "打开豆包后自动点击右上角电话，进入通话模式"
          : "自动进入 Gemini Live 语音（会先收起键盘）");
      voice.setTextColor(Color.WHITE);
      voice.setChecked(p.getBoolean(i + "voice", true));
      settingsContent.addView(voice);
      voice.setOnCheckedChangeListener(
          (v, on) -> {
            p.edit().putBoolean(i + "voice", on).apply();
            draw();
          });
    }
    if (!gpt) {
      option("设置前置应用", () -> pick(i, true));
      option(
          "取消前置应用",
          () -> {
            p.edit().putString(i + "before", "").apply();
            Toast.makeText(this, "前置应用已取消", 0).show();
          });
      TextView delay = text("前置等待时间：" + (p.getInt(i + "delay", 3000) / 1000) + " 秒", 15);
      settingsContent.addView(delay);
      SeekBar bar = new SeekBar(this);
      bar.setMax(9);
      bar.setProgress(Math.max(0, Math.min(9, p.getInt(i + "delay", 3000) / 1000 - 1)));
      settingsContent.addView(bar);
      bar.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b) {}

            public void onStopTrackingTouch(SeekBar b) {}

            public void onProgressChanged(SeekBar b, int v, boolean user) {
              if (user) {
                p.edit().putInt(i + "delay", (v + 1) * 1000).apply();
                delay.setText("前置等待时间：" + (v + 1) + " 秒");
              }
            }
          });
    }
  }

  void reorder(int from) {
    detailPage("交换应用位置");
    for (int j = 0; j < 4; j++) {
      final int to = j;
      option(
          "位置 " + (j + 1) + " · " + get(j, "name", names[j]),
          () -> {
            if (to == from) return;
            String aIcon = p.getString(from + "icon", ""), bIcon = p.getString(to + "icon", "");
            String aName = get(from, "name", names[from]), bName = get(to, "name", names[to]);
            String aPkg = get(from, "pkg", pkgs[from]), bPkg = get(to, "pkg", pkgs[to]);
            String
                aBefore = get(from, "before", from == 0 ? "com.github.metacubex.clash.meta" : ""),
                bBefore = get(to, "before", to == 0 ? "com.github.metacubex.clash.meta" : "");
            boolean av = p.getBoolean(from + "voice", from == 0),
                bv = p.getBoolean(to + "voice", to == 0);
            int ad = p.getInt(from + "delay", 3000), bd = p.getInt(to + "delay", 3000);
            p.edit()
                .putString(from + "icon", bIcon)
                .putString(to + "icon", aIcon)
                .putString(from + "name", bName)
                .putString(to + "name", aName)
                .putString(from + "pkg", bPkg)
                .putString(to + "pkg", aPkg)
                .putString(from + "before", bBefore)
                .putString(to + "before", aBefore)
                .putBoolean(from + "voice", bv)
                .putBoolean(to + "voice", av)
                .putInt(from + "delay", bd)
                .putInt(to + "delay", ad)
                .apply();
            draw();
            settingsPanel();
          });
    }
  }

  void wallpapers() {
    new AlertDialog.Builder(this)
        .setTitle("背景与壁纸")
        .setItems(
            new String[] {"纯黑（默认）", "纯白", "暗夜精灵 · 墨绿", "午夜星雾 · 蓝紫", "幽谷微光 · 青黑", "从相册选择"},
            (d, w) -> {
              if (w == 5) {
                try {
                  startActivityForResult(
                      new Intent(Intent.ACTION_OPEN_DOCUMENT)
                          .setType("image/*")
                          .addCategory(Intent.CATEGORY_OPENABLE)
                          .addFlags(
                              Intent.FLAG_GRANT_READ_URI_PERMISSION
                                  | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                      91);
                } catch (Exception e) {
                  Toast.makeText(this, "没有可用的相册或文件选择器", 1).show();
                }
              } else {
                p.edit().putInt("background", w).remove("wallpaper").apply();
                draw();
              }
            })
        .show();
  }

  void background(FrameLayout root) {
    if (p.getBoolean("paper_theme", false)) {
      root.setBackgroundColor(0xffe5e2d9);
      return;
    }
    int b = p.getInt("background", 0);
    root.setBackgroundColor(b == 1 ? Color.WHITE : Color.BLACK);
    String uri = p.getString("wallpaper", "");
    if (!uri.isEmpty()) {
      try {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        java.io.InputStream in = getContentResolver().openInputStream(android.net.Uri.parse(uri));
        android.graphics.BitmapFactory.decodeStream(in, null, o);
        in.close();
        o.inSampleSize = 1;
        while (o.outWidth / o.inSampleSize > 2400 || o.outHeight / o.inSampleSize > 2400)
          o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        in = getContentResolver().openInputStream(android.net.Uri.parse(uri));
        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(in, null, o);
        in.close();
        if (bm == null) throw new Exception();
        ImageView wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setImageBitmap(bm);
        wall.setAlpha(0.32f);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
      } catch (Exception e) {
        p.edit().remove("wallpaper").apply();
        Toast.makeText(this, "壁纸不可用，已恢复背景", 0).show();
      }
    } else if (b >= 5) {
      root.setBackground(new NightWallpaper(b - 5));
    } else if (b >= 2) {
      int[][] colors = {
        {0xff010604, 0xff102a21, 0xff020705},
        {0xff03040a, 0xff1a1730, 0xff030710},
        {0xff010707, 0xff103034, 0xff020508}
      };
      root.setBackground(
          new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[Math.min(b - 2, 2)]));
    }
  }

  protected void onActivityResult(int r, int c, Intent data) {
    super.onActivityResult(r, c, data);
    if (r == 95 && c == RESULT_OK && data != null && data.getData() != null) {
      new Thread(
              () -> {
                try {
                  java.io.File file =
                      new java.io.File(
                          getFilesDir(),
                          "logs/"
                              + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                                  .format(new java.util.Date())
                              + ".log");
                  try (java.io.InputStream in = new java.io.FileInputStream(file);
                      java.io.OutputStream out =
                          getContentResolver().openOutputStream(data.getData())) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                  }
                  runOnUiThread(() -> Toast.makeText(this, "日志已导出", 0).show());
                } catch (Exception ex) {
                  runOnUiThread(() -> Toast.makeText(this, "日志导出失败", 1).show());
                }
              })
          .start();
      return;
    }
    if ((r == 93 || r == 94) && c == RESULT_OK && data != null && data.getData() != null) {
      cancelLaunch();
      new Thread(
              () -> {
                try {
                  if (r == 93) ConfigBackup.write(this, p, data.getData());
                  else ConfigBackup.restore(this, p, data.getData());
                  runOnUiThread(
                      () -> {
                        draw();
                        settingsPanel();
                        Toast.makeText(this, r == 93 ? "备份已导出" : "配置已恢复", 0).show();
                      });
                } catch (Exception ex) {
                  runOnUiThread(() -> Toast.makeText(this, "备份操作失败：" + ex.getMessage(), 1).show());
                }
              })
          .start();
      return;
    }
    if (r == 92 && c == RESULT_OK && data != null && data.getData() != null && pendingIcon >= 0) {
      try {
        int slot = pendingIcon;
        java.io.InputStream in = getContentResolver().openInputStream(data.getData());
        android.graphics.BitmapFactory.Options options =
            new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeStream(in, null, options);
        in.close();
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > 512
            || options.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        in = getContentResolver().openInputStream(data.getData());
        android.graphics.Bitmap bitmap =
            android.graphics.BitmapFactory.decodeStream(in, null, options);
        in.close();
        if (bitmap == null) throw new Exception();
        java.io.File out =
            new java.io.File(getFilesDir(), "icon-" + java.util.UUID.randomUUID() + ".png");
        java.io.FileOutputStream stream = new java.io.FileOutputStream(out);
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        stream.close();
        p.edit().putString(slot + "icon", out.getAbsolutePath()).apply();
        draw();
        settingsPanel();
      } catch (Exception ex) {
        Toast.makeText(this, "无法读取图标图片", 1).show();
      }
      pendingIcon = -1;
      return;
    }
    if (r == 91 && c == RESULT_OK && data != null && data.getData() != null) {
      try {
        getContentResolver()
            .takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        p.edit().putString("wallpaper", data.getData().toString()).putInt("background", 0).apply();
        draw();
        if (settingsDialog != null && settingsDialog.isShowing()) {
          settingsContent.removeAllViews();
          fillWallpapers(settingsContent);
        }
      } catch (Exception e) {
        Toast.makeText(this, "无法读取这张图片，请重新选择", 1).show();
      }
    }
  }

  void cancelLaunch() {
    stopTonePreview();
    clearCleanFeedback();
    AppLog.write("CANCEL generation=" + launchGeneration, null);
    queuedSlot=-1;
    launchGeneration++;
    h.removeCallbacksAndMessages(null);
    busy = false;
    ButtonService.cancelVoice();
  }

  void clearCleanFeedback() {
    FrameLayout old = cleanFeedback;
    cleanFeedback = null;
    feedbackHandler.removeCallbacksAndMessages(null);
    releaseCleanTone();
    if (old != null) {
      old.animate().cancel();
      if (old.getParent() instanceof android.view.ViewGroup)
        ((android.view.ViewGroup) old.getParent()).removeView(old);
    }
  }

  void releaseCleanTone() {
    if (cleanTone != null) { cleanTone.close(); cleanTone = null; }
  }

  void playCleanSuccessTone() {
    if (!p.getBoolean("clean_success_sound", true)) return;
    if (!ReminderAudio.ringing(this)) { ReminderAudio.vibrate(this); return; }
    if (cleanTone == null) cleanTone = new SystemTonePlayer(this, feedbackHandler);
    cleanTone.play(p.getString("clean_success_tone", ""), android.media.ToneGenerator.TONE_PROP_ACK);
    AppLog.write("CLEAN_SUCCESS_TONE requested", null);
  }

  void showCleanFeedback() {
    clearCleanFeedback();
    if (!desktopVisible || desktopRoot == null) return;
    if (idlePower != null) idlePower.activity("clean_success");
    final FrameLayout overlay = new FrameLayout(this);
    cleanFeedback = overlay;
    overlay.setContentDescription("清理成功，搞定了");
    final LinearLayout successContent = new LinearLayout(this);
    successContent.setOrientation(LinearLayout.VERTICAL);
    successContent.setGravity(Gravity.CENTER);
    successContent.setPadding(dp(20), dp(16), dp(20), dp(16));
    ImageView patrick = new ImageView(this);
    patrick.setScaleType(ImageView.ScaleType.FIT_CENTER);
    patrick.setContentDescription("派大星");
    patrick.setImageResource(getResources().getIdentifier("cleanup_patrick", "drawable", getPackageName()));
    successContent.addView(patrick, new LinearLayout.LayoutParams(-1, 0, 1));
    final TextView message = new TextView(this);
    message.setText("搞定了");
    message.setTextSize(64);
    message.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    message.setTextColor(0xffb6f4dc);
    message.setGravity(Gravity.CENTER);
    successContent.addView(message, new LinearLayout.LayoutParams(-1, -2));
    successContent.setVisibility(View.VISIBLE);
    overlay.addView(successContent, new FrameLayout.LayoutParams(-1, -1));
    desktopRoot.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    overlay.setBackgroundColor(0xff0a1418);
    playCleanSuccessTone();
    AppLog.write("CLEAN_FEEDBACK shown", null);
    feedbackHandler.postDelayed(() -> {
      if (cleanFeedback != overlay) return;
      overlay.animate().alpha(0f).setDuration(200).withEndAction(() -> {
        if (cleanFeedback == overlay) clearCleanFeedback();
      }).start();
    }, 3000);
  }

  void clean() {
    if (cleaning) return;
    cleaning = true;
    queuedSlot = -1;
    AppLog.write("CLEAN_REQUEST", null);
    cancelLaunch();
    new Thread(
            () -> {
              try {
                Set<String> targets = new HashSet<>(Arrays.asList(pkgs));
                targets.add("mark.via");
                for (int i = 0; i < 4; i++) targets.add(get(i, "pkg", pkgs[i]));
                StringBuilder cmd = new StringBuilder("result=0; ");
                for (String pkg : targets)
                  if (pkg.matches("[a-zA-Z0-9_.]+")
                      && !Arrays.asList(
                              "com.benbenyu.btquicklaunch",
                              "com.github.metacubex.clash.meta")
                          .contains(pkg)) cmd.append("am force-stop ").append(pkg).append(" || result=1; ");
                java.lang.Process process = new ProcessBuilder("su", "-c", cmd.append("exit $result").toString()).start();
                if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                  process.destroy();
                  throw new Exception("清理超时");
                }
                int result = process.exitValue();
                AppLog.write("CLEAN_RESULT exit=" + result, null);
                runOnUiThread(() -> {
                  if (isDestroyed() || isFinishing()) return;
                  if (result == 0) showCleanFeedback();
                  else Toast.makeText(this, "清理失败，请检查 Root 授权", 1).show();
                });
              } catch (Exception e) {
                AppLog.write("CLEAN_FAILED",e);runOnUiThread(() -> Toast.makeText(this, "清理失败，请查看日志或 Root 授权", 1).show());
              } finally {
                runOnUiThread(
                    () -> {
                      cleaning = false;
                      int next = queuedSlot;
                      queuedSlot = -1;
                      if (next >= 0) launch(next);
                    });
              }
            })
        .start();
  }

  void pick(int i, boolean before) {
    cancelLaunch();
    settingsTab = 0;
    if (settingsDialog == null || !settingsDialog.isShowing()) settingsPanel();
    settingsContent.removeAllViews();
    TextView title = text(before ? "选择前置应用" : "位置 " + (i + 1) + " · 选择应用", 20);
    settingsContent.addView(title);
    EditText search = new EditText(this);
    search.setSingleLine();
    search.setTextColor(Color.WHITE);
    search.setHintTextColor(0xff919cab);
    search.setHint("搜索应用");
    settingsContent.addView(search);
    List<ResolveInfo> apps =
        getPackageManager()
            .queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
    Collections.sort(
        apps,
        (x, y) ->
            x.loadLabel(getPackageManager())
                .toString()
                .compareToIgnoreCase(y.loadLabel(getPackageManager()).toString()));
    ListView list = new ListView(this);
    settingsContent.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    ArrayList<ResolveInfo> filtered = new ArrayList<>();
    BaseAdapter adapter =
        new BaseAdapter() {
          public int getCount() {
            return filtered.size();
          }

          public Object getItem(int pos) {
            return filtered.get(pos);
          }

          public long getItemId(int pos) {
            return pos;
          }

          public View getView(int pos, View reuse, android.view.ViewGroup parent) {
            ResolveInfo app = filtered.get(pos);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            ImageView icon = new CircularIconView(MainActivity.this);
            icon.setImageDrawable(app.loadIcon(getPackageManager()));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            row.addView(text(app.loadLabel(getPackageManager()).toString(), 16));
            return row;
          }
        };
    list.setAdapter(adapter);
    Runnable filter =
        () -> {
          filtered.clear();
          String q = search.getText().toString().toLowerCase(java.util.Locale.ROOT);
          for (ResolveInfo app : apps)
            if (!app.activityInfo.packageName.equals(getPackageName())
                && app.loadLabel(getPackageManager())
                    .toString()
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains(q)) filtered.add(app);
          adapter.notifyDataSetChanged();
        };
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence c, int st, int count, int after) {}

          public void onTextChanged(CharSequence c, int st, int beforeCount, int count) {
            filter.run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    filter.run();
    list.setOnItemClickListener(
        (parent, view, pos, id) -> {
          ResolveInfo app = filtered.get(pos);
          p.edit().putString(i + (before ? "before" : "pkg"), app.activityInfo.packageName).apply();
          if (!before)
            p.edit()
                .remove(i + "icon")
                .putString(i + "name", app.loadLabel(getPackageManager()).toString())
                .putBoolean(i + "voice", supportsVoice(app.activityInfo.packageName))
                .apply();
          ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .hideSoftInputFromWindow(search.getWindowToken(), 0);
          draw();
          settingsPanel();
        });
  }

  void pickProxyApps() {
    settingsTab = 2;
    detailPage("需检测代理的应用（点击切换）");
    EditText search = new EditText(this);
    search.setSingleLine();
    search.setTextColor(p.getBoolean("paper_theme", false) ? 0xff252525 : Color.WHITE);
    search.setHintTextColor(0xff919cab);
    search.setHint("搜索已安装应用（新安装的应用也会自动出现在此）");
    settingsContent.addView(search);
    List<ResolveInfo> apps =
        getPackageManager()
            .queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
    Collections.sort(
        apps,
        (x, y) -> {
          boolean gx = ProxyGuard.shouldGuard(p, x.activityInfo.packageName);
          boolean gy = ProxyGuard.shouldGuard(p, y.activityInfo.packageName);
          if (gx != gy) return gx ? -1 : 1;
          return x.loadLabel(getPackageManager())
              .toString()
              .compareToIgnoreCase(y.loadLabel(getPackageManager()).toString());
        });
    ListView list = new ListView(this);
    settingsContent.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    ArrayList<ResolveInfo> filtered = new ArrayList<>();
    BaseAdapter adapter =
        new BaseAdapter() {
          public int getCount() {
            return filtered.size();
          }

          public Object getItem(int pos) {
            return filtered.get(pos);
          }

          public long getItemId(int pos) {
            return pos;
          }

          public View getView(int pos, View reuse, android.view.ViewGroup parent) {
            ResolveInfo app = filtered.get(pos);
            String pkg = app.activityInfo.packageName;
            boolean on = ProxyGuard.shouldGuard(p, pkg);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            ImageView icon = new CircularIconView(MainActivity.this);
            icon.setImageDrawable(app.loadIcon(getPackageManager()));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(1);
            col.setPadding(dp(12), 0, dp(12), 0);
            TextView title = new TextView(MainActivity.this);
            title.setText(app.loadLabel(getPackageManager()).toString());
            title.setTextSize(16);
            title.setTextColor(p.getBoolean("paper_theme", false) ? 0xff252525 : Color.WHITE);
            col.addView(title);
            TextView sub = new TextView(MainActivity.this);
            sub.setText(pkg);
            sub.setTextSize(11);
            sub.setTextColor(0xff919cab);
            col.addView(sub);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView badge = new TextView(MainActivity.this);
            badge.setText(on ? "已启用 ✓" : "未启用");
            badge.setTextSize(14);
            badge.setTextColor(on ? 0xff55d796 : 0xff7f8a99);
            row.addView(badge);
            return row;
          }
        };
    list.setAdapter(adapter);
    Runnable filter =
        () -> {
          filtered.clear();
          String q = search.getText().toString().toLowerCase(java.util.Locale.ROOT);
          for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals(ProxyGuard.CLASH)) continue;
            String label = app.loadLabel(getPackageManager()).toString().toLowerCase(java.util.Locale.ROOT);
            if (label.contains(q) || pkg.toLowerCase(java.util.Locale.ROOT).contains(q))
              filtered.add(app);
          }
          adapter.notifyDataSetChanged();
        };
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence c, int st, int count, int after) {}

          public void onTextChanged(CharSequence c, int st, int beforeCount, int count) {
            filter.run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    filter.run();
    list.setOnItemClickListener(
        (parent, view, pos, id) -> {
          String pkg = filtered.get(pos).activityInfo.packageName;
          boolean next = !ProxyGuard.shouldGuard(p, pkg);
          ProxyGuard.setGuarded(p, pkg, next);
          adapter.notifyDataSetChanged();
          draw();
        });
  }
}
