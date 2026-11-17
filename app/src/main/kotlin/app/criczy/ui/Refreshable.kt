package app.criczy.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.criczy.core.data.Loadable
import app.criczy.core.data.keepLastData
import app.criczy.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Stop upstream work five seconds after the last collector leaves (rotation survives, backgrounding does not). */
val WhileUiVisible: SharingStarted = SharingStarted.WhileSubscribed(5_000)

/**
 * A loadable value a screen can pull-to-refresh. Refreshing restarts the source flow while the
 * previous data stays visible, and nothing runs when no screen is collecting [state].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Refreshable<T>(scope: CoroutineScope, source: () -> Flow<Loadable<T>>) {
    private val trigger = MutableStateFlow(0)

    val state: StateFlow<Loadable<T>> = trigger
        .flatMapLatest { source() }
        .keepLastData()
        .stateIn(scope, WhileUiVisible, Loadable())

    fun refresh() {
        trigger.value++
    }
}
