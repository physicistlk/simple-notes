package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.CanvasUiState
import personal.notes.touch.ui.canvas.DEFAULT_ERASER_WIDTH
import personal.notes.touch.ui.canvas.TouchNotesEraserPresets
import personal.notes.touch.ui.canvas.TouchNotesWidthPresets
import personal.notes.touch.ui.canvas.activeStrokeWidth
import personal.notes.touch.ui.canvas.defaultWidthForTool
import personal.notes.touch.ui.canvas.withStrokeWidth

/**
 * The size chips are shared by every tool. Picking a size while the eraser is
 * selected used to update the pen width, so the eraser never changed size.
 */
class StrokeWidthRoutingTest {

    @Test
    fun penSizeUpdatesPen() {
        val state = CanvasUiState(selectedTool = CanvasTool.PEN).withStrokeWidth(12f)
        assertEquals(12f, state.strokeWidth, 0.001f)
        assertEquals(36f, state.eraserWidth, 0.001f)
    }

    @Test
    fun eraserSizeUpdatesEraser() {
        val state = CanvasUiState(selectedTool = CanvasTool.ERASER).withStrokeWidth(56f)
        assertEquals(56f, state.eraserWidth, 0.001f)
        assertEquals(6f, state.strokeWidth, 0.001f)
    }

    @Test
    fun highlighterAndShapesUseThePenSize() {
        for (tool in listOf(CanvasTool.HIGHLIGHTER, CanvasTool.SHAPE_RECT, CanvasTool.LASSO)) {
            val state = CanvasUiState(selectedTool = tool).withStrokeWidth(2.5f)
            assertEquals(2.5f, state.strokeWidth, 0.001f)
            assertEquals(36f, state.eraserWidth, 0.001f)
        }
    }

    @Test
    fun activeWidthFollowsTheTool() {
        assertEquals(
            6f,
            CanvasUiState(selectedTool = CanvasTool.PEN).activeStrokeWidth(),
            0.001f
        )
        assertEquals(
            36f,
            CanvasUiState(selectedTool = CanvasTool.ERASER).activeStrokeWidth(),
            0.001f
        )
    }

    @Test
    fun defaultEraserSizeIsOneOfTheChips() {
        // Otherwise the eraser would show no selected chip on a fresh install.
        assertEquals(true, TouchNotesEraserPresets.any { it.first == CanvasUiState().eraserWidth })
        assertEquals(true, TouchNotesWidthPresets.any { it.first == CanvasUiState().strokeWidth })
    }

    @Test
    fun thePenResetsToTheDefaultOfTheSettings() {
        // The size in use may be anything; the reset button goes back to the
        // pen default the settings hold.
        val state = CanvasUiState(
            selectedTool = CanvasTool.PEN,
            strokeWidth = 24f,
            defaultStrokeWidth = 6f
        )
        assertEquals(6f, state.defaultWidthForTool(), 0.001f)
    }

    @Test
    fun theEraserResetsToItsOwnDefault() {
        val state = CanvasUiState(
            selectedTool = CanvasTool.ERASER,
            eraserWidth = 80f,
            defaultEraserWidth = DEFAULT_ERASER_WIDTH
        )
        assertEquals(DEFAULT_ERASER_WIDTH, state.defaultWidthForTool(), 0.001f)
    }
}
