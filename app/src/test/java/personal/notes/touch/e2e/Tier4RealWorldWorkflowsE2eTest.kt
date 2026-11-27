package personal.notes.touch.e2e

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.data.db.APP_MIGRATIONS
import personal.notes.touch.data.files.UiPrefs
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LassoHandle
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.LayerTileMapping
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.PageScrubberMath
import personal.notes.touch.ui.canvas.StrokeTransformSnapshot
import personal.notes.touch.ui.canvas.TocItem
import personal.notes.touch.util.ExportManager
import personal.notes.touch.util.MarginCropResult
import personal.notes.touch.util.MarginExportMath
import personal.notes.touch.util.OutlineNode
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfModeReconciliationPolicy
import personal.notes.touch.util.PdfOutlineImporter
import personal.notes.touch.util.PdfPageGeometry
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
 * Tier 4 E2E Tests — Real-World Application Workflows (`T4-01` .. `T4-10`).
 */
class Tier4RealWorldWorkflowsE2eTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `T4-01 end-to-end PDF layer history tile and disk workspace persistence round-trip`() {
        val filesDir = tempFolder.newFolder("filesDir")
        val (note, pages) = E2eTestHarness.pdfNoteFixture(noteId = "workflow-pdf-1", title = "Topology: Ch1?", pageCount = 2)
        val workspace = E2eTestHarness.createTempNoteWorkspace(filesDir, note.id)

        // Step 1: 3-layer stack on page 0 with Layer 2 hidden
        val stack = PageLayerStack.base()
        val l1 = stack.layers.first()
        val l2 = stack.add() ?: error("l2 missing")
        val l3 = stack.add() ?: error("l3 missing")
        stack.setVisible(2, false)
        stack.select(3)

        // Step 2: Commit pen (center), highlighter (left margin), and eraser ops
        val history = InkHistory(stack)
        val centerPen = E2eTestHarness.strokeOp(
            layer = l1,
            tool = CanvasTool.PEN,
            points = listOf(InkPoint(0.25f, 0.25f, 0.005f), InkPoint(0.75f, 0.75f, 0.005f))
        )
        val marginHighlighter = E2eTestHarness.strokeOp(
            layer = l2,
            tool = CanvasTool.HIGHLIGHTER,
            points = listOf(InkPoint(-0.6f, 0.4f, 0.02f), InkPoint(-0.2f, 0.4f, 0.02f))
        )
        val layer3Pen = E2eTestHarness.strokeOp(
            layer = l3,
            tool = CanvasTool.FOUNTAIN_PEN,
            points = listOf(InkPoint(1.2f, 0.3f, 0.006f), InkPoint(1.6f, 0.5f, 0.006f))
        )
        val eraser = E2eTestHarness.eraseOp(layer = l1)
        history.commit(centerPen)
        history.commit(marginHighlighter)
        history.commit(layer3Pen)
        history.commit(eraser)

        // Step 3: Undo last erase, then redo it -> verifies history coherence
        var eraseRestored: Boolean? = null
        assertTrue(history.undo(onErase = { _, r -> eraseRestored = r }, onClear = { _, _ -> }))
        assertEquals(true, eraseRestored)
        assertEquals(3, history.ops.size)
        assertTrue(history.redo(onErase = { _, r -> eraseRestored = r }, onClear = { _, _ -> }))
        assertEquals(false, eraseRestored)
        assertEquals(4, history.ops.size)

        // Step 4: Map tiles on 16:9 landscape PDF page
        val aspect = 16f / 9f
        val layerSize = ContinuousPageGeometry.layerSize(note.pageType, aspect)
        val grid = LayerTileGrid(layerSize.width, layerSize.height)
        val tilesInfo = PageLayerMeta.LayerTilesInfo(
            width = layerSize.width,
            height = layerSize.height,
            tileSize = grid.tileSize,
            tiles = listOf(grid.tileName(0, 0), grid.tileName(2, 0), grid.tileName(5, 0))
        )

        val layerRect = ContinuousPageGeometry.layerRect(note.pageType, aspect, 1280f, 720f)
        val tile0 = LayerTileMapping.tileRect(grid, 0, 0, layerRect, 1280f, 720f)
        assertFalse(tile0.empty)

        // Step 5: Persist to temp disk workspace & reload in fresh session
        val opsMap = mapOf(
            1 to history.ops.filter { it.layer.index == 1 },
            2 to history.ops.filter { it.layer.index == 2 },
            3 to history.ops.filter { it.layer.index == 3 }
        )
        workspace.writeContinuousPage(pages[0].index, stack, opsMap, mapOf(1 to tilesInfo))

        val (reloadedStack, reloadedOps) = workspace.readContinuousPage(0)
        assertEquals(3, reloadedStack.size)
        assertEquals(3, reloadedStack.currentIndex)
        assertEquals(listOf(1, 3), reloadedStack.visibleLayers().map { it.index })
        assertEquals(2, reloadedOps[1]?.size)
        assertEquals(1, reloadedOps[2]?.size)
        assertEquals(1, reloadedOps[3]?.size)

        val reloadedMargin = (reloadedOps.getValue(2).first() as InkOp.Stroke).stroke
        assertEquals(-0.6f, reloadedMargin.points[0].x, 1e-6f)
        assertEquals("Topology_ Ch1_", ExportManager.sanitizeFilename(note.title))

        val decodedTiles = PageLayerMeta.decodeTiles(workspace.annotMetaFile(0).readText())
        assertEquals(listOf(0 to 0, 2 to 0, 5 to 0), decodedTiles[1]?.positions())
    }

    @Test
    fun `T4-02 release static manifest schema JNI and audit integrity gate passes`() {
        val root = E2eTestHarness.locateRepoRoot()

        val bangHits = E2eTestHarness.scanKtFilesForToken(File(root, "app/src"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP)
        assertEquals("Double-bang hits: $bangHits", 0, bangHits.size)

        val runBlockingHits = E2eTestHarness.scanKtFilesForToken(File(root, "app/src/main"), "runBlocking")
        assertEquals("runBlocking hits: $runBlockingHits", 0, runBlockingHits.size)

        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("<uses-permission"))
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:fullBackupContent="false""""))

        assertTrue(File(root, "app/src/main/jniLibs/arm64-v8a/libpdfium.so").length() > 0L)
        assertTrue(File(root, "app/src/main/jniLibs/armeabi-v7a/libpdfium.so").length() > 0L)

        val schema15 = File(root, "app/schemas/personal.notes.touch.data.db.AppDatabase/15.json")
        assertTrue(schema15.isFile)
        assertNotNull(APP_MIGRATIONS.find { it.startVersion == 14 && it.endVersion == 15 })
        assertTrue(File(root, "state/AUDIT.md").isFile)
    }

    // --- PHASE B: Real-World Workflows (T4-03 .. T4-10) ---

    @Test
    fun `T4-03 workflow 16-by-9 lecture slide deck annotation thumbnails Home card and 12-page export`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t4_03"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t4-03-slides", title = "ML Slides", pageCount = 12)
        env.seedNoteAndPages(note, pages)

        val cache = PageAspectCache()
        for (i in 0 until 12) {
            cache.put(pages[i].pdfSourcePath, i, 960f, 540f)
        }
        val aspect = cache.get(pages[0].pdfSourcePath, 0) ?: error("missing slide aspect")
        val layerSize = ContinuousPageGeometry.layerSize("pdf", aspect)
        assertEquals(6480, layerSize.width)
        assertEquals(1215, layerSize.height)

        val layer = PageLayerStack.base().layers.first()
        assertTrue(env.repository.savePageLayerOps(note.id, 0, 1, listOf(E2eTestHarness.strokeOp(layer))))
        assertTrue(env.repository.savePageLayerOps(note.id, 4, 1, listOf(E2eTestHarness.strokeOp(layer))))
        assertEquals(2, env.touchedUpdatedAtCalls.size)

        val m0 = ThumbnailPolicy.readPageDiskMtimes(env.fileStore.getNoteDir(note.id), 0)
        val m4 = ThumbnailPolicy.readPageDiskMtimes(env.fileStore.getNoteDir(note.id), 4)
        assertTrue(m0.hasAnyAnnotationFiles && m4.hasAnyAnnotationFiles)
        assertEquals(listOf(2160, 0, 4320, 1215), ThumbnailPolicy.continuousPixelCropRect(6480, 1215, "pdf").toList())

        for (i in 0 until 12) {
            val src = PdfExportPolicy.resolveVectorPageSource(pages, i)
            assertNotNull(src)
            assertTrue(PdfExportPolicy.shouldUseVectorPdfBackground(src, hasBackgroundOverride = false))
            val (renderW, renderH) = PdfExportPolicy.exportRenderTargetSize(960f, 540f)
            assertEquals(2160, renderW)
            assertEquals(1215, renderH)
            assertEquals(842 to 474, PdfExportPolicy.resolveExportPageSizePts(renderW, renderH, 960f, 540f))
        }

        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportManager.contains("for (pageIdx in 0 until pageCount)"))
    }

    @Test
    fun `T4-04 workflow 120-page textbook streaming import outline search and scrubber`() = runTest {
        PdfTextIndex.clearCache()
        try {
            val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t4_04"))
            val (note, pages) = E2eTestHarness.pdfNoteFixture("t4-04-book", title = "Linear Algebra", pageCount = 1)
            env.seedNoteAndPages(note, pages)

            val incoming = tempFolder.newFile("textbook_120.pdf")
            assertTrue(PdfVectorImporter.copyStreamToFile(ByteArrayInputStream("%PDF-1.7\n".toByteArray()), incoming))
            val stored = PdfVectorImporter.storeSourceFile(incoming, env.fileStore.getNoteDir(note.id), isTempFile = true) ?: error("stored null")
            val relPath = "note/${note.id}/${stored.name}"

            val eagerThumbs = mutableListOf<Int>()
            var lastProgress = 0 to 0
            val sink = object : PdfVectorImporter.PdfImportSink {
                override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note.id)
                override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean {
                    eagerThumbs.add(notePageIndex)
                    return env.fileStore.writePageFileAtomically(note.id, notePageIndex) { it.writeBytes(byteArrayOf(1)) }
                }
                override suspend fun insertPageBatch(
                    startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int, reuseFirstPage: Boolean, newTotalPageCount: Int
                ) {
                    env.repository.appendPdfPagesBatch(note.id, startNotePageIndex, relPath, pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount)
                }
                override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
            }

            val outcome = PdfVectorImporter.importPages(
                pageCount = 120, reuseFirstPage = true, sink = sink, batchSize = 20,
                onProgress = { done, total -> lastProgress = done to total }
            )
            assertEquals(120, outcome.importedPages)
            assertEquals(120 to 120, lastProgress)
            assertEquals(listOf(0), eagerThumbs)
            assertEquals(120, env.repository.getPagesForNoteSync(note.id).size)

            val pdfOutline = PdfOutlineImporter.importNodes(
                listOf(
                    OutlineNode("Chapter 1 Foundations", 0, 0),
                    OutlineNode("Chapter 4 Spectral Theory", 80, 0),
                    OutlineNode("4.2 Eigenvalues", 95, 1)
                ),
                pageCount = 120
            )
            val userBookmark = TocItem(id = "u-note", title = "Midterm Review", pageIndex = 41, source = PdfOutlineImporter.SOURCE_USER)
            val mergedToc = PdfOutlineImporter.mergeWithUserEntries(listOf(userBookmark), pdfOutline)
            assertEquals(4, mergedToc.size)

            val descriptors = (0 until 120).map { PdfTextIndex.PdfPageDescriptor(it, relPath, it) }
            val index = PdfTextIndex.buildNoteIndex(
                pages = descriptors,
                cacheFile = env.fileStore.getPdfTextIndexFile(note.id),
                sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(relPath, 50_000L, 1234L) },
                pageTextProvider = { _, idx -> if (idx == 95) "Every symmetric matrix has real eigenvalue spectrum." else "Page $idx" }
            ) ?: error("index null")
            assertEquals(PdfTextIndex.SearchInputAction.JumpToPage(41), PdfTextIndex.classifySearchInput("42", 120))
            val hits = PdfTextIndex.search(index, "eigenvalue")
            assertEquals(1, hits.size)
            assertEquals(95, hits.first().pageIndex)

            assertTrue(PageScrubberMath.isScrubberEnabled(120))
            val scrubbed = PageScrubberMath.scrubFractionToPage(99f / 119f, 120)
            assertEquals(99, scrubbed)
            assertEquals(pdfOutline[2].id, PdfOutlineImporter.activeEntryId(mergedToc, scrubbed))
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T4-05 workflow scanned PDF search fallback Text tool guard margin notes and full-width export`() {
        PdfTextIndex.clearCache()
        try {
            val scanned = PdfTextIndex.buildIndex(8, pageTextProvider = { "" }) ?: error("scanned index null")
            assertTrue(scanned.isScannedPdf)
            assertTrue(PdfTextIndex.search(scanned, "theorem").isEmpty())
            assertEquals("No searchable text (scanned PDF)", PdfTextIndex.SCANNED_PDF_MESSAGE)
            assertFalse(PdfTextToolPolicy.isAvailable(isVectorPdfPage = true, charCount = 0))
            assertEquals(-1, PdfTextSelectionMath.clampIndex(0, charCount = 0))

            val presence = MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.5f, 1.5f), margin = 1.0f)
            assertTrue(presence.hasLeftMarginInk && presence.hasRightMarginInk)
            val guideState = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, presence)
            assertTrue(guideState.showLeftOffscreenChevron && guideState.showRightOffscreenChevron)

            val crop = MarginExportMath.cropRect(6480, 3056, 2160f / 3056f, 1.0f, includeMargins = true)
            assertEquals(MarginCropResult(0, 0, 6480, 3056, 6480, 3056, cropped = false), crop)
            assertEquals(842 to 397, MarginExportMath.exportPdfPageSizePts(2160f / 3056f, 1.0f, includeMargins = true))
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T4-06 workflow mixed portrait and landscape PDF adapts thumbnails reader and export per page`() {
        val cache = PageAspectCache()
        val dims = listOf(595f to 842f, 960f to 540f, 595f to 842f, 960f to 540f)
        dims.forEachIndexed { idx, (w, h) -> cache.put("mixed.pdf", idx, w, h) }

        dims.forEachIndexed { idx, (w, h) ->
            val aspect = cache.get("mixed.pdf", idx) ?: error("missing aspect $idx")
            val thumb = PdfPageGeometry.thumbnailSize(aspect, 340)
            val layer = ContinuousPageGeometry.layerSize("pdf", aspect)
            val pagePx = PdfModeReconciliationPolicy.pagePixelSize(aspect)
            val exportTarget = PdfExportPolicy.exportRenderTargetSize(w, h)
            val exportPts = PdfExportPolicy.resolveExportPageSizePts(exportTarget.first, exportTarget.second, w, h)
            if (idx % 2 == 0) {
                assertTrue(thumb.first < thumb.second)
                assertEquals(3057, layer.height)
                assertEquals(2160 to 3057, pagePx)
                assertEquals(595 to 842, exportPts)
            } else {
                assertTrue(thumb.first > thumb.second)
                assertEquals(1215, layer.height)
                assertEquals(2160 to 1215, pagePx)
                assertEquals(842 to 474, exportPts)
            }
        }
    }

    @Test
    fun `T4-07 workflow Continuous to Single-Page multi-layer and Text select round-trip without data loss`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t4_07"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t4-07", pageCount = 2)
        env.seedNoteAndPages(note, pages)

        val stack = PageLayerStack.base()
        val l1 = stack.layers.first()
        val l2 = stack.add() ?: error("l2 missing")
        stack.setVisible(2, false)

        val history = InkHistory(stack)
        history.commit(E2eTestHarness.strokeOp(l1))
        history.commit(E2eTestHarness.strokeOp(l2, points = listOf(InkPoint(-0.4f, 0.5f, 0.005f), InkPoint(-0.2f, 0.6f, 0.005f))))

        assertTrue(env.repository.savePageLayerMeta(note.id, 0, PageLayerMeta.encode(stack)))
        assertTrue(env.repository.savePageLayerOps(note.id, 0, 1, history.ops.filter { it.layer.index == 1 }))
        assertTrue(env.repository.savePageLayerOps(note.id, 0, 2, history.ops.filter { it.layer.index == 2 }))

        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("pdf", true))
        val plan = PdfModeReconciliationPolicy.planLayerVisibility(stack, hasSinglePageStrokes = true, floatingLayersVisible = listOf(true))
        assertEquals(listOf(1), plan.visibleDrawingLayerIndices)

        assertEquals(
            PdfTextSelectActivation.SWITCH_TO_SINGLE_AND_ACTIVATE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = true, isContinuous = true)
        )
        val range = PdfTextSelectionMath.range(0, 15)
        assertEquals(16, PdfTextSelectionMath.count(range))
        assertTrue(PdfTextToolPolicy.shouldExitPdfTextMode(pdfTextMode = true, readingMode = false, isContinuous = true, switchingToSinglePage = false))

        val reloadedMeta = PageLayerMeta.decode(env.fileStore.getAnnotationMetaFile(note.id, 0).readText()) ?: error("meta null")
        assertEquals(2, reloadedMeta.size)
        assertFalse(reloadedMeta.layers[1].visible)
        assertEquals(1, env.repository.loadPageLayerOps(note.id, 0, 1)?.size)
        assertEquals(1, env.repository.loadPageLayerOps(note.id, 0, 2)?.size)
    }

    @Test
    fun `T4-08 workflow margin guides pan reveal chevron Page only vs Include margins and UiPrefs key`() {
        val presence = MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.45f, 0.5f, 1.45f), margin = 1.0f)
        val centered = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, presence)
        assertTrue(centered.showLeftGuide && centered.showRightGuide)
        assertTrue(centered.showLeftOffscreenChevron && centered.showRightOffscreenChevron)

        val pannedRight = MarginExportMath.computeGuideState("pdf", -600f, 1000f, 1000f, presence)
        assertFalse(pannedRight.showRightOffscreenChevron)
        assertTrue(pannedRight.showLeftOffscreenChevron)

        val cropPageOnly = MarginExportMath.cropRect(6480, 3056, 2160f / 3056f, 1.0f, includeMargins = false)
        assertEquals(MarginCropResult(2160, 0, 2160, 3056, 2160, 3056, cropped = true), cropPageOnly)
        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(2160f / 3056f, 1.0f, includeMargins = false))

        val cropWithMargins = MarginExportMath.cropRect(6480, 3056, 2160f / 3056f, 1.0f, includeMargins = true)
        assertEquals(MarginCropResult(0, 0, 6480, 3056, 6480, 3056, cropped = false), cropWithMargins)
        assertEquals(842 to 397, MarginExportMath.exportPdfPageSizePts(2160f / 3056f, 1.0f, includeMargins = true))

        assertEquals("export_include_margins", UiPrefs.KEY_EXPORT_INCLUDE_MARGINS)
        val exportDialog = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/export/ExportDialog.kt")
        val viewModel = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        val exportManager = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue(exportDialog.contains("Page only") && exportDialog.contains("Include margins"))
        assertTrue(viewModel.contains("exportIncludeMargins"))
        assertTrue(exportManager.contains("includeMargins: Boolean = false"))
    }

    @Test
    fun `T4-09 workflow continuous Lasso and Arrow transforms on 16-by-9 PDF with 2x undo and 1x redo`() {
        val ratio = 16f / 9f
        val margin = ContinuousPageGeometry.marginFor("pdf")
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()
        val history = InkHistory(stack)

        val op = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.25f, 0.5f, 0.005f), InkPoint(0.65f, 0.5f, 0.005f)))
        history.commit(op)

        val poly = listOf(0.15f to 0.35f, 0.75f to 0.35f, 0.75f to 0.65f, 0.15f to 0.65f)
        assertEquals(listOf(0), ContinuousStrokeTransformMath.selectStrokesInLasso(listOf(op.stroke), poly))
        assertEquals(0, ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(listOf(op.stroke), 0.45f, 0.5f, ratio, 0.05f))

        val origPts = op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        val origLen = ContinuousStrokeTransformMath.isotropicDistance(origPts[0], origPts[1], ratio)
        val rotated = ContinuousStrokeTransformMath.rotatePoints(origPts, (PI / 4.0).toFloat(), 0.45f, 0.5f, ratio, margin)
        assertEquals(origLen, ContinuousStrokeTransformMath.isotropicDistance(rotated[0], rotated[1], ratio), 1e-5f)

        val mirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(rotated, 0.45f, margin)
        op.stroke.points.clear()
        op.stroke.points.addAll(mirrored)
        history.commitTransform(listOf(StrokeTransformSnapshot(op, origPts, mirrored, null, null)))

        val copiedStrokes = ContinuousStrokeTransformMath.copyStrokesWithOffset(listOf(op.stroke), 0.1f, 0.05f, margin)
        val copiedOps = copiedStrokes.map { InkOp.Stroke(it, shape = null, layer = layer) }
        history.ops.addAll(copiedOps)
        history.commitAddBatch(copiedOps)

        val bounds = ContinuousStrokeTransformMath.selectionBounds(listOf(copiedOps.first().stroke), ratio) ?: error("bounds null")
        val edge = ContinuousStrokeTransformMath.edgeScaleFactors(
            LassoHandle.SCALE_RIGHT, bounds, bounds.right, bounds.centerY, bounds.right + 0.1f, bounds.centerY
        ) ?: error("edge null")
        val stretched = ContinuousStrokeTransformMath.scalePoints(
            copiedOps.first().stroke.points, edge.scaleX, edge.scaleY, edge.anchorXNorm, edge.anchorYNorm, margin
        )
        assertTrue(stretched.all { it.x in -margin..(1f + margin) && it.y in 0f..1f })

        // Undo copy, undo transform -> back to original stroke; redo transform
        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(1, history.ops.size)
        var restoredPts: List<InkPoint> = emptyList()
        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }, onTransform = { snaps, isUndo ->
            if (isUndo) restoredPts = snaps.first().beforePoints
        }))
        assertEquals(origPts[0].x, restoredPts[0].x, 1e-6f)
        assertTrue(history.redo(onErase = { _, _ -> }, onClear = { _, _ -> }, onTransform = { snaps, isUndo ->
            if (!isUndo) restoredPts = snaps.first().afterPoints
        }))
        assertEquals(mirrored[0].x, restoredPts[0].x, 1e-6f)

        val decoded = ContinuousOpCodec.decode(ContinuousOpCodec.encode(history.ops)).orEmpty()
        assertEquals(1, decoded.size)

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
    fun `T4-10 workflow mid-import cancel at page 17 cleanup clean retry and R4-AUDIT gate`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t4_10"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t4-10-cancel", pageCount = 1)
        env.seedNoteAndPages(note, pages)
        env.fileStore.getNoteDir(note.id).apply { File(this, "pdf_temp.pdf").writeBytes(byteArrayOf(1, 2, 3)) }

        var cancelAt17 = false
        val cancelSink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean =
                env.fileStore.writePageFileAtomically(note.id, notePageIndex) { it.writeBytes(byteArrayOf(7)) }
            override suspend fun insertPageBatch(
                startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int, reuseFirstPage: Boolean, newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(note.id, startNotePageIndex, "note/${note.id}/pdf_temp.pdf", pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount)
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                env.repository.permanentlyDeleteNote(note.id)
            }
        }

        val cancelledOutcome = PdfVectorImporter.importPages(
            pageCount = 100, reuseFirstPage = true, sink = cancelSink, batchSize = 5,
            onProgress = { done, _ -> if (done >= 17) cancelAt17 = true }, isCancelled = { cancelAt17 }
        )
        assertTrue(cancelledOutcome.cancelled)
        assertNull(env.notes[note.id])
        assertTrue(env.repository.getPagesForNoteSync(note.id).isEmpty())
        assertFalse(File(env.filesDir, "note/${note.id}").exists())

        val (retryNote, retryPages) = E2eTestHarness.pdfNoteFixture("t4-10-retry", pageCount = 1)
        env.seedNoteAndPages(retryNote, retryPages)
        val retrySink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(retryNote.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean =
                env.fileStore.writePageFileAtomically(retryNote.id, notePageIndex) { it.writeBytes(byteArrayOf(7)) }
            override suspend fun insertPageBatch(
                startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int, reuseFirstPage: Boolean, newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(retryNote.id, startNotePageIndex, "note/${retryNote.id}/pdf_ok.pdf", pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount)
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
        }
        val retryOutcome = PdfVectorImporter.importPages(100, reuseFirstPage = true, sink = retrySink, batchSize = 20)
        assertFalse(retryOutcome.cancelled)
        assertEquals(100, retryOutcome.importedPages)
        assertEquals(100, env.repository.getPagesForNoteSync(retryNote.id).size)

        val root = E2eTestHarness.locateRepoRoot()
        assertEquals(0, E2eTestHarness.scanKtFilesForToken(File(root, "app/src"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP).size)
        assertEquals(0, E2eTestHarness.scanKtFilesForToken(File(root, "app/src/main"), "runBlocking").size)
        assertFalse(File(root, "app/src/main/AndroidManifest.xml").readText().contains("<uses-permission"))

        val audit = E2eTestHarness.readRepoFile("state/AUDIT.md")
        for (id in E2eTestHarness.ALL_15_FEATURE_IDS) {
            val section = E2eTestHarness.extractAuditSection(audit, id)
            assertTrue("Missing section $id", section.isNotBlank())
            assertTrue("Missing [REQUIRES USER TESTING] in $id", section.contains("[REQUIRES USER TESTING]"))
        }
    }
}
