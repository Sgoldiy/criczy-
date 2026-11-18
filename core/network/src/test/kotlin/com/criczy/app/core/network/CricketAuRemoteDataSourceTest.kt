package com.criczy.app.core.network

import com.criczy.app.core.analytics.InningsAnalytics
import com.criczy.app.core.analytics.WagonWheel
import com.criczy.app.core.model.MatchFormat
import com.criczy.app.core.model.MatchState
import com.criczy.app.core.model.PlayerRole
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real client against responses captured from the live API on 16 Nov 2026. */
class CricketAuRemoteDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: CricketRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()
        source = CricketAuRemoteDataSource.create(client, server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueue(fixture: String, code: Int = 200) {
        val body = checkNotNull(javaClass.getResource("/fixtures/$fixture")) { "missing $fixture" }.readText()
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `match board maps the three buckets and sends required counts`() = runTest {
        enqueue("fixtures.json")
        val board = source.matchBoard(upcoming = 500, competitionId = 4687)

        val request = server.takeRequest()
        assertEquals("100", request.requestUrl?.queryParameter("UpcomingFixturesCount"), "server caps counts at 100")
        assertEquals("4687", request.requestUrl?.queryParameter("CompetitionId"))
        assertNull(request.requestUrl?.queryParameter("TeamId"))

        assertEquals(2, board.live.size)
        assertEquals(4, board.upcoming.size)
        assertTrue(board.upcoming.all { it.state == MatchState.UPCOMING })
        assertTrue(board.completed.all { it.state == MatchState.COMPLETED })

        // Upcoming league fixtures arrive with a blank GameType; the format comes from the competition.
        assertTrue(board.all.none { it.format == MatchFormat.OTHER }, board.all.filter { it.format == MatchFormat.OTHER }.joinToString { it.competition.name })
        assertEquals(MatchFormat.T20, board.all.first { it.competition.name.startsWith("Weber WBBL") }.format)

        val test = board.live.first { it.id == 41015 }
        assertEquals(MatchFormat.TEST, test.format)
        assertEquals(MatchState.IN_PROGRESS, test.state, "at stumps the match is in progress but not live")
        assertEquals("AFG", test.home.shortName)
        assertEquals("#1143EE", test.home.colorHex)
        assertEquals(listOf("339", "217", "295/9d", "143/5"), test.innings.map { it.scoreText })
        assertEquals(4, test.day)
        assertTrue(test.statusText!!.startsWith("Stumps"))
        assertEquals("Sharjah Cricket Association Stadium", test.venue?.displayName, "city is not repeated")
        assertEquals("United Arab Emirates", test.venue?.country)
    }

    @Test
    fun `scorecard maps innings, players and officials`() = runTest {
        enqueue("scorecard.json")
        val detail = assertNotNull(source.matchDetail(40644))

        assertEquals(MatchState.COMPLETED, detail.match.state)
        assertEquals(MatchFormat.T20, detail.match.format)
        assertEquals(97, detail.match.winnerTeamId)
        assertEquals(20, detail.oversPerInnings)
        assertEquals(listOf("120/8", "114"), detail.innings.map { it.score.scoreText })

        val first = detail.innings.first()
        val opener = first.batters.first()
        assertEquals("Amy Jones", opener.name)
        assertEquals(13, opener.runs)
        assertEquals("c: Jemma Barsby b: Sam Bates", opener.dismissal)
        assertEquals(first.score.wickets, first.fallOfWickets.size)
        assertEquals(24, first.fallOfWickets.first().runs)
        assertEquals(first.extras.total, with(first.extras) { wides + noBalls + byes + legByes + penalties })

        val keeper = assertNotNull(detail.player(409))
        assertEquals(PlayerRole.WICKET_KEEPER, keeper.role)
        assertEquals(LocalDate.of(1993, 6, 13), keeper.dateOfBirth)
        assertFalse(keeper.bio!!.contains('<'), "markup must be stripped from bios")
        assertTrue(detail.squad(97).isNotEmpty() && detail.squad(102).isNotEmpty())
        assertTrue(detail.officials.any { it.role == "Umpire" })
    }

    @Test
    fun `test match target is derived from all four innings`() = runTest {
        enqueue("summary_test_match.json")
        val detail = assertNotNull(source.matchDetail(41015))
        assertEquals(4, detail.innings.size)
        // Zimbabwe 339 and 295/9d, Afghanistan 217: Afghanistan need 418, and at 143/5 "require 275".
        assertEquals(418, detail.target)
        assertEquals(275, detail.target!! - detail.currentInnings!!.score.runs)
        assertNotNull(detail.currentInnings?.currentPartnership)
        assertEquals("6.0", detail.oversRemaining)
    }

    @Test
    fun `unknown match returns null instead of an empty scorecard`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Players":[],"ResponseError":false}"""))
        assertNull(source.matchDetail(1))
    }

    @Test
    fun `a fixture listed in two buckets is kept once`() = runTest {
        val fixture = """{"Id":7,"HomeTeam":{"Id":1},"AwayTeam":{"Id":2}}"""
        server.enqueue(MockResponse().setBody("""{"InProgressFixtures":[$fixture],"UpcomingFixtures":[$fixture],"CompletedFixtures":[$fixture,$fixture]}"""))
        val board = source.matchBoard()
        assertEquals(listOf(7), board.all.map { it.id })
        assertEquals(1, board.live.size)
    }

    @Test
    fun `a ball re-bowled after a wide keeps its place in the over`() = runTest {
        enqueue("comments_mobile.json")
        val over = source.commentaryPage(40644, inningsNumber = 2).overs.first { it.number == 14 }
        // The mobile feed has no ball ids and both deliveries are numbered 2.
        val pair = over.balls.filter { it.ballNumber == 2 }
        assertEquals(2, pair.size)
        assertTrue(pair.all { it.id == 0L })
        assertTrue(pair[0].time!! < pair[1].time!!, "bowled order")
    }

    @Test
    fun `ball lengths and lines read as words`() = runTest {
        enqueue("comments_mobile.json")
        val balls = source.commentaryPage(40644, inningsNumber = 2).overs.flatMap { it.balls }
        assertEquals(
            setOf("Length ball", "Back of a length", "Half volley", "Short", "Yorker", "Half tracker"),
            balls.mapNotNull { it.length }.toSet(),
        )
        assertTrue("Outside off" in balls.mapNotNull { it.line })
    }

    @Test
    fun `balls carry the bowler's arm and side of the wicket`() = runTest {
        enqueue("comments_mobile.json")
        val balls = source.commentaryPage(40644, inningsNumber = 2).overs.flatMap { it.balls }
        assertTrue(balls.all { it.bowlingArm in setOf("Right", "Left") })
        assertTrue(balls.all { it.bowlingFrom in setOf("Over", "Round") })
    }

    @Test
    fun `commentary page reports the cursor for older overs`() = runTest {
        enqueue("comments_mobile.json")
        val page = source.commentaryPage(40644, inningsNumber = 2)
        assertEquals((11..20).toList(), page.overs.map { it.number })
        assertFalse(page.isComplete)
        assertEquals(11, page.olderThanOver)
        val last = page.overs.last().balls.last()
        assertTrue(last.commentary.isNotEmpty())
    }

    @Test
    fun `full innings data is internally consistent and drives the charts`() = runTest {
        enqueue("comments_web_innings.json")
        val innings = source.fullInnings(40644, inningsNumber = 1)
        assertEquals(1, server.requestCount, "a 20-over innings fits in one request")
        assertEquals("50", server.takeRequest().requestUrl?.queryParameter("OverLimit"))

        assertTrue(innings.isComplete)
        assertEquals((1..20).toList(), innings.overs.map { it.number })
        assertEquals(120, innings.overs.last().inningsRuns)
        assertEquals(8, innings.overs.last().inningsWickets)

        // The ball data must add up to the scoreboard, or every chart built on it is wrong.
        assertEquals(120, innings.balls.sumOf { it.teamRuns })
        assertEquals(120, innings.balls.count { it.isLegal })
        assertEquals(8, innings.balls.count { it.isWicket })
        innings.overs.forEach { over ->
            assertEquals(over.runs, over.balls.sumOf { it.teamRuns }, "over ${over.number}")
        }

        val manhattan = InningsAnalytics.manhattan(innings)
        assertEquals(120, manhattan.sumOf { it.runs })
        assertEquals(8, manhattan.sumOf { it.wickets })
        assertEquals(6.0, InningsAnalytics.runRateProgression(innings).last().runRate, 0.001)

        val scoring = InningsAnalytics.scoring(innings.balls)
        assertEquals(120, scoring.runsOffBat + scoring.extras)
        assertEquals(7, scoring.extras, "scorecard shows 7 extras")

        val stands = InningsAnalytics.partnerships(innings)
        assertEquals(120, stands.sumOf { it.runs })
        assertEquals(1, stands.count { it.unbroken })
        assertEquals(24, stands.first().runs, "first wicket fell at 24")

        val phases = InningsAnalytics.phases(innings, oversPerInnings = 20)
        assertEquals(listOf("Powerplay", "Middle", "Death"), phases.map { it.name })
        assertEquals(120, phases.sumOf { it.runs })

        val zones = WagonWheel.zones(innings.balls)
        assertEquals(8, zones.size)
        assertTrue(zones.sumOf { it.runs } in 90..scoring.runsOffBat, "most runs off the bat are tracked")
        assertTrue(WagonWheel.points(innings.balls).all { it.distance in 0f..1f })
    }

    @Test
    fun `long innings are fetched in pages until over one`() = runTest {
        fun page(range: IntRange) = range.reversed().joinToString(",", """{"Inning":{"InningNumber":1,"Overs":[""", "]}}") {
            """{"OverNumber":$it,"TotalInningRuns":${it * 3},"Balls":[]}"""
        }
        server.enqueue(MockResponse().setBody(page(65..114)))
        server.enqueue(MockResponse().setBody(page(15..64)))
        server.enqueue(MockResponse().setBody(page(1..14)))

        val innings = source.fullInnings(41015, inningsNumber = 1)

        assertEquals((1..114).toList(), innings.overs.map { it.number })
        assertTrue(innings.isComplete)
        assertNull(server.takeRequest().requestUrl?.queryParameter("LastOverNumber"))
        assertEquals("65", server.takeRequest().requestUrl?.queryParameter("LastOverNumber"))
        assertEquals("15", server.takeRequest().requestUrl?.queryParameter("LastOverNumber"))
    }

    @Test
    fun `standings keep table order and expose which columns apply`() = runTest {
        enqueue("standings.json")
        val table = source.standings(4687)
        assertEquals(8, table.rows.size)
        assertEquals((1..8).toList(), table.rows.map { it.position })
        with(table.rows.first()) {
            assertEquals("SIX", team.shortName)
            assertEquals(10, points)
            assertEquals(0.922, netRunRate!!, 0.0001)
            assertEquals(played, won + lost + tied + drawn + noResult)
        }
        assertTrue(table.columns.netRunRate)
        assertFalse(table.columns.drawn)
    }

    @Test
    fun `competitions, teams, players, news, home and highlights parse`() = runTest {
        enqueue("competitions.json")
        val competitions = source.competitions()
        assertEquals("ICC Men's ODI World Cup 2027", competitions.first().name)
        assertEquals("ODI", competitions.first().formatLabel)
        assertNotNull(competitions.first().start)

        enqueue("teams.json")
        assertEquals("ENG", source.teams().first().shortName)

        enqueue("players.json")
        val player = source.players(listOf(8201, 8201)).single()
        assertEquals("PlayerIds=8201", server.apply { repeat(2) { takeRequest() } }.takeRequest().requestUrl?.query)
        assertEquals("Innocent Kaia", player.name)
        assertEquals(LocalDate.of(1992, 8, 10), player.dateOfBirth, "day-first dates of birth")
        assertEquals("Right-arm Off Spin", player.bowlingStyle)
        assertNotNull(player.imageUrl, "falls back to a per-format headshot")

        enqueue("news.json")
        val news = source.news()
        assertEquals(6, news.size)
        assertTrue(news.all { it.url.startsWith("https://") && it.published != null })
        assertEquals(listOf(40644), news.first().matchIds)

        enqueue("home.json")
        val home = source.homeFeed()
        assertEquals(5, home.news.size)
        assertEquals(88, home.videos.first().durationSeconds)
        assertEquals(4, home.featured.size)

        enqueue("highlights.json")
        val highlights = source.highlights(40644, 4687)
        assertEquals(20, highlights.size)
        assertEquals("Wickets", highlights.first().type)
    }

    @Test
    fun `empty player request makes no call`() = runTest {
        assertTrue(source.players(emptyList()).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server validation errors surface the server message`() = runTest {
        enqueue("error.json", code = 400)
        val error = assertFailsWith<CricketApiException.Server> { source.highlights(1, 0) }
        assertEquals(400, error.code)
        assertFalse(error.retryable)
        assertTrue("Competition Id" in error.message!!)
    }

    @Test
    fun `gateway errors are retryable and an unreachable server is a network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))
        assertTrue(assertFailsWith<CricketApiException.Server> { source.teams() }.retryable)

        server.shutdown()
        assertFailsWith<CricketApiException.Network> { source.teams() }
    }

    @Test
    fun `a body that is not the expected JSON is reported as malformed`() = runTest {
        server.enqueue(MockResponse().setBody("<html>maintenance</html>"))
        assertFailsWith<CricketApiException.Malformed> { source.standings(1) }
    }
}
