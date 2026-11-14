package com.benbenyu.btquicklaunch;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

final class ConfigBackup {
  static void write(Context c, SharedPreferences p, Uri uri) throws Exception {
    JSONObject json = new JSONObject(p.getAll());
    Map<String, byte[]> assets = new HashMap<>();
    for (int i = 0; i < 4; i++) {
      String path = p.getString(i + "icon", "");
      if (!path.isEmpty()) {
        try (InputStream in = new FileInputStream(path)) {
          String name = "icon" + i + ".png";
          assets.put(name, read(in, 4 * 1024 * 1024));
          json.put(i + "icon", name);
        }
      }
    }
    String wallpaper = p.getString("wallpaper", "");
    if (!wallpaper.isEmpty())
      try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(wallpaper))) {
        assets.put("wallpaper", read(in, 16 * 1024 * 1024));
        json.put("wallpaper", "wallpaper");
      }
    try (ZipOutputStream zip = new ZipOutputStream(c.getContentResolver().openOutputStream(uri))) {
      zip.putNextEntry(new ZipEntry("config.json"));
      zip.write(json.toString().getBytes("UTF-8"));
      zip.closeEntry();
      for (Map.Entry<String, byte[]> e : assets.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue());
        zip.closeEntry();
      }
    }
  }

  static byte[] read(InputStream in, int max) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) != -1) {
      if (out.size() + n > max) throw new IOException("备份文件过大");
      out.write(b, 0, n);
    }
    return out.toByteArray();
  }

  static void restore(Context c, SharedPreferences p, Uri uri) throws Exception {
    Map<String, byte[]> files = new HashMap<>();
    int total = 0;
    try (ZipInputStream zip = new ZipInputStream(c.getContentResolver().openInputStream(uri))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName();
        if (!name.matches("config.json|icon[0-3].png|wallpaper") || files.containsKey(name))
          throw new IOException("无效备份");
        byte[] data = read(zip, 16 * 1024 * 1024);
        total += data.length;
        if (total > 32 * 1024 * 1024) throw new IOException("备份过大");
        files.put(name, data);
      }
    }
    if (!files.containsKey("config.json")) throw new IOException("配置缺失");
    JSONObject j = new JSONObject(new String(files.get("config.json"), "UTF-8"));
    Map<String, Object> values = new HashMap<>();
    Iterator<String> keys = j.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!key.matches(
          "[0-3](name|pkg|before|delay|voice|icon)|background|wallpaper|brightness|breathing|paper_theme|idle_power|proxy_guard|proxy_on_boot|proxy_apps|disable_ime"))
        continue;
      Object value = j.get(key);
      boolean boolKey =
          key.endsWith("voice") || key.startsWith("proxy_") || key.equals("breathing") || key.equals("paper_theme") || key.equals("idle_power") || key.equals("disable_ime");
      boolean intKey =
          key.endsWith("delay") || key.equals("background") || key.equals("brightness");
      if (boolKey && !(value instanceof Boolean)
          || intKey && !(value instanceof Integer)
          || (!boolKey && !intKey && !(value instanceof String))) throw new IOException("配置字段类型无效");
      if (key.equals("background") && ((Integer) value < 0 || (Integer) value > 8)
          || key.equals("brightness") && ((Integer) value < 0 || (Integer) value > 100)
          || key.endsWith("delay") && ((Integer) value < 1000 || (Integer) value > 10000))
        throw new IOException("配置数值超出范围");
      if (value instanceof String && value.toString().length() > 1024)
        throw new IOException("配置文本过长");
      if (key.endsWith("pkg") || key.endsWith("before")) {
        if (!(value instanceof String)
            || (!value.equals("") && !value.toString().matches("[A-Za-z0-9_.]+")))
          throw new IOException("应用配置无效");
      }
      if (key.endsWith("icon") || key.equals("wallpaper")) {
        String asset = value.toString();
        if (asset.isEmpty()) {
          values.put(key, "");
          continue;
        }
        if (!files.containsKey(asset)) throw new IOException("图片缺失");
        File target = new File(c.getFilesDir(), "restored-" + UUID.randomUUID() + ".png");
        try (FileOutputStream out = new FileOutputStream(target)) {
          out.write(files.get(asset));
        }
        value =
            key.equals("wallpaper") ? Uri.fromFile(target).toString() : target.getAbsolutePath();
      }
      if (!(value instanceof String || value instanceof Boolean || value instanceof Integer))
        throw new IOException("配置类型无效");
      values.put(key, value);
    }
    SharedPreferences.Editor editor = p.edit();
    for (int i = 0; i < 4; i++)
      for (String field : new String[] {"name", "pkg", "before", "delay", "voice", "icon"})
        editor.remove(i + field);
    editor.remove("wallpaper");
    for (Map.Entry<String, Object> e : values.entrySet()) {
      Object v = e.getValue();
      if (v instanceof Boolean) editor.putBoolean(e.getKey(), (Boolean) v);
      else if (v instanceof Integer) editor.putInt(e.getKey(), (Integer) v);
      else editor.putString(e.getKey(), v.toString());
    }
    editor.commit();
  }
}
