package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The user tables from spec 03, landed in M1 ahead of the content tables (which arrive
 * with the M2 ingest pipeline) — shaped exactly per spec so M3's SRS engine needs no
 * schema migration. Table/column names match the spec's SQL.
 */
@Entity(tableName = "CharacterProgress", indices = [Index("dueAt")])
data class CharacterProgressEntity(
    @PrimaryKey val character: String,
    val state: String,
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    val lastGrade: Int?,
)

@Entity(
    tableName = "ReviewLog",
    indices = [
        Index("character", "reviewedAt"), Index("reviewedAt"),
        Index("uuid", unique = true),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val reviewedAt: Long,
    val grade: Int,
    val drawnCorrectly: Boolean,
    val durationMs: Long?,
    val session: String?,
    /** Client-generated UUID: the sync set-union key (spec 12, added M4/v3). */
    @androidx.room.ColumnInfo(defaultValue = "") val uuid: String = "",
)

/** Queued uploads (spec 12's outbox), drained by WorkManager on connectivity. */
@Entity(tableName = "SyncOutbox")
data class SyncOutboxEntity(
    @PrimaryKey val uuid: String, // idempotent retries
    val kind: String, // "progress" | "reviewLog" | "xp"
    val payload: String, // JSON, interpreted by kind
    val createdAt: Long,
)

@Entity(tableName = "Meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM CharacterProgress")
    fun observeAll(): Flow<List<CharacterProgressEntity>>

    @Query("SELECT * FROM CharacterProgress WHERE character = :character")
    suspend fun get(character: String): CharacterProgressEntity?

    @Query("SELECT * FROM CharacterProgress WHERE dueAt <= :now ORDER BY dueAt")
    suspend fun due(now: Long): List<CharacterProgressEntity>

    /**
     * New characters introduced via the guided track since [todayStart] — counts
     * against the daily cap (spec 04; browse practice is exempt via its session tag).
     * SQLite's bare-column-with-MIN semantics make `session` come from the first row.
     */
    @Query(
        "SELECT COUNT(*) FROM (SELECT character, MIN(reviewedAt) AS firstAt, session " +
            "FROM ReviewLog GROUP BY character HAVING firstAt >= :todayStart AND session = :sessionTag)",
    )
    suspend fun introducedSince(todayStart: Long, sessionTag: String): Int

    /** Streak metric (spec 10): days *played*, pauses gracefully — no loss framing. */
    @Query("SELECT COUNT(DISTINCT date(reviewedAt/1000, 'unixepoch', 'localtime')) FROM ReviewLog")
    suspend fun daysPlayed(): Int

    @Upsert
    suspend fun upsert(progress: CharacterProgressEntity)

    @Insert
    suspend fun insertLog(log: ReviewLogEntity)

    /** Restore path (spec 12): union inserts must not trip the unique uuid index. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertLogIgnore(log: ReviewLogEntity)

    // Manual reset (spec 04: destructive, behind a confirmation).
    @Query("DELETE FROM CharacterProgress") suspend fun clearProgress()
    @Query("DELETE FROM ReviewLog") suspend fun clearLog()

    // Sync support (spec 12, M4).
    @Query("SELECT uuid FROM ReviewLog") suspend fun logUuids(): List<String>
    @Query("SELECT * FROM ReviewLog") suspend fun allLogs(): List<ReviewLogEntity>
    @Query("SELECT * FROM CharacterProgress") suspend fun allProgress(): List<CharacterProgressEntity>
}

@Dao
interface OutboxDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncOutboxEntity)

    @Query("SELECT * FROM SyncOutbox ORDER BY createdAt LIMIT :limit")
    suspend fun oldest(limit: Int = 100): List<SyncOutboxEntity>

    @Query("DELETE FROM SyncOutbox WHERE uuid IN (:uuids)")
    suspend fun delete(uuids: List<String>)

    @Query("SELECT COUNT(*) FROM SyncOutbox")
    suspend fun count(): Int
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM Meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: MetaEntity)
}

@Database(
    entities = [
        CharacterProgressEntity::class, ReviewLogEntity::class, MetaEntity::class,
        SyncOutboxEntity::class,
        CharacterEntity::class, CurriculumEntryEntity::class, StrokePathEntity::class,
        WordEntity::class, WordCharacterEntity::class,
        SentenceEntity::class, SentenceCharacterEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HanziDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun metaDao(): MetaDao
    abstract fun contentDao(): ContentDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        /** Bundled dataset asset produced by `./gradlew :data-ingest:run` (spec 02). */
        const val ASSET_PATH = "databases/hanzi_v1.sqlite"

        @Volatile private var instance: HanziDatabase? = null

        fun get(context: Context): HanziDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HanziDatabase::class.java,
                "hanzi.db",
            )
                // Fresh installs start from the bundled dataset (content + empty user
                // tables). Existing installs take MIGRATION_1_2 + the DatasetSeeder
                // reseed path instead — user data preservation is the inviolable rule
                // (spec 03).
                .createFromAsset(ASSET_PATH)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
