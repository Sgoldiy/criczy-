package app.criczy.core.model

import java.time.Instant

enum class BallOutcome { DOT, RUNS, FOUR, SIX, WICKET, WIDE, NO_BALL, BYE, LEG_BYE }

/** One delivery, with the tracking data the provider records for it. */
data class Ball(
    val id: Long,
    val overNumber: Int,
    /** Position within the over as bowled, counting extras (1-based). */
    val ballNumber: Int,
    val time: Instant?,
    val batterId: Int,
    val nonStrikerId: Int,
    val bowlerId: Int,
    /** Runs off the bat. */
    val batterRuns: Int,
    /** Everything the batting side gained from this delivery. */
    val teamRuns: Int,
    /** Runs charged to the bowler. */
    val bowlerRuns: Int,
    val isWicket: Boolean,
    val dismissalType: String?,
    val isWide: Boolean,
    val isNoBall: Boolean,
    val isBye: Boolean,
    val isLegBye: Boolean,
    val isBoundary: Boolean,
    val isFreeHit: Boolean,
    val isPowerPlay: Boolean,
    /** Degrees, as supplied by the provider; see `WagonWheel` for the orientation. */
    val shotAngle: Int?,
    /** Distance the ball travelled, in the provider's units. */
    val shotMagnitude: Int?,
    val battingHand: String?,
    val line: String?,
    val length: String?,
    val shot: String?,
    val connection: String?,
    val footwork: String?,
    val fieldingPosition: String?,
    val bowlingDetail: String?,
    val inTheAir: Boolean,
    val commentary: List<Comment>,
    /** "Right" or "Left": the bowler's arm. */
    val bowlingArm: String? = null,
    /** "Over" or "Round" the wicket. */
    val bowlingFrom: String? = null,
) {
    /** Counts towards the six legal balls of an over. */
    val isLegal: Boolean get() = !isWide && !isNoBall

    val outcome: BallOutcome
        get() = when {
            isWicket -> BallOutcome.WICKET
            isWide -> BallOutcome.WIDE
            isNoBall -> BallOutcome.NO_BALL
            batterRuns == 6 -> BallOutcome.SIX
            batterRuns == 4 && isBoundary -> BallOutcome.FOUR
            isLegBye -> BallOutcome.LEG_BYE
            isBye -> BallOutcome.BYE
            teamRuns == 0 -> BallOutcome.DOT
            else -> BallOutcome.RUNS
        }

    /** Short scorer's notation: `W`, `4`, `1wd`, `2lb`, `•`. */
    val symbol: String
        get() = when (outcome) {
            BallOutcome.WICKET -> if (teamRuns > 0) "W+$teamRuns" else "W"
            BallOutcome.WIDE -> if (teamRuns > 1) "${teamRuns}wd" else "wd"
            BallOutcome.NO_BALL -> if (teamRuns > 1) "${teamRuns}nb" else "nb"
            BallOutcome.LEG_BYE -> "${teamRuns}lb"
            BallOutcome.BYE -> "${teamRuns}b"
            BallOutcome.DOT -> "•"
            else -> teamRuns.toString()
        }
}

data class Comment(val type: String, val text: String, val order: Int) {
    val isEndOfOver: Boolean get() = type == "EndOfOver"
}

data class Over(
    val number: Int,
    val runs: Int,
    val wickets: Int,
    val extras: Int,
    /** Innings total at the end of this over. */
    val inningsRuns: Int,
    val inningsWickets: Int,
    val runRate: Double,
    /** In bowling order. */
    val balls: List<Ball>,
) {
    val legalBalls: Int get() = balls.count { it.isLegal }
    val isComplete: Boolean get() = legalBalls >= 6
    val isMaiden: Boolean get() = isComplete && balls.all { it.bowlerRuns == 0 }
    val bowlerId: Int? get() = balls.firstOrNull()?.bowlerId
}

/** Ball-by-ball data for one innings. [overs] is ascending by over number. */
data class InningsBallByBall(
    val matchId: Int,
    val inningsNumber: Int,
    val battingTeamId: Int,
    val bowlingTeamId: Int,
    val overs: List<Over>,
    /** True when every over bowled so far is present, so derived charts are complete. */
    val isComplete: Boolean,
    /** Over number to pass as the cursor for the next (older) page, if there is one. */
    val olderThanOver: Int?,
) {
    val balls: List<Ball> get() = overs.flatMap { it.balls }
}
