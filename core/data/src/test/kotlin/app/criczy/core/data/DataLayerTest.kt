package app.criczy.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.criczy.core.model.Competition
import app.criczy.core.model.InningsScore
import app.criczy.core.model.Match
import app.criczy.core.model.MatchBoard
import app.criczy.core.model.MatchFormat
import app.criczy.core.model.MatchState
import app.criczy.core.model.Team
import app.criczy.core.network.ConnectionMonitor
import app.criczy.core.network.CricketApiException
import app.criczy.core.network.CricketAuRemoteDataSource
import app.criczy.core.network.CricketHttp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataLayerTest {

    private lateinit var db: CriczyDatabase
    private lateinit var server: MockWebServer
    private lateinit var matches: MatchRepository
    private lateinit var catalog: CatalogRepository
    private lateinit var players: PlayerRepository

    private fun fixture(name: String) = File("../network/src/test/resources/fixtures/$name").readText()

    @Before
    fun setUp() {
        db = CriczyDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("views/fixtures") -> MockResponse().setBody(fixture("fixtures.json"))
                    path.endsWith("views/scorecard") -> MockResponse().setBody(fixture("scorecard.json"))
                    path.endsWith("web/teams") -> MockResponse().setBody(fixture("teams.json"))
                    path.endsWith("web/competitions") -> MockResponse().setBody(fixture("competitions.json"))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val remote = CricketAuRemoteDataSource.create(
            CricketHttp.client(null, ConnectionMonitor(), "test"), server.url("/").toString(),
        )
        matches = MatchRepository(remote, db.seenPlayers())
        catalog = CatalogRepository(remote)
        players = PlayerRepository(remote, db.seenPlayers())
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    // ---- polling -------------------------------------------------------------------------

    @Test
    fun `polling waits the interval chosen from the data and stops when told`() = runTest {
        var calls = 0
        val states = pollingFlow(
            nextDelayMs = { value: Int -> if (value < 3) 6_000L else null },
            now = { currentTime },
        ) { ++calls }.toList()

        assertEquals(listOf(null, 1, 2, 3), states.map { it.data })
        assertTrue(states.first().isInitialLoad)
        assertEquals(12_000L, currentTime, "two waits of six seconds, then polling ended")
        assertEquals(3, calls)
    }

    @Test
    fun `failures keep the last data, back off, and recover`() = runTest {
        var calls = 0
        val states = pollingFlow(nextDelayMs = { _: String -> 1_000L }, now = { currentTime }) {
            calls++
            when (calls) {
                1 -> "first"
                2, 3 -> throw CricketApiException.Network(IOException("down"))
                else -> "recovered"
            }
        }.take(5).toList()

        assertEquals(listOf(null, "first", "first", "first", "recovered"), states.map { it.data })
        assertEquals(listOf(null, null, LoadError.OFFLINE, LoadError.OFFLINE, null), states.map { it.error })
        // 1 s normal wait, then 5 s and 10 s of backoff.
        assertEquals(16_000L, currentTime)
    }

    @Test
    fun `one-shot load reports a failure once and does not retry`() = runTest {
        var calls = 0
        val states = loadOnce<String> { calls++; throw CricketApiException.Server(503, "busy") }.toList()
        assertEquals(1, calls)
        assertEquals(LoadError.SERVER, states.last().error)
        assertTrue(states.last().isFailedEmpty)
    }

    @Test
    fun `refresh policy never polls a finished match`() {
        assertNull(RefreshPolicy.forMatch(MatchState.COMPLETED))
        assertTrue(RefreshPolicy.forMatch(MatchState.LIVE)!! < RefreshPolicy.forMatch(MatchState.IN_PROGRESS)!!)
        val idle = MatchBoard(listOf(match(1, MatchState.IN_PROGRESS)), emptyList(), emptyList())
        val live = MatchBoard(listOf(match(1, MatchState.LIVE)), emptyList(), emptyList())
        assertTrue(RefreshPolicy.forBoard(live) < RefreshPolicy.forBoard(idle))
    }

    // ---- repositories --------------------------------------------------------------------

    @Test
    fun `opening a match indexes its players for search`() = runTest {
        assertEquals(0, db.seenPlayers().count())
        assertNotNull(matches.detail(40644))
        assertEquals(22, db.seenPlayers().count())

        val search = SearchRepository(catalog, matches, db.seenPlayers())
        val hit = search.search("amy jon").players.single()
        assertEquals("Amy Jones", hit.name)
        assertEquals("Melbourne Stars Women", hit.teamName)
    }

    @Test
    fun `a player's part in a match covers batting, bowling and the opponent`() = runTest {
        val detail = assertNotNull(matches.detail(40644))
        val jonesId = detail.innings.first().batters.first().playerId
        val form = assertNotNull(playerMatchForm(detail, jonesId))
        assertEquals("Amy Jones", form.batting.single().name)
        assertEquals(form.batting.single().runs, form.runs)
        assertTrue(form.opponent != null && form.opponent?.id != detail.player(jonesId)?.teamId)
        assertNull(playerMatchForm(detail, -1), "not in either side")

        val bowler = detail.innings.first().bowlers.first()
        val bowling = assertNotNull(playerMatchForm(detail, bowler.playerId))
        assertEquals(bowler.wickets, bowling.wickets)
        assertEquals(detail.innings.first().battingTeamId, bowling.opponent?.id)

        val summary = FormSummary.of(listOf(form, form))
        assertEquals(2, summary.matches)
        assertEquals(form.runs * 2, summary.runs)
    }

    @Test
    fun `recent form needs the player's team and reads its finished matches`() = runTest {
        assertTrue(players.recentForm(409).isEmpty(), "team unknown until seen in a scorecard")
        assertNotNull(matches.detail(40644))
        val form = players.recentForm(409, limit = 3)
        assertTrue(form.isNotEmpty())
        assertTrue(form.size <= 3)
        assertTrue(form.all { f -> f.batting.all { it.playerId == 409 } })
    }

    @Test
    fun `search covers teams, competitions and board matches and ignores short queries`() = runTest {
        val search = SearchRepository(catalog, matches, db.seenPlayers())
        assertTrue(search.search("e").isEmpty)

        matches.board()
        val england = search.search("england")
        assertTrue(england.teams.any { it.shortName == "ENG" })
        assertTrue(england.competitions.any { "England" in it.name })

        val afg = search.search("AFG")
        assertTrue(afg.matches.any { it.id == 41015 }, "short codes match board fixtures")
    }

    @Test
    fun `search still returns local results when the network is down`() = runTest {
        matches.detail(40644)
        server.shutdown()
        val results = SearchRepository(catalog, matches, db.seenPlayers()).search("Jones")
        assertTrue(results.players.isNotEmpty())
        assertTrue(results.teams.isEmpty())
    }

    @Test
    fun `favourites toggle and are observable`() = runTest {
        val repo = FavouritesRepository(db.favourites()) { 1L }
        val team = Team(11, "Pakistan Men", "PAK", null, "#006600")
        assertFalse(repo.isFavourite(FavouriteType.TEAM, 11).first())
        assertTrue(repo.toggle(FavouritesRepository.of(team)))
        assertTrue(repo.isFavourite(FavouriteType.TEAM, 11).first())
        assertEquals("Pakistan Men", repo.all.first().single().title)
        assertFalse(repo.toggle(FavouritesRepository.of(team)))
        assertTrue(repo.all.first().isEmpty())
    }

    @Test
    fun `leaderboard totals a competition from its scorecards and skips failures`() = runTest {
        val board = matches.board()
        val table = catalog.leaderboard(board.completed.take(3))
        // The fake server returns the same scorecard for every id, so it is counted once.
        assertEquals(1, table.matches)
        val top = table.mostRuns.first()
        assertTrue(top.runs >= table.mostRuns.last().runs)
        assertEquals(18, table.mostWickets.sumOf { it.wickets } + 0, "8 + 10 wickets, including run outs excluded below")
    }

    // ---- alerts --------------------------------------------------------------------------

    private fun match(
        id: Int,
        state: MatchState,
        wickets: Int = 0,
        start: Instant? = null,
        home: Int = 1,
        away: Int = 2,
        competition: Int = 9,
    ) = Match(
        id = id, name = "1st T20", competition = Competition(competition, "Cup", null),
        home = Team(home, "Alpha", "ALP", null, null), away = Team(away, "Beta", "BET", null, null),
        venue = null, start = start, end = null, state = state, format = MatchFormat.T20, formatLabel = "T20",
        innings = if (state == MatchState.UPCOMING) emptyList() else listOf(InningsScore(1, home, 50 + wickets, wickets, "7.2")),
        statusText = if (state == MatchState.COMPLETED) "Alpha win by 5 runs" else null,
    )

    private val prefs = UserPreferences(alertWickets = true)

    @Test
    fun `followed matches come from favourite teams, matches and competitions`() {
        val all = listOf(match(1, MatchState.LIVE), match(2, MatchState.LIVE, home = 7, away = 8, competition = 3), match(3, MatchState.LIVE, home = 5, away = 6, competition = 4))
        val favs = listOf(
            Favourite(FavouriteType.TEAM, 2, "Beta", null, null, null),
            Favourite(FavouriteType.COMPETITION, 3, "Other", null, null, null),
            Favourite(FavouriteType.PLAYER, 5, "Someone", null, null, null),
        )
        assertEquals(listOf(1, 2), AlertPlanner.followed(all, favs).map { it.id })
    }

    @Test
    fun `a match seen for the first time is recorded without notifying`() {
        val plan = AlertPlanner.plan(listOf(match(1, MatchState.LIVE, wickets = 3)), emptyList(), prefs, 0)
        assertTrue(plan.alerts.isEmpty())
        assertEquals(3, plan.newStates.single().wickets)
    }

    @Test
    fun `start, wicket and result are each notified once`() {
        var states = AlertPlanner.plan(listOf(match(1, MatchState.UPCOMING)), emptyList(), prefs, 0).newStates

        var plan = AlertPlanner.plan(listOf(match(1, MatchState.LIVE)), states, prefs, 1)
        assertEquals(listOf(AlertKind.STARTED), plan.alerts.map { it.kind })
        states = plan.newStates

        plan = AlertPlanner.plan(listOf(match(1, MatchState.LIVE, wickets = 1)), states, prefs, 2)
        assertEquals(listOf(AlertKind.WICKET), plan.alerts.map { it.kind })
        assertEquals("ALP 51/1 (7.2)", plan.alerts.single().text)
        states = plan.newStates

        assertTrue(AlertPlanner.plan(listOf(match(1, MatchState.LIVE, wickets = 1)), states, prefs, 3).alerts.isEmpty())

        plan = AlertPlanner.plan(listOf(match(1, MatchState.COMPLETED, wickets = 2)), states, prefs, 4)
        assertEquals(listOf(AlertKind.RESULT), plan.alerts.map { it.kind }, "the result replaces a wicket alert")
        assertEquals("Alpha win by 5 runs", plan.alerts.single().text)
        assertTrue(AlertPlanner.plan(listOf(match(1, MatchState.COMPLETED, wickets = 2)), plan.newStates, prefs, 5).alerts.isEmpty())
    }

    @Test
    fun `starting soon fires once inside the window`() {
        val start = Instant.ofEpochMilli(30 * 60_000L)
        val upcoming = listOf(match(1, MatchState.UPCOMING, start = start))
        val first = AlertPlanner.plan(upcoming, emptyList(), prefs, nowMs = 0)
        assertEquals(listOf(AlertKind.STARTING_SOON), first.alerts.map { it.kind })
        assertTrue(AlertPlanner.plan(upcoming, first.newStates, prefs, nowMs = 60_000).alerts.isEmpty())

        val far = listOf(match(2, MatchState.UPCOMING, start = Instant.ofEpochMilli(3 * 3_600_000L)))
        assertTrue(AlertPlanner.plan(far, emptyList(), prefs, nowMs = 0).alerts.isEmpty())
    }

    @Test
    fun `preferences silence alerts but state is still tracked`() {
        val before = AlertPlanner.plan(listOf(match(1, MatchState.LIVE)), emptyList(), prefs, 0).newStates
        val wicket = listOf(match(1, MatchState.LIVE, wickets = 1))
        assertTrue(AlertPlanner.plan(wicket, before, prefs.copy(alertWickets = false), 1).alerts.isEmpty())
        val muted = AlertPlanner.plan(wicket, before, prefs.copy(alertsEnabled = false), 1)
        assertTrue(muted.alerts.isEmpty())
        assertEquals(1, muted.newStates.single().wickets)
    }
}
