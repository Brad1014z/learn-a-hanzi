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
    fun shouldOfferHint(state: QuizState): Boolean =
        !state.isComplete && state.rejectsOnCurrent >= 2 && !state.hintActive
}
