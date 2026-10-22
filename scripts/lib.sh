# Shared helpers for the adb scripts. Sourced, not run.
#
#   TV=$(tv_target "$1")   -> "192.168.1.50:5555" or an adb serial
#   tv ...                 -> adb -s "$TV" ...
#   tv_shell ...           -> adb -s "$TV" shell ...
#   confirm "question"     -> exit status 0 on y/yes, or when YES=1

PKG=dev.lucas.tvhome
ACTIVITY=$PKG/.MainActivity
UNDO_FILE=/data/local/tmp/tvhome-disabled.txt
STOCK_LAUNCHER=com.google.android.tvlauncher

command -v adb >/dev/null 2>&1 || { echo "adb not found. brew install android-platform-tools"; exit 1; }

tv_target() {
  case "$1" in
    "") echo "usage: $0 <TV_IP | host:port | adb serial> ..." >&2; exit 1 ;;
    *:*|emulator-*) echo "$1" ;;
    *) echo "$1:5555" ;;
  esac
}

# </dev/null: adb would otherwise swallow the stdin of a "while read" loop.
tv() { adb -s "$TV" "$@" </dev/null; }
tv_shell() { adb -s "$TV" shell "$@" </dev/null; }

tv_connect() {
  case "$TV" in
    *:*) adb connect "$TV" >/dev/null 2>&1 || true ;;
  esac
  state=$(adb -s "$TV" get-state 2>&1 || true)
  case "$state" in
    device) ;;
    *unauthorized*)
      echo "The TV is asking whether to allow this computer. Accept the prompt on the TV, then run again."; exit 1 ;;
    *)
      echo "Can't reach $TV ($state)."
      echo "Check: USB debugging on, same network, TV awake. A full power-off at the wall sometimes helps."
      exit 1 ;;
  esac
}

tv_info() {
  echo "TV:       $(tv_shell getprop ro.product.model | tr -d '\r') ($(tv_shell getprop ro.product.device | tr -d '\r'))"
  echo "Android:  $(tv_shell getprop ro.build.version.release | tr -d '\r') (SDK $(tv_shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "Build:    $(tv_shell getprop ro.build.display.id | tr -d '\r')"
}

sdk_level() { tv_shell getprop ro.build.version.sdk | tr -d '\r'; }

installed() { tv_shell pm list packages "$1" | tr -d '\r' | grep -qx "package:$1"; }
disabled()  { tv_shell pm list packages -d "$1" | tr -d '\r' | grep -qx "package:$1"; }
persistent() { tv_shell dumpsys package "$1" | tr -d '\r' | grep -q 'PERSISTENT'; }

home_handler() {
  tv_shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
    | tr -d '\r' | tail -1
}

confirm() {
  [ "${YES:-}" = "1" ] && return 0
  # No keyboard (run from a script or an agent): treat as "no", quietly.
  { : </dev/tty; } 2>/dev/null || { echo "$1 [y/N] no (no keyboard; set YES=1 to agree)"; return 1; }
  printf '%s [y/N] ' "$1"
  read -r a </dev/tty || return 1
  case "$a" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

record_disabled() {
  tv_shell "grep -qx '$1' $UNDO_FILE 2>/dev/null || echo '$1' >> $UNDO_FILE"
}

disable_pkg() {
  if persistent "$1"; then
    echo "  skip $1: marked PERSISTENT, it would not stay off"
    return 1
  fi
  record_disabled "$1"
  out=$(tv_shell pm disable-user --user 0 "$1" | tr -d '\r')
  if disabled "$1"; then echo "  off  $1"; return 0; fi
  echo "  FAILED $1: $out"; return 1
}

enable_pkg() {
  out=$(tv_shell pm enable "$1" | tr -d '\r')
  if disabled "$1"; then echo "  FAILED $1: $out"; return 1; fi
  echo "  on   $1"
}
