package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import personal.notes.touch.util.PdfTextSelectionMath

/**
 * Defect F-10: PDF text selection math. pdfium only reports hit indices and
 * char boxes; ordering, clamping and underline geometry are our own logic and
 * are verified here on the JVM (the JNI calls themselves need a device).
 */
class PdfTextSelectionMathTest {

    @Test
    fun range_isInclusiveAndDirectionIndependent() {
        assertEquals(3..7, PdfTextSelectionMath.range(3, 7))
        assertEquals(3..7, PdfTextSelectionMath.range(7, 3))
        assertEquals(4..4, PdfTextSelectionMath.range(4, 4))
        assertNull(PdfTextSelectionMath.range(-1, 4))
        assertNull(PdfTextSelectionMath.range(4, -1))
    }

    @Test
    fun count_matchesInclusiveRangeAndHandlesNone() {
        assertEquals(5, PdfTextSelectionMath.count(3..7))
        assertEquals(1, PdfTextSelectionMath.count(0..0))
        assertEquals(0, PdfTextSelectionMath.count(null))
    }

    @Test
    fun clampIndex_keepsHitsInsideThePage() {
        assertEquals(4, PdfTextSelectionMath.clampIndex(4, 10))
        assertEquals(9, PdfTextSelectionMath.clampIndex(12, 10))
        assertEquals(-1, PdfTextSelectionMath.clampIndex(-1, 10))
        assertEquals(-1, PdfTextSelectionMath.clampIndex(3, 0))
    }

    @Test
    fun underlineRect_sitsAtTheBottomWithMinimumThickness() {
        val band = PdfTextSelectionMath.underlineRect(
            floatArrayOf(10f, 100f, 110f, 130f),
            thickness = 3f
        )
        assertEquals(10f, band[0], 1e-4f)
        assertEquals(127f, band[1], 1e-4f)
        assertEquals(110f, band[2], 1e-4f)
        assertEquals(130f, band[3], 1e-4f)
        // Degenerate thickness never collapses to nothing.
        val thin = PdfTextSelectionMath.underlineRect(floatArrayOf(0f, 0f, 10f, 10f), 0f)
        assertEquals(1f, thin[3] - thin[1], 1e-4f)
    }

    @Test
    fun tolerancePageUnits_shrinksAsTheUserZoomsIn() {
        assertEquals(28f, PdfTextSelectionMath.tolerancePageUnits(28f, 1f), 1e-4f)
        assertEquals(14f, PdfTextSelectionMath.tolerancePageUnits(28f, 2f), 1e-4f)
        // Guard rail: invalid scale falls back to the screen value.
        assertEquals(28f, PdfTextSelectionMath.tolerancePageUnits(28f, 0f), 1e-4f)
    }
}
