package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.PageScrubberMath

/**
 * Pure-JVM unit tests for `PDF-QOL-5` (`PageScrubberMath`).
 */
class PageScrubberMathTest {

    @Test
    fun isScrubberEnabled_onlyWhenTotalPagesGreaterThanFive() {
        for (n in -2..5) {
            assertFalse("Expected disabled for totalPages=$n", PageScrubberMath.isScrubberEnabled(n))
        }
        for (n in 6..50) {
            assertTrue("Expected enabled for totalPages=$n", PageScrubberMath.isScrubberEnabled(n))
        }
    }

    @Test
    fun scrubFractionToPage_handlesEdgeValuesAndInvalidFloats() {
        assertEquals(0, PageScrubberMath.scrubFractionToPage(0.5f, 0))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(0.5f, -3))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(0.5f, 1))

        assertEquals(0, PageScrubberMath.scrubFractionToPage(0f, 20))
        assertEquals(19, PageScrubberMath.scrubFractionToPage(1f, 20))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(-0.5f, 20))
        assertEquals(19, PageScrubberMath.scrubFractionToPage(1.5f, 20))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(Float.NaN, 20))
        assertEquals(0, PageScrubberMath.scrubFractionToPage(Float.NEGATIVE_INFINITY, 20))
        assertEquals(19, PageScrubberMath.scrubFractionToPage(Float.POSITIVE_INFINITY, 20))
    }

    @Test
    fun scrubFractionToPage_isMonotonicAcrossDenseSweep() {
        for (totalPages in listOf(6, 25, 200)) {
            var prev = 0
            for (step in 0..1000) {
                val fraction = step / 1000f
                val page = PageScrubberMath.scrubFractionToPage(fraction, totalPages)
                assertTrue(page in 0 until totalPages)
                assertTrue("Non-monotonic at step=$step for N=$totalPages: $prev -> $page", page >= prev)
                prev = page
            }
            assertEquals(totalPages - 1, prev)
        }
    }

    @Test
    fun pageToScrubFraction_roundTripsAllValidIndices() {
        assertEquals(0f, PageScrubberMath.pageToScrubFraction(0, 1), 0.0001f)
        for (totalPages in listOf(6, 17, 100)) {
            for (idx in 0 until totalPages) {
                val frac = PageScrubberMath.pageToScrubFraction(idx, totalPages)
                assertTrue(frac in 0f..1f)
                assertEquals(idx, PageScrubberMath.scrubFractionToPage(frac, totalPages))
            }
        }
    }

    @Test
    fun dragDeltaAndApplyScrubDelta_respectsOrientationAndGuardsInvalidInputs() {
        // Vertical mode (0): uses deltaY, ignores deltaX
        assertEquals(
            0.25f,
            PageScrubberMath.dragDeltaToFractionStep(deltaX = 500f, deltaY = 50f, pageTurningDirection = 0, trackSpanPx = 200f),
            0.0001f
        )

        // Horizontal mode (1): uses dominant deltaX when larger, else capsule deltaY
        assertEquals(
            0.5f,
            PageScrubberMath.dragDeltaToFractionStep(deltaX = 100f, deltaY = 10f, pageTurningDirection = 1, trackSpanPx = 200f),
            0.0001f
        )
        assertEquals(
            -0.25f,
            PageScrubberMath.dragDeltaToFractionStep(deltaX = 5f, deltaY = -50f, pageTurningDirection = 1, trackSpanPx = 200f),
            0.0001f
        )

        // Invalid span or non-finite deltas yield 0f
        assertEquals(0f, PageScrubberMath.dragDeltaToFractionStep(10f, 10f, 0, 0f), 0.0001f)
        assertEquals(0f, PageScrubberMath.dragDeltaToFractionStep(10f, 10f, 0, -100f), 0.0001f)
        assertEquals(0f, PageScrubberMath.dragDeltaToFractionStep(Float.NaN, 10f, 0, 100f), 0.0001f)
        assertEquals(0f, PageScrubberMath.dragDeltaToFractionStep(10f, Float.POSITIVE_INFINITY, 0, 100f), 0.0001f)

        // applyScrubDelta clamps result to 0f..1f
        assertEquals(1f, PageScrubberMath.applyScrubDelta(0.9f, 0f, 50f, 0, 100f), 0.0001f)
        assertEquals(0f, PageScrubberMath.applyScrubDelta(0.1f, 0f, -50f, 0, 100f), 0.0001f)
    }
}
