package com.criczy.app.feature.favourites

import com.criczy.app.ui.components.bottomBarPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.criczy.app.core.data.FavouritesRepository
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.Team
import com.criczy.app.ui.FavouriteStatus
import com.criczy.app.ui.favouriteStatus
import com.criczy.app.ui.followedMatches
import com.criczy.app.ui.latestBoard
import com.criczy.app.ui.suggestedTeams
import com.criczy.app.ui.components.MatchCard
import kotlinx.coroutines.flow.combine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.criczy.app.core.data.Favourite
import com.criczy.app.core.data.FavouriteType
import com.criczy.app.di.AppContainer
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.WhileUiVisible
import com.criczy.app.ui.components.AppTitle
import com.criczy.app.ui.components.Badge
import com.criczy.app.ui.components.MessageState
import com.criczy.app.ui.theme.cricketColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.criczy.app.feature.community.CommunityRoute

class FavouritesViewModel(private val container: AppContainer) : ViewModel() {
    /** Null until the first database read, so the empty state does not flash on open. */
    val state: StateFlow<List<Favourite>?> = container.favourites.all.map<List<Favourite>, List<Favourite>?> { it }
        .stateIn(viewModelScope, WhileUiVisible, null)

    /** What everything followed is doing, from the same board the home screen shows. */
    val dashboard: StateFlow<FavouritesDashboard> = combine(container.favourites.all, container.matches.latestBoard()) { favourites, board ->
        FavouritesDashboard(
            matches = followedMatches(board, favourites),
            status = favourites.mapNotNull { favourite -> favouriteStatus(board, favourite)?.let { favourite.key to it } }.toMap(),
            suggestions = suggestedTeams(board, favourites.filter { it.type == FavouriteType.TEAM }.mapTo(HashSet()) { it.id }),
        )
    }.stateIn(viewModelScope, WhileUiVisible, FavouritesDashboard())

    fun remove(favourite: Favourite) { viewModelScope.launch { container.favourites.toggle(favourite) } }
    fun follow(team: Team) { viewModelScope.launch { container.favourites.toggle(FavouritesRepository.of(team)) } }
}

/** The personal side of favourites: followed matches, a status per favourite, and teams to follow. */
data class FavouritesDashboard(
    val matches: List<Match> = emptyList(),
    val status: Map<String, FavouriteStatus> = emptyMap(),
    val suggestions: List<Team> = emptyList(),
)

private val Favourite.key: String get() = "${type}-${id}"

@Composable
fun FavouritesRoute(onMatch: (Int) -> Unit, onTeam: (Int) -> Unit, onCompetition: (Int, String) -> Unit, onPlayer: (Int) -> Unit, onBrowseTeams: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FavouritesViewModel = viewModel { FavouritesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    FavouritesScreen(state, onMatch, onTeam, onCompetition, onPlayer, onBrowseTeams, vm::remove, dashboard, community = { CommunityRoute() }, onFollowTeam = vm::follow)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    favourites: List<Favourite>?,
    onMatch: (Int) -> Unit,
    onTeam: (Int) -> Unit,
    onCompetition: (Int, String) -> Unit,
    onPlayer: (Int) -> Unit,
    onBrowseTeams: () -> Unit,
    onRemove: (Favourite) -> Unit,
    dashboard: FavouritesDashboard = FavouritesDashboard(),
    initialTab: Int = 0,
    /** The fan community for the followed teams, shown in the second tab. */
    community: @Composable () -> Unit = {},
    onFollowTeam: (Team) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Scaffold(topBar = { TopAppBar(title = { AppTitle("Favourites") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Following", "Community").forEachIndexed { index, title ->
                    Tab(tab == index, onClick = { tab = index }, text = { Text(title) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                tab == 1 -> community()
                favourites == null -> Unit
                favourites.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomBarPadding())) {
                    MessageState(
                        Icons.Outlined.StarBorder, "Nothing followed yet",
                        "Tap the star on a team, series, match or player. Followed teams and series are highlighted on the home screen and can send match alerts.",
                        actionLabel = "Browse teams", onAction = onBrowseTeams,
                    )
                    if (dashboard.suggestions.isNotEmpty()) {
                        SectionTitle("Playing now · tap to follow")
                        TeamSuggestions(dashboard.suggestions, onFollowTeam)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = bottomBarPadding(24.dp))) {
                    // Everything followed, in play or next, before the lists.
                    if (dashboard.matches.isNotEmpty()) {
                        item(key = "your-matches") { SectionTitle("Your matches") }
                        items(dashboard.matches, key = { "match-card-${it.id}" }) { match ->
                            MatchCard(match, onClick = { onMatch(match.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), isFavourite = true)
                        }
                    }
                    listOf(
                        FavouriteType.TEAM to "Teams", FavouriteType.COMPETITION to "Series",
                        FavouriteType.MATCH to "Matches", FavouriteType.PLAYER to "Players",
                    ).forEach { (type, title) ->
                        val group = favourites.filter { it.type == type }
                        if (group.isNotEmpty()) {
                            item(key = title) { SectionTitle(title) }
                            items(group, key = { it.key }) { favourite ->
                                FavouriteRow(favourite, dashboard.status[favourite.key], onRemove) {
                                    when (favourite.type) {
                                        FavouriteType.TEAM -> onTeam(favourite.id)
                                        FavouriteType.COMPETITION -> onCompetition(favourite.id, favourite.title)
                                        FavouriteType.MATCH -> onMatch(favourite.id)
                                        FavouriteType.PLAYER -> onPlayer(favourite.id)
                                    }
                                }
                            }
                        }
                    }
                    if (dashboard.suggestions.isNotEmpty()) {
                        item(key = "suggestions") {
                            Column {
                                SectionTitle("More teams playing now")
                                TeamSuggestions(dashboard.suggestions, onFollowTeam)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))
}

/** A followed item with what it is doing now under its name, live in red. */
@Composable
private fun FavouriteRow(favourite: Favourite, status: FavouriteStatus?, onRemove: (Favourite) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(favourite.subtitle?.takeIf { favourite.type == FavouriteType.TEAM } ?: favourite.title, favourite.imageUrl, favourite.colorHex, size = 40.dp, photo = favourite.type == FavouriteType.PLAYER)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(favourite.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.live) {
                        Box(Modifier.size(7.dp).background(cricketColors.live, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        status.text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (status.live) cricketColors.live else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (status.live) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            } else {
                favourite.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        IconButton(onClick = { onRemove(favourite) }) { Icon(Icons.Filled.Star, contentDescription = "Remove ${favourite.title} from favourites", tint = cricketColors.accent) }
    }
}

/** Teams with a match on now or soon, each followed with one tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamSuggestions(teams: List<Team>, onFollow: (Team) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        teams.forEach { team ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    .clickable(onClickLabel = "Follow ${team.name}") { onFollow(team) }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Badge(team.shortName, team.logoUrl, team.colorHex, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(team.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = cricketColors.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
