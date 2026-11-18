# Handover: Criczy (Sgoldiy/criczy-, branch main)

For the next session: final touches before release. CLAUDE.md is the source of truth and is up to date; read it
first, then open only the files a task touches. Do not re-explore the codebase, re-probe the API or re-check library
versions unless a task needs it.

## State on main (18 Nov 2026)

- Android cricket app, Kotlin + Jetpack Compose + Material 3, app id `com.criczy.app`.
- Done: live scores, match centre (summary, scorecard, commentary with shot and over replays, analysis, squads,
  info), series, teams, players, news, search, favourites, match alerts, pinned live score (foreground service),
  fan community (Firebase Auth + Firestore) with in-app and web account deletion, three home screen widgets (Score,
  Live score, Match centre), AdMob ads configured from Firebase Realtime Database (`docs/ADS.md`), Crashlytics for
  release builds, status bar icons following the app theme, tablet layouts.
- Store assets ready: icon (`docs/store/make_icon.py`), 6 phone + 4×2 tablet screenshots and feature graphic
  (`docs/store/play/`), listing text (`docs/store/LISTING.md`), public site generator and Pages workflow
  (`docs/store/make_site.py`, `.github/workflows/pages.yml`).
- 188 test runs (175 passing, 13 gated skips), lint 0 errors, release APK and AAB build. CI runs on every push.

## Never done

- The app has never run on a real device. Real Firebase, real ads, consent form, widgets on a launcher,
  notifications and the R8 release build are untested outside Robolectric.

## Waiting on the owner (remind, do not block)

- Support email is set (mvsgondaliya@gmail.com); the site publishes once Pages is on.
- GitHub → Settings → Pages → Source: GitHub Actions.
- `keystore.properties` for release signing (`docs/RELEASE.md`).
- Firebase Console: Anonymous auth, Firestore rules (`firebase/firestore.rules`, republish: they now allow account
  deletion), TTL on `expireAt`, Realtime Database with `firebase/database.rules.json` and `docs/ads/ads_config.json`,
  enable Crashlytics, download `google-services.json` again.
- AdMob: app + 7 units, consent messages, gambling block, `admob.properties` with the app id.
- Play Console: foreground-service declaration, Data safety (community, ads, crash logs, deletion URL), Advertising
  ID, content rating (user-generated content), target audience 13+, closed test (12 testers, 14 days).
- Data licensing with Cricket Australia (biggest Play risk).

## How the owner works

- Do exactly the task given, nothing extra; no refactors or upgrades on the side.
- Iterate with targeted tests; before committing run the full suite, lint and a release build.
- For UI changes, look at the screenshot before saying it is done.
- Commit with a clear message, push to `main` (the owner has approved pushing to main) and to the session branch.
- Add one line to CLAUDE.md for anything a future session would need, in the same commit.
- Keep replies short: what changed, what was verified, what was not. When the app changes, give the release APK path.

## Cloud session setup

Follow CLAUDE.md "Environment setup in a fresh cloud session" (Android SDK install, `local.properties`). Maven may
answer HTTP 429 on a cold build: wait 45 seconds and rerun.
