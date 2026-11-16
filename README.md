# Criczy

A new Android cricket app, built to replace Cricpeak. Working name; check availability on Google Play before release.

## Status

Built and tested so far:

- `core:model` — domain types (matches, scorecards, ball-by-ball, standings, news). Pure Kotlin.
- `core:network` — client for the cricket data API behind a provider-neutral interface, with error classification. Tested against responses captured from the live API, plus an opt-in live contract test.
- `core:analytics` — Manhattan, worm, run-rate progression, scoring breakdown, phases, partnerships, batter-v-bowler matchups, wagon wheel zones, pitch map, projected totals.

Not built yet: everything that needs the Android toolchain (database, UI, widget, notifications, release builds). See [docs/ROADMAP.md](docs/ROADMAP.md).

## Build and test

Requires JDK 17 or newer.

```bash
./gradlew test
```

```bash
./gradlew :core:network:test -PliveApi=true --tests '*LiveApiContractTest*'
```

The second command calls the real API and is skipped in normal builds.

## Documentation

- [docs/CRICPEAK_AUDIT.md](docs/CRICPEAK_AUDIT.md) — every Cricpeak screen and feature, and the problems found.
- [docs/API.md](docs/API.md) — every endpoint, what the data supports, and what it cannot.
- [docs/ROADMAP.md](docs/ROADMAP.md) — planned architecture and features, with what each depends on.

## Secrets

No keys, signing files or `google-services.json` belong in this repository; `.gitignore` excludes them. The cricket API needs no key.
