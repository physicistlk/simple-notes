package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.util.PdfExportPolicy
import personal.notes.touch.util.PdfRenderPolicy

/**
 * Pure-JVM unit tests for `PDF-02` (multi-page vector PDF background export
 * resolution & layer sizing) and `PDF-QOL-4` (per-page export page sizing).
 */
class PdfExportPolicyTest {

    private fun page(index: Int, source: String, pdfPage: Int) = PageEntity(
        id = "p-$index",
        noteId = "note-1",
        index = index,
        bitmapPath = "/tmp/page_$index.png",
        pdfSourcePath = source,
        pdfPageIndex = pdfPage
    )

    @Test
    fun resolveVectorPageSource_resolvesAllPagesAcrossMultiPagePdf() {
        val pages = listOf(
            page(0, "note/n1/doc.pdf", 0),
            page(1, "note/n1/doc.pdf", 1),
            page(2, "note/n1/doc.pdf", 2),
            page(5, "note/n1/doc2.pdf", 3),
            page(6, "", -1),
            page(7, "note/n1/doc.pdf", -1)
        )

        for (i in 0..2) {
            val resolved = PdfExportPolicy.resolveVectorPageSource(pages, i)
            assertNotNull(resolved)
            assertEquals("note/n1/doc.pdf", resolved?.pdfSourcePath)
            assertEquals(i, resolved?.pdfPageIndex)
        }
        val p5 = PdfExportPolicy.resolveVectorPageSource(pages, 5)
        assertEquals(PdfExportPolicy.PdfPageSource("note/n1/doc2.pdf", 3), p5)
        assertNull(PdfExportPolicy.resolveVectorPageSource(pages, 3))
        assertNull(PdfExportPolicy.resolveVectorPageSource(pages, 6))
        assertNull(PdfExportPolicy.resolveVectorPageSource(pages, 7))
    }

    @Test
    fun shouldUseVectorPdfBackground_respectsBackgroundOverride() {
        val src = PdfExportPolicy.PdfPageSource("note/n1/doc.pdf", 1)
        assertTrue(PdfExportPolicy.shouldUseVectorPdfBackground(src, hasBackgroundOverride = false))
        assertFalse(PdfExportPolicy.shouldUseVectorPdfBackground(src, hasBackgroundOverride = true))
        assertFalse(PdfExportPolicy.shouldUseVectorPdfBackground(null, hasBackgroundOverride = false))
    }

    @Test
    fun exportRenderTargetSize_producesCrispSizesAndCapsUltraTallPages() {
        val a4 = PdfExportPolicy.exportRenderTargetSize(595f, 842f)
        assertEquals(2160, a4.first)
        assertTrue(a4.second in 3056..3058)

        val landscape = PdfExportPolicy.exportRenderTargetSize(960f, 540f)
        assertEquals(2160 to 1215, landscape)

        val ultraTall = PdfExportPolicy.exportRenderTargetSize(100f, 5000f)
        assertTrue(ultraTall.second <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(ultraTall.first >= 1 && ultraTall.second >= 1)

        val degenerate = PdfExportPolicy.exportRenderTargetSize(0f, 0f)
        assertEquals(2160 to 3056, degenerate)
    }

    @Test
    fun resolveAnnotationLayerSize_adaptsToPageAspectAndPreservesTileSize() {
        assertEquals(
            ContinuousPageGeometry.LayerSize(6480, 1215),
            PdfExportPolicy.resolveAnnotationLayerSize("pdf", 16f / 9f, tilesWidth = 0, tilesHeight = 0)
        )
        assertEquals(
            ContinuousPageGeometry.LayerSize(6480, 3056),
            PdfExportPolicy.resolveAnnotationLayerSize("pdf", ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 0, 0)
        )
        assertEquals(
            ContinuousPageGeometry.LayerSize(2160, 3056),
            PdfExportPolicy.resolveAnnotationLayerSize("a4", ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 0, 0)
        )
        assertEquals(
            ContinuousPageGeometry.LayerSize(4000, 2250),
            PdfExportPolicy.resolveAnnotationLayerSize("pdf", 16f / 9f, tilesWidth = 4000, tilesHeight = 2250)
        )
    }

    @Test
    fun resolveExportPageSizePts_sizesLandscapePortraitMarginsAndFallbacks() {
        assertEquals(842 to 474, PdfExportPolicy.resolveExportPageSizePts(2160, 1215, 960f, 540f))
        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(2160, 3056, 595f, 842f))
        // Continuous PDF page with 3x width margin layer (6480x3056) keeps full aspect without squishing
        assertEquals(842 to 397, PdfExportPolicy.resolveExportPageSizePts(6480, 3056, 595f, 842f))
        // Blank bitmap falls back to PDF point size
        assertEquals(842 to 474, PdfExportPolicy.resolveExportPageSizePts(0, 0, 960f, 540f))
        // Totally blank non-PDF falls back to A4 portrait
        assertEquals(595 to 842, PdfExportPolicy.resolveExportPageSizePts(0, 0, 0f, 0f))
    }
}
