package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String? = null,
    val favorite: Boolean = false,
    val deleted: Boolean = false,
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val content: String? = null,
    val pageCount: Int = 1,
    val pageType: String = "infinite"
)