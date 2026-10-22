#!/bin/sh
# Turn off ad, telemetry and promo packages, one at a time, with a prompt for each.
#
#   sh scripts/debloat.sh <TV_IP>                 # ask about every package
#   sh scripts/debloat.sh <TV_IP> safe            # only the [safe] tier, still asking
#   YES=1 sh scripts/debloat.sh <TV_IP> safe      # [safe] tier, no questions
#
# The list is packages/philips-tpm171e.txt. Packages under [keep] are never
# touched. Every package turned off is written to the TV's undo file so
# scripts/undo.sh can restore it. pm disable-user hides and stops a package; it
# does not delete anything.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"

TV=$(tv_target "${1:-}")
ONLY=${2:-all}
LIST=${LIST:-$HERE/../packages/philips-tpm171e.txt}

tv_connect
tv_info
echo
echo "Reading $LIST"
echo

tier=
sed 's/\r//' "$LIST" | while IFS= read -r line; do
  case "$line" in
    "["*"]") tier=$(echo "$line" | tr -d '[]'); continue ;;
    ""|"#"*) continue ;;
  esac
  pkg=$(echo "$line" | sed 's/#.*//' | tr -d ' \t')
  why=$(echo "$line" | sed -n 's/^[^#]*#[ ]*//p')
  [ -n "$pkg" ] || continue
  case "$tier" in
    keep) continue ;;
    safe) ;;
    choice) [ "$ONLY" = "safe" ] && continue ;;
    *) continue ;;
  esac
  installed "$pkg" || continue
  if disabled "$pkg"; then echo "  already off: $pkg"; continue; fi
  echo "[$tier] $pkg"
  [ -n "$why" ] && echo "        $why"
  if confirm "  Turn off $pkg?"; then disable_pkg "$pkg" || true; fi
done

echo
echo "Off now:"
tv_shell pm list packages -d | tr -d '\r' | sed 's/^package:/  /' | sort
echo
echo "Undo any time with: sh scripts/undo.sh $1"
