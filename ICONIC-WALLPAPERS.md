# Iconic wallpaper refresh — local-only (v15)

This update preserves every existing wallpaper. **It does not claim to include new downloaded ATLA or Ghost images**. We cannot verify the image hosts here, and downloading arbitrary gallery images caused broken builds previously.

## First priorities

**Avatar: The Last Airbender (title 05):** choose 6 recognizable 16:9 scenes:
1. Aang + Appa above the Southern Air Temple / sky bison flying.
2. Team Avatar (Aang, Katara, Sokka, Toph, Zuko) together.
3. Aang in the Avatar State with four elements.
4. Zuko vs. Azula, the Final Agni Kai (blue/red fire).
5. Iroh and Zuko, sunset/tea/Dragon of the West.
6. Aang vs. Ozai at Sozin's Comet / Fire Nation airships.

**Ghost of Tsushima (title 12):** choose up to 8 images: Jin in white pampas grass, iconic red maple leaves, golden fields, dueling under falling sakura, Jin on horseback, Sakai armor/katana, bamboo forests, and Iki Island coast. Keep all images landscape.

**Other titles:** favor each show's/game's primary recognizable key art and iconic scenes, rather than ambiguous generic landscape images. Preserve the existing six Naruto wallpapers.

## Sources to browse (personal device wallpaper use; check reuse terms)

- ATLA scene-labeled 1080p selection: https://wallpaperfort.com/avatar-the-last-airbender-wallpapers/
- ATLA official Nickelodeon photo gallery: https://www.paramountpressexpress.com/nickelodeon/shows/avatar-the-last-airbender/photos/
- Ghost of Tsushima publisher wallpaper gallery: https://www.playstation.com/en-gb/wallpapers/
- Ghost of Tsushima PlayStation game art: https://www.playstation.com/en-us/games/ghost-of-tsushima/

## Import without another unreliable Gradle download

Save JPEG images to the project paths below. These are explicit **optional replacements**:

```
wallpaper-imports/iconic/w05_01.jpg  # ATLA scene 1
wallpaper-imports/iconic/w05_02.jpg  # ATLA scene 2
wallpaper-imports/iconic/w05_03.jpg  # ATLA scene 3
wallpaper-imports/iconic/w05_04.jpg  # ATLA scene 4
wallpaper-imports/iconic/w05_05.jpg  # ATLA scene 5
wallpaper-imports/iconic/w05_06.jpg  # ATLA scene 6
wallpaper-imports/iconic/w12_01.jpg  # Ghost scene 1
... up to w12_08.jpg
```

Images must be >1100 x 600 landscape, real JPEG/PNG-decoded by Java ImageIO (JPG preferred). Run `bash UPGRADE-V15-TERMUX.sh` to normalize them into the packaged 1920×1080 assets and previews. Only supplied slots are replaced. If no iconic files are supplied, the existing 43–50 images remain unchanged. Any invalid supplied file fails clearly so you can correct it. Do not commit downloaded copyrighted wallpapers to a public repository.

## Optional automatic ATLA refresh

Run `pkg install curl file -y` then `bash FETCH-ICONIC-ATLA.sh`. It tries six scene-labeled direct images from WallpaperFort, validating that each response is an image and saving it under `wallpaper-imports/iconic/`. The downloader does **not** run during normal builds and does not overwrite existing wallpapers on a failed download. Sites may block automated requests; any failed slots can instead be saved manually using the same filenames. The next build imports only successful, validated files.
