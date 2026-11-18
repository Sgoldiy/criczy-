package com.criczy.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.criczy.app.core.model.BallOutcome
import com.criczy.app.core.model.BatterLine
import com.criczy.app.core.model.BowlerLine
import com.criczy.app.core.model.Competition
import com.criczy.app.core.model.InningsScore
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchBoard
import com.criczy.app.core.model.MatchFormat
import com.criczy.app.core.model.MatchState
import com.criczy.app.core.model.Team
import com.criczy.app.core.model.Venue
import com.criczy.app.widget.LiveScoreWidgetContent
import com.criczy.app.widget.MatchCentreWidgetContent
import com.criczy.app.widget.ScoreWidgetContent
import com.criczy.app.widget.WidgetActions
import com.criczy.app.widget.WidgetCentre
import com.criczy.app.widget.WidgetOver
import com.criczy.app.widget.WidgetSnapshot
import com.criczy.app.widget.footerDetail
import com.criczy.app.widget.pickWidgetMatch
import com.criczy.app.widget.updatedLabel
import com.criczy.app.widget.widgetCentre
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three home screen widgets, rendered the way a launcher shows them (Glance → RemoteViews → views),
 * from the captured responses. PNGs go to app/build/screenshots/widgets for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class WidgetsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-18T10:00:00Z")
    private val remote = FixtureServer.remote()

    /** The Test match in the fixtures, at stumps on day 4: two batters in, a bowler, a chase of 418. */
    private val test by lazy { runBlocking { remote.matchDetail(41015)!! } }
    private val board by lazy { runBlocking { remote.matchBoard() } }
    private val overBalls by lazy { runBlocking { remote.commentaryPage(40644, inningsNumber = 2) }.overs.maxBy { it.number } }

    private fun liveSnapshot(): WidgetSnapshot {
        val match = test.match.copy(state = MatchState.LIVE, statusText = "Afghanistan need 275 runs to win, 5 wickets left")
        val centre = widgetCentre(test.copy(match = match), null).copy(over = WidgetOver(45, false, overBalls.balls.take(5)))
        return WidgetSnapshot(match, centre, now)
    }

    @Test
    fun `widgets show a pinned match first, then a followed live match, then any live match`() {
        val live = board.live
        assertTrue(live.size >= 2)
        assertEquals(live[1].id, pickWidgetMatch(board, followed = emptyList(), pinnedId = live[1].id)?.id)
        assertEquals(live.last().id, pickWidgetMatch(board.copy(live = live.map { it.copy(state = MatchState.LIVE) }), followed = listOf(live.last()))?.id)
        assertEquals(board.upcoming.sortedBy { it.start }.first().id, pickWidgetMatch(MatchBoard(emptyList(), board.upcoming, board.completed), emptyList())?.id)
        assertNull(pickWidgetMatch(MatchBoard.Empty, emptyList()))
    }

    @Test
    fun `the match centre widget takes the batters at the crease, the bowler and the chase from the scorecard`() {
        val centre = widgetCentre(test, null)
        assertEquals(2, centre.batters.size)
        assertTrue(centre.batters.all { it.isAtCrease })
        assertTrue(centre.batters.first().isOnStrike || centre.batters.none { it.isOnStrike })
        assertTrue(centre.bowler?.isBowlingNow == true)
        assertEquals(418, centre.target)
        assertEquals("CRR 3.24  ·  Target 418", footerDetail(test.match, centre))
        // A finished match has no one at the crease; an upcoming one has nothing at all.
        val finished = widgetCentre(test.copy(match = test.match.copy(state = MatchState.COMPLETED)), null)
        assertTrue(finished.batters.isEmpty() && finished.bowler == null && finished.over == null)
    }

    @Test
    fun `updated time is shown as a clock time, or as offline`() {
        assertEquals("Updated 3:30 PM", updatedLabel(Instant.parse("2026-11-18T10:00:00Z"), now, ZoneOffset.ofHoursMinutes(5, 30), Locale.US))
        assertEquals("Updated 17 Nov", updatedLabel(Instant.parse("2026-11-17T10:00:00Z"), now, ZoneOffset.UTC, Locale.US))
        assertEquals("Offline", updatedLabel(null, now))
    }

    @Test
    fun `score widget, from 2x1 to 3x2`() {
        val snapshot = liveSnapshot()
        val texts = render("score-live-3x1", 250, 60) { ScoreWidgetContent(snapshot, WidgetActions(), now) }
        assertTrue("AFG" in texts && "ZIM" in texts && "217 & 143/5" in texts && "339 & 295/9d" in texts, texts.toString())
        render("score-live-2x1-compact", 120, 40) { ScoreWidgetContent(snapshot, WidgetActions(), now) }
        render("score-live-3x2", 250, 110) { ScoreWidgetContent(snapshot, WidgetActions(), now) }
        val upcoming = board.upcoming.first()
        val next = render("score-upcoming-3x1", 250, 60) { ScoreWidgetContent(WidgetSnapshot(upcoming), WidgetActions(), now) }
        assertTrue(upcoming.title in next, next.toString())
        render("score-result-3x1", 250, 60) { ScoreWidgetContent(WidgetSnapshot(board.completed.first()), WidgetActions(), now) }
        val empty = render("score-empty-2x1", 120, 50) { ScoreWidgetContent(null, WidgetActions(), now) }
        assertTrue("No match right now" in empty)
    }

    @Test
    fun `live score widget, 3x2 and 4x2`() {
        val snapshot = liveSnapshot()
        val texts = render("live-4x2", 320, 160) { LiveScoreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
        assertTrue("Afghanistan Men" in texts && "LIVE" in texts && snapshot.match!!.statusText in texts, texts.toString())
        assertTrue(texts.any { it.startsWith("Updated ") }, texts.toString())
        render("live-3x2", 200, 115) { LiveScoreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
        val updating = render("live-4x2-updating", 320, 160) { LiveScoreWidgetContent(snapshot, refreshing = true, WidgetActions(), now) }
        assertTrue("Updating…" in updating)
        val result = render("live-result-4x2", 320, 160) { LiveScoreWidgetContent(WidgetSnapshot(board.completed.first(), updatedAt = now), refreshing = false, WidgetActions(), now) }
        assertTrue("RESULT" in result)
        render("live-upcoming-4x2", 320, 160) { LiveScoreWidgetContent(WidgetSnapshot(board.upcoming.first(), updatedAt = now), refreshing = false, WidgetActions(), now) }
    }

    @Test
    fun `match centre widget, 4x3 and 4x4`() {
        val snapshot = liveSnapshot()
        val centre = snapshot.centre!!
        val texts = render("centre-4x4", 320, 300) { MatchCentreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
        val striker = centre.batters.first()
        assertTrue((if (striker.isOnStrike) "${striker.name} *" else striker.name) in texts, texts.toString())
        assertTrue(centre.bowler!!.name in texts && "OVER 45" in texts && "CRR 3.24  ·  Target 418" in texts, texts.toString())
        val regular = render("centre-4x3", 320, 250) { MatchCentreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
        assertTrue("OVER 45" !in regular && centre.bowler!!.name in regular, "the over row needs a taller widget")
        val short = render("centre-4x3-short", 260, 185) { MatchCentreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
        assertTrue(centre.bowler!!.name !in short && striker.name in short.joinToString(), "a short widget keeps the batters")
        render("centre-result-4x3", 320, 250) {
            MatchCentreWidgetContent(WidgetSnapshot(board.completed.first(), WidgetCentre(playerOfTheMatch = "Kim Garth"), now), refreshing = false, WidgetActions(), now)
        }
        render("centre-upcoming-4x3", 320, 250) { MatchCentreWidgetContent(WidgetSnapshot(board.upcoming.first(), updatedAt = now), refreshing = false, WidgetActions(), now) }
    }

    /** Invented teams and players, as in the onboarding art: these become the widget picker previews. */
    @Test
    fun `picker previews`() {
        val lions = Team(901, "Lions", "LIO", null, "#1F4BB4")
        val hawks = Team(902, "Hawks", "HAW", null, "#C62828")
        val match = Match(
            id = 900, name = "Final", competition = Competition(900, "City Cup", null), home = lions, away = hawks,
            venue = Venue(900, "Riverside Oval", null), start = now, end = null, state = MatchState.LIVE, format = MatchFormat.T20, formatLabel = "T20",
            innings = listOf(InningsScore(1, lions.id, 182, 6, "20.0"), InningsScore(2, hawks.id, 171, 4, "18.2")),
            statusText = "Hawks need 12 runs from 10 balls",
        )
        val template = overBalls.balls.first()
        fun ball(runs: Int, four: Boolean = false, wicket: Boolean = false) =
            template.copy(batterRuns = runs, teamRuns = runs, bowlerRuns = runs, isBoundary = four, isWicket = wicket, isWide = false, isNoBall = false, isBye = false, isLegBye = false)
        val over = listOf(ball(1), ball(0), ball(4, four = true), ball(2))
        assertEquals(listOf(BallOutcome.RUNS, BallOutcome.DOT, BallOutcome.FOUR, BallOutcome.RUNS), over.map { it.outcome })
        val centre = WidgetCentre(
            batters = listOf(
                BatterLine(1, "Sam Rivers", 64, 38, 6, 2, 168.4, null, false, null, isAtCrease = true, isOnStrike = true, order = 3),
                BatterLine(2, "Leo Hart", 29, 17, 2, 1, 170.6, null, false, null, isAtCrease = true, isOnStrike = false, order = 4),
            ),
            bowler = BowlerLine(3, "Max Doyle", "3.2", 0, 28, 1, 8.4, 6, 1, 0, isBowlingNow = true, order = 2),
            over = WidgetOver(19, false, over), runRate = 9.33, requiredRate = 7.2,
        )
        val snapshot = WidgetSnapshot(match, centre, now)
        render("preview-score", 250, 64, background = false) { ScoreWidgetContent(snapshot, WidgetActions(), now) }
        render("preview-live", 320, 160, background = false) { LiveScoreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
        render("preview-centre", 320, 300, background = false) { MatchCentreWidgetContent(snapshot, refreshing = false, WidgetActions(), now) }
    }

    /** Draws [content] at [widthDp] x [heightDp] over a wallpaper-grey square and returns every text shown. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(name: String, widthDp: Int, heightDp: Int, background: Boolean = true, content: @Composable () -> Unit): List<String> {
        val result = runBlocking { GlanceRemoteViews().compose(context, DpSize(widthDp.dp, heightDp.dp), content = content) }
        val view = result.remoteViews.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val pad = if (background) (16 * density).roundToInt() else 0
        val bitmap = Bitmap.createBitmap(width + 2 * pad, height + 2 * pad, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            if (background) drawColor(0xFF5B6475.toInt())
            translate(pad.toFloat(), pad.toFloat())
            view.draw(this)
        }
        File("build/screenshots/widgets").mkdirs()
        File("build/screenshots/widgets/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return texts(view)
    }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOfNotNull(view.text?.toString()?.takeIf { it.isNotEmpty() && view.visibility == View.VISIBLE })
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
}
