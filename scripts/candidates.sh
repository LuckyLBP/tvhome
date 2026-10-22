#!/bin/sh
# Dump every package on the TV with its state and flags into .local/packages.txt
# so you can decide what to turn off before touching anything. Read-only.
#
#   sh scripts/candidates.sh <TV_IP>
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"

TV=$(tv_target "${1:-}")
tv_connect
tv_info
OUT=$HERE/../.local
mkdir -p "$OUT"

tv_shell pm list packages -f | tr -d '\r' | sed 's/^package://' | sort > "$OUT/packages-all.txt"
tv_shell pm list packages -d | tr -d '\r' | sed 's/^package://' | sort > "$OUT/packages-disabled.txt"
tv_shell pm list packages -3 | tr -d '\r' | sed 's/^package://' | sort > "$OUT/packages-thirdparty.txt"
tv_shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER 2>/dev/null \
  | tr -d '\r' > "$OUT/leanback-apps.txt" || true
tv_shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
  | tr -d '\r' > "$OUT/home-handlers.txt" || true
tv_shell dumpsys package 2>/dev/null | tr -d '\r' | grep -B30 'PERSISTENT' | grep -o 'Package \[[^]]*\]' | sort -u \
  > "$OUT/persistent.txt" || true

echo
echo "Philips packages (org.droidtv.*):"
grep -o 'org\.droidtv\.[A-Za-z0-9_.]*$' "$OUT/packages-all.txt" | sort -u | sed 's/^/  /'
echo
echo "Apps in the TV launcher: $(grep -c . "$OUT/leanback-apps.txt" 2>/dev/null || echo 0)"
echo "HOME handlers:"
sed 's/^/  /' "$OUT/home-handlers.txt"
echo
echo "Written to $OUT/: packages-all, packages-disabled, packages-thirdparty, leanback-apps, home-handlers, persistent"
echo "Compare against packages/philips-tpm171e.txt before running debloat.sh."
