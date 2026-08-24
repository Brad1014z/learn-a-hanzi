package io.github.brad1014z.hanzi.engine.play

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress

/** Everything needed to plan today without exposing persistence details. */
data class TodayProgress(
    val due: List<CharacterProgress>,
    val progress: Map<String, CharacterProgress>,
    val introducedToday: Int,
    val xpTotal: Int,
    val daysPlayed: Int,
)

/**
 * A stable client-generated [attemptId] makes retrying a card safe. The XP delta belongs
 * to the same commit as the review and SRS transition; callers must never award it again.
 */
data class CardCompletion(
    val attemptId: String,
    val character: String,
    val grade: Int,
    val drawnCorrectly: Boolean,
    val durationMs: Long?,
    val sessionTag: String,
    val reviewedAt: Long,
    val xpDelta: Int,
)

data class CardCommitResult(
    val applied: Boolean,
    val previousRank: RankState,
    val currentRank: RankState,
    val totalXp: Int,
)

data class ChestCompletion(
    val completionId: String,
    val completedAt: Long,
    val xpDelta: Int,
)

data class ChestCommitResult(val applied: Boolean, val totalXp: Int)

/**
 * The persistence seam for the learning loop. Every mutating operation is transactional
 * and idempotent; a normal retry is part of the interface, not an exceptional workaround.
 */
interface QuestStore {
    suspend fun loadToday(now: Long): TodayProgress

    suspend fun commitCard(completion: CardCompletion): CardCommitResult

    suspend fun openChest(completion: ChestCompletion): ChestCommitResult

    suspend fun resetAllProgress()
}
