# Roadmap

## Architecture

Multi-module, Clean Architecture, MVVM with unidirectional state.

```
app                       navigation, DI wiring, theme
feature:home|match|series|team|player|news|search|favourites|settings|premium
widget                    Glance live-score widget
core:designsystem         Material 3 theme, typography, shared components, charts
core:data                 repositories: network + database, offline-first, polling policy
core:database             Room cache, favourites, search index
core:datastore            preferences
core:notifications        WorkManager match alerts
core:ads                  AdMob behind an interface (no-op when unconfigured)
core:ai                   Gemini behind an interface, key supplied at build time
core:analytics            built
core:network              built
core:model                built
```

Rules: features depend on `core` only, never on each other; only `core:network` knows the provider; UI state comes from Room so every screen works offline with a "last updated" marker.

Live updates: poll only while a screen showing a live match is visible, at 5–10 s for the open match and 30 s for lists, with backoff on errors and no polling for finished matches.

## Features

Status key: **ready** = data and logic exist in the core modules; **planned** = data confirmed, UI not built; **blocked** = see note.

| Feature | Status | Depends on |
|---|---|---|
| Live / upcoming / completed dashboard | planned | `matchBoard` |
| Match centre: summary, scorecard, squads, officials, match info | planned | `matchDetail` |
| Ball-by-ball commentary with paging | planned | `commentaryPage` |
| Per-ball detail (line, length, shot, footwork) and single-shot view | planned | ball data |
| Manhattan, worm, run-rate graphs | ready | `core:analytics` |
| Wagon wheel (innings and per batter), pitch map | ready | `core:analytics` |
| Partnerships, batter-v-bowler matchups, phase analysis | ready | `core:analytics` |
| Projected score, target and required rate | ready | `core:analytics`, `MatchDetail.target` |
| Series and tournament pages with fixtures and results | planned | `competitions`, `matchBoard(competitionId)` |
| Points tables | planned | `standings` |
| Team pages with fixtures and results | planned | `teams`, `matchBoard(teamId)` |
| Player profiles | planned | `players`, scorecard squads |
| News feed (headlines linking to publisher) | planned | `news`, `homeFeed` |
| Match highlights list | planned | `highlights` |
| Tournament leaderboards (runs, wickets) | planned | computed from a competition's scorecards |
| Favourite teams, players, matches | planned | Room |
| Match alerts (start, wicket, result) for favourites | planned | WorkManager polling; no push server |
| Universal search | planned, limited | teams, competitions, and players already seen |
| Player comparison | planned, limited | in-match and cached-match stats only |
| Live-score widget | planned | Glance |
| Shareable scorecard image | planned | |
| Offline cache | planned | Room |
| Light and dark themes, tablet layouts | planned | |
| AI match prediction and fantasy XI | planned | new Gemini key, supplied outside the repo |
| AdMob ads and rewarded unlocks | planned | AdMob ids and Firebase config, supplied outside the repo |
| ICC rankings | blocked | not in the API |
| Career statistics | blocked | not in the API |

## Carried over from Cricpeak, changed on purpose

- Fantasy XI will show an error when the AI fails instead of a random eleven labelled as AI.
- No API key in source. The Gemini key ships via build configuration; a key inside an APK can still be extracted, so a small proxy is the proper fix later.
- No install-counter call to a third-party domain, no cleartext traffic, no body logging in release.
- Umpires and referee are shown; Cricpeak left them blank although the data was there.
