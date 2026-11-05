package personal.notes.touch.util

/**
 * Pure coordinate math for PDF pages (F-10), kept JVM-testable.
 *
 * Two spaces are involved:
 *  - **PDF user space** (what pdfium returns): origin at the page's
 *    BOTTOM-left, y grows upward. Text boxes are `{left, top, right, bottom}`
 *    with `top > bottom`.
 *  - **Canvas space** (what the drawing view paints): origin at the page's
 *    TOP-left, y grows downward, unit = rendered pixels of the page bitmap.
 *
 * A page rendered [outW] px wide from a [pageWidthPts]-pt page maps user
 * space to canvas space with the factor [canvasScale].
 */
object PdfPageGeometry {

    /** Canvas pixels per PDF point for a page rendered [outW] px wide. */
    fun canvasScale(pageWidthPts: Float, outW: Int): Float =
        if (pageWidthPts > 0f && outW > 0) outW.toFloat() / pageWidthPts else 1f

    /**
     * Converts a user-space rect `{left, top, right, bottom}` (y up) into a
     * canvas-space rect `{left, top, right, bottom}` (y down, pixels).
     * Output is normalized so `top < bottom` and `left < right`.
     */
    fun userRectToCanvas(rect: FloatArray, pageHeightPts: Float, pxPerPt: Float): FloatArray {
        val left = rect[0] * pxPerPt
        val right = rect[2] * pxPerPt
        val first = (pageHeightPts - rect[1]) * pxPerPt
        val second = (pageHeightPts - rect[3]) * pxPerPt
        return floatArrayOf(
            minOf(left, right),
            minOf(first, second),
            maxOf(left, right),
            maxOf(first, second)
        )
    }

    /** Canvas-space point → PDF user-space point (y up). */
    fun canvasToUserPoint(x: Float, y: Float, pageHeightPts: Float, pxPerPt: Float): FloatArray =
        floatArrayOf(x / pxPerPt, pageHeightPts - y / pxPerPt)

    /**
     * Bounds of a set of canvas-space rects as `{left, top, right, bottom}`;
     * null when [rects] is empty.
     */
    fun unionRect(rects: List<FloatArray>): FloatArray? {
        if (rects.isEmpty()) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (r in rects) {
            left = minOf(left, r[0])
            top = minOf(top, r[1])
            right = maxOf(right, r[2])
            bottom = maxOf(bottom, r[3])
        }
        return floatArrayOf(left, top, right, bottom)
    }
}
