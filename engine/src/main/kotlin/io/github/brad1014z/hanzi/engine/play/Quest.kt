package io.github.brad1014z.hanzi.engine.play

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress

/**
 * The daily quest (spec 04/10): warm-up → reviews → new characters → boss → chest.
 * Pure logic — the app supplies the due queue and new candidates, submits grades to
 * the SRS as cards complete, and renders whatever step this machine says is next.
 */
sealed interface QuestStep {
    val character: String

    /** One easy due card first — a guaranteed early win (highest interval = best-known). */
    data class WarmUp(override val character: String) : QuestStep

    data class Review(override val character: String) : QuestStep

    data class NewChar(override val character: String) : QuestStep

    /** Same-session re-test at the queue tail (SRS learning step 1 / failed cards). */
    data class ReTest(override val character: String) : QuestStep

    /** Written fully from memory — no guide (spec 04). Failing costs only the re-queue. */
    data class Boss(override val character: String) : QuestStep
}

data class QuestPlan(val steps: List<QuestStep>, val backlogWarning: Boolean) {
    val isEmpty: Boolean get() = steps.isEmpty()
}

object QuestBuilder {
    const val BACKLOG_THRESHOLD = 100

    /**
     * [due]: cards with dueAt ≤ now. [newCandidates]: unlocked-world characters with no
     * progress row, in teaching order. [remainingNewCap]: today's headroom (daily cap
     * minus already-introduced-today, spec 04).
     */
    fun build(
        due: List<CharacterProgress>,
        newCandidates: List<String>,
        remainingNewCap: Int,
        backlogThreshold: Int = BACKLOG_THRESHOLD,
    ): QuestPlan {
        val backlog = due.size > backlogThreshold
        val warmUp = due.maxByOrNull { it.intervalDays }
        val reviews = due.filter { it !== warmUp }.sortedBy { it.dueAt }
        // Backlogged? Suggest clearing reviews before new material (nudge, not a block —
        // the caller may override by passing the cap anyway; spec 04).
        val newChars = if (backlog) emptyList() else newCandidates.take(remainingNewCap.coerceAtLeast(0))
        val steps = buildList {
            warmUp?.let { add(QuestStep.WarmUp(it.character)) }
            reviews.forEach { add(QuestStep.Review(it.character)) }
            newChars.forEach { add(QuestStep.NewChar(it)) }
            // Boss: today's material, freshest first (spec 04: "picked from today's material").
            val boss = newChars.lastOrNull() ?: reviews.lastOrNull()?.character ?: warmUp?.character
            boss?.let { add(QuestStep.Boss(it)) }
        }
        return QuestPlan(steps, backlog)
    }
}

/**
 * The running quest: immutable state machine. [advance] folds one completed card in,
 * pays XP (every step pays — effort economy, spec 10), and schedules same-session
 * re-tests at the tail (grade < 3, or a new character's SRS learning step 1).
 */
data class QuestSession(
    val steps: List<QuestStep>,
    val index: Int = 0,
    val xpEarned: Int = 0,
    val reTestCounts: Map<String, Int> = emptyMap(),
    val chestOpened: Boolean = false,
) {
    val current: QuestStep? get() = steps.getOrNull(index)
    val cardsDone: Int get() = index.coerceAtMost(steps.size)
    val cardsTotal: Int get() = steps.size

    /** All cards written; the chest is the final beat (spec 10: opens regardless of boss). */
    val readyForChest: Boolean get() = index >= steps.size && !chestOpened

    companion object {
        const val MAX_RETESTS_PER_CHAR = 2

        fun start(plan: QuestPlan) = QuestSession(steps = plan.steps)
    }

    fun advance(grade: Int): QuestSession {
        val step = current ?: return this
        val xp = when (step) {
            is QuestStep.WarmUp -> XpConfig.WARM_UP
            is QuestStep.Review, is QuestStep.ReTest -> XpConfig.REVIEW_CLEARED
            is QuestStep.NewChar -> XpConfig.NEW_CHARACTER
            is QuestStep.Boss -> XpConfig.BOSS_ATTEMPTED // attempted, not necessarily aced
        }
        val needsReTest = when (step) {
            is QuestStep.Boss -> false // the session ends; a failed boss just stays due
            is QuestStep.NewChar -> true // SRS learning step 1: re-test at the tail
            else -> grade < 3
        }
        val count = reTestCounts.getOrDefault(step.character, 0)
        val newSteps = if (needsReTest && count < MAX_RETESTS_PER_CHAR) {
            // Insert before the boss so the boss stays the closing beat.
            val bossAt = steps.drop(index + 1).indexOfFirst { it is QuestStep.Boss }
            val insertAt = if (bossAt >= 0) index + 1 + bossAt else steps.size
            steps.toMutableList().apply { add(insertAt, QuestStep.ReTest(step.character)) }
        } else {
            steps
        }
        return copy(
            steps = newSteps,
            index = index + 1,
            xpEarned = xpEarned + xp,
            reTestCounts = if (needsReTest) reTestCounts + (step.character to count + 1) else reTestCounts,
        )
    }

    fun openChest(): QuestSession {
        check(readyForChest) { "chest opens once, after the last card" }
        return copy(chestOpened = true, xpEarned = xpEarned + XpConfig.CHEST_OPENED)
    }
}
