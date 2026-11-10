package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.NoteRefEntity

/**
 * One outgoing reference, resolved against the live note titles.
 * [noteId] is null when no note currently carries that title — the reference
 * sheet shows those as "not found" instead of hiding them.
 */
data class RefTarget(
    val targetKey: String,
    val targetTitle: String,
    val noteId: String?,
    val resolvedTitle: String?
)

/**
 * Wiki-link index (`note_refs`, schema 7): who links to whom, by title key.
 *
 * Reading is a join with `notes`, so a reference resolves to whatever note
 * currently carries that title and disappears from the index only when the
 * source note is deleted. Writes replace one note's rows atomically.
 */
@Dao
abstract class NoteRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(refs: List<NoteRefEntity>)

    @Query("SELECT * FROM note_refs WHERE noteId = :noteId")
    abstract suspend fun getForNote(noteId: String): List<NoteRefEntity>

    @Query("DELETE FROM note_refs WHERE noteId = :noteId")
    abstract suspend fun deleteForNote(noteId: String)

    @Query("DELETE FROM note_refs WHERE noteId IN (SELECT id FROM notes WHERE deleted = 1)")
    abstract suspend fun deleteForRecycledNotes(): Int

    @Query("DELETE FROM note_refs WHERE noteId NOT IN (SELECT id FROM notes)")
    abstract suspend fun deleteOrphans(): Int

    /** Notes that contain `[[…]]` pointing at [targetKey] (backlinks). */
    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_refs r ON r.noteId = n.id
        WHERE r.targetKey = :targetKey AND n.deleted = 0 AND n.id != :selfId
        ORDER BY n.updatedAt DESC
        """
    )
    abstract fun observeBacklinks(targetKey: String, selfId: String): Flow<List<NoteEntity>>

    /** References written in [noteId], with their target resolved when it exists. */
    @Query(
        """
        SELECT r.targetKey AS targetKey,
               r.targetTitle AS targetTitle,
               n.id AS noteId,
               n.title AS resolvedTitle
        FROM note_refs r
        LEFT JOIN notes n ON (
                lower(trim(n.title)) = r.targetKey
                OR lower(replace(replace(trim(n.title), '  ', ' '), '  ', ' ')) = r.targetKey
            ) AND n.deleted = 0
        WHERE r.noteId = :noteId
        ORDER BY r.targetTitle COLLATE NOCASE ASC
        """
    )
    abstract fun observeOutgoing(noteId: String): Flow<List<RefTarget>>

    @Transaction
    open suspend fun replaceRefs(noteId: String, refs: List<NoteRefEntity>) {
        deleteForNote(noteId)
        if (refs.isNotEmpty()) insertAll(refs)
    }
}
