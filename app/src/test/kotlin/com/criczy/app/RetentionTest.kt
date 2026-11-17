package com.criczy.app

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.criczy.app.core.model.Team
import com.criczy.app.feature.onboarding.TeamPickerScreen
import com.criczy.app.feature.onboarding.TeamPickerState
import com.criczy.app.feature.onboarding.TeamPickerRoute
import com.criczy.app.feature.onboarding.orderTeams
import com.criczy.app.notifications.LiveScoreService
import com.criczy.app.notifications.LiveScoreText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class RetentionTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<TestCriczyApp>()

    @Test
    fun `team picker lists teams playing first and allows five`() {
        val teams = runBlocking { app.container.catalog.teams() }
        val board = runBlocking { FixtureServer.remote().matchBoard() }
        val playing = board.live.flatMap { listOf(it.home.id, it.away.id) }
        val ordered = orderTeams(teams, playing)
        val known = playing.distinct().filter { id -> teams.any { it.id == id } }
        assertEquals(known, ordered.take(known.size).map { it.id })

        val state = androidx.compose.runtime.mutableStateOf(TeamPickerState(teams = ordered, playingNow = playing.toSet()))
        compose.setCriczyContent {
            TeamPickerScreen(state.value, onToggle = { id ->
                val s = state.value
                state.value = s.copy(selected = if (id in s.selected) s.selected - id else (s.selected + id).take(5))
            }, onContinue = {}, onSkip = {})
        }
        compose.onNodeWithText("Pick at least one team").assertIsNotEnabled()
        ordered.take(2).forEach { compose.onNodeWithContentDescription(it.name).performClick() }
        compose.onNodeWithText("Continue with 2 of 5").assertIsEnabled()
        compose.screenshot("onboarding-team-picker-phone-dark")
        compose.onNode(hasSetTextAction()).performTextInput(ordered.last().name.take(5))
        compose.onNodeWithContentDescription(ordered.last().name).performClick()
        compose.onNodeWithText("Continue with 3 of 5").assertIsEnabled()
    }

    @Test
    fun `team picker saves the chosen teams as favourites, at most five`() {
        var done = false
        compose.setCriczyContent { TeamPickerRoute(onDone = { done = true }) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasClickAction() and hasContentDescription("", substring = true)).fetchSemanticsNodes().size > 7 }
        val teams = runBlocking { app.container.catalog.teams() }
        val board = runBlocking { FixtureServer.remote().matchBoard() }
        val ordered = orderTeams(teams, (board.live + board.upcoming).flatMap { listOf(it.home.id, it.away.id) })
        ordered.take(7).forEach { compose.onNodeWithContentDescription(it.name).performScrollTo().performClick() }
        compose.onNodeWithText("Continue with 5 of 5").performClick()
        compose.waitUntil(10_000) { Thread.sleep(20); shadowOf(android.os.Looper.getMainLooper()).idle(); done }
        val saved = runBlocking { app.container.favourites.all.first() }.map { it.id }.toSet()
        assertEquals(ordered.take(5).map(Team::id).toSet(), saved)
    }

    @Test
    fun `pinned score reads like the scoreboard`() {
        val live = runBlocking { FixtureServer.remote().matchBoard() }.live.first { it.id == 41015 }
        val text = LiveScoreText.of(live)
        assertEquals("AFG 217 & 143/5 (44.0) · ZIM 339 & 295/9d", text.title)
        assertEquals(live.statusText, text.text)
    }

    @Test
    fun `pinning starts a foreground notification and stop removes it`() {
        val intent = Intent(app, LiveScoreService::class.java).putExtra("match", 41015)
        val controller = Robolectric.buildService(LiveScoreService::class.java, intent).create().startCommand(0, 1)
        val service = controller.get()
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals(41015, LiveScoreService.pinned.value)
        controller.withIntent(Intent(app, LiveScoreService::class.java).setAction("com.criczy.app.STOP_LIVE_SCORE")).startCommand(0, 2)
        assertNull(LiveScoreService.pinned.value)
        controller.destroy()
    }
}
