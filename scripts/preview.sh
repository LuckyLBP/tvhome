#!/bin/sh
# Live preview of the home screen in an Android TV emulator on this Mac.
# Nothing here touches the real TV.
#
#   sh scripts/preview.sh setup            # create the emulator (needs the SDK pieces below)
#   sh scripts/preview.sh start            # boot it, install, open the home screen
#   sh scripts/preview.sh watch            # rebuild + reinstall on every save, screenshot each time
#   sh scripts/preview.sh shot [name]      # screenshot to .local/preview/<name>.png
#   sh scripts/preview.sh key up|down|left|right|ok|back|home|menu|longok
#   sh scripts/preview.sh photos <folder>  # push photos into the emulator only
#   sh scripts/preview.sh dock [labels]    # placeholder dock items named like the TV's apps
#   sh scripts/preview.sh dock off         # back to the emulator's real apps
#   sh scripts/preview.sh stop
#
# One-time downloads (about 1.2 GB from dl.google.com), only when you choose to:
#   sdkmanager "emulator" "system-images;android-34;android-tv;arm64-v8a"
#
# The emulator runs Android TV 14 at 1920x1080. The TV runs Android TV 8; the
# views, sizes, clipping and animations render the same, but the emulator has
# none of your streaming apps, so the dock shows whatever it has installed.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
PKG=dev.lucas.tvhome
ACTIVITY=$PKG/.MainActivity
AVD=tvhome-preview
IMAGE="system-images;android-34;android-tv;arm64-v8a"
OUT=$ROOT/.local/preview
mkdir -p "$OUT"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}
export ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK
[ -n "${JAVA_HOME:-}" ] || JAVA_HOME=/opt/homebrew/opt/openjdk@17
export JAVA_HOME PATH=$JAVA_HOME/bin:$PATH
EMU=$SDK/emulator/emulator

need_sdk() {
  # The emulator refuses an SDK folder without platform-tools; Homebrew keeps adb elsewhere.
  if [ ! -e "$SDK/platform-tools" ] && command -v adb >/dev/null 2>&1; then
    pt=$(dirname "$(readlink -f "$(command -v adb)")")
    [ "$(basename "$pt")" = platform-tools ] && ln -sfn "$pt" "$SDK/platform-tools"
  fi
  if [ ! -x "$EMU" ] || [ ! -d "$SDK/system-images/android-34/android-tv/arm64-v8a" ]; then
    echo "The emulator or the Android TV 14 image is not installed. One-time, about 1.2 GB:"
    echo "  sdkmanager \"emulator\" \"$IMAGE\""
    exit 1
  fi
}

# Android 10+ makes Home a role; older versions use the preferred activity. Do both.
# Android TV 12+ keeps sending HOME to its own launcher even when another app holds the
# role, so on the emulator (and only there: e() refuses anything but an emulator) the
# stock launcher is switched off. The TV runs Android 8 and does not need this.
set_home() {
  e shell pm disable-user --user 0 com.google.android.tvlauncher >/dev/null 2>&1 || true
  e shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
  e shell cmd package set-home-activity "$ACTIVITY" >/dev/null 2>&1 || true
}

serial() { adb devices | awk '/^emulator-[0-9]+\tdevice/{print $1; exit}'; }
# Always the emulator, never a real TV: with no emulator running, refuse rather than
# let adb fall back to whatever device it finds.
e() {
  s=$(serial)
  [ -n "$s" ] || { echo "No preview emulator is running. Start it: sh scripts/preview.sh start" >&2; exit 1; }
  adb -s "$s" "$@" </dev/null
}

case "${1:-}" in
  setup)
    need_sdk
    if avdmanager list avd 2>/dev/null | grep -q "Name: $AVD"; then echo "Emulator $AVD already exists."; exit 0; fi
    echo no | avdmanager create avd -n "$AVD" -k "$IMAGE" -d tv_1080p --force >/dev/null
    CFG=$HOME/.android/avd/$AVD.avd/config.ini
    # 1080p panel, no on-screen skin, enough RAM to stay smooth.
    sed -i '' -e 's/^hw.ramSize=.*/hw.ramSize=2048/' "$CFG" 2>/dev/null || true
    grep -q '^hw.keyboard=' "$CFG" && sed -i '' 's/^hw.keyboard=.*/hw.keyboard=yes/' "$CFG" || echo 'hw.keyboard=yes' >> "$CFG"
    echo "Created emulator $AVD (Android TV 14, 1920x1080)."
    ;;

  start)
    need_sdk
    if [ -z "$(serial)" ]; then
      echo "Booting $AVD (first boot takes a minute or two)..."
      nohup "$EMU" -avd "$AVD" -no-boot-anim -no-snapshot-save -gpu host -scale 0.5 \
        >"$OUT/emulator.log" 2>&1 &
      i=0
      until [ -n "$(serial)" ] && [ "$(e shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do
        i=$((i+1)); [ $i -gt 180 ] && { echo "Emulator did not boot; see $OUT/emulator.log"; exit 1; }
        sleep 2
      done
    fi
    [ -f "$ROOT/out/tvhome.apk" ] || sh "$ROOT/build.sh" >/dev/null
    e install -r "$ROOT/out/tvhome.apk" >/dev/null
    set_home
    e shell am start -W -n "$ACTIVITY" >/dev/null
    echo "Preview running on $(serial). The emulator window shows the home screen."
    ;;

  watch)
    need_sdk
    [ -n "$(serial)" ] || sh "$0" start
    STAMP=$OUT/.stamp
    touch "$STAMP"
    echo "Watching src/, res/, apps.txt and the manifest. Ctrl-C stops."
    while :; do
      changed=$(find "$ROOT/src" "$ROOT/res" "$ROOT/apps.txt" "$ROOT/AndroidManifest.xml" -newer "$STAMP" -type f 2>/dev/null | head -1)
      if [ -n "$changed" ]; then
        touch "$STAMP"
        t0=$(date +%s)
        if sh "$ROOT/build.sh" >"$OUT/build.log" 2>&1; then
          e install -r "$ROOT/out/tvhome.apk" >/dev/null
          set_home    # a reinstall clears the Home choice
          e shell am force-stop "$PKG"
          e shell am start -W -n "$ACTIVITY" >/dev/null
          sleep 2
          e exec-out screencap -p > "$OUT/latest.png"
          echo "$(date +%T) reloaded in $(( $(date +%s) - t0 ))s  ->  .local/preview/latest.png"
        else
          echo "$(date +%T) BUILD FAILED, see .local/preview/build.log:"
          grep -E 'error:' "$OUT/build.log" | head -5
        fi
      fi
      sleep 1
    done
    ;;

  shot)
    name=${2:-latest}
    e exec-out screencap -p > "$OUT/.$name.tmp" && mv "$OUT/.$name.tmp" "$OUT/$name.png"
    echo ".local/preview/$name.png"
    ;;

  key)
    case "${2:-}" in
      up) k=KEYCODE_DPAD_UP ;; down) k=KEYCODE_DPAD_DOWN ;; left) k=KEYCODE_DPAD_LEFT ;;
      right) k=KEYCODE_DPAD_RIGHT ;; ok) k=KEYCODE_DPAD_CENTER ;; back) k=KEYCODE_BACK ;;
      home) k=KEYCODE_HOME ;; menu) k=KEYCODE_MENU ;;
      longok) e shell input keyevent --longpress KEYCODE_DPAD_CENTER; exit 0 ;;
      *) echo "key: up down left right ok back home menu longok"; exit 1 ;;
    esac
    e shell input keyevent "$k"
    ;;

  photos)
    dir=${2:-}
    [ -d "$dir" ] || { echo "photos: give a folder"; exit 1; }
    s=$(serial); [ -n "$s" ] || { echo "No preview emulator is running."; exit 1; }
    sh "$HERE/wallpapers.sh" "$s" add "$dir"/*
    ;;

  dock)
    F=/sdcard/Android/data/$PKG/files/preview-dock.txt
    if [ "${2:-}" = off ]; then
      e shell rm -f "$F"
    else
      labels=${2:-"Netflix,YouTube,SVT Play,TV4 Play,Viaplay,Disney+,Max,SkyShowtime,Apple TV,Prime Video,Plex,Spotify,Twitch"}
      e shell mkdir -p "/sdcard/Android/data/$PKG/files"
      printf '%s\n' "$labels" | tr ',' '\n' > "$OUT/.dock.txt"
      e push "$OUT/.dock.txt" "$F" >/dev/null
    fi
    e shell am force-stop "$PKG"
    e shell am start -W -n "$ACTIVITY" >/dev/null
    echo "Preview dock: ${2:-TV app names}"
    ;;

  stop)
    [ -n "$(serial)" ] && e emu kill >/dev/null 2>&1 || true
    echo "Emulator stopped."
    ;;

  *)
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
