package app.criczy.core.data

import app.criczy.core.analytics.Leaderboard
import app.criczy.core.analytics.Leaderboards
import app.criczy.core.model.Competition
import app.criczy.core.model.Highlight
import app.criczy.core.model.HomeFeed
import app.criczy.core.model.InningsBallByBall
import app.criczy.core.model.Match
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.MatchDetail
import app.criczy.core.model.MatchState
import app.criczy.core.model.NewsArticle
import app.criczy.core.model.Player
import app.criczy.core.model.Standings
import app.criczy.core.model.Team
import app.criczy.core.network.CricketApiException
import app.criczy.core.network.CricketRemoteDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** How often to refresh, chosen from what is actually happening in the data. */
object RefreshPolicy {
    const val OPEN_LIVE_MATCH_MS = 6_000L
    const val OPEN_PAUSED_MATCH_MS = 60_000L
    const val BOARD_WITH_LIVE_MS = 20_000L
    const val BOARD_IDLE_MS = 120_000L

    /** Null once the match is over: a finished scorecard does not change. */
    fun forMatch(state: MatchState): Long? = when (state) {
        MatchState.LIVE -> OPEN_LIVE_MATCH_MS
        MatchState.IN_PROGRESS -> OPEN_PAUSED_MATCH_MS
        MatchState.UPCOMING -> OPEN_PAUSED_MATCH_MS
        MatchState.COMPLETED -> null
    }

    fun forBoard(board: MatchBoard): Long =
        if (board.live.any { it.state == MatchState.LIVE }) BOARD_WITH_LIVE_MS else BOARD_IDLE_MS
}

class MatchRepository(
    private val remote: CricketRemoteDataSource,
    private val seenPlayers: SeenPlayerDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _lastBoard = MutableStateFlow(MatchBoard.Empty)

    /** The most recent unfiltered board, shared with search and the widget. */
    val lastBoard: StateFlow<MatchBoard> = _lastBoard.asStateFlow()

    suspend fun board(): MatchBoard =
        remote.matchBoard(upcoming = 40, live = 40, completed = 40).also { _lastBoard.value = it }

    suspend fun competitionMatches(competitionId: Int): MatchBoard =
        remote.matchBoard(upcoming = 100, live = 20, completed = 100, competitionId = competitionId)

    suspend fun teamMatches(teamId: Int): MatchBoard =
        remote.matchBoard(upcoming = 20, live = 5, completed = 20, teamId = teamId)

    suspend fun detail(matchId: Int): MatchDetail? = remote.matchDetail(matchId)?.also { remember(it) }

    suspend fun commentary(matchId: Int, innings: Int, olderThanOver: Int? = null): InningsBallByBall =
        remote.commentaryPage(matchId, innings, olderThanOver)

    suspend fun fullInnings(matchId: Int, innings: Int): InningsBallByBall = remote.fullInnings(matchId, innings)

    suspend fun highlights(match: Match): List<Highlight> = remote.highlights(match.id, match.competition.id)

    private suspend fun remember(detail: MatchDetail) {
        if (detail.players.isEmpty()) return
        val time = now()
        seenPlayers.upsert(
            detail.players.map {
                SeenPlayerEntity(
                    id = it.id,
                    name = it.name,
                    teamId = it.teamId,
                    teamName = it.teamId?.let(detail.match::team)?.name,
                    role = it.roleLabel,
                    imageUrl = it.imageUrl,
                    lastSeenAt = time,
                )
            },
        )
    }
}

class CatalogRepository(private val remote: CricketRemoteDataSource) {
    private val teamsLock = Mutex()
    private var cachedTeams: List<Team>? = null
    private val competitionsLock = Mutex()
    private var cachedCompetitions: List<Competition>? = null

    /** The team list rarely changes, so one fetch per app run is enough. */
    suspend fun teams(): List<Team> = teamsLock.withLock {
        cachedTeams ?: remote.teams().sortedBy { it.name }.also { cachedTeams = it }
    }

    suspend fun competitions(forceRefresh: Boolean = false): List<Competition> = competitionsLock.withLock {
        cachedCompetitions?.takeUnless { forceRefresh }
            ?: remote.competitions(limit = 80).also { cachedCompetitions = it }
    }

    suspend fun standings(competitionId: Int): Standings = remote.standings(competitionId)

    /**
     * Totals for a competition, computed from the scorecards of its most recent completed
     * matches (at most [maxMatches]) because the provider publishes no leaderboards.
     * Scorecards that fail to load are skipped rather than failing the whole table.
     */
    suspend fun leaderboard(completed: List<Match>, maxMatches: Int = 40): Leaderboard = coroutineScope {
        val limiter = Semaphore(4)
        val details = completed.take(maxMatches).map { match ->
            async {
                limiter.withPermit {
                    try {
                        remote.matchDetail(match.id)
                    } catch (_: CricketApiException) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
        Leaderboards.compute(details)
    }
}

class PlayerRepository(
    private val remote: CricketRemoteDataSource,
    private val seenPlayers: SeenPlayerDao,
) {
    /** Full profile from the provider, or null if it does not know the player. */
    suspend fun player(id: Int): Player? = remote.players(listOf(id)).firstOrNull()

    suspend fun teamNameFor(playerId: Int): String? = seenPlayers.byId(playerId)?.teamName
}

class NewsRepository(private val remote: CricketRemoteDataSource) {
    suspend fun news(): List<NewsArticle> = remote.news(limit = 40)
    suspend fun homeFeed(): HomeFeed = remote.homeFeed()
}

data class Favourite(
    val type: FavouriteType,
    val id: Int,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val colorHex: String?,
)

class FavouritesRepository(
    private val dao: FavouriteDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val all: Flow<List<Favourite>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    fun isFavourite(type: FavouriteType, id: Int): Flow<Boolean> = dao.observeIs(type.name, id)

    suspend fun snapshot(): List<Favourite> = dao.all().mapNotNull { it.toModel() }

    /** Returns the new state: true if it is now a favourite. */
    suspend fun toggle(favourite: Favourite): Boolean {
        val exists = dao.exists(favourite.type.name, favourite.id)
        if (exists) {
            dao.delete(favourite.type.name, favourite.id)
        } else {
            dao.insert(
                FavouriteEntity(
                    favourite.type.name, favourite.id, favourite.title, favourite.subtitle,
                    favourite.imageUrl, favourite.colorHex, now(),
                ),
            )
        }
        return !exists
    }

    private fun FavouriteEntity.toModel(): Favourite? {
        val parsed = FavouriteType.entries.firstOrNull { it.name == type } ?: return null
        return Favourite(parsed, refId, title, subtitle, imageUrl, colorHex)
    }

    companion object {
        fun of(team: Team) = Favourite(FavouriteType.TEAM, team.id, team.name, team.shortName, team.logoUrl, team.colorHex)

        fun of(match: Match) = Favourite(
            FavouriteType.MATCH, match.id, "${match.home.name} v ${match.away.name}",
            listOf(match.name, match.competition.name).filter { it.isNotBlank() }.joinToString(" · "),
            match.competition.logoUrl, null,
        )

        fun of(competition: Competition) = Favourite(
            FavouriteType.COMPETITION, competition.id, competition.name, competition.formatLabel, competition.logoUrl, null,
        )

        fun of(player: Player, teamName: String?) = Favourite(
            FavouriteType.PLAYER, player.id, player.name,
            listOfNotNull(player.roleLabel, teamName).joinToString(" · ").ifBlank { null }, player.imageUrl, null,
        )
    }
}

data class SearchResults(
    val teams: List<Team> = emptyList(),
    val competitions: List<Competition> = emptyList(),
    val matches: List<Match> = emptyList(),
    val players: List<SeenPlayerEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = teams.isEmpty() && competitions.isEmpty() && matches.isEmpty() && players.isEmpty()
}

/**
 * Search across everything the app can enumerate. Teams and competitions come from the
 * provider's full lists; matches from the current board; players from those already seen in
 * scorecards, because the provider offers no player search.
 */
class SearchRepository(
    private val catalog: CatalogRepository,
    private val matches: MatchRepository,
    private val seenPlayers: SeenPlayerDao,
) {
    suspend fun search(rawQuery: String): SearchResults {
        val query = rawQuery.trim()
        if (query.length < 2) return SearchResults()
        fun String.hit() = contains(query, ignoreCase = true)

        // Each source is optional: being offline for one must not empty the others.
        val teams = attempt { catalog.teams() }.orEmpty()
            .filter { it.name.hit() || it.shortName.equals(query, ignoreCase = true) }
            .sortedBy { !it.name.startsWith(query, ignoreCase = true) }
            .take(12)
        val competitions = attempt { catalog.competitions() }.orEmpty().filter { it.name.hit() }.take(12)
        val board = matches.lastBoard.value
        val matchHits = board.all.filter {
            it.home.name.hit() || it.away.name.hit() || it.competition.name.hit() || it.name.hit() ||
                it.home.shortName.equals(query, true) || it.away.shortName.equals(query, true)
        }.take(15)
        val players = seenPlayers.search(query, limit = 20)
        return SearchResults(teams, competitions, matchHits, players)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (_: CricketApiException) {
            null
        }
}
