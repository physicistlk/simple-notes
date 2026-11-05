package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.PageEntity

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY `index` ASC")
    fun getPagesForNote(noteId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY `index` ASC")
    suspend fun getPagesForNoteSync(noteId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pages WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    /**
     * F-10 vector PDF import: records the source document + page index for
     * one page row (see [PageEntity.pdfSourcePath]).
     */
    @Query(
        "UPDATE pages SET pdfSourcePath = :pdfSourcePath, pdfPageIndex = :pdfPageIndex, " +
            "updatedAt = :updatedAt WHERE noteId = :noteId AND `index` = :index"
    )
    suspend fun setPdfSource(
        noteId: String,
        index: Int,
        pdfSourcePath: String,
        pdfPageIndex: Int,
        updatedAt: Long
    )
}
