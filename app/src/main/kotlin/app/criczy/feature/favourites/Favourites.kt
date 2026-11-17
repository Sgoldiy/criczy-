package app.criczy.feature.favourites

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.core.data.Favourite
import app.criczy.core.data.FavouriteType
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.WhileUiVisible
import app.criczy.ui.components.Badge
import app.criczy.ui.components.MessageState
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavouritesViewModel(private val container: AppContainer) : ViewModel() {
    /** Null until the first database read, so the empty state does not flash on open. */
    val state: StateFlow<List<Favourite>?> = container.favourites.all.map<List<Favourite>, List<Favourite>?> { it }
        .stateIn(viewModelScope, WhileUiVisible, null)

    fun remove(favourite: Favourite) { viewModelScope.launch { container.favourites.toggle(favourite) } }
}

@Composable
fun FavouritesRoute(onMatch: (Int) -> Unit, onTeam: (Int) -> Unit, onCompetition: (Int, String) -> Unit, onPlayer: (Int) -> Unit, onBrowseTeams: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FavouritesViewModel = viewModel { FavouritesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    FavouritesScreen(state, onMatch, onTeam, onCompetition, onPlayer, onBrowseTeams, vm::remove)
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
) {
    Scaffold(topBar = { TopAppBar(title = { Text("FAVOURITES", style = MaterialTheme.typography.headlineSmall) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                favourites == null -> Unit
                favourites.isEmpty() -> MessageState(
                    Icons.Outlined.StarBorder, "Nothing followed yet",
                    "Tap the star on a team, series, match or player. Followed teams and series are highlighted on the home screen and can send match alerts.",
                    actionLabel = "Browse teams", onAction = onBrowseTeams,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    listOf(
                        FavouriteType.TEAM to "Teams", FavouriteType.COMPETITION to "Series",
                        FavouriteType.MATCH to "Matches", FavouriteType.PLAYER to "Players",
                    ).forEach { (type, title) ->
                        val group = favourites.filter { it.type == type }
                        if (group.isNotEmpty()) {
                            item(key = title) {
                                Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                            }
                            items(group, key = { "${it.type}-${it.id}" }) { favourite ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        when (favourite.type) {
                                            FavouriteType.TEAM -> onTeam(favourite.id)
                                            FavouriteType.COMPETITION -> onCompetition(favourite.id, favourite.title)
                                            FavouriteType.MATCH -> onMatch(favourite.id)
                                            FavouriteType.PLAYER -> onPlayer(favourite.id)
                                        }
                                    }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Badge(favourite.subtitle?.takeIf { favourite.type == FavouriteType.TEAM } ?: favourite.title, favourite.imageUrl, favourite.colorHex, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(favourite.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        favourite.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                    IconButton(onClick = { onRemove(favourite) }) { Icon(Icons.Filled.Star, contentDescription = "Remove ${favourite.title} from favourites", tint = cricketColors.accent) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
