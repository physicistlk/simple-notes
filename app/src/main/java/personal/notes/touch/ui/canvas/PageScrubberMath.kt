package personal.notes.touch.ui.canvas

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-JVM math for the right-edge capsule page scrubber (`PDF-QOL-5`).
 */
object PageScrubberMath {

    const val MIN_PAGES_FOR_SCRUBBER = 6

    fun isScrubberEnabled(totalPages: Int): Boolean = totalPages >= MIN_PAGES_FOR_SCRUBBER

    fun clampFraction(fraction: Float): Float {
        if (fraction.isNaN()) return 0f
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        return fraction
    }

    /** Maps normalized scrub [fraction] (`0f..1f`) monotonically to `0 until totalPages`. */
    fun scrubFractionToPage(fraction: Float, totalPages: Int): Int {
        if (totalPages <= 1) return 0
        val clamped = clampFraction(fraction)
        return (clamped * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
    }

    /** Inverse of [scrubFractionToPage] for any valid [pageIndex]. */
    fun pageToScrubFraction(pageIndex: Int, totalPages: Int): Float {
        if (totalPages <= 1) return 0f
        return pageIndex.coerceIn(0, totalPages - 1).toFloat() / (totalPages - 1).toFloat()
    }

    /**
     * Converts a pointer drag delta `(deltaX, deltaY)` into a normalized fraction step
     * respecting [pageTurningDirection] (`0` = vertical, `1` = horizontal / capsule axis).
     */
    fun dragDeltaToFractionStep(
        deltaX: Float,
        deltaY: Float,
        pageTurningDirection: Int,
        trackSpanPx: Float
    ): Float {
        if (!trackSpanPx.isFinite() || trackSpanPx <= 0f || !deltaX.isFinite() || !deltaY.isFinite()) {
            return 0f
        }
        val delta = if (pageTurningDirection == 1) {
            if (abs(deltaX) > abs(deltaY)) deltaX else deltaY
        } else {
            deltaY
        }
        return delta / trackSpanPx
    }

    fun applyScrubDelta(
        currentFraction: Float,
        deltaX: Float,
        deltaY: Float,
        pageTurningDirection: Int,
        trackSpanPx: Float
    ): Float {
        val base = clampFraction(currentFraction)
        val step = dragDeltaToFractionStep(deltaX, deltaY, pageTurningDirection, trackSpanPx)
        return clampFraction(base + step)
    }
}
