package app.criczy.feature.search

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criczy.core.data.SearchResults
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.WhileUiVisible
import app.criczy.ui.components.Badge
import app.criczy.ui.components.EmptyState
import app.criczy.ui.format.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(val query: String = "", val results: SearchResults = SearchResults(), val searched: Boolean = false)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    val text: StateFlow<String> = query

    val state: StateFlow<SearchUiState> = query
        .debounce { if (it.isBlank()) 0L else 250L }
        .mapLatest { q -> SearchUiState(q, container.search.search(q), searched = q.trim().length >= 2) }
        .stateIn(viewModelScope, WhileUiVisible, SearchUiState())

    fun setQuery(value: String) { query.value = value }
}

@Composable
fun SearchRoute(onBack: () -> Unit, onMatch: (Int) -> Unit, onTeam: (Int) -> Unit, onCompetition: (Int, String) -> Unit, onPlayer: (Int) -> Unit) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val text by vm.text.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    SearchScreen(text, state, vm::setQuery, onBack, onMatch, onTeam, onCompetition, onPlayer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    text: String,
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onMatch: (Int) -> Unit,
    onTeam: (Int) -> Unit,
    onCompetition: (Int, String) -> Unit,
    onPlayer: (Int) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = text, onValueChange = onQuery, singleLine = true,
                        placeholder = { Text("Teams, series, matches, players") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { if (text.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") } },
            )
        },
    ) { padding ->
        val r = state.results
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.searched -> EmptyState("Search Criczy", "Type at least two letters. Players appear once you've opened a match they played in.")
                r.isEmpty -> EmptyState("Nothing found", "No team, series, match or player matches “${state.query.trim()}”.")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (r.matches.isNotEmpty()) {
                        item { Heading("Matches") }
                        items(r.matches, key = { "m${it.id}" }) { m ->
                            ResultRow("${m.home.shortName} v ${m.away.shortName}", "${m.competition.name} · ${Formatters.statusLine(m)}", m.home.shortName, m.home.logoUrl, m.home.colorHex) { onMatch(m.id) }
                        }
                    }
                    if (r.teams.isNotEmpty()) {
                        item { Heading("Teams") }
                        items(r.teams, key = { "t${it.id}" }) { t -> ResultRow(t.name, t.shortName, t.shortName, t.logoUrl, t.colorHex) { onTeam(t.id) } }
                    }
                    if (r.competitions.isNotEmpty()) {
                        item { Heading("Series") }
                        items(r.competitions, key = { "c${it.id}" }) { c ->
                            ResultRow(c.name, Formatters.dateRange(c.start, c.end) ?: c.formatLabel.orEmpty(), c.name, c.logoUrl, null) { onCompetition(c.id, c.name) }
                        }
                    }
                    if (r.players.isNotEmpty()) {
                        item { Heading("Players") }
                        items(r.players, key = { "p${it.id}" }) { p ->
                            ResultRow(p.name, listOfNotNull(p.role, p.teamName).joinToString(" · "), p.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), p.imageUrl, null, photo = true) { onPlayer(p.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(title: String, subtitle: String, badge: String, imageUrl: String?, colorHex: String?, photo: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge(badge, imageUrl, colorHex, size = 38.dp, photo = photo)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
