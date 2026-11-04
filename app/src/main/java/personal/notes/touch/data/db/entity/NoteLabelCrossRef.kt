package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "note_label_cross_ref",
    primaryKeys = ["noteId", "labelId"],
    indices = [Index(value = ["labelId"])]
)
data class NoteLabelCrossRef(
    val noteId: String,
    val labelId: String
)
