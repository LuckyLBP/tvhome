#!/bin/sh
# Show the current state: what HOME opens, what is off, whether the launcher is
# installed. Run after a firmware update to see what changed.
#
#   sh scripts/check.sh <TV_IP>
#   sh scripts/check.sh <TV_IP> --fix     # re-apply the preferred Home
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"

TV=$(tv_target "${1:-}")
tv_connect
tv_info
echo
if installed "$PKG"; then
  echo "Home screen:   installed $(tv_shell dumpsys package "$PKG" | tr -d '\r' | sed -n 's/.*versionName=//p' | head -1)"
else
  echo "Home screen:   NOT installed"
fi
echo "HOME opens:    $(home_handler)"
if installed "$STOCK_LAUNCHER"; then
  if disabled "$STOCK_LAUNCHER"; then echo "Stock launcher: off"; else echo "Stock launcher: on"; fi
fi
echo
echo "Packages off:"
tv_shell pm list packages -d | tr -d '\r' | sed 's/^package:/  /' | sort
echo
echo "Recorded for undo:"
tv_shell "cat $UNDO_FILE 2>/dev/null" | tr -d '\r' | sed 's/^/  /' || true

if [ "${2:-}" = "--fix" ] && installed "$PKG"; then
  echo
  tv_shell cmd package set-home-activity "$ACTIVITY" >/dev/null 2>&1 || true
  echo "Preferred Home re-applied. HOME opens: $(home_handler)"
fi
