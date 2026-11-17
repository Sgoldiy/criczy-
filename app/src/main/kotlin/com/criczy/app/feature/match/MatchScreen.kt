package com.criczy.app.feature.match

import android.widget.Toast
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import com.criczy.app.notifications.LiveScoreService
import com.criczy.app.notifications.Notifier
import com.criczy.app.ui.components.scrollingText
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.criczy.app.core.data.Loadable
import com.criczy.app.core.model.Highlight
import com.criczy.app.core.model.InningsBallByBall
import com.criczy.app.core.model.MatchDetail
import com.criczy.app.core.model.MatchState
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.components.Badge
import com.criczy.app.ui.components.EmptyState
import com.criczy.app.ui.components.ErrorState
import com.criczy.app.ui.components.LivePill
import com.criczy.app.ui.components.Pill
import com.criczy.app.ui.components.SkeletonList
import com.criczy.app.ui.components.StaleBanner
import com.criczy.app.ui.components.matchDescription
import com.criczy.app.ui.format.Formatters
import com.criczy.app.ui.theme.ScoreFont
import com.criczy.app.ui.theme.cricketColors
import com.criczy.app.ui.theme.parseTeamColor
import kotlinx.coroutines.launch
import com.criczy.app.ui.components.AppTitle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.criczy.app.core.model.Match
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf

data class MatchActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onToggleFavourite: () -> Unit,
    val onShare: (MatchDetail) -> Unit,
    val onPlayer: (Int) -> Unit,
    val onTeam: (Int) -> Unit,
    val onCompetition: (Int, String) -> Unit,
    val onSelectCommentaryInnings: (Int) -> Unit,
    val onLoadOlderCommentary: () -> Unit,
    val onRequestAnalysis: () -> Unit,
    val onRequestHighlights: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onMatch: (Int) -> Unit = {},
    val onRequestSeries: () -> Unit = {},
    /** Pins or unpins the match's live score in the notification bar. */
    val onTogglePin: (Int) -> Unit = {},
)

@Composable
fun MatchRoute(
    matchId: Int,
    onBack: () -> Unit,
    onPlayer: (Int) -> Unit,
    onTeam: (Int) -> Unit,
    onCompetition: (Int, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onMatch: (Int) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: MatchViewModel = viewModel(key = "match-$matchId") { MatchViewModel(matchId, container) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val commentary by vm.commentary.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val favourite by vm.isFavourite.collectAsStateWithLifecycle()
    val cached by vm.servingCached.collectAsStateWithLifecycle()
    val event by vm.scoreEvent.collectAsStateWithLifecycle()
    val series by vm.seriesMatches.collectAsStateWithLifecycle()
    val pinned by LiveScoreService.pinned.collectAsStateWithLifecycle()
    MatchScreen(
        pinned = pinned == matchId,
        detail = detail, commentary = commentary, analysis = analysis, highlights = highlights,
        isFavourite = favourite, servingCached = cached, scoreEvent = event, series = series,
        actions = MatchActions(
            onBack = onBack, onRefresh = vm::refresh, onToggleFavourite = vm::toggleFavourite,
            onShare = { ScorecardSharer.share(context, it) },
            onPlayer = onPlayer, onTeam = onTeam, onCompetition = onCompetition,
            onSelectCommentaryInnings = vm::selectCommentaryInnings, onLoadOlderCommentary = vm::loadOlderCommentary,
            onRequestAnalysis = vm::requestAnalysis, onRequestHighlights = vm::requestHighlights, onOpenUrl = onOpenUrl,
            onMatch = onMatch, onRequestSeries = vm::requestSeries,
            onTogglePin = { id ->
                when {
                    pinned == id -> LiveScoreService.stop(context)
                    !Notifier.canPost(context) -> Toast.makeText(context, "Turn on notifications for Criczy to pin the score.", Toast.LENGTH_LONG).show()
                    else -> LiveScoreService.start(context, id)
                }
            },
        ),
    )
}

private enum class MatchTab(val title: String) {
    SUMMARY("Summary"), SCORECARD("Scorecard"), COMMENTARY("Commentary"), ANALYSIS("Analysis"), SQUADS("Squads"), INFO("Info")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MatchScreen(
    detail: Loadable<MatchLookup>,
    commentary: CommentaryUiState,
    analysis: Loadable<Map<Int, InningsBallByBall>>,
    highlights: List<Highlight>,
    isFavourite: Boolean,
    servingCached: Boolean,
    scoreEvent: ScoreEvent?,
    actions: MatchActions,
    initialTab: Int = 0,
    series: List<Match> = emptyList(),
    pinned: Boolean = false,
) {
    val data = detail.data?.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        AppTitle(data?.match?.title ?: "Match", fontSize = 24.sp)
                        if (data != null) {
                            Text(
                                listOf(data.match.name, data.match.competition.name).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, modifier = Modifier.scrollingText(),
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (data != null) {
                        if (pinned || data.match.state == MatchState.LIVE || data.match.state == MatchState.IN_PROGRESS) {
                            IconButton(onClick = { actions.onTogglePin(data.match.id) }) {
                                if (pinned) Icon(Icons.Filled.PushPin, contentDescription = "Unpin score from notifications", tint = cricketColors.accent)
                                else Icon(Icons.Outlined.PushPin, contentDescription = "Pin score to notifications")
                            }
                        }
                        IconButton(onClick = actions.onToggleFavourite) {
                            if (isFavourite) Icon(Icons.Filled.Star, contentDescription = "Remove from favourites", tint = cricketColors.accent)
                            else Icon(Icons.Outlined.StarBorder, contentDescription = "Add to favourites")
                        }
                        IconButton(onClick = { actions.onShare(data) }) { Icon(Icons.Outlined.Share, contentDescription = "Share scorecard") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                detail.isInitialLoad -> SkeletonList(count = 4, itemHeight = 140.dp)
                detail.isFailedEmpty -> ErrorState(detail.error!!, actions.onRefresh)
                data == null -> EmptyState("Match not found", "This match is no longer available from the score service.")
                else -> {
                    val tabs = remember(data.match.state, data.innings.isEmpty()) {
                        if (data.innings.isEmpty()) listOf(MatchTab.SUMMARY, MatchTab.SQUADS, MatchTab.INFO) else MatchTab.entries
                    }
                    val pager = rememberPagerState(initialPage = initialTab.coerceIn(0, tabs.lastIndex)) { tabs.size }
                    val scope = rememberCoroutineScope()
                    // A ball or over picked elsewhere (the summary's key moments, recent overs) to open in the commentary.
                    var commentaryFocus by rememberSaveable { mutableStateOf<String?>(null) }
                    val openInCommentary: (String?) -> Unit = { key ->
                        commentaryFocus = key
                        tabs.indexOf(MatchTab.COMMENTARY).takeIf { it >= 0 }?.let { scope.launch { pager.animateScrollToPage(it) } }
                    }
                    Column(Modifier.fillMaxSize()) {
                        if (servingCached) {
                            StaleBanner("Offline · saved copy" + (Formatters.updatedAgo(detail.updatedAt)?.let { " · ${it.lowercase()}" } ?: ""))
                        }
                        MatchHeader(data)
                        ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                            tabs.forEachIndexed { index, tab ->
                                Tab(pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(tab.title) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                            when (tabs.getOrNull(page) ?: return@HorizontalPager) {
                                MatchTab.SUMMARY -> {
                                    LaunchedEffect(Unit) { actions.onRequestSeries() }
                                    SummaryTab(data, commentary, actions, series, openInCommentary)
                                }
                                MatchTab.SCORECARD -> ScorecardTab(data, actions.onPlayer)
                                MatchTab.COMMENTARY -> CommentaryTab(data, commentary, actions, focus = commentaryFocus) { commentaryFocus = null }
                                MatchTab.ANALYSIS -> {
                                    LaunchedEffect(Unit) { actions.onRequestAnalysis() }
                                    AnalysisTab(data, analysis, actions.onRefresh)
                                }
                                MatchTab.SQUADS -> SquadsTab(data, actions.onPlayer)
                                MatchTab.INFO -> {
                                    LaunchedEffect(Unit) { actions.onRequestHighlights() }
                                    InfoTab(data, highlights, actions)
                                }
                            }
                        }
                    }
                    ScoreEventOverlay(scoreEvent, data)
                }
            }
        }
    }
}

/** Scoreboard header: both teams, every innings, and the state of the game in one line. */
@Composable
private fun MatchHeader(detail: MatchDetail) {
    val match = detail.match
    val home = parseTeamColor(match.home.colorHex) ?: MaterialTheme.colorScheme.primary
    val away = parseTeamColor(match.away.colorHex) ?: MaterialTheme.colorScheme.tertiary
    val scrim = Color(0xFF0B1018)
    val current = detail.currentInnings
    val description = matchDescription(match)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(home, away)))
            .background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0.62f), scrim.copy(alpha = 0.9f))))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (match.state == MatchState.LIVE) liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (team in listOf(match.home, match.away)) {
            val scores = match.inningsFor(team.id)
            val batting = match.state != MatchState.COMPLETED && current?.battingTeamId == team.id
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(team.shortName, team.logoUrl, team.colorHex, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(team.shortName, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                    Text(team.name, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (scores.isEmpty()) {
                    if (match.state != MatchState.UPCOMING) Text("Yet to bat", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                } else {
                    Text(
                        scores.joinToString(" & ") { it.scoreText },
                        fontFamily = ScoreFont, fontWeight = FontWeight.Bold,
                        fontSize = if (batting || match.winnerTeamId == team.id) 34.sp else 28.sp,
                        color = if (batting || match.state == MatchState.COMPLETED) Color.White else Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("(${scores.last().overs})", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (match.state) {
                MatchState.LIVE -> LivePill()
                MatchState.IN_PROGRESS -> Pill(match.day?.let { "DAY $it" } ?: "IN PROGRESS", container = Color.White.copy(alpha = 0.16f), content = Color.White)
                MatchState.UPCOMING -> Pill(Formatters.countdown(match.start)?.uppercase() ?: "UPCOMING", container = Color.White.copy(alpha = 0.16f), content = Color.White)
                MatchState.COMPLETED -> Pill("RESULT", container = Color.White.copy(alpha = 0.16f), content = Color.White)
            }
            Text(
                Formatters.statusLine(match), style = MaterialTheme.typography.bodySmall, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Brief full-screen flash for a boundary or wicket, the moment it shows up in a refresh. */
@Composable
internal fun TabEmpty(title: String, body: String) {
    Box(Modifier.fillMaxSize()) { EmptyState(title, body, Modifier.height(260.dp)) }
}
