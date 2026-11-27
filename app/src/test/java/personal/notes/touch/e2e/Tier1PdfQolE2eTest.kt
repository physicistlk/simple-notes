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
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.files.UiPrefs
import personal.notes.touch.text.MarkdownText
import personal.notes.touch.text.NoteSearch
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.DrawerTab
import personal.notes.touch.ui.canvas.InkHistory
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LassoHandle
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.PageRestore
import personal.notes.touch.ui.canvas.PageScrubberMath
import personal.notes.touch.ui.canvas.StrokeTransformSnapshot
import personal.notes.touch.ui.canvas.TocEntry
import personal.notes.touch.ui.canvas.TocItem
import personal.notes.touch.util.MarginCropResult
import personal.notes.touch.util.MarginExportMath
import personal.notes.touch.util.OutlineNode
import personal.notes.touch.util.OutlineTreeNode
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfImportManager
import personal.notes.touch.util.PdfOutlineImporter
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfRenderPolicy
import personal.notes.touch.util.PdfTextIndex
import personal.notes.touch.util.PdfVectorImporter
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tier 1 E2E Tests — PDF QoL (`PDF-QOL-1` .. `PDF-QOL-7`) & `R4-AUDIT`, Phases A & B.
 * Covers primary happy-path behavior using pure-JVM production APIs and repo invariants.
 */
class Tier1PdfQolE2eTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- PDF-QOL-4: Per-Page Aspect Ratio ---

    @Test
    fun `T1-QOL4-01 ContinuousPageGeometry preserves 16-by-9 landscape pageRect and layerSize`() {
        val ratio = 16f / 9f
        val rect = ContinuousPageGeometry.pageRect("pdf", ratio, 1000f, 1000f)
        assertEquals(ratio, rect.width / rect.height, 1e-4f)

        val layerSize = ContinuousPageGeometry.layerSize("pdf", ratio)
        assertEquals(ContinuousPageGeometry.PAGE_WIDTH * 3, layerSize.width)
        assertEquals((ContinuousPageGeometry.PAGE_WIDTH / ratio).roundToInt(), layerSize.height)
    }

    @Test
    fun `T1-QOL4-02 A4 legacy layerSize remains exact 2160 x 3056`() {
        val size = ContinuousPageGeometry.layerSize("a4", ContinuousPageGeometry.DEFAULT_PAGE_RATIO)
        assertEquals(2160, size.width)
        assertEquals(3056, size.height)
    }

    // --- PDF-QOL-2: Streaming Import + Budget ---

    @Test
    fun `T1-QOL2-01 PdfRenderPolicy and PdfImportManager keep 100-page mixed dims within budget`() {
        for (page in 0 until 100) {
            val isLandscape = page % 2 == 1
            val wPts = if (isLandscape) 960f else 595f
            val hPts = if (isLandscape) 540f else 842f
            val contWidth = PdfRenderPolicy.continuousRenderWidth(wPts, hPts, desiredWidthPx = 2400)
            assertTrue(contWidth in 1..PdfRenderPolicy.MAX_DIMENSION)

            val (thumbW, thumbH) = PdfImportManager.calculateOutputSize(
                wPts.toInt(), hPts.toInt(), targetW = 1080, targetH = 1920
            )
            assertTrue(thumbW in 1..PdfImportManager.MAX_DIMENSION)
            assertTrue(thumbH in 1..PdfImportManager.MAX_DIMENSION)
            assertEquals(wPts / hPts, thumbW.toFloat() / thumbH.toFloat(), 0.02f)
        }
    }

    // --- PDF-QOL-1: Native PDF Outline Models & Navigation Targets ---

    @Test
    fun `T1-QOL1-01 TocItem hierarchy levels and DrawerTab contracts validate against pageCount`() {
        val tabs = DrawerTab.values().toSet()
        assertEquals(setOf(DrawerTab.THUMBNAILS, DrawerTab.BOOKMARKS, DrawerTab.OUTLINE), tabs)

        val items = E2eTestHarness.tocFixture()
        assertEquals(listOf(0, 1, 2), items.map { it.level })
        val totalPages = 10
        items.forEach { item ->
            assertEquals(item.pageIndex, PageRestore.restoredPageIndex(item.pageIndex, totalPages))
        }

        val entry = TocEntry(title = "Chapter 1", page = 2, parentId = items.first().id, expanded = true)
        assertEquals(items.first().id, entry.parentId)
        assertTrue(entry.expanded)
    }

    // --- PDF-QOL-3: Full-Text Search Across PDF Pages ---

    @Test
    fun `T1-QOL3-01 NoteSearch and MarkdownText strip fences plain markup and highlight multi-word match`() {
        val rawMarkdown = """
            ```
            val ignored = "gradient descent inside code fence"
            ```
            ## Stochastic **Gradient** `Descent`
            Converges fast on convex objectives.
        """.trimIndent()
        val unfenced = MarkdownText.outsideFences(rawMarkdown)
        assertEquals(rawMarkdown.lines().size, unfenced.lines().size)
        assertFalse(unfenced.contains("ignored"))
        assertEquals(
            "Stochastic Gradient Descent",
            MarkdownText.plain("## Stochastic **Gradient** `Descent`")
        )

        val tokens = NoteSearch.tokens("gradient descent")
        assertEquals(listOf("gradient", "descent"), tokens)
        assertTrue(NoteSearch.containsAll(unfenced, tokens))

        val snippet = NoteSearch.snippet(unfenced, tokens)
        assertNotNull(snippet)
        val s = snippet ?: error("snippet must not be null")
        assertTrue(s.ranges.isNotEmpty())
        val highlightedTerms = s.ranges.map { s.text.substring(it).lowercase() }
        assertTrue("Expected 'gradient' in highlighted terms: $highlightedTerms", "gradient" in highlightedTerms)
        assertTrue("Expected 'descent' in highlighted terms: $highlightedTerms", "descent" in highlightedTerms)
    }

    @Test
    fun `T1-QOL3-02 PdfPageGeometry userRectToCanvas and unionRect bound multi-box search hit`() {
        val r1 = PdfPageGeometry.userRectToCanvas(floatArrayOf(50f, 700f, 150f, 680f), 842f, 2f)
        val r2 = PdfPageGeometry.userRectToCanvas(floatArrayOf(155f, 700f, 260f, 680f), 842f, 2f)
        val union = PdfPageGeometry.unionRect(listOf(r1, r2))
        assertNotNull(union)
        val u = union ?: error("union must not be null")
        assertEquals(100f, u[0], 1e-4f)
        assertEquals(284f, u[1], 1e-4f)
        assertEquals(520f, u[2], 1e-4f)
        assertEquals(324f, u[3], 1e-4f)
    }

    // --- PDF-QOL-5: Page Scrubber & Restore Navigation ---

    @Test
    fun `T1-QOL5-01 PageRestore and NoteEntity lastPageIndex restore valid page across multi-page notes`() {
        val note = NoteEntity(id = "n-300", title = "Textbook", pageCount = 300, pageType = "pdf", lastPageIndex = 149)
        assertEquals(149, PageRestore.restoredPageIndex(note.lastPageIndex, note.pageCount))
        assertEquals(5, PageRestore.restoredPageIndex(5, 6))
        assertEquals(0, PageRestore.restoredPageIndex(0, 20))
    }

    // --- PDF-QOL-7: Continuous Lasso & Arrow Selection History & Codec ---

    @Test
    fun `T1-QOL7-01 InkHistory commitTransform dispatches undo and redo snapshot callbacks`() {
        val stack = PageLayerStack.base()
        val history = InkHistory(stack)
        val op = E2eTestHarness.strokeOp(stack.layers.first())
        val before = op.stroke.points.toList()
        val after = listOf(InkPoint(0.4f, 0.3f, 0.005f), InkPoint(0.8f, 0.8f, 0.005f))
        val snapshot = StrokeTransformSnapshot(op, before, after, beforeShape = null, afterShape = null)

        history.commit(op)
        history.commitTransform(listOf(snapshot))

        var undoFlag: Boolean? = null
        var snapshotCount = 0
        assertTrue(
            history.undo(
                onErase = { _, _ -> },
                onClear = { _, _ -> },
                onTransform = { snaps, isUndo ->
                    snapshotCount = snaps.size
                    undoFlag = isUndo
                }
            )
        )
        assertEquals(1, snapshotCount)
        assertEquals(true, undoFlag)

        assertTrue(
            history.redo(
                onErase = { _, _ -> },
                onClear = { _, _ -> },
                onTransform = { snaps, isUndo ->
                    snapshotCount = snaps.size
                    undoFlag = isUndo
                }
            )
        )
        assertEquals(1, snapshotCount)
        assertEquals(false, undoFlag)
    }

    @Test
    fun `T1-QOL7-02 ContinuousOpCodec preserves transformed float coordinates within 1e-6f`() {
        val layer = PageLayerStack.base().layers.first()
        val op = E2eTestHarness.strokeOp(
            layer = layer,
            points = listOf(InkPoint(-0.345678f, 0.123456f, 0.0045f), InkPoint(1.234567f, 0.876543f, 0.0055f))
        )
        val decoded = ContinuousOpCodec.decode(ContinuousOpCodec.encode(listOf(op))).orEmpty()
        assertEquals(1, decoded.size)
        val pts = (decoded.first() as personal.notes.touch.ui.canvas.InkOp.Stroke).stroke.points
        assertEquals(-0.345678f, pts[0].x, 1e-6f)
        assertEquals(0.123456f, pts[0].y, 1e-6f)
        assertEquals(1.234567f, pts[1].x, 1e-6f)
        assertEquals(0.876543f, pts[1].y, 1e-6f)
    }

    // --- PDF-QOL-6: Margin Guides & Geometry ---

    @Test
    fun `T1-QOL6-01 ContinuousPageGeometry marginFor distinguishes pdf infinite and a4`() {
        assertEquals(1.0f, ContinuousPageGeometry.marginFor("pdf"), 1e-6f)
        assertEquals(1.0f, ContinuousPageGeometry.marginFor("infinite"), 1e-6f)
        assertEquals(0.0f, ContinuousPageGeometry.marginFor("a4"), 1e-6f)
    }

    @Test
    fun `T1-QOL6-02 ContinuousPageGeometry layerRect centers pdf pageRect in exact middle third`() {
        val ratio = ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        val page = ContinuousPageGeometry.pageRect("pdf", ratio, 1000f, 1000f)
        val layer = ContinuousPageGeometry.layerRect("pdf", ratio, 1000f, 1000f)

        assertEquals(3f * page.width, layer.width, 1e-4f)
        assertEquals(layer.left + layer.width / 3f, page.left, 1e-4f)
        assertEquals(page.top, layer.top, 1e-4f)
        assertEquals(page.bottom, layer.bottom, 1e-4f)
    }

    // --- R4-AUDIT: Static Invariants & Native Assets ---

    @Test
    fun `T1-AUDIT-01 zero not-null assertion operators exist across app src main`() {
        val root = E2eTestHarness.locateRepoRoot()
        val hits = E2eTestHarness.scanKtFilesForToken(File(root, "app/src/main"), E2eTestHarness.FORBIDDEN_NOT_NULL_OP)
        assertEquals("Unexpected double-bang occurrences in app/src/main: $hits", 0, hits.size)
    }

    @Test
    fun `T1-AUDIT-02 zero runBlocking calls exist across app src main`() {
        val root = E2eTestHarness.locateRepoRoot()
        val hits = E2eTestHarness.scanKtFilesForToken(File(root, "app/src/main"), "runBlocking")
        assertEquals("Unexpected runBlocking calls: $hits", 0, hits.size)
    }

    @Test
    fun `T1-AUDIT-03 AndroidManifest declares zero uses-permission and disables backup`() {
        val root = E2eTestHarness.locateRepoRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("<uses-permission"))
        assertTrue(manifest.contains("""android:allowBackup="false""""))
    }

    @Test
    fun `T1-AUDIT-04 prebuilt libpdfium binaries and state AUDIT log exist and are non-empty`() {
        val root = E2eTestHarness.locateRepoRoot()
        val arm64 = File(root, "app/src/main/jniLibs/arm64-v8a/libpdfium.so")
        val arm32 = File(root, "app/src/main/jniLibs/armeabi-v7a/libpdfium.so")
        val audit = File(root, "state/AUDIT.md")

        assertTrue("arm64 libpdfium.so missing", arm64.isFile && arm64.length() > 0L)
        assertTrue("armeabi-v7a libpdfium.so missing", arm32.isFile && arm32.length() > 0L)
        assertTrue("state/AUDIT.md missing", audit.isFile && audit.readText().isNotBlank())
    }

    // --- PHASE B: PDF-QOL-4 Per-Page Aspect Ratio ---

    @Test
    fun `T1-QOL4-03 PdfPageGeometry aspect computes accurate ratios for portrait 16-by-9 and square`() {
        assertEquals(595f / 842f, PdfPageGeometry.aspect(595f, 842f), 1e-4f)
        assertEquals(16f / 9f, PdfPageGeometry.aspect(1920f, 1080f), 1e-4f)
        assertEquals(1.0f, PdfPageGeometry.aspect(600f, 600f), 1e-4f)
    }

    @Test
    fun `T1-QOL4-04 PdfPageGeometry exportPdfPageSize produces orientation-aware PDF points`() {
        assertEquals(595 to 842, PdfPageGeometry.exportPdfPageSize(595f / 842f, longSidePts = 842))
        assertEquals(842 to 474, PdfPageGeometry.exportPdfPageSize(16f / 9f, longSidePts = 842))
        assertEquals(842 to 842, PdfPageGeometry.exportPdfPageSize(1.0f, longSidePts = 842))

        assertEquals(842 to 474, PdfExportPolicy.resolveExportPageSizePts(1920, 1080, 960f, 540f, 842))
    }

    @Test
    fun `T1-QOL4-05 PageAspectCache stores distinct portrait and landscape aspects under same source`() {
        val cache = PageAspectCache()
        val p0 = cache.put("note/n1/doc.pdf", 0, 595f, 842f)
        val p1 = cache.put("note/n1/doc.pdf", 1, 1920f, 1080f)
        assertEquals(p0, cache.get("note/n1/doc.pdf", 0) ?: 0f, 1e-5f)
        assertEquals(p1, cache.get("note/n1/doc.pdf", 1) ?: 0f, 1e-5f)
        assertNotEquals(p0, p1)

        var loadCalls = 0
        val cached = cache.getOrLoad("note/n1/doc.pdf", 1) {
            loadCalls++
            floatArrayOf(100f, 100f)
        }
        assertEquals(p1, cached, 1e-5f)
        assertEquals(0, loadCalls)

        cache.invalidate("note/n1/doc.pdf")
        assertEquals(0, cache.size())
    }

    // --- PHASE B: PDF-QOL-2 Streaming Import & Progress ---

    @Test
    fun `T1-QOL2-02 NoteFileStore writePageFileAtomically writes page_0 and removes temp file`() {
        val fileStore = NoteFileStore(tempFolder.newFolder("qol2_02"))
        val payload = byteArrayOf(10, 20, 30, 40)
        assertTrue(fileStore.writePageFileAtomically("n1", 0) { tmp -> tmp.writeBytes(payload) })

        val page0 = fileStore.getPageFile("n1", 0)
        assertTrue(page0.isFile)
        assertEquals(payload.toList(), page0.readBytes().toList())
        assertFalse(fileStore.getTempPageFile("n1", 0).exists())
    }

    @Test
    fun `T1-QOL2-03 PdfVectorImporter importPages emits monotonic 1 to 10 progress and page 0 thumb`() = runTest {
        val progressEvents = mutableListOf<Pair<Int, Int>>()
        val thumbIndices = mutableListOf<Int>()
        val sink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = 0
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean {
                thumbIndices.add(notePageIndex)
                return true
            }
            override suspend fun insertPageBatch(
                startNotePageIndex: Int,
                pdfPageOffset: Int,
                batchCount: Int,
                reuseFirstPage: Boolean,
                newTotalPageCount: Int
            ) = Unit
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) = Unit
        }

        val outcome = PdfVectorImporter.importPages(
            pageCount = 10,
            reuseFirstPage = true,
            sink = sink,
            batchSize = 4,
            onProgress = { done, total -> progressEvents.add(done to total) }
        )
        assertFalse(outcome.cancelled)
        assertEquals(10, outcome.importedPages)
        assertEquals((1..10).map { it to 10 }, progressEvents)
        assertEquals(listOf(0), thumbIndices)
    }

    @Test
    fun `T1-QOL2-04 streaming copyStreamToFile storeSourceFile and importPages avoid readBytes`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("qol2_04"))
        val (note, initialPages) = E2eTestHarness.pdfNoteFixture("qol2-04", pageCount = 1)
        env.seedNoteAndPages(note, initialPages)

        val tempPdf = tempFolder.newFile("incoming.pdf")
        val fakePdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".toByteArray()
        assertTrue(PdfVectorImporter.copyStreamToFile(ByteArrayInputStream(fakePdfBytes), tempPdf))
        val stored = PdfVectorImporter.storeSourceFile(tempPdf, env.fileStore.getNoteDir(note.id), isTempFile = true)
        assertNotNull(stored)
        val storedFile = stored ?: error("stored file missing")
        assertTrue(storedFile.isFile)
        assertTrue(storedFile.name.startsWith("pdf_"))
        val relPath = "note/${note.id}/${storedFile.name}"

        val thumbPages = mutableListOf<Int>()
        val sink = object : PdfVectorImporter.PdfImportSink {
            override suspend fun nextPageIndex(): Int = env.repository.getNextPageIndex(note.id)
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean {
                thumbPages.add(notePageIndex)
                return env.fileStore.writePageFileAtomically(note.id, notePageIndex) { it.writeBytes(byteArrayOf(1, 2)) }
            }
            override suspend fun insertPageBatch(
                startNotePageIndex: Int,
                pdfPageOffset: Int,
                batchCount: Int,
                reuseFirstPage: Boolean,
                newTotalPageCount: Int
            ) {
                env.repository.appendPdfPagesBatch(
                    note.id, startNotePageIndex, relPath, pdfPageOffset, batchCount, reuseFirstPage, newTotalPageCount
                )
            }
            override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                env.repository.rollbackAppendedPdfPages(note.id, startNotePageIndex)
            }
        }

        val outcome = PdfVectorImporter.importPages(10, reuseFirstPage = true, sink = sink, batchSize = 5)
        assertEquals(10, outcome.importedPages)
        assertEquals(listOf(0), thumbPages)
        assertTrue(env.fileStore.getPageFile(note.id, 0).isFile)
        assertFalse(env.fileStore.getPageFile(note.id, 1).isFile)
        assertEquals(10, env.repository.getPagesForNoteSync(note.id).size)

        val importerSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/PdfVectorImporter.kt")
        assertFalse(importerSrc.contains(".readBytes("))
    }

    @Test
    fun `T1-QOL2-05 batch appendPdfPagesBatch and native FPDF_LoadDocument contract avoid heap copy`() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("qol2_05"))
        val (note, initialPages) = E2eTestHarness.pdfNoteFixture("qol2-05", pageCount = 1)
        env.seedNoteAndPages(note, initialPages)

        env.repository.appendPdfPagesBatch(
            noteId = note.id,
            startNotePageIndex = 0,
            pdfSourcePath = "note/${note.id}/src.pdf",
            pdfPageOffset = 0,
            batchCount = 6,
            reuseFirstPage = true,
            newTotalPageCount = 6
        )
        assertEquals(1, env.setPdfSourceCalls.size)
        assertEquals(1, env.insertAllBatches.size)
        assertEquals(5, env.insertAllBatches.first().size)
        assertEquals(6, env.repository.getPagesForNoteSync(note.id).size)

        val jniC = E2eTestHarness.readRepoFile("app/src/main/cpp/touchnotes_pdfium.c")
        val pdfiumDoc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/util/PdfiumDocument.kt")
        assertTrue(jniC.contains("PdfiumBridge_openDocumentFromPath"))
        assertTrue(jniC.contains("FPDF_LoadDocument(pathStr, NULL)"))
        assertTrue(pdfiumDoc.contains("PdfiumBridge.openDocumentFromPath(file.absolutePath)"))
    }

    // --- PHASE B: PDF-QOL-1 Native PDF Outline ---

    @Test
    fun `T1-QOL1-02 PdfOutlineImporter flattenTree and importNodes preserve hierarchy and pdf source`() {
        val roots = listOf(
            OutlineTreeNode(
                title = "Chapter 1",
                pageIndex = 0,
                children = listOf(
                    OutlineTreeNode(
                        title = "Section 1.1",
                        pageIndex = 2,
                        children = listOf(OutlineTreeNode("Sub 1.1.1", 3))
                    )
                )
            ),
            OutlineTreeNode("Chapter 2", 10)
        )
        val flat = PdfOutlineImporter.flattenTree(roots)
        val imported = PdfOutlineImporter.importNodes(flat, pageCount = 20)
        assertEquals(4, imported.size)
        assertEquals(listOf(0, 1, 2, 0), imported.map { it.level })
        assertTrue(imported.all { it.source == PdfOutlineImporter.SOURCE_PDF })
    }

    @Test
    fun `T1-QOL1-03 PdfOutlineImporter mergeWithUserEntries preserves user items sorted by page`() {
        val existing = listOf(
            TocItem(id = "u1", title = "My Bookmark Note", pageIndex = 5, level = 0, source = PdfOutlineImporter.SOURCE_USER),
            TocItem(id = "p-old", title = "Old PDF", pageIndex = 1, level = 0, source = PdfOutlineImporter.SOURCE_PDF)
        )
        val imported = PdfOutlineImporter.importNodes(
            listOf(OutlineNode("Intro", 0, 0), OutlineNode("Appendix", 12, 0)),
            pageCount = 20
        )
        val merged = PdfOutlineImporter.mergeWithUserEntries(existing, imported)
        assertEquals(3, merged.size)
        assertEquals(listOf(0, 5, 12), merged.map { it.pageIndex })
        assertTrue(merged.any { it.id == "u1" && it.source == PdfOutlineImporter.SOURCE_USER })
    }

    @Test
    fun `T1-QOL1-04 re-importing PDF outline replaces old pdf entries without duplicating user entries`() {
        val userEntry = TocItem(id = "u-keep", title = "Exam Hint", pageIndex = 4, source = PdfOutlineImporter.SOURCE_USER)
        val firstPass = PdfOutlineImporter.mergeWithUserEntries(
            listOf(userEntry),
            PdfOutlineImporter.importNodes(listOf(OutlineNode("Draft Ch1", 0, 0)), pageCount = 15)
        )
        val secondPass = PdfOutlineImporter.mergeWithUserEntries(
            firstPass,
            PdfOutlineImporter.importNodes(listOf(OutlineNode("Final Ch1", 1, 0), OutlineNode("Final Ch2", 8, 0)), pageCount = 15)
        )
        assertEquals(3, secondPass.size)
        assertEquals(1, secondPass.count { it.source == PdfOutlineImporter.SOURCE_USER })
        assertFalse(secondPass.any { it.title == "Draft Ch1" })
        assertEquals("Final Ch1", secondPass.first().title)

        val vmSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        assertTrue(vmSrc.contains("fun reimportPdfOutline("))
        assertTrue(vmSrc.contains("PdfOutlineImporter.mergeWithUserEntries(existing, imported)"))
    }

    @Test
    fun `T1-QOL1-05 PDF note without native outline has no dummy Chapter 1 Overview`() {
        val emptyImported = PdfOutlineImporter.importNodes(emptyList(), pageCount = 10)
        assertTrue(emptyImported.isEmpty())
        assertTrue(PdfOutlineImporter.mergeWithUserEntries(emptyList(), emptyImported).isEmpty())

        val vmSrc = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        val loadStart = vmSrc.indexOf("private suspend fun loadDrawerData()")
        assertTrue("loadDrawerData missing in CanvasViewModel.kt", loadStart >= 0)
        val loadEnd = vmSrc.indexOf("fun reloadDrawerData()", loadStart)
        assertTrue("reloadDrawerData missing after loadDrawerData", loadEnd > loadStart)
        val loadBody = vmSrc.substring(loadStart, loadEnd)
        assertTrue(loadBody.contains("if (isPdfNote) {"))
        assertTrue(loadBody.contains("extractPdfOutlineFromPages(pagesList)"))

        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        assertTrue(Regex("Text\\(\\s*text\\s*=\\s*\"No Outline Entries\"").containsMatchIn(canvasScreen))
    }

    // --- PHASE B: PDF-QOL-3 Full-Text Search ---

    @Test
    fun `T1-QOL3-03 PdfTextIndex buildIndex and search return exact offsets snippets and ranges`() {
        PdfTextIndex.clearCache()
        try {
            val pages = listOf(
                "Introduction to linear algebra.",
                "Stochastic gradient descent converges rapidly on smooth convex functions.",
                "Backpropagation computes gradients efficiently.",
                "Another mention of Gradient Descent in chapter 4.",
                "Conclusion."
            )
            val idx = PdfTextIndex.buildIndex(pages.size, pageTextProvider = { p -> pages[p] })
            assertNotNull(idx)
            val built = idx ?: error("index null")
            assertFalse(built.isScannedPdf)
            val hits = PdfTextIndex.search(built, "gradient descent")
            assertEquals(2, hits.size)
            assertEquals(1, hits[0].pageIndex)
            assertEquals(11, hits[0].startOffset)
            assertEquals("gradient descent".length, hits[0].length)
            assertTrue(hits[0].matchRanges.isNotEmpty())
            assertEquals(3, hits[1].pageIndex)
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    @Test
    fun `T1-QOL3-04 PdfTextIndex classifySearchInput routes numeric jump vs text search`() {
        assertEquals(PdfTextIndex.SearchInputAction.JumpToPage(3), PdfTextIndex.classifySearchInput("4", totalPages = 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText("matrix"), PdfTextIndex.classifySearchInput("matrix", totalPages = 10))
    }

    @Test
    fun `T1-QOL3-05 PdfTextIndex persists pdf_text_json and reloads from disk after clearCache`() {
        PdfTextIndex.clearCache()
        try {
            val fileStore = NoteFileStore(tempFolder.newFolder("qol3_05"))
            val cacheFile = fileStore.getPdfTextIndexFile("n1")
            assertEquals("pdf_text.json", cacheFile.name)
            val descriptors = listOf(
                PdfTextIndex.PdfPageDescriptor(0, "note/n1/doc.pdf", 0),
                PdfTextIndex.PdfPageDescriptor(1, "note/n1/doc.pdf", 1)
            )
            val meta = PdfTextIndex.PdfSourceMeta("note/n1/doc.pdf", fileSize = 4096L, lastModified = 123456L)
            var providerCalls = 0
            val first = PdfTextIndex.buildNoteIndex(
                pages = descriptors,
                cacheFile = cacheFile,
                sourceMetaProvider = { meta },
                pageTextProvider = { _, pdfIdx ->
                    providerCalls++
                    if (pdfIdx == 0) "Eigenvalues and eigenvectors" else "Spectral theorem proof"
                }
            )
            assertNotNull(first)
            assertEquals(2, providerCalls)
            assertTrue(cacheFile.isFile)

            PdfTextIndex.clearCache()
            val reloaded = PdfTextIndex.buildNoteIndex(
                pages = descriptors,
                cacheFile = cacheFile,
                sourceMetaProvider = { meta },
                pageTextProvider = { _, _ ->
                    providerCalls++
                    "SHOULD_NOT_BE_CALLED"
                }
            )
            assertNotNull(reloaded)
            assertEquals(2, providerCalls)
            assertEquals(1, PdfTextIndex.search(reloaded ?: error("reloaded null"), "spectral").size)
        } finally {
            PdfTextIndex.clearCache()
        }
    }

    // --- PHASE B: PDF-QOL-5 Page Scrubber ---

    @Test
    fun `T1-QOL5-02 PageScrubberMath scrubFractionToPage maps monotonically across 20 pages`() {
        val fractions = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        val mapped = fractions.map { PageScrubberMath.scrubFractionToPage(it, totalPages = 20) }
        assertEquals(0, mapped.first())
        assertEquals(19, mapped.last())
        for (i in 1 until mapped.size) {
            assertTrue(mapped[i] > mapped[i - 1])
        }
    }

    @Test
    fun `T1-QOL5-03 PageScrubberMath isScrubberEnabled is true for totalPages above 5`() {
        assertTrue(PageScrubberMath.isScrubberEnabled(6))
        assertTrue(PageScrubberMath.isScrubberEnabled(11))
        assertTrue(PageScrubberMath.isScrubberEnabled(300))
    }

    @Test
    fun `T1-QOL5-04 PageScrubberMath dragDeltaToFractionStep respects vertical and horizontal orientation`() {
        val vertStep = PageScrubberMath.dragDeltaToFractionStep(deltaX = 80f, deltaY = 20f, pageTurningDirection = 0, trackSpanPx = 200f)
        assertEquals(0.10f, vertStep, 1e-6f)

        val horizDominantX = PageScrubberMath.dragDeltaToFractionStep(deltaX = 80f, deltaY = 20f, pageTurningDirection = 1, trackSpanPx = 200f)
        assertEquals(0.40f, horizDominantX, 1e-6f)

        val horizCapsuleY = PageScrubberMath.dragDeltaToFractionStep(deltaX = 5f, deltaY = 50f, pageTurningDirection = 1, trackSpanPx = 200f)
        assertEquals(0.25f, horizCapsuleY, 1e-6f)
    }

    @Test
    fun `T1-QOL5-05 capsule bookmark toggle invokes ViewModel toggleBookmark and updates bookmarks json`() {
        val canvasScreen = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt")
        val viewModel = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        assertTrue(canvasScreen.contains("onClick = { viewModel.toggleBookmark(currentPageIndex) }"))
        assertTrue(viewModel.contains("fun toggleBookmark(pageIndex: Int)"))
        assertTrue(viewModel.contains("saveBookmarksJson(current)"))
    }

    // --- PHASE B: PDF-QOL-7 Continuous Lasso & Arrow Transforms ---

    @Test
    fun `T1-QOL7-03 ContinuousStrokeTransformMath rotatePoints preserves isotropic length without skew`() {
        val pts = listOf(InkPoint(0.3f, 0.5f, 0.005f), InkPoint(0.7f, 0.5f, 0.005f))
        for (ratio in listOf(16f / 9f, ContinuousPageGeometry.DEFAULT_PAGE_RATIO)) {
            val beforeLen = ContinuousStrokeTransformMath.isotropicDistance(pts[0], pts[1], ratio)
            val rotated = ContinuousStrokeTransformMath.rotatePoints(
                points = pts,
                deltaRad = (PI / 2.0).toFloat(),
                pivotXNorm = 0.5f,
                pivotYNorm = 0.5f,
                boxRatio = ratio,
                margin = 1.0f
            )
            assertEquals(pts[0].x, 0.3f, 1e-6f)
            assertTrue(abs(rotated[1].x - rotated[0].x) < 1e-5f)
            val afterLen = ContinuousStrokeTransformMath.isotropicDistance(rotated[0], rotated[1], ratio)
            assertEquals(beforeLen, afterLen, 1e-5f)
        }
    }

    @Test
    fun `T1-QOL7-04 ContinuousStrokeTransformMath mirror proportional scale and edge stretch work cleanly`() {
        val pts = listOf(InkPoint(0.2f, 0.3f, 0.005f), InkPoint(0.4f, 0.5f, 0.005f))
        val mirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(pts, centerXNorm = 0.3f, margin = 1.0f)
        assertEquals(0.4f, mirrored[0].x, 1e-5f)
        assertEquals(0.2f, mirrored[1].x, 1e-5f)

        val factor = ContinuousStrokeTransformMath.proportionalScaleFactor(
            startXNorm = 0.4f, startYNorm = 0.5f,
            curXNorm = 0.6f, curYNorm = 0.7f,
            anchorXNorm = 0.2f, anchorYNorm = 0.3f,
            boxRatio = 16f / 9f
        )
        assertEquals(2.0f, factor, 1e-4f)
        val scaled = ContinuousStrokeTransformMath.scalePoints(pts, factor, factor, 0.2f, 0.3f, margin = 1.0f)
        assertEquals(0.6f, scaled[1].x, 1e-4f)
        assertEquals(0.7f, scaled[1].y, 1e-4f)

        val stroke = ContinuousStroke(CanvasTool.PEN, 0xFF000000.toInt(), 0.005f, points = pts.toMutableList())
        val bounds = ContinuousStrokeTransformMath.selectionBounds(listOf(stroke), 1f) ?: error("bounds null")
        val edge = ContinuousStrokeTransformMath.edgeScaleFactors(
            LassoHandle.SCALE_RIGHT, bounds, startXNorm = bounds.right, startYNorm = bounds.centerY,
            curXNorm = bounds.left + bounds.width * 2f, curYNorm = bounds.centerY
        ) ?: error("edge spec null")
        assertEquals(2.0f, edge.scaleX, 1e-4f)
        assertEquals(1.0f, edge.scaleY, 1e-6f)
    }

    @Test
    fun `T1-QOL7-05 copyStrokesWithOffset with InkHistory commitAddBatch supports undo and redo`() {
        val stack = PageLayerStack.base()
        val layer = stack.layers.first()
        val history = InkHistory(stack)
        val origOp = E2eTestHarness.strokeOp(layer, points = listOf(InkPoint(0.2f, 0.2f, 0.005f), InkPoint(0.3f, 0.3f, 0.005f)))
        history.commit(origOp)

        val copiedStrokes = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            listOf(origOp.stroke),
            offsetXNorm = 0.05f,
            offsetYNorm = 0.04f,
            margin = ContinuousPageGeometry.marginFor("pdf")
        )
        val copiedOps = copiedStrokes.map { InkOp.Stroke(it, shape = null, layer = layer) }
        history.ops.addAll(copiedOps)
        history.commitAddBatch(copiedOps)
        assertEquals(2, history.ops.size)
        assertEquals(0.25f, (history.ops[1] as InkOp.Stroke).stroke.points[0].x, 1e-5f)

        assertTrue(history.undo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(1, history.ops.size)
        assertTrue(history.redo(onErase = { _, _ -> }, onClear = { _, _ -> }))
        assertEquals(2, history.ops.size)
    }

    // --- PHASE B: PDF-QOL-6 Margin Guides & Margin Export Option ---

    @Test
    fun `T1-QOL6-03 MarginExportMath cropRect Page only crops PDF layer to middle third`() {
        val crop = MarginExportMath.cropRect(
            layerWidth = 6480,
            layerHeight = 3056,
            pageAspect = 2160f / 3056f,
            margin = 1.0f,
            includeMargins = false
        )
        assertEquals(MarginCropResult(2160, 0, 2160, 3056, 2160, 3056, cropped = true), crop)
        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(2160f / 3056f, 1.0f, includeMargins = false))
    }

    @Test
    fun `T1-QOL6-04 MarginExportMath cropRect Include margins exports full 6480x3056 canvas`() {
        val crop = MarginExportMath.cropRect(
            layerWidth = 6480,
            layerHeight = 3056,
            pageAspect = 2160f / 3056f,
            margin = 1.0f,
            includeMargins = true
        )
        assertEquals(MarginCropResult(0, 0, 6480, 3056, 6480, 3056, cropped = false), crop)
        assertEquals(842 to 397, MarginExportMath.exportPdfPageSizePts(2160f / 3056f, 1.0f, includeMargins = true))
    }

    @Test
    fun `T1-QOL6-05 MarginExportMath detects off-screen margin chevrons and UiPrefs key persists margin option`() {
        val presence = MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.4f, 0.5f, 1.4f), margin = 1.0f)
        assertTrue(presence.hasLeftMarginInk)
        assertTrue(presence.hasRightMarginInk)

        val centered = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 0f,
            pageWidthPx = 1000f,
            viewportWidthPx = 1000f,
            presence = presence
        )
        assertTrue(centered.showLeftOffscreenChevron)
        assertTrue(centered.showRightOffscreenChevron)

        val pannedLeftRevealed = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 600f,
            pageWidthPx = 1000f,
            viewportWidthPx = 1000f,
            presence = presence
        )
        assertFalse(pannedLeftRevealed.showLeftOffscreenChevron)
        assertTrue(pannedLeftRevealed.showRightOffscreenChevron)

        assertEquals("export_include_margins", UiPrefs.KEY_EXPORT_INCLUDE_MARGINS)
        val fileStore = NoteFileStore(tempFolder.newFolder("qol6_05"))
        assertEquals("ui_prefs.json", fileStore.getUiPrefsFile().name)

        val exportDialog = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/export/ExportDialog.kt")
        val viewModel = E2eTestHarness.readRepoFile("app/src/main/java/personal/notes/touch/ui/canvas/CanvasViewModel.kt")
        assertTrue(exportDialog.contains("UiPrefs.KEY_EXPORT_INCLUDE_MARGINS"))
        assertTrue(exportDialog.contains("Page only"))
        assertTrue(exportDialog.contains("Include margins"))
        assertTrue(viewModel.contains("UiPrefs.KEY_EXPORT_INCLUDE_MARGINS"))
    }

    // --- PHASE B: R4-AUDIT ---

    @Test
    fun `T1-AUDIT-05 state AUDIT md covers all 15 PDF items with status files and REQUIRES USER TESTING`() {
        val audit = E2eTestHarness.readRepoFile("state/AUDIT.md")
        assertTrue(audit.contains("## 6. PDF Mode Fixes & QoL"))
        for (id in E2eTestHarness.ALL_15_FEATURE_IDS) {
            val section = E2eTestHarness.extractAuditSection(audit, id)
            assertTrue("Missing AUDIT.md section for $id", section.isNotBlank())
            assertTrue("$id missing IMPLEMENTED status", section.contains("IMPLEMENTED"))
            assertTrue("$id missing [REQUIRES USER TESTING]", section.contains("[REQUIRES USER TESTING]"))
            assertTrue("$id missing Modified / Created Files", section.contains("Modified / Created Files"))
            assertTrue("$id missing Tablet Verification", section.contains("Tablet Verification"))
        }
    }
}
