package io.github.brad1014z.hanzi.data

import android.content.Context
import io.github.brad1014z.hanzi.BuildConfig
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.data.ExampleSentence
import io.github.brad1014z.hanzi.engine.data.LessonContentRepository
import io.github.brad1014z.hanzi.engine.data.LessonContent
import io.github.brad1014z.hanzi.engine.data.LessonManifest
import io.github.brad1014z.hanzi.engine.data.PrimaryReading
import io.github.brad1014z.hanzi.engine.data.PronunciationAudio
import io.github.brad1014z.hanzi.engine.data.TeacherReview
import io.github.brad1014z.hanzi.engine.data.Phrase
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import kotlinx.serialization.json.Json

/**
 * Content queries over the bundled dataset (M2). Replaces the Phase 0 resource-based
 * engine CharacterRepository in the app (that one lives on for engine tests). Geometry
 * in the DB is already normalized (ingest applies the Y-flip — spec 02), so no
 * transform happens here.
 */
class RoomContentRepository(
    context: Context,
    private val db: HanziDatabase,
) : LessonContentRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val signedLessons: Map<String, LessonContent> = context.assets
        .open("content/lesson-content-first30.json")
        .bufferedReader()
        .use { json.decodeFromString<LessonManifest>(it.readText()) }
        .lessons
        .filter { it.review.approved }
        .associateBy { it.character }
    private val cache = mutableMapOf<String, CharacterData>()
    private var curriculumSequence: Map<String, Int>? = null

    data class World(val id: String, val name: String, val characters: List<CurriculumRow>)

    /** Worlds in teaching order (sequence is world-major, computed at ingest — spec 04). */
    suspend fun worlds(): List<World> =
        db.contentDao().curriculum()
            .also { rows -> curriculumSequence = rows.associate { it.character to it.sequence } }
            .groupBy { it.world to it.worldName } // groupBy preserves encounter order
            .map { (key, rows) -> World(key.first, key.second, rows) }

    override suspend fun load(character: String): CharacterData {
        cache[character]?.let { return it }
        val dao = db.contentDao()
        val c = checkNotNull(dao.character(character)) { "Character $character not in dataset" }
        val strokes = dao.strokes(character)
        check(strokes.isNotEmpty()) { "$character has no stroke data (ingest should have failed)" }
        val data = CharacterData(
            character = character,
            strokeOutlines = strokes.map { SvgPathParser.parse(it.pathData) },
            medians = strokes.map { row ->
                json.decodeFromString<List<List<Double>>>(row.median).map { (x, y) -> Point(x, y) }
            },
            pinyin = json.decodeFromString<List<String>>(c.pinyin),
            definition = c.definition,
            phrases = dao.words(character).map { Phrase(it.simplified, it.pinyin, it.english) },
            sentence = dao.sentence(character)?.let {
                ExampleSentence(it.text, it.pinyin, it.english, it.source)
            },
            lessonContent = lessonContentFor(
                character = character,
                readings = json.decodeFromString(c.pinyin),
                legacyDefinition = c.definition,
                sequence = curriculumSequence?.get(character)
                    ?: dao.curriculum().first { it.character == character }.sequence,
            ),
        )
        cache[character] = data
        return data
    }

    private fun lessonContentFor(
        character: String,
        readings: List<String>,
        legacyDefinition: String,
        sequence: Int,
    ): LessonContent {
        signedLessons[character]?.let { return it }
        check(BuildConfig.DEBUG) {
            "$character has no signed LessonContent; release content is intentionally blocked"
        }
        val starter = STARTER_CONTENT[character]
        val primary = starter?.first ?: readings.firstOrNull().orEmpty()
        val gloss = starter?.second ?: legacyDefinition.substringBefore(";").substringBefore(",").trim()
        return LessonContent(
            character = character,
            primaryReading = PrimaryReading(primary, toneOf(primary)),
            learnerGloss = gloss,
            pronunciationAudio = PronunciationAudio(
                spokenText = character,
                assetPath = "audio/zh-Hans/${sha1(character)}.mp3",
            ),
            secondaryReadings = readings.filterNot { it == primary },
            curriculumSequence = if (starter != null) STARTER_ORDER.getValue(character) else sequence + 3,
            contentVersion = "m4.1-engineering-draft",
            review = TeacherReview(
                approved = false,
                reviewer = "",
                reviewedAt = null,
            ),
        )
    }

    private fun toneOf(pinyin: String): Int {
        val marks = listOf("āēīōūǖ", "áéíóúǘ", "ǎěǐǒǔǚ", "àèìòùǜ")
        return marks.indexOfFirst { set -> pinyin.any { it in set } }.let { if (it < 0) 5 else it + 1 }
    }

    private fun sha1(text: String): String =
        java.security.MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private val STARTER_CONTENT = mapOf(
            "人" to ("rén" to "person"),
            "大" to ("dà" to "big"),
            "天" to ("tiān" to "sky; day"),
        )
        private val STARTER_ORDER = mapOf("人" to 1, "大" to 2, "天" to 3)
    }
}
