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
            dao.insertLog(record.toEntity())
            dao.upsert(updated.toEntity())
        }
    }

    // --- Quest-facing queries (app-side; the engine's quest logic stays pure) ---

    suspend fun due(now: Long): List<CharacterProgress> = dao.due(now).map { it.toModel() }

    suspend fun progressOf(character: String): CharacterProgress? = dao.get(character)?.toModel()

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

private fun PracticeRecord.toEntity() = ReviewLogEntity(
    character = character,
    reviewedAt = reviewedAt,
    grade = grade,
    drawnCorrectly = drawnCorrectly,
    durationMs = durationMs,
    session = session,
)
