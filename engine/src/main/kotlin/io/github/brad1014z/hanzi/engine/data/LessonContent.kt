package io.github.brad1014z.hanzi.engine.data

import kotlinx.serialization.Serializable

@Serializable
data class PrimaryReading(val pinyin: String, val tone: Int) {
    init {
        require(pinyin.isNotBlank()) { "primary pinyin cannot be blank" }
        require(tone in 1..5) { "tone must be 1..5" }
    }
}

@Serializable
data class UsageExample(
    val text: String,
    val segmentedPinyin: String,
    val translation: String,
    val targetReading: String,
)

@Serializable
data class PronunciationAudio(val spokenText: String, val assetPath: String)

@Serializable
data class TeacherReview(
    val approved: Boolean,
    val reviewer: String,
    val reviewedAt: String? = null,
)

/** The only learner-facing reading/gloss/example bundle. */
@Serializable
data class LessonContent(
    val character: String,
    val primaryReading: PrimaryReading,
    val learnerGloss: String,
    val usageExample: UsageExample? = null,
    val pronunciationAudio: PronunciationAudio,
    val secondaryReadings: List<String> = emptyList(),
    val curriculumSequence: Int,
    val contentVersion: String,
    val review: TeacherReview,
) {
    init {
        require(character.codePointCount(0, character.length) == 1) { "lesson target must be one character" }
        require(learnerGloss.isNotBlank()) { "learner gloss cannot be blank" }
        require(curriculumSequence > 0) { "curriculum sequence must be positive" }
        require(contentVersion.isNotBlank()) { "content version cannot be blank" }
    }
}

@Serializable
data class LessonManifest(
    val contentVersion: String,
    val lessons: List<LessonContent>,
)
