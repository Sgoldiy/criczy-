# Release

## Signing

Create an upload key once and keep it and its passwords outside the repository:

```bash
keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Create `keystore.properties` in the project root (it is git-ignored):

```
storeFile=upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

Without this file, release builds are signed with the debug key so they can be installed for testing. **Those cannot be uploaded to Google Play.**

## Build

```bash
./gradlew :app:bundleRelease
```

```bash
./gradlew :app:assembleRelease
```

Outputs: `app/build/outputs/bundle/release/app-release.aab` (upload this to Play) and `app/build/outputs/apk/release/app-release.apk`. Keep `app/build/outputs/mapping/release/mapping.txt` for each release to read crash reports.

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every upload.

## Configuration summary

| | |
|---|---|
| Application id | `com.criczy.app` (debug builds use `com.criczy.app.debug`) |
| Min / target / compile SDK | 24 / 36 / 36 |
| Shrinking | R8 with resource shrinking |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`; WorkManager adds `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` |
| Backup | Favourites and settings only |
| Cleartext traffic | Disabled |

## Google Play checklist

Done in the project:

- [x] Adaptive icon with monochrome layer, splash screen, notification icon
- [x] Target SDK 36, 64-bit safe (no native code)
- [x] Notification permission requested at runtime on Android 13+
- [x] In-app privacy policy; policy text in `docs/PRIVACY_POLICY.md`
- [x] No analytics; ads (AdMob) are set up as described in `docs/ADS.md`, whose release checklist covers AdMob, Realtime Database and Play Console

For you to do:

- [ ] **Decide on the data source.** The API is Cricket Australia's and is not licensed to third parties. Team logos, player photos and news images are theirs too. Publishing with it risks a takedown or account strike. Get permission, or switch provider (see ARCHITECTURE.md).
- [ ] Check the name "Criczy" is free on Play and not a trademark.
- [ ] Test the release build on real devices (see TESTING.md).
- [ ] Privacy policy URL: `https://sgoldiy.github.io/criczy-/privacy.html`, built by `docs/store/make_site.py` and published by `.github/workflows/pages.yml`. Once: Settings → Pages → Build and deployment → Source: **GitHub Actions**. It publishes when the support email is in `docs/PRIVACY_POLICY.md` and that change is on `main` (or run Actions → Website → Run workflow). Until then the workflow skips with a warning.
- [ ] Data safety form: scores need no data, but the fan community stores a username (Personal info: User IDs) and posts, polls and votes (App activity: user-generated content, app interactions) with Firebase, encrypted in transit and deleted after 24 hours; not shared. Also Crash logs and Diagnostics (Crashlytics), not shared. Account deletion: in the app (Community → ⋮) and at `https://sgoldiy.github.io/criczy-/delete-account.html` (enter this URL in the Data safety form).
- [ ] Content rating questionnaire, target audience, app category (Sports).
- [ ] Store listing: all text and images are ready in `docs/store/LISTING.md` and `docs/store/play/` (icon, feature graphic, six phone and four 7-inch and 10-inch tablet screenshots). Do not use team logos or player photos in the listing without rights.
- [ ] Add a contact email to the listing and to the privacy policy.
- [ ] New personal developer accounts must run a closed test with 12 testers for 14 days before production.
