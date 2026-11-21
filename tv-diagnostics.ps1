$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force diagnostics | Out-Null
adb shell getprop ro.product.manufacturer | Out-File diagnostics/manufacturer.txt
adb shell getprop ro.product.model | Out-File diagnostics/model.txt
adb shell getprop ro.build.version.release | Out-File diagnostics/android-version.txt
adb shell pm list packages -s | Out-File diagnostics/system-packages.txt
adb shell pm list packages -3 | Out-File diagnostics/user-packages.txt
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Out-File diagnostics/current-home.txt
adb shell dumpsys package com.leaftv.launcher | Out-File diagnostics/leaftv-package.txt
Write-Host 'Diagnostics saved. No packages changed.'
