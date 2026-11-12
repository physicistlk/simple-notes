package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPatch
import personal.notes.touch.ui.canvas.OpEntry
import personal.notes.touch.ui.canvas.PageLayer
import personal.notes.touch.ui.canvas.PageLayerStack

/**
 * Tests of the ink history of the continuous reader.
 *
 * The strokes of a session are kept as vectors (they never touch the 26 MB
 * session bitmap), so the undo/redo works on the list of committed
 * operations - and the eraser, which does clear pixels, hands its pixel
 * patch back to the controller.
 *
 * Every operation belongs to a layer of the page: the undo of a deleted or
 * a merged layer brings the layer back.
 */
class ContinuousInkHistoryTest {

    private fun layer(index: Int = 1): PageLayer = PageLayer(index, "Layer$index", true)

    private fun stroke(tool: CanvasTool = CanvasTool.PEN, width: Float = 0.01f): ContinuousStroke {
        val s = ContinuousStroke(tool, 0xFF000000.toInt(), width)
        s.points.add(personal.notes.touch.ui.canvas.InkPoint(0.1f, 0.1f, width))
        s.points.add(personal.notes.touch.ui.canvas.InkPoint(0.2f, 0.2f, width))
        return s
    }

    /**
     * A patch of one tile: the tile (0, 0), a rectangle of 4 x 4 pixels
     * inside it and the pixels before and after the wipe.
     */
    private fun patch(): InkPatch =
        InkPatch(0, 0, 0, 0, 4, 4, IntArray(16) { 1 }, IntArray(16) { 2 })

    @Test
    fun committedStrokeStaysInTheListAndUndoTakesItOutAgain() {
        val history = InkHistory(PageLayerStack.base())
        val op = InkOp.Stroke(stroke(), null, layer())
        history.commit(op)

        assertEquals(1, history.ops.size)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(0, history.ops.size)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        assertTrue(history.redo({ _, _ -> }, { _, _ -> }))
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === op)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun strokesKeepTheirOrder() {
        val history = InkHistory(PageLayerStack.base())
        val first = InkOp.Stroke(stroke(), null, layer())
        val second = InkOp.Stroke(stroke(CanvasTool.HIGHLIGHTER), null, layer())
        history.commit(first)
        history.commit(second)

        assertEquals(listOf<InkOp>(first, second), history.ops)
        history.undo({ _, _ -> }, { _, _ -> })
        assertEquals(listOf<InkOp>(first), history.ops)
    }

    @Test
    fun aNewStrokeDropsTheRedoHistory() {
        val history = InkHistory(PageLayerStack.base())
        history.commit(InkOp.Stroke(stroke(), null, layer()))
        history.undo({ _, _ -> }, { _, _ -> })
        assertTrue(history.canRedo)

        history.commit(InkOp.Stroke(stroke(), null, layer()))
        assertFalse(history.canRedo)
    }

    @Test
    fun undoOfAnEraserPutsThePixelsBackAndRedoWipesThemAgain() {
        val history = InkHistory(PageLayerStack.base())
        val erase = InkOp.Erase(stroke(CanvasTool.ERASER), listOf(patch()), layer())
        history.commit(erase)

        var restored = false
        var wiped = false
        assertTrue(history.undo({ _, restore -> restored = restore }, { _, _ -> }))
        assertTrue(restored)
        assertEquals(0, history.ops.size)

        assertTrue(history.redo({ _, restore -> wiped = !restore }, { _, _ -> }))
        assertTrue(wiped)
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === erase)
    }

    @Test
    fun undoOfAClearBringsTheStrokesAndTheInkBack() {
        val history = InkHistory(PageLayerStack.base())
        val first = InkOp.Stroke(stroke(), null, layer())
        val second = InkOp.Stroke(stroke(), null, layer())
        history.commit(first)
        history.commit(second)

        val cleared = history.ops.mapIndexed { index, op -> OpEntry(index, op) }
        history.commitClear(listOf(patch()), cleared)
        assertEquals(0, history.ops.size)

        var restored = false
        assertTrue(history.undo({ _, _ -> }, { _, restore -> restored = restore }))
        assertTrue(restored)
        assertEquals(2, history.ops.size)
        assertTrue(history.ops[0] === first)
        assertTrue(history.ops[1] === second)

        assertTrue(history.redo({ _, _ -> }, { _, restore -> restored = restore }))
        assertFalse(restored)
        assertEquals(0, history.ops.size)
    }

    @Test
    fun undoOnAnEmptyHistoryDoesNothing() {
        val history = InkHistory(PageLayerStack.base())
        assertFalse(history.undo({ _, _ -> }, { _, _ -> }))
        assertFalse(history.redo({ _, _ -> }, { _, _ -> }))
        assertEquals(0, history.ops.size)
    }

    @Test
    fun theOldestActionsAreDroppedWhenTheyTakeTooMuchMemory() {
        val history = InkHistory(PageLayerStack.base())
        repeat(4) { history.commit(InkOp.Erase(stroke(CanvasTool.ERASER), listOf(patch()), layer())) }
        // Every erase keeps 4x4 pixels twice: 128 bytes per action.
        history.trim(200)
        assertTrue(history.canUndo)
        assertTrue(history.ops.size >= 1)
    }

    @Test
    fun aDeletedLayerTakesItsStrokesWithItAndComesBackWithTheUndo() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val second = stack.add() ?: return
        val stroke = InkOp.Stroke(stroke(), null, second)
        history.commit(stroke)

        history.commitRemoveLayer(second, merged = false)
        assertEquals(1, stack.size)
        assertEquals(0, history.ops.size)

        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(2, stack.size)
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === stroke)
        assertTrue(history.ops[0].layer === second)
    }

    @Test
    fun aMergedLayerGoesIntoTheBaseLayerAndComesBackWithTheUndo() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val base = stack.layers[0]
        val second = stack.add() ?: return
        val stroke = InkOp.Stroke(stroke(), null, second)
        history.commit(stroke)

        history.commitRemoveLayer(second, merged = true)
        // The layer is gone and its stroke is part of the base layer now.
        assertEquals(1, stack.size)
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0].layer === base)

        // The undo brings the layer back with its stroke.
        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(2, stack.size)
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0].layer === second)
    }
}
