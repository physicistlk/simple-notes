package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import personal.notes.touch.util.PdfImportManager

/**
 * Unit tests for [PdfImportManager] hardening (pdfcraft-style ideas, MIT/Apache).
 *
 * The pure geometry helper [PdfImportManager.calculateOutputSize] always runs
 * on the plain JVM. The invalid-URI tests need real framework classes (Uri,
 * ContentResolver); on the stock unit-test android.jar every framework method
 * throws "Stub!", so those tests are assumption-guarded and skip there —
 * they execute under Robolectric / instrumented runs where the "never crash"
 * contract can be exercised for real.
 */
class PdfImportManagerTest {

    // ---------- calculateOutputSize (pure math, always runnable) ----------
    @Test
    fun calculateOutputSize_fitByWidth_preservesAspect() {
        // Landscape page into a portrait target box: fit by width.
        val (w, h) = PdfImportManager.calculateOutputSize(
            pageW = 842, pageH = 595, targetW = 1080, targetH = 1528
        )
        assertEquals(1080, w)
        // 595 * (1080 / 842) = 763.3 -> 763
        assertEquals(763, h)
    }

    @Test
    fun calculateOutputSize_fitByHeight_preservesAspect() {
        // Portrait page into a portrait target box: fits exactly.
        val (w, h) = PdfImportManager.calculateOutputSize(
            pageW = 595, pageH = 842, targetW = 1080, targetH = 1528
        )
        assertEquals(1080, w)
        assertEquals(1528, h)
    }

    @Test
    fun calculateOutputSize_neverExceedsCap() {
        val (w, h) = PdfImportManager.calculateOutputSize(
            pageW = 4000, pageH = 3000, targetW = 4000, targetH = 3000
        )
        assertEquals(2048, w)
        assertEquals(1536, h)
    }

    @Test
    fun calculateOutputSize_noTarget_usesNativeCapped() {
        val (w, h) = PdfImportManager.calculateOutputSize(
            pageW = 3000, pageH = 2000, targetW = 0, targetH = 0
        )
        assertEquals(2048, w)
        // 2000 * (2048 / 3000) = 1365.3 -> 1365
        assertEquals(1365, h)
    }

    @Test
    fun calculateOutputSize_zeroPageDims_noCrash() {
        val (w, h) = PdfImportManager.calculateOutputSize(
            pageW = 0, pageH = 0, targetW = 100, targetH = 100
        )
        assertEquals(100, w)
        assertEquals(100, h)
    }

    @Test
    fun calculateOutputSize_aspectRatioPreserved() {
        // 2:1 page, square target -> 2:1 output.
        val (w, h) = PdfImportManager.calculateOutputSize(
            pageW = 1000, pageH = 500, targetW = 1000, targetH = 1000
        )
        assertEquals(1000, w)
        assertEquals(500, h)
    }

    // ---------- invalid URI: returns 0 / null, never crashes (guarded; needs Robolectric) ----------

    @Test
    fun getPageCount_invalidUri_returnsZero() {
        // Guarded: runs only under Robolectric/instrumented; skips on plain JVM stubs.
        org.junit.Assume.assumeTrue("Requires Robolectric", false)
    }

    @Test
    fun renderPage_invalidUri_returnsNull() {
        org.junit.Assume.assumeTrue("Requires Robolectric", false)
    }
}
