package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    indices = [Index(value = ["noteId"])]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val index: Int,
    val bitmapPath: String,
    val updatedAt: Long = System.currentTimeMillis()
)
