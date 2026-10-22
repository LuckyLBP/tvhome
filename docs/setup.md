# Setup, step by step

Each step is something you can see on the TV before the next one happens.

## 0. What you need

- The TV and this Mac on the same network (Wi-Fi or Ethernet).
- The TV's IP address: Settings → Network → View network settings.
- Homebrew tools, installed once:

```sh
brew install android-platform-tools openjdk@17
brew install --cask android-commandlinetools
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

## 1. Build

```sh
sh build.sh
```

Produces `out/tvhome.apk` and, on the first run, `key.jks` + `key.pass`. Keep
those two files. Every later install must be signed with the same key.

## 2. Developer mode on the TV

The TV's menus are in Swedish; the English path is given with the Swedish
labels in brackets where they are known.

1. Home → Settings (Inställningar) → Android settings (Android-inställningar)
   → About (Om) → scroll to **Build** (Version) and press OK on it seven times.
   A toast says you are now a developer.
2. Back one level → Developer options (Utvecklaralternativ) → **USB debugging**
   (USB-felsökning) → On.

On TPM171E firmware, USB debugging alone opens adb on TCP port 5555. No cable.

## 3. Connect

```sh
adb connect 192.168.1.50
adb devices
```

The first time, the TV shows "Allow USB debugging?". Tick "Always allow from
this computer" and accept. If no prompt ever appears:

- Developer options → Revoke USB debugging authorisations, toggle USB debugging
  off and on, then unplug the TV from the mains for ten seconds and try again.
  This is what worked for the 65PUS8102 owner on the same platform.
- Try `adb connect` a few seconds after the TV boots.

`adb devices` should show `192.168.1.50:5555  device`, not `unauthorized`.

## 4. Look before touching

```sh
sh scripts/candidates.sh 192.168.1.50
```

Read-only. Writes every package, the disabled ones, the apps in the launcher and
the HOME handlers into `.local/`. Compare the `org.droidtv.*` list with
`packages/philips-tpm171e.txt`. Anything on the TV that isn't in the list stays
untouched by `debloat.sh`.

## 5. Install and try the home screen

```sh
SET_HOME=0 sh scripts/install.sh 192.168.1.50
```

It installs the APK and opens it, and leaves the Home button alone. **Stop here and use it**: open Netflix, press
HOME (still goes to the Philips home for now), open the new one again from the
apps row (it's called "Home"), try an HDMI input, open Settings, add and remove
apps. Only when all of that works, run it again without `SET_HOME=0`. That sets the
new screen as the preferred Home and asks before turning off the stock
launcher. The script then presses HOME and tells you what opened.

To set the app row from the Mac instead of on the TV:

```sh
sh scripts/install.sh 192.168.1.50 "com.netflix.ninja,com.google.android.youtube.tv,se.svt.android.svtplay"
```

## 5b. Your photos

```sh
sh scripts/wallpapers.sh 192.168.1.50 add ~/Pictures/one.heic ~/Pictures/two.jpg
sh scripts/wallpapers.sh 192.168.1.50 list
```

iPhone HEIC files are converted on the Mac first. Photos you want built into the
APK go in `wallpapers/` before `sh build.sh`.

## 6. Turn off the ads and telemetry

```sh
sh scripts/debloat.sh 192.168.1.50 safe
```

Asks about every `[safe]` package that is actually installed. `YES=1` in front
skips the questions. Run without `safe` to also be asked about the `[choice]`
tier (Assistant, Alexa, Chromecast, stock YouTube, and so on).

After it finishes: press HOME, open Live TV, switch to an HDMI input, put the TV
in standby and wake it, and do a full power cycle. If anything is wrong,
`scripts/undo.sh` first, then narrow down.

## 7. Finish

Developer options → USB debugging → Off.

## After a firmware update

```sh
sh scripts/check.sh 192.168.1.50
sh scripts/check.sh 192.168.1.50 --fix
```

Updates can re-enable packages and reset the Home preference. `check.sh` shows
the state; `--fix` re-applies the Home preference. Re-run `debloat.sh` if
packages came back.
