package personal.notes.touch.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central registry of Room schema [Migration]s for [AppDatabase].
 *
 * ## Policy (audit C1 / CH4, 2026-11-04 — "schema upgrades never lose user data")
 *
 * - Schema versions 1, 2 and 3 were shipped before this registry existed
 *   (legacy dev installs). They are covered by
 *   `fallbackToDestructiveMigrationFrom(1, 2)` in TouchNotesApp (3 is migrated, never wiped):
 *   migrating FROM one of those versions with no matching [Migration] clears
 *   and recreates the database rather than crashing. (From 3, the
 *   registered [MIGRATION_3_4] provides the data-preserving path.)
 * - Schema version 3 was the baseline before this registry existed; its
 *   schema JSON stays at `app/schemas/personal.notes.touch.data.db.AppDatabase/3.json`
 *   and [MIGRATION_3_4] upgrades legacy 3 installs to 4 without data loss.
 * - Every future schema change MUST:
 *   1. bump `version` in [AppDatabase],
 *   2. add a real, correct [Migration] step here and append it to
 *      [APP_MIGRATIONS],
 *   3. build once and keep the newly generated
 *      `app/schemas/personal.notes.touch.data.db.AppDatabase/<newVersion>.json`.
 * - Do NOT add new schema versions to the fallback-from list. From version 4
 *   upward an upgrade without a registered [Migration] throws at database
 *   open instead of silently destroying user data — that failure mode is the
 *   point of this policy.
 *
 * The registry starts empty on purpose (no migrations are fabricated against
 * an unverified schema). The first real schema change replaces the empty
 * array with its holder object, for example:
 *
 * ```
 * object AppMigrations {
 *
 *     val MIGRATION_3_4 = object : Migration(3, 4) {
 *         override fun migrate(db: SupportSQLiteDatabase) {
 *             // Statements that turn the 3.json schema into 4.json, e.g.:
 *             db.execSQL(
 *                 "ALTER TABLE settings ADD COLUMN lastBackupAt TEXT NOT NULL DEFAULT ''"
 *             )
 *         }
 *     }
 *
 *     internal val APP_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
 * }
 * ```
 */
internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Per-page paper pattern (0 = BLANK/unset → falls back to the app default).
        db.execSQL("ALTER TABLE pages ADD COLUMN paperPattern INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * F-10 vector PDF import: every page remembers the source document it came
 * from so the canvas can re-render it from vectors at any zoom level.
 * Additive columns only — existing pages keep `pdfSourcePath = ''` /
 * `pdfPageIndex = -1` and behave exactly as before (raster page PNG).
 */
internal val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pages ADD COLUMN pdfSourcePath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE pages ADD COLUMN pdfPageIndex INTEGER NOT NULL DEFAULT -1")
    }
}

/**
 * note_blocks stable block ids (schema 6): the text editor stops deriving ids
 * from the block's line number and persists one row per block with a UUID.
 * Purely additive — `notes.content` remains the text source of truth, and
 * existing notes are migrated lazily (ids minted on first open).
 */
internal val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_blocks` (" +
                "`id` TEXT NOT NULL, " +
                "`noteId` TEXT NOT NULL, " +
                "`blockIndex` INTEGER NOT NULL, " +
                "`blockType` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`headingLevel` INTEGER NOT NULL, " +
                "`indent` INTEGER NOT NULL, " +
                "`orderNo` INTEGER NOT NULL, " +
                "`checked` INTEGER NOT NULL, " +
                "`closed` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_note_blocks_noteId_blockIndex` " +
                "ON `note_blocks` (`noteId`, `blockIndex`)"
        )
    }
}


/**
 * Knowledge index (schema 7): `note_tags` and `note_refs` are derived from
 * `notes.content` — tags from `#tag#`, references from `[[Title]]`. Both are
 * additive tables; nothing existing is touched, and the first library start
 * after the upgrade re-indexes whatever notes already carry content
 * (`NoteRepository.reindexAll`), so the migration itself only creates schema.
 */
internal val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_tags` (" +
                "`noteId` TEXT NOT NULL, " +
                "`tag` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "PRIMARY KEY(`noteId`, `tag`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_note_tags_tag` ON `note_tags` (`tag`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_refs` (" +
                "`noteId` TEXT NOT NULL, " +
                "`targetKey` TEXT NOT NULL, " +
                "`targetTitle` TEXT NOT NULL, " +
                "PRIMARY KEY(`noteId`, `targetKey`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_note_refs_targetKey` ON `note_refs` (`targetKey`)"
        )
    }
}

internal val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE note_blocks ADD COLUMN collapsed INTEGER NOT NULL DEFAULT 0")
    }
}

internal val APP_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
