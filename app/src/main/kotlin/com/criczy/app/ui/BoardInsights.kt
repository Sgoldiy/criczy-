package com.criczy.app.ui

import com.criczy.app.core.data.Favourite
import com.criczy.app.core.data.FavouriteType
import com.criczy.app.core.data.MatchRepository
import com.criczy.app.core.model.Competition
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchBoard
import com.criczy.app.core.model.MatchState
import com.criczy.app.core.model.Team
import com.criczy.app.ui.format.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant

/**
 * The latest board of matches the app has, shared with the home screen; loads one first when
 * nothing has been loaded yet (opening a screen other than home). A failed load leaves it empty.
 */
fun MatchRepository.latestBoard(): Flow<MatchBoard> = lastBoard.onStart {
    if (lastBoard.value.isEmpty) {
        try {
            board()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing to show beside the main content; the screen works without it.
        }
    }
}

/** A series with a match in play, or one starting within a day; [live] when a ball is being bowled. */
data class SeriesShortcut(val competition: Competition, val live: Boolean)

fun seriesInPlay(board: MatchBoard, now: Instant = Instant.now(), limit: Int = 8): List<SeriesShortcut> {
    val inPlay = board.live.sortedBy { it.state != MatchState.LIVE }.groupBy { it.competition.id }
        .map { (_, matches) -> SeriesShortcut(matches.first().competition, matches.any { it.state == MatchState.LIVE }) }
    val taken = inPlay.mapTo(HashSet()) { it.competition.id }
    val soon = board.upcoming
        .filter { match -> match.start?.let { !it.isBefore(now) && Duration.between(now, it) <= Duration.ofHours(24) } == true }
        .sortedBy { it.start }
        .map { it.competition }
        .filter { it.id !in taken }
        .distinctBy { it.id }
        .map { SeriesShortcut(it, live = false) }
    return (inPlay + soon).take(limit)
}

/** What a series is doing now: the match it has in play, and its next fixture. */
data class SeriesActivity(val current: Match?, val next: Match?)

fun seriesActivity(board: MatchBoard): Map<Int, SeriesActivity> {
    val current = board.live.sortedBy { it.state != MatchState.LIVE }.groupBy { it.competition.id }.mapValues { it.value.first() }
    val next = board.upcoming.filter { it.start != null }.groupBy { it.competition.id }.mapValues { (_, matches) -> matches.minBy { it.start!! } }
    return (current.keys + next.keys).associateWith { SeriesActivity(current[it], next[it]) }
}

private fun Match.involves(favourite: Favourite): Boolean = when (favourite.type) {
    FavouriteType.TEAM -> home.id == favourite.id || away.id == favourite.id
    FavouriteType.COMPETITION -> competition.id == favourite.id
    FavouriteType.MATCH -> id == favourite.id
    FavouriteType.PLAYER -> false
}

/** Matches involving anything followed: those in play, then the next three, then the last two results. */
fun followedMatches(board: MatchBoard, favourites: List<Favourite>): List<Match> {
    if (favourites.isEmpty()) return emptyList()
    fun Match.followed() = favourites.any { involves(it) }
    val inPlay = board.live.filter { it.followed() }.sortedBy { it.state != MatchState.LIVE }
    val next = board.upcoming.filter { it.followed() }.sortedBy { it.start }.take(3)
    val results = board.completed.filter { it.followed() }.sortedByDescending { it.start }.take(2)
    return (inPlay + next + results).distinctBy { it.id }
}

/** One line on what a followed team, series or match is doing, from the board. */
data class FavouriteStatus(val text: String, val live: Boolean)

private fun Match.scoreLine(): String? = currentInnings?.let { innings -> "${team(innings.battingTeamId)?.shortName.orEmpty()} ${innings.scoreText}".trim() }

fun favouriteStatus(board: MatchBoard, favourite: Favourite, now: Instant = Instant.now()): FavouriteStatus? {
    if (favourite.type == FavouriteType.PLAYER) return null
    board.live.filter { it.involves(favourite) }.minByOrNull { if (it.state == MatchState.LIVE) 0 else 1 }?.let { match ->
        val label = if (match.state == MatchState.LIVE) "Live" else "In progress"
        val what = if (favourite.type == FavouriteType.MATCH) null else match.title
        return FavouriteStatus(listOfNotNull(label, what, match.scoreLine()).joinToString(" · "), live = match.state == MatchState.LIVE)
    }
    board.upcoming.filter { it.involves(favourite) }.minByOrNull { it.start ?: Instant.MAX }?.let { match ->
        val label = if (favourite.type == FavouriteType.MATCH) "Starts" else "Next"
        val what = if (favourite.type == FavouriteType.MATCH) null else match.title
        return FavouriteStatus(listOfNotNull(label, what, Formatters.matchTime(match.start, now)).joinToString(" · "), live = false)
    }
    board.completed.filter { it.involves(favourite) }.maxByOrNull { it.start ?: Instant.MIN }?.let { match ->
        val label = if (favourite.type == FavouriteType.MATCH) "Result" else "Latest"
        return FavouriteStatus("$label · ${match.statusText ?: match.title}", live = false)
    }
    return null
}

/** Teams playing now or soon, to offer someone who follows nothing yet. */
fun suggestedTeams(board: MatchBoard, followed: Set<Int>, limit: Int = 8): List<Team> =
    (board.live + board.upcoming.sortedBy { it.start })
        .flatMap { listOf(it.home, it.away) }
        .distinctBy { it.id }
        .filter { it.id !in followed }
        .take(limit)
