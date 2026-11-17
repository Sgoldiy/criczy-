# Roadmap

Everything in the README feature list is built. This page lists what is not.

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
| Career statistics, career-based comparison | No endpoint. Comparison covers a match or a series instead. |
| Player search by name across all players | No search endpoint. Search covers players seen in scorecards you have opened. |
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

## Suggested next steps

1. Real-device testing of the release build.
2. Resolve data licensing.
3. Tournament leaderboards currently read up to 40 scorecards per series; cache the result in Room.
4. Split `app/feature/*` into Gradle modules once more than one person works on the code.
5. Upgrade to AGP 9 and the current Kotlin and Compose releases.
