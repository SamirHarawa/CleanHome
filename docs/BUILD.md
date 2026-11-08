# Building CleanHome from source

CleanHome has no Gradle and no libraries - just the Android SDK command-line tools.

## Requirements

- **JDK 17 or newer** (`javac` on your PATH). Mac: `brew install openjdk@21`.
- **Android SDK** with `platforms;android-34` and `build-tools;34.0.0`:

  ```sh
  sdkmanager "platforms;android-34" "build-tools;34.0.0"
  ```

  The script looks in `$ANDROID_HOME`, `~/Library/Android/sdk` (Mac) or `~/Android/Sdk` (Linux).

## Build

```sh
cd app
./build.sh
```

Output: `app/CleanHome.apk`. The first build creates a signing key at `app/keystore.jks` (ignored by git).
To sign with your own key: `CLEANHOME_KEYSTORE=/path/key.jks CLEANHOME_KS_PASS=secret ./build.sh`.

> Android only installs an update signed with the same key as the installed copy. If you switch keys, uninstall
> CleanHome first (`installer/uninstall.sh`).

## Install your build

```sh
cd installer
./install.sh 192.168.1.50        # picks up ../app/CleanHome.apk automatically
```

## Making a release

```sh
./scripts/make-release.sh        # builds the APK and dist/CleanHome-installer.zip
```

Attach `dist/CleanHome.apk` and `dist/CleanHome-installer.zip` to a GitHub release. The installer's download links
point at `releases/latest/download/...`.

## Code layout

```
app/
  AndroidManifest.xml
  src/com/samir/cleanhome/   HomeActivity (UI), HomeKeyService (accessibility), Weather, Frost, Glide, Motion, AutoMark ...
  res/drawable/              vector icons and brand logos (g_*.xml)
  assets/wallpapers/         built-in photos (Unsplash, credits in PHOTO-CREDIT.md)
  assets/fonts/              Manrope (OFL)
  tools/                     svg2vector.py and the SVGs the logos were made from
installer/
  install.sh / install.ps1   run on your computer
  tv/setup.sh, tv/remove.sh  run on the TV (pushed by the installer)
  tv/debloat-*.txt           what --debloat switches off
```

Notes: Java sources are compiled to Java 8 bytecode with lambdas (desugared by d8). Avoid anonymous inner classes in
`HomeActivity`; some d8 versions choke on them - use lambdas or named static classes.
