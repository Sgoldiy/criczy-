package app.criczy.feature.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criczy.core.data.FavouriteType
import app.criczy.core.data.FavouritesRepository
import app.criczy.core.data.LoadError
import app.criczy.core.data.Loadable
import app.criczy.core.data.RefreshPolicy
import app.criczy.core.data.keepLastData
import app.criczy.core.data.loadOnce
import app.criczy.core.data.pollingFlow
import app.criczy.core.model.Highlight
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.MatchState
import app.criczy.core.model.Over
import app.criczy.core.network.CricketApiException
import app.criczy.di.AppContainer
import app.criczy.ui.Refreshable
import app.criczy.ui.WhileUiVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The scorecard lookup result; [detail] is null when the provider does not know the match. */
data class MatchLookup(val detail: MatchDetail?)

enum class ScoreEventType { FOUR, SIX, WICKET }

/** Something worth celebrating between two refreshes. [id] changes for every new event. */
data class ScoreEvent(val type: ScoreEventType, val id: Long)

data class CommentaryUiState(
    val inningsNumber: Int? = null,
    /** Newest over first. */
    val overs: List<Over> = emptyList(),
    val loading: Boolean = true,
    val error: LoadError? = null,
    val canLoadOlder: Boolean = false,
    val loadingOlder: Boolean = false,
)

/** Detects boundaries and wickets by comparing consecutive scorecards of the same innings. */
internal fun detectScoreEvent(previous: MatchDetail?, current: MatchDetail, id: Long): ScoreEvent? {
    val before = previous?.currentInnings ?: return null
    val now = current.currentInnings ?: return null
    if (before.number != now.number || current.match.state == MatchState.COMPLETED) return null
    val type = when {
        now.score.wickets > before.score.wickets -> ScoreEventType.WICKET
        now.score.runs - before.score.runs == 6 -> ScoreEventType.SIX
        now.score.runs - before.score.runs == 4 -> ScoreEventType.FOUR
        else -> return null
    }
    return ScoreEvent(type, id)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModel(private val matchId: Int, private val container: AppContainer) : ViewModel() {

    private val lookup = Refreshable(viewModelScope) {
        pollingFlow(nextDelayMs = { found: MatchLookup -> found.detail?.match?.state?.let(RefreshPolicy::forMatch) }) {
            MatchLookup(container.matches.detail(matchId))
        }
    }
    val detail: StateFlow<Loadable<MatchLookup>> = lookup.state

    private val detailData = detail.map { it.data?.detail }

    val isFavourite: StateFlow<Boolean> = container.favourites.isFavourite(FavouriteType.MATCH, matchId)
        .stateIn(viewModelScope, WhileUiVisible, false)

    val servingCached: StateFlow<Boolean> = container.connection.servingCached

    val scoreEvent: StateFlow<ScoreEvent?> = detailData
        .runningFold(Triple<MatchDetail?, ScoreEvent?, Long>(null, null, 0L)) { (previous, _, counter), current ->
            if (current == null) Triple(previous, null, counter)
            else Triple(current, detectScoreEvent(previous, current, counter + 1), counter + 1)
        }
        .map { it.second }
        .stateIn(viewModelScope, WhileUiVisible, null)

    // ---- commentary ----------------------------------------------------------------------

    private val chosenInnings = MutableStateFlow<Int?>(null)
    private val olderOvers = MutableStateFlow<Map<Int, List<Over>>>(emptyMap())
    private val loadingOlder = MutableStateFlow(false)

    /** Changes whenever the visible innings has new balls, which is when the feed must refetch. */
    private data class FeedKey(val innings: Int, val runs: Int, val wickets: Int, val overs: String)

    private val feedKey = combine(detailData, chosenInnings) { data, chosen ->
        val innings = data?.innings?.firstOrNull { it.number == chosen } ?: data?.currentInnings
        innings?.let { FeedKey(it.number, it.score.runs, it.score.wickets, it.score.overs) }
    }.distinctUntilChanged()

    private val latestPage = feedKey.flatMapLatest { key ->
        if (key == null) flowOf(null)
        else loadOnce { container.matches.commentary(matchId, key.innings) }.map { key.innings to it }
    }

    val commentary: StateFlow<CommentaryUiState> = combine(latestPage, olderOvers, loadingOlder) { page, older, busy ->
        if (page == null) return@combine CommentaryUiState(loading = detail.value.loading)
        val (innings, loadable) = page
        val merged = LinkedHashMap<Int, Over>()
        older[innings].orEmpty().forEach { merged[it.number] = it }
        loadable.data?.overs?.forEach { merged[it.number] = it }
        val overs = merged.values.sortedByDescending { it.number }
        CommentaryUiState(
            inningsNumber = innings,
            overs = overs,
            loading = loadable.loading,
            error = loadable.error,
            canLoadOlder = (overs.lastOrNull()?.number ?: 1) > 1,
            loadingOlder = busy,
        )
    }
        // Keep the previous overs on screen while the next page of the same innings loads.
        .runningFold(CommentaryUiState()) { previous, next ->
            if (next.overs.isEmpty() && next.loading && previous.inningsNumber == next.inningsNumber) {
                previous.copy(loading = true)
            } else {
                next
            }
        }
        .stateIn(viewModelScope, WhileUiVisible, CommentaryUiState())

    fun selectCommentaryInnings(number: Int) {
        chosenInnings.value = number
    }

    fun loadOlderCommentary() {
        val current = commentary.value
        val innings = current.inningsNumber ?: return
        val cursor = current.overs.lastOrNull()?.number ?: return
        if (cursor <= 1 || loadingOlder.value) return
        loadingOlder.value = true
        viewModelScope.launch {
            try {
                val page = container.matches.commentary(matchId, innings, olderThanOver = cursor)
                olderOvers.update { known ->
                    known + (innings to (known[innings].orEmpty() + current.overs + page.overs).distinctBy { it.number })
                }
            } catch (_: CricketApiException) {
                // Leave the list as it is; the "load earlier" control stays available to retry.
            } finally {
                loadingOlder.value = false
            }
        }
    }

    // ---- analysis ------------------------------------------------------------------------

    private val analysisWanted = MutableStateFlow(false)

    /** Refetch ball data at most once per completed over, and never for a finished match twice. */
    private val analysisKey = detailData.map { data ->
        data?.innings?.map { it.number to it.score.overs.substringBefore('.') }
    }.distinctUntilChanged()

    val analysis: StateFlow<Loadable<Map<Int, InningsBallByBall>>> =
        combine(analysisWanted, analysisKey) { wanted, key -> key.takeIf { wanted } }
            .flatMapLatest { key ->
                if (key == null) flowOf(Loadable(loading = true))
                else loadOnce {
                    coroutineScope {
                        key.map { (number, _) -> async { number to container.matches.fullInnings(matchId, number) } }
                            .awaitAll().toMap()
                    }
                }
            }
            .keepLastData()
            .stateIn(viewModelScope, WhileUiVisible, Loadable())

    fun requestAnalysis() {
        analysisWanted.value = true
    }

    // ---- highlights ----------------------------------------------------------------------

    private val highlightsWanted = MutableStateFlow(false)

    val highlights: StateFlow<List<Highlight>> =
        combine(highlightsWanted, detailData.map { it?.match }.distinctUntilChanged { a, b -> a?.id == b?.id && a?.state == b?.state }) { wanted, match ->
            match.takeIf { wanted }
        }.flatMapLatest { match ->
            if (match == null) flowOf(emptyList())
            else loadOnce { container.matches.highlights(match) }.map { it.data.orEmpty() }
        }.stateIn(viewModelScope, WhileUiVisible, emptyList())

    fun requestHighlights() {
        highlightsWanted.value = true
    }

    // ---- actions -------------------------------------------------------------------------

    fun refresh() = lookup.refresh()

    fun toggleFavourite() {
        val match = detail.value.data?.detail?.match ?: return
        viewModelScope.launch { container.favourites.toggle(FavouritesRepository.of(match)) }
    }
}
