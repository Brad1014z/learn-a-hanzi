package io.github.brad1014z.hanzi.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The migration harness promised in spec 08: each test materializes a database file
 * from a checked-in schema JSON (exactly what shipped at that version), plants user
 * rows, then opens it with Room + the production migrations. Room's own open-time
 * validation compares the migrated schema against the generated expectation, so a
 * migration that drifts from schemas/3.json fails here — before it eats someone's
 * progress on a phone (spec 03's inviolable rule).
 *
 * (Room's MigrationTestHelper needs the schema JSONs as instrumentation assets, which
 * AGP only wires for on-device tests; reading the JSONs straight off the repo keeps
 * this in the host-side unit suite that CI already runs.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"
    private var db: HanziDatabase? = null

    @After
    fun cleanUp() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    /** DDL for a historical version, straight from the exported schema JSON. */
    private fun schemaStatements(version: Int): List<String> {
        val file = File("schemas/io.github.brad1014z.hanzi.data.HanziDatabase/$version.json")
        assertTrue(file.exists(), "checked-in schema missing: ${file.absolutePath}")
        val database = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
        val statements = mutableListOf<String>()
        for (entity in database["entities"]!!.jsonArray) {
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            fun sql(node: kotlinx.serialization.json.JsonElement) =
                node.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            statements += sql(entity)
            entity.jsonObject["indices"]?.jsonArray?.forEach { statements += sql(it) }
        }
        // room_master_table + the version's identity hash, so Room sees a real old install.
        for (query in database["setupQueries"]!!.jsonArray) {
            statements += query.jsonPrimitive.content
        }
        return statements
    }

    /** Creates hanzi.db as it existed at [version], with [rows] of user data planted. */
    private fun seedInstallAt(version: Int, rows: List<String>) {
        val file = context.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            schemaStatements(version).forEach(raw::execSQL)
            rows.forEach(raw::execSQL)
            raw.version = version
        }
    }

    /** The production open path: migrations registered exactly as [HanziDatabase.get]. */
    private fun openWithMigrations(): HanziDatabase =
        Room.databaseBuilder(context, HanziDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
            .also { db = it }

    @Test
    fun `2 to 3 backfills distinct uuids and creates an empty outbox`() = runTest {
        seedInstallAt(
            2,
            listOf(
                "INSERT INTO ReviewLog (character, reviewedAt, grade, drawnCorrectly, durationMs, session) " +
                    "VALUES ('火', 1000, 5, 1, 4200, 'quest'), ('人', 2000, 3, 1, NULL, 'browse')",
            ),
        )

        val migrated = openWithMigrations()
        val uuids = migrated.progressDao().allLogs().map { it.uuid }
        assertEquals(2, uuids.size)
        assertTrue(uuids.all { it.isNotBlank() }, "backfill left a blank uuid: $uuids")
        assertEquals(uuids.size, uuids.distinct().size, "backfilled uuids collide: $uuids")
        assertEquals(0, migrated.outboxDao().count())
    }

    @Test
    fun `full 1 to 3 chain carries user progress through untouched`() = runTest {
        seedInstallAt(
            1,
            listOf(
                "INSERT INTO CharacterProgress (character, state, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt, lastGrade) " +
                    "VALUES ('我', 'review', 86400000, 6.0, 2.5, 4, 1, 1000, 4)",
                "INSERT INTO ReviewLog (character, reviewedAt, grade, drawnCorrectly, durationMs, session) " +
                    "VALUES ('我', 1000, 4, 1, 3000, 'quest')",
                "INSERT INTO Meta (`key`, value) VALUES ('xpTotal', '120')",
            ),
        )

        val migrated = openWithMigrations()
        val progress = assertNotNull(migrated.progressDao().get("我"), "progress row lost in migration")
        assertEquals("review", progress.state)
        assertEquals(86400000, progress.dueAt)
        assertEquals(6.0, progress.intervalDays)
        assertEquals(2.5, progress.ease)
        assertEquals(4, progress.reps)
        assertEquals(1, progress.lapses)
        assertEquals("120", migrated.metaDao().get("xpTotal"))
        // 1→2 creates the content tables empty; the DatasetSeeder fills them on launch.
        migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM Character").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }
}
