# Ads

Google AdMob, configured remotely from Firebase Realtime Database. Code: `app/src/main/kotlin/com/criczy/app/ads/`.
Where every ad goes, as a table: `docs/ads/ad-placements.pdf`.

## Where ads go, and where they never go

| Unit (`ads_config` key) | Format | Where | When |
|---|---|---|---|
| `native_home` | Native card | Home → Upcoming and Results lists | After the 3rd match card, then every 8th |
| `native_news` | Native card | News list | After the 4th story, then every 6th |
| `banner_match` | Adaptive banner | End of the Scorecard and Analysis tabs | Finished matches only |
| `banner_browse` | Adaptive banner | End of Series list, series table, series stats, team page, player page | Always |
| `interstitial` | Full screen | Leaving a finished match (back to the previous screen) | Caps below |
| `rewarded` | Full screen, opt-in | More → Ads → "Hide ads for 24 hours" | Only when the user taps it |
| `app_open` | Full screen | Returning to the app after a long time away | Caps below; off by default |

Never: the Live tab, any live or in-progress match screen, commentary and shot replays, widgets, notifications, the
pinned score, the fan community, onboarding and the team picker, Settings rows, dialogs, or next to buttons people tap
often. No full-screen ad on app launch, on exit, during a live match, or within 10 minutes of opening the app from a
notification, widget or link.

## Realtime Database: `ads_config`

Import `docs/ads/ads_config.json` (Realtime Database → ⋮ → Import JSON), then replace each `REPLACE_WITH_…` with the
unit id from AdMob. Changes reach phones within seconds while the app is open, and on the next launch otherwise (the
last configuration is kept on the phone, so ads start without waiting for the network).

Top level:

| Field | Type | Meaning |
|---|---|---|
| `enabled` | boolean | Master switch. `false` turns every ad off. |
| `test_device_ids` | list of strings | Phones that get test ads in release builds (the id is printed in Logcat by the AdMob SDK). |

Every unit object:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | boolean | false | This unit on or off. |
| `unit_id` | string | – | AdMob ad unit id, `ca-app-pub-…/…`. Anything else keeps the unit off. |

Extra fields, by unit:

| Field | Units | Default | Meaning |
|---|---|---|---|
| `first_position` | native | 3 (home), 4 (news) | The first ad goes after this many items. |
| `interval` | native | 8 (home), 6 (news) | Items between two ads (at least 2). |
| `min_interval_minutes` | interstitial, app_open | 4 | Minutes between two ads of the unit. |
| `daily_cap` | interstitial, app_open | 3 (interstitial), 1 (app open) | Ads of the unit per day. |
| `grace_hours` | interstitial, app_open | 48 (interstitial), 168 (app open) | No ad of the unit this soon after install. |
| `quiet_after_link_minutes` | interstitial, app_open | 10 | No ad of the unit this soon after opening the app from a notification, widget or link. |
| `min_away_minutes` | app_open | 120 | Minutes the app must have been in the background. |
| `reward_hours` | rewarded | 24 | Hours without banners, interstitials and app open ads after watching (1–72). Native ads stay. |

Security rules: `firebase/database.rules.json` (anyone can read `ads_config`, nobody can write from the app). Paste
them into Realtime Database → Rules and publish.

Debug builds always use Google's test unit ids, whatever the database says, so nobody taps a real ad during development.
Without Firebase (debug builds have no Firebase client), debug builds show test ads with the defaults.

## Show rate, match rate and smoothness

- **Show rate** (impressions ÷ loaded ads) is kept close to 100% by loading an ad only when it is about to be seen:
  native ads and banners load as their slot comes on screen, with one native ad kept ready per unit; the interstitial
  loads when a finished match opens; the rewarded ad when More opens; the app open ad when the app goes to the background,
  only if a later return could show it. Ads are thrown away before they expire (55 minutes; app open: 4 hours).
- **Match rate** (loaded ÷ requested) depends on how many advertisers bid for each request, which no code controls. It
  rises with AdMob mediation (bidding from AppLovin, Meta, InMobi, Unity…): each network is set up in the AdMob
  console and its adapter added to the app. Expect well below 100% in some countries.
- **No lag**: the SDK starts on a background thread after consent; `OPTIMIZE_INITIALIZATION` and `OPTIMIZE_AD_LOADING`
  move its work off the main thread; slots take no space until an ad has loaded, so lists never jump.

## Consent

Google's User Messaging Platform runs once the user is past onboarding. Where required (EEA, UK, US states with privacy
laws) it shows Google's consent form; ads are requested only once it allows. More → Ads → "Ad privacy choices" appears
where the law requires a way to change the choice. Create the GDPR and US state messages in AdMob → Privacy & messaging.

## Release checklist

1. AdMob: add the app, create the units (native ×2, banner ×2, interstitial, rewarded, app open), and the GDPR and US
   state messages.
2. AdMob → Blocking controls: block gambling, betting and real-money gaming categories; maximum ad content rating T.
3. `admob.properties` in the project root (never committed): `admob.appId=ca-app-pub-…~…`. Without it, builds carry
   Google's sample app id and show only test ads.
4. Firebase: create the Realtime Database, publish `firebase/database.rules.json`, import `docs/ads/ads_config.json`
   and fill in the unit ids. Download `google-services.json` again afterwards, so it contains the database URL.
5. `app-ads.txt` on the developer website (the GitHub Pages site): the line AdMob shows under Apps → app-ads.txt.
6. Play Console: "Contains ads" Yes; Advertising ID declaration (used for advertising); Data safety: device or other
   IDs, approximate location and app interactions, collected and shared for advertising, analytics and fraud
   prevention; target audience 13+.
7. Never tap your own ads in a release build; add your phone to `test_device_ids`.
