# adzan-time

Offline prayer times with a home-screen widget and per-prayer adzan notifications.
`applicationId` `id.larus.adzan`, minSdk 26, compileSdk 36, Kotlin + Compose + Material 3.
Read the repo-root `CLAUDE.md` first for the house style; this file is the app's specifics.

## Build

```
./build.sh              # debug  -> app/build/outputs/apk/debug/app-debug.apk
./build.sh test         # the calculator vs. published schedules
./build.sh install      # adb install to the connected device
./build.sh release      # signed if keystore.properties exists
./build.sh lint
```

Cold build: minutes. Incremental: ~10s. Run it, then wait for it — it is not stuck.
`RELEASE.md` documents the Play release flow; `deploy/` holds store copy and screenshots.

## The hard rule

**The app has no `INTERNET` permission, and that is a feature, not an oversight.** Prayer times
are computed on the device; every adzan recording is bundled in the APK. Do not add a network
dependency, an analytics SDK, Play Services, or anything that needs them. Location comes from
the platform `LocationManager` (`location/LocationFinder.kt`), never from a fused provider.

## Layout

| Path | What lives there |
|---|---|
| `prayer/PrayerCalculator.kt` | Solar maths. Pure JVM, unit-tested — the one place that must stay dependency-free. |
| `prayer/Schedule.kt` | A day into concrete instants; applies the Jumuah rules. |
| `prayer/HijriDate.kt`, `HijriHolidays.kt` | Umm al-Qura conversion and the holiday table. |
| `data/Settings.kt` | Every preference, one synchronous `SharedPreferences` store. |
| `data/AdzanSound.kt`, `CustomSound.kt` | Bundled sounds, and the user's picked/recorded one. |
| `data/WidgetSettings.kt` | Per-`appWidgetId` style, keyed by id. |
| `alarm/` | Chained exact-alarm scheduler, notifier, playback service, boot receiver. |
| `widget/` | `RemoteViews` providers — wide (`AdzanWidgetProvider`) and square (`AdzanSquareWidgetProvider`, a subclass). |
| `ui/AdzanApp.kt` | The whole Times tab plus the nav scaffold. The largest file; read it before editing it. |
| `ui/HijriCalendarScreen.kt`, `QiblaScreen.kt`, `AboutScreen.kt` | The other three tabs. |

## Things that will catch you

- **`Settings` is not reactive.** It is plain `SharedPreferences`, read synchronously by alarm
  receivers and the widget provider on their own threads. Compose does not observe it. The UI
  pattern is `revision` + `invalidate()` in `AdzanApp`: bump the counter, which re-`key`s the
  cards and reschedules alarms. Anything that must update live while a dialog is open has to be
  hoisted into real Compose state instead — `invalidate()` from inside a dialog tears that dialog
  down, because it re-keys the card hosting it.
- **Screen structure is deliberate.** Times tab is three cards — adzan (Today), calculation,
  widget. Per-prayer notification and sound live in each prayer row's dialog, Jumuah inside the
  Dhuhr dialog, location in a chip on the TODAY caption. Do not reintroduce per-setting cards.
- **Alarms are chained**: each fired alarm schedules the next, so `AdzanScheduler.rescheduleAll`
  must be called after any change that moves a time. `invalidate()` already does this.
- **Alarm-style notifications** need the Android 14 full-screen-intent grant and an unrestricted
  battery setting; both are checked in the UI and both dead-end silently if skipped.
- **Widget ids lie.** A launcher allocates an id for a drag or preview and abandons it, so
  `getAppWidgetIds()` can return more ids than there are widgets. `WidgetCard` filters to ids
  with a non-zero `OPTION_APPWIDGET_MIN_WIDTH`, falling back to the raw list.
- **Hijri dates** use `IslamicCalendar` with `ISLAMIC_UMALQURA`, always anchored on a real
  instant — `clear()` + `set()` drops the era and makes `add()` unreliable.
- **Audio** in `res/raw/` is re-encoded mono 56 kbps; keep new sounds at that budget or the APK
  grows fast.
