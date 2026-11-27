package personal.notes.touch.e2e

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.LayerTileMapping
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.PageScrubberMath
import personal.notes.touch.ui.canvas.ReaderTransform
import personal.notes.touch.ui.canvas.StrokeTransformSnapshot
import personal.notes.touch.util.MarginExportMath
import personal.notes.touch.util.OutlineTreeNode
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfImportManager
import personal.notes.touch.util.PdfModeReconciliationPolicy
import personal.notes.touch.util.PdfOutlineImporter
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfRenderPolicy
import personal.notes.touch.util.PdfTextIndex
import personal.notes.touch.util.PdfTextSelectActivation
import personal.notes.touch.util.PdfTextSelectionMath
import personal.notes.touch.util.PdfTextToolPolicy
import personal.notes.touch.util.PdfVectorImporter
import personal.notes.touch.util.ThumbnailPolicy
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.PI

/**
 * Tier 3 E2E Tests — Cross-Feature / Pairwise Combinations (`T3-01` .. `T3-18`).
 */
class Tier3CrossFeatureE2eTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `T3-01 PDF-01 plus PDF-07 multi-layer stack visibility and ops survive ContinuousOpCodec and PageLayerMeta round-trip`() {
        val stack = PageLayerStack.base()
        val l1 = stack.layers.first()
        val l2 = stack.add() ?: error("l2 missing")
        val l3 = stack.add() ?: error("l3 missing")
        stack.setVisible(2, false)
        stack.select(1)

        val history = InkHistory(stack)
        val op1 = E2eTestHarness.strokeOp(l1, tool = CanvasTool.PEN)
        val op2 = E2eTestHarness.strokeOp(l2, tool = CanvasTool.HIGHLIGHTER)
        val op3 = E2eTestHarness.strokeOp(l3, tool = CanvasTool.FOUNTAIN_PEN)
        history.commit(op1)
        history.commit(op2)
        history.commit(op3)

        val metaJson = PageLayerMeta.encode(stack)
        val opsByLayer = mapOf(
            1 to ContinuousOpCodec.encode(history.ops.filter { it.layer.index == 1 }),
            2 to ContinuousOpCodec.encode(history.ops.filter { it.layer.index == 2 }),
            3 to ContinuousOpCodec.encode(history.ops.filter { it.layer.index == 3 })
        )

        val restoredStack = PageLayerMeta.decode(metaJson) ?: error("restoredStack null")
        assertEquals(3, restoredStack.size)
        assertEquals(listOf(1, 3), restoredStack.visibleLayers().map { it.index })
        assertEquals(1, restoredStack.currentIndex)

        assertEquals(1, ContinuousOpCodec.decode(opsByLayer.getValue(1))?.size)
        assertEquals(1, ContinuousOpCodec.decode(opsByLayer.getValue(2))?.size)
        assertEquals(1, ContinuousOpCodec.decode(opsByLayer.getValue(3))?.size)
    }

    @Test
    fun `T3-02 PDF-05 plus PDF-QOL-6 margin stroke and on-page stroke project into left and center thumbnail tiles`() {
        val ratio = ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        val layerSize = ContinuousPageGeometry.layerSize("pdf", ratio)
        val grid = LayerTileGrid(layerSize.width, layerSize.height)
        val boxW = 300f
        val boxH = 400f
        val pageRect = ContinuousPageGeometry.pageRect("pdf", ratio, boxW, boxH)
        val layerRect = ContinuousPageGeometry.layerRect("pdf", ratio, boxW, boxH)

        // Left-margin point (x = -0.5 * pageWidth from page left) vs center-page point (x = +0.5 * pageWidth)
        val leftMarginBoxX = pageRect.left - 0.5f * pageRect.width
        val onPageBoxX = pageRect.left + 0.5f * pageRect.width
        val midY = pageRect.top + 0.5f * pageRect.height

        val leftLayerPt = ContinuousPageGeometry.layerPoint(layerRect, leftMarginBoxX, midY, layerSize.width, layerSize.height)
        val centerLayerPt = ContinuousPageGeometry.layerPoint(layerRect, onPageBoxX, midY, layerSize.width, layerSize.height)

        assertTrue(leftLayerPt.x < layerSize.width / 3f)
        assertTrue(centerLayerPt.x in (layerSize.width / 3f)..(2f * layerSize.width / 3f))

        val leftTileIdx = grid.tilesForRect(leftLayerPt.x.toInt(), leftLayerPt.y.toInt(), leftLayerPt.x.toInt() + 2, leftLayerPt.y.toInt() + 2).first()
        val centerTileIdx = grid.tilesForRect(centerLayerPt.x.toInt(), centerLayerPt.y.toInt(), centerLayerPt.x.toInt() + 2, centerLayerPt.y.toInt() + 2).first()

        val leftTx = grid.tileColumn(leftTileIdx)
        val centerTx = grid.tileColumn(centerTileIdx)
        assertTrue(leftTx < centerTx)

        val leftScreenRect = LayerTileMapping.tileRect(grid, leftTx, grid.tileRow(leftTileIdx), layerRect, boxW, boxH)
        val centerScreenRect = LayerTileMapping.tileRect(grid, centerTx, grid.tileRow(centerTileIdx), layerRect, boxW, boxH)
        assertFalse(leftScreenRect.empty)
        assertFalse(centerScreenRect.empty)
        assertTrue(leftScreenRect.right <= centerScreenRect.left + 0.01f)
    }

    @Test
    fun `T3-03 PDF-03 plus PDF-QOL-4 16-by-9 landscape PDF text selection rect scales cleanly into canvas space`() {
        val wPts = 960f
        val hPts = 540f
        val outW = PdfRenderPolicy.continuousRenderWidth(wPts, hPts, desiredWidthPx = 1920)
        assertEquals(1920, outW)
        val pxPerPt = PdfPageGeometry.canvasScale(wPts, outW)
        assertEquals(2.0f, pxPerPt, 1e-6f)

        val userRect = floatArrayOf(100f, 400f, 500f, 380f)
        val canvasRect = PdfPageGeometry.userRectToCanvas(userRect, hPts, pxPerPt)
        val underline = PdfTextSelectionMath.underlineRect(canvasRect, thickness = 4f)

        assertEquals(200f, underline[0], 1e-4f)
        assertEquals(1000f, underline[2], 1e-4f)
        assertTrue(underline[1] >= 0f && underline[3] <= 1080f)
        assertEquals(4f, underline[3] - underline[1], 1e-4f)
    }

    @Test
    fun `T3-04 PDF-08 plus PDF-QOL-6 ReaderTransform allows pulling page aside by at least viewportWidth to expose margins`() {
        val viewportW = 1080f
        val layout = ReaderTransform.Layout(
            viewportWidth = viewportW,
            viewportHeight = 1920f,
            horizontal = false,
            pageWidth = viewportW,
            pageHeight = 1528f,
            originX = 0f,
            originY = 196f
        )

        val limitAt1x = ReaderTransform.pullLimit(viewportW, 1.0f)
        assertTrue(limitAt1x >= viewportW)

        val panned1x = ReaderTransform.apply(
            state = ReaderTransform.State(scale = 1.0f),
            gesture = ReaderTransform.Gesture(540f, 960f, panX = viewportW, panY = 0f, zoom = 1.0f),
            layout = layout
        )
        assertEquals(viewportW, panned1x.state.panX, 0.01f)

        val limitAt3x = ReaderTransform.pullLimit(viewportW, 3.0f)
        assertTrue(limitAt3x > limitAt1x)
    }

    @Test
    fun `T3-05 PDF-04 plus PDF-07 Layer2 transform and move survive layer hide merge and undo`() {
        val stack = PageLayerStack.base()
        val base = stack.layers.first()
        val l2 = stack.add() ?: error("l2 missing")
        val history = InkHistory(stack)

        val op = E2eTestHarness.strokeOp(l2, listOf(InkPoint(0.2f, 0.2f, 0.005f), InkPoint(0.3f, 0.3f, 0.005f)))
        history.commit(op)

        val afterPts = listOf(InkPoint(0.4f, 0.2f, 0.005f), InkPoint(0.5f, 0.3f, 0.005f))
        op.stroke.points.clear()
        op.stroke.points.addAll(afterPts)
        history.commitTransform(
            listOf(StrokeTransformSnapshot(op, listOf(InkPoint(0.2f, 0.2f, 0.005f), InkPoint(0.3f, 0.3f, 0.005f)), afterPts, null, null))
        )
        stack.setVisible(2, false)
        assertTrue(stack.visibleLayers().none { it === l2 })

        history.commitRemoveLayer(l2, merged = true)
        assertEquals(1, stack.size)
        assertTrue(op.layer === base)
        assertEquals(0.4f, op.stroke.points[0].x, 1e-6f)

        // Undo merge -> Layer 2 ownership restored while transformed points stay intact
        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(2, stack.size)
        assertTrue(op.layer === l2)
        assertEquals(0.4f, op.stroke.points[0].x, 1e-6f)
    }

    @Test
    fun `T3-06 PDF-02 plus PDF-QOL-2 100-page mixed PDF policy keeps export scale and thumb dims within bounds`() {
        val sizes = listOf(595f to 842f, 960f to 540f, 612f to 792f, 1200f to 1200f)
        for (page in 0 until 100) {
            val (w, h) = sizes[page % sizes.size]
            val exportScale = PdfRenderPolicy.capPxPerPt(w, h, requestedPxPerPt = 3.5f)
            val exportOut = PdfRenderPolicy.outputSize(w, h, exportScale)
            assertTrue(maxOf(exportOut[0], exportOut[1]) <= PdfRenderPolicy.MAX_DIMENSION)
            assertTrue(exportOut[0].toLong() * exportOut[1].toLong() <= PdfRenderPolicy.MAX_PIXELS)

            val (thumbW, thumbH) = PdfImportManager.calculateOutputSize(w.toInt(), h.toInt(), 720, 720)
            assertTrue(thumbW in 1..PdfImportManager.MAX_DIMENSION)
            assertTrue(thumbH in 1..PdfImportManager.MAX_DIMENSION)
            assertNotNull(exportOut)
        }
    }

    // --- PHASE B: Cross-Feature Combinations (T3-07 .. T3-18) ---

    @Test
    fun `T3-07 PDF-02 plus PDF-QOL-4 plus PDF-QOL-6 mixed A4 and 16-by-9 export Page only vs Include margins`() {
        val a4Aspect = PdfPageGeometry.aspect(595f, 842f)
        val slideAspect = PdfPageGeometry.aspect(960f, 540f)

        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(a4Aspect, 1.0f, includeMargins = false))
        assertEquals(842 to 474, MarginExportMath.exportPdfPageSizePts(slideAspect, 1.0f, includeMargins = false))
        assertEquals(842 to 397, MarginExportMath.exportPdfPageSizePts(a4Aspect, 1.0f, includeMargins = true))
        assertEquals(842 to 158, MarginExportMath.exportPdfPageSizePts(slideAspect, 1.0f, includeMargins = true))

        val slideLayer = ContinuousPageGeometry.layerSize("pdf", slideAspect)
        assertEquals(6480, slideLayer.width)
        val pageOnlyCrop = MarginExportMath.cropRect(slideLayer.width, slideLayer.height, slideAspect, 1.0f, includeMargins = false)
        assertTrue(pageOnlyCrop.cropped)
        assertEquals(2160, pageOnlyCrop.left)
        assertEquals(2160, pageOnlyCrop.width)

        val withMarginsCrop = MarginExportMath.cropRect(slideLayer.width, slideLayer.height, slideAspect, 1.0f, includeMargins = true)
        assertFalse(withMarginsCrop.cropped)
        assertEquals(6480, withMarginsCrop.width)

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportManager.contains("MarginExportMath.cropRect("))
    }

    @Test
    fun `T3-08 PDF-QOL-1 plus PDF-QOL-3 plus PDF-QOL-5 outline search and scrubber stay synchronized`() {
        PdfTextIndex.clearCache()
        try {
            val outline = PdfOutlineImporter.importNodes(
                PdfOutlineImporter.flattenTree(
                    listOf(
                        OutlineTreeNode("Part I", 0, listOf(OutlineTreeNode("Linear Maps", 3))),
                        OutlineTreeNode("Part II", 10, listOf(OutlineTreeNode("Laplacian Matrix", 14)))
                    )
                ),
                pageCount = 20
            )
            assertTrue(PageScrubberMath.isScrubberEnabled(20))
            val scrubbedPage = PageScrubberMath.scrubFractionToPage(4f / 19f, 20)
            assertEquals(4, scrubbedPage)
            val scrubbedChapter = PdfOutlineImporter.activeEntryId(outline, scrubbedPage)
            assertEquals(outline[1].id, scrubbedChapter)

            val index = PdfTextIndex.buildIndex(20, pageTextProvider = { p ->
                if (p == 15) "Discrete Laplacian eigenvalues on graphs" else "Standard lecture page $p"
            }) ?: error("index null")
            val hits = PdfTextIndex.search(index, "laplacian")
            assertEquals(1, hits.size)
            val jumpedPage = hits.first().pageIndex
            assertEquals(15, jumpedPage)
            assertEquals(outline[3].id, PdfOutlineImporter.activeEntryId(outline, jumpedPage))
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T3-09 PDF-04 plus PDF-08 plus PDF-QOL-7 16-by-9 continuous rotate pinch zoom and undo`() {
        val ratio = 16f / 9f
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val op = E2eTestHarness.strokeOp(stack.layers.first(), points = listOf(InkPoint(0.3f, 0.5f, 0.005f), InkPoint(0.7f, 0.5f, 0.005f)))
        history.commit(op)

        val beforePts = op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        val beforeLen = ContinuousStrokeTransformMath.isotropicDistance(beforePts[0], beforePts[1], ratio)
        val rotated = ContinuousStrokeTransformMath.rotatePoints(beforePts, (PI / 2.0).toFloat(), 0.5f, 0.5f, ratio, margin = 1.0f)
        assertEquals(beforeLen, ContinuousStrokeTransformMath.isotropicDistance(rotated[0], rotated[1], ratio), 1e-5f)

        op.stroke.points.clear()
        op.stroke.points.addAll(rotated)
        history.commitTransform(listOf(StrokeTransformSnapshot(op, beforePts, rotated, null, null)))

        val readerRes = ReaderTransform.apply(
            ReaderTransform.State(scale = 1.0f),
            ReaderTransform.Gesture(500f, 300f, panX = 10f, panY = 0f, zoom = 2.0f),
            ReaderTransform.Layout(1000f, 600f, false, 1000f, 562f, 0f, 19f)
        )
        assertEquals(2.0f, readerRes.state.scale, 1e-5f)

        var restoredAfterUndo = false
        assertTrue(
            history.undo(
                onErase = { _, _ -> },
                onClear = { _, _ -> },
                onTransform = { snaps, isUndo ->
                    if (isUndo) {
                        snaps.forEach { s ->
                            s.op.stroke.points.clear()
                            s.op.stroke.points.addAll(s.beforePoints)
                        }
                        restoredAfterUndo = true
                    }
                }
            )
        )
        assertTrue(restoredAfterUndo)
        assertEquals(0.3f, op.stroke.points[0].x, 1e-6f)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("PointerEventPass.Initial"))
        assertTrue(canvasScreen.contains("ink.cancelStroke()"))
    }

    @Test
    fun `T3-10 PDF-05 plus PDF-06 plus PDF-QOL-4 16-by-9 page 0 and 2 annotations drive thumbnails and Home card`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t3_10"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t3-10", pageCount = 4)
        env.seedNoteAndPages(note, pages)

        val cache = PageAspectCache()
        val aspect0 = cache.put(pages[0].pdfSourcePath, 0, 1920f, 1080f)
        val aspect2 = cache.put(pages[2].pdfSourcePath, 2, 1920f, 1080f)
        assertEquals(340 to 191, PdfPageGeometry.thumbnailSize(aspect0, 340))
        assertEquals(500 to 281, PdfPageGeometry.thumbnailSize(aspect2, 500))

        val layerSize = ContinuousPageGeometry.layerSize("pdf", aspect0)
        assertEquals(6480, layerSize.width)
        assertEquals(1215, layerSize.height)
        assertEquals(listOf(2160, 0, 4320, 1215), ThumbnailPolicy.continuousPixelCropRect(6480, 1215, "pdf").toList())

        val layer = PageLayerStack.base().layers.first()
        assertTrue(env.repository.savePageLayerOps(note.id, 0, 1, listOf(E2eTestHarness.strokeOp(layer))))
        assertTrue(env.repository.savePageLayerOps(note.id, 2, 1, listOf(E2eTestHarness.strokeOp(layer))))
        assertEquals(2, env.touchedUpdatedAtCalls.size)

        val seedBefore = ThumbnailPolicy.homePreviewSeedKey(note.id, 100L, null, "pdf")
        val seedAfter = ThumbnailPolicy.homePreviewSeedKey(note.id, env.touchedUpdatedAtCalls.last().second, null, "pdf")
        assertNotEquals(seedBefore, seedAfter)
    }

    @Test
    fun `T3-11 PDF-QOL-2 plus PDF-QOL-1 plus PDF-QOL-4 streaming import caches mixed aspects and nested outline`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t3_11"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t3-11", pageCount = 1)
        env.seedNoteAndPages(note, pages)

        val incoming = tempFolder.newFile("t3_11.pdf")
        assertTrue(PdfVectorImporter.copyStreamToFile(ByteArrayInputStream("%PDF-1.5\n".toByteArray()), incoming))
        val stored = PdfVectorImporter.storeSourceFile(incoming, env.fileStore.getNoteDir(note.id), isTempFile = true) ?: error("stored null")
        val relPath = "note/${note.id}/${stored.name}"

        val progress = mutableListOf<Int>()
        val aspectCache = PageAspectCache()
        val sink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean =
                env.fileStore.writePageFileAtomically(note.id, notePageIndex) { it.writeBytes(byteArrayOf(1)) }
            override suspend fun insertPageBatch(
                startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int, reuseFirstPage: Boolean, newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(note.id, startNotePageIndex, relPath, pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount)
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
        }

        val outcome = PdfVectorImporter.importPages(
            pageCount = 20, reuseFirstPage = true, sink = sink, batchSize = 10,
            onProgress = { done, total ->
                assertEquals(20, total)
                progress.add(done)
                if (done % 2 == 1) aspectCache.put(relPath, done - 1, 595f, 842f)
                else aspectCache.put(relPath, done - 1, 960f, 540f)
            }
        )
        assertEquals(20, outcome.importedPages)
        assertEquals((1..20).toList(), progress)
        assertTrue(env.fileStore.getPageFile(note.id, 0).isFile)
        assertFalse(env.fileStore.getPageFile(note.id, 1).isFile)
        assertTrue((aspectCache.get(relPath, 1) ?: 0f) > 1.7f)

        val outline = PdfOutlineImporter.importNodes(
            PdfOutlineImporter.flattenTree(listOf(OutlineTreeNode("Unit 1", 0, listOf(OutlineTreeNode("Topic A", 5))))),
            pageCount = 20
        )
        assertEquals(listOf(0, 1), outline.map { it.level })
        assertTrue(outline.all { it.source == PdfOutlineImporter.SOURCE_PDF })
    }

    @Test
    fun `T3-12 PDF-01 plus PDF-03 plus PDF-02 text highlight continuous strokes and export policy compose cleanly`() {
        assertEquals(
            PdfTextSelectActivation.SWITCH_TO_SINGLE_AND_ACTIVATE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = true, isContinuous = true)
        )
        val range = PdfTextSelectionMath.range(10, 22)
        assertEquals(13, PdfTextSelectionMath.count(range))
        val canvasRect = PdfPageGeometry.userRectToCanvas(floatArrayOf(72f, 700f, 240f, 684f), 842f, 2f)
        val underline = PdfTextSelectionMath.underlineRect(canvasRect, 3f)
        assertEquals(3f, underline[3] - underline[1], 1e-4f)

        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("pdf", true))
        val stack = PageLayerStack.base()
        val op = E2eTestHarness.strokeOp(stack.layers.first())
        val decodedOps = ContinuousOpCodec.decode(ContinuousOpCodec.encode(listOf(op))).orEmpty()
        assertEquals(1, decodedOps.size)

        val (_, pages) = E2eTestHarness.pdfNoteFixture("t3-12", pageCount = 3)
        val src2 = PdfExportPolicy.resolveVectorPageSource(pages, 2)
        assertTrue(PdfExportPolicy.shouldUseVectorPdfBackground(src2, hasBackgroundOverride = false))
        val (w, h) = PdfExportPolicy.exportRenderTargetSize(595f, 842f)
        assertEquals(2160, w)
        assertTrue(h > 3000)

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(
            Regex("""flattenBitmap\(\s*strokeBmp,\s*pdfBackground,\s*layers,\s*beyondInk,\s*annotationLayer,\s*pageType,\s*includeMargins""")
                .containsMatchIn(exportManager)
        )
    }

    @Test
    fun `T3-13 PDF-QOL-3 plus PDF-QOL-4 search hit user rects align inside 16-by-9 continuous page box`() {
        PdfTextIndex.clearCache()
        try {
            val aspectCache = PageAspectCache()
            val ratio = aspectCache.put("slides.pdf", 1, 960f, 540f)
            val index = PdfTextIndex.buildIndex(2, pageTextProvider = { p ->
                if (p == 1) "Convolutional neural networks" else "Cover slide"
            }) ?: error("index null")
            val hits = PdfTextIndex.search(index, "convolutional")
            assertEquals(1, hits.size)
            assertEquals(1, hits.first().pageIndex)

            val boxRect = ContinuousPageGeometry.pageRect("pdf", ratio, 1000f, 1000f)
            val rects = PdfTextIndex.userRectsToBoxCanvasRects(
                userRects = listOf(floatArrayOf(120f, 400f, 480f, 370f)),
                pageWidthPts = 960f,
                pageHeightPts = 540f,
                pageLeftPx = boxRect.left,
                pageTopPx = boxRect.top,
                pageWidthPx = boxRect.width
            )
            assertEquals(1, rects.size)
            val r = rects.first()
            assertTrue(r[0] >= boxRect.left && r[2] <= boxRect.right)
            assertTrue(r[1] >= boxRect.top && r[3] <= boxRect.bottom)
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T3-14 PDF-QOL-6 plus PDF-QOL-7 margin stroke Lasso move and mirror update off-screen chevron`() {
        val margin = ContinuousPageGeometry.marginFor("pdf")
        val stroke = E2eTestHarness.strokeOp(
            PageLayerStack.base().layers.first(),
            points = listOf(InkPoint(-0.7f, 0.3f, 0.005f), InkPoint(-0.5f, 0.5f, 0.005f))
        ).stroke
        val poly = listOf(-0.9f to 0.1f, -0.3f to 0.1f, -0.3f to 0.7f, -0.9f to 0.7f)
        assertEquals(listOf(0), ContinuousStrokeTransformMath.selectStrokesInLasso(listOf(stroke), poly))

        val moved = ContinuousStrokeTransformMath.translatePoints(stroke.points, -1.0f, 0f, margin)
        assertEquals(-1.0f, moved[0].x, 1e-6f)
        val mirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(moved, centerXNorm = -0.6f, margin = margin)
        assertTrue(mirrored.all { it.x in -1.0f..2.0f })

        val presence = MarginExportMath.detectMarginInkFromNormalizedX(mirrored.map { it.x }, margin)
        val centered = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, presence)
        assertTrue(centered.showLeftOffscreenChevron)

        val pannedIntoView = MarginExportMath.computeGuideState("pdf", 1000f, 1000f, 1000f, presence)
        assertFalse(pannedIntoView.showLeftOffscreenChevron)
    }

    @Test
    fun `T3-15 PDF-QOL-2 plus PDF-QOL-3 cancelled import leaves no stale text index and retry builds cleanly`() = runTest {
        PdfTextIndex.clearCache()
        try {
            val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t3_15"))
            val (note, pages) = E2eTestHarness.pdfNoteFixture("t3-15", pageCount = 1)
            env.seedNoteAndPages(note, pages)

            var cancelAt2 = false
            val cancelSink = object : PdfVectorImporter.PdfImportSink {
                override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note.id)
                override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean = true
                override suspend fun insertPageBatch(
                    startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int, reuseFirstPage: Boolean, newTotalPageCount: Int
                ) {
                    env.repository.appendPdfPagesBatch(note.id, startNotePageIndex, "note/${note.id}/doc.pdf", pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount)
                }
                override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                    env.repository.permanentlyDeleteNote(note.id)
                }
            }
            val cancelled = PdfVectorImporter.importPages(
                pageCount = 10, reuseFirstPage = true, sink = cancelSink, batchSize = 2,
                onProgress = { done, _ -> if (done >= 2) cancelAt2 = true }, isCancelled = { cancelAt2 }
            )
            assertTrue(cancelled.cancelled)
            assertFalse(env.fileStore.getPdfTextIndexFile(note.id).exists())

            val (note2, pages2) = E2eTestHarness.pdfNoteFixture("t3-15-retry", pageCount = 1)
            env.seedNoteAndPages(note2, pages2)
            val descriptors = (0 until 4).map { PdfTextIndex.PdfPageDescriptor(it, "note/${note2.id}/doc.pdf", it) }
            val meta = PdfTextIndex.PdfSourceMeta("note/${note2.id}/doc.pdf", 2048L, 999L)
            val cacheFile = env.fileStore.getPdfTextIndexFile(note2.id)
            val built = PdfTextIndex.buildNoteIndex(descriptors, cacheFile, { meta }, { _, idx -> "Retry page $idx eigenvalue" })
                ?: error("index null")
            assertTrue(cacheFile.isFile)
            assertEquals(4, PdfTextIndex.search(built, "eigenvalue").size)
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T3-16 PDF-07 plus PDF-05 plus PDF-06 hiding Layer2 gates drawer thumbnails and Home card`() {
        val filesDir = tempFolder.newFolder("t3_16")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "t3-16")
        val stack = PageLayerStack.base()
        val l1 = stack.layers.first()
        val l2 = stack.add() ?: error("l2 missing")
        stack.setVisible(2, false)
        ws.writeContinuousPage(0, stack, mapOf(1 to listOf(E2eTestHarness.strokeOp(l1)), 2 to listOf(E2eTestHarness.strokeOp(l2))))

        val visibilityPlan = PdfModeReconciliationPolicy.planLayerVisibility(stack, hasSinglePageStrokes = true, floatingLayersVisible = emptyList())
        assertEquals(listOf(1), visibilityPlan.visibleDrawingLayerIndices)

        val mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        val thumbPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf", isVectorPdf = true, hasCoverOverride = false, hasPageFile = false,
            hasBackgroundOverride = false, hasStrokesFile = true, hasAnnotationLayers = mtimes.hasAnyAnnotationFiles,
            hasImageLayers = false, baseLayerVisible = true
        )
        assertTrue(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS in thumbPlan.overlays)

        val bitmapUtils = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/BitmapUtils.kt")
        assertTrue(bitmapUtils.contains("if (!layer.visible) continue"))
    }

    @Test
    fun `T3-17 PDF-QOL-5 plus PDF-05 plus PDF-QOL-4 scrubber preview handles 15 mixed portrait and landscape pages`() {
        val filesDir = tempFolder.newFolder("t3_17")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "t3-17")
        val cache = PageAspectCache()
        for (i in 0 until 15) {
            if (i % 2 == 0) cache.put("mixed.pdf", i, 595f, 842f)
            else cache.put("mixed.pdf", i, 960f, 540f)
        }
        val stack = PageLayerStack.base()
        ws.writeContinuousPage(7, stack, mapOf(1 to listOf(E2eTestHarness.strokeOp(stack.layers.first()))))

        assertTrue(PageScrubberMath.isScrubberEnabled(15))
        val target = PageScrubberMath.scrubFractionToPage(0.5f, 15)
        assertEquals(7, target)
        val aspect = cache.get("mixed.pdf", target) ?: error("missing aspect")
        assertEquals(340 to 191, PdfPageGeometry.thumbnailSize(aspect, 340))
        val mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, target)
        assertTrue(mtimes.hasAnyAnnotationFiles)
        val key = ThumbnailPolicy.buildPageThumbnailCacheKey("t3-17", target, "pdf", "mixed.pdf", target, mtimes, 1, 340)
        assertTrue(key.contains("#7:"))
    }

    @Test
    fun `T3-18 PDF-01 plus PDF-QOL-7 plus R4-AUDIT transform undo redo mode policy and static gates`() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val op = E2eTestHarness.strokeOp(stack.layers.first(), points = listOf(InkPoint(0.2f, 0.3f, 0.005f), InkPoint(0.5f, 0.3f, 0.005f)))
        history.commit(op)
        val before = op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        val transformed = ContinuousStrokeTransformMath.scalePoints(before, 1.5f, 1.5f, 0.2f, 0.3f, margin = 1.0f)
        op.stroke.points.clear()
        op.stroke.points.addAll(transformed)
        history.commitTransform(listOf(StrokeTransformSnapshot(op, before, transformed, null, null)))

        val encoded = ContinuousOpCodec.encode(history.ops)
        val decoded = ContinuousOpCodec.decode(encoded).orEmpty()
        assertEquals(1, decoded.size)
        assertEquals(0.65f, (decoded.first() as InkOp.Stroke).stroke.points[1].x, 1e-5f)
        assertEquals(1f / 3f to 2f / 3f, PdfModeReconciliationPolicy.singlePageCropFractions("pdf"))

        val root = E2eTestHarness.locateRepoRoot()
        assertEquals(0, E2eTestHarness.scanKtFilesForToken(File(root, "app/src"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP).size)
        assertEquals(0, E2eTestHarness.scanKtFilesForToken(File(root, "app/src/main"), "runBlocking").size)
        val audit = E2eTestHarness.readRepoFile("state/AUDIT.md")
        assertTrue(E2eTestHarness.ALL_15_FEATURE_IDS.all { E2eTestHarness.extractAuditSection(audit, it).contains("IMPLEMENTED") })
    }
}
