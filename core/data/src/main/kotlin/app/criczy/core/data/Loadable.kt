package app.criczy.core.data

import app.criczy.core.network.CricketApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan

enum class LoadError {
    /** No connection and nothing cached for this request. */
    OFFLINE,

    /** The data provider is failing or refusing the request. */
    SERVER,

    /** The provider answered in a format the app does not understand. */
    FORMAT;

    companion object {
        fun from(e: CricketApiException): LoadError = when (e) {
            is CricketApiException.Network -> OFFLINE
            is CricketApiException.Server -> SERVER
            is CricketApiException.Malformed -> FORMAT
        }
    }
}

/** What a screen needs to draw: the last good data, whether a load is running, and any failure. */
data class Loadable<out T>(
    val data: T? = null,
    val loading: Boolean = true,
    val error: LoadError? = null,
    val updatedAt: Long? = null,
) {
    val isInitialLoad: Boolean get() = loading && data == null
    val isFailedEmpty: Boolean get() = error != null && data == null
}

/**
 * Fetches repeatedly while collected. [nextDelayMs] decides, from the latest value, how long
 * to wait before the next fetch; returning null ends polling (for example once a match is over).
 * Failures back off exponentially up to a minute and never discard data already shown.
 */
fun <T> pollingFlow(
    nextDelayMs: (T) -> Long?,
    now: () -> Long = System::currentTimeMillis,
    fetch: suspend () -> T,
): Flow<Loadable<T>> = flow {
    var state = Loadable<T>()
    emit(state)
    var failures = 0
    while (true) {
        val wait: Long? = try {
            val value = fetch()
            failures = 0
            state = Loadable(value, loading = false, error = null, updatedAt = now())
            emit(state)
            nextDelayMs(value)
        } catch (e: CricketApiException) {
            failures++
            state = state.copy(loading = false, error = LoadError.from(e))
            emit(state)
            minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl (failures - 1).coerceAtMost(4))
        }
        delay(wait ?: return@flow)
    }
}

/** One-shot load expressed as the same state type. A failure is reported once, not retried. */
fun <T> loadOnce(now: () -> Long = System::currentTimeMillis, fetch: suspend () -> T): Flow<Loadable<T>> = flow {
    emit(Loadable())
    try {
        emit(Loadable(fetch(), loading = false, error = null, updatedAt = now()))
    } catch (e: CricketApiException) {
        emit(Loadable(loading = false, error = LoadError.from(e)))
    }
}

/**
 * When a refresh restarts the upstream flow, keep showing the previous data instead of
 * flashing back to an empty loading state.
 */
fun <T> Flow<Loadable<T>>.keepLastData(): Flow<Loadable<T>> =
    scan(Loadable<T>()) { previous, next ->
        if (next.data == null && previous.data != null) {
            next.copy(data = previous.data, updatedAt = previous.updatedAt)
        } else {
            next
        }
    }

private const val BASE_BACKOFF_MS = 5_000L
private const val MAX_BACKOFF_MS = 60_000L
