# Play Store assets

Everything the Play Console asks for on a first submission. See `../RELEASE.md` for the build
and signing side.

```
deploy/
  listing/en-US.md                     app name, short + full description, console answers
  listing/id-ID.md                     the same, in Bahasa Indonesia
  graphics/icon-512.png                512×512 app icon
  graphics/feature-graphic-1024x500.png
  screenshots/phone/                   6 × 1080×2160
  screenshots/tablet-7/                6 × 1200×1920
  screenshots/tablet-10/               6 × 1600×2560
  capture.sh                           regenerates the screenshots from an emulator
```

## Screenshots

Play wants 2–8 per form factor; six are here, each showing a different part of the app: prayer
times, calculation settings, per-prayer sounds, the qibla compass, the Hijri calendar, and the
about/permissions screen. Upload them in that order — the first two are what most people see.

Phone shots are 1080×2160, exactly 2:1. The emulator's native 1080×2424 is 2.24:1, which is
past the ratio Play accepts for phone screenshots, so `capture.sh` resizes the display first.

To regenerate after a UI change:

```bash
./build.sh install && ./deploy/capture.sh all
```

It needs one running emulator and drives everything else itself — status bar frozen to a clean
10:30 / full battery / wifi-only, the tablet form factors produced by resizing that same
emulator with `wm size` and `wm density`. It restores the display on exit.

The screenshots were taken with the location set to Jakarta and a city name typed in; a fresh
emulator will show empty coordinates until you set them.

## Icon

`icon-512.png` is the adaptive icon flattened: the emerald mark from
`app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp` on `#0B1220`, the same background
the launcher uses. Play draws its own rounded corners, so the source is a full square with no
transparency and no pre-applied mask.

## Still to produce by hand

- **Privacy policy URL** — required for the listing, and required again in Data Safety. The
  honest version is short: the app collects nothing and has no internet permission.
- Anything you want beyond the six screenshots per size (a widget-on-home-screen shot is the
  obvious addition, and has to be taken manually — placing a widget can't be scripted).
