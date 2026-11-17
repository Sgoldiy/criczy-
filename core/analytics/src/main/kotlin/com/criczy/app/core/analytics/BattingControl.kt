package com.criczy.app.core.analytics

import com.criczy.app.core.model.Ball

enum class BowlerKind { PACE, SPIN }

/** Runs, balls and wickets against one kind of bowling. */
data class Split(val runs: Int = 0, val balls: Int = 0, val wickets: Int = 0) {
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
    operator fun plus(other: Split) = Split(runs + other.runs, balls + other.balls, wickets + other.wickets)
}

/**
 * How cleanly a batter struck the ball: of the balls where the provider judged the contact
 * (everything except leaves and untracked balls), the share that was well timed or middled.
 */
data class BatterControl(
    val batterId: Int,
    val ballsFaced: Int,
    val judged: Int,
    val controlled: Int,
    val pace: Split,
    val spin: Split,
) {
    val controlPercent: Double? get() = if (judged == 0) null else controlled * 100.0 / judged
}

object BattingControl {
    private val controlledShots = setOf("welltimed", "middled", "defended")
    private val notJudged = setOf("left", "none", "null", "")

    /** Pace or spin from a player's bowling type ("Pace", "Off Spin", "Orthodox", "Right-arm fast"). */
    fun kindOf(bowlingType: String?): BowlerKind? {
        val type = bowlingType?.lowercase() ?: return null
        return when {
            listOf("spin", "orthodox", "unorthodox", "chinaman", "googly").any { it in type } -> BowlerKind.SPIN
            listOf("pace", "fast", "medium", "seam", "swing").any { it in type } -> BowlerKind.PACE
            else -> null
        }
    }

    private fun key(connection: String?) = connection?.lowercase()?.filter { it.isLetter() }.orEmpty()

    private fun split(ball: Ball) = Split(
        runs = if (ball.isBye || ball.isLegBye) 0 else ball.batterRuns,
        balls = if (ball.isWide) 0 else 1,
        wickets = if (ball.isWicket && ball.dismissalType?.contains("run out", ignoreCase = true) != true) 1 else 0,
    )

    /** One line per batter who faced a ball, most balls first. [kind] gives each bowler's kind. */
    fun batters(balls: List<Ball>, kind: (Int) -> BowlerKind?): List<BatterControl> =
        balls.filter { it.batterId != 0 && !it.isWide }.groupBy { it.batterId }.map { (id, faced) ->
            val judged = faced.filter { key(it.connection) !in notJudged }
            BatterControl(
                batterId = id,
                ballsFaced = faced.size,
                judged = judged.size,
                controlled = judged.count { key(it.connection) in controlledShots },
                pace = faced.filter { kind(it.bowlerId) == BowlerKind.PACE }.fold(Split()) { acc, b -> acc + split(b) },
                spin = faced.filter { kind(it.bowlerId) == BowlerKind.SPIN }.fold(Split()) { acc, b -> acc + split(b) },
            )
        }.sortedByDescending { it.ballsFaced }

    /** The batting side against pace and against spin, extras off the bat excluded. */
    fun innings(balls: List<Ball>, kind: (Int) -> BowlerKind?): Map<BowlerKind, Split> =
        BowlerKind.entries.associateWith { k -> balls.filter { kind(it.bowlerId) == k }.fold(Split()) { acc, b -> acc + split(b) } }
}
