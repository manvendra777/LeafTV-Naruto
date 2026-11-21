# LeafTV v16 – 6 GTA VI + 6 Ghost wallpapers in the APK

1. Download images using `bash ~/storage/downloads/download-game-wallpapers.sh` (provided previously).
2. `pkg install imagemagick python -y`
3. From the existing LeafTV project directory: `bash IMPORT-GAME-WALLPAPERS.sh`.
4. `bash BUILD-TERMUX.sh && bash INSTALL-V16-TERMUX.sh`.

The importer inspects images and accepts only six distinct HD landscape files in **each** source folder. It converts them into 1920×1080 JPEGs under `wallpaper-imports/iconic` with IDs `w10_01.jpg`–`w10_06.jpg` (GTA VI) and `w12_01.jpg`–`w12_06.jpg` (Ghost). The Gradle bundle task imports the images into `app/src/main/assets/wallpapers` and generates thumbnail assets.

It maintains Ghost's existing eight-slot support; slots 7 and 8 are optional. Other games remain available. The Android activity will recognize all six GTA VI images after this patch.

**This patch contains no third-party artwork**; it imports your downloaded files. The ZIP alone is not a finished APK. Do not publish third-party wallpapers to a public repo without permission.
