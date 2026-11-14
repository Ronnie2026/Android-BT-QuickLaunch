#!/system/bin/sh
apk="$(pm path com.benbenyu.btquicklaunch | head -n1 | cut -d: -f2)"
CLASSPATH="$apk:/data/local/tmp/simplehome-qa.jar" app_process /system/bin com.benbenyu.btquicklaunch.DeviceChecks
