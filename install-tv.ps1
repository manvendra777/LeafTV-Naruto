param([string]$Apk = 'app/build/outputs/apk/debug/app-debug.apk')
$ErrorActionPreference = 'Stop'
if (!(Test-Path $Apk)) { throw "APK not found: $Apk" }
adb devices
adb install -r $Apk
Write-Host 'Test LeafTV and TV Settings before changing HOME.'
Write-Host 'Set HOME: adb shell cmd package set-home-activity com.leaftv.launcher/.MainActivity'
Write-Host 'Inspect HOME: adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
Write-Host 'Inspect system packages: adb shell pm list packages -s'
Write-Host 'No system apps were disabled.'
