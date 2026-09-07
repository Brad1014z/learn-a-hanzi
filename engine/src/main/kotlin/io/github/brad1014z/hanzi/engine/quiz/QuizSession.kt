package io.github.brad1014z.hanzi.engine.quiz

import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.geometry.Polyline
import io.github.brad1014z.hanzi.engine.grading.GradingConfig
import io.github.brad1014z.hanzi.engine.grading.StrokeGrader
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict

/** How one accepted stroke went, plus how many rejects it took to land it. */
data class StrokeRecord(val outcome: StrokeOutcome, val rejectsBeforeAccept: Int)

enum class StrokeOutcome { CLEAN, SLOPPY, HINTED }

/**
 * Immutable quiz state for one character card. The ViewModel owns an instance and folds
 * [QuizEngine] results back into it; the engine itself stays pure (spec 06).
 */
data class QuizState(
    val character: CharacterData,
    val expectedIndex: Int = 0,
    val records: List<StrokeRecord> = emptyList(),
    val rejectsOnCurrent: Int = 0,
    val hintActive: Boolean = false,
    val hintsUsed: Int = 0,
) {
    val isComplete: Boolean get() = expectedIndex >= character.strokeCount
}

/**
 * Card outcome → SM-2 grade. This is the authoritative table from spec 05 (shared with
 * the SRS engine in spec 06): clean 5, sloppy 4, hinted 3, heavy retries 2, abandoned 1.
 */
fun QuizState.toGrade(): Int = when {
    !isComplete -> 1
    records.any { it.rejectsBeforeAccept >= 3 } -> 2
    records.any { it.outcome == StrokeOutcome.HINTED } -> 3
    records.any { it.outcome == StrokeOutcome.SLOPPY } -> 4
    else -> 5
}

/** `drawnCorrectly` as logged to ReviewLog (spec 05): grade ≥ 4. */
fun QuizState.drawnCorrectly(): Boolean = toGrade() >= 4

class QuizEngine(
    private val config: GradingConfig = GradingConfig.Default,
    private val grader: StrokeGrader = StrokeGrader(config),
) {

    fun start(character: CharacterData) = QuizState(character)

    /** Grade a finished stroke and fold the verdict into the state. */
    fun submitStroke(state: QuizState, rawStroke: Polyline): Pair<QuizState, StrokeVerdict> {
        if (state.isComplete) return state to StrokeVerdict.Ignored
        val verdict = grader.grade(
            rawStroke = rawStroke,
            expectedIndex = state.expectedIndex,
            medians = state.character.medians,
            hintActive = state.hintActive,
        )
        val newState = when (verdict) {
            is StrokeVerdict.Accept -> state.copy(
                expectedIndex = state.expectedIndex + 1,
                records = state.records + StrokeRecord(
                    outcome = when {
                        state.hintActive -> StrokeOutcome.HINTED
                        verdict.sloppy -> StrokeOutcome.SLOPPY
                        else -> StrokeOutcome.CLEAN
                    },
                    rejectsBeforeAccept = state.rejectsOnCurrent,
                ),
                rejectsOnCurrent = 0,
                hintActive = false,
            )
            is StrokeVerdict.WrongOrder,
            is StrokeVerdict.Reject -> state.copy(rejectsOnCurrent = state.rejectsOnCurrent + 1)
            StrokeVerdict.Ignored -> state
        }
        return newState to verdict
    }

    /**
     * "Show me" (spec 05, failure UX): replays the demo for the current stroke and lifts
     * grading pressure — the next accept on this stroke is marked HINTED.
     */
    fun useHint(state: QuizState): QuizState {
        if (state.isComplete) return state
        return state.copy(hintActive = true, hintsUsed = state.hintsUsed + 1)
    }

    /** Undo the last accepted stroke (spec 05: persistent undo for misfires). */
    fun undo(state: QuizState): QuizState {
        if (state.records.isEmpty()) return state
        return state.copy(
            expectedIndex = state.expectedIndex - 1,
            records = state.records.dropLast(1),
            rejectsOnCurrent = 0,
            hintActive = false,
        )
    }

    /** Suggest offering the hint button prominently (spec 05: after 2 consecutive rejects). */
    fun shouldOfferHint(state: QuizState): Boolean = helpFor(state) == StrokeHelp.OFFER_HINT

    /**
     * How hard the app should be helping with the current stroke (spec 05, failure UX).
     *
     * `rejectsOnCurrent` only resets when the stroke is finally accepted (or undone), so
     * it measures how long this learner has been stuck on *this* stroke — which is
     * exactly what should drive help. Constitution principle 5: feedback teaches, it
     * never punishes, and there is no attempt limit anywhere in this ladder.
     */
    fun helpFor(state: QuizState): StrokeHelp = when {
        state.isComplete -> StrokeHelp.NONE
        // Checked before `hintActive`: showing the stroke and *still* missing it is the
        // one case where a passive hint has already proved insufficient.
        state.rejectsOnCurrent >= HelpPolicy.SHOW_ME_AFTER_REJECTS -> StrokeHelp.SHOW_ME
        state.hintActive -> StrokeHelp.NONE
        state.rejectsOnCurrent >= HelpPolicy.AUTO_HINT_AFTER_REJECTS -> StrokeHelp.AUTO_HINT
        state.rejectsOnCurrent >= HelpPolicy.OFFER_HINT_AFTER_REJECTS -> StrokeHelp.OFFER_HINT
        else -> StrokeHelp.NONE
    }
}

/**
 * The escalating help ladder for a stroke the learner keeps missing. They never have to
 * know a threshold exists — the app just gets more helpful the longer they struggle, and
 * nothing here ever blocks them or ends the card.
 */
enum class StrokeHelp {
    /** Let them try. */
    NONE,

    /** Draw attention to the Hint button — still their choice to take it. */
    OFFER_HINT,

    /** Stop waiting to be asked and show the stroke. */
    AUTO_HINT,

    /** The hint is already on screen and they are still missing: offer to draw it for
     *  them to trace along. */
    SHOW_ME,
}

/** Thresholds for [QuizEngine.helpFor], in consecutive rejects on the same stroke. */
object HelpPolicy {
    const val OFFER_HINT_AFTER_REJECTS = 2
    const val AUTO_HINT_AFTER_REJECTS = 3
    const val SHOW_ME_AFTER_REJECTS = 5
}
