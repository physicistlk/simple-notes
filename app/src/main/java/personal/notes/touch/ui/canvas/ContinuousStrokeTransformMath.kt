package personal.notes.touch.ui.canvas

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Interactive handles for lasso / arrow selection overlays in continuous modes.
 */
enum class LassoHandle {
    NONE,
    MOVE,
    ROTATE,
    MIRROR,
    DELETE,
    COPY,
    SCALE_CORNER_BR,
    SCALE_LEFT,
    SCALE_RIGHT,
    SCALE_TOP,
    SCALE_BOTTOM
}

/**
 * Pure normalized bounding box in page-box coordinates (`x` in `[-margin, 1+margin]`, `y` in `[0, 1]` for fixed pages).
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    val isEmpty: Boolean
        get() = !left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite() ||
            width <= 0f || height <= 0f

    fun contains(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x in left..right && y in top..bottom
}

/**
 * Normalized center positions for all active handles around [bounds].
 */
data class HandleLayout(
    val bounds: NormRect,
    val rotateCenter: Pair<Float, Float>,
    val mirrorCenter: Pair<Float, Float>,
    val deleteCenter: Pair<Float, Float>,
    val copyCenter: Pair<Float, Float>,
    val scaleCornerBR: Pair<Float, Float>,
    val scaleLeft: Pair<Float, Float>,
    val scaleRight: Pair<Float, Float>,
    val scaleTop: Pair<Float, Float>,
    val scaleBottom: Pair<Float, Float>
)

/**
 * Axis-constrained scale parameters computed from an edge-handle drag gesture.
 */
data class EdgeScaleSpec(
    val scaleX: Float,
    val scaleY: Float,
    val anchorXNorm: Float,
    val anchorYNorm: Float
)

/**
 * Pure-JVM normalized stroke geometry, selection hit-testing, handle layout, and aspect-correct
 * transform math for Continuous modes (`infinite`, `pdf`, `a4`).
 *
 * Uses zero Android or AndroidX types so unit tests exercise production logic directly on JVM.
 */
object ContinuousStrokeTransformMath {

    const val DEFAULT_MIN_SCALE = 0.15f
    const val DEFAULT_MAX_SCALE = 8.0f
    const val DEFAULT_MIN_PAD_NORM = 0.015f
    const val HANDLE_RADIUS_PX = 20f
    const val DEFAULT_COPY_OFFSET_ISO = 0.02f

    fun safeBoxRatio(boxRatio: Float, fallback: Float = 1f): Float {
        val safeFallback = if (fallback.isFinite() && fallback > 0f) fallback else 1f
        return if (boxRatio.isFinite() && boxRatio > 0f) boxRatio else safeFallback
    }

    fun safeMargin(margin: Float): Float =
        if (margin.isFinite() && margin >= 0f) margin else 0f

    fun clampX(x: Float, margin: Float): Float {
        val m = safeMargin(margin)
        return if (x.isFinite()) x.coerceIn(-m, 1f + m) else 0f.coerceIn(-m, 1f + m)
    }

    fun clampY(y: Float): Float =
        if (y.isFinite()) y.coerceIn(0f, 1f) else 0f

    fun clampPoints(points: List<InkPoint>, margin: Float): List<InkPoint> {
        val m = safeMargin(margin)
        return points.map { p ->
            InkPoint(clampX(p.x, m), clampY(p.y), p.w, p.tilt)
        }
    }

    /**
     * Computes isotropic-padded selection bounds in normalized box coordinates across [strokes].
     * Padding in Y equals `padX * boxRatio` so pixel padding is uniform on all four sides.
     */
    fun selectionBounds(
        strokes: List<ContinuousStroke>,
        boxRatio: Float = 1f,
        minPadNorm: Float = DEFAULT_MIN_PAD_NORM
    ): NormRect? {
        if (strokes.isEmpty()) return null
        val r = safeBoxRatio(boxRatio)
        val safeMinPad = if (minPadNorm.isFinite() && minPadNorm >= 0f) minPadNorm else DEFAULT_MIN_PAD_NORM
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var hasPoints = false

        for (st in strokes) {
            val baseW = if (st.baseWidthNorm.isFinite() && st.baseWidthNorm >= 0f) st.baseWidthNorm else 0f
            val padX = (baseW * 2.5f).coerceAtLeast(safeMinPad)
            val padY = padX * r
            for (p in st.points) {
                if (!p.x.isFinite() || !p.y.isFinite()) continue
                hasPoints = true
                if (p.x - padX < minX) minX = p.x - padX
                if (p.y - padY < minY) minY = p.y - padY
                if (p.x + padX > maxX) maxX = p.x + padX
                if (p.y + padY > maxY) maxY = p.y + padY
            }
        }
        return if (hasPoints) NormRect(minX, minY, maxX, maxY) else null
    }

    /**
     * Computes tight (unpadded) bounds across all finite points in [strokes].
     */
    fun pointsBounds(strokes: List<ContinuousStroke>): NormRect? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var hasPoints = false

        for (st in strokes) {
            for (p in st.points) {
                if (!p.x.isFinite() || !p.y.isFinite()) continue
                hasPoints = true
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
        }
        return if (hasPoints) NormRect(minX, minY, maxX, maxY) else null
    }

    fun isotropicDistance(a: InkPoint, b: InkPoint, boxRatio: Float): Float {
        if (!a.x.isFinite() || !a.y.isFinite() || !b.x.isFinite() || !b.y.isFinite()) return 0f
        val r = safeBoxRatio(boxRatio)
        return hypot(((b.x - a.x) * r).toDouble(), (b.y - a.y).toDouble()).toFloat()
    }

    /**
     * Rotates [points] around ([pivotXNorm], [pivotYNorm]) by [deltaRad] in isotropic coordinates
     * `(x * boxRatio, y)` so non-square pages do not skew strokes.
     */
    fun rotatePoints(
        points: List<InkPoint>,
        deltaRad: Float,
        pivotXNorm: Float,
        pivotYNorm: Float,
        boxRatio: Float,
        margin: Float = 0f,
        clampToBounds: Boolean = true
    ): List<InkPoint> {
        if (points.isEmpty()) return emptyList()
        if (!deltaRad.isFinite() || !pivotXNorm.isFinite() || !pivotYNorm.isFinite()) {
            return points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        }
        val r = safeBoxRatio(boxRatio)
        val m = safeMargin(margin)
        val cosA = cos(deltaRad.toDouble()).toFloat()
        val sinA = sin(deltaRad.toDouble()).toFloat()
        val pivotXIso = pivotXNorm * r

        return points.map { p ->
            if (!p.x.isFinite() || !p.y.isFinite()) {
                InkPoint(p.x, p.y, p.w, p.tilt)
            } else {
                val dxIso = (p.x - pivotXNorm) * r
                val dyIso = p.y - pivotYNorm
                val rxIso = pivotXIso + dxIso * cosA - dyIso * sinA
                val ryIso = pivotYNorm + dxIso * sinA + dyIso * cosA
                var nx = rxIso / r
                var ny = ryIso
                if (clampToBounds) {
                    nx = clampX(nx, m)
                    ny = clampY(ny)
                }
                InkPoint(nx, ny, p.w, p.tilt)
            }
        }
    }

    fun mirrorHorizontalPoints(
        points: List<InkPoint>,
        centerXNorm: Float,
        margin: Float = 0f,
        clampToBounds: Boolean = true
    ): List<InkPoint> {
        if (points.isEmpty()) return emptyList()
        if (!centerXNorm.isFinite()) {
            return points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        }
        val m = safeMargin(margin)
        return points.map { p ->
            if (!p.x.isFinite() || !p.y.isFinite()) {
                InkPoint(p.x, p.y, p.w, p.tilt)
            } else {
                var nx = 2f * centerXNorm - p.x
                var ny = p.y
                if (clampToBounds) {
                    nx = clampX(nx, m)
                    ny = clampY(ny)
                }
                InkPoint(nx, ny, p.w, p.tilt)
            }
        }
    }

    fun scalePoints(
        points: List<InkPoint>,
        scaleX: Float,
        scaleY: Float,
        anchorXNorm: Float,
        anchorYNorm: Float,
        margin: Float = 0f,
        clampToBounds: Boolean = true,
        minScale: Float = DEFAULT_MIN_SCALE,
        maxScale: Float = DEFAULT_MAX_SCALE
    ): List<InkPoint> {
        if (points.isEmpty()) return emptyList()
        if (!anchorXNorm.isFinite() || !anchorYNorm.isFinite()) {
            return points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        }
        val safeMin = if (minScale.isFinite() && minScale > 0f) minScale else DEFAULT_MIN_SCALE
        val safeMax = if (maxScale.isFinite() && maxScale >= safeMin) maxScale else DEFAULT_MAX_SCALE
        val sx = if (scaleX.isFinite()) scaleX.coerceIn(safeMin, safeMax) else 1f
        val sy = if (scaleY.isFinite()) scaleY.coerceIn(safeMin, safeMax) else 1f
        val m = safeMargin(margin)

        return points.map { p ->
            if (!p.x.isFinite() || !p.y.isFinite()) {
                InkPoint(p.x, p.y, p.w, p.tilt)
            } else {
                var nx = anchorXNorm + (p.x - anchorXNorm) * sx
                var ny = anchorYNorm + (p.y - anchorYNorm) * sy
                if (clampToBounds) {
                    nx = clampX(nx, m)
                    ny = clampY(ny)
                }
                InkPoint(nx, ny, p.w, p.tilt)
            }
        }
    }

    fun proportionalScaleFactor(
        startXNorm: Float,
        startYNorm: Float,
        curXNorm: Float,
        curYNorm: Float,
        anchorXNorm: Float,
        anchorYNorm: Float,
        boxRatio: Float,
        minDiagIso: Float = 0.01f,
        minScale: Float = DEFAULT_MIN_SCALE,
        maxScale: Float = DEFAULT_MAX_SCALE
    ): Float {
        if (!startXNorm.isFinite() || !startYNorm.isFinite() ||
            !curXNorm.isFinite() || !curYNorm.isFinite() ||
            !anchorXNorm.isFinite() || !anchorYNorm.isFinite()
        ) {
            return 1f
        }
        val r = safeBoxRatio(boxRatio)
        val safeMinDiag = if (minDiagIso.isFinite() && minDiagIso > 0f) minDiagIso else 0.01f
        val safeMin = if (minScale.isFinite() && minScale > 0f) minScale else DEFAULT_MIN_SCALE
        val safeMax = if (maxScale.isFinite() && maxScale >= safeMin) maxScale else DEFAULT_MAX_SCALE
        val rawStartDiag = hypot(
            ((startXNorm - anchorXNorm) * r).toDouble(),
            (startYNorm - anchorYNorm).toDouble()
        ).toFloat()
        val rawCurDiag = hypot(
            ((curXNorm - anchorXNorm) * r).toDouble(),
            (curYNorm - anchorYNorm).toDouble()
        ).toFloat()
        if ((rawStartDiag <= 1e-6f && rawCurDiag <= 1e-6f) ||
            (abs(curXNorm - startXNorm) <= 1e-6f && abs(curYNorm - startYNorm) <= 1e-6f)
        ) {
            return 1f
        }
        val startDiag = rawStartDiag.coerceAtLeast(safeMinDiag)
        val curDiag = rawCurDiag.coerceAtLeast(safeMinDiag * 0.5f)
        return (curDiag / startDiag).coerceIn(safeMin, safeMax)
    }

    fun edgeScaleFactors(
        handle: LassoHandle,
        bounds: NormRect,
        startXNorm: Float,
        startYNorm: Float,
        curXNorm: Float,
        curYNorm: Float,
        minSpanNorm: Float = 0.01f,
        minScale: Float = DEFAULT_MIN_SCALE,
        maxScale: Float = DEFAULT_MAX_SCALE
    ): EdgeScaleSpec? {
        if (!startXNorm.isFinite() || !startYNorm.isFinite() ||
            !curXNorm.isFinite() || !curYNorm.isFinite()
        ) {
            return null
        }
        val safeMinSpan = if (minSpanNorm.isFinite() && minSpanNorm > 0f) minSpanNorm else 0.01f
        val safeMin = if (minScale.isFinite() && minScale > 0f) minScale else DEFAULT_MIN_SCALE
        val safeMax = if (maxScale.isFinite() && maxScale >= safeMin) maxScale else DEFAULT_MAX_SCALE

        return when (handle) {
            LassoHandle.SCALE_LEFT -> {
                val ax = bounds.right
                val ay = bounds.centerY
                val startSpan = (startXNorm - ax).let { if (abs(it) < safeMinSpan) -safeMinSpan else it }
                val sx = ((curXNorm - ax) / startSpan).coerceIn(safeMin, safeMax)
                EdgeScaleSpec(sx, 1f, ax, ay)
            }
            LassoHandle.SCALE_RIGHT -> {
                val ax = bounds.left
                val ay = bounds.centerY
                val startSpan = (startXNorm - ax).let { if (abs(it) < safeMinSpan) safeMinSpan else it }
                val sx = ((curXNorm - ax) / startSpan).coerceIn(safeMin, safeMax)
                EdgeScaleSpec(sx, 1f, ax, ay)
            }
            LassoHandle.SCALE_TOP -> {
                val ax = bounds.centerX
                val ay = bounds.bottom
                val startSpan = (startYNorm - ay).let { if (abs(it) < safeMinSpan) -safeMinSpan else it }
                val sy = ((curYNorm - ay) / startSpan).coerceIn(safeMin, safeMax)
                EdgeScaleSpec(1f, sy, ax, ay)
            }
            LassoHandle.SCALE_BOTTOM -> {
                val ax = bounds.centerX
                val ay = bounds.top
                val startSpan = (startYNorm - ay).let { if (abs(it) < safeMinSpan) safeMinSpan else it }
                val sy = ((curYNorm - ay) / startSpan).coerceIn(safeMin, safeMax)
                EdgeScaleSpec(1f, sy, ax, ay)
            }
            else -> null
        }
    }

    /**
     * Clamps a group translation delta against [bounds] so multi-stroke selections move rigidly
     * up to `[-margin, 1+margin]` in X and `[0, 1]` in Y without squashing against edges.
     */
    fun clampTranslationDelta(
        bounds: NormRect,
        dxNorm: Float,
        dyNorm: Float,
        margin: Float
    ): Pair<Float, Float> {
        if (!dxNorm.isFinite() || !dyNorm.isFinite() ||
            !bounds.left.isFinite() || !bounds.top.isFinite() ||
            !bounds.right.isFinite() || !bounds.bottom.isFinite()
        ) {
            return 0f to 0f
        }
        val m = safeMargin(margin)
        val maxSpanX = 1f + 2f * m
        val minDx: Float
        val maxDx: Float
        if (bounds.width <= maxSpanX) {
            minDx = -m - bounds.left
            maxDx = (1f + m) - bounds.right
        } else {
            minDx = -m - bounds.centerX
            maxDx = (1f + m) - bounds.centerX
        }
        val clampedDx = if (minDx <= maxDx) dxNorm.coerceIn(minDx, maxDx) else (minDx + maxDx) * 0.5f

        val minDy: Float
        val maxDy: Float
        if (bounds.height <= 1f) {
            minDy = 0f - bounds.top
            maxDy = 1f - bounds.bottom
        } else {
            minDy = 0f - bounds.centerY
            maxDy = 1f - bounds.centerY
        }
        val clampedDy = if (minDy <= maxDy) dyNorm.coerceIn(minDy, maxDy) else (minDy + maxDy) * 0.5f
        return clampedDx to clampedDy
    }

    fun translatePoints(
        points: List<InkPoint>,
        dxNorm: Float,
        dyNorm: Float,
        margin: Float = 0f,
        clampToBounds: Boolean = true
    ): List<InkPoint> {
        if (points.isEmpty()) return emptyList()
        if (!dxNorm.isFinite() || !dyNorm.isFinite()) {
            return points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
        }
        val m = safeMargin(margin)
        return points.map { p ->
            if (!p.x.isFinite() || !p.y.isFinite()) {
                InkPoint(p.x, p.y, p.w, p.tilt)
            } else {
                var nx = p.x + dxNorm
                var ny = p.y + dyNorm
                if (clampToBounds) {
                    nx = clampX(nx, m)
                    ny = clampY(ny)
                }
                InkPoint(nx, ny, p.w, p.tilt)
            }
        }
    }

    fun copyStrokesWithOffset(
        strokes: List<ContinuousStroke>,
        offsetXNorm: Float = DEFAULT_COPY_OFFSET_ISO,
        offsetYNorm: Float = DEFAULT_COPY_OFFSET_ISO,
        margin: Float = 0f,
        clampToBounds: Boolean = true
    ): List<ContinuousStroke> {
        if (strokes.isEmpty()) return emptyList()
        val dxRaw = if (offsetXNorm.isFinite()) offsetXNorm else 0f
        val dyRaw = if (offsetYNorm.isFinite()) offsetYNorm else 0f
        val pb = if (clampToBounds) pointsBounds(strokes) else null
        val (dx, dy) = if (pb != null) {
            clampTranslationDelta(pb, dxRaw, dyRaw, margin)
        } else {
            dxRaw to dyRaw
        }
        val perPointClamp = clampToBounds && pb == null
        val m = safeMargin(margin)
        return strokes.map { src ->
            val copiedPts = translatePoints(src.points, dx, dy, m, perPointClamp).toMutableList()
            ContinuousStroke(
                tool = src.tool,
                color = src.color,
                baseWidthNorm = src.baseWidthNorm,
                autoShapeEnabled = src.autoShapeEnabled,
                points = copiedPts
            )
        }
    }

    fun pointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        if (polygon.size < 3 || !x.isFinite() || !y.isFinite()) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if (xi.isFinite() && yi.isFinite() && xj.isFinite() && yj.isFinite()) {
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    fun selectStrokesInLasso(
        strokes: List<ContinuousStroke>,
        polygonNorm: List<Pair<Float, Float>>
    ): List<Int> {
        if (strokes.isEmpty() || polygonNorm.size < 3) return emptyList()
        val picked = mutableListOf<Int>()
        for (idx in strokes.indices) {
            val pts = strokes[idx].points
            if (pts.isEmpty()) continue
            var inside = 0
            for (p in pts) {
                if (pointInPolygon(p.x, p.y, polygonNorm)) inside++
            }
            if (inside * 2 >= pts.size) picked.add(idx)
        }
        return picked
    }

    fun distSqPointToSegmentIso(
        pxNorm: Float,
        pyNorm: Float,
        axNorm: Float,
        ayNorm: Float,
        bxNorm: Float,
        byNorm: Float,
        boxRatio: Float
    ): Float {
        if (!pxNorm.isFinite() || !pyNorm.isFinite() ||
            !axNorm.isFinite() || !ayNorm.isFinite() ||
            !bxNorm.isFinite() || !byNorm.isFinite()
        ) {
            return Float.MAX_VALUE
        }
        val r = safeBoxRatio(boxRatio)
        val ax = axNorm * r
        val ay = ayNorm
        val bx = bxNorm * r
        val by = byNorm
        val px = pxNorm * r
        val py = pyNorm
        val vx = bx - ax
        val vy = by - ay
        val lenSq = vx * vx + vy * vy
        val t = if (lenSq > 1e-12f) {
            (((px - ax) * vx + (py - ay) * vy) / lenSq).coerceIn(0f, 1f)
        } else {
            0f
        }
        val dx = px - (ax + t * vx)
        val dy = py - (ay + t * vy)
        return dx * dx + dy * dy
    }

    fun hitTestSingleStrokeIndex(
        strokes: List<ContinuousStroke>,
        tapXNorm: Float,
        tapYNorm: Float,
        boxRatio: Float,
        toleranceIso: Float
    ): Int {
        if (strokes.isEmpty() || !tapXNorm.isFinite() || !tapYNorm.isFinite() ||
            !toleranceIso.isFinite() || toleranceIso <= 0f
        ) {
            return -1
        }
        val r = safeBoxRatio(boxRatio)
        val tolSq = toleranceIso * toleranceIso
        var bestIdx = -1
        var bestDistSq = Float.MAX_VALUE

        for (idx in strokes.indices.reversed()) {
            val st = strokes[idx]
            val pts = st.points
            if (pts.isEmpty()) continue
            var minStrokeD2 = Float.MAX_VALUE
            if (pts.size == 1) {
                val p0 = pts[0]
                if (p0.x.isFinite() && p0.y.isFinite()) {
                    val dx = (p0.x - tapXNorm) * r
                    val dy = p0.y - tapYNorm
                    minStrokeD2 = dx * dx + dy * dy
                }
            } else {
                for (i in 0 until pts.size - 1) {
                    val a = pts[i]
                    val b = pts[i + 1]
                    val d2 = distSqPointToSegmentIso(tapXNorm, tapYNorm, a.x, a.y, b.x, b.y, r)
                    if (d2 < minStrokeD2) minStrokeD2 = d2
                }
            }
            if (minStrokeD2 <= tolSq && minStrokeD2 < bestDistSq) {
                bestDistSq = minStrokeD2
                bestIdx = idx
            }
        }
        return bestIdx
    }

    fun computeHandleLayout(
        bounds: NormRect,
        boxRatio: Float,
        boxHeightPx: Float,
        scale: Float,
        handleRadiusPx: Float = HANDLE_RADIUS_PX
    ): HandleLayout {
        val r = safeBoxRatio(boxRatio)
        val invScale = 1f / (if (scale.isFinite()) scale.coerceAtLeast(0.1f) else 1f)
        val safeH = if (boxHeightPx.isFinite() && boxHeightPx > 0f) boxHeightPx else 1000f
        val safeRadius = if (handleRadiusPx.isFinite() && handleRadiusPx > 0f) handleRadiusPx else HANDLE_RADIUS_PX
        val gapIso = (safeRadius * 2.4f * invScale) / safeH
        val gapNormX = gapIso / r
        val mirrorX = (bounds.left + gapNormX).coerceAtMost(max(bounds.left + gapNormX * 0.5f, bounds.centerX))

        return HandleLayout(
            bounds = bounds,
            rotateCenter = bounds.left to bounds.top,
            mirrorCenter = mirrorX to bounds.top,
            deleteCenter = bounds.right to bounds.top,
            copyCenter = bounds.left to bounds.bottom,
            scaleCornerBR = bounds.right to bounds.bottom,
            scaleLeft = bounds.left to bounds.centerY,
            scaleRight = bounds.right to bounds.centerY,
            scaleTop = bounds.centerX to bounds.top,
            scaleBottom = bounds.centerX to bounds.bottom
        )
    }

    /**
     * Expands [bounds] symmetrically around its center when a selection is small on screen
     * so corner/edge handles never overlap each other or cover the interior [LassoHandle.MOVE] zone.
     */
    fun expandBoundsForHandles(
        bounds: NormRect,
        boxRatio: Float,
        boxHeightPx: Float,
        scale: Float,
        handleRadiusPx: Float = HANDLE_RADIUS_PX
    ): NormRect {
        if (!bounds.left.isFinite() || !bounds.top.isFinite() ||
            !bounds.right.isFinite() || !bounds.bottom.isFinite()
        ) {
            return bounds
        }
        val r = safeBoxRatio(boxRatio)
        val invScale = 1f / (if (scale.isFinite()) scale.coerceAtLeast(0.1f) else 1f)
        val safeH = if (boxHeightPx.isFinite() && boxHeightPx > 0f) boxHeightPx else 1000f
        val safeRadius = if (handleRadiusPx.isFinite() && handleRadiusPx > 0f) handleRadiusPx else HANDLE_RADIUS_PX
        val minSpanXIso = (safeRadius * 8.8f * invScale) / safeH
        val minSpanYIso = (safeRadius * 5.4f * invScale) / safeH
        val minSpanX = minSpanXIso / r
        val minSpanY = minSpanYIso
        val halfW = max(bounds.width * 0.5f, minSpanX * 0.5f)
        val halfH = max(bounds.height * 0.5f, minSpanY * 0.5f)
        val cx = bounds.centerX
        val cy = bounds.centerY
        return NormRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    fun hitTestHandle(
        bounds: NormRect,
        tapXNorm: Float,
        tapYNorm: Float,
        boxRatio: Float,
        boxHeightPx: Float,
        scale: Float,
        handleRadiusPx: Float = HANDLE_RADIUS_PX
    ): LassoHandle {
        if (!tapXNorm.isFinite() || !tapYNorm.isFinite()) return LassoHandle.NONE
        val r = safeBoxRatio(boxRatio)
        val invScale = 1f / (if (scale.isFinite()) scale.coerceAtLeast(0.1f) else 1f)
        val safeH = if (boxHeightPx.isFinite() && boxHeightPx > 0f) boxHeightPx else 1000f
        val safeRadius = if (handleRadiusPx.isFinite() && handleRadiusPx > 0f) handleRadiusPx else HANDLE_RADIUS_PX
        val hitRIso = (safeRadius * 1.8f * invScale) / safeH
        val hitR2 = hitRIso * hitRIso
        val layout = computeHandleLayout(bounds, r, safeH, scale, safeRadius)

        fun near(pos: Pair<Float, Float>): Boolean {
            val dxIso = (tapXNorm - pos.first) * r
            val dyIso = tapYNorm - pos.second
            return dxIso * dxIso + dyIso * dyIso <= hitR2
        }

        if (near(layout.deleteCenter)) return LassoHandle.DELETE
        if (near(layout.rotateCenter)) return LassoHandle.ROTATE
        if (near(layout.mirrorCenter)) return LassoHandle.MIRROR
        if (near(layout.copyCenter)) return LassoHandle.COPY
        if (near(layout.scaleCornerBR)) return LassoHandle.SCALE_CORNER_BR
        if (near(layout.scaleLeft)) return LassoHandle.SCALE_LEFT
        if (near(layout.scaleRight)) return LassoHandle.SCALE_RIGHT
        if (near(layout.scaleTop)) return LassoHandle.SCALE_TOP
        if (near(layout.scaleBottom)) return LassoHandle.SCALE_BOTTOM
        if (bounds.contains(tapXNorm, tapYNorm)) return LassoHandle.MOVE
        return LassoHandle.NONE
    }
}
