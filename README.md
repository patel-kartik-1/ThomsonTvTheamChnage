# Thomson TV Theme Change

A devotional, ad-free home screen for an Android TV, made for a Thomson Path 7X Pro (Android TV 9, 1 GB RAM). It replaces Google's home screen, which used about 114 MB of memory; this one uses about 18 MB.

## What's on the screen

- **Jay Swaminarayan** at the top, and a **ring clock**: a saffron ring fills with the seconds around the date, beside the time.
- **Wi-Fi, Bluetooth, TV settings and Settings** buttons at the top right.
- Three cards:
  - **Sandhya Aarti**: counts down to the evening aarti ("in 25 min").
  - **TV Health**: free memory, free storage and whether the remote is connected. OK clears apps waiting in the background.
  - **Wi-Fi**: the connected network's name. OK opens Wi-Fi settings.
- **Apps row**: YouTube, JioHotstar, VLC, File Commander, Play Store and All Apps.
- **Recommended Videos**: videos stored on the TV, played in VLC.
- **Menu**: Home · Aarti (plays the Sandhya Aarti) · BAPS Kirtan · BAPS Channel · Inputs · Apps. The two BAPS buttons open the [BAPS Kirtan Channel](https://www.youtube.com/@BAPSKirtanChannel) and [BAPS](https://www.youtube.com/@BAPS) on YouTube.
- **Aarti reminder**: at Sandhya Aarti time a small popup asks "Play now?".
- The cards and apps stay on the left, so the wallpaper photo's subject on the right is never covered.

## On the TV

| Folder | What goes there |
| --- | --- |
| `BAPS/Aarti`, `BAPS/Kirtan`, `BAPS/Katha` | Videos. The file name is the title; a `.jpg` with the same name is the thumbnail. |
| `BAPS/Wallpapers` | Wallpaper photos. Pick one in Settings → Wallpaper, or choose "Change daily". |

Copy files there with File Commander from a USB stick, or with `adb push`.

Settings (the ⚙ button): Sandhya Aarti time (◀ ▶ changes it 5 minutes at a time), the reminder popup on or off, the wallpaper, Wi-Fi, Bluetooth & remote, and the TV's own settings.

## Build and install

You need Java 17 and the Android SDK (platform 34, build-tools 34.0.0). On a Mac:

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

Then:

```sh
cp /path/to/your-photo.jpg assets/darshan.jpg   # the built-in wallpaper, 16:9 (not in this repo)
sh build.sh                                     # makes out/home.apk and, the first time, key.jks
adb connect <TV_IP>:5555
adb install -r out/home.apk
adb shell pm grant com.kartik.tvhome android.permission.ACCESS_FINE_LOCATION      # Wi-Fi name
adb shell pm grant com.kartik.tvhome android.permission.READ_EXTERNAL_STORAGE     # videos, wallpapers
adb shell cmd package set-home-activity com.kartik.tvhome/.MainActivity
```

Keep `key.jks` safe and private: every update must be signed with the same key. It is deliberately left out of this repository.

On TVs where Google's own home screen keeps taking over Home, it has to be turned off first (`adb shell pm disable-user --user 0 com.google.android.tvlauncher`). That can be undone with `pm enable`.

To try the aarti popup without waiting:

```sh
adb shell am start -n com.kartik.tvhome/.MainActivity --ez test_popup true
```

## Limits

- On Android TV 9 the remote's **All Apps** button goes only to Google's home screen. Use the All Apps tile or the Apps menu instead.

## Credits

Built on [TV Detox](https://github.com/Ansub/tv-detox) by Ansub Khan (MIT, see `LICENSE-tv-detox.txt`): the build script, the line icons and parts of the app launching and input code.
