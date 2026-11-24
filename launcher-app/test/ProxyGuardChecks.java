package com.benbenyu.btquicklaunch;

public final class ProxyGuardChecks {
  public static void main(String[] args) {
    String trace = "fl=21f12\nip=203.0.113.8\nts=1700000000\nloc=jp\nwarp=off\n";
    check("jp".equals(ProxyGuard.parseTraceValue(trace, "loc=")), "parse trace loc");
    check("203.0.113.8".equals(ProxyGuard.parseTraceValue(trace, "ip=")), "parse trace ip");
    check("".equals(ProxyGuard.parseTraceValue(trace, "missing=")), "missing trace key returns empty");
    check("".equals(ProxyGuard.parseTraceValue(null, "loc=")), "null trace body returns empty");

    check("日本".equals(ProxyGuard.countryDisplayName("jp")), "JP display name");
    check("美国".equals(ProxyGuard.countryDisplayName("US")), "US display name");
    check("英国".equals(ProxyGuard.countryDisplayName("UK")), "UK alias display name");
    check("SG".equals("SG") && "新加坡".equals(ProxyGuard.countryDisplayName("SG")), "SG display name");
    check("NL".equals("NL") && "荷兰".equals(ProxyGuard.countryDisplayName("nl")), "NL display name");
    check("BR".equals(ProxyGuard.countryDisplayName("br")), "unlisted country falls back to uppercase ISO");
    check("".equals(ProxyGuard.countryDisplayName("")), "empty country returns empty");

    System.out.println("PASS ProxyGuard trace parsing and country display mapping");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
