package io.github.brad1014z.hanzi.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Drains the sync outbox + refreshes the restore-merge when signed in and online
 * (spec 12: WorkManager on connectivity with backoff). Offline or signed out it is a
 * silent no-op — the airplane-mode experience stays untouched (constitution).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val cloud = Cloud.get(applicationContext)
        cloud.sync.drainOutbox()
        cloud.sync.restore()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private val CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** One-shot drain — call after quests and on app start. */
        fun kickNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(CONSTRAINTS).build(),
            )
        }

        /** Background safety net, every 6 hours on connectivity. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(CONSTRAINTS).build(),
            )
        }
    }
}
