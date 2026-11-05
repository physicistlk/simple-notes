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
    /**
     * F-10 vector PDF source: path of the imported PDF, relative to the
     * app's `filesDir` (e.g. `note/<noteId>/pdf_<ts>.pdf`). Empty for pages
     * that are not PDF imports. Kept relative so backups restored under a
     * different install path still resolve.
     */
    val pdfSourcePath: String = "",
    /** Page index inside [pdfSourcePath]; -1 for non-PDF pages. */
    val pdfPageIndex: Int = -1,
    val updatedAt: Long = System.currentTimeMillis()
)
