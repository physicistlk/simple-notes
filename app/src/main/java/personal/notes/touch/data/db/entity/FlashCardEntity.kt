package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flash_cards",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["sourceBlockId"]),
        Index(value = ["sourceNoteId"])
    ]
)
data class FlashCardEntity(
    @PrimaryKey(autoGenerate = true) val cAutoID: Long = 0,
    val id: String,
    val cName: String,
    val cSync: Int = 0,
    val createTime: String,
    val cQuestion: String? = null,
    val cAns: String? = null,
    val cTips: String? = null,
    val cNum: Int = 0,
    val cRDate: String = "",
    val nid: String,
    val pid: String? = null,
    val isFavor: Int = 0,
    val reviewStatus: Int = 0,
    val card_usn: Int = 0,
    val easeFactor: Double = 2.5,
    val card_last_usn: Int = 0,
    val note_usn: Int = 0,
    val note_last_usn: Int = 0,
    /**
     * Provenance for cards minted from an editor block (schema 10). Null for
     * cards created by hand in the Flashcards screen. The block id is the
     * note_blocks UUID, so the link survives edits and reordering; the note id
     * is kept alongside it because looking up the note from a deleted block is
     * otherwise impossible.
     */
    val sourceNoteId: String? = null,
    val sourceBlockId: String? = null
)
