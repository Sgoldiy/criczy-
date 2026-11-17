package app.criczy.core.data

import app.criczy.core.model.BatterLine
import app.criczy.core.model.BowlerLine
import app.criczy.core.model.Match
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.Team

/**
 * One player's part in one finished match: every innings they batted and every spell they
 * bowled (two of each in a multi-day match). The API has no career statistics, so the player
 * page builds recent form from the scorecards of their team's latest matches.
 */
data class PlayerMatchForm(
    val match: Match,
    val opponent: Team?,
    val batting: List<BatterLine>,
    val bowling: List<BowlerLine>,
    val isPlayerOfTheMatch: Boolean,
) {
    val runs: Int get() = batting.sumOf { it.runs }
    val wickets: Int get() = bowling.sumOf { it.wickets }
}

/** Totals over a run of matches, for the line above the recent form list. */
data class FormSummary(val matches: Int, val runs: Int, val innings: Int, val notOuts: Int, val wickets: Int, val runsConceded: Int) {
    /** Batting average, or null before the player has been out. */
    val average: Double? get() = (innings - notOuts).takeIf { it > 0 }?.let { runs.toDouble() / it }

    companion object {
        fun of(form: List<PlayerMatchForm>) = FormSummary(
            matches = form.size,
            runs = form.sumOf { it.runs },
            innings = form.sumOf { it.batting.size },
            notOuts = form.sumOf { f -> f.batting.count { !it.isOut } },
            wickets = form.sumOf { it.wickets },
            runsConceded = form.sumOf { f -> f.bowling.sumOf { it.runs } },
        )
    }
}

/** What [playerId] did in [detail], or null if they were not in the side. */
fun playerMatchForm(detail: MatchDetail, playerId: Int): PlayerMatchForm? {
    val batting = detail.innings.flatMap { it.batted }.filter { it.playerId == playerId }
    val bowling = detail.innings.flatMap { it.bowlers }.filter { it.playerId == playerId }
    val player = detail.player(playerId)
    if (batting.isEmpty() && bowling.isEmpty() && (player == null || player.isSubstitute)) return null
    val teamId = player?.teamId
        ?: detail.innings.firstOrNull { innings -> innings.batters.any { it.playerId == playerId } }?.battingTeamId
        ?: detail.innings.firstOrNull { innings -> innings.bowlers.any { it.playerId == playerId } }?.bowlingTeamId
    val opponent = teamId?.let { id -> listOf(detail.match.home, detail.match.away).firstOrNull { it.id != id } }
    return PlayerMatchForm(detail.match, opponent, batting, bowling, player?.isPlayerOfTheMatch == true)
}
