package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One outgoing wiki link of a note (`note_refs`, schema version 7).
 *
 * Deliberate scope: references are keyed by **target title** ([targetKey] is
 * the trimmed/lowercased title, see `text.WikiLinks.key`), not by block or note
 * id. That keeps the syntax readable and the index tiny, and it makes an
 * unresolved reference a first-class state (a `[[Title]]` whose note does not
 * exist yet) instead of an error. The cost is explicit: renaming a note does
 * not rewrite the references that pointed at it — they show up as unresolved
 * in the referencing note's reference sheet.
 *
 * Rows are derived from `notes.content` on every save, exactly like
 * [NoteTagEntity], and replaced wholesale per note.
 */
@Entity(
    tableName = "note_refs",
    primaryKeys = ["noteId", "targetKey"],
    indices = [Index(value = ["targetKey"])]
)
data class NoteRefEntity(
    /** The note that contains the `[[…]]` (the source). */
    val noteId: String,
    /** Normalised target title used for matching. */
    val targetKey: String,
    /** target as written, so the sheet can show the original spelling. */
    val targetTitle: String
)
