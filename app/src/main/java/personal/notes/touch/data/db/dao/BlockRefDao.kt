package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.BlockRefEntity

/**
 * One block a note references (`((key))`), resolved against the live block
 * rows. Null target fields mean the target block no longer exists; the sheet
 * shows that instead of hiding the reference.
 */
data class BlockRefTarget(
    val targetKey: String,
    val targetAlias: String?,
    val targetBlockId: String?,
    val targetNoteId: String?,
    val targetText: String?,
    val targetTitle: String?
)

/** One block reference pointing at a block of the open note. */
data class BlockBacklink(
    val sourceNoteId: String,
    val sourceBlockId: String?,
    val sourceTitle: String,
    val sourceText: String?,
    val targetKey: String,
    val targetBlockId: String?,
    val targetText: String?
)

/** A block offered by the `((` autocomplete. */
data class BlockCandidate(
    val blockId: String,
    val noteId: String,
    val text: String,
    val noteTitle: String,
    val blockIndex: Int,
    /** [personal.notes.touch.ui.editor.BlockType] name, so a query can skip QUERY blocks. */
    val blockType: String = "PARAGRAPH"
)


/**
 * Block-reference index (`block_refs`, schema 11).
 *
 * Reads join back to `note_blocks`, so a reference written before the target
 * was indexed starts resolving on its own, and a deleted target degrades to a
 * "missing" row rather than an orphan that disappears. Writes replace one
 * note's rows atomically.
 */
@Dao
abstract class BlockRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(refs: List<BlockRefEntity>)

    @Query("SELECT * FROM block_refs WHERE sourceNoteId = :noteId")
    abstract suspend fun getForNote(noteId: String): List<BlockRefEntity>

    @Query("DELETE FROM block_refs WHERE sourceNoteId = :noteId")
    abstract suspend fun deleteForNote(noteId: String)

    @Query("DELETE FROM block_refs WHERE sourceNoteId IN (SELECT id FROM notes WHERE deleted = 1)")
    abstract suspend fun deleteForRecycledNotes(): Int

    /** Source note → note that owns the referenced block, for the graph view. */
    @Query(
        """
        SELECT r.sourceNoteId AS sourceNoteId, b.noteId AS targetNoteId
        FROM block_refs r
        INNER JOIN note_blocks b ON b.id LIKE r.targetKey || '%'
        INNER JOIN notes s ON s.id = r.sourceNoteId AND s.deleted = 0
        INNER JOIN notes t ON t.id = b.noteId AND t.deleted = 0
        """
    )
    abstract suspend fun getGraphEdges(): List<NoteGraphEdge>

    @Transaction
    open suspend fun replaceForNote(noteId: String, refs: List<BlockRefEntity>) {
        deleteForNote(noteId)
        if (refs.isNotEmpty()) insertAll(refs)
    }

    /**
     * References written in [noteId], each resolved to its target block.
     *
     * The correlated subquery picks one block per key deterministically, so an
     * (extremely unlikely) shared 8-char prefix cannot duplicate the row list.
     */
    @Query(
        """
        SELECT r.targetKey AS targetKey,
               r.targetAlias AS targetAlias,
               b.id AS targetBlockId,
               b.noteId AS targetNoteId,
               b.text AS targetText,
               n.title AS targetTitle
        FROM block_refs r
        LEFT JOIN note_blocks b ON b.id = (
            SELECT x.id FROM note_blocks x
            WHERE x.id LIKE r.targetKey || '%'
            ORDER BY x.id
            LIMIT 1
        )
        LEFT JOIN notes n ON n.id = b.noteId AND n.deleted = 0
        WHERE r.sourceNoteId = :noteId
        ORDER BY COALESCE(b.blockIndex, 999999), r.targetKey
        """
    )
    abstract fun observeOutgoing(noteId: String): Flow<List<BlockRefTarget>>

    /** References in other notes that point at any block of [noteId]. */
    @Query(
        """
        SELECT r.sourceNoteId AS sourceNoteId,
               r.sourceBlockId AS sourceBlockId,
               n.title AS sourceTitle,
               b.text AS sourceText,
               r.targetKey AS targetKey,
               t.id AS targetBlockId,
               t.text AS targetText
        FROM block_refs r
        INNER JOIN notes n ON n.id = r.sourceNoteId AND n.deleted = 0
        LEFT JOIN note_blocks b ON b.id = r.sourceBlockId
        INNER JOIN note_blocks t ON t.id LIKE r.targetKey || '%'
        WHERE t.noteId = :noteId AND r.sourceNoteId != :noteId
        ORDER BY n.updatedAt DESC, t.blockIndex ASC
        """
    )
    abstract fun observeBacklinks(noteId: String): Flow<List<BlockBacklink>>
}
