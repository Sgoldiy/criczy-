package app.criczy.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import app.criczy.core.model.NewsArticle
import app.criczy.ui.SeriesShortcut
import app.criczy.ui.seriesInPlay
import app.criczy.ui.components.NewsRow
import app.criczy.ui.components.SeriesChip
import app.criczy.ui.theme.cricketColors
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.core.data.FavouriteType
import app.criczy.core.data.Loadable
import app.criczy.core.data.RefreshPolicy
import app.criczy.core.data.pollingFlow
import app.criczy.core.model.Match
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.MatchFormat
import app.criczy.core.model.MatchState
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.Refreshable
import app.criczy.ui.WhileUiVisible
import app.criczy.ui.components.AppTitle
import app.criczy.ui.components.EmptyState
import app.criczy.ui.components.ErrorState
import app.criczy.ui.components.FeaturedMatchCard
import app.criczy.ui.components.MatchCard
import app.criczy.ui.components.SkeletonList
import app.criczy.ui.components.StaleBanner
import app.criczy.ui.format.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import androidx.compose.runtime.getValue

enum class HomeFilter(val label: String) {
    ALL("All"), FOLLOWING("Following"), INTERNATIONAL("International"), T20("T20"), ONE_DAY("One-day"), TEST("Tests"), WOMEN("Women");

    fun accepts(match: Match, followedTeams: Set<Int>, followedMatches: Set<Int>, followedCompetitions: Set<Int>): Boolean = when (this) {
        ALL -> true
        FOLLOWING -> match.id in followedMatches || match.home.id in followedTeams || match.away.id in followedTeams || match.competition.id in followedCompetitions
        INTERNATIONAL -> match.format in setOf(MatchFormat.TEST, MatchFormat.ODI, MatchFormat.T20I)
        T20 -> match.format in setOf(MatchFormat.T20, MatchFormat.T20I, MatchFormat.HUNDRED)
        ONE_DAY -> match.format in setOf(MatchFormat.ODI, MatchFormat.ONE_DAY)
        TEST -> match.format.isMultiDay
        WOMEN -> match.competition.isWomen || match.home.isWomen || match.away.isWomen
    }
}

data class HomeUiState(
    val board: Loadable<MatchBoard> = Loadable(),
    val filter: HomeFilter = HomeFilter.ALL,
    val followedMatchIds: Set<Int> = emptySet(),
    val servingCached: Boolean = false,
    val live: List<Match> = emptyList(),
    val upcoming: List<Match> = emptyList(),
    val completed: List<Match> = emptyList(),
    /** Series with a match in play or starting within a day, unfiltered. */
    val seriesInPlay: List<SeriesShortcut> = emptyList(),
    /** The latest few stories, to read between overs. */
    val stories: List<NewsArticle> = emptyList(),
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val board = Refreshable(viewModelScope) {
        pollingFlow(nextDelayMs = RefreshPolicy::forBoard) { container.matches.board() }
    }
    private val filter = MutableStateFlow(HomeFilter.ALL)
    private val stories = MutableStateFlow<List<NewsArticle>>(emptyList())

    init {
        // Stories are a bonus under the scores: loaded once, and the screen is complete without them.
        viewModelScope.launch {
            stories.value = try {
                container.news.news().take(3)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        board.state, filter, container.favourites.all, container.connection.servingCached, stories,
    ) { loadable, selected, favourites, cached, latestStories ->
        val teams = favourites.filter { it.type == FavouriteType.TEAM }.mapTo(HashSet()) { it.id }
        val matchIds = favourites.filter { it.type == FavouriteType.MATCH }.mapTo(HashSet()) { it.id }
        val competitions = favourites.filter { it.type == FavouriteType.COMPETITION }.mapTo(HashSet()) { it.id }
        fun List<Match>.filtered() = filter { selected.accepts(it, teams, matchIds, competitions) }
        val data = loadable.data ?: MatchBoard.Empty
        HomeUiState(
            board = loadable,
            filter = selected,
            followedMatchIds = data.all.filter { HomeFilter.FOLLOWING.accepts(it, teams, matchIds, competitions) }.mapTo(HashSet()) { it.id },
            servingCached = cached,
            // Ball-in-play matches first, then those at a break.
            live = data.live.filtered().sortedBy { it.state != MatchState.LIVE },
            upcoming = data.upcoming.filtered().sortedBy { it.start },
            completed = data.completed.filtered().sortedByDescending { it.start },
            seriesInPlay = seriesInPlay(data),
            stories = latestStories,
        )
    }.stateIn(viewModelScope, WhileUiVisible, HomeUiState())

    fun refresh() = board.refresh()
    fun setFilter(value: HomeFilter) { filter.value = value }
}

@Composable
fun HomeRoute(
    onMatch: (Int) -> Unit,
    onSearch: () -> Unit,
    onCompetition: (Int, String) -> Unit = { _, _ -> },
    onOpenUrl: (String) -> Unit = {},
    onNews: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(state, onMatch, onSearch, vm::refresh, vm::setFilter, onCompetition, onOpenUrl, onNews)
}

private val tabs = listOf("Live", "Upcoming", "Results")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onMatch: (Int) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (HomeFilter) -> Unit,
    onCompetition: (Int, String) -> Unit = { _, _ -> },
    onOpenUrl: (String) -> Unit = {},
    onNews: () -> Unit = {},
) {
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppTitle("Criczy") },
                actions = { IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.servingCached) {
                StaleBanner("Offline · showing saved scores" + (Formatters.updatedAgo(state.board.updatedAt)?.let { " · ${it.lowercase()}" } ?: ""))
            }
            TabRow(selectedTabIndex = pager.currentPage) {
                tabs.forEachIndexed { index, title ->
                    val count = if (index == 0) state.live.size else 0
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(if (count > 0) "$title · $count" else title) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeFilter.entries.forEach { filter ->
                    FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = { Text(filter.label) })
                }
            }
            PullToRefreshBox(
                isRefreshing = state.board.loading && state.board.data != null,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.board.isInitialLoad -> SkeletonList()
                    state.board.isFailedEmpty -> ErrorState(state.board.error!!, onRefresh)
                    else -> HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> LiveTab(state, onMatch, onCompetition, onOpenUrl, onNews)
                            1 -> MatchList(state.upcoming, state, onMatch, groupByDay = true, emptyTitle = "No upcoming matches", emptyBody = "Nothing scheduled matches this filter.")
                            else -> MatchList(state.completed, state, onMatch, groupByDay = false, emptyTitle = "No results", emptyBody = "No finished matches match this filter.")
                        }
                    }
                }
            }
        }
    }
}

private val gridPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
private fun LiveTab(
    state: HomeUiState,
    onMatch: (Int) -> Unit,
    onCompetition: (Int, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onNews: () -> Unit,
) {
    val inPlay = state.live.filter { it.state == MatchState.LIVE }
    val paused = state.live.filter { it.state != MatchState.LIVE }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(330.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.live.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    "No matches in play",
                    if (state.filter == HomeFilter.ALL) "The next matches are listed below." else "Nothing live matches this filter.",
                )
            }
        }
        if (inPlay.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val pager = rememberPagerState { inPlay.size }
                HorizontalPager(pager, pageSpacing = 12.dp, contentPadding = PaddingValues(end = if (inPlay.size > 1) 36.dp else 0.dp)) { index ->
                    val match = inPlay.getOrNull(index) ?: return@HorizontalPager
                    FeaturedMatchCard(match, onClick = { onMatch(match.id) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (paused.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupLabel(if (inPlay.isEmpty()) "In progress" else "At a break") }
            items(paused, key = { it.id }) { MatchCard(it, { onMatch(it.id) }, isFavourite = it.id in state.followedMatchIds) }
        }
        // Straight into a series with something on: tables, fixtures and stats are a tap away.
        if (state.seriesInPlay.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "series-in-play") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupLabel("Series in play")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.seriesInPlay.forEach { shortcut ->
                            SeriesChip(shortcut.competition, shortcut.live) { onCompetition(shortcut.competition.id, shortcut.competition.name) }
                        }
                    }
                }
            }
        }
        val next = state.upcoming.take(4)
        if (next.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupLabel("Up next") }
            items(next, key = { "next-${it.id}" }) { MatchCard(it, { onMatch(it.id) }, isFavourite = it.id in state.followedMatchIds) }
        }
        if (state.stories.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "stories-title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { GroupLabel("Top stories") }
                    Text(
                        "More news", style = MaterialTheme.typography.labelLarge, color = cricketColors.accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp).clickable(onClickLabel = "Open news", onClick = onNews).padding(8.dp),
                    )
                }
            }
            items(state.stories, key = { "story-${it.id}" }) { article -> NewsRow(article, onClick = { onOpenUrl(article.url) }) }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun MatchList(
    matches: List<Match>,
    state: HomeUiState,
    onMatch: (Int) -> Unit,
    groupByDay: Boolean,
    emptyTitle: String,
    emptyBody: String,
) {
    if (matches.isEmpty()) {
        Box(Modifier.fillMaxSize()) { EmptyState(emptyTitle, emptyBody) }
        return
    }
    val zone = ZoneId.systemDefault()
    val groups = matches.groupBy { it.start?.atZone(zone)?.toLocalDate() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(330.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { (day, dayMatches) ->
            if (groupByDay || groups.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "day-$day") {
                    GroupLabel(dayMatches.first().start?.let { Formatters.matchTime(it).substringBefore(',') } ?: "Date to be confirmed")
                }
            }
            items(dayMatches, key = { it.id }) { MatchCard(it, { onMatch(it.id) }, isFavourite = it.id in state.followedMatchIds) }
        }
    }
}
