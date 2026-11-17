package com.criczy.app.feature.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.criczy.app.core.data.Communities
import com.criczy.app.core.data.FavouritesRepository
import com.criczy.app.core.model.Team
import com.criczy.app.di.AppContainer
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.components.Badge
import com.criczy.app.ui.latestBoard
import com.criczy.app.ui.theme.cricketColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamPickerState(
    /** Null while loading. Teams playing now or soon come first. */
    val teams: List<Team>? = null,
    val playingNow: Set<Int> = emptySet(),
    val selected: List<Int> = emptyList(),
    val failed: Boolean = false,
    val saving: Boolean = false,
)

class TeamPickerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TeamPickerState())
    val state: StateFlow<TeamPickerState> = _state

    init { load() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(failed = false) }
        try {
            val teams = container.catalog.teams()
            val board = try { container.matches.latestBoard().first() } catch (_: Exception) { null }
            val playing = board?.let { (it.live + it.upcoming).flatMap { m -> listOf(m.home.id, m.away.id) } }.orEmpty()
            _state.update { it.copy(teams = orderTeams(teams, playing), playingNow = playing.toSet()) }
        } catch (_: Exception) {
            _state.update { it.copy(failed = true) }
        }
    }

    fun toggle(id: Int) = _state.update {
        when {
            id in it.selected -> it.copy(selected = it.selected - id)
            it.selected.size >= Communities.MAX_TEAMS -> it
            else -> it.copy(selected = it.selected + id)
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(saving = true) }
        val existing = container.favourites.snapshot().map { it.id }.toSet()
        current.teams.orEmpty().filter { it.id in current.selected && it.id !in existing }
            .forEach { container.favourites.toggle(FavouritesRepository.of(it)) }
        onDone()
    }
}

private val placeholder = Regex("^[A-Z]{1,2}[0-9]{1,2}$")

/** Teams playing now or next (in board order, without repeats), then the rest alphabetically. */
internal fun orderTeams(all: List<Team>, playingIds: List<Int>): List<Team> {
    // The provider lists bracket placeholders ("A1", "B2") as teams; nobody follows those.
    val teams = all.filterNot { placeholder.matches(it.name) }
    val byId = teams.associateBy { it.id }
    val first = playingIds.distinct().mapNotNull { byId[it] }
    val firstIds = first.map { it.id }.toSet()
    return first + teams.filter { it.id !in firstIds }.sortedBy { it.name }
}

@Composable
fun TeamPickerRoute(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: TeamPickerViewModel = viewModel { TeamPickerViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    TeamPickerScreen(state, onToggle = vm::toggle, onContinue = { vm.save(onDone) }, onSkip = onDone, onRetry = { vm.load() })
}

@Composable
fun TeamPickerScreen(state: TeamPickerState, onToggle: (Int) -> Unit, onContinue: () -> Unit, onSkip: () -> Unit, onRetry: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pick your teams", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onSkip) { Text("Skip") }
            }
            Text(
                "Choose up to ${Communities.MAX_TEAMS}. Their matches come first, alerts follow them, and each one gets a fan community.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search teams") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val teams = state.teams
                when {
                    state.failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not load teams.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRetry) { Text("Try again") }
                    }
                    teams == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> {
                        val shown = if (query.isBlank()) teams else teams.filter { it.name.contains(query.trim(), true) || it.shortName.contains(query.trim(), true) }
                        LazyVerticalGrid(GridCells.Adaptive(100.dp), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (query.isBlank() && state.playingNow.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text("PLAYING NOW OR NEXT, THEN A TO Z", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(shown, key = { it.id }) { team ->
                                TeamTile(team, team.id in state.selected, team.id in state.playingNow) { onToggle(team.id) }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onContinue,
                enabled = state.selected.isNotEmpty() && !state.saving,
                colors = ButtonDefaults.buttonColors(containerColor = cricketColors.accent, contentColor = cricketColors.onAccent),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(52.dp),
            ) {
                Text(if (state.selected.isEmpty()) "Pick at least one team" else "Continue with ${state.selected.size} of ${Communities.MAX_TEAMS}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TeamTile(team: Team, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) cricketColors.accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = team.name }
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge(team.shortName, team.logoUrl, team.colorHex, size = 44.dp)
            Spacer(Modifier.height(8.dp))
            Text(team.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (playing) Text("Playing", style = MaterialTheme.typography.labelSmall, color = cricketColors.live)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = cricketColors.accent, modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
    }
}
