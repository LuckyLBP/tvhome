# Wallpapers

Drop your own photos in this folder. Two ways to get them onto the TV:

**Built in.** `sh build.sh` packs every `.jpg`, `.jpeg`, `.png` and `.webp` here
into the APK, so they're always there, even after a factory reset of the
launcher's data. Reinstall with `scripts/install.sh` after adding photos.

**Pushed.** `sh scripts/wallpapers.sh <TV_IP> add photo1.heic photo2.jpg ...`
converts (HEIC from an iPhone included), downsizes to 1920 px wide and pushes
each photo to the TV. No rebuild. `list` shows what's on the TV, `remove <name>`
and `clear` take them off again.

On the TV: the picture glyph top right opens the photo picker with thumbnails.
"Shuffle" shows a different photo each time the home screen comes up; settings
lets you choose every visit, every 10 minutes (default), every hour, or only when
you pick one. With no photos at all, a soft dark gradient is used.

Size: the TV decodes photos at 1920×1080, so bigger files only cost APK size
and decode time. The push script resizes for you. For built-in photos, resize
first:

```sh
sips -s format jpeg -Z 1920 IMG_1234.HEIC --out wallpapers/beach.jpg
```

Files in this folder other than README.md are ignored by git by default; drop
the line in `.gitignore` if you want them committed.
