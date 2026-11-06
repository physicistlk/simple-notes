package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import personal.notes.touch.data.db.entity.NoteBlockEntity

/**
 * Block rows for the text editor (`note_blocks`, schema version 6).
 *
 * Writes always replace the whole row set of one note: block order and count
 * change together with the text, and a stale row would otherwise survive as
 * an orphan. [replaceBlocks] does both statements in one transaction.
 */
@Dao
abstract class NoteBlockDao {

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY blockIndex ASC")
    abstract suspend fun getBlocksForNote(noteId: String): List<NoteBlockEntity>

    @Query("SELECT COUNT(*) FROM note_blocks WHERE noteId = :noteId")
    abstract suspend fun countForNote(noteId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(blocks: List<NoteBlockEntity>)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    abstract suspend fun deleteForNote(noteId: String)

    /** Drops blocks of notes that sit in the recycle bin (called while emptying it). */
    @Query("DELETE FROM note_blocks WHERE noteId IN (SELECT id FROM notes WHERE deleted = 1)")
    abstract suspend fun deleteForRecycledNotes(): Int

    /**
     * `((` autocomplete: blocks whose text matches [query], newest notes first.
     * The query is escaped by the repository; ESCAPE keeps `%`/`_` literal.
     */
    @Query(
        """
        SELECT b.id AS blockId,
               b.noteId AS noteId,
               b.text AS text,
               n.title AS noteTitle,
               b.blockIndex AS blockIndex
        FROM note_blocks b
        INNER JOIN notes n ON n.id = b.noteId
        WHERE n.deleted = 0
          AND b.text != ''
          AND b.id != :excludeBlockId
          AND b.text LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY n.updatedAt DESC, b.blockIndex ASC
        LIMIT :limit
        """
    )
    abstract suspend fun searchBlocks(
        query: String,
        excludeBlockId: String,
        limit: Int
    ): List<BlockCandidate>

    /** Every block of a live note, for the one-time reference reindex. */
    @Query(
        """
        SELECT b.* FROM note_blocks b
        INNER JOIN notes n ON n.id = b.noteId AND n.deleted = 0
        ORDER BY b.noteId, b.blockIndex
        """
    )
    abstract suspend fun getAllForLiveNotes(): List<NoteBlockEntity>

    /** Atomic rewrite: an empty [blocks] list just clears the note's rows. */
    @Transaction
    open suspend fun replaceBlocks(noteId: String, blocks: List<NoteBlockEntity>) {
        deleteForNote(noteId)
        if (blocks.isNotEmpty()) insertAll(blocks)
    }
}
