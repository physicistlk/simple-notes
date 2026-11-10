package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.NoteEntity

/** Live notes filed directly in one folder (null = unfiled), for the tree. */
data class FolderCount(val folderId: String?, val count: Int)

/** One note-to-note edge derived from `[[wiki links]]`. */
data class NoteGraphEdge(
    val sourceNoteId: String,
    val targetNoteId: String
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND folderId = :folderId ORDER BY updatedAt DESC")
    fun getByFolder(folderId: String?): Flow<List<NoteEntity>>

    /** Notes in any of [folderIds] — one notebook subtree in a single query. */
    @Query(
        "SELECT * FROM notes WHERE deleted = 0 AND folderId IN (:folderIds) " +
            "ORDER BY updatedAt DESC"
    )
    fun getByFolders(folderIds: List<String>): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND favorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted = 1 ORDER BY updatedAt DESC")
    fun getRecycled(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE notes SET deleted = :deleted, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDeleted(id: String, deleted: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?, updatedAt: Long = System.currentTimeMillis())

    /** Bulk move when a notebook is deleted; notes are never deleted with it. */
    @Query("UPDATE notes SET folderId = :to, updatedAt = :updatedAt WHERE folderId = :from")
    suspend fun reassignFolder(from: String, to: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE deleted = 1")
    suspend fun clearRecycled()

    /** The ids of the notes in the recycle bin (their files are deleted with them). */
    @Query("SELECT id FROM notes WHERE deleted = 1")
    suspend fun getRecycledIds(): List<String>

    /** Notes the knowledge index derives from (tags, wiki links). */
    @Query("SELECT * FROM notes WHERE deleted = 0 AND content IS NOT NULL")
    suspend fun getNotesWithContent(): List<NoteEntity>

    /** Newest live note with this exact title (daily notes are titled by date). */
    @Query("SELECT * FROM notes WHERE deleted = 0 AND title = :title ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getByTitle(title: String): NoteEntity?

    /**
     * One row per folder that holds notes, plus a null-folder row. The
     * notebook tree aggregates descendants itself, so this stays a cheap
     * GROUP BY rather than a recursive query.
     */
    @Query(
        "SELECT folderId AS folderId, COUNT(*) AS count FROM notes " +
            "WHERE deleted = 0 GROUP BY folderId"
    )
    fun observeFolderCounts(): Flow<List<FolderCount>>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllLive(): List<NoteEntity>

    /** Resolved `[[Title]]` links, for the knowledge graph. */
    @Query(
        """
        SELECT r.noteId AS sourceNoteId, n.id AS targetNoteId
        FROM note_refs r
        INNER JOIN notes n ON lower(n.title) = r.targetKey AND n.deleted = 0
        INNER JOIN notes s ON s.id = r.noteId AND s.deleted = 0
        """
    )
    suspend fun getWikiGraphEdges(): List<NoteGraphEdge>
}
