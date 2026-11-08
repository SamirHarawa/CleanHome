#!/usr/bin/env bash
# CleanHome installer for macOS and Linux.
#   ./install.sh 192.168.1.50            install CleanHome on the TV at that address
#   ./install.sh 192.168.1.50 --debloat  ...and switch off tracking/bloat apps (undo with uninstall.sh)
set -euo pipefail
cd "$(dirname "$0")"
REPO=SamirHarawa/CleanHome

IP="${1:-}"; DEBLOAT=""
for a in "$@"; do [ "$a" = "--debloat" ] && DEBLOAT=debloat; done
if [ -z "$IP" ] || [ "$IP" = "--debloat" ]; then
  echo "Usage: ./install.sh <TV IP address> [--debloat]"
  echo "Find the address on the TV: Settings > Network & Internet > (your network)."
  exit 1
fi
case "$IP" in *:*) ;; *) IP="$IP:5555" ;; esac

step() { printf "\n\033[1m%s\033[0m\n" "$*"; }

# 1. adb (Google's Android Debug Bridge). Use the one installed, or fetch Google's official copy here.
step "1/5  Getting adb"
if command -v adb >/dev/null; then ADB=adb
elif [ -x platform-tools/adb ]; then ADB=./platform-tools/adb
else
  OS=$(uname -s | tr '[:upper:]' '[:lower:]'); [ "$OS" = darwin ] || OS=linux
  echo "  Downloading Android platform-tools from Google..."
  curl -fsSL -o platform-tools.zip "https://dl.google.com/android/repository/platform-tools-latest-$OS.zip"
  unzip -q -o platform-tools.zip && rm platform-tools.zip
  ADB=./platform-tools/adb
fi
echo "  Using $ADB"

# 2. The app itself: next to this script (release download), or the latest release from GitHub.
step "2/5  Getting CleanHome.apk"
APK=""
for f in CleanHome.apk ../CleanHome.apk ../app/CleanHome.apk; do [ -f "$f" ] && { APK="$f"; break; }; done
if [ -z "$APK" ]; then
  echo "  Downloading the latest release..."
  curl -fsSL -o CleanHome.apk "https://github.com/$REPO/releases/latest/download/CleanHome.apk"
  APK=CleanHome.apk
fi
echo "  Using $APK"

# 3. Connect. The first time, the TV asks "Allow network debugging?" - choose Always allow, then OK.
step "3/5  Connecting to the TV at $IP"
echo "  If the TV asks to allow debugging, tick 'Always allow from this computer' and press OK."
$ADB connect "$IP" >/dev/null 2>&1 || true
for i in $(seq 1 60); do
  state=$($ADB -s "$IP" get-state 2>/dev/null || true)
  [ "$state" = device ] && break
  [ $((i % 10)) = 0 ] && $ADB connect "$IP" >/dev/null 2>&1 || true
  sleep 2
done
[ "$state" = device ] || { echo "  Could not connect. Check the IP and that Network debugging is on (see README)."; exit 1; }
SH() { $ADB -s "$IP" shell "$@"; }
echo "  Connected: $(SH getprop ro.product.manufacturer | tr -d '\r') $(SH getprop ro.product.model | tr -d '\r'), Android $(SH getprop ro.build.version.release | tr -d '\r')"
SH pm path com.google.android.apps.tv.launcherx >/dev/null 2>&1 || echo "  Note: this TV doesn't have the Google TV home. CleanHome works best on Google TV."

# 4. Install.
step "4/5  Installing CleanHome"
$ADB -s "$IP" install -r "$APK" | tail -1

# 5. Set up on the TV (permission, Home button helper, default home, TCL start-up list, optional debloat).
step "5/5  Setting up"
SH rm -rf /data/local/tmp/cleanhome
$ADB -s "$IP" push tv /data/local/tmp/cleanhome >/dev/null 2>&1
SH sh /data/local/tmp/cleanhome/setup.sh $DEBLOAT

if [ -n "$DEBLOAT" ]; then
  echo "  Saving changes on the TV (40 seconds - please don't turn it off)..."
  sleep 40
fi
printf "\n\033[1mCleanHome is installed.\033[0m Press Home on the remote. To undo everything: ./uninstall.sh %s\n" "${IP%:5555}"
