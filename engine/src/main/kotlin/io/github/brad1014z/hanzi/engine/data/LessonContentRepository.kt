package io.github.brad1014z.hanzi.engine.data

/**
 * Content seam used by the learning loop. UI and controller tests do not know whether
 * the lesson came from Room, a signed manifest, or an in-memory test adapter.
 */
interface LessonContentRepository {
    suspend fun load(character: String): CharacterData
}
