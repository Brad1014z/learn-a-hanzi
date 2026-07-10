package io.github.brad1014z.hanzi.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The spec 03 "reseed" path: a dataset update replaces content tables but must
 * preserve user tables. Fresh installs never need this (createFromAsset ships the
 * dataset); existing installs hit it after a schema migration or when a newer
 * `Meta.datasetVersion` is bundled.
 */
class DatasetSeeder(private val context: Context, private val db: HanziDatabase) {

    /** Idempotent; cheap when versions already match. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val installed = db.metaDao().get(DATASET_VERSION_KEY)
        val assetFile = copyAssetToCache()
        try {
            SQLiteDatabase.openDatabase(
                assetFile.path, null, SQLiteDatabase.OPEN_READONLY,
            ).use { asset ->
                val bundled = asset.stringOrNull(
                    "SELECT value FROM Meta WHERE `key` = '$DATASET_VERSION_KEY'",
                ) ?: error("Bundled dataset asset has no $DATASET_VERSION_KEY (spec 02: ingest must stamp it)")
                if (bundled == installed) return@withContext
                reseedFrom(asset, bundled)
            }
        } finally {
            assetFile.delete()
        }
    }

    private suspend fun reseedFrom(asset: SQLiteDatabase, bundledVersion: String) {
        val dao = db.contentDao()
        db.withTransaction {
            dao.clearSentenceCharacters(); dao.clearSentences()
            dao.clearWordCharacters(); dao.clearWords()
            dao.clearStrokes(); dao.clearCurriculum(); dao.clearCharacters()

            dao.insertCharacters(
                asset.readRows("Character") {
                    CharacterEntity(
                        character = it.s("character"), lang = it.s("lang"),
                        pinyin = it.s("pinyin"), definition = it.s("definition"),
                        radical = it.sOrNull("radical"), strokeCount = it.i("strokeCount"),
                        freqRank = it.iOrNull("freqRank"),
                        decomposition = it.sOrNull("decomposition"),
                        etymologyHint = it.sOrNull("etymologyHint"),
                    )
                },
            )
            dao.insertCurriculum(
                asset.readRows("CurriculumEntry") {
                    CurriculumEntryEntity(
                        curriculumId = it.s("curriculumId"), character = it.s("character"),
                        level = it.i("level"), sequence = it.i("sequence"),
                        world = it.s("world"), worldName = it.s("worldName"),
                    )
                },
            )
            dao.insertStrokes(
                asset.readRows("StrokePath") {
                    StrokePathEntity(
                        character = it.s("character"), strokeIndex = it.i("strokeIndex"),
                        pathData = it.s("pathData"), median = it.s("median"),
                    )
                },
            )
            dao.insertWords(
                asset.readRows("Word") {
                    WordEntity(
                        id = it.l("id"), lang = it.s("lang"), simplified = it.s("simplified"),
                        traditional = it.sOrNull("traditional"), pinyin = it.s("pinyin"),
                        english = it.s("english"), freqRank = it.iOrNull("freqRank"),
                    )
                },
            )
            dao.insertWordCharacters(
                asset.readRows("WordCharacter") {
                    WordCharacterEntity(it.l("wordId"), it.s("character"), it.i("position"))
                },
            )
            dao.insertSentences(
                asset.readRows("Sentence") {
                    SentenceEntity(
                        id = it.l("id"), lang = it.s("lang"), text = it.s("text"),
                        pinyin = it.sOrNull("pinyin"), english = it.sOrNull("english"),
                        source = it.s("source"), contributor = it.sOrNull("contributor"),
                        forCharacter = it.sOrNull("forCharacter"),
                    )
                },
            )
            dao.insertSentenceCharacters(
                asset.readRows("SentenceCharacter") {
                    SentenceCharacterEntity(it.l("sentenceId"), it.s("character"), it.i("position"))
                },
            )
            db.metaDao().put(MetaEntity(DATASET_VERSION_KEY, bundledVersion))
        }
    }

    private fun copyAssetToCache(): File {
        val out = File.createTempFile("dataset", ".sqlite", context.cacheDir)
        context.assets.open(HanziDatabase.ASSET_PATH).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return out
    }

    companion object {
        const val DATASET_VERSION_KEY = "datasetVersion"
    }
}

private fun SQLiteDatabase.stringOrNull(sql: String): String? =
    rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getString(0) else null }

private fun <T> SQLiteDatabase.readRows(table: String, map: (android.database.Cursor) -> T): List<T> =
    rawQuery("SELECT * FROM `$table`", null).use { c ->
        buildList { while (c.moveToNext()) add(map(c)) }
    }

private fun android.database.Cursor.s(col: String): String = getString(getColumnIndexOrThrow(col))
private fun android.database.Cursor.sOrNull(col: String): String? =
    getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
private fun android.database.Cursor.i(col: String): Int = getInt(getColumnIndexOrThrow(col))
private fun android.database.Cursor.iOrNull(col: String): Int? =
    getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
private fun android.database.Cursor.l(col: String): Long = getLong(getColumnIndexOrThrow(col))
