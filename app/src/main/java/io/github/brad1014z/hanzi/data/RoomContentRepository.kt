package io.github.brad1014z.hanzi.data

import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.data.ExampleSentence
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
class RoomContentRepository(private val db: HanziDatabase) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, CharacterData>()

    data class World(val id: String, val name: String, val characters: List<CurriculumRow>)

    /** Worlds in teaching order (sequence is world-major, computed at ingest — spec 04). */
    suspend fun worlds(): List<World> =
        db.contentDao().curriculum()
            .groupBy { it.world to it.worldName } // groupBy preserves encounter order
            .map { (key, rows) -> World(key.first, key.second, rows) }

    suspend fun load(character: String): CharacterData {
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
        )
        cache[character] = data
        return data
    }
}
