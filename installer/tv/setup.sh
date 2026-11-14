# CleanHome setup - runs ON the TV (pushed and started by install.sh / install.ps1).
# Usage: sh setup.sh [debloat]
# Everything it changes is recorded in /sdcard/CleanHome/ so remove.sh can undo it.

PKG=com.samir.cleanhome
SVC=$PKG/$PKG.HomeKeyService
DIR=/sdcard/CleanHome
HERE=$(dirname "$0")
mkdir -p $DIR

say() { echo "  $*"; }

# 1. Permission CleanHome uses to keep its Home button helper switched on.
pm grant $PKG android.permission.WRITE_SECURE_SETTINGS 2>/dev/null && say "Permission granted"
# Approximate location for the weather: the TV's own position (Google's Wi-Fi positioning) is far closer
# than one guessed from the internet connection. Coarse only; it never leaves the TV more precise than ~1 km.
pm grant $PKG android.permission.ACCESS_COARSE_LOCATION 2>/dev/null && say "Location for the weather allowed"

# 2. Switch on the Home button helper (an accessibility service), keeping any others already on.
cur=$(settings get secure enabled_accessibility_services)
case "$cur" in
  *"$SVC"*) ;;
  ""|null) settings put secure enabled_accessibility_services "$SVC" ;;
  *) settings put secure enabled_accessibility_services "$cur:$SVC" ;;
esac
settings put secure accessibility_enabled 1
say "Home button helper on"

# 3. Make CleanHome the default home app.
cmd role add-role-holder android.app.role.HOME $PKG 0 >/dev/null 2>&1 || cmd role add-role-holder android.app.role.HOME $PKG >/dev/null 2>&1
say "CleanHome set as home app"

# 4. Some TVs (TCL) block apps from starting at power-on unless they are on an allow-list.
appops set $PKG AUTO_START allow >/dev/null 2>&1
URI=content://com.tcl.providers.config/AppBootPolicy
raw=$(content query --uri $URI 2>/dev/null)
case "$raw" in
  *config_content=*)
    json=${raw#*config_content=}
    [ -f $DIR/tcl-AppBootPolicy.backup.json ] || echo "$json" > $DIR/tcl-AppBootPolicy.backup.json
    case "$json" in
      *"\"$PKG\""*) say "TCL start-up allow-list: already set" ;;
      *)
        new=$(echo "$json" | sed \
          -e "s/\"autoStartWhiteList\":{\"packageName\":\[/&\"$PKG\",/" \
          -e "s/\"assocStartWhiteList\":{\"packageName\":\[/&\"$PKG\",/" \
          -e "s/\"bindServiceWhiteList\":{\"packageName\":\[/&\"$PKG\",/" \
          -e "s/\"$PKG\",\]/\"$PKG\"]/g")
        esc=$(echo "$new" | sed 's/:/\\:/g')
        content update --uri $URI --bind config_content:s:"$esc" && say "TCL start-up allow-list: CleanHome added"
        ;;
    esac
    ;;
esac

# 5. Optional: switch off tracking, feedback and promo apps (never uninstalled - remove.sh turns them back on).
if [ "$1" = "debloat" ]; then
  n=0
  for list in "$HERE/debloat-google.txt" "$HERE/debloat-tcl.txt"; do
    [ -f "$list" ] || continue
    while read -r p; do
      case "$p" in ""|\#*) continue ;; esac
      if pm list packages -e 2>/dev/null | grep -qx "package:$p"; then
        pm disable-user --user 0 "$p" >/dev/null 2>&1 && { echo "$p" >> $DIR/disabled-by-cleanhome.txt; n=$((n+1)); }
      fi
    done < "$list"
  done
  say "Switched off $n tracking/bloat apps"
fi

# 6. Go home.
am start -a android.intent.action.MAIN -c android.intent.category.HOME -n $PKG/.HomeActivity >/dev/null 2>&1
say "Done"
