package io.github.brad1014z.hanzi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sync outbox payloads (spec 12 / PR #1 design): every local write queues an upload,
 * drained by the sync worker when signed in + online. Client-generated UUIDs make
 * retries idempotent. JSON payloads keep the table schema stable across kinds.
 */
object Outbox {
    const val KIND_PROGRESS = "progress"
    const val KIND_REVIEW_LOG = "reviewLog"
    const val KIND_XP = "xp"
    private const val MAX_QUEUED = 2000 // forever-signed-out devices stay bounded

    val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ProgressPayload(
        val character: String, val state: String, val dueAt: Long, val intervalDays: Double,
        val ease: Double, val reps: Int, val lapses: Int,
        val lastReviewedAt: Long?, val lastGrade: Int?,
    )

    @Serializable
    data class ReviewLogPayload(
        val uuid: String, val character: String, val reviewedAt: Long, val grade: Int,
        val drawnCorrectly: Boolean, val durationMs: Long?, val session: String?,
    )

    @Serializable
    data class XpPayload(val total: Int, val weekId: String, val weekXp: Int)

    suspend fun enqueueReview(db: HanziDatabase, log: ReviewLogEntity, progress: CharacterProgressEntity) {
        val outbox = db.outboxDao()
        outbox.enqueue(
            SyncOutboxEntity(
                uuid = log.uuid, // reuse the log's uuid: retries stay idempotent
                kind = KIND_REVIEW_LOG,
                payload = json.encodeToString(
                    ReviewLogPayload(
                        log.uuid, log.character, log.reviewedAt, log.grade,
                        log.drawnCorrectly, log.durationMs, log.session,
                    ),
                ),
                createdAt = log.reviewedAt,
            ),
        )
        outbox.enqueue(
            SyncOutboxEntity(
                uuid = "progress-${progress.character}-${log.reviewedAt}",
                kind = KIND_PROGRESS,
                payload = json.encodeToString(
                    ProgressPayload(
                        progress.character, progress.state, progress.dueAt, progress.intervalDays,
                        progress.ease, progress.reps, progress.lapses,
                        progress.lastReviewedAt, progress.lastGrade,
                    ),
                ),
                createdAt = log.reviewedAt,
            ),
        )
        prune(db)
    }

    suspend fun enqueueXp(db: HanziDatabase, total: Int, weekId: String, weekXp: Int, now: Long) {
        db.outboxDao().enqueue(
            SyncOutboxEntity(
                uuid = "xp-$weekId-$now",
                kind = KIND_XP,
                payload = json.encodeToString(XpPayload(total, weekId, weekXp)),
                createdAt = now,
            ),
        )
        prune(db)
    }

    private suspend fun prune(db: HanziDatabase) {
        val outbox = db.outboxDao()
        val excess = outbox.count() - MAX_QUEUED
        if (excess > 0) {
            outbox.delete(outbox.oldest(excess).map { it.uuid })
        }
    }
}
