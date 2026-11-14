#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 5; done
sleep 8
cmd package set-home-activity com.benbenyu.btquicklaunch/.MainActivity
dumpsys deviceidle whitelist +com.benbenyu.btquicklaunch
svc bluetooth enable
settings --user 0 put secure accessibility_enabled 1
am start --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME -n com.benbenyu.btquicklaunch/.MainActivity
SERVICE=com.benbenyu.btquicklaunch/com.benbenyu.btquicklaunch.ButtonService
while true; do
 enabled="$(settings --user 0 get secure enabled_accessibility_services)"
 case ":$enabled:" in *":$SERVICE:"*|*":com.benbenyu.btquicklaunch/.ButtonService:"*) ;; *)
 case "$enabled" in null|'') enabled="$SERVICE" ;; *) enabled="$enabled:$SERVICE" ;; esac
 settings --user 0 put secure enabled_accessibility_services "$enabled" ;; esac
 if ! dumpsys activity services com.benbenyu.btquicklaunch | grep -q 'ServiceRecord{.*com.benbenyu.btquicklaunch/.ButtonService'; then
 clean="$(settings --user 0 get secure enabled_accessibility_services | tr ':' '\n' | grep -v '^com.benbenyu.btquicklaunch/' | paste -sd ':' -)"
 settings --user 0 put secure enabled_accessibility_services "$clean"
 sleep 1
 settings --user 0 put secure enabled_accessibility_services "$clean:$SERVICE"
 fi
 sleep 30
done
