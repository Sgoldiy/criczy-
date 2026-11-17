package com.criczy.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.criczy.app.CriczyApp
import com.criczy.app.R
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps one pinned match's score in the notification bar while it is in play: refreshed every
 * 30 seconds (2 minutes during breaks), stopped by the notification's Stop action, when the match
 * ends, or after 8 hours. Started only from the match screen, while the app is open.
 */
class LiveScoreService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val matchId = intent?.getIntExtra(EXTRA_MATCH, -1) ?: -1
        if (intent?.action == ACTION_STOP || matchId < 0) {
            stop()
            return START_NOT_STICKY
        }
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(this, matchId, "Loading score…", null, null, ongoing = true), type)
        _pinned.value = matchId
        job?.cancel()
        job = scope.launch { follow(matchId) }
        return START_NOT_STICKY
    }

    private suspend fun follow(matchId: Int) {
        val container = (application as CriczyApp).container
        val until = System.currentTimeMillis() + MAX_RUN_MS
        while (System.currentTimeMillis() < until) {
            val match = try {
                container.matches.detail(matchId)?.match
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // Keep the last score and try again.
            }
            if (match != null) {
                val content = LiveScoreText.of(match)
                val done = match.state == MatchState.COMPLETED
                notify(build(this, matchId, content.title, content.text, content.subText, ongoing = !done))
                if (done) {
                    // Leave the result in the bar, dismissible, and stop updating.
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    _pinned.value = null
                    stopSelf()
                    return
                }
                delay(if (match.state == MatchState.LIVE) LIVE_REFRESH_MS else BREAK_REFRESH_MS)
            } else {
                delay(BREAK_REFRESH_MS)
            }
        }
        stop()
    }

    private fun notify(notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked; the service keeps its foreground notification.
        }
    }

    private fun stop() {
        job?.cancel()
        _pinned.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _pinned.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_LIVE = "live_score"
        private const val NOTIFICATION_ID = 0x5C0E
        private const val EXTRA_MATCH = "match"
        private const val ACTION_STOP = "com.criczy.app.STOP_LIVE_SCORE"
        private const val LIVE_REFRESH_MS = 30_000L
        private const val BREAK_REFRESH_MS = 120_000L
        private const val MAX_RUN_MS = 8L * 60 * 60 * 1000

        private val _pinned = MutableStateFlow<Int?>(null)
        /** The match pinned to the notification bar, if any. */
        val pinned: StateFlow<Int?> = _pinned.asStateFlow()

        fun start(context: Context, matchId: Int) {
            ContextCompat.startForegroundService(context, Intent(context, LiveScoreService::class.java).putExtra(EXTRA_MATCH, matchId))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LiveScoreService::class.java).setAction(ACTION_STOP))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val channel = NotificationChannel(CHANNEL_LIVE, "Pinned live score", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The score of a match you pinned, kept up to date while it is in play"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun build(context: Context, matchId: Int, title: String, text: String?, subText: String?, ongoing: Boolean) =
            NotificationCompat.Builder(context, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_stat_criczy)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(Notifier.matchIntent(context, matchId))
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .apply {
                    if (ongoing) {
                        val stop = PendingIntent.getService(
                            context, 0, Intent(context, LiveScoreService::class.java).setAction(ACTION_STOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        addAction(0, "Stop", stop)
                    }
                }
                .build()
    }
}

/** The pinned notification's lines: scores in the title, the match situation below. */
data class LiveScoreText(val title: String, val text: String?, val subText: String?) {
    companion object {
        fun of(match: Match): LiveScoreText {
            fun side(teamId: Int, short: String): String {
                val innings = match.inningsFor(teamId)
                if (innings.isEmpty()) return short
                val current = match.currentInnings
                val score = innings.joinToString(" & ") { it.scoreText }
                val overs = innings.lastOrNull()?.takeIf { it == current && match.state != MatchState.COMPLETED }?.let { " (${it.overs})" } ?: ""
                return "$short $score$overs"
            }
            return LiveScoreText(
                title = "${side(match.home.id, match.home.shortName)} · ${side(match.away.id, match.away.shortName)}",
                text = match.statusText?.takeIf { it.isNotBlank() } ?: if (match.state == MatchState.UPCOMING) "Starting soon" else null,
                subText = listOf(match.name, match.competition.name).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
            )
        }
    }
}
