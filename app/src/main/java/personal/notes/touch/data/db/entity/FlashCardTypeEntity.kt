package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flash_cards_types")
data class FlashCardTypeEntity(
    @PrimaryKey(autoGenerate = true) val lAutoID: Long = 0,
    val tName: String,
    val sync: Int = 0,
    val labelId: String,
    val usn: Int = 0,
    val last_usn: Int = 0
)
