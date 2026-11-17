package app.criczy.di

import android.content.Context
import app.criczy.BuildConfig
import app.criczy.core.data.CatalogRepository
import app.criczy.core.data.CriczyDatabase
import app.criczy.core.data.FavouritesRepository
import app.criczy.core.data.MatchRepository
import app.criczy.core.data.NewsRepository
import app.criczy.core.data.PlayerRepository
import app.criczy.core.data.SearchRepository
import app.criczy.core.data.UserPreferencesRepository
import app.criczy.core.network.ConnectionMonitor
import app.criczy.core.network.CricketAuRemoteDataSource
import app.criczy.core.network.CricketHttp
import app.criczy.core.network.CricketRemoteDataSource
import okhttp3.OkHttpClient
import java.io.File

/**
 * The app's object graph, built by hand. Everything is created lazily and lives as long as
 * the process. To change data provider, replace [remote]: nothing else refers to cricket.com.au.
 */
class AppContainer(
    private val context: Context,
    remoteOverride: CricketRemoteDataSource? = null,
    databaseOverride: CriczyDatabase? = null,
) {
    val connection = ConnectionMonitor()

    val httpClient: OkHttpClient by lazy {
        CricketHttp.client(
            cacheDir = File(context.cacheDir, "http"),
            monitor = connection,
            userAgent = "Criczy/${BuildConfig.VERSION_NAME} (Android)",
        )
    }

    val remote: CricketRemoteDataSource by lazy { remoteOverride ?: CricketAuRemoteDataSource.create(httpClient) }
    val database: CriczyDatabase by lazy { databaseOverride ?: CriczyDatabase.create(context) }

    val preferences by lazy { UserPreferencesRepository(context) }
    val matches by lazy { MatchRepository(remote, database.seenPlayers()) }
    val catalog by lazy { CatalogRepository(remote) }
    val players by lazy { PlayerRepository(remote, database.seenPlayers()) }
    val news by lazy { NewsRepository(remote) }
    val favourites by lazy { FavouritesRepository(database.favourites()) }
    val search by lazy { SearchRepository(catalog, matches, database.seenPlayers()) }
}
