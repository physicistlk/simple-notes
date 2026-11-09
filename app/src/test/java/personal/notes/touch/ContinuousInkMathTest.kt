package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ContinuousInkMath
import personal.notes.touch.ui.canvas.InkPoint

/**
 * Reader inking geometry: strokes are stored in normalised page coordinates,
 * so the same stroke has to map correctly onto a layer of any resolution and
 * its undo patch must stay inside the layer.
 */
class ContinuousInkMathTest {

    @Test
    fun emptyStrokeHasNoBounds() {
        assertNull(ContinuousInkMath.strokeBounds(emptyList(), 0.01f, 2160, 3056))
    }

    @Test
    fun zeroSizedLayerHasNoBounds() {
        val points = listOf(InkPoint(0.5f, 0.5f, 0.01f))
        assertNull(ContinuousInkMath.strokeBounds(points, 0.01f, 0, 3056))
        assertNull(ContinuousInkMath.strokeBounds(points, 0.01f, 2160, 0))
    }

    @Test
    fun boundsCoverTheStrokeAndStayInsideTheLayer() {
        val points = listOf(InkPoint(0.25f, 0.25f, 0.01f), InkPoint(0.75f, 0.75f, 0.01f))
        val bounds = ContinuousInkMath.strokeBounds(points, 0.01f, 1000, 1400)
        assertNotNull(bounds)
        val b = bounds!!
        assertTrue("left ${b.left}", b.left in 0 until 250)
        assertTrue("top ${b.top}", b.top in 0 until 350)
        assertTrue("right ${b.right}", b.right in 750..1000)
        assertTrue("bottom ${b.bottom}", b.bottom in 1050..1400)
    }

    @Test
    fun edgeStrokesAreClampedNotWrapped() {
        val topLeft = ContinuousInkMath.strokeBounds(
            listOf(InkPoint(0f, 0f, 0.01f)), 0.01f, 2160, 3056
        )!!
        assertEquals(0, topLeft.left)
        assertEquals(0, topLeft.top)
        assertTrue(topLeft.width > 0)
        assertTrue(topLeft.height > 0)

        val bottomRight = ContinuousInkMath.strokeBounds(
            listOf(InkPoint(1f, 1f, 0.01f)), 0.01f, 2160, 3056
        )!!
        assertEquals(2160, bottomRight.right)
        assertEquals(3056, bottomRight.bottom)
        assertTrue(bottomRight.left < 2160)
        assertTrue(bottomRight.top < 3056)
    }

    @Test
    fun aWideNibGrowsThePatch() {
        val points = listOf(InkPoint(0.5f, 0.5f, 0.01f))
        val thin = ContinuousInkMath.strokeBounds(points, 0.01f, 2160, 3056)!!
        val widePoints = listOf(InkPoint(0.5f, 0.5f, 0.05f))
        val thick = ContinuousInkMath.strokeBounds(widePoints, 0.05f, 2160, 3056)!!
        assertTrue(thick.width > thin.width)
        assertTrue(thick.height > thin.height)
    }

    @Test
    fun fountainWidthFollowsPressure() {
        val base = 0.01f
        val light = ContinuousInkMath.fountainWidth(0.01f, base, 1f, true)
        val heavy = ContinuousInkMath.fountainWidth(1f, base, 1f, true)
        assertEquals(base * 0.35f + base * 0.01f * 1.35f, light, 0.0005f)
        assertEquals(base * 1.7f, heavy, 0.0005f)
        assertTrue(heavy > light)
    }

    @Test
    fun fountainWidthIgnoresPressureWhenSizeIsOff() {
        assertEquals(
            0.02f,
            ContinuousInkMath.fountainWidth(0.01f, 0.02f, 1f, false),
            0.0001f
        )
    }

    @Test
    fun sensitivityChangesTheCurve() {
        val base = 0.01f
        // sensitivity is applied as 1/sensitivity in the exponent, so a higher
        // setting already reaches a wide stroke at mid pressure.
        val moreSensitive = ContinuousInkMath.fountainWidth(0.5f, base, 2f, true)
        val lessSensitive = ContinuousInkMath.fountainWidth(0.5f, base, 0.5f, true)
        assertTrue(moreSensitive > lessSensitive)
    }

    @Test
    fun smoothingMovesTowardTheTarget() {
        assertEquals(0.65f, ContinuousInkMath.smooth(1f, 0f), 0.0001f)
        // 0 means "no previous sample", so the target is taken as-is.
        assertEquals(1f, ContinuousInkMath.smooth(0f, 1f), 0.0001f)
        assertEquals(0.2f, ContinuousInkMath.smooth(-1f, 0.2f), 0.0001f)
    }

    @Test
    fun highlighterStrokeBoundsAreLargerThanPen() {
        val points = listOf(InkPoint(0.5f, 0.5f, 0.01f), InkPoint(0.6f, 0.6f, 0.01f))
        val penBounds = ContinuousInkMath.strokeBounds(points, 0.01f, 2160, 3056, padFactor = 0.6f)!!
        val highlighterBounds = ContinuousInkMath.strokeBounds(points, 0.01f * 3.2f, 2160, 3056, padFactor = 0.85f)!!
        assertTrue(highlighterBounds.width > penBounds.width)
        assertTrue(highlighterBounds.height > penBounds.height)
        assertTrue(highlighterBounds.left <= penBounds.left)
        assertTrue(highlighterBounds.right >= penBounds.right)
    }
}
