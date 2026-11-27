package personal.notes.touch.ui.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared stroke & brush renderer used by both [ContinuousInkController] and
 * [personal.notes.touch.util.ExportManager] so exported vector annotations
 * preserve Fountain Pen, Pen, Pencil (3-layer graphite), Highlighter, Eraser,
 * Shapes, and quadratic midpoint smoothing identically to on-screen rendering.
 */
internal object ContinuousStrokeRenderer {

    fun drawStroke(
        canvas: Canvas,
        stroke: ContinuousStroke,
        w: Float,
        h: Float,
        preview: Boolean
    ) {
        if (stroke.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
        }
        val widthPx = stroke.baseWidthNorm * w
        when (stroke.tool) {
            CanvasTool.SHAPE_RECT -> {
                paint.strokeWidth = widthPx
                canvas.drawRect(
                    min(stroke.startX, stroke.endX) * w,
                    min(stroke.startY, stroke.endY) * h,
                    max(stroke.startX, stroke.endX) * w,
                    max(stroke.startY, stroke.endY) * h,
                    paint
                )
            }
            CanvasTool.SHAPE_CIRCLE -> {
                paint.strokeWidth = widthPx
                val cx = (stroke.startX + stroke.endX) / 2f * w
                val cy = (stroke.startY + stroke.endY) / 2f * h
                val rx = (stroke.endX - stroke.startX) / 2f * w
                val ry = (stroke.endY - stroke.startY) / 2f * h
                canvas.drawOval(
                    cx - kotlin.math.abs(rx), cy - kotlin.math.abs(ry),
                    cx + kotlin.math.abs(rx), cy + kotlin.math.abs(ry),
                    paint
                )
            }
            CanvasTool.SHAPE_LINE -> {
                paint.strokeWidth = widthPx
                canvas.drawLine(
                    stroke.startX * w, stroke.startY * h,
                    stroke.endX * w, stroke.endY * h, paint
                )
            }
            CanvasTool.HIGHLIGHTER -> {
                paint.strokeWidth = widthPx * 3.2f
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.BEVEL
                paint.alpha = 110
                canvas.drawPath(smoothPath(stroke, w, h), paint)
            }
            CanvasTool.ERASER -> {
                if (preview) {
                    paint.color = 0x44222222
                    paint.strokeWidth = widthPx
                    canvas.drawPath(smoothPath(stroke, w, h), paint)
                } else {
                    paint.strokeWidth = widthPx
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    canvas.drawPath(smoothPath(stroke, w, h), paint)
                }
            }
            CanvasTool.FOUNTAIN_PEN, CanvasTool.PEN -> {
                val pts = stroke.points
                val count = pts.size
                paint.style = Paint.Style.FILL
                val cached1 = stroke.cachedPath1
                val canUseCache = !preview &&
                    stroke.cachedCount == count &&
                    stroke.cachedW == w &&
                    stroke.cachedH == h &&
                    cached1 != null
                val ribbon = if (canUseCache && cached1 != null) {
                    cached1
                } else {
                    val xs = FloatArray(count) { pts[it].x * w }
                    val ys = FloatArray(count) { pts[it].y * h }
                    val ws = FloatArray(count) { (pts[it].w * w).coerceAtLeast(1f) }
                    val built = PencilGrainShader.buildVariableWidthRibbon(
                        xs, ys, ws, count, widthMultiplier = 1f, taperEnds = true
                    )
                    if (!preview) {
                        stroke.cachedW = w
                        stroke.cachedH = h
                        stroke.cachedCount = count
                        stroke.cachedPath1 = built
                    }
                    built
                }
                canvas.drawPath(ribbon, paint)
            }
            CanvasTool.PENCIL -> {
                val pts = stroke.points
                val count = pts.size
                val cached1 = stroke.cachedPath1
                val cached2 = stroke.cachedPath2
                val canUseCache = !preview &&
                    stroke.cachedCount == count &&
                    stroke.cachedW == w &&
                    stroke.cachedH == h &&
                    cached1 != null &&
                    cached2 != null
                val avgTilt: Float
                val pathEdge: Path
                val pathMid: Path
                val pathCore: Path?
                if (canUseCache && cached1 != null && cached2 != null) {
                    avgTilt = stroke.cachedAvgTilt
                    pathEdge = cached1
                    pathMid = cached2
                    pathCore = stroke.cachedPath3
                } else {
                    val xs = FloatArray(count) { pts[it].x * w }
                    val ys = FloatArray(count) { pts[it].y * h }
                    val ws = FloatArray(count) { (pts[it].w * w).coerceAtLeast(1.2f) }
                    var tiltSum = 0f
                    for (i in 0 until count) tiltSum += pts[i].tilt
                    avgTilt = (tiltSum / count.coerceAtLeast(1)).coerceIn(0f, 1f)
                    pathEdge = PencilGrainShader.buildVariableWidthRibbon(
                        xs, ys, ws, count, widthMultiplier = 1.14f, taperEnds = false
                    )
                    pathMid = PencilGrainShader.buildVariableWidthRibbon(
                        xs, ys, ws, count, widthMultiplier = 0.80f, taperEnds = true
                    )
                    pathCore = PencilGrainShader.buildVariableWidthRibbon(
                        xs, ys, ws, count, widthMultiplier = 0.45f * (1f - 0.30f * avgTilt), taperEnds = true
                    )
                    if (!preview) {
                        stroke.cachedW = w
                        stroke.cachedH = h
                        stroke.cachedCount = count
                        stroke.cachedAvgTilt = avgTilt
                        stroke.cachedPath1 = pathEdge
                        stroke.cachedPath2 = pathMid
                        stroke.cachedPath3 = pathCore
                    }
                }

                val baseAlpha = android.graphics.Color.alpha(stroke.color).coerceIn(24, 255)
                paint.style = Paint.Style.FILL

                paint.shader = PencilGrainShader.shaderEdge
                paint.alpha = (baseAlpha * (0.28f + 0.16f * avgTilt)).roundToInt().coerceIn(10, 255)
                canvas.drawPath(pathEdge, paint)

                paint.shader = PencilGrainShader.shaderMid
                paint.alpha = (baseAlpha * (0.58f - 0.18f * avgTilt)).roundToInt().coerceIn(18, 255)
                canvas.drawPath(pathMid, paint)

                val coreAlpha = (baseAlpha * (0.78f - 0.52f * avgTilt)).roundToInt()
                if (coreAlpha >= 14 && pathCore != null) {
                    paint.shader = PencilGrainShader.shaderCore
                    paint.alpha = coreAlpha.coerceIn(14, 255)
                    canvas.drawPath(pathCore, paint)
                }
            }
            CanvasTool.SHAPE_FILL -> {
                if (stroke.points.size < 3) {
                    paint.style = Paint.Style.FILL
                    val pt = stroke.points.first()
                    canvas.drawCircle(pt.x * w, pt.y * h, (widthPx * 0.5f).coerceAtLeast(2f), paint)
                } else {
                    paint.style = Paint.Style.FILL
                    val path = smoothPath(stroke, w, h)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }
            else -> {
                paint.strokeWidth = widthPx
                canvas.drawPath(smoothPath(stroke, w, h), paint)
            }
        }
    }

    /** Quadratic midpoint path, matching single-page mode smoothing. */
    fun smoothPath(stroke: ContinuousStroke, w: Float, h: Float): Path {
        val path = Path()
        val points = stroke.points
        if (points.isEmpty()) return path
        path.moveTo(points[0].x * w, points[0].y * h)
        if (points.size == 1) {
            path.lineTo(points[0].x * w + 0.01f, points[0].y * h)
            return path
        }
        for (i in 1 until points.size - 1) {
            val p = points[i]
            val n = points[i + 1]
            path.quadTo(
                p.x * w, p.y * h,
                (p.x + n.x) / 2f * w, (p.y + n.y) / 2f * h
            )
        }
        val last = points.last()
        path.lineTo(last.x * w, last.y * h)
        return path
    }
}
