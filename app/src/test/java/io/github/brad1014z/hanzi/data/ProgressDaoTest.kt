package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DAO behavior that the sync layer (spec 12) and daily cap (spec 04) lean on.
 * In-memory Room under Robolectric — no device, runs with the unit suite in CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressDaoTest {
    private lateinit var db: HanziDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HanziDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    private fun log(character: String, at: Long, session: String?, uuid: String) = ReviewLogEntity(
        character = character, reviewedAt = at, grade = 4, drawnCorrectly = true,
        durationMs = null, session = session, uuid = uuid,
    )

    @Test
    fun `restore union ignores review logs whose uuid is already present`() = runTest {
        val dao = db.progressDao()
        dao.insertLog(log("火", 1000, "quest", uuid = "aaa"))

        // Set-union restore (spec 12): the same uuid arriving from the backup is a no-op…
        dao.insertLogIgnore(log("火", 9999, "quest", uuid = "aaa"))
        // …and a new uuid lands.
        dao.insertLogIgnore(log("人", 2000, "quest", uuid = "bbb"))

        val logs = dao.allLogs()
        assertEquals(2, logs.size)
        assertEquals(1000, logs.first { it.uuid == "aaa" }.reviewedAt)
    }

    @Test
    fun `introducedSince counts only characters first seen in a guided session today`() = runTest {
        val dao = db.progressDao()
        val todayStart = 10_000L
        // 火: introduced yesterday, reviewed today — not new today.
        dao.insertLog(log("火", 5_000, "quest", uuid = "u1"))
        dao.insertLog(log("火", 12_000, "quest", uuid = "u2"))
        // 人: first seen today via the guided quest — counts.
        dao.insertLog(log("人", 11_000, "quest", uuid = "u3"))
        // 我: first seen today but in browse — cap-exempt (spec 04).
        dao.insertLog(log("我", 13_000, "browse", uuid = "u4"))

        assertEquals(1, dao.introducedSince(todayStart, sessionTag = "quest"))
    }

    @Test
    fun `outbox enqueue is idempotent and drains oldest first`() = runTest {
        val dao = db.outboxDao()
        dao.enqueue(SyncOutboxEntity(uuid = "a", kind = "xp", payload = "{}", createdAt = 300))
        dao.enqueue(SyncOutboxEntity(uuid = "b", kind = "progress", payload = "{}", createdAt = 100))
        // WorkManager retry re-enqueues the same uuid: must not duplicate.
        dao.enqueue(SyncOutboxEntity(uuid = "a", kind = "xp", payload = "{}", createdAt = 300))

        assertEquals(2, dao.count())
        assertEquals(listOf("b", "a"), dao.oldest().map { it.uuid })

        dao.delete(listOf("b"))
        assertEquals(listOf("a"), dao.oldest().map { it.uuid })
    }
}
