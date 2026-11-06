package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.entity.SettingsEntity
import personal.notes.touch.ui.canvas.CanvasUiState
import personal.notes.touch.util.PdfRenderPolicy

class PageTurningSettingsTest {

    @Test
    fun defaultPageTurningSettingsAreContinuousAndVertical() {
        val defaultSettings = SettingsEntity()
        assertEquals(0, defaultSettings.pageTurningDirection) // 0 = Vertical
        assertEquals(0, defaultSettings.pageTurningMode) // 0 = Continuous

        val defaultUiState = CanvasUiState()
        assertEquals(0, defaultUiState.pageTurningDirection)
        assertEquals(0, defaultUiState.pageTurningMode)
    }

    @Test
    fun pageTurningDirectionModes() {
        val vertState = CanvasUiState(pageTurningDirection = 0)
        val horizState = CanvasUiState(pageTurningDirection = 1)
        assertEquals(0, vertState.pageTurningDirection)
        assertEquals(1, horizState.pageTurningDirection)

        val continuousState = CanvasUiState(pageTurningMode = 0)
        val singleState = CanvasUiState(pageTurningMode = 1)
        assertEquals(0, continuousState.pageTurningMode)
        assertEquals(1, singleState.pageTurningMode)
    }

    @Test
    fun pdfRenderPolicyHighResBudget() {
        assertEquals(18_000_000L, PdfRenderPolicy.MAX_PIXELS)
        assertEquals(1.15f, PdfRenderPolicy.HYSTERESIS, 1e-4f)
        assertEquals(4096, PdfRenderPolicy.MAX_DIMENSION)

        // Verify that 20% zoom triggers a sharp re-render with hysteresis 1.15
        assertTrue(PdfRenderPolicy.shouldRender(neededPxPerPt = 1.25f, currentPxPerPt = 1.0f))
        assertFalse(PdfRenderPolicy.shouldRender(neededPxPerPt = 1.10f, currentPxPerPt = 1.0f))
    }
}
