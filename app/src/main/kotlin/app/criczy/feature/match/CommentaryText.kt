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
