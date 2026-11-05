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
    /** Per-page paper pattern (DrawingCanvasView.PaperPattern.code); 0 = unset → fall back to app default. */
    val paperPattern: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
