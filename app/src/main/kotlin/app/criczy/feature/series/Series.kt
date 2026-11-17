package app.criczy.feature.series

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.core.analytics.Leaderboard
import app.criczy.core.data.FavouriteType
import app.criczy.core.data.FavouritesRepository
import app.criczy.core.data.Loadable
import app.criczy.core.data.RefreshPolicy
import app.criczy.core.data.keepLastData
import app.criczy.core.data.loadOnce
import app.criczy.core.data.pollingFlow
import app.criczy.core.model.Competition
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.Standings
import app.criczy.di.AppContainer
import app.criczy.feature.match.ComparePlayersSheet
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.Refreshable
import app.criczy.ui.WhileUiVisible
import app.criczy.ui.components.Badge
import app.criczy.ui.components.EmptyState
import app.criczy.ui.components.ErrorState
import app.criczy.ui.components.MatchCard
import app.criczy.ui.components.Panel
import app.criczy.ui.components.Pill
import app.criczy.ui.components.SkeletonList
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.ScoreFont
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

// ---- list ----------------------------------------------------------------------------------

class SeriesListViewModel(private val container: AppContainer) : ViewModel() {
    private var force = false
    private val source = Refreshable(viewModelScope) {
        loadOnce { container.catalog.competitions(forceRefresh = force).also { force = false } }
    }
    val state: StateFlow<Loadable<List<Competition>>> = source.state
    fun refresh() { force = true; source.refresh() }
}

@Composable
fun SeriesListRoute(onCompetition: (Int, String) -> Unit, onSearch: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SeriesListViewModel = viewModel { SeriesListViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    SeriesListScreen(state, onCompetition, onSearch, vm::refresh)
}

private enum class SeriesFilter(val label: String) { ALL("All"), MEN("Men"), WOMEN("Women") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListScreen(
    state: Loadable<List<Competition>>,
    onCompetition: (Int, String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    now: Instant = Instant.now(),
) {
    var filter by rememberSaveable { mutableStateOf(SeriesFilter.ALL) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SERIES", style = MaterialTheme.typography.headlineSmall) },
                actions = { IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            val all = state.data
            when {
                state.isInitialLoad -> SkeletonList(itemHeight = 76.dp)
                all == null -> ErrorState(state.error!!, onRefresh)
                else -> {
                    val shown = all.filter {
                        when (filter) { SeriesFilter.ALL -> true; SeriesFilter.MEN -> !it.isWomen; SeriesFilter.WOMEN -> it.isWomen }
                    }
                    val current = shown.filter { c -> c.start?.isBefore(now) == true && c.end?.isAfter(now) != false }.sortedBy { it.end }
                    val upcoming = shown.filter { it.start?.isAfter(now) == true }.sortedBy { it.start }
                    val finished = shown.filter { it.end?.isBefore(now) == true }.sortedByDescending { it.end }
                    LazyVerticalGrid(
                        GridCells.Adaptive(330.dp), contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeriesFilter.entries.forEach { f -> FilterChip(filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                            }
                        }
                        listOf("Happening now" to current, "Coming up" to upcoming, "Recently finished" to finished).forEach { (title, list) ->
                            if (list.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = title) {
                                    Text("${title.uppercase()} · ${list.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                                }
                                items(list, key = { it.id }) { CompetitionRow(it, inProgress = list === current) { onCompetition(it.id, it.name) } }
                            }
                        }
                        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("No series", "No series match this filter.") }
                    }
                }
            }
        }
    }
}

/** The feed's long names ("Women's Twenty20 internationals") as the short tag a card has room for. */
internal fun shortFormat(label: String): String {
    val text = label.lowercase()
    val international = "international" in text || "intl" in text
    return when {
        "test" in text -> "TEST"
        "odi" in text || ("one" in text && international) -> "ODI"
        "t20" in text || "twenty20" in text -> if (international) "T20I" else "T20"
        "one" in text -> "ONE-DAY"
        else -> label.uppercase()
    }
}

/** One series. Every card is the same height, with the format in the top-right corner. */
@Composable
private fun CompetitionRow(competition: Competition, inProgress: Boolean, onClick: () -> Unit) {
    Panel(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Open series", onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Badge(competition.name.filter { it.isUpperCase() || it.isDigit() }.take(3).ifBlank { competition.name }, competition.logoUrl, null, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    // Two lines are always reserved so the date sits in the same place on every card.
                    Text(competition.name, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    competition.formatLabel?.let {
                        Spacer(Modifier.width(8.dp))
                        Pill(shortFormat(it))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (inProgress) {
                        Box(Modifier.size(6.dp).background(cricketColors.live, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        Formatters.dateRange(competition.start, competition.end) ?: "Dates to be confirmed",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---- detail --------------------------------------------------------------------------------

data class CompetitionUiState(
    val name: String,
    val competition: Competition? = null,
    val matches: Loadable<MatchBoard> = Loadable(),
    val standings: Loadable<Standings> = Loadable(),
    val leaderboard: Loadable<Leaderboard> = Loadable(),
    val isFavourite: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CompetitionViewModel(private val id: Int, name: String, private val container: AppContainer) : ViewModel() {
    private val matches = Refreshable(viewModelScope) {
        pollingFlow(nextDelayMs = RefreshPolicy::forBoard) { container.matches.competitionMatches(id) }
    }
    private val standings = Refreshable(viewModelScope) { loadOnce { container.catalog.standings(id) } }
    private val statsWanted = MutableStateFlow(false)

    // Recomputed only when the set of finished matches changes, not on every score refresh.
    private val leaderboard = combine(statsWanted, matches.state.map { it.data?.completed.orEmpty() }.distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }) { wanted, done ->
        done.takeIf { wanted }
    }.flatMapLatest { done ->
        if (done == null) flowOf(Loadable(loading = true)) else loadOnce { container.catalog.leaderboard(done) }
    }.keepLastData()

    private val competition = MutableStateFlow<Competition?>(null)

    init {
        viewModelScope.launch {
            competition.value = try {
                container.catalog.competitions().firstOrNull { it.id == id }
            } catch (_: Exception) {
                null
            }
        }
    }

    val state: StateFlow<CompetitionUiState> = combine(
        matches.state, standings.state, leaderboard, container.favourites.isFavourite(FavouriteType.COMPETITION, id), competition,
    ) { m, s, l, fav, known ->
        val resolved = known ?: m.data?.all?.firstOrNull()?.competition
        CompetitionUiState(resolved?.name ?: name, resolved, m, s, l, fav)
    }.stateIn(viewModelScope, WhileUiVisible, CompetitionUiState(name))

    fun refresh() { matches.refresh(); standings.refresh() }
    fun requestStats() { statsWanted.value = true }
    fun toggleFavourite() {
        val target = state.value.competition ?: return
        viewModelScope.launch { container.favourites.toggle(FavouritesRepository.of(target)) }
    }
}

@Composable
fun CompetitionRoute(id: Int, name: String, onBack: () -> Unit, onMatch: (Int) -> Unit, onTeam: (Int) -> Unit, onPlayer: (Int) -> Unit) {
    val container = LocalAppContainer.current
    val vm: CompetitionViewModel = viewModel(key = "competition-$id") { CompetitionViewModel(id, name, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    CompetitionScreen(state, onBack, onMatch, onTeam, onPlayer, vm::refresh, vm::requestStats, vm::toggleFavourite)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionScreen(
    state: CompetitionUiState,
    onBack: () -> Unit,
    onMatch: (Int) -> Unit,
    onTeam: (Int) -> Unit,
    onPlayer: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRequestStats: () -> Unit,
    onToggleFavourite: () -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "Series" }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.competition != null) {
                        IconButton(onClick = onToggleFavourite) {
                            if (state.isFavourite) Icon(Icons.Filled.Star, contentDescription = "Remove from favourites", tint = cricketColors.accent)
                            else Icon(Icons.Outlined.StarBorder, contentDescription = "Add to favourites")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Matches", "Table", "Stats").forEachIndexed { index, title -> Tab(tab == index, onClick = { tab = index }, text = { Text(title) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            when (tab) {
                0 -> CompetitionMatches(state.matches, onMatch, onRefresh)
                1 -> StandingsTable(state.standings, onTeam, onRefresh)
                else -> {
                    LaunchedEffect(Unit) { onRequestStats() }
                    StatsTab(state, onPlayer, onRefresh)
                }
            }
        }
    }
}

@Composable
private fun CompetitionMatches(matches: Loadable<MatchBoard>, onMatch: (Int) -> Unit, onRetry: () -> Unit) {
    val board = matches.data
    when {
        matches.isInitialLoad -> return SkeletonList()
        board == null -> return ErrorState(matches.error!!, onRetry)
        board.isEmpty -> return EmptyState("No matches", "No fixtures have been published for this series.")
    }
    board!!
    LazyVerticalGrid(
        GridCells.Adaptive(330.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("Live" to board.live, "Upcoming" to board.upcoming.sortedBy { it.start }, "Results" to board.completed.sortedByDescending { it.start }).forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = title) {
                    Text("${title.uppercase()} · ${list.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list, key = { it.id }) { MatchCard(it, { onMatch(it.id) }) }
            }
        }
    }
}

@Composable
fun StandingsTable(standings: Loadable<Standings>, onTeam: (Int) -> Unit, onRetry: () -> Unit) {
    val table = standings.data
    when {
        standings.isInitialLoad -> return SkeletonList(itemHeight = 48.dp, count = 8)
        table == null -> return ErrorState(standings.error!!, onRetry)
        table.rows.isEmpty() -> return EmptyState("No table", "This series has no points table. Bilateral series usually don't.")
    }
    table!!
    val c = table.columns
    val columns = buildList {
        add("P"); add("W"); add("L")
        if (c.tied) add("T"); if (c.drawn) add("D"); if (c.noResult) add("NR"); if (c.bonus) add("BP")
        add("Pts"); if (c.netRunRate) add("NRR")
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        table.groups.forEach { (group, rows) ->
            item(key = "group-$group") {
                Panel {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                        if (group != null) Text(group.uppercase(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("TEAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(150.dp))
                            columns.forEach { TableCell(it, header = true, wide = it == "NRR") }
                        }
                        rows.forEach { row ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.clickable(onClickLabel = "Open team") { onTeam(row.team.id) }.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.width(150.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${row.position}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                                    Badge(row.team.shortName, row.team.logoUrl, row.team.colorHex, size = 26.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(row.team.shortName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                }
                                val values = buildList {
                                    add(row.played.toString()); add(row.won.toString()); add(row.lost.toString())
                                    if (c.tied) add(row.tied.toString()); if (c.drawn) add(row.drawn.toString()); if (c.noResult) add(row.noResult.toString()); if (c.bonus) add(row.bonus.toString())
                                    add(row.points.toString())
                                    if (c.netRunRate) add(row.netRunRate?.let { (if (it > 0) "+" else "") + String.format(java.util.Locale.US, "%.3f", it) } ?: "–")
                                }
                                values.forEachIndexed { index, value -> TableCell(value, bold = columns[index] == "Pts", wide = columns[index] == "NRR") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("P played · W won · L lost · T tied · D drawn · NR no result · BP bonus points · NRR net run rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TableCell(text: String, header: Boolean = false, bold: Boolean = false, wide: Boolean = false) {
    Text(
        text, modifier = Modifier.width(if (wide) 62.dp else 38.dp), textAlign = TextAlign.End, maxLines = 1,
        fontFamily = if (header) null else ScoreFont, fontSize = if (header) 11.sp else 16.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else if (bold) cricketColors.accent else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StatsTab(state: CompetitionUiState, onPlayer: (Int) -> Unit, onRetry: () -> Unit) {
    val board = state.leaderboard.data
    var comparing by rememberSaveable { mutableStateOf(false) }
    when {
        board == null && state.leaderboard.error != null -> return ErrorState(state.leaderboard.error!!, onRetry)
        board == null -> return Column {
            Text("Adding up scorecards…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            SkeletonList(itemHeight = 180.dp, count = 3)
        }
        board.matches == 0 -> return EmptyState("No statistics yet", "Leaderboards appear after the first match of the series is finished.")
    }
    board!!
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("From ${board.matches} completed match${if (board.matches == 1) "" else "es"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { comparing = true }) {
                    Icon(Icons.Outlined.CompareArrows, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Compare")
                }
            }
        }
        item {
            LeaderPanel("Most runs", board.mostRuns.take(10).map { Triple(it.playerId, it.name, "${it.runs}") to "${it.innings} inns · HS ${it.highest}${if (it.highestNotOut) "*" else ""} · SR ${Formatters.oneDecimal(it.strikeRate)}" }, onPlayer)
        }
        item {
            LeaderPanel("Most wickets", board.mostWickets.take(10).map { Triple(it.playerId, it.name, "${it.wickets}") to "${it.overs} ov · best ${it.best} · econ ${Formatters.rate(it.economy)}" }, onPlayer)
        }
        if (board.mostSixes.isNotEmpty()) {
            item { LeaderPanel("Most sixes", board.mostSixes.take(5).map { Triple(it.playerId, it.name, "${it.sixes}") to "${it.runs} runs · ${it.fours} fours" }, onPlayer) }
        }
    }
    if (comparing) ComparePlayersSheet(board, "Totals from ${board.matches} completed matches of ${state.name}", onDismiss = { comparing = false })
}

@Composable
private fun LeaderPanel(title: String, rows: List<Pair<Triple<Int, String, String>, String>>, onPlayer: (Int) -> Unit) {
    Panel {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (rows.isEmpty()) Text("Nothing recorded yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
            rows.forEachIndexed { index, (main, detail) ->
                val (id, name, value) = main
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open player") { onPlayer(id) }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(value, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if (index == 0) cricketColors.accent else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
