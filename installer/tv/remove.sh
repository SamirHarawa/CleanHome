# CleanHome removal - runs ON the TV (pushed and started by uninstall.sh / uninstall.ps1).
# Puts back everything setup.sh changed, then uninstalls CleanHome.

PKG=com.samir.cleanhome
SVC=$PKG/$PKG.HomeKeyService
DIR=/sdcard/CleanHome
say() { echo "  $*"; }

# 1. Switch off the Home button helper (leaving any other accessibility services as they were).
cur=$(settings get secure enabled_accessibility_services)
new=$(echo "$cur" | tr ':' '\n' | grep -v "^$SVC\$" | grep -v '^$' | tr '\n' ':' | sed 's/:$//')
if [ -z "$new" ] || [ "$new" = "null" ]; then
  settings delete secure enabled_accessibility_services >/dev/null
  settings put secure accessibility_enabled 0
else
  settings put secure enabled_accessibility_services "$new"
fi
say "Home button helper off"

# 2. TCL start-up allow-list back to how it was.
if [ -f $DIR/tcl-AppBootPolicy.backup.json ]; then
  esc=$(sed 's/:/\\:/g' $DIR/tcl-AppBootPolicy.backup.json)
  content update --uri content://com.tcl.providers.config/AppBootPolicy --bind config_content:s:"$esc" && say "TCL start-up allow-list restored"
  rm -f $DIR/tcl-AppBootPolicy.backup.json
fi

# 3. Turn back on any apps the installer switched off.
if [ -f $DIR/disabled-by-cleanhome.txt ]; then
  n=0
  while read -r p; do [ -n "$p" ] && pm enable "$p" >/dev/null 2>&1 && n=$((n+1)); done < $DIR/disabled-by-cleanhome.txt
  rm -f $DIR/disabled-by-cleanhome.txt
  say "Turned $n apps back on"
fi

# 4. Uninstall CleanHome; Google TV's own home comes back.
pm uninstall $PKG >/dev/null 2>&1 && say "CleanHome uninstalled"
rmdir $DIR 2>/dev/null
input keyevent KEYCODE_HOME
say "Done - Google TV home restored"
