#!/usr/bin/env bash
# Builds the APK and the installer zip for a GitHub release:
#   dist/CleanHome.apk  and  dist/CleanHome-installer.zip
# Sign with the release key:  CLEANHOME_KEYSTORE=/path/release.jks CLEANHOME_KS_PASS=... ./scripts/make-release.sh
set -euo pipefail
cd "$(dirname "$0")/.."
app/build.sh
rm -rf dist && mkdir -p dist/CleanHome
cp app/CleanHome.apk dist/
cp -R installer/install.sh installer/install.ps1 installer/uninstall.sh installer/uninstall.ps1 installer/tv app/CleanHome.apk dist/CleanHome/
cat > dist/CleanHome/HOW-TO-INSTALL.txt <<'TXT'
CleanHome - a clean home screen for Google TV
=============================================

1. On the TV: Settings > System > About > press OK 7 times on "Android TV OS build".
   Then Settings > System > Developer options > turn on "USB debugging".
2. Find the TV's IP address: Settings > Network & Internet > your network.
3. Run the installer from this folder (replace the address with your TV's):
     Mac / Linux:  ./install.sh 192.168.1.50
     Windows:      powershell -ExecutionPolicy Bypass -File .\install.ps1 192.168.1.50
   Add --debloat (Mac/Linux) or -Debloat (Windows) to also switch off tracking/bloat apps.
4. On the TV, allow debugging from this computer (tick "Always allow", then OK).

To remove:  ./uninstall.sh 192.168.1.50   (Windows: .\uninstall.ps1 192.168.1.50)
Full guide: https://github.com/SamirHarawa/CleanHome/blob/main/docs/INSTALL.md
TXT
chmod +x dist/CleanHome/*.sh
(cd dist && zip -qr CleanHome-installer.zip CleanHome)
echo "Release files:"; ls -la dist/CleanHome.apk dist/CleanHome-installer.zip
