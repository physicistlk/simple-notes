package personal.notes.touch.util

import kotlin.math.roundToInt

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

    /** Default A4 portrait aspect ratio (width / height) used when a page has no valid PDF size. */
    const val DEFAULT_ASPECT = 0.707f

    /**
     * Aspect ratio (width / height) for a page of size [width] × [height].
     * Returns [fallback] when either dimension is non-positive, NaN, or infinite,
     * or when the computed ratio underflows/overflows to a non-finite or zero value.
     */
    fun aspect(width: Float, height: Float, fallback: Float = DEFAULT_ASPECT): Float {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            return fallback
        }
        val ratio = width / height
        return if (ratio.isFinite() && ratio > 0f) ratio else fallback
    }

    /**
     * Export PDF page size in points `(width, height)` for a page with [aspect]
     * (`width / height`), scaled so the longest side equals [longSidePts] (842 pt = A4 long side).
     * Portrait (`aspect <= 1f`) yields `(round(longSidePts * aspect), longSidePts)`;
     * landscape (`aspect > 1f`) yields `(longSidePts, round(longSidePts / aspect))`.
     */
    fun exportPdfPageSize(aspect: Float, longSidePts: Int = 842): Pair<Int, Int> {
        val safeLong = longSidePts.coerceAtLeast(1)
        val safeAspect = if (aspect.isFinite() && aspect > 0f) aspect else DEFAULT_ASPECT
        return if (safeAspect <= 1f) {
            (safeLong * safeAspect).roundToInt().coerceAtLeast(1) to safeLong
        } else {
            safeLong to (safeLong / safeAspect).roundToInt().coerceAtLeast(1)
        }
    }

    /**
     * Thumbnail target dimensions `(width, height)` in pixels for a page with [aspect],
     * bounded so the longest side equals [longSidePx].
     */
    fun thumbnailSize(aspect: Float, longSidePx: Int = 340): Pair<Int, Int> =
        exportPdfPageSize(aspect, longSidePx)

    /** Canvas pixels per PDF point for a page rendered [outW] px wide. */
    fun canvasScale(pageWidthPts: Float, outW: Int): Float {
        if (!pageWidthPts.isFinite() || pageWidthPts <= 0f || outW <= 0) return 1f
        val scale = outW.toFloat() / pageWidthPts
        return if (scale.isFinite() && scale > 0f) scale else 1f
    }

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
    fun canvasToUserPoint(x: Float, y: Float, pageHeightPts: Float, pxPerPt: Float): FloatArray {
        val safeScale = if (pxPerPt.isFinite() && pxPerPt > 0f) pxPerPt else 1f
        val safeX = if (x.isFinite()) x else 0f
        val safeY = if (y.isFinite()) y else 0f
        val safeH = if (pageHeightPts.isFinite()) pageHeightPts else 0f
        val ux = safeX / safeScale
        val uy = safeH - safeY / safeScale
        return floatArrayOf(
            if (ux.isFinite()) ux else 0f,
            if (uy.isFinite()) uy else 0f
        )
    }

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
