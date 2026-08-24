package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.brad1014z.hanzi.engine.play.CardCompletion
import io.github.brad1014z.hanzi.engine.play.ChestCompletion
import io.github.brad1014z.hanzi.engine.play.XpConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomQuestStoreTest {
    private lateinit var db: HanziDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HanziDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    private fun card(id: String = "attempt-1") = CardCompletion(
        attemptId = id,
        character = "人",
        grade = 5,
        drawnCorrectly = true,
        durationMs = 2_000,
        sessionTag = Sessions.QUEST_NEW,
        reviewedAt = 1_700_000_000_000,
        xpDelta = XpConfig.NEW_CHARACTER,
    )

    @Test
    fun `card commit rolls every table back when the transaction fails`() = runTest {
        val store = RoomQuestStore(
            db = db,
            afterCardWrites = { error("injected failure") },
        )

        assertFailsWith<IllegalStateException> { store.commitCard(card()) }

        assertNull(db.progressDao().get("人"))
        assertNull(db.progressDao().logByUuid("attempt-1"))
        assertNull(db.metaDao().get(RoomProgressRepository.XP_KEY))
        assertTrue(db.metaDao().all().none { it.key.startsWith(RoomProgressRepository.WEEK_XP_PREFIX) })
        assertEquals(0, db.outboxDao().count())
    }

    @Test
    fun `repeating an attempt id cannot double award progress or xp`() = runTest {
        val store = RoomQuestStore(db)

        val first = store.commitCard(card())
        val repeated = store.commitCard(card())

        assertTrue(first.applied)
        assertFalse(repeated.applied)
        assertEquals(XpConfig.NEW_CHARACTER, repeated.totalXp)
        assertEquals(1, db.progressDao().allLogs().size)
        assertEquals(1, assertNotNull(db.progressDao().get("人")).reps)
        assertEquals(3, db.outboxDao().count()) // review log + SRS snapshot + XP
    }

    @Test
    fun `chest reward is transactional and idempotent`() = runTest {
        val store = RoomQuestStore(db)
        store.commitCard(card())
        val chest = ChestCompletion("core-2026-08-23", card().reviewedAt + 1, XpConfig.CHEST_OPENED)

        val first = store.openChest(chest)
        val repeated = store.openChest(chest)

        assertTrue(first.applied)
        assertFalse(repeated.applied)
        assertEquals(XpConfig.NEW_CHARACTER + XpConfig.CHEST_OPENED, repeated.totalXp)
        assertNotNull(db.metaDao().get("quest:chest:${chest.completionId}"))
    }

    @Test
    fun `chest failure rolls reward metadata xp and outbox back together`() = runTest {
        val store = RoomQuestStore(
            db = db,
            afterCardWrites = {},
            afterChestWrites = { error("injected chest failure") },
        )
        val chest = ChestCompletion("core-2026-08-23", 1_700_000_000_000, XpConfig.CHEST_OPENED)

        assertFailsWith<IllegalStateException> { store.openChest(chest) }

        assertNull(db.metaDao().get("quest:chest:${chest.completionId}"))
        assertNull(db.metaDao().get(RoomProgressRepository.XP_KEY))
        assertTrue(db.metaDao().all().none { it.key.startsWith("xpWeek:") || it.key.startsWith("quest:") })
        assertEquals(0, db.outboxDao().count())
    }

    @Test
    fun `reset clears progress logs xp weeks quest markers and outbox but keeps content metadata`() = runTest {
        val store = RoomQuestStore(db)
        store.commitCard(card())
        store.openChest(ChestCompletion("core", card().reviewedAt + 1, XpConfig.CHEST_OPENED))
        db.metaDao().put(MetaEntity("datasetVersion", "content-v1"))
        db.metaDao().put(MetaEntity("sync:lastRestore", "old"))

        store.resetAllProgress()

        assertTrue(db.progressDao().allProgress().isEmpty())
        assertTrue(db.progressDao().allLogs().isEmpty())
        assertEquals(0, db.outboxDao().count())
        assertEquals("0", db.metaDao().get(RoomProgressRepository.XP_KEY))
        assertEquals("content-v1", db.metaDao().get("datasetVersion"))
        assertNull(db.metaDao().get("sync:lastRestore"))
        assertTrue(db.metaDao().all().none { it.key.startsWith("xpWeek:") || it.key.startsWith("quest:") })
        assertEquals("true", db.metaDao().get(RoomQuestStore.RESTORE_BLOCKED_KEY))
    }
}
