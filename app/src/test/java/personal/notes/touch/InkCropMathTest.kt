package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.InkCropMath

/**
 * Geometry of the ink crops the canvas keeps outside its page bitmap: ink
 * written on a layer and ink written past the edge of the paper.
 */
class InkCropMathTest {

    @Test
    fun anEmptyStrokeHasNoBox() {
        assertNull(InkCropMath.strokeBounds(FloatArray(0), FloatArray(0), 10f))
    }

    @Test
    fun mismatchedPointArraysHaveNoBox() {
        assertNull(InkCropMath.strokeBounds(floatArrayOf(1f, 2f), floatArrayOf(1f), 10f))
    }

    @Test
    fun theBoxCoversTheStrokeAndTheNib() {
        val box = InkCropMath.strokeBounds(
            floatArrayOf(100f, 400f),
            floatArrayOf(50f, 300f),
            widest = 20f,
            padFactor = 0.5f,
            extra = 6f
        )
        assertNotNull(box)
        val b = box!!
        // The nib (20 px) plus the cap margin (6 px) surrounds the stroke.
        assertTrue("left ${b.left}", b.left <= 100 - 16)
        assertTrue("top ${b.top}", b.top <= 50 - 16)
        assertTrue("right ${b.right}", b.right >= 400 + 16)
        assertTrue("bottom ${b.bottom}", b.bottom >= 300 + 16)
    }

    @Test
    fun aWiderNibGrowsTheBox() {
        val thin = InkCropMath.strokeBounds(floatArrayOf(10f), floatArrayOf(10f), 2f)!!
        val thick = InkCropMath.strokeBounds(floatArrayOf(10f), floatArrayOf(10f), 40f)!!
        assertTrue(thick.width > thin.width)
        assertTrue(thick.height > thin.height)
    }

    @Test
    fun theHighlighterBandNeedsMoreRoom() {
        val pen = InkCropMath.strokeBounds(floatArrayOf(10f), floatArrayOf(10f), 10f, 0.5f, 6f)!!
        val highlighter = InkCropMath.strokeBounds(floatArrayOf(10f), floatArrayOf(10f), 10f, 1.7f, 6f)!!
        assertTrue(highlighter.width > pen.width)
    }

    @Test
    fun aStrokeOnThePaperIsOnPaper() {
        val box = InkCropMath.strokeBounds(floatArrayOf(100f, 400f), floatArrayOf(100f, 400f), 10f)!!
        assertTrue(InkCropMath.isOnPaper(box, 2160, 3056))
        assertFalse(InkCropMath.isOffPaper(box, 2160, 3056))
    }

    @Test
    fun aStrokePastTheEdgeIsOffPaper() {
        val box = InkCropMath.strokeBounds(floatArrayOf(2100f, 2200f), floatArrayOf(100f, 200f), 10f)!!
        assertTrue(InkCropMath.isOffPaper(box, 2160, 3056))
        val left = InkCropMath.strokeBounds(floatArrayOf(-40f, 100f), floatArrayOf(10f, 20f), 10f)!!
        assertTrue(InkCropMath.isOffPaper(left, 2160, 3056))
    }

    @Test
    fun aCropHasToHoldTheWholeStroke() {
        assertTrue(InkCropMath.cropCoversStroke(0, 0, 400, 300, 300, 200))
        assertFalse(InkCropMath.cropCoversStroke(0, 0, 100, 100, 300, 200))
    }
}
