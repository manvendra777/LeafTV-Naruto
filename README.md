# Leaf TV v2 - anime-themed Android TV launcher

## Home screen
- Compact translucent app tray: 102x96 dp cards, 44 dp icons, each card zooms when focused.
- Light scrim so wallpaper colours stay vivid.
- Back once = wallpaper only (no tray, no scrim). Back again (or any key) = Home returns.
  This never shuffles or reloads the wallpaper. The HOME button also brings the controls back.
- Hold OK on an app (or tap and hold): Open / Pin to front / Unpin / Move left / Move right / App info.
  Pin order is kept. Icons and the app list are cached; they refresh when apps are installed or removed.
- Menu tile (or MENU key): wallpaper bundle, rotation speed, theme, next wallpaper,
  show all apps, clear pins, Android settings. Back inside menus goes back one level.

## Your images
Bundles are folders of images:
- Built into the APK: app/src/main/assets/wallpapers/<BundleName>/*.jpg
  Easiest from Termux:  bash IMPORT-MY-IMAGES.sh ~/LeafTV/LeafTV
- Or loaded on the device without rebuilding (open the app once first):
  adb push Naruto /sdcard/Android/data/com.leaftv.home/files/wallpapers/
Tip: resize to about 1920x1080 so the APK stays small and smooth.
Only use images you have the rights to use.

## Build
Push to GitHub -> Actions -> "Build APK" -> download "LeafTV-apk" -> adb install -r app-debug.apk
Make it the Home button:  adb shell cmd package set-home-activity com.leaftv.home/.MainActivity
