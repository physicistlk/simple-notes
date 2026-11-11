package personal.notes.touch.ui.canvas

import kotlin.math.max
import kotlin.math.min

/**
 * The geometry of a reader page: where the page image sits inside the
 * reader's page box, and whether a stroke still lies on the page.
 *
 * The reader's page box is bigger than the page image, so the user can
 * write in the margin of an imported PDF - the only free space on a text
 * heavy page. A fixed page (A4 or PDF) is centred in its box and keeps a
 * margin of [PAGE_MARGIN] around it; an infinite page has no edge at all:
 * its box is the visible canvas and the saved image is drawn inside it at
 * its own size, so the endless canvas is not bounded by the saved bitmap.
 *
 * Everything here is pure, so the mapping can be unit tested without a
 * device.
 */
object ContinuousPageGeometry {

    /**
     * The margin around a fixed page, as a fraction of the page's own size:
     * a quarter of the page's width on the left and on the right, a quarter
     * of the page's height above and below.
     *
     * The margin is proportional on both axes on purpose. The box then
     * keeps the page's aspect ratio, so the mapping between the box (the
     * space the strokes are normalised to) and the page (the space the ink
     * layer lives in) is a plain scale. A margin of a quarter of the page's
     * *width* on all four sides would change the aspect ratio of an A4 page
     * by 11 %, and every stroke would be squashed by 11 % when it is
     * written into the page's pixel space - the live preview would not
     * match the stroke that comes back from the disk.
     */
    const val PAGE_MARGIN = 0.25f

    /** The aspect ratio (width / height) of a saved A4 page. */
    const val DEFAULT_PAGE_RATIO = 2160f / 3056f

    /** The aspect ratio (width / height) of the saved bitmap of an infinite page. */
    const val INFINITE_PAGE_RATIO = 2160f / 3840f

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

    /** The place of a crop in the pixel space of a page. */
    data class CropPlace(val x: Float, val y: Float, val width: Int, val height: Int)

    /** True when the note's pages are an endless canvas. */
    fun isInfinite(pageType: String?): Boolean = pageType == "infinite"

    /** The aspect ratio of the page image of a note type. */
    fun defaultRatio(pageType: String?): Float =
        if (isInfinite(pageType)) INFINITE_PAGE_RATIO else DEFAULT_PAGE_RATIO

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
     * A fixed page: centred in its box, with a margin of [PAGE_MARGIN] of
     * the page's size around it.
     */
    private fun fixedRect(ratio: Float, boxWidth: Float, boxHeight: Float): BoxRect {
        // The page plus its margin, in units of the page's height.
        val totalWidth = ratio * (1f + 2f * PAGE_MARGIN)
        val totalHeight = 1f + 2f * PAGE_MARGIN
        val scale = min(boxWidth / totalWidth, boxHeight / totalHeight)
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

    /** True when the stroke (in normalised box coordinates) lies on the page. */
    fun isInsidePage(rect: BoxRect, bounds: BoxRect): Boolean =
        !rect.empty &&
            bounds.left >= rect.left && bounds.top >= rect.top &&
            bounds.right <= rect.right && bounds.bottom <= rect.bottom

    /**
     * The place of a crop in the pixel space of the page: the bounds of the
     * stroke (normalised box coordinates) are mapped into the page and
     * rounded out. The crop may reach past the page - that is what a margin
     * note is.
     */
    fun cropPlace(
        rect: BoxRect,
        bounds: BoxRect,
        pageWidth: Int,
        pageHeight: Int
    ): CropPlace? {
        if (rect.empty || pageWidth <= 0 || pageHeight <= 0) return null
        val x0 = ((bounds.left - rect.left) / rect.width * pageWidth).toInt()
        val y0 = ((bounds.top - rect.top) / rect.height * pageHeight).toInt()
        val x1 = ((bounds.right - rect.left) / rect.width * pageWidth).toInt()
        val y1 = ((bounds.bottom - rect.top) / rect.height * pageHeight).toInt()
        val width = (x1 - x0).coerceAtLeast(1)
        val height = (y1 - y0).coerceAtLeast(1)
        return CropPlace(x0.toFloat(), y0.toFloat(), width, height)
    }
}
