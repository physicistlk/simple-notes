package personal.notes.touch.ui.nav

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object NoteList : Screen("notes")
    object Canvas : Screen("canvas/{noteId}") {
        fun createRoute(noteId: String) = "canvas/$noteId"
    }
    object Editor : Screen("editor/{noteId}") {
        fun createRoute(noteId: String) = "editor/$noteId"
    }
    /** Two text notes side by side (multi-document workspace). */
    object Split : Screen("split/{leftId}/{rightId}") {
        fun createRoute(leftId: String, rightId: String) = "split/$leftId/$rightId"
    }
    object Settings : Screen("settings")
    object Flashcards : Screen("flashcards")
}
