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
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun pageDao(): PageDao
    abstract fun labelDao(): LabelDao
    abstract fun flashCardDao(): FlashCardDao
    abstract fun settingsDao(): SettingsDao
}
