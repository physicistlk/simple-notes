package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One `((blockId))` written inside a note (`block_refs`, schema 11).
 *
 * Keyed by **short block key**, not by a stored foreign key: a reference to a
 * block that has not been opened yet (or a block that was deleted) is a
 * first-class state the sheet can show as missing, exactly like an unresolved
 * `[[title]]`. The full id is recovered at read time with
 * `note_blocks.id LIKE targetKey || '%'`, so a reference written before the
 * target note was ever opened starts resolving as soon as it is.
 *
 * Rows are derived from the block rows on every save — one row per
 * (source note, source block, target key) — and replaced wholesale per note.
 */
@Entity(
    tableName = "block_refs",
    primaryKeys = ["sourceNoteId", "sourceBlockId", "targetKey"],
    indices = [Index(value = ["targetKey"]), Index(value = ["sourceNoteId"])]
)
data class BlockRefEntity(
    /** The note containing the reference. */
    val sourceNoteId: String,
    /** The block containing it (`note_blocks.id`), so the sheet can say where. */
    val sourceBlockId: String,
    /** Normalised short key of the target block (lowercase prefix of its UUID). */
    val targetKey: String,
    /** Alias as written in `((key|alias))`, null when there is none. */
    val targetAlias: String? = null
)
