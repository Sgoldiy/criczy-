package com.criczy.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.criczy.app.R
import com.criczy.app.core.model.Ball
import com.criczy.app.core.model.BallOutcome
import com.criczy.app.core.model.BatterLine
import com.criczy.app.core.model.BowlerLine
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchState
import com.criczy.app.core.model.Team
import com.criczy.app.ui.format.Formatters
import com.criczy.app.ui.theme.parseTeamColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a tap goes: [open] for the whole widget, [refresh] for the refresh button. Null in previews and tests. */
data class WidgetActions(val open: Action? = null, val refresh: Action? = null)

private object Palette {
    val text = Color.White
    val soft = Color(0xFFC9D2E0)
    val muted = Color(0xFF8D99AD)
    val amber = Color(0xFFFFB627)
    val live = Color(0xFFFF5449)
    val divider = Color(0x1FFFFFFF)
    /** The middle of the card's gradient, for blending. */
    val card = Color(0xFF131C2D)
    val four = Color(0xFF58A6FF)
    val six = Color(0xFFC792FF)
    val wicket = Color(0xFFFF6B5E)
    val extra = Color(0xFF6FD6C4)
    val dot = Color(0xFF55627A)
    val teams = listOf(Color(0xFF2557D6), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF6A1B9A), Color(0xFF00838F), Color(0xFFEF6C00))
}

/** The system's condensed sans (Roboto Condensed): the closest a widget can get to the app's score font. */
private val Condensed = FontFamily("sans-serif-condensed")

private fun cp(color: Color) = ColorProvider(color)

/**
 * [color] at [alpha] over the card, as an opaque colour. A widget tints its white shapes with SRC_ATOP,
 * which turns a translucent tint nearly white, so soft fills are blended here instead.
 */
private fun over(color: Color, alpha: Float, card: Color = Palette.card): Color =
    Color(red = color.red * alpha + card.red * (1 - alpha), green = color.green * alpha + card.green * (1 - alpha), blue = color.blue * alpha + card.blue * (1 - alpha))

private fun style(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal, condensed: Boolean = false) =
    TextStyle(color = cp(color), fontSize = size, fontWeight = weight, fontFamily = if (condensed) Condensed else null)

private fun teamColor(team: Team): Color = parseTeamColor(team.colorHex) ?: Palette.teams[Math.floorMod(team.id, Palette.teams.size)]

// ------------------------------------------------------------------ the three widgets

/** Small: just the score. Two lines, one per side; the side batting now is marked. */
@Composable
fun ScoreWidgetContent(snapshot: WidgetSnapshot?, actions: WidgetActions, now: Instant = Instant.now()) {
    val size = LocalSize.current
    val match = snapshot?.match
    val roomy = size.height >= 56.dp
    val narrow = size.width < 170.dp
    WidgetSurface(actions, horizontal = if (roomy) 14.dp else 12.dp, vertical = if (roomy) 6.dp else 2.dp) {
        when {
            match == null -> EmptyState(compact = true)
            match.state == MatchState.UPCOMING -> {
                Text(match.title, style = style(Palette.text, if (roomy) 15.sp else 13.sp, FontWeight.Bold, condensed = true), maxLines = 1)
                Text(Formatters.statusLine(match, now), style = style(Palette.muted, 11.sp), maxLines = 1)
            }
            else -> {
                ScoreLine(match, match.home, roomy = roomy, narrow = narrow)
                if (roomy) Spacer(GlanceModifier.height(4.dp))
                ScoreLine(match, match.away, roomy = roomy, narrow = narrow)
                if (size.height >= 100.dp) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(Formatters.statusLine(match, now), style = style(statusColor(match), 11.sp), maxLines = 1)
                }
            }
        }
    }
}

/** Medium: the score with the match situation, as on a match card. */
@Composable
fun LiveScoreWidgetContent(snapshot: WidgetSnapshot?, refreshing: Boolean, actions: WidgetActions, now: Instant = Instant.now()) {
    val size = LocalSize.current
    val match = snapshot?.match
    val tall = size.height >= 150.dp
    val fullName = size.width >= 280.dp
    val overs = size.width >= 250.dp
    WidgetSurface(actions, horizontal = 14.dp, vertical = if (tall) 12.dp else 9.dp) {
        if (match == null) {
            EmptyState(compact = false)
            return@WidgetSurface
        }
        Header(match)
        Spacer(GlanceModifier.height(if (tall) 10.dp else 5.dp))
        TeamRow(match, match.home, badge = if (tall) 26.dp else 22.dp, scoreSize = if (tall) 19.sp else 18.sp, fullName = fullName, overs = overs)
        Spacer(GlanceModifier.height(if (tall) 6.dp else 3.dp))
        TeamRow(match, match.away, badge = if (tall) 26.dp else 22.dp, scoreSize = if (tall) 19.sp else 18.sp, fullName = fullName, overs = overs)
        Spacer(GlanceModifier.height(if (tall) 10.dp else 5.dp))
        Text(situation(match, now), style = style(statusColor(match), 12.sp), maxLines = if (tall) 2 else 1)
        if (tall) {
            Spacer(GlanceModifier.defaultWeight())
            Footer(snapshot.updatedAt, refreshing, actions, now, detail = null)
        }
    }
}

/**
 * Large: the score plus who is batting and bowling, this over, and the run rates.
 * Short (under 240dp): score and batters. Regular: plus the situation and the bowler. Tall (285dp up): plus this over.
 */
@Composable
fun MatchCentreWidgetContent(snapshot: WidgetSnapshot?, refreshing: Boolean, actions: WidgetActions, now: Instant = Instant.now()) {
    val size = LocalSize.current
    val match = snapshot?.match
    val short = size.height < 240.dp
    val tall = size.height >= 285.dp
    val fullName = size.width >= 300.dp
    WidgetSurface(actions, horizontal = 14.dp, vertical = if (short) 9.dp else 12.dp) {
        if (match == null) {
            EmptyState(compact = false)
            return@WidgetSurface
        }
        val centre = snapshot.centre
        Header(match)
        Spacer(GlanceModifier.height(if (short) 5.dp else 8.dp))
        TeamRow(match, match.home, badge = if (short) 22.dp else 24.dp, scoreSize = if (short) 17.sp else 18.sp, fullName = fullName, overs = true)
        Spacer(GlanceModifier.height(if (short) 3.dp else 5.dp))
        TeamRow(match, match.away, badge = if (short) 22.dp else 24.dp, scoreSize = if (short) 17.sp else 18.sp, fullName = fullName, overs = true)
        // Short: the batters say more than the status line, which the footer's rates partly repeat.
        if (!short || centre?.batters.isNullOrEmpty()) {
            Spacer(GlanceModifier.height(if (short) 4.dp else 7.dp))
            // The venue gets its own row below, so the status line keeps to the start time.
            Text(Formatters.statusLine(match, now), style = style(statusColor(match), 12.sp), maxLines = if (tall) 2 else 1)
        }
        Details(match, centre, gap = if (short) 6.dp else 9.dp, bowler = !short, over = tall)
        Spacer(GlanceModifier.defaultWeight())
        Footer(snapshot.updatedAt, refreshing, actions, now, detail = footerDetail(match, centre))
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun WidgetSurface(actions: WidgetActions, horizontal: Dp, vertical: Dp, content: @Composable ColumnScope.() -> Unit) {
    var modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_background))
    actions.open?.let { modifier = modifier.clickable(it) }
    Column(modifier.padding(horizontal = horizontal, vertical = vertical), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun EmptyState(compact: Boolean) {
    Text("CRICZY", style = style(Palette.amber, if (compact) 14.sp else 16.sp, FontWeight.Bold, condensed = true))
    Spacer(GlanceModifier.height(2.dp))
    Text(if (compact) "No match right now" else "No match to show right now. Tap to open Criczy.", style = style(Palette.muted, 11.sp), maxLines = 2)
}

@Composable
private fun ScoreLine(match: Match, team: Team, roomy: Boolean, narrow: Boolean) {
    val batting = match.battingNow(team)
    val color = if (match.emphasises(team)) Palette.text else Palette.soft
    val scores = match.inningsFor(team.id)
    // Narrow: only the latest innings, so a Test score still fits on one line.
    val score = (if (narrow) scores.takeLast(1) else scores).joinToString(" & ") { it.scoreText }.ifEmpty { "–" }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (roomy) {
            TeamBadge(team, 18.dp)
            Spacer(GlanceModifier.width(7.dp))
        }
        Text(team.shortName, style = style(color, if (roomy) 13.sp else 12.sp, if (batting) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
        if (batting && match.state == MatchState.LIVE) {
            Spacer(GlanceModifier.width(5.dp))
            Image(ImageProvider(R.drawable.widget_disc), contentDescription = "Batting now", modifier = GlanceModifier.size(6.dp), colorFilter = ColorFilter.tint(cp(Palette.live)))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(score, style = style(color, if (roomy) 17.sp else 14.sp, FontWeight.Bold, condensed = true), maxLines = 1)
        if (!narrow && batting) Text(" ${scores.last().overs}", style = style(Palette.muted, 10.sp), maxLines = 1)
    }
}

@Composable
private fun Header(match: Match) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatePill(match)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            listOf(match.name, match.competition.name).filter { it.isNotBlank() }.joinToString(" · "),
            style = style(Palette.muted, 11.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun StatePill(match: Match) = when (match.state) {
    MatchState.LIVE -> Pill("LIVE", Palette.live, over(Palette.live, 0.2f), dot = true)
    MatchState.IN_PROGRESS -> Pill(match.day?.let { "DAY $it" } ?: "BREAK", Palette.soft, over(Color.White, 0.12f))
    MatchState.COMPLETED -> Pill("RESULT", Palette.amber, over(Palette.amber, 0.18f))
    MatchState.UPCOMING -> Pill(match.format.label.uppercase(), Palette.soft, over(Color.White, 0.12f))
}

@Composable
private fun Pill(text: String, color: Color, fill: Color, dot: Boolean = false) {
    Row(
        GlanceModifier.background(ImageProvider(R.drawable.widget_pill), colorFilter = ColorFilter.tint(cp(fill))).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Image(ImageProvider(R.drawable.widget_disc), contentDescription = null, modifier = GlanceModifier.size(6.dp), colorFilter = ColorFilter.tint(cp(color)))
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(text, style = style(color, 10.sp, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun TeamBadge(team: Team, size: Dp) {
    val color = teamColor(team)
    Box(
        GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_disc), colorFilter = ColorFilter.tint(cp(color))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            team.shortName.take(3).uppercase(),
            style = style(if (color.luminance() > 0.55f) Color(0xFF0B1018) else Color.White, (size.value * 0.36f).sp, FontWeight.Bold, condensed = true),
            maxLines = 1,
        )
    }
}

@Composable
private fun TeamRow(match: Match, team: Team, badge: Dp, scoreSize: TextUnit, fullName: Boolean, overs: Boolean) {
    val scores = match.inningsFor(team.id)
    val batting = match.battingNow(team)
    val color = if (match.emphasises(team)) Palette.text else Palette.soft
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(team, badge)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            if (fullName) team.name else team.shortName, style = style(color, 14.sp, if (batting) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1, modifier = GlanceModifier.defaultWeight(),
        )
        if (scores.isNotEmpty()) {
            Text(scores.joinToString(" & ") { it.scoreText }, style = style(color, scoreSize, FontWeight.Bold, condensed = true), maxLines = 1)
            if (overs) Text(" (${scores.last().overs})", style = style(Palette.muted, 11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Details(match: Match, centre: WidgetCentre?, gap: Dp, bowler: Boolean, over: Boolean) {
    val inPlay = match.inPlay && centre != null && (centre.batters.isNotEmpty() || centre.bowler != null)
    val potm = centre?.playerOfTheMatch?.takeIf { match.state == MatchState.COMPLETED }
    val venue = match.venue?.displayName?.takeIf { match.state == MatchState.UPCOMING }
    if (!inPlay && potm == null && venue == null) return
    Column(GlanceModifier.fillMaxWidth()) {
        Spacer(GlanceModifier.height(gap))
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(cp(Palette.divider))) {}
        Spacer(GlanceModifier.height(gap - 1.dp))
        when {
            inPlay -> {
                centre!!.batters.forEach { BatterRow(it) }
                if (bowler) centre.bowler?.let { BowlerRow(it) }
                if (over) centre.over?.let { OverRow(it) }
            }
            potm != null -> LabelledLine("Player of the match", potm)
            venue != null -> LabelledLine("Venue", venue)
        }
    }
}

@Composable
private fun LabelledLine(label: String, value: String) {
    Text(label, style = style(Palette.muted, 11.sp), maxLines = 1)
    Text(value, style = style(Palette.text, 14.sp, FontWeight.Medium), maxLines = 1)
}

@Composable
private fun BatterRow(batter: BatterLine) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (batter.isOnStrike) "${batter.name} *" else batter.name,
            style = style(if (batter.isOnStrike) Palette.text else Palette.soft, 13.sp, if (batter.isOnStrike) FontWeight.Medium else FontWeight.Normal),
            maxLines = 1, modifier = GlanceModifier.defaultWeight(),
        )
        Text(batter.runs.toString(), style = style(Palette.text, 15.sp, FontWeight.Bold, condensed = true), maxLines = 1)
        Text(" (${batter.balls})", style = style(Palette.muted, 11.sp), maxLines = 1)
    }
}

@Composable
private fun BowlerRow(bowler: BowlerLine) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.widget_disc), contentDescription = "Bowling", modifier = GlanceModifier.size(7.dp), colorFilter = ColorFilter.tint(cp(Palette.wicket)))
        Spacer(GlanceModifier.width(6.dp))
        Text(bowler.name, style = style(Palette.soft, 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text("${bowler.wickets}/${bowler.runs}", style = style(Palette.text, 15.sp, FontWeight.Bold, condensed = true), maxLines = 1)
        Text(" (${bowler.overs})", style = style(Palette.muted, 11.sp), maxLines = 1)
    }
}

@Composable
private fun OverRow(over: WidgetOver) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("OVER ${over.number}", style = style(Palette.muted, 10.sp, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.width(54.dp))
        over.balls.takeLast(8).forEach { ball -> Box(GlanceModifier.padding(end = 4.dp)) { BallChip(ball) } }
    }
}

@Composable
private fun BallChip(ball: Ball) {
    val (fill, ink) = when (ball.outcome) {
        BallOutcome.WICKET -> Palette.wicket to Color.White
        BallOutcome.SIX -> Palette.six to Color.White
        BallOutcome.FOUR -> Palette.four to Color.White
        BallOutcome.WIDE, BallOutcome.NO_BALL, BallOutcome.BYE, BallOutcome.LEG_BYE -> over(Palette.extra, 0.28f) to Palette.extra
        BallOutcome.DOT -> over(Palette.dot, 0.6f) to Palette.soft
        BallOutcome.RUNS -> over(Color.White, 0.16f) to Color.White
    }
    Box(
        GlanceModifier.size(22.dp).background(ImageProvider(R.drawable.widget_disc), colorFilter = ColorFilter.tint(cp(fill))),
        contentAlignment = Alignment.Center,
    ) {
        Text(ball.symbol, style = style(ink, if (ball.symbol.length > 2) 8.sp else 11.sp, FontWeight.Bold, condensed = true), maxLines = 1)
    }
}

@Composable
private fun Footer(updatedAt: Instant?, refreshing: Boolean, actions: WidgetActions, now: Instant, detail: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(detail.orEmpty(), style = style(Palette.soft, 11.sp, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(if (refreshing) "Updating…" else updatedLabel(updatedAt, now), style = style(Palette.muted, 10.sp), maxLines = 1)
        val refresh = actions.refresh
        Box(
            (if (refresh != null) GlanceModifier.clickable(refresh) else GlanceModifier).size(width = 34.dp, height = 26.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Image(ImageProvider(R.drawable.widget_refresh), contentDescription = "Refresh", modifier = GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(cp(Palette.soft)))
        }
    }
}

// ------------------------------------------------------------------ text

private fun Match.battingNow(team: Team): Boolean = inPlay && currentInnings?.battingTeamId == team.id

/** Bright text for the side batting now, the winner, or both sides before and between innings. */
private fun Match.emphasises(team: Team): Boolean = when (state) {
    MatchState.COMPLETED -> winnerTeamId == null || winnerTeamId == team.id
    MatchState.UPCOMING -> true
    else -> currentInnings == null || currentInnings?.battingTeamId == team.id
}

private fun statusColor(match: Match): Color = if (match.state == MatchState.COMPLETED) Palette.amber else Palette.soft

/** The status line, with the venue added before the start. */
internal fun situation(match: Match, now: Instant): String {
    val status = Formatters.statusLine(match, now)
    return if (match.state == MatchState.UPCOMING) listOfNotNull(status, match.venue?.name).joinToString(" · ") else status
}

/** The rates for a match in play, or nothing. */
internal fun footerDetail(match: Match, centre: WidgetCentre?): String? {
    if (!match.inPlay || centre == null) return null
    return listOfNotNull(
        centre.runRate?.let { "CRR ${Formatters.rate(it)}" },
        centre.requiredRate?.let { "RRR ${Formatters.rate(it)}" } ?: centre.target?.let { "Target $it" },
    ).joinToString("  ·  ").ifEmpty { null }
}

internal fun updatedLabel(updatedAt: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    if (updatedAt == null) return "Offline"
    val time = DateTimeFormatter.ofPattern("h:mm a", locale).withZone(zone).format(updatedAt)
    val sameDay = updatedAt.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    return if (sameDay) "Updated $time" else "Updated ${DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone).format(updatedAt)}"
}
