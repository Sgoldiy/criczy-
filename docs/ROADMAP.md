# Roadmap

Everything in the README feature list is built, plus the November 2026 polish session below.
This page lists what to build next and what is not built. `CLAUDE.md` has the working notes.
Effort: S = small (an hour or two), M = medium (a session), L = large (several sessions).

## Where things stand

Done in the November 2026 polish session:

- **Crash fix (IND v NZ):** commentary rows were keyed by ball number and id; the feed has no
  ids and re-bowled balls repeat their number. Keys are now positional.
- **Navigation:** floating bottom bar with a sliding highlight; tab and screen transitions;
  every top bar uses the white-then-yellow `AppTitle`.
- **Commentary:** a "See shot · FOUR" link per ball opens a full-size ground replaying the ball
  like broadcast analysis (from the bowler's hand, bounce at the recorded length, shot in the
  recorded direction); "Replay over" on over rows plays the whole over and ends on an over
  summary. Filters (All, Wickets, Boundaries), "Bowler to Batter" headings, key balls tinted,
  live events (four, six, wicket, wide, no ball), new balls stay in view, earlier overs load
  on their own.
- **Match centre:** summary with result card, player of the match, key moments that open the
  ball in the commentary, recent overs that open the over replay, "More from this series";
  squads as a role-grouped grid with a team switch; scorecard milestone badges.
- **Main screens:** Matches (series in play, top stories, countdowns), Series (live or next
  match on each card, Following filter), News (lead story, compact rows, Following and Match
  report filters), Favourites (your matches, status per favourite, one-tap follow
  suggestions), More (cards with icons).
- **Player photos** are cropped from the top and zoomed on the face everywhere.

Done on 17 November 2026:

- **Continuous checks:** GitHub Actions (`.github/workflows/checks.yml`) runs every test, lint
  and a release build on each push and keeps the APK as the `criczy-release-apk` artifact.
- **Search test:** taps the result through semantics, so it is no longer flaky.
- **Bottom bar:** smaller; main screens scroll on behind it.
- **Long names** scroll sideways instead of being cut off.
- **Images:** their own HTTP client (more downloads at once) and Coil memory and disk caches.
- **Scorecard:** a small photo on each batting and bowling row.
- **Player page:** recent form (last 5 matches of the player's team), shirt number, club, debut
  dates and social links when the API has them.

Owner decisions from that session: the over-summary pitch map was removed and must not come
back; the Analysis tab's line-and-length table stays.

## 1. Before release

| What | Why | Effort |
|---|---|---|
| Test the release build on a real phone | Never done. Check photo framing (the photo host is blocked in the sandbox), notifications on Android 13+, the widget, replay and scroll smoothness, R8 behaviour | M |
| Release signing | Add `keystore.properties`; see `RELEASE.md` | S |
| Data licensing decision | The API, logos and photos belong to Cricket Australia; the biggest Play risk. Either get permission or move to a licensed provider (the data layer is built to be swapped). A licensed provider would also unlock most of "Blocked by the data source" below | — |

## 2. Data the API already sends that the app does not use

1. **Upcoming match preview (M).** The upcoming summary is thin. Fixtures by `TeamId` give
   each team's last five results (form) and the recent meetings between the two teams
   (head-to-head computed on the device); each team's last scorecard gives a likely XI.
2. **Match reports and related news (S).** Every story carries `matchIds`, `teamIds` and
   `competitionIds`. Add "Read the match report" to finished matches, and news on team and
   series pages.
3. **DRS reviews (S–M).** Balls carry `IsReferred` and `ReferralOutcomeId`
   (`OutReviewRetained`, `NotOutReviewLost`, `OutThirdUmpire`…): 34 of 3,042 balls in the
   NZ v IND series. Review chips in the commentary, a reviews list in the summary, a review
   event animation. (`IsAppeal` is set on every ball and `IsPowerPlay` on most, so neither
   is useful.)
4. **Batting control and pace v spin (M, done 18 Nov 2026 except front/back foot).** `BattingConnectionId` (WellTimed, MisTimed,
   Missed, edges…) gives a control percentage per batter; `BowlingTypeId` (FastSeam, Medium,
   OffSpin, LegSpin, Orthodox, Unorthodox) gives runs and strike rate against pace and spin;
   `BattingFeetId` gives front and back foot splits. For the Analysis tab and player compare.
5. **Fielding stats (S).** Scorecard batters carry `DismissedByPlayerId`: catches, run-outs
   and stumpings per player; "Most catches" in series stats.
6. **Milestone celebrations (S).** Fifties, hundreds, five-wicket hauls and 50/100
   partnerships, detected between scorecard refreshes, animated like the ball events.
7. **Series pages (S).** `Competition.bannerUrl` is unused (banner header); a form column
   (last five results) in points tables; table rows open the team page.
8. **Team page (M).** Form strip (done 18 Nov 2026), next match countdown, team news, XI from the last match.
9. **Featured matches (S).** The home endpoint's `featured` list is unused; use it to order
   the top cards when nothing is live.
10. **Player recent form (done, basic).** Built: last 5 matches from the team's scorecards.
    Still open: caching scorecards in Room, and players whose team the app has not seen yet.
11. **Better player search (M).** The API has no name search, and the app only knows players
    from opened scorecards. Prefetch scorecards of current and recent matches (Wi-Fi, once a
    day) to fill the index.

## 3. Retention and growth

- **Follow teams during onboarding (S–M):** one-tap follow from teams playing now, and turn
  alerts on, so the app is personal from the first launch.
- **Pinned live score in the notification shade (M–L):** the strongest live-sport retention
  feature (Android 16 Live Updates or an ongoing notification). Check Google Play's
  foreground-service policy first.
- **Daily "Today for you" notification (S)** for followed teams' matches.
- **Share a moment (M):** the final frame of a ball replay or a result card as an image
  (`ScorecardImage` shows the pattern).
- **More widgets (M):** "My team" (next or live match), several live matches.
- **Launcher shortcuts and an in-app review prompt (S).**
- **Tablets (M):** scorecard and commentary side by side in the match centre.

## 4. Quality and technical

- **Localisation (L):** almost all UI text is in Kotlin (`strings.xml` has three strings).
  Move it to resources, then add Hindi and others. Commentary and news stay English (API).
- **Cache tournament leaderboards in Room (M):** they currently read up to 40 scorecards per
  series each time; caching makes series stats open instantly.
- **Baseline profile (S)** for faster start-up; needs a device or emulator to generate.
- **Screenshot comparison (S):** Roborazzi only records; verify against saved images in CI.
- **TalkBack pass on a device (S)** and a **data saver** option that skips images (S).
- **Later:** split `app/feature/*` into Gradle modules once more than one person works on the
  code; upgrade to AGP 9 and the current Kotlin and Compose releases.

## Suggested order

1. Release readiness: real-device findings and signing (both need the owner).
2. Match page depth: upcoming preview, match report link, DRS reviews, milestones.
3. Stats: batting control, pace v spin, fielding, standings form, team page.
4. Retention: onboarding team picker, pinned live score, daily notification, sharing, widgets.
5. Localisation (Hindi first).

## Deferred by decision

| Item | Notes |
|---|---|
| AdMob ads and rewarded unlocks | Needs your AdMob ids and Firebase config, supplied outside Git. See ARCHITECTURE.md for where it plugs in. |
| AI match prediction and fantasy XI | Needs a new Gemini key. The old one was committed to a public repository and must be revoked. |
| Premium purchases | Not requested for the new app. |

## Blocked by the data source

| Item | Why |
|---|---|
| ICC rankings | No endpoint. |
| Career statistics, career-based comparison | No endpoint. Comparison covers a match or a series instead; "Player recent form" above is the closest substitute. |
| Player search by name across all players | No search endpoint. Search covers players seen in scorecards you have opened; "Better player search" above widens that. |
| Head-to-head and venue statistics | No endpoints. Recent meetings can be computed from team fixtures (see "Upcoming match preview"). |
| Win probability | No source in the API; a home-made model should not be shown as fact. |
| Instant push alerts | No push server. Alerts are checked on the device about every 15 minutes, so a wicket alert can be several minutes late. |

## Cricpeak features changed on purpose

| Cricpeak | Criczy |
|---|---|
| Commentary locked behind video ads every 2 overs | Open (ads deferred) |
| Uninstall feedback screen writing to Firebase | Dropped: no backend. "Rate" and "Share" remain. |
| Install counter sent to a third-party domain | Dropped |
| iZooto marketing push | Replaced by on-device match alerts |
| Exit confirmation dialog | Kept as an optional setting, off by default |
| 5-second polling everywhere, forever | Interval chosen from match state; stops when the screen is hidden or the match is over |
| Umpires always blank | Shown |
| Portrait only | Phone and tablet layouts |
