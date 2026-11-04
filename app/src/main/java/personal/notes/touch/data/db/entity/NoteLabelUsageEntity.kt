package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_label_usage")
data class NoteLabelUsageEntity(
    @PrimaryKey(autoGenerate = true) val nAutoID: Long = 0,
    val id: String,
    val notePath: String,
    val labelId: String
)
