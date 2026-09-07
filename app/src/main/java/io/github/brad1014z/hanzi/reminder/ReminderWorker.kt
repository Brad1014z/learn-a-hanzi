package io.github.brad1014z.hanzi.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.data.SettingsStore
import io.github.brad1014z.hanzi.engine.reminder.ReminderPolicy
import kotlinx.coroutines.flow.first

/**
 * The daily reminder tick (spec 10 guardrail 5). Runs once, decides whether today
 * deserves a nudge, then hands off to [ReminderScheduler] to arrange the next one —
 * this worker never repeats itself; each run schedules exactly the next run.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val settings = SettingsStore(applicationContext)
        val enabled = settings.reminderEnabled.first()
        if (enabled) {
            val progress = progressRepositoryFactory(applicationContext)
            val playedToday = progress.playedToday()
            if (ReminderPolicy.shouldNotify(enabled = true, playedToday = playedToday)) {
                ReminderNotifications.notify(applicationContext)
            }
            // Reschedule regardless of whether we notified — a day played is not a
            // reason to stop reminding tomorrow.
            val hour = settings.reminderHour.first()
            val minute = settings.reminderMinute.first()
            ReminderScheduler.schedule(applicationContext, hour, minute)
        }
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        /**
         * Test-only seam, mirroring the existing `Cloud.activityProvider` pattern
         * elsewhere in this app: production always resolves the real singleton;
         * `ReminderWorkerTest` substitutes an in-memory repository so the worker can be
         * exercised without the bundled dataset asset or the `HanziDatabase` singleton's
         * cross-test state.
         */
        internal var progressRepositoryFactory: (Context) -> RoomProgressRepository =
            { context -> RoomProgressRepository(HanziDatabase.get(context)) }
    }
}
