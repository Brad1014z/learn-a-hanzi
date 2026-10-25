package io.github.brad1014z.hanzi.engine.data

/**
 * Release gate for the signed first-30 manifest (docs/milestones/m4.1-release-gates.md).
 *
 * This lives in `:engine` — not `:data-ingest` — because it is part of the domain: the
 * same rules must be enforceable by the ingest pipeline, by CI against the checked-in
 * asset, and by the signing gate. All errors are reported together so a teacher/content
 * edit gets one useful review cycle rather than one failure at a time.
 *
 * [requireApproval] exists for the teacher-review draft generator: a draft is *supposed*
 * to have `approved = false`, but every other rule (readings, tones, audio, example
 * vocabulary) should still be checked while it is authored. A release build always calls
 * this with the default `true`.
 */
fun requireValidLessonManifest(
    manifest: LessonManifest,
    dictionaryReadings: Map<String, List<String>>,
    audioExists: (String) -> Boolean,
    requiredCount: Int = 30,
    requiredPrefix: List<String> = listOf("人", "大", "天"),
    requireApproval: Boolean = true,
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
        } else if (lesson.character !in lesson.pronunciationAudio.spokenText) {
            failures += "$prefix audio spoken text does not contain its target"
        }
        if (
            requireApproval &&
            (!lesson.review.approved || lesson.review.reviewer.isBlank() || lesson.review.reviewedAt.isNullOrBlank())
        ) {
            failures += "$prefix lacks complete Chinese-teacher approval metadata"
        }
        lesson.usageExample?.let { example ->
            if (lesson.character !in example.text) failures += "$prefix example does not contain its target"
            if (example.targetReading.lowercase() != lesson.primaryReading.pinyin.lowercase()) {
                failures += "$prefix example target reading does not match the primary reading"
            }
            if (example.targetReading.lowercase() !in pinyinSyllables(example.segmentedPinyin)) {
                failures += "$prefix example segmented pinyin does not contain target reading"
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

class LessonContentValidationException(val failures: List<String>) :
    IllegalArgumentException(failures.joinToString(prefix = "Lesson content rejected:\n- ", separator = "\n- "))

private fun isHanzi(ch: Char): Boolean = ch in '一'..'鿿'

private fun toneOf(pinyin: String): Int {
    val marks = listOf("āēīōūǖ", "áéíóúǘ", "ǎěǐǒǔǚ", "àèìòùǜ")
    val found = marks.indexOfFirst { set -> pinyin.any { it in set } }
    return if (found < 0) 5 else found + 1
}

private fun pinyinSyllables(text: String): List<String> = text.lowercase()
    .split(Regex("[^a-züāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+"))
    .filter(String::isNotBlank)
