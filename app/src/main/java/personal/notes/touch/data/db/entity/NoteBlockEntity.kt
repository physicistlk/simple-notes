package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted block of a text note (`note_blocks`, schema version 6).
 *
 * ## Why this table exists
 *
 * Before v6 the block editor derived its blocks from `notes.content` on every
 * load, with ids computed from the block's start line ("blk12"). Those ids
 * changed whenever an earlier line was added or removed, so nothing could
 * safely point at a block. This table gives every block a stable [id] (UUID)
 * minted once and kept across saves, opens and raw-markdown edits — the
 * prerequisite for references, backlinks, tags and per-block flashcards.
 *
 * ## Source of truth
 *
 * `notes.content` (markdown) remains the exported/previewed text and the
 * authoritative content: rows here are re-written on every save from the same
 * block list. If a crash lands between the two writes, the markdown wins on
 * the next open and ids are re-attached by
 * `ui.editor.BlockPersistence.reconcileIds` (match on type + text).
 *
 * [blockIndex] is the position inside the note (0-based) and is rewritten on
 * every save; it is an ordering aid only, never an identity.
 */
@Entity(
    tableName = "note_blocks",
    indices = [Index(value = ["noteId", "blockIndex"])]
)
data class NoteBlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val blockIndex: Int,
    /** [personal.notes.touch.ui.editor.BlockType] name; unknown values read as PARAGRAPH. */
    val blockType: String,
    /** Block text WITHOUT its markdown marker (`# `, `- `, `> ` …). */
    val text: String,
    /** 1–6 for HEADING, 1 otherwise. */
    val headingLevel: Int = 1,
    val indent: Int = 0,
    /** Ordinal shown by NUMBERED blocks. */
    val orderNo: Int = 1,
    val checked: Boolean = false,
    /** CODE only: closing fence present. */
    val closed: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
