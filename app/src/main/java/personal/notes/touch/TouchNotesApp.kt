package personal.notes.touch

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import personal.notes.touch.data.db.AppDatabase
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.data.repo.SettingsRepository

class TouchNotesApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    val flashCardRepository: personal.notes.touch.data.repo.FlashCardRepository by lazy {
        personal.notes.touch.data.repo.FlashCardRepository(database.flashCardDao())
    }

    private fun buildDatabase(): AppDatabase {
        return Room.databaseBuilder(this, AppDatabase::class.java, "touchnotes.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun buildNoteRepository(): NoteRepository {
        return NoteRepository(
            noteDao = database.noteDao(),
            folderDao = database.folderDao(),
            pageDao = database.pageDao(),
            fileStore = fileStore
        )
    }

    private fun buildSettingsRepository(): SettingsRepository {
        return SettingsRepository(database.settingsDao())
    }

    fun reloadDatabase() {
        synchronized(this) {
            try {
                _database?.close()
            } catch (_: Exception) {}
            _database = buildDatabase()
            _noteRepository = buildNoteRepository()
            _settingsRepository = buildSettingsRepository()
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
