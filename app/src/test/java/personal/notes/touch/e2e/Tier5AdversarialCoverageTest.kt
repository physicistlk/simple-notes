package personal.notes.touch.e2e

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.files.UiPrefs
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.CanvasViewModel
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPatch
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LassoHandle
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.NormRect
import personal.notes.touch.ui.canvas.PageLayer
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.PageScrubberMath
import personal.notes.touch.ui.canvas.TocItem
import personal.notes.touch.util.ExportManager
import personal.notes.touch.util.MarginCropResult
import personal.notes.touch.util.MarginExportMath
import personal.notes.touch.util.MarginInkPresence
import personal.notes.touch.util.OutlineNode
import personal.notes.touch.util.OutlineTreeNode
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfModeReconciliationPolicy
import personal.notes.touch.util.PdfOutlineImporter
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfTextIndex
import personal.notes.touch.util.PdfTextSelectionInfo
import personal.notes.touch.util.PdfTextSelectionMath
import personal.notes.touch.util.PdfVectorImporter
import personal.notes.touch.util.PdfiumDocument
import personal.notes.touch.util.ThumbnailPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tier 5 adversarial white-box coverage suite (`T5-P1-01`..`T5-P1-13` and
 * `T5-P2-01`..`T5-P2-12`). Exercises genuine production APIs across geometry,
 * policies, indexing, outline, scrubber, transforms, codec, storage, repository,
 * `PageLayerStack`, and `CanvasViewModel`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tier5AdversarialCoverageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun awaitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            while (!predicate() && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    // --- Partition 1: T5-P1-01 .. T5-P1-13 ---

    @Test
    fun `T5-P1-01 PdfPageGeometry round-trip extreme-aspect and non-finite guards`() {
        val inverted = PdfPageGeometry.userRectToCanvas(
            rect = floatArrayOf(300f, 680f, 100f, 720f),
            pageHeightPts = 842f,
            pxPerPt = 2.5f
        )
        assertTrue(inverted[0] <= inverted[2])
        assertTrue(inverted[1] <= inverted[3])

        val topLeftUser = PdfPageGeometry.canvasToUserPoint(inverted[0], inverted[1], 842f, 2.5f)
        val bottomRightUser = PdfPageGeometry.canvasToUserPoint(inverted[2], inverted[3], 842f, 2.5f)
        assertEquals(100f, topLeftUser[0], 1e-3f)
        assertEquals(720f, topLeftUser[1], 1e-3f)
        assertEquals(300f, bottomRightUser[0], 1e-3f)
        assertEquals(680f, bottomRightUser[1], 1e-3f)

        val portraitEdge = PdfPageGeometry.exportPdfPageSize(0.9999f, longSidePts = 842)
        val squareEdge = PdfPageGeometry.exportPdfPageSize(1.0f, longSidePts = 842)
        val landscapeEdge = PdfPageGeometry.exportPdfPageSize(1.0001f, longSidePts = 842)
        assertTrue(portraitEdge.first <= portraitEdge.second)
        assertEquals(842 to 842, squareEdge)
        assertTrue(landscapeEdge.first >= landscapeEdge.second)
        assertEquals(1 to 1, PdfPageGeometry.exportPdfPageSize(1.5f, longSidePts = -10))

        assertEquals(1f, PdfPageGeometry.canvasScale(-595f, 1080), 0f)
        assertEquals(1f, PdfPageGeometry.canvasScale(595f, -10), 0f)
        assertEquals(1f, PdfPageGeometry.canvasScale(Float.NaN, 1080), 0f)
        assertEquals(1f, PdfPageGeometry.canvasScale(Float.POSITIVE_INFINITY, 1080), 0f)
        assertEquals(1f, PdfPageGeometry.canvasScale(1e-40f, 1080), 0f)

        val zeroScalePt = PdfPageGeometry.canvasToUserPoint(100f, 200f, 842f, 0f)
        assertTrue(zeroScalePt[0].isFinite() && zeroScalePt[1].isFinite())
        val nanScalePt = PdfPageGeometry.canvasToUserPoint(Float.NaN, Float.POSITIVE_INFINITY, 842f, Float.NaN)
        assertTrue(nanScalePt[0].isFinite() && nanScalePt[1].isFinite())
        assertNull(PdfPageGeometry.unionRect(emptyList()))
    }

    @Test
    fun `T5-P1-02 PageAspectCache rejection update-at-capacity and hash-prefix isolation`() {
        val cache = PageAspectCache(maxEntries = 3)
        val fallback = 1.25f

        assertEquals(fallback, cache.put("", 0, 595f, 842f, fallback), 0f)
        assertEquals(fallback, cache.put("a.pdf", -1, 595f, 842f, fallback), 0f)
        assertEquals(fallback, cache.put("a.pdf", 0, 0f, 842f, fallback), 0f)
        assertEquals(fallback, cache.put("a.pdf", 0, Float.NaN, 100f, fallback), 0f)
        assertEquals(fallback, cache.put("a.pdf", 0, Float.POSITIVE_INFINITY, 100f, fallback), 0f)
        assertEquals(fallback, cache.put("a.pdf", 0, Float.MAX_VALUE, 1e-30f, fallback), 0f)
        assertEquals(fallback, cache.put("a.pdf", 0, 1e-40f, 1e30f, fallback), 0f)
        assertEquals(0, cache.size())

        assertEquals(fallback, cache.getOrLoad("a.pdf", 0, fallback) { FloatArray(0) }, 0f)
        assertEquals(fallback, cache.getOrLoad("a.pdf", 0, fallback) { FloatArray(1) }, 0f)
        assertEquals(0, cache.size())

        val minCap = PageAspectCache(maxEntries = 0)
        minCap.put("p1.pdf", 0, 100f, 100f)
        assertEquals(1, minCap.size())
        minCap.put("p2.pdf", 0, 200f, 100f)
        assertEquals(1, minCap.size())

        cache.put("note/1/doc", 0, 595f, 842f)
        cache.put("note/1/doc#2.pdf", 0, 1920f, 1080f)
        cache.put("note/1/other.pdf", 0, 800f, 800f)
        assertEquals(3, cache.size())

        // Updating existing key at capacity must NOT evict siblings
        cache.put("note/1/doc", 0, 1000f, 500f)
        assertEquals(3, cache.size())
        assertEquals(2.0f, cache.get("note/1/doc", 0) ?: 0f, 1e-4f)
        assertNotNull(cache.get("note/1/doc#2.pdf", 0))
        assertNotNull(cache.get("note/1/other.pdf", 0))

        // Invalidating "note/1/doc" must NOT evict "note/1/doc#2.pdf"
        cache.invalidate("")
        cache.invalidate("nonexistent.pdf")
        assertEquals(3, cache.size())
        cache.invalidate("note/1/doc")
        assertNull(cache.get("note/1/doc", 0))
        assertNotNull(cache.get("note/1/doc#2.pdf", 0))
        assertNotNull(cache.get("note/1/other.pdf", 0))
    }

    @Test
    fun `T5-P1-03 PdfExportPolicy partial-degenerate and priority invariants`() {
        assertNull(PdfExportPolicy.resolveVectorPageSource(emptyList(), 0))
        val blankPathPage = PageEntity(
            id = "p0",
            noteId = "n",
            index = 0,
            bitmapPath = "p0.png",
            pdfSourcePath = "   ",
            pdfPageIndex = 0
        )
        assertNull(PdfExportPolicy.resolveVectorPageSource(listOf(blankPathPage), 0))

        val dupFirst = PageEntity(
            id = "d1",
            noteId = "n",
            index = 2,
            bitmapPath = "p2.png",
            pdfSourcePath = "note/n/first.pdf",
            pdfPageIndex = 4
        )
        val dupSecond = PageEntity(
            id = "d2",
            noteId = "n",
            index = 2,
            bitmapPath = "p2.png",
            pdfSourcePath = "note/n/second.pdf",
            pdfPageIndex = 9
        )
        assertEquals(
            PdfExportPolicy.PdfPageSource("note/n/first.pdf", 4),
            PdfExportPolicy.resolveVectorPageSource(listOf(dupFirst, dupSecond), 2)
        )

        assertEquals(2160 to 3056, PdfExportPolicy.exportRenderTargetSize(595f, 842f, baseWidthPx = 0))
        assertEquals(2160 to 3056, PdfExportPolicy.exportRenderTargetSize(595f, 842f, baseWidthPx = -100))
        assertEquals(2160 to 3056, PdfExportPolicy.exportRenderTargetSize(Float.NaN, 842f))
        assertEquals(2160 to 3056, PdfExportPolicy.exportRenderTargetSize(1e-45f, 842f))

        val oneSideZeroW = PdfExportPolicy.resolveAnnotationLayerSize("pdf", 16f / 9f, 4000, 0)
        assertTrue(oneSideZeroW.width == 6480 && oneSideZeroW.height > 0)
        val oneSideZeroH = PdfExportPolicy.resolveAnnotationLayerSize("pdf", 16f / 9f, 0, 2250)
        assertTrue(oneSideZeroH.width == 6480 && oneSideZeroH.height > 0)

        assertEquals(
            1000 to 563,
            PdfExportPolicy.resolveExportPageSizePts(
                bitmapWidth = 2160,
                bitmapHeight = 0,
                pdfPageWidthPts = 960f,
                pdfPageHeightPts = 540f,
                longSidePts = 1000
            )
        )
    }

    @Test
    fun `T5-P1-04 ContinuousPageGeometry ratioOfLayer strokeBounds and PdfModeReconciliationPolicy clamping`() {
        for (type in listOf("pdf", "a4", "infinite")) {
            for (ratio in listOf(0.5f, 0.707f, 16f / 9f)) {
                val sz = ContinuousPageGeometry.layerSize(type, ratio)
                val recovered = ContinuousPageGeometry.ratioOfLayer(type, sz.width, sz.height)
                assertEquals("type=$type ratio=$ratio", ratio, recovered, 1e-3f)
            }
        }
        assertEquals(
            ContinuousPageGeometry.defaultRatio("pdf"),
            ContinuousPageGeometry.ratioOfLayer("pdf", 0, 100),
            1e-5f
        )
        assertEquals(
            ContinuousPageGeometry.defaultRatio("a4"),
            ContinuousPageGeometry.ratioOfLayer("a4", -1, -1),
            1e-5f
        )

        val emptyBox = ContinuousPageGeometry.BoxRect(0.5f, 0.5f, 0.5f, 0.8f)
        assertTrue(emptyBox.empty)
        val validBox = ContinuousPageGeometry.BoxRect(0.1f, 0.2f, 0.9f, 0.8f)
        assertFalse(validBox.empty)
        assertTrue(validBox.contains(0.1f, 0.2f))
        assertFalse(validBox.contains(0.95f, 0.5f))

        assertEquals(2160 to 4096, PdfModeReconciliationPolicy.pagePixelSize(0.1f, baseWidth = 2160))
        assertEquals(1 to 1, PdfModeReconciliationPolicy.pagePixelSize(100f, baseWidth = 0))

        val emptyPlan = PdfModeReconciliationPolicy.planLayerVisibility(
            stack = PageLayerStack(),
            hasSinglePageStrokes = true,
            floatingLayersVisible = emptyList()
        )
        assertTrue(emptyPlan.showSinglePageStrokes)
        assertTrue(emptyPlan.visibleDrawingLayerIndices.isEmpty())

        val pts = listOf(InkPoint(0.2f, 0.3f, 0.005f), InkPoint(0.4f, 0.5f, 0.005f))
        assertNull(ContinuousPageGeometry.strokeBounds(pts, Float.NaN, 1000f, 0.5f, 4f))
        assertNull(ContinuousPageGeometry.strokeBounds(pts, Float.POSITIVE_INFINITY, 1000f, 0.5f, 4f))
        assertNull(ContinuousPageGeometry.strokeBounds(listOf(InkPoint(Float.NaN, 0.2f, 0.005f)), 1000f, 1000f, 0.5f, 4f))

        val narrow = ContinuousPageGeometry.strokeBounds(pts, 1000f, 1000f, 0.6f, 4f, minWidthNorm = 0.005f)
        val wide = ContinuousPageGeometry.strokeBounds(pts, 1000f, 1000f, 0.85f, 4f, minWidthNorm = 0.005f * 3.2f)
        assertNotNull(narrow)
        assertNotNull(wide)
        assertTrue((wide?.width ?: 0f) > (narrow?.width ?: 0f))
    }

    @Test
    fun `T5-P1-05 PdfOutlineImporter boundary whitespace Unicode unsorted activeEntryId and cap`() {
        assertTrue(PdfOutlineImporter.flattenTree(emptyList()).isEmpty())
        assertTrue(PdfOutlineImporter.flattenTree(listOf(OutlineTreeNode("Root", 0)), maxDepth = -1).isEmpty())
        assertTrue(PdfOutlineImporter.flattenTree(listOf(OutlineTreeNode("Root", 0)), maxNodes = 0).isEmpty())
        val roots = listOf(
            OutlineTreeNode("Root", 0, listOf(OutlineTreeNode("Child", 1)))
        )
        val depthZero = PdfOutlineImporter.flattenTree(roots, maxDepth = 0)
        assertEquals(1, depthZero.size)
        assertEquals("Root", depthZero[0].title)

        assertTrue(PdfOutlineImporter.importNodes(emptyList(), pageCount = 10).isEmpty())
        assertTrue(
            PdfOutlineImporter.importNodes(listOf(OutlineNode("Ch1", 0, 0)), pageCount = 10, maxEntries = 0).isEmpty()
        )
        assertEquals("第1章 🚀 Intro", PdfOutlineImporter.normalizeTitle("\t第1章\n\r  🚀 Intro  "))

        val existing = listOf(
            TocItem(id = "u2", title = "User B", pageIndex = 8, source = "bookmark"),
            TocItem(id = "u1", title = "User A", pageIndex = 2, source = PdfOutlineImporter.SOURCE_USER),
            TocItem(id = "old", title = "Old PDF", pageIndex = 5, source = PdfOutlineImporter.SOURCE_PDF)
        )
        val imported = PdfOutlineImporter.importNodes(
            listOf(OutlineNode("New PDF", pageIndex = 4, level = 0)),
            pageCount = 10
        )
        val merged = PdfOutlineImporter.mergeWithUserEntries(existing, imported)
        assertEquals(listOf("u1", imported[0].id, "u2"), merged.map { it.id })
        for (i in 1 until merged.size) {
            assertTrue(merged[i].pageIndex >= merged[i - 1].pageIndex)
        }

        val unsorted = listOf(
            TocItem(id = "late", title = "Late", pageIndex = 10),
            TocItem(id = "early", title = "Early", pageIndex = 3),
            TocItem(id = "mid", title = "Mid", pageIndex = 6)
        )
        assertNull(PdfOutlineImporter.activeEntryId(unsorted, currentPageIndex = -1))
        assertNull(PdfOutlineImporter.activeEntryId(unsorted, currentPageIndex = 2))
        assertEquals("mid", PdfOutlineImporter.activeEntryId(unsorted, currentPageIndex = 8))
        assertEquals("late", PdfOutlineImporter.activeEntryId(unsorted, currentPageIndex = 15))
    }

    @Test
    fun `T5-P1-06 PdfTextIndex LRU eviction cancel isolation legacy cache and negative radius`() {
        PdfTextIndex.clearCache()
        val calls = HashMap<String, Int>()
        val descriptors = (0 until 17).map { i ->
            PdfTextIndex.PdfPageDescriptor(0, "src_$i.pdf", 0)
        }
        for (i in 0 until 16) {
            val d = listOf(descriptors[i])
            PdfTextIndex.buildNoteIndex(
                pages = d,
                sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 100L, 1L) },
                pageTextProvider = { p, _ ->
                    calls[p] = (calls[p] ?: 0) + 1
                    "Text $p"
                }
            )
        }
        // Re-access src_1 so LRU promotes it above src_0
        PdfTextIndex.buildNoteIndex(
            pages = listOf(descriptors[1]),
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 100L, 1L) },
            pageTextProvider = { p, _ ->
                calls[p] = (calls[p] ?: 0) + 1
                "Text $p"
            }
        )
        assertEquals(1, calls["src_1.pdf"])
        // Insert 17th entry -> src_0 should be evicted, src_1 retained
        PdfTextIndex.buildNoteIndex(
            pages = listOf(descriptors[16]),
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 100L, 1L) },
            pageTextProvider = { p, _ ->
                calls[p] = (calls[p] ?: 0) + 1
                "Text $p"
            }
        )
        PdfTextIndex.buildNoteIndex(
            pages = listOf(descriptors[1]),
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 100L, 1L) },
            pageTextProvider = { p, _ ->
                calls[p] = (calls[p] ?: 0) + 1
                "Text $p"
            }
        )
        assertEquals(1, calls["src_1.pdf"])
        PdfTextIndex.buildNoteIndex(
            pages = listOf(descriptors[0]),
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 100L, 1L) },
            pageTextProvider = { p, _ ->
                calls[p] = (calls[p] ?: 0) + 1
                "Text $p"
            }
        )
        assertEquals(2, calls["src_0.pdf"])

        // Mid-cancel cache isolation
        PdfTextIndex.clearCache()
        val cancelFile = File(tempFolder.newFolder("t5_text_cancel"), "pdf_text.json")
        val cancelFlag = AtomicBoolean(false)
        val cancelledResult = PdfTextIndex.buildNoteIndex(
            pages = listOf(
                PdfTextIndex.PdfPageDescriptor(0, "a.pdf", 0),
                PdfTextIndex.PdfPageDescriptor(1, "b.pdf", 0)
            ),
            cacheFile = cancelFile,
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 50L, 2L) },
            pageTextProvider = { p, _ ->
                if (p == "b.pdf") cancelFlag.set(true)
                "Page $p"
            },
            isCancelled = { cancelFlag.get() }
        )
        assertNull(cancelledResult)
        assertFalse(cancelFile.exists())
        var reExtracted = 0
        val afterCancel = PdfTextIndex.buildNoteIndex(
            pages = listOf(PdfTextIndex.PdfPageDescriptor(0, "a.pdf", 0)),
            cacheFile = cancelFile,
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 50L, 2L) },
            pageTextProvider = { _, _ ->
                reExtracted++
                "Page a.pdf"
            }
        )
        assertNotNull(afterCancel)
        assertEquals(1, reExtracted)

        // Malformed / legacy disk caches
        val diskDir = tempFolder.newFolder("t5_text_disk")
        val diskFile = File(diskDir, "pdf_text.json")
        diskFile.writeText("""{"version":1,"sources":[]}""")
        assertNotNull(PdfTextIndex.buildNoteIndex(listOf(PdfTextIndex.PdfPageDescriptor(0, "v1.pdf", 0)), diskFile, pageTextProvider = { _, _ -> "ok" }))
        diskFile.writeText("{broken")
        assertNotNull(PdfTextIndex.buildNoteIndex(listOf(PdfTextIndex.PdfPageDescriptor(0, "bad.pdf", 0)), diskFile, pageTextProvider = { _, _ -> "ok" }))
        diskFile.writeText("""{"version":2,"sources":[{"sourcePath":"arr.pdf","fileSize":10,"lastModified":20,"pages":["Page0",null,"Page2"]}]}""")
        var arrProviderCalls = 0
        val arrIndex = PdfTextIndex.buildNoteIndex(
            pages = listOf(
                PdfTextIndex.PdfPageDescriptor(0, "arr.pdf", 0),
                PdfTextIndex.PdfPageDescriptor(2, "arr.pdf", 2)
            ),
            cacheFile = diskFile,
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 10L, 20L) },
            pageTextProvider = { _, _ -> arrProviderCalls++; "miss" }
        )
        assertEquals(0, arrProviderCalls)
        assertEquals(listOf("Page0", "Page2"), arrIndex?.pages?.map { it.rawText })

        diskFile.writeText("""{"version":2,"sources":[{"sourcePath":"neg.pdf","fileSize":10,"lastModified":20,"pages":{"-1":"bad","0":"good"}}]}""")
        val negIndex = PdfTextIndex.buildNoteIndex(
            pages = listOf(PdfTextIndex.PdfPageDescriptor(0, "neg.pdf", 0)),
            cacheFile = diskFile,
            sourceMetaProvider = { PdfTextIndex.PdfSourceMeta(it, 10L, 20L) },
            pageTextProvider = { _, _ -> "miss" }
        )
        assertEquals("good", negIndex?.pages?.firstOrNull()?.rawText)

        // Negative radius guard (#1) & non-adjacent search
        val negRadius = PdfTextIndex.buildPlainSnippet(
            "Alpha Beta Gamma Delta Epsilon Zeta",
            hitAt = 11,
            hitLen = 5,
            tokens = listOf("Gamma"),
            radius = -5
        )
        assertTrue(negRadius.text.contains("Gamma"))

        val searchIdx = PdfTextIndex.buildIndex(3, pageTextProvider = { page -> "Page $page alpha beta gamma alpha gamma" })
        assertNotNull(searchIdx)
        val nonNullIdx = searchIdx ?: error("index null")
        assertTrue(PdfTextIndex.search(nonNullIdx, "alpha", maxHits = 0).isEmpty())
        val cappedHits = PdfTextIndex.search(nonNullIdx, "alpha gamma", maxHits = 2)
        assertEquals(2, cappedHits.size)
        for (hit in cappedHits) {
            val raw = nonNullIdx.pages.first { it.notePageIndex == hit.pageIndex }.rawText
            val slice = raw.substring(hit.startOffset, hit.startOffset + hit.length)
            assertTrue(slice.equals("alpha gamma", ignoreCase = true) || slice.equals("gamma", ignoreCase = true))
            assertTrue(hit.matchRanges.all { it.first >= 0 && it.last < hit.snippet.length })
        }

        val canvasRects = PdfTextIndex.userRectsToBoxCanvasRects(
            userRects = listOf(floatArrayOf(1f, 2f), floatArrayOf(10f, 100f, 50f, 80f)),
            pageWidthPts = 595f,
            pageHeightPts = 842f,
            pageLeftPx = 0f,
            pageTopPx = 0f,
            pageWidthPx = 595f
        )
        assertEquals(1, canvasRects.size)
        assertTrue(
            PdfTextIndex.userRectsToBoxCanvasRects(
                listOf(floatArrayOf(0f, 10f, 20f, 0f)),
                pageWidthPts = 0f,
                pageHeightPts = 842f,
                pageLeftPx = 0f,
                pageTopPx = 0f,
                pageWidthPx = 595f
            ).isEmpty()
        )
    }

    @Test
    fun `T5-P1-07 PageScrubberMath and PdfTextSelectionMath edge invariants`() {
        assertEquals(0f, PageScrubberMath.pageToScrubFraction(-5, 10), 0f)
        assertEquals(1f, PageScrubberMath.pageToScrubFraction(50, 10), 0f)
        assertEquals(0f, PageScrubberMath.pageToScrubFraction(0, 0), 0f)
        assertEquals(0f, PageScrubberMath.pageToScrubFraction(0, -3), 0f)

        // Exact diagonal tie in horizontal mode must prefer deltaY (abs(deltaX) > abs(deltaY) is strict)
        val tieStep = PageScrubberMath.dragDeltaToFractionStep(
            deltaX = 50f,
            deltaY = -50f,
            trackSpanPx = 100f,
            pageTurningDirection = 1
        )
        assertEquals(-0.5f, tieStep, 1e-4f)

        val nanApplied = PageScrubberMath.applyScrubDelta(Float.NaN, 25f, 0f, 0, 100f)
        assertTrue(nanApplied.isFinite() && nanApplied in 0f..1f)
        val clampedApplied = PageScrubberMath.applyScrubDelta(2.5f, 0f, 100f, 0, 100f)
        assertEquals(1f, clampedApplied, 0f)

        assertTrue(PdfTextSelectionInfo(5, 3, "ab").isEmpty)
        assertTrue(PdfTextSelectionInfo(0, 0, "").isEmpty)
        assertFalse(PdfTextSelectionInfo(0, 0, "a").isEmpty)
        assertEquals(-1, PdfTextSelectionMath.clampIndex(5, -2))
        assertEquals(0, PdfTextSelectionMath.count(IntRange(5, 3)))

        val nanUnderline = PdfTextSelectionMath.underlineRect(floatArrayOf(10f, 20f, 100f, 40f), Float.NaN)
        assertEquals(39f, nanUnderline[1], 1e-4f)
        assertEquals(24f, PdfTextSelectionMath.tolerancePageUnits(24f, Float.NaN), 1e-4f)
    }

    @Test
    fun `T5-P1-08 ContinuousStrokeTransformMath untested APIs NaN bounds and handle layout`() {
        val clamped = ContinuousStrokeTransformMath.clampPoints(
            points = listOf(
                InkPoint(Float.NaN, Float.NEGATIVE_INFINITY, 0.005f),
                InkPoint(-5f, 5f, 0.005f)
            ),
            margin = 1f
        )
        assertTrue(clamped.all { it.x.isFinite() && it.y.isFinite() && it.x in -1f..2f && it.y in 0f..1f })
        val negMarginClamped = ContinuousStrokeTransformMath.clampPoints(listOf(InkPoint(-0.5f, 0.5f, 0.005f)), -1f)
        assertEquals(0f, negMarginClamped[0].x, 0f)

        val nanStroke = ContinuousStroke(CanvasTool.PEN, 0, 0.005f, points = mutableListOf(InkPoint(Float.NaN, 0.5f, 0.005f)))
        assertNull(ContinuousStrokeTransformMath.selectionBounds(listOf(nanStroke), 1f))
        assertNull(ContinuousStrokeTransformMath.pointsBounds(listOf(nanStroke)))
        val emptyStroke = ContinuousStroke(CanvasTool.PEN, 0, 0.005f, points = mutableListOf())
        val validStroke = ContinuousStroke(CanvasTool.PEN, 0, 0.005f, points = mutableListOf(InkPoint(0.2f, 0.3f, 0.005f)))
        assertNotNull(ContinuousStrokeTransformMath.pointsBounds(listOf(emptyStroke, validStroke)))

        // Oversized bounds height (> 1f) clamps centerY rigidly without squashing
        val tallBounds = NormRect(0.2f, -0.5f, 0.4f, 1.5f)
        val (_, dyUp) = ContinuousStrokeTransformMath.clampTranslationDelta(tallBounds, 0f, 5f, margin = 0f)
        val (_, dyDown) = ContinuousStrokeTransformMath.clampTranslationDelta(tallBounds, 0f, -5f, margin = 0f)
        assertTrue(tallBounds.centerY + dyUp <= 1f + 1e-5f)
        assertTrue(tallBounds.centerY + dyDown >= -1e-5f)

        assertNull(ContinuousStrokeTransformMath.edgeScaleFactors(LassoHandle.ROTATE, validStroke.let { NormRect(0.2f, 0.2f, 0.8f, 0.8f) }, 0.2f, 0.2f, 0.4f, 0.4f))
        assertNull(ContinuousStrokeTransformMath.edgeScaleFactors(LassoHandle.MOVE, NormRect(0.2f, 0.2f, 0.8f, 0.8f), 0.2f, 0.2f, 0.4f, 0.4f))
        assertNull(ContinuousStrokeTransformMath.edgeScaleFactors(LassoHandle.NONE, NormRect(0.2f, 0.2f, 0.8f, 0.8f), 0.2f, 0.2f, 0.4f, 0.4f))
        assertNull(ContinuousStrokeTransformMath.edgeScaleFactors(LassoHandle.SCALE_RIGHT, NormRect(0.2f, 0.2f, 0.8f, 0.8f), Float.NaN, 0.5f, 0.9f, 0.5f))
        for (h in listOf(LassoHandle.SCALE_LEFT, LassoHandle.SCALE_RIGHT, LassoHandle.SCALE_TOP, LassoHandle.SCALE_BOTTOM)) {
            val spec = ContinuousStrokeTransformMath.edgeScaleFactors(h, NormRect(0.2f, 0.2f, 0.8f, 0.8f), 0.5f, 0.5f, 5f, 5f)
            assertNotNull(spec)
            assertTrue((spec?.scaleX ?: 0f) in ContinuousStrokeTransformMath.DEFAULT_MIN_SCALE..ContinuousStrokeTransformMath.DEFAULT_MAX_SCALE)
            assertTrue((spec?.scaleY ?: 0f) in ContinuousStrokeTransformMath.DEFAULT_MIN_SCALE..ContinuousStrokeTransformMath.DEFAULT_MAX_SCALE)
        }

        assertTrue(ContinuousStrokeTransformMath.copyStrokesWithOffset(emptyList()).isEmpty())
        val copiedUnclamped = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            listOf(validStroke, nanStroke),
            offsetXNorm = Float.NaN,
            offsetYNorm = 2.0f,
            clampToBounds = false
        )
        assertEquals(2.3f, copiedUnclamped[0].points[0].y, 1e-4f)

        val layout = ContinuousStrokeTransformMath.computeHandleLayout(
            bounds = NormRect(0.5f, 0.5f, 0.505f, 0.505f),
            boxRatio = 1f,
            boxHeightPx = 1000f,
            scale = 1f
        )
        assertTrue(layout.mirrorCenter.first > layout.rotateCenter.first)
        assertFalse(ContinuousStrokeTransformMath.pointInPolygon(Float.NaN, 0.5f, listOf(0f to 0f, 1f to 0f, 0f to 1f)))
        assertFalse(ContinuousStrokeTransformMath.pointInPolygon(0.2f, 0.2f, listOf(0f to 0f, Float.NaN to 0f, 0f to 1f)))
        assertEquals(-1, ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(listOf(emptyStroke, nanStroke), 0.5f, 0.5f, 1f, 0.1f))

        assertEquals(
            1f,
            ContinuousStrokeTransformMath.proportionalScaleFactor(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 1f),
            1e-5f
        )
        assertTrue(NormRect(Float.NaN, 0f, 1f, 1f).isEmpty)
    }

    @Test
    fun `T5-P1-09 ContinuousOpCodec malformed fields trailing junk all tools and NaN safety`() {
        val layer = PageLayer(1, "Layer1", true)
        val allToolsOps = CanvasTool.entries.flatMap { tool ->
            listOf(true, false).map { auto ->
                InkOp.Stroke(
                    ContinuousStroke(
                        tool = tool,
                        color = 0xFF123456.toInt(),
                        baseWidthNorm = 0.008f,
                        autoShapeEnabled = auto,
                        points = mutableListOf(InkPoint(0.1f, 0.2f, 0.008f), InkPoint(0.7f, 0.8f, 0.009f))
                    ),
                    shape = null,
                    layer = layer
                )
            }
        }
        val encoded = ContinuousOpCodec.encode(allToolsOps)
        val decoded = ContinuousOpCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(allToolsOps.size, decoded?.size)
        decoded?.forEachIndexed { idx, op ->
            val expected = allToolsOps[idx].stroke
            val actual = (op as InkOp.Stroke).stroke
            assertEquals(expected.tool, actual.tool)
            assertEquals(expected.color, actual.color)
            assertEquals(expected.autoShapeEnabled, actual.autoShapeEnabled)
            assertEquals(expected.baseWidthNorm, actual.baseWidthNorm, 1e-6f)
            assertEquals(expected.points.size, actual.points.size)
        }

        val badPayloads = listOf(
            """[{"type":"stroke","tool":"PEN","color":"red","baseWidthNorm":0.01,"autoShapeEnabled":false,"points":[]}]""",
            """[{"type":"stroke","tool":"PEN","color":0,"baseWidthNorm":"x","autoShapeEnabled":false,"points":[]}]""",
            """[{"type":"stroke","tool":"PEN","color":0,"baseWidthNorm":0.01,"autoShapeEnabled":1,"points":[]}]""",
            """[{"type":"stroke","tool":"PEN","color":0,"baseWidthNorm":0.01,"autoShapeEnabled":false,"points":[123]}]""",
            """[{"type":"stroke","tool":"PEN","color":0,"baseWidthNorm":0.01,"autoShapeEnabled":false,"points":[{"x":0.1,"y":0.2}]}]""",
            """[{"type":"stroke","tool":"PEN","color":0,"baseWidthNorm":1e400,"autoShapeEnabled":false,"points":[]}]""",
            "[] trailing"
        )
        for (bad in badPayloads) {
            assertNull("Expected null for $bad", ContinuousOpCodec.decode(bad))
        }

        // Encoding a stroke with non-finite floats must still yield valid JSON preserving valid strokes
        val withNaN = listOf(
            E2eTestHarness.strokeOp(layer),
            InkOp.Stroke(
                ContinuousStroke(
                    CanvasTool.PEN,
                    0,
                    Float.NaN,
                    points = mutableListOf(InkPoint(Float.NaN, 0.2f, 0.005f), InkPoint(0.5f, 0.5f, Float.POSITIVE_INFINITY))
                ),
                null,
                layer
            )
        )
        val sanitizedEncoded = ContinuousOpCodec.encode(withNaN)
        val sanitizedDecoded = ContinuousOpCodec.decode(sanitizedEncoded)
        assertNotNull(sanitizedDecoded)
        assertEquals(2, sanitizedDecoded?.size)
        assertEquals(1, (sanitizedDecoded?.get(1) as? InkOp.Stroke)?.stroke?.points?.size)
    }

    @Test
    fun `T5-P1-10 MarginExportMath and ThumbnailPolicy boundary branches`() {
        assertEquals(PdfPageGeometry.DEFAULT_ASPECT, MarginExportMath.exportPageAspect(Float.NaN, 1f, false), 1e-5f)
        assertEquals(0.8f, MarginExportMath.exportPageAspect(0.8f, -1f, true), 1e-5f)

        val aDom = MarginInkPresence(hasLeftMarginInk = true, hasRightMarginInk = true, leftMinX = -0.9f, rightMaxX = 1.9f)
        val bSub = MarginInkPresence(hasLeftMarginInk = true, hasRightMarginInk = true, leftMinX = -0.2f, rightMaxX = 1.2f)
        assertEquals(aDom, MarginExportMath.mergePresence(aDom, bSub))

        assertEquals(0, MarginExportMath.tileLeftMarginEndLocalPx(-1, 6480, 1024, 1f))
        assertEquals(0, MarginExportMath.tileLeftMarginEndLocalPx(0, 0, 1024, 1f))
        assertEquals(0, MarginExportMath.tileLeftMarginEndLocalPx(0, 6480, 0, Float.NaN))
        assertEquals(1024, MarginExportMath.tileRightMarginStartLocalPx(-1, 6480, 1024, 1f))
        assertNull(MarginExportMath.tileNormalizedXSpan(-1, 6480, 1024, 1f))
        assertNull(MarginExportMath.tileNormalizedXSpan(0, 6480, 0, 1f))
        assertNull(MarginExportMath.tileNormalizedXSpan(0, 6480, 1024, Float.NaN))

        val zeroGuide = MarginExportMath.computeGuideState("pdf", 0f, 0f, 0f, aDom)
        assertFalse(zeroGuide.showLeftGuide || zeroGuide.showRightGuide)
        val fallbackChevron = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 0f,
            pageWidthPx = 1000f,
            viewportWidthPx = 1000f,
            presence = MarginInkPresence(hasLeftMarginInk = true, leftMinX = 0f)
        )
        assertTrue(fallbackChevron.showLeftOffscreenChevron)

        val tinyMarginCrop = MarginExportMath.cropRect(100, 100, 1f, margin = 1e-10f, includeMargins = false)
        assertEquals(MarginCropResult(0, 0, 100, 100, 100, 100, cropped = false), tinyMarginCrop)

        val infWithAnnot = ThumbnailPolicy.planThumbnailComposition(
            pageType = "infinite",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.BLANK_WHITE, infWithAnnot.background)
        val infEmpty = ThumbnailPolicy.planThumbnailComposition(
            pageType = "infinite",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = false,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.NONE, infEmpty.background)
        assertArrayEquals(intArrayOf(0, 0, 0, 0), ThumbnailPolicy.continuousPixelCropRect(100, 0, "pdf"))
        assertEquals(3, ThumbnailPolicy.continuousComposeWidthPx(0, "pdf"))

        val noteDir = tempFolder.newFolder("t5_thumb_mtime")
        val layersDir = File(noteDir, "layers").apply { mkdirs() }
        val layerPng = File(layersDir, "page_0_layer_0.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val fixedTime = 1_700_000_000_000L
        layerPng.setLastModified(fixedTime)
        val keyBefore = ThumbnailPolicy.buildPageThumbnailCacheKey(
            "n", 0, "pdf", "a.pdf", 0, ThumbnailPolicy.readPageDiskMtimes(noteDir, 0), 0, 340, 1L
        )
        layerPng.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        layerPng.setLastModified(fixedTime)
        val keyAfter = ThumbnailPolicy.buildPageThumbnailCacheKey(
            "n", 0, "pdf", "a.pdf", 0, ThumbnailPolicy.readPageDiskMtimes(noteDir, 0), 0, 340, 1L
        )
        assertNotEquals(keyBefore, keyAfter)
    }

    @Test
    fun `T5-P1-11 PdfVectorImporter stream store batch and cancel resource cleanup invariants`() = runTest {
        val dir = tempFolder.newFolder("t5_importer")
        val zeroChunkFirst = object : InputStream() {
            private var step = 0
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                return when (step++) {
                    0 -> 0
                    1 -> {
                        val data = "PDF".toByteArray()
                        System.arraycopy(data, 0, b, off, data.size)
                        data.size
                    }
                    else -> -1
                }
            }
        }
        val dest1 = File(dir, "zero_chunk.pdf")
        assertTrue(PdfVectorImporter.copyStreamToFile(zeroChunkFirst, dest1, bufferSize = -500))
        assertEquals("PDF", dest1.readText())

        val cancelPostRead = AtomicBoolean(false)
        val postReadStream = object : InputStream() {
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b[off] = 0x41
                cancelPostRead.set(true)
                return 1
            }
        }
        val dest2 = File(dir, "post_cancel.pdf")
        assertFalse(PdfVectorImporter.copyStreamToFile(postReadStream, dest2, isCancelled = { cancelPostRead.get() }))
        assertFalse(dest2.exists())

        val noteDir = File(dir, "note/n1").apply { mkdirs() }
        assertNull(PdfVectorImporter.storeSourceFile(File(dir, "missing.pdf"), noteDir, true))
        assertNull(PdfVectorImporter.storeSourceFile(dir, noteDir, true))
        val inNote = File(noteDir, "existing.pdf").apply { writeText("pdf") }
        assertSame(inNote, PdfVectorImporter.storeSourceFile(inNote, noteDir, isTempFile = false))

        // Same-millisecond storeSourceFile calls must not overwrite each other
        val srcA = File(dir, "srcA.pdf").apply { writeText("AAA") }
        val srcB = File(dir, "srcB.pdf").apply { writeText("BBB") }
        val storedA = PdfVectorImporter.storeSourceFile(srcA, noteDir, isTempFile = true)
        val storedB = PdfVectorImporter.storeSourceFile(srcB, noteDir, isTempFile = true)
        assertNotNull(storedA)
        assertNotNull(storedB)
        assertNotEquals(storedA?.absolutePath, storedB?.absolutePath)
        assertEquals("AAA", storedA?.readText())
        assertEquals("BBB", storedB?.readText())

        // importPages boundary cases & pre-cancel on pageCount == 0
        val batchCounts = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val sink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = 0
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean = true
            override suspend fun insertPageBatch(
                startNotePageIndex: Int,
                pdfPageOffset: Int,
                batchCount: Int,
                reuseFirstPage: Boolean,
                newTotalPageCount: Int
            ) {
                assertTrue(batchCount > 0)
                batchCounts.add(batchCount)
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
        }
        val preCancelledZero = PdfVectorImporter.importPages(0, true, sink, isCancelled = { true })
        assertTrue(preCancelledZero.cancelled)
        assertEquals(0, preCancelledZero.importedPages)
        assertFalse(PdfVectorImporter.importPages(0, true, sink).cancelled)

        val single = PdfVectorImporter.importPages(1, true, sink, batchSize = 0)
        assertEquals(1, single.importedPages)
        assertEquals(listOf(1), batchCounts)

        batchCounts.clear()
        val exact20 = PdfVectorImporter.importPages(
            20,
            true,
            sink,
            batchSize = 20,
            onProgress = { d, t -> progress.add(d to t) }
        )
        assertEquals(20, exact20.importedPages)
        assertEquals(listOf(20), batchCounts)
        assertEquals((1..20).map { it to 20 }, progress)

        // Failed thumbnail must abort & roll back without inserting pages
        var rolledBack = false
        val failingThumbSink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = 0
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean = false
            override suspend fun insertPageBatch(
                startNotePageIndex: Int,
                pdfPageOffset: Int,
                batchCount: Int,
                reuseFirstPage: Boolean,
                newTotalPageCount: Int
            ) {
                error("insertPageBatch must not run when thumbnail fails")
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                rolledBack = true
            }
        }
        val failedThumb = PdfVectorImporter.importPages(5, true, failingThumbSink)
        assertTrue(rolledBack)
        assertEquals(0, failedThumb.importedPages)

        // Early cancellation on ImportedPdf(isTempFile = true) must delete temp file
        val earlyTemp = File(dir, "early_temp.pdf").apply { writeText("temp") }
        val earlySourceCheck = PdfVectorImporter.ImportedPdf(
            document = PdfiumDocument(),
            sourceFile = earlyTemp,
            pageCount = 10,
            isTempFile = true
        )
        earlySourceCheck.close()
        assertFalse(earlyTemp.exists())
    }

    @Test
    fun `T5-P1-12 NoteFileStore atomic write error safety overwrite pathinfo and UiPrefs JVM persistence`() {
        val filesDir = tempFolder.newFolder("t5_filestore")
        val store = NoteFileStore(filesDir)
        val noteId = "n-fs"

        assertFalse(store.writePageFileAtomically(noteId, 0) { /* writes nothing */ })
        val p0 = store.getPageFile(noteId, 0)
        assertTrue(store.writePageFileAtomically(noteId, 0) { it.writeBytes(byteArrayOf(1, 2, 3)) })
        assertArrayEquals(byteArrayOf(1, 2, 3), p0.readBytes())

        // Exception mid-write must preserve existing page_0.png and leave no temp file
        assertFalse(
            store.writePageFileAtomically(noteId, 0) { tmp ->
                tmp.writeBytes(byteArrayOf(9, 9))
                throw RuntimeException("disk full")
            }
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), p0.readBytes())
        assertFalse(store.getTempPageFile(noteId, 0).exists())

        // renamePageFile overwriting destination preserves source contents & moves layer JSON
        store.getPageFile(noteId, 1).writeBytes(byteArrayOf(7, 8))
        store.getLayerInfoFile(noteId, 1).writeText("""[{"name":"L1"}]""")
        assertTrue(store.renamePageFile(noteId, 1, 0))
        assertArrayEquals(byteArrayOf(7, 8), store.getPageFile(noteId, 0).readBytes())
        assertEquals("""[{"name":"L1"}]""", store.getLayerInfoFile(noteId, 0).readText())
        assertFalse(store.getPageFile(noteId, 1).exists())
        assertFalse(store.getLayerInfoFile(noteId, 1).exists())

        // getNoteAsset rejects "." / ".." but allows valid "photo..1.png"
        val validDots = File(store.getNoteDir(noteId), "photo..1.png").apply { writeBytes(byteArrayOf(4)) }
        assertEquals(validDots.absolutePath, store.getNoteAsset(noteId, "photo..1.png")?.absolutePath)
        assertNull(store.getNoteAsset(noteId, ".."))
        assertNull(store.getNoteAsset(noteId, "."))

        // catalog.json pure-JVM round-trip with null/negative edge values
        store.getCatalogFile(noteId).writeText(
            """[{"id":null,"title":"Ch1","pageIndex":-3,"level":-2,"source":null},{"id":"x","title":"  ","pageIndex":1}]"""
        )
        val parsedToc = store.readCatalogToc(noteId)
        assertEquals(1, parsedToc.size)
        assertEquals("Ch1", parsedToc[0].title)
        assertEquals(0, parsedToc[0].pageIndex)
        assertEquals(0, parsedToc[0].level)
        assertEquals("user", parsedToc[0].source)

        assertTrue(store.writeCatalogToc(noteId, listOf(TocItem("id1", "Sec \"1\"\nLine2", 2, 1, "pdf"))))
        val roundTripToc = store.readCatalogToc(noteId)
        assertEquals(1, roundTripToc.size)
        assertEquals("Sec \"1\"\nLine2", roundTripToc[0].title)
        assertEquals("pdf", roundTripToc[0].source)

        // UiPrefs pure-JVM persistence & non-finite float guard
        val prefs = store.uiPrefs()
        prefs.putBoolean(UiPrefs.KEY_EXPORT_INCLUDE_MARGINS, true)
        assertTrue(prefs.getBoolean(UiPrefs.KEY_EXPORT_INCLUDE_MARGINS, false))
        prefs.putFloat("ratio", 1.5f)
        prefs.putFloat("ratio", Float.NaN)
        prefs.putFloat("ratio", Float.POSITIVE_INFINITY)
        assertEquals(1.5f, prefs.getFloat("ratio", 0f), 1e-4f)
        prefs.putString("tab", "pdf-1")
        assertEquals("pdf-1", prefs.getString("tab", "default"))

        store.savePathInfoJson("""{"path":"/a"}""")
        assertEquals("""{"path":"/a"}""", store.readPathInfoJson())
        store.saveUserSettingsJson("""{"u":1}""")
        assertEquals("""{"u":1}""", store.readUserSettingsJson())

        assertTrue(store.deleteNoteFiles(noteId))
        assertFalse(File(filesDir, "note/$noteId").exists())
        assertTrue(store.deleteNoteFiles(noteId))
    }

    @Test
    fun `T5-P1-13 NoteRepository insertPage deletePage batch pattern and hierarchy cycle invariants`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t5_repo"))
        val noteId = "repo-note"
        val note = NoteEntity(id = noteId, title = "Repo", pageCount = 3, pageType = "pdf")
        val pages = listOf(
            PageEntity(id = "p0", noteId = noteId, index = 0, bitmapPath = "p0.png", paperPattern = 1),
            PageEntity(id = "p1", noteId = noteId, index = 1, bitmapPath = "p1.png", paperPattern = 2),
            PageEntity(id = "p2", noteId = noteId, index = 2, bitmapPath = "p2.png", paperPattern = 3)
        )
        env.seedNoteAndPages(note, pages)
        val layer1 = PageLayer(1, "Layer1", true)
        for (i in 0..2) {
            env.fileStore.getPageFile(noteId, i).writeBytes(byteArrayOf(i.toByte()))
            assertTrue(env.repository.savePageLayerOps(noteId, i, 1, listOf(E2eTestHarness.strokeOp(layer1))))
        }

        val inserted = env.repository.insertPage(noteId, 1)
        assertEquals(1, inserted.index)
        assertEquals(2, inserted.paperPattern)
        assertEquals(4, env.notes[noteId]?.pageCount)
        assertEquals(listOf(0, 1, 2, 3), env.repository.getPagesForNoteSync(noteId).map { it.index })
        assertNotNull(env.repository.loadPageLayerOps(noteId, 2, 1))
        assertNotNull(env.repository.loadPageLayerOps(noteId, 3, 1))

        val appended = env.repository.addPage(noteId)
        assertEquals(4, appended.index)
        assertEquals(5, env.notes[noteId]?.pageCount)

        assertFalse(env.repository.deletePage(noteId, -1))
        assertFalse(env.repository.deletePage(noteId, 99))
        assertTrue(env.repository.deletePage(noteId, 0))
        assertEquals(listOf(0, 1, 2, 3), env.repository.getPagesForNoteSync(noteId).map { it.index })
        assertEquals(4, env.notes[noteId]?.pageCount)

        // Delete down to 1 page, then deletePage must return false
        assertTrue(env.repository.deletePage(noteId, 3))
        assertTrue(env.repository.deletePage(noteId, 2))
        assertTrue(env.repository.deletePage(noteId, 1))
        assertFalse(env.repository.deletePage(noteId, 0))

        // Pattern lookup by PageEntity.index even when indices have a gap
        env.seedNoteAndPages(
            NoteEntity(id = "gap-note", title = "Gap", pageCount = 2, pageType = "a4"),
            listOf(
                PageEntity(id = "g0", noteId = "gap-note", index = 0, bitmapPath = "g0.png", paperPattern = 0),
                PageEntity(id = "g2", noteId = "gap-note", index = 2, bitmapPath = "g2.png", paperPattern = 0)
            )
        )
        assertFalse(env.repository.savePagePattern("gap-note", 1, 2))
        assertTrue(env.repository.savePagePattern("gap-note", 2, 3))
        assertEquals(3, env.repository.loadPagePattern("gap-note", 2))
        assertEquals(0, env.repository.loadPagePattern("gap-note", 1))

        // appendPdfPagesBatch batchCount=0 no-op and second batch offset
        val prevBatches = env.insertAllBatches.size
        env.repository.appendPdfPagesBatch(noteId, 0, "doc.pdf", 0, batchCount = 0, reuseFirstPage = true, newTotalPageCount = 1)
        assertEquals(prevBatches, env.insertAllBatches.size)
        env.repository.appendPdfPagesBatch(noteId, 0, "doc.pdf", pdfPageOffset = 20, batchCount = 10, reuseFirstPage = true, newTotalPageCount = 30)
        assertEquals(10, env.insertAllBatches.last().size)
        assertEquals(20, env.insertAllBatches.last().first().index)

        // Deleting non-existent layer files must not bump updatedAt
        val touchBefore = env.touchedUpdatedAtCalls.size
        assertFalse(env.repository.deletePageLayerTile(noteId, 9, 1, 0, 0))
        assertTrue(env.repository.deletePageLayerTiles(noteId, 9, 1))
        assertFalse(env.repository.deletePageLayerOps(noteId, 9, 1))
        assertEquals(touchBefore, env.touchedUpdatedAtCalls.size)

        // 2-level sub-page delete and cyclic ancestor guard
        val root = NoteEntity(id = "root", title = "Root", pageCount = 1)
        val child = NoteEntity(id = "child", title = "Child", pageCount = 1, parentNoteId = "root")
        val grand = NoteEntity(id = "grand", title = "Grand", pageCount = 1, parentNoteId = "child")
        env.seedNoteAndPages(root, listOf(PageEntity("rp0", "root", 0, "rp0.png")))
        env.seedNoteAndPages(child, listOf(PageEntity("cp0", "child", 0, "cp0.png")))
        env.seedNoteAndPages(grand, listOf(PageEntity("gp0", "grand", 0, "gp0.png")))
        env.fileStore.getPageFile("grand", 0).writeBytes(byteArrayOf(1))
        env.repository.permanentlyDeleteNote("root")
        assertNull(env.notes["root"])
        assertNull(env.notes["child"])
        assertNull(env.notes["grand"])
        assertFalse(File(env.filesDir, "note/grand").exists())

        val cycA = NoteEntity(id = "cycA", title = "A", parentNoteId = "cycB")
        val cycB = NoteEntity(id = "cycB", title = "B", parentNoteId = "cycA")
        env.seedNoteAndPages(cycA, emptyList())
        env.seedNoteAndPages(cycB, emptyList())
        val ancestors = env.repository.getAncestors("cycA")
        assertTrue(ancestors.size <= 2)
    }

    // --- Partition 2: T5-P2-01 .. T5-P2-12 ---

    @Test
    fun `T5-P2-01 PageLayerStack identity stability under HashMap and InkHistory undo redo`() {
        val stack = PageLayerStack.base()
        val l1 = stack.layers[0]
        val l2 = stack.add() ?: error("Layer 2 missing")
        val l3 = stack.add() ?: error("Layer 3 missing")

        val tileMap = HashMap<PageLayer, String>()
        tileMap[l1] = "tiles_1"
        tileMap[l2] = "tiles_2"
        tileMap[l3] = "tiles_3"

        val history = InkHistory(stack)
        val opOnL3 = E2eTestHarness.strokeOp(l3)
        history.commit(opOnL3)

        // Removing Layer 2 renumbers l3.index from 3 -> 2; HashMap lookup by identity must stay intact
        val removed = stack.remove(2)
        assertSame(l2, removed)
        assertEquals(2, l3.index)
        assertEquals("tiles_3", tileMap[l3])
        assertEquals("tiles_1", tileMap[l1])

        // Undo removeLayer via InkHistory restores l2 without colliding with l3 in HashMap
        stack.restore(l2, 2)
        assertEquals(3, l3.index)
        assertEquals("tiles_2", tileMap[l2])
        assertEquals("tiles_3", tileMap[l3])

        val mergeHistory = InkHistory(stack)
        val opOnL2 = E2eTestHarness.strokeOp(l2)
        mergeHistory.commit(opOnL2)
        mergeHistory.commitRemoveLayer(l2, merged = false)
        assertEquals(2, stack.size)
        assertEquals("tiles_3", tileMap[l3])
        assertTrue(mergeHistory.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(3, stack.size)
        assertEquals("tiles_2", tileMap[l2])
        assertEquals("tiles_3", tileMap[l3])
    }

    @Test
    fun `T5-P2-02 CanvasViewModel full lifecycle on JVM`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t5_vm_life"))
        val noteId = "vm-life-note"
        val note = NoteEntity(
            id = noteId,
            title = "VM Lifecycle",
            pageCount = 5,
            pageType = "pdf",
            lastPageIndex = 3
        )
        val pages = (0 until 5).map { i ->
            PageEntity(id = "$noteId-p$i", noteId = noteId, index = i, bitmapPath = "page_$i.png")
        }
        env.seedNoteAndPages(note, pages)
        env.fileStore.uiPrefs().putBoolean(UiPrefs.KEY_EXPORT_INCLUDE_MARGINS, true)
        env.fileStore.writeCatalogToc(
            noteId,
            listOf(
                TocItem(id = "u0", title = "User 0", pageIndex = 0, source = PdfOutlineImporter.SOURCE_USER),
                TocItem(id = "p2", title = "PDF 2", pageIndex = 2, source = PdfOutlineImporter.SOURCE_PDF),
                TocItem(id = "u4", title = "User 4", pageIndex = 4, source = PdfOutlineImporter.SOURCE_USER)
            )
        )
        File(env.fileStore.getNoteDir(noteId), "bookmarks.json").writeText("[0,2,4]")

        val store = ViewModelStore()
        val vm = CanvasViewModel(noteId, env.repository, env.fileStore, env.settingsRepository)
        store.put("vm", vm)
        val noteCollector = backgroundScope.launch(testDispatcher) { vm.note.collect {} }
        val pagesCollector = backgroundScope.launch(testDispatcher) { vm.pages.collect {} }

        try {
            awaitUntil { vm.uiState.value.tocEntries.size == 3 && vm.uiState.value.exportIncludeMargins }
            assertEquals(3, vm.currentPageIndex.value)
            assertEquals(setOf(0, 2, 4), vm.uiState.value.bookmarkedPages)

            // Insert at index 2 -> pages=6, bookmarks={0,3,5}, TOC indices >= 2 shift +1
            val insertedDone = CompletableDeferred<Unit>()
            vm.insertNewPage(atIndex = 2, isContinuous = true) { insertedDone.complete(Unit) }
            insertedDone.await()
            assertEquals(6, vm.pages.value.size)
            assertEquals(setOf(0, 3, 5), vm.uiState.value.bookmarkedPages)
            assertEquals(listOf(0, 3, 5), vm.uiState.value.tocEntries.map { it.pageIndex })
            awaitUntil {
                val bm = File(env.fileStore.getNoteDir(noteId), "bookmarks.json")
                bm.exists() && bm.readText() == "[0,3,5]"
            }
            assertEquals(listOf(0, 3, 5), env.fileStore.readCatalogToc(noteId).map { it.pageIndex })

            // Delete page 2 -> pages=5, bookmarks={0,2,4} (since 3->2, 5->4), TOC={0,2,4}
            val deleteDone = CompletableDeferred<Unit>()
            vm.deletePage(pageIndex = 2, isContinuous = true) { deleteDone.complete(Unit) }
            deleteDone.await()
            assertEquals(5, vm.pages.value.size)
            assertEquals(setOf(0, 2, 4), vm.uiState.value.bookmarkedPages)
            assertEquals(listOf(0, 2, 4), vm.uiState.value.tocEntries.map { it.pageIndex })

            // reimportPdfOutline with missing PDF preserves SOURCE_USER entries
            val reimportDone = CompletableDeferred<Int>()
            vm.reimportPdfOutline { count -> reimportDone.complete(count) }
            assertEquals(0, reimportDone.await())
            assertEquals(listOf("u0", "u4"), vm.uiState.value.tocEntries.map { it.id })

            // addTocEntry & toggleBookmark
            vm.addTocEntry("User Added", pageIndex = 1)
            assertTrue(vm.uiState.value.tocEntries.any { it.title == "User Added" && it.pageIndex == 1 })
            vm.toggleBookmark(1)
            assertTrue(1 in vm.uiState.value.bookmarkedPages)

            // setExportIncludeMargins persists to UiPrefs
            vm.setExportIncludeMargins(false)
            assertFalse(vm.uiState.value.exportIncludeMargins)
            awaitUntil { !env.fileStore.uiPrefs().getBoolean(UiPrefs.KEY_EXPORT_INCLUDE_MARGINS, true) }
        } finally {
            noteCollector.cancel()
            pagesCollector.cancel()
            store.clear()
        }

        // Clamping oversized lastPageIndex = 99
        val noteClamp = NoteEntity(id = "vm-clamp", title = "Clamp", pageCount = 5, pageType = "pdf", lastPageIndex = 99)
        env.seedNoteAndPages(
            noteClamp,
            (0 until 5).map { i -> PageEntity("c-p$i", "vm-clamp", i, "page_$i.png") }
        )
        val store2 = ViewModelStore()
        val vm2 = CanvasViewModel("vm-clamp", env.repository, env.fileStore, env.settingsRepository)
        store2.put("vm2", vm2)
        val cNote = backgroundScope.launch(testDispatcher) { vm2.note.collect {} }
        val cPages = backgroundScope.launch(testDispatcher) { vm2.pages.collect {} }
        try {
            assertEquals(4, vm2.currentPageIndex.value)
        } finally {
            cNote.cancel()
            cPages.cancel()
            store2.clear()
        }
    }

    @Test
    fun `T5-P2-03 CanvasViewModel tool-switching and PDF vs A4 pageTurningMode isolation`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t5_vm_tools"))
        val pdfNoteId = "vm-pdf-mode"
        env.seedNoteAndPages(
            NoteEntity(id = pdfNoteId, title = "PDF", pageCount = 2, pageType = "pdf"),
            listOf(
                PageEntity("p0", pdfNoteId, 0, "p0.png"),
                PageEntity("p1", pdfNoteId, 1, "p1.png")
            )
        )
        env.settingsRepository.updatePageTurningMode(1)
        env.settingsRepository.updatePdfPageTurningMode(0)

        val store = ViewModelStore()
        val vm = CanvasViewModel(pdfNoteId, env.repository, env.fileStore, env.settingsRepository)
        store.put("vm", vm)
        val noteCol = backgroundScope.launch(testDispatcher) { vm.note.collect {} }
        val pagesCol = backgroundScope.launch(testDispatcher) { vm.pages.collect {} }
        try {
            vm.selectTool(CanvasTool.PEN)
            vm.selectTool(CanvasTool.HIGHLIGHTER)
            vm.selectTool(CanvasTool.ERASER)
            vm.cyclePreviousTool()
            assertEquals(CanvasTool.HIGHLIGHTER, vm.uiState.value.selectedTool)

            vm.selectEraserTool()
            assertEquals(CanvasTool.ERASER, vm.uiState.value.selectedTool)
            vm.selectEraserTool()
            assertEquals(CanvasTool.HIGHLIGHTER, vm.uiState.value.selectedTool)

            assertEquals(0, vm.uiState.value.pageTurningMode)
            vm.setPageTurningModeTransient(1)
            assertEquals(1, vm.uiState.value.pageTurningMode)
            assertEquals(0, env.settingsRepository.getSettingsDirect().pdfPageTurningMode)
            assertEquals(1, env.settingsRepository.getSettingsDirect().pageTurningMode)
        } finally {
            noteCol.cancel()
            pagesCol.cancel()
            store.clear()
        }
    }

    @Test
    fun `T5-P2-04 ContinuousOpCodec InkHistory and PageLayerStack multi-layer erase transform merge round-trip`() {
        val stack = PageLayerStack.base()
        val l1 = stack.layers[0]
        val l2 = stack.add() ?: error("Layer 2 missing")
        val l3 = stack.add() ?: error("Layer 3 missing")
        val history = InkHistory(stack)

        val penOp = InkOp.Stroke(
            ContinuousStroke(CanvasTool.PEN, 0xFF111111.toInt(), 0.005f, false, mutableListOf(InkPoint(0.2f, 0.2f, 0.005f), InkPoint(0.4f, 0.2f, 0.006f))),
            null,
            l1
        )
        val fountainOp = InkOp.Stroke(
            ContinuousStroke(CanvasTool.FOUNTAIN_PEN, 0xFF222222.toInt(), 0.006f, true, mutableListOf(InkPoint(0.3f, 0.3f, 0.004f), InkPoint(0.5f, 0.4f, 0.008f))),
            null,
            l2
        )
        val pencilOp = InkOp.Stroke(
            ContinuousStroke(CanvasTool.PENCIL, 0xFF333333.toInt(), 0.007f, false, mutableListOf(InkPoint(0.1f, 0.6f, 0.007f), InkPoint(0.3f, 0.7f, 0.007f))),
            null,
            l2
        )
        val highlighterOp = InkOp.Stroke(
            ContinuousStroke(CanvasTool.HIGHLIGHTER, 0x66FFFF00, 0.015f, true, mutableListOf(InkPoint(0.4f, 0.5f, 0.015f), InkPoint(0.7f, 0.5f, 0.015f))),
            null,
            l3
        )
        val eraseOp = InkOp.Erase(
            ContinuousStroke(CanvasTool.ERASER, 0, 0.02f, false, mutableListOf(InkPoint(0.3f, 0.3f, 0.02f), InkPoint(0.35f, 0.35f, 0.02f))),
            listOf(
                InkPatch(0, 0, 0, 0, 2, 2, intArrayOf(1, 2, 3, 4), intArrayOf(0, 0, 0, 0), target = null),
                InkPatch(1, 0, 10, 10, 2, 2, intArrayOf(5, 6, 7, 8), intArrayOf(0, 0, 0, 0), target = null)
            ),
            l2
        )
        listOf(penOp, fountainOp, pencilOp, highlighterOp, eraseOp).forEach { history.commit(it) }

        // Transform Layer 2 strokes (rotate, scale, mirror)
        val rotated = ContinuousStrokeTransformMath.rotatePoints(fountainOp.stroke.points, (Math.PI / 2).toFloat(), 0.4f, 0.35f, 0.707f, 1f)
        val scaled = ContinuousStrokeTransformMath.scalePoints(rotated, 1.2f, 1.2f, 0.4f, 0.35f, 1f)
        val mirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(scaled, 0.4f, 1f)
        fountainOp.stroke.points.clear()
        fountainOp.stroke.points.addAll(mirrored)

        // Merge Layer 2 into Base, undo, redo, then undo back so Layer 2 keeps its own ops
        history.commitRemoveLayer(l2, merged = true)
        assertEquals(2, stack.size)
        assertSame(l1, fountainOp.layer)
        assertSame(l1, pencilOp.layer)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(3, stack.size)
        assertSame(l2, fountainOp.layer)
        assertSame(l2, pencilOp.layer)

        assertTrue(history.redo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(2, stack.size)
        assertSame(l1, fountainOp.layer)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertSame(l2, fountainOp.layer)

        // Encode & decode per layer
        val l1Encoded = ContinuousOpCodec.encode(history.ops.filter { it.layer === l1 })
        val l2Encoded = ContinuousOpCodec.encode(history.ops.filter { it.layer === l2 })
        val l3Encoded = ContinuousOpCodec.encode(history.ops.filter { it.layer === l3 })

        val l1Decoded = ContinuousOpCodec.decode(l1Encoded) ?: error("l1 decode null")
        val l2Decoded = ContinuousOpCodec.decode(l2Encoded) ?: error("l2 decode null")
        val l3Decoded = ContinuousOpCodec.decode(l3Encoded) ?: error("l3 decode null")
        assertEquals(1, l1Decoded.size)
        assertEquals(3, l2Decoded.size)
        assertEquals(1, l3Decoded.size)
        assertEquals(CanvasTool.HIGHLIGHTER, (l3Decoded[0] as InkOp.Stroke).stroke.tool)
        assertTrue((l2Decoded[0] as InkOp.Stroke).stroke.autoShapeEnabled)
        assertTrue(l2Decoded[2] is InkOp.Erase)
    }

    @Test
    fun `T5-P2-05 ExportManager sanitizeFilename adversarial OS Unicode and control-char matrix`() {
        val baseDir = tempFolder.newFolder("t5_sanitize").canonicalFile
        val inputs = listOf(
            "",
            "   ",
            "..",
            ".",
            "../secret/../../note",
            """a<b>c:d"e/f\g|h?i*j""",
            "null\u0000byte\r\n",
            "x".repeat(300),
            "講義ノート📄2026"
        )
        val forbidden = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000', '\r', '\n')
        for (raw in inputs) {
            val clean = ExportManager.sanitizeFilename(raw)
            assertTrue("Output must be non-empty for '$raw'", clean.isNotEmpty())
            assertTrue("Output too long: ${clean.length}", clean.length <= 120)
            for (ch in forbidden) {
                assertFalse("Forbidden char $ch in '$clean'", clean.contains(ch))
            }
            val resolved = File(baseDir, "$clean.pdf").canonicalFile
            assertEquals("Path escaped baseDir for '$raw' -> '$clean'", baseDir, resolved.parentFile)
        }
        val cjk = ExportManager.sanitizeFilename("講義ノート📄2026")
        assertTrue(cjk.contains("講義ノート") && cjk.contains("2026"))
    }

    @Test
    fun `T5-P2-06 MarginExportMath and PdfExportPolicy multi-page PDF margin export consistency`() {
        val pages = listOf(
            595f to 842f,
            960f to 540f,
            600f to 600f,
            100f to 5000f
        )
        for ((wPts, hPts) in pages) {
            val aspect = PdfPageGeometry.aspect(wPts, hPts)
            val layerSize = PdfExportPolicy.resolveAnnotationLayerSize("pdf", aspect, 0, 0)
            val cropPageOnly = MarginExportMath.cropRect(layerSize.width, layerSize.height, aspect, 1f, includeMargins = false)
            assertTrue(cropPageOnly.cropped)
            assertEquals(layerSize.width / 3, cropPageOnly.left)
            val cropRatio = cropPageOnly.width.toFloat() / cropPageOnly.height.toFloat()
            val expectedRatio = layerSize.width.toFloat() / 3f / layerSize.height.toFloat()
            assertEquals(expectedRatio, cropRatio, 0.01f)

            val cropWithMargins = MarginExportMath.cropRect(layerSize.width, layerSize.height, aspect, 1f, includeMargins = true)
            assertFalse(cropWithMargins.cropped)
            assertEquals(layerSize.width, cropWithMargins.outputWidth)

            val ptsNoMargin = MarginExportMath.exportPdfPageSizePts(aspect, 1f, includeMargins = false)
            val ptsWithMargin = MarginExportMath.exportPdfPageSizePts(aspect, 1f, includeMargins = true)
            assertTrue(ptsNoMargin.first >= 1 && ptsNoMargin.second >= 1)
            assertTrue(ptsWithMargin.first >= 1 && ptsWithMargin.second >= 1)
            if (aspect > 1f) assertTrue(ptsNoMargin.first >= ptsNoMargin.second)
            if (aspect < 1f) assertTrue(ptsNoMargin.first <= ptsNoMargin.second)
        }

        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(0, 0, 0f, 0f))
        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(-1, -1, Float.NaN, Float.NaN))

        val grid = LayerTileGrid(6480, 3056)
        var prevRight = -1.0f
        for (tx in 0 until grid.columns) {
            val span = MarginExportMath.tileNormalizedXSpan(tx, grid.width, grid.tileSize, 1f)
            assertNotNull(span)
            val s = span ?: error("span null")
            if (tx > 0) assertEquals(prevRight, s.first, 1e-5f)
            prevRight = s.second
        }
        assertEquals(-1f, MarginExportMath.tileNormalizedXSpan(0, grid.width, grid.tileSize, 1f)?.first ?: 0f, 1e-5f)
        assertEquals(2f, prevRight, 1e-5f)

        val leftOnly = MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.2f, 0.5f), 1f)
        assertTrue(leftOnly.hasLeftMarginInk && !leftOnly.hasRightMarginInk)
        val rightOnly = MarginExportMath.detectMarginInkFromNormalizedX(listOf(0.5f, 1.2f), 1f)
        assertTrue(!rightOnly.hasLeftMarginInk && rightOnly.hasRightMarginInk)
    }

    @Test
    fun `T5-P2-07 PdfTextIndex multi-PDF overlapping surrogate-pair cancel LRU and canvas rect round-trip`() {
        PdfTextIndex.clearCache()
        val pageTexts = Array(12) { idx ->
            when (idx) {
                0 -> "ababababa"
                1 -> "A\uD834\uDD1EB match\r\nnext line match"
                else -> "Page $idx content"
            }
        }
        val idx = PdfTextIndex.buildIndex(12, pageTextProvider = { pageTexts[it] }) ?: error("index null")
        val hitsAba = PdfTextIndex.search(idx, "aba")
        assertTrue(hitsAba.isNotEmpty())
        for (h in hitsAba) {
            assertTrue(pageTexts[h.pageIndex].substring(h.startOffset, h.startOffset + h.length).equals("aba", ignoreCase = true))
        }
        val hitsMatch = PdfTextIndex.search(idx, "match")
        assertEquals(2, hitsMatch.size)
        for (h in hitsMatch) {
            assertEquals("match", pageTexts[h.pageIndex].substring(h.startOffset, h.startOffset + h.length))
        }

        // Mid-build cancel at page 5 stops calling pageTextProvider for remaining pages
        val visited = mutableListOf<Int>()
        val cancelAt5 = AtomicBoolean(false)
        val cancelled = PdfTextIndex.buildIndex(
            pageCount = 12,
            pageTextProvider = { p ->
                visited.add(p)
                if (p == 5) cancelAt5.set(true)
                "p$p"
            },
            isCancelled = { cancelAt5.get() }
        )
        assertNull(cancelled)
        assertEquals((0..5).toList(), visited)

        // Canvas rect mapping bottom-left [0, hPts] -> top [0] and [wPts, 0] -> bottom [canvasH]
        val wPts = 600f
        val hPts = 800f
        val scale = PdfPageGeometry.canvasScale(wPts, 1200)
        val rect = PdfPageGeometry.userRectToCanvas(floatArrayOf(0f, hPts, wPts, 0f), hPts, scale)
        assertEquals(0f, rect[0], 1e-3f)
        assertEquals(0f, rect[1], 1e-3f)
        assertEquals(1200f, rect[2], 1e-3f)
        assertEquals(1600f, rect[3], 1e-3f)
    }

    @Test
    fun `T5-P2-08 PdfOutlineImporter deep-tree sibling-cap invalid-pages and merge preservation`() {
        var deepNode = OutlineTreeNode("Leaf 20", 1)
        for (lvl in 19 downTo 0) {
            deepNode = OutlineTreeNode("Level $lvl", 1, listOf(deepNode))
        }
        val flatDeep = PdfOutlineImporter.flattenTree(listOf(deepNode))
        assertTrue(flatDeep.all { it.level in 0..PdfOutlineImporter.MAX_LEVEL })
        assertEquals(PdfOutlineImporter.MAX_LEVEL + 1, flatDeep.size)

        val siblings = (0 until 650).map { i -> OutlineNode("S$i", pageIndex = i % 10, level = 0) } +
            listOf(OutlineNode("Neg", -1, 0), OutlineNode("OOB", 10, 0))
        val imported = PdfOutlineImporter.importNodes(siblings, pageCount = 10)
        assertEquals(PdfOutlineImporter.DEFAULT_MAX_ENTRIES, imported.size)
        assertTrue(imported.all { it.pageIndex in 0 until 10 })

        val existing = listOf(
            TocItem(id = "u1", title = "User 1", pageIndex = 1, source = PdfOutlineImporter.SOURCE_USER),
            TocItem(id = "u2", title = "User 2", pageIndex = 5, source = PdfOutlineImporter.SOURCE_USER),
            TocItem(id = "stale", title = "Old PDF", pageIndex = 2, source = PdfOutlineImporter.SOURCE_PDF)
        )
        val merged = PdfOutlineImporter.mergeWithUserEntries(existing, imported.take(5))
        assertTrue(merged.any { it.id == "u1" })
        assertTrue(merged.any { it.id == "u2" })
        assertFalse(merged.any { it.id == "stale" })
    }

    @Test
    fun `T5-P2-09 PageScrubberMath bidirectional consistency and degenerate track geometry`() {
        for (total in listOf(0, 1, 5)) {
            assertFalse(PageScrubberMath.isScrubberEnabled(total))
        }
        for (total in listOf(6, 7, 500)) {
            assertTrue(PageScrubberMath.isScrubberEnabled(total))
            for (p in 0 until total) {
                val frac = PageScrubberMath.pageToScrubFraction(p, total)
                val mapped = PageScrubberMath.scrubFractionToPage(frac, total)
                assertEquals("total=$total p=$p", p, mapped)
            }
        }
        val degenerates = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1e9f, 1e9f)
        for (v in degenerates) {
            val step = PageScrubberMath.dragDeltaToFractionStep(v, v, 0, v)
            assertTrue(step.isFinite())
            val applied = PageScrubberMath.applyScrubDelta(v, v, v, 0, v)
            assertTrue(applied.isFinite() && applied in 0f..1f)
        }
    }

    @Test
    fun `T5-P2-10 PdfVectorImporter partial-stream IOException and batch boundary cancellation rollback`() = runTest {
        val dir = tempFolder.newFolder("t5_vec_cancel")
        val failingStream = object : InputStream() {
            private var n = 0
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (n++ == 0) {
                    b[off] = 0x25
                    return 1
                }
                throw IOException("network/SAF drop")
            }
        }
        val destErr = File(dir, "err.pdf")
        assertFalse(PdfVectorImporter.copyStreamToFile(failingStream, destErr))
        assertFalse(destErr.exists())

        val emptyDest = File(dir, "empty.pdf")
        assertFalse(PdfVectorImporter.copyStreamToFile(ByteArrayInputStream(ByteArray(0)), emptyDest))
        assertFalse(emptyDest.exists())

        for (cancelAt in listOf(1, 21, 41)) {
            val committed = mutableListOf<Int>()
            var rolledBack = false
            val cancelFlag = AtomicBoolean(false)
            val sink = object : PdfVectorImporter.PdfImportSink {
                override suspend fun nextPageIndex(): Int = 0
                override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean = true
                override suspend fun insertPageBatch(
                    startNotePageIndex: Int,
                    pdfPageOffset: Int,
                    batchCount: Int,
                    reuseFirstPage: Boolean,
                    newTotalPageCount: Int
                ) {
                    for (k in 0 until batchCount) committed.add(pdfPageOffset + k)
                }
                override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                    rolledBack = true
                    committed.clear()
                }
            }
            val outcome = PdfVectorImporter.importPages(
                pageCount = 41,
                reuseFirstPage = true,
                sink = sink,
                batchSize = 20,
                onProgress = { done, _ -> if (done >= cancelAt) cancelFlag.set(true) },
                isCancelled = { cancelFlag.get() }
            )
            assertTrue("cancelAt=$cancelAt must cancel", outcome.cancelled)
            assertEquals(0, outcome.importedPages)
            assertTrue("cancelAt=$cancelAt must roll back", rolledBack)
            assertTrue("cancelAt=$cancelAt committed must be empty", committed.isEmpty())
        }
    }

    @Test
    fun `T5-P2-11 ContinuousStrokeTransformMath isotropic aspect invariance and mirror involution`() {
        val samplePts = listOf(
            InkPoint(0.3f, 0.4f, 0.005f),
            InkPoint(0.6f, 0.4f, 0.005f),
            InkPoint(0.6f, 0.7f, 0.005f)
        )
        for (ratio in listOf(0.25f, 0.707f, 1.0f, 16f / 9f, 4.0f)) {
            val d01Before = ContinuousStrokeTransformMath.isotropicDistance(samplePts[0], samplePts[1], ratio)
            val d12Before = ContinuousStrokeTransformMath.isotropicDistance(samplePts[1], samplePts[2], ratio)
            val rotated = ContinuousStrokeTransformMath.rotatePoints(
                points = samplePts,
                deltaRad = 0.73f,
                pivotXNorm = 0.45f,
                pivotYNorm = 0.55f,
                boxRatio = ratio,
                margin = 1f,
                clampToBounds = false
            )
            assertEquals(d01Before, ContinuousStrokeTransformMath.isotropicDistance(rotated[0], rotated[1], ratio), 1e-4f)
            assertEquals(d12Before, ContinuousStrokeTransformMath.isotropicDistance(rotated[1], rotated[2], ratio), 1e-4f)

            // Mirror twice with same pivotX is an involution
            val m1 = ContinuousStrokeTransformMath.mirrorHorizontalPoints(samplePts, 0.45f, margin = 1f, clampToBounds = false)
            val m2 = ContinuousStrokeTransformMath.mirrorHorizontalPoints(m1, 0.45f, margin = 1f, clampToBounds = false)
            samplePts.forEachIndexed { idx, p ->
                assertEquals(p.x, m2[idx].x, 1e-5f)
                assertEquals(p.y, m2[idx].y, 1e-5f)
            }
        }

        // Zero-area single-point stroke never produces NaN/Infinity under rotate or scale
        val singlePt = listOf(InkPoint(0.5f, 0.5f, 0.005f))
        val rotSingle = ContinuousStrokeTransformMath.rotatePoints(singlePt, 1.2f, 0.5f, 0.5f, 16f / 9f, margin = 0f)
        val scaleSingle = ContinuousStrokeTransformMath.scalePoints(singlePt, 2f, 2f, 0.5f, 0.5f, margin = 0f)
        assertTrue(rotSingle.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(scaleSingle.all { it.x.isFinite() && it.y.isFinite() })

        // clampTranslationDelta guarantees moved points stay within [-margin, 1+margin] x [0, 1]
        for (margin in listOf(0f, 1f)) {
            val st = ContinuousStroke(CanvasTool.PEN, 0, 0.005f, points = samplePts.toMutableList())
            val bounds = ContinuousStrokeTransformMath.pointsBounds(listOf(st)) ?: error("bounds null")
            val (dx, dy) = ContinuousStrokeTransformMath.clampTranslationDelta(bounds, -10f, 10f, margin)
            val moved = ContinuousStrokeTransformMath.translatePoints(samplePts, dx, dy, margin, clampToBounds = false)
            assertTrue(moved.all { it.x in (-margin - 1e-4f)..(1f + margin + 1e-4f) && it.y in -1e-4f..(1f + 1e-4f) })
        }
    }

    @Test
    fun `T5-P2-12 source-contract AUDIT integrity and B-01 to B-12 wiring lock`() {
        val repoRoot = E2eTestHarness.locateRepoRoot()
        val testFiles = File(repoRoot, "app/src/test").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val totalTests = testFiles.sumOf { file ->
            Regex("""^\s*@Test\b""", RegexOption.MULTILINE).findAll(file.readText()).count()
        }
        val e2eTests = File(repoRoot, "app/src/test/java/personal/notes/touch/e2e").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { file ->
                Regex("""^\s*@Test\b""", RegexOption.MULTILINE).findAll(file.readText()).count()
            }
        assertTrue("Expected >= 698 @Test functions, got $totalTests", totalTests >= 698)
        assertTrue("Expected >= 63 test files, got ${testFiles.size}", testFiles.size >= 63)
        assertTrue("Expected >= 213 e2e tests, got $e2eTests", e2eTests >= 213)

        // Static invariants
        val appSrc = File(repoRoot, "app/src")
        assertEquals(emptyList<String>(), E2eTestHarness.scanKtFilesForToken(appSrc, E2eTestHarness.FORBIDDEN_NOT_NULL_OP))
        assertEquals(emptyList<String>(), E2eTestHarness.scanKtFilesForToken(File(repoRoot, "app/src/main"), "runBlocking"))
        val manifest = E2eTestHarness.readRepoFile("app/src/main/AndroidManifest.xml")
        assertFalse(manifest.contains("<uses-permission"))

        // AUDIT.md consistency
        val audit = E2eTestHarness.readRepoFile("state/AUDIT.md")
        assertTrue("AUDIT.md missing exact total test count $totalTests", audit.contains("$totalTests total"))
        assertTrue("AUDIT.md missing exact file count ${testFiles.size}", audit.contains("${testFiles.size} suites"))
        assertTrue("AUDIT.md missing exact e2e count $e2eTests", audit.contains("$e2eTests `e2e/` tests"))
        for (id in E2eTestHarness.ALL_15_FEATURE_IDS) {
            val sec = E2eTestHarness.extractAuditSection(audit, id)
            assertTrue("Missing AUDIT section for $id", sec.isNotEmpty())
            assertTrue("Missing [REQUIRES USER TESTING] in $id", sec.contains("[REQUIRES USER TESTING]"))
        }

        // Verify every referenced file in Section 6 exists on disk
        val sec6 = audit.substring(audit.indexOf("## 6. PDF Mode Fixes & QoL"))
        val pathRegex = Regex("""`(app/src/[^`]+)`""")
        for (m in pathRegex.findAll(sec6)) {
            val rel = m.groupValues[1]
            assertTrue("Referenced AUDIT path missing on disk: $rel", File(repoRoot, rel).exists())
        }

        // Verify B-01..B-12 structural wiring locks
        val layerStackSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/PageLayerStack.kt")
        val pageLayerBlock = layerStackSrc.substring(layerStackSrc.indexOf("class PageLayer("), layerStackSrc.indexOf("class PageLayerStack"))
        assertFalse("B-01: PageLayer must not override equals", pageLayerBlock.contains("override fun equals"))
        assertFalse("B-01: PageLayer must not override hashCode", pageLayerBlock.contains("override fun hashCode"))

        val inkCtrlSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt")
        assertTrue("B-03: minWidthNorm = effectiveWidthNorm must be passed", inkCtrlSrc.contains("minWidthNorm = effectiveWidthNorm"))
        val drawLaserBlock = inkCtrlSrc.substring(inkCtrlSrc.indexOf("private fun drawLaser("), inkCtrlSrc.indexOf("private fun drawStroke("))
        assertFalse("B-04: drawLaser must not mutate laserTrail", drawLaserBlock.contains("laserTrail.removeAt"))

        val exportMgrSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/ExportManager.kt")
        assertTrue("B-05: MarginExportMath.exportPdfPageSizePts must be wired", exportMgrSrc.contains("MarginExportMath.exportPdfPageSizePts"))
        val infExportBlock = exportMgrSrc.substring(exportMgrSrc.indexOf("private suspend fun loadInfiniteExportBitmap("), exportMgrSrc.indexOf("private suspend fun loadAnnotationLayer("))
        assertTrue("B-06: loadInfiniteExportBitmap must honor flipHorizontal", infExportBlock.contains("item.flipHorizontal"))
        assertTrue("B-06: loadInfiniteExportBitmap must use ContinuousStrokeRenderer", infExportBlock.contains("ContinuousStrokeRenderer.drawStroke"))

        val canvasSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue("B-11: SWITCH_TO_SINGLE_AND_ACTIVATE must use transient mode", canvasSrc.contains("setPageTurningModeTransient(1)"))
        assertTrue("B-07: vertical LazyColumn must pass allowLeftPanReveal = horizontalPanReveal", canvasSrc.contains("allowLeftPanReveal = horizontalPanReveal"))

        val cBridgeSrc = E2eTestHarness.readRepoFile("app/src/main/cpp/touchnotes_pdfium.c")
        val charBoxBlock = cBridgeSrc.substring(cBridgeSrc.indexOf("PdfiumBridge_textCharBox"), cBridgeSrc.indexOf("#define MAX_OUTLINE_NODES"))
        assertTrue("B-09: textCharBox must call ensure_text_page", charBoxBlock.contains("ensure_text_page(e, pageIndex)"))
        val getCharsBlock = cBridgeSrc.substring(cBridgeSrc.indexOf("PdfiumBridge_textGetChars"), cBridgeSrc.indexOf("PdfiumBridge_textSelectionRectCount"))
        assertTrue("B-09: textGetChars must guard start < 0", getCharsBlock.contains("start < 0"))

        val exportDlgSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/export/ExportDialog.kt")
        assertTrue("B-10: ExportDialog must gate on marginsPrefReady", exportDlgSrc.contains("marginsPrefReady"))
        assertTrue("B-10: ExportDialog must recycle displaced bitmaps", exportDlgSrc.contains("oldBitmap.recycle()"))
    }
}
