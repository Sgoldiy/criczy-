package app.criczy.feature.match

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import app.criczy.ui.components.BallStoryKind
import app.criczy.ui.components.OverGround
import androidx.compose.foundation.layout.height
import app.criczy.ui.components.OverPitchMap
import app.criczy.ui.components.replayMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.criczy.ui.components.ballStory
import app.criczy.ui.components.storyColor
import kotlinx.coroutines.delay

import androidx.compose.ui.graphics.Color
import app.criczy.core.model.MatchState
import app.criczy.ui.components.BallGround
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criczy.core.analytics.InningsAnalytics
import app.criczy.core.analytics.PitchMap
import app.criczy.core.analytics.WagonWheel
import app.criczy.core.data.Loadable
import app.criczy.core.model.Ball
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.Over
import app.criczy.ui.components.ChartLegend
import app.criczy.ui.components.ChartSeries
import app.criczy.ui.components.ErrorState
import app.criczy.ui.components.ManhattanChart
import app.criczy.ui.components.Panel
import app.criczy.ui.components.PartnershipBars
import app.criczy.ui.components.Pill
import app.criczy.ui.components.PitchMapGrid
import app.criczy.ui.components.SkeletonList
import app.criczy.ui.components.StatCell
import app.criczy.ui.components.WagonWheelChart
import app.criczy.ui.components.WormChart
import app.criczy.ui.components.ZoneBreakdown
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import app.criczy.ui.theme.parseTeamColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ---- commentary ----------------------------------------------------------------------------

@Composable
internal fun CommentaryTab(detail: MatchDetail, state: CommentaryUiState, actions: MatchActions) {
    if (!detail.hasCommentary) return TabEmpty("No commentary", "Ball-by-ball commentary isn't provided for this match.")
    Column(Modifier.fillMaxSize()) {
        InningsChips(detail, state.inningsNumber ?: detail.currentInnings?.number ?: 1, actions.onSelectCommentaryInnings)
        when {
            state.overs.isEmpty() && state.loading -> SkeletonList(count = 6, itemHeight = 64.dp)
            state.overs.isEmpty() && state.error != null -> ErrorState(state.error, actions.onRefresh)
            state.overs.isEmpty() -> TabEmpty("No commentary yet", "Commentary appears here as soon as the first ball is bowled.")
            else -> {
                // One ball open at a time; opening another closes the first. Reset per innings.
                var open by rememberSaveable(state.inningsNumber) { mutableStateOf<String?>(null) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                state.overs.forEach { over ->
                    if (over.isComplete || over != state.overs.first()) {
                        item(key = "over-${over.number}") {
                            val key = "over-${over.number}"
                            Box(Modifier.animateItem()) {
                                OverSummaryRow(detail, over, expanded = open == key) { open = if (open == key) null else key }
                            }
                        }
                    }
                    // Keyed by position in the over: the feed sends no ball id, and a ball re-bowled
                    // after a wide or no-ball repeats its number, so neither is unique.
                    val last = over.balls.lastIndex
                    items(over.balls.size, key = { "ball-${over.number}-${last - it}" }) { index ->
                        val ball = over.balls[last - index]
                        val key = "ball-${over.number}-${last - index}"
                        Box(Modifier.animateItem()) {
                            BallRow(detail, ball, expanded = open == key) { open = if (open == key) null else key }
                        }
                    }
                }
                if (state.canLoadOlder) {
                    item(key = "older") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (state.loadingOlder) CircularProgressIndicator(Modifier.size(28.dp))
                            else OutlinedButton(onClick = actions.onLoadOlderCommentary) { Text("Load earlier overs") }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun OverSummaryRow(detail: MatchDetail, over: Over, expanded: Boolean, onToggle: () -> Unit) {
    val bowler = over.bowlerId?.let { detail.player(it)?.name }
    val accent = cricketColors.accent
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "overChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Close the over replay" else "Replay the over", onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("END OF OVER ${over.number}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull("${over.runs} run${if (over.runs == 1) "" else "s"}", "${over.wickets} wkt".takeIf { over.wickets > 0 }, "maiden".takeIf { over.isMaiden }, bowler).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replay over", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
                }
            }
            Text("${over.inningsRuns}/${over.inningsWickets}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(320)) + fadeIn(tween(320)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(180)),
        ) {
            OverReplayPanel(detail, over)
        }
    }
}

/**
 * The over replayed as a broadcast does when an over ends: each ball plays on the ground in
 * turn, then the over summary shows every shot together with where each ball pitched. The
 * strip of balls below follows along; tapping one plays the over from that ball.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OverReplayPanel(detail: MatchDetail, over: Over) {
    val balls = over.balls
    var startAt by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }
    var showing by remember { mutableIntStateOf(0) }
    LaunchedEffect(run) {
        for (i in startAt until balls.size) {
            showing = i
            delay(replayMillis(balls[i]) + 650L)
        }
        showing = balls.size
    }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        delay(340)
        bringIntoView.bringIntoView()
    }
    val accent = cricketColors.accent
    val bowler = over.bowlerId?.let { detail.player(it)?.name } ?: "Bowler"
    val stats = remember(over) {
        val stories = balls.map { ballStory(it) }
        listOfNotNull(
            "${over.runs} run${if (over.runs == 1) "" else "s"}",
            over.wickets.takeIf { it > 0 }?.let { "$it wicket${if (it == 1) "" else "s"}" },
            balls.count { it.isLegal && it.teamRuns == 0 && !it.isWicket }.takeIf { it > 0 }?.let { "$it dot${if (it == 1) "" else "s"}" },
            stories.count { it.kind == BallStoryKind.FOUR }.takeIf { it > 0 }?.let { "$it four${if (it == 1) "" else "s"}" },
            stories.count { it.kind == BallStoryKind.SIX }.takeIf { it > 0 }?.let { "$it six${if (it == 1) "" else "es"}" },
            over.extras.takeIf { it > 0 }?.let { "$it extra${if (it == 1) "" else "s"}" },
            "Maiden".takeIf { over.isMaiden },
        )
    }
    Panel(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp).bringIntoViewRequester(bringIntoView)) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OVER ${over.number} · $bowler", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedContent(
                        targetState = showing,
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                        label = "overNowPlaying",
                    ) { i ->
                        if (i < balls.size) {
                            val ball = balls[i]
                            val story = ballStory(ball)
                            val batter = detail.player(ball.batterId)?.name ?: "Batter"
                            Text(
                                buildAnnotatedString {
                                    append("${ball.overNumber - 1}.${ball.ballNumber} · $batter · ")
                                    withStyle(SpanStyle(color = storyColor(story.kind), fontWeight = FontWeight.Bold)) { append(story.headline) }
                                },
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text("Over summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TextButton(onClick = { startAt = 0; run++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Replay")
                }
            }
            AnimatedContent(
                targetState = showing,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 8.dp),
                label = "overGround",
            ) { i ->
                if (i < balls.size) {
                    BallGround(balls[i], Modifier.fillMaxWidth(), animate = true, replayKey = run)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverGround(balls, Modifier.fillMaxWidth(), animate = true, replayKey = run)
                        Text("WHERE EACH BALL PITCHED", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                        OverPitchMap(balls, Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 6.dp), animate = true, replayKey = run)
                    }
                }
            }
            // The over, ball by ball; the one on the ground is ringed.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                balls.forEachIndexed { i, ball ->
                    val current = i == showing
                    val scale by animateFloatAsState(if (current) 1.12f else 1f, label = "overStripBall")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (current) accent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                                .border(2.dp, if (current) accent else Color.Transparent, CircleShape)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Play from ball ${i + 1}") { startAt = i; run++ }
                                .padding(3.dp),
                        ) { BallChip(ball, size = 30) }
                    }
                }
                val onSummary = showing == balls.size
                Text(
                    "Summary",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = if (onSummary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .border(2.dp, if (onSummary) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .clickable(onClickLabel = "Show the over summary") { startAt = balls.size; run++ }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { stats.forEach { Pill(it) } }
        }
    }
}

@Composable
private fun BallRow(detail: MatchDetail, ball: Ball, expanded: Boolean, onToggle: () -> Unit) {
    val comments = ball.commentary.filterNot { it.isEndOfOver }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = if (expanded) 4.dp else 9.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text("${ball.overNumber - 1}.${ball.ballNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                BallChip(ball, size = 32)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (comments.isEmpty()) {
                    val bowler = detail.player(ball.bowlerId)?.name ?: "Bowler"
                    val batter = detail.player(ball.batterId)?.name ?: "batter"
                    Text("$bowler to $batter, ${spokenBall(ball)}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    comments.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
                }
                Spacer(Modifier.size(8.dp))
                SeeShotLink(ball, expanded, onToggle)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(320)) + fadeIn(tween(320)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(180)),
        ) {
            BallReplayPanel(detail, ball)
        }
    }
}

/** "See shot · SIX": what happened, in the colour of the outcome; opens the ground below. */
@Composable
private fun SeeShotLink(ball: Ball, expanded: Boolean, onToggle: () -> Unit) {
    val story = remember(ball) { ballStory(ball) }
    val tone = storyColor(story.kind)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "chevron")
    val verb = if (story.showsShot) "See shot" else "See ball"
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.copy(alpha = if (expanded) 0.24f else 0.14f))
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide the ball on the ground" else "Show the ball on the ground", onClick = onToggle)
            .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("$verb · ") }
                withStyle(SpanStyle(color = tone, fontWeight = FontWeight.Bold)) { append(story.headline) }
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
    }
}

/**
 * The open ball: the full-size ground replaying the delivery, and everything the scorers noted
 * about it in one row underneath. Tapping the ground or Replay plays it again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BallReplayPanel(detail: MatchDetail, ball: Ball) {
    var replay by remember { mutableIntStateOf(0) }
    val story = remember(ball) { ballStory(ball) }
    val shot = remember(ball) { WagonWheel.point(ball) }
    val facts = listOfNotNull(
        ball.length, ball.line, ball.bowlingDetail, ball.shot, ball.footwork, ball.connection,
        shot?.takeIf { story.showsShot }?.let { if (it.inTheAir) "In the air" else "Along the ground" },
        ball.fieldingPosition, ball.dismissalType,
    ).distinct()
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        // Let the space open first, then make sure the whole ground is on screen.
        delay(340)
        bringIntoView.bringIntoView()
    }
    Panel(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).bringIntoViewRequester(bringIntoView)) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val bowler = detail.player(ball.bowlerId)?.name ?: "Bowler"
                val batter = detail.player(ball.batterId)?.name ?: "batter"
                Column(Modifier.weight(1f)) {
                    Text("${ball.overNumber - 1}.${ball.ballNumber}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$bowler to $batter", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { replay++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Replay")
                }
            }
            BallGround(
                ball,
                Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 8.dp)
                    .clickable(onClickLabel = "Replay the ball") { replay++ },
                animate = true, replayKey = replay,
            )
            if (facts.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) { facts.forEach { Pill(it) } }
            }
        }
    }
}

// ---- analysis ------------------------------------------------------------------------------

@Composable
internal fun AnalysisTab(detail: MatchDetail, analysis: Loadable<Map<Int, InningsBallByBall>>, onRetry: () -> Unit) {
    val data = analysis.data
    when {
        data == null && analysis.error != null -> return ErrorState(analysis.error!!, onRetry)
        data == null -> return SkeletonList(count = 3, itemHeight = 220.dp)
        data.values.all { it.overs.isEmpty() } -> return TabEmpty("No ball-by-ball data", "Charts need ball-by-ball data, which isn't provided for this match.")
    }
    data!!
    var selected by rememberSaveable {
        mutableStateOf(if (detail.match.state == app.criczy.core.model.MatchState.COMPLETED) detail.innings.firstOrNull()?.number ?: 1 else detail.currentInnings?.number ?: 1)
    }
    val innings = data[selected]?.takeIf { it.overs.isNotEmpty() } ?: data.values.first { it.overs.isNotEmpty() }
    var batterFilter by rememberSaveable(selected) { mutableStateOf<Int?>(null) }
    var bowlerFilter by rememberSaveable(selected) { mutableStateOf<Int?>(null) }

    val palette = listOf(cricketColors.accent, cricketColors.four, cricketColors.six, cricketColors.extra)
    fun colorFor(number: Int, teamId: Int) = if (data.size <= 2) {
        parseTeamColor(detail.match.team(teamId)?.colorHex)?.takeIf { data.values.map { i -> detail.match.team(i.battingTeamId)?.colorHex }.distinct().size == data.size }
            ?: palette[(number - 1).mod(palette.size)]
    } else {
        palette[(number - 1).mod(palette.size)]
    }
    fun label(i: InningsBallByBall): String {
        val team = detail.match.team(i.battingTeamId)?.shortName ?: "Inns"
        return if (detail.match.format.isMultiDay) "$team ${if (data.values.count { it.battingTeamId == i.battingTeamId && it.inningsNumber <= i.inningsNumber } == 1) "1st" else "2nd"}" else team
    }
    // Innings without ball data (not started, or not covered) would only add empty legend entries.
    val ordered = data.values.filter { it.overs.isNotEmpty() }.sortedBy { it.inningsNumber }
    val name: (Int) -> String = { id -> detail.player(id)?.name ?: "Player" }

    Column(Modifier.fillMaxSize()) {
        InningsChips(detail, innings.inningsNumber) { selected = it }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Panel {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        PanelTitle("Worm", "runs by over")
                        WormChart(ordered.map { ChartSeries(label(it), colorFor(it.inningsNumber, it.battingTeamId), InningsAnalytics.worm(it)) }, Modifier.padding(horizontal = 10.dp))
                        ChartLegend(ordered.map { label(it) to colorFor(it.inningsNumber, it.battingTeamId) } + ("Wicket" to MaterialTheme.colorScheme.outline), Modifier.padding(horizontal = 14.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()))
                    }
                }
            }
            item {
                Panel {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        PanelTitle("Manhattan", "runs per over")
                        // Side-by-side bars only read well for a pair of limited-overs innings.
                        val series = if (!detail.match.format.isMultiDay && ordered.size == 2) ordered else listOf(innings)
                        ManhattanChart(series.map { ChartSeries(label(it), colorFor(it.inningsNumber, it.battingTeamId), InningsAnalytics.manhattan(it)) }, Modifier.padding(horizontal = 10.dp))
                        ChartLegend(series.map { label(it) to colorFor(it.inningsNumber, it.battingTeamId) } + ("Wicket" to cricketColors.wicket), Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
            item {
                val scoring = InningsAnalytics.scoring(innings.balls)
                Panel {
                    Column(Modifier.padding(bottom = 14.dp)) {
                        PanelTitle("How the runs came", label(innings))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatCell("Dots", scoring.dots.toString()); StatCell("1s", scoring.singles.toString()); StatCell("2s", scoring.twos.toString())
                            StatCell("3s", scoring.threes.toString()); StatCell("4s", scoring.fours.toString(), valueColor = cricketColors.four); StatCell("6s", scoring.sixes.toString(), valueColor = cricketColors.six)
                        }
                        Spacer(Modifier.size(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatCell("Dot ball %", Formatters.oneDecimal(scoring.dotPercent))
                            StatCell("Runs in boundaries %", Formatters.oneDecimal(scoring.boundaryPercent))
                            StatCell("Extras", scoring.extras.toString())
                        }
                    }
                }
            }
            val phases = InningsAnalytics.phases(innings, detail.oversPerInnings.takeUnless { detail.match.format.isMultiDay })
            if (phases.isNotEmpty()) {
                item {
                    Panel {
                        Column(Modifier.padding(bottom = 12.dp)) {
                            PanelTitle("Phases")
                            phases.forEach { phase ->
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(phase.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Overs ${phase.fromOver}–${phase.toOver}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${phase.runs}/${phase.wickets}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.widthIn(min = 60.dp))
                                    Text("RR ${Formatters.rate(phase.runRate)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            val tracked = innings.balls.filter { it.shotAngle != null }
            if (tracked.isNotEmpty()) {
                item {
                    val batters = innings.balls.map { it.batterId }.filter { it != 0 }.distinct()
                    val balls = innings.balls.filter { batterFilter == null || it.batterId == batterFilter }
                    Panel {
                        Column(Modifier.padding(bottom = 14.dp)) {
                            PanelTitle("Wagon wheel", "scoring shots")
                            PlayerFilterRow(batters, batterFilter, name) { batterFilter = it }
                            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                WagonWheelChart(WagonWheel.points(balls), Modifier.weight(1f).widthIn(max = 260.dp))
                                Spacer(Modifier.width(14.dp))
                                ZoneBreakdown(WagonWheel.zones(balls), Modifier.weight(1f))
                            }
                            ChartLegend(listOf("4" to cricketColors.four, "6" to cricketColors.six, "1–3" to MaterialTheme.colorScheme.onSurfaceVariant, "Off side" to cricketColors.four, "Leg side" to cricketColors.accent), Modifier.padding(horizontal = 14.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()))
                        }
                    }
                }
            }
            val pitched = innings.balls.filter { it.line != null && it.length != null }
            if (pitched.isNotEmpty()) {
                item {
                    val bowlers = innings.balls.map { it.bowlerId }.filter { it != 0 }.distinct()
                    Panel {
                        Column(Modifier.padding(bottom = 14.dp)) {
                            PanelTitle("Pitch map", "line and length")
                            PlayerFilterRow(bowlers, bowlerFilter, name) { bowlerFilter = it }
                            PitchMapGrid(PitchMap.cells(pitched.filter { bowlerFilter == null || it.bowlerId == bowlerFilter }), Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
            val stands = InningsAnalytics.partnerships(innings)
            if (stands.isNotEmpty()) {
                item {
                    Panel {
                        Column(Modifier.padding(bottom = 14.dp)) {
                            PanelTitle("Partnerships", "runs (balls)")
                            PartnershipBars(stands, name, Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
            val matchups = InningsAnalytics.matchups(innings.balls).filter { it.balls >= 6 }.take(8)
            if (matchups.isNotEmpty()) {
                item {
                    Panel {
                        Column(Modifier.padding(bottom = 12.dp)) {
                            PanelTitle("Key match-ups", "batter v bowler")
                            matchups.forEach { m ->
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name(m.batterId), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("v ${name(m.bowlerId)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${m.runs} (${m.balls})", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    if (m.dismissals > 0) Pill("OUT", container = cricketColors.wicket.copy(alpha = 0.18f), content = cricketColors.wicket)
                                    else Text("SR ${Formatters.oneDecimal(m.strikeRate)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (!innings.isComplete) {
                item { Text("Some early overs of this innings could not be loaded, so partnerships are hidden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PlayerFilterRow(ids: List<Int>, selected: Int?, name: (Int) -> String, onSelect: (Int?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        ids.forEach { id ->
            FilterChip(selected == id, onClick = { onSelect(id) }, label = { Text(name(id).substringAfterLast(' ')) }, modifier = Modifier.clip(MaterialTheme.shapes.small))
        }
    }
}
