package app.criczy.feature.match

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.criczy.core.model.Match
import app.criczy.core.model.Player
import app.criczy.ui.components.Badge
import app.criczy.ui.components.MatchCard
import app.criczy.ui.components.PlayerPhoto
import app.criczy.ui.components.ballStory
import app.criczy.ui.components.storyColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.analytics.InningsAnalytics
import app.criczy.core.analytics.oversToBalls
import app.criczy.core.model.Ball
import app.criczy.core.model.BallOutcome
import app.criczy.core.model.BatterLine
import app.criczy.core.model.BowlerLine
import app.criczy.core.model.Innings
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.MatchState
import app.criczy.core.model.Over
import app.criczy.ui.components.Panel
import app.criczy.ui.components.Pill
import app.criczy.ui.components.StatCell
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors

internal val tabPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)

@Composable
internal fun PanelTitle(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.titleLarge, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ballColor(ball: Ball): Color = when (ball.outcome) {
    BallOutcome.WICKET -> cricketColors.wicket
    BallOutcome.SIX -> cricketColors.six
    BallOutcome.FOUR -> cricketColors.four
    BallOutcome.WIDE, BallOutcome.NO_BALL, BallOutcome.BYE, BallOutcome.LEG_BYE -> cricketColors.extra
    BallOutcome.DOT -> cricketColors.dot
    BallOutcome.RUNS -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun BallChip(ball: Ball, size: Int = 30) {
    val color = ballColor(ball)
    val filled = ball.outcome in setOf(BallOutcome.WICKET, BallOutcome.SIX, BallOutcome.FOUR)
    Box(
        Modifier.size(size.dp).background(if (filled) color else color.copy(alpha = 0.16f), CircleShape)
            .semantics { contentDescription = spokenBall(ball) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ball.symbol, fontFamily = ScoreFont, fontWeight = FontWeight.Bold,
            fontSize = if (ball.symbol.length > 2) 11.sp else 14.sp,
            color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1,
        )
    }
}

internal fun spokenBall(ball: Ball): String = when (ball.outcome) {
    BallOutcome.WICKET -> "wicket"
    BallOutcome.SIX -> "six"
    BallOutcome.FOUR -> "four"
    BallOutcome.WIDE -> "wide"
    BallOutcome.NO_BALL -> "no ball"
    BallOutcome.BYE -> "${ball.teamRuns} byes"
    BallOutcome.LEG_BYE -> "${ball.teamRuns} leg byes"
    BallOutcome.DOT -> "dot ball"
    BallOutcome.RUNS -> "${ball.teamRuns} run${if (ball.teamRuns == 1) "" else "s"}"
}

@Composable
internal fun SummaryTab(
    detail: MatchDetail,
    commentary: CommentaryUiState,
    actions: MatchActions,
    series: List<Match> = emptyList(),
    onOpenCommentary: (String?) -> Unit = {},
) {
    val match = detail.match
    val current = detail.currentInnings
    val moments = remember(commentary.overs) { keyMoments(commentary.overs) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (match.state == MatchState.UPCOMING) {
            item {
                Panel {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Formatters.countdown(match.start)?.let { "Starts $it" } ?: "Start time to be confirmed", style = MaterialTheme.typography.headlineSmall)
                        Text(Formatters.fullDateTime(match.start), style = MaterialTheme.typography.bodyMedium)
                        match.venue?.let { Text(it.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        match.tossText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text("Add this match to favourites to be notified when it starts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (current != null && match.state != MatchState.COMPLETED) {
            item { RatePanel(detail, current) }
            item { AtTheCreasePanel(current, actions.onPlayer) { detail.player(it)?.imageUrl } }
        }
        if (match.state == MatchState.COMPLETED) {
            item { ResultPanel(detail, actions.onPlayer) }
        }
        val recent = commentary.overs.take(4)
        if (recent.isNotEmpty() && current != null && commentary.inningsNumber == current.number && match.state != MatchState.COMPLETED) {
            item { RecentOversPanel(recent) { onOpenCommentary("over-$it") } }
        }
        if (moments.isNotEmpty()) {
            item(key = "moments") { KeyMomentsPanel(detail, commentary, moments, onOpenBall = { onOpenCommentary(it) }, onSeeAll = { onOpenCommentary(null) }) }
        }
        detail.innings.forEach { innings ->
            item(key = "top-${innings.number}") { TopPerformersPanel(detail, innings, actions.onPlayer) }
        }
        // What to watch next: the rest of the series, live first, then the next fixture and recent results.
        if (series.isNotEmpty()) {
            item(key = "series-title") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MORE FROM ${match.competition.name.uppercase()}", style = MaterialTheme.typography.titleLarge, fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "See all", style = MaterialTheme.typography.labelLarge, color = cricketColors.accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClickLabel = "Open the series") { actions.onCompetition(match.competition.id, match.competition.name) }.padding(8.dp),
                    )
                }
            }
            items(series, key = { "series-${it.id}" }) { other -> MatchCard(other, onClick = { actions.onMatch(other.id) }) }
        } else {
            item {
                Text(
                    "${match.competition.name} ›",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { actions.onCompetition(match.competition.id, match.competition.name) }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * The latest wickets and boundaries, newest first. Each opens that ball's replay in the
 * commentary; See all opens the commentary itself.
 */
@Composable
private fun KeyMomentsPanel(detail: MatchDetail, commentary: CommentaryUiState, moments: List<KeyMoment>, onOpenBall: (String) -> Unit, onSeeAll: () -> Unit) {
    val innings = detail.innings.firstOrNull { it.number == commentary.inningsNumber }
    val team = innings?.let { detail.match.team(it.battingTeamId)?.shortName }
    Panel {
        Column(Modifier.padding(bottom = 4.dp)) {
            PanelTitle("Key moments", team?.let { "$it innings" })
            moments.forEach { moment ->
                val ball = moment.ball
                val story = ballStory(ball)
                val tone = storyColor(story.kind)
                val bowler = detail.player(ball.bowlerId)?.name ?: "Bowler"
                val batter = detail.player(ball.batterId)?.name ?: "batter"
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Replay this ball") { onOpenBall(moment.key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BallChip(ball, size = 34)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = tone, fontWeight = FontWeight.Bold)) { append(story.headline) }
                                story.detail?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${ball.overNumber - 1}.${ball.ballNumber} · $bowler to $batter",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "See all commentary", style = MaterialTheme.typography.labelLarge, color = cricketColors.accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSeeAll).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RatePanel(detail: MatchDetail, current: Innings) {
    val target = detail.target
    val projections = if (target == null) {
        InningsAnalytics.projectedTotals(current.score.runs, oversToBalls(current.score.overs), detail.oversPerInnings.takeUnless { detail.match.format.isMultiDay })
    } else {
        null
    }
    Panel {
        Column {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCell("Run rate", Formatters.rate(current.runRate))
                if (target != null) {
                    StatCell("Target", target.toString())
                    StatCell("Need", (target - current.score.runs).coerceAtLeast(0).toString(), valueColor = cricketColors.accent)
                    current.requiredRunRate?.let { StatCell("Required", Formatters.rate(it)) }
                } else {
                    StatCell("Extras", current.extras.total.toString())
                    detail.oversRemaining?.let { StatCell("Overs left", it) }
                }
            }
            if (projections != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PanelTitle("Projected total")
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    projections.forEach { (label, total) -> StatCell(label, total.toString()) }
                }
            }
        }
    }
}

@Composable
private fun AtTheCreasePanel(innings: Innings, onPlayer: (Int) -> Unit, photoOf: (Int) -> String?) {
    val batters = innings.batters.filter { it.isAtCrease }
    val bowler = innings.bowlers.firstOrNull { it.isBowlingNow } ?: innings.bowlers.lastOrNull()
    if (batters.isEmpty() && bowler == null) return
    Panel {
        Column {
            if (batters.isNotEmpty()) {
                BattingHeaderRow("At the crease")
                batters.forEach { BatterRow(it, onPlayer, showDismissal = false, photoUrl = photoOf(it.playerId)) }
                innings.currentPartnership?.takeIf { it.isCurrent }?.let {
                    Text(
                        "Partnership ${it.runs} (${it.balls} balls) · rate ${Formatters.rate(it.runRate)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            if (bowler != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                BowlingHeaderRow("Bowling")
                BowlerRow(bowler, onPlayer, photoOf(bowler.playerId))
            }
            innings.fallOfWickets.lastOrNull()?.let {
                val out = innings.batted.firstOrNull { b -> b.playerId == it.playerId }
                Text(
                    "Last wicket: ${it.playerName}${out?.let { b -> " ${b.runs} (${b.balls})" }.orEmpty()} · ${it.runs}/${it.order} (${it.over} ov)",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentOversPanel(overs: List<Over>, onOpenOver: (Int) -> Unit) {
    Panel {
        Column(Modifier.padding(bottom = 8.dp)) {
            PanelTitle("Recent overs", "tap to replay")
            overs.forEach { over ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Replay over ${over.number}") { onOpenOver(over.number) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ov ${over.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(46.dp))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        over.balls.forEach { BallChip(it) }
                    }
                    Text("${over.runs}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(detail: MatchDetail, onPlayer: (Int) -> Unit) {
    val match = detail.match
    val winner = match.winnerTeamId?.let { match.team(it) }
    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (winner != null) {
                    Badge(winner.shortName, winner.logoUrl, winner.colorHex, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    match.statusText ?: "Match finished", style = MaterialTheme.typography.headlineSmall,
                    color = if (winner != null) cricketColors.win else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                )
            }
            match.tossText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            detail.playersOfTheMatch.forEach { PlayerOfTheMatchCard(detail, it, onPlayer) }
        }
    }
}

@Composable
private fun PlayerOfTheMatchCard(detail: MatchDetail, player: Player, onPlayer: (Int) -> Unit) {
    val accent = cricketColors.accent
    val team = player.teamId?.let { detail.match.team(it) }
    val figures = remember(detail, player.id) { matchFigures(detail, player.id) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.1f))
            .clickable(onClickLabel = "Open player") { onPlayer(player.id) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerPhoto(player.name, player.imageUrl, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PLAYER OF THE MATCH", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOfNotNull(team?.shortName, figures).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopPerformersPanel(detail: MatchDetail, innings: Innings, onPlayer: (Int) -> Unit) {
    val team = detail.match.team(innings.battingTeamId)
    val batters = innings.batted.sortedByDescending { it.runs }.take(2)
    val bowlers = innings.bowlers.sortedWith(compareByDescending<BowlerLine> { it.wickets }.thenBy { it.runs }).take(2)
    if (batters.isEmpty()) return
    Panel {
        Column(Modifier.padding(bottom = 8.dp)) {
            PanelTitle("${team?.shortName ?: "Innings ${innings.number}"} ${innings.score.scoreText}", "${innings.score.overs} ov")
            batters.forEach { batter ->
                PerformerRow(batter.name, detail.player(batter.playerId)?.imageUrl, "${batter.runs}${if (batter.isOut) "" else "*"} (${batter.balls})", onClick = { onPlayer(batter.playerId) })
            }
            bowlers.forEach { bowler ->
                PerformerRow(bowler.name, detail.player(bowler.playerId)?.imageUrl, "${bowler.wickets}/${bowler.runs} (${bowler.overs})", onClick = { onPlayer(bowler.playerId) })
            }
        }
    }
}

@Composable
private fun PerformerRow(name: String, imageUrl: String?, figures: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open player", onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerPhoto(name, imageUrl, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(figures, fontFamily = ScoreFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

// ---- scorecard -----------------------------------------------------------------------------

private val numberColumn = 34.dp
private val wideColumn = 46.dp

@Composable
private fun NumberCell(text: String, wide: Boolean = false, bold: Boolean = false, header: Boolean = false) {
    Text(
        text, modifier = Modifier.width(if (wide) wideColumn else numberColumn), textAlign = TextAlign.End, maxLines = 1,
        fontFamily = if (header) null else ScoreFont, fontSize = if (header) 11.sp else 16.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** Size of the small headshot at the start of each batting and bowling row. */
private val SCORECARD_PHOTO = 28.dp

@Composable
private fun BattingHeaderRow(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        NumberCell("R", header = true); NumberCell("B", header = true); NumberCell("4s", header = true); NumberCell("6s", header = true); NumberCell("SR", wide = true, header = true)
    }
}

@Composable
private fun BatterRow(batter: BatterLine, onPlayer: (Int) -> Unit, showDismissal: Boolean = true, photoUrl: String? = null) {
    val spoken = "${batter.name}, ${batter.runs} runs from ${batter.balls} balls, ${batter.fours} fours, ${batter.sixes} sixes" +
        (if (batter.isOut) ", ${batter.dismissal ?: "out"}" else ", not out")
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open player") { onPlayer(batter.playerId) }
            .semantics(mergeDescendants = true) { contentDescription = spoken }.padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerPhoto(batter.name, photoUrl, size = SCORECARD_PHOTO)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(batter.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (batter.isAtCrease) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (batter.isOnStrike) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(7.dp).background(cricketColors.accent, CircleShape))
                }
                battingMilestone(batter.runs)?.let {
                    Spacer(Modifier.width(6.dp))
                    Pill(it, container = cricketColors.accent.copy(alpha = 0.2f), content = cricketColors.accent)
                }
            }
            if (showDismissal) {
                Text(
                    if (batter.isOut) batter.dismissal ?: "out" else "not out",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (batter.isOut) MaterialTheme.colorScheme.onSurfaceVariant else cricketColors.win,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NumberCell(batter.runs.toString(), bold = true); NumberCell(batter.balls.toString()); NumberCell(batter.fours.toString()); NumberCell(batter.sixes.toString())
        NumberCell(Formatters.oneDecimal(batter.strikeRate), wide = true)
    }
}

@Composable
private fun BowlingHeaderRow(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        NumberCell("O", header = true); NumberCell("M", header = true); NumberCell("R", header = true); NumberCell("W", header = true); NumberCell("Econ", wide = true, header = true)
    }
}

@Composable
private fun BowlerRow(bowler: BowlerLine, onPlayer: (Int) -> Unit, photoUrl: String? = null) {
    val spoken = "${bowler.name}, ${bowler.wickets} wickets for ${bowler.runs} runs from ${bowler.overs} overs"
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open player") { onPlayer(bowler.playerId) }
            .semantics(mergeDescendants = true) { contentDescription = spoken }.padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerPhoto(bowler.name, photoUrl, size = SCORECARD_PHOTO)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(bowler.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            bowlingHaul(bowler.wickets)?.let {
                Spacer(Modifier.width(6.dp))
                Pill(it, container = cricketColors.wicket.copy(alpha = 0.18f), content = cricketColors.wicket)
            }
        }
        NumberCell(bowler.overs); NumberCell(bowler.maidens.toString()); NumberCell(bowler.runs.toString()); NumberCell(bowler.wickets.toString(), bold = true)
        NumberCell(Formatters.rate(bowler.economy), wide = true)
    }
}

@Composable
internal fun InningsChips(detail: MatchDetail, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        detail.innings.forEach { innings ->
            val team = detail.match.team(innings.battingTeamId)?.shortName ?: "Inns ${innings.number}"
            val ordinal = if (detail.match.format.isMultiDay) (if (detail.innings.count { it.battingTeamId == innings.battingTeamId && it.number <= innings.number } == 1) " 1st" else " 2nd") else ""
            FilterChip(selected = innings.number == selected, onClick = { onSelect(innings.number) }, label = { Text("$team$ordinal · ${innings.score.scoreText}") })
        }
    }
}

@Composable
internal fun ScorecardTab(detail: MatchDetail, onPlayer: (Int) -> Unit) {
    if (detail.innings.isEmpty()) return TabEmpty("No scorecard yet", "The scorecard appears once play begins.")
    // A finished scorecard reads from the top; a match in play opens on the innings being played.
    var selected by rememberSaveable {
        mutableIntStateOf(if (detail.match.state == MatchState.COMPLETED) detail.innings.first().number else detail.currentInnings!!.number)
    }
    val innings = detail.innings.firstOrNull { it.number == selected } ?: detail.innings.last()
    Column(Modifier.fillMaxSize()) {
        InningsChips(detail, innings.number) { selected = it }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Panel {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        BattingHeaderRow("Batting")
                        innings.batted.forEach { BatterRow(it, onPlayer, photoUrl = detail.player(it.playerId)?.imageUrl) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                        val e = innings.extras
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Text("Extras", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${e.total}  (b ${e.byes}, lb ${e.legByes}, w ${e.wides}, nb ${e.noBalls}${if (e.penalties > 0) ", p ${e.penalties}" else ""})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${innings.score.scoreText}  (${innings.score.overs} ov, RR ${Formatters.rate(innings.runRate)})", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        if (innings.yetToBat.isNotEmpty()) {
                            Text(
                                "${if (detail.match.state == MatchState.COMPLETED || innings.number < detail.innings.size) "Did not bat" else "Yet to bat"}: ${innings.yetToBat.joinToString { it.name }}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            if (innings.bowlers.isNotEmpty()) {
                item {
                    Panel {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            BowlingHeaderRow("Bowling")
                            innings.bowlers.forEach { BowlerRow(it, onPlayer, detail.player(it.playerId)?.imageUrl) }
                        }
                    }
                }
            }
            if (innings.fallOfWickets.isNotEmpty()) {
                item {
                    Panel {
                        Column(Modifier.padding(bottom = 10.dp)) {
                            PanelTitle("Fall of wickets")
                            innings.fallOfWickets.forEach { wicket ->
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${wicket.runs}/${wicket.order}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.widthIn(min = 56.dp))
                                    Text(wicket.playerName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${wicket.over} ov", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
