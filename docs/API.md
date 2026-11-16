# Cricket data API

Provider: `https://apiv2.cricket.com.au/` — the backend of Cricket Australia's own apps. It needs no key. It is undocumented and unlicensed for third-party use, so the app treats it as replaceable: everything goes through `CricketRemoteDataSource` in `core:network`, and nothing outside that module knows which provider is behind it.

Everything below was confirmed against the live service on 16 Nov 2026. Rerun the check with:

```bash
./gradlew :core:network:test -PliveApi=true --tests '*LiveApiContractTest*'
```

## Endpoints in use

| Endpoint | Parameters | Returns | Notes |
|---|---|---|---|
| `mobile/views/fixtures` | `UpcomingFixturesCount`, `InProgressFixturesCount`, `CompletedFixturesCount` (all required, max 100), optional `CompetitionId`, `TeamId` | Three match lists with teams, competition, venue, innings scores | Cricpeak never used the filters; they power series and team pages |
| `mobile/views/scorecard` | `FixtureId` | Full scorecard, squads with bios, officials, toss, partnership | Unknown ids return a body with no `Fixture` |
| `mobile/views/comments` | `FixtureId`, `InningNumber`, optional `LastOverNumber` | About 10 overs of ball-by-ball, newest first, plus a `NextPage` cursor | Used for the live commentary feed |
| `web/views/comments` | `FixtureId`, `InningNumber`, `OverLimit` (max 50), optional `LastOverNumber` | Up to 50 overs of one innings in one call | Used for charts; Test innings are paged back to over 1 |
| `mobile/views/standings` | `CompetitionId` | Points table with team objects and flags for which columns apply | |
| `web/competitions` | `Limit` | Competitions with dates, format, tour | Default page is 10; `Limit` is the only paging parameter that works |
| `web/teams` | none | 321 teams with colours, logos, banners | |
| `web/players` | `PlayerIds` (comma separated) | Player profiles | No name search: `Search`, `Name`, `TeamId` and similar are ignored |
| `mobile/news` | `Region`, `Limit` | News headlines with summary, image, link, related match/team/competition ids | Links open on cricket.com.au |
| `mobile/views/home` | `Region` | 5 news, 5 videos, 4 featured matches | |
| `mobile/views/highlights` | `FixtureId`, `CompetitionId` (both required) | Key moments with thumbnails | Only for matches with video coverage |

Also available but not needed: `mobile/views/summary` (same shape as scorecard), `web/fixtures?CompetitionId=&Limit=` (flat fixture list with broadcasters), `web/tours`, `web/fixtures/yearfilter`.

## Ball-level data

Each ball carries: batter, non-striker, bowler, runs (off bat, team, charged to bowler), wicket and dismissal type, wide/no-ball/bye/leg-bye flags, free hit, powerplay, timestamp, shot angle and distance, batting hand, line, length, shot type, connection, footwork, fielding position, delivery type, and commentary text. Each over carries the running innings total and wickets.

This is what makes Manhattan, worm, run-rate, wagon wheel, pitch map, partnerships, batter-v-bowler matchups and phase analysis possible without any estimation. A test checks that the ball data of a real innings adds up exactly to its scoreboard.

Shot angles are in ground coordinates: for a right-hander 0° is square leg, 90° behind the wicket, 180° square on the off side, 270° straight. This was derived from the fielding position recorded with each ball and is covered by tests.

## Not available from this provider

Probed and absent (404): player career statistics, ICC rankings, competition stat leaders (most runs, most wickets), squad lists outside a match, venue pages, head-to-head records, player name search.

Consequences for the feature list:

- **Rankings**: cannot be built from this API.
- **Career statistics and career-based player comparison**: cannot be built. Comparison within a match or across the matches the app has cached can be.
- **Tournament leaderboards**: can be computed by the app from scorecards of a competition's completed matches, since `fixtures?CompetitionId=` lists them.
- **Player search**: possible only over players the app has already seen in scorecards, plus all teams and competitions.

## Quirks handled in the mapper

- Fields are omitted freely; every DTO field has a default.
- Dates of birth come as `1993-06-13` or `13/06/1993`.
- Bios are HTML fragments; they are converted to plain text.
- `HasStanding` is the string `"true"`/`"false"`/`""`.
- `RequiredRunRate` is `0` when not applicable.
- A shot angle of 0 with distance 0 means "not tracked".
- Video duration is `HH:mm:ss` on the home feed and milliseconds on highlights.
- Validation errors return HTTP 400 with `ResponseStatus.Message`; gateway errors return HTML.
