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
    val pageType: String = "infinite",
    /**
     * Page the note was last closed on (zero-based, into the note's `pages`
     * rows). Written only by [personal.notes.touch.data.repo.NoteRepository.saveLastPage]
     * so reopening a note never reorders the library ([updatedAt] stays put).
     * The canvas clamps it to the pages that still exist on open.
     */
    val lastPageIndex: Int = 0
)