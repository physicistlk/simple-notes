package personal.notes.touch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import personal.notes.touch.data.db.dao.FlashCardDao
import personal.notes.touch.data.db.dao.FolderDao
import personal.notes.touch.data.db.dao.LabelDao
import personal.notes.touch.data.db.dao.NoteDao
import personal.notes.touch.data.db.dao.PageDao
import personal.notes.touch.data.db.dao.SettingsDao
import personal.notes.touch.data.db.entity.FlashCardEntity
import personal.notes.touch.data.db.entity.FlashCardTagEntity
import personal.notes.touch.data.db.entity.FlashCardTypeEntity
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.data.db.entity.LabelEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.NoteLabelCrossRef
import personal.notes.touch.data.db.entity.NoteLabelUsageEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.db.entity.SettingsEntity

/**
 * TouchNotes Room database (current schema version: 5).
 *
 * ## Migration & data-loss policy (audit C1 / CH4, 2026-11-04)
 *
 * - Schema export is enabled (`exportSchema = true` plus the kapt arg
 *   `room.schemaLocation` in app/build.gradle): every schema version
 *   is written to `app/schemas/personal.notes.touch.data.db.AppDatabase/<version>.json`
 *   and stays in the repo, so future migration steps can be written and
 *   verified against exact schemas.
 * - Legacy dev installs (schema versions 1, 2, 3) are allowed a destructive
 *   fallback when no migration path applies:
 *   `fallbackToDestructiveMigrationFrom(1, 2, 3)` in
 *   TouchNotesApp.buildDatabase. Version 3 is the current schema.
 * - Every future schema change MUST bump `version` here AND register a real
 *   `androidx.room.migration.Migration` in `data/db/Migrations.kt`
 *   (`APP_MIGRATIONS` is wired into `.addMigrations(...)` by
 *   TouchNotesApp.buildDatabase). From version 4 onward there is NO
 *   destructive fallback: an upgrade without a registered Migration will
 *   throw at database open instead of wiping user data — intentional.
 *
 * ## Adding a migration (version N to N + 1)
 * 1. Bump `version` below and make the entity/DAO changes.
 * 2. In `data/db/Migrations.kt` add the `androidx.room.migration.Migration`
 *    step (e.g. `MIGRATION_3_4`) and append it to `APP_MIGRATIONS`; the KDoc
 *    there shows the holder pattern. Never edit the fallback-from list.
 * 3. Build once and keep the generated
 *    `app/schemas/personal.notes.touch.data.db.AppDatabase/(N + 1).json`.
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        PageEntity::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        FlashCardEntity::class,
        FlashCardTypeEntity::class,
        FlashCardTagEntity::class,
        NoteLabelUsageEntity::class,
        SettingsEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun pageDao(): PageDao
    abstract fun labelDao(): LabelDao
    abstract fun flashCardDao(): FlashCardDao
    abstract fun settingsDao(): SettingsDao
}
