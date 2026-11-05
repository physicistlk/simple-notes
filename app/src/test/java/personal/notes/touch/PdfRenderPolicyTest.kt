package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.util.PdfRenderPolicy

/**
 * Defect F-10: render policy of the live vector-PDF background. The JVM-side
 * contract: upgrade the page bitmap only when the zoom clearly outgrows it,
 * and never let the allocated bitmap exceed the memory budget.
 */
class PdfRenderPolicyTest {

    @Test
    fun firstRenderIsAlwaysNeeded() {
        assertTrue(PdfRenderPolicy.shouldRender(neededPxPerPt = 1.8f, currentPxPerPt = 0f))
        assertFalse(PdfRenderPolicy.shouldRender(neededPxPerPt = 0f, currentPxPerPt = 0f))
    }

    @Test
    fun rerenderOnlyPastHysteresis() {
        // 10% more pixels than current: keep the existing bitmap.
        assertFalse(PdfRenderPolicy.shouldRender(2.0f, 1.8f))
        // 50% more: re-render.
        assertTrue(PdfRenderPolicy.shouldRender(2.7f, 1.8f))
        // Exactly at the hysteresis boundary is not enough (strict >).
        assertFalse(PdfRenderPolicy.shouldRender(1.8f * PdfRenderPolicy.HYSTERESIS, 1.8f))
    }

    @Test
    fun capKeepsAreaWithinBudget() {
        // A4 portrait at 595x842 pt: the area budget dominates.
        val capped = PdfRenderPolicy.capPxPerPt(595f, 842f, requestedPxPerPt = 10f)
        val (w, h) = PdfRenderPolicy.outputSize(595f, 842f, capped).let { it[0] to it[1] }
        assertTrue("area ${w * h} must be <= ${PdfRenderPolicy.MAX_PIXELS}", w.toLong() * h <= PdfRenderPolicy.MAX_PIXELS)
    }

    @Test
    fun capKeepsLongestSideWithinDimensionLimit() {
        // Very tall page: the 4096 side cap dominates.
        val capped = PdfRenderPolicy.capPxPerPt(100f, 5000f, requestedPxPerPt = 10f)
        val height = PdfRenderPolicy.outputSize(100f, 5000f, capped)[1]
        assertTrue("height $height must be <= ${PdfRenderPolicy.MAX_DIMENSION}", height <= PdfRenderPolicy.MAX_DIMENSION)
    }

    @Test
    fun capNeverRaisesTheRequestedScale() {
        assertEquals(1.0f, PdfRenderPolicy.capPxPerPt(595f, 842f, 1.0f), 1e-6f)
        assertEquals(0f, PdfRenderPolicy.capPxPerPt(0f, 842f, 2f), 0f)
        assertEquals(0f, PdfRenderPolicy.capPxPerPt(595f, 842f, 0f), 0f)
    }

    @Test
    fun outputSizeNeverReturnsZeroDimensions() {
        val size = PdfRenderPolicy.outputSize(0.4f, 0.4f, 1f)
        assertEquals(1, size[0])
        assertEquals(1, size[1])
    }
}
