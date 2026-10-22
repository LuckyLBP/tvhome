#!/bin/sh
# Put the TV back: re-enable every package the other scripts turned off and make
# the stock launcher the Home again. The new home screen stays installed; remove
# it with: adb uninstall dev.lucas.tvhome
#
#   sh scripts/undo.sh <TV_IP>
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"

TV=$(tv_target "${1:-}")
tv_connect
tv_info
echo

list=$(tv_shell "cat $UNDO_FILE 2>/dev/null" | tr -d '\r' || true)
if [ -z "$list" ]; then
  echo "Nothing recorded in $UNDO_FILE on the TV."
else
  echo "Turning back on:"
  echo "$list" | while IFS= read -r p; do
    [ -n "$p" ] || continue
    enable_pkg "$p" || true
  done
  tv_shell "rm -f $UNDO_FILE"
fi

# Anything else that is off and was not ours is listed, not changed.
rest=$(tv_shell pm list packages -d | tr -d '\r' | sed 's/^package://')
if [ -n "$rest" ]; then
  echo
  echo "Still off (not turned off by these scripts, left alone):"
  echo "$rest" | sed 's/^/  /'
fi

if installed "$STOCK_LAUNCHER"; then
  tv_shell cmd package set-home-activity "$STOCK_LAUNCHER/.MainActivity" >/dev/null 2>&1 || true
  if [ "$(sdk_level)" -ge 29 ]; then
    tv_shell cmd role add-role-holder --user 0 android.app.role.HOME "$STOCK_LAUNCHER" >/dev/null 2>&1 || true
  fi
fi
tv_shell input keyevent KEYCODE_HOME
echo
echo "HOME now resolves to: $(home_handler)"
