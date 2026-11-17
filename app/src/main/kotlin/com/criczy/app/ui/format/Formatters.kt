package com.criczy.app.ui.format

import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All user-facing date, time and number formatting, in the device's zone and locale. */
object Formatters {
    private fun time(locale: Locale) = DateTimeFormatter.ofPattern("h:mm a", locale)
    private fun dayMonth(locale: Locale) = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private fun full(locale: Locale) = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", locale)
    private fun date(locale: Locale) = DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    fun matchTime(instant: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        instant ?: return "Time TBC"
        val local = instant.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val clock = local.format(time(locale))
        return when (local.toLocalDate()) {
            today -> "Today, $clock"
            today.plusDays(1) -> "Tomorrow, $clock"
            today.minusDays(1) -> "Yesterday, $clock"
            else -> "${local.format(dayMonth(locale))}, $clock"
        }
    }

    fun fullDateTime(instant: Instant?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        instant?.atZone(zone)?.format(full(locale)) ?: "To be confirmed"

    fun date(instant: Instant?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? =
        instant?.atZone(zone)?.format(date(locale))

    fun dateRange(start: Instant?, end: Instant?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? {
        val from = date(start, zone, locale) ?: return null
        val to = date(end, zone, locale) ?: return from
        return if (from == to) from else "$from – $to"
    }

    /** "in 3h 20m", "in 2 days"; null once the start time has passed. */
    fun countdown(start: Instant?, now: Instant = Instant.now()): String? {
        start ?: return null
        val gap = Duration.between(now, start)
        if (gap.isNegative) return null
        val minutes = gap.toMinutes()
        return when {
            minutes < 1 -> "Starting now"
            minutes < 60 -> "in ${minutes}m"
            minutes < 24 * 60 -> "in ${minutes / 60}h ${minutes % 60}m"
            else -> (minutes / (24 * 60)).let { "in $it day${if (it == 1L) "" else "s"}" }
        }
    }

    fun ago(instant: Instant?, now: Instant = Instant.now()): String? {
        instant ?: return null
        val minutes = Duration.between(instant, now).toMinutes()
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
            else -> date(instant)
        }
    }

    fun updatedAgo(epochMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
        epochMs ?: return null
        val seconds = (nowMs - epochMs) / 1000
        return when {
            seconds < 10 -> "Updated just now"
            seconds < 60 -> "Updated ${seconds}s ago"
            seconds < 3600 -> "Updated ${seconds / 60}m ago"
            else -> "Updated ${seconds / 3600}h ago"
        }
    }

    fun age(dateOfBirth: LocalDate?, today: LocalDate = LocalDate.now()): Int? =
        dateOfBirth?.let { Period.between(it, today).years }?.takeIf { it in 10..80 }

    fun birthDate(dateOfBirth: LocalDate?, locale: Locale = Locale.getDefault()): String? =
        dateOfBirth?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))

    fun rate(value: Double): String = String.format(Locale.US, "%.2f", value)
    fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun duration(seconds: Int?): String? = seconds?.let { "%d:%02d".format(Locale.US, it / 60, it % 60) }

    /** The status line under a match: result, live status, or when it starts. */
    fun statusLine(match: Match, now: Instant = Instant.now()): String = when (match.state) {
        MatchState.UPCOMING -> matchTime(match.start, now)
        else -> match.statusText ?: when (match.state) {
            MatchState.LIVE -> "Live"
            MatchState.IN_PROGRESS -> "In progress"
            else -> "Finished"
        }
    }

    /** Plain-text scorecard summary for sharing. */
    fun shareText(match: Match): String = buildString {
        append(match.home.name).append(" v ").append(match.away.name).append('\n')
        append(listOf(match.name, match.competition.name).filter { it.isNotBlank() }.joinToString(" · ")).append('\n')
        for (team in listOf(match.home, match.away)) {
            val scores = match.inningsFor(team.id)
            if (scores.isNotEmpty()) {
                append(team.shortName).append("  ")
                append(scores.joinToString(" & ") { "${it.scoreText} (${it.overs})" }).append('\n')
            }
        }
        append(statusLine(match))
    }
}
