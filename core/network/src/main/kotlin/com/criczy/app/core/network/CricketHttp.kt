package com.criczy.app.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Tells the UI whether the data on screen came from the network or from the offline cache. */
class ConnectionMonitor {
    private val _servingCached = MutableStateFlow(false)

    /** True after a request had to be answered from the offline cache; false after a network success. */
    val servingCached: StateFlow<Boolean> = _servingCached.asStateFlow()

    internal fun report(cached: Boolean) {
        _servingCached.value = cached
    }
}

/**
 * When the network fails, answer from the HTTP cache however old the entry is. Anything the
 * user has opened before therefore stays readable offline, with no separate persistence code.
 * If nothing is cached the original failure is rethrown.
 */
internal class OfflineFallbackInterceptor(private val monitor: ConnectionMonitor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request).also { if (it.networkResponse != null) monitor.report(false) }
        } catch (failure: IOException) {
            if (request.method != "GET") throw failure
            val stale = CacheControl.Builder().onlyIfCached().maxStale(Int.MAX_VALUE, TimeUnit.SECONDS).build()
            val cached = try {
                chain.proceed(request.newBuilder().cacheControl(stale).build())
            } catch (_: IOException) {
                throw failure
            }
            if (cached.code == 504) {
                // OkHttp's answer for "only-if-cached but nothing cached".
                cached.close()
                throw failure
            }
            monitor.report(true)
            cached
        }
    }
}

/**
 * The API sends no caching headers. Mark successful responses cacheable for a few seconds so
 * they are stored for offline use and so two screens asking for the same thing share one call.
 */
internal class CacheHeaderInterceptor(private val freshSeconds: Int) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (chain.request().method != "GET" || !response.isSuccessful) return response
        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .header("Cache-Control", "public, max-age=$freshSeconds")
            .build()
    }
}

object CricketHttp {
    private const val CACHE_BYTES = 30L * 1024 * 1024

    /**
     * @param cacheDir directory for the offline cache; null disables caching (tests).
     * @param configure hook for debug-only additions such as logging.
     */
    fun client(
        cacheDir: File?,
        monitor: ConnectionMonitor,
        userAgent: String,
        freshSeconds: Int = 3,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .apply { if (cacheDir != null) cache(Cache(cacheDir, CACHE_BYTES)) }
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .build(),
            )
        }
        .addInterceptor(OfflineFallbackInterceptor(monitor))
        .addNetworkInterceptor(CacheHeaderInterceptor(freshSeconds))
        .apply(configure)
        .build()
}
