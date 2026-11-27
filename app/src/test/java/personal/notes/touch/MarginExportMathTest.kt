package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.util.MarginCropResult
import personal.notes.touch.util.MarginExportMath
import personal.notes.touch.util.MarginInkPresence

/**
 * Pure-JVM unit tests for `PDF-QOL-6` (`MarginExportMath` crop geometry,
 * export sizing, normalized margin ink detection, tile margin spans, and
 * guide/chevron visibility state).
 */
class MarginExportMathTest {

    @Test
    fun supportsMarginExport_enablesOnlyFixedPagesWithMargins() {
        assertTrue(MarginExportMath.supportsMarginExport("pdf"))
        assertTrue(MarginExportMath.supportsMarginExport("image_bg"))
        assertFalse(MarginExportMath.supportsMarginExport("a4"))
        assertFalse(MarginExportMath.supportsMarginExport("infinite"))
        assertFalse(MarginExportMath.supportsMarginExport("text"))
        assertFalse(MarginExportMath.supportsMarginExport(null))
        assertFalse(MarginExportMath.supportsMarginExport(""))
    }

    @Test
    fun cropRect_cropsPdfToCenteredPageWhenIncludeMarginsFalse() {
        val portrait = MarginExportMath.cropRect(
            layerWidth = 6480,
            layerHeight = 3056,
            pageAspect = ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            margin = 1.0f,
            includeMargins = false
        )
        assertEquals(
            MarginCropResult(
                left = 2160,
                top = 0,
                width = 2160,
                height = 3056,
                outputWidth = 2160,
                outputHeight = 3056,
                cropped = true
            ),
            portrait
        )

        val landscape = MarginExportMath.cropRect(
            layerWidth = 6480,
            layerHeight = 1215,
            pageAspect = 16f / 9f,
            margin = 1.0f,
            includeMargins = false
        )
        assertEquals(
            MarginCropResult(
                left = 2160,
                top = 0,
                width = 2160,
                height = 1215,
                outputWidth = 2160,
                outputHeight = 1215,
                cropped = true
            ),
            landscape
        )

        val square = MarginExportMath.cropRect(
            layerWidth = 6480,
            layerHeight = 2160,
            pageAspect = 1f,
            margin = 1.0f,
            includeMargins = false
        )
        assertEquals(
            MarginCropResult(
                left = 2160,
                top = 0,
                width = 2160,
                height = 2160,
                outputWidth = 2160,
                outputHeight = 2160,
                cropped = true
            ),
            square
        )
    }

    @Test
    fun cropRect_preservesFullLayerWhenIncludeMarginsTrueOrMarginZero() {
        val pdfFull = MarginExportMath.cropRect(
            layerWidth = 6480,
            layerHeight = 3056,
            pageAspect = ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            margin = 1.0f,
            includeMargins = true
        )
        assertEquals(
            MarginCropResult(
                left = 0,
                top = 0,
                width = 6480,
                height = 3056,
                outputWidth = 6480,
                outputHeight = 3056,
                cropped = false
            ),
            pdfFull
        )

        val a4False = MarginExportMath.cropRect(
            layerWidth = 2160,
            layerHeight = 3056,
            pageAspect = ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            margin = 0.0f,
            includeMargins = false
        )
        val a4True = MarginExportMath.cropRect(
            layerWidth = 2160,
            layerHeight = 3056,
            pageAspect = ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            margin = 0.0f,
            includeMargins = true
        )
        val expectedA4 = MarginCropResult(0, 0, 2160, 3056, 2160, 3056, cropped = false)
        assertEquals(expectedA4, a4False)
        assertEquals(expectedA4, a4True)
    }

    @Test
    fun cropRect_handlesDegenerateAndNonFiniteInputsSafely() {
        assertEquals(
            MarginCropResult(0, 0, 0, 0, 0, 0, cropped = false),
            MarginExportMath.cropRect(0, 3056, 0.707f, 1.0f, false)
        )
        assertEquals(
            MarginCropResult(0, 0, 0, 0, 0, 0, cropped = false),
            MarginExportMath.cropRect(6480, -10, 0.707f, 1.0f, true)
        )
        val nonFiniteMargin = MarginExportMath.cropRect(
            layerWidth = 2160,
            layerHeight = 3056,
            pageAspect = Float.NaN,
            margin = Float.POSITIVE_INFINITY,
            includeMargins = false
        )
        assertEquals(MarginCropResult(0, 0, 2160, 3056, 2160, 3056, cropped = false), nonFiniteMargin)

        val negativeMargin = MarginExportMath.cropRect(
            layerWidth = 2160,
            layerHeight = 3056,
            pageAspect = -1f,
            margin = -0.5f,
            includeMargins = false
        )
        assertEquals(MarginCropResult(0, 0, 2160, 3056, 2160, 3056, cropped = false), negativeMargin)
    }

    @Test
    fun exportPageAspectAndPdfPageSizePts_matchPageOnlyAndIncludeMargins() {
        val a4Aspect = ContinuousPageGeometry.DEFAULT_PAGE_RATIO
        assertEquals(a4Aspect, MarginExportMath.exportPageAspect(a4Aspect, 1.0f, false), 0.0001f)
        assertEquals(a4Aspect * 3f, MarginExportMath.exportPageAspect(a4Aspect, 1.0f, true), 0.0001f)
        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(a4Aspect, 1.0f, false))
        assertEquals(842 to 397, MarginExportMath.exportPdfPageSizePts(a4Aspect, 1.0f, true))

        val wideAspect = 16f / 9f
        assertEquals(842 to 474, MarginExportMath.exportPdfPageSizePts(wideAspect, 1.0f, false))
        assertEquals(842 to 158, MarginExportMath.exportPdfPageSizePts(wideAspect, 1.0f, true))

        // A4 note (margin = 0) stays 595 x 842 regardless of includeMargins flag
        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(a4Aspect, 0.0f, false))
        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(a4Aspect, 0.0f, true))

        // Invalid aspect falls back cleanly to DEFAULT_ASPECT
        assertEquals(595 to 842, MarginExportMath.exportPdfPageSizePts(Float.NaN, 1.0f, false))
    }

    @Test
    fun detectMarginInkFromNormalizedX_distinguishesPageBoundsAndMargins() {
        assertEquals(
            MarginInkPresence.NONE,
            MarginExportMath.detectMarginInkFromNormalizedX(listOf(0f, 0.5f, 1f), margin = 1.0f)
        )
        assertEquals(
            MarginInkPresence.NONE,
            MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.4f, 1.5f), margin = 0.0f)
        )

        val both = MarginExportMath.detectMarginInkFromNormalizedX(
            listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.25f, -1.8f, 0.5f, 1.15f, 2.5f),
            margin = 1.0f
        )
        assertTrue(both.hasLeftMarginInk)
        assertTrue(both.hasRightMarginInk)
        assertTrue(both.hasAnyMarginInk)
        assertEquals(-1.0f, both.leftMinX, 0.0001f)
        assertEquals(2.0f, both.rightMaxX, 0.0001f)

        val leftOnly = MarginExportMath.detectMarginInkFromNormalizedX(listOf(-0.35f, 0.2f), margin = 1.0f)
        assertTrue(leftOnly.hasLeftMarginInk)
        assertFalse(leftOnly.hasRightMarginInk)
        assertEquals(-0.35f, leftOnly.leftMinX, 0.0001f)
    }

    @Test
    fun mergePresence_combinesSidesAndExtremeBounds() {
        val left = MarginInkPresence(hasLeftMarginInk = true, leftMinX = -0.2f)
        val right = MarginInkPresence(hasRightMarginInk = true, rightMaxX = 1.4f)
        val deeperLeft = MarginInkPresence(hasLeftMarginInk = true, leftMinX = -0.8f)

        assertEquals(left, MarginExportMath.mergePresence(MarginInkPresence.NONE, left))
        assertEquals(right, MarginExportMath.mergePresence(right, MarginInkPresence.NONE))

        val merged = MarginExportMath.mergePresence(MarginExportMath.mergePresence(left, right), deeperLeft)
        assertTrue(merged.hasLeftMarginInk)
        assertTrue(merged.hasRightMarginInk)
        assertEquals(-0.8f, merged.leftMinX, 0.0001f)
        assertEquals(1.4f, merged.rightMaxX, 0.0001f)
    }

    @Test
    fun tileMarginBoundariesAndNormalizedSpans_match6480PdfLayerGrid() {
        val layerW = 6480
        val tileSize = 1024
        val margin = 1.0f

        assertEquals(2160 to 4320, MarginExportMath.layerPagePixelBounds(layerW, margin))
        assertEquals(0 to 0, MarginExportMath.layerPagePixelBounds(0, margin))

        // tx = 0, 1: purely left margin
        assertEquals(1024, MarginExportMath.tileLeftMarginEndLocalPx(0, layerW, tileSize, margin))
        assertEquals(1024, MarginExportMath.tileLeftMarginEndLocalPx(1, layerW, tileSize, margin))
        // tx = 2: [2048, 3072) -> left margin [2048, 2160) => 112 local px
        assertEquals(112, MarginExportMath.tileLeftMarginEndLocalPx(2, layerW, tileSize, margin))
        assertEquals(1024, MarginExportMath.tileRightMarginStartLocalPx(2, layerW, tileSize, margin))
        // tx = 3: [3072, 4096) -> purely on page
        assertEquals(0, MarginExportMath.tileLeftMarginEndLocalPx(3, layerW, tileSize, margin))
        assertEquals(1024, MarginExportMath.tileRightMarginStartLocalPx(3, layerW, tileSize, margin))
        // tx = 4: [4096, 5120) -> right margin (4320, 5120) => starts at local 224 px
        assertEquals(0, MarginExportMath.tileLeftMarginEndLocalPx(4, layerW, tileSize, margin))
        assertEquals(224, MarginExportMath.tileRightMarginStartLocalPx(4, layerW, tileSize, margin))
        // tx = 5, 6: purely right margin
        assertEquals(0, MarginExportMath.tileRightMarginStartLocalPx(5, layerW, tileSize, margin))
        assertEquals(0, MarginExportMath.tileRightMarginStartLocalPx(6, layerW, tileSize, margin))
        // tx = 7: out of bounds -> empty
        assertEquals(0, MarginExportMath.tileLeftMarginEndLocalPx(7, layerW, tileSize, margin))
        assertEquals(tileSize, MarginExportMath.tileRightMarginStartLocalPx(7, layerW, tileSize, margin))

        val span0 = MarginExportMath.tileNormalizedXSpan(0, layerW, tileSize, margin)
        assertEquals(-1.0f, span0?.first ?: Float.NaN, 0.0001f)
        val span6 = MarginExportMath.tileNormalizedXSpan(6, layerW, tileSize, margin)
        assertEquals(2.0f, span6?.second ?: Float.NaN, 0.0001f)
        assertNull(MarginExportMath.tileNormalizedXSpan(7, layerW, tileSize, margin))
    }

    @Test
    fun computeGuideState_controlsPanRevealZoomOutAndOffscreenChevrons() {
        // Infinite and A4 never show guides or chevrons
        val presenceBoth = MarginInkPresence(true, true, leftMinX = -0.3f, rightMaxX = 1.3f)
        val inf = MarginExportMath.computeGuideState("infinite", 100f, 800f, 1200f, presenceBoth)
        assertFalse(inf.showLeftGuide || inf.showRightGuide || inf.showLeftOffscreenChevron || inf.showRightOffscreenChevron)

        val a4 = MarginExportMath.computeGuideState("a4", 100f, 800f, 1200f, presenceBoth)
        assertFalse(a4.showLeftGuide || a4.showRightGuide || a4.showLeftOffscreenChevron || a4.showRightOffscreenChevron)

        // Centered PDF at 1x without margin ink: no guides, no chevrons
        val centeredClean = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, MarginInkPresence.NONE)
        assertFalse(centeredClean.showLeftGuide)
        assertFalse(centeredClean.showRightGuide)
        assertFalse(centeredClean.showLeftOffscreenChevron)
        assertFalse(centeredClean.showRightOffscreenChevron)

        // Centered PDF at 1x WITH margin ink on both sides: guides + offscreen chevrons shown
        val centeredInk = MarginExportMath.computeGuideState("pdf", 0f, 1000f, 1000f, presenceBoth)
        assertTrue(centeredInk.showLeftGuide)
        assertTrue(centeredInk.showRightGuide)
        assertTrue(centeredInk.showLeftOffscreenChevron)
        assertTrue(centeredInk.showRightOffscreenChevron)

        // Pan right 350px: left ink (-0.3 * 1000 = -300 -> screen x = +50) is now on screen -> left chevron hides
        val pannedRight = MarginExportMath.computeGuideState("pdf", 350f, 1000f, 1000f, presenceBoth)
        assertTrue(pannedRight.showLeftGuide)
        assertFalse(pannedRight.showLeftOffscreenChevron)
        assertTrue(pannedRight.showRightOffscreenChevron)

        // Zoom out so entire page + both margins fit in viewport -> guides shown, chevrons hidden
        val zoomedOut = MarginExportMath.computeGuideState("pdf", 350f, 500f, 1200f, presenceBoth)
        assertTrue(zoomedOut.showLeftGuide)
        assertTrue(zoomedOut.showRightGuide)
        assertEquals(350f, zoomedOut.leftRevealedPx, 0.01f)
        assertEquals(350f, zoomedOut.rightRevealedPx, 0.01f)
        assertFalse(zoomedOut.showLeftOffscreenChevron)
        assertFalse(zoomedOut.showRightOffscreenChevron)
    }

    @Test
    fun computeGuideState_suppressesUnsupportedPageTypes() {
        val presenceBoth = MarginInkPresence(true, true, leftMinX = -0.3f, rightMaxX = 1.3f)
        for (unsupported in listOf(null, "", "text", "a4", "infinite")) {
            val state = MarginExportMath.computeGuideState(
                pageType = unsupported,
                pageLeftInViewportPx = 200f,
                pageWidthPx = 600f,
                viewportWidthPx = 1200f,
                presence = presenceBoth
            )
            assertFalse("left guide must stay hidden for $unsupported", state.showLeftGuide)
            assertFalse("right guide must stay hidden for $unsupported", state.showRightGuide)
            assertFalse("left chevron must stay hidden for $unsupported", state.showLeftOffscreenChevron)
            assertFalse("right chevron must stay hidden for $unsupported", state.showRightOffscreenChevron)
            assertEquals(0f, state.leftRevealedPx, 0.001f)
            assertEquals(0f, state.rightRevealedPx, 0.001f)
        }
    }

    @Test
    fun computeGuideState_suppressesInnerGutterPanRevealWhilePreservingInkGuides() {
        // Right-hand page in doublePageMode at rest: left inner gutter is revealed in viewport,
        // right outer margin is also revealed. Without margin ink, inner gutter must stay hidden.
        val rightPageClean = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 520f,
            pageWidthPx = 420f,
            viewportWidthPx = 1000f,
            presence = MarginInkPresence.NONE,
            allowLeftPanReveal = false,
            allowRightPanReveal = true
        )
        assertFalse(rightPageClean.showLeftGuide)
        assertTrue(rightPageClean.showRightGuide)

        // Left-hand page in doublePageMode at rest: right inner gutter suppressed when clean.
        val leftPageClean = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 60f,
            pageWidthPx = 420f,
            viewportWidthPx = 1000f,
            presence = MarginInkPresence.NONE,
            allowLeftPanReveal = true,
            allowRightPanReveal = false
        )
        assertTrue(leftPageClean.showLeftGuide)
        assertFalse(leftPageClean.showRightGuide)

        // Inner gutter WITH actual margin ink still reveals its dashed guide.
        val rightPageLeftInk = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 520f,
            pageWidthPx = 420f,
            viewportWidthPx = 1000f,
            presence = MarginInkPresence(hasLeftMarginInk = true, leftMinX = -0.3f),
            allowLeftPanReveal = false,
            allowRightPanReveal = false
        )
        assertTrue(rightPageLeftInk.showLeftGuide)
        assertFalse(rightPageLeftInk.showRightGuide)

        val leftPageRightInk = MarginExportMath.computeGuideState(
            pageType = "pdf",
            pageLeftInViewportPx = 60f,
            pageWidthPx = 420f,
            viewportWidthPx = 1000f,
            presence = MarginInkPresence(hasRightMarginInk = true, rightMaxX = 1.3f),
            allowLeftPanReveal = false,
            allowRightPanReveal = false
        )
        assertFalse(leftPageRightInk.showLeftGuide)
        assertTrue(leftPageRightInk.showRightGuide)
    }

    @Test
    fun hasHorizontalPanOrZoomReveal_requiresActualPanOrZoomOut() {
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(0f, 1.0f))
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(2.0f, 0.99f))
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(-2.0f, 1.5f))
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(Float.NaN, 1.0f))
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(0f, Float.NaN))
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(0f, 0f))
        assertFalse(MarginExportMath.hasHorizontalPanOrZoomReveal(0f, -0.5f))

        assertTrue(MarginExportMath.hasHorizontalPanOrZoomReveal(2.1f, 1.0f))
        assertTrue(MarginExportMath.hasHorizontalPanOrZoomReveal(-2.5f, 1.0f))
        assertTrue(MarginExportMath.hasHorizontalPanOrZoomReveal(0f, 0.98f))
        assertTrue(MarginExportMath.hasHorizontalPanOrZoomReveal(150f, 0.75f))
    }
}
