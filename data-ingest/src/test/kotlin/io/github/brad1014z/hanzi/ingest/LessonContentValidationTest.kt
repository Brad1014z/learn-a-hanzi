package io.github.brad1014z.hanzi.ingest

import io.github.brad1014z.hanzi.engine.data.LessonContent
import io.github.brad1014z.hanzi.engine.data.LessonManifest
import io.github.brad1014z.hanzi.engine.data.PrimaryReading
import io.github.brad1014z.hanzi.engine.data.PronunciationAudio
import io.github.brad1014z.hanzi.engine.data.TeacherReview
import io.github.brad1014z.hanzi.engine.data.UsageExample
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LessonContentValidationTest {
    private fun lesson(
        character: String,
        sequence: Int,
        pinyin: String,
        tone: Int,
        example: UsageExample? = null,
        reviewed: Boolean = true,
        audio: String = "$character.mp3",
    ) = LessonContent(
        character = character,
        primaryReading = PrimaryReading(pinyin, tone),
        learnerGloss = "gloss",
        usageExample = example,
        pronunciationAudio = PronunciationAudio(character, audio),
        curriculumSequence = sequence,
        contentVersion = "v1",
        review = TeacherReview(reviewed, if (reviewed) "Teacher" else "", if (reviewed) "2026-08-23" else null),
    )

    private val valid = listOf(
        lesson("人", 1, "rén", 2),
        lesson("大", 2, "dà", 4, UsageExample("大人", "dà rén", "adult", "dà")),
        lesson("天", 3, "tiān", 1),
    )
    private val readings = mapOf("人" to listOf("rén"), "大" to listOf("dà"), "天" to listOf("tiān"))

    private fun validate(lessons: List<LessonContent>, audio: (String) -> Boolean = { true }) =
        requireValidLessonManifest(
            LessonManifest("v1", lessons),
            dictionaryReadings = readings,
            audioExists = audio,
            requiredCount = 3,
        )

    @Test
    fun `accepts a complete reviewed starter manifest`() = validate(valid)

    @Test
    fun `rejects an example containing a future character`() {
        val broken = valid.toMutableList().apply {
            this[0] = lesson("人", 1, "rén", 2, UsageExample("大人", "dà rén", "adult", "rén"))
        }
        val error = assertFailsWith<LessonContentValidationException> { validate(broken) }
        assertTrue(error.message!!.contains("future characters"))
    }

    @Test
    fun `rejects mismatched target reading and missing target occurrence`() {
        val broken = valid.toMutableList().apply {
            this[1] = lesson("大", 2, "dà", 4, UsageExample("人", "rén", "person", "dǎ"))
        }
        val error = assertFailsWith<LessonContentValidationException> { validate(broken) }
        assertTrue(error.message!!.contains("does not contain its target"))
        assertTrue(error.message!!.contains("does not match the primary reading"))
    }

    @Test
    fun `rejects segmented pinyin that does not use the declared target reading`() {
        val broken = valid.toMutableList().apply {
            this[1] = lesson("大", 2, "dà", 4, UsageExample("大人", "dǎ rén", "adult", "dà"))
        }

        val error = assertFailsWith<LessonContentValidationException> { validate(broken) }

        assertTrue(error.message!!.contains("segmented pinyin does not contain target reading"))
    }

    @Test
    fun `rejects an audio cue that omits the lesson target`() {
        val broken = valid.toMutableList().apply {
            this[1] = valid[1].copy(pronunciationAudio = PronunciationAudio("人", "大.mp3"))
        }

        val error = assertFailsWith<LessonContentValidationException> { validate(broken) }

        assertTrue(error.message!!.contains("audio spoken text does not contain its target"))
    }

    @Test
    fun `rejects missing audio duplicate ordering and unreviewed content`() {
        val broken = listOf(valid[0], valid[1].copy(curriculumSequence = 1), lesson("天", 3, "tiān", 1, reviewed = false))
        val error = assertFailsWith<LessonContentValidationException> {
            validate(broken) { path -> path != "人.mp3" }
        }
        assertTrue(error.message!!.contains("missing audio"))
        assertTrue(error.message!!.contains("duplicate sequences"))
        assertTrue(error.message!!.contains("approval metadata"))
    }

    @Test
    fun `rejects a primary reading absent from the dictionary`() {
        val broken = valid.toMutableList().apply { this[2] = lesson("天", 3, "tián", 2) }
        val error = assertFailsWith<LessonContentValidationException> { validate(broken) }
        assertTrue(error.message!!.contains("not in source readings"))
    }
}
