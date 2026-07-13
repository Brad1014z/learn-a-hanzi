package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.SrsEngine
import io.github.brad1014z.hanzi.engine.progress.SrsEngine.DAY_MS
import io.github.brad1014z.hanzi.engine.progress.SrsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SrsEngineTest {

    private val t0 = 1_700_000_000_000L

    private fun learn(grade: Int = 5, from: CharacterProgress? = null, at: Long = t0) =
        SrsEngine.apply(from, "火", grade, at)

    @Test
    fun `first exposure leaves the card due NOW - the session-tail re-test (spec 06)`() {
        val p = learn(grade = 5)
        assertEquals(SrsState.LEARNING, p.state)
        assertEquals(1, p.reps)
        assertEquals(t0, p.dueAt) // tail re-test pending
    }

    @Test
    fun `tail re-test passed schedules the 1-day step`() {
        var p = learn(grade = 5, at = t0)
        p = SrsEngine.apply(p, "火", 4, t0 + 60_000) // tail re-test, same session
        assertEquals(SrsState.LEARNING, p.state)
        assertEquals(2, p.reps)
        assertEquals(t0 + 60_000 + DAY_MS, p.dueAt)
    }

    @Test
    fun `three successes graduate to REVIEW at 1 day interval`() {
        var p = learn(grade = 5, at = t0)
        p = SrsEngine.apply(p, "火", 4, t0 + 60_000)
        val t2 = p.dueAt
        p = SrsEngine.apply(p, "火", 4, t2)
        assertEquals(SrsState.REVIEW, p.state)
        assertEquals(3, p.reps)
        assertEquals(1.0, p.intervalDays)
        assertEquals(t2 + DAY_MS, p.dueAt)
    }

    @Test
    fun `a failed learning step resets to the session tail, not a lapse`() {
        var p = learn(grade = 5)
        p = SrsEngine.apply(p, "火", 2, t0 + 60_000)
        assertEquals(SrsState.LEARNING, p.state)
        assertEquals(0, p.reps)
        assertEquals(0, p.lapses) // never learned ⇒ nothing forgotten
        assertEquals(t0 + 60_000, p.dueAt) // due immediately (session tail)
    }

    @Test
    fun `review successes grow the interval on the SM-2 schedule`() {
        var p = graduated()
        val t1 = p.dueAt
        p = SrsEngine.apply(p, "火", 5, t1) // 1d → 6d
        assertEquals(6.0, p.intervalDays)
        val t2 = p.dueAt
        p = SrsEngine.apply(p, "火", 5, t2) // 6d → 6·EF
        assertTrue(p.intervalDays > 6.0 * 2, "interval should multiply by ease, was ${p.intervalDays}")
        assertEquals(SrsState.REVIEW, p.state)
    }

    @Test
    fun `grade 5 raises ease and grade 3 lowers it`() {
        var up = graduated()
        up = SrsEngine.apply(up, "火", 5, up.dueAt)
        assertTrue(up.ease > CharacterProgress.INITIAL_EASE)

        var down = graduated()
        down = SrsEngine.apply(down, "火", 3, down.dueAt)
        assertTrue(down.ease < CharacterProgress.INITIAL_EASE)
    }

    @Test
    fun `a review failure is a lapse - RELEARNING, halved pending interval, ease penalty`() {
        var p = graduated()
        repeat(3) { p = SrsEngine.apply(p, "火", 5, p.dueAt) } // grow the interval
        val grownInterval = p.intervalDays
        val easeBefore = p.ease
        val tFail = p.dueAt
        p = SrsEngine.apply(p, "火", 1, tFail)
        assertEquals(SrsState.RELEARNING, p.state)
        assertEquals(1, p.lapses)
        assertEquals(0, p.reps)
        assertEquals(easeBefore - 0.2, p.ease, 1e-9)
        assertEquals(grownInterval * 0.5, p.intervalDays, 1e-9) // pending interval
        assertEquals(tFail, p.dueAt) // session tail
    }

    @Test
    fun `relearning re-graduates with the halved interval after three successes`() {
        var p = graduated()
        repeat(3) { p = SrsEngine.apply(p, "火", 5, p.dueAt) }
        val halved = SrsEngine.apply(p, "火", 1, p.dueAt).intervalDays
        var q = SrsEngine.apply(p, "火", 1, p.dueAt) // lapse
        q = SrsEngine.apply(q, "火", 4, q.dueAt) // relearn quiz
        assertEquals(SrsState.RELEARNING, q.state)
        q = SrsEngine.apply(q, "火", 4, q.dueAt) // tail re-test
        assertEquals(SrsState.RELEARNING, q.state)
        q = SrsEngine.apply(q, "火", 4, q.dueAt) // 1-day step → re-graduate
        assertEquals(SrsState.REVIEW, q.state)
        assertEquals(halved, q.intervalDays, 1e-9)
    }

    @Test
    fun `ease never drops below the 1_3 floor even in a lapse chain`() {
        var p = graduated()
        repeat(12) {
            p = SrsEngine.apply(p, "火", 1, p.dueAt) // lapse
            p = SrsEngine.apply(p, "火", 3, p.dueAt) // relearn quiz
            p = SrsEngine.apply(p, "火", 3, p.dueAt) // tail re-test
            p = SrsEngine.apply(p, "火", 3, p.dueAt) // 1-day step → REVIEW
            p = SrsEngine.apply(p, "火", 3, p.dueAt) // graded review keeps pulling ease down
        }
        assertTrue(p.ease >= SrsEngine.MIN_EASE - 1e-9, "ease ${p.ease} broke the floor")
        assertTrue(p.intervalDays >= 1.0)
    }

    @Test
    fun `interval is capped`() {
        var p = graduated()
        repeat(30) { p = SrsEngine.apply(p, "火", 5, p.dueAt) }
        assertTrue(p.intervalDays <= SrsEngine.MAX_INTERVAL_DAYS)
    }

    @Test
    fun `grades outside 0-5 are rejected`() {
        assertFailsWith<IllegalArgumentException> { SrsEngine.apply(null, "火", 6, t0) }
        assertFailsWith<IllegalArgumentException> { SrsEngine.apply(null, "火", -1, t0) }
    }

    @Test
    fun `lastGrade and lastReviewedAt always record the latest answer`() {
        var p = learn(grade = 5)
        p = SrsEngine.apply(p, "火", 2, t0 + 5_000)
        assertEquals(2, p.lastGrade)
        assertEquals(t0 + 5_000, p.lastReviewedAt)
    }

    private fun graduated(): CharacterProgress {
        var p = learn(grade = 5, at = t0)
        p = SrsEngine.apply(p, "火", 5, t0 + 60_000)
        p = SrsEngine.apply(p, "火", 5, p.dueAt)
        check(p.state == SrsState.REVIEW)
        return p
    }
}
