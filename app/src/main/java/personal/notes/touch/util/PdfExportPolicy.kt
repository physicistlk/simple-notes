package personal.notes.touch.util

import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.ui.canvas.ContinuousPageGeometry

/**
 * Pure-JVM decision policy for vector PDF background export (`PDF-02`) and
 * per-page export page sizing (`PDF-QOL-4`).
 *
 * Kept free of `android.*` imports so every rule is unit-testable directly on
 * the JVM.
 */
object PdfExportPolicy {

    data class PdfPageSource(val pdfSourcePath: String, val pdfPageIndex: Int)

    /**
     * Resolves the vector PDF source for [pageIndex] from [pages] (matching on
     * [PageEntity.index]). Returns `null` when no matching page exists or the
     * page has no valid PDF source.
     */
    fun resolveVectorPageSource(pages: List<PageEntity>, pageIndex: Int): PdfPageSource? {
        val page = pages.firstOrNull { it.index == pageIndex } ?: return null
        if (page.pdfSourcePath.isBlank() || page.pdfPageIndex < 0) return null
        return PdfPageSource(page.pdfSourcePath, page.pdfPageIndex)
    }

    /**
     * True when export should render the live vector PDF background instead of
     * a raster fallback or `background_<i>.png` override.
     */
    fun shouldUseVectorPdfBackground(
        source: PdfPageSource?,
        hasBackgroundOverride: Boolean
    ): Boolean = source != null && !hasBackgroundOverride

    /**
     * Computes export render bitmap dimensions `(width, height)` in pixels for a
     * PDF page sized [pageWidthPts] × [pageHeightPts] points, targeting
     * [baseWidthPx] width while respecting [PdfRenderPolicy] caps.
     */
    fun exportRenderTargetSize(
        pageWidthPts: Float,
        pageHeightPts: Float,
        baseWidthPx: Int = 2160
    ): Pair<Int, Int> {
        if (!pageWidthPts.isFinite() || !pageHeightPts.isFinite() ||
            pageWidthPts <= 0f || pageHeightPts <= 0f || baseWidthPx <= 0
        ) {
            return 2160 to 3056
        }
        val requestedPxPerPt = baseWidthPx.toFloat() / pageWidthPts
        if (!requestedPxPerPt.isFinite()) return 2160 to 3056
        val capped = PdfRenderPolicy.capPxPerPt(pageWidthPts, pageHeightPts, requestedPxPerPt)
        if (capped <= 0f) return 2160 to 3056
        val size = PdfRenderPolicy.outputSize(pageWidthPts, pageHeightPts, capped)
        return size[0] to size[1]
    }

    /**
     * Resolves the annotation layer pixel dimensions for [loadAnnotationLayer].
     * Preserves recorded [tilesWidth] × [tilesHeight] when present, otherwise
     * delegates to [ContinuousPageGeometry.layerSize] using [pageType] and [pageAspect].
     */
    fun resolveAnnotationLayerSize(
        pageType: String?,
        pageAspect: Float,
        tilesWidth: Int,
        tilesHeight: Int
    ): ContinuousPageGeometry.LayerSize {
        if (tilesWidth > 0 && tilesHeight > 0) {
            return ContinuousPageGeometry.LayerSize(tilesWidth, tilesHeight)
        }
        return ContinuousPageGeometry.layerSize(pageType, pageAspect)
    }

    /**
     * Computes exported PDF `PageInfo` dimensions `(widthPts, heightPts)` for one
     * page. Prefers the flattened bitmap's aspect when available, else the PDF
     * page's native point dimensions, else [ContinuousPageGeometry.DEFAULT_PAGE_RATIO].
     */
    fun resolveExportPageSizePts(
        bitmapWidth: Int,
        bitmapHeight: Int,
        pdfPageWidthPts: Float,
        pdfPageHeightPts: Float,
        longSidePts: Int = 842
    ): Pair<Int, Int> {
        val aspect = when {
            bitmapWidth > 0 && bitmapHeight > 0 ->
                PdfPageGeometry.aspect(bitmapWidth.toFloat(), bitmapHeight.toFloat(), ContinuousPageGeometry.DEFAULT_PAGE_RATIO)
            pdfPageWidthPts.isFinite() && pdfPageHeightPts.isFinite() && pdfPageWidthPts > 0f && pdfPageHeightPts > 0f ->
                PdfPageGeometry.aspect(pdfPageWidthPts, pdfPageHeightPts, ContinuousPageGeometry.DEFAULT_PAGE_RATIO)
            else -> ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        }
        return PdfPageGeometry.exportPdfPageSize(aspect, longSidePts)
    }
}
