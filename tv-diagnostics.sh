#!/usr/bin/env bash
set -euo pipefail
mkdir -p diagnostics
adb shell getprop ro.product.manufacturer > diagnostics/manufacturer.txt
adb shell getprop ro.product.model > diagnostics/model.txt
adb shell getprop ro.build.version.release > diagnostics/android-version.txt
adb shell pm list packages -s > diagnostics/system-packages.txt
adb shell pm list packages -3 > diagnostics/user-packages.txt
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > diagnostics/current-home.txt
adb shell dumpsys package com.leaftv.launcher > diagnostics/leaftv-package.txt
printf 'Diagnostics saved in ./diagnostics. No packages were changed.\n'
