package com.criczy.app.core.model

import java.time.LocalDate

enum class PlayerRole { BATTER, BOWLER, ALL_ROUNDER, WICKET_KEEPER, UNKNOWN }

data class Player(
    val id: Int,
    val name: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val teamId: Int? = null,
    val role: PlayerRole = PlayerRole.UNKNOWN,
    val roleLabel: String? = null,
    val battingHand: String? = null,
    val bowlingHand: String? = null,
    val bowlingType: String? = null,
    val dateOfBirth: LocalDate? = null,
    val birthPlace: String? = null,
    val nationality: String? = null,
    val height: String? = null,
    val imageUrl: String? = null,
    /** Plain text; any markup from the provider is stripped in the data layer. */
    val bio: String? = null,
    val didYouKnow: String? = null,
    val isCaptain: Boolean = false,
    val isWicketKeeper: Boolean = false,
    val isSubstitute: Boolean = false,
    val isPlayerOfTheMatch: Boolean = false,
    val order: Int = 0,
    /** International and domestic debuts the provider has a date for, oldest first. */
    val debuts: List<PlayerDebut> = emptyList(),
    val localClub: String? = null,
    val shirtNumber: String? = null,
    val socialLinks: List<SocialLink> = emptyList(),
) {
    val battingStyle: String? get() = battingHand?.takeIf { it.isNotBlank() }?.let { "$it-hand bat" }

    val bowlingStyle: String?
        get() {
            val type = bowlingType?.takeIf { it.isNotBlank() } ?: return null
            val arm = bowlingHand?.takeIf { it.isNotBlank() }
            return if (arm != null) "$arm-arm $type" else type
        }
}

data class PlayerDebut(val format: String, val date: LocalDate)

data class SocialLink(val label: String, val url: String)

data class BatterLine(
    val playerId: Int,
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val minutes: Int?,
    val isOut: Boolean,
    val dismissal: String?,
    val isAtCrease: Boolean,
    val isOnStrike: Boolean,
    val order: Int,
) {
    /** A batter listed in the order who has not faced a ball and is not in. */
    val didNotBat: Boolean get() = !isOut && !isAtCrease && balls == 0 && runs == 0
}

data class BowlerLine(
    val playerId: Int,
    val name: String,
    val overs: String,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Double,
    val dots: Int,
    val wides: Int,
    val noBalls: Int,
    val isBowlingNow: Boolean,
    val order: Int,
)

data class FallOfWicket(
    val order: Int,
    val playerId: Int,
    val playerName: String,
    val runs: Int,
    val over: String,
)

data class Partnership(
    val firstPlayerId: Int,
    val firstName: String,
    val firstRuns: Int,
    val firstBalls: Int,
    val secondPlayerId: Int,
    val secondName: String,
    val secondRuns: Int,
    val secondBalls: Int,
    val runs: Int,
    val balls: Int,
    val runRate: Double,
    val isCurrent: Boolean,
)

data class Extras(
    val total: Int,
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,
    val penalties: Int,
)

data class Innings(
    val number: Int,
    val battingTeamId: Int,
    val bowlingTeamId: Int,
    val score: InningsScore,
    val runRate: Double,
    val requiredRunRate: Double?,
    val ballsRemaining: Int?,
    val batters: List<BatterLine>,
    val bowlers: List<BowlerLine>,
    val fallOfWickets: List<FallOfWicket>,
    val extras: Extras,
    val currentPartnership: Partnership?,
) {
    val yetToBat: List<BatterLine> get() = batters.filter { it.didNotBat }
    val batted: List<BatterLine> get() = batters.filterNot { it.didNotBat }
}

data class Official(val name: String, val role: String)

/** Everything behind the match centre: the list-level [match] plus full innings and squads. */
data class MatchDetail(
    val match: Match,
    val innings: List<Innings>,
    val players: List<Player>,
    val officials: List<Official>,
    val oversPerInnings: Int?,
    val oversRemaining: String?,
    val ticketUrl: String?,
    val hasCommentary: Boolean,
) {
    private val playersById: Map<Int, Player> by lazy { players.associateBy { it.id } }

    fun player(id: Int): Player? = playersById[id]

    fun squad(teamId: Int): List<Player> = players.filter { it.teamId == teamId }.sortedBy { it.order }

    val currentInnings: Innings? get() = innings.maxByOrNull { it.number }

    val playersOfTheMatch: List<Player> get() = players.filter { it.isPlayerOfTheMatch }

    /** Total the side batting last must reach to win, when the match is in its final innings. */
    val target: Int?
        get() {
            val format = match.format
            val current = currentInnings ?: return null
            val isLastInnings = current.number == format.inningsPerSide * 2
            if (!isLastInnings) return null
            val chasing = innings
                .filter { it.battingTeamId == current.battingTeamId && it.number != current.number }
                .sumOf { it.score.runs }
            val setting = innings.filter { it.battingTeamId != current.battingTeamId }.sumOf { it.score.runs }
            return setting - chasing + 1
        }
}
