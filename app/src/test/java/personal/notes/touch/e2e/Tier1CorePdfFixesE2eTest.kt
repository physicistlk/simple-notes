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
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousInkMath
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkCropMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.InkToolSettings
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.LayerTileMapping
import personal.notes.touch.ui.canvas.LayerTilePatch
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.ReaderTransform
import personal.notes.touch.util.ExportManager
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfModeReconciliationPolicy
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfRenderPolicy
import personal.notes.touch.util.PdfTextSelectActivation
import personal.notes.touch.util.PdfTextSelectionMath
import personal.notes.touch.util.PdfTextToolPolicy
import personal.notes.touch.util.TextDockTapAction
import personal.notes.touch.util.ThumbnailPolicy
import kotlin.math.abs

/**
 * Tier 1 E2E Tests — Core PDF Fixes (`PDF-01` .. `PDF-08`), Phase A baseline.
 * Covers primary happy-path behavior using existing pure-JVM production APIs.
 */
class Tier1CorePdfFixesE2eTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- PDF-01: Preserve Continuous-Mode Strokes on Mode Switch ---

    @Test
    fun `T1-PDF01-01 multi-tool InkOp stroke list survives ContinuousOpCodec round-trip`() {
        val layer = PageLayerStack.base().layers.first()
        val fountainW1 = ContinuousInkMath.fountainWidth(
            pressure = 0.25f, baseNorm = 0.006f, sensitivity = 1.0f, sizeEnabled = true
        )
        val fountainW2 = ContinuousInkMath.smooth(
            previous = fountainW1,
            target = ContinuousInkMath.fountainWidth(
                pressure = 0.9f, baseNorm = 0.006f, sensitivity = 1.0f, sizeEnabled = true
            )
        )
        assertTrue(fountainW2 > fountainW1)
        assertEquals(
            0.006f,
            ContinuousInkMath.fountainWidth(0.25f, 0.006f, 1.0f, sizeEnabled = false),
            1e-6f
        )
        assertEquals(0.004f, ContinuousInkMath.smooth(previous = 0f, target = 0.004f), 1e-6f)

        val ops = listOf(
            E2eTestHarness.strokeOp(
                layer = layer,
                tool = CanvasTool.PEN,
                color = 0xFF112233.toInt(),
                widthNorm = 0.004f,
                autoShape = false,
                points = listOf(InkPoint(0.1f, 0.2f, 0.004f), InkPoint(0.4f, 0.6f, 0.0045f))
            ),
            E2eTestHarness.strokeOp(
                layer = layer,
                tool = CanvasTool.HIGHLIGHTER,
                color = 0x66FFEE00,
                widthNorm = 0.022f,
                autoShape = false,
                points = listOf(InkPoint(0.2f, 0.5f, 0.022f), InkPoint(0.8f, 0.5f, 0.022f))
            ),
            E2eTestHarness.strokeOp(
                layer = layer,
                tool = CanvasTool.FOUNTAIN_PEN,
                color = 0xFF000000.toInt(),
                widthNorm = 0.006f,
                autoShape = true,
                points = listOf(InkPoint(0.3f, 0.1f, fountainW1), InkPoint(0.7f, 0.9f, fountainW2))
            )
        )

        val encoded = ContinuousOpCodec.encode(ops)
        val decoded = ContinuousOpCodec.decode(encoded)
        assertNotNull(decoded)
        val result = decoded ?: error("decoded must not be null")
        assertEquals(3, result.size)

        ops.forEachIndexed { index, expectedOp ->
            val expected = expectedOp.stroke
            val actual = (result[index] as InkOp.Stroke).stroke
            assertEquals(expected.tool, actual.tool)
            assertEquals(expected.color, actual.color)
            assertEquals(expected.baseWidthNorm, actual.baseWidthNorm, 1e-6f)
            assertEquals(expected.autoShapeEnabled, actual.autoShapeEnabled)
            assertEquals(expected.points.size, actual.points.size)
            expected.points.forEachIndexed { pIdx, ep ->
                val ap = actual.points[pIdx]
                assertEquals(ep.x, ap.x, 1e-6f)
                assertEquals(ep.y, ap.y, 1e-6f)
                assertEquals(ep.w, ap.w, 1e-6f)
            }
        }
    }

    @Test
    fun `T1-PDF01-02 normalized PDF page and margin coordinates map into expected layer thirds`() {
        val ratio = ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        val boxW = 1000f
        val boxH = 1000f
        val layerSize = ContinuousPageGeometry.layerSize("pdf", ratio)
        val layerRect = ContinuousPageGeometry.layerRect("pdf", ratio, boxW, boxH)
        val pageRect = ContinuousPageGeometry.pageRect("pdf", ratio, boxW, boxH)

        val onPageX = pageRect.left + pageRect.width * 0.25f
        val onPageY = pageRect.top + pageRect.height * 0.5f
        val onPagePt = ContinuousPageGeometry.layerPoint(
            layerRect, onPageX, onPageY, layerSize.width, layerSize.height
        )
        assertTrue(onPagePt.x >= layerSize.width / 3f && onPagePt.x <= 2f * layerSize.width / 3f)
        assertEquals(layerSize.height * 0.5f, onPagePt.y, 1f)

        val leftMarginX = pageRect.left - pageRect.width * 0.4f
        val leftMarginPt = ContinuousPageGeometry.layerPoint(
            layerRect, leftMarginX, onPageY, layerSize.width, layerSize.height
        )
        assertTrue(leftMarginPt.x >= 0f && leftMarginPt.x < layerSize.width / 3f)
    }

    @Test
    fun `T1-PDF01-03 InkHistory commit and reload via ContinuousOpCodec preserves surviving ops`() {
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()
        val history = InkHistory(stack)

        val s1 = E2eTestHarness.strokeOp(layer, listOf(InkPoint(0.1f, 0.1f, 0.005f), InkPoint(0.2f, 0.2f, 0.005f)))
        val s2 = E2eTestHarness.strokeOp(layer, listOf(InkPoint(0.3f, 0.3f, 0.005f), InkPoint(0.4f, 0.4f, 0.005f)))
        val s3 = E2eTestHarness.strokeOp(layer, listOf(InkPoint(0.5f, 0.5f, 0.005f), InkPoint(0.6f, 0.6f, 0.005f)))
        val e1 = E2eTestHarness.eraseOp(layer)

        history.commit(s1)
        history.commit(s2)
        history.commit(s3)
        history.commit(e1)
        assertEquals(4, history.ops.size)

        val payload = ContinuousOpCodec.encode(history.ops)
        val reloaded = ContinuousOpCodec.decode(payload).orEmpty()
        val freshHistory = InkHistory(PageLayerStack.base())
        reloaded.forEach { freshHistory.commit(it) }

        assertEquals(4, freshHistory.ops.size)
        val expectedStrokes = listOf(s1, s2, s3)
        expectedStrokes.forEachIndexed { idx, expectedOp ->
            val actualOp = freshHistory.ops[idx] as InkOp.Stroke
            assertEquals(expectedOp.stroke.tool, actualOp.stroke.tool)
            assertEquals(expectedOp.stroke.points.size, actualOp.stroke.points.size)
            expectedOp.stroke.points.forEachIndexed { pIdx, ep ->
                val ap = actualOp.stroke.points[pIdx]
                assertEquals(ep.x, ap.x, 1e-6f)
                assertEquals(ep.y, ap.y, 1e-6f)
                assertEquals(ep.w, ap.w, 1e-6f)
            }
        }

        val decodedErase = freshHistory.ops[3] as InkOp.Erase
        assertEquals(CanvasTool.ERASER, decodedErase.stroke.tool)
        assertEquals(e1.stroke.baseWidthNorm, decodedErase.stroke.baseWidthNorm, 1e-6f)
        assertEquals(e1.stroke.points.size, decodedErase.stroke.points.size)
        e1.stroke.points.forEachIndexed { pIdx, ep ->
            val ap = decodedErase.stroke.points[pIdx]
            assertEquals(ep.x, ap.x, 1e-6f)
            assertEquals(ep.y, ap.y, 1e-6f)
            assertEquals(ep.w, ap.w, 1e-6f)
        }
    }

    // --- PDF-02: Multi-Page Vector PDF Export (Pages 2..N) ---

    @Test
    fun `T1-PDF02-01 PdfRenderPolicy caps and preserves A4 ratio across 5 exported pages`() {
        val pageW = 595f
        val pageH = 842f
        val expectedRatio = pageW / pageH
        for (pageIndex in 0 until 5) {
            val scale = PdfRenderPolicy.capPxPerPt(pageW, pageH, 3.0f)
            assertTrue("page $pageIndex scale must be positive", scale > 0f)
            val size = PdfRenderPolicy.outputSize(pageW, pageH, scale)
            assertTrue(size[0] <= PdfRenderPolicy.MAX_DIMENSION)
            assertTrue(size[1] <= PdfRenderPolicy.MAX_DIMENSION)
            assertTrue(size[0].toLong() * size[1].toLong() <= PdfRenderPolicy.MAX_PIXELS)
            val actualRatio = size[0].toFloat() / size[1].toFloat()
            assertEquals(expectedRatio, actualRatio, 0.01f)
        }
    }

    @Test
    fun `T1-PDF02-02 PdfPageGeometry canvasScale scales consistently across target widths`() {
        val widths = listOf(1080, 2160, 3000)
        for (outW in widths) {
            val scale = PdfPageGeometry.canvasScale(595f, outW)
            assertEquals(outW / 595f, scale, 1e-6f)
        }
    }

    // --- PDF-03: PDF Text Selection Tool (F-10) Reachable from Dock ---

    @Test
    fun `T1-PDF03-01 PdfTextSelectionMath range handles forward and backward drag symmetrically`() {
        val forward = PdfTextSelectionMath.range(anchor = 5, focus = 14)
        val backward = PdfTextSelectionMath.range(anchor = 14, focus = 5)
        assertEquals(5..14, forward)
        assertEquals(5..14, backward)
        assertEquals(10, PdfTextSelectionMath.count(forward))
        assertEquals(10, PdfTextSelectionMath.count(backward))
    }

    @Test
    fun `T1-PDF03-02 PdfPageGeometry userRectToCanvas and underlineRect align in top-down pixels`() {
        val userRect = floatArrayOf(72f, 720f, 300f, 700f)
        val canvasRect = PdfPageGeometry.userRectToCanvas(userRect, pageHeightPts = 842f, pxPerPt = 2f)
        assertEquals(144f, canvasRect[0], 1e-4f)
        assertEquals(244f, canvasRect[1], 1e-4f)
        assertEquals(600f, canvasRect[2], 1e-4f)
        assertEquals(284f, canvasRect[3], 1e-4f)

        val underline = PdfTextSelectionMath.underlineRect(canvasRect, thickness = 3f)
        assertEquals(144f, underline[0], 1e-4f)
        assertEquals(281f, underline[1], 1e-4f)
        assertEquals(600f, underline[2], 1e-4f)
        assertEquals(284f, underline[3], 1e-4f)
    }

    @Test
    fun `T1-PDF03-03 PdfTextSelectionMath tolerancePageUnits scales inversely with zoom`() {
        assertEquals(24f, PdfTextSelectionMath.tolerancePageUnits(24f, 1f), 1e-6f)
        assertEquals(12f, PdfTextSelectionMath.tolerancePageUnits(24f, 2f), 1e-6f)
        assertEquals(6f, PdfTextSelectionMath.tolerancePageUnits(24f, 4f), 1e-6f)
    }

    // --- PDF-04: Lasso & Arrow Selection in Continuous PDF Mode ---

    @Test
    fun `T1-PDF04-01 InkToolSettings classifies LASSO and SELECT as non-writing and non-laser`() {
        val lasso = InkToolSettings(CanvasTool.LASSO, 0, 4f, 16f, 1f, false)
        val arrow = InkToolSettings(CanvasTool.SELECT, 0, 4f, 16f, 1f, false)

        assertFalse(lasso.isWritingTool)
        assertFalse(lasso.isLaserPointer)
        assertFalse(arrow.isWritingTool)
        assertFalse(arrow.isLaserPointer)
    }

    @Test
    fun `T1-PDF04-02 InkHistory commitMove dispatches inverse undo and forward redo deltas`() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val op = E2eTestHarness.strokeOp(stack.layers.first())
        history.commit(op)
        history.commitMove(listOf(op), dx = 0.15f, dy = -0.1f)

        var lastDx = 0f
        var lastDy = 0f
        var movedCount = 0
        assertTrue(
            history.undo(
                onErase = { _, _ -> },
                onClear = { _, _ -> },
                onMove = { ops, dx, dy ->
                    movedCount = ops.size
                    lastDx = dx
                    lastDy = dy
                }
            )
        )
        assertEquals(1, movedCount)
        assertEquals(-0.15f, lastDx, 1e-6f)
        assertEquals(0.1f, lastDy, 1e-6f)

        assertTrue(
            history.redo(
                onErase = { _, _ -> },
                onClear = { _, _ -> },
                onMove = { ops, dx, dy ->
                    movedCount = ops.size
                    lastDx = dx
                    lastDy = dy
                }
            )
        )
        assertEquals(1, movedCount)
        assertEquals(0.15f, lastDx, 1e-6f)
        assertEquals(-0.1f, lastDy, 1e-6f)
    }

    @Test
    fun `T1-PDF04-03 ContinuousPageGeometry and ContinuousInkMath strokeBounds tightly enclose multi-point stroke`() {
        val pts = listOf(
            InkPoint(0.2f, 0.3f, 0.01f),
            InkPoint(0.4f, 0.6f, 0.02f)
        )
        val bounds = ContinuousPageGeometry.strokeBounds(pts, boxWidth = 1000f, boxHeight = 1400f, padFactor = 0.5f, extraPx = 4f)
        assertNotNull(bounds)
        val b = bounds ?: error("bounds must not be null")
        assertTrue(b.left < 0.2f)
        assertTrue(b.top < 0.3f)
        assertTrue(b.right > 0.4f)
        assertTrue(b.bottom > 0.6f)
        assertFalse(b.empty)

        val pxBounds = ContinuousInkMath.strokeBounds(
            points = pts, maxWidthNorm = 0.01f, bitmapWidth = 6480, bitmapHeight = 3056
        )
        assertNotNull(pxBounds)
        val pb = pxBounds ?: error("pixel bounds must not be null")
        assertTrue(pb.left in 0 until (0.2f * 6480).toInt())
        assertTrue(pb.top in 0 until (0.3f * 3056).toInt())
        assertTrue(pb.right in ((0.4f * 6480).toInt() + 1)..6480)
        assertTrue(pb.bottom in ((0.6f * 3056).toInt() + 1)..3056)
        assertTrue(pb.width > 0 && pb.height > 0)
    }

    // --- PDF-05: Continuous-Mode Annotations in Page Drawer Thumbnails ---

    @Test
    fun `T1-PDF05-01 PageLayerMeta round-trip excludes hidden Layer2 from visibleLayers`() {
        val stack = PageLayerStack.base()
        val l2 = stack.add()
        assertNotNull(l2)
        stack.setVisible(2, false)

        val encoded = PageLayerMeta.encode(stack)
        val decoded = PageLayerMeta.decode(encoded)
        assertNotNull(decoded)
        val d = decoded ?: error("decoded must not be null")
        assertEquals(2, d.size)
        val visible = d.visibleLayers()
        assertEquals(1, visible.size)
        assertEquals(1, visible.first().index)
    }

    @Test
    fun `T1-PDF05-02 LayerTileMapping and LayerTilePatch project tile 1_0 of 6480x3056 layer`() {
        val grid = LayerTileGrid(6480, 3056)
        val layerRect = ContinuousPageGeometry.layerRect("pdf", ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 240f, 340f)
        val rect = LayerTileMapping.tileRect(grid, tx = 1, ty = 0, layerRect = layerRect, viewWidth = 240f, viewHeight = 340f)

        assertFalse(rect.empty)
        assertTrue(rect.left >= layerRect.left * 240f)
        assertTrue(rect.right <= layerRect.right * 240f + 0.01f)
        assertTrue(rect.top >= 0f && rect.bottom <= 340f + 0.01f)
        val expectedW = (1024f / 6480f) * layerRect.width * 240f
        assertEquals(expectedW, rect.width, 0.05f)

        // Erase rect crossing tile (1, 0) [1024..2048) into tile (2, 0) [2048..3072)
        val inTile1 = LayerTilePatch.rectInTile(grid, tx = 1, ty = 0, left = 1800, top = 100, right = 2200, bottom = 300)
        assertNotNull(inTile1)
        val t1 = inTile1 ?: error("tile patch for (1,0) must not be null")
        assertEquals(listOf(776, 100, 1024, 300), t1.toList())

        val inLayer1 = LayerTilePatch.rectInLayer(grid, tx = 1, ty = 0, left = 1800, top = 100, right = 2200, bottom = 300)
        assertNotNull(inLayer1)
        assertEquals(listOf(1800, 100, 2048, 300), (inLayer1 ?: error("layer patch null")).toList())

        val disjoint = LayerTilePatch.rectInTile(grid, tx = 0, ty = 0, left = 1800, top = 100, right = 2200, bottom = 300)
        assertEquals(null, disjoint)
    }

    @Test
    fun `T1-PDF05-03 decoded ops preserve coordinates and InkCropMath classifies on-paper vs off-paper crops`() {
        val layer = PageLayerStack.base().layers.first()
        val centerStroke = E2eTestHarness.strokeOp(
            layer = layer,
            points = listOf(InkPoint(0.45f, 0.25f, 0.005f), InkPoint(0.55f, 0.75f, 0.005f))
        )
        val marginStroke = E2eTestHarness.strokeOp(
            layer = layer,
            points = listOf(InkPoint(-0.5f, 0.3f, 0.005f), InkPoint(-0.2f, 0.6f, 0.005f))
        )
        val decoded = ContinuousOpCodec.decode(ContinuousOpCodec.encode(listOf(centerStroke, marginStroke))).orEmpty()
        assertEquals(2, decoded.size)
        val dCenter = (decoded[0] as InkOp.Stroke).stroke
        val dMargin = (decoded[1] as InkOp.Stroke).stroke
        assertEquals(-0.5f, dMargin.points[0].x, 1e-6f)
        assertEquals(-0.2f, dMargin.points[1].x, 1e-6f)

        val pageW = 2160
        val pageH = 3056
        val centerBox = InkCropMath.strokeBounds(
            xs = dCenter.points.map { it.x * pageW }.toFloatArray(),
            ys = dCenter.points.map { it.y * pageH }.toFloatArray(),
            widest = dCenter.baseWidthNorm * pageW
        ) ?: error("centerBox must not be null")
        assertTrue(InkCropMath.isOnPaper(centerBox, pageW, pageH))
        assertFalse(InkCropMath.isOffPaper(centerBox, pageW, pageH))

        val marginBox = InkCropMath.strokeBounds(
            xs = dMargin.points.map { it.x * pageW }.toFloatArray(),
            ys = dMargin.points.map { it.y * pageH }.toFloatArray(),
            widest = dMargin.baseWidthNorm * pageW
        ) ?: error("marginBox must not be null")
        assertTrue(InkCropMath.isOffPaper(marginBox, pageW, pageH))
        assertFalse(InkCropMath.isOnPaper(marginBox, pageW, pageH))
        assertTrue(
            InkCropMath.cropCoversStroke(
                left = 0, top = 0,
                cropWidth = marginBox.width, cropHeight = marginBox.height,
                strokeRight = marginBox.width, strokeBottom = marginBox.height
            )
        )
        assertFalse(
            InkCropMath.cropCoversStroke(
                left = 0, top = 0,
                cropWidth = marginBox.width - 10, cropHeight = marginBox.height,
                strokeRight = marginBox.width, strokeBottom = marginBox.height
            )
        )
        assertEquals(null, InkCropMath.strokeBounds(FloatArray(0), FloatArray(0), 5f))
    }

    // --- PDF-06: Home Screen Library Card Thumbnail Refresh ---

    @Test
    fun `T1-PDF06-01 page-0 entity schema and production annotation layer tile metadata contracts`() {
        val (note, pages) = E2eTestHarness.pdfNoteFixture(noteId = "home-card-1", pageCount = 3)
        assertEquals("pdf", note.pageType)
        assertEquals(3, note.pageCount)
        assertEquals(0, note.lastPageIndex)
        assertTrue(note.updatedAt > 0L)
        assertEquals(3, pages.size)
        assertEquals(0, pages[0].index)
        assertEquals(0, pages[0].pdfPageIndex)
        assertEquals("note/home-card-1/page_0.png", pages[0].bitmapPath)
        assertEquals("note/home-card-1/pdf_1700000000000.pdf", pages[0].pdfSourcePath)

        // Production page-0 thumbnail geometry and layer tile readiness
        val ratio = ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        val thumbW = 240f
        val thumbH = 340f
        val pageRect = ContinuousPageGeometry.pageRect(note.pageType, ratio, thumbW, thumbH)
        val layerRect = ContinuousPageGeometry.layerRect(note.pageType, ratio, thumbW, thumbH)
        val layerSize = ContinuousPageGeometry.layerSize(note.pageType, ratio)
        assertEquals(layerRect.left + layerRect.width / 3f, pageRect.left, 1e-4f)

        val onPageX = pageRect.left + pageRect.width * 0.5f
        val onPageY = pageRect.top + pageRect.height * 0.5f
        val layerPt = ContinuousPageGeometry.layerPoint(
            layerRect, onPageX, onPageY, layerSize.width, layerSize.height
        )
        val grid = LayerTileGrid(layerSize.width, layerSize.height)
        val touched = grid.tilesForRect(
            layerPt.x.toInt(), layerPt.y.toInt(),
            layerPt.x.toInt() + 8, layerPt.y.toInt() + 8
        )
        assertTrue(touched.isNotEmpty())
        val tileNames = touched.map { idx -> grid.tileName(grid.tileColumn(idx), grid.tileRow(idx)) }

        val stack = PageLayerStack.base()
        val p0Op = E2eTestHarness.strokeOp(
            layer = stack.layers.first(),
            points = listOf(InkPoint(0.4f, 0.4f, 0.005f), InkPoint(0.6f, 0.6f, 0.005f))
        )
        val tilesInfo = PageLayerMeta.LayerTilesInfo(
            width = layerSize.width,
            height = layerSize.height,
            tileSize = grid.tileSize,
            tiles = tileNames
        )
        val metaJson = PageLayerMeta.encode(stack, mapOf(1 to tilesInfo))
        val opsJson = ContinuousOpCodec.encode(listOf(p0Op))

        val decodedStack = PageLayerMeta.decode(metaJson) ?: error("page-0 stack decode failed")
        val decodedTiles = PageLayerMeta.decodeTiles(metaJson)
        val decodedOps = ContinuousOpCodec.decode(opsJson) ?: error("page-0 ops decode failed")
        assertEquals(1, decodedStack.visibleLayers().size)
        assertFalse(PageLayerMeta.isLegacyLayer(decodedTiles, 1))
        assertEquals(touched.size, decodedTiles[1]?.positions()?.size)
        assertEquals(1, decodedOps.size)
        assertEquals(0.4f, (decodedOps[0] as InkOp.Stroke).stroke.points[0].x, 1e-6f)
    }

    @Test
    fun `T1-PDF06-02 page-0 annot metadata and ops JSON round-trip on temp disk workspace`() {
        val root = tempFolder.newFolder("filesDir2")
        val ws = E2eTestHarness.createTempNoteWorkspace(root, "home-card-2")
        val stack = PageLayerStack.base()
        val op = E2eTestHarness.strokeOp(stack.layers.first())
        ws.writeContinuousPage(0, stack, mapOf(1 to listOf(op)))

        assertTrue(ws.annotMetaFile(0).isFile)
        assertTrue(ws.annotOpsFile(0, 1).isFile)

        val (readStack, readOps) = ws.readContinuousPage(0)
        assertEquals(1, readStack.visibleLayers().size)
        assertEquals(1, readOps[1]?.size)
    }

    // --- PDF-07: Reconcile Continuous & Single-Page Layer Systems ---

    @Test
    fun `T1-PDF07-01 3-layer stack visibility and currentIndex survive PageLayerMeta round-trip`() {
        val stack = PageLayerStack.base()
        stack.add()
        stack.add()
        stack.setVisible(2, false)
        stack.select(1)

        val decoded = PageLayerMeta.decode(PageLayerMeta.encode(stack))
        assertNotNull(decoded)
        val d = decoded ?: error("decoded must not be null")
        assertEquals(3, d.size)
        assertEquals(1, d.currentIndex)
        assertTrue(d.layers[0].visible)
        assertFalse(d.layers[1].visible)
        assertTrue(d.layers[2].visible)
    }

    @Test
    fun `T1-PDF07-02 merging Layer2 into base re-homes ops and undo restores Layer2 ownership`() {
        val stack = PageLayerStack.base()
        val base = stack.layers.first()
        val l2 = stack.add() ?: error("layer 2 must be added")
        val history = InkHistory(stack)
        val opOn2 = E2eTestHarness.strokeOp(l2)
        history.commit(opOn2)

        history.commitRemoveLayer(l2, merged = true)
        assertEquals(1, stack.size)
        assertTrue(opOn2.layer === base)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(2, stack.size)
        assertTrue(opOn2.layer === l2)
    }

    @Test
    fun `T1-PDF07-03 PageLayerMeta LayerTilesInfo round-trips dimensions and tile coordinates`() {
        val stack = PageLayerStack.base()
        val tilesInfo = PageLayerMeta.LayerTilesInfo(
            width = 6480,
            height = 3056,
            tileSize = 1024,
            tiles = listOf("t0_0", "t2_1", "t5_2")
        )
        val json = PageLayerMeta.encode(stack, mapOf(1 to tilesInfo))
        val decodedTiles = PageLayerMeta.decodeTiles(json)
        assertEquals(1, decodedTiles.size)
        val info = decodedTiles[1] ?: error("tiles info for layer 1 missing")
        assertEquals(6480, info.width)
        assertEquals(3056, info.height)
        assertEquals(1024, info.tileSize)
        assertEquals(listOf(0 to 0, 2 to 1, 5 to 2), info.positions())
        assertFalse(PageLayerMeta.isLegacyLayer(decodedTiles, 1))
    }

    // --- PDF-08: Continuous Reader Pointer Handler Cleanup ---

    @Test
    fun `T1-PDF08-01 ReaderTransform apply preserves centroid screen coordinate under 2x pinch zoom`() {
        val layout = ReaderTransform.Layout(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            horizontal = false,
            pageWidth = 1080f,
            pageHeight = 1528f,
            originX = 0f,
            originY = 196f,
            pagesBefore = 0
        )
        val state = ReaderTransform.State(scale = 1f, panX = 0f, panY = 0f)
        val centroidX = 300f
        val centroidY = 400f
        val beforeContent = ReaderTransform.contentPoint(layout, state.scale, centroidX, centroidY)

        val res = ReaderTransform.apply(
            state = state,
            gesture = ReaderTransform.Gesture(centroidX, centroidY, panX = 0f, panY = 0f, zoom = 2f),
            layout = layout
        )
        assertEquals(2f, res.state.scale, 1e-5f)
        val afterScreen = ReaderTransform.screenPoint(
            res.originX, res.originY, res.state.scale, beforeContent.first, beforeContent.second
        )
        assertTrue(abs(afterScreen.first - centroidX) < 0.01f)
        assertTrue(abs(afterScreen.second - centroidY) < 0.01f)
    }

    @Test
    fun `T1-PDF08-02 ReaderTransform routes cross-axis pan to panX in vertical and panY in horizontal`() {
        val state = ReaderTransform.State(scale = 1f, panX = 0f, panY = 0f)
        val vLayout = ReaderTransform.Layout(1000f, 1600f, horizontal = false, 1000f, 1400f, 0f, 100f)
        val vRes = ReaderTransform.apply(state, ReaderTransform.Gesture(500f, 800f, 120f, 40f, 1f), vLayout)
        assertEquals(120f, vRes.state.panX, 0.01f)
        assertEquals(0f, vRes.state.panY, 0.01f)

        val hLayout = ReaderTransform.Layout(1600f, 1000f, horizontal = true, 1400f, 1000f, 100f, 0f)
        val hRes = ReaderTransform.apply(state, ReaderTransform.Gesture(800f, 500f, 40f, -90f, 1f), hLayout)
        assertEquals(0f, hRes.state.panX, 0.01f)
        assertEquals(-90f, hRes.state.panY, 0.01f)
    }

    @Test
    fun `T1-PDF08-03 ReaderTransform resetZoom and centre mutate only their respective axes`() {
        val state = ReaderTransform.State(scale = 3.2f, panX = 140f, panY = -80f)
        val reset = ReaderTransform.resetZoom(state)
        assertEquals(1f, reset.scale, 1e-6f)
        assertEquals(140f, reset.panX, 1e-6f)
        assertEquals(-80f, reset.panY, 1e-6f)

        val centred = ReaderTransform.centre(state)
        assertEquals(3.2f, centred.scale, 1e-6f)
        assertEquals(0f, centred.panX, 1e-6f)
        assertEquals(0f, centred.panY, 1e-6f)
    }

    // --- PHASE B: PDF-01 Mode Switch Preservation ---

    @Test
    fun `T1-PDF01-04 Continuous to Single-Page mode switch wires annotationOverlay for PDF notes`() {
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("pdf", isVectorPdf = true))
        assertEquals(1f / 3f to 2f / 3f, PdfModeReconciliationPolicy.singlePageCropFractions("pdf"))
        assertEquals(2160 to 3056, PdfModeReconciliationPolicy.pagePixelSize(ContinuousPageGeometry.DEFAULT_PAGE_RATIO))

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("suspend fun reloadSinglePageOverlaysAndLayers("))
        assertTrue(canvasScreen.contains("PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("))
        assertTrue(canvasScreen.contains("inkController.composeAllLayersForSinglePage("))
        assertTrue(canvasScreen.contains("canvas.annotationOverlay = overlay"))
        assertTrue(canvasScreen.contains("inkController.releaseSessionSuspend()"))
    }

    @Test
    fun `T1-PDF01-05 Single-Page to Continuous round-trip keeps strokes non-destructive and ops persisted`() = runTest {
        assertFalse(PdfModeReconciliationPolicy.continuousPdfRenderIncludesInk())
        assertTrue(PdfModeReconciliationPolicy.shouldDrawSinglePageStrokes(hasStrokes = true, baseLayerVisible = true))
        assertEquals(
            PdfModeReconciliationPolicy.MergeTarget.STROKES_FILE,
            PdfModeReconciliationPolicy.resolveMergeTarget(isVectorPdf = true)
        )

        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("pdf01_05"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("pdf01-05", pageCount = 2)
        env.seedNoteAndPages(note, pages)
        val layer = PageLayerStack.base().layers.first()
        val op = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.2f, 0.3f, 0.005f), InkPoint(0.7f, 0.8f, 0.005f)))
        assertTrue(env.repository.savePageLayerOps(note.id, 0, 1, listOf(op)))
        val reloaded = env.repository.loadPageLayerOps(note.id, 0, 1)
        assertEquals(1, reloaded?.size)
        assertTrue(env.fileStore.getStrokesFile(note.id, 0).name == "strokes_0.png")

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val inkController = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        assertTrue(canvasScreen.contains("onZoomOutToContinuous ="))
        assertTrue(canvasScreen.contains("flushCurrentNote()"))
        assertTrue(inkController.contains("PdfModeReconciliationPolicy.continuousPdfRenderIncludesInk()"))
        assertTrue(inkController.contains("s.singlePageStrokes = spStrokes"))
    }

    // --- PHASE B: PDF-02 Multi-Page Vector Export ---

    @Test
    fun `T1-PDF02-03 multi-page vector PDF resolves page sources 0 to 3 without raster pages 1 to 3`() {
        val filesDir = tempFolder.newFolder("pdf02_03")
        val fileStore = NoteFileStore(filesDir)
        val (note, pages) = E2eTestHarness.pdfNoteFixture("pdf02-03", pageCount = 4)
        fileStore.writePageFileAtomically(note.id, 0) { it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(fileStore.getPageFile(note.id, 0).isFile)
        assertFalse(fileStore.getPageFile(note.id, 1).isFile)
        assertFalse(fileStore.getPageFile(note.id, 2).isFile)
        assertFalse(fileStore.getPageFile(note.id, 3).isFile)

        for (i in 0 until 4) {
            val src = PdfExportPolicy.resolveVectorPageSource(pages, i)
            assertNotNull(src)
            assertEquals(i, src?.pdfPageIndex)
            assertTrue(PdfExportPolicy.shouldUseVectorPdfBackground(src, hasBackgroundOverride = false))
        }
        val (w, h) = PdfExportPolicy.exportRenderTargetSize(595f, 842f, baseWidthPx = 2160)
        assertEquals(2160, w)
        assertTrue(h in 3055..3058)

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportManager.contains("PdfExportPolicy.shouldUseVectorPdfBackground(source, backgroundOverride != null)"))
        assertTrue(exportManager.contains("doc.renderPage(source.pdfPageIndex, targetW, targetH)"))
    }

    @Test
    fun `T1-PDF02-04 multi-page annotated PDF export composites strokes and ops across pages 0 to 3`() {
        val filesDir = tempFolder.newFolder("pdf02_04")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "pdf02-04")
        val (_, pages) = E2eTestHarness.pdfNoteFixture("pdf02-04", pageCount = 4)
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()

        for (i in 0 until 4) {
            ws.strokesFile(i).writeBytes(byteArrayOf(i.toByte(), 9))
            val op = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.1f + i * 0.1f, 0.2f, 0.005f)))
            ws.writeContinuousPage(i, stack, mapOf(1 to listOf(op)))
            assertNotNull(PdfExportPolicy.resolveVectorPageSource(pages, i))
            val (_, readOps) = ws.readContinuousPage(i)
            assertEquals(1, readOps[1]?.size)
        }

        val layerSize = PdfExportPolicy.resolveAnnotationLayerSize("pdf", ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 0, 0)
        assertEquals(6480, layerSize.width)
        assertEquals(3056, layerSize.height)
        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(0, 0, 595f, 842f))

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportManager.contains("for (pageIdx in 0 until pageCount)"))
        assertTrue(exportManager.contains("loadFlattenedPageBitmap("))
        assertTrue(exportManager.contains("pdfDocs.values.forEach { it.close() }"))
    }

    @Test
    fun `T1-PDF02-05 single-page PNG export on page 3 resolves pageIndex 2 vector background and filename`() {
        val filesDir = tempFolder.newFolder("pdf02_05")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "pdf02-05")
        val (note, pages) = E2eTestHarness.pdfNoteFixture("pdf02-05", title = "Topology: Lecture 3?", pageCount = 4)
        val src = PdfExportPolicy.resolveVectorPageSource(pages, 2)
        assertNotNull(src)
        assertEquals(2, src?.pdfPageIndex)
        assertEquals("Topology_ Lecture 3_", ExportManager.sanitizeFilename(note.title))

        val stack = PageLayerStack.base()
        val op = E2eTestHarness.strokeOp(stack.layers.first())
        ws.strokesFile(2).writeBytes(byteArrayOf(4, 5, 6))
        ws.writeContinuousPage(2, stack, mapOf(1 to listOf(op)))
        assertEquals(1, ws.readContinuousPage(2).second[1]?.size)

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        val exportDialog = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/export/ExportDialog.kt")
        assertTrue(exportManager.contains("suspend fun exportPageToPngFile("))
        assertTrue(exportDialog.contains("ExportManager.loadFlattenedPageBitmap("))
    }

    // --- PHASE B: PDF-03 Dock Text Selection Tool ---

    @Test
    fun `T1-PDF03-04 dock Text tool activates PDF text selection on extractable PDF in both docks`() {
        assertTrue(PdfTextToolPolicy.isAvailable(isVectorPdfPage = true, charCount = 42))
        assertEquals(TextDockTapAction.OPEN_TEXT_MENU, PdfTextToolPolicy.dockTapAction(pdfTextAvailable = true, pdfTextMode = false))
        assertEquals(
            PdfTextSelectActivation.SWITCH_TO_SINGLE_AND_ACTIVATE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = true, isContinuous = true)
        )
        assertEquals(
            PdfTextSelectActivation.ACTIVATE_IN_SINGLE_PAGE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = true, isContinuous = false)
        )
        assertFalse(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true, readingMode = false, isContinuous = true, switchingToSinglePage = true
            )
        )

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("PdfTextToolPolicy.dockTapAction(pdfTextAvailable, pdfTextMode)"))
        assertTrue(canvasScreen.contains("showTextMenuHoriz"))
        assertTrue(canvasScreen.contains("showTextMenuVert"))
        assertTrue(canvasScreen.contains("Text(\"Select PDF Text\")"))
        assertTrue(canvasScreen.contains("activatePdfTextSelect()"))
        assertTrue(canvasScreen.contains("canvasViewRef?.setPdfTextSelectionMode(pdfTextMode)"))
    }

    @Test
    fun `T1-PDF03-05 exiting PDF text mode via dock tool switch or reading mode clears selection cleanly`() {
        assertEquals(TextDockTapAction.EXIT_PDF_TEXT_MODE, PdfTextToolPolicy.dockTapAction(pdfTextAvailable = true, pdfTextMode = true))
        assertTrue(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true, readingMode = true, isContinuous = false, switchingToSinglePage = false
            )
        )
        assertTrue(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true, readingMode = false, isContinuous = true, switchingToSinglePage = false
            )
        )

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val drawingView = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/DrawingCanvasView.kt")
        assertTrue(canvasScreen.contains("LaunchedEffect(uiState.selectedTool)"))
        assertTrue(canvasScreen.contains("exitPdfTextMode()"))
        assertTrue(drawingView.contains("fun setPdfTextSelectionMode(enabled: Boolean)"))
    }

    // --- PHASE B: PDF-04 Continuous Lasso & Arrow Selection ---

    @Test
    fun `T1-PDF04-04 selecting LASSO or SELECT in continuous mode does not force Single-Page switch`() {
        val lasso = InkToolSettings(CanvasTool.LASSO, 0, 4f, 16f, 1f, false)
        val arrow = InkToolSettings(CanvasTool.SELECT, 0, 4f, 16f, 1f, false)
        assertFalse(lasso.isWritingTool)
        assertFalse(arrow.isWritingTool)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val lassoStart = canvasScreen.indexOf("val selectLassoTool: () -> Unit = {")
        assertTrue("selectLassoTool missing in CanvasScreen.kt", lassoStart >= 0)
        val arrowStart = canvasScreen.indexOf("val selectArrowTool: () -> Unit = {", lassoStart)
        assertTrue("selectArrowTool missing after selectLassoTool", arrowStart > lassoStart)
        val arrowEnd = canvasScreen.indexOf("suspend fun prepareLayerEngine(", arrowStart)
        assertTrue("prepareLayerEngine missing after selectArrowTool", arrowEnd > arrowStart)

        val lassoBody = canvasScreen.substring(lassoStart, arrowStart)
        assertTrue(lassoBody.contains("exitPdfTextMode()"))
        assertTrue(lassoBody.contains("viewModel.selectTool(CanvasTool.LASSO)"))
        assertFalse(lassoBody.contains("setPageTurningMode"))

        val arrowBody = canvasScreen.substring(arrowStart, arrowEnd)
        assertTrue(arrowBody.contains("exitPdfTextMode()"))
        assertTrue(arrowBody.contains("viewModel.selectTool(CanvasTool.SELECT)"))
        assertFalse(arrowBody.contains("setPageTurningMode"))

        assertTrue(canvasScreen.contains("onSelectLasso = selectLassoTool"))
        assertTrue(canvasScreen.contains("onSelectArrow = selectArrowTool"))
    }

    @Test
    fun `T1-PDF04-05 Lasso and Arrow hit-test and move stay isolated to target PDF page`() {
        val p0 = listOf(ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.2f, 0.2f, 0.005f), InkPoint(0.4f, 0.2f, 0.005f))))
        val p1 = listOf(ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.3f, 0.4f, 0.005f), InkPoint(0.5f, 0.4f, 0.005f))))
        val p2 = listOf(ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.6f, 0.6f, 0.005f), InkPoint(0.8f, 0.6f, 0.005f))))

        val ratio = ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        val margin = ContinuousPageGeometry.marginFor("pdf")
        val lassoPoly = listOf(0.2f to 0.3f, 0.6f to 0.3f, 0.6f to 0.5f, 0.2f to 0.5f)
        val picked = ContinuousStrokeTransformMath.selectStrokesInLasso(p1, lassoPoly)
        assertEquals(listOf(0), picked)
        assertEquals(0, ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(p1, 0.4f, 0.4f, ratio, 0.05f))

        val bounds = ContinuousStrokeTransformMath.selectionBounds(listOf(p1[0]), ratio) ?: error("bounds missing")
        val (dx, dy) = ContinuousStrokeTransformMath.clampTranslationDelta(bounds, 0.1f, 0.05f, margin)
        val moved = ContinuousStrokeTransformMath.translatePoints(p1[0].points, dx, dy, margin)
        assertEquals(0.4f, moved[0].x, 1e-5f)
        assertEquals(0.45f, moved[0].y, 1e-5f)
        // Pages 0 and 2 remain untouched
        assertEquals(0.2f, p0[0].points[0].x, 1e-6f)
        assertEquals(0.6f, p2[0].points[0].x, 1e-6f)

        val inkController = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(inkController.contains("fun hasLassoSelectionOnPage(pageIndex: Int): Boolean"))
        assertTrue(inkController.contains("fun clearLassoIfNotOnPage(pageIndex: Int)"))
        assertTrue(canvasScreen.contains("ink.selectSingleStrokeAt(pageIndex"))
        assertTrue(canvasScreen.contains("ink.lassoFinish(pageIndex"))
        assertTrue(canvasScreen.contains("inkController.clearLassoIfNotOnPage(currentPageIndex)"))
    }

    // --- PHASE B: PDF-05 Continuous Annotations in Page Drawer Thumbnails ---

    @Test
    fun `T1-PDF05-04 PageThumbnailCard plan includes continuous annotations when strokes PNG is absent`() {
        val filesDir = tempFolder.newFolder("pdf05_04")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "pdf05-04")
        val stack = PageLayerStack.base()
        val op = E2eTestHarness.strokeOp(stack.layers.first())
        ws.writeContinuousPage(1, stack, mapOf(1 to listOf(op)))

        val mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, pageIndex = 1)
        assertTrue(mtimes.hasAnyAnnotationFiles)
        assertEquals(0L, mtimes.strokesMtime)

        val plan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = mtimes.hasAnyAnnotationFiles,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, plan.background)
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), plan.overlays)
        assertEquals(listOf(2160, 0, 4320, 3056), ThumbnailPolicy.continuousPixelCropRect(6480, 3056, "pdf").toList())

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val bitmapUtils = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/BitmapUtils.kt")
        assertTrue(canvasScreen.contains("private fun PageThumbnailCard("))
        assertTrue(bitmapUtils.contains("ThumbnailPolicy.planThumbnailComposition("))
        assertTrue(bitmapUtils.contains("drawContinuousAnnotationsOntoThumbnail("))
    }

    @Test
    fun `T1-PDF05-05 saving a new continuous stroke invalidates PageThumbnailCard cache key`() {
        val filesDir = tempFolder.newFolder("pdf05_05")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "pdf05-05")
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()
        val op1 = E2eTestHarness.strokeOp(layer)
        ws.writeContinuousPage(0, stack, mapOf(1 to listOf(op1)))

        val mtimesBefore = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        val keyBefore = ThumbnailPolicy.buildPageThumbnailCacheKey(
            noteId = ws.noteId, pageIndex = 0, pageType = "pdf",
            pdfSourcePath = "doc.pdf", pdfPageIndex = 0,
            mtimes = mtimesBefore, savedGeneration = 0, longSidePx = 340, noteUpdatedAt = 100L
        )

        val op2 = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.5f, 0.5f, 0.005f), InkPoint(0.8f, 0.8f, 0.005f)))
        ws.writeContinuousPage(0, stack, mapOf(1 to listOf(op1, op2)))
        val mtimesAfter = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        val keyAfter = ThumbnailPolicy.buildPageThumbnailCacheKey(
            noteId = ws.noteId, pageIndex = 0, pageType = "pdf",
            pdfSourcePath = "doc.pdf", pdfPageIndex = 0,
            mtimes = mtimesAfter, savedGeneration = 1, longSidePx = 340, noteUpdatedAt = 200L
        )

        assertNotEquals(keyBefore, keyAfter)
        assertTrue(mtimesAfter.annotTotalBytes > mtimesBefore.annotTotalBytes)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val viewModel = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        assertTrue(canvasScreen.contains("LaunchedEffect(uiState.isThumbnailDrawerOpen)"))
        assertTrue(canvasScreen.contains("inkController.flushCurrentSuspend()"))
        assertTrue(viewModel.contains("pageSavedEvents"))
    }

    // --- PHASE B: PDF-06 Home Screen Library Card Thumbnail Refresh ---

    @Test
    fun `T1-PDF06-03 NoteFileStore page-0 paths and NoteRepository touchUpdatedAt refresh Home seed key`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("pdf06_03"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("pdf06-03", pageCount = 2)
        env.seedNoteAndPages(note, pages)

        assertEquals("page_0.png", env.fileStore.getPageFile(note.id, 0).name)
        assertEquals("strokes_0.png", env.fileStore.getStrokesFile(note.id, 0).name)
        assertEquals("page_0_annot.json", env.fileStore.getAnnotationMetaFile(note.id, 0).name)
        assertEquals("page_0_annot_1_ops.json", env.fileStore.getAnnotationLayerOpsFile(note.id, 0, 1).name)

        val layer = PageLayerStack.base().layers.first()
        val op = E2eTestHarness.strokeOp(layer)
        assertTrue(env.repository.savePageLayerOps(note.id, 0, 1, listOf(op)))
        assertTrue(env.repository.savePageLayerMeta(note.id, 0, PageLayerMeta.encode(PageLayerStack.base())))
        assertEquals(2, env.touchedUpdatedAtCalls.size)
        assertTrue(env.touchedUpdatedAtCalls.all { it.first == note.id && it.second > 0L })

        val seed1 = ThumbnailPolicy.homePreviewSeedKey(note.id, 1000L, null, "pdf")
        val seed2 = ThumbnailPolicy.homePreviewSeedKey(note.id, 2000L, null, "pdf")
        assertNotEquals(seed1, seed2)

        val noteList = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/notes/NoteListScreen.kt")
        assertTrue(noteList.contains("ThumbnailPolicy.homePreviewSeedKey(note.id, note.updatedAt, note.coverPath, note.pageType)"))
    }

    @Test
    fun `T1-PDF06-04 Home card preview composition includes continuous page-0 ink over page_0`() {
        val filesDir = tempFolder.newFolder("pdf06_04")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "pdf06-04")
        ws.pagePreviewFile(0).writeBytes(byteArrayOf(1, 2, 3))
        val stack = PageLayerStack.base()
        ws.writeContinuousPage(0, stack, mapOf(1 to listOf(E2eTestHarness.strokeOp(stack.layers.first()))))

        val mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        assertTrue(mtimes.pageMtime > 0L)
        assertTrue(mtimes.hasAnyAnnotationFiles)

        val plan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = true,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = mtimes.hasAnyAnnotationFiles,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.PAGE_FILE, plan.background)
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), plan.overlays)

        val key = ThumbnailPolicy.buildPageThumbnailCacheKey(
            noteId = ws.noteId, pageIndex = 0, pageType = "pdf",
            pdfSourcePath = "", pdfPageIndex = -1,
            mtimes = mtimes, savedGeneration = 0, longSidePx = 500, noteUpdatedAt = 50L
        )
        assertTrue(key.contains(":l500:"))

        val noteList = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/notes/NoteListScreen.kt")
        assertTrue(noteList.contains("fun NoteInsidePreview("))
        assertTrue(noteList.contains("longSidePx = 500"))
    }

    @Test
    fun `T1-PDF06-05 Home card composition orders strokes_0 before continuous annotations over page_0`() {
        val filesDir = tempFolder.newFolder("pdf06_05")
        val ws = E2eTestHarness.createTempNoteWorkspace(filesDir, "pdf06-05")
        ws.pagePreviewFile(0).writeBytes(byteArrayOf(1, 2))
        ws.strokesFile(0).writeBytes(byteArrayOf(3, 4))
        val stack = PageLayerStack.base()
        ws.writeContinuousPage(0, stack, mapOf(1 to listOf(E2eTestHarness.strokeOp(stack.layers.first()))))

        val mtimes = ThumbnailPolicy.readPageDiskMtimes(ws.noteDir, 0)
        val plan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = true,
            hasBackgroundOverride = false,
            hasStrokesFile = mtimes.strokesMtime > 0L,
            hasAnnotationLayers = mtimes.hasAnyAnnotationFiles,
            hasImageLayers = false,
            baseLayerVisible = true
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, plan.background)
        assertEquals(
            listOf(ThumbnailPolicy.OverlayStep.STROKES_PNG, ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS),
            plan.overlays
        )
        val (leftFrac, rightFrac) = ThumbnailPolicy.continuousCropFraction("pdf")
        assertEquals(1f / 3f, leftFrac, 1e-5f)
        assertEquals(2f / 3f, rightFrac, 1e-5f)
    }

    // --- PHASE B: PDF-07 Unified Layers Between Continuous & Single-Page ---

    @Test
    fun `T1-PDF07-04 hidden Layer2 in continuous stack is excluded in Single-Page overlay plan`() {
        val stack = PageLayerStack.base()
        stack.add()
        stack.setVisible(2, false)
        val decoded = PageLayerMeta.decode(PageLayerMeta.encode(stack)) ?: error("stack decode failed")

        val plan = PdfModeReconciliationPolicy.planLayerVisibility(
            stack = decoded,
            hasSinglePageStrokes = true,
            floatingLayersVisible = listOf(true, false)
        )
        assertTrue(plan.showSinglePageStrokes)
        assertEquals(listOf(1), plan.visibleDrawingLayerIndices)
        assertEquals(listOf(0), plan.visibleFloatingLayerIndices)

        val inkController = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        val bitmapUtils = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/BitmapUtils.kt")
        assertTrue(inkController.contains("val visibleLayers = stack.layers.filter { it.visible }"))
        assertTrue(bitmapUtils.contains("if (!layer.visible) continue"))
    }

    @Test
    fun `T1-PDF07-05 unified layer visibility toggle and merge round-trip cleanly across modes`() = runTest {
        assertEquals(
            PdfModeReconciliationPolicy.LayerEntryTarget.UNIFIED_SHEET,
            PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote = false)
        )
        assertEquals(
            PdfModeReconciliationPolicy.MergeTarget.STROKES_FILE,
            PdfModeReconciliationPolicy.resolveMergeTarget(isVectorPdf = true)
        )

        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("pdf07_05"))
        val stack = PageLayerStack.base()
        val l1 = stack.layers.first()
        val l2 = stack.add() ?: error("layer 2 missing")
        val history = InkHistory(stack)
        val op1 = E2eTestHarness.strokeOp(l1)
        val op2 = E2eTestHarness.strokeOp(l2, points = listOf(InkPoint(0.4f, 0.4f, 0.005f)))
        history.commit(op1)
        history.commit(op2)
        history.commitRemoveLayer(l2, merged = true)

        assertTrue(env.repository.savePageLayerMeta("n-merge", 0, PageLayerMeta.encode(stack)))
        assertTrue(env.repository.savePageLayerOps("n-merge", 0, 1, history.ops))

        val loadedOps = env.repository.loadPageLayerOps("n-merge", 0, 1).orEmpty()
        assertEquals(2, loadedOps.size)
        val plan = PdfModeReconciliationPolicy.planLayerVisibility(stack, true, emptyList())
        assertEquals(listOf(1), plan.visibleDrawingLayerIndices)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote)"))
        assertTrue(canvasScreen.contains("showLayersPanel"))
    }

    // --- PHASE B: PDF-08 Continuous Reader Pointer Handler Cleanup ---

    @Test
    fun `T1-PDF08-04 CanvasScreen continuous wrapper removes duplicate stroke pointerInput and wires stylus`() {
        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val boxStart = canvasScreen.indexOf(".background(if (isInfiniteNote) Color.White else Color(0xFFEEEEEE))")
        val boxEnd = canvasScreen.indexOf("if (isInfiniteNote) {", startIndex = boxStart)
        assertTrue("Continuous outer Box section not found", boxStart >= 0 && boxEnd > boxStart)
        val wrapperHeader = canvasScreen.substring(boxStart, boxEnd)
        val pointerInputCount = ".pointerInput(".let { needle ->
            wrapperHeader.windowed(needle.length).count { it == needle }
        }
        assertTrue("Expected at most 2 outer .pointerInput modifiers, found $pointerInputCount", pointerInputCount <= 2)
        assertTrue(wrapperHeader.contains("PointerEventPass.Initial"))
        assertTrue(wrapperHeader.contains("event.changes.count { it.pressed } >= 2"))

        val leafStart = canvasScreen.indexOf("fun ContinuousPageView(")
        assertTrue(leafStart >= 0)
        val leafSection = canvasScreen.substring(leafStart)
        assertTrue(leafSection.contains(".pointerInput(page.id, pageIndex, inkSettings)"))

        val stylusCallSites = canvasScreen.windowed("onStylusDetected = {".length).count { it == "onStylusDetected = {" }
        assertTrue("Expected >= 3 onStylusDetected call sites, found $stylusCallSites", stylusCallSites >= 3)
    }

    @Test
    fun `T1-PDF08-05 mid-stroke two-finger gesture cancels active stroke and applies ReaderTransform cleanly`() {
        val layout = ReaderTransform.Layout(1080f, 1920f, false, 1080f, 1528f, 0f, 196f)
        val transformed = ReaderTransform.apply(
            state = ReaderTransform.State(scale = 1f),
            gesture = ReaderTransform.Gesture(540f, 960f, panX = 48f, panY = 0f, zoom = 1.5f),
            layout = layout
        )
        assertEquals(1.5f, transformed.state.scale, 1e-5f)
        assertEquals(48f, transformed.state.panX, 0.05f)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val leafSection = canvasScreen.substring(canvasScreen.indexOf("fun ContinuousPageView("))
        assertTrue(leafSection.contains("if (event.changes.count { it.pressed } > 1)"))
        assertTrue(leafSection.contains("ink.cancelStroke()"))
        assertTrue(leafSection.contains("ink.cancelLassoGesture()"))
    }
}
