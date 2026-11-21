#!/usr/bin/env bash
set -euo pipefail
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [ ! -f "$APK" ]; then echo "APK not found: $APK" >&2; exit 1; fi
adb devices
adb install -r "$APK"
echo 'Installed LeafTV. Launch it once, then verify remote navigation and Settings access.'
echo 'To set as HOME (only after testing):'
echo '  adb shell cmd package set-home-activity com.leaftv.launcher/.MainActivity'
echo 'To inspect HOME resolution:'
echo '  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
echo 'To list removable candidates (no changes made):'
echo '  adb shell pm list packages -s'
echo 'Do NOT disable the stock launcher until you have confirmed LeafTV works.'
