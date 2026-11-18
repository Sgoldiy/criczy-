package com.criczy.app.feature.teams

import com.criczy.app.ads.AdUnit
import com.criczy.app.ads.BannerAdSlot
import com.criczy.app.ui.teamForm
import com.criczy.app.ui.FormResult
import com.criczy.app.core.model.Match
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.criczy.app.ui.theme.ScoreFont
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.criczy.app.core.data.FavouriteType
import com.criczy.app.core.data.FavouritesRepository
import com.criczy.app.core.data.Loadable
import com.criczy.app.core.data.RefreshPolicy
import com.criczy.app.core.data.loadOnce
import com.criczy.app.core.data.pollingFlow
import com.criczy.app.core.model.MatchBoard
import com.criczy.app.core.model.Team
import com.criczy.app.di.AppContainer
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.Refreshable
import com.criczy.app.ui.WhileUiVisible
import com.criczy.app.ui.components.AppTitle
import com.criczy.app.ui.components.Badge
import com.criczy.app.ui.components.EmptyState
import com.criczy.app.ui.components.ErrorState
import com.criczy.app.ui.components.MatchCard
import com.criczy.app.ui.components.Panel
import com.criczy.app.ui.components.SkeletonList
import com.criczy.app.ui.theme.cricketColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
                title = { AppTitle("Teams") },
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
                        AppTitle(team?.name ?: "Team")
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
                    val form = team?.let { teamForm(it.id, board.completed) }.orEmpty()
                    if (form.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "form") { FormStrip(form, onMatch) }
                    }
                    listOf("Live" to board.live, "Upcoming" to board.upcoming.sortedBy { it.start }, "Recent results" to board.completed.sortedByDescending { it.start }).forEach { (title, list) ->
                        if (list.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = title) { Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(list, key = { it.id }) { MatchCard(it, { onMatch(it.id) }) }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "ad") { BannerAdSlot(AdUnit.BANNER_BROWSE) }
                }
            }
        }
    }
}

/** The last five results as coloured letters, newest first; each opens its match. */
@Composable
private fun FormStrip(form: List<Pair<Match, FormResult>>, onMatch: (Int) -> Unit) {
    Column {
        Text("FORM", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            form.forEach { (match, result) ->
                val color = when (result) {
                    FormResult.WIN -> cricketColors.six
                    FormResult.LOSS -> cricketColors.wicket
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)).border(1.5.dp, color, CircleShape)
                        .clickable(onClickLabel = "Open ${match.title}") { onMatch(match.id) }
                        .semantics { contentDescription = "${match.title}, ${result.label}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(result.letter, fontFamily = ScoreFont, fontWeight = FontWeight.Bold, color = color)
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("newest first", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

