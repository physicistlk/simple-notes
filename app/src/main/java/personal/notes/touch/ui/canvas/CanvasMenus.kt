package personal.notes.touch.ui.canvas

/**
 * Which canvas overlays close themselves when the user goes back to the page.
 *
 * The page drawer (left), the tools panel (right) and the page manager are
 * *menus*: once a setting is changed or a page is picked, the user's next move
 * is to write again. Closing them on the first touch of the page removes the
 * extra "close" tap that otherwise sits between changing something and drawing.
 *
 * Deliberately excluded: the pen-case dock (the tool palette must stay
 * reachable), the image-layer opacity bar and the PDF text action bar (both
 * belong to an active selection) and real dialogs (they are modal).
 */
object CanvasMenus {

    /** State with every dismissible menu closed, or null when none was open. */
    fun dismiss(state: CanvasUiState): CanvasUiState? {
        val anyOpen = state.isToolsPanelOpen ||
            state.isThumbnailDrawerOpen ||
            state.isPageManagerOpen
        if (!anyOpen) return null
        return state.copy(
            isToolsPanelOpen = false,
            isThumbnailDrawerOpen = false,
            isPageManagerOpen = false
        )
    }
}
