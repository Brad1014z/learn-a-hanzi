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
    indices = [Index("character", "reviewedAt"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val reviewedAt: Long,
    val grade: Int,
    val drawnCorrectly: Boolean,
    val durationMs: Long?,
    val session: String?,
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

    @Upsert
    suspend fun upsert(progress: CharacterProgressEntity)

    @Insert
    suspend fun insertLog(log: ReviewLogEntity)
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
        CharacterEntity::class, CurriculumEntryEntity::class, StrokePathEntity::class,
        WordEntity::class, WordCharacterEntity::class,
        SentenceEntity::class, SentenceCharacterEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HanziDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun metaDao(): MetaDao
    abstract fun contentDao(): ContentDao

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
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
