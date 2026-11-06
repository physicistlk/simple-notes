package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.TemplateEntity

@Dao
interface TemplateDao {

    /** Daily template first, then alphabetical — the order the sheet shows. */
    @Query("SELECT * FROM templates ORDER BY isDaily DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TemplateEntity?

    /** The template behind "Today"; the newest one wins if legacy data has two. */
    @Query("SELECT * FROM templates WHERE isDaily = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getDaily(): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: TemplateEntity)

    @Query("UPDATE templates SET isDaily = 0")
    suspend fun clearDailyFlags()

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int
}
