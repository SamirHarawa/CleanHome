# Installing CleanHome - step by step

This takes about 5 minutes. You need:

- a **Google TV** (any brand - TCL, Sony, Hisense, Philips, Chromecast with Google TV...),
- a **computer** (Mac, Windows or Linux) connected to the **same network** as the TV (Wi-Fi or cable, same router).

You don't need to install anything else - the installer fetches Google's official `adb` tool by itself.

---

## 1. Turn on network debugging on the TV

This lets your computer install an app on the TV over your home network. It's a standard Android feature.

1. On the TV open **Settings > System > About**.
2. Scroll to **Android TV OS build** and press **OK on it 7 times**. A message says *"You are now a developer!"*.
3. Go back to **Settings > System**. There is now a **Developer options** entry. Open it.
4. Turn on **USB debugging**. (Despite the name, this also allows debugging over the network on Google TV.)

> **Only "Wireless debugging" and no network option?** Some newer TVs use *Wireless debugging* instead.
> Turn it on, choose **Pair device with pairing code**, then on the computer run `adb pair <address shown>`
> and type the code (Mac/Linux: `./platform-tools/adb pair ...` after running the installer once).
> Then use the **IP address and port** shown under *Wireless debugging* (e.g. `192.168.1.50:37123`) in step 4.

## 2. Find the TV's IP address

**Settings > Network & Internet**, then select your network. Note the **IP address**, for example `192.168.1.50`.

## 3. Download the installer

Download **[CleanHome-installer.zip](https://github.com/SamirHarawa/CleanHome/releases/latest/download/CleanHome-installer.zip)**
and unzip it. You'll get a folder with `install.sh`, `install.ps1`, `uninstall.sh`, `uninstall.ps1`, `CleanHome.apk`
and a `tv` folder.

## 4. Run the installer

Replace `192.168.1.50` with your TV's address.

### Mac or Linux

1. Open **Terminal** (Mac: press Cmd+Space, type *Terminal*, press Return).
2. Type `cd ` (with a space), drag the unzipped folder into the Terminal window, press Return.
3. Run:

   ```sh
   ./install.sh 192.168.1.50
   ```

### Windows

1. Open the unzipped folder, **right-click** on an empty area and choose **Open in Terminal**
   (on older Windows: hold Shift + right-click > *Open PowerShell window here*).
2. Run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\install.ps1 192.168.1.50
   ```

## 5. Allow the computer on the TV

The first time, the TV shows **"Allow network debugging?"** (or *"Allow USB debugging?"*).
Tick **Always allow from this computer** and select **OK**. The installer is waiting for this and continues by itself.

When it says **CleanHome is installed**, press **Home** on the remote. Done!

---

## Optional: clean-up (`--debloat`)

Add `--debloat` (Mac/Linux) or `-Debloat` (Windows):

```sh
./install.sh 192.168.1.50 --debloat
```

This **switches off** (it never uninstalls):

- on every Google TV: Google's feedback/telemetry apps and the service that auto-installs promoted apps;
- on TCL TVs: TCL's tracking, ads and bloat apps (the full list is in [`installer/tv/debloat-tcl.txt`](../installer/tv/debloat-tcl.txt)).

Voice, Chromecast, AirPlay, the Play Store, inputs/Live TV and system updates are not touched. Everything that was
switched off is recorded on the TV and turned back on by the uninstaller. After a clean-up the installer waits 40
seconds so the TV saves the change - don't switch the TV off during that time.

## Removing CleanHome

From the same folder:

- Mac/Linux: `./uninstall.sh 192.168.1.50`
- Windows: `powershell -ExecutionPolicy Bypass -File .\uninstall.ps1 192.168.1.50`

The TV goes back to exactly how it was: normal Google TV home, any switched-off apps back on, TCL settings restored.

## Updating CleanHome

Download the newest installer zip and run the installer again. Your dock, wallpaper choice and other settings are kept.

## When you're done

You can turn **USB debugging** off again in Developer options. CleanHome keeps working; you only need it on to
install, update or remove CleanHome.

---

## Troubleshooting

**"Could not connect"**
- Check the IP address again (it can change after the router restarts).
- Make sure *USB debugging* is on and the computer is on the same network as the TV.
- Look at the TV - the *Allow debugging?* question may be waiting for you.
- Try again with the port: `./install.sh 192.168.1.50:5555`. If your TV uses *Wireless debugging*, see the note in step 1.

**"Permission denied" when running `./install.sh` on Mac/Linux**
Run `chmod +x install.sh uninstall.sh` once, then try again.

**Windows says running scripts is disabled**
Use the full command shown above (`powershell -ExecutionPolicy Bypass -File ...`). It only applies to that one run.

**The Google TV home appears for a moment when I press Home**
That's normal right after a TV restart, for a second or two, while CleanHome's Home button helper starts.
If it keeps happening, run the installer again - it re-enables the helper.

**After a restart the TV shows Google TV home instead of CleanHome**
Run the installer again. On TCL TVs it adds CleanHome to TCL's start-up allow-list; if that was reset by a firmware
update it needs adding again.

**The weather shows the wrong town**
The location comes from your internet connection. If your provider routes you through another city, the weather will
be for that city. (A setting for this may come in a later version - open an issue if you need it.)

**Something else?** [Open an issue](https://github.com/SamirHarawa/CleanHome/issues) with your TV's brand and model.
