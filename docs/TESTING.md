# Testing

```bash
./gradlew test
```

75 tests run offline in about two minutes. Two more call the real API and run only on request:

```bash
./gradlew :core:network:test -PliveApi=true --tests '*LiveApiContractTest*'
```

| Module | Tests | What they cover |
|---|---|---|
| `core:network` | 16 (+2 live) | Every endpoint mapped from responses captured from the live API; server-side limits; paging of long innings; error classification; offline cache fallback and request sharing |
| `core:analytics` | 12 | Manhattan, worm, run rate, scoring breakdown, partnerships, match-ups, phases, projections, wagon-wheel zones (checked against real fielding positions), pitch map |
| `core:data` | 14 | Polling intervals and backoff, one-shot loads, favourites, search (including offline), leaderboards, alert planning (start, wicket, result, starting soon, muted) |
| `app` | 19 | The real navigation graph, view models and repositories rendered with Robolectric against captured data: home tabs and filters, match centre tabs, compare sheet, Test match target, favourites, series table and stats, news, search, error and retry, settings, onboarding |

One network test checks that a real innings' ball data adds up exactly to its scoreboard (runs, legal balls, wickets, every over), because every chart depends on that.

## Screenshots and device sizes

The UI tests save a screenshot of each screen to `app/build/screenshots/`. They run at three sizes: a 411×891 dp phone, a 320×568 dp small phone and a 1280×800 dp tablet, in dark and light themes. A selection is copied to `docs/screenshots`.

## Static checks

```bash
./gradlew :app:lintDebug
```

Lint reports 0 errors. Kotlin warnings are treated as errors in the pure-Kotlin modules.

## Not tested

- **No run on a physical device or emulator.** The build environment has no emulator. Everything above runs on the JVM with Robolectric, which exercises real app code but not a real GPU, real touch input or real system services.
- The release (minified) build was built and inspected (serializers, API interface, manifest and permissions are present) but never launched.
- The widget's rendering, notification delivery, the share sheet, Custom Tabs and the splash screen were compiled and their logic is unit-tested, but they were not seen running.
- Image loading from the provider's image host.

Before publishing, install the release build on at least one phone and walk through: home, a live match for several minutes, commentary paging, analysis, share, follow a team, add the widget, toggle airplane mode.
