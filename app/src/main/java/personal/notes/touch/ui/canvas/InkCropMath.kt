package personal.notes.touch.ui.canvas

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry of the ink crops the canvas keeps outside its page bitmap.
 *
 * The page's ink bitmap covers the paper only, so ink that belongs to a layer
 * or that is written past the edge of the paper is kept as a small
 * transparent crop. The numbers are pure so they can be unit-tested without
 * a device.
 */
object InkCropMath {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Bounding box of a stroke in page pixels.
     *
     * [widest] is the largest nib width of the stroke; [padFactor] scales it
     * (the highlighter paints a band that is much wider than the nib) and
     * [extra] adds a couple of pixels for the round cap.
     */
    fun strokeBounds(
        xs: FloatArray,
        ys: FloatArray,
        widest: Float,
        padFactor: Float = 0.5f,
        extra: Float = 6f
    ): Box? {
        if (xs.isEmpty() || xs.size != ys.size) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in xs.indices) {
            minX = min(minX, xs[i]); maxX = max(maxX, xs[i])
            minY = min(minY, ys[i]); maxY = max(maxY, ys[i])
        }
        val pad = widest * padFactor + extra
        val left = floor(minX - pad).toInt()
        val top = floor(minY - pad).toInt()
        val right = ceil(maxX + pad).toInt()
        val bottom = ceil(maxY + pad).toInt()
        return Box(left, top, max(right, left + 1), max(bottom, top + 1))
    }

    /** True when the box lies completely on the paper. */
    fun isOnPaper(box: Box, paperWidth: Int, paperHeight: Int): Boolean =
        box.left >= 0 && box.top >= 0 && box.right <= paperWidth && box.bottom <= paperHeight

    /** True when the box reaches past the edge of the paper. */
    fun isOffPaper(box: Box, paperWidth: Int, paperHeight: Int): Boolean =
        !isOnPaper(box, paperWidth, paperHeight)

    /**
     * True when a crop of [cropWidth] x [cropHeight] pixels placed at
     * ([left], [top]) can hold a stroke that reaches [strokeRight] /
     * [strokeBottom] — i.e. the crop is big enough for the whole mark.
     */
    fun cropCoversStroke(
        left: Int,
        top: Int,
        cropWidth: Int,
        cropHeight: Int,
        strokeRight: Int,
        strokeBottom: Int
    ): Boolean = left <= 0 && top <= 0 &&
        left + cropWidth >= strokeRight && top + cropHeight >= strokeBottom
}
