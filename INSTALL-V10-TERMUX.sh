#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
APK=app/build/outputs/apk/debug/app-debug.apk
TV="${TV:-10.0.0.53:5555}"
[ -s "$APK" ] || { echo "No APK: run bash BUILD-TERMUX.sh first" >&2; exit 1; }
adb connect "$TV"
adb -s "$TV" install -r "$APK"
# Verify BEFORE reporting success; leave saved app data intact.
adb -s "$TV" shell dumpsys package com.leaftv.launcher | grep -E -m2 'versionCode=|versionName='
adb -s "$TV" shell am force-stop com.leaftv.launcher
adb -s "$TV" shell am start -n com.leaftv.launcher/.MainActivity
echo 'Look for LeafTV · v10 in the TV header; All Apps · v10 in Apps.'
