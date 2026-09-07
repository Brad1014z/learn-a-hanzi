package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.data.CharacterRepository
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict
import io.github.brad1014z.hanzi.engine.quiz.HelpPolicy
import io.github.brad1014z.hanzi.engine.quiz.QuizEngine
import io.github.brad1014z.hanzi.engine.quiz.QuizState
import io.github.brad1014z.hanzi.engine.quiz.StrokeHelp
import io.github.brad1014z.hanzi.engine.quiz.StrokeOutcome
import io.github.brad1014z.hanzi.engine.quiz.toGrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The escalating help ladder (spec 05, failure UX). The property under test is that a
 * learner cannot get stranded on one stroke: help arrives on its own, and it arrives in
 * increasing strength, without ever blocking the card or capping attempts.
 */
class StrokeHelpLadderTest {

    private val repo = CharacterRepository()
    private val engine = QuizEngine()

    /** A stroke that is nowhere near the target, so it is always rejected. */
    private fun QuizState.missOnce(): QuizState {
        val target = character.medians[expectedIndex]
        val wrong = target.map { Point(it.x + 300.0, it.y + 300.0) }
        val (next, verdict) = engine.submitStroke(this, wrong)
        assertIs<StrokeVerdict.Reject>(verdict)
        return next
    }

    private fun QuizState.miss(times: Int): QuizState {
        var s = this
        repeat(times) { s = s.missOnce() }
        return s
    }

    @Test
    fun `help escalates none to offer to auto to show me as misses pile up`() {
        var state = engine.start(repo.load("十"))
        assertEquals(StrokeHelp.NONE, engine.helpFor(state), "no help before any miss")

        state = state.miss(1)
        assertEquals(StrokeHelp.NONE, engine.helpFor(state), "one miss is just a miss")

        state = state.miss(1) // 2
        assertEquals(StrokeHelp.OFFER_HINT, engine.helpFor(state))

        state = state.miss(1) // 3
        assertEquals(StrokeHelp.AUTO_HINT, engine.helpFor(state))

        // The UI acts on AUTO_HINT by taking the hint for the learner.
        state = engine.useHint(state)
        assertEquals(
            StrokeHelp.NONE,
            engine.helpFor(state),
            "with the stroke on screen, stop nagging and let them trace it",
        )

        state = state.miss(2) // 5 total: hint shown and still missing
        assertEquals(StrokeHelp.SHOW_ME, engine.helpFor(state))
    }

    @Test
    fun `show me survives further misses and never becomes a dead end`() {
        var state = engine.start(repo.load("十"))
        state = engine.useHint(state.miss(HelpPolicy.SHOW_ME_AFTER_REJECTS))
        assertEquals(StrokeHelp.SHOW_ME, engine.helpFor(state))

        // Twenty more misses: still offering help, still on the same stroke, no
        // "out of attempts" state anywhere.
        state = state.miss(20)
        assertEquals(StrokeHelp.SHOW_ME, engine.helpFor(state))
        assertEquals(0, state.expectedIndex, "the card has not moved on or ended")
    }

    @Test
    fun `a stroke rescued by help still completes, graded as hinted`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        state = engine.useHint(state.miss(HelpPolicy.AUTO_HINT_AFTER_REJECTS))

        // Now trace it correctly, then finish the character cleanly.
        for (i in 0 until shi.strokeCount) {
            val (next, verdict) = engine.submitStroke(state, shi.medians[i])
            assertIs<StrokeVerdict.Accept>(verdict)
            state = next
        }

        assertTrue(state.isComplete, "help must never prevent completion")
        assertEquals(StrokeOutcome.HINTED, state.records.first().outcome)
        assertEquals(
            2,
            state.toGrade(),
            "three misses AND a hint grades 2 (heavy retries), not 3: automatic help must " +
                "not launder how hard the stroke actually was, or SRS would schedule it " +
                "as if the learner knew it (constitution: game signals stay truthful)",
        )
    }

    @Test
    fun `taking the hint early still grades 3, so accepting help is not punished`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        // Two misses — the OFFER_HINT rung — then take it, below the heavy-retry line.
        state = engine.useHint(state.miss(HelpPolicy.OFFER_HINT_AFTER_REJECTS))
        for (i in 0 until shi.strokeCount) {
            val (next, _) = engine.submitStroke(state, shi.medians[i])
            state = next
        }
        assertTrue(state.isComplete)
        assertEquals(StrokeOutcome.HINTED, state.records.first().outcome)
        assertEquals(3, state.toGrade(), "a hint alone caps at 3 — asking for help is fine")
    }

    @Test
    fun `help resets once the learner moves to the next stroke`() {
        val shi = repo.load("十")
        var state = engine.start(shi)
        state = state.miss(HelpPolicy.OFFER_HINT_AFTER_REJECTS)
        assertEquals(StrokeHelp.OFFER_HINT, engine.helpFor(state))

        val (next, verdict) = engine.submitStroke(state, shi.medians[0])
        assertIs<StrokeVerdict.Accept>(verdict)
        state = next

        assertEquals(
            StrokeHelp.NONE,
            engine.helpFor(state),
            "struggling with stroke 1 must not pre-load help for stroke 2",
        )
    }

    @Test
    fun `a completed card asks for no further help`() {
        val yi = repo.load("一")
        var state = engine.start(yi)
        state = state.miss(HelpPolicy.SHOW_ME_AFTER_REJECTS)
        val (next, _) = engine.submitStroke(state, yi.medians[0])
        state = next
        assertTrue(state.isComplete)
        assertEquals(StrokeHelp.NONE, engine.helpFor(state))
    }
}
