package personal.notes.touch.ui.canvas

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The geometry of a reader page: where the page image sits inside the
 * reader's page box, and where the annotation layer of the page sits.
 *
 * The reader's page box is wider than the page image, so the user can write
 * in the margin of an imported PDF - the only free space on a text heavy
 * page. A fixed page (A4 or PDF) keeps its height and its place in the
 * reader: the box is the page plus a quarter of the page's width on the
 * left and on the right, so only the horizontal room is added. An infinite
 * page has no edge at all: its box is the visible canvas and the saved image
 * is drawn inside it at its own size.
 *
 * The writing does not live on the page image - it lives on the annotation
 * layer of the page, which is as wide as the box and as tall as the page.
 * The page (the PDF) stays clean; a stroke in the margin is part of the
 * layer.
 *
 * Everything here is pure, so the mapping can be unit tested without a
 * device.
 */
object ContinuousPageGeometry {

    /**
     * The margin around a fixed page, as a fraction of the page's own width:
     * **a whole page width** on the left and on the right.
     *
     * The margin is horizontal only on purpose. The box then has the height
     * of the page, so the page keeps its place in the reader (the page does
     * not move down when the margin is added) and the mapping between the
     * box (the space the strokes are normalised to) and the layer (the
     * space the ink lives in) is a plain scale on both axes - a stroke
     * cannot change its shape when it is written into the layer.
     */
    const val PAGE_MARGIN = 1.0f

    /**
     * The index of the base annotation layer of a page (layer 1).
     *
     * The reader writes on this layer. The layer stack (layers 2 and 3, and
     * the "flatten to the base" action) comes with the next step; the index
     * lives here so the layers of a page share one naming
     * (`layers/page_<i>_layer_<n>.png`).
     */
    const val BASE_LAYER_INDEX = 1

    /** The aspect ratio (width / height) of a saved A4 page. */
    const val DEFAULT_PAGE_RATIO = 2160f / 3056f

    /** The aspect ratio (width / height) of the saved bitmap of an infinite page. */
    const val INFINITE_PAGE_RATIO = 2160f / 3840f

    /** The width of the saved page image the layer of a page is based on. */
    const val PAGE_WIDTH = 2160

    /**
     * A rectangle in normalised box coordinates (0..1 of the page box).
     *
     * The page image always keeps its aspect ratio, so the rect describes
     * the same shape in the box as the page has in its own pixels.
     */
    data class BoxRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val empty: Boolean get() = width <= 0f || height <= 0f

        /** True when the point (normalised box coordinates) is on the page. */
        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    /** The size of the annotation layer of a page, in pixels. */
    data class LayerSize(val width: Int, val height: Int)

    /** A point in the pixel space of a layer. */
    data class LayerPoint(val x: Float, val y: Float)

    /** True when the note's pages are an endless canvas. */
    fun isInfinite(pageType: String?): Boolean = pageType == "infinite"

    /**
     * The margin around a page, as a fraction of the page's own width: a
     * whole page width on the left and on the right for PDF and infinite
     * pages (the user writes in the margin), and no margin for A4 pages
     * (the page is limited to its A4 size).
     */
    fun marginFor(pageType: String?): Float = if (pageType == "a4") 0.0f else 1.0f

    /** The aspect ratio of the page image of a note type. */
    fun defaultRatio(pageType: String?): Float =
        if (isInfinite(pageType)) INFINITE_PAGE_RATIO else DEFAULT_PAGE_RATIO

    /**
     * The aspect ratio (width / height) of the reader's page box.
     *
     * A fixed page: the page plus the margin on both sides, so the box is
     * wider than the page. An infinite page: the box is the visible canvas,
     * so its shape is the shape of the screen.
     */
    fun boxRatio(pageType: String?, pageRatio: Float): Float {
        val ratio = if (pageRatio > 0f) pageRatio else defaultRatio(pageType)
        // The box of a page is the page: the document fills the screen and the
        // margin of the annotation layer reaches past it - the page is pulled
        // aside to write in it.
        return ratio
    }

    /**
     * The rect of the page image inside the box, in normalised box
     * coordinates.
     *
     * [pageRatio] is the aspect ratio (width / height) of the page image.
     */
    fun pageRect(
        pageType: String?,
        pageRatio: Float,
        boxWidth: Float,
        boxHeight: Float
    ): BoxRect {
        if (boxWidth <= 0f || boxHeight <= 0f) return BoxRect(0f, 0f, 1f, 1f)
        val ratio = if (pageRatio > 0f) pageRatio else defaultRatio(pageType)
        return if (isInfinite(pageType)) {
            infiniteRect(ratio, boxWidth, boxHeight)
        } else {
            fixedRect(ratio, boxWidth, boxHeight)
        }
    }

    /**
     * A fixed page: the full height of the box, centred in it, with a margin
     * of [PAGE_MARGIN] of the page's width on the left and on the right.
     *
     * There is no margin above and below the page: the box has the height of
     * the page, so the page keeps its place in the reader.
     */
    private fun fixedRect(ratio: Float, boxWidth: Float, boxHeight: Float): BoxRect {
        // The page is fitted into the box: it fills the screen, which is what
        // the "reset zoom" has to show. The margin of the annotation layer
        // reaches past the box (see layerRect).
        val scale = min(boxWidth / ratio, boxHeight)
        return centredRect(ratio * scale, scale, boxWidth, boxHeight)
    }

    /**
     * An infinite page: the saved image keeps its own size inside the box
     * (it is fitted into it and centred), so the endless canvas is not
     * bounded by the bitmap that was saved.
     */
    private fun infiniteRect(ratio: Float, boxWidth: Float, boxHeight: Float): BoxRect {
        val scale = min(boxWidth / ratio, boxHeight)
        return centredRect(ratio * scale, scale, boxWidth, boxHeight)
    }

    private fun centredRect(
        width: Float,
        height: Float,
        boxWidth: Float,
        boxHeight: Float
    ): BoxRect {
        val left = (boxWidth - width) / 2f
        val top = (boxHeight - height) / 2f
        return BoxRect(
            left / boxWidth,
            top / boxHeight,
            (left + width) / boxWidth,
            (top + height) / boxHeight
        )
    }

    /**
     * The size of the annotation layer of a page, in pixels.
     *
     * The layer is the page plus a margin on both sides - the room the user
     * writes in. That is the same rule for every page type: an infinite page
     * gets the margin as well, so a stroke that lands outside the saved
     * canvas is kept instead of being clipped (audit C33).
     */
    fun layerSize(
        pageType: String?,
        pageRatio: Float,
        pageWidth: Int = PAGE_WIDTH
    ): LayerSize {
        val ratio = if (pageRatio > 0f) pageRatio else defaultRatio(pageType)
        val width = (pageWidth * (1f + 2f * marginFor(pageType))).roundToInt()
        val height = (pageWidth / ratio).roundToInt().coerceAtLeast(1)
        return LayerSize(width.coerceAtLeast(1), height)
    }

    /**
     * The aspect ratio of the page a layer belongs to, taken from the size of
     * the layer: the layer is wider than its page (the margin on both sides).
     */
    fun ratioOfLayer(pageType: String?, layerWidth: Int, layerHeight: Int): Float {
        if (layerWidth <= 0 || layerHeight <= 0) return defaultRatio(pageType)
        val pageWidth = layerWidth / (1f + 2f * marginFor(pageType))
        return pageWidth / layerHeight
    }

    /**
     * Where the annotation layer sits inside the box, in normalised box
     * coordinates.
     *
     * A fixed page: the layer covers the whole box (the page plus the
     * margin on both sides). An infinite page: the layer is the page image
     * plus its margin, so it reaches past the saved canvas - that is the room
     * the user writes in on an endless canvas.
     */
    fun layerRect(
        pageType: String?,
        pageRatio: Float,
        boxWidth: Float,
        boxHeight: Float
    ): BoxRect {
        val page = pageRect(pageType, pageRatio, boxWidth, boxHeight)
        if (page.empty) return page
        // The layer is the page plus a whole page width of margin on each
        // side, centred on the page. Its page part therefore lands exactly on
        // the page, and the margin reaches past the screen: the page is
        // pulled aside to write in it.
        val grow = marginFor(pageType)
        val width = page.width * (1f + 2f * grow)
        val left = page.left - page.width * grow
        return BoxRect(left, page.top, left + width, page.bottom)
    }

    /**
     * The place of a point of the box in the pixel space of the layer.
     *
     * The strokes are normalised to the box, the layer has its own pixels:
     * the point is mapped through the rect the layer has in the box.
     */
    fun layerPoint(
        rect: BoxRect,
        x: Float,
        y: Float,
        layerWidth: Int,
        layerHeight: Int
    ): LayerPoint {
        if (rect.empty || layerWidth <= 0 || layerHeight <= 0) return LayerPoint(0f, 0f)
        return LayerPoint(
            (x - rect.left) / rect.width * layerWidth,
            (y - rect.top) / rect.height * layerHeight
        )
    }

    /**
     * The bounds of a stroke in normalised box coordinates, nib included.
     *
     * The nib is a fraction of the box's width, so the padding is a fraction
     * of the box's width in x and of the box's height in y - the same
     * geometry the single-page engine uses for its own crops.
     */
    fun strokeBounds(
        points: List<InkPoint>,
        boxWidth: Float,
        boxHeight: Float,
        padFactor: Float,
        extraPx: Float
    ): BoxRect? {
        if (points.isEmpty() || boxWidth <= 0f || boxHeight <= 0f) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var widest = 0f
        for (p in points) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
            if (p.w > widest) widest = p.w
        }
        val pad = widest * boxWidth * padFactor + extraPx
        val padX = pad / boxWidth
        val padY = pad / boxHeight
        return BoxRect(minX - padX, minY - padY, maxX + padX, maxY + padY)
    }
}
