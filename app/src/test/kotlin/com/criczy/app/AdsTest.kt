package com.criczy.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
import com.criczy.app.ads.AdUnit
import com.criczy.app.ads.AdUnitConfig
import com.criczy.app.ads.AdsConfig
import com.criczy.app.ads.AdsHistory
import com.criczy.app.ads.AdsPolicy
import com.criczy.app.ads.LocalAds
import com.criczy.app.ads.PreviewAds
import com.criczy.app.ads.parseAdsConfig
import com.criczy.app.navigation.CriczyNavHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The ad rules, the database configuration, and where ads sit (placeholders of the real size). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class AdsTest {
    @get:Rule
    val compose = createComposeRule()

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val utc = ZoneOffset.UTC
    private val installed = 1_800_000_000_000L
    private val interstitial = AdUnitConfig.defaults(AdUnit.INTERSTITIAL, "ca-app-pub-1/1", enabled = true)
    private val appOpen = AdUnitConfig.defaults(AdUnit.APP_OPEN, "ca-app-pub-1/2", enabled = true)

    @Before
    fun online() {
        FixtureServer.offline = false
    }

    // ---------------------------------------------------------------- rules

    @Test
    fun `native ads go after the first position, then every interval`() {
        val config = AdUnitConfig.defaults(AdUnit.NATIVE_HOME, "x", true)
        assertEquals(listOf(3, 11, 19), AdsPolicy.nativePositions(20, config))
        assertEquals(emptyList(), AdsPolicy.nativePositions(3, config), "a list of three has no ad")
        assertEquals(listOf(4, 10), AdsPolicy.nativePositions(12, AdUnitConfig.defaults(AdUnit.NATIVE_NEWS, "x", true)))
    }

    @Test
    fun `no interstitial in the first two days, right after a link, too soon, or past the daily cap`() {
        val history = AdsHistory(firstOpen = installed)
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, history, installed + 47 * hour, utc), "grace period")
        val later = installed + 3 * day
        assertTrue(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, history, later, utc))
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, history.copy(lastLinkOpen = later - 5 * 60_000), later, utc), "opened from a notification")
        var shown = AdsPolicy.recordShown(AdUnit.INTERSTITIAL, history, later, utc)
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, shown, later + 3 * 60_000, utc), "four minutes apart")
        shown = AdsPolicy.recordShown(AdUnit.INTERSTITIAL, shown, later + 10 * 60_000, utc)
        shown = AdsPolicy.recordShown(AdUnit.INTERSTITIAL, shown, later + 20 * 60_000, utc)
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, shown, later + 60 * 60_000, utc), "three a day")
        assertTrue(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, shown, later + day, utc), "a new day")
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial, history.copy(adFreeUntil = later + hour), later, utc), "rewarded: ad free")
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.INTERSTITIAL, interstitial.copy(enabled = false), history, later, utc), "switched off")
    }

    @Test
    fun `app open ad only after a long time away, once a day, from the second week`() {
        val now = installed + 8 * day
        val away = AdsHistory(firstOpen = installed, lastBackground = now - 3 * hour)
        assertTrue(AdsPolicy.mayShowFullScreen(AdUnit.APP_OPEN, appOpen, away, now, utc))
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.APP_OPEN, appOpen, away.copy(lastBackground = now - 30 * 60_000), now, utc), "only half an hour away")
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.APP_OPEN, appOpen, away.copy(firstOpen = now - 3 * day), now, utc), "first week")
        assertFalse(AdsPolicy.mayShowFullScreen(AdUnit.APP_OPEN, appOpen, AdsPolicy.recordShown(AdUnit.APP_OPEN, away, now - hour, utc), now, utc), "one a day")
    }

    @Test
    fun `ads are thrown away before Google stops counting them`() {
        assertTrue(AdsPolicy.fresh(com.criczy.app.ads.AdFormat.INTERSTITIAL, 0, 50 * 60_000))
        assertFalse(AdsPolicy.fresh(com.criczy.app.ads.AdFormat.INTERSTITIAL, 0, 56 * 60_000))
        assertTrue(AdsPolicy.fresh(com.criczy.app.ads.AdFormat.APP_OPEN, 0, 3 * hour))
    }

    // ---------------------------------------------------------------- configuration from the database

    private val database: Map<String, Any?> = mapOf(
        "enabled" to true,
        "test_device_ids" to listOf("33BE2250B43518CCDA7DE426D04EE231"),
        "native_home" to mapOf("enabled" to true, "unit_id" to "ca-app-pub-1111/2222", "first_position" to 2L, "interval" to 6L),
        "interstitial" to mapOf("enabled" to true, "unit_id" to "ca-app-pub-1111/3333", "daily_cap" to 2L, "min_interval_minutes" to "five"),
        "banner_match" to mapOf("enabled" to true, "unit_id" to ""),
        "app_open" to mapOf("enabled" to false, "unit_id" to "ca-app-pub-1111/4444"),
    )

    @Test
    fun `each unit is read from its own object, with defaults for anything missing or wrong`() {
        val config = parseAdsConfig(database, debug = false)
        assertTrue(config.enabled)
        assertEquals(listOf("33BE2250B43518CCDA7DE426D04EE231"), config.testDeviceIds)
        assertEquals("ca-app-pub-1111/2222", config.unit(AdUnit.NATIVE_HOME).unitId)
        assertEquals(listOf(2, 8), AdsPolicy.nativePositions(10, config.unit(AdUnit.NATIVE_HOME)))
        assertEquals(2, config.unit(AdUnit.INTERSTITIAL).dailyCap)
        assertEquals(4, config.unit(AdUnit.INTERSTITIAL).minIntervalMinutes, "not a number: default")
        assertFalse(config.usable(AdUnit.BANNER_MATCH), "no unit id: off")
        assertFalse(config.usable(AdUnit.APP_OPEN), "switched off")
        assertFalse(config.usable(AdUnit.REWARDED), "missing: off")
        assertFalse(parseAdsConfig(database + ("enabled" to false), debug = false).usable(AdUnit.NATIVE_HOME), "master switch")
        assertEquals(AdsConfig.Off, parseAdsConfig(null, debug = false), "nothing read: no ads")
    }

    @Test
    fun `debug builds always use Google's test units`() {
        val config = parseAdsConfig(database, debug = true)
        assertEquals(AdUnit.NATIVE_HOME.testId, config.unit(AdUnit.NATIVE_HOME).unitId)
        assertTrue(config.usable(AdUnit.REWARDED), "missing unit in debug: on, with the test id")
        assertTrue(parseAdsConfig(null, debug = true).usable(AdUnit.INTERSTITIAL))
    }

    // ---------------------------------------------------------------- placements

    private fun launch() = compose.setCriczyContent { CompositionLocalProvider(LocalAds provides PreviewAds) { CriczyNavHost() } }

    @Test
    fun `native ads sit between results, never on the live tab`() {
        launch()
        compose.awaitText("Afghanistan Men")
        assertTrue(compose.onAllNodesWithText("Native ad", substring = true).fetchSemanticsNodes().isEmpty(), "live tab")
        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.awaitText("Native ad · native_home")
        compose.screenshot("ads/home-results-native")
    }

    @Test
    fun `a finished match has a banner at the end of its scorecard`() {
        launch()
        compose.awaitText("Afghanistan Men")
        compose.onNodeWithText("Results").performClick()
        compose.awaitText("Melbourne Stars Women win by 6 runs")
        compose.onAllNodesWithText("Melbourne Stars Women win by 6 runs").onFirst().performClick()
        compose.awaitText("Scorecard")
        compose.onNodeWithText("Scorecard").performClick()
        compose.awaitText("Amy Jones")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("Banner ad", substring = true))
        compose.screenshot("ads/match-scorecard-banner")
    }

    @Test
    fun `a live match has no ads`() {
        launch()
        compose.awaitText("Afghanistan Men")
        compose.onAllNodesWithText("Afghanistan Men").onFirst().performClick()
        compose.awaitText("Target")
        compose.onNodeWithText("Scorecard").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("ad ·", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `series, news and more`() {
        launch()
        compose.awaitText("Afghanistan Men")
        compose.onAllNodesWithText("Series").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("COMING UP", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("Banner ad", substring = true))
        compose.screenshot("ads/series-banner")
        compose.onAllNodesWithText("News").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("VIDEOS").fetchSemanticsNodes().isNotEmpty() }
        // The news list is the outer scrolling container; the videos row inside it scrolls too.
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Native ad · native_news"))
        compose.screenshot("ads/news-native")
        compose.onAllNodesWithText("More").onFirst().performClick()
        compose.onNodeWithText("Hide ads for 24 hours").performScrollTo()
        compose.screenshot("ads/more-rewarded")
    }
}
