package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A reusable text-note template (schema 9).
 *
 * `content` is markdown in the editor's own grammar and may contain variables
 * (`{{date}}`, `{{title}}`, ...) expanded by `text/NoteTemplates` at the moment
 * a note is created — templates are stored with the placeholders intact, so a
 * template is never stale.
 *
 * [isDaily] marks the template used by the library's "Today" action. At most
 * one row carries it; the repository clears the flag on the others when a new
 * one is set.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val content: String = "",
    val isDaily: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
