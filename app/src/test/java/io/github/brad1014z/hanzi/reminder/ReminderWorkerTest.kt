package io.github.brad1014z.hanzi.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.brad1014z.hanzi.data.HanziDatabase
import io.github.brad1014z.hanzi.data.ReviewLogEntity
import io.github.brad1014z.hanzi.data.RoomProgressRepository
import io.github.brad1014z.hanzi.data.SettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The reminder tick, end to end through the real Worker (spec 10 guardrail 5). Uses
 * Robolectric's shadow NotificationManager to observe whether a notification was
 * actually posted — the property that matters, not just that `doWork` returned success.
 *
 * The database is swapped for an in-memory one via [ReminderWorker.progressRepositoryFactory]
 * (mirrors the `Cloud.activityProvider` test seam), so this stays fast and isolated from
 * the real `HanziDatabase` singleton and its bundled asset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderWorkerTest {

    private lateinit var db: HanziDatabase
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // ReminderScheduler.schedule (called by doWork after a successful tick) reaches
        // the real WorkManager.getInstance(context) to queue the next tick; this is the
        // documented way to make that available under test.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, HanziDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ReminderWorker.progressRepositoryFactory = { RoomProgressRepository(db) }
        settings = SettingsStore(context)
    }

    @After
    fun tearDown() {
        ReminderWorker.progressRepositoryFactory = { ctx -> RoomProgressRepository(HanziDatabase.get(ctx)) }
        db.close()
    }

    private fun notificationCount(): Int {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        return Shadows.shadowOf(manager).allNotifications.size
    }

    private fun worker(): ReminderWorker {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestListenableWorkerBuilder<ReminderWorker>(context).build()
    }

    /**
     * Robolectric denies runtime permissions by default, unlike a real device where the
     * learner already granted this before the reminder could be enabled at all — grant
     * it explicitly wherever a test needs the worker to actually be able to notify.
     */
    private fun grantNotificationPermission() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `disabled reminder never notifies`() = runTest {
        grantNotificationPermission()
        settings.setReminderEnabled(false)
        val result = worker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notificationCount())
    }

    @Test
    fun `enabled reminder notifies when today has no graded write`() = runTest {
        grantNotificationPermission()
        settings.setReminderEnabled(true)
        settings.setReminderTime(18, 0)
        val result = worker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, notificationCount())
    }

    @Test
    fun `permission not granted -- stays silent rather than crashing`() = runTest {
        // Deliberately no grantNotificationPermission() here: this is the state a real
        // device is in if the learner revokes the permission after enabling reminders
        // in Settings (Android allows that at any time from app settings).
        settings.setReminderEnabled(true)
        settings.setReminderTime(18, 0)
        val result = worker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notificationCount())
    }

    @Test
    fun `enabled reminder stays silent on a day already played`() = runTest {
        grantNotificationPermission()
        settings.setReminderEnabled(true)
        settings.setReminderTime(18, 0)
        db.progressDao().insertLog(
            ReviewLogEntity(
                uuid = "test-1", character = "人", reviewedAt = System.currentTimeMillis(),
                grade = 5, drawnCorrectly = true, durationMs = null, session = "quest-new",
            ),
        )
        val result = worker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            "a day already played must not be nudged (constitution: no guilt mechanics)",
            0,
            notificationCount(),
        )
    }
}
