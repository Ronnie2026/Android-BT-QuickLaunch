#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 5; done
sleep 8
PKG=com.benbenyu.btquicklaunch
SERVICE=$PKG/com.benbenyu.btquicklaunch.ButtonService
cmd package set-home-activity $PKG/.MainActivity
dumpsys deviceidle whitelist +$PKG
pm grant $PKG android.permission.RECEIVE_SMS 2>/dev/null || true
pm grant $PKG android.permission.READ_SMS 2>/dev/null || true
pm grant $PKG android.permission.READ_PHONE_STATE 2>/dev/null || true
cmd appops set $PKG RUN_IN_BACKGROUND allow 2>/dev/null || true
cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true
cmd appops set $PKG WAKE_LOCK allow 2>/dev/null || true
svc bluetooth enable
settings --user 0 put secure accessibility_enabled 1
am start --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME -n $PKG/.MainActivity
while true; do
 enabled="$(settings --user 0 get secure enabled_accessibility_services)"
 case ":$enabled:" in *":$SERVICE:"*|*":$PKG/.ButtonService:"*) ;; *)
 case "$enabled" in null|'') enabled="$SERVICE" ;; *) enabled="$enabled:$SERVICE" ;; esac
 settings --user 0 put secure enabled_accessibility_services "$enabled" ;; esac
 if ! dumpsys activity services $PKG | grep -q 'ServiceRecord{.*com.benbenyu.btquicklaunch/.ButtonService'; then
 clean="$(settings --user 0 get secure enabled_accessibility_services | tr ':' '\n' | grep -v "^$PKG/" | paste -sd ':' -)"
 settings --user 0 put secure enabled_accessibility_services "$clean"
 sleep 1
 if [ -n "$clean" ]; then
  settings --user 0 put secure enabled_accessibility_services "$clean:$SERVICE"
 else
  settings --user 0 put secure enabled_accessibility_services "$SERVICE"
 fi
 fi
 sleep 30
done
