package io.github.brad1014z.hanzi.data

import androidx.room.withTransaction
import io.github.brad1014z.hanzi.engine.play.CardCommitResult
import io.github.brad1014z.hanzi.engine.play.CardCompletion
import io.github.brad1014z.hanzi.engine.play.ChestCommitResult
import io.github.brad1014z.hanzi.engine.play.ChestCompletion
import io.github.brad1014z.hanzi.engine.play.QuestStore
import io.github.brad1014z.hanzi.engine.play.Ranks
import io.github.brad1014z.hanzi.engine.play.TodayProgress
import io.github.brad1014z.hanzi.engine.progress.SrsEngine
import io.github.brad1014z.hanzi.engine.social.Weeks
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/**
 * The Room adapter at the quest persistence seam. A card or chest either lands in full
 * (review/SRS/XP/week/outbox) or not at all, and caller-provided ids make retries no-ops.
 */
class RoomQuestStore internal constructor(
    private val db: HanziDatabase,
    /** Test-only failure point inside the transaction, after every card write. */
    private val afterCardWrites: suspend () -> Unit,
    /** Test-only failure point inside the transaction, after every chest write. */
    private val afterChestWrites: suspend () -> Unit = {},
) : QuestStore {

    constructor(db: HanziDatabase) : this(db, afterCardWrites = {})

    private val progressDao = db.progressDao()
    private val metaDao = db.metaDao()

    override suspend fun loadToday(now: Long): TodayProgress {
        val all = progressDao.allProgress().associate { it.character to it.toModel() }
        return TodayProgress(
            due = progressDao.due(now).map { it.toModel() },
            progress = all,
            introducedToday = progressDao.introducedSince(startOfDay(now), Sessions.QUEST_NEW),
            xpTotal = xpTotal(),
            daysPlayed = progressDao.daysPlayed(),
        )
    }

    override suspend fun commitCard(completion: CardCompletion): CardCommitResult {
        require(completion.attemptId.isNotBlank()) { "attemptId must be stable and non-blank" }
        require(completion.grade in 1..5) { "grade must be 1..5" }
        require(completion.xpDelta >= 0) { "xpDelta cannot be negative" }
        return db.withTransaction {
            val previous = progressDao.get(completion.character)?.toModel()
            val previousRank = Ranks.of(previous)
            if (progressDao.logByUuid(completion.attemptId) != null) {
                return@withTransaction CardCommitResult(
                    applied = false,
                    previousRank = previousRank,
                    currentRank = previousRank,
                    totalXp = xpTotal(),
                )
            }

            val updated = SrsEngine.apply(
                previous = previous,
                character = completion.character,
                grade = completion.grade,
                now = completion.reviewedAt,
            )
            val log = ReviewLogEntity(
                character = completion.character,
                reviewedAt = completion.reviewedAt,
                grade = completion.grade,
                drawnCorrectly = completion.drawnCorrectly,
                durationMs = completion.durationMs,
                session = completion.sessionTag,
                uuid = completion.attemptId,
            )
            check(progressDao.insertLogIdempotent(log) != -1L) {
                "attempt ${completion.attemptId} raced with another commit"
            }
            val progressEntity = updated.toEntity()
            progressDao.upsert(progressEntity)

            val totalXp = addXp(completion.xpDelta)
            val (weekId, weekXp) = addWeeklyXp(completion.xpDelta, completion.reviewedAt)
            Outbox.enqueueReview(db, log, progressEntity)
            Outbox.enqueueXp(
                db = db,
                total = totalXp,
                weekId = weekId,
                weekXp = weekXp,
                now = completion.reviewedAt,
                idempotencyKey = "xp-card-${completion.attemptId}",
            )
            afterCardWrites()

            CardCommitResult(
                applied = true,
                previousRank = previousRank,
                currentRank = Ranks.of(updated),
                totalXp = totalXp,
            )
        }
    }

    override suspend fun openChest(completion: ChestCompletion): ChestCommitResult {
        require(completion.completionId.isNotBlank()) { "completionId must be stable and non-blank" }
        require(completion.xpDelta >= 0) { "xpDelta cannot be negative" }
        return db.withTransaction {
            val markerKey = "$CHEST_PREFIX${completion.completionId}"
            if (metaDao.get(markerKey) != null) {
                return@withTransaction ChestCommitResult(applied = false, totalXp = xpTotal())
            }
            val totalXp = addXp(completion.xpDelta)
            val (weekId, weekXp) = addWeeklyXp(completion.xpDelta, completion.completedAt)
            metaDao.put(MetaEntity(markerKey, completion.completedAt.toString()))
            val localDate = Instant.ofEpochMilli(completion.completedAt)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            metaDao.put(MetaEntity("$DAILY_COMPLETE_PREFIX$localDate", completion.completedAt.toString()))
            Outbox.enqueueXp(
                db = db,
                total = totalXp,
                weekId = weekId,
                weekXp = weekXp,
                now = completion.completedAt,
                idempotencyKey = "xp-chest-${completion.completionId}",
            )
            afterChestWrites()
            ChestCommitResult(applied = true, totalXp = totalXp)
        }
    }

    override suspend fun resetAllProgress() {
        db.withTransaction {
            progressDao.clearProgress()
            progressDao.clearLog()
            db.outboxDao().clearAll()
            metaDao.clearProgressMetadata()
            metaDao.put(MetaEntity(RoomProgressRepository.XP_KEY, "0"))
            // A reset is a local deletion barrier. Frozen sync must not merge an old
            // cloud snapshot back onto the device if social is enabled in the future.
            metaDao.put(MetaEntity(RESTORE_BLOCKED_KEY, "true"))
        }
    }

    private suspend fun xpTotal(): Int =
        metaDao.get(RoomProgressRepository.XP_KEY)?.toIntOrNull() ?: 0

    private suspend fun addXp(delta: Int): Int {
        val next = xpTotal() + delta
        metaDao.put(MetaEntity(RoomProgressRepository.XP_KEY, next.toString()))
        return next
    }

    private suspend fun addWeeklyXp(delta: Int, now: Long): Pair<String, Int> {
        val weekId = Weeks.weekId(now)
        val key = "${RoomProgressRepository.WEEK_XP_PREFIX}$weekId"
        val next = (metaDao.get(key)?.toIntOrNull() ?: 0) + delta
        metaDao.put(MetaEntity(key, next.toString()))
        return weekId to next
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val RESTORE_BLOCKED_KEY = "restore:blockedAfterReset"
        private const val CHEST_PREFIX = "quest:chest:"
        private const val DAILY_COMPLETE_PREFIX = "quest:completed:"
    }
}
