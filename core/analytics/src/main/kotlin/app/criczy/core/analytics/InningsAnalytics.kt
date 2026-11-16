package app.criczy.core.analytics

import app.criczy.core.model.Ball
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.Over

/** One bar of a Manhattan chart. */
data class OverBar(val over: Int, val runs: Int, val wickets: Int)

/** One point of a worm chart: the innings total at the end of [over]. */
data class WormPoint(val over: Int, val runs: Int, val wickets: Int, val wicketFellThisOver: Boolean)

data class RunRatePoint(val over: Int, val runRate: Double)

/** How the runs off the bat were made. */
data class ScoringBreakdown(
    val legalBalls: Int,
    val dots: Int,
    val singles: Int,
    val twos: Int,
    val threes: Int,
    val fours: Int,
    val sixes: Int,
    val runsOffBat: Int,
    val extras: Int,
) {
    val boundaryRuns: Int get() = fours * 4 + sixes * 6
    val dotPercent: Double get() = percent(dots, legalBalls)
    val boundaryPercent: Double get() = percent(boundaryRuns, runsOffBat)
}

/** A stretch of the innings such as the powerplay. Overs are inclusive and 1-based. */
data class PhaseSummary(
    val name: String,
    val fromOver: Int,
    val toOver: Int,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
) {
    val runRate: Double get() = if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
}

data class PartnershipStand(
    val wicket: Int,
    val batterIds: Pair<Int, Int>,
    val runs: Int,
    val balls: Int,
    /** Runs off the bat by each batter, in the order of [batterIds]. */
    val contributions: Pair<Int, Int>,
    val unbroken: Boolean,
)

/** One batter against one bowler. */
data class Matchup(
    val batterId: Int,
    val bowlerId: Int,
    val balls: Int,
    val runs: Int,
    val dots: Int,
    val fours: Int,
    val sixes: Int,
    val dismissals: Int,
) {
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
}

private fun percent(part: Int, whole: Int): Double = if (whole == 0) 0.0 else part * 100.0 / whole

/**
 * Charts and breakdowns derived from ball-by-ball data. Everything here is computed from what
 * was actually bowled; nothing is estimated. Pure functions, so results are cheap to cache.
 */
object InningsAnalytics {

    fun manhattan(innings: InningsBallByBall): List<OverBar> =
        innings.overs.map { OverBar(it.number, it.runs, it.wickets) }

    fun worm(innings: InningsBallByBall): List<WormPoint> =
        innings.overs.map { WormPoint(it.number, it.inningsRuns, it.inningsWickets, it.wickets > 0) }

    /** Cumulative run rate after each over, from legal balls actually bowled. */
    fun runRateProgression(innings: InningsBallByBall): List<RunRatePoint> {
        var balls = 0
        return innings.overs.map { over ->
            balls += over.legalBalls
            RunRatePoint(over.number, if (balls == 0) 0.0 else over.inningsRuns * 6.0 / balls)
        }
    }

    fun scoring(balls: List<Ball>): ScoringBreakdown {
        val faced = balls.filter { !it.isWide }
        // Byes and leg byes are not off the bat even though the ball was faced.
        fun offBat(ball: Ball) = if (ball.isBye || ball.isLegBye) 0 else ball.batterRuns
        val runsOffBat = faced.sumOf(::offBat)
        return ScoringBreakdown(
            legalBalls = balls.count { it.isLegal },
            dots = balls.count { it.isLegal && it.teamRuns == 0 },
            singles = faced.count { offBat(it) == 1 },
            twos = faced.count { offBat(it) == 2 },
            threes = faced.count { offBat(it) == 3 },
            fours = faced.count { offBat(it) == 4 && it.isBoundary },
            sixes = faced.count { offBat(it) == 6 },
            runsOffBat = runsOffBat,
            extras = balls.sumOf { it.teamRuns } - runsOffBat,
        )
    }

    /**
     * Splits the innings into phases for the format's over limit. Formats without a limit
     * (Tests, first-class) return an empty list because phases have no meaning there.
     */
    fun phases(innings: InningsBallByBall, oversPerInnings: Int?): List<PhaseSummary> {
        val ranges = when (oversPerInnings) {
            20 -> listOf(Triple("Powerplay", 1, 6), Triple("Middle", 7, 15), Triple("Death", 16, 20))
            50 -> listOf(Triple("Powerplay", 1, 10), Triple("Middle", 11, 40), Triple("Death", 41, 50))
            else -> return emptyList()
        }
        return ranges.mapNotNull { (name, from, to) ->
            val overs = innings.overs.filter { it.number in from..to }
            if (overs.isEmpty()) return@mapNotNull null
            PhaseSummary(name, from, to, overs.sumOf(Over::runs), overs.sumOf(Over::wickets), overs.sumOf(Over::legalBalls))
        }
    }

    /**
     * Rebuilds every partnership of the innings. Requires complete data: with overs missing,
     * stands would be silently merged, so an incomplete innings returns an empty list.
     */
    fun partnerships(innings: InningsBallByBall): List<PartnershipStand> {
        if (!innings.isComplete) return emptyList()
        val stands = mutableListOf<PartnershipStand>()
        var pair: Pair<Int, Int>? = null
        var runs = 0
        var balls = 0
        var first = 0
        var second = 0

        fun close(unbroken: Boolean) {
            val current = pair ?: return
            stands += PartnershipStand(stands.size + 1, current, runs, balls, first to second, unbroken)
            pair = null; runs = 0; balls = 0; first = 0; second = 0
        }

        for (ball in innings.balls) {
            if (ball.batterId == 0) continue
            val current = pair
            val atCrease = setOf(ball.batterId, ball.nonStrikerId)
            if (current == null) {
                pair = ball.batterId to ball.nonStrikerId
            } else if (setOf(current.first, current.second) != atCrease) {
                // A batter changed without a wicket ball in between (retired hurt, or a run out
                // recorded on the previous delivery): start a new stand rather than mix them.
                close(unbroken = false)
                pair = ball.batterId to ball.nonStrikerId
            }
            val active = pair!!
            runs += ball.teamRuns
            if (ball.isLegal) balls++
            if (!ball.isBye && !ball.isLegBye) {
                if (ball.batterId == active.first) first += ball.batterRuns else second += ball.batterRuns
            }
            if (ball.isWicket) close(unbroken = false)
        }
        close(unbroken = true)
        return stands
    }

    fun matchups(balls: List<Ball>): List<Matchup> =
        balls.filter { it.batterId != 0 && it.bowlerId != 0 }
            .groupBy { it.batterId to it.bowlerId }
            .map { (ids, faced) ->
                val counted = faced.filter { !it.isWide }
                Matchup(
                    batterId = ids.first,
                    bowlerId = ids.second,
                    balls = counted.size,
                    runs = counted.sumOf { if (it.isBye || it.isLegBye) 0 else it.batterRuns },
                    dots = counted.count { it.teamRuns == 0 },
                    fours = counted.count { it.batterRuns == 4 && it.isBoundary },
                    sixes = counted.count { it.batterRuns == 6 },
                    // Run outs are not credited to the bowler.
                    dismissals = faced.count { it.isWicket && it.dismissalType?.startsWith("Run out") != true },
                )
            }
            .sortedByDescending { it.balls }

    /**
     * Projected total at the current rate and at fixed rates, for limited-overs first innings.
     * Returns null when the innings has no over limit or has finished.
     */
    fun projectedTotals(runs: Int, legalBalls: Int, oversPerInnings: Int?): Map<String, Int>? {
        val totalBalls = (oversPerInnings ?: return null) * 6
        val remaining = totalBalls - legalBalls
        if (legalBalls < 6 || remaining <= 0) return null
        val currentRate = runs * 6.0 / legalBalls
        val rates = linkedMapOf("Current rate" to currentRate)
        val step = if (oversPerInnings <= 20) 2 else 1
        val base = currentRate.toInt()
        for (rate in listOf(base, base + step, base + 2 * step)) {
            if (rate > 0) rates["At $rate an over"] = rate.toDouble()
        }
        return rates.mapValues { (_, rate) -> runs + Math.round(rate * remaining / 6.0).toInt() }
    }
}
