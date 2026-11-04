package personal.notes.touch.ui.canvas

import java.util.UUID

data class TocEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val page: Int,
    val parentId: String? = null,
    val expanded: Boolean = true
)