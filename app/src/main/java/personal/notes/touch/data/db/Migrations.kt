package personal.notes.touch.data.db

import androidx.room.migration.Migration

/**
 * Central registry of Room schema [Migration]s for [AppDatabase].
 *
 * ## Policy (audit C1 / CH4, 2026-11-04 — "schema upgrades never lose user data")
 *
 * - Schema versions 1, 2 and 3 were shipped before this registry existed
 *   (legacy dev installs). They are covered by
 *   `fallbackToDestructiveMigrationFrom(1, 2, 3)` in TouchNotesApp:
 *   migrating FROM one of those versions with no matching [Migration] clears
 *   and recreates the database rather than crashing.
 * - Schema version 3 is the current schema and the baseline; its schema JSON
 *   is exported to `app/schemas/personal.notes.touch.data.db.AppDatabase/3.json`.
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
internal val APP_MIGRATIONS: Array<Migration> = emptyArray()
