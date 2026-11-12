package personal.notes.touch

import kotlin.math.roundToInt
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
 * box, and where the annotation layer of the page sits.
 *
 * The reader's box is wider than the page image (the page plus a quarter of
 * the page's width on both sides), so the user can write in the margin of an
 * imported PDF; an infinite page has no edge at all.
 */
class ContinuousPageGeometryTest {

    /** The aspect ratio of an A4 page (width / height). */
    private val a4 = 1f / 1.4142f

    private fun point(x: Float, y: Float, w: Float = 0.01f) = InkPoint(x, y, w)

    // ---- the box of a fixed page ----------------------------------------

    @Test
    fun aFixedPageIsWiderThanThePageAndKeepsItsHeight() {
        // The box of a fixed page: the page plus a quarter of the page's width
        // on the left and on the right, and the height of the page.
        val boxHeight = 1000f
        val boxWidth = boxHeight * a4 * 1.5f
        val rect = ContinuousPageGeometry.pageRect("a4", a4, boxWidth, boxHeight)

        // The page fills the height of the box: there is no margin above and
        // below it, the page keeps its place in the reader.
        assertEquals(0f, rect.top, 0.0001f)
        assertEquals(1f, rect.bottom, 0.0001f)
        // The page is centred: a sixth of the box is free on each side.
        assertEquals(1f / 6f, rect.left, 0.001f)
        assertEquals(5f / 6f, rect.right, 0.001f)
        // The margin is a quarter of the page's own width.
        val pageWidth = rect.width * boxWidth
        assertEquals(0.25f * pageWidth, rect.left * boxWidth, 1f)
    }

    @Test
    fun theBoxOfAFixedPageIsAWiderRectangleThanItsPage() {
        val boxRatio = ContinuousPageGeometry.boxRatio("a4", a4)
        assertEquals(a4 * 1.5f, boxRatio, 0.0001f)
        // The page is two thirds of the box.
        assertTrue("the box is wider", boxRatio > a4)
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
        // The box of a fixed page has the aspect of the page plus its
        // margins, so the page fills the height of the box.
        val boxHeight = 1000f
        val boxWidth = boxHeight * a4 * 1.5f
        val fixed = ContinuousPageGeometry.pageRect("a4", a4, boxWidth, boxHeight)
        assertTrue("the margin is left of the page", fixed.left > 0f)
        assertTrue("the margin is right of the page", fixed.right < 1f)
        assertEquals("no margin above the page", 0f, fixed.top, 0.0001f)
        assertEquals("no margin below the page", 1f, fixed.bottom, 0.0001f)

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

    // ---- the annotation layer -------------------------------------------

    @Test
    fun theLayerOfAFixedPageIsAsWideAsTheBoxAndAsTallAsThePage() {
        // The layer of an A4 page (2160 px wide): the page plus the margin
        // on both sides, so a third wider than the page, and as tall as the
        // page.
        val size = ContinuousPageGeometry.layerSize(
            "a4", ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        )
        assertEquals(3240, size.width)
        assertEquals(3056, size.height)
    }

    @Test
    fun theLayerOfAnInfinitePageHasTheMarginAsWell() {
        // The endless canvas gets the same room around its saved image as a
        // fixed page, so a stroke outside the saved canvas is kept (C33).
        val size = ContinuousPageGeometry.layerSize(
            "infinite", ContinuousPageGeometry.INFINITE_PAGE_RATIO
        )
        assertEquals((2160 * 1.5f).roundToInt(), size.width)
        assertEquals(3840, size.height)
    }

    @Test
    fun theLayerOfAnInfinitePageReachesPastTheSavedCanvas() {
        val boxWidth = 1600f
        val boxHeight = 2263f
        val page = ContinuousPageGeometry.pageRect(
            "infinite", ContinuousPageGeometry.INFINITE_PAGE_RATIO, boxWidth, boxHeight
        )
        val layer = ContinuousPageGeometry.layerRect(
            "infinite", ContinuousPageGeometry.INFINITE_PAGE_RATIO, boxWidth, boxHeight
        )
        // The layer is wider than the page and centred on it.
        assertTrue("the layer has to reach past the page", layer.left < page.left)
        assertTrue("the layer has to reach past the page", layer.right > page.right)
        assertEquals(page.left - (page.right - page.left) * ContinuousPageGeometry.PAGE_MARGIN, layer.left, 0.0001f)
    }

    @Test
    fun theLayerOfAFixedPageCoversTheWholeBox() {
        val boxHeight = 1000f
        val boxWidth = boxHeight * a4 * 1.5f
        val rect = ContinuousPageGeometry.layerRect("a4", a4, boxWidth, boxHeight)
        assertEquals(0f, rect.left, 0.0001f)
        assertEquals(0f, rect.top, 0.0001f)
        assertEquals(1f, rect.right, 0.0001f)
        assertEquals(1f, rect.bottom, 0.0001f)
    }

    @Test
    fun aStrokeInTheMarginIsInsideTheLayerAndOutsideThePage() {
        val boxHeight = 1000f
        val boxWidth = boxHeight * a4 * 1.5f
        val page = ContinuousPageGeometry.pageRect("a4", a4, boxWidth, boxHeight)
        val layer = ContinuousPageGeometry.layerRect("a4", a4, boxWidth, boxHeight)

        // A stroke in the left margin of the page (5% of the box).
        val x = 0.05f
        val y = 0.5f
        assertFalse("the stroke is not on the page", page.contains(x, y))
        assertTrue("the stroke is on the layer", layer.contains(x, y))

        // Its place in the layer's pixels: inside the layer, and left of the
        // page (the page starts a quarter of its width further right).
        val size = ContinuousPageGeometry.layerSize("a4", a4)
        val point = ContinuousPageGeometry.layerPoint(layer, x, y, size.width, size.height)
        assertTrue("x ${point.x}", point.x > 0f && point.x < size.width)
        assertTrue("y ${point.y}", point.y > 0f && point.y < size.height)
        val pageLeft = size.width / 6f
        assertTrue("the stroke is left of the page", point.x < pageLeft)
    }

    @Test
    fun aStrokeOnThePageLandsOnThePageOfTheLayer() {
        val boxHeight = 1000f
        val boxWidth = boxHeight * a4 * 1.5f
        val layer = ContinuousPageGeometry.layerRect("a4", a4, boxWidth, boxHeight)
        val size = ContinuousPageGeometry.layerSize("a4", a4)
        // The middle of the box is the middle of the page.
        val point = ContinuousPageGeometry.layerPoint(layer, 0.5f, 0.5f, size.width, size.height)
        assertEquals(size.width / 2f, point.x, 1f)
        assertEquals(size.height / 2f, point.y, 1f)
    }

    // ---- the decision: on the page or a crop ----------------------------

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
