package com.criczy.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.criczy.app.MainActivity
import com.criczy.app.R
import com.criczy.app.core.data.AlertKind
import com.criczy.app.core.data.MatchAlert
import com.criczy.app.navigation.Routes

object Notifier {
    const val CHANNEL_MATCHES = "match_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_MATCHES, "Match alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Starts, wickets and results for teams, series and matches you follow"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun matchIntent(context: Context, matchId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Routes.matchDeepLink(matchId), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, matchId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** One notification per match, replaced as the match moves on, so alerts never pile up. */
    fun post(context: Context, alert: MatchAlert) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MATCHES)
            .setSmallIcon(R.drawable.ic_stat_criczy)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(matchIntent(context, alert.matchId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(alert.kind == AlertKind.WICKET)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alert.matchId, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}
