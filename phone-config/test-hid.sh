#!/system/bin/sh
node=$(getevent -lp 2>/dev/null | awk '/add device/{dev=$NF} /"COIDEA KM"/{print dev;exit}')
[ -n "$node" ] || { echo KEYBOARD_NOT_CONNECTED;exit 2; }
key(){ sendevent "$node" 1 "$1" "$2";sendevent "$node" 0 0 0; }
case "$1" in
 double2) key 3 1;sleep .08;key 3 0;sleep .12;key 3 1;sleep .08;key 3 0;;
 triple) key 2 1;key 3 1;key 4 1;sleep .1;key 2 0;key 3 0;key 4 0;;
esac
