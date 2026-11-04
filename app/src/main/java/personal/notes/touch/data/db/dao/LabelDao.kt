package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.LabelEntity
import personal.notes.touch.data.db.entity.NoteLabelCrossRef

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY name ASC")
    fun getAll(): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLabelToNote(crossRef: NoteLabelCrossRef)

    @Query("DELETE FROM note_label_cross_ref WHERE noteId = :noteId AND labelId = :labelId")
    suspend fun removeLabelFromNote(noteId: String, labelId: String)

    @Query("""
        SELECT l.* FROM labels l
        INNER JOIN note_label_cross_ref nl ON l.id = nl.labelId
        WHERE nl.noteId = :noteId
    """)
    fun getLabelsForNote(noteId: String): Flow<List<LabelEntity>>
}
