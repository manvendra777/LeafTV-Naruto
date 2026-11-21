#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
command -v curl >/dev/null || { echo 'Install curl: pkg install curl'; exit 1; }
command -v file >/dev/null || { echo 'Install file: pkg install file'; exit 1; }
mkdir -p wallpaper-imports/ghost
OUT=wallpaper-imports/ghost
# Real distinct Ghost of Tsushima photos/wallpapers from multiple hosts,
# selected from searchable source image results. Personal use only.
URLS=(
'https://w.wallhaven.cc/full/yx/wallhaven-yx6qp7.jpg'
'https://w.wallhaven.cc/full/je/wallhaven-jex59m.jpg'
'https://w.wallhaven.cc/full/jx/wallhaven-jxxdo5.jpg'
'https://w.wallhaven.cc/full/po/wallhaven-po9zle.png'
'https://w.wallhaven.cc/full/p9/wallhaven-p9mpre.jpg'
'https://rare-gallery.com/mocahbig/410460-Ghost-of-Tsushima-4K-PlayStation-4-Playstation-5.jpg'
'https://rare-gallery.com/uploads/posts/900173-Jin-Sakai-Ghost-of-Tsushima-samurai-screen-shot.png'
'https://www.chromethemer.com/download/hd-wallpapers/ghost-of-tsushima-3840x2160.jpg'
'https://images5.alphacoders.com/126/1266676.jpg'
'https://rare-gallery.com/uploads/posts/302829-Ghost-of-Tsushima-Jin-Sakai-4K.jpg'
'https://cdn.mos.cms.futurecdn.net/CTYxMWagKe6b3SasmbJy4B.jpg'
'https://images.pushsquare.com/d77e1625f0403/edtpnttwaaa04-c.large.jpg'
'https://cdn.mos.cms.futurecdn.net/e2vU2XUtLPoRNAWGHNhFcF-2560-80.jpg'
'https://www.wallpapers.com/images/hd/fantasmadi-tsushima-jin-sakai-si-avvicina-ai-nemici-in-4k-1xhzjfiys7udfsqm.jpg'
)
slot=2
for url in "${URLS[@]}"; do
  while [ "$slot" -le 8 ] && [ -s "$OUT/ghost-$slot.download" ]; do slot=$((slot+1)); done
  [ "$slot" -le 8 ] || break
  echo "Trying Ghost wallpaper #$slot: ${url%%\?*}"
  part="$OUT/.candidate.part"
  rm -f "$part"
  if curl -fL --compressed --retry 1 --connect-timeout 8 --max-time 24 -A 'Mozilla/5.0' -o "$part" "$url" 2>/dev/null; then
    mime="$(file -b --mime-type "$part")"
    bytes="$(wc -c < "$part")"
    if [ "$bytes" -gt 30000 ] && [[ "$mime" == image/jpeg || "$mime" == image/png || "$mime" == image/webp ]]; then
      mv "$part" "$OUT/ghost-$slot.download"
      printf '%s\n' "$url" > "$OUT/ghost-$slot.source"
      echo "  Saved new image #$slot ($mime, $bytes bytes)"
      slot=$((slot+1))
      continue
    fi
    echo "  URL returned invalid or small file ($mime, $bytes bytes)"
  else
    echo '  Unreachable on this network; trying another host'
  fi
  rm -f "$part"
done
have=$(find "$OUT" -maxdepth 1 -name 'ghost-[2-8].download' -type f | wc -l | tr -d ' ')
echo "Saved $have/7 additional Ghost images locally."
echo 'Now run: bash BUILD-TERMUX.sh && bash INSTALL-V14-TERMUX.sh'
echo 'If some hosts are blocked, download 16:9 Ghost images in a browser and save them as wallpaper-imports/ghost/ghost-2.download through ghost-8.download.'
