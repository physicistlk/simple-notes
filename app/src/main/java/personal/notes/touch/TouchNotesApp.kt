package personal.notes.touch

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import personal.notes.touch.data.db.AppDatabase
import personal.notes.touch.data.db.APP_MIGRATIONS
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.FlashCardRepository
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.data.repo.SettingsRepository

class TouchNotesApp : Application() {
    /** Outlives any screen's ViewModel: used for writes that must finish after navigation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _database: AppDatabase? = null

    val database: AppDatabase
        get() = _database ?: synchronized(this) {
            _database ?: buildDatabase().also { _database = it }
        }

    val fileStore: NoteFileStore by lazy {
        NoteFileStore(this)
    }

    @Volatile
    private var _noteRepository: NoteRepository? = null

    val noteRepository: NoteRepository
        get() = _noteRepository ?: synchronized(this) {
            _noteRepository ?: buildNoteRepository().also { _noteRepository = it }
        }

    @Volatile
    private var _settingsRepository: SettingsRepository? = null

    val settingsRepository: SettingsRepository
        get() = _settingsRepository ?: synchronized(this) {
            _settingsRepository ?: buildSettingsRepository().also { _settingsRepository = it }
        }

    @Volatile
    private var _flashCardRepository: FlashCardRepository? = null

    val flashCardRepository: FlashCardRepository
        get() = _flashCardRepository ?: synchronized(this) {
            _flashCardRepository ?: buildFlashCardRepository().also { _flashCardRepository = it }
        }

    private fun buildDatabase(): AppDatabase {
        return Room.databaseBuilder(this, AppDatabase::class.java, "touchnotes.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Destructive fallback is allowed only from the pre-release dev
            // schemas 1-2, which never shipped user data. Version 3 onward has
            // a registered Migration chain (3→4→5→6→7) and MUST NOT wipe:
            // Room also rejects a Builder where a migration starts at a version
            // listed here ("Inconsistency detected… Start version: 3"), which is
            // exactly what guards this. An upgrade without a migration crashes
            // at open time instead of silently destroying notes.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .addMigrations(*APP_MIGRATIONS)
            .build()
    }

    private fun buildNoteRepository(): NoteRepository {
        return NoteRepository(
            noteDao = database.noteDao(),
            noteBlockDao = database.noteBlockDao(),
            noteTagDao = database.noteTagDao(),
            noteRefDao = database.noteRefDao(),
            folderDao = database.folderDao(),
            pageDao = database.pageDao(),
            templateDao = database.templateDao(),
            blockRefDao = database.blockRefDao(),
            fileStore = fileStore
        )
    }

    private fun buildSettingsRepository(): SettingsRepository {
        return SettingsRepository(database.settingsDao())
    }

    private fun buildFlashCardRepository(): FlashCardRepository {
        return FlashCardRepository(database.flashCardDao())
    }

    fun reloadDatabase() {
        synchronized(this) {
            try {
                _database?.close()
            } catch (_: Exception) {}
            _database = buildDatabase()
            _noteRepository = buildNoteRepository()
            _settingsRepository = buildSettingsRepository()
            _flashCardRepository = buildFlashCardRepository()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure initial settings exist in the Room database
        appScope.launch {
            settingsRepository.getSettingsDirect()
        }
    }
}
