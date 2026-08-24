package io.github.brad1014z.hanzi.ingest

import io.github.brad1014z.hanzi.engine.data.LessonManifest

class LessonContentValidationException(val failures: List<String>) :
    IllegalArgumentException(failures.joinToString(prefix = "Lesson content rejected:\n- ", separator = "\n- "))

/**
 * Release gate for the signed first-30 manifest. All errors are reported together so a
 * teacher/content edit gets one useful review cycle rather than one failure at a time.
 */
fun requireValidLessonManifest(
    manifest: LessonManifest,
    dictionaryReadings: Map<String, List<String>>,
    audioExists: (String) -> Boolean,
    requiredCount: Int = 30,
    requiredPrefix: List<String> = listOf("人", "大", "天"),
) {
    val failures = mutableListOf<String>()
    val ordered = manifest.lessons.sortedBy { it.curriculumSequence }
    val characters = ordered.map { it.character }
    val sequences = ordered.map { it.curriculumSequence }

    if (manifest.contentVersion.isBlank()) failures += "contentVersion is blank"
    if (ordered.size != requiredCount) failures += "expected $requiredCount lessons, found ${ordered.size}"
    if (characters.take(requiredPrefix.size) != requiredPrefix) {
        failures += "starter order must be ${requiredPrefix.joinToString(" → ")}"
    }
    val duplicateCharacters = characters.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (duplicateCharacters.isNotEmpty()) failures += "duplicate characters: $duplicateCharacters"
    val duplicateSequences = sequences.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (duplicateSequences.isNotEmpty()) failures += "duplicate sequences: $duplicateSequences"
    if (sequences != (1..ordered.size).toList()) failures += "sequences must be unique and contiguous from 1"

    val learned = mutableSetOf<String>()
    for (lesson in ordered) {
        val prefix = "${lesson.character}#${lesson.curriculumSequence}"
        if (lesson.contentVersion != manifest.contentVersion) {
            failures += "$prefix content version does not match manifest ${manifest.contentVersion}"
        }
        val sourceReadings = dictionaryReadings[lesson.character].orEmpty()
        if (sourceReadings.none { it.lowercase() == lesson.primaryReading.pinyin.lowercase() }) {
            failures += "$prefix primary reading ${lesson.primaryReading.pinyin} is not in source readings $sourceReadings"
        }
        val actualTone = toneOf(lesson.primaryReading.pinyin)
        if (actualTone != lesson.primaryReading.tone) {
            failures += "$prefix tone ${lesson.primaryReading.tone} does not match ${lesson.primaryReading.pinyin} (tone $actualTone)"
        }
        if (!audioExists(lesson.pronunciationAudio.assetPath)) {
            failures += "$prefix missing audio ${lesson.pronunciationAudio.assetPath}"
        }
        if (lesson.pronunciationAudio.spokenText.isBlank()) {
            failures += "$prefix audio spoken text is blank"
        }
        if (!lesson.review.approved || lesson.review.reviewer.isBlank() || lesson.review.reviewedAt.isNullOrBlank()) {
            failures += "$prefix lacks complete Chinese-teacher approval metadata"
        }
        lesson.usageExample?.let { example ->
            if (lesson.character !in example.text) failures += "$prefix example does not contain its target"
            if (example.targetReading.lowercase() != lesson.primaryReading.pinyin.lowercase()) {
                failures += "$prefix example target reading does not match the primary reading"
            }
            if (example.segmentedPinyin.isBlank() || example.translation.isBlank()) {
                failures += "$prefix example pinyin/translation is incomplete"
            }
            val allowed = learned + lesson.character
            val future = example.text.filter(::isHanzi).map(Char::toString).filterNot { it in allowed }.distinct()
            if (future.isNotEmpty()) failures += "$prefix example introduces future characters $future"
        }
        learned += lesson.character
    }

    if (failures.isNotEmpty()) throw LessonContentValidationException(failures)
}

private fun toneOf(pinyin: String): Int {
    val marks = listOf("āēīōūǖ", "áéíóúǘ", "ǎěǐǒǔǚ", "àèìòùǜ")
    val found = marks.indexOfFirst { set -> pinyin.any { it in set } }
    return if (found < 0) 5 else found + 1
}
