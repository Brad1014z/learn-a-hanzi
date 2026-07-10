package io.github.brad1014z.hanzi.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Content tables per spec 03 (amended 2026-07-09: `CurriculumEntry.world`,
 * `Sentence.pinyin`). Read-only at runtime — seeded from the bundled dataset produced
 * by :data-ingest; only the seeder writes them. No declared Room foreign keys: user
 * progress may reference non-curriculum characters (e.g. the Phase 0 prototype set),
 * and a dataset swap must never cascade into user tables.
 */
@Entity(tableName = "Character", indices = [Index("freqRank")])
data class CharacterEntity(
    @PrimaryKey val character: String,
    val lang: String,
    val pinyin: String, // JSON array of tone-marked readings
    val definition: String,
    val radical: String?,
    val strokeCount: Int,
    val freqRank: Int?,
    val decomposition: String?,
    val etymologyHint: String?,
)

@Entity(
    tableName = "CurriculumEntry",
    primaryKeys = ["curriculumId", "character"],
    indices = [Index("curriculumId", "level", "sequence")],
)
data class CurriculumEntryEntity(
    val curriculumId: String,
    val character: String,
    val level: Int,
    val sequence: Int, // world-major teaching order, computed at ingest (spec 04)
    val world: String, // curated world id (spec 04/10)
    val worldName: String, // placeholder display name — final naming is the son's (spec 11)
)

@Entity(tableName = "StrokePath", primaryKeys = ["character", "strokeIndex"])
data class StrokePathEntity(
    val character: String,
    val strokeIndex: Int,
    val pathData: String, // SVG outline path, already normalized to 1000×1000 Y-down
    val median: String, // JSON [[x,y],…], normalized likewise
)

@Entity(tableName = "Word", indices = [Index("simplified")])
data class WordEntity(
    @PrimaryKey val id: Long,
    val lang: String,
    val simplified: String,
    val traditional: String?,
    val pinyin: String, // space-separated, tone-marked
    val english: String,
    val freqRank: Int?,
)

@Entity(
    tableName = "WordCharacter",
    primaryKeys = ["wordId", "character", "position"],
    indices = [Index("character")],
)
data class WordCharacterEntity(
    val wordId: Long,
    val character: String,
    val position: Int,
)

@Entity(tableName = "Sentence")
data class SentenceEntity(
    @PrimaryKey val id: Long,
    val lang: String,
    val text: String,
    val pinyin: String?, // tone-marked (LLM sentences); null for sources without it
    val english: String?,
    val source: String, // "llm" | "tatoeba"
    val contributor: String?, // Tatoeba username, or the pinned model id for "llm"
    val forCharacter: String?, // the character this sentence was crafted for (LLM rows)
)

@Entity(
    tableName = "SentenceCharacter",
    primaryKeys = ["sentenceId", "character", "position"],
    indices = [Index("character")],
)
data class SentenceCharacterEntity(
    val sentenceId: Long,
    val character: String,
    val position: Int,
)

/** One curriculum grid row: character + world + display fields. */
data class CurriculumRow(
    val character: String,
    val world: String,
    val worldName: String,
    val sequence: Int,
    val pinyin: String,
    val definition: String,
)

@Dao
interface ContentDao {
    @Query(
        "SELECT ce.character AS character, ce.world AS world, ce.worldName AS worldName, " +
            "ce.sequence AS sequence, c.pinyin AS pinyin, c.definition AS definition " +
            "FROM CurriculumEntry ce JOIN Character c ON c.character = ce.character " +
            "WHERE ce.curriculumId = :curriculumId AND ce.level = :level ORDER BY ce.sequence",
    )
    suspend fun curriculum(curriculumId: String = "hsk", level: Int = 1): List<CurriculumRow>

    @Query("SELECT * FROM Character WHERE character = :character")
    suspend fun character(character: String): CharacterEntity?

    @Query("SELECT * FROM StrokePath WHERE character = :character ORDER BY strokeIndex")
    suspend fun strokes(character: String): List<StrokePathEntity>

    @Query(
        "SELECT w.* FROM Word w JOIN WordCharacter wc ON wc.wordId = w.id " +
            "WHERE wc.character = :character " +
            "ORDER BY CASE WHEN w.freqRank IS NULL THEN 1 ELSE 0 END, w.freqRank LIMIT :limit",
    )
    suspend fun words(character: String, limit: Int = 3): List<WordEntity>

    @Query(
        "SELECT s.* FROM Sentence s JOIN SentenceCharacter sc ON sc.sentenceId = s.id " +
            "WHERE sc.character = :character " +
            "ORDER BY CASE WHEN s.forCharacter = :character THEN 0 ELSE 1 END, " +
            "CASE WHEN s.source = 'llm' THEN 0 ELSE 1 END, s.id LIMIT 1",
    )
    suspend fun sentence(character: String): SentenceEntity?

    @Query("SELECT COUNT(*) FROM Character")
    suspend fun characterCount(): Int

    // Seeder writes (content refresh — spec 03 “reseed” path).
    @Insert fun insertCharacters(rows: List<CharacterEntity>)
    @Insert fun insertCurriculum(rows: List<CurriculumEntryEntity>)
    @Insert fun insertStrokes(rows: List<StrokePathEntity>)
    @Insert fun insertWords(rows: List<WordEntity>)
    @Insert fun insertWordCharacters(rows: List<WordCharacterEntity>)
    @Insert fun insertSentences(rows: List<SentenceEntity>)
    @Insert fun insertSentenceCharacters(rows: List<SentenceCharacterEntity>)

    @Query("DELETE FROM Character") fun clearCharacters()
    @Query("DELETE FROM CurriculumEntry") fun clearCurriculum()
    @Query("DELETE FROM StrokePath") fun clearStrokes()
    @Query("DELETE FROM Word") fun clearWords()
    @Query("DELETE FROM WordCharacter") fun clearWordCharacters()
    @Query("DELETE FROM Sentence") fun clearSentences()
    @Query("DELETE FROM SentenceCharacter") fun clearSentenceCharacters()
}
