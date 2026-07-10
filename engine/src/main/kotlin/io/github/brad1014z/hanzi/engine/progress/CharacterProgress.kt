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

/**
 * Pre-SRS bookkeeping (M1): fold one completed practice into a character's progress,
 * recording facts without scheduling — the card stays LEARNING and "due now" until
 * SM-2 (M3, spec 06) takes over the interval/ease/state fields. `reps` counts
 * successes in a row (grade ≥ 3, the SM-2 convention); `lapses` counts grade < 3 on a
 * previously-seen card ("needing that much correction is forgetting", spec 05).
 */
fun applyPractice(
    previous: CharacterProgress?,
    character: String,
    grade: Int,
    now: Long,
): CharacterProgress {
    require(grade in 0..5) { "grade must be 0..5, was $grade" }
    val base = previous ?: CharacterProgress.initial(character, now)
    return base.copy(
        dueAt = now,
        reps = if (grade >= 3) base.reps + 1 else 0,
        lapses = base.lapses + if (previous != null && grade < 3) 1 else 0,
        lastReviewedAt = now,
        lastGrade = grade,
    )
}
