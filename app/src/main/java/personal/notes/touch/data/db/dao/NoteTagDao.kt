package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.NoteTagEntity

/** `#tag#` counts for the library tag bar (deleted notes excluded). */
data class TagCount(
    val tag: String,
    val displayName: String,
    val count: Int
)

/**
 * Derived tag index (`note_tags`, schema 7).
 *
 * Rows are a projection of `notes.content`: the repository rewrites them on
 * every save and re-indexes existing notes once at library start, so nothing
 * here is ever edited by the user directly.
 */
@Dao
abstract class NoteTagDao {

    @Query("SELECT * FROM note_tags")
    abstract fun observeAll(): Flow<List<NoteTagEntity>>

    @Query("SELECT * FROM note_tags WHERE noteId = :noteId")
    abstract suspend fun getForNote(noteId: String): List<NoteTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(tags: List<NoteTagEntity>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    abstract suspend fun deleteForNote(noteId: String)

    @Query("DELETE FROM note_tags WHERE noteId IN (SELECT id FROM notes WHERE deleted = 1)")
    abstract suspend fun deleteForRecycledNotes(): Int

    @Query("DELETE FROM note_tags WHERE noteId NOT IN (SELECT id FROM notes)")
    abstract suspend fun deleteOrphans(): Int

    /**
     * Tag bar data: one row per tag with the number of live notes using it.
     * The MIN(displayName) keeps the alphabetically-first spelling stable so
     * the chip label does not flip between `Work` and `work`.
     */
    @Query(
        """
        SELECT t.tag AS tag, MIN(t.displayName) AS displayName, COUNT(*) AS count
        FROM note_tags t INNER JOIN notes n ON n.id = t.noteId
        WHERE n.deleted = 0
        GROUP BY t.tag
        ORDER BY count DESC, t.tag ASC
        """
    )
    abstract fun observeTagCounts(): Flow<List<TagCount>>

    @Transaction
    open suspend fun replaceTags(noteId: String, tags: List<NoteTagEntity>) {
        deleteForNote(noteId)
        if (tags.isNotEmpty()) insertAll(tags)
    }
}
