package app.criczy.feature.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.core.data.FavouriteType
import app.criczy.core.data.FavouritesRepository
import app.criczy.core.data.Loadable
import app.criczy.core.data.RefreshPolicy
import app.criczy.core.data.loadOnce
import app.criczy.core.data.pollingFlow
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.Team
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.Refreshable
import app.criczy.ui.WhileUiVisible
import app.criczy.ui.components.Badge
import app.criczy.ui.components.EmptyState
import app.criczy.ui.components.ErrorState
import app.criczy.ui.components.MatchCard
import app.criczy.ui.components.Panel
import app.criczy.ui.components.SkeletonList
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeamsViewModel(private val container: AppContainer) : ViewModel() {
    private val source = Refreshable(viewModelScope) { loadOnce { container.catalog.teams() } }
    val state: StateFlow<Loadable<List<Team>>> = source.state
    val favouriteIds: StateFlow<Set<Int>> = combine(container.favourites.all, MutableStateFlow(Unit)) { all, _ ->
        all.filter { it.type == FavouriteType.TEAM }.mapTo(HashSet()) { it.id }
    }.stateIn(viewModelScope, WhileUiVisible, emptySet())

    fun refresh() = source.refresh()
    fun toggle(team: Team) { viewModelScope.launch { container.favourites.toggle(FavouritesRepository.of(team)) } }
}

@Composable
fun TeamsRoute(onBack: () -> Unit, onTeam: (Int) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TeamsViewModel = viewModel { TeamsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favourites by vm.favouriteIds.collectAsStateWithLifecycle()
    TeamsScreen(state, favourites, onBack, onTeam, vm::toggle, vm::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    state: Loadable<List<Team>>,
    favouriteIds: Set<Int>,
    onBack: () -> Unit,
    onTeam: (Int) -> Unit,
    onToggleFavourite: (Team) -> Unit,
    onRetry: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TEAMS", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val teams = state.data
            when {
                state.isInitialLoad -> SkeletonList(itemHeight = 60.dp, count = 8)
                teams == null -> ErrorState(state.error!!, onRetry)
                else -> {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text("Filter ${teams.size} teams") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    val shown = teams.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.shortName.equals(query.trim(), true) }
                        .sortedBy { it.id !in favouriteIds }
                    if (shown.isEmpty()) EmptyState("No teams found", "No team matches “${query.trim()}”.")
                    LazyVerticalGrid(
                        GridCells.Adaptive(300.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shown, key = { it.id }) { team ->
                            Panel(Modifier.clickable(onClickLabel = "Open team") { onTeam(team.id) }) {
                                Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Badge(team.shortName, team.logoUrl, team.colorHex, size = 38.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(team.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(team.shortName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onToggleFavourite(team) }) {
                                        if (team.id in favouriteIds) Icon(Icons.Filled.Star, contentDescription = "Unfollow ${team.name}", tint = cricketColors.accent)
                                        else Icon(Icons.Outlined.StarBorder, contentDescription = "Follow ${team.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- detail --------------------------------------------------------------------------------

data class TeamUiState(val team: Team? = null, val matches: Loadable<MatchBoard> = Loadable(), val isFavourite: Boolean = false)

class TeamViewModel(private val id: Int, private val container: AppContainer) : ViewModel() {
    private val matches = Refreshable(viewModelScope) {
        pollingFlow(nextDelayMs = RefreshPolicy::forBoard) { container.matches.teamMatches(id) }
    }
    private val team = MutableStateFlow<Team?>(null)

    init {
        viewModelScope.launch {
            team.value = try {
                container.catalog.teams().firstOrNull { it.id == id }
            } catch (_: Exception) {
                null
            }
        }
    }

    val state: StateFlow<TeamUiState> = combine(team, matches.state, container.favourites.isFavourite(FavouriteType.TEAM, id)) { known, m, fav ->
        // Fall back to the team as it appears in its own fixtures when the catalogue is unavailable.
        val resolved = known ?: m.data?.all?.firstNotNullOfOrNull { it.team(id) }
        TeamUiState(resolved, m, fav)
    }.stateIn(viewModelScope, WhileUiVisible, TeamUiState())

    fun refresh() = matches.refresh()
    fun toggleFavourite() {
        val target = state.value.team ?: return
        viewModelScope.launch { container.favourites.toggle(FavouritesRepository.of(target)) }
    }
}

@Composable
fun TeamRoute(id: Int, onBack: () -> Unit, onMatch: (Int) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TeamViewModel = viewModel(key = "team-$id") { TeamViewModel(id, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    TeamScreen(state, onBack, onMatch, vm::refresh, vm::toggleFavourite)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(state: TeamUiState, onBack: () -> Unit, onMatch: (Int) -> Unit, onRetry: () -> Unit, onToggleFavourite: () -> Unit) {
    val team = state.team
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (team != null) { Badge(team.shortName, team.logoUrl, team.colorHex, size = 32.dp); Spacer(Modifier.width(10.dp)) }
                        Text(team?.name ?: "Team", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (team != null) {
                        IconButton(onClick = onToggleFavourite) {
                            if (state.isFavourite) Icon(Icons.Filled.Star, contentDescription = "Unfollow team", tint = cricketColors.accent)
                            else Icon(Icons.Outlined.StarBorder, contentDescription = "Follow team")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val board = state.matches.data
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.matches.isInitialLoad -> SkeletonList()
                board == null -> ErrorState(state.matches.error!!, onRetry)
                board.isEmpty -> EmptyState("No matches", "No recent or upcoming matches are listed for this team.")
                else -> LazyVerticalGrid(
                    GridCells.Adaptive(330.dp), contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!state.isFavourite) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Follow this team to see its matches first and get match alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    listOf("Live" to board.live, "Upcoming" to board.upcoming.sortedBy { it.start }, "Recent results" to board.completed.sortedByDescending { it.start }).forEach { (title, list) ->
                        if (list.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = title) { Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(list, key = { it.id }) { MatchCard(it, { onMatch(it.id) }) }
                        }
                    }
                }
            }
        }
    }
}
