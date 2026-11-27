package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.ContinuousStrokeTransformMath
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LassoHandle
import personal.notes.touch.ui.canvas.NormRect
import kotlin.math.PI
import kotlin.math.abs

class ContinuousStrokeTransformMathTest {

    private fun makeStroke(
        vararg coords: Pair<Float, Float>,
        tool: CanvasTool = CanvasTool.PEN,
        color: Int = 0xFF112233.toInt(),
        baseWidthNorm: Float = 0.01f,
        autoShape: Boolean = false
    ): ContinuousStroke {
        val pts = coords.map { (x, y) -> InkPoint(x, y, baseWidthNorm, 0.25f) }.toMutableList()
        return ContinuousStroke(tool, color, baseWidthNorm, autoShape, pts)
    }

    @Test
    fun rotatePointsPreservesIsotropicLengthAcrossRatiosWithoutSkew() {
        val ratios = listOf(16f / 9f, ContinuousPageGeometry.DEFAULT_PAGE_RATIO, 1f)
        val anglesDeg = listOf(45f, 90f, 180f, -30f)

        for (ratio in ratios) {
            // Horizontal segment of normalized dx = 0.1f -> isotropic length = 0.1f * ratio
            val p0 = InkPoint(0.45f, 0.5f, 0.01f)
            val p1 = InkPoint(0.55f, 0.5f, 0.01f)
            val origIsoLen = ContinuousStrokeTransformMath.isotropicDistance(p0, p1, ratio)

            for (deg in anglesDeg) {
                val rad = Math.toRadians(deg.toDouble()).toFloat()
                val rotated = ContinuousStrokeTransformMath.rotatePoints(
                    points = listOf(p0, p1),
                    deltaRad = rad,
                    pivotXNorm = 0.5f,
                    pivotYNorm = 0.5f,
                    boxRatio = ratio,
                    margin = 1f,
                    clampToBounds = false
                )
                assertEquals(2, rotated.size)
                val rotIsoLen = ContinuousStrokeTransformMath.isotropicDistance(rotated[0], rotated[1], ratio)
                assertEquals("ratio=$ratio deg=$deg", origIsoLen, rotIsoLen, 1e-5f)
            }

            // Specifically verify 90 degree rotation turns horizontal dx into vertical dy == dx * boxRatio
            val rot90 = ContinuousStrokeTransformMath.rotatePoints(
                points = listOf(p0, p1),
                deltaRad = (PI / 2.0).toFloat(),
                pivotXNorm = 0.5f,
                pivotYNorm = 0.5f,
                boxRatio = ratio,
                margin = 1f,
                clampToBounds = false
            )
            assertEquals(0f, abs(rot90[1].x - rot90[0].x), 1e-5f)
            assertEquals(0.1f * ratio, rot90[1].y - rot90[0].y, 1e-5f)
        }
    }

    @Test
    fun rotatePointsRespectsPdfA4AndInfiniteBoundsClamping() {
        val pts = listOf(InkPoint(0.95f, 0.9f, 0.01f), InkPoint(0.95f, 0.1f, 0.01f))
        // Rotating 90 deg around (0.95f, 0.5f) on 16:9 pushes points horizontally outside [0, 1]
        val pdfMargin = ContinuousPageGeometry.marginFor("pdf")
        val a4Margin = ContinuousPageGeometry.marginFor("a4")

        val pdfRotated = ContinuousStrokeTransformMath.rotatePoints(
            points = pts,
            deltaRad = (PI / 2.0).toFloat(),
            pivotXNorm = 0.95f,
            pivotYNorm = 0.5f,
            boxRatio = 16f / 9f,
            margin = pdfMargin,
            clampToBounds = true
        )
        assertTrue(pdfRotated.any { it.x > 1.0f })
        assertTrue(pdfRotated.all { it.x in -1f..2f && it.y in 0f..1f })

        val a4Rotated = ContinuousStrokeTransformMath.rotatePoints(
            points = pts,
            deltaRad = (PI / 2.0).toFloat(),
            pivotXNorm = 0.95f,
            pivotYNorm = 0.5f,
            boxRatio = ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            margin = a4Margin,
            clampToBounds = true
        )
        assertTrue(a4Rotated.all { it.x in 0f..1f && it.y in 0f..1f })

        val infRotated = ContinuousStrokeTransformMath.rotatePoints(
            points = listOf(InkPoint(-2f, -2f, 0.01f)),
            deltaRad = (PI / 4.0).toFloat(),
            pivotXNorm = -1.5f,
            pivotYNorm = -1.5f,
            boxRatio = 1f,
            margin = 0f,
            clampToBounds = false
        )
        assertTrue(infRotated[0].x < -1f && infRotated[0].y < -1f)
    }

    @Test
    fun mirrorHorizontalReflectsSymmetricallyAndDoubleMirrorIsIdentity() {
        val stroke = makeStroke(0.2f to 0.3f, 0.35f to 0.4f, 0.6f to 0.8f)
        val bounds = ContinuousStrokeTransformMath.selectionBounds(listOf(stroke), 16f / 9f)
        assertNotNull(bounds)
        val cx = bounds?.centerX ?: 0f

        val mirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(
            points = stroke.points,
            centerXNorm = cx,
            margin = 0f,
            clampToBounds = true
        )
        for (i in stroke.points.indices) {
            assertEquals(2f * cx, stroke.points[i].x + mirrored[i].x, 1e-6f)
            assertEquals(stroke.points[i].y, mirrored[i].y, 1e-6f)
        }

        val doubleMirrored = ContinuousStrokeTransformMath.mirrorHorizontalPoints(
            points = mirrored,
            centerXNorm = cx,
            margin = 0f,
            clampToBounds = true
        )
        for (i in stroke.points.indices) {
            assertEquals(stroke.points[i].x, doubleMirrored[i].x, 1e-6f)
            assertEquals(stroke.points[i].y, doubleMirrored[i].y, 1e-6f)
        }
    }

    @Test
    fun proportionalAndEdgeScalePreserveAspectOrAxisAndFixNarrowSpanSign() {
        val ratio = 16f / 9f
        val st = makeStroke(0.2f to 0.2f, 0.4f to 0.2f, 0.4f to 0.5f)
        val anchorX = 0.2f
        val anchorY = 0.2f

        val half = ContinuousStrokeTransformMath.scalePoints(
            st.points, 0.5f, 0.5f, anchorX, anchorY, margin = 1f, clampToBounds = true
        )
        val twice = ContinuousStrokeTransformMath.scalePoints(
            st.points, 2.0f, 2.0f, anchorX, anchorY, margin = 1f, clampToBounds = true
        )
        val origW = 0.2f * ratio
        val origH = 0.3f
        val origIsoAspect = origW / origH
        val halfIsoAspect = ((half[1].x - half[0].x) * ratio) / (half[2].y - half[1].y)
        val twiceIsoAspect = ((twice[1].x - twice[0].x) * ratio) / (twice[2].y - twice[1].y)
        assertEquals(origIsoAspect, halfIsoAspect, 1e-5f)
        assertEquals(origIsoAspect, twiceIsoAspect, 1e-5f)

        // Min scale floor prevents collapse/inversion
        val clampedMin = ContinuousStrokeTransformMath.scalePoints(
            st.points, -1f, 0.01f, anchorX, anchorY, margin = 1f, clampToBounds = true
        )
        assertEquals(0.2f + 0.2f * ContinuousStrokeTransformMath.DEFAULT_MIN_SCALE, clampedMin[1].x, 1e-5f)
        assertEquals(0.2f + 0.3f * ContinuousStrokeTransformMath.DEFAULT_MIN_SCALE, clampedMin[2].y, 1e-5f)

        // Proportional drag factor
        val f = ContinuousStrokeTransformMath.proportionalScaleFactor(
            startXNorm = 0.4f, startYNorm = 0.5f,
            curXNorm = 0.6f, curYNorm = 0.8f,
            anchorXNorm = 0.2f, anchorYNorm = 0.2f,
            boxRatio = ratio
        )
        assertEquals(2.0f, f, 1e-4f)

        // All 4 edge handles + narrow span sign check for SCALE_LEFT and SCALE_TOP
        val narrowBounds = NormRect(0.498f, 0.498f, 0.502f, 0.502f) // width/height = 0.004 < minSpanNorm (0.01)
        val leftSpec = ContinuousStrokeTransformMath.edgeScaleFactors(
            LassoHandle.SCALE_LEFT, narrowBounds,
            startXNorm = 0.498f, startYNorm = 0.5f,
            curXNorm = 0.482f, curYNorm = 0.5f
        )
        assertNotNull(leftSpec)
        assertTrue((leftSpec?.scaleX ?: 0f) > 1.5f)
        assertEquals(1f, leftSpec?.scaleY ?: 0f, 1e-6f)
        assertEquals(narrowBounds.right, leftSpec?.anchorXNorm ?: 0f, 1e-6f)

        val rightSpec = ContinuousStrokeTransformMath.edgeScaleFactors(
            LassoHandle.SCALE_RIGHT, narrowBounds,
            startXNorm = 0.502f, startYNorm = 0.5f,
            curXNorm = 0.522f, curYNorm = 0.5f
        )
        assertNotNull(rightSpec)
        assertTrue((rightSpec?.scaleX ?: 0f) > 1.5f)
        assertEquals(1f, rightSpec?.scaleY ?: 0f, 1e-6f)

        val topSpec = ContinuousStrokeTransformMath.edgeScaleFactors(
            LassoHandle.SCALE_TOP, narrowBounds,
            startXNorm = 0.5f, startYNorm = 0.498f,
            curXNorm = 0.5f, curYNorm = 0.482f
        )
        assertNotNull(topSpec)
        assertEquals(1f, topSpec?.scaleX ?: 0f, 1e-6f)
        assertTrue((topSpec?.scaleY ?: 0f) > 1.5f)
        assertEquals(narrowBounds.bottom, topSpec?.anchorYNorm ?: 0f, 1e-6f)

        val bottomSpec = ContinuousStrokeTransformMath.edgeScaleFactors(
            LassoHandle.SCALE_BOTTOM, narrowBounds,
            startXNorm = 0.5f, startYNorm = 0.502f,
            curXNorm = 0.5f, curYNorm = 0.522f
        )
        assertNotNull(bottomSpec)
        assertEquals(1f, bottomSpec?.scaleX ?: 0f, 1e-6f)
        assertTrue((bottomSpec?.scaleY ?: 0f) > 1.5f)
    }

    @Test
    fun rigidTranslationAndCopyRespectPdfMarginVsA4VsInfinite() {
        val st1 = makeStroke(0.2f to 0.2f, 0.4f to 0.3f)
        val st2 = makeStroke(0.3f to 0.25f, 0.5f to 0.4f)
        val pb = ContinuousStrokeTransformMath.pointsBounds(listOf(st1, st2))
        assertNotNull(pb)
        val bounds = pb ?: NormRect(0f, 0f, 0f, 0f)

        // PDF allows [-1f, 2f] in X and [0f, 1f] in Y
        val (pdfDxLeft, pdfDyLeft) = ContinuousStrokeTransformMath.clampTranslationDelta(bounds, -0.7f, 0f, margin = 1f)
        assertEquals(-0.7f, pdfDxLeft, 1e-6f)
        assertEquals(0f, pdfDyLeft, 1e-6f)

        val (pdfDxWall, pdfDyWall) = ContinuousStrokeTransformMath.clampTranslationDelta(bounds, -5f, 5f, margin = 1f)
        assertEquals(-1f - bounds.left, pdfDxWall, 1e-6f)
        assertEquals(1f - bounds.bottom, pdfDyWall, 1e-6f)

        // A4 restricts rigidly to [0f, 1f] x [0f, 1f]
        val (a4Dx, a4Dy) = ContinuousStrokeTransformMath.clampTranslationDelta(bounds, -5f, -5f, margin = 0f)
        assertEquals(-bounds.left, a4Dx, 1e-6f)
        assertEquals(-bounds.top, a4Dy, 1e-6f)

        // Infinite unclamped
        val infMoved = ContinuousStrokeTransformMath.translatePoints(
            st1.points, -3f, 5f, margin = 0f, clampToBounds = false
        )
        assertEquals(-2.8f, infMoved[0].x, 1e-5f)
        assertEquals(5.2f, infMoved[0].y, 1e-5f)

        // Copy-with-offset deep clones without mutating source
        val copies = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            strokes = listOf(st1),
            offsetXNorm = 0.05f,
            offsetYNorm = 0.05f,
            margin = 0f,
            clampToBounds = true
        )
        assertEquals(1, copies.size)
        assertEquals(st1.tool, copies[0].tool)
        assertEquals(st1.color, copies[0].color)
        assertEquals(st1.baseWidthNorm, copies[0].baseWidthNorm, 1e-6f)
        assertEquals(0.25f, copies[0].points[0].x, 1e-6f)
        copies[0].points[0] = InkPoint(0.99f, 0.99f, 0.01f)
        assertEquals(0.2f, st1.points[0].x, 1e-6f)
    }

    @Test
    fun boundsLassoPolygonAndSegmentHitTestingWorkAcrossAspectRatios() {
        assertNull(ContinuousStrokeTransformMath.selectionBounds(emptyList(), 16f / 9f))

        // Single-point stroke produces uniform isotropic padding
        val dot = makeStroke(0.5f to 0.5f, baseWidthNorm = 0.01f)
        val ratio = 16f / 9f
        val b = ContinuousStrokeTransformMath.selectionBounds(listOf(dot), ratio)
        assertNotNull(b)
        val padX = (b?.width ?: 0f) * 0.5f
        val padY = (b?.height ?: 0f) * 0.5f
        assertEquals(padX * ratio, padY, 1e-6f)

        // Polygon selection (>= 50% points inside, horizontal edge safe, < 3 vertices returns empty)
        val squarePoly = listOf(0.1f to 0.1f, 0.5f to 0.1f, 0.5f to 0.5f, 0.1f to 0.5f)
        assertTrue(ContinuousStrokeTransformMath.pointInPolygon(0.3f, 0.3f, squarePoly))
        assertFalse(ContinuousStrokeTransformMath.pointInPolygon(0.7f, 0.3f, squarePoly))
        assertTrue(ContinuousStrokeTransformMath.selectStrokesInLasso(listOf(dot), listOf(0f to 0f, 1f to 0f)).isEmpty())

        val halfIn = makeStroke(0.2f to 0.2f, 0.9f to 0.9f) // 1 of 2 inside -> >= 50%
        val mostlyOut = makeStroke(0.2f to 0.2f, 0.8f to 0.8f, 0.9f to 0.9f) // 1 of 3 inside -> < 50%
        val picked = ContinuousStrokeTransformMath.selectStrokesInLasso(listOf(halfIn, mostlyOut), squarePoly)
        assertEquals(listOf(0), picked)

        // Single-stroke hit test hits 2-point segment midpoint and prefers topmost stroke on tie
        val bottomLine = makeStroke(0.2f to 0.5f, 0.8f to 0.5f)
        val topLine = makeStroke(0.2f to 0.5f, 0.8f to 0.5f)
        val hitIdx = ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(
            strokes = listOf(bottomLine, topLine),
            tapXNorm = 0.5f,
            tapYNorm = 0.51f,
            boxRatio = ratio,
            toleranceIso = 0.02f
        )
        assertEquals(1, hitIdx)
        assertEquals(
            -1,
            ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(
                listOf(bottomLine), 0.5f, 0.7f, ratio, toleranceIso = 0.02f
            )
        )
    }

    @Test
    fun handleLayoutAndIsotropicHitTestCoverAllHandlesAndEqualPixelRadius() {
        val ratio = 16f / 9f
        val boxHeightPx = 900f
        val bounds = NormRect(0.2f, 0.2f, 0.7f, 0.8f)
        val layout = ContinuousStrokeTransformMath.computeHandleLayout(
            bounds = bounds,
            boxRatio = ratio,
            boxHeightPx = boxHeightPx,
            scale = 1f
        )

        assertEquals(LassoHandle.DELETE, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.deleteCenter.first, layout.deleteCenter.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.ROTATE, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.rotateCenter.first, layout.rotateCenter.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.MIRROR, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.mirrorCenter.first, layout.mirrorCenter.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.COPY, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.copyCenter.first, layout.copyCenter.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.SCALE_CORNER_BR, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleCornerBR.first, layout.scaleCornerBR.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.SCALE_LEFT, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleLeft.first, layout.scaleLeft.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.SCALE_RIGHT, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleRight.first, layout.scaleRight.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.SCALE_TOP, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleTop.first, layout.scaleTop.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.SCALE_BOTTOM, ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleBottom.first, layout.scaleBottom.second, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.MOVE, ContinuousStrokeTransformMath.hitTestHandle(bounds, bounds.centerX, bounds.centerY, ratio, boxHeightPx, 1f))
        assertEquals(LassoHandle.NONE, ContinuousStrokeTransformMath.hitTestHandle(bounds, 0.01f, 0.01f, ratio, boxHeightPx, 1f))

        // Verify equal screen pixel hit radius along X and Y on 16:9
        val hitRPx = ContinuousStrokeTransformMath.HANDLE_RADIUS_PX * 1.8f * 0.9f
        val dNormY = hitRPx / boxHeightPx
        val dNormX = dNormY / ratio // same pixel distance horizontally!
        assertEquals(
            LassoHandle.SCALE_BOTTOM,
            ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleBottom.first + dNormX, layout.scaleBottom.second, ratio, boxHeightPx, 1f)
        )
        assertEquals(
            LassoHandle.SCALE_BOTTOM,
            ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleBottom.first, layout.scaleBottom.second + dNormY, ratio, boxHeightPx, 1f)
        )
        val outPx = ContinuousStrokeTransformMath.HANDLE_RADIUS_PX * 1.8f * 1.1f
        assertEquals(
            LassoHandle.MOVE,
            ContinuousStrokeTransformMath.hitTestHandle(bounds, layout.scaleBottom.first, layout.scaleBottom.second - outPx / boxHeightPx, ratio, boxHeightPx, 1f)
        )
    }

    @Test
    fun degenerateInputsAreSafelyHandledWithoutNaNOrCrash() {
        assertEquals(1f, ContinuousStrokeTransformMath.safeBoxRatio(0f), 1e-6f)
        assertEquals(1f, ContinuousStrokeTransformMath.safeBoxRatio(-2f), 1e-6f)
        assertEquals(1f, ContinuousStrokeTransformMath.safeBoxRatio(Float.NaN), 1e-6f)
        assertEquals(1f, ContinuousStrokeTransformMath.safeBoxRatio(Float.POSITIVE_INFINITY), 1e-6f)
        assertEquals(0f, ContinuousStrokeTransformMath.safeMargin(-1f), 1e-6f)
        assertEquals(0f, ContinuousStrokeTransformMath.safeMargin(Float.NaN), 1e-6f)

        val pt = listOf(InkPoint(0.3f, 0.4f, 0.01f))
        val badRot = ContinuousStrokeTransformMath.rotatePoints(pt, Float.NaN, 0.5f, 0.5f, 1f)
        assertEquals(0.3f, badRot[0].x, 1e-6f)
        val badScale = ContinuousStrokeTransformMath.scalePoints(pt, Float.NaN, Float.POSITIVE_INFINITY, 0.5f, 0.5f)
        assertEquals(0.3f, badScale[0].x, 1e-6f)
        assertTrue(ContinuousStrokeTransformMath.rotatePoints(emptyList(), 1f, 0f, 0f, 1f).isEmpty())
        assertEquals(-1, ContinuousStrokeTransformMath.hitTestSingleStrokeIndex(listOf(makeStroke(0.5f to 0.5f)), 0.5f, 0.5f, 1f, 0f))
        assertEquals(LassoHandle.NONE, ContinuousStrokeTransformMath.hitTestHandle(NormRect(0.2f, 0.2f, 0.4f, 0.4f), Float.NaN, 0.3f, 1f, 0f, 1f))
        val nanBoundsDelta = ContinuousStrokeTransformMath.clampTranslationDelta(
            NormRect(Float.NaN, 0.1f, 0.5f, 0.9f), 0.1f, 0.1f, 1f
        )
        assertEquals(0f, nanBoundsDelta.first, 1e-6f)
        assertEquals(0f, nanBoundsDelta.second, 1e-6f)
    }

    @Test
    fun clampTranslationDeltaHandlesIeee754BoundaryRangesWithoutThrowing() {
        val caseY = ContinuousStrokeTransformMath.clampTranslationDelta(
            NormRect(0.2f, 0.1f, 0.6f, 1.1f),
            0.5f,
            0.5f,
            0f
        )
        assertTrue(caseY.first.isFinite() && caseY.second.isFinite())
        assertEquals(0.4f, caseY.first, 1e-5f)
        assertEquals(-0.1f, caseY.second, 1e-5f)

        val casePdfX = ContinuousStrokeTransformMath.clampTranslationDelta(
            NormRect(-0.3f, 0.2f, 2.7f, 0.6f),
            0.5f,
            0.5f,
            1f
        )
        assertTrue(casePdfX.first.isFinite() && casePdfX.second.isFinite())
        assertEquals(-0.7f, casePdfX.first, 1e-5f)
        assertEquals(0.4f, casePdfX.second, 1e-5f)

        val caseA4Both = ContinuousStrokeTransformMath.clampTranslationDelta(
            NormRect(0.1f, 0.1f, 1.1f, 1.1f),
            0.5f,
            0.5f,
            0f
        )
        assertTrue(caseA4Both.first.isFinite() && caseA4Both.second.isFinite())
        assertEquals(-0.1f, caseA4Both.first, 1e-5f)
        assertEquals(-0.1f, caseA4Both.second, 1e-5f)
    }

    @Test
    fun copyStrokesWithOffsetPreservesOversizedStrokeDimensionsRigidly() {
        // Oversized width = 4.0f on PDF (margin = 1f) must NOT be squashed to 3.0f
        val widePdfStroke = makeStroke(-1.5f to 0.5f, 2.5f to 0.5f)
        val copiedWidePdf = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            strokes = listOf(widePdfStroke),
            offsetXNorm = 0.02f,
            offsetYNorm = 0.02f,
            margin = 1f,
            clampToBounds = true
        )
        assertEquals(1, copiedWidePdf.size)
        assertEquals(4.0f, copiedWidePdf[0].points[1].x - copiedWidePdf[0].points[0].x, 1e-5f)
        assertEquals(0.52f, copiedWidePdf[0].points[0].y, 1e-5f)

        // Oversized width = 2.0f on A4 (margin = 0f) must NOT be squashed to 1.0f
        val wideA4Stroke = makeStroke(-0.5f to 0.5f, 1.5f to 0.5f)
        val copiedWideA4 = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            strokes = listOf(wideA4Stroke),
            offsetXNorm = 0.02f,
            offsetYNorm = 0.02f,
            margin = 0f,
            clampToBounds = true
        )
        assertEquals(1, copiedWideA4.size)
        assertEquals(2.0f, copiedWideA4[0].points[1].x - copiedWideA4[0].points[0].x, 1e-5f)
        assertEquals(0.52f, copiedWideA4[0].points[0].y, 1e-5f)

        // Oversized height = 1.5f with clampToBounds = true must NOT be squashed to 1.0f
        val tallStroke = makeStroke(0.5f to -0.25f, 0.5f to 1.25f)
        val copiedTall = ContinuousStrokeTransformMath.copyStrokesWithOffset(
            strokes = listOf(tallStroke),
            offsetXNorm = 0.02f,
            offsetYNorm = 0.02f,
            margin = 0f,
            clampToBounds = true
        )
        assertEquals(1, copiedTall.size)
        assertEquals(1.5f, copiedTall[0].points[1].y - copiedTall[0].points[0].y, 1e-5f)
        assertEquals(0.52f, copiedTall[0].points[0].x, 1e-5f)
    }

    @Test
    fun expandBoundsForHandlesGuaranteesMoveInteriorAndAllHandlesForTinySelections() {
        val ratios = listOf(
            1f, // Infinite canvas
            ContinuousPageGeometry.DEFAULT_PAGE_RATIO, // A4 (210/297)
            ContinuousPageGeometry.boxRatio("pdf", ContinuousPageGeometry.DEFAULT_PAGE_RATIO) // PDF with margins
        )
        val scales = listOf(0.5f, 1.0f, 2.5f)
        val boxHeightPx = 1200f

        for (ratio in ratios) {
            for (scale in scales) {
                val tinyStroke = makeStroke(0.500f to 0.500f, 0.501f to 0.501f, baseWidthNorm = 0.003f)
                val rawBounds = ContinuousStrokeTransformMath.selectionBounds(listOf(tinyStroke), ratio)
                assertNotNull(rawBounds)
                val expanded = ContinuousStrokeTransformMath.expandBoundsForHandles(
                    bounds = rawBounds ?: NormRect(0.5f, 0.5f, 0.5f, 0.5f),
                    boxRatio = ratio,
                    boxHeightPx = boxHeightPx,
                    scale = scale
                )
                // Center must remain invariant under expansion
                assertEquals(rawBounds?.centerX ?: 0.5f, expanded.centerX, 1e-5f)
                assertEquals(rawBounds?.centerY ?: 0.5f, expanded.centerY, 1e-5f)

                val layout = ContinuousStrokeTransformMath.computeHandleLayout(
                    bounds = expanded,
                    boxRatio = ratio,
                    boxHeightPx = boxHeightPx,
                    scale = scale
                )

                // Center of tiny selection MUST hit LassoHandle.MOVE (never shadowed by edge/corner handles!)
                assertEquals(
                    "center MOVE failed for ratio=$ratio scale=$scale",
                    LassoHandle.MOVE,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, expanded.centerX, expanded.centerY, ratio, boxHeightPx, scale
                    )
                )

                // Every corner and edge handle must be individually reachable at its center
                assertEquals(
                    LassoHandle.SCALE_CORNER_BR,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.scaleCornerBR.first, layout.scaleCornerBR.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.COPY,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.copyCenter.first, layout.copyCenter.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.DELETE,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.deleteCenter.first, layout.deleteCenter.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.MIRROR,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.mirrorCenter.first, layout.mirrorCenter.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.ROTATE,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.rotateCenter.first, layout.rotateCenter.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.SCALE_LEFT,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.scaleLeft.first, layout.scaleLeft.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.SCALE_RIGHT,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.scaleRight.first, layout.scaleRight.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.SCALE_TOP,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.scaleTop.first, layout.scaleTop.second, ratio, boxHeightPx, scale
                    )
                )
                assertEquals(
                    LassoHandle.SCALE_BOTTOM,
                    ContinuousStrokeTransformMath.hitTestHandle(
                        expanded, layout.scaleBottom.first, layout.scaleBottom.second, ratio, boxHeightPx, scale
                    )
                )
            }
        }
    }
}
