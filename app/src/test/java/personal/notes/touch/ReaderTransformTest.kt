package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ReaderTransform

/**
 * The pinch of the reader (audit C45).
 *
 * The page under the fingers has to stay under the fingers: the zoom grows
 * the page around the point between the fingers, not around the middle of
 * the page. The zoom is also bounded - the page can be zoomed in far enough
 * to write small text, and far enough out to see the whole note.
 */
class ReaderTransformTest {

    /** The window of the reader: a tablet in portrait, in pixels. */
    private val viewportWidth = 1600f
    private val viewportHeight = 2560f

    /**
     * The layout of a reader that shows one page at the given place.
     *
     * The page is as big as the zoom says (the reader measures the pages at
     * the zoom) and it starts at the given corner of the window.
     */
    private fun layout(
        scale: Float = 1f,
        originX: Float = 0f,
        originY: Float = 0f,
        pagesBefore: Int = 0,
        horizontal: Boolean = false,
        pageWidth: Float = viewportWidth * scale,
        pageHeight: Float = viewportHeight * scale
    ): ReaderTransform.Layout = ReaderTransform.Layout(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        horizontal = horizontal,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        originX = originX,
        originY = originY,
        pagesBefore = pagesBefore
    )

    /** Where a point of the page's own space is on the screen. */
    private fun screenPoint(
        result: ReaderTransform.Result,
        contentX: Float,
        contentY: Float
    ): Pair<Float, Float> = ReaderTransform.screenPoint(
        result.originX,
        result.originY,
        result.state.scale,
        contentX,
        contentY
    )

    @Test
    fun thePointUnderTheFingersStaysUnderTheFingersInAColumn() {
        val scale = 1f
        val layout = layout(scale = scale)
        val centroids = listOf(
            200f to 300f,
            800f to 1280f,
            1400f to 2200f
        )
        val zooms = listOf(1.25f, 1.5f, 2f, 3f, 6f)
        for ((cx, cy) in centroids) {
            for (zoom in zooms) {
                val gesture = ReaderTransform.Gesture(cx, cy, 0f, 0f, zoom)
                val result = ReaderTransform.apply(
                    ReaderTransform.State(scale),
                    gesture,
                    layout
                )
                val content = ReaderTransform.contentPoint(layout, scale, cx, cy)
                val screen = screenPoint(result, content.first, content.second)
                assertEquals(cx, screen.first, 0.01f)
                assertEquals(cy, screen.second, 0.01f)
            }
        }
    }

    @Test
    fun thePointUnderTheFingersStaysUnderTheFingersInARow() {
        val scale = 1f
        val layout = layout(scale = scale, horizontal = true)
        val centroids = listOf(
            150f to 200f,
            800f to 1280f,
            1450f to 2300f
        )
        val zooms = listOf(1.25f, 2f, 4f)
        for ((cx, cy) in centroids) {
            for (zoom in zooms) {
                val gesture = ReaderTransform.Gesture(cx, cy, 0f, 0f, zoom)
                val result = ReaderTransform.apply(
                    ReaderTransform.State(scale),
                    gesture,
                    layout
                )
                val content = ReaderTransform.contentPoint(layout, scale, cx, cy)
                val screen = screenPoint(result, content.first, content.second)
                assertEquals(cx, screen.first, 0.01f)
                assertEquals(cy, screen.second, 0.01f)
            }
        }
    }

    @Test
    fun thePointUnderTheFingersFollowsTheFingers() {
        val scale = 1f
        val layout = layout(scale = scale)
        val gesture = ReaderTransform.Gesture(800f, 1280f, 40f, -25f, 1.8f)
        val result = ReaderTransform.apply(ReaderTransform.State(scale), gesture, layout)
        val content = ReaderTransform.contentPoint(layout, scale, 800f, 1280f)
        val screen = screenPoint(result, content.first, content.second)
        assertEquals(840f, screen.first, 0.01f)
        assertEquals(1255f, screen.second, 0.01f)
    }

    @Test
    fun thePointUnderTheFingersStaysWhenThePageIsNotTheFirstOne() {
        // The pages above the one under the fingers grow with the zoom and
        // push it down: the scroll has to pay for that.
        val scale = 1f
        val layout = layout(scale = scale, originY = -400f, pagesBefore = 1)
        val gesture = ReaderTransform.Gesture(700f, 900f, 0f, 0f, 2f)
        val result = ReaderTransform.apply(ReaderTransform.State(scale), gesture, layout)
        val content = ReaderTransform.contentPoint(layout, scale, 700f, 900f)
        val screen = screenPoint(result, content.first, content.second)
        assertEquals(700f, screen.first, 0.01f)
        assertEquals(900f, screen.second, 0.01f)
    }

    @Test
    fun thePageIsNotPulledFurtherThanTheLimit() {
        val layout = layout(scale = 1f)
        val gesture = ReaderTransform.Gesture(800f, 1280f, 100000f, 100000f, 2f)
        val result = ReaderTransform.apply(ReaderTransform.State(scale = 1f), gesture, layout)
        val limit = ReaderTransform.pullLimit(viewportWidth, result.state.scale)
        assertTrue(
            "the page was pulled ${result.state.panX} px, the limit is $limit px",
            result.state.panX <= limit + 0.01f
        )
        assertTrue(
            "the page was pulled ${result.state.panX} px, the limit is $limit px",
            result.state.panX >= -limit - 0.01f
        )
    }

    @Test
    fun theZoomIsKeptInsideTheLimits() {
        val layout = layout(scale = 1f)
        val far = ReaderTransform.apply(
            ReaderTransform.State(scale = 1f),
            ReaderTransform.Gesture(800f, 1280f, 0f, 0f, 1000f),
            layout
        )
        assertEquals(ReaderTransform.MAX_SCALE, far.state.scale, 0.001f)
        val near = ReaderTransform.apply(
            ReaderTransform.State(scale = 1f),
            ReaderTransform.Gesture(800f, 1280f, 0f, 0f, 0.0001f),
            layout
        )
        assertEquals(ReaderTransform.MIN_SCALE, near.state.scale, 0.001f)
        assertEquals(8f, ReaderTransform.MAX_SCALE, 0.001f)
        assertEquals(0.4f, ReaderTransform.MIN_SCALE, 0.001f)
    }

    @Test
    fun theSmallestZoomStillShowsTheWholePage() {
        // At 100 % the page fills the screen; at the smallest zoom it has to
        // fit on the screen with room around it.
        val pageAtFullSize = viewportWidth
        assertTrue(pageAtFullSize * ReaderTransform.MIN_SCALE < viewportWidth)
        assertTrue(viewportHeight * ReaderTransform.MIN_SCALE < viewportHeight)
        // And the biggest zoom is the one the single page engine allows.
        assertEquals(8f, ReaderTransform.MAX_SCALE, 0.001f)
    }

    @Test
    fun theLockedZoomDoesNotChangeTheScale() {
        val layout = layout(scale = 1f)
        val result = ReaderTransform.apply(
            ReaderTransform.State(scale = 1f),
            ReaderTransform.Gesture(800f, 1280f, 10f, 0f, 2f),
            layout,
            zoomLocked = true
        )
        assertEquals(1f, result.state.scale, 0.001f)
    }

    @Test
    fun theResetAndTheCentreAreTwoDifferentActions() {
        val state = ReaderTransform.State(scale = 3f, panX = 120f, panY = -80f)
        val reset = ReaderTransform.resetZoom(state)
        assertEquals(1f, reset.scale, 0.001f)
        assertEquals(120f, reset.panX, 0.001f)
        assertEquals(-80f, reset.panY, 0.001f)
        val centred = ReaderTransform.centre(state)
        assertEquals(3f, centred.scale, 0.001f)
        assertEquals(0f, centred.panX, 0.001f)
        assertEquals(0f, centred.panY, 0.001f)
    }

    @Test
    fun thePageOfAColumnIsPulledSidewaysAndThePageOfARowUpAndDown() {
        val column = ReaderTransform.apply(
            ReaderTransform.State(scale = 1f),
            ReaderTransform.Gesture(800f, 1280f, 60f, 0f, 1f),
            layout(scale = 1f, horizontal = false)
        )
        assertEquals(60f, column.state.panX, 0.01f)
        assertEquals(0f, column.state.panY, 0.01f)
        val row = ReaderTransform.apply(
            ReaderTransform.State(scale = 1f),
            ReaderTransform.Gesture(800f, 1280f, 0f, 60f, 1f),
            layout(scale = 1f, horizontal = true)
        )
        assertEquals(0f, row.state.panX, 0.01f)
        assertEquals(60f, row.state.panY, 0.01f)
    }

    @Test
    fun theScrollOfTheListPaysForTheZoom() {
        // The list scrolls, so the zoom has to tell it how far: without the
        // scroll the page would slide away under the fingers.
        val layout = layout(scale = 1f, originY = 100f)
        val result = ReaderTransform.apply(
            ReaderTransform.State(scale = 1f),
            ReaderTransform.Gesture(800f, 1280f, 0f, 0f, 2f),
            layout
        )
        // The page under the fingers was 1180 px below the top of the page;
        // at twice the size it has to start 1180 px higher.
        assertTrue(
            "the list was not scrolled (${result.scrollDelta} px)",
            result.scrollDelta > 1000f
        )
    }
}
