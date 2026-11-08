#!/usr/bin/env bash
# Builds CleanHome.apk with the plain Android SDK tools (no Gradle):
#   aapt2 -> javac -> d8 -> zipalign -> apksigner
#
# Needs: a JDK (17 or newer) and the Android SDK with "platforms;android-34" and "build-tools;34.0.0".
# Optional environment:
#   ANDROID_HOME / ANDROID_SDK_ROOT   SDK location (default: ~/Library/Android/sdk or ~/Android/Sdk)
#   CLEANHOME_KEYSTORE                signing keystore (default: app/keystore.jks, created if missing)
#   CLEANHOME_KS_PASS                 keystore password (default: cleanhome)
#   VERSION_CODE / VERSION_NAME       override the version
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
for d in "$SDK" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -n "$d" ] && [ -d "$d/platforms" ] && { SDK="$d"; break; }; done
[ -d "$SDK/platforms" ] || { echo "Android SDK not found. Set ANDROID_HOME (see docs/BUILD.md)."; exit 1; }
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$SDK/platforms/android-34/android.jar"
[ -f "$AJ" ] || AJ="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar"
command -v javac >/dev/null || { [ -d /opt/homebrew/opt/openjdk@21 ] && export PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH; }
command -v javac >/dev/null || { echo "A JDK (javac) is needed. See docs/BUILD.md."; exit 1; }

VC=${VERSION_CODE:-$(date +%y%m%d%H)}
VN=${VERSION_NAME:-$(cat ../VERSION 2>/dev/null || echo 1.0.0)}
KS=${CLEANHOME_KEYSTORE:-keystore.jks}
PW=${CLEANHOME_KS_PASS:-cleanhome}

rm -rf build && mkdir -p build/gen build/classes
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets --java build/gen build/res.zip \
  --min-sdk-version 28 --target-sdk-version 34 --version-code "$VC" --version-name "$VN"
LAMBDA=""; [ -f "$BT/core-lambda-stubs.jar" ] && LAMBDA=":$BT/core-lambda-stubs.jar"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$AJ$LAMBDA" -cp "$AJ" -d build/classes \
  $(find src build/gen -name '*.java') 2>&1 | grep -v "bootstrap classpath" || true
[ -n "$(find build/classes -name HomeActivity.class)" ] || { echo "javac failed"; exit 1; }
(cd build/classes && jar cf ../classes.jar .)
"$BT/d8" --min-api 28 --lib "$AJ" --output build build/classes.jar
cp build/base.apk build/unsigned.apk && (cd build && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass "$PW" -keypass "$PW" -alias home \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CleanHome" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PW" --out CleanHome.apk build/aligned.apk
rm -f CleanHome.apk.idsig
echo "Built $(pwd)/CleanHome.apk ($VN, versionCode $VC)"
