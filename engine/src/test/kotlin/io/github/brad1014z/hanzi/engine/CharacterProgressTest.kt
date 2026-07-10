package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.SrsState
import io.github.brad1014z.hanzi.engine.progress.applyPractice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CharacterProgressTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `initial progress matches the spec 03 defaults`() {
        val p = CharacterProgress.initial("火", now)
        assertEquals(SrsState.LEARNING, p.state)
        assertEquals(now, p.dueAt)
        assertEquals(0.0, p.intervalDays)
        assertEquals(CharacterProgress.INITIAL_EASE, p.ease)
        assertEquals(0, p.reps)
        assertEquals(0, p.lapses)
        assertNull(p.lastReviewedAt)
        assertNull(p.lastGrade)
    }

    @Test
    fun `first practice creates a row with the grade recorded`() {
        val p = applyPractice(previous = null, character = "火", grade = 5, now = now)
        assertEquals("火", p.character)
        assertEquals(5, p.lastGrade)
        assertEquals(now, p.lastReviewedAt)
        assertEquals(1, p.reps)
        assertEquals(0, p.lapses)
    }

    @Test
    fun `successful reps accumulate and a low grade resets them`() {
        var p = applyPractice(null, "火", grade = 5, now = now)
        p = applyPractice(p, "火", grade = 4, now = now + 1)
        assertEquals(2, p.reps)
        p = applyPractice(p, "火", grade = 2, now = now + 2)
        assertEquals(0, p.reps)
        assertEquals(1, p.lapses)
    }

    @Test
    fun `a failed first attempt is not counted as a lapse`() {
        val p = applyPractice(null, "我", grade = 1, now = now)
        assertEquals(0, p.lapses)
        assertEquals(0, p.reps)
        assertEquals(1, p.lastGrade)
    }

    @Test
    fun `pre-SRS placeholder keeps the card due now and LEARNING`() {
        var p = applyPractice(null, "火", grade = 5, now = now)
        p = applyPractice(p, "火", grade = 5, now = now + 100)
        assertEquals(now + 100, p.dueAt)
        assertEquals(SrsState.LEARNING, p.state)
        assertEquals(CharacterProgress.INITIAL_EASE, p.ease)
    }

    @Test
    fun `grades outside 0-5 are rejected`() {
        assertFailsWith<IllegalArgumentException> { applyPractice(null, "火", grade = 6, now = now) }
        assertFailsWith<IllegalArgumentException> { applyPractice(null, "火", grade = -1, now = now) }
    }
}
