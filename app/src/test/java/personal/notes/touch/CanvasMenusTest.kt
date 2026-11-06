package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasMenus
import personal.notes.touch.ui.canvas.CanvasUiState

/**
 * Canvas touch-to-dismiss policy: a touch on the page closes the slide-over
 * menus, leaves the pen-case dock and the current tool alone, and does not
 * emit state when nothing was open.
 */
class CanvasMenusTest {

    @Test
    fun nothingOpenMeansNoStateChange() {
        assertNull(CanvasMenus.dismiss(CanvasUiState()))
    }

    @Test
    fun toolsPanelCloses() {
        val state = CanvasUiState(isToolsPanelOpen = true)
        val dismissed = requireNotNull(CanvasMenus.dismiss(state))
        assertFalse(dismissed.isToolsPanelOpen)
        assertNull(CanvasMenus.dismiss(dismissed))
    }

    @Test
    fun pageDrawerCloses() {
        val dismissed = requireNotNull(CanvasMenus.dismiss(CanvasUiState(isThumbnailDrawerOpen = true)))
        assertFalse(dismissed.isThumbnailDrawerOpen)
    }

    @Test
    fun bothSlideOversAndPageManagerCloseTogether() {
        val state = CanvasUiState(
            isToolsPanelOpen = true,
            isThumbnailDrawerOpen = true,
            isPageManagerOpen = true
        )
        val dismissed = requireNotNull(CanvasMenus.dismiss(state))
        assertFalse(dismissed.isToolsPanelOpen)
        assertFalse(dismissed.isThumbnailDrawerOpen)
        assertFalse(dismissed.isPageManagerOpen)
    }

    @Test
    fun dockAndCurrentToolAreUntouched() {
        val state = CanvasUiState(
            isToolsPanelOpen = true,
            showPencase = true,
            dockHorizontal = true,
            strokeWidth = 9f,
            stylusOnly = true
        )
        val dismissed = requireNotNull(CanvasMenus.dismiss(state))
        assertTrue(dismissed.showPencase)
        assertTrue(dismissed.dockHorizontal)
        assertTrue(dismissed.stylusOnly)
        assertEquals(9f, dismissed.strokeWidth, 0f)
    }
}
