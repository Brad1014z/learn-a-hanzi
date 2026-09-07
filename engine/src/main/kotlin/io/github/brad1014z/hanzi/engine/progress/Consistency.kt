package io.github.brad1014z.hanzi.engine.progress

/**
 * How consistently the learner has been showing up (spec 10).
 *
 * Deliberately *not* a classic streak. The constitution bans guilt mechanics and streak
 * shaming outright, so this type has no concept of a streak being "lost": it reports the
 * run you are currently on, and if that run is 1 because you were away, that is framed as
 * a comeback rather than a failure. Nothing here can ever decrease [daysPlayed], and
 * nothing here gates content.
 *
 * A "day played" is a local calendar day with at least one graded write — the same
 * definition the `daysPlayed` query has always used.
 */
data class Consistency(
    /** Lifetime count of days played. Only ever goes up. */
    val daysPlayed: Int,
    /**
     * Consecutive days played, up to and including today. A run does **not** end merely
     * because today has no practice yet — today isn't over. It ends once a whole
     * unpractised day has passed.
     */
    val currentRun: Int,
    val playedToday: Boolean,
    /** Unpractised days between the previous day played and today. */
    val missedBeforeToday: Int,
) {
    /**
     * True on the day the learner returns after being away. The UI greets this instead of
     * mentioning what was broken.
     */
    val isComeback: Boolean
        get() = playedToday && missedBeforeToday >= COMEBACK_MISSED_DAYS

    companion object {
        /** Days away before a return counts as a comeback rather than just a normal day. */
        const val COMEBACK_MISSED_DAYS = 2

        val None = Consistency(daysPlayed = 0, currentRun = 0, playedToday = false, missedBeforeToday = 0)
    }
}

/**
 * Derive [Consistency] from the days the learner practised.
 *
 * Days are epoch days (days since 1970-01-01) so this stays pure Kotlin with no date
 * library — the caller owns the timezone question by deciding what "today" is locally.
 */
fun consistencyOf(playedDays: Collection<Long>, today: Long): Consistency {
    val days = playedDays.toHashSet()
    if (days.isEmpty()) return Consistency.None

    val playedToday = today in days
    // Anchor the run at today if practised, otherwise at yesterday — an unpractised
    // today must not retroactively erase a run that is still alive.
    val anchor = when {
        playedToday -> today
        (today - 1) in days -> today - 1
        else -> null
    }
    var run = 0
    if (anchor != null) {
        var day = anchor
        while (day in days) {
            run++
            day--
        }
    }

    val previousDayPlayed = days.filter { it < today }.maxOrNull()
    val missedBeforeToday = if (playedToday && previousDayPlayed != null) {
        (today - previousDayPlayed - 1).toInt().coerceAtLeast(0)
    } else {
        0
    }

    return Consistency(
        daysPlayed = days.size,
        currentRun = run,
        playedToday = playedToday,
        missedBeforeToday = missedBeforeToday,
    )
}
