package com.criczy.app.core.analytics

import com.criczy.app.core.model.MatchDetail

data class BattingAggregate(
    val playerId: Int,
    val name: String,
    val teamId: Int,
    val innings: Int,
    val notOuts: Int,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val highest: Int,
    val highestNotOut: Boolean,
    val fifties: Int,
    val hundreds: Int,
) {
    /** Null when the batter has never been dismissed, which has no average. */
    val average: Double? get() = (innings - notOuts).takeIf { it > 0 }?.let { runs.toDouble() / it }
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
}

data class BowlingAggregate(
    val playerId: Int,
    val name: String,
    val teamId: Int,
    val innings: Int,
    val balls: Int,
    val runs: Int,
    val wickets: Int,
    val maidens: Int,
    val bestWickets: Int,
    val bestRuns: Int,
) {
    val overs: String get() = "${balls / 6}.${balls % 6}"
    val economy: Double get() = if (balls == 0) 0.0 else runs * 6.0 / balls
    val average: Double? get() = wickets.takeIf { it > 0 }?.let { runs.toDouble() / it }
    val best: String get() = "$bestWickets/$bestRuns"
}

data class Leaderboard(
    val matches: Int,
    val batting: List<BattingAggregate>,
    val bowling: List<BowlingAggregate>,
) {
    val mostRuns: List<BattingAggregate> get() = batting.sortedByDescending { it.runs }
    val mostWickets: List<BowlingAggregate>
        get() = bowling.filter { it.wickets > 0 }.sortedWith(compareByDescending<BowlingAggregate> { it.wickets }.thenBy { it.economy })
    val mostSixes: List<BattingAggregate> get() = batting.filter { it.sixes > 0 }.sortedByDescending { it.sixes }
}

/** `"4"`, `"3.2"` or `"114.5"` as a number of legal balls. */
fun oversToBalls(overs: String): Int {
    val whole = overs.substringBefore('.').toIntOrNull() ?: 0
    val part = overs.substringAfter('.', "0").take(1).toIntOrNull() ?: 0
    return whole * 6 + part.coerceIn(0, 5)
}

/**
 * Aggregates player figures across scorecards, for tournament leaderboards and for comparing
 * players over the matches the app has data for. These are totals of the supplied matches
 * only, never career figures.
 */
object Leaderboards {

    fun compute(matches: Collection<MatchDetail>): Leaderboard {
        val batting = HashMap<Int, BattingAggregate>()
        val bowling = HashMap<Int, BowlingAggregate>()
        for (match in matches.distinctBy { it.match.id }) {
            for (innings in match.innings) {
                for (line in innings.batted) {
                    val prev = batting[line.playerId]
                    val beatsHighest = prev == null || line.runs > prev.highest ||
                        (line.runs == prev.highest && !line.isOut)
                    batting[line.playerId] = BattingAggregate(
                        playerId = line.playerId,
                        name = line.name,
                        teamId = innings.battingTeamId,
                        innings = (prev?.innings ?: 0) + 1,
                        notOuts = (prev?.notOuts ?: 0) + if (line.isOut) 0 else 1,
                        runs = (prev?.runs ?: 0) + line.runs,
                        balls = (prev?.balls ?: 0) + line.balls,
                        fours = (prev?.fours ?: 0) + line.fours,
                        sixes = (prev?.sixes ?: 0) + line.sixes,
                        highest = if (beatsHighest) line.runs else prev!!.highest,
                        highestNotOut = if (beatsHighest) !line.isOut else prev!!.highestNotOut,
                        fifties = (prev?.fifties ?: 0) + if (line.runs in 50..99) 1 else 0,
                        hundreds = (prev?.hundreds ?: 0) + if (line.runs >= 100) 1 else 0,
                    )
                }
                for (line in innings.bowlers) {
                    val prev = bowling[line.playerId]
                    val better = prev == null || line.wickets > prev.bestWickets ||
                        (line.wickets == prev.bestWickets && line.runs < prev.bestRuns)
                    bowling[line.playerId] = BowlingAggregate(
                        playerId = line.playerId,
                        name = line.name,
                        teamId = innings.bowlingTeamId,
                        innings = (prev?.innings ?: 0) + 1,
                        balls = (prev?.balls ?: 0) + oversToBalls(line.overs),
                        runs = (prev?.runs ?: 0) + line.runs,
                        wickets = (prev?.wickets ?: 0) + line.wickets,
                        maidens = (prev?.maidens ?: 0) + line.maidens,
                        bestWickets = if (better) line.wickets else prev!!.bestWickets,
                        bestRuns = if (better) line.runs else prev!!.bestRuns,
                    )
                }
            }
        }
        return Leaderboard(matches.distinctBy { it.match.id }.size, batting.values.toList(), bowling.values.toList())
    }
}
