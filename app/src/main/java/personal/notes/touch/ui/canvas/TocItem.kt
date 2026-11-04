package personal.notes.touch.ui.canvas

import java.util.UUID

enum class DrawerTab {
    THUMBNAILS,
    BOOKMARKS,
    OUTLINE
}

data class TocItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val pageIndex: Int,
    val level: Int = 0
)
