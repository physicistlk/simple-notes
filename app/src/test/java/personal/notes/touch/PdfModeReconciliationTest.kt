package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.util.PdfModeReconciliationPolicy

/**
 * Pure-JVM unit tests for `PDF-01` (Continuous <-> Single-Page stroke preservation)
 * and `PDF-07` (Unified layer system reconciliation), invoking production code directly.
 */
class PdfModeReconciliationTest {

    @Test
    fun shouldLoadAnnotationOverlay_trueForPdfAndA4_falseForInfiniteAndText() {
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("pdf", isVectorPdf = true))
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("pdf", isVectorPdf = false))
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("a4", isVectorPdf = false))
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("a4", isVectorPdf = true))
        assertTrue(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay(null, isVectorPdf = true))
        assertFalse(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("infinite", isVectorPdf = false))
        assertFalse(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay("text", isVectorPdf = false))
        assertFalse(PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay(null, isVectorPdf = false))
    }

    @Test
    fun singlePageCropFractions_returnsCenterThirdForPdf_fullWidthForA4() {
        val (pdfLeft, pdfRight) = PdfModeReconciliationPolicy.singlePageCropFractions("pdf")
        assertEquals(1f / 3f, pdfLeft, 1e-4f)
        assertEquals(2f / 3f, pdfRight, 1e-4f)

        val (a4Left, a4Right) = PdfModeReconciliationPolicy.singlePageCropFractions("a4")
        assertEquals(0f, a4Left, 1e-5f)
        assertEquals(1f, a4Right, 1e-5f)
    }

    @Test
    fun fitLayerPageFraction_matchesMarginGeometryForPdfA4AndInfinite() {
        assertEquals(
            1f / (1f + 2f * ContinuousPageGeometry.marginFor("pdf")),
            PdfModeReconciliationPolicy.fitLayerPageFraction("pdf"),
            1e-5f
        )
        assertEquals(1f / 3f, PdfModeReconciliationPolicy.fitLayerPageFraction("pdf"), 1e-5f)
        assertEquals(1f, PdfModeReconciliationPolicy.fitLayerPageFraction("a4"), 1e-5f)
        assertEquals(1f, PdfModeReconciliationPolicy.fitLayerPageFraction("infinite"), 1e-5f)
    }

    @Test
    fun pagePixelSize_preservesA4AndAdaptsLandscapePdf() {
        val (a4W, a4H) = PdfModeReconciliationPolicy.pagePixelSize(ContinuousPageGeometry.DEFAULT_PAGE_RATIO)
        assertEquals(2160, a4W)
        assertEquals(3056, a4H)

        val (landW, landH) = PdfModeReconciliationPolicy.pagePixelSize(16f / 9f)
        assertEquals(2160, landW)
        assertEquals(1215, landH)

        val (fallbackW, fallbackH) = PdfModeReconciliationPolicy.pagePixelSize(Float.NaN)
        assertEquals(2160, fallbackW)
        assertTrue(fallbackH in 3055..3056)
    }

    @Test
    fun continuousPdfRender_excludesBakedSinglePageInk_andRespectsBaseLayerVisibility() {
        assertFalse(PdfModeReconciliationPolicy.continuousPdfRenderIncludesInk())
        assertTrue(
            PdfModeReconciliationPolicy.shouldDrawSinglePageStrokes(
                hasStrokes = true,
                baseLayerVisible = true
            )
        )
        assertFalse(
            PdfModeReconciliationPolicy.shouldDrawSinglePageStrokes(
                hasStrokes = true,
                baseLayerVisible = false
            )
        )
        assertFalse(
            PdfModeReconciliationPolicy.shouldDrawSinglePageStrokes(
                hasStrokes = false,
                baseLayerVisible = true
            )
        )
    }

    @Test
    fun resolveMergeTarget_selectsStrokesFileForVectorPdf_andPageBitmapForA4() {
        assertEquals(
            PdfModeReconciliationPolicy.MergeTarget.STROKES_FILE,
            PdfModeReconciliationPolicy.resolveMergeTarget(isVectorPdf = true)
        )
        assertEquals(
            PdfModeReconciliationPolicy.MergeTarget.PAGE_BITMAP,
            PdfModeReconciliationPolicy.resolveMergeTarget(isVectorPdf = false)
        )
    }

    @Test
    fun resolveLayerEntryTarget_unifiesPdfAndA4_andKeepsInfiniteDedicated() {
        assertEquals(
            PdfModeReconciliationPolicy.LayerEntryTarget.UNIFIED_SHEET,
            PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote = false)
        )
        assertEquals(
            PdfModeReconciliationPolicy.LayerEntryTarget.INFINITE_DRAWING_ONLY,
            PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote = true)
        )
    }

    @Test
    fun planLayerVisibility_ordersAndFiltersDrawingAndFloatingLayersAcrossModes() {
        val stack = PageLayerStack.base()
        stack.add() // Layer2
        stack.add() // Layer3
        stack.setVisible(2, false)

        val planVisibleBase = PdfModeReconciliationPolicy.planLayerVisibility(
            stack = stack,
            hasSinglePageStrokes = true,
            floatingLayersVisible = listOf(true, false, true)
        )
        assertTrue(planVisibleBase.showSinglePageStrokes)
        assertEquals(listOf(1, 3), planVisibleBase.visibleDrawingLayerIndices)
        assertEquals(listOf(0, 2), planVisibleBase.visibleFloatingLayerIndices)

        stack.setVisible(PageLayerStack.BASE_LAYER_INDEX, false)
        val planHiddenBase = PdfModeReconciliationPolicy.planLayerVisibility(
            stack = stack,
            hasSinglePageStrokes = true,
            floatingLayersVisible = listOf(false, true)
        )
        assertFalse(planHiddenBase.showSinglePageStrokes)
        assertEquals(listOf(3), planHiddenBase.visibleDrawingLayerIndices)
        assertEquals(listOf(1), planHiddenBase.visibleFloatingLayerIndices)
    }

    @Test
    fun pageLayerStackSelect_roundTripsCurrentIndexViaPageLayerMeta() {
        val stack = PageLayerStack.base()
        stack.add() // Layer2 (currentIndex = 2)
        assertTrue(stack.select(1))
        assertEquals(1, stack.currentIndex)

        val encoded = PageLayerMeta.encode(stack)
        val decoded = PageLayerMeta.decode(encoded)
        assertNotNull(decoded)
        assertEquals(1, decoded?.currentIndex)

        assertTrue(stack.select(2))
        val encoded2 = PageLayerMeta.encode(stack)
        val decoded2 = PageLayerMeta.decode(encoded2)
        assertEquals(2, decoded2?.currentIndex)
    }
}
