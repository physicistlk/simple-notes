package personal.notes.touch.util

import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.PageLayerStack
import kotlin.math.roundToInt

/**
 * Pure-JVM policy for Continuous <-> Single-Page stroke preservation (`PDF-01`)
 * and unified layer system reconciliation (`PDF-07`).
 */
object PdfModeReconciliationPolicy {

    enum class MergeTarget {
        STROKES_FILE,
        PAGE_BITMAP
    }

    enum class LayerEntryTarget {
        UNIFIED_SHEET,
        INFINITE_DRAWING_ONLY
    }

    data class LayerRenderPlan(
        val showSinglePageStrokes: Boolean,
        val visibleDrawingLayerIndices: List<Int>,
        val visibleFloatingLayerIndices: List<Int>
    )

    fun shouldLoadAnnotationOverlay(pageType: String?, isVectorPdf: Boolean): Boolean =
        pageType == "a4" || pageType == "pdf" || isVectorPdf

    fun continuousPdfRenderIncludesInk(): Boolean = false

    fun shouldDrawSinglePageStrokes(hasStrokes: Boolean, baseLayerVisible: Boolean): Boolean =
        hasStrokes && baseLayerVisible

    fun singlePageCropFractions(pageType: String?): Pair<Float, Float> {
        val margin = ContinuousPageGeometry.marginFor(pageType)
        val span = 1f + 2f * margin
        return (margin / span) to ((margin + 1f) / span)
    }

    fun fitLayerPageFraction(pageType: String?): Float =
        if (ContinuousPageGeometry.isInfinite(pageType)) {
            1f
        } else {
            1f / (1f + 2f * ContinuousPageGeometry.marginFor(pageType))
        }

    fun pagePixelSize(
        pageAspect: Float,
        baseWidth: Int = ContinuousPageGeometry.PAGE_WIDTH
    ): Pair<Int, Int> {
        val safeW = baseWidth.coerceAtLeast(1)
        val safeAspect = if (pageAspect.isFinite() && pageAspect > 0f) {
            pageAspect
        } else {
            PdfPageGeometry.DEFAULT_ASPECT
        }
        val h = (safeW / safeAspect).roundToInt().coerceIn(1, 4096)
        return safeW to h
    }

    fun resolveMergeTarget(isVectorPdf: Boolean): MergeTarget =
        if (isVectorPdf) MergeTarget.STROKES_FILE else MergeTarget.PAGE_BITMAP

    fun resolveLayerEntryTarget(isInfiniteNote: Boolean): LayerEntryTarget =
        if (isInfiniteNote) LayerEntryTarget.INFINITE_DRAWING_ONLY else LayerEntryTarget.UNIFIED_SHEET

    fun planLayerVisibility(
        stack: PageLayerStack,
        hasSinglePageStrokes: Boolean,
        floatingLayersVisible: List<Boolean>
    ): LayerRenderPlan {
        val baseVisible = stack.layerAt(PageLayerStack.BASE_LAYER_INDEX)?.visible != false
        val showSingle = shouldDrawSinglePageStrokes(hasSinglePageStrokes, baseVisible)
        val visibleDrawing = stack.visibleLayers().map { it.index }
        val visibleFloating = floatingLayersVisible.mapIndexedNotNull { idx, visible ->
            if (visible) idx else null
        }
        return LayerRenderPlan(
            showSinglePageStrokes = showSingle,
            visibleDrawingLayerIndices = visibleDrawing,
            visibleFloatingLayerIndices = visibleFloating
        )
    }
}
