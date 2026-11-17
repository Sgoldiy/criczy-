package app.criczy.notifications

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.criczy.CriczyApp
import app.criczy.core.data.AlertPlanner
import app.criczy.core.network.CricketApiException
import app.criczy.widget.LiveScoreWidget
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background check: refreshes the widget and raises alerts for followed matches.
 * Runs every 15 minutes at most (the system minimum) and only with a network connection.
 * When nothing is followed and no widget is placed it does one cheap local check and stops.
 */
class MatchAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CriczyApp).container
        val favourites = container.favourites.snapshot()
        val prefs = container.preferences.preferences.first()
        val hasWidget = LiveScoreWidget.hasInstances(applicationContext)
        val wantsAlerts = prefs.alertsEnabled && favourites.isNotEmpty() && Notifier.canPost(applicationContext)
        if (!hasWidget && !wantsAlerts) return Result.success()

        val board = try {
            container.matches.board()
        } catch (_: CricketApiException) {
            return Result.success() // Try again at the next period; retrying sooner wastes battery.
        }
        if (hasWidget) LiveScoreWidget().updateAll(applicationContext)

        if (wantsAlerts) {
            val followed = AlertPlanner.followed(board.all, favourites)
            val dao = container.database.alertState()
            val now = System.currentTimeMillis()
            val plan = AlertPlanner.plan(followed, dao.forMatches(followed.map { it.id }), prefs, now)
            dao.upsert(plan.newStates)
            dao.deleteOlderThan(now - TimeUnit.DAYS.toMillis(14))
            plan.alerts.forEach { Notifier.post(applicationContext, it) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "match-alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MatchAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
