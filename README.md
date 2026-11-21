<<<<<<< HEAD
# LeafTV v15 — Home UI + immersive wallpaper Back

- Smaller Home app cards (102×96 dp, 44 dp icons) with individual focus zoom.
- No pinned-app count in the Home tray; the compact tray is more translucent and occupies less screen space.
- Lighter overall wallpaper scrim for more visible wallpaper colors.
- On **Home**, press Back once for *wallpaper only* (no app tray, nav, or scrim). Press **Back again** to restore Home. This does **not** shuffle or reload the image. Back within an overlay returns through normal navigation. HOME returns the Home controls.
- Maintains existing pin order, cached app icons, selected wallpaper bundles, and the app's previous SharedPreferences.
- No TV runtime wallpaper download. The offline build uses your original 43 images and up to 7 optional Ghost images.
- Optional curated ATLA / Ghost image replacements supported through `wallpaper-imports/iconic/w05_01.jpg` etc; instructions in ICONIC-WALLPAPERS.md, with optional `bash FETCH-ICONIC-ATLA.sh` to try six scene-labeled images. **New copyrighted art is not embedded in this source ZIP.**

## Install (Termux, from existing project)

```
cd ~/LeafTV/LeafTV
unzip -o ~/storage/downloads/LeafTV-v15-ui-patch.zip -d .
bash UPGRADE-V15-TERMUX.sh && bash INSTALL-V15-TERMUX.sh
```

Version verification: `adb shell dumpsys package com.leaftv.launcher | grep versionName` should print **15.0**. Same signing key required for update; do not uninstall the older version, as that would erase your app preferences.

Wallpaper source files downloaded earlier remain in the user's existing project and are not included in the source ZIP. A fresh clone must supply them before offline builds. The complete APK cannot be compiled in this environment without the Android SDK and those images; source syntax and unit tests can run here.
=======
# LeafTV-Naruto
Lightweight custom Android TV launcher
>>>>>>> origin/main
