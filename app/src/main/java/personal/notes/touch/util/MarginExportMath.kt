package personal.notes.touch.util

import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-JVM crop geometry and margin ink guide/chevron math for `PDF-QOL-6`.
 *
 * Kept free of `android.*` / `androidx.*` imports so every rule is unit-testable
 * directly on the JVM.
 */
data class MarginCropResult(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val cropped: Boolean
)

data class MarginInkPresence(
    val hasLeftMarginInk: Boolean = false,
    val hasRightMarginInk: Boolean = false,
    val leftMinX: Float = 0f,
    val rightMaxX: Float = 1f
) {
    val hasAnyMarginInk: Boolean get() = hasLeftMarginInk || hasRightMarginInk

    companion object {
        val NONE = MarginInkPresence()
    }
}

data class MarginGuideState(
    val showLeftGuide: Boolean,
    val showRightGuide: Boolean,
    val leftRevealedPx: Float,
    val rightRevealedPx: Float,
    val showLeftOffscreenChevron: Boolean,
    val showRightOffscreenChevron: Boolean
)

object MarginExportMath {

    fun supportsMarginExport(pageType: String?): Boolean =
        !pageType.isNullOrBlank() &&
            pageType != "text" &&
            pageType != "infinite" &&
            ContinuousPageGeometry.marginFor(pageType) > 0f

    fun cropRect(
        layerWidth: Int,
        layerHeight: Int,
        @Suppress("UNUSED_PARAMETER") pageAspect: Float,
        margin: Float,
        includeMargins: Boolean
    ): MarginCropResult {
        if (layerWidth <= 0 || layerHeight <= 0) {
            return MarginCropResult(0, 0, 0, 0, 0, 0, cropped = false)
        }
        val safeMargin = if (margin.isFinite() && margin > 0f) margin else 0f
        if (includeMargins || safeMargin <= 0f) {
            return MarginCropResult(
                left = 0,
                top = 0,
                width = layerWidth,
                height = layerHeight,
                outputWidth = layerWidth,
                outputHeight = layerHeight,
                cropped = false
            )
        }
        val span = 1f + 2f * safeMargin
        val width = (layerWidth / span).roundToInt().coerceIn(1, layerWidth)
        val maxLeft = (layerWidth - width).coerceAtLeast(0)
        val left = ((layerWidth - width) / 2f).roundToInt().coerceIn(0, maxLeft)
        val isCropped = left > 0 || width < layerWidth
        return MarginCropResult(
            left = left,
            top = 0,
            width = width,
            height = layerHeight,
            outputWidth = width,
            outputHeight = layerHeight,
            cropped = isCropped
        )
    }

    fun exportPageAspect(pageAspect: Float, margin: Float, includeMargins: Boolean): Float {
        val safeAspect = if (pageAspect.isFinite() && pageAspect > 0f) {
            pageAspect
        } else {
            PdfPageGeometry.DEFAULT_ASPECT
        }
        if (!includeMargins) return safeAspect
        val safeMargin = if (margin.isFinite() && margin > 0f) margin else 0f
        return safeAspect * (1f + 2f * safeMargin)
    }

    fun exportPdfPageSizePts(
        pageAspect: Float,
        margin: Float,
        includeMargins: Boolean,
        longSidePts: Int = 842
    ): Pair<Int, Int> =
        PdfPageGeometry.exportPdfPageSize(
            aspect = exportPageAspect(pageAspect, margin, includeMargins),
            longSidePts = longSidePts
        )

    fun mergePresence(a: MarginInkPresence, b: MarginInkPresence): MarginInkPresence {
        if (!a.hasAnyMarginInk) return b
        if (!b.hasAnyMarginInk) return a
        val hasLeft = a.hasLeftMarginInk || b.hasLeftMarginInk
        val hasRight = a.hasRightMarginInk || b.hasRightMarginInk
        val minLeft = when {
            a.hasLeftMarginInk && b.hasLeftMarginInk -> min(a.leftMinX, b.leftMinX)
            a.hasLeftMarginInk -> a.leftMinX
            b.hasLeftMarginInk -> b.leftMinX
            else -> 0f
        }
        val maxRight = when {
            a.hasRightMarginInk && b.hasRightMarginInk -> max(a.rightMaxX, b.rightMaxX)
            a.hasRightMarginInk -> a.rightMaxX
            b.hasRightMarginInk -> b.rightMaxX
            else -> 1f
        }
        return MarginInkPresence(hasLeft, hasRight, minLeft, maxRight)
    }

    fun detectMarginInkFromNormalizedX(
        xCoords: Iterable<Float>,
        margin: Float
    ): MarginInkPresence {
        if (!margin.isFinite() || margin <= 0f) return MarginInkPresence.NONE
        var hasLeft = false
        var hasRight = false
        var minLeft = 0f
        var maxRight = 1f
        for (x in xCoords) {
            if (!x.isFinite()) continue
            if (x < 0f) {
                hasLeft = true
                val clamped = x.coerceAtLeast(-margin)
                if (clamped < minLeft) minLeft = clamped
            } else if (x > 1f) {
                hasRight = true
                val clamped = x.coerceAtMost(1f + margin)
                if (clamped > maxRight) maxRight = clamped
            }
        }
        return if (!hasLeft && !hasRight) {
            MarginInkPresence.NONE
        } else {
            MarginInkPresence(hasLeft, hasRight, minLeft, maxRight)
        }
    }

    /** Pixel X in layer space where the page starts/ends (`[pageLeftPx, pageRightPx)`). */
    fun layerPagePixelBounds(layerWidth: Int, margin: Float): Pair<Int, Int> {
        if (layerWidth <= 0) return 0 to 0
        if (!margin.isFinite() || margin <= 0f) return 0 to layerWidth
        val span = 1f + 2f * margin
        val pageLeft = floor(layerWidth * (margin / span)).toInt().coerceIn(0, layerWidth)
        val pageRight = ceil(layerWidth * ((margin + 1f) / span)).toInt().coerceIn(pageLeft, layerWidth)
        return pageLeft to pageRight
    }

    /**
     * Local pixel X end `[0, end)` inside tile column [tx] that falls in the left margin.
     * Returns `0` if tile [tx] does not overlap the left margin.
     */
    fun tileLeftMarginEndLocalPx(tx: Int, layerWidth: Int, tileSize: Int, margin: Float): Int {
        if (tx < 0 || layerWidth <= 0 || tileSize <= 0 || !margin.isFinite() || margin <= 0f) return 0
        val tLeft = tx * tileSize
        if (tLeft >= layerWidth) return 0
        val tRight = min(layerWidth, tLeft + tileSize)
        val (pageLeft, _) = layerPagePixelBounds(layerWidth, margin)
        return (min(tRight, pageLeft) - tLeft).coerceAtLeast(0)
    }

    /**
     * Local pixel X start `[start, tileWidth)` inside tile column [tx] that falls in the right margin.
     * Returns `tileWidth` (or `tileSize` if out of bounds) when tile [tx] does not overlap the right margin.
     */
    fun tileRightMarginStartLocalPx(tx: Int, layerWidth: Int, tileSize: Int, margin: Float): Int {
        if (tx < 0 || layerWidth <= 0 || tileSize <= 0 || !margin.isFinite() || margin <= 0f) {
            return tileSize.coerceAtLeast(0)
        }
        val tLeft = tx * tileSize
        if (tLeft >= layerWidth) return tileSize.coerceAtLeast(0)
        val tRight = min(layerWidth, tLeft + tileSize)
        val tWidth = tRight - tLeft
        val (_, pageRight) = layerPagePixelBounds(layerWidth, margin)
        return (max(tLeft, pageRight) - tLeft).coerceIn(0, tWidth)
    }

    /** Normalized box X span `[normLeft, normRight]` of tile column [tx] (page is `[0f, 1f]`). */
    fun tileNormalizedXSpan(tx: Int, layerWidth: Int, tileSize: Int, margin: Float): Pair<Float, Float>? {
        if (tx < 0 || layerWidth <= 0 || tileSize <= 0 || !margin.isFinite() || margin < 0f) return null
        val tLeft = tx * tileSize
        if (tLeft >= layerWidth) return null
        val tRight = min(layerWidth, tLeft + tileSize)
        val span = 1f + 2f * margin
        val normLeft = (tLeft.toFloat() / layerWidth) * span - margin
        val normRight = (tRight.toFloat() / layerWidth) * span - margin
        return normLeft to normRight
    }

    fun hasHorizontalPanOrZoomReveal(
        continuousPanX: Float,
        continuousScale: Float,
        panThresholdPx: Float = 2f,
        zoomOutThreshold: Float = 0.99f
    ): Boolean {
        val panned = continuousPanX.isFinite() && kotlin.math.abs(continuousPanX) > panThresholdPx
        val zoomedOut = continuousScale.isFinite() && continuousScale > 0f && continuousScale < zoomOutThreshold
        return panned || zoomedOut
    }

    fun computeGuideState(
        pageType: String?,
        pageLeftInViewportPx: Float,
        pageWidthPx: Float,
        viewportWidthPx: Float,
        presence: MarginInkPresence,
        revealThresholdPx: Float = 2f,
        allowLeftPanReveal: Boolean = true,
        allowRightPanReveal: Boolean = true
    ): MarginGuideState {
        if (!supportsMarginExport(pageType)) {
            return MarginGuideState(false, false, 0f, 0f, false, false)
        }
        val margin = ContinuousPageGeometry.marginFor(pageType)
        if (margin <= 0f || !pageLeftInViewportPx.isFinite() ||
            !pageWidthPx.isFinite() || pageWidthPx <= 0f ||
            !viewportWidthPx.isFinite() || viewportWidthPx <= 0f
        ) {
            return MarginGuideState(false, false, 0f, 0f, false, false)
        }
        val pageRightInViewportPx = pageLeftInViewportPx + pageWidthPx
        val leftRevealedPx = pageLeftInViewportPx.coerceAtLeast(0f)
        val rightRevealedPx = (viewportWidthPx - pageRightInViewportPx).coerceAtLeast(0f)
        val showLeftGuide = (allowLeftPanReveal && leftRevealedPx > revealThresholdPx) || presence.hasLeftMarginInk
        val showRightGuide = (allowRightPanReveal && rightRevealedPx > revealThresholdPx) || presence.hasRightMarginInk

        val showLeftChevron = if (!presence.hasLeftMarginInk) {
            false
        } else if (presence.leftMinX < 0f) {
            (pageLeftInViewportPx + presence.leftMinX * pageWidthPx) < -0.5f
        } else {
            pageLeftInViewportPx <= 0.5f
        }

        val showRightChevron = if (!presence.hasRightMarginInk) {
            false
        } else if (presence.rightMaxX > 1f) {
            (pageLeftInViewportPx + presence.rightMaxX * pageWidthPx) > viewportWidthPx + 0.5f
        } else {
            pageRightInViewportPx >= viewportWidthPx - 0.5f
        }

        return MarginGuideState(
            showLeftGuide = showLeftGuide,
            showRightGuide = showRightGuide,
            leftRevealedPx = leftRevealedPx,
            rightRevealedPx = rightRevealedPx,
            showLeftOffscreenChevron = showLeftChevron,
            showRightOffscreenChevron = showRightChevron
        )
    }
}
