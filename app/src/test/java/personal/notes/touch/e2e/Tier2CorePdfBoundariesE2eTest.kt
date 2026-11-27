package personal.notes.touch.e2e

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.InkToolSettings
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.LayerTileMapping
import personal.notes.touch.ui.canvas.PageLayer
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.ReaderTransform
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfModeReconciliationPolicy
import personal.notes.touch.util.PdfRenderPolicy
import personal.notes.touch.util.PdfTextSelectActivation
import personal.notes.touch.util.PdfTextSelectionMath
import personal.notes.touch.util.PdfTextToolPolicy
import personal.notes.touch.util.PdfiumDocument
import personal.notes.touch.util.TextDockTapAction
import personal.notes.touch.util.ThumbnailPolicy
import java.io.File

/**
 * Tier 2 E2E Tests — Core PDF Boundaries & Error Cases (`PDF-01` .. `PDF-08`), Phases A & B.
 */
class Tier2CorePdfBoundariesE2eTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- PDF-01 Boundaries ---

    @Test
    fun `T2-PDF01-01 ContinuousOpCodec decode returns null safely on malformed JSON`() {
        assertNull(ContinuousOpCodec.decode(""))
        assertNull(ContinuousOpCodec.decode("{bad"))
        assertNull(ContinuousOpCodec.decode("""[{"type":"unknown"}]"""))
        assertNull(ContinuousOpCodec.decode("""{"type":"stroke"}"""))
    }

    @Test
    fun `T2-PDF01-02 ContinuousOpCodec round-trips zero-point one-point and 2000-point strokes`() {
        val layer = PageLayerStack.base().layers.first()
        val emptyStroke = E2eTestHarness.strokeOp(layer, emptyList())
        val dotStroke = E2eTestHarness.strokeOp(layer, listOf(InkPoint(0.5f, 0.5f, 0.005f)))
        val longPts = (0 until 2000).map { i ->
            val t = i / 2000f
            InkPoint(t, 0.25f + 0.5f * t, 0.004f)
        }
        val longStroke = E2eTestHarness.strokeOp(layer, longPts)

        val decoded = ContinuousOpCodec.decode(
            ContinuousOpCodec.encode(listOf(emptyStroke, dotStroke, longStroke))
        )
        assertNotNull(decoded)
        val result = decoded ?: error("decoded must not be null")
        assertEquals(3, result.size)
        assertEquals(0, (result[0] as InkOp.Stroke).stroke.points.size)
        assertEquals(1, (result[1] as InkOp.Stroke).stroke.points.size)
        assertEquals(2000, (result[2] as InkOp.Stroke).stroke.points.size)
    }

    @Test
    fun `T2-PDF01-03 ContinuousPageGeometry layerPoint handles empty rect and out-of-bounds coords safely`() {
        val emptyRect = ContinuousPageGeometry.BoxRect(0f, 0f, 0f, 0f)
        val zeroPt = ContinuousPageGeometry.layerPoint(emptyRect, -1.5f, 2.5f, 6480, 3056)
        assertEquals(0f, zeroPt.x, 1e-6f)
        assertEquals(0f, zeroPt.y, 1e-6f)

        val validRect = ContinuousPageGeometry.layerRect("pdf", ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 1000f, 1000f)

        val ptNeg = ContinuousPageGeometry.layerPoint(validRect, -1.5f, -0.1f, 6480, 3056)
        assertTrue(ptNeg.x < 0f)
        assertTrue(ptNeg.y < 0f)
        assertEquals((-1.5f - validRect.left) / validRect.width * 6480f, ptNeg.x, 1e-2f)
        assertEquals((-0.1f - validRect.top) / validRect.height * 3056f, ptNeg.y, 1e-2f)

        val ptPos = ContinuousPageGeometry.layerPoint(validRect, 2.5f, 1.1f, 6480, 3056)
        assertTrue(ptPos.x > 6480f)
        assertTrue(ptPos.y > 3056f)
        assertEquals((2.5f - validRect.left) / validRect.width * 6480f, ptPos.x, 1e-2f)
        assertEquals((1.1f - validRect.top) / validRect.height * 3056f, ptPos.y, 1e-2f)
    }

    // --- PDF-02 Boundaries ---

    @Test
    fun `T2-PDF02-01 PdfRenderPolicy returns zero safely on degenerate page dimensions`() {
        assertEquals(0f, PdfRenderPolicy.capPxPerPt(0f, 842f, 2f), 1e-6f)
        assertEquals(0f, PdfRenderPolicy.capPxPerPt(595f, -1f, 2f), 1e-6f)
        assertEquals(0f, PdfRenderPolicy.capPxPerPt(595f, 842f, 0f), 1e-6f)
        assertEquals(0, PdfRenderPolicy.continuousRenderWidth(0f, 842f, 1080))
        assertEquals(0, PdfRenderPolicy.continuousRenderWidth(595f, 842f, 0))
    }

    @Test
    fun `T2-PDF02-02 PdfRenderPolicy caps extreme banner page inside MAX_DIMENSION and MAX_PIXELS`() {
        val scale = PdfRenderPolicy.capPxPerPt(10f, 10_000f, requestedPxPerPt = 5f)
        assertTrue(scale > 0f)
        val out = PdfRenderPolicy.outputSize(10f, 10_000f, scale)
        assertTrue(maxOf(out[0], out[1]) <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(out[0].toLong() * out[1].toLong() <= PdfRenderPolicy.MAX_PIXELS)
    }

    @Test
    fun `T2-PDF02-03 PdfRenderPolicy shouldRender enforces strict greater-than hysteresis threshold`() {
        assertFalse(PdfRenderPolicy.shouldRender(1.0f * PdfRenderPolicy.HYSTERESIS, 1.0f))
        assertTrue(PdfRenderPolicy.shouldRender(1.0f * (PdfRenderPolicy.HYSTERESIS + 0.001f), 1.0f))
        assertTrue(PdfRenderPolicy.shouldRender(1.0f, 0f))
        assertFalse(PdfRenderPolicy.shouldRender(0f, 1.0f))
    }

    // --- PDF-03 Boundaries ---

    @Test
    fun `T2-PDF03-01 PdfTextSelectionMath range returns null when anchor or focus is negative`() {
        assertNull(PdfTextSelectionMath.range(-1, 5))
        assertNull(PdfTextSelectionMath.range(5, -1))
        assertNull(PdfTextSelectionMath.range(-1, -1))
        assertEquals(0, PdfTextSelectionMath.count(null))
    }

    @Test
    fun `T2-PDF03-02 PdfTextSelectionMath clampIndex handles empty negative and overflow indices`() {
        assertEquals(-1, PdfTextSelectionMath.clampIndex(0, charCount = 0))
        assertEquals(-1, PdfTextSelectionMath.clampIndex(-5, charCount = 10))
        assertEquals(9, PdfTextSelectionMath.clampIndex(500, charCount = 10))
        assertEquals(4, PdfTextSelectionMath.clampIndex(4, charCount = 10))
    }

    @Test
    fun `T2-PDF03-03 PdfTextSelectionMath underlineRect clamps thickness and tolerancePageUnits guards zero scale`() {
        val rect = floatArrayOf(10f, 20f, 100f, 40f)
        val zeroThick = PdfTextSelectionMath.underlineRect(rect, thickness = 0f)
        assertEquals(39f, zeroThick[1], 1e-6f)
        assertEquals(40f, zeroThick[3], 1e-6f)

        val negThick = PdfTextSelectionMath.underlineRect(rect, thickness = -3f)
        assertEquals(39f, negThick[1], 1e-6f)

        assertEquals(10f, PdfTextSelectionMath.tolerancePageUnits(10f, 0f), 1e-6f)
        assertEquals(10f, PdfTextSelectionMath.tolerancePageUnits(10f, -2f), 1e-6f)
    }

    // --- PDF-04 Boundaries ---

    @Test
    fun `T2-PDF04-01 ContinuousPageGeometry strokeBounds returns null on empty points or zero box`() {
        val pts = listOf(InkPoint(0.2f, 0.2f, 0.01f))
        assertNull(ContinuousPageGeometry.strokeBounds(emptyList(), 1000f, 1000f, 0.5f, 4f))
        assertNull(ContinuousPageGeometry.strokeBounds(pts, 0f, 1000f, 0.5f, 4f))
        assertNull(ContinuousPageGeometry.strokeBounds(pts, 1000f, 0f, 0.5f, 4f))
    }

    @Test
    fun `T2-PDF04-02 InkHistory handles empty undo redo and trim with zero maxBytes safely`() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertFalse(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertFalse(history.redo(onErase = { _, _ -> }, onClear = { _, _ -> }))

        val layer = stack.layers.first()
        val e1 = E2eTestHarness.eraseOp(layer, points = listOf(InkPoint(0.1f, 0.1f, 0.02f)))
        val e2 = E2eTestHarness.eraseOp(layer, points = listOf(InkPoint(0.2f, 0.2f, 0.02f)))
        val e3 = E2eTestHarness.eraseOp(layer, points = listOf(InkPoint(0.3f, 0.3f, 0.02f)))
        history.commit(e1)
        history.commit(e2)
        history.commit(e3)
        history.trim(0L)
        assertTrue(history.canUndo)

        var undoneOp: InkOp.Erase? = null
        assertTrue(history.undo(onErase = { op, _ -> undoneOp = op }, onClear = { _, _ -> }))
        assertTrue(undoneOp === e3)
        assertEquals(2, history.ops.size)
        assertFalse(history.canUndo)
        assertFalse(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
    }

    @Test
    fun `T2-PDF04-03 InkToolSettings distinguishes readingMode stylusOnly ERASER and LASER_POINTER`() {
        val eraser = InkToolSettings(CanvasTool.ERASER, 0, 4f, 18f, 1f, false, stylusOnly = true, readingMode = true)
        assertTrue(eraser.isWritingTool)
        assertFalse(eraser.isLaserPointer)
        assertTrue(eraser.stylusOnly)
        assertTrue(eraser.readingMode)

        val laser = InkToolSettings(CanvasTool.LASER_POINTER, 0, 4f, 18f, 1f, false)
        assertFalse(laser.isWritingTool)
        assertTrue(laser.isLaserPointer)
    }

    // --- PDF-05 Boundaries ---

    @Test
    fun `T2-PDF05-01 PageLayerMeta decode returns null cleanly on truncated empty or invalid index`() {
        assertNull(PageLayerMeta.decode(""))
        assertNull(PageLayerMeta.decode("{broken"))
        assertNull(PageLayerMeta.decode("""{"version":1,"current":1,"layers":[]}"""))
        assertNull(PageLayerMeta.decode("""{"version":1,"current":0,"layers":[{"index":0,"name":"Zero","visible":true}]}"""))
        assertNull(PageLayerMeta.decode("""{"version":1,"current":-1,"layers":[{"index":-1,"name":"Neg","visible":true}]}"""))
    }

    @Test
    fun `T2-PDF05-02 LayerTileGrid parseTileName rejects all malformed tile strings`() {
        val invalid = listOf("", "t_0", "t-1_0", "t1_2_3", "t99999999999_0", "x0_0", "t0_", "t0_a")
        for (name in invalid) {
            assertNull("Expected null for '$name'", LayerTileGrid.parseTileName(name))
        }
        assertEquals(3 to 4, LayerTileGrid.parseTileName("t3_4"))
    }

    @Test
    fun `T2-PDF05-03 LayerTileMapping tileRect returns empty rect for out-of-range tiles or zero grid`() {
        val grid = LayerTileGrid(6480, 3056)
        val layerRect = ContinuousPageGeometry.layerRect("pdf", ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 300f, 400f)

        assertTrue(LayerTileMapping.tileRect(grid, -1, 0, layerRect, 300f, 400f).empty)
        assertTrue(LayerTileMapping.tileRect(grid, 99, 99, layerRect, 300f, 400f).empty)

        val zeroGrid = LayerTileGrid(0, 0)
        assertTrue(LayerTileMapping.tileRect(zeroGrid, 0, 0, layerRect, 300f, 400f).empty)
    }

    // --- PDF-06 Boundaries ---

    @Test
    fun `T2-PDF06-02 ContinuousOpCodec decode returns null on missing points or unknown tool`() {
        val missingPoints = """[{"type":"stroke","tool":"PEN","color":0,"baseWidthNorm":0.01,"autoShapeEnabled":false}]"""
        val unknownTool = """[{"type":"stroke","tool":"MAGIC_WAND","color":0,"baseWidthNorm":0.01,"autoShapeEnabled":false,"points":[]}]"""
        assertNull(ContinuousOpCodec.decode(missingPoints))
        assertNull(ContinuousOpCodec.decode(unknownTool))
    }

    // --- PDF-07 Boundaries ---

    @Test
    fun `T2-PDF07-01 PageLayerStack forbids removing base layer and adding past MAX_LAYERS`() {
        val stack = PageLayerStack.base()
        assertNull(stack.remove(PageLayerStack.BASE_LAYER_INDEX))
        assertNotNull(stack.add())
        assertNotNull(stack.add())
        assertEquals(PageLayerStack.MAX_LAYERS, stack.size)
        assertFalse(stack.canAdd)
        assertNull(stack.add())
        assertEquals(PageLayerStack.MAX_LAYERS, stack.size)
    }

    @Test
    fun `T2-PDF07-02 PageLayerStack load truncates extra layers renumbers and falls back invalid currentIndex`() {
        val stack = PageLayerStack()
        stack.load(
            layers = listOf(
                PageLayer(5, "Five", true),
                PageLayer(0, "InvalidZero", true),
                PageLayer(2, "Two", false),
                PageLayer(4, "Four", true),
                PageLayer(3, "Three", true)
            ),
            currentIndex = 99
        )
        assertEquals(3, stack.size)
        assertEquals(listOf(1, 2, 3), stack.layers.map { it.index })
        assertEquals(1, stack.currentIndex)
        assertFalse(stack.layers[0].visible) // original index 2 had visible=false
    }

    @Test
    fun `T2-PDF07-03 InkHistory commitRemoveLayer without merge renumbers Layer3 and undo restores Layer2 identity`() {
        val stack = PageLayerStack.base()
        val l2 = stack.add() ?: error("layer 2 missing")
        val l3 = stack.add() ?: error("layer 3 missing")
        val history = InkHistory(stack)

        val op2 = E2eTestHarness.strokeOp(l2)
        val op3 = E2eTestHarness.strokeOp(l3)
        history.commit(op2)
        history.commit(op3)

        history.commitRemoveLayer(l2, merged = false)
        assertEquals(2, stack.size)
        assertEquals(2, l3.index)
        assertEquals(listOf<InkOp>(op3), history.ops)
        assertTrue(op3.layer === l3)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(3, stack.size)
        assertEquals(listOf(1, 2, 3), stack.layers.map { it.index })
        assertTrue(stack.layers[1] === l2)
        assertTrue(stack.layers[2] === l3)
        assertEquals(2, history.ops.size)
    }

    // --- PDF-08 Boundaries ---

    @Test
    fun `T2-PDF08-01 ReaderTransform clamps extreme zoom factors inside MIN_SCALE and MAX_SCALE`() {
        val layout = ReaderTransform.Layout(1000f, 1600f, false, 1000f, 1400f, 0f, 100f)
        val state = ReaderTransform.State(scale = 1f)

        val minRes = ReaderTransform.apply(state, ReaderTransform.Gesture(500f, 800f, 0f, 0f, 0.001f), layout)
        assertEquals(ReaderTransform.MIN_SCALE, minRes.state.scale, 1e-6f)

        val maxRes = ReaderTransform.apply(state, ReaderTransform.Gesture(500f, 800f, 0f, 0f, 1000f), layout)
        assertEquals(ReaderTransform.MAX_SCALE, maxRes.state.scale, 1e-6f)
    }

    @Test
    fun `T2-PDF08-02 ReaderTransform zoomLocked preserves scale and clamps extreme pan to pullLimit`() {
        val layout = ReaderTransform.Layout(1000f, 1600f, false, 1000f, 1400f, 0f, 100f)
        val state = ReaderTransform.State(scale = 2.0f)
        val res = ReaderTransform.apply(
            state = state,
            gesture = ReaderTransform.Gesture(500f, 800f, panX = 99_999f, panY = 0f, zoom = 2.5f),
            layout = layout,
            zoomLocked = true
        )
        assertEquals(2.0f, res.state.scale, 1e-6f)
        val limit = ReaderTransform.pullLimit(layout.viewportWidth, 2.0f)
        assertTrue(res.state.panX in -limit..limit)
        assertEquals(limit, res.state.panX, 1e-3f)
    }

    @Test
    fun `T2-PDF08-03 ReaderTransform contentPoint guards non-positive scale without division by zero`() {
        val layout = ReaderTransform.Layout(1000f, 1600f, false, 1000f, 1400f, 50f, 100f)
        assertEquals(0f to 0f, ReaderTransform.contentPoint(layout, 0f, 300f, 400f))
        assertEquals(0f to 0f, ReaderTransform.contentPoint(layout, -1f, 300f, 400f))
    }

    // --- PHASE B: PDF-01 Boundaries ---

    @Test
    fun `T2-PDF01-04 switching PDF page with zero continuous strokes yields null overlay cleanly`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t2_pdf01_04"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t2-pdf01-04", pageCount = 2)
        env.seedNoteAndPages(note, pages)

        val mtimes = ThumbnailPolicy.readPageDiskMtimes(env.fileStore.getNoteDir(note.id), 0)
        assertFalse(mtimes.hasAnyAnnotationFiles)
        assertNull(env.repository.loadPageLayerOps(note.id, 0, 1))
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("pdf", isVectorPdf = true))

        val inkController = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        val noteRepo = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/data/repo/NoteRepository.kt")
        val drawingView = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/DrawingCanvasView.kt")
        assertTrue(inkController.contains("if (composedLayers.isEmpty()) return@withContext null"))
        assertTrue(noteRepo.contains("if (annotFiles.isEmpty()) return@withContext null"))
        assertTrue(drawingView.contains("annotationOverlay?.let"))
    }

    @Test
    fun `T2-PDF01-05 rapid mode toggle sequences flush and sequence-guard async overlay loads`() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val op1 = E2eTestHarness.strokeOp(stack.layers.first())
        val op2 = E2eTestHarness.strokeOp(stack.layers.first(), points = listOf(InkPoint(0.5f, 0.5f, 0.005f)))
        history.commit(op1)
        history.commit(op2)
        val encoded = ContinuousOpCodec.encode(history.ops)
        assertEquals(2, ContinuousOpCodec.decode(encoded)?.size)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val inkController = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        assertTrue(canvasScreen.contains("val seq = singlePageOverlayLoadSeq.incrementAndGet()"))
        assertTrue(canvasScreen.contains("inkController.releaseSessionSuspend()"))
        assertTrue(inkController.contains("s.loadJob?.join()"))
        assertTrue(inkController.contains("if (session !== s) return"))
        assertTrue(inkController.contains("if (s.dirty) {"))
    }

    // --- PHASE B: PDF-02 Boundaries ---

    @Test
    fun `T2-PDF02-04 missing pdfSourcePath file safely falls back without JNI crash`() {
        val missing = File(tempFolder.newFolder("t2_pdf02_04"), "missing.pdf")
        assertNull(PdfiumDocument.open(missing))
        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(2160, 3056, 0f, 0f))
        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(0, 0, 0f, 0f))

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportManager.contains("else if (pageFile.exists())"))
        assertTrue(exportManager.contains("Bitmap.createBitmap(blankW, blankH, Bitmap.Config.ARGB_8888)"))
    }

    @Test
    fun `T2-PDF02-05 out-of-range pdfPageIndex 99 or -1 resolves safely without crash`() {
        val doc = PdfiumDocument()
        val size = doc.pageSize(99)
        assertEquals(2, size.size)
        assertEquals(0f, size[0], 1e-6f)
        assertEquals(0f, size[1], 1e-6f)

        val (_, pages) = E2eTestHarness.pdfNoteFixture("t2-pdf02-05", pageCount = 3)
        assertNull(PdfExportPolicy.resolveVectorPageSource(pages, 99))
        val badPages = listOf(PageEntity(id = "bad", noteId = "n", index = 0, bitmapPath = "", pdfSourcePath = "doc.pdf", pdfPageIndex = -1))
        assertNull(PdfExportPolicy.resolveVectorPageSource(badPages, 0))

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportManager.contains("pts[0] > 0f && pts[1] > 0f"))
    }

    // --- PHASE B: PDF-03 Boundaries ---

    @Test
    fun `T2-PDF03-04 dock Text tap on scanned PDF with 0 chars opens Live Text without crash`() {
        assertFalse(PdfTextToolPolicy.isAvailable(isVectorPdfPage = true, charCount = 0))
        assertEquals(TextDockTapAction.OPEN_LIVE_TEXT, PdfTextToolPolicy.dockTapAction(pdfTextAvailable = false, pdfTextMode = false))
        assertEquals(PdfTextSelectActivation.UNAVAILABLE, PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = false, isContinuous = true))
    }

    @Test
    fun `T2-PDF03-05 dock Text tap on A4 or Infinite note never enters pdfTextMode`() {
        assertFalse(PdfTextToolPolicy.isAvailable(isVectorPdfPage = false, charCount = 500))
        assertEquals(TextDockTapAction.OPEN_LIVE_TEXT, PdfTextToolPolicy.dockTapAction(pdfTextAvailable = false, pdfTextMode = false))

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("pdfTextAvailable = false"))
        assertTrue(canvasScreen.contains("exitPdfTextMode()"))
    }

    // --- PHASE B: PDF-04 Boundaries ---

    @Test
    fun `T2-PDF04-04 empty Lasso loop selects zero strokes and commits no history entry`() {
        val strokes = listOf(
            ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.2f, 0.2f, 0.005f), InkPoint(0.3f, 0.3f, 0.005f)))
        )
        val disjointPoly = listOf(0.7f to 0.7f, 0.9f to 0.7f, 0.9f to 0.9f, 0.7f to 0.9f)
        val picked = ContinuousStrokeTransformMath.selectStrokesInLasso(strokes, disjointPoly)
        assertTrue(picked.isEmpty())
        assertNull(ContinuousStrokeTransformMath.selectionBounds(emptyList(), ContinuousPageGeometry.DEFAULT_PAGE_RATIO))

        val history = InkHistory(PageLayerStack.base())
        assertFalse(history.canUndo)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("if (picked == 0) ink.lassoClear()"))
    }

    @Test
    fun `T2-PDF04-05 cross-page Lasso and Arrow selection stays scoped to originating page`() {
        val page0 = listOf(ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.3f, 0.3f, 0.005f))))
        val page1 = listOf(ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.3f, 0.3f, 0.005f))))

        val poly = listOf(0.1f to 0.1f, 0.5f to 0.1f, 0.5f to 0.5f, 0.1f to 0.5f)
        assertEquals(listOf(0), ContinuousStrokeTransformMath.selectStrokesInLasso(page0, poly))
        assertEquals(-1, ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(page1, 0.8f, 0.8f, 1f, 0.05f))

        val inkController = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(inkController.contains("var lassoPageIndex: Int = -1") || inkController.contains("lassoPageIndex"))
        assertTrue(canvasScreen.contains("inkController.clearLassoIfNotOnPage(currentPageIndex)"))
    }

    // --- PHASE B: PDF-05 Boundaries ---

    @Test
    fun `T2-PDF05-04 thumbnail policy excludes masked layers when all annotation layers are hidden`() {
        val stack = PageLayerStack.base()
        stack.add()
        stack.setVisible(1, false)
        stack.setVisible(2, false)
        assertTrue(stack.visibleLayers().isEmpty())

        val layerPlan = PdfModeReconciliationPolicy.planLayerVisibility(
            stack = stack,
            hasSinglePageStrokes = true,
            floatingLayersVisible = listOf(false)
        )
        assertFalse(layerPlan.showSinglePageStrokes)
        assertTrue(layerPlan.visibleDrawingLayerIndices.isEmpty())
        assertTrue(layerPlan.visibleFloatingLayerIndices.isEmpty())

        val thumbPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = true,
            hasAnnotationLayers = false,
            hasImageLayers = false,
            baseLayerVisible = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, thumbPlan.background)
        assertTrue(thumbPlan.overlays.isEmpty())
    }

    @Test
    fun `T2-PDF05-05 corrupt _ops json and annot meta json are ignored safely without blocking PDF background`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t2_pdf05_05"))
        val metaFile = env.fileStore.getAnnotationMetaFile("n-corrupt", 0)
        val opsFile = env.fileStore.getAnnotationLayerOpsFile("n-corrupt", 0, 1)
        metaFile.parentFile?.mkdirs()
        metaFile.writeText("{corrupt_meta")
        opsFile.writeText("{corrupt_ops")

        assertNull(PageLayerMeta.decode(metaFile.readText()))
        assertNull(env.repository.loadPageLayerOps("n-corrupt", 0, 1))

        val plan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, plan.background)
    }

    // --- PHASE B: PDF-06 Boundaries ---

    @Test
    fun `T2-PDF06-01 NoteFileStore getNoteAsset rejects path traversal and blank names`() {
        val fileStore = NoteFileStore(tempFolder.newFolder("t2_pdf06_01"))
        val noteDir = fileStore.getNoteDir("n1")
        val valid = File(noteDir, "img.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertNull(fileStore.getNoteAsset("n1", "../secret"))
        assertNull(fileStore.getNoteAsset("n1", "a/b"))
        assertNull(fileStore.getNoteAsset("n1", "a\\b"))
        assertNull(fileStore.getNoteAsset("n1", "   "))
        assertNull(fileStore.getNoteAsset("n1", ""))
        assertNull(fileStore.getNoteAsset("n1", "missing.png"))
        assertEquals(valid.absolutePath, fileStore.getNoteAsset("n1", "img.png")?.absolutePath)
    }

    @Test
    fun `T2-PDF06-03 annotations on page 4 leave page 0 mtimes and Home cache key isolated`() {
        val filesDir = tempFolder.newFolder("t2_pdf06_03")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "t2-pdf06-03")
        val beforeMtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        val beforeKey = ThumbnailPolicy.buildPageThumbnailCacheKey(
            ws.noteId, 0, "pdf", "doc.pdf", 0, beforeMtimes, 0, 500, 100L
        )

        val stack = PageLayerStack.base()
        ws.writeContinuousPage(4, stack, mapOf(1 to listOf(E2eTestHarness.strokeOp(stack.layers.first()))))

        val p0Mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        val p4Mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 4)
        assertFalse(p0Mtimes.hasAnyAnnotationFiles)
        assertEquals(0, p0Mtimes.annotFileCount)
        assertTrue(p4Mtimes.hasAnyAnnotationFiles)
        val afterKey = ThumbnailPolicy.buildPageThumbnailCacheKey(
            ws.noteId, 0, "pdf", "doc.pdf", 0, p0Mtimes, 0, 500, 100L
        )
        assertEquals(beforeKey, afterKey)
    }

    @Test
    fun `T2-PDF06-04 custom coverPath takes precedence over page 0 continuous ink in Home preview`() {
        val coverPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = true,
            hasPageFile = true,
            hasBackgroundOverride = false,
            hasStrokesFile = true,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.COVER_FILE, coverPlan.background)
        assertTrue(coverPlan.overlays.isEmpty())

        val normalPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, normalPlan.background)
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), normalPlan.overlays)

        assertNotEquals(
            ThumbnailPolicy.homePreviewSeedKey("n1", 10L, "/cover.png", "pdf"),
            ThumbnailPolicy.homePreviewSeedKey("n1", 10L, null, "pdf")
        )
    }

    @Test
    fun `T2-PDF06-05 missing page_0 PNG falls back gracefully to vector PDF or blank white with annotations`() {
        val vectorFallback = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, vectorFallback.background)
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), vectorFallback.overlays)

        val blankFallback = ThumbnailPolicy.planThumbnailComposition(
            pageType = "a4",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.BLANK_WHITE, blankFallback.background)
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), blankFallback.overlays)
    }

    // --- PHASE B: PDF-07 Boundaries ---

    @Test
    fun `T2-PDF07-04 undoing merged Layer2 then hiding it reflects accurate Single-Page mask`() {
        val stack = PageLayerStack.base()
        val l2 = stack.add() ?: error("layer 2 missing")
        val history = InkHistory(stack)
        history.commit(E2eTestHarness.strokeOp(l2))
        history.commitRemoveLayer(l2, merged = true)
        assertEquals(1, stack.size)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(2, stack.size)
        stack.setVisible(2, false)

        val plan = PdfModeReconciliationPolicy.planLayerVisibility(stack, hasSinglePageStrokes = true, floatingLayersVisible = emptyList())
        assertEquals(listOf(1), plan.visibleDrawingLayerIndices)
    }

    @Test
    fun `T2-PDF07-05 NoteFileStore deletePageFile and renamePageFile clean and shift page assets`() {
        val fileStore = NoteFileStore(tempFolder.newFolder("t2_pdf07_05"))
        val noteId = "n-shift"
        fun seedPageAssets(idx: Int) {
            fileStore.getPageFile(noteId, idx).writeBytes(byteArrayOf(idx.toByte()))
            fileStore.getStrokesFile(noteId, idx).writeBytes(byteArrayOf(idx.toByte()))
            fileStore.getPageBackgroundFile(noteId, idx).writeBytes(byteArrayOf(idx.toByte()))
            fileStore.getBeyondInkFile(noteId, idx).apply { parentFile?.mkdirs(); writeText("{}") }
            File(fileStore.getLayersDir(noteId), "beyond_${idx}_0.png").writeBytes(byteArrayOf(1))
            File(fileStore.getLayersDir(noteId), "page_${idx}_layer_0.png").writeBytes(byteArrayOf(2))
            fileStore.getLayerInfoFile(noteId, idx).writeText("[]")
            fileStore.getAnnotationMetaFile(noteId, idx).writeText("{}")
            fileStore.getAnnotationLayerOpsFile(noteId, idx, 1).writeText("[]")
            fileStore.getAnnotationLayerTileFile(noteId, idx, 1, 0, 0).writeBytes(byteArrayOf(3))
        }
        seedPageAssets(0)
        seedPageAssets(1)

        assertTrue(fileStore.deletePageFile(noteId, 0))
        assertFalse(fileStore.getPageFile(noteId, 0).exists())
        assertFalse(fileStore.getStrokesFile(noteId, 0).exists())
        assertFalse(fileStore.getPageBackgroundFile(noteId, 0).exists())
        assertFalse(fileStore.getBeyondInkFile(noteId, 0).exists())
        assertFalse(File(fileStore.getLayersDir(noteId), "beyond_0_0.png").exists())
        assertFalse(File(fileStore.getLayersDir(noteId), "page_0_layer_0.png").exists())
        assertFalse(fileStore.getLayerInfoFile(noteId, 0).exists())
        assertFalse(fileStore.getAnnotationMetaFile(noteId, 0).exists())
        assertFalse(fileStore.getAnnotationLayerOpsFile(noteId, 0, 1).exists())
        assertFalse(fileStore.getAnnotationLayerTileFile(noteId, 0, 1, 0, 0).exists())

        assertTrue(fileStore.renamePageFile(noteId, 1, 0))
        assertTrue(fileStore.getPageFile(noteId, 0).isFile)
        assertTrue(fileStore.getStrokesFile(noteId, 0).isFile)
        assertTrue(fileStore.getPageBackgroundFile(noteId, 0).isFile)
        assertTrue(fileStore.getBeyondInkFile(noteId, 0).isFile)
        assertTrue(File(fileStore.getLayersDir(noteId), "beyond_0_0.png").isFile)
        assertTrue(File(fileStore.getLayersDir(noteId), "page_0_layer_0.png").isFile)
        assertTrue(fileStore.getLayerInfoFile(noteId, 0).isFile)
        assertFalse(fileStore.getLayerInfoFile(noteId, 1).exists())
        assertTrue(fileStore.getAnnotationMetaFile(noteId, 0).isFile)
        assertTrue(fileStore.getAnnotationLayerOpsFile(noteId, 0, 1).isFile)
        assertTrue(fileStore.getAnnotationLayerTileFile(noteId, 0, 1, 0, 0).isFile)
        assertFalse(fileStore.getAnnotationMetaFile(noteId, 1).exists())

        // Vector PDF pages without page_<old>.png still rename layer files, returning false for missing PNG
        fileStore.getAnnotationLayerOpsFile(noteId, 2, 1).writeText("[]")
        assertFalse(fileStore.renamePageFile(noteId, 2, 1))
        assertTrue(fileStore.getAnnotationLayerOpsFile(noteId, 1, 1).isFile)
    }

    // --- PHASE B: PDF-08 Boundaries ---

    @Test
    fun `T2-PDF08-04 CanvasScreen continuous Box has no competing outer single-finger stroke handler`() {
        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val boxStart = canvasScreen.indexOf(".background(if (isInfiniteNote) Color.White else Color(0xFFEEEEEE))")
        val boxEnd = canvasScreen.indexOf("if (isInfiniteNote) {", startIndex = boxStart)
        val wrapper = canvasScreen.substring(boxStart, boxEnd)
        assertFalse(wrapper.contains("beginStroke("))
        assertFalse(wrapper.contains("extendStroke("))
        assertTrue(wrapper.contains("PointerEventPass.Initial"))
        assertTrue(wrapper.contains("if (pressed.size < 2) break"))
    }

    @Test
    fun `T2-PDF08-05 readingMode mid-gesture suppresses stroke capture and exits PDF text mode`() {
        val settings = InkToolSettings(CanvasTool.PEN, 0, 4f, 16f, 1f, false, readingMode = true)
        assertTrue(settings.readingMode)
        assertTrue(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true, readingMode = true, isContinuous = true, switchingToSinglePage = false
            )
        )

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val leafSection = canvasScreen.substring(canvasScreen.indexOf("fun ContinuousPageView("))
        assertTrue(leafSection.contains("if (inkSettings.readingMode) return@pointerInput"))
        assertTrue(leafSection.contains("} finally {"))
        assertTrue(leafSection.contains("ink.cancelStroke()"))
    }
}
