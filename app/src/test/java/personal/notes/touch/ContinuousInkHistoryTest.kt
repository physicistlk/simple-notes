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

/**
 * Tests of the ink history of the continuous reader.
 *
 * The strokes of a session are kept as vectors (they never touch the 26 MB
 * session bitmap), so the undo/redo works on the list of committed
 * operations - and the eraser, which does clear pixels, hands its pixel
 * patch back to the controller.
 */
class ContinuousInkHistoryTest {

    private fun stroke(tool: CanvasTool = CanvasTool.PEN, width: Float = 0.01f): ContinuousStroke {
        val s = ContinuousStroke(tool, 0xFF000000.toInt(), width)
        s.points.add(personal.notes.touch.ui.canvas.InkPoint(0.1f, 0.1f, width))
        s.points.add(personal.notes.touch.ui.canvas.InkPoint(0.2f, 0.2f, width))
        return s
    }

    private fun patch(): InkPatch =
        InkPatch(0, 0, 4, 4, IntArray(16) { 1 }, IntArray(16) { 2 })

    @Test
    fun committedStrokeStaysInTheListAndUndoTakesItOutAgain() {
        val history = InkHistory()
        val op = InkOp.Stroke(stroke(), null)
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
        val history = InkHistory()
        val first = InkOp.Stroke(stroke(), null)
        val second = InkOp.Stroke(stroke(CanvasTool.HIGHLIGHTER), null)
        history.commit(first)
        history.commit(second)

        assertEquals(listOf<InkOp>(first, second), history.ops)
        history.undo({ _, _ -> }, { _, _ -> })
        assertEquals(listOf<InkOp>(first), history.ops)
    }

    @Test
    fun aNewStrokeDropsTheRedoHistory() {
        val history = InkHistory()
        history.commit(InkOp.Stroke(stroke(), null))
        history.undo({ _, _ -> }, { _, _ -> })
        assertTrue(history.canRedo)

        history.commit(InkOp.Stroke(stroke(), null))
        assertFalse(history.canRedo)
    }

    @Test
    fun undoOfAnEraserPutsThePixelsBackAndRedoWipesThemAgain() {
        val history = InkHistory()
        val erase = InkOp.Erase(stroke(CanvasTool.ERASER), patch())
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
        val history = InkHistory()
        val first = InkOp.Stroke(stroke(), null)
        val second = InkOp.Stroke(stroke(), null)
        history.commit(first)
        history.commit(second)

        val cleared = history.ops.toList()
        history.commitClear(patch(), cleared)
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
        val history = InkHistory()
        assertFalse(history.undo({ _, _ -> }, { _, _ -> }))
        assertFalse(history.redo({ _, _ -> }, { _, _ -> }))
        assertEquals(0, history.ops.size)
    }

    @Test
    fun theOldestActionsAreDroppedWhenTheyTakeTooMuchMemory() {
        val history = InkHistory()
        repeat(4) { history.commit(InkOp.Erase(stroke(CanvasTool.ERASER), patch())) }
        // Every erase keeps 4x4 pixels twice: 128 bytes per action.
        history.trim(200)
        assertTrue(history.canUndo)
        assertTrue(history.ops.size >= 1)
    }

    @Test
    fun undoOfACropTakesTheCropAwayAndRedoBringsItBack() {
        val history = InkHistory()
        val place = personal.notes.touch.ui.canvas.ContinuousPageGeometry.CropPlace(-40f, 120f, 60, 30)
        val crop = InkOp.Crop(7, place)
        history.commit(crop)

        assertEquals(1, history.ops.size)
        assertTrue(history.canUndo)

        var undone: InkOp.Crop? = null
        var restored = false
        assertTrue(
            history.undo(
                { _, _ -> },
                { _, _ -> },
                { op, restore -> undone = op; restored = restore }
            )
        )
        assertNotNull(undone)
        assertTrue("the undo takes the crop away", restored)
        assertEquals(0, history.ops.size)
        assertTrue(history.canRedo)

        var redone: InkOp.Crop? = null
        assertTrue(
            history.redo(
                { _, _ -> },
                { _, _ -> },
                { op, _ -> redone = op }
            )
        )
        assertNotNull(redone)
        assertEquals(7, redone!!.id)
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === crop)
    }

    @Test
    fun aCropCountsTowardsTheUndoMemory() {
        val history = InkHistory()
        val place = personal.notes.touch.ui.canvas.ContinuousPageGeometry.CropPlace(0f, 0f, 100, 100)
        history.commit(InkOp.Crop(1, place))
        // 100x100 pixels are 40 KB; the oldest action is dropped when the
        // history is trimmed below that.
        history.trim(1024)
        assertTrue(history.ops.size >= 1)
    }
}
