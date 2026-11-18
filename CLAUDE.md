# Criczy: working notes for Claude

Read this before exploring. It records what is already known so it does not need to be rediscovered. Deeper detail is in `docs/`; open those only when the task needs them.

## What this is

Android cricket app (Kotlin, Jetpack Compose, Material 3) that replaces the owner's older app, Cricpeak (`Sgoldiy/CRICSCORE`, package `com.cricpeak.scorelive`). Do not modify that repository. Its audit is in `docs/CRICPEAK_AUDIT.md`; there is no need to read its code again.

Status: feature-complete for v1.0.0 plus the fan community, match-day features, team picker, pinned live score, batting control and three home screen widgets; 169 test runs, 160 passing and 9 skipped (the gated store renders and live API checks; core:data tests run for debug and release), lint clean, release APK and AAB build. Never run on a real device or emulator.

## Decisions already made by the owner (do not re-ask)

- Data source stays the cricket.com.au API, kept behind `CricketRemoteDataSource` so it can be swapped.
- AI (Gemini) and ads (AdMob, rewarded locks) are deferred. Do not add them unless asked.
- Firebase is used only for the fan community (anonymous Auth + Firestore); no Analytics, Messaging or Crashlytics. Design and console setup: `docs/FIREBASE.md`.
- No premium purchases.
- App name is Criczy, application id `com.criczy.app` (renamed from `app.criczy` on 18 Nov 2026, before any Play upload; packages and source folders match it). Never change it again once published.

## Layout

```
app/            Compose UI. feature/<name>/ holds ViewModel + Route + Screen in one file.
                navigation/, notifications/, widget/, ui/components, ui/theme, di/AppContainer.kt
core/data       Room (favourites, seen players, alert state), DataStore, repositories,
                Loadable + pollingFlow, RefreshPolicy, AlertPlanner
core/network    CricketAuApi (Retrofit), dto/Dtos.kt, mapper/Mappers.kt, CricketHttp (offline cache)
core/analytics  InningsAnalytics, WagonWheel, PitchMap, Leaderboards (pure functions)
core/model      domain types
```

Dependency direction: `app → core:data → core:network, core:analytics → core:model`. Manual DI in `AppContainer`; no Hilt.

## Commands

```bash
./gradlew test                     # all 169 test runs, about 5 minutes
./gradlew :app:testDebugUnitTest   # UI tests only; screenshots go to app/build/screenshots
./gradlew :app:lintDebug
./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease
./gradlew :core:network:test -PliveApi=true --tests '*LiveApiContractTest*'   # hits the real API
```

To see a screen, run the UI tests and open the PNG with the Read tool. That is the only way to view the UI here: there is no emulator (no KVM).

## Environment setup in a fresh cloud session

The container is temporary, so the SDK must be reinstalled each session.

1. Network: the environment must allow `dl.google.com` (Android SDK and all AndroidX/AGP artifacts), `gcp.mirrors.maven.org` (large Maven Central files, needed by Robolectric) and `apiv2.cricket.com.au` (only for live API tests). The "Cricket app" environment already has these.
2. Install the SDK (about 3 minutes):

```bash
export ANDROID_HOME=/home/user/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp
curl -sSo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q clt.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "build-tools;35.0.0" "platform-tools"
```

3. In the repository: `echo "sdk.dir=/home/user/android-sdk" > local.properties` (git-ignored).

## Gotchas that cost time before

- Maven Central sometimes answers HTTP 429 during a cold build. Wait 45 seconds and rerun; do not change repositories. `org.gradle.parallel` is off for this reason.
- Versions are pinned to a set verified here (AGP 8.11.1, Kotlin 2.2.0, KSP 2.2.0-2.0.2, Compose BOM 2025.07.00, Gradle 8.14.3). Much newer ones exist (AGP 9, Kotlin 2.4). Do not upgrade as a side effect of another task.
- Robolectric 4.15.1 supports up to SDK 35, so tests run with `sdk=35` (`app/src/test/resources/robolectric.properties`) while the app compiles against 36.
- Unit tests are disabled for the release variant on purpose (they need the debug-only test activity).
- `java.time` works on API 24–25 only because core library desugaring is enabled in `app` and `core:data`. Keep it.
- `core:data` exposes Room and DataStore with `api(...)`; `app` needs their types.
- Roborazzi only writes screenshots because `roborazzi.test.record=true` is set in `gradle.properties`.
- In UI tests, tabs and filter chips in scrolling rows need `performScrollTo()` before `performClick()`, and items below the fold of a lazy list need `performScrollToNode`.
- The search test (`search finds teams…`) can time out waiting for recent form when the whole suite runs under load; rerun it alone before suspecting a regression.
- A tap that is sometimes lost in a UI test (seen on search results) is fixed by `performSemanticsAction(SemanticsActions.OnClick)` instead of `performClick()`.
- Rows with `semantics(mergeDescendants = true) { contentDescription = … }` are found by content description, not by their inner text.
- Commentary feed: the mobile feed has no ball ids and a ball re-bowled after a wide repeats its ball number, so list keys must not use either. A fixture can appear in two buckets at once; the mapper keeps the first.
- Live events come from the newest ball in the commentary feed (`detectBallEvent`), falling back to score changes for matches without commentary. In tests with the paused clock (`mainClock.autoAdvance = false`), a state change needs `waitForIdle()` plus `advanceTimeByFrame()` before `advanceTimeBy`.
- Every top bar title goes through `AppTitle` (white then accent, CRIC·ZY style; titles render in capitals, so tests must match capitals). The phone bottom bar is the custom `FloatingBottomBar` in `CriczyNavHost`; tablets keep the `NavigationRail`.
- Commentary: each ball ends with a "See shot · FOUR" link; tapping it opens one ball at a time with `BallGround` replaying it (zoomed on the pitch from the bowler's hand, bounce from `bounceFraction(length)`, line from `lineOffset(line)`, then the camera pulls back for the shot). Drawn from behind the bowler: batter at the top, a right-hander's off side on the left; `shotEnd` turns the provider's behind-the-batter frame 180°. `ballStory` gives the label; dot balls never draw an arrow.
- Over rows ("END OF OVER n", or "OVER n · IN PROGRESS" live) show the over's balls as chips and open `OverReplayPanel` in the same one-at-a-time accordion as balls: each ball plays in `BallGround` for `replayMillis(ball)` + 650 ms, then `OverGround` shows the over's shots numbered in bowling order. The owner removed the over pitch map; do not add it back.
- Commentary feed (`CommentaryFeed`): filters All/Wickets/Boundaries (`CommentaryFilter`); `commentaryLines` splits "OUT! Bowler to Batter. …" into a heading and description using the scorecard names (all fixture messages match). A LazyColumn keeps its place by key, so a ball added above the first visible item stays off-screen: the feed checks "was at top" with `Snapshot.withoutReadObservation` when the newest ball changes and scrolls up, otherwise shows the "New ball" button. Earlier overs load on reaching the end, once per page (a failed page is not retried automatically).
- Player photos go through `PlayerPhoto` / `Badge(photo = true)`: headshots are cut-outs with the head high in the frame, so the loaded image is cropped from the top and zoomed by `PHOTO_ZOOM` (initials are never zoomed). The headshot host `static-files.cricket-australia.pulselive.com` is not allowed from the sandbox (the owner declined adding it), so photo framing can only be checked on a device.
- Match summary: key moments and recent overs call `openInCommentary(key)` in `MatchScreen`, which sets `commentaryFocus`; `CommentaryFeed` opens that row and scrolls to `feedIndexOf(key)`. "More from" the series comes from `MatchViewModel.seriesMatches` (`moreFromSeries`: live, then next, then recent) and opens matches through `MatchActions.onMatch`. Squads are a role-grouped grid (`squadGroups`) with a team switch.
- Main screens share the home board through `MatchRepository.latestBoard()` (loads once if home has not) and the pure helpers in `ui/BoardInsights.kt` (`seriesInPlay`, `seriesActivity`, `followedMatches`, `favouriteStatus`, `suggestedTeams`). News stories carry team, series and match ids, which drive the News Following filter. The test data's series list and board share no series, so series-card activity is tested by rendering `SeriesListScreen` directly.
- Top-level screens draw behind the floating bottom bar: the NavHost provides its height as `LocalBottomBarInset`, and each top-level list adds `bottomBarPadding()` / `listPaddingAboveBar()` at the end. A new top-level screen must do the same or its last item hides under the bar.
- One-line names that may not fit (top bar titles, match card team and series names) use `Modifier.scrollingText()` (a marquee) instead of an ellipsis.
- Images use `AppContainer.imageHttpClient` (no JSON headers, 16 downloads per host) and Coil's own memory and disk caches, not the API client; `Badge` uses `AsyncImage` with an `onState` flag, not `SubcomposeAsyncImage`.
- Player recent form (`PlayerRepository.recentForm`, `playerMatchForm`) reads the scorecards of the last finished matches of the team the player was last seen with; unknown team means no form section.
- Fan community (`feature/community`, `core/data/Community.kt`, `community/FirestoreCommunityBackend.kt`): username only, one feed per followed team (max 5, enforced in `FavouritesRepository.toggle`) plus All fans, opinions and polls, one post per 24 h, everything expires after 24 h. Match polls (`MatchPolls`) and match posts are described in `docs/FIREBASE.md`. Security rules live in `firebase/firestore.rules` and must be pasted into the console after any change. Tests use `UnavailableCommunityBackend` (UI) or `FakeCommunityBackend` (data); Robolectric never touches Firebase.
- The Google Services plugin is applied only when `app/google-services.json` exists (CI builds without it), and is skipped for debug while the file has no `com.criczy.app.debug` client. A worktree needs the file copied in by hand.
- Widgets (`widget/`): Score (3×1), Live score (4×2) and Match centre (4×3) share one `WidgetData` snapshot: the pinned match first, then `pickWidgetMatch`. They update from `MatchAlertWorker` (15 min), the refresh button (`RefreshWidgetsAction`) and every 30 s while a match is pinned (`Widgets.onPinnedDetail`). Widgets tint white shapes with SRC_ATOP, so a translucent fill turns white: blend it with `over()`, never `copy(alpha)`. Each widget lays out by `LocalSize` (`SizeMode.Responsive`); `WidgetsTest` renders them through `GlanceRemoteViews` to `app/build/screenshots/widgets`, and the picker previews (`drawable-nodpi/widget_preview_*.webp`) are its `preview-*.png` (invented teams) saved as WebP.
- Onboarding ends with `TeamPickerRoute` (up to 5 teams, saved as favourites). Pinning a live match (`LiveScoreService`, pin icon on the match screen) is a `specialUse` foreground service: the Play Console needs a foreground-service declaration for it before release.
- A UI test that waits on a view model's Room write must pump the main looper inside `waitUntil` (`Thread.sleep(20); shadowOf(mainLooper).idle()`); a plain `waitUntil { done }` hangs.
- CI: `.github/workflows/checks.yml` runs tests, lint and a release build on every push and keeps the APK as the `criczy-release-apk` artifact.
- Pushing needs the Claude GitHub App to have write access to `Sgoldiy/criczy-`. A 403 on push is a permission problem for the owner to fix, not something to retry.

## API facts (full detail in `docs/API.md`)

Base `https://apiv2.cricket.com.au/`, no key. Working endpoints: `mobile/views/fixtures` (three counts required, max 100; optional `CompetitionId`, `TeamId`), `mobile/views/scorecard`, `mobile/views/comments` (about 10 overs per page), `web/views/comments` (`OverLimit` up to 50, page with `LastOverNumber`), `mobile/views/standings`, `web/competitions?Limit=`, `web/teams`, `web/players?PlayerIds=`, `mobile/news?Region=&Limit=`, `mobile/views/home?Region=`, `mobile/views/highlights` (needs both `FixtureId` and `CompetitionId`).

Confirmed absent, do not probe again: rankings, career statistics, stat leaders, player name search, squads outside a match, venues, head-to-head.

Quirks: `GameType` is often blank on upcoming fixtures (format falls back to the competition); shot angle 0 with distance 0 means untracked; shot angles are ground coordinates with 270° straight and 0° square leg for a right-hander; dates of birth come in two formats.

Captured real responses for tests live in `core/network/src/test/resources/fixtures` and are reused by `core:data` and `app` tests.

## Conventions

- Every screen: `XRoute` (wires the view model) and a stateless `XScreen(state, callbacks)`.
- Loading state is `Loadable<T>`; use `Refreshable` in view models and `WhileUiVisible` for `stateIn` so nothing polls off-screen.
- Scores are written runs-first: `143/5`, `339`, `295/9d`.
- Team, competition and player images go through `Badge`, which falls back to a coloured disc.
- New endpoint: add to `CricketAuApi`, DTO with defaults on every field, mapper, a function on `CricketRemoteDataSource`, and a fixture-backed test.
- Kotlin warnings are errors in the pure-Kotlin modules.
- The launcher icon (a bowled ball: amber C, off stump knocked back, bails flying) is vector only and generated by `python3 docs/store/make_icon.py`, which writes `ic_launcher_foreground/background/monochrome.xml`, `ic_stat_criczy.xml` and `docs/store/icon.svg`: edit the script, not the XML. `docs/store/icon-512.png` (the Play listing icon) is rendered from `icon.svg` by `make_store_images.js`.
- Onboarding illustrations use invented teams (Lions, Hawks) on purpose; never put real teams or results in intro art.
- Play Store images: `./gradlew :app:testDebugUnitTest -Pstore=true --tests '*StoreScreenshotsTest*'` renders the screens at 560dpi with the fixtures rewritten to India v Australia (`storeData`), plus the launcher icon as Android draws it (`icon-*.png`); then `node docs/store/make_store_images.js` writes the six 1080×1920 screenshots (one continuous background across the set, zoomed callouts measured on a 1078 px wide render), the 1024×500 feature graphic and `icon-512.png`.
- Never commit keys, `keystore.properties`, `google-services.json`, APKs or AABs.

## Open items

What to build next, in order, with the API fields each idea uses: `docs/ROADMAP.md` (start at "Suggested order"). The November 2026 polish session (commentary replays, match centre, main screens) is merged to `main`.

1. Test the release build on a real device (never done).
2. Release builds are debug-signed until the owner adds `keystore.properties` (`docs/RELEASE.md`).
3. Data licensing: the API, logos and photos belong to Cricket Australia; unresolved Google Play risk.
4. The repository is public; the owner originally wanted it private.
5. Deferred: ads, AI. Blocked by the data source: rankings, career statistics.
6. Possible follow-ups: cache tournament leaderboards in Room; split `feature/*` into Gradle modules; upgrade to AGP 9.
7. Store follow-ups the owner approved, to do next: fix the "Back of alength" chip text and re-render screenshot 3; write the Play short and full descriptions and What's new; tablet screenshots (7" and 10"); host the privacy policy on GitHub Pages (needs the owner's support email).

## Working efficiently

- Keep this file current: when a decision is made or a gotcha is found, add one line here in the same commit.
- Prefer targeted test runs (`--tests '*Name*'`) while iterating, and the full suite once before committing.
- Do not re-audit Cricpeak, re-probe the API or re-verify library versions unless the task is about that.
