package io.github.brad1014z.hanzi.engine.progress

import kotlinx.coroutines.flow.Flow

/** One completed practice card, as appended to ReviewLog (spec 03). */
data class PracticeRecord(
    val character: String,
    val reviewedAt: Long,
    val grade: Int,
    val drawnCorrectly: Boolean,
    val durationMs: Long? = null,
    val session: String? = null,
)

/**
 * User-progress persistence boundary (spec 06): the interface lives in the pure
 * engine, the Room implementation lives in :app — the same pattern as SpeechService.
 */
interface ProgressRepository {
    /** All progress rows keyed by character; re-emits on every change. */
    fun observeAll(): Flow<Map<String, CharacterProgress>>

    /**
     * Append [record] to the review log and fold it into the character's progress
     * (via [applyPractice]) atomically.
     */
    suspend fun recordPractice(record: PracticeRecord)
}
