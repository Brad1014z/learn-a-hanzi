package io.github.brad1014z.hanzi.data

import androidx.room.withTransaction
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.PracticeRecord
import io.github.brad1014z.hanzi.engine.progress.ProgressRepository
import io.github.brad1014z.hanzi.engine.progress.SrsEngine
import io.github.brad1014z.hanzi.engine.progress.SrsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Session tags on ReviewLog rows (spec 04: only guided-track "new" consumes the cap). */
object Sessions {
    const val BROWSE = "practice"
    const val QUEST_WARMUP = "quest-warmup"
    const val QUEST_REVIEW = "quest-review"
    const val QUEST_NEW = "quest-new"
    const val QUEST_RETEST = "quest-retest"
    const val QUEST_BOSS = "quest-boss"
}

/** Room-backed [ProgressRepository] — the :app side of the engine's persistence boundary. */
class RoomProgressRepository(private val db: HanziDatabase) : ProgressRepository {

    private val dao = db.progressDao()

    override fun observeAll(): Flow<Map<String, CharacterProgress>> =
        dao.observeAll().map { rows -> rows.associate { it.character to it.toModel() } }

    override suspend fun recordPractice(record: PracticeRecord) {
        db.withTransaction {
            val previous = dao.get(record.character)?.toModel()
            val updated = SrsEngine.apply(previous, record.character, record.grade, record.reviewedAt)
            val log = record.toEntity(uuid = java.util.UUID.randomUUID().toString())
            val progressEntity = updated.toEntity()
            dao.insertLog(log)
            dao.upsert(progressEntity)
            // Queue for backup (spec 12 outbox); drained by the sync worker when
            // signed in + online, pruned so a forever-signed-out device stays bounded.
            Outbox.enqueueReview(db, log, progressEntity)
        }
    }

    // --- Quest-facing queries (app-side; the engine's quest logic stays pure) ---

    suspend fun due(now: Long): List<CharacterProgress> = dao.due(now).map { it.toModel() }

    suspend fun progressOf(character: String): CharacterProgress? = dao.get(character)?.toModel()

    // Restore-with-merge support (spec 12; used by the sync layer).
    suspend fun allProgress(): Map<String, CharacterProgress> =
        dao.allProgress().associate { it.character to it.toModel() }

    suspend fun upsert(progress: CharacterProgress) = dao.upsert(progress.toEntity())

    suspend fun introducedToday(todayStart: Long): Int =
        dao.introducedSince(todayStart, Sessions.QUEST_NEW)

    suspend fun daysPlayed(): Int = dao.daysPlayed()

    /** Total XP lives in the Meta user keys (spec 10 data notes). */
    suspend fun xpTotal(): Int = db.metaDao().get(XP_KEY)?.toIntOrNull() ?: 0

    suspend fun addXp(delta: Int): Int {
        val next = xpTotal() + delta
        db.metaDao().put(MetaEntity(XP_KEY, next.toString()))
        return next
    }

    /**
     * Fold a finished quest into this week's board tally (ceilinged per session —
     * spec 10/12) and queue the totals for upload. Returns the week's new total.
     */
    suspend fun addBoardXp(sessionXp: Int, now: Long = System.currentTimeMillis()): Int {
        val weekId = io.github.brad1014z.hanzi.engine.social.Weeks.weekId(now)
        val key = "$WEEK_XP_PREFIX$weekId"
        val next = (db.metaDao().get(key)?.toIntOrNull() ?: 0) +
            io.github.brad1014z.hanzi.engine.social.Weeks.boardXpForSession(sessionXp)
        db.metaDao().put(MetaEntity(key, next.toString()))
        Outbox.enqueueXp(db, total = xpTotal(), weekId = weekId, weekXp = next, now = now)
        return next
    }

    suspend fun weekBoardXp(weekId: String): Int =
        db.metaDao().get("$WEEK_XP_PREFIX$weekId")?.toIntOrNull() ?: 0

    /** Destructive manual reset (spec 04) — content tables untouched. */
    suspend fun resetProgress() {
        db.withTransaction {
            dao.clearProgress()
            dao.clearLog()
            db.metaDao().put(MetaEntity(XP_KEY, "0"))
        }
    }

    companion object {
        const val XP_KEY = "xpTotal"
        const val WEEK_XP_PREFIX = "xpWeek:"
    }
}

private fun CharacterProgressEntity.toModel() = CharacterProgress(
    character = character,
    state = SrsState.valueOf(state),
    dueAt = dueAt,
    intervalDays = intervalDays,
    ease = ease,
    reps = reps,
    lapses = lapses,
    lastReviewedAt = lastReviewedAt,
    lastGrade = lastGrade,
)

private fun CharacterProgress.toEntity() = CharacterProgressEntity(
    character = character,
    state = state.name,
    dueAt = dueAt,
    intervalDays = intervalDays,
    ease = ease,
    reps = reps,
    lapses = lapses,
    lastReviewedAt = lastReviewedAt,
    lastGrade = lastGrade,
)

private fun PracticeRecord.toEntity(uuid: String) = ReviewLogEntity(
    character = character,
    reviewedAt = reviewedAt,
    grade = grade,
    drawnCorrectly = drawnCorrectly,
    durationMs = durationMs,
    session = session,
    uuid = uuid,
)
