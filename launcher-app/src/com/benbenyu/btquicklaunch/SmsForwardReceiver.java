package com.benbenyu.btquicklaunch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-priority manifest receiver for incoming SMS. Concatenates multipart PDUs per sender and
 * hands off to {@link SmsBarkForwarder} under a pending-result + WakeLock guard.
 */
public final class SmsForwardReceiver extends BroadcastReceiver {
  public void onReceive(Context context, Intent intent) {
    if (context == null || intent == null) return;
    if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

    SmsMessage[] messages;
    try {
      messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
    } catch (Exception e) {
      AppLog.write("SMS_RECEIVE_PARSE_ERR", e);
      return;
    }
    if (messages == null || messages.length == 0) return;

    LinkedHashMap<String, StringBuilder> bodies = new LinkedHashMap<>();
    LinkedHashMap<String, Long> timestamps = new LinkedHashMap<>();
    for (SmsMessage msg : messages) {
      if (msg == null) continue;
      String sender = SmsBarkForwarder.normalizeSender(msg.getDisplayOriginatingAddress());
      String part = msg.getDisplayMessageBody();
      if (part == null) part = msg.getMessageBody();
      if (part == null) continue;
      StringBuilder sb = bodies.get(sender);
      if (sb == null) {
        sb = new StringBuilder();
        bodies.put(sender, sb);
        timestamps.put(sender, msg.getTimestampMillis());
      }
      sb.append(part);
    }
    if (bodies.isEmpty()) return;

    final PendingResult pending = goAsync();
    final java.util.concurrent.atomic.AtomicInteger remaining =
        new java.util.concurrent.atomic.AtomicInteger(bodies.size());
    Runnable onEachDone =
        () -> {
          if (remaining.decrementAndGet() == 0) {
            try {
              pending.finish();
            } catch (Exception ignored) {
            }
          }
        };

    for (Map.Entry<String, StringBuilder> e : bodies.entrySet()) {
      String sender = e.getKey();
      String fullBody = e.getValue().toString();
      Long ts = timestamps.get(sender);
      SmsBarkForwarder.onSmsReceived(
          context, sender, fullBody, ts != null ? ts : System.currentTimeMillis(), onEachDone);
    }
  }
}
