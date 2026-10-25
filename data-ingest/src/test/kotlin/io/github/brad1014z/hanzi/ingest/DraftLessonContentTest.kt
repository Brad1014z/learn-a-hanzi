package io.github.brad1014z.hanzi.ingest

import io.github.brad1014z.hanzi.engine.data.LessonContentValidationException
import io.github.brad1014z.hanzi.engine.data.LessonManifest
import io.github.brad1014z.hanzi.engine.data.requireValidLessonManifest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the teacher-review draft to the same structural rules as the release gate. The
 * draft is allowed to lack approvals (that is the point), but it must be otherwise valid,
 * or the teacher would spend a review cycle on mechanical errors.
 */
class DraftLessonContentTest {

    private val root = File(".").canonicalFile
    private val assets = File(root, "app/src/main/assets")
    private val source = loadManifestSource(File(assets, "databases/hanzi_v1.sqlite"))

    @Test
    fun `draft has 30 ordered lessons starting with the starter trio`() {
        val draft = buildDraft(source, assets)
        assertEquals(30, draft.size)
        assertEquals(listOf("人", "大", "天"), draft.take(3).map { it.lesson.character })
        assertEquals((1..30).toList(), draft.map { it.lesson.curriculumSequence })
        assertEquals(30, draft.map { it.lesson.character }.distinct().size)
    }

    @Test
    fun `draft is structurally valid but never carries approval metadata`() {
        val draft = buildDraft(source, assets)
        for (entry in draft) {
            assertFalse(entry.lesson.review.approved, "${entry.lesson.character} must not be pre-approved")
            assertTrue(entry.lesson.review.reviewer.isBlank())
            assertNull(entry.lesson.review.reviewedAt)
            assertTrue(entry.audioFile?.isFile == true, "missing audio for ${entry.lesson.character}")
        }

        // Every rule except approval passes.
        requireValidLessonManifest(
            manifest = LessonManifest(DRAFT_CONTENT_VERSION, draft.map { it.lesson }),
            dictionaryReadings = source.readings,
            audioExists = { File(assets, it).isFile },
            requiredCount = 30,
            requireApproval = false,
        )

        // And the release gate correctly refuses the same draft until a teacher signs it.
        assertFailsWith<LessonContentValidationException> {
            requireValidLessonManifest(
                manifest = LessonManifest(DRAFT_CONTENT_VERSION, draft.map { it.lesson }),
                dictionaryReadings = source.readings,
                audioExists = { File(assets, it).isFile },
                requiredCount = 30,
                requireApproval = true,
            )
        }
    }

    @Test
    fun `starter trio gets a suggested example`() {
        val draft = buildDraft(source, assets).take(3)
        assertEquals(
            listOf("人人", "大人", "天天"),
            draft.map { it.lesson.usageExample?.text },
        )
    }

    @Test
    fun `every included example only uses characters learned by that lesson`() {
        val draft = buildDraft(source, assets)
        val learned = mutableSetOf<String>()
        for (entry in draft) {
            learned += entry.lesson.character
            entry.lesson.usageExample?.let { example ->
                val future = example.text
                    .filter { it in '一'..'鿿' }
                    .filterNot { it.toString() in learned }
                assertTrue(
                    future.isEmpty(),
                    "${entry.lesson.character} example uses future characters $future: ${example.text}",
                )
            }
        }
    }

    @Test
    fun `the checked-in manifest is either explicitly pending or fully valid`() {
        val manifestFile = File(assets, "content/lesson-content-first30.json")
        val manifest = json.decodeFromString<LessonManifest>(manifestFile.readText())
        if (manifest.lessons.isEmpty()) {
            assertTrue(
                manifest.contentVersion.contains("pending"),
                "an empty manifest must say it is pending, not look like approved content",
            )
            return
        }
        requireValidLessonManifest(
            manifest = manifest,
            dictionaryReadings = source.readings,
            audioExists = { File(assets, it).isFile },
            requiredCount = 30,
            requireApproval = true,
        )
    }
}
