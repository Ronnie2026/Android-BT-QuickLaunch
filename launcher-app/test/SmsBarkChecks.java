package com.benbenyu.btquicklaunch;

public final class SmsBarkChecks {
  public static void main(String[] args) throws Exception {
    check(
        "https://api.day.app/DemoKey123456".equals(
            SmsBarkForwarder.normalizeEndpoint(
                "https://api.day.app/DemoKey123456/title/body?group=example&ttl=600")),
        "strip sample path and query from full Bark URL");
    check(
        "https://api.day.app/DemoKey123456".equals(
            SmsBarkForwarder.normalizeEndpoint("DemoKey123456")),
        "bare key expands to default Bark host");
    check(
        "https://bark.example.com:8443/MyDeviceKey88".equals(
            SmsBarkForwarder.normalizeEndpoint("https://bark.example.com:8443/MyDeviceKey88/")),
        "custom host and port preserved");
    check("".equals(SmsBarkForwarder.normalizeEndpoint("   ")), "blank endpoint rejected");
    check("".equals(SmsBarkForwarder.normalizeEndpoint("ftp://bad/key")), "non-http scheme rejected");

    check(
        "https://open.feishu.cn/open-apis/bot/v2/hook/abc123456".equals(
            SmsBarkForwarder.normalizeWebhookUrl(
                " https://open.feishu.cn/open-apis/bot/v2/hook/abc123456 ")),
        "valid https webhook preserved");
    check("".equals(SmsBarkForwarder.normalizeWebhookUrl("not-a-url")), "invalid webhook rejected");
    check(
        SmsBarkForwarder.maskWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/abc123456")
            .startsWith("https://open.feishu.cn/..."),
        "webhook URL masked safely");

    check(
        "https://api.day.app/Demo...3456".equals(
            SmsBarkForwarder.maskEndpoint("https://api.day.app/DemoKey123456")),
        "mask device key in UI summary");
    check("未配置（点击填入）".equals(SmsBarkForwarder.maskEndpoint("")), "empty endpoint mask");

    check("短信 · 10681234".equals(SmsBarkForwarder.buildBarkTitle(" 10681234 ")), "format SMS title");
    String body = SmsBarkForwarder.buildBarkBody("验证码 886622", 1700000000000L, "");
    check(body.startsWith("验证码 886622\n\n[来自安卓助理的通知 · "), "default source tag appended");
    check("a%20b%2Fc".equals(SmsBarkForwarder.rfc3986Encode("a b/c")), "RFC3986 space and slash encoding");

    System.out.println("PASS SmsBarkForwarder dual-channel URL normalization, masking, formatting, and encoding");
  }

  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
