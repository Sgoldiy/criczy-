package com.criczy.app.core.data

import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchDetail
import com.criczy.app.core.model.MatchState
import java.time.Duration
import java.time.Instant

/**
 * The polls the app opens in a team's feed: "Who wins?" from a day before a match until it
 * ends, and "Player of the match?" for a day after it. Ids depend only on the match, poll and
 * feed, so every phone arrives at the same poll.
 */
object MatchPolls {
    private val BEFORE_START: Duration = Duration.ofHours(24)
    private val AFTER_END: Duration = Duration.ofHours(24)

    /** The match to show at the top of a team's feed: in play, else starting within a day. */
    fun featured(teamId: Int, matches: List<Match>, now: Instant): Match? {
        val mine = matches.filter { it.home.id == teamId || it.away.id == teamId }
        return mine.firstOrNull { it.state == MatchState.LIVE }
            ?: mine.firstOrNull { it.state == MatchState.IN_PROGRESS }
            ?: mine.filter { it.state == MatchState.UPCOMING && it.start != null && it.start!! in now..now.plus(BEFORE_START) }.minByOrNull { it.start!! }
    }

    /** "Who wins?" polls for the team's matches in play or starting within a day. */
    fun winPolls(teamId: Int, matches: List<Match>, now: Instant): List<MatchPoll> {
        val community = Communities.team(teamId)
        return matches.filter { it.home.id == teamId || it.away.id == teamId }
            .filter { m ->
                m.state == MatchState.LIVE || m.state == MatchState.IN_PROGRESS ||
                    (m.state == MatchState.UPCOMING && m.start != null && m.start!! in now..now.plus(BEFORE_START))
            }
            .map { m ->
                val options = listOf(m.home.name, m.away.name) + if (m.format.inningsPerSide == 2) listOf("Draw") else emptyList()
                MatchPoll(
                    "match-${m.id}-win-$community", community, m.id,
                    "Who wins? ${m.home.shortName} v ${m.away.shortName}${m.competition.name.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}".take(Communities.MAX_TEXT),
                    options.map { it.take(Communities.MAX_OPTION) },
                )
            }
    }

    /** Finished matches of the team recent enough for a "Player of the match?" poll. */
    fun recentlyFinished(teamId: Int, matches: List<Match>, now: Instant): List<Match> =
        matches.filter { (it.home.id == teamId || it.away.id == teamId) && it.state == MatchState.COMPLETED }
            .filter { m -> (m.end ?: m.start)?.let { it.isAfter(now.minus(AFTER_END)) } ?: false }

    /** Up to four standouts: the top two scorers and the top two wicket-takers. */
    fun playerOfTheMatch(teamId: Int, detail: MatchDetail): MatchPoll? {
        val community = Communities.team(teamId)
        val batters = detail.innings.flatMap { it.batters }.filter { !it.didNotBat }
            .groupBy { it.playerId }.map { (id, lines) -> Triple(id, lines.first().name, lines.sumOf { it.runs }) }
            .sortedByDescending { it.third }
        val bowlers = detail.innings.flatMap { it.bowlers }
            .groupBy { it.playerId }.map { (id, lines) -> Triple(id, lines.first().name, lines.sumOf { it.wickets } to lines.sumOf { it.runs }) }
            .filter { it.third.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, String, Pair<Int, Int>>> { it.third.first }.thenBy { it.third.second })
        val picks = LinkedHashMap<Int, String>()
        batters.take(2).forEach { picks.putIfAbsent(it.first, "${it.second} · ${it.third} runs") }
        bowlers.take(2).forEach { (id, name, figures) -> if (id !in picks) picks[id] = "$name · ${figures.first}/${figures.second}" }
        val options = picks.values.take(4).map { it.take(Communities.MAX_OPTION) }
        if (options.size < 2) return null
        val m = detail.match
        return MatchPoll("match-${m.id}-pom-$community", community, m.id, "Player of the match? ${m.home.shortName} v ${m.away.shortName}", options)
    }
}
