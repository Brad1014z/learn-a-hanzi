package io.github.brad1014z.hanzi.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.brad1014z.hanzi.engine.reminder.ReminderPolicy
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Arranges [ReminderWorker] runs (spec 10 guardrail 5). Deliberately inexact: this is a
 * once-a-day habit nudge, not an alarm clock, so it uses WorkManager rather than
 * `AlarmManager`'s exact-alarm APIs — no `SCHEDULE_EXACT_ALARM` permission, no boot
 * receiver, and it survives reboots on its own (constitution: minimal permissions).
 * The trade-off is a delivery window of roughly the chosen minute, sometimes later under
 * Doze — acceptable for "remind me this evening", not for anything time-critical.
 */
object ReminderScheduler {
    private const val UNIQUE_WORK_NAME = "daily-reminder"

    /** (Re)schedules the next tick for [hour]:[minute] local time, replacing any pending one. */
    fun schedule(context: Context, hour: Int, minute: Int) {
        ReminderNotifications.ensureChannel(context)
        val delay = ReminderPolicy.nextTrigger(
            hour = hour,
            minute = minute,
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
        ).epochSecond - Instant.now().epochSecond
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.SECONDS)
            .build()
        // A precise one-shot timed to the chosen minute, which reschedules itself
        // (see ReminderWorker) — a plain 24h PeriodicWorkRequest would drift away from
        // the chosen time run over run, since WorkManager doesn't re-align periodic
        // intervals to a wall-clock target.
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
