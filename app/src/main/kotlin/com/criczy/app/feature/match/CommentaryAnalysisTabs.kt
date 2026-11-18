package com.criczy.app.feature.match

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import com.criczy.app.ads.AdUnit
import com.criczy.app.ads.BannerAdSlot
import com.criczy.app.core.analytics.BattingControl
import com.criczy.app.core.analytics.BowlerKind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.criczy.app.ui.components.BallStoryKind
import com.criczy.app.ui.components.OverGround
import androidx.compose.foundation.layout.height
import com.criczy.app.ui.components.replayMillis
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
import com.criczy.app.ui.components.ballStory
import com.criczy.app.ui.components.storyColor
import kotlinx.coroutines.delay

import androidx.compose.ui.graphics.Color
import com.criczy.app.core.model.MatchState
import com.criczy.app.ui.components.BallGround
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
import com.criczy.app.core.analytics.InningsAnalytics
import com.criczy.app.core.analytics.PitchMap
import com.criczy.app.core.analytics.WagonWheel
import com.criczy.app.core.data.Loadable
import com.criczy.app.core.model.Ball
import com.criczy.app.core.model.InningsBallByBall
import com.criczy.app.core.model.MatchDetail
import com.criczy.app.core.model.Over
import com.criczy.app.ui.components.ChartLegend
import com.criczy.app.ui.components.ChartSeries
import com.criczy.app.ui.components.ErrorState
import com.criczy.app.ui.components.ManhattanChart
import com.criczy.app.ui.components.Panel
import com.criczy.app.ui.components.PartnershipBars
import com.criczy.app.ui.components.Pill
import com.criczy.app.ui.components.PitchMapGrid
import com.criczy.app.ui.components.SkeletonList
import com.criczy.app.ui.components.StatCell
import com.criczy.app.ui.components.WagonWheelChart
import com.criczy.app.ui.components.WormChart
import com.criczy.app.ui.components.ZoneBreakdown
import com.criczy.app.ui.format.Formatters
import com.criczy.app.ui.theme.ScoreFont
import com.criczy.app.ui.theme.cricketColors
import com.criczy.app.ui.theme.parseTeamColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ---- commentary ----------------------------------------------------------------------------

@Composable
internal fun CommentaryTab(
    detail: MatchDetail,
    state: CommentaryUiState,
    actions: MatchActions,
    focus: String? = null,
    onFocusHandled: () -> Unit = {},
) {
    if (!detail.hasCommentary) return TabEmpty("No commentary", "Ball-by-ball commentary isn't provided for this match.")
    var filter by rememberSaveable { mutableStateOf(CommentaryFilter.ALL) }
    Column(Modifier.fillMaxSize()) {
        InningsChips(detail, state.inningsNumber ?: detail.currentInnings?.number ?: 1, actions.onSelectCommentaryInnings)
        when {
            state.overs.isEmpty() && state.loading -> SkeletonList(count = 6, itemHeight = 64.dp)
            state.overs.isEmpty() && state.error != null -> ErrorState(state.error, actions.onRefresh)
            state.overs.isEmpty() -> TabEmpty("No commentary yet", "Commentary appears here as soon as the first ball is bowled.")
            else -> CommentaryFeed(detail, state, actions, filter, { filter = it }, focus, onFocusHandled)
        }
    }
}

@Composable
private fun CommentaryFeed(
    detail: MatchDetail,
    state: CommentaryUiState,
    actions: MatchActions,
    filter: CommentaryFilter,
    onFilter: (CommentaryFilter) -> Unit,
    focus: String? = null,
    onFocusHandled: () -> Unit = {},
) {
    // One ball or over open at a time; opening another closes the first. Reset per innings.
    var open by rememberSaveable(state.inningsNumber) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val live = detail.match.state != MatchState.COMPLETED && state.inningsNumber == detail.currentInnings?.number
    val balls = remember(state.overs) { state.overs.flatMap { it.balls } }
    val counts = remember(balls) { CommentaryFilter.entries.associateWith { f -> balls.count(f::accepts) } }

    LaunchedEffect(state.inningsNumber) { if (focus == null) listState.scrollToItem(0) }

    // A ball or over picked on another tab: open it here, in the full list, and bring it up.
    LaunchedEffect(focus) {
        val key = focus ?: return@LaunchedEffect
        onFilter(CommentaryFilter.ALL)
        open = key
        feedIndexOf(key, state.overs)?.let { listState.scrollToItem(it) }
        onFocusHandled()
    }

    // A new ball arrives at the top of the list. Someone reading from the top sees it come in;
    // someone who has scrolled away gets a button back to it instead of losing their place.
    val newest = latestBall(state)
    val newestId = newest?.id
    val wasAtTop = remember(newestId) {
        Snapshot.withoutReadObservation { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48 }
    }
    var seenId by remember(state.inningsNumber) { mutableStateOf<Long?>(null) }
    var unseen by remember(state.inningsNumber) { mutableStateOf<LatestBall?>(null) }
    LaunchedEffect(newestId) {
        val before = seenId
        seenId = newestId
        if (newest == null || before == null || newestId == before) return@LaunchedEffect
        if (wasAtTop) listState.animateScrollToItem(0) else unseen = newest
    }
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atTop) { if (atTop) unseen = null }

    // Earlier overs load on their own at the end of the list. A page that fails is not retried
    // automatically: the button stays for that.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    val oldestOver = state.overs.lastOrNull()?.number
    var autoLoadedFrom by remember(state.inningsNumber) { mutableStateOf<Int?>(null) }
    LaunchedEffect(nearEnd, oldestOver, state.canLoadOlder, state.loadingOlder, filter) {
        if (filter == CommentaryFilter.ALL && nearEnd && state.canLoadOlder && !state.loadingOlder && oldestOver != null && autoLoadedFrom != oldestOver) {
            autoLoadedFrom = oldestOver
            actions.onLoadOlderCommentary()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommentaryFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = {
                        onFilter(f)
                        scope.launch { listState.scrollToItem(0) }
                    },
                    label = { Text(if (f == CommentaryFilter.ALL) f.label else "${f.label} · ${counts[f]}") },
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 28.dp)) {
                if (filter == CommentaryFilter.ALL) {
                    state.overs.forEachIndexed { overIndex, over ->
                        val title = when {
                            over.isComplete -> "END OF OVER ${over.number}"
                            overIndex == 0 && live -> "OVER ${over.number} · IN PROGRESS"
                            else -> "OVER ${over.number}"
                        }
                        item(key = "over-${over.number}") {
                            val key = "over-${over.number}"
                            Box(Modifier.animateItem()) {
                                OverSummaryRow(detail, over, title, expanded = open == key) { open = if (open == key) null else key }
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
                } else {
                    val matches = state.overs.flatMap { over -> over.balls.indices.reversed().map { over to it } }
                        .filter { (over, index) -> filter.accepts(over.balls[index]) }
                    if (matches.isEmpty()) {
                        item(key = "none") {
                            Text(
                                "No ${filter.label.lowercase()} in overs ${state.overs.last().number}–${state.overs.first().number}.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                    items(matches, key = { (over, index) -> "ball-${over.number}-$index" }) { (over, index) ->
                        val key = "ball-${over.number}-$index"
                        Box(Modifier.animateItem()) {
                            BallRow(detail, over.balls[index], expanded = open == key) { open = if (open == key) null else key }
                        }
                    }
                }
                if (state.canLoadOlder) {
                    item(key = "older") {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (filter != CommentaryFilter.ALL) {
                                Text(
                                    "Searched overs ${state.overs.last().number}–${state.overs.first().number}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            if (state.loadingOlder) CircularProgressIndicator(Modifier.size(28.dp))
                            else OutlinedButton(onClick = actions.onLoadOlderCommentary) { Text("Load earlier overs") }
                        }
                    }
                }
            }
            NewBallButton(
                latest = unseen.takeIf { filter == CommentaryFilter.ALL },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                unseen = null
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    }
}

/** Takes someone who scrolled down back to the ball that just arrived; hidden while [latest] is null. */
@Composable
private fun NewBallButton(latest: LatestBall?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Keep the last ball while the button slides away.
    var shown by remember { mutableStateOf(latest) }
    if (latest != null) shown = latest
    val colors = cricketColors
    AnimatedVisibility(
        visible = latest != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val story = remember(shown?.id) { shown?.let { ballStory(it.ball) } }
        Row(
            Modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(colors.accent)
                .clickable(role = Role.Button, onClickLabel = "Go to the newest ball", onClick = onClick)
                .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("New ball${story?.let { " · ${it.headline}" }.orEmpty()}", color = colors.onAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverSummaryRow(detail: MatchDetail, over: Over, title: String, expanded: Boolean, onToggle: () -> Unit) {
    val bowler = over.bowlerId?.let { detail.player(it)?.name }
    val accent = cricketColors.accent
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "overChevron")
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Close the over replay" else "Replay the over", onClick = onToggle)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${over.inningsRuns}/${over.inningsWickets}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            // The over at a glance, ball by ball in the order bowled.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                over.balls.forEach { BallChip(it, size = 26) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull("${over.runs} run${if (over.runs == 1) "" else "s"}", "${over.wickets} wkt".takeIf { over.wickets > 0 }, "maiden".takeIf { over.isMaiden }, bowler).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("Replay over", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
            }
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
                modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = groundMaxWidth()).fillMaxWidth().padding(horizontal = 8.dp),
                label = "overGround",
            ) { i ->
                if (i < balls.size) {
                    BallGround(balls[i], Modifier.fillMaxWidth(), animate = true, replayKey = run)
                } else {
                    OverGround(balls, Modifier.fillMaxWidth(), animate = true, replayKey = run)
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
    val story = remember(ball) { ballStory(ball) }
    val keyMoment = story.kind == BallStoryKind.WICKET || story.kind == BallStoryKind.FOUR || story.kind == BallStoryKind.SIX
    val tone = storyColor(story.kind)
    val bowler = detail.player(ball.bowlerId)?.name
    val batter = detail.player(ball.batterId)?.name
    val paragraphs = remember(ball, bowler, batter) {
        val comments = ball.commentary.filterNot { it.isEndOfOver }
        if (comments.isEmpty()) {
            listOf(CommentaryLines("${bowler ?: "Bowler"} to ${batter ?: "batter"}", spokenBall(ball).replaceFirstChar { it.uppercaseChar() }))
        } else {
            comments.map { commentaryLines(it.text, bowler, batter) ?: CommentaryLines("", it.text) }
        }
    }
    Column(
        Modifier.fillMaxWidth().then(
            // Wickets and boundaries stand out while scrolling: a tint and an edge in their colour.
            if (keyMoment) Modifier.background(tone.copy(alpha = 0.07f)).drawBehind { drawRect(tone, size = Size(3.dp.toPx(), size.height)) } else Modifier,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = if (expanded) 4.dp else 10.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text("${ball.overNumber - 1}.${ball.ballNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                BallChip(ball, size = 32)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                paragraphs.forEach { lines ->
                    if (lines.heading.isNotEmpty()) {
                        Text(lines.heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    if (lines.body.isNotEmpty()) {
                        val cut = if (ball.isWicket) dismissalSentenceStart(lines.body) else -1
                        Text(
                            if (cut < 0) AnnotatedString(lines.body) else buildAnnotatedString {
                                append(lines.body.substring(0, cut))
                                withStyle(SpanStyle(color = tone, fontWeight = FontWeight.Bold)) { append(lines.body.substring(cut)) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
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
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = groundMaxWidth()).fillMaxWidth().padding(horizontal = 8.dp)
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
        mutableStateOf(if (detail.match.state == com.criczy.app.core.model.MatchState.COMPLETED) detail.innings.firstOrNull()?.number ?: 1 else detail.currentInnings?.number ?: 1)
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
            val kind: (Int) -> BowlerKind? = { id -> BattingControl.kindOf(detail.player(id)?.bowlingType) }
            val control = BattingControl.batters(innings.balls, kind).filter { it.ballsFaced >= 6 }
            if (control.any { it.controlPercent != null } || control.any { it.pace.balls + it.spin.balls > 0 }) {
                item {
                    val sides = BattingControl.innings(innings.balls, kind)
                    Panel {
                        Column(Modifier.padding(bottom = 12.dp)) {
                            PanelTitle("Control, pace v spin", "well-timed shots · runs (balls)")
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(BowlerKind.PACE to "v Pace", BowlerKind.SPIN to "v Spin").forEach { (k, title) ->
                                    val split = sides.getValue(k)
                                    StatCell(title, if (split.balls == 0) "–" else "${split.runs}/${split.wickets}")
                                    StatCell("SR", if (split.balls == 0) "–" else Formatters.oneDecimal(split.strikeRate))
                                }
                            }
                            control.forEach { line ->
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                        .semantics(mergeDescendants = true) { contentDescription = "${name(line.batterId)}: control ${line.controlPercent?.let { Formatters.oneDecimal(it) + " percent" } ?: "not tracked"}" },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name(line.batterId), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                line.pace.takeIf { it.balls > 0 }?.let { "Pace ${it.runs} (${it.balls})" },
                                                line.spin.takeIf { it.balls > 0 }?.let { "Spin ${it.runs} (${it.balls})" },
                                            ).joinToString(" · ").ifEmpty { "${line.ballsFaced} balls" },
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val pct = line.controlPercent
                                    if (pct != null) {
                                        Box(Modifier.width(64.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                            Box(Modifier.fillMaxWidth((pct / 100).toFloat()).fillMaxHeight().background(cricketColors.accent))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("${Formatters.oneDecimal(pct)}%", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.widthIn(min = 52.dp))
                                    }
                                }
                            }
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
            // Banners only once the match is over: never beside a live score.
            if (detail.match.state == MatchState.COMPLETED) item(key = "ad") { BannerAdSlot(AdUnit.BANNER_MATCH) }
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

/**
 * The replay ground is square: no wider than 480dp, nor than half the screen's height, so on a landscape tablet the
 * whole ground stays on screen below the match header. (widthIn must come before fillMaxWidth, or the full width wins.)
 */
@Composable
internal fun groundMaxWidth(): Dp = minOf(480.dp, (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
