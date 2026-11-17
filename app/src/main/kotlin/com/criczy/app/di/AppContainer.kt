package com.criczy.app.di

import android.content.Context
import com.criczy.app.BuildConfig
import com.criczy.app.community.FirestoreCommunityBackend
import com.criczy.app.core.data.CommunityBackend
import com.criczy.app.core.data.CommunityRepository
import com.criczy.app.core.data.CatalogRepository
import com.criczy.app.core.data.CriczyDatabase
import com.criczy.app.core.data.FavouritesRepository
import com.criczy.app.core.data.MatchRepository
import com.criczy.app.core.data.NewsRepository
import com.criczy.app.core.data.PlayerRepository
import com.criczy.app.core.data.SearchRepository
import com.criczy.app.core.data.UserPreferencesRepository
import com.criczy.app.core.network.ConnectionMonitor
import com.criczy.app.core.network.CricketAuRemoteDataSource
import com.criczy.app.core.network.CricketHttp
import com.criczy.app.core.network.CricketRemoteDataSource
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The app's object graph, built by hand. Everything is created lazily and lives as long as
 * the process. To change data provider, replace [remote]: nothing else refers to cricket.com.au.
 */
class AppContainer(
    private val context: Context,
    remoteOverride: CricketRemoteDataSource? = null,
    databaseOverride: CriczyDatabase? = null,
    communityOverride: CommunityBackend? = null,
) {
    val connection = ConnectionMonitor()

    val httpClient: OkHttpClient by lazy {
        CricketHttp.client(
            cacheDir = File(context.cacheDir, "http"),
            monitor = connection,
            userAgent = "Criczy/${BuildConfig.VERSION_NAME} (Android)",
        )
    }

    /**
     * Images get their own client. The API client marks every response as JSON that goes stale
     * after a few seconds and, like any OkHttp client, downloads at most 5 files at a time from
     * one host, so a screen of logos or headshots filled in slowly. Coil keeps its own disk
     * cache (see CriczyApp), which also serves images when offline.
     */
    val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "Criczy/${BuildConfig.VERSION_NAME} (Android)").build())
            }
            .build()
    }

    val remote: CricketRemoteDataSource by lazy { remoteOverride ?: CricketAuRemoteDataSource.create(httpClient) }
    val database: CriczyDatabase by lazy { databaseOverride ?: CriczyDatabase.create(context) }

    val preferences by lazy { UserPreferencesRepository(context) }
    val matches by lazy { MatchRepository(remote, database.seenPlayers()) }
    val catalog by lazy { CatalogRepository(remote) }
    val players by lazy { PlayerRepository(remote, database.seenPlayers()) }
    val news by lazy { NewsRepository(remote) }
    val favourites by lazy { FavouritesRepository(database.favourites()) }
    val community by lazy { CommunityRepository(communityOverride ?: FirestoreCommunityBackend(context), database.community()) }
    val search by lazy { SearchRepository(catalog, matches, database.seenPlayers()) }
}
