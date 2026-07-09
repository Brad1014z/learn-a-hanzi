package io.github.brad1014z.hanzi.data

import androidx.room.withTransaction
import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.PracticeRecord
import io.github.brad1014z.hanzi.engine.progress.ProgressRepository
import io.github.brad1014z.hanzi.engine.progress.SrsState
import io.github.brad1014z.hanzi.engine.progress.applyPractice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [ProgressRepository] — the :app side of the engine's persistence boundary. */
class RoomProgressRepository(private val db: HanziDatabase) : ProgressRepository {

    private val dao = db.progressDao()

    override fun observeAll(): Flow<Map<String, CharacterProgress>> =
        dao.observeAll().map { rows -> rows.associate { it.character to it.toModel() } }

    override suspend fun recordPractice(record: PracticeRecord) {
        db.withTransaction {
            val previous = dao.get(record.character)?.toModel()
            val updated = applyPractice(previous, record.character, record.grade, record.reviewedAt)
            dao.insertLog(record.toEntity())
            dao.upsert(updated.toEntity())
        }
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
