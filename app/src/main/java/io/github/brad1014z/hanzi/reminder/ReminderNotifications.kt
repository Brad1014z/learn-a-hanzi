package io.github.brad1014z.hanzi.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.brad1014z.hanzi.MainActivity
import io.github.brad1014z.hanzi.R
import io.github.brad1014z.hanzi.engine.reminder.ReminderPolicy

/**
 * The one neutral notification this app ever sends (spec 10 guardrail 5). No badge
 * count, no repeat-alert, no sound override — a single quiet nudge that opens straight
 * into the app.
 */
object ReminderNotifications {
    const val CHANNEL_ID = "daily-reminder"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "One quiet nudge a day, only on days you haven't practiced yet."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** No-op, silently, if permission was revoked after the reminder was enabled. */
    fun notify(context: Context) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            android.content.Intent(context, MainActivity::class.java)
                .setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ReminderPolicy.MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        // hasPermission() above is the real guard; this catch is belt-and-braces for the
        // narrow window where the OS revokes the permission between that check and this
        // call (also satisfies lint's MissingPermission, which can't see across the
        // function boundary to hasPermission()).
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight — nothing to show, nothing to crash.
        }
    }
}
