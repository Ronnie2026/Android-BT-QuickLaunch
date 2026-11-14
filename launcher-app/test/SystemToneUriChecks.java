package com.benbenyu.btquicklaunch;

public final class SystemToneUriChecks {
  public static void main(String[] args) {
    check(SystemTonePlayer.allowed(""), "built-in fallback");
    check(SystemTonePlayer.allowed("adara"), "bundled system sound");
    for (String uri : new String[] {null, "unknown", "../adara", "content://media/internal/audio/media/8", "file:///sdcard/custom.mp3", "https://example.com/a.mp3",
        "content://media/external/audio/media/8", "content://settings/system/notification_sound",
        "content://another.provider/internal/audio/media/8", "content://media/internal/audio/media/8/../9",
        "content://media/internal/audio/media/8?file=external", "content://media/internal/audio/media/-1"})
      check(!SystemTonePlayer.allowed(uri), "reject unsupported sound: " + uri);
    System.out.println("PASS bundled sound IDs only; system URIs, external files, network and path traversal rejected");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
