#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
command -v aapt2 >/dev/null || { echo 'Install native AAPT2: pkg install aapt2'; exit 1; }
command -v gradle >/dev/null || { echo 'Install Gradle: pkg install gradle openjdk-17'; exit 1; }
sed -i '/^android\.aapt2FromMavenOverride=/d' gradle.properties
printf '\nandroid.aapt2FromMavenOverride=%s\n' "$(command -v aapt2)" >> gradle.properties
APK=app/build/outputs/apk/debug/app-debug.apk
rm -f "$APK"
rm -f app/src/main/java/com/leafvillage/tv/MainActivity.java
gradle --no-daemon assembleDebug
[ -s "$APK" ] || { echo 'ERROR: no fresh APK was created'; exit 1; }
# Require six GTA VI images in addition to the original 43. Ghost #2-8 are optional.
full=0
for folder in wallpapers wallpaper_thumbs; do
  count=$(find "app/src/main/assets/$folder" -maxdepth 1 -name 'w??_??.jpg' -type f | wc -l | tr -d ' ')
  [ "$count" -ge 48 ] && [ "$count" -le 55 ] || { echo "ERROR: expected 43-50 $folder, got $count"; exit 1; }
  packaged=$(jar tf "$APK" | grep -Ec "^assets/$folder/w[0-9][0-9]_[0-9][0-9]\.jpg$" || true)
  [ "$packaged" -eq "$count" ] || { echo "ERROR: $folder has $count files but APK contains $packaged"; exit 1; }
  if [ "$folder" = wallpapers ]; then full="$count"; else
    [ "$count" -eq "$full" ] || { echo 'ERROR: missing thumbnails'; exit 1; }
  fi
done
for n in 01 02 03 04 05 06; do
  [ -s "app/src/main/assets/wallpapers/w10_$n.jpg" ] || { echo "MISSING GTA VI #$n"; exit 1; }
done
printf 'WALLPAPERS VERIFIED: %s REAL IMAGES + %s THUMBNAILS INSIDE APK (offline)\n' "$full" "$full"
echo "BUILD OK: $PWD/$APK"
