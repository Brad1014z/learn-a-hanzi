package io.github.brad1014z.hanzi.engine.progress

/**
 * The SRS engine (spec 06): SM-2 with a state machine on top. A pure function of
 * (progress, grade, now) → new progress — the trust core, so every branch is tested.
 *
 * State machine: NEW → LEARNING → REVIEW, with RELEARNING on lapse. LEARNING and
 * RELEARNING use two fixed steps — re-test at the tail of the current session's queue,
 * then 1 day — before (re)graduating to the SM-2 interval schedule.
 *
 * Column encoding (fits the spec 03 schema with no migration — documented in spec 06):
 * - `reps` counts consecutive successes (grade ≥ 3) including the learning steps, so
 *   the sitting index inside LEARNING/RELEARNING is `reps` itself: after success #1
 *   (the intro/lapse quiz) the tail re-test is pending (due now); after success #2
 *   (the tail re-test) the 1-day step is pending; success #3 graduates to REVIEW.
 * - During RELEARNING, `intervalDays` holds the *pending post-graduation interval*
 *   (the lapsed interval halved), applied when the card re-graduates.
 */
object SrsEngine {

    const val MIN_EASE = 1.3
    const val LAPSE_EASE_PENALTY = 0.2
    const val LAPSE_INTERVAL_FACTOR = 0.5
    const val GRADUATION_INTERVAL_DAYS = 1.0
    const val SECOND_INTERVAL_DAYS = 6.0
    const val MAX_INTERVAL_DAYS = 365.0
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Fold one graded card into the schedule. [previous] == null means first exposure. */
    fun apply(previous: CharacterProgress?, character: String, grade: Int, now: Long): CharacterProgress {
        require(grade in 0..5) { "grade must be 0..5, was $grade" }
        val p = previous ?: CharacterProgress.initial(character, now)
        val success = grade >= 3
        val next = when (p.state) {
            SrsState.NEW, SrsState.LEARNING -> learningStep(p, success, isRelearning = false, now)
            SrsState.RELEARNING -> learningStep(p, success, isRelearning = true, now)
            SrsState.REVIEW -> reviewStep(p, grade, success, now)
        }
        return next.copy(lastReviewedAt = now, lastGrade = grade)
    }

    private fun learningStep(
        p: CharacterProgress,
        success: Boolean,
        isRelearning: Boolean,
        now: Long,
    ): CharacterProgress {
        if (!success) {
            // Not a lapse (nothing scheduled was forgotten): back to the session tail.
            return p.copy(state = stateOf(isRelearning), reps = 0, dueAt = now)
        }
        val successes = p.reps + 1
        return when {
            successes >= 3 -> {
                // Graduate (or re-graduate) to the SM-2 schedule.
                val interval = if (isRelearning) {
                    p.intervalDays.coerceAtLeast(GRADUATION_INTERVAL_DAYS) // pending halved interval
                } else {
                    GRADUATION_INTERVAL_DAYS
                }
                p.copy(
                    state = SrsState.REVIEW,
                    reps = successes,
                    intervalDays = interval,
                    dueAt = now + (interval * DAY_MS).toLong(),
                )
            }
            successes == 2 -> p.copy( // tail re-test passed → the 1-day step
                state = stateOf(isRelearning),
                reps = successes,
                dueAt = now + DAY_MS,
            )
            else -> p.copy( // intro/lapse quiz passed → re-test at the session tail
                state = stateOf(isRelearning),
                reps = successes,
                dueAt = now,
            )
        }
    }

    private fun reviewStep(p: CharacterProgress, grade: Int, success: Boolean, now: Long): CharacterProgress {
        if (!success) {
            // Lapse: relearn from the session tail; the halved interval waits in
            // intervalDays until re-graduation (see class docs).
            return p.copy(
                state = SrsState.RELEARNING,
                reps = 0,
                lapses = p.lapses + 1,
                ease = (p.ease - LAPSE_EASE_PENALTY).coerceAtLeast(MIN_EASE),
                intervalDays = (p.intervalDays * LAPSE_INTERVAL_FACTOR)
                    .coerceAtLeast(GRADUATION_INTERVAL_DAYS),
                dueAt = now,
            )
        }
        // SM-2 ease update: EF' = EF + (0.1 − (5−q)(0.08 + (5−q)·0.02)), floored.
        val q = grade
        val ease = (p.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))).coerceAtLeast(MIN_EASE)
        val interval = when {
            p.intervalDays < SECOND_INTERVAL_DAYS -> SECOND_INTERVAL_DAYS
            else -> (p.intervalDays * ease).coerceAtMost(MAX_INTERVAL_DAYS)
        }
        return p.copy(
            reps = p.reps + 1,
            ease = ease,
            intervalDays = interval,
            dueAt = now + (interval * DAY_MS).toLong(),
        )
    }

    private fun stateOf(isRelearning: Boolean) =
        if (isRelearning) SrsState.RELEARNING else SrsState.LEARNING
}
