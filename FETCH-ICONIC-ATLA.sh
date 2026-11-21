#!/data/data/com.termux/files/usr/bin/bash
# Optional; only saves recognizable ATLA scenes that genuinely download.
# Does not change the APK until you build, and never deletes the existing six images.
set -euo pipefail
cd "$(dirname "$0")"
command -v curl >/dev/null || { echo 'Install curl: pkg install curl'; exit 1; }
command -v file >/dev/null || { echo 'Install file: pkg install file'; exit 1; }
OUT=wallpaper-imports/iconic
mkdir -p "$OUT"
# Scene-tagged selections from WallpaperFort: Aang/Appa, Gaang, Avatars,
# Final Agni Kai, Iroh/Zuko, and Aang/Zuko. Personal wallpaper use only.
URLS=(
  'https://i.wallpaperfort.com/avatar-the-last-airbender/f/7.jpg'
  'https://i.wallpaperfort.com/avatar-the-last-airbender/f/45.jpg'
  'https://i.wallpaperfort.com/avatar-the-last-airbender/f/35.jpg'
  'https://i.wallpaperfort.com/avatar-the-last-airbender/f/56.jpg'
  'https://i.wallpaperfort.com/avatar-the-last-airbender/f/20.jpg'
  'https://i.wallpaperfort.com/avatar-the-last-airbender/f/6.jpg'
)
LABELS=( 'Aang and Appa' 'Team Avatar' 'Avatar State' 'Final Agni Kai' 'Zuko and Iroh' 'Aang and Zuko' )
count=0
for i in 0 1 2 3 4 5; do
  id=$(printf '%02d' "$((i+1))")
  dst="$OUT/w05_${id}.jpg"
  if [ -s "$dst" ]; then
    echo "Existing curated ATLA #$id left unchanged: ${LABELS[i]}"
    count=$((count+1))
    continue
  fi
  echo "Fetching ATLA #$id — ${LABELS[i]}"
  temp="$OUT/.w05_${id}.part"
  rm -f "$temp"
  if curl -fLsS --compressed --retry 1 --connect-timeout 10 --max-time 25 \
       -A 'Mozilla/5.0' "${URLS[i]}" -o "$temp"; then
    mime=$(file -b --mime-type "$temp")
    bytes=$(wc -c < "$temp")
    if [ "$bytes" -gt 30000 ] && { [ "$mime" = image/jpeg ] || [ "$mime" = image/png ]; }; then
      mv "$temp" "$dst"
      count=$((count+1))
      echo "  Saved ${LABELS[i]} ($bytes bytes)"
      continue
    fi
    echo "  Invalid response ($mime, $bytes bytes); original wallpaper preserved"
  else
    echo '  Host unreachable; original wallpaper preserved'
  fi
  rm -f "$temp"
done
echo "ATLA curated candidates downloaded: $count/6 (existing wallpaper remains until a successful build)."
echo 'Build: bash UPGRADE-V15-TERMUX.sh && bash INSTALL-V15-TERMUX.sh'
