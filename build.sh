#!/bin/sh
# Build out/tvhome.apk without Gradle: aapt2 + javac + d8 + zipalign + apksigner.
#
#   sh build.sh
#
# Needs: Java 17, Android SDK with platforms;android-34 and build-tools;34.0.0.
#   brew install openjdk@17 android-platform-tools
#   brew install --cask android-commandlinetools
#   sdkmanager --licenses
#   sdkmanager "platforms;android-34" "build-tools;34.0.0"
#
# The signing key is created on first build as key.jks with a random password
# stored in key.pass. Keep both: updates must be signed with the same key, or the
# TV refuses to install over the old version.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
PKG=dev.lucas.tvhome
PKG_DIR=$(echo "$PKG" | tr . /)
MIN_SDK=26
TARGET_SDK=34
PLATFORM=34
BUILD_TOOLS=34.0.0
VERSION_CODE=${VERSION_CODE:-4}
VERSION_NAME=${VERSION_NAME:-0.3.0}

# --- Java
if [ -z "${JAVA_HOME:-}" ]; then
  for c in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /usr/lib/jvm/java-17-openjdk-amd64; do
    [ -x "$c/bin/javac" ] && JAVA_HOME=$c && break
  done
fi
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] || { echo "Java 17 not found. brew install openjdk@17"; exit 1; }
# d8 and apksigner are shell wrappers that need java on the PATH.
export JAVA_HOME
PATH=$JAVA_HOME/bin:$PATH

# --- Android SDK
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ]; then
  for c in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -d "$c" ] && SDK=$c && break
  done
fi
AJAR=$SDK/platforms/android-$PLATFORM/android.jar
BT=$SDK/build-tools/$BUILD_TOOLS
if [ ! -f "$AJAR" ] || [ ! -x "$BT/aapt2" ]; then
  echo "Android SDK pieces missing under: ${SDK:-<not found>}"
  echo "Run:  sdkmanager --licenses && sdkmanager \"platforms;android-$PLATFORM\" \"build-tools;$BUILD_TOOLS\""
  exit 1
fi

OUT=$HERE/out
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/gen/$PKG_DIR" "$OUT/res"

# --- Apps.java from apps.txt
APPS=${APPS:-$HERE/apps.txt}
{
  echo "package $PKG;"
  echo "final class Apps {"
  echo "    static final String[] LIST = {"
  sed 's/#.*//' "$APPS" | tr -d ' \t\r' | grep -v '^$' | sed 's/.*/        "&",/'
  echo "    };"
  echo "    private Apps() { }"
  echo "}"
} > "$OUT/gen/$PKG_DIR/Apps.java"

# --- wallpapers -> assets/wallpapers (jpg, jpeg, png, webp in wallpapers/)
mkdir -p "$OUT/assets/wallpapers"
n=0
for f in "$HERE"/wallpapers/*; do
  [ -f "$f" ] || continue
  case "$(echo "$f" | tr 'A-Z' 'a-z')" in
    *.jpg|*.jpeg|*.png|*.webp) cp "$f" "$OUT/assets/wallpapers/"; n=$((n+1)) ;;
    *.heic|*.heif) echo "note: $(basename "$f") is HEIC; convert it first (see wallpapers/README.md)" ;;
  esac
done
echo "wallpapers built in: $n"

# --- resources + manifest
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" \
  --manifest "$HERE/AndroidManifest.xml" \
  -I "$AJAR" \
  -A "$OUT/assets" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$OUT/res.zip"

# --- Java -> dex
# Paths may contain spaces, so sources go through an argument file.
find "$HERE/src" "$OUT/gen" -name '*.java' | sed 's/.*/"&"/' > "$OUT/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$AJAR:$BT/core-lambda-stubs.jar" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' -print0 \
  | xargs -0 "$BT/d8" --release --min-api "$MIN_SDK" --lib "$AJAR" --output "$OUT"

# --- assemble, align, sign
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KEYSTORE=${KEYSTORE:-$HERE/key.jks}
PASSFILE=${PASSFILE:-$HERE/key.pass}
if [ ! -f "$KEYSTORE" ]; then
  if [ ! -f "$PASSFILE" ]; then
    LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24 > "$PASSFILE"
    chmod 600 "$PASSFILE"
  fi
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias tvhome \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$(cat "$PASSFILE")" -keypass "$(cat "$PASSFILE")" \
    -dname "CN=tvhome" >/dev/null 2>&1
  echo "Created signing key: $KEYSTORE (password in $PASSFILE). Back both up."
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias tvhome \
  --ks-pass "pass:$(cat "$PASSFILE")" --key-pass "pass:$(cat "$PASSFILE")" \
  --out "$OUT/tvhome.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/tvhome.apk"

ls -l "$OUT/tvhome.apk"
