#!/usr/bin/env bash
# Removes CleanHome and puts the TV back exactly as it was.   ./uninstall.sh 192.168.1.50
set -euo pipefail
cd "$(dirname "$0")"
IP="${1:-}"; [ -n "$IP" ] || { echo "Usage: ./uninstall.sh <TV IP address>"; exit 1; }
case "$IP" in *:*) ;; *) IP="$IP:5555" ;; esac
if command -v adb >/dev/null; then ADB=adb; elif [ -x platform-tools/adb ]; then ADB=./platform-tools/adb
else echo "adb not found - run install.sh once (it downloads adb), or install Android platform-tools."; exit 1; fi

$ADB connect "$IP" >/dev/null 2>&1 || true
for i in $(seq 1 30); do [ "$($ADB -s "$IP" get-state 2>/dev/null || true)" = device ] && break; sleep 2; done
$ADB -s "$IP" push tv /data/local/tmp/cleanhome >/dev/null 2>&1
$ADB -s "$IP" shell sh /data/local/tmp/cleanhome/remove.sh
$ADB -s "$IP" shell rm -rf /data/local/tmp/cleanhome
echo "Saving changes on the TV (40 seconds)..."; sleep 40
echo "CleanHome removed. Your TV is back to Google TV's own home."
