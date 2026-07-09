package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.data.CharacterRepository
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict
import io.github.brad1014z.hanzi.engine.quiz.QuizEngine
import io.github.brad1014z.hanzi.engine.quiz.StrokeOutcome
import io.github.brad1014z.hanzi.engine.quiz.StrokeRecord
import io.github.brad1014z.hanzi.engine.quiz.drawnCorrectly
import io.github.brad1014z.hanzi.engine.quiz.toGrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QuizEngineTest {

    private val repo = CharacterRepository()
    private val engine = QuizEngine()

    @Test
    fun `a clean run through a character completes with grade 5`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        for (median in shi.medians) {
            val (next, verdict) = engine.submitStroke(state, median)
            assertIs<StrokeVerdict.Accept>(verdict)
            state = next
        }
        assertTrue(state.isComplete)
        assertEquals(5, state.toGrade())
        assertTrue(state.drawnCorrectly())
    }

    @Test
    fun `rejects accumulate and trigger the hint offer after two misses`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        val wrong = shi.medians[0].map { Point(it.x + 300.0, it.y + 300.0) }
        repeat(2) {
            val (next, verdict) = engine.submitStroke(state, wrong)
            assertIs<StrokeVerdict.Reject>(verdict)
            state = next
        }
        assertTrue(engine.shouldOfferHint(state))
        assertEquals(0, state.expectedIndex)
    }

    @Test
    fun `a hinted accept marks the stroke HINTED and caps the grade at 3`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        state = engine.useHint(state)
        val (afterHinted, verdict) = engine.submitStroke(state, shi.medians[0])
        assertIs<StrokeVerdict.Accept>(verdict)
        state = afterHinted
        assertEquals(StrokeOutcome.HINTED, state.records.single().outcome)

        val (complete, _) = engine.submitStroke(state, shi.medians[1])
        assertTrue(complete.isComplete)
        assertEquals(3, complete.toGrade())
        assertFalse(complete.drawnCorrectly())
    }

    @Test
    fun `undo rewinds the last accepted stroke`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        state = engine.submitStroke(state, shi.medians[0]).first
        assertEquals(1, state.expectedIndex)
        state = engine.undo(state)
        assertEquals(0, state.expectedIndex)
        assertTrue(state.records.isEmpty())
    }

    @Test
    fun `grade table matches spec 05`() {
        val data = repo.load("一")
        fun state(vararg records: StrokeRecord) =
            engine.start(data).copy(expectedIndex = data.strokeCount, records = records.toList())

        val clean = StrokeRecord(StrokeOutcome.CLEAN, 0)
        assertEquals(5, state(clean).toGrade())
        assertEquals(4, state(clean, StrokeRecord(StrokeOutcome.SLOPPY, 0)).toGrade())
        assertEquals(3, state(clean, StrokeRecord(StrokeOutcome.HINTED, 1)).toGrade())
        assertEquals(2, state(clean, StrokeRecord(StrokeOutcome.CLEAN, 3)).toGrade())
        assertEquals(1, engine.start(data).toGrade()) // abandoned / incomplete
    }
}
