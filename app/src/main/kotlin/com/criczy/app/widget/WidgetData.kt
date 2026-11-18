package com.criczy.app.widget

import android.content.Context
import com.criczy.app.CriczyApp
import com.criczy.app.core.data.AlertPlanner
import com.criczy.app.core.model.Ball
import com.criczy.app.core.model.BatterLine
import com.criczy.app.core.model.BowlerLine
import com.criczy.app.core.model.InningsBallByBall
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchBoard
import com.criczy.app.core.model.MatchDetail
import com.criczy.app.core.model.MatchState
import com.criczy.app.core.network.CricketApiException
import com.criczy.app.notifications.LiveScoreService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** The match all widgets show, with the extra details the Match centre widget needs, and when it was fetched. */
data class WidgetSnapshot(
    val match: Match?,
    val centre: WidgetCentre? = null,
    val updatedAt: Instant? = null,
)

/** The newest over of the innings in play, oldest ball first. */
data class WidgetOver(val number: Int, val isComplete: Boolean, val balls: List<Ball>)

/** What the Match centre widget adds to the score: who is batting and bowling, this over, and the chase. */
data class WidgetCentre(
    val batters: List<BatterLine> = emptyList(),
    val bowler: BowlerLine? = null,
    val over: WidgetOver? = null,
    val runRate: Double? = null,
    val requiredRate: Double? = null,
    val target: Int? = null,
    val playerOfTheMatch: String? = null,
)

/** What the widgets show: a pinned match first, then a followed live match, any live match, the next fixture, a result. */
internal fun pickWidgetMatch(board: MatchBoard, followed: List<Match>, pinnedId: Int? = null): Match? {
    if (pinnedId != null) board.all.firstOrNull { it.id == pinnedId }?.let { return it }
    val followedIds = followed.mapTo(HashSet()) { it.id }
    fun List<Match>.preferFollowed() = sortedBy { it.id !in followedIds }
    return board.live.filter { it.state == MatchState.LIVE }.preferFollowed().firstOrNull()
        ?: board.live.preferFollowed().firstOrNull()
        ?: board.upcoming.sortedBy { it.start }.let { upcoming -> upcoming.firstOrNull { it.id in followedIds } ?: upcoming.firstOrNull() }
        ?: board.completed.firstOrNull()
}

internal val Match.inPlay: Boolean get() = state == MatchState.LIVE || state == MatchState.IN_PROGRESS

/** The batters at the crease (striker first), the bowler, the newest over and the rates, from the scorecard and commentary. */
internal fun widgetCentre(detail: MatchDetail, commentary: InningsBallByBall?): WidgetCentre {
    val match = detail.match
    if (!match.inPlay) {
        val potm = detail.playersOfTheMatch.firstOrNull()?.name?.takeIf { match.state == MatchState.COMPLETED }
        return WidgetCentre(playerOfTheMatch = potm)
    }
    val innings = detail.currentInnings ?: return WidgetCentre()
    val newest = commentary?.takeIf { it.inningsNumber == innings.number }?.overs?.maxByOrNull { it.number }
    return WidgetCentre(
        batters = innings.batters.filter { it.isAtCrease }.sortedByDescending { it.isOnStrike }.take(2),
        bowler = innings.bowlers.firstOrNull { it.isBowlingNow },
        over = newest?.takeIf { it.balls.isNotEmpty() }?.let { WidgetOver(it.number, it.isComplete, it.balls) },
        runRate = innings.runRate.takeIf { it > 0 },
        requiredRate = innings.requiredRunRate?.takeIf { it > 0 },
        target = detail.target,
    )
}

/**
 * Loads and holds the widgets' snapshot. Every widget reads the same [snapshot], so placing all three costs
 * one board request (plus the scorecard and newest over when a Match centre widget is placed).
 */
object WidgetData {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<WidgetSnapshot?>(null)
    private val _refreshing = MutableStateFlow(false)
    private var lastFetch: Instant = Instant.EPOCH

    val snapshot: StateFlow<WidgetSnapshot?> = _snapshot.asStateFlow()

    /** True while a refresh asked for from the widget runs, so the widgets can say so. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** A widget starting up within this long of the last fetch reuses it instead of asking the network again. */
    private val fresh: Duration = Duration.ofSeconds(60)

    suspend fun load(context: Context, force: Boolean = false, board: MatchBoard? = null): WidgetSnapshot = mutex.withLock {
        val now = Instant.now()
        val cached = _snapshot.value
        if (!force && board == null && cached != null && Duration.between(lastFetch, now) < fresh) return@withLock cached

        val container = (context.applicationContext as CriczyApp).container
        val fetched = board ?: try {
            container.matches.board()
        } catch (_: CricketApiException) {
            null
        }
        // Offline: the last board this process saw, so a widget never goes blank because of one failed request.
        val usable = fetched ?: container.matches.lastBoard.value
        val match = pickWidgetMatch(usable, AlertPlanner.followed(usable.all, container.favourites.snapshot()), LiveScoreService.pinned.value)
            ?: cached?.match
        val centre = if (match != null && Widgets.hasMatchCentre(context)) loadCentre(context, match, cached) else null
        val next = WidgetSnapshot(match, centre, if (fetched != null) now else cached?.updatedAt)
        if (fetched != null) lastFetch = now
        _snapshot.value = next
        next
    }

    /** The pinned live score fetched [detail] anyway: show it without another request for the score. */
    suspend fun accept(context: Context, detail: MatchDetail): WidgetSnapshot = mutex.withLock {
        val cached = _snapshot.value
        val centre = if (Widgets.hasMatchCentre(context)) loadCentre(context, detail.match, cached, detail) else null
        val now = Instant.now()
        lastFetch = now
        WidgetSnapshot(detail.match, centre, now).also { _snapshot.value = it }
    }

    suspend fun <T> showingProgress(block: suspend () -> T): T {
        _refreshing.value = true
        try {
            return block()
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun loadCentre(context: Context, match: Match, cached: WidgetSnapshot?, known: MatchDetail? = null): WidgetCentre? {
        if (match.state == MatchState.UPCOMING) return null
        val container = (context.applicationContext as CriczyApp).container
        val previous = cached?.centre?.takeIf { cached.match?.id == match.id }
        return try {
            val detail = known ?: container.matches.detail(match.id) ?: return previous
            val innings = detail.currentInnings
            val commentary = if (match.inPlay && detail.hasCommentary && innings != null) {
                try {
                    container.matches.commentary(match.id, innings.number)
                } catch (_: CricketApiException) {
                    null
                }
            } else {
                null
            }
            val built = widgetCentre(detail, commentary)
            // Keep the last over shown if only the commentary request failed.
            if (built.over == null && commentary == null && previous?.over != null && match.inPlay) built.copy(over = previous.over) else built
        } catch (_: CricketApiException) {
            previous
        }
    }
}
