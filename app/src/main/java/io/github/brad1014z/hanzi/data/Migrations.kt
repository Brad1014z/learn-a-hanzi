package io.github.brad1014z.hanzi.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 (M1: user tables only) → v2 (M2: + content tables). Creates the content tables
 * empty; the [DatasetSeeder] fills them from the bundled asset on next launch (the
 * spec 03 reseed path). SQL mirrors app/schemas/2.json exactly — Room validates it.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Character` (`character` TEXT NOT NULL, " +
                "`lang` TEXT NOT NULL, `pinyin` TEXT NOT NULL, `definition` TEXT NOT NULL, " +
                "`radical` TEXT, `strokeCount` INTEGER NOT NULL, `freqRank` INTEGER, " +
                "`decomposition` TEXT, `etymologyHint` TEXT, PRIMARY KEY(`character`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Character_freqRank` ON `Character` (`freqRank`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `CurriculumEntry` (`curriculumId` TEXT NOT NULL, " +
                "`character` TEXT NOT NULL, `level` INTEGER NOT NULL, `sequence` INTEGER NOT NULL, " +
                "`world` TEXT NOT NULL, `worldName` TEXT NOT NULL, " +
                "PRIMARY KEY(`curriculumId`, `character`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_CurriculumEntry_curriculumId_level_sequence` " +
                "ON `CurriculumEntry` (`curriculumId`, `level`, `sequence`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `StrokePath` (`character` TEXT NOT NULL, " +
                "`strokeIndex` INTEGER NOT NULL, `pathData` TEXT NOT NULL, `median` TEXT NOT NULL, " +
                "PRIMARY KEY(`character`, `strokeIndex`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Word` (`id` INTEGER NOT NULL, `lang` TEXT NOT NULL, " +
                "`simplified` TEXT NOT NULL, `traditional` TEXT, `pinyin` TEXT NOT NULL, " +
                "`english` TEXT NOT NULL, `freqRank` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Word_simplified` ON `Word` (`simplified`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `WordCharacter` (`wordId` INTEGER NOT NULL, " +
                "`character` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`wordId`, `character`, `position`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WordCharacter_character` ON `WordCharacter` (`character`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Sentence` (`id` INTEGER NOT NULL, `lang` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, `pinyin` TEXT, `english` TEXT, `source` TEXT NOT NULL, " +
                "`contributor` TEXT, `forCharacter` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SentenceCharacter` (`sentenceId` INTEGER NOT NULL, " +
                "`character` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`sentenceId`, `character`, `position`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_SentenceCharacter_character` ON `SentenceCharacter` (`character`)")
    }
}

/**
 * v2 → v3 (M4, spec 12): ReviewLog gains the sync set-union `uuid` (backfilled for
 * existing rows) and the SyncOutbox table arrives. SQL mirrors app/schemas/3.json.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `ReviewLog` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `ReviewLog` SET `uuid` = lower(hex(randomblob(16))) WHERE `uuid` = ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ReviewLog_uuid` ON `ReviewLog` (`uuid`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SyncOutbox` (`uuid` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
        )
    }
}
