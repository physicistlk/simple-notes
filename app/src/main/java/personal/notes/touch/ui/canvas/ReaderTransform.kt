package personal.notes.touch.ui.canvas

/**
 * The transform of the reader: the zoom of the pages and the way they are
 * pulled aside.
 *
 * The reader shows its pages in a list - a column (a book read from the top)
 * or a row (a book read from the side). The zoom is a *real* zoom: the pages
 * are measured bigger (`pageWidthDp = screenWidth * continuousScale`), so
 * the pages next to the one the user reads are drawn as well and the whole
 * note can be seen at once.
 *
 * A pinch therefore does two things at the same time: it changes the size of
 * the pages and it moves them. The movement is the part that used to be
 * wrong: the page grew around its own centre instead of around the fingers,
 * because the maths compared the finger with the middle of the screen and
 * not with the point of the page that was under the fingers.
 *
 * The maths of the pinch is here, in a small pure object, so it can be
 * tested without a device. The view only has to tell it where the page
 * under the fingers is, and it gets the new zoom, the new pan and the
 * scroll of the list back.
 *
 * The model of the reader, in one paragraph: the page under the fingers is a
 * rectangle of `pageWidth x pageHeight` pixels (the page measured at the
 * current zoom) whose top left corner sits at `originX/originY` in the
 * reader's window. The content of the page (the image, the writing) is
 * scaled by the zoom around that corner. The pages of a column are centred
 * in the window and are pulled aside by `panX`; the pages of a row are
 * centred in the window and are pulled aside by `panY`. The list scrolls
 * along the other axis.
 */
object ReaderTransform {

    /**
     * The smallest zoom of the reader.
     *
     * Below one the pages are smaller than they fit, so the user sees the
     * note from far away - the overview of the whole note. At 0.4 the page
     * still fits on the screen with room around it.
     */
    const val MIN_SCALE = 0.4f

    /**
     * The biggest zoom of the reader.
     *
     * Eight times the size of the page is what writing small text needs;
     * the single page engine allows the same (`scale.coerceIn(minZoom, 8f)`
     * in `DrawingCanvasView`).
     */
    const val MAX_SCALE = 8f

    /** The zoom of the reader's pages and the way they are pulled aside. */
    data class State(
        /** The zoom of the pages (1 = the page at its own size). */
        val scale: Float = 1f,
        /**
         * How far the pages are pulled aside, in pixels. In a column the
         * pages are pulled sideways (`panX`), in a row they are pulled up
         * and down (`panY`); the other value is always zero.
         */
        val panX: Float = 0f,
        val panY: Float = 0f
    )

    /**
     * What the transform needs to know about the reader's layout.
     *
     * All the sizes are in pixels of the reader's window.
     */
    data class Layout(
        /** The width of the reader's window. */
        val viewportWidth: Float,
        /** The height of the reader's window. */
        val viewportHeight: Float,
        /** True when the pages lie in a row (a book read from the side). */
        val horizontal: Boolean,
        /** The width of the page under the fingers, at the current zoom. */
        val pageWidth: Float,
        /** The height of the page under the fingers, at the current zoom. */
        val pageHeight: Float,
        /** Where the page under the fingers starts in the window. */
        val originX: Float,
        val originY: Float,
        /**
         * How many pages lie between the first visible page and the page
         * under the fingers.
         *
         * Those pages grow with the zoom, so they push the page under the
         * fingers down (in a column) or to the right (in a row).
         */
        val pagesBefore: Int = 0
    )

    /**
     * The two fingers: where they are, how they moved and how far they
     * spread.
     */
    data class Gesture(
        /** The middle of the fingers, in the reader's window. */
        val centroidX: Float,
        val centroidY: Float,
        /** How far the fingers moved since the last event, in pixels. */
        val panX: Float,
        val panY: Float,
        /** How much the fingers spread (1 = they did not move apart). */
        val zoom: Float
    )

    /** What a gesture does to the reader. */
    data class Result(
        /** The new zoom and the new pan of the pages. */
        val state: State,
        /**
         * How far the page list has to scroll, in pixels. A positive value
         * scrolls forward (the pages move up).
         */
        val scrollDelta: Float,
        /** Where the page under the fingers starts after the gesture. */
        val originX: Float,
        val originY: Float
    )

    /** The zoom, kept inside the limits of the reader. */
    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /**
     * The "reset zoom" action: the pages are shown at their own size again.
     *
     * The pan is left alone: bringing the canvas back to its origin is a
     * different action (see [centre]).
     */
    fun resetZoom(state: State): State = state.copy(scale = 1f)

    /**
     * The "centre" action: the pages are moved back to where they started.
     *
     * The zoom is left alone: that is what the reset is for (see
     * [resetZoom]).
     */
    fun centre(state: State): State = state.copy(panX = 0f, panY = 0f)

    /**
     * How far the page can be pulled aside, in pixels.
     *
     * Half of the room the zoom gave the page, plus a whole window - so the
     * page can be moved out of the way (to write in the margin of a PDF, or
     * on the endless canvas) even when it is not zoomed in.
     */
    fun pullLimit(viewport: Float, scale: Float): Float =
        (viewport * (scale - 1f)) / 2f + viewport

    /**
     * The transform of one pinch.
     *
     * The point of the page that is under the fingers stays under the
     * fingers: the page is scaled around that point, not around its own
     * centre. The zoom is kept inside [MIN_SCALE] and [MAX_SCALE] (unless
     * the zoom is locked, then it does not change at all), the pan is kept
     * inside the pull limit, and the list is told how far it has to scroll
     * so the page ends up where the fingers want it.
     */
    fun apply(
        state: State,
        gesture: Gesture,
        layout: Layout,
        zoomLocked: Boolean = false
    ): Result {
        val oldScale = if (state.scale > 0f) state.scale else 1f
        val newScale = if (zoomLocked) oldScale else clampScale(oldScale * gesture.zoom)
        val factor = newScale / oldScale

        // The point of the page under the fingers, in the page's own space.
        val content = contentPoint(layout, oldScale, gesture.centroidX, gesture.centroidY)
        val contentX = content.first
        val contentY = content.second

        // Where the page has to start so that this point stays under the
        // fingers (and follows them when they move).
        val wantedX = gesture.centroidX + gesture.panX - contentX * newScale
        val wantedY = gesture.centroidY + gesture.panY - contentY * newScale

        // The axis the pages are pulled aside on: the page is centred in the
        // window, so the pan has to carry the difference between the wanted
        // place and the centred one.
        val crossWanted = if (layout.horizontal) wantedY else wantedX
        val crossCentre =
            if (layout.horizontal) layout.viewportHeight / 2f else layout.viewportWidth / 2f
        val crossSize = if (layout.horizontal) layout.pageHeight else layout.pageWidth
        val crossViewport =
            if (layout.horizontal) layout.viewportHeight else layout.viewportWidth
        val limit = pullLimit(crossViewport, newScale)
        val crossPan = (crossWanted - crossCentre + crossSize * factor / 2f)
            .coerceIn(-limit, limit)

        // The axis the list scrolls on: the pages above the one under the
        // fingers grow with the zoom and push it down, so the scroll has to
        // pay for that as well.
        val scrollWanted = if (layout.horizontal) wantedX else wantedY
        val scrollOrigin = if (layout.horizontal) layout.originX else layout.originY
        val scrollSize = if (layout.horizontal) layout.pageWidth else layout.pageHeight
        val layoutShift = layout.pagesBefore * scrollSize * (factor - 1f)
        val scrollDelta = scrollOrigin + layoutShift - scrollWanted

        val newState = if (layout.horizontal) {
            State(newScale, state.panX, crossPan)
        } else {
            State(newScale, crossPan, state.panY)
        }

        // Where the page under the fingers starts after the gesture: the
        // pull axis is the centred page plus the pan, the scroll axis is the
        // place the list was at, moved by the pages above and by the scroll.
        val crossOrigin = crossCentre - crossSize * factor / 2f + crossPan
        val scrollOriginAfter = scrollOrigin + layoutShift - scrollDelta
        val originX = if (layout.horizontal) scrollOriginAfter else crossOrigin
        val originY = if (layout.horizontal) crossOrigin else scrollOriginAfter

        return Result(newState, scrollDelta, originX, originY)
    }

    /**
     * The point of the page under the fingers, in the page's own space.
     *
     * The page's own space is the space the strokes are normalised to: it
     * does not change when the page is zoomed, so the writing stays on the
     * paper.
     */
    fun contentPoint(layout: Layout, scale: Float, x: Float, y: Float): Pair<Float, Float> {
        if (scale <= 0f) return 0f to 0f
        return (x - layout.originX) / scale to (y - layout.originY) / scale
    }

    /**
     * Where a point of the page's own space is on the screen.
     *
     * The inverse of [contentPoint]: the page is scaled around its top left
     * corner, so a point of the page moves with the zoom.
     */
    fun screenPoint(
        originX: Float,
        originY: Float,
        scale: Float,
        contentX: Float,
        contentY: Float
    ): Pair<Float, Float> =
        (originX + contentX * scale) to (originY + contentY * scale)
}
