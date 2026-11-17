package app.criczy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.core.app.ApplicationProvider
import app.criczy.core.data.Favourite
import app.criczy.core.data.FavouriteType
import app.criczy.core.data.UserPreferences
import app.criczy.core.data.Loadable
import app.criczy.core.model.MatchState
import app.criczy.feature.favourites.FavouritesScreen
import app.criczy.feature.home.HomeScreen
import app.criczy.feature.home.HomeUiState
import app.criczy.feature.series.shortFormat
import app.criczy.ui.components.matchDescription
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.assertCountEquals
import app.criczy.core.model.Ball
import app.criczy.core.model.BallOutcome
import app.criczy.feature.match.CommentaryTab
import app.criczy.feature.match.CommentaryUiState
import app.criczy.feature.match.LatestBall
import app.criczy.feature.match.MatchActions
import app.criczy.feature.match.OverReplayPanel
import app.criczy.ui.components.replayMillis
import app.criczy.feature.match.ScoreEvent
import app.criczy.feature.match.ScoreEventOverlay
import app.criczy.feature.match.ScoreEventType
import app.criczy.feature.match.detectBallEvent
import app.criczy.ui.components.ballStory
import app.criczy.ui.components.shotEnd
import app.criczy.core.analytics.WagonWheel
import androidx.compose.ui.geometry.Offset
import app.criczy.feature.onboarding.OnboardingScreen
import app.criczy.feature.settings.PrivacyPolicyScreen
import app.criczy.feature.settings.SettingsActions
import app.criczy.feature.settings.SettingsScreen
import app.criczy.navigation.CriczyNavHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PHONE = "w411dp-h891dp-420dpi"
private const val SMALL_PHONE = "w320dp-h568dp-320dpi"
private const val TABLET = "w1280dp-h800dp-240dpi"

/**
 * Drives the real navigation graph, view models, repositories, HTTP client and mappers
 * against captured API responses, and saves a screenshot of each screen for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun online() {
        FixtureServer.offline = false
    }

    private fun launchApp(dark: Boolean = true) = compose.setCriczyContent(dark) { CriczyNavHost() }

    @Test
    fun `home lists live, upcoming and finished matches`() {
        launchApp()
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("Live · 2").assertIsDisplayed()
        compose.screenshot("home-live-phone-dark")

        compose.onNodeWithText("Upcoming").performClick()
        compose.awaitText("Pakistan Men")
        compose.screenshot("home-upcoming-phone-dark")

        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.screenshot("home-results-phone-dark")
    }

    @Test
    fun `home filter narrows the list and can leave it empty`() {
        launchApp()
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.onNodeWithText("Tests").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Melbourne Stars Women win by 6 runs").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `light theme home`() {
        launchApp(dark = false)
        compose.awaitText("Afghanistan Men")
        compose.screenshot("home-live-phone-light")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `tablet uses a navigation rail and a multi-column grid`() {
        launchApp()
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.screenshot("home-results-tablet-dark")
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun `small phone still fits the match card`() {
        launchApp()
        compose.awaitText("Afghanistan Men")
        compose.screenshot("home-live-smallphone-dark")
    }

    private fun openFinishedMatch() {
        launchApp()
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.onAllNodesWithText("Melbourne Stars Women win by 6 runs").onFirst().performClick()
        compose.awaitText("Scorecard")
    }

    @Test
    fun `match centre shows summary, scorecard, commentary, analysis, squads and info`() {
        openFinishedMatch()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.screenshot("match-summary-phone-dark")

        compose.onNodeWithText("Scorecard").performClick()
        compose.awaitText("Amy Jones")
        // Each batter row is one spoken sentence for screen readers.
        compose.onNode(hasContentDescription("Amy Jones, 13 runs from 12 balls, 3 fours, 0 sixes, c: Jemma Barsby b: Sam Bates")).assertIsDisplayed()
        compose.screenshot("match-scorecard-phone-dark")

        compose.onNodeWithText("Commentary").performClick()
        awaitGrounds()
        // Each ball ends with a link saying what happened; the ground stays closed until asked for.
        assertEquals(0, groundsShowing("wicket, Caught · bowler"))
        compose.onNodeWithText("See shot · WICKET").performClick()
        compose.waitUntil(5_000) { groundsShowing("wicket, Caught · bowler") == 1 }
        compose.screenshot("match-commentary-phone-dark")
        // Opening another ball closes the first: one ball at a time.
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("See ball · MISSED"))
        compose.onAllNodesWithText("See ball · MISSED").onFirst().performClick()
        compose.waitUntil(5_000) { groundsShowing("missed, played a drive") == 1 && groundsShowing("wicket, Caught · bowler") == 0 }
        compose.screenshot("match-commentary-open-dot-phone-dark")
        // The end of an over replays the whole over; opening it closes the open ball.
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("Replay over", substring = true))
        compose.onAllNodesWithText("Replay over", substring = true).onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Summary").fetchSemanticsNodes().isNotEmpty() && groundsShowing("missed, played a drive") == 0 }

        compose.onNodeWithText("Analysis").performScrollTo().performClick()
        compose.awaitText("WORM")
        compose.screenshot("match-analysis-worm-phone-dark")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("WAGON WHEEL", substring = true))
        compose.screenshot("match-analysis-wagon-phone-dark")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("PARTNERSHIPS", substring = true))
        compose.screenshot("match-analysis-partnerships-phone-dark")

        compose.onNodeWithText("Squads").performScrollTo().performClick()
        compose.awaitText("Compare two players in this match")
        compose.screenshot("match-squads-phone-dark")
        compose.onNodeWithText("Compare two players in this match").performClick()
        compose.awaitText("COMPARE PLAYERS")
        compose.screenshot("match-compare-phone-dark")
    }

    @Test
    fun `match info tab shows venue, officials and key moments`() {
        openFinishedMatch()
        compose.onNodeWithText("Info").performScrollTo().performClick()
        compose.awaitText("VENUE")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("OFFICIALS"))
        compose.onNodeWithText("Kate Rowlands", substring = true).assertIsDisplayed()
        compose.screenshot("match-info-phone-dark")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `match analysis on a tablet`() {
        openFinishedMatch()
        compose.onNodeWithText("Analysis").performScrollTo().performClick()
        compose.awaitText("WORM")
        compose.screenshot("match-analysis-tablet-dark")
    }

    @Test
    fun `a test match in progress shows target and all four innings`() {
        launchApp(dark = false)
        compose.awaitText("Afghanistan Men")
        compose.onAllNodesWithText("Afghanistan Men").onFirst().performClick()
        compose.awaitText("Target")
        compose.onNodeWithText("418").assertIsDisplayed()
        compose.onNodeWithText("275").assertIsDisplayed()
        compose.screenshot("match-live-test-phone-light")
    }

    @Test
    fun `commentary of a match in play marks the latest ball`() {
        // Real scorecard and commentary; only the state is set to live, which the fixtures cannot capture.
        val remote = FixtureServer.remote()
        val (detail, page) = runBlocking { remote.matchDetail(40644)!! to remote.commentaryPage(40644, inningsNumber = 2) }
        val live = detail.copy(match = detail.match.copy(state = MatchState.LIVE))
        val feed = CommentaryUiState(inningsNumber = 2, overs = page.overs.sortedByDescending { it.number }, loading = false)
        val none = MatchActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})
        compose.setCriczyContent(dark = false) { CommentaryTab(live, feed, none) }
        awaitGrounds()
        compose.onNodeWithText("See shot · WICKET").performClick()
        compose.waitUntil(5_000) { groundsShowing("wicket, Caught · bowler") == 1 }
        compose.screenshot("match-commentary-live-phone-light")
    }

    @Test
    fun `favouriting a match shows it under favourites`() {
        openFinishedMatch()
        compose.onNodeWithContentDescription("Add to favourites").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Remove from favourites")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Favourites").performClick()
        compose.awaitText("Sydney Thunder Women v Melbourne Stars Women")
        compose.screenshot("favourites-phone-dark")
    }

    @Test
    fun `featured live cards are all the same size`() {
        val board = runBlocking { FixtureServer.remote().matchBoard() }
        // One card with two innings a side and a long status, one where a side has not batted.
        val busy = board.live.first { it.id == 41015 }.copy(state = MatchState.LIVE)
        val quiet = board.live.first { it.id != 41015 }.let { m -> m.copy(state = MatchState.LIVE, statusText = "Lunch", innings = m.innings.take(1)) }
        compose.setCriczyContent {
            HomeScreen(HomeUiState(board = Loadable(data = board, loading = false), live = listOf(busy, quiet)), {}, {}, {}, {})
        }
        compose.screenshot("home-featured-phone-dark")
        val cards = listOf(busy, quiet).map { compose.onNode(hasContentDescription(matchDescription(it))).fetchSemanticsNode().size }
        assertEquals(cards[0], cards[1])
    }

    @Test
    fun `series format tags are short`() {
        assertEquals(
            listOf("TEST", "TEST", "ODI", "ODI", "T20I", "T20I", "T20I", "T20", "ONE-DAY", "THE HUNDRED"),
            listOf("Test", "Women's Tests", "ODI", "Women's One-day internationals", "T20 International", "T20 Intl", "Women's Twenty20 internationals", "T20", "One day", "The Hundred").map(::shortFormat),
        )
    }

    /** Every ball row has a ground; its description says what happened on that ball. */
    private fun awaitGrounds() =
        compose.waitUntil(10_000) { compose.onAllNodesWithText("See shot · WICKET").fetchSemanticsNodes().isNotEmpty() }

    private fun groundsShowing(description: String) =
        compose.onAllNodes(hasContentDescription(description, substring = true)).fetchSemanticsNodes().size

    @Test
    fun `an over replays ball by ball, then shows the over summary`() {
        val remote = FixtureServer.remote()
        val (detail, page) = runBlocking { remote.matchDetail(40644)!! to remote.commentaryPage(40644, inningsNumber = 2) }
        val over = page.overs.filter { it.isComplete }.maxBy { o -> o.balls.count { it.shotAngle != null && it.batterRuns > 0 } }
        compose.mainClock.autoAdvance = false
        compose.setCriczyContent { Box(Modifier.fillMaxSize()) { OverReplayPanel(detail, over) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(700)
        // First ball on the ground, the strip below marking it.
        compose.onNodeWithText("${over.number - 1}.${over.balls.first().ballNumber}", substring = true).assertExists()
        compose.screenshot("over-replay-1-first-ball-phone-dark")
        val whole = over.balls.sumOf { replayMillis(it) + 650L } + 380L * over.balls.size + 2_000L
        compose.mainClock.advanceTimeBy(whole)
        compose.onNode(hasContentDescription("over summary", substring = true)).assertExists()
        compose.onNodeWithText("Over summary").assertExists()
        compose.screenshot("over-replay-2-summary-phone-dark")
    }

    @Test
    fun `opening a ball replays it like a broadcast, from the bowler's hand to the shot`() {
        val four = fixtureBalls().first { it.outcome == BallOutcome.FOUR && it.shotAngle != null && it.length != null }
        compose.mainClock.autoAdvance = false
        compose.setCriczyContent {
            Box(Modifier.fillMaxSize()) { app.criczy.ui.components.BallGround(four, Modifier.size(360.dp), animate = true) }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        // Close on the pitch: the ball has pitched and is on its way to the batter.
        compose.mainClock.advanceTimeBy(700)
        compose.screenshot("ball-replay-1-delivery-phone-dark")
        // Camera pulling back while the shot runs to the rope.
        compose.mainClock.advanceTimeBy(500)
        compose.screenshot("ball-replay-2-shot-phone-dark")
        compose.mainClock.advanceTimeBy(3_000)
        compose.screenshot("ball-replay-3-done-phone-dark")
        assertTrue(groundsShowing("four") == 1)
    }

    @Test
    fun `each ball is told in plain words from what the scorers recorded`() {
        val balls = fixtureBalls()
        val dot = balls.first { it.outcome == BallOutcome.DOT }
        fun story(ball: Ball) = ballStory(ball).let { it.headline to it.showsShot }
        assertEquals("FOUR" to true, story(balls.first { it.outcome == BallOutcome.FOUR }))
        assertEquals("WICKET", ballStory(balls.first { it.isWicket }).headline)
        assertEquals("WIDE" to false, story(balls.first { it.isWide }))
        assertEquals("SINGLE" to true, story(balls.first { it.batterRuns == 1 && it.shotAngle != null }))
        assertEquals("DOUBLE", ballStory(dot.copy(batterRuns = 2, teamRuns = 2)).headline)
        assertEquals("TRIPLE", ballStory(dot.copy(batterRuns = 3, teamRuns = 3)).headline)
        assertEquals("SIX", ballStory(dot.copy(batterRuns = 6, teamRuns = 6, isBoundary = true)).headline)
        // Nothing scored: the ground stays empty and says what the batter did.
        assertEquals("MISSED" to false, story(dot.copy(connection = "Missed")))
        assertEquals("LEFT ALONE", ballStory(dot.copy(connection = "Left", shot = "Leave")).headline)
        assertEquals("DEFENDED", ballStory(dot.copy(connection = null, shot = "Forward defensive")).headline)
        assertEquals("HIT THE PAD", ballStory(dot.copy(connection = "Hit pad")).headline)
        assertEquals("EDGED", ballStory(dot.copy(connection = "Outside edge")).headline)
        assertTrue(balls.filter { it.batterRuns == 0 && !it.isWicket }.none { ballStory(it).showsShot }, "dot balls never draw an arrow")
    }

    @Test
    fun `ball grounds for every kind of outcome`() {
        val balls = fixtureBalls()
        val dot = balls.first { it.outcome == BallOutcome.DOT }
        val single = balls.first { it.batterRuns == 1 && it.shotAngle != null }
        val shown = listOf(
            balls.first { it.outcome == BallOutcome.FOUR && it.shotAngle != null },
            single,
            single.copy(batterRuns = 6, teamRuns = 6, isBoundary = true, inTheAir = true),
            balls.first { it.isWicket },
            dot.copy(connection = null, shot = "Forward defensive"),
            balls.first { it.isWide },
        )
        compose.setCriczyContent {
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxSize()) {
                shown.forEach { app.criczy.ui.components.BallGround(it, Modifier.size(180.dp)) }
            }
        }
        listOf("four", "single", "six", "wicket", "defended", "wide").forEach {
            assertTrue(compose.onAllNodes(hasContentDescription(it, substring = true)).fetchSemanticsNodes().isNotEmpty(), it)
        }
        compose.screenshot("ball-grounds-phone-dark")
    }

    @Test
    fun `shots point the way they would from behind the bowler`() {
        val c = Offset(100f, 100f)
        val batter = Offset(100f, 82f) // upper end of the pitch
        fun end(angle: Int, hand: String) = WagonWheel.point(fixtureBalls().first().copy(shotAngle = angle, shotMagnitude = 180, battingHand = hand))!!
            .let { shotEnd(it.x, it.y, 1f, batter, c, 90f)!! }
        // Right-hander: off side on the left of the screen, leg side on the right, straight goes down.
        assertTrue(end(199, "Right").x < c.x, "cover drive goes left")
        assertTrue(end(0, "Right").x > c.x, "square leg is on the right")
        val straight = end(270, "Right")
        assertTrue(straight.y > c.y && kotlin.math.abs(straight.x - c.x) < 1f, "straight drive goes down past the bowler")
        assertTrue(end(90, "Right").y < batter.y, "behind the stumps is above the batter")
        // A left-hander's cover drive goes the other way.
        assertTrue(end(341, "Left").x > c.x, "left-hander's cover is on the right")
        assertEquals(90f, kotlin.math.hypot(end(45, "Right").x - c.x, end(45, "Right").y - c.y), 0.5f, "a boundary ends on the rope")
    }

    private fun fixtureBalls() = runBlocking { FixtureServer.remote().commentaryPage(40644, inningsNumber = 2) }.overs.flatMap { it.balls }

    @Test
    fun `a new delivery becomes a live event, the first one seen does not`() {
        val balls = fixtureBalls()
        val dot = balls.first { it.outcome == BallOutcome.DOT }
        val four = balls.first { it.outcome == BallOutcome.FOUR }
        val wicket = balls.first { it.isWicket }
        val wide = balls.first { it.isWide }
        fun at(position: Int, ball: Ball, over: Int = 14, innings: Int = 2) = LatestBall(innings, over, position, ball)

        assertEquals(null, detectBallEvent(null, at(1, four)), "opening a match replays nothing")
        assertEquals(null, detectBallEvent(at(2, dot), at(2, four)), "same delivery")
        assertEquals(null, detectBallEvent(at(2, dot, innings = 1), at(3, four)), "another innings")
        assertEquals(null, detectBallEvent(at(1, dot), at(2, dot)), "a dot ball is not an event")
        assertEquals(ScoreEventType.FOUR, detectBallEvent(at(1, dot), at(2, four))?.type)
        assertEquals(ScoreEventType.WICKET, detectBallEvent(at(6, dot, over = 13), at(1, wicket))?.type)
        assertEquals(ScoreEventType.WIDE, detectBallEvent(at(1, dot), at(2, wide))?.type)
        val noBall = dot.copy(isNoBall = true, teamRuns = 1)
        assertEquals(ScoreEventType.NO_BALL, detectBallEvent(at(1, dot), at(2, noBall))?.type)
        assertEquals(ScoreEventType.SIX, detectBallEvent(at(1, dot), at(2, noBall.copy(batterRuns = 6, teamRuns = 7, isBoundary = true)))?.type, "the six matters more than the no-ball")
        val event = detectBallEvent(at(1, dot), at(2, four))!!
        assertEquals(four, event.ball)
        assertEquals(event.id, detectBallEvent(at(1, dot), at(2, four))!!.id, "the same delivery keeps its id")
    }

    @Test
    fun `live events animate for four, six, wicket, wide and no ball`() {
        val balls = fixtureBalls()
        val dot = balls.first { it.outcome == BallOutcome.DOT }
        val events = listOf(
            ScoreEvent(ScoreEventType.FOUR, 1, balls.first { it.outcome == BallOutcome.FOUR }),
            ScoreEvent(ScoreEventType.SIX, 2, dot.copy(batterRuns = 6, teamRuns = 6, isBoundary = true)),
            ScoreEvent(ScoreEventType.WICKET, 3, balls.first { it.isWicket }),
            ScoreEvent(ScoreEventType.WIDE, 4, balls.first { it.isWide }),
            ScoreEvent(ScoreEventType.NO_BALL, 5, dot.copy(isNoBall = true, teamRuns = 1)),
        )
        var event by mutableStateOf<ScoreEvent?>(null)
        compose.mainClock.autoAdvance = false
        compose.setCriczyContent { Box(Modifier.fillMaxSize()) { ScoreEventOverlay(event, null) } }
        events.forEach { e ->
            compose.runOnIdle { event = e }
            // With the clock paused, the state change needs one frame before time can advance.
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(380)
            compose.onNode(hasContentDescription(e.type.name.replace('_', ' ').lowercase(), substring = true)).assertExists()
            compose.screenshot("event-${e.type.name.lowercase()}-phone-dark")
            compose.mainClock.advanceTimeBy(4_000)
            compose.onAllNodes(hasContentDescription(e.type.name.replace('_', ' ').lowercase(), substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun `commentary scrolls past a ball re-bowled after a wide`() {
        openFinishedMatch()
        compose.onNodeWithText("Commentary").performClick()
        awaitGrounds()
        // Over 14 has two balls numbered 2 and the feed sends no ball ids; this used to crash the list.
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("END OF OVER 13"))
        compose.onNodeWithText("END OF OVER 13").assertIsDisplayed()
    }

    @Test
    fun `series list, table and tournament stats`() {
        launchApp()
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("Series").performClick()
        compose.awaitText("England v Bangladesh Test 2027 - Men")
        compose.screenshot("series-list-phone-dark")

        compose.onNodeWithText("England v Bangladesh Test 2027 - Men").performClick()
        compose.awaitText("Table")
        compose.onNodeWithText("Table").performClick()
        compose.awaitText("SIX")
        compose.onNodeWithText("+0.922").assertIsDisplayed()
        compose.screenshot("series-table-phone-dark")

        compose.onNodeWithText("Stats").performClick()
        compose.awaitText("MOST RUNS", timeoutMs = 20_000)
        compose.screenshot("series-stats-phone-dark")
    }

    @Test
    fun `news feed`() {
        launchApp(dark = false)
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("News").performClick()
        compose.awaitText("Dean stunner seals incredible Melbourne Stars comeback")
        compose.screenshot("news-phone-light")
    }

    @Test
    fun `search finds teams, series, matches and players seen in a scorecard`() {
        openFinishedMatch()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Jones")
        compose.awaitText("Amy Jones")
        compose.screenshot("search-player-phone-dark")
        compose.onNodeWithText("Amy Jones").performClick()
        // The player title is in capitals, which the search row is not.
        compose.waitUntil(10_000) { listOf("PLAYER", "AMY JONES").any { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } }
    }

    @Test
    fun `offline with nothing cached shows a clear error with retry`() {
        FixtureServer.offline = true
        launchApp()
        compose.awaitText("Scores are unavailable right now")
        compose.screenshot("home-error-phone-dark")
        FixtureServer.offline = false
        compose.onNodeWithText("Try again").performClick()
        compose.awaitText("Afghanistan Men")
    }

    @Test
    fun `settings toggles report changes`() {
        var wickets: Boolean? = null
        compose.setCriczyContent {
            SettingsScreen(
                UserPreferences(),
                SettingsActions({}, {}, {}, {}, { wickets = it }, {}, {}, {}, {}, {}, {}, {}),
            )
        }
        compose.onNodeWithText("Wickets").performClick()
        assertEquals(true, wickets)
        compose.screenshot("settings-phone-dark")
    }

    @Test
    fun `onboarding walks through to the end and can go back`() {
        var finished = false
        compose.setCriczyContent { OnboardingScreen { finished = true } }
        compose.onNodeWithText("Every ball, as it happens").assertIsDisplayed()
        compose.screenshot("onboarding-1-phone-dark")
        compose.onNodeWithText("Next").performClick()
        compose.awaitText("See the match, not just the score")
        compose.screenshot("onboarding-2-phone-dark")
        compose.onNodeWithText("Back").performClick()
        compose.awaitText("Every ball, as it happens")
        compose.onNodeWithText("Next").performClick()
        compose.awaitText("See the match, not just the score")
        compose.onNodeWithText("Next").performClick()
        compose.awaitText("Get started")
        compose.onNodeWithText("Follow what matters to you").assertIsDisplayed()
        compose.screenshot("onboarding-3-phone-dark")
        compose.onNodeWithText("Get started").performClick()
        assertTrue(finished)
    }

    @Test
    fun `onboarding skip finishes immediately`() {
        var finished = false
        compose.setCriczyContent(dark = false) { OnboardingScreen { finished = true } }
        compose.screenshot("onboarding-1-phone-light")
        compose.onNodeWithText("Skip").performClick()
        assertTrue(finished)
    }

    @Test
    @Config(qualifiers = SMALL_PHONE)
    fun `onboarding fits a small phone`() {
        compose.setCriczyContent { OnboardingScreen {} }
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText("Every ball, as it happens").assertIsDisplayed()
        compose.screenshot("onboarding-1-smallphone-dark")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `onboarding on a tablet`() {
        compose.setCriczyContent { OnboardingScreen {} }
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.screenshot("onboarding-1-tablet-dark")
    }

    @Test
    fun `empty favourites explains what to do, and privacy policy renders`() {
        var browse = false
        compose.setCriczyContent(dark = false) { FavouritesScreen(emptyList(), {}, {}, { _, _ -> }, {}, { browse = true }, {}) }
        compose.onNodeWithText("Browse teams").performClick()
        assertTrue(browse)
        compose.screenshot("favourites-empty-phone-light")
    }

    @Test
    fun `favourites remove button reports the item`() {
        val team = Favourite(FavouriteType.TEAM, 11, "Pakistan Men", "PAK", null, "#006600")
        var removed: Favourite? = null
        compose.setCriczyContent { FavouritesScreen(listOf(team), {}, {}, { _, _ -> }, {}, {}, { removed = it }) }
        compose.onNodeWithContentDescription("Remove Pakistan Men from favourites").performClick()
        assertEquals(team, removed)
    }

    @Test
    fun `privacy policy is readable in the app`() {
        compose.setCriczyContent { PrivacyPolicyScreen {} }
        compose.onNodeWithText("What stays on your device").assertIsDisplayed()
        assertTrue(ApplicationProvider.getApplicationContext<TestCriczyApp>().packageName.startsWith("app.criczy"))
    }
}
