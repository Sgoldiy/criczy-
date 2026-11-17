package com.criczy.app.core.data

import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchState

enum class AlertKind { STARTING_SOON, STARTED, WICKET, RESULT }

data class MatchAlert(val matchId: Int, val kind: AlertKind, val title: String, val text: String)

data class AlertPlan(val alerts: List<MatchAlert>, val newStates: List<AlertStateEntity>)

/**
 * Decides which notifications a fresh match board warrants, given what was known last time.
 * Pure logic so it can be tested without Android. A match seen for the first time records
 * its state silently: the user is never notified about things that happened before they
 * followed it.
 */
object AlertPlanner {
    private const val STARTING_SOON_WINDOW_MS = 45 * 60_000L

    fun followed(matches: List<Match>, favourites: List<Favourite>): List<Match> {
        val teams = favourites.filter { it.type == FavouriteType.TEAM }.mapTo(HashSet()) { it.id }
        val ids = favourites.filter { it.type == FavouriteType.MATCH }.mapTo(HashSet()) { it.id }
        val competitions = favourites.filter { it.type == FavouriteType.COMPETITION }.mapTo(HashSet()) { it.id }
        return matches.filter {
            it.id in ids || it.home.id in teams || it.away.id in teams || it.competition.id in competitions
        }
    }

    fun plan(
        matches: List<Match>,
        previous: List<AlertStateEntity>,
        prefs: UserPreferences,
        nowMs: Long,
    ): AlertPlan {
        val known = previous.associateBy { it.matchId }
        val alerts = mutableListOf<MatchAlert>()
        val states = matches.map { match ->
            val before = known[match.id]
            val current = match.currentInnings
            val title = "${match.home.shortName} v ${match.away.shortName}"
            val scoreLine = current?.let {
                val team = match.team(it.battingTeamId)?.shortName.orEmpty()
                "$team ${it.scoreText} (${it.overs})".trim()
            }
            var soonSent = before?.startingSoonSent ?: false

            if (prefs.alertsEnabled) {
                val startMs = match.start?.toEpochMilli()
                if (prefs.alertMatchStart && match.state == MatchState.UPCOMING && !soonSent &&
                    startMs != null && startMs - nowMs in 0..STARTING_SOON_WINDOW_MS
                ) {
                    val minutes = ((startMs - nowMs) / 60_000L).coerceAtLeast(1)
                    alerts += MatchAlert(match.id, AlertKind.STARTING_SOON, title, "Starts in about $minutes min · ${match.competition.name}")
                    soonSent = true
                }
                if (before != null) {
                    val was = MatchState.entries.firstOrNull { it.name == before.state }
                    val started = was == MatchState.UPCOMING &&
                        (match.state == MatchState.LIVE || match.state == MatchState.IN_PROGRESS)
                    val finished = was != MatchState.COMPLETED && match.state == MatchState.COMPLETED
                    when {
                        finished && prefs.alertResults ->
                            alerts += MatchAlert(match.id, AlertKind.RESULT, title, match.statusText ?: "Match finished")
                        started && prefs.alertMatchStart ->
                            alerts += MatchAlert(match.id, AlertKind.STARTED, title, match.tossText ?: "Play is under way")
                        !finished && prefs.alertWickets && current != null &&
                            current.inningsNumber == before.inningsNumber && current.wickets > before.wickets ->
                            alerts += MatchAlert(match.id, AlertKind.WICKET, "Wicket · $title", scoreLine ?: "A wicket has fallen")
                    }
                }
            }
            AlertStateEntity(
                matchId = match.id,
                state = match.state.name,
                inningsNumber = current?.inningsNumber ?: 0,
                wickets = current?.wickets ?: 0,
                startingSoonSent = soonSent,
                updatedAt = nowMs,
            )
        }
        return AlertPlan(alerts, states)
    }
}
