package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.InkPoint

/**
 * The geometry of a reader page: where the page image sits inside the page
 * box, and when a stroke has left the page.
 *
 * The reader's box is bigger than the page image, so the user can write in
 * the margin of an imported PDF; an infinite page has no edge at all.
 */
class ContinuousPageGeometryTest {

    /** The aspect ratio of an A4 page (width / height). */
    private val a4 = 1f / 1.4142f

    private fun point(x: Float, y: Float, w: Float = 0.01f) = InkPoint(x, y, w)

    // ---- the box of a fixed page ----------------------------------------

    @Test
    fun aFixedPageKeepsAMarginOfAQuarterOnEverySide() {
        // A box with the aspect ratio of the page: the page image is centred
        // and every side has a quarter of the page's size free.
        val boxWidth = 1000f
        val boxHeight = 1000f / a4
        val rect = ContinuousPageGeometry.pageRect("a4", a4, boxWidth, boxHeight)

        assertEquals(1f / 6f, rect.left, 0.001f)
        assertEquals(1f / 6f, rect.top, 0.001f)
        assertEquals(5f / 6f, rect.right, 0.001f)
        assertEquals(5f / 6f, rect.bottom, 0.001f)
    }

    @Test
    fun thePageKeepsItsAspectRatioInTheBox() {
        val boxWidth = 1200f
        val boxHeight = 1500f
        val rect = ContinuousPageGeometry.pageRect("pdf", a4, boxWidth, boxHeight)

        // The page is drawn with one scale in both directions: the rect of
        // the box it occupies has the aspect ratio of the page.
        val destWidth = rect.width * boxWidth
        val destHeight = rect.height * boxHeight
        assertEquals(a4, destWidth / destHeight, 0.001f)
    }

    @Test
    fun theMarginIsThereForEveryPageTypeButTheInfiniteOne() {
        val fixed = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        assertTrue("the margin is left of the page", fixed.left > 0f)
        assertTrue("the margin is above the page", fixed.top > 0f)

        // An infinite page is fitted into the box: the endless canvas is not
        // bounded by the saved bitmap, so the free space can be on any side.
        val infinite = ContinuousPageGeometry.pageRect("infinite", 0.5625f, 1000f, 2000f)
        assertTrue("the image fills the width", infinite.left < 0.001f)
        assertTrue("there is room above and below", infinite.top > 0.01f)
    }

    @Test
    fun anInfinitePageIsCentredInTheBox() {
        val rect = ContinuousPageGeometry.pageRect("infinite", 0.5625f, 1000f, 2000f)
        assertEquals(1f - rect.left, rect.right, 0.001f)
        assertEquals(1f - rect.top, rect.bottom, 0.001f)
    }

    @Test
    fun anEmptyBoxHasNoPageRect() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 0f, 0f)
        assertEquals(0f, rect.left, 0.0001f)
        assertEquals(1f, rect.right, 0.0001f)
    }

    // ---- the decision: on the page or a crop ----------------------------

    @Test
    fun aStrokeOnThePageStaysAVectorOperation() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        val bounds = ContinuousPageGeometry.BoxRect(0.3f, 0.3f, 0.7f, 0.7f)
        assertTrue(ContinuousPageGeometry.isInsidePage(rect, bounds))
    }

    @Test
    fun aStrokeInTheMarginBecomesACrop() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        // It reaches over the left edge of the page.
        val left = ContinuousPageGeometry.BoxRect(0.05f, 0.3f, 0.5f, 0.4f)
        assertFalse(ContinuousPageGeometry.isInsidePage(rect, left))
        // And over the top edge.
        val top = ContinuousPageGeometry.BoxRect(0.3f, 0.02f, 0.4f, 0.5f)
        assertFalse(ContinuousPageGeometry.isInsidePage(rect, top))
    }

    @Test
    fun theBoundsOfAStrokeIncludeTheNib() {
        val bounds = ContinuousPageGeometry.strokeBounds(
            listOf(point(0.5f, 0.5f, 0.02f)),
            boxWidth = 1000f,
            boxHeight = 1000f,
            padFactor = 0.6f,
            extraPx = 4f
        )
        assertNotNull(bounds)
        val b = bounds!!
        // The nib is 2% of the box width (20 px): 60% of it around the
        // stroke, plus 4 px for the round cap.
        assertTrue("left ${b.left}", b.left <= 0.5f - 0.016f)
        assertTrue("right ${b.right}", b.right >= 0.5f + 0.016f)
    }

    @Test
    fun anEmptyStrokeHasNoBounds() {
        assertNull(
            ContinuousPageGeometry.strokeBounds(
                emptyList(), 1000f, 1000f, 0.6f, 4f
            )
        )
    }

    // ---- the crop in the page's pixel space -----------------------------

    @Test
    fun aCropInTheMarginReachesPastTheEdgeOfThePage() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        val bounds = ContinuousPageGeometry.BoxRect(0.05f, 0.3f, 0.2f, 0.35f)
        val place = ContinuousPageGeometry.cropPlace(rect, bounds, 2160, 3056)
        assertNotNull(place)
        val p = place!!
        // The stroke is left of the page: the crop starts left of it.
        assertTrue("x ${p.x}", p.x < 0f)
        val expectedWidth = 2160f * 0.15f / (2f / 3f)
        assertTrue(
            "width ${p.width} vs $expectedWidth",
            kotlin.math.abs(p.width - expectedWidth) <= 1f
        )
    }

    @Test
    fun aCropOnThePageSitsOnThePage() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        val bounds = ContinuousPageGeometry.BoxRect(0.3f, 0.3f, 0.6f, 0.4f)
        val place = ContinuousPageGeometry.cropPlace(rect, bounds, 2160, 3056)
        assertNotNull(place)
        val p = place!!
        assertTrue("x ${p.x}", p.x > 0f)
        assertTrue("y ${p.y}", p.y > 0f)
        assertTrue("right", p.x + p.width < 2160f)
    }

    @Test
    fun aCropNeedsAPageToLiveIn() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        val bounds = ContinuousPageGeometry.BoxRect(0.3f, 0.3f, 0.6f, 0.4f)
        assertNull(ContinuousPageGeometry.cropPlace(rect, bounds, 0, 0))
    }

    // ---- the mapping between the box and the page ------------------------

    @Test
    fun theMiddleOfTheBoxIsTheMiddleOfThePage() {
        val rect = ContinuousPageGeometry.pageRect("a4", a4, 1000f, 1414f)
        val pageX = (0.5f - rect.left) / rect.width * 2160
        val pageY = (0.5f - rect.top) / rect.height * 3056
        assertEquals(1080f, pageX, 1f)
        assertEquals(1528f, pageY, 1f)
    }

    @Test
    fun thePageIsDrawnWithOneScaleInBothDirections() {
        // The box is bigger than the page, but the page keeps its shape: one
        // box pixel is the same amount of page pixels in x and in y, so a
        // stroke does not change its shape when it is written into the page.
        for (box in listOf(1000f to 1414f, 1200f to 1500f, 800f to 800f)) {
            val rect = ContinuousPageGeometry.pageRect("a4", a4, box.first, box.second)
            val scaleX = rect.width * box.first / 2160f
            val scaleY = rect.height * box.second / 3056f
            assertEquals("box $box", scaleX, scaleY, scaleX * 0.001f)
        }
    }

    @Test
    fun theInfinitePageIsDrawnWithOneScaleInBothDirections() {
        val boxWidth = 1000f
        val boxHeight = 2000f
        val rect = ContinuousPageGeometry.pageRect("infinite", 0.5625f, boxWidth, boxHeight)
        val scaleX = rect.width * boxWidth / 2160f
        val scaleY = rect.height * boxHeight / 3840f
        assertEquals(scaleX, scaleY, scaleX * 0.001f)
    }
}
