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
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.text.MarkdownText
import personal.notes.touch.text.NoteSearch
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LassoHandle
import personal.notes.touch.ui.canvas.NormRect
import personal.notes.touch.ui.canvas.OpEntry
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.PageRestore
import personal.notes.touch.ui.canvas.PageScrubberMath
import personal.notes.touch.ui.canvas.StrokeTransformSnapshot
import personal.notes.touch.ui.canvas.TocEntry
import personal.notes.touch.ui.canvas.TocItem
import personal.notes.touch.util.ExportManager
import personal.notes.touch.util.MarginCropResult
import personal.notes.touch.util.MarginExportMath
import personal.notes.touch.util.MarginInkPresence
import personal.notes.touch.util.OutlineNode
import personal.notes.touch.util.OutlineTreeNode
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfImportManager
import personal.notes.touch.util.PdfOutlineImporter
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfTextIndex
import personal.notes.touch.util.PdfVectorImporter
import personal.notes.touch.util.PdfiumDocument
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Tier 2 E2E Tests — PDF QoL Boundaries & `R4-AUDIT` (`PDF-QOL-1` .. `PDF-QOL-7`, `R4-AUDIT`), Phases A & B.
 */
class Tier2PdfQolBoundariesE2eTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- PDF-QOL-4 Boundaries ---

    @Test
    fun `T2-QOL4-01 ContinuousPageGeometry boxRatio falls back to DEFAULT_PAGE_RATIO for invalid ratios`() {
        assertEquals(ContinuousPageGeometry.DEFAULT_PAGE_RATIO, ContinuousPageGeometry.boxRatio("pdf", 0f), 1e-6f)
        assertEquals(ContinuousPageGeometry.DEFAULT_PAGE_RATIO, ContinuousPageGeometry.boxRatio("pdf", -1.5f), 1e-6f)
        assertEquals(ContinuousPageGeometry.DEFAULT_PAGE_RATIO, ContinuousPageGeometry.boxRatio("pdf", Float.NaN), 1e-6f)
        assertEquals(ContinuousPageGeometry.DEFAULT_PAGE_RATIO, ContinuousPageGeometry.boxRatio("pdf", Float.POSITIVE_INFINITY), 1e-6f)
        assertEquals(ContinuousPageGeometry.DEFAULT_PAGE_RATIO, ContinuousPageGeometry.boxRatio("pdf", Float.NEGATIVE_INFINITY), 1e-6f)
    }

    @Test
    fun `T2-QOL4-02 ContinuousPageGeometry layerSize clamps ultra-wide and ultra-tall ratios safely`() {
        val wide = ContinuousPageGeometry.layerSize("pdf", 5.0f)
        assertEquals(ContinuousPageGeometry.PAGE_WIDTH * 3, wide.width)
        assertEquals((ContinuousPageGeometry.PAGE_WIDTH / 5.0f).roundToInt(), wide.height)
        assertTrue(wide.height >= 1)

        val tall = ContinuousPageGeometry.layerSize("pdf", 0.2f)
        assertEquals(ContinuousPageGeometry.PAGE_WIDTH * 3, tall.width)
        assertEquals((ContinuousPageGeometry.PAGE_WIDTH / 0.2f).roundToInt(), tall.height)
    }

    // --- PDF-QOL-2 Boundaries ---

    @Test
    fun `T2-QOL2-01 PdfImportManager calculateOutputSize bounds degenerate and extreme dims within 1 to MAX_DIMENSION`() {
        val zero = PdfImportManager.calculateOutputSize(0, 0, 0, 0)
        assertTrue(zero.first in 1..PdfImportManager.MAX_DIMENSION)
        assertTrue(zero.second in 1..PdfImportManager.MAX_DIMENSION)

        val negTarget = PdfImportManager.calculateOutputSize(595, 842, -100, -200)
        assertEquals(595 to 842, negTarget)

        val huge = PdfImportManager.calculateOutputSize(10_000, 10_000, 10_000, 10_000)
        assertEquals(PdfImportManager.MAX_DIMENSION, huge.first)
        assertEquals(PdfImportManager.MAX_DIMENSION, huge.second)
    }

    // --- PDF-QOL-1 Boundaries ---

    @Test
    fun `T2-QOL1-01 TocItem and TocEntry out-of-range page targets clamp safely under shrinking pageCount`() {
        val badTargets = listOf(
            TocItem(title = "Neg", pageIndex = -1, level = 0),
            TocItem(title = "Zero", pageIndex = 0, level = 0),
            TocItem(title = "Far", pageIndex = 999, level = 1)
        )
        for (item in badTargets) {
            assertEquals(0, PageRestore.restoredPageIndex(item.pageIndex, pageCount = 0))
            val clamped = PageRestore.restoredPageIndex(item.pageIndex, pageCount = 2)
            assertTrue(clamped in 0..1)
        }

        val entry = TocEntry(title = "Orphan", page = 999)
        assertEquals(1, PageRestore.restoredPageIndex(entry.page, pageCount = 2))
    }

    // --- PDF-QOL-3 Boundaries ---

    @Test
    fun `T2-QOL3-01 NoteSearch and MarkdownText handle non-ASCII wildcards fences and blank query`() {
        val nonAsciiTokens = NoteSearch.tokens("ÄPFEL où")
        assertTrue(NoteSearch.containsAll("Frische äpfel und OÙ aller", nonAsciiTokens))

        val wildcardTokens = NoteSearch.tokens("100% a_b")
        assertTrue(NoteSearch.containsAll("Score 100% on a_b test", wildcardTokens))
        assertFalse(NoteSearch.containsAll("Score 1000 on axb test", wildcardTokens))

        assertTrue(NoteSearch.tokens("   \t\n  ").isEmpty())
        assertFalse(NoteSearch.containsAll("anything", emptyList()))

        val tildeFenced = "Visible\n~~~\nsecret 100%\n~~~\nAfter"
        assertFalse(MarkdownText.outsideFences(tildeFenced).contains("secret"))
        val unclosedFence = "Before\n```\ntrailing fenced line"
        assertFalse(MarkdownText.outsideFences(unclosedFence).contains("trailing"))
        assertEquals("Alias [Image]", MarkdownText.plain("> [[Target|Alias]] ![](img.png)"))
    }

    @Test
    fun `T2-QOL3-02 NoteSearch rangesIn merges overlapping and duplicate case-variant hits`() {
        val ranges = NoteSearch.rangesIn("aaa aa", listOf("aa", "AA"))
        assertEquals(listOf(0..2, 4..5), ranges)
    }

    // --- PDF-QOL-5 Boundaries ---

    @Test
    fun `T2-QOL5-01 PageRestore restoredPageIndex coerces negative and overflow indices for pageCount 0 1 and 10`() {
        assertEquals(0, PageRestore.restoredPageIndex(-1, 0))
        assertEquals(0, PageRestore.restoredPageIndex(5, 0))

        assertEquals(0, PageRestore.restoredPageIndex(-1, 1))
        assertEquals(0, PageRestore.restoredPageIndex(0, 1))
        assertEquals(0, PageRestore.restoredPageIndex(10, 1))

        assertEquals(0, PageRestore.restoredPageIndex(-5, 10))
        assertEquals(9, PageRestore.restoredPageIndex(10, 10))
        assertEquals(9, PageRestore.restoredPageIndex(60, 10))
    }

    // --- PDF-QOL-7 Boundaries ---

    @Test
    fun `T2-QOL7-01 InkHistory clears redo stack when a new stroke commits after undo`() {
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()
        val history = InkHistory(stack)

        val op1 = E2eTestHarness.strokeOp(layer)
        val op2 = E2eTestHarness.strokeOp(layer)
        history.ops.add(op1)
        history.ops.add(op2)
        history.commitAddBatch(listOf(op1, op2))
        history.commitTransform(
            listOf(
                StrokeTransformSnapshot(
                    op1,
                    op1.stroke.points.toList(),
                    listOf(InkPoint(0.4f, 0.4f, 0.005f)),
                    null,
                    null
                )
            )
        )
        history.commitMove(listOf(op2), 0.1f, 0.1f)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertTrue(history.canRedo)

        history.commit(E2eTestHarness.strokeOp(layer))
        assertFalse(history.canRedo)
        assertEquals(3, history.ops.size)
    }

    @Test
    fun `T2-QOL7-02 ContinuousOpCodec preserves extreme margin coordinates -0_99f and +1_99f`() {
        val layer = PageLayerStack.base().layers.first()
        val op = E2eTestHarness.strokeOp(
            layer,
            listOf(InkPoint(-0.99f, 0.01f, 0.005f), InkPoint(1.99f, 0.99f, 0.005f))
        )
        val decoded = ContinuousOpCodec.decode(ContinuousOpCodec.encode(listOf(op))).orEmpty()
        assertEquals(1, decoded.size)
        val pts = (decoded.first() as InkOp.Stroke).stroke.points
        assertEquals(-0.99f, pts[0].x, 1e-6f)
        assertEquals(1.99f, pts[1].x, 1e-6f)
    }

    // --- PDF-QOL-6 Boundaries ---

    @Test
    fun `T2-QOL6-01 ContinuousPageGeometry marginFor handles null and unknown page types safely`() {
        assertEquals(1.0f, ContinuousPageGeometry.marginFor(null), 1e-6f)
        assertEquals(1.0f, ContinuousPageGeometry.marginFor("unknown"), 1e-6f)
        assertEquals(0.0f, ContinuousPageGeometry.marginFor("a4"), 1e-6f)
    }

    @Test
    fun `T2-QOL6-02 ExportManager sanitizeFilename handles blank and illegal filename characters`() {
        assertEquals("TouchNote", ExportManager.sanitizeFilename("   "))
        assertEquals("TouchNote", ExportManager.sanitizeFilename(""))
        val sanitized = ExportManager.sanitizeFilename("""a/b\c:d*e?f"g<h>i|j""")
        assertEquals("a_b_c_d_e_f_g_h_i_j", sanitized)
    }

    // --- R4-AUDIT Boundaries ---

    @Test
    fun `T2-AUDIT-01 zero literal double-bang operators anywhere in app src tests comments or string literals`() {
        val root = E2eTestHarness.locateRepoRoot()
        val testHits = E2eTestHarness.scanKtFilesForToken(File(root, "app/src/test"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP)
        assertEquals("Found forbidden double-bang operator(s) in app/src/test: $testHits", 0, testHits.size)
        val allHits = E2eTestHarness.scanKtFilesForToken(File(root, "app/src"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP)
        assertEquals("Found forbidden double-bang operator(s) in app/src: $allHits", 0, allHits.size)
    }

    @Test
    fun `T2-AUDIT-02 Room schema 15 matches APP_MIGRATIONS and declares PDF columns`() {
        val root = E2eTestHarness.locateRepoRoot()
        val schema15 = File(root, "app/schemas/personal.notes.touch.data.db.AppDatabase/15.json")
        assertTrue("15.json missing", schema15.isFile)
        val json = schema15.readText()
        assertTrue(json.contains("\"version\": 15"))
        assertTrue(json.contains("\"pdfSourcePath\""))
        assertTrue(json.contains("\"pdfPageIndex\""))
        assertTrue(json.contains("\"lastPageIndex\""))

        val covered = APP_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 3 until 15) {
            assertTrue("Missing Room Migration $v -> ${v + 1}", (v to v + 1) in covered)
        }
    }

    @Test
    fun `T2-AUDIT-03 AndroidManifest disables cloud backup and declares zero permissions`() {
        val root = E2eTestHarness.locateRepoRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("<uses-permission"))
        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:fullBackupContent="false""""))
    }

    // --- PHASE B: PDF-QOL-4 Boundaries ---

    @Test
    fun `T2-QOL4-03 PdfPageGeometry aspect returns DEFAULT_ASPECT on degenerate inputs`() {
        assertEquals(PdfPageGeometry.DEFAULT_ASPECT, PdfPageGeometry.aspect(0f, 0f), 1e-6f)
        assertEquals(PdfPageGeometry.DEFAULT_ASPECT, PdfPageGeometry.aspect(-100f, 500f), 1e-6f)
        assertEquals(PdfPageGeometry.DEFAULT_ASPECT, PdfPageGeometry.aspect(Float.NaN, 100f), 1e-6f)
        assertEquals(PdfPageGeometry.DEFAULT_ASPECT, PdfPageGeometry.aspect(Float.POSITIVE_INFINITY, 100f), 1e-6f)
    }

    @Test
    fun `T2-QOL4-04 PdfPageGeometry exportPdfPageSize and PdfExportPolicy clamp invalid inputs safely`() {
        for (badAspect in listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY, 1000f)) {
            val (w, h) = PdfPageGeometry.exportPdfPageSize(badAspect, longSidePts = 842)
            assertTrue("Expected width >= 1 for $badAspect, got $w", w >= 1)
            assertTrue("Expected height >= 1 for $badAspect, got $h", h >= 1)
        }
        val (zeroLongW, zeroLongH) = PdfPageGeometry.exportPdfPageSize(1.5f, longSidePts = 0)
        assertTrue(zeroLongW >= 1 && zeroLongH >= 1)
        assertEquals(2160 to 3056, PdfExportPolicy.exportRenderTargetSize(0f, 0f))
    }

    @Test
    fun `T2-QOL4-05 PageAspectCache handles concurrent reads writes and invalidation without errors`() {
        // Phase 1: Concurrent put / get / getOrLoad hit-consistency + per-path invalidation (240 keys < maxEntries 512)
        val nonEvictingCache = PageAspectCache(maxEntries = 512)
        val pool = Executors.newFixedThreadPool(4)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val startGate = CountDownLatch(1)
        val doneLatch = CountDownLatch(4)
        try {
            for (t in 0 until 4) {
                pool.submit {
                    try {
                        startGate.await()
                        val stablePath = "note/stable-$t/doc.pdf"
                        val churnPath = "note/churn-$t/doc.pdf"
                        for (i in 0 until 50) {
                            val expected = (960f + i) / 540f
                            val ratio = nonEvictingCache.put(stablePath, i, 960f + i, 540f)
                            assertEquals(expected, ratio, 1e-5f)
                            assertEquals(expected, nonEvictingCache.get(stablePath, i) ?: Float.NaN, 1e-5f)
                            val loaded = nonEvictingCache.getOrLoad(stablePath, i) {
                                error("Unexpected cache miss for non-evicted key $stablePath#$i")
                            }
                            assertEquals(expected, loaded, 1e-5f)
                        }
                        for (j in 0 until 10) {
                            nonEvictingCache.put(churnPath, j, 1280f + j, 720f)
                        }
                        assertNotNull(nonEvictingCache.get(churnPath, 0))
                        nonEvictingCache.invalidate(churnPath)
                        assertNull(nonEvictingCache.get(churnPath, 0))
                        val reloaded = nonEvictingCache.getOrLoad(churnPath, 0) { floatArrayOf(960f, 540f) }
                        assertEquals(960f / 540f, reloaded, 1e-5f)
                        assertNull(nonEvictingCache.get("note/missing/doc.pdf", 0))
                    } catch (th: Throwable) {
                        errors.add(th)
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startGate.countDown()
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
            assertTrue("Concurrent non-evicting cache errors: $errors", errors.isEmpty())
            for (t in 0 until 4) {
                for (i in 0 until 50) {
                    assertEquals(
                        (960f + i) / 540f,
                        nonEvictingCache.get("note/stable-$t/doc.pdf", i) ?: Float.NaN,
                        1e-5f
                    )
                }
            }
            nonEvictingCache.invalidate("note/stable-0/doc.pdf")
            assertNull(nonEvictingCache.get("note/stable-0/doc.pdf", 0))
            assertNotNull(nonEvictingCache.get("note/stable-1/doc.pdf", 0))

            // Phase 2: Concurrent bounded-eviction stress (200 distinct keys > maxEntries 128)
            val boundedCache = PageAspectCache(maxEntries = 128)
            val boundedErrors = ConcurrentLinkedQueue<Throwable>()
            val boundedGate = CountDownLatch(1)
            val boundedLatch = CountDownLatch(4)
            for (t in 0 until 4) {
                pool.submit {
                    try {
                        boundedGate.await()
                        val path = "note/bounded-$t/doc.pdf"
                        for (i in 0 until 50) {
                            val w = 960f + (i % 40)
                            val h = 540f
                            val expected = w / h
                            assertEquals(expected, boundedCache.put(path, i, w, h), 1e-5f)
                            val loaded = boundedCache.getOrLoad(path, i) { floatArrayOf(w, h) }
                            assertEquals(expected, loaded, 1e-5f)
                            boundedCache.get(path, i)?.let { assertEquals(expected, it, 1e-5f) }
                            if (i % 17 == 0) {
                                boundedCache.invalidate(path)
                            }
                            val sz = boundedCache.size()
                            assertTrue("Cache size $sz exceeded maxEntries 128", sz in 0..128)
                        }
                    } catch (th: Throwable) {
                        boundedErrors.add(th)
                    } finally {
                        boundedLatch.countDown()
                    }
                }
            }
            boundedGate.countDown()
            assertTrue(boundedLatch.await(10, TimeUnit.SECONDS))
            assertTrue("Concurrent bounded cache errors: $boundedErrors", boundedErrors.isEmpty())
            assertTrue("Final cache size ${boundedCache.size()} exceeded 128", boundedCache.size() in 0..128)
        } finally {
            pool.shutdownNow()
        }

        // Phase 3: Deterministic 128 -> 129 boundary eviction check
        val exactCache = PageAspectCache(maxEntries = 128)
        for (i in 0 until 128) {
            exactCache.put("note/exact/doc.pdf", i, 595f, 842f)
        }
        assertEquals(128, exactCache.size())
        exactCache.put("note/exact/doc.pdf", 128, 960f, 540f)
        assertEquals(1, exactCache.size())
        assertNotNull(exactCache.get("note/exact/doc.pdf", 128))
        assertNull(exactCache.get("note/exact/doc.pdf", 0))
    }

    // --- PHASE B: PDF-QOL-2 Boundaries ---

    @Test
    fun `T2-QOL2-02 NoteFileStore deletePageFile is idempotent on missing pages`() {
        val fileStore = NoteFileStore(tempFolder.newFolder("t2_qol2_02"))
        assertTrue(fileStore.deletePageFile("missing-note", 7))
        assertTrue(fileStore.deletePageFile("missing-note", 7))
    }

    @Test
    fun `T2-QOL2-03 cancelling streaming import aborts cleanly and removes partial DB rows and files`() = runTest {
        val dir = tempFolder.newFolder("t2_qol2_03")
        val partialDest = File(dir, "partial.pdf")
        var chunksRead = 0
        val chunkedStream = object : InputStream() {
            override fun read(): Int = 1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                chunksRead++
                b[off] = 0x25
                return 1
            }
        }
        assertFalse(
            PdfVectorImporter.copyStreamToFile(
                input = chunkedStream,
                destFile = partialDest,
                isCancelled = { chunksRead >= 2 }
            )
        )
        assertFalse(partialDest.exists())

        val env = E2eTestHarness.createInMemoryNoteEnv(dir)
        val (note, initialPages) = E2eTestHarness.pdfNoteFixture("t2-qol2-03", pageCount = 1)
        env.seedNoteAndPages(note, initialPages)
        env.fileStore.getNoteDir(note.id).apply { File(this, "pdf_partial.pdf").writeBytes(byteArrayOf(1, 2)) }

        var cancelAt3 = false
        val sink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean =
                env.fileStore.writePageFileAtomically(note.id, notePageIndex) { it.writeBytes(byteArrayOf(9)) }
            override suspend fun insertPageBatch(
                startNotePageIndex: Int,
                pdfPageOffset: Int,
                batchCount: Int,
                reuseFirstPage: Boolean,
                newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(
                    note.id, startNotePageIndex, "note/${note.id}/pdf_partial.pdf",
                    pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount
                )
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                if (reuseFirstPage) {
                    env.repository.permanentlyDeleteNote(note.id)
                } else {
                    env.repository.rollbackAppendedPdfPages(note.id, startNotePageIndex)
                }
            }
        }

        val outcome = PdfVectorImporter.importPages(
            pageCount = 20,
            reuseFirstPage = true,
            sink = sink,
            batchSize = 2,
            onProgress = { done, _ -> if (done >= 3) cancelAt3 = true },
            isCancelled = { cancelAt3 }
        )
        assertTrue(outcome.cancelled)
        assertEquals(0, outcome.importedPages)
        assertNull(env.notes[note.id])
        assertTrue(env.repository.getPagesForNoteSync(note.id).isEmpty())
        assertFalse(File(dir, "note/${note.id}").exists())

        // Also verify append rollback (reuseFirstPage = false)
        val (note2, pages2) = E2eTestHarness.pdfNoteFixture("t2-qol2-03b", pageCount = 2)
        env.seedNoteAndPages(note2, pages2)
        env.repository.appendPdfPagesBatch(note2.id, 2, "doc.pdf", 0, 3, reuseFirstPage = false, newTotalPageCount = 5)
        assertEquals(5, env.repository.getPagesForNoteSync(note2.id).size)
        env.repository.rollbackAppendedPdfPages(note2.id, 2)
        assertEquals(2, env.repository.getPagesForNoteSync(note2.id).size)
    }

    @Test
    fun `T2-QOL2-04 PdfiumDocument open guards missing and zero-byte files and C handles corrupt bytes`() {
        val dir = tempFolder.newFolder("t2_qol2_04")
        val missing = File(dir, "missing.pdf")
        val empty = File(dir, "empty.pdf").apply { writeBytes(ByteArray(0)) }

        assertNull(PdfiumDocument.open(missing))
        assertNull(PdfiumDocument.open(empty))
        val doc = PdfiumDocument()
        assertEquals(0, doc.pageCount)
        assertFalse(doc.isOpen)

        val jniC = E2eTestHarness.readRepoFile("app/src/main/cpp/touchnotes_pdfium.c")
        assertTrue(jniC.contains("FPDF_DOCUMENT doc = FPDF_LoadDocument(pathStr, NULL);"))
        assertTrue(jniC.contains("if (doc == NULL) {"))
        assertTrue(jniC.contains("return 0;"))
    }

    @Test
    fun `T2-QOL2-05 PdfVectorImporter reuseFirstPage true updates index 0 while false appends at next index`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t2_qol2_05"))
        val (note1, pages1) = E2eTestHarness.pdfNoteFixture("qol2-reuse", pageCount = 1)
        env.seedNoteAndPages(note1, pages1)

        val sinkReuse = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note1.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean = true
            override suspend fun insertPageBatch(
                startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int,
                reuseFirstPage: Boolean, newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(
                    note1.id, startNotePageIndex, "note/${note1.id}/new.pdf",
                    pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount
                )
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
        }
        val outReuse = PdfVectorImporter.importPages(1, reuseFirstPage = true, sink = sinkReuse)
        assertEquals(1, outReuse.importedPages)
        assertEquals(1, env.repository.getPagesForNoteSync(note1.id).size)
        assertEquals("note/${note1.id}/new.pdf", env.repository.getPage(note1.id, 0)?.pdfSourcePath)

        val (note2, pages2) = E2eTestHarness.pdfNoteFixture("qol2-append", pageCount = 2)
        env.seedNoteAndPages(note2, pages2)
        val sinkAppend = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note2.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean = true
            override suspend fun insertPageBatch(
                startNotePageIndex: Int, pdfPageOffset: Int, batchCount: Int,
                reuseFirstPage: Boolean, newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(
                    note2.id, startNotePageIndex, "note/${note2.id}/appended.pdf",
                    pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount
                )
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
        }
        val outAppend = PdfVectorImporter.importPages(1, reuseFirstPage = false, sink = sinkAppend)
        assertEquals(2, outAppend.startNotePageIndex)
        assertEquals(3, env.repository.getPagesForNoteSync(note2.id).size)
        assertEquals("note/${note2.id}/appended.pdf", env.repository.getPage(note2.id, 2)?.pdfSourcePath)
    }

    // --- PHASE B: PDF-QOL-1 Boundaries ---

    @Test
    fun `T2-QOL1-02 PdfOutlineImporter importNodes drops out-of-range pageIndex entries`() {
        val nodes = listOf(
            OutlineNode("Neg", -1, 0),
            OutlineNode("Valid 0", 0, 0),
            OutlineNode("AtLimit", 5, 0),
            OutlineNode("Far", 999, 1)
        )
        val imported = PdfOutlineImporter.importNodes(nodes, pageCount = 5)
        assertEquals(1, imported.size)
        assertEquals("Valid 0", imported.first().title)
        assertTrue(PdfOutlineImporter.importNodes(nodes, pageCount = 0).isEmpty())
    }

    @Test
    fun `T2-QOL1-03 PdfOutlineImporter importNodes strictly caps output at 500 entries`() {
        val nodes = (0 until 650).map { i -> OutlineNode("Chapter $i", i % 100, i % 3) }
        val imported = PdfOutlineImporter.importNodes(nodes, pageCount = 100, maxEntries = 500)
        assertEquals(500, imported.size)
    }

    @Test
    fun `T2-QOL1-04 PdfOutlineImporter drops blank titles and caps depth at 16`() {
        val nodes = listOf(
            OutlineNode("", 0, 0),
            OutlineNode("   \t\n ", 1, 0),
            OutlineNode("Deep", 2, 20)
        )
        val imported = PdfOutlineImporter.importNodes(nodes, pageCount = 10)
        assertEquals(1, imported.size)
        assertEquals(PdfOutlineImporter.MAX_LEVEL, imported.first().level)

        fun deepChain(depth: Int): OutlineTreeNode =
            if (depth <= 0) OutlineTreeNode("Leaf", 0)
            else OutlineTreeNode("Level $depth", 0, listOf(deepChain(depth - 1)))

        val flattened = PdfOutlineImporter.flattenTree(listOf(deepChain(20)), maxDepth = 16)
        assertEquals(17, flattened.size) // levels 0..16
        assertEquals(16, flattened.maxOf { it.level })

        val jniC = E2eTestHarness.readRepoFile("app/src/main/cpp/touchnotes_pdfium.c")
        assertTrue(jniC.contains("#define MAX_OUTLINE_DEPTH 16"))
        assertTrue(jniC.contains("visited[MAX_OUTLINE_NODES]"))
    }

    @Test
    fun `T2-QOL1-05 empty PDF outline merge preserves user entries and ViewModel shifts TOC on deletePage`() = runTest {
        val existing = listOf(
            TocItem(id = "u1", title = "User Note", pageIndex = 3, source = PdfOutlineImporter.SOURCE_USER),
            TocItem(id = "p1", title = "Old PDF Ch1", pageIndex = 1, source = PdfOutlineImporter.SOURCE_PDF)
        )
        val merged = PdfOutlineImporter.mergeWithUserEntries(existing, emptyList())
        assertEquals(1, merged.size)
        assertEquals("u1", merged.first().id)

        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("t2_qol1_05"))
        val (note, pages) = E2eTestHarness.pdfNoteFixture("t2-qol1-05", pageCount = 4)
        env.seedNoteAndPages(note, pages)
        assertTrue(env.repository.deletePage(note.id, 1))
        assertEquals(3, env.repository.getPagesForNoteSync(note.id).size)

        val vmSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        val deleteStart = vmSrc.indexOf("fun deletePage(")
        assertTrue("fun deletePage( missing in CanvasViewModel.kt", deleteStart >= 0)
        val deleteEnd = vmSrc.indexOf("private companion object", deleteStart)
        assertTrue("private companion object missing after deletePage", deleteEnd > deleteStart)
        val deleteBody = vmSrc.substring(deleteStart, deleteEnd)
        assertTrue(deleteBody.contains("repository.deletePage(noteId, pageIndex)"))
        assertTrue(deleteBody.contains("item.pageIndex == pageIndex -> null"))
        assertTrue(deleteBody.contains("item.pageIndex > pageIndex -> item.copy(pageIndex = item.pageIndex - 1)"))
        assertTrue(deleteBody.contains("catalogMutex.withLock"))
        assertTrue(deleteBody.contains("writeCatalogToc") || deleteBody.contains("saveCatalogJson(newToc)"))
    }

    // --- PHASE B: PDF-QOL-3 Boundaries ---

    @Test
    fun `T2-QOL3-03 PdfTextIndex classifySearchInput routes out-of-range and mixed tokens to SearchText`() {
        for (q in listOf("0", "-3", "11", "999999999999", "1a", "")) {
            assertEquals(
                PdfTextIndex.SearchInputAction.SearchText(q.trim()),
                PdfTextIndex.classifySearchInput(q, totalPages = 10)
            )
        }
        assertEquals(
            PdfTextIndex.SearchInputAction.SearchText("1"),
            PdfTextIndex.classifySearchInput("1", totalPages = 0)
        )
    }

    @Test
    fun `T2-QOL3-04 PdfTextIndex flags scanned PDFs and exposes SCANNED_PDF_MESSAGE`() {
        PdfTextIndex.clearCache()
        try {
            val scanned = PdfTextIndex.buildIndex(4, pageTextProvider = { "" }) ?: error("scanned index null")
            assertTrue(scanned.isScannedPdf)
            assertEquals(0, scanned.totalCharCount)
            assertEquals("No searchable text (scanned PDF)", PdfTextIndex.SCANNED_PDF_MESSAGE)
            assertTrue(PdfTextIndex.search(scanned, "theorem").isEmpty())

            val searchSheet = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/PdfSearchSheet.kt")
            assertTrue(searchSheet.contains("PdfTextIndex.SCANNED_PDF_MESSAGE"))
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T2-QOL3-05 PdfTextIndex invalidates stale pdf_text_json when source size or mtime changes`() {
        PdfTextIndex.clearCache()
        try {
            val fileStore = NoteFileStore(tempFolder.newFolder("t2_qol3_05"))
            val cacheFile = fileStore.getPdfTextIndexFile("n-stale")
            val descriptors = listOf(PdfTextIndex.PdfPageDescriptor(0, "note/n-stale/doc.pdf", 0))
            var version = 1
            var meta = PdfTextIndex.PdfSourceMeta("note/n-stale/doc.pdf", fileSize = 100L, lastModified = 1000L)

            val first = PdfTextIndex.buildNoteIndex(
                pages = descriptors,
                cacheFile = cacheFile,
                sourceMetaProvider = { meta },
                pageTextProvider = { _, _ -> "Revision v$version" }
            ) ?: error("first index null")
            assertEquals("Revision v1", first.pages.first().rawText)

            PdfTextIndex.clearCache()
            version = 2
            meta = meta.copy(fileSize = 200L, lastModified = 2000L)
            val rebuilt = PdfTextIndex.buildNoteIndex(
                pages = descriptors,
                cacheFile = cacheFile,
                sourceMetaProvider = { meta },
                pageTextProvider = { _, _ -> "Revision v$version" }
            ) ?: error("rebuilt index null")
            assertEquals("Revision v2", rebuilt.pages.first().rawText)
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    // --- PHASE B: PDF-QOL-5 Boundaries ---

    @Test
    fun `T2-QOL5-02 PageScrubberMath scrubFractionToPage clamps negative overflow and NaN`() {
        assertEquals(0, PageScrubberMath.scrubFractionToPage(-0.5f, 20))
        assertEquals(19, PageScrubberMath.scrubFractionToPage(1.5f, 20))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(Float.NaN, 20))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(Float.NEGATIVE_INFINITY, 20))
        assertEquals(19, PageScrubberMath.scrubFractionToPage(Float.POSITIVE_INFINITY, 20))
    }

    @Test
    fun `T2-QOL5-03 PageScrubberMath handles totalPages 0 and 1 safely`() {
        assertEquals(0, PageScrubberMath.scrubFractionToPage(0.5f, 0))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(0.5f, 1))
        assertEquals(0f, PageScrubberMath.pageToScrubFraction(0, 1), 1e-6f)
    }

    @Test
    fun `T2-QOL5-04 PageScrubberMath enforces threshold between 5 and 6 pages`() {
        for (count in -1..5) {
            assertFalse("Expected disabled for $count", PageScrubberMath.isScrubberEnabled(count))
        }
        assertTrue(PageScrubberMath.isScrubberEnabled(6))
    }

    @Test
    fun `T2-QOL5-05 rapid 50-step scrubber sweep stays bounded and 80ms throttle plus release wired`() {
        var frac = 0f
        var lastTarget = 0
        for (step in 0 until 50) {
            frac = PageScrubberMath.applyScrubDelta(frac, 0f, 12f, pageTurningDirection = 0, trackSpanPx = 400f)
            val target = PageScrubberMath.scrubFractionToPage(frac, totalPages = 30)
            assertTrue(target in 0..29)
            assertTrue(target >= lastTarget)
            lastTarget = target
        }
        assertEquals(29, lastTarget)

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(canvasScreen.contains("(now - lastThrottledJumpMs) >= 80L"))
        assertTrue(canvasScreen.contains("onDragEnd = {"))
        assertTrue(canvasScreen.contains("onDragCancel = {"))
    }

    // --- PHASE B: PDF-QOL-7 Boundaries ---

    @Test
    fun `T2-QOL7-03 ContinuousStrokeTransformMath clamps OOB coords to margin bounds`() {
        val oob = listOf(InkPoint(-2.5f, -0.4f, 0.005f), InkPoint(3.5f, 1.8f, 0.005f))

        val pdfClamped = ContinuousStrokeTransformMath.translatePoints(oob, 0f, 0f, margin = 1.0f)
        assertEquals(-1.0f, pdfClamped[0].x, 1e-6f)
        assertEquals(0.0f, pdfClamped[0].y, 1e-6f)
        assertEquals(2.0f, pdfClamped[1].x, 1e-6f)
        assertEquals(1.0f, pdfClamped[1].y, 1e-6f)

        val a4Clamped = ContinuousStrokeTransformMath.rotatePoints(oob, 0.2f, 0.5f, 0.5f, boxRatio = 0.707f, margin = 0.0f)
        assertTrue(a4Clamped.all { it.x in 0.0f..1.0f && it.y in 0.0f..1.0f })
    }

    @Test
    fun `T2-QOL7-04 ContinuousStrokeTransformMath guards degenerate 1-point and zero boxRatio inputs`() {
        val dot = ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = mutableListOf(InkPoint(0.5f, 0.5f, 0.005f)))
        val bounds = ContinuousStrokeTransformMath.selectionBounds(listOf(dot), boxRatio = 0f)
        assertNotNull(bounds)
        val rot = ContinuousStrokeTransformMath.rotatePoints(dot.points, (PI / 4.0).toFloat(), 0.5f, 0.5f, boxRatio = Float.NaN, margin = -1f)
        assertTrue(rot.all { it.x.isFinite() && it.y.isFinite() })
        val mirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(dot.points, 0.5f, margin = 0f)
        assertTrue(mirrored.all { it.x.isFinite() && it.y.isFinite() })
        val scaled = ContinuousStrokeTransformMath.scalePoints(dot.points, 2f, 2f, 0.5f, 0.5f, margin = 0f)
        assertTrue(scaled.all { it.x.isFinite() && it.y.isFinite() })
        val zeroRect = NormRect(0.5f, 0.5f, 0.5f, 0.5f)
        val edge = ContinuousStrokeTransformMath.edgeScaleFactors(LassoHandle.SCALE_RIGHT, zeroRect, 0.5f, 0.5f, 0.7f, 0.5f)
        assertNotNull(edge)
        assertEquals(0, ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(listOf(dot), 0.5f, 0.5f, boxRatio = -1f, toleranceIso = 0.05f))
        assertEquals(LassoHandle.NONE, ContinuousStrokeTransformMath.hitTestHandle(zeroRect, Float.NaN, 0.5f, 0f, 0f, 0f))
    }

    @Test
    fun `T2-QOL7-05 cut delete preserves Erase ops and undo restores cut strokes at exact indices`() {
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()
        val history = InkHistory(stack)
        val s1 = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.1f, 0.1f, 0.005f)))
        val e1 = E2eTestHarness.eraseOp(layer)
        val s2 = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.7f, 0.7f, 0.005f)))
        history.commit(s1)
        history.commit(e1)
        history.commit(s2)

        history.commitClear(emptyList(), listOf(OpEntry(0, s1)))
        assertEquals(2, history.ops.size)
        assertTrue(history.ops[0] === e1)
        assertTrue(history.ops[1] === s2)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(3, history.ops.size)
        assertTrue(history.ops[0] === s1)
        assertTrue(history.ops[1] === e1)

        assertTrue(history.redo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(2, history.ops.size)
        assertTrue(history.ops[0] === e1)
    }

    // --- PHASE B: PDF-QOL-6 Boundaries ---

    @Test
    fun `T2-QOL6-03 MarginExportMath cropRect returns full frame on A4 margin zero`() {
        assertFalse(MarginExportMath.supportsMarginExport("a4"))
        val cropFalse = MarginExportMath.cropRect(2160, 3056, ContinuousPageGeometry.DEFAULT_PAGE_RATIO, margin = 0.0f, includeMargins = false)
        val cropTrue = MarginExportMath.cropRect(2160, 3056, ContinuousPageGeometry.DEFAULT_PAGE_RATIO, margin = 0.0f, includeMargins = true)
        assertEquals(MarginCropResult(0, 0, 2160, 3056, 2160, 3056, cropped = false), cropFalse)
        assertEquals(cropFalse, cropTrue)
    }

    @Test
    fun `T2-QOL6-04 MarginExportMath cropRect guards zero layer dims and NaN margin safely`() {
        val zeroCrop = MarginExportMath.cropRect(0, 0, 0f, Float.NaN, includeMargins = false)
        assertEquals(MarginCropResult(0, 0, 0, 0, 0, 0, cropped = false), zeroCrop)
        val nanMargin = MarginExportMath.cropRect(2160, 3056, 0f, -1f, includeMargins = false)
        assertEquals(2160, nanMargin.width)
        assertEquals(3056, nanMargin.height)
        val (wPts, hPts) = MarginExportMath.exportPdfPageSizePts(Float.NaN, Float.NaN, includeMargins = false)
        assertTrue(wPts >= 1 && hPts >= 1)
    }

    @Test
    fun `T2-QOL6-05 MarginExportMath guide state accurately tracks left right and centered clean state`() {
        val leftOnly = MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.4f, 0.5f), margin = 1.0f)
        val leftState = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, leftOnly)
        assertTrue(leftState.showLeftOffscreenChevron)
        assertFalse(leftState.showRightOffscreenChevron)
        assertTrue(leftState.showLeftGuide)
        assertFalse(leftState.showRightGuide)

        val rightOnly = MarginExportMath.detectMarginInkFromNormalizedX(listOf(0.5f, 1.4f), margin = 1.0f)
        val rightState = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, rightOnly)
        assertFalse(rightState.showLeftOffscreenChevron)
        assertTrue(rightState.showRightOffscreenChevron)

        val centeredClean = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, MarginInkPresence.NONE)
        assertFalse(centeredClean.showLeftGuide)
        assertFalse(centeredClean.showRightGuide)
        assertFalse(centeredClean.showLeftOffscreenChevron)
        assertFalse(centeredClean.showRightOffscreenChevron)
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(0f, 1.0f))
    }

    // --- PHASE B: R4-AUDIT Boundaries ---

    @Test
    fun `T2-AUDIT-04 every AUDIT md PDF section includes REQUIRES USER TESTING and valid file paths`() {
        val root = E2eTestHarness.locateRepoRoot()
        val audit = E2eTestHarness.readRepoFile("state/AUDIT.md")
        val pathRegex = Regex("""app/src/main/[^\s`)]+\.(kt|c)""")
        for (id in E2eTestHarness.ALL_15_FEATURE_IDS) {
            val section = E2eTestHarness.extractAuditSection(audit, id)
            assertTrue("Missing section for $id", section.isNotBlank())
            assertTrue("Missing [REQUIRES USER TESTING] in $id", section.contains("[REQUIRES USER TESTING]"))
            val paths = pathRegex.findAll(section).map { it.value }.toList()
            assertTrue("Expected at least one source path in $id", paths.isNotEmpty())
            for (rel in paths) {
                assertTrue("Referenced file missing on disk for $id: $rel", File(root, rel).isFile)
            }
        }
    }

    @Test
    fun `T2-AUDIT-05 repo layout has zero source or test files inside teamwork metadata and static gates pass`() {
        val root = E2eTestHarness.locateRepoRoot()
        val teamworkDir = File(root, ".agents/teamwork")
        if (teamworkDir.exists()) {
            val forbiddenExtensions = setOf("kt", "java", "c", "cpp", "h")
            val misplaced = teamworkDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in forbiddenExtensions }
                .map { it.absolutePath }
                .toList()
            assertEquals("Source/test files found in .agents/teamwork: $misplaced", 0, misplaced.size)
        }
        assertEquals(0, E2eTestHarness.scanKtFilesForToken(File(root, "app/src"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP).size)
        assertEquals(0, E2eTestHarness.scanKtFilesForToken(File(root, "app/src/main"), "runBlocking").size)
    }
}
