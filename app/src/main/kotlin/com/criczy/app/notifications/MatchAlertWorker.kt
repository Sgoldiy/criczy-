package com.criczy.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.criczy.app.CriczyApp
import com.criczy.app.core.data.AlertPlanner
import com.criczy.app.core.network.CricketApiException
import com.criczy.app.widget.Widgets
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background check: refreshes the widgets and raises alerts for followed matches.
 * Runs every 15 minutes at most (the system minimum) and only with a network connection.
 * When nothing is followed and no widget is placed it does one cheap local check and stops.
 */
class MatchAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CriczyApp).container
        val favourites = container.favourites.snapshot()
        val prefs = container.preferences.preferences.first()
        val hasWidget = Widgets.anyPlaced(applicationContext)
        val wantsAlerts = prefs.alertsEnabled && favourites.isNotEmpty() && Notifier.canPost(applicationContext)
        if (!hasWidget && !wantsAlerts) return Result.success()

        val board = try {
            container.matches.board()
        } catch (_: CricketApiException) {
            return Result.success() // Try again at the next period; retrying sooner wastes battery.
        }
        if (hasWidget) Widgets.refresh(applicationContext, board = board)

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
