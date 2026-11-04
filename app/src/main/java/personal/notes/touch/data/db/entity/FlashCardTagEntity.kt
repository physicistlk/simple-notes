package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flash_cards_tags")
data class FlashCardTagEntity(
    @PrimaryKey(autoGenerate = true) val tAutoID: Long = 0,
    val id: String,
    val cardId: String,
    val labelId: String
)
