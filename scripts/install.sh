#!/bin/sh
# Install the home screen and make it the TV's Home.
#
#   sh scripts/install.sh <TV_IP>                       # install, keep current app row
#   sh scripts/install.sh <TV_IP> "com.netflix.ninja,com.google.android.youtube.tv"
#   sh scripts/install.sh <TV_IP> auto                  # forget the saved row, rediscover
#   SET_HOME=0 sh scripts/install.sh <TV_IP>            # install and open only; Home button unchanged
#
# Steps, each visible before it happens:
#   1. connect over adb and print what TV this is
#   2. adb install -r out/tvhome.apk (build.sh first if missing)
#   3. open it so you can see it works
#   4. cmd package set-home-activity  (Android 8+)
#   5. offer to turn off the stock launcher, recording it for undo.sh
#   6. press HOME and report which app answered
#
# Nothing is uninstalled. scripts/undo.sh puts everything back.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"

TV=$(tv_target "${1:-}")
APPS=${2:-}
APK=${APK:-$HERE/../out/tvhome.apk}

tv_connect
tv_info
echo

[ -f "$APK" ] || { echo "No $APK. Building..."; sh "$HERE/../build.sh"; }

echo "Installing $APK"
if ! out=$(tv install -r "$APK" 2>&1); then
  case "$out" in
    *UPDATE_INCOMPATIBLE*|*INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      echo "An older copy signed with a different key is installed."
      if confirm "Uninstall it (your app row on the TV will be reset) and install fresh?"; then
        tv uninstall "$PKG" >/dev/null 2>&1 || true
        tv install -r "$APK"
      else exit 1; fi ;;
    *) echo "$out"; exit 1 ;;
  esac
fi

STAMP=$(date +%s)
if [ -n "$APPS" ]; then
  tv_shell am start -W -n "$ACTIVITY" --es stamp "$STAMP" --es apps "$APPS" >/dev/null
else
  tv_shell am start -W -n "$ACTIVITY" >/dev/null
fi
echo "Opened. You should see the new home screen on the TV now."
echo

if [ "${SET_HOME:-1}" != 1 ]; then
  echo "SET_HOME=0: leaving the Home button as it is. Open \"Home\" from the apps row to try it."
  echo "HOME currently resolves to: $(home_handler)"
  exit 0
fi

SDK=$(sdk_level)
if [ "$SDK" -ge 29 ]; then
  tv_shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
fi
tv_shell cmd package set-home-activity "$ACTIVITY" >/dev/null 2>&1 || true
echo "Preferred Home set to $ACTIVITY"
echo "HOME currently resolves to: $(home_handler)"
echo

if installed "$STOCK_LAUNCHER" && ! disabled "$STOCK_LAUNCHER"; then
  echo "The stock launcher ($STOCK_LAUNCHER) is still on. On most TVs the"
  echo "HOME button keeps opening it until it is turned off. This is reversible"
  echo "with scripts/undo.sh, and the package stays installed."
  if confirm "Turn off $STOCK_LAUNCHER now?"; then
    disable_pkg "$STOCK_LAUNCHER" || true
    sleep 3
    tv_shell cmd package set-home-activity "$ACTIVITY" >/dev/null 2>&1 || true
  fi
fi

tv_shell input keyevent KEYCODE_HOME
sleep 2
top=$(tv_shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -m1 -E 'mResumedActivity|topResumedActivity|ResumedActivity' || true)
case "$top" in
  *"$PKG"*) echo "HOME now opens the new home screen." ;;
  *) echo "HOME opened something else:"; echo "  $top"
     echo "If the screen is black or stuck, run: sh scripts/undo.sh $1" ;;
esac
echo
echo "Done. Turn USB debugging off on the TV when you are finished."
