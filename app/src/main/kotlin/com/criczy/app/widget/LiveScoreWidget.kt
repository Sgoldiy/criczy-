package com.criczy.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.criczy.app.CriczyApp
import com.criczy.app.MainActivity
import com.criczy.app.core.data.AlertPlanner
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchBoard
import com.criczy.app.core.model.MatchState
import com.criczy.app.core.network.CricketApiException
import com.criczy.app.navigation.Routes
import com.criczy.app.ui.format.Formatters

/** What the widget shows: a followed live match first, then any live match, then the next fixture. */
internal fun pickWidgetMatch(board: MatchBoard, followed: List<Match>): Match? {
    val followedIds = followed.mapTo(HashSet()) { it.id }
    fun List<Match>.preferFollowed() = sortedBy { it.id !in followedIds }
    return board.live.filter { it.state == MatchState.LIVE }.preferFollowed().firstOrNull()
        ?: board.live.preferFollowed().firstOrNull()
        ?: board.upcoming.sortedBy { it.start }.let { upcoming -> upcoming.firstOrNull { it.id in followedIds } ?: upcoming.firstOrNull() }
        ?: board.completed.firstOrNull()
}

class LiveScoreWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CriczyApp).container
        // The HTTP layer falls back to its offline cache, so this usually has something to show.
        val match = try {
            val board = container.matches.board()
            pickWidgetMatch(board, AlertPlanner.followed(board.all, container.favourites.snapshot()))
        } catch (_: CricketApiException) {
            container.matches.lastBoard.value.let { pickWidgetMatch(it, emptyList()) }
        }
        provideContent { GlanceTheme { WidgetContent(context, match) } }
    }

    companion object {
        suspend fun hasInstances(context: Context): Boolean =
            GlanceAppWidgetManager(context).getGlanceIds(LiveScoreWidget::class.java).isNotEmpty()
    }
}

private val ink = ColorProvider(Color(0xFF0B1018))
private val white = ColorProvider(Color.White)
private val muted = ColorProvider(Color(0xFFA9B3C3))
private val amber = ColorProvider(Color(0xFFFFB627))
private val live = ColorProvider(Color(0xFFFF5449))

@Composable
private fun WidgetContent(context: Context, match: Match?) {
    val open = if (match != null) {
        actionStartActivity(Intent(Intent.ACTION_VIEW, Routes.matchDeepLink(match.id), context, MainActivity::class.java))
    } else {
        actionStartActivity(Intent(context, MainActivity::class.java))
    }
    Column(
        GlanceModifier.fillMaxSize().background(ink).cornerRadius(20.dp).padding(14.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (match == null) {
            Text("CRICZY", style = TextStyle(color = amber, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(GlanceModifier.height(4.dp))
            Text("No scores available. Tap to open.", style = TextStyle(color = muted, fontSize = 12.sp))
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(match.competition.name, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (match.state == MatchState.LIVE) Text("● LIVE", style = TextStyle(color = live, fontWeight = FontWeight.Bold, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        for (team in listOf(match.home, match.away)) {
            val scores = match.inningsFor(team.id)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(team.shortName, style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 18.sp), modifier = GlanceModifier.defaultWeight())
                if (scores.isNotEmpty()) {
                    Text(scores.joinToString(" & ") { it.scoreText }, style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 18.sp))
                    Text("  (${scores.last().overs})", style = TextStyle(color = muted, fontSize = 11.sp))
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(Formatters.statusLine(match), style = TextStyle(color = if (match.state == MatchState.COMPLETED) amber else muted, fontSize = 12.sp), maxLines = 2)
    }
}

class LiveScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LiveScoreWidget()
}
