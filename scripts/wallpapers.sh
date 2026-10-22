#!/bin/sh
# Push photos to the TV as wallpapers, no rebuild needed.
#
#   sh scripts/wallpapers.sh <TV_IP> add photo.heic other.jpg ...   # convert, resize, push
#   sh scripts/wallpapers.sh <TV_IP> list
#   sh scripts/wallpapers.sh <TV_IP> remove <name>                  # name as shown by list
#   sh scripts/wallpapers.sh <TV_IP> clear
#
# Photos land in the launcher's own folder on the TV:
#   /sdcard/Android/data/dev.lucas.tvhome/files/wallpapers/
# HEIC and anything else sips can read is converted to JPEG at 1920 px wide.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib.sh"

TV=$(tv_target "${1:-}")
CMD=${2:-list}
DIR=/sdcard/Android/data/$PKG/files/wallpapers

tv_connect
tv_shell mkdir -p "$DIR"

restart_home() {
  tv_shell am force-stop "$PKG" >/dev/null 2>&1 || true
  tv_shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
}

case "$CMD" in
  list)
    echo "Wallpapers on the TV:"
    tv_shell ls -la "$DIR" | tr -d '\r' | grep -v -E '^total|/$| \.$| \.\.$' | awk '{print "  " $NF "  (" $5 " bytes)"}' || echo "  (none)"
    ;;
  add)
    shift 2
    [ $# -gt 0 ] || { echo "add: give one or more photo files"; exit 1; }
    TMP=$(mktemp -d)
    for f in "$@"; do
      [ -f "$f" ] || { echo "skip: $f not found"; continue; }
      base=$(basename "$f")
      name=$(echo "${base%.*}" | tr ' ' '_' | tr -cd 'A-Za-z0-9_.-')
      out="$TMP/$name.jpg"
      if command -v sips >/dev/null 2>&1; then
        sips -s format jpeg -s formatOptions 88 -Z 1920 "$f" --out "$out" >/dev/null 2>&1 || { echo "skip: could not convert $f"; continue; }
      else
        cp "$f" "$out"
      fi
      tv push "$out" "$DIR/$name.jpg" >/dev/null
      echo "  pushed $name.jpg ($(du -k "$out" | cut -f1) KB)"
    done
    rm -rf "$TMP"
    restart_home
    echo "Home screen restarted. The picture glyph top right lists them."
    ;;
  remove)
    name=${3:-}
    [ -n "$name" ] || { echo "remove: give the file name shown by list"; exit 1; }
    tv_shell rm -f "$DIR/$name" "$DIR/$name.jpg"
    restart_home
    echo "  removed $name"
    ;;
  clear)
    if confirm "Remove every pushed wallpaper from the TV?"; then
      tv_shell rm -f "$DIR"/*
      restart_home
      echo "  cleared"
    fi
    ;;
  *)
    echo "unknown command: $CMD (add | list | remove | clear)"; exit 1 ;;
esac
