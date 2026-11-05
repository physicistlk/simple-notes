package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.CanvasUiState

class DrawingToolsPressureTest {

    @Test
    fun testFirstThreeToolsEnumOrder() {
        // Tool 1: Pen tool (pressure sensitive fountain pen)
        // Tool 2: Ball pen (not pressure sensitive fineliner/pen)
        // Tool 3: Highlighter (not pressure sensitive)
        val tools = CanvasTool.values()
        assertEquals(CanvasTool.FOUNTAIN_PEN, tools[0])
        assertEquals(CanvasTool.PEN, tools[1])
        assertEquals(CanvasTool.HIGHLIGHTER, tools[2])
    }

    @Test
    fun testCanvasUiStateToolSelection() {
        val defaultState = CanvasUiState()
        assertEquals(CanvasTool.PEN, defaultState.selectedTool)

        val fountainState = defaultState.copy(selectedTool = CanvasTool.FOUNTAIN_PEN)
        assertEquals(CanvasTool.FOUNTAIN_PEN, fountainState.selectedTool)

        val highlighterState = defaultState.copy(selectedTool = CanvasTool.HIGHLIGHTER)
        assertEquals(CanvasTool.HIGHLIGHTER, highlighterState.selectedTool)
    }
}
