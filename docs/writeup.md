# What we built, and how: a brief for another AI

This document describes the project in enough detail that another AI model (or
a person) can write about it accurately. Everything here was done in one working
session on 22 October 2026, from a Mac, against a real TV in the room.

## The goal, and the first correction

The owner has a 55-inch TV and wanted to "get rid of the OS and put my own
software on it", with the Samsung-focused projects
[CleanFrame](https://github.com/nthnclrk/CleanFrame) as the reference.

Two facts reshaped the task immediately:

1. **CleanFrame does not replace an OS.** It is a Tizen web app, packaged as a
   `.wgt`, signed with a Samsung developer certificate and sideloaded onto a
   Samsung The Frame. Its "quieter home screen" is an app that is then made the
   Home target. The reference was a design and an approach, not reusable code.
2. **The owner's TV is not a Samsung.** A photo of the TV's software screen
   showed a Philips on platform **TPM171E**, running **Android TV 8.0** (API 26),
   build `TPM171E_R.107.001.143.000`, fingerprint
   `Philips/PH7M_EU_5596/PH7M_EU_5596:8.0.0/OC/4718:user/release-keys`, built in
   November 2021. That is a MediaTek-based TP Vision set from the 2017/2018
   range.

Removing Android from such a TV is not realistic: the bootloader is locked, the
firmware is signed, and the tuner, HDMI switching, Ambilight, remote and audio
all live inside the vendor's Android build. Owners of this exact platform also
report that `adb reboot bootloader` can soft-brick it. So the project became
the Android equivalent of CleanFrame, modelled on the second reference it
credits, [TV Detox](https://github.com/Ansub/tv-detox):

- a **replacement home screen** installed over adb, made the Home target;
- the **ad, promo and telemetry packages disabled**, not deleted, with a full undo.

## Research that fed the package list

- TV Detox's `install.sh`, `build.sh`, `MainActivity.java` and `docs/debloat.md`
  (how it builds without Gradle, how it sets Home, which Google packages are
  safe to turn off, which must never be).
- Two community lists from the `cleanyourtv` project: a Philips 50PUS8517 on
  Android 11, and, decisively, a **Philips 65PUS8102 on the same TPM171E /
  Android 8.0 platform**, verified by its author across standby and reboots.
  That list also documented a known quirk: the adb pairing dialog on this
  firmware only appears after revoking authorisations and a mains power cycle.
- The `yath/tpm171e` repository for low-level platform notes, including the
  bootloader warning.

## Toolchain (nothing but the SDK)

Installed with Homebrew: `android-platform-tools` (adb), `openjdk@17`,
`android-commandlinetools`, then `platforms;android-34` and
`build-tools;34.0.0`. There is **no Gradle and no Android Studio**. `build.sh`
runs `aapt2 compile/link`, `javac` against `android.jar`, `d8` to produce
`classes.dex`, `zip`, `zipalign` and `apksigner`. A signing key (`key.jks`) is
generated on first build with a random password stored in `key.pass`; both are
gitignored and must be kept, because updates have to be signed with the same
key. The APK is about 40 KB and declares no permissions.

Two build-script lessons: paths containing spaces broke unquoted `find`
expansions (fixed with an argument file and `xargs -0`), and the SDK's `d8` and
`apksigner` wrappers need `java` on `PATH`, not just a `JAVA_HOME`.

## Getting onto the TV

Developer options on the TV: Android settings → About → press the build entry
seven times (the TV's UI is in Swedish, so the write-up mapped the labels), then
USB debugging on. On this platform, USB debugging alone opens adb on TCP port
5555; no cable.

The first connections sat at `unauthorized` with no dialog on the TV, exactly
the documented quirk. The sequence that worked: revoke USB debugging
authorisations, toggle USB debugging off and on, unplug the TV from the mains
for ten seconds, and reconnect within about a minute of boot. A background loop
reconnecting every five seconds caught the window.

A read-only script (`scripts/candidates.sh`) then dumped every package, the
disabled ones, the leanback apps and the HOME handlers. The TV had 172
packages, nothing disabled, and only two HOME handlers: Google's
`com.google.android.tvlauncher` and Android's fallback. Packages the reference
list did not know were identified by pulling their APKs and reading the label
with `aapt2 dump badging` (for example `org.droidtv.sam` and `org.droidtv.csm`
are both "Service Alignment Mode", `org.droidtv.usbbreakin` is a USB service,
four unknown `nettvapp` IDs were Cinetrailer, TV5Monde, France24 and Social TV).

## Making it the Home screen

`cmd package set-home-activity dev.lucas.tvhome/.MainActivity` was enough on
Android 8: pressing HOME from inside Netflix opened the new screen. The stock
launcher **did not need to be disabled** and is still enabled. The install
script offers to disable it only after the new screen has been launched once,
records the change for undo, and re-enables it automatically if HOME then
opens something else. A `SET_HOME=0` mode installs and opens the app without
touching the Home preference, and explicitly pins the stock launcher as
preferred Home so that having two HOME-capable apps does not trigger Android's
chooser dialog.

## The package list

`packages/philips-tpm171e.txt` has three tiers read by `scripts/debloat.sh`:

- **safe** (49 entries, 43 present on this TV): Google's recommendation rows and
  first-run nags; Philips' "Top Picks" row, usage logger, two analytics
  uploaders (`candeebug`, `gel`), the Inscape ACR client that fingerprints
  whatever is on screen including HDMI, retail demo mode, the Smart TV
  Collection store, eighteen preinstalled NetTV web apps, and preloads like
  Gameloft, TED, GameFly and Tellybean.
- **choice** (16): things that work fine off but cost a feature: Assistant and
  the mic button, Alexa, Chromecast, DLNA, stock YouTube, TalkBack, the
  screensaver, Netflix and Prime Video themselves, the stock launcher.
- **keep** (63): tuner, inputs, remote key handling, system UI, Ambilight,
  firmware update, settings backends, and two packages (`org.droidtv.eum`,
  `org.droidtv.nettvbrowser`) that hung the TV engine or broke HbbTV on other
  models when disabled.

Everything uses `pm disable-user --user 0`, which hides and stops a package
without deleting it. Each disabled package is appended to an undo file on the
TV (`/data/local/tmp/tvhome-disabled.txt`); `scripts/undo.sh` re-enables them
and restores the stock Home. Packages that `dumpsys` marks `PERSISTENT` (the
Alexa bridge, among others) are skipped because they would not stay off. The
safe tier was run on the TV at the end of the session: 42 packages off, one
skipped as persistent, Live TV and the Home button verified afterwards. A bug
surfaced on the first run: `adb shell` inside a `while read` loop consumed the
rest of the package list from stdin, so only the first package was disabled;
the fix is `</dev/null` on every adb call in `scripts/lib.sh`.

## The launcher, iteration by iteration

**Version 0.1**: a grid of app banners with a clock and a bottom row of Live
TV, HDMI inputs, Play Store, Settings. Three layout bugs found by screenshot
over adb (`screencap`): the bottom row overflowed the screen, icon tiles drew
the icon over the label, and a focused banner showed an empty caption bar.

**Version 0.2**: redesigned after CleanFrame's look. Full-screen photo, time
top left, three line glyphs top right drawn in code, a frosted dock along the
bottom with the apps the owner chose. Photos come from a `wallpapers/` folder
packed into the APK by `build.sh`, or are pushed to the TV's app-private folder
by `scripts/wallpapers.sh`, which converts iPhone HEIC to JPEG and resizes to
1920 px wide with `sips`. Twenty-one of the owner's photos were pushed this way.

**Version 0.3** (current): the "pixel-perfect, liquid glass" pass.

- **White logos without any artwork.** `Mono.java` turns each app's own icon
  into a white glyph. The icon is drawn at 96×96; for adaptive icons only the
  foreground layer is used. The background is found by flood-filling inward from
  the border with a local colour-step tolerance and a global distance from the
  border colour, which copes with gradient backgrounds. Everything not reached
  is the logo and becomes white. Enclosed areas in the background colour become
  holes, and so do near-white or near-black details that differ strongly from
  the shape's most common colour (a play arrow in a disc, sound waves in a
  circle). The mask is softened with a one-pixel blur, trimmed to its content
  bounds and fitted into the icon box so wordmarks and discs read at similar
  sizes. Results that would be almost all or almost nothing white are rejected
  and the colour icon is used instead; a settings toggle switches all logos to
  colour.
- **Glass.** `Glass.java` is a drawable that draws the blurred wallpaper behind
  the panel, a dark tint, a highlight gradient along the top and a hairline
  border. The blur is a single 1/8-scale copy of the screen, box-blurred three
  times in plain Java (no RenderScript, which is deprecated and unreliable on
  this hardware); each panel maps its own screen rectangle into that bitmap
  through a shader matrix. The dock, the app drawer, the settings panel and the
  edit sheet all share it.
- **App drawer**, Launchpad style: a seven-column grid of everything on the TV
  (apps, HDMI inputs, Live TV, Play Store, TV settings) on a glass sheet. OK
  opens, long press adds to the dock; a second mode toggles dock membership with
  a green check badge.
- **Settings panel**: a strip of photo thumbnails (decoded on a background
  thread and cached as small JPEGs) with a Shuffle tile; how often the photo
  changes (every visit, 10 minutes, hour, never); white or colour logos; dock
  editing and reset; TV settings and network shortcuts.
- **Animation**: dock items scale up with an overshoot and lift on focus, the
  background highlight colour animates, panels fade and slide in over a dimmed
  background, wallpapers crossfade between two stacked ImageViews, and the dock
  staggers in on first launch.
- **Remote model**: left/right along the dock, up to the glyphs, OK opens, long
  OK or MENU edits, BACK closes panels and does nothing on the home screen
  itself, HOME returns to the first dock item.

Verification throughout was by screenshots pulled from the TV, checking the
resumed activity with `dumpsys activity`, and reading `logcat` for crashes.
There were none.

## A preview loop, and the clipping bugs it found

After the first TV builds, the owner saw borders cut off and focus circles
flattened. Rather than iterate on the TV, the project gained an exact preview:
the Android emulator with the Android TV 14 image at 1920×1080, driven by
`scripts/preview.sh` (boot, install, set Home, push photos, simulate remote
keys, screenshot, and a watch mode that rebuilds and reloads on every save).
Two setup surprises: the emulator refuses an SDK folder without a
`platform-tools` subfolder (Homebrew keeps adb elsewhere, so the script links
it), and Android TV 14 keeps sending HOME to its own launcher even when another
app holds the Home role, so the stock launcher is switched off on the emulator
only. A placeholder mode fills the dock with the TV's app names, since the
emulator has none of those apps.

Every clipping bug had one cause: an item grows on focus, but its container
gave it no room. The fixes, each verified with before and after screenshots:

- The dock now insets its items 16 px top and bottom like the reference, the
  focused card grows 3% instead of 7% plus a lift, and both ends fade into the
  glass instead of cutting an item in half.
- The top-right circles sit in a row padded by 8 px and shifted out by the
  same amount, so a focused circle stays round.
- The settings photo strip, the app-drawer rows and the chip rows each carry
  their own padding to grow into, reaching into the panel padding by the same
  amount so alignment is unchanged.
- The glass panels now report a rounded outline, so content clips to the
  rounded corners rather than a rectangle.

One finding worth keeping: on this Android build, letting a view draw past its
own edges (`clipChildren=false`) did not reliably work for nested rows, while
letting it draw into its own padding (`clipToPadding=false`) always did. Every
fix uses the second.

## Version 0.4: "as if Apple made it, but not Apple TV"

The owner sent a picture of an iPad-style dock and asked for that feel: date,
time and weather top left, Wi-Fi and the app drawer top right, and a dock of
large colour icons with no text. Everything was built and checked in the
emulator first; the TV was not touched.

- **Colour icons in Apple's shape.** `AppIcon.java` redraws each app's own icon
  as a superellipse ("squircle") rather than shipping any artwork. Android TV
  icons come in every shape, so each is sorted: adaptive icons use their
  background and foreground layers; full-bleed squares are cropped; rounded
  squares are zoomed slightly so their own corners fall outside the new shape,
  with the gap filled in their edge colour; round badges sit on a plate of
  their own outer colour so the circle merges into a square; bare logos sit on
  white or near-black depending on how light they are. A faint top sheen and a
  hairline rim finish them.
- **The dock** floats centred above the bottom edge, sized to its contents. Icon
  size is solved so all apps fit (128 px down to 96 px, then it scrolls). The
  glass is only the lower part of the dock's box; the space above it is where a
  focused icon rises into, growing 18% from its base with an overshoot and a
  deepening shadow. That keeps the earlier lesson: grow into your own padding,
  never past an edge.
- **Header**: weekday and date with a multicolour weather symbol and the
  temperature, above a large time. **Status capsule**: a small glass pill with a
  live Wi-Fi indicator (signal bars, Ethernet symbol, or crossed out offline)
  and the app-drawer button; a focused button turns into a white disc with a
  dark glyph.
- **Weather** is the launcher's first network use. Open-Meteo, no key, location
  rounded to about a kilometre; location from ipapi.co (geojs.io as fallback)
  or a typed city; cached, refreshed at most every 30 minutes, and switchable
  off. Fahrenheit only where the TV's region uses it.
- **A settings choice between Colour and Glass icons**, at the owner's request:
  Glass shows the white logos from `Mono.java` on frosted tiles. Glass icons
  lift without a shadow, because a shadow shows through a see-through tile as a
  dark square.

Bugs the preview caught before they reached the TV: the header sized itself to
the time (a `TextClock` sets its text only once attached), which cut the date
off and squeezed the weather to zero width; and closing a panel let Android
hand focus to the first free button, which then overwrote the record of where
focus should return to.

## Numbers

- Java: eight files, about 2,900 lines, no libraries, no XML layouts.
- Shell: seven scripts.
- Default dock: the streaming apps actually installed on the TV, in a chosen
  order, editable on the TV.
- Package list: 128 entries across three tiers.

## Still open

- The choice tier (Assistant, Alexa, Chromecast, stock YouTube and so on) was
  deliberately left alone.
- USB debugging is still on and should be turned off when finished.
- The white-logo heuristic produces blobs for a few icons that are full-bleed
  artwork; those apps are mostly the preloads that get disabled anyway.
- Fonts are the system Roboto; CleanFrame's custom fonts were not copied.
