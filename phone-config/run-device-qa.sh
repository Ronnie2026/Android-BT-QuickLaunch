#!/system/bin/sh
set -eu
apk="$(pm path com.benbenyu.btquicklaunch | head -n1 | cut -d: -f2)"
for check in DeviceChecks HidStateChecks ReminderChecks VoiceTargetChecks SystemToneUriChecks; do
 CLASSPATH="$apk:/data/local/tmp/simplehome-qa.jar" app_process /system/bin "com.benbenyu.btquicklaunch.$check"
done
