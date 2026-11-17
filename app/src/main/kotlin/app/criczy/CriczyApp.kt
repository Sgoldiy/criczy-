package app.criczy

import android.app.Application
import app.criczy.di.AppContainer
import app.criczy.notifications.MatchAlertWorker
import app.criczy.notifications.Notifier
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

open class CriczyApp : Application(), SingletonImageLoader.Factory {

    /** Built in [createContainer], which tests override to supply fakes. */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        Notifier.createChannels(this)
        startBackgroundWork()
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)

    protected open fun startBackgroundWork() = MatchAlertWorker.schedule(this)

    // Images use their own HTTP client and caches: a quarter of the app's memory for decoded
    // images, and a disk cache that keeps logos and headshots between launches and offline.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.imageHttpClient })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(150)
            .build()

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 100L * 1024 * 1024
    }
}
