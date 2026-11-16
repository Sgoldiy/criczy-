package app.criczy.core.model

import java.time.Instant

/** Where a match is in its life. Derived once in the data layer so the UI never guesses. */
enum class MatchState {
    UPCOMING,

    /** Ball is in play right now. */
    LIVE,

    /** Started but not currently in play: stumps, innings break, rain delay. */
    IN_PROGRESS,
    COMPLETED,
}

enum class MatchFormat(val label: String, val inningsPerSide: Int) {
    TEST("Test", 2),
    FIRST_CLASS("First class", 2),
    ODI("ODI", 1),
    ONE_DAY("One day", 1),
    T20I("T20I", 1),
    T20("T20", 1),
    HUNDRED("100-ball", 1),
    OTHER("Match", 1);

    val isMultiDay: Boolean get() = inningsPerSide == 2
}

data class Team(
    val id: Int,
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    /** Brand colour as `#RRGGBB`, when the provider supplies one. */
    val colorHex: String?,
    val bannerUrl: String? = null,
    val isWomen: Boolean = false,
)

data class Competition(
    val id: Int,
    val name: String,
    val logoUrl: String?,
    val bannerUrl: String? = null,
    val start: Instant? = null,
    val end: Instant? = null,
    val isWomen: Boolean = false,
    val formatLabel: String? = null,
    val tourName: String? = null,
    val hasStandings: Boolean? = null,
)

data class Venue(
    val id: Int,
    val name: String,
    val city: String?,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val displayName: String
        get() = if (city.isNullOrBlank() || name.contains(city, ignoreCase = true)) name else "$name, $city"
}

/** One innings line as shown on a match card: `339 (114.5)` or `143/5 (44.0)`. */
data class InningsScore(
    val inningsNumber: Int,
    val battingTeamId: Int,
    val runs: Int,
    val wickets: Int,
    val overs: String,
    val isDeclared: Boolean = false,
    val isFollowOn: Boolean = false,
) {
    val isAllOut: Boolean get() = wickets >= 10

    /** `339`, `143/5` or `295/9d`. */
    val scoreText: String
        get() = buildString {
            append(runs)
            if (!isAllOut) append('/').append(wickets)
            if (isDeclared) append('d')
        }
}

/** A match as it appears in lists: enough for a card, a widget or a notification. */
data class Match(
    val id: Int,
    val name: String,
    val competition: Competition,
    val home: Team,
    val away: Team,
    val venue: Venue?,
    val start: Instant?,
    val end: Instant?,
    val state: MatchState,
    val format: MatchFormat,
    val formatLabel: String,
    val innings: List<InningsScore>,
    /** Result, or live status such as "Stumps: Afghanistan require 275 runs". */
    val statusText: String?,
    val tossText: String? = null,
    val winnerTeamId: Int? = null,
    val day: Int? = null,
    val numberOfDays: Int? = null,
) {
    fun team(id: Int): Team? = when (id) {
        home.id -> home
        away.id -> away
        else -> null
    }

    fun inningsFor(teamId: Int): List<InningsScore> = innings.filter { it.battingTeamId == teamId }

    val currentInnings: InningsScore? get() = innings.maxByOrNull { it.inningsNumber }

    val title: String get() = "${home.shortName} v ${away.shortName}"
}

/** The three home buckets, as one consistent snapshot. */
data class MatchBoard(
    val live: List<Match>,
    val upcoming: List<Match>,
    val completed: List<Match>,
) {
    val isEmpty: Boolean get() = live.isEmpty() && upcoming.isEmpty() && completed.isEmpty()
    val all: List<Match> get() = live + upcoming + completed

    companion object {
        val Empty = MatchBoard(emptyList(), emptyList(), emptyList())
    }
}
