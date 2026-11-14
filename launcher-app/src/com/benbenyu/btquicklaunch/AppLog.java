package com.benbenyu.btquicklaunch;

import java.io.*;
import java.text.*;
import java.util.*;

final class AppLog {
  static File dir;
  static int owner = -1;

  static synchronized void init(File d, int uid) {
    dir = d;
    owner = uid;
    dir.mkdirs();
    fix(dir);
  }

  static void fix(File f) {
    if (android.os.Process.myUid() == 0 && owner >= 0)
      try {
        android.system.Os.chown(f.getAbsolutePath(), owner, owner);
        android.system.Os.chmod(f.getAbsolutePath(), f.isDirectory() ? 0700 : 0600);
      } catch (Exception ignored) {
      }
  }

  static synchronized void write(String event, Throwable error) {
    if (dir == null) return;
    try {
      String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
      File file = new File(dir, day + ".log");
      if (file.length() > 2 * 1024 * 1024) {
        try (RandomAccessFile r = new RandomAccessFile(file, "rw")) {
          byte[] tail = new byte[1024 * 1024];
          r.seek(r.length() - tail.length);
          r.readFully(tail);
          r.seek(0);
          r.write(tail);
          r.setLength(tail.length);
        }
      }
      String text =
          new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date())
              + " pid="
              + android.os.Process.myPid()
              + " "
              + event
              + "\n";
      if (error != null) {
        StringWriter w = new StringWriter();
        error.printStackTrace(new PrintWriter(w));
        text += w.toString();
      }
      try (FileOutputStream out = new FileOutputStream(file, true)) {
        out.write(text.getBytes("UTF-8"));
      }
      fix(file);
      File[] files = dir.listFiles();
      if (files != null)
        for (File old : files)
          if (old.getName().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}.log")
              && System.currentTimeMillis() - old.lastModified() > 14L * 86400000) old.delete();
    } catch (Exception ignored) {
    }
  }
}
