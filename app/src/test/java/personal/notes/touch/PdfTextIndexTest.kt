package personal.notes.touch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfTextIndex
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure-JVM unit tests for `PDF-QOL-3` (`PdfTextIndex`, `NoteSearch`,
 * and `PdfPageGeometry.userRectToCanvas` offset-to-rect mapping).
 */
class PdfTextIndexTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        PdfTextIndex.clearCache()
    }

    @After
    fun tearDown() {
        PdfTextIndex.clearCache()
    }

    @Test
    fun classifySearchInput_routesValidPageNumbersAndTextQueries() {
        assertEquals(PdfTextIndex.SearchInputAction.JumpToPage(2), PdfTextIndex.classifySearchInput(" 3 ", 10))
        assertEquals(PdfTextIndex.SearchInputAction.JumpToPage(0), PdfTextIndex.classifySearchInput("1", 10))
        assertEquals(PdfTextIndex.SearchInputAction.JumpToPage(9), PdfTextIndex.classifySearchInput("10", 10))

        assertEquals(PdfTextIndex.SearchInputAction.SearchText("0"), PdfTextIndex.classifySearchInput("0", 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText("-1"), PdfTextIndex.classifySearchInput("-1", 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText("999"), PdfTextIndex.classifySearchInput("999", 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText("12abc"), PdfTextIndex.classifySearchInput("12abc", 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText("+3"), PdfTextIndex.classifySearchInput("+3", 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText(""), PdfTextIndex.classifySearchInput("   ", 10))
        assertEquals(PdfTextIndex.SearchInputAction.SearchText("1"), PdfTextIndex.classifySearchInput("1", 0))
    }

    @Test
    fun search_preservesRawUcs2OffsetsWithoutMarkdownStripping() {
        val rawPage = "  # Heading\r\n**bold** [link](url) targetToken here"
        val index = PdfTextIndex.buildIndex(pageCount = 1, pageTextProvider = { rawPage })
        assertNotNull(index)
        val built = index ?: return

        val headingHits = PdfTextIndex.search(built, "# Heading")
        assertEquals(1, headingHits.size)
        val h0 = headingHits[0]
        assertEquals(0, h0.pageIndex)
        assertEquals("# Heading", rawPage.substring(h0.startOffset, h0.startOffset + h0.length))

        val boldHits = PdfTextIndex.search(built, "**bold**")
        assertEquals(1, boldHits.size)
        val h1 = boldHits[0]
        assertEquals("**bold**", rawPage.substring(h1.startOffset, h1.startOffset + h1.length))
        assertTrue(h1.snippet.contains("**bold**"))
        assertTrue(h1.matchRanges.isNotEmpty())
    }

    @Test
    fun search_caseInsensitiveNonAsciiLiteralWildcardsAndMultiToken() {
        val pages = listOf(
            "Frische ÄPFEL im Angebot! 100% bio und a_b Test.",
            "Où est le budget pour March 2026? Quarterly budget review."
        )
        val index = PdfTextIndex.buildIndex(pageCount = pages.size, pageTextProvider = { pages[it] })
        assertNotNull(index)
        val built = index ?: return

        val appleHits = PdfTextIndex.search(built, "äpfel")
        assertEquals(1, appleHits.size)
        assertEquals(0, appleHits[0].pageIndex)
        assertEquals("ÄPFEL", pages[0].substring(appleHits[0].startOffset, appleHits[0].startOffset + appleHits[0].length))

        val accentHits = PdfTextIndex.search(built, "où")
        assertEquals(1, accentHits.size)
        assertEquals(1, accentHits[0].pageIndex)

        val percentHits = PdfTextIndex.search(built, "100%")
        assertEquals(1, percentHits.size)
        assertEquals(0, percentHits[0].pageIndex)

        val underscoreHits = PdfTextIndex.search(built, "a_b")
        assertEquals(1, underscoreHits.size)
        assertEquals(0, underscoreHits[0].pageIndex)

        val multiToken = PdfTextIndex.search(built, "budget march")
        assertTrue(multiToken.isNotEmpty())
        assertTrue(multiToken.all { it.pageIndex == 1 })
        assertTrue(multiToken[0].matchRanges.isNotEmpty())
    }

    @Test
    fun search_multipleHitsPerPageAndMultiplePagesOrdered() {
        val pages = listOf(
            "Alpha beta alpha gamma ALPHA.",
            "No match here.",
            "Second page alpha occurrence."
        )
        val index = PdfTextIndex.buildIndex(pageCount = pages.size, pageTextProvider = { pages[it] })
        assertNotNull(index)
        val built = index ?: return

        val hits = PdfTextIndex.search(built, "alpha")
        assertEquals(4, hits.size)
        assertEquals(listOf(0, 0, 0, 2), hits.map { it.pageIndex })
        assertEquals(listOf(0, 11, 23, 12), hits.map { it.startOffset })
        assertTrue(hits.all { pages[it.pageIndex].substring(it.startOffset, it.startOffset + it.length).equals("alpha", ignoreCase = true) })
    }

    @Test
    fun buildIndex_reportsProgressAndStopsOnCancellation() {
        val progress = mutableListOf<Pair<Int, Int>>()
        val full = PdfTextIndex.buildIndex(
            pageCount = 4,
            pageTextProvider = { "Page $it text" },
            onProgress = { done, total -> progress.add(done to total) }
        )
        assertNotNull(full)
        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), progress)

        val cancelled = AtomicBoolean(false)
        val extracted = AtomicInteger(0)
        val aborted = PdfTextIndex.buildIndex(
            pageCount = 10,
            pageTextProvider = { idx ->
                extracted.incrementAndGet()
                "Page $idx"
            },
            onProgress = { done, _ ->
                if (done >= 3) cancelled.set(true)
            },
            isCancelled = { cancelled.get() }
        )
        assertNull(aborted)
        assertEquals(3, extracted.get())
    }

    @Test
    fun buildIndex_detectsScannedPdfOnlyWhenPdfPagesHaveZeroChars() {
        val scanned = PdfTextIndex.buildIndex(pageCount = 3, pageTextProvider = { "" })
        assertNotNull(scanned)
        assertTrue(scanned?.isScannedPdf == true)
        assertEquals(0, scanned?.totalCharCount)

        val empty = PdfTextIndex.buildIndex(pageCount = 0, pageTextProvider = { "" })
        assertNotNull(empty)
        assertFalse(empty?.isScannedPdf == true)

        val hasText = PdfTextIndex.buildIndex(pageCount = 2, pageTextProvider = { if (it == 1) "hi" else "" })
        assertNotNull(hasText)
        assertFalse(hasText?.isScannedPdf == true)
    }

    @Test
    fun buildNoteIndex_handlesBlankPagesDiskCacheRoundTripAndInvalidation() {
        val cacheFile = File(tempFolder.root, "note1/pdf_text.json")
        val descriptors = listOf(
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 0, pdfSourcePath = "note/n1/a.pdf", pdfPageIndex = 0),
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 1, pdfSourcePath = "", pdfPageIndex = -1), // inserted blank page
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 2, pdfSourcePath = "note/n1/a.pdf", pdfPageIndex = 1),
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 3, pdfSourcePath = "note/n1/b.pdf", pdfPageIndex = 0)
        )
        var sizeA = 1000L
        var mtimeA = 5000L
        val extractCalls = AtomicInteger(0)
        val rawSpecial = "Line1\r\n\t\"Quoted\" \\slash\\ \u0007bell Äpfel 日本語"

        val metaProvider: (String) -> PdfTextIndex.PdfSourceMeta? = { path ->
            when (path) {
                "note/n1/a.pdf" -> PdfTextIndex.PdfSourceMeta(path, sizeA, mtimeA)
                "note/n1/b.pdf" -> PdfTextIndex.PdfSourceMeta(path, 2000L, 6000L)
                else -> null
            }
        }
        val textProvider: (String, Int) -> String = { path, pdfIdx ->
            extractCalls.incrementAndGet()
            when {
                path == "note/n1/a.pdf" && pdfIdx == 0 -> rawSpecial
                path == "note/n1/a.pdf" && pdfIdx == 1 -> "Second PDF A page"
                else -> "PDF B page 0"
            }
        }

        val first = PdfTextIndex.buildNoteIndex(
            pages = descriptors,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(first)
        assertEquals(3, extractCalls.get())
        assertTrue(cacheFile.isFile)
        assertEquals(listOf(0, 2, 3), first?.pages?.map { it.notePageIndex })
        assertEquals(rawSpecial, first?.pages?.first()?.rawText)

        // Memory cache hit: zero provider calls
        val memHit = PdfTextIndex.buildNoteIndex(
            pages = descriptors,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(memHit)
        assertEquals(3, extractCalls.get())

        // Clear memory cache -> disk cache hit preserving control chars & non-ASCII verbatim
        PdfTextIndex.clearCache()
        val diskHit = PdfTextIndex.buildNoteIndex(
            pages = descriptors,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(diskHit)
        assertEquals(3, extractCalls.get())
        assertEquals(rawSpecial, diskHit?.pages?.first()?.rawText)

        // Mutate fileSize of a.pdf -> invalidates a.pdf only (re-extracts its 2 pages), b.pdf stays cached
        PdfTextIndex.clearCache()
        sizeA = 1001L
        val invalidated = PdfTextIndex.buildNoteIndex(
            pages = descriptors,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(invalidated)
        assertEquals(5, extractCalls.get())
    }

    @Test
    fun searchHitOffsetMapsThroughUserRectToCanvasAndBoxCanvasRects() {
        val raw = "Header\nQuarterly Revenue Report"
        val index = PdfTextIndex.buildIndex(pageCount = 1, pageTextProvider = { raw })
        assertNotNull(index)
        val built = index ?: return

        val hits = PdfTextIndex.search(built, "Revenue")
        assertEquals(1, hits.size)
        val hit = hits[0]
        assertEquals(17, hit.startOffset)
        assertEquals(7, hit.length)

        // Simulated PDFium user-space rect for the token on a 612x792 pt page (bottom-left origin)
        val userRect = floatArrayOf(100f, 700f, 200f, 680f)
        val singleCanvas = PdfPageGeometry.userRectToCanvas(userRect, pageHeightPts = 792f, pxPerPt = 2f)
        assertEquals(200f, singleCanvas[0], 0.01f)
        assertEquals((792f - 700f) * 2f, singleCanvas[1], 0.01f)
        assertEquals(400f, singleCanvas[2], 0.01f)
        assertEquals((792f - 680f) * 2f, singleCanvas[3], 0.01f)

        val boxRects = PdfTextIndex.userRectsToBoxCanvasRects(
            userRects = listOf(userRect),
            pageWidthPts = 612f,
            pageHeightPts = 792f,
            pageLeftPx = 306f,
            pageTopPx = 0f,
            pageWidthPx = 1224f
        )
        assertEquals(1, boxRects.size)
        val boxRect = boxRects[0]
        assertEquals(306f + 200f, boxRect[0], 0.01f)
        assertEquals((792f - 700f) * 2f, boxRect[1], 0.01f)
        assertEquals(306f + 400f, boxRect[2], 0.01f)
        assertEquals((792f - 680f) * 2f, boxRect[3], 0.01f)
    }

    @Test
    fun buildNoteIndex_partialPageSubsetDoesNotPoisonMissingPages() {
        val cacheFile = File(tempFolder.root, "note_partial/pdf_text.json")
        val metaProvider: (String) -> PdfTextIndex.PdfSourceMeta? = { path ->
            PdfTextIndex.PdfSourceMeta(path, fileSize = 4096L, lastModified = 9000L)
        }
        val providerCalls = mutableListOf<Int>()
        val textProvider: (String, Int) -> String = { _, pdfIdx ->
            providerCalls.add(pdfIdx)
            "ExtractedPage$pdfIdx"
        }

        val subset = listOf(
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 0, pdfSourcePath = "note/n/doc.pdf", pdfPageIndex = 0),
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 1, pdfSourcePath = "note/n/doc.pdf", pdfPageIndex = 2)
        )
        val first = PdfTextIndex.buildNoteIndex(
            pages = subset,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(first)
        assertEquals(listOf(0, 2), providerCalls)

        // Request [0, 1, 2] with memory cache populated: only page 1 should invoke provider.
        val fullDescriptors = listOf(
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 0, pdfSourcePath = "note/n/doc.pdf", pdfPageIndex = 0),
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 1, pdfSourcePath = "note/n/doc.pdf", pdfPageIndex = 1),
            PdfTextIndex.PdfPageDescriptor(notePageIndex = 2, pdfSourcePath = "note/n/doc.pdf", pdfPageIndex = 2)
        )
        val second = PdfTextIndex.buildNoteIndex(
            pages = fullDescriptors,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(second)
        assertEquals(listOf(0, 2, 1), providerCalls)
        assertEquals("ExtractedPage1", second?.pages?.get(1)?.rawText)

        // Clear memory cache -> disk cache hit for all 3 pages (0 provider calls).
        PdfTextIndex.clearCache()
        val fromDisk = PdfTextIndex.buildNoteIndex(
            pages = fullDescriptors,
            cacheFile = cacheFile,
            sourceMetaProvider = metaProvider,
            pageTextProvider = textProvider
        )
        assertNotNull(fromDisk)
        assertEquals(listOf(0, 2, 1), providerCalls)
        assertEquals(listOf("ExtractedPage0", "ExtractedPage1", "ExtractedPage2"), fromDisk?.pages?.map { it.rawText })
    }

    @Test
    fun buildPlainSnippet_preservesLongEndOfPageMatchWithoutTrailingEllipsis() {
        val longPhrase = "International Financial Reporting Standards Compliance"
        val raw = "Introductory text ".repeat(5) + longPhrase
        val index = PdfTextIndex.buildIndex(pageCount = 1, pageTextProvider = { raw })
        assertNotNull(index)
        val built = index ?: return

        val hits = PdfTextIndex.search(built, longPhrase, snippetRadius = 12)
        assertEquals(1, hits.size)
        val hit = hits[0]
        assertTrue("Expected full phrase in snippet '${hit.snippet}'", hit.snippet.contains(longPhrase))
        assertFalse("Expected no trailing ellipsis in '${hit.snippet}'", hit.snippet.endsWith("…"))
        assertTrue(hit.matchRanges.isNotEmpty())
    }

    @Test
    fun buildPlainSnippet_neverSplitsUtf16SurrogatePairsAtWindowBoundaries() {
        val emoji = "\uD83D\uDE00" // U+1F600 (2 UTF-16 code units)
        // Build text where emoji straddles both left and right boundaries of winStart/winEnd and start/end.
        val leftPad = emoji.repeat(30)
        val rightPad = emoji.repeat(30)
        val raw = "${leftPad}TARGET_KEYWORD${rightPad}"

        for (radius in listOf(5, 10, 24, 44)) {
            val hitAt = raw.indexOf("TARGET_KEYWORD")
            val snippet = PdfTextIndex.buildPlainSnippet(
                rawText = raw,
                hitAt = hitAt,
                hitLen = "TARGET_KEYWORD".length,
                tokens = listOf("TARGET_KEYWORD"),
                radius = radius
            )
            val s = snippet.text
            assertTrue(s.contains("TARGET_KEYWORD"))
            for (i in s.indices) {
                val ch = s[i]
                if (ch.isHighSurrogate()) {
                    assertTrue(
                        "Unpaired high surrogate at $i in radius=$radius",
                        i + 1 < s.length && s[i + 1].isLowSurrogate()
                    )
                }
                if (ch.isLowSurrogate()) {
                    assertTrue(
                        "Unpaired low surrogate at $i in radius=$radius",
                        i - 1 >= 0 && s[i - 1].isHighSurrogate()
                    )
                }
            }
        }
    }
}
