# Releasing to Google Play

Application ID: `id.larus.adzan` (set in `app/build.gradle.kts`). The Kotlin namespace stays
`com.rusydiana.adzantime` — Play only keys off the application ID, and renaming the source
package would churn every file for no benefit.

## 1. Signing

The upload key is `andro-key0.jks` in this directory (gitignored — keep a backup somewhere
safe; losing it means you can never update the listing without Play's key-reset process).

```bash
cp keystore.properties.example keystore.properties
```

Fill in `storePassword`, `keyAlias` and `keyPassword` yourself — I don't handle those. To see
which aliases the keystore holds:

```bash
keytool -list -v -keystore andro-key0.jks
```

## 2. Build the bundle

Play takes an AAB, not an APK:

```bash
./build.sh bundle
```

Output lands in `app/build/outputs/bundle/release/app-release.aab`.

Sanity-check the signed release on a real device before uploading:

```bash
./build.sh release && adb install -r app/build/outputs/apk/release/app-release.apk
```

## 3. Bumping versions

Every upload needs a higher `versionCode` than the last. Both live in `app/build.gradle.kts`
`defaultConfig`: bump `versionCode` by 1, and set `versionName` to whatever users should see
(e.g. `1.0.1`).

## 4. Play Console declarations this app will be asked for

The manifest requests several permissions that trigger a review form:

- **`USE_EXACT_ALARM`** — the alarm/clock exemption. Justification: the app's core function is
  sounding the adzan at astronomically computed prayer times, which are exact by definition.
  Without it the calls drift and the app is useless.
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`** — plays the adzan audio when the time arrives; you
  must describe this use in the foreground-service declaration.
- **`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`** — location determines the prayer times
  and the qibla bearing. It never leaves the device (the app holds no `INTERNET` permission,
  which is worth stating in the form).
- **`RECORD_AUDIO`** — only for recording a custom adzan sound, on user request.

**Data safety**: no data collected, no data shared, nothing transmitted — there is no network
permission at all. A privacy policy URL is still required for the listing.

## 5. Listing assets you still need

- App icon 512×512 PNG
- Feature graphic 1024×500
- At least 2 phone screenshots (4–8 is better); the widget is worth showing
- Short description (≤80 chars) and full description, in English and Indonesian — the app
  already ships `values-in` strings, so the listing should match
- Content rating questionnaire, target audience, and the privacy policy URL
