package io.github.brad1014z.hanzi.engine.data

import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.geometry.Polyline
import io.github.brad1014z.hanzi.engine.svg.SvgCommand
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import io.github.brad1014z.hanzi.engine.svg.mapPoints
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Coordinate normalization (specs 02/05): make-me-a-hanzi / hanzi-writer geometry lives
 * in a ~1024-unit font em-square with the Y axis pointing UP and the glyph box offset by
 * the baseline (y from -124 to 900). Rendering it raw draws characters upside down.
 * We store everything in a 1000×1000, Y-down (screen convention) box:
 *
 *   x' = x · (1000/1024)        y' = (900 − y) · (1000/1024)
 */
object HanziCoordinates {
    const val SOURCE_SIZE = 1024.0
    const val BASELINE_OFFSET = 900.0
    const val TARGET_SIZE = 1000.0
    private const val SCALE = TARGET_SIZE / SOURCE_SIZE

    fun normalize(p: Point) = Point(p.x * SCALE, (BASELINE_OFFSET - p.y) * SCALE)
}

/** A multi-char word/idiom containing the character, for reading practice (spec 07). */
@Serializable
data class Phrase(
    val phrase: String,   // e.g. "小心翼翼"
    val pinyin: String,   // tone-marked, e.g. "xiǎo xīn yì yì"
    val english: String,  // gloss, e.g. "(idiom) cautious; careful"
)

/** One teachable character: per-stroke outline commands (rendering) + medians (grading). */
data class CharacterData(
    val character: String,
    /** One entry per stroke, in writing order: the filled-outline path, normalized. */
    val strokeOutlines: List<List<SvgCommand>>,
    /** One entry per stroke, parallel to [strokeOutlines]: the centerline, normalized. */
    val medians: List<Polyline>,
    /** Tone-marked readings, e.g. ["huǒ"] (from make-me-a-hanzi dictionary.txt). */
    val pinyin: List<String> = emptyList(),
    /** English gloss, often ;-separated clauses; UI may shorten (spec 02). */
    val definition: String = "",
    /** 2-3 common words/idioms containing the character (from CC-CEDICT — spec 02). */
    val phrases: List<Phrase> = emptyList(),
) {
    /** First clause of the gloss — fits small UI (e.g. "fire" from "fire, flame; to burn"). */
    val shortDefinition: String
        get() = definition.substringBefore(";").trim()

    val strokeCount: Int get() = medians.size

    init {
        require(strokeOutlines.size == medians.size) {
            "$character: strokes (${strokeOutlines.size}) and medians (${medians.size}) must be parallel"
        }
        require(medians.all { it.size >= 2 }) { "$character: every median needs ≥2 points" }
    }
}

@Serializable
private data class HanziWriterFile(
    val strokes: List<String>,
    val medians: List<List<List<Double>>>,
)

@Serializable
private data class DictionaryEntry(
    val pinyin: List<String> = emptyList(),
    val definition: String = "",
)

/**
 * Loads the checked-in Phase 0 character slice from classpath resources under
 * `/characters/` (downloaded from hanzi-writer-data — see NOTICE.md there).
 * Phase 1 replaces this with the Room-backed repository over the ingested dataset.
 */
class CharacterRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, CharacterData>()
    private val dictionary: Map<String, DictionaryEntry> by lazy {
        json.decodeFromString<Map<String, DictionaryEntry>>(readResource("dictionary.json"))
    }
    private val phrases: Map<String, List<Phrase>> by lazy {
        json.decodeFromString<Map<String, List<Phrase>>>(readResource("phrases.json"))
    }

    /** Characters included in the prototype, in teaching order. */
    fun listCharacters(): List<String> =
        json.decodeFromString<List<String>>(readResource("index.json"))

    fun load(character: String): CharacterData = cache.getOrPut(character) {
        val file = json.decodeFromString<HanziWriterFile>(readResource("$character.json"))
        require(file.strokes.size == file.medians.size) {
            "$character: schema drift — strokes/medians not parallel (spec 02: fail loudly)"
        }
        val entry = dictionary[character]
        CharacterData(
            character = character,
            strokeOutlines = file.strokes.map { path ->
                SvgPathParser.parse(path).mapPoints(HanziCoordinates::normalize)
            },
            medians = file.medians.map { median ->
                median.map { (x, y) -> HanziCoordinates.normalize(Point(x, y)) }
            },
            pinyin = entry?.pinyin.orEmpty(),
            definition = entry?.definition.orEmpty(),
            phrases = phrases[character].orEmpty(),
        )
    }

    private fun readResource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/characters/$name")) {
            "Missing bundled resource /characters/$name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
