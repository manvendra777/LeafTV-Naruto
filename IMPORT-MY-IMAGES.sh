#!/usr/bin/env bash
# Copies your wallpaper images into the app so they are built into the APK.
# Each sub-folder becomes a wallpaper bundle (e.g. Naruto, Ghost, ATLA).
# Usage (run inside this project folder, e.g. in Termux):
#   bash IMPORT-MY-IMAGES.sh ~/LeafTV/LeafTV
SRC="${1:?usage: bash IMPORT-MY-IMAGES.sh <folder with your images>}"
DEST="app/src/main/assets/wallpapers"
mkdir -p "$DEST"
find "$SRC" -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) | while read -r f; do
  case "$f" in */mipmap*|*/drawable*|*/build/*|*/.git/*|*/LeafTV/app/src/main/res/*) continue;; esac
  sub="$(basename "$(dirname "$f")")"
  mkdir -p "$DEST/$sub"
  cp -n "$f" "$DEST/$sub/"
done
echo "Bundles now in $DEST:"; ls "$DEST"
du -sh "$DEST"
