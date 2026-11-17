package app.criczy

import android.app.Application
import app.criczy.di.AppContainer
import app.criczy.notifications.MatchAlertWorker
import app.criczy.notifications.Notifier
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

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

    // Images share the app's HTTP client, so logos and headshots also use the offline cache.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
            .crossfade(true)
            .build()
}
