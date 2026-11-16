package com.benbenyu.btquicklaunch;

public final class PowerIndicatorChecks {
  public static void main(String[] args) {
    check("0.00W\n0mA 0.00V".equals(PowerIndicator.formatPower(0, 0)), "zero voltage and current");
    check("0.00W\n0mA 4.19V".equals(PowerIndicator.formatPower(0, 4191)), "zero current with voltage");
    check("1.26W\n300mA 4.19V".equals(PowerIndicator.formatPower(-300_000, 4191)), "discharging current wattage");
    check("5.00W\n1000mA 5.00V".equals(PowerIndicator.formatPower(1_000_000, 5000)), "charging current wattage");
    check("0.00W\n0mA 4.00V".equals(PowerIndicator.formatPower(Integer.MIN_VALUE, 4000)), "unsupported current sentinel");
    check("35.0°C".equals(PowerIndicator.formatDisplay(0, 4191, 350, false, true)), "temperature-only display");
    check("1.26W  35.0°C\n300mA 4.19V".equals(PowerIndicator.formatDisplay(-300_000, 4191, 350, true, true)), "combined power and temperature display");
    check("".equals(PowerIndicator.formatDisplay(0, 4191, 350, false, false)), "hidden display");
    System.out.println("PASS PowerIndicator wattage, current, voltage, and temperature formatting");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
