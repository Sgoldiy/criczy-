package app.criczy.feature.match

import app.criczy.core.model.Ball

/** Narrows the commentary to the moments people look for. Applies to the overs loaded so far. */
enum class CommentaryFilter(val label: String) {
    ALL("All"),
    WICKETS("Wickets"),
    BOUNDARIES("Boundaries");

    fun accepts(ball: Ball): Boolean = when (this) {
        ALL -> true
        WICKETS -> ball.isWicket
        BOUNDARIES -> ball.batterRuns == 6 || (ball.batterRuns == 4 && ball.isBoundary)
    }
}

/** One ball's commentary as a heading ("Bowler to Batter") and what happened. */
internal data class CommentaryLines(val heading: String, val body: String)

/** The outcome words the feed puts in front of a message; the ball chip and link already say them. */
private val outcomePrefixes = listOf("FOUR!", "SIX!", "OUT!", "Wide.", "No ball.")

/**
 * Splits a feed message such as "FOUR! Bowler to Batter. Full, off stump, driven for 4 runs"
 * into the heading "Bowler to Batter" and the description. Only splits when the names are the
 * bowler's and batter's from the scorecard, so a message in any other shape is left whole (null).
 */
internal fun commentaryLines(message: String, bowler: String?, batter: String?): CommentaryLines? {
    if (bowler.isNullOrBlank() || batter.isNullOrBlank()) return null
    var text = message.trim()
    outcomePrefixes.firstOrNull { text.startsWith(it, ignoreCase = true) }?.let { text = text.substring(it.length).trimStart() }
    val heading = "$bowler to $batter"
    if (!text.startsWith(heading)) return null
    val body = text.substring(heading.length).trimStart('.', ',', ' ')
    return CommentaryLines(heading, body.replaceFirstChar { it.uppercaseChar() })
}

/**
 * Where the closing "Batter Caught for 42." sentence of a wicket's description starts, so it can
 * be set apart; -1 when the description does not end that way.
 */
internal fun dismissalSentenceStart(body: String): Int {
    val trimmed = body.trimEnd()
    if (!Regex(""" for \d+\.?$""").containsMatchIn(trimmed)) return -1
    val stop = trimmed.dropLast(1).lastIndexOf(". ")
    return if (stop >= 0) stop + 2 else -1
}

/** A wicket, four or six worth going back to, with the key of its row in the commentary. */
internal data class KeyMoment(val key: String, val ball: Ball)

/** The latest wickets and boundaries in [overs] (newest first, as the feed holds them). */
internal fun keyMoments(overs: List<app.criczy.core.model.Over>, limit: Int = 5): List<KeyMoment> =
    overs.asSequence()
        .flatMap { over -> over.balls.indices.reversed().asSequence().map { index -> over to index } }
        .filter { (over, index) -> over.balls[index].let { it.isWicket || CommentaryFilter.BOUNDARIES.accepts(it) } }
        .take(limit)
        .map { (over, index) -> KeyMoment("ball-${over.number}-$index", over.balls[index]) }
        .toList()

/**
 * Where the row with [key] sits in the full commentary list: each over's row, then its balls
 * newest first. Null when that ball or over is not loaded.
 */
internal fun feedIndexOf(key: String, overs: List<app.criczy.core.model.Over>): Int? {
    var index = 0
    for (over in overs) {
        if (key == "over-${over.number}") return index
        index++
        val last = over.balls.lastIndex
        for (i in 0..last) {
            if (key == "ball-${over.number}-${last - i}") return index
            index++
        }
    }
    return null
}

/** "50", "100", "150"… for a batter past a milestone; null below fifty. */
internal fun battingMilestone(runs: Int): String? = if (runs >= 50) "${runs / 50 * 50}" else null

/** "4W", "5W"… for a bowler with a haul worth marking; null below four wickets. */
internal fun bowlingHaul(wickets: Int): String? = if (wickets >= 4) "${wickets}W" else null

/** A player's figures in the match: "33 (42) · 2/17 (4)", from every innings they batted or bowled in. */
internal fun matchFigures(detail: app.criczy.core.model.MatchDetail, playerId: Int): String? {
    val batting = detail.innings.flatMap { it.batted }.filter { it.playerId == playerId }
        .map { "${it.runs}${if (it.isOut) "" else "*"} (${it.balls})" }
    val bowling = detail.innings.flatMap { it.bowlers }.filter { it.playerId == playerId }
        .map { "${it.wickets}/${it.runs} (${it.overs})" }
    return (batting + bowling).joinToString(" · ").ifEmpty { null }
}
