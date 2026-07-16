package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.social.Identity
import io.github.brad1014z.hanzi.engine.social.SyncMerge
import io.github.brad1014z.hanzi.engine.social.Weeks
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocialTest {

    // ----- Identity (spec 12: generated, safe, no free text) --------------------

    @Test
    fun `generated names are two kid-friendly words and re-rolls vary`() {
        val name = Identity.generateName(Random(1))
        val (adj, animal) = name.split(" ")
        assertTrue(adj in Identity.ADJECTIVES && animal in Identity.ANIMALS)
        val rolls = (1..20).map { Identity.generateName(Random(it)) }.toSet()
        assertTrue(rolls.size > 5, "re-rolls should actually vary")
    }

    @Test
    fun `friend codes use the unambiguous alphabet and validate`() {
        repeat(50) { seed ->
            val code = Identity.generateFriendCode(Random(seed))
            assertEquals(Identity.CODE_LENGTH, code.length)
            assertTrue(Identity.isValidCode(code), "invalid: $code")
            assertTrue(code.none { it in "0O1IL" })
        }
        assertEquals("AB2345", Identity.normalizeCode(" ab 2-345 "))
        assertTrue(!Identity.isValidCode(Identity.normalizeCode("O00000"))) // confusables just fail
    }

    // ----- Weeks + board ceiling (spec 10/12) ------------------------------------

    @Test
    fun `week ids are ISO Mon-Sun and flip at the Monday boundary`() {
        val zone = ZoneId.of("Asia/Shanghai")
        // 2026-07-12 is a Sunday; 2026-07-13 a Monday.
        val sunday = java.time.LocalDateTime.of(2026, 7, 12, 23, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val monday = java.time.LocalDateTime.of(2026, 7, 13, 1, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals("2026-W28", Weeks.weekId(sunday, zone))
        assertEquals("2026-W29", Weeks.weekId(monday, zone))
    }

    @Test
    fun `board XP is ceilinged per session so grinding cannot dominate`() {
        assertEquals(90, Weeks.boardXpForSession(90))
        assertEquals(150, Weeks.boardXpForSession(150))
        assertEquals(150, Weeks.boardXpForSession(9_999))
        assertEquals(0, Weeks.boardXpForSession(-5))
    }

    // ----- Merge rules (spec 12) ---------------------------------------------------

    private fun progress(char: String, reviewedAt: Long?, reps: Int) =
        CharacterProgress.initial(char, 0L).copy(lastReviewedAt = reviewedAt, reps = reps)

    @Test
    fun `progress merge keeps the latest-reviewed record per character`() {
        val local = mapOf(
            "一" to progress("一", reviewedAt = 100, reps = 1),
            "火" to progress("火", reviewedAt = 500, reps = 9),
        )
        val remote = mapOf(
            "一" to progress("一", reviewedAt = 200, reps = 2), // newer → wins
            "火" to progress("火", reviewedAt = 300, reps = 3), // older → loses
            "我" to progress("我", reviewedAt = 50, reps = 1), // remote-only → adopted
        )
        val merged = SyncMerge.mergeProgress(local, remote)
        assertEquals(2, merged.getValue("一").reps)
        assertEquals(9, merged.getValue("火").reps)
        assertEquals(1, merged.getValue("我").reps)
    }

    @Test
    fun `ties and null timestamps keep local`() {
        val local = mapOf("一" to progress("一", reviewedAt = 100, reps = 1))
        val tied = SyncMerge.mergeProgress(local, mapOf("一" to progress("一", 100, 7)))
        assertEquals(1, tied.getValue("一").reps)
        val nullRemote = SyncMerge.mergeProgress(local, mapOf("一" to progress("一", null, 7)))
        assertEquals(1, nullRemote.getValue("一").reps)
    }

    @Test
    fun `review log union inserts only remote-only uuids and xp never decreases`() {
        assertEquals(
            setOf("c"),
            SyncMerge.logEntriesToInsert(localUuids = setOf("a", "b"), remoteUuids = setOf("b", "c")),
        )
        assertEquals(140, SyncMerge.mergeXpTotal(local = 140, remote = 90))
        assertEquals(200, SyncMerge.mergeXpTotal(local = 140, remote = 200))
    }
}
