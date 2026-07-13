package io.github.brad1014z.hanzi.engine.progress

/** SRS card lifecycle states (spec 03/06). */
enum class SrsState { NEW, LEARNING, REVIEW, RELEARNING }

/**
 * Per-character progress, shaped exactly per spec 03 so the SRS milestone (M3) is an
 * engine change, not a schema migration. Until SM-2 lands, the scheduling fields hold
 * the pre-SRS placeholder semantics documented on [applyPractice].
 */
data class CharacterProgress(
    val character: String,
    val state: SrsState,
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    val lastGrade: Int?,
) {
    companion object {
        /** SM-2 initial ease factor (spec 03: ease ≥ 1.3, starts at 2.5). */
        const val INITIAL_EASE = 2.5

        fun initial(character: String, now: Long) = CharacterProgress(
            character = character,
            state = SrsState.LEARNING,
            dueAt = now,
            intervalDays = 0.0,
            ease = INITIAL_EASE,
            reps = 0,
            lapses = 0,
            lastReviewedAt = null,
            lastGrade = null,
        )
    }
}

// M1's pre-SRS `applyPractice` placeholder was replaced by [SrsEngine.apply] in M3 —
// same signature shape, real scheduling (spec 06).
