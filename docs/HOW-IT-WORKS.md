# How CleanHome works

CleanHome is an ordinary Android app (`com.samir.cleanhome`) that acts as a home screen. Google TV makes it hard for
a third-party home screen to take over, so the installer and the app use a few standard Android mechanisms.

## The pieces

| Piece | What it does |
|---|---|
| `HomeActivity` | The home screen itself: clock, weather, dock, sheets, wallpapers, search. |
| `HomeKeyService` | An **accessibility service** (the "Home button helper"). Google TV's own launcher always claims the Home button; whenever Google TV's home appears, this switches straight to CleanHome. It also lets the remote's Settings and YouTube buttons work everywhere, and starts the screensaver. |
| `Frost`, `Glide`, `Motion`, `GlideScroll` | Rendering: the frosted glass dock, the gliding focus highlight, the motion curves, and the Apps grid's tvOS-style scrolling (the focused row stays put while the grid glides under it). |
| `AutoMark` | Turns any app's icon or banner into a clean logo in its brand colour. |
| `Sounds` | CleanHome's own interface sounds (`assets/sounds`): a soft tick when focus moves, a click on OK, a muted bump at the end of a row. Arrow-key focus moves are handled by CleanHome itself so these replace the system beeps; the system "sound effects" switch still turns them off. |
| `BannerArt` | Apps grid cards: checks whether an app's banner is a real one, and builds a 16:9 banner (icon above the app's name) when it isn't. |
| `Weather` | Location and forecast, with the colour weather icons. |
| `LiveWall` | Live wallpapers: picks an unseen picture from Google's feeds every hour, on the hour, keeps the next one downloaded and ready. |

## What the installer changes on the TV

All done with `adb` from your computer; everything is recorded in `/sdcard/CleanHome/` on the TV and undone by the
uninstaller.

1. Installs `CleanHome.apk`.
2. Grants `WRITE_SECURE_SETTINGS` so CleanHome can switch its Home button helper back on if the TV turns it off,
   and `ACCESS_COARSE_LOCATION` so the weather uses the TV's own approximate location.
3. Turns on the Home button helper (accessibility service), keeping any others you had on.
4. Sets CleanHome as the default home app (`cmd role add-role-holder android.app.role.HOME`).
5. **TCL only:** TCL's firmware blocks third-party apps from starting at power-on unless they are on an allow-list
   (`content://com.tcl.providers.config/AppBootPolicy`). The installer adds CleanHome to three lists there
   (`autoStartWhiteList`, `assocStartWhiteList`, `bindServiceWhiteList`) after saving the original.
6. **Only with `--debloat`:** switches off (`pm disable-user`) the packages in `installer/tv/debloat-*.txt` that exist
   on the TV.

Google TV's own launcher is **not** disabled. It keeps running in the background because voice search, the quick settings
panel, the profile chooser and system updates depend on it. That is what keeps the TV stable.

## Clever bits

- **Screensaver on Back.** Android only lets system apps start the screensaver. CleanHome fades the screen to black with
  an accessibility overlay, opens Google's quick settings panel underneath it, presses its *Screensaver* button, and
  lifts the black once the screensaver is showing. You never see the panel.
- **Profile picture.** The account icon shows your Google profile picture, copied from Google's own quick settings panel
  the next time it's opened (no account access is used).
- **No banding, no grain.** Wallpapers and the frosted glass are computed in floating point and written with an
  invisible dither, so gradients stay smooth on big TVs.

## Network and privacy

CleanHome has no analytics, ads or accounts. It contacts only:

| Service | Why | What it sends |
|---|---|---|
| `clients3.google.com` (Chromecast backdrop list) and `*.googleusercontent.com` (the photos) | Google ambient wallpapers | Nothing personal |
| `www.gstatic.com` (artwork list) and `*.ggpht.com` (the artworks) | Google Arts & Culture wallpapers | Nothing personal |
| `www.bing.com` | Photo of the day | Nothing personal |
| `ipwho.is` (fallback `ipapi.co`) | Approximate location for the weather, once a day, only when the TV's own location isn't available | Your public IP address, as with any website |
| `api.open-meteo.com` | The forecast, every 30 minutes | The latitude/longitude rounded to about 1 km |

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE` (network icon), `ACCESS_COARSE_LOCATION` (the weather's location: the TV's own
approximate position, read from what the TV already knows, rounded to about 1 km, and turned into a town name by the TV's
own geocoder), `QUERY_ALL_PACKAGES` (to list your apps), `WRITE_SECURE_SETTINGS`
(granted by the installer, see above). The accessibility service can see window changes and remote keys; it uses them
only for the Home, Settings, YouTube and Back behaviour described above and doesn't store or send anything.
