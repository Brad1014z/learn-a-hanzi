package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.SrsState
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
