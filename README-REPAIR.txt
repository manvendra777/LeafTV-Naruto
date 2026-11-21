LEAFTV v14 — OFFLINE-FIRST GHOST WALLPAPER REPAIR

WHY: The earlier script made every app rebuild depend on seven new images from hosts that
refused connections. The old 43 local files had already downloaded successfully.

APPLY:
  cd ~/LeafTV/LeafTV
  unzip -o ~/storage/downloads/LeafTV-v14-offline-build-fix.zip -d .

BUILD/INSTALL RIGHT NOW WITHOUT WAITING ON WALLPAPER WEBSITES:
  bash BUILD-TERMUX.sh && bash INSTALL-V14-TERMUX.sh

To try downloading 7 new, different Ghost of Tsushima wallpapers from multiple hosts:
  pkg install curl file -y
  bash GET-GHOST-WALLPAPERS.sh
  bash BUILD-TERMUX.sh && bash INSTALL-V14-TERMUX.sh

The images are downloaded to local folder wallpaper-imports/ghost, normalized to 1920x1080
by Gradle, and stored inside the APK. No TV runtime downloads. The build prints the
ACTUAL number of available Ghost wallpapers (1 through 8); it never invents a full set.

If the 7 image hosts block downloads, you can save your own real landscape JPG/PNG images
from your browser as:
  wallpaper-imports/ghost/ghost-2.download
  wallpaper-imports/ghost/ghost-3.download
  ... through ghost-8.download
and run bash BUILD-TERMUX.sh again. Existing 43 images need not be touched.

NOTE: This package contains code changes only, not newly downloaded copyrighted game artwork.
No 50-image build is promised until all missing images have actually been imported.
