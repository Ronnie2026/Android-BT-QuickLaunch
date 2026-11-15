package com.benbenyu.btquicklaunch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ProcessStateChecks {
  public static void main(String[] args) {
    Set<String> running = new HashSet<>(Arrays.asList("com.larus.nova", "com.openai.chatgpt:voice", "com.autonavi.minimap.fake"));
    check(MainActivity.processActive(running, "com.larus.nova"), "main process");
    check(MainActivity.processActive(running, "com.openai.chatgpt"), "subprocess");
    check(!MainActivity.processActive(running, "com.autonavi.minimap"), "similar prefix is not a match");
    String[] slots = {"com.openai.chatgpt", "com.larus.nova", "com.autonavi.minimap"};
    String swap = slots[1]; slots[1] = slots[2]; slots[2] = swap;
    check(!MainActivity.processActive(running, slots[1]), "old slot no longer highlighted");
    check(MainActivity.processActive(running, slots[2]), "running package follows reorder");
    running.remove("com.larus.nova");
    check(!MainActivity.processActive(running, slots[2]), "stopped application loses highlight");
    System.out.println("PASS process/subprocess detection, package isolation, reorder and stopped state");
  }

  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
