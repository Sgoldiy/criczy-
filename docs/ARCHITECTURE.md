# Architecture

## Layers

```
UI (app)            Route composable -> ViewModel -> StateFlow<UiState> -> stateless Screen composable
Data (core:data)    Repositories, Room, DataStore, polling and refresh policy, alert planning
Remote (core:network)  CricketRemoteDataSource interface; CricketAuRemoteDataSource; OkHttp cache
Domain (core:model, core:analytics)  plain Kotlin types and pure functions
```

Each screen is two composables: a `…Route` that creates the view model and collects state, and a `…Screen` that only takes state and callbacks. Tests and previews use the `…Screen` directly.

Dependencies are wired by hand in `di/AppContainer.kt`. There is no DI framework; the container is small enough to read in one sitting, and tests replace the remote source and database through its constructor.

## Live updates and battery

- View models expose state with `SharingStarted.WhileSubscribed(5s)` and screens collect with `collectAsStateWithLifecycle`. When the screen is not visible, nothing polls.
- `RefreshPolicy` picks the interval from the data: 6 s for an open live match, 60 s at a break, 20 s for the match list when something is live, 2 min otherwise, and never for a finished match.
- Failures back off from 5 s to 60 s and keep the last data on screen.
- Ball-by-ball data for charts is refetched at most once per over.
- Background work is one WorkManager job every 15 minutes, only with a network, and it does nothing if no widget is placed and nothing is followed.

## Offline

`core:network` adds two OkHttp interceptors. One marks successful responses cacheable for 3 seconds, which stores them on disk and lets two screens share one request. The other, on a network failure, replays the request against the cache accepting any age. So everything opened before is available offline with no per-screen persistence code. `ConnectionMonitor` tells the UI when it is looking at a saved copy.

Room holds what is the user's own: favourites, the index of players seen (the provider has no player search), and alert bookkeeping. DataStore holds settings.

## Changing the data provider

Implement `CricketRemoteDataSource` (11 functions returning `core:model` types, throwing `CricketApiException`) and change the one line in `AppContainer.remote`. Nothing else references cricket.com.au except the "stories open on…" and attribution strings in News and More.

## Adding a feature

1. Add any new endpoint to `CricketAuApi`, its DTO to `dto/Dtos.kt`, a mapper, and a function on `CricketRemoteDataSource`. Capture a real response into `core/network/src/test/resources/fixtures` and test the mapping.
2. Add a repository function in `core:data` if it needs caching, combining or persistence.
3. Create `feature/<name>/` with a view model, a Route and a Screen; add a route in `navigation/Routes.kt` and `CriczyNavHost.kt`.
4. Add a screen test in `app/src/test`.

When the app grows, each `feature/<name>` package can become its own Gradle module without code changes, because features only depend on `core` and on `ui/components`.

## Deferred integrations

- **Ads**: add a `core:ads` module exposing an `AdSlot` composable and an interface with a no-op default; place slots in list screens.
- **AI**: add a `core:ai` module with a `MatchInsights` interface. Do not ship a provider key inside the app; put a small server in between.

## Versions

AGP 8.11, Kotlin 2.2, Compose BOM 2025.07, compile and target SDK 36, min SDK 24 (with core library desugaring for `java.time`). Newer major versions of AGP and Kotlin exist; upgrading is routine but was not done here to keep to versions verified in this build.
