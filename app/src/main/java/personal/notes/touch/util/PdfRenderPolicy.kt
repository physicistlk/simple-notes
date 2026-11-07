package personal.notes.touch.util

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure render policy for the live vector-PDF background (F-10).
 *
 * The canvas draws a pdfium-rendered bitmap for the current page. Re-rendering
 * on every pinch frame would thrash; instead the bitmap is upgraded only when
 * the zoom level needs more pixels than the current render has (with
 * hysteresis), and the render scale is capped so the bitmap stays within a
 * safe memory budget.
 *
 * Kept separate from the view so the policy is unit-testable on the JVM.
 */
object PdfRenderPolicy {

    /** Longest bitmap side in pixels (pdfium/allocation safety). */
    const val MAX_DIMENSION = 4096

    /**
     * Total pixel budget per background bitmap. ARGB_8888 = 4 bytes/px, so
     * 18M px ≈ 72 MB — high quality 300+ DPI render budget for vector pages.
     */
    const val MAX_PIXELS = 18_000_000L

    /**
     * Area budget for one page in continuous reading mode. Several pages are
     * held at once while scrolling, so the per-page budget is lower than the
     * single-page background budget (12 MPx ≈ 48 MB, ≈ 2900 px wide on A4).
     */
    const val CONTINUOUS_MAX_PIXELS = 12_000_000L

    /** Re-render when the needed scale exceeds current by 15% for smooth vector sharpness. */
    const val HYSTERESIS = 1.15f

    /**
     * True when a render at [neededPxPerPt] would improve on a bitmap
     * rendered at [currentPxPerPt]. Always true before the first render.
     */
    fun shouldRender(neededPxPerPt: Float, currentPxPerPt: Float): Boolean {
        if (neededPxPerPt <= 0f) return false
        if (currentPxPerPt <= 0f) return true
        return neededPxPerPt > currentPxPerPt * HYSTERESIS
    }

    /**
     * Clamps a requested render scale to the bitmap budget: neither side of
     * the **rounded** bitmap exceeds [MAX_DIMENSION] and its area stays within
     * [MAX_PIXELS]. Returns 0 for degenerate pages.
     *
     * The result is verified against [outputSize] rather than the unrounded
     * product: `ceil` can push a bitmap just past the budget (an A4 page at
     * the exact area limit rounds up to 2,378 × 3,365 = 8,001,970 px), so the
     * scale is nudged down until the integer size really fits.
     */
    fun capPxPerPt(
        pageWidthPts: Float,
        pageHeightPts: Float,
        requestedPxPerPt: Float,
        maxPixels: Long = MAX_PIXELS,
        maxDimension: Int = MAX_DIMENSION
    ): Float {
        if (pageWidthPts <= 0f || pageHeightPts <= 0f || requestedPxPerPt <= 0f) return 0f
        val byDimension = min(maxDimension / pageWidthPts, maxDimension / pageHeightPts)
        val byArea = sqrt(
            maxPixels.toDouble() / (pageWidthPts.toDouble() * pageHeightPts.toDouble())
        ).toFloat()
        var scale = min(requestedPxPerPt, min(byDimension, byArea))
        // At most a couple of iterations: the ceil overshoot is sub-pixel, so
        // the first shrink already lands inside the budget.
        repeat(8) {
            val size = outputSize(pageWidthPts, pageHeightPts, scale)
            val area = size[0].toLong() * size[1].toLong()
            val overArea = area > maxPixels
            val longest = maxOf(size[0], size[1])
            val overDimension = longest > maxDimension
            if (!overArea && !overDimension) return scale
            val shrink = min(
                if (overArea) maxPixels.toDouble() / area.toDouble() else 1.0,
                if (overDimension) maxDimension.toDouble() / longest.toDouble() else 1.0
            )
            scale *= (shrink * 0.999).toFloat()
        }
        return if (scale > 0f) scale else 0f
    }

    /**
     * Pixel width for one continuous-reading page that is displayed
     * [desiredWidthPx] wide (already multiplied by the reader's zoom). The
     * height follows the page aspect ratio and both sides are capped by
     * [CONTINUOUS_MAX_PIXELS] / [MAX_DIMENSION]. Returns 0 for degenerate
     * pages.
     */
    fun continuousRenderWidth(
        pageWidthPts: Float,
        pageHeightPts: Float,
        desiredWidthPx: Int
    ): Int {
        if (pageWidthPts <= 0f || pageHeightPts <= 0f || desiredWidthPx <= 0) return 0
        val scale = capPxPerPt(
            pageWidthPts,
            pageHeightPts,
            desiredWidthPx / pageWidthPts,
            CONTINUOUS_MAX_PIXELS
        )
        if (scale <= 0f) return 0
        return outputSize(pageWidthPts, pageHeightPts, scale)[0]
    }

    /** Output bitmap size for a render; both sides ≥ 1. */
    fun outputSize(pageWidthPts: Float, pageHeightPts: Float, pxPerPt: Float): IntArray {
        val width = ceil(pageWidthPts * pxPerPt).toInt().coerceAtLeast(1)
        val height = ceil(pageHeightPts * pxPerPt).toInt().coerceAtLeast(1)
        return intArrayOf(width, height)
    }
}
