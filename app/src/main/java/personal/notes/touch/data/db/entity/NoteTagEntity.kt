package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One `#tag#` of a note (`note_tags`, schema version 7).
 *
 * ## Why a table
 *
 * Tags are *derived* from `notes.content` (markdown stays the source of truth)
 * and re-written whenever a note is saved, like `note_blocks`. Keeping them in
 * a table makes the library's tag bar a single grouped query instead of a
 * full scan of every note body on each keystroke, and gives the tag list its
 * counts for free.
 *
 * [tag] is the case-insensitive identity (`#Work#` -> `work`); [displayName]
 * keeps the first spelling seen so the UI can show `#Work`. Rows for a note
 * are replaced wholesale, never merged.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tag"],
    indices = [Index(value = ["tag"])]
)
data class NoteTagEntity(
    val noteId: String,
    /** Lowercased tag identity, e.g. `work`, `project/alpha`. */
    val tag: String,
    /** Spelling kept for display, e.g. `Work`. */
    val displayName: String
)
