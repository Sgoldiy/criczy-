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
- [x] No ads, analytics, accounts or advertising ID

For you to do:

- [ ] **Decide on the data source.** The API is Cricket Australia's and is not licensed to third parties. Team logos, player photos and news images are theirs too. Publishing with it risks a takedown or account strike. Get permission, or switch provider (see ARCHITECTURE.md).
- [ ] Check the name "Criczy" is free on Play and not a trademark.
- [ ] Test the release build on real devices (see TESTING.md).
- [ ] Host the privacy policy at a public URL and enter it in Play Console.
- [ ] Data safety form: no data collected or shared; data is not encrypted in transit by you but all traffic is HTTPS.
- [ ] Content rating questionnaire, target audience, app category (Sports).
- [ ] Store listing: 512×512 icon (ready at `docs/store/icon-512.png`), 1024×500 feature graphic, phone and tablet screenshots (`docs/screenshots` is a start), short and full descriptions. Do not use team logos or player photos in the listing without rights.
- [ ] Add a contact email to the listing and to the privacy policy.
- [ ] New personal developer accounts must run a closed test with 12 testers for 14 days before production.
