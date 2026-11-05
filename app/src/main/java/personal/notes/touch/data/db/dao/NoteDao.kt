package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.NoteEntity

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

    @Query("DELETE FROM notes WHERE deleted = 1")
    suspend fun clearRecycled()

    /** Notes the knowledge index derives from (tags, wiki links). */
    @Query("SELECT * FROM notes WHERE deleted = 0 AND content IS NOT NULL")
    suspend fun getNotesWithContent(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0")
    suspend fun count(): Int
}
