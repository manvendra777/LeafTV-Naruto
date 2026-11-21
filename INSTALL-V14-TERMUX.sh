#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
APK=app/build/outputs/apk/debug/app-debug.apk
[ -s "$APK" ] || { echo 'No new APK: first run bash BUILD-TERMUX.sh'; exit 1; }
command -v adb >/dev/null || { echo 'Install ADB: pkg install android-tools'; exit 1; }
adb connect 10.0.0.53:5555 || true
adb install -r "$APK" || { echo 'Install failed; old app preserved. If signatures differ, ask before uninstalling because that erases settings.'; exit 1; }
adb shell am force-stop com.leaftv.launcher
adb shell am start -n com.leaftv.launcher/.MainActivity
echo 'Installed app package version:'
adb shell dumpsys package com.leaftv.launcher | grep -m 1 versionName || true
