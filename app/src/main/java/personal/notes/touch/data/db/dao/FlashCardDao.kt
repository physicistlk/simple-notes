package personal.notes.touch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import personal.notes.touch.data.db.entity.FlashCardEntity
import personal.notes.touch.data.db.entity.FlashCardTagEntity
import personal.notes.touch.data.db.entity.FlashCardTypeEntity

@Dao
interface FlashCardDao {
    @Query("SELECT * FROM flash_cards ORDER BY cAutoID DESC")
    fun getAllCards(): Flow<List<FlashCardEntity>>

    @Query("SELECT * FROM flash_cards WHERE nid = :noteId")
    fun getCardsByNote(noteId: String): Flow<List<FlashCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: FlashCardEntity)

    @Query("DELETE FROM flash_cards WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("SELECT * FROM flash_cards_types ORDER BY lAutoID ASC")
    fun getAllTypes(): Flow<List<FlashCardTypeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertType(type: FlashCardTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: FlashCardTagEntity)
}
