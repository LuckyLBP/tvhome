# TV Home

A quiet home screen for a Philips Android TV, and the scripts to put it in charge.

The TV this was written for: Philips 55", platform **TPM171E**, **Android TV 8.0**,
build `TPM171E_R.107.001.143.000` (Philips/PH7M_EU_5596, November 2021).

## What this does, and what it does not

You can't remove Android from this TV. The bootloader is locked, the firmware is
signed, and the tuner, HDMI switching, Ambilight, remote and audio all live inside
Philips' Android build. What you *can* do, without root and fully reversibly:

1. **Replace the home screen.** `out/tvhome.apk` is a small Java launcher in
   the spirit of CleanFrame: one of your own photos fills the screen, the time
   sits top left, three glyphs top right (wallpaper, all apps, settings), and a
   frosted dock along the bottom holds the apps you chose. No ads, no rows, no
   "recommendations", no network access at all.
2. **Turn off the ad and telemetry packages.** Google's recommendation rows,
   Philips' Top Picks, the usage logger, the analytics uploaders, the ACR client
   that fingerprints what's on screen, the Smart TV Collection store and its
   preinstalled web apps. They are disabled with `pm disable-user`, which hides
   and stops them. Nothing is deleted, and `scripts/undo.sh` turns them all back on.

This is the same approach as [CleanFrame](https://github.com/nthnclrk/CleanFrame)
(Samsung Tizen) and [TV Detox](https://github.com/Ansub/tv-detox) (Android TV),
written for this specific Philips platform.

## Layout

```
AndroidManifest.xml              HOME + LEANBACK_LAUNCHER activity, minSdk 26
src/dev/lucas/tvhome/            MainActivity.java (the launcher), Mono.java (white logos
                                 from app icons), Glass.java (blur + border + highlight),
                                 Wallpapers.java (photos, thumbnails), Glyph.java (line icons)
res/                             app name and a banner for the stock launcher
apps.txt                         default dock, one package per line
wallpapers/                      your photos; packed into the APK by build.sh
build.sh                         aapt2 + javac + d8 + apksigner, no Gradle
scripts/install.sh               install, set Home, offer to turn off stock launcher
scripts/wallpapers.sh            push photos to the TV without a rebuild (HEIC ok)
scripts/preview.sh               live preview in an Android TV emulator on the Mac
scripts/debloat.sh               turn off packages from the list, one prompt each
scripts/undo.sh                  turn everything back on, restore stock Home
scripts/check.sh                 what HOME opens, what is off, --fix re-applies Home
scripts/candidates.sh            read-only dump of every package into .local/
packages/philips-tpm171e.txt     the package list with [safe] / [choice] / [keep]
docs/setup.md                    step by step, including the remote-side steps
docs/recovery.md                 if HOME goes black, if adb stops answering
```

## Quick start

On the Mac, once:

```sh
brew install android-platform-tools openjdk@17
brew install --cask android-commandlinetools
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

On the TV: Settings → Android settings → About → press **Build** seven times,
then Developer options → **USB debugging** on. On this platform that alone opens
adb on TCP port 5555, no USB cable needed. Note the TV's IP under Network.

Then:

```sh
sh build.sh                              # out/tvhome.apk, creates key.jks on first run
sh scripts/candidates.sh 192.168.1.50    # read-only: see what the TV has
SET_HOME=0 sh scripts/install.sh 192.168.1.50   # install and open only, Home button unchanged
sh scripts/install.sh 192.168.1.50               # set as Home, offer to turn off stock launcher
sh scripts/debloat.sh 192.168.1.50 safe  # ad/telemetry packages, prompt per package
sh scripts/check.sh 192.168.1.50
```

Accept the "Allow USB debugging?" prompt on the TV the first time. Turn USB
debugging off again when you are done: while it is on, anything on your network
can control the TV.

See [docs/setup.md](docs/setup.md) for the long version and
[docs/recovery.md](docs/recovery.md) before you turn off the stock launcher.

## Using the home screen

| Remote        | Action                                                  |
|---------------|---------------------------------------------------------|
| left / right  | move along the dock (it scrolls if you add many apps)   |
| up            | the three glyphs top right                              |
| OK            | open                                                    |
| long OK, MENU | edit a dock item: move left / right, remove, app info   |
| BACK          | nothing, this is the home screen                        |
| HOME          | back here, first dock item                              |

Top right: the **picture** glyph opens the photo picker (long press: next photo
now), the **grid** glyph opens the app drawer, a grid of everything on the TV
(apps, HDMI inputs, Live TV, Play Store, TV settings) where OK opens and a long
press adds to the dock, and the **gear** glyph opens settings: photos with
thumbnails and shuffle, how often the photo changes, white or colour logos, dock
editing, TV settings. BACK closes any panel.

**White logos** are made on the TV from each app's own icon: the icon's
background is found and removed and what's left becomes white, with enclosed
details such as a play arrow cut out. No logo artwork is shipped. Apps whose
icons are full-bleed artwork come out as blobs; switch those TVs to "Colour" in
settings if it bothers you.

Your dock is stored on the TV. `install.sh <IP> "pkg1,pkg2,..."` replaces it,
`install.sh <IP> auto` clears it and lets the launcher rediscover apps.

## Preview on the Mac first

Changes can be checked in an Android TV emulator before anything goes to the TV.
It runs the same APK at 1920×1080, so sizes, clipping, focus and animation look
the same. One-time setup, about 1.2 GB from Google:

```sh
sdkmanager "emulator" "system-images;android-34;android-tv;arm64-v8a"
sh scripts/preview.sh setup
sh scripts/preview.sh start
sh scripts/preview.sh dock                 # placeholder dock named like the TV's apps
sh scripts/preview.sh photos ~/Pictures/tv # your photos, emulator only
sh scripts/preview.sh watch                # rebuild + reload on every save
```

`key up|down|left|right|ok|longok|back|home` drives it like the remote and
`shot <name>` saves a screenshot to `.local/preview/`. The script only ever
talks to an emulator: with none running it refuses instead of falling through
to a TV on the network. The emulator runs Android TV 14 and has none of your
streaming apps, so the dock shows placeholders with each app's initial;
real white logos only appear on the TV.

## Your photos

Put photos in `wallpapers/` and rebuild, or push them without a rebuild:

```sh
sh scripts/wallpapers.sh 192.168.1.50 add ~/Pictures/beach.heic ~/Pictures/forest.jpg
```

See [wallpapers/README.md](wallpapers/README.md).

## Changing it

Everything visual is in `MainActivity.java`: the design metrics and colours at
the top, `buildChrome()` for the clock, glyphs and dock frame, `dockItem()` for
one dock entry, `frost()` for the blur. Rebuild with `sh build.sh`, reinstall
with `sh scripts/install.sh <IP>`. Updates must be signed with the same
`key.jks`; back it and `key.pass` up.

## Risks

- Turning off `com.google.android.tvlauncher` before the new Home is confirmed
  working leaves HOME with nothing to open. `install.sh` only offers it after the
  new home screen has been launched once, and `undo.sh` re-enables it.
- A Philips firmware update can re-enable packages or reset the Home preference.
  `scripts/check.sh <IP> --fix` re-applies it.
- Don't disable anything in the `[keep]` tier. Two packages there,
  `org.droidtv.eum` and `org.droidtv.nettvbrowser`, have hung the TV or broken
  HbbTV on other Philips models when turned off.
- Never run `adb reboot bootloader` on this platform. Owners of TPM171E TVs report
  it can soft-brick the TV.
- A factory reset is the fallback if something goes badly wrong. It clears the
  disabled list and the Home preference.

## References

- [CleanFrame](https://github.com/nthnclrk/CleanFrame), the Samsung Tizen version of this idea
- [TV Detox](https://github.com/Ansub/tv-detox), the Android TV version, and its [package notes](https://github.com/Ansub/tv-detox/blob/main/docs/debloat.md)
- [cleanyourtv: Philips 65PUS8102 (TPM171E, Android 8.0)](https://github.com/Gathin23/cleanyourtv/pull/295), the package list this one is based on
- [cleanyourtv: Philips 50PUS8517 (Android 11)](https://github.com/Gathin23/cleanyourtv/pull/178) and the [follow-up on eum/aurora](https://github.com/Gathin23/cleanyourtv/pull/234)
- [yath/tpm171e](https://github.com/yath/tpm171e), low-level notes on this platform, including the bootloader warning
- [XDA: Philips Android TV](https://xdaforums.com/t/philips-android-tv.2935545/) owners' thread
