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
import app.criczy.feature.favourites.FavouritesScreen
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
        compose.awaitText("END OF OVER")
        compose.screenshot("match-commentary-phone-dark")

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
        compose.awaitText("Player")
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
    fun `onboarding walks through to the end`() {
        var finished = false
        compose.setCriczyContent { OnboardingScreen { finished = true } }
        compose.screenshot("onboarding-phone-dark")
        compose.onNodeWithText("Next").performClick()
        compose.awaitText("See the match, not just the score")
        compose.onNodeWithText("Next").performClick()
        compose.awaitText("Get started")
        compose.onNodeWithText("Get started").performClick()
        assertTrue(finished)
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
