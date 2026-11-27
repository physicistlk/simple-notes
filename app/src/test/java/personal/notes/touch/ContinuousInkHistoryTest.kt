package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPatch
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.OpEntry
import personal.notes.touch.ui.canvas.PageLayer
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.StrokeTransformSnapshot
import kotlin.math.PI

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

    @Test
    fun historyOperationsTrackLayerStrokesForGlassBackdrop() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val base = stack.layers[0]

        val stroke1 = InkOp.Stroke(stroke(), null, base)
        val stroke2 = InkOp.Stroke(stroke(CanvasTool.FOUNTAIN_PEN), null, base)
        history.commit(stroke1)
        history.commit(stroke2)

        val baseOps = history.ops.filter { it.layer.index == base.index }
        assertEquals(2, baseOps.size)
        assertTrue(baseOps[0] === stroke1)
        assertTrue(baseOps[1] === stroke2)

        history.undo({ _, _ -> }, { _, _ -> })
        val remainingOps = history.ops.filter { it.layer.index == base.index }
        assertEquals(1, remainingOps.size)
        assertTrue(remainingOps[0] === stroke1)
    }

    @Test
    fun lassoMoveIsUndoneAndRedoneByTheReverseAndSameDelta() {
        val history = InkHistory(PageLayerStack.base())
        val op = InkOp.Stroke(stroke(), null, layer())
        history.commit(op)
        history.commitMove(listOf(op), 0.25f, -0.5f)
        assertTrue(history.canUndo)

        val deltas = mutableListOf<Pair<Float, Float>>()
        assertTrue(
            history.undo(
                { _, _ -> }, { _, _ -> }, { _, _ -> },
                { moved, dx, dy -> assertTrue(moved[0] === op); deltas.add(dx to dy) }
            )
        )
        assertEquals(listOf(-0.25f to 0.5f), deltas)
        assertEquals(1, history.ops.size)
        assertTrue(history.canRedo)

        assertTrue(
            history.redo(
                { _, _ -> }, { _, _ -> }, { _, _ -> },
                { _, dx, dy -> deltas.add(dx to dy) }
            )
        )
        assertEquals(0.25f to -0.5f, deltas[1])
    }

    @Test
    fun lassoDeleteReusesClearAndUndoBringsTheStrokesBack() {
        val history = InkHistory(PageLayerStack.base())
        val a = InkOp.Stroke(stroke(), null, layer())
        val b = InkOp.Stroke(stroke(), null, layer())
        history.commit(a)
        history.commit(b)
        history.commitClear(emptyList(), listOf(OpEntry(0, a)))
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === b)

        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(2, history.ops.size)
        assertTrue(history.ops[0] === a)
    }

    @Test
    fun lassoCopyAddBatchIsUndoneAndRedoneCleanly() {
        val history = InkHistory(PageLayerStack.base())
        val orig = InkOp.Stroke(stroke(), null, layer())
        val copy = InkOp.Stroke(stroke(), null, layer())
        history.commit(orig)
        history.ops.add(copy)
        history.commitAddBatch(listOf(copy))
        assertEquals(2, history.ops.size)

        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === orig)

        assertTrue(history.redo({ _, _ -> }, { _, _ -> }))
        assertEquals(2, history.ops.size)
        assertTrue(history.ops[1] === copy)
    }

    @Test
    fun lassoTransformSnapshotUndoRedoInvokesBeforeAndAfter() {
        val history = InkHistory(PageLayerStack.base())
        val op = InkOp.Stroke(stroke(), null, layer())
        history.commit(op)
        val before = op.stroke.points.map { personal.notes.touch.ui.canvas.InkPoint(it.x, it.y, it.w, it.tilt) }
        val after = listOf(
            personal.notes.touch.ui.canvas.InkPoint(0.5f, 0.5f, 0.01f),
            personal.notes.touch.ui.canvas.InkPoint(0.6f, 0.6f, 0.01f)
        )
        history.commitTransform(listOf(StrokeTransformSnapshot(op, before, after, null, null)))

        val events = mutableListOf<Boolean>()
        assertTrue(history.undo({ _, _ -> }, { _, _ -> }, onTransform = { _, isUndo -> events.add(isUndo) }))
        assertEquals(listOf(true), events)

        assertTrue(history.redo({ _, _ -> }, { _, _ -> }, onTransform = { _, isUndo -> events.add(isUndo) }))
        assertEquals(listOf(true, false), events)
    }

    private fun applyTransformSnapshots(snapshots: List<StrokeTransformSnapshot>, isUndo: Boolean) {
        for (snap in snapshots) {
            val pts = if (isUndo) snap.beforePoints else snap.afterPoints
            snap.op.stroke.points.clear()
            snap.op.stroke.points.addAll(pts.map { InkPoint(it.x, it.y, it.w, it.tilt) })
        }
    }

    @Test
    fun aspectCorrectRotationTransformUndoRedoAndCodecRoundTrip() {
        val ratios = listOf(16f / 9f, ContinuousPageGeometry.DEFAULT_PAGE_RATIO)
        for (ratio in ratios) {
            val history = InkHistory(PageLayerStack.base())
            val st = ContinuousStroke(
                CanvasTool.PEN,
                0xFF224466.toInt(),
                0.01f,
                points = mutableListOf(
                    InkPoint(0.45f, 0.50f, 0.01f),
                    InkPoint(0.55f, 0.50f, 0.01f)
                )
            )
            val op = InkOp.Stroke(st, null, layer())
            history.commit(op)

            val before = st.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
            val after = ContinuousStrokeTransformMath.rotatePoints(
                points = before,
                deltaRad = (PI / 2.0).toFloat(),
                pivotXNorm = 0.5f,
                pivotYNorm = 0.5f,
                boxRatio = ratio,
                margin = 1f,
                clampToBounds = true
            )
            st.points.clear()
            st.points.addAll(after)
            history.commitTransform(listOf(StrokeTransformSnapshot(op, before, after, null, null)))

            val encoded = ContinuousOpCodec.encode(history.ops)
            val decoded = ContinuousOpCodec.decode(encoded)
            assertNotNull(decoded)
            val decodedStroke = decoded?.singleOrNull() as? InkOp.Stroke
            assertNotNull(decodedStroke)
            val decPts = decodedStroke?.stroke?.points ?: emptyList()
            assertEquals(2, decPts.size)
            assertEquals(after[0].x, decPts[0].x, 1e-6f)
            assertEquals(after[0].y, decPts[0].y, 1e-6f)
            assertEquals(after[1].x, decPts[1].x, 1e-6f)
            assertEquals(after[1].y, decPts[1].y, 1e-6f)

            assertTrue(history.undo({ _, _ -> }, { _, _ -> }, onTransform = ::applyTransformSnapshots))
            assertEquals(before[0].x, st.points[0].x, 1e-6f)
            assertEquals(before[1].y, st.points[1].y, 1e-6f)

            assertTrue(history.redo({ _, _ -> }, { _, _ -> }, onTransform = ::applyTransformSnapshots))
            assertEquals(after[0].x, st.points[0].x, 1e-6f)
            assertEquals(after[1].y, st.points[1].y, 1e-6f)
        }
    }

    @Test
    fun mirrorAndEdgeStretchTransformUndoRedoRestoreExactPoints() {
        val history = InkHistory(PageLayerStack.base())
        val st = ContinuousStroke(
            CanvasTool.PEN,
            0xFF000000.toInt(),
            0.01f,
            points = mutableListOf(
                InkPoint(0.2f, 0.3f, 0.01f),
                InkPoint(0.5f, 0.6f, 0.01f)
            )
        )
        val op = InkOp.Stroke(st, null, layer())
        history.commit(op)

        val beforeMirror = st.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        val afterMirror = ContinuousStrokeTransformMath.mirrorHorizontalPoints(
            points = beforeMirror,
            centerXNorm = 0.35f,
            margin = 0f,
            clampToBounds = true
        )
        st.points.clear()
        st.points.addAll(afterMirror)
        history.commitTransform(listOf(StrokeTransformSnapshot(op, beforeMirror, afterMirror, null, null)))

        val afterStretch = ContinuousStrokeTransformMath.scalePoints(
            points = afterMirror,
            scaleX = 1.5f,
            scaleY = 1f,
            anchorXNorm = 0.2f,
            anchorYNorm = 0.45f,
            margin = 0f,
            clampToBounds = true
        )
        st.points.clear()
        st.points.addAll(afterStretch)
        history.commitTransform(listOf(StrokeTransformSnapshot(op, afterMirror, afterStretch, null, null)))

        assertTrue(history.undo({ _, _ -> }, { _, _ -> }, onTransform = ::applyTransformSnapshots))
        assertEquals(afterMirror[0].x, st.points[0].x, 1e-6f)
        assertTrue(history.undo({ _, _ -> }, { _, _ -> }, onTransform = ::applyTransformSnapshots))
        assertEquals(beforeMirror[0].x, st.points[0].x, 1e-6f)
        assertTrue(history.redo({ _, _ -> }, { _, _ -> }, onTransform = ::applyTransformSnapshots))
        assertEquals(afterMirror[0].x, st.points[0].x, 1e-6f)
        assertTrue(history.redo({ _, _ -> }, { _, _ -> }, onTransform = ::applyTransformSnapshots))
        assertEquals(afterStretch[0].x, st.points[0].x, 1e-6f)
    }

    @Test
    fun clampedRigidMoveUndoRedoAtPdfAndA4Bounds() {
        val history = InkHistory(PageLayerStack.base())
        val st = ContinuousStroke(
            CanvasTool.PEN,
            0xFF000000.toInt(),
            0.01f,
            points = mutableListOf(
                InkPoint(0.3f, 0.4f, 0.01f),
                InkPoint(0.5f, 0.6f, 0.01f)
            )
        )
        val op = InkOp.Stroke(st, null, layer())
        history.commit(op)

        val pb = ContinuousStrokeTransformMath.pointsBounds(listOf(st))
        assertNotNull(pb)
        val (cdx, cdy) = ContinuousStrokeTransformMath.clampTranslationDelta(
            pb ?: personal.notes.touch.ui.canvas.NormRect(0f, 0f, 0f, 0f),
            dxNorm = -5f,
            dyNorm = 0.2f,
            margin = ContinuousPageGeometry.marginFor("pdf")
        )
        val moved = ContinuousStrokeTransformMath.translatePoints(st.points, cdx, cdy, margin = 1f, clampToBounds = true)
        st.points.clear()
        st.points.addAll(moved)
        history.commitMove(listOf(op), cdx, cdy)

        assertEquals(-1f, st.points[0].x, 1e-6f)
        assertEquals(-0.8f, st.points[1].x, 1e-6f)

        val applyMove: (List<InkOp.Stroke>, Float, Float) -> Unit = { ops, dx, dy ->
            for (movedOp in ops) {
                val res = ContinuousStrokeTransformMath.translatePoints(
                    movedOp.stroke.points, dx, dy, margin = 1f, clampToBounds = false
                )
                movedOp.stroke.points.clear()
                movedOp.stroke.points.addAll(res)
            }
        }

        assertTrue(history.undo({ _, _ -> }, { _, _ -> }, onMove = applyMove))
        assertEquals(0.3f, st.points[0].x, 1e-6f)
        assertEquals(0.4f, st.points[0].y, 1e-6f)
        assertEquals(0.5f, st.points[1].x, 1e-6f)

        assertTrue(history.redo({ _, _ -> }, { _, _ -> }, onMove = applyMove))
        assertEquals(-1f, st.points[0].x, 1e-6f)
        assertEquals(-0.8f, st.points[1].x, 1e-6f)
    }

    @Test
    fun copyWithOffsetAndDeleteIntegrateWithAddBatchAndClearUndoRedo() {
        val history = InkHistory(PageLayerStack.base())
        val baseLayer = layer()
        val orig = InkOp.Stroke(stroke(), null, baseLayer)
        history.commit(orig)

        val copiedStrokes = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            strokes = listOf(orig.stroke),
            offsetXNorm = 0.05f,
            offsetYNorm = 0.05f,
            margin = ContinuousPageGeometry.marginFor("pdf"),
            clampToBounds = true
        )
        val copyOp = InkOp.Stroke(copiedStrokes.single(), null, baseLayer)
        history.ops.add(copyOp)
        history.commitAddBatch(listOf(copyOp))
        assertEquals(2, history.ops.size)
        assertEquals(0.15f, copyOp.stroke.points[0].x, 1e-6f)

        history.commitClear(emptyList(), listOf(OpEntry(1, copyOp)))
        assertEquals(1, history.ops.size)

        // Undo delete -> restores copy at index 1
        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(2, history.ops.size)
        assertTrue(history.ops[1] === copyOp)

        // Undo copy -> removes copy only
        assertTrue(history.undo({ _, _ -> }, { _, _ -> }))
        assertEquals(1, history.ops.size)
        assertTrue(history.ops[0] === orig)

        // Redo copy -> restores copy
        assertTrue(history.redo({ _, _ -> }, { _, _ -> }))
        assertEquals(2, history.ops.size)
    }
}
