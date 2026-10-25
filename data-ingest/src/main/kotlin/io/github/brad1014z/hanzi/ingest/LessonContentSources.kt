package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.security.MessageDigest
import java.sql.DriverManager

/**
 * Read-only view of the bundled dataset, shared by the manifest verifier and the
 * teacher-review draft generator. It reads the checked-in SQLite asset, so both tools
 * work without `data/raw/` and without regenerating the dataset — which matters while
 * the teacher-content gate is still open.
 */
data class ManifestSource(
    val readings: Map<String, List<String>>,
    val definitions: Map<String, String>,
    val curriculum: List<CurriculumInfo>,
    val sentences: Map<String, SentenceInfo>,
    val words: List<WordInfo>,
) {
    val curriculumOrder: List<String> get() = curriculum.map { it.character }
    val worldByCharacter: Map<String, CurriculumInfo> get() = curriculum.associateBy { it.character }
}

data class CurriculumInfo(
    val character: String,
    val sequence: Int,
    val world: String,
    val worldName: String,
)

data class SentenceInfo(
    val text: String,
    val pinyin: String,
    val english: String,
)

data class WordInfo(
    val simplified: String,
    val pinyin: String,
    val english: String,
    val freqRank: Int?,
)

fun loadManifestSource(database: File): ManifestSource {
    check(database.isFile) { "Dataset not found: ${database.path} (run :data-ingest:run first)" }
    return DriverManager.getConnection("jdbc:sqlite:${database.path}").use { conn ->
        val readings = LinkedHashMap<String, List<String>>()
        val definitions = LinkedHashMap<String, String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT character, pinyin, definition FROM Character").use { rs ->
                while (rs.next()) {
                    val character = rs.getString("character")
                    readings[character] = json.decodeFromString<List<String>>(rs.getString("pinyin"))
                    definitions[character] = rs.getString("definition").orEmpty()
                }
            }
        }

        val curriculum = buildList {
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT character, sequence, world, worldName FROM CurriculumEntry " +
                        "WHERE curriculumId = 'hsk' AND level = 1 ORDER BY sequence",
                ).use { rs ->
                    while (rs.next()) {
                        add(
                            CurriculumInfo(
                                character = rs.getString("character"),
                                sequence = rs.getInt("sequence"),
                                world = rs.getString("world"),
                                worldName = rs.getString("worldName"),
                            ),
                        )
                    }
                }
            }
        }

        // Mirrors ContentDao.sentence(): the character-specific LLM sentence first.
        val sentences = LinkedHashMap<String, SentenceInfo>()
        conn.prepareStatement(
            "SELECT s.text, s.pinyin, s.english FROM Sentence s " +
                "JOIN SentenceCharacter sc ON sc.sentenceId = s.id " +
                "WHERE sc.character = ? " +
                "ORDER BY CASE WHEN s.forCharacter = ? THEN 0 ELSE 1 END, " +
                "CASE WHEN s.source = 'llm' THEN 0 ELSE 1 END, s.id LIMIT 1",
        ).use { ps ->
            for (character in readings.keys) {
                ps.setString(1, character)
                ps.setString(2, character)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val pinyin = rs.getString("pinyin") ?: continue
                        val english = rs.getString("english") ?: continue
                        sentences[character] = SentenceInfo(rs.getString("text"), pinyin, english)
                    }
                }
            }
        }

        val words = buildList {
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT simplified, pinyin, english, freqRank FROM Word " +
                        "ORDER BY CASE WHEN freqRank IS NULL THEN 1 ELSE 0 END, freqRank, simplified",
                ).use { rs ->
                    while (rs.next()) {
                        add(
                            WordInfo(
                                simplified = rs.getString("simplified"),
                                pinyin = rs.getString("pinyin"),
                                english = rs.getString("english"),
                                freqRank = (rs.getObject("freqRank") as? Number)?.toInt(),
                            ),
                        )
                    }
                }
            }
        }

        ManifestSource(readings, definitions, curriculum, sentences, words)
    }
}

/** The app's bundled-clip naming convention (PregenAudioSpeechService). */
fun audioAssetPath(character: String): String = "audio/zh-Hans/${sha1(character)}.mp3"

fun sha1(text: String): String = MessageDigest.getInstance("SHA-1")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

/** Resolve a CLI path against the repository root (all data-ingest tasks run there). */
fun resolveFromRoot(root: File, path: String): File =
    File(path).let { if (it.isAbsolute) it else File(root, path) }
