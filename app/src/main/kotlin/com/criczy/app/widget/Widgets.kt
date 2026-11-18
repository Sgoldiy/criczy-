package com.criczy.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.criczy.app.MainActivity
import com.criczy.app.core.model.MatchBoard
import com.criczy.app.core.model.MatchDetail
import com.criczy.app.navigation.Routes
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Three home screen widgets for the same match, picked by [pickWidgetMatch]:
 * [ScoreWidget] (just the score), [LiveScoreWidget] (score and situation) and [MatchCentreWidget]
 * (plus batters, bowler, this over and the rates). They share one [WidgetData] snapshot.
 */
object Widgets {
    private val kinds = listOf(ScoreWidget::class.java, LiveScoreWidget::class.java, MatchCentreWidget::class.java)

    suspend fun anyPlaced(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return kinds.any { manager.getGlanceIds(it).isNotEmpty() }
    }

    suspend fun hasMatchCentre(context: Context): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(MatchCentreWidget::class.java).isNotEmpty()

    /** Fetches (or reuses [board]) and redraws every placed widget. Does nothing when none is placed. */
    suspend fun refresh(context: Context, force: Boolean = true, board: MatchBoard? = null) {
        if (!anyPlaced(context)) return
        WidgetData.load(context, force, board)
        redraw(context)
    }

    /** The pinned live score polls the scorecard every 30 s; passing it on keeps the widgets live at no extra cost. */
    suspend fun onPinnedDetail(context: Context, detail: MatchDetail) {
        if (!anyPlaced(context)) return
        WidgetData.accept(context, detail)
        redraw(context)
    }

    private suspend fun redraw(context: Context) {
        ScoreWidget().updateAll(context)
        LiveScoreWidget().updateAll(context)
        MatchCentreWidget().updateAll(context)
    }

    internal fun actions(context: Context, snapshot: WidgetSnapshot?): WidgetActions {
        val match = snapshot?.match
        val open = if (match != null) {
            Intent(Intent.ACTION_VIEW, Routes.matchDeepLink(match.id), context, MainActivity::class.java)
        } else {
            Intent(context, MainActivity::class.java)
        }
        return WidgetActions(open = actionStartActivity(open), refresh = actionRunCallback<RefreshWidgetsAction>())
    }
}

/** The refresh button: fetch now, saying "Updating…" meanwhile. Runs in a broadcast, so it gives up after 20 s. */
class RefreshWidgetsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetData.showingProgress {
            ScoreWidget().updateAll(context)
            LiveScoreWidget().updateAll(context)
            MatchCentreWidget().updateAll(context)
            withTimeoutOrNull(20_000) { Widgets.refresh(context, force = true) }
        }
        Widgets.refresh(context, force = false)
    }
}

/** Shared by the three widgets: load (or reuse) the snapshot, then follow it while the widget session runs. */
abstract class CriczyWidget : GlanceAppWidget() {
    @Composable
    abstract fun Content(snapshot: WidgetSnapshot?, refreshing: Boolean, actions: WidgetActions)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetData.load(context)
        provideContent {
            val snapshot by WidgetData.snapshot.collectAsState()
            val refreshing by WidgetData.refreshing.collectAsState()
            val current = snapshot ?: first
            Content(current, refreshing, Widgets.actions(context, current))
        }
    }
}

class ScoreWidget : CriczyWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 40.dp), DpSize(170.dp, 40.dp), DpSize(110.dp, 56.dp), DpSize(170.dp, 56.dp), DpSize(170.dp, 100.dp)))

    @Composable
    override fun Content(snapshot: WidgetSnapshot?, refreshing: Boolean, actions: WidgetActions) = ScoreWidgetContent(snapshot, actions)
}

class LiveScoreWidget : CriczyWidget() {
    override val sizeMode = SizeMode.Responsive(listOf(180, 250, 280).flatMap { w -> listOf(110, 150).map { h -> DpSize(w.dp, h.dp) } }.toSet())

    @Composable
    override fun Content(snapshot: WidgetSnapshot?, refreshing: Boolean, actions: WidgetActions) = LiveScoreWidgetContent(snapshot, refreshing, actions)
}

class MatchCentreWidget : CriczyWidget() {
    override val sizeMode = SizeMode.Responsive(listOf(250, 300).flatMap { w -> listOf(180, 240, 285).map { h -> DpSize(w.dp, h.dp) } }.toSet())

    @Composable
    override fun Content(snapshot: WidgetSnapshot?, refreshing: Boolean, actions: WidgetActions) = MatchCentreWidgetContent(snapshot, refreshing, actions)
}

class ScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoreWidget()
}

class LiveScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LiveScoreWidget()
}

class MatchCentreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MatchCentreWidget()
}
