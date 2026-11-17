package app.criczy.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.core.data.FavouriteType
import app.criczy.core.data.FavouritesRepository
import app.criczy.core.data.Loadable
import app.criczy.core.data.loadOnce
import app.criczy.core.model.Player
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.Refreshable
import app.criczy.ui.WhileUiVisible
import app.criczy.ui.components.Badge
import app.criczy.ui.components.EmptyState
import app.criczy.ui.components.ErrorState
import app.criczy.ui.components.Panel
import app.criczy.ui.components.SkeletonList
import app.criczy.ui.format.Formatters
import app.criczy.ui.theme.cricketColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.criczy.ui.components.AppTitle
import androidx.compose.runtime.getValue

/** [player] is null when the provider has no profile for the id. */
data class PlayerProfile(val player: Player?, val teamName: String?)

class PlayerViewModel(private val id: Int, private val container: AppContainer) : ViewModel() {
    private val source = Refreshable(viewModelScope) {
        loadOnce { PlayerProfile(container.players.player(id), container.players.teamNameFor(id)) }
    }
    val state: StateFlow<Loadable<PlayerProfile>> = source.state
    val isFavourite: StateFlow<Boolean> = container.favourites.isFavourite(FavouriteType.PLAYER, id).stateIn(viewModelScope, WhileUiVisible, false)

    fun refresh() = source.refresh()
    fun toggleFavourite() {
        val profile = state.value.data ?: return
        val player = profile.player ?: return
        viewModelScope.launch { container.favourites.toggle(FavouritesRepository.of(player, profile.teamName)) }
    }
}

@Composable
fun PlayerRoute(id: Int, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: PlayerViewModel = viewModel(key = "player-$id") { PlayerViewModel(id, container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favourite by vm.isFavourite.collectAsStateWithLifecycle()
    PlayerScreen(state, favourite, onBack, vm::refresh, vm::toggleFavourite)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(state: Loadable<PlayerProfile>, isFavourite: Boolean, onBack: () -> Unit, onRetry: () -> Unit, onToggleFavourite: () -> Unit) {
    val profile = state.data
    val player = profile?.player
    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppTitle(player?.name ?: "Player") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (player != null) {
                        IconButton(onClick = onToggleFavourite) {
                            if (isFavourite) Icon(Icons.Filled.Star, contentDescription = "Remove from favourites", tint = cricketColors.accent)
                            else Icon(Icons.Outlined.StarBorder, contentDescription = "Add to favourites")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isInitialLoad -> SkeletonList(count = 3, itemHeight = 140.dp)
                profile == null -> ErrorState(state.error!!, onRetry)
                player == null -> EmptyState("Profile not available", "The score service has no profile for this player.")
                else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(player.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), player.imageUrl, null, size = 96.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(player.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                listOfNotNull(player.roleLabel, profile.teamName ?: player.nationality).joinToString(" · ").ifBlank { "Cricketer" },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val facts = listOfNotNull(
                        player.battingStyle?.let { "Batting" to it },
                        player.bowlingStyle?.let { "Bowling" to it },
                        Formatters.birthDate(player.dateOfBirth)?.let { born -> "Born" to (born + (Formatters.age(player.dateOfBirth)?.let { " (age $it)" } ?: "")) },
                        player.birthPlace?.let { "Birthplace" to it },
                        player.nationality?.let { "Nationality" to it },
                        player.height?.let { "Height" to it },
                    )
                    if (facts.isNotEmpty()) {
                        Panel {
                            FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                facts.forEach { (label, value) ->
                                    Column(Modifier.widthIn(min = 130.dp)) {
                                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(value, style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    }
                    player.didYouKnow?.let {
                        Panel {
                            Column(Modifier.padding(14.dp)) {
                                Text("DID YOU KNOW", style = MaterialTheme.typography.labelSmall, color = cricketColors.accent)
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    player.bio?.let {
                        Text("ABOUT", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Career statistics aren't available from the score service. Match and tournament figures are in each scorecard and under a series' Stats tab.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
