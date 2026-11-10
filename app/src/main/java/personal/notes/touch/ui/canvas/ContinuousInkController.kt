package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.util.PdfDocumentCache
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The tool configuration the reader needs to draw a stroke. It mirrors the
 * fields of [CanvasUiState] that matter for inking; the sizes are in screen
 * pixels at fit-to-width, which is how the single-page canvas shows them too.
 */
data class InkToolSettings(
    val tool: CanvasTool,
    val color: Int,
    val strokeWidthPx: Float,
    val eraserWidthPx: Float,
    val pressureSensitivity: Float,
    val pressureSizeEnabled: Boolean,
    val stylusOnly: Boolean = false,
    val readingMode: Boolean = false,
    val autoShapeEnabled: Boolean = false
) {
    /** Tools that write; lasso/image/text keep opening the page instead. */
    val isWritingTool: Boolean
        get() = when (tool) {
            CanvasTool.FOUNTAIN_PEN, CanvasTool.PEN, CanvasTool.HIGHLIGHTER,
            CanvasTool.ERASER, CanvasTool.SHAPE_RECT,
            CanvasTool.SHAPE_CIRCLE, CanvasTool.SHAPE_LINE -> true
            else -> false
        }

    /** The laser pointer shows a dot instead of writing. */
    val isLaserPointer: Boolean
        get() = tool == CanvasTool.LASER_POINTER
}

/** One sampled point of a stroke, in normalised page coordinates. */
class InkPoint(val x: Float, val y: Float, val w: Float)

/**
 * A stroke in normalised page space: [InkPoint.x] / [InkPoint.y] are 0..1
 * fractions of the page width / height, [InkPoint.w] is the stroke width as a
 * fraction of the page width. Keeping strokes normalised means the same
 * stroke renders identically in the page box, into the ink layer at its own
 * resolution, and after a zoom that changed the render size.
 */
class ContinuousStroke(
    val tool: CanvasTool,
    val color: Int,
    val baseWidthNorm: Float,
    val autoShapeEnabled: Boolean = false,
    val points: MutableList<InkPoint> = mutableListOf()
) {
    val startX: Float get() = points.first().x
    val startY: Float get() = points.first().y
    val endX: Float get() = points.last().x
    val endY: Float get() = points.last().y
}

/** Pixel patch saved before a stroke so it can be undone (and redone). */
internal class InkPatch(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val before: IntArray,
    var after: IntArray? = null
) {
    val bytes: Long get() = w.toLong() * h.toLong() * 4L * (if (after == null) 1 else 2)
}

/**
 * Pure geometry and width helpers for reader inking. Kept free of Bitmap and
 * Canvas so the mapping can be unit-tested: the drawing code itself only adds
 * paint on top of these numbers.
 */
object ContinuousInkMath {

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Bounds of what a stroke touches in layer pixels: the point extent grown
     * by half the widest nib (plus a couple of pixels for the round cap) and
     * clamped to the layer, so a stroke along the edge never indexes outside.
     */
    fun strokeBounds(
        points: List<InkPoint>,
        maxWidthNorm: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
        padFactor: Float = 0.6f
    ): Bounds? {
        if (points.isEmpty() || bitmapWidth <= 0 || bitmapHeight <= 0) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var widest = maxWidthNorm
        for (p in points) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
            widest = max(widest, p.w)
        }
        val pad = widest * bitmapWidth * padFactor + 4f
        val left = (minX * bitmapWidth - pad).toInt().coerceIn(0, bitmapWidth - 1)
        val top = (minY * bitmapHeight - pad).toInt().coerceIn(0, bitmapHeight - 1)
        val right = (maxX * bitmapWidth + pad).toInt().coerceIn(left + 1, bitmapWidth)
        val bottom = (maxY * bitmapHeight + pad).toInt().coerceIn(top + 1, bitmapHeight)
        return Bounds(left, top, right, bottom)
    }

    /** Pressure curve of the single-page fountain pen. */
    fun fountainWidth(
        pressure: Float,
        baseNorm: Float,
        sensitivity: Float,
        sizeEnabled: Boolean
    ): Float {
        if (!sizeEnabled) return baseNorm
        val clamped = pressure.coerceIn(0.01f, 1.0f)
        val exponent = 1.0 / sensitivity.coerceIn(0.2f, 3f)
        val calibrated = clamped.toDouble().pow(exponent).toFloat()
        return baseNorm * (0.35f + calibrated * 1.35f)
    }

    /** One smoothing step, matching the canvas's 0.65 / 0.35 filter. */
    fun smooth(previous: Float, target: Float): Float =
        if (previous > 0f) previous * 0.65f + target * 0.35f else target
}

/**
 * The writable layer of one reader page.
 *
 * Vector pages keep their annotations in a separate stroke layer (as in
 * single-page mode), so [ink] is that layer and [background] is a clean
 * pdfium render of the page. Raster pages have their annotations baked into
 * the page PNG, so [ink] is the page image itself and there is no separate
 * background.
 */
class ContinuousInkSession internal constructor(
    val pageIndex: Int,
    val isVector: Boolean,
    var inkWidth: Int,
    var inkHeight: Int
) {
    internal var ink: Bitmap? = null
    internal var background: Bitmap? = null
    internal var backgroundWidth: Int = 0
    internal var ready: Boolean = false
    internal var loadJob: Job? = null
    internal var saveJob: Job? = null
    internal var dirty: Boolean = false
    internal var savedWhileActive: Boolean = false
    internal var live: ContinuousStroke? = null
    internal val undo = ArrayDeque<InkPatch>()
    internal val redo = ArrayDeque<InkPatch>()
    internal val pending = mutableListOf<ContinuousStroke>()

    /** True once the layer is loaded and displayed; before that the reader's
     *  own page bitmap stays on screen and strokes are buffered. */
    val isReady: Boolean get() = ready

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
}

/**
 * In-place stylus inking for the continuous reader.
 *
 * The reader is a Lazy list of page bitmaps, so writing there needs its own
 * small ink engine: this controller owns one page's writable layer at a time,
 * draws the live stroke into the page box, commits it into the layer, and
 * persists through the same repository calls single-page mode uses
 * (`savePageStrokes` for vector pages, `savePageBitmap` for raster pages).
 *
 * One session, not one per page: a page layer is ~26 MB decoded and the
 * reader keeps two or three pages on screen. The session follows the stylus
 * from page to page; the previous page is flushed and released first.
 *
 * Undo/redo uses dirty-rectangle pixel patches instead of whole-page
 * snapshots, so a short stroke costs a few hundred KB rather than 26 MB.
 */
class ContinuousInkController(
    private val noteId: String,
    private val repository: NoteRepository,
    private val pdfDocCache: PdfDocumentCache,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val onSaved: (pageIndex: Int) -> Unit,
    private val onUndoRedoChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit
) {
    /**
     * Bumped whenever the committed layer changes (a stroke was committed,
     * undone or the session swapped). The page composable reads this, so it
     * must stay rare: reading it per pointer sample would recompose the page
     * on every movement of the pen.
     */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * Bumped on every sample of the stroke in progress. It is read only in
     * the draw phase (the live-stroke canvas), never in composition, so the
     * pen can move without a recomposition per event.
     */
    var previewRevision by mutableIntStateOf(0)
        private set

    var session: ContinuousInkSession? by mutableStateOf(null)
        private set

    private val closingSessions = mutableMapOf<Int, ContinuousInkSession>()

    fun getSession(pageIndex: Int): ContinuousInkSession? {
        val s = session
        if (s != null && s.pageIndex == pageIndex) return s
        return closingSessions[pageIndex]
    }

    fun isActiveSession(pageIndex: Int): Boolean {
        val s = session
        return s != null && s.pageIndex == pageIndex && s.isReady
    }

    /**
     * True from the moment a stylus goes down on a page until it lifts. The
     * reader turns its list scrolling off while this is set: Compose's
     * interop touch bridge deliberately does not consume the initial down, so
     * a scrollable ancestor would otherwise take the gesture away
     * mid-stroke (observed on device: ACTION_CANCEL 12 ms after ACTION_DOWN).
     */
    var isDrawing by mutableStateOf(false)
        private set


    // ---- laser pointer ---------------------------------------------------

    /** True while the laser pointer is on screen. */
    var laserActive by mutableStateOf(false)
        private set

    /** Bumped when the laser moves; read in the draw phase only. */
    var laserRevision by mutableIntStateOf(0)
        private set

    /** The page the laser is currently on. */
    var laserPageIndex by mutableIntStateOf(-1)
        private set

    /** The laser trail in page-normalised coordinates. */
    private val laserTrail = mutableStateListOf<LaserSample>()

    /** Moves the laser dot; nothing is written to the page. */
    fun updateLaser(pageIndex: Int, x: Float, y: Float) {
        laserPageIndex = pageIndex
        laserTrail.add(LaserSample(x, y, System.currentTimeMillis()))
        while (laserTrail.size > 40) laserTrail.removeAt(0)
        laserActive = true
        // The laser is only read in the draw phase, so it must not bump the
        // revision that the composition observes (that would recompose the
        // page on every pointer event).
        laserRevision++
    }

    /** The laser trail with the time of each sample. */
    private data class LaserSample(val x: Float, val y: Float, val time: Long)

    /** Hides the laser dot. */
    fun clearLaser() {
        if (laserTrail.isEmpty() && !laserActive) return
        laserTrail.clear()
        laserPageIndex = -1
        laserActive = false
        revision++
    }

    // ---- touch ----------------------------------------------------------

    /**
     * Starts a pen stroke at a point in page-normalised (0..1) coordinates.
     *
     * The reader feeds this from Compose's pointer pipeline rather than from
     * a MotionEvent bridge: a bridge cannot stop an enclosing scrollable from
     * taking the gesture (it deliberately leaves the initial down
     * unconsumed), while a pointer handler in the page item can consume the
     * whole event stream before the list sees it.
     */
    fun beginStroke(
        page: PageEntity,
        pageIndex: Int,
        x: Float,
        y: Float,
        pressure: Float,
        boxWidth: Float,
        boxHeight: Float,
        renderWidthPx: Int,
        settings: InkToolSettings
    ): Boolean {
        if (!settings.isWritingTool) return false
        if (boxWidth <= 0f || boxHeight <= 0f) return false
        val s = ensureSession(page, pageIndex, boxWidth, boxHeight, renderWidthPx)
        val widthNorm = if (settings.tool == CanvasTool.ERASER) {
            settings.eraserWidthPx / boxWidth
        } else {
            settings.strokeWidthPx / boxWidth
        }
        val stroke = ContinuousStroke(settings.tool, settings.color, widthNorm, settings.autoShapeEnabled)
        addPoint(stroke, x, y, pressure, settings)
        s.live = stroke
        isDrawing = true
        revision++
        return true
    }

    /** Adds a point to the stroke in progress (page-normalised). */
    fun extendStroke(x: Float, y: Float, pressure: Float, settings: InkToolSettings) {
        val stroke = session?.live ?: return
        addPoint(stroke, x, y, pressure, settings)
        previewRevision++
    }

    /** Lifts the pen: the stroke joins the page's ink layer and is saved. */
    fun endStroke(x: Float, y: Float, pressure: Float, settings: InkToolSettings) {
        val s = session ?: return
        isDrawing = false
        val stroke = s.live ?: return
        addPoint(stroke, x, y, pressure, settings)
        s.live = null
        finishStroke(s, stroke, settings.autoShapeEnabled)
        revision++
    }

    /** The gesture was taken away (mode exit, page change): drop the stroke. */
    fun cancelStroke() {
        val s = session ?: return
        isDrawing = false
        s.live = null
        revision++
    }

    private fun addPoint(
        stroke: ContinuousStroke,
        x: Float,
        y: Float,
        pressure: Float,
        settings: InkToolSettings
    ) {
        val cx = x.coerceIn(0f, 1f)
        val cy = y.coerceIn(0f, 1f)
        val w = if (stroke.tool == CanvasTool.FOUNTAIN_PEN) {
            fountainWidth(pressure, stroke.baseWidthNorm, settings, stroke.points.lastOrNull()?.w)
        } else {
            stroke.baseWidthNorm
        }
        val last = stroke.points.lastOrNull()
        if (last != null) {
            val dx = cx - last.x
            val dy = cy - last.y
            // ~2 px on a 2160 px page: dense enough to look smooth, sparse
            // enough to keep the path small while the pen moves quickly.
            if (dx * dx + dy * dy < 0.0000012f) return
        }
        stroke.points.add(InkPoint(cx, cy, w))
    }

    /** Same pressure curve the single-page fountain pen uses. */
    private fun fountainWidth(
        pressure: Float,
        baseNorm: Float,
        settings: InkToolSettings,
        last: Float?
    ): Float {
        val target = ContinuousInkMath.fountainWidth(
            pressure, baseNorm, settings.pressureSensitivity, settings.pressureSizeEnabled
        )
        return last?.let { ContinuousInkMath.smooth(it, target) } ?: target
    }

    // ---- session lifecycle ---------------------------------------------

    private fun ensureSession(
        page: PageEntity,
        pageIndex: Int,
        boxWidth: Float,
        boxHeight: Float,
        renderWidthPx: Int
    ): ContinuousInkSession {
        val existing = session
        if (existing != null && existing.pageIndex == pageIndex) return existing
        if (existing != null) {
            flushNow(existing)
        }
        val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
        // Until the layer is decoded we only need a plausible resolution for
        // the live preview; the real size comes from the file (raster) or the
        // page geometry (vector, 2160 px wide — the single-page convention).
        val ratio = if (boxWidth > 0f) boxHeight / boxWidth else 1.414f
        val inkWidth = 2160
        val inkHeight = (inkWidth * ratio).roundToInt().coerceAtLeast(1)
        val created = ContinuousInkSession(pageIndex, isVector, inkWidth, inkHeight)
        session = created
        onUndoRedoChanged(false, false)
        startLoad(created, page, renderWidthPx)
        return created
    }

    private fun startLoad(s: ContinuousInkSession, page: PageEntity, renderWidthPx: Int) {
        if (s.loadJob != null) return
        s.loadJob = scope.launch {
            try {
                val ink = withContext(Dispatchers.IO) {
                    if (s.isVector) {
                        val strokes = repository.loadPageStrokes(noteId, s.pageIndex)
                        if (strokes != null) mutableCopy(strokes)
                        else Bitmap.createBitmap(
                            s.inkWidth, s.inkHeight, Bitmap.Config.ARGB_8888
                        )
                    } else {
                        val pageBmp = repository.loadPageBitmap(noteId, s.pageIndex)
                        if (pageBmp != null) mutableCopy(pageBmp)
                        else Bitmap.createBitmap(
                            s.inkWidth, s.inkHeight, Bitmap.Config.ARGB_8888
                        )
                    }
                }
                if (session !== s) {
                    ink.recycle()
                    return@launch
                }
                s.ink = ink
                s.inkWidth = ink.width
                s.inkHeight = ink.height
                if (s.isVector) {
                    s.background = withContext(Dispatchers.IO) {
                        renderContinuousPage(
                            page, s.pageIndex, noteId, pdfDocCache, repository,
                            renderWidthPx.coerceAtLeast(720), includeInk = false
                        )
                    }
                    s.backgroundWidth = renderWidthPx
                }
                // Strokes drawn while the layer decoded: commit them now, in
                // order, so the first stroke is not lost to the load.
                val queued = s.pending.toList()
                s.pending.clear()
                for (stroke in queued) commit(s, stroke, stroke.autoShapeEnabled)
                s.ready = true
                if (s.dirty) scheduleSave(s)
                onUndoRedoChanged(s.canUndo, s.canRedo)
                revision++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Re-renders the clean page under the ink when the zoom changed enough. */
    fun refreshBackground(page: PageEntity, pageIndex: Int, renderWidthPx: Int) {
        val s = session ?: return
        if (s.pageIndex != pageIndex) return
        if (!s.isVector || !s.ready) return
        if (renderWidthPx <= 0) return
        val current = s.backgroundWidth
        if (current > 0 && renderWidthPx <= current * 1.15f && renderWidthPx >= current / 1.15f) {
            return
        }
        s.backgroundWidth = renderWidthPx
        scope.launch {
            val bg = withContext(Dispatchers.IO) {
                renderContinuousPage(
                    page, s.pageIndex, noteId, pdfDocCache, repository,
                    renderWidthPx, includeInk = false
                )
            }
            if (session === s && bg != null) {
                s.background = bg
                revision++
            } else {
                bg?.recycle()
            }
        }
    }

    // ---- commit / undo -------------------------------------------------

    private fun finishStroke(s: ContinuousInkSession, stroke: ContinuousStroke, autoShapeEnabled: Boolean = stroke.autoShapeEnabled) {
        if (!s.ready) {
            s.pending.add(stroke)
            return
        }
        commit(s, stroke, autoShapeEnabled)
        scheduleSave(s)
        onUndoRedoChanged(s.canUndo, s.canRedo)
    }

    private fun commit(s: ContinuousInkSession, stroke: ContinuousStroke, autoShapeEnabled: Boolean = false) {
        val ink = s.ink ?: return
        if (ink.isRecycled) return
        var shape: Path? = null
        if (autoShapeEnabled && (stroke.tool == CanvasTool.PEN || stroke.tool == CanvasTool.FOUNTAIN_PEN)) {
            val pts = stroke.points.map { (it.x * s.inkWidth) to (it.y * s.inkHeight) }
            shape = autoShape(pts)
        }
        var bounds = strokeBounds(stroke, s.inkWidth, s.inkHeight) ?: return
        if (shape != null) {
            val shapeRectF = RectF()
            @Suppress("DEPRECATION")
            shape.computeBounds(shapeRectF, true)
            val strokePad = stroke.baseWidthNorm * s.inkWidth * 0.6f + 4f
            val sLeft = (shapeRectF.left - strokePad).toInt().coerceIn(0, s.inkWidth - 1)
            val sTop = (shapeRectF.top - strokePad).toInt().coerceIn(0, s.inkHeight - 1)
            val sRight = (shapeRectF.right + strokePad).toInt().coerceIn(sLeft + 1, s.inkWidth)
            val sBottom = (shapeRectF.bottom + strokePad).toInt().coerceIn(sTop + 1, s.inkHeight)
            bounds = Rect(
                min(bounds.left, sLeft),
                min(bounds.top, sTop),
                max(bounds.right, sRight),
                max(bounds.bottom, sBottom)
            )
        }
        val patch = snapshot(ink, bounds) ?: return
        val canvas = Canvas(ink)
        if (shape != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = stroke.color
                strokeWidth = stroke.baseWidthNorm * s.inkWidth
            }
            canvas.drawPath(shape, paint)
        } else {
            drawStroke(canvas, stroke, s.inkWidth.toFloat(), s.inkHeight.toFloat(), preview = false)
        }
        patch.after = capture(ink, bounds)
        s.undo.addLast(patch)
        s.redo.clear()
        trimUndo(s)
        s.dirty = true
    }

    private fun autoShape(points: List<Pair<Float, Float>>): Path? {
        if (points.size < 4) return null
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val minX = xs.minOrNull() ?: return null
        val maxX = xs.maxOrNull() ?: return null
        val minY = ys.minOrNull() ?: return null
        val maxY = ys.maxOrNull() ?: return null
        val w = maxX - minX
        val h = maxY - minY
        if (w < 5f || h < 5f) return null
        val aspect = w / h
        val first = points.first()
        val last = points.last()
        val dist = Math.hypot((last.first - first.first).toDouble(), (last.second - first.second).toDouble()).toFloat()
        if (points.size <= 8 || dist > (w + h) * 0.8f && (aspect > 4f || aspect < 0.25f)) {
            if (aspect > 4f || aspect < 0.25f) {
                val p = Path()
                p.moveTo(first.first, first.second)
                p.lineTo(last.first, last.second)
                return p
            }
        }
        val closed = dist < Math.max(w, h) * 0.25f
        if (closed) {
            if (aspect in 0.7f..1.3f) {
                val cx = (minX + maxX) / 2f
                val cy = (minY + maxY) / 2f
                val r = min(w, h) / 2f
                val p = Path()
                p.addCircle(cx, cy, r, Path.Direction.CW)
                return p
            }
            val p = Path()
            p.addRect(RectF(minX, minY, maxX, maxY), Path.Direction.CW)
            return p
        }
        return null
    }

    fun undo(): Boolean {
        val s = session ?: return false
        val patch = s.undo.removeLastOrNull() ?: return false
        val ink = s.ink
        if (ink != null && !ink.isRecycled) {
            restore(ink, patch, useAfter = false)
            s.dirty = true
            scheduleSave(s)
        }
        s.redo.addLast(patch)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    fun redo(): Boolean {
        val s = session ?: return false
        val patch = s.redo.removeLastOrNull() ?: return false
        val ink = s.ink
        if (ink != null && !ink.isRecycled) {
            restore(ink, patch, useAfter = true)
            s.dirty = true
            scheduleSave(s)
        }
        s.undo.addLast(patch)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /** Clears in-place ink on a specific page, supporting undo. */
    fun clearPage(pageIndex: Int, page: PageEntity? = null, renderWidthPx: Int = 2160): Boolean {
        val s = session
        if (s != null && s.pageIndex == pageIndex) {
            return if (s.ready) {
                clearSession(s)
            } else {
                scope.launch {
                    s.loadJob?.join()
                    if (session === s && s.ready) {
                        clearSession(s)
                    }
                }
                true
            }
        }
        if (s != null) {
            flushNow(s)
            releaseBitmaps(s)
            session = null
            onUndoRedoChanged(false, false)
        }
        if (page == null) return false
        val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
        val created = ContinuousInkSession(pageIndex, isVector, 2160, 3056)
        session = created
        onUndoRedoChanged(false, false)
        startLoad(created, page, renderWidthPx)
        scope.launch {
            created.loadJob?.join()
            if (session === created && created.ready) {
                clearSession(created)
            }
        }
        return true
    }

    /** Clears in-place ink on the active session, supporting undo. */
    fun clearCurrentPage(): Boolean = session?.let { clearSession(it) } ?: false

    private fun clearSession(s: ContinuousInkSession): Boolean {
        val ink = s.ink ?: return false
        if (ink.isRecycled) return false
        val contentBounds = findContentBounds(ink)
        val patch = if (contentBounds != null) {
            snapshot(ink, contentBounds)
        } else null
        ink.eraseColor(android.graphics.Color.TRANSPARENT)
        if (patch != null) {
            // Note: patch.after is deliberately left null to avoid allocating a second
            // full/large array filled with zeroes. restore() handles null useAfter by clearing the rect.
            s.undo.addLast(patch)
            s.redo.clear()
            trimUndo(s)
        }
        s.dirty = true
        scheduleSave(s)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    private fun findContentBounds(bitmap: Bitmap): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        var top = -1
        val row = IntArray(w)
        for (y in 0 until h) {
            try {
                bitmap.getPixels(row, 0, w, 0, y, w, 1)
            } catch (t: Throwable) {
                return Rect(0, 0, w, h)
            }
            for (x in 0 until w) {
                if (row[x] ushr 24 != 0) {
                    top = y
                    break
                }
            }
            if (top != -1) break
        }
        if (top == -1) return null // Completely transparent

        var bottom = h - 1
        for (y in h - 1 downTo top) {
            try {
                bitmap.getPixels(row, 0, w, 0, y, w, 1)
            } catch (t: Throwable) {
                return Rect(0, 0, w, h)
            }
            for (x in 0 until w) {
                if (row[x] ushr 24 != 0) {
                    bottom = y
                    break
                }
            }
            if (bottom != h - 1) break
        }

        var left = w
        var right = 0
        val colH = bottom - top + 1
        val col = IntArray(colH)
        for (x in 0 until w) {
            try {
                bitmap.getPixels(col, 0, 1, x, top, 1, colH)
            } catch (t: Throwable) {
                return Rect(0, 0, w, h)
            }
            for (y in 0 until colH) {
                if (col[y] ushr 24 != 0) {
                    left = x
                    break
                }
            }
            if (left != w) break
        }

        for (x in w - 1 downTo left) {
            try {
                bitmap.getPixels(col, 0, 1, x, top, 1, colH)
            } catch (t: Throwable) {
                return Rect(0, 0, w, h)
            }
            for (y in 0 until colH) {
                if (col[y] ushr 24 != 0) {
                    right = x
                    break
                }
            }
            if (right != 0) break
        }

        return if (right >= left && bottom >= top) {
            Rect(left, top, right + 1, bottom + 1)
        } else {
            null
        }
    }

    private fun capture(bitmap: Bitmap, r: Rect): IntArray? {
        if (r.width() <= 0 || r.height() <= 0) return null
        return try {
            val pixels = IntArray(r.width() * r.height())
            bitmap.getPixels(pixels, 0, r.width(), r.left, r.top, r.width(), r.height())
            pixels
        } catch (t: Throwable) {
            null
        }
    }

    private fun snapshot(bitmap: Bitmap, r: Rect): InkPatch? {
        val before = capture(bitmap, r) ?: return null
        return InkPatch(r.left, r.top, r.width(), r.height(), before)
    }

    private fun restore(bitmap: Bitmap, patch: InkPatch, useAfter: Boolean) {
        val pixels = if (useAfter) patch.after else patch.before
        if (pixels == null) {
            if (useAfter) {
                try {
                    val c = Canvas(bitmap)
                    val p = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                    c.drawRect(
                        RectF(
                            patch.x.toFloat(),
                            patch.y.toFloat(),
                            (patch.x + patch.w).toFloat(),
                            (patch.y + patch.h).toFloat()
                        ),
                        p
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }
        try {
            bitmap.setPixels(pixels, 0, patch.w, patch.x, patch.y, patch.w, patch.h)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Bounds of what a stroke touches, in ink pixels, clamped to the layer. */
    private fun strokeBounds(stroke: ContinuousStroke, w: Int, h: Int): Rect? {
        val effectiveWidthNorm = if (stroke.tool == CanvasTool.HIGHLIGHTER) {
            stroke.baseWidthNorm * 3.2f
        } else {
            stroke.baseWidthNorm
        }
        val padFactor = if (stroke.tool == CanvasTool.HIGHLIGHTER) 0.85f else 0.6f
        val bounds = ContinuousInkMath.strokeBounds(
            stroke.points, effectiveWidthNorm, w, h, padFactor
        ) ?: return null
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun trimUndo(s: ContinuousInkSession) {
        var total = s.undo.sumOf { it.bytes }
        while (total > MAX_UNDO_BYTES && s.undo.size > 1) {
            total -= s.undo.removeFirst().bytes
        }
    }

    // ---- saving --------------------------------------------------------

    private fun scheduleSave(s: ContinuousInkSession) {
        s.saveJob?.cancel()
        s.saveJob = scope.launch {
            delay(700)
            persist(s)
        }
    }

    private suspend fun persist(s: ContinuousInkSession) {
        val ink = s.ink
        if (ink == null || ink.isRecycled) return
        // The page is only "clean" when the write actually succeeded: a
        // failed write must stay dirty so the next save tries again.
        s.dirty = false
        // Copy on the main thread (drawing also happens there); the PNG
        // encoder then reads the copy off-thread, so a stroke landing during
        // the encode can never tear the file.
        val copy = try {
            ink.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            s.dirty = true
            return
        } ?: run { s.dirty = true; return }
        val ok = withContext(Dispatchers.IO) {
            if (s.isVector) {
                repository.savePageStrokes(noteId, s.pageIndex, copy)
            } else {
                repository.savePageBitmap(noteId, s.pageIndex, copy)
            }
        }
        copy.recycle()
        if (!ok) {
            // The write failed: keep the session dirty so the next autosave
            // or the flush on exit tries again.
            s.dirty = true
            android.util.Log.w("TouchNotes", "Could not save page ${s.pageIndex}")
            return
        }
        s.savedWhileActive = true
        onSaved(s.pageIndex)
    }

    /** Writes a dirty session immediately (page switch, mode exit, dispose). */
    fun flushNow(s: ContinuousInkSession) {
        s.saveJob?.cancel()
        s.saveJob = null
        if (!s.dirty) {
            if (s.savedWhileActive) {
                // Was already saved to disk while active; keep in closingSessions briefly so
                // page switch does not flicker before disk reload takes effect.
                closingSessions[s.pageIndex] = s
                appScope.launch {
                    kotlinx.coroutines.delay(120)
                    withContext(Dispatchers.Main) {
                        closingSessions.remove(s.pageIndex)
                        releaseBitmaps(s)
                        revision++
                        onSaved(s.pageIndex)
                    }
                }
            } else {
                releaseBitmaps(s)
            }
            return
        }
        val ink = s.ink ?: run {
            releaseBitmaps(s)
            return
        }
        if (ink.isRecycled) {
            releaseBitmaps(s)
            return
        }
        s.dirty = false
        s.savedWhileActive = true
        val copy = try {
            ink.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: OutOfMemoryError) {
            null
        } ?: run {
            releaseBitmaps(s)
            return
        }
        closingSessions[s.pageIndex] = s
        appScope.launch {
            withContext(Dispatchers.IO) {
                if (s.isVector) {
                    repository.savePageStrokes(noteId, s.pageIndex, copy)
                } else {
                    repository.savePageBitmap(noteId, s.pageIndex, copy)
                }
            }
            copy.recycle()
            withContext(Dispatchers.Main) {
                closingSessions.remove(s.pageIndex)
                releaseBitmaps(s)
                revision++
                onSaved(s.pageIndex)
            }
        }
    }

    /** Writes the current session synchronously on IO (suspends until saved). */
    suspend fun flushCurrentSuspend() {
        val s = session ?: return
        s.saveJob?.cancel()
        s.saveJob = null
        if (!s.dirty) return
        persist(s)
    }

    /** Flushes and cleans up the session synchronously on IO. */
    suspend fun releaseSessionSuspend() {
        val s = session ?: return
        session = null
        s.saveJob?.cancel()
        s.loadJob?.cancel()
        if (s.dirty) {
            persist(s)
        }
        releaseBitmaps(s)
        revision++
        onUndoRedoChanged(false, false)
    }

    /** Writes the current session now (called before leaving the reader). */
    fun flushCurrent() {
        session?.let { flushNow(it) }
    }

    /** Leaves continuous mode: flush, free the layer, restore the toolbar. */
    fun releaseSession() {
        val s = session ?: return
        session = null
        if (s.dirty) {
            val ink = s.ink
            if (ink != null && !ink.isRecycled) {
                s.dirty = false
                val copy = try {
                    ink.copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: OutOfMemoryError) {
                    null
                }
                if (copy != null) {
                    appScope.launch {
                        withContext(Dispatchers.IO) {
                            if (s.isVector) {
                                repository.savePageStrokes(noteId, s.pageIndex, copy)
                            } else {
                                repository.savePageBitmap(noteId, s.pageIndex, copy)
                            }
                        }
                        copy.recycle()
                        onSaved(s.pageIndex)
                    }
                }
            }
        }
        s.saveJob?.cancel()
        s.loadJob?.cancel()
        releaseBitmaps(s)
        revision++
        onUndoRedoChanged(false, false)
    }

    /** Cancels all active loads/saves and releases sessions without saving. */
    fun discardAll() {
        cancelStroke()
        val s = session
        session = null
        val closing = closingSessions.values.toList()
        closingSessions.clear()
        val allSessions = (listOfNotNull(s) + closing).distinct()
        for (sess in allSessions) {
            sess.saveJob?.cancel()
            sess.loadJob?.cancel()
            sess.dirty = false
            sess.savedWhileActive = false
            sess.pending.clear()
            releaseBitmaps(sess)
        }
        onUndoRedoChanged(false, false)
    }

    /** Called from the screen's onDispose with an app-lifetime scope. */
    fun dispose(appScope: CoroutineScope) {
        val s = session
        session = null
        val closing = closingSessions.values.toList()
        closingSessions.clear()
        val allSessions = (listOfNotNull(s) + closing).distinct()
        for (sess in allSessions) {
            sess.saveJob?.cancel()
            sess.loadJob?.cancel()
            val ink = sess.ink
            if (sess.dirty && ink != null && !ink.isRecycled) {
                val copy = try {
                    ink.copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: OutOfMemoryError) {
                    null
                }
                if (copy != null) {
                    appScope.launch {
                        withContext(Dispatchers.IO) {
                            if (sess.isVector) {
                                repository.savePageStrokes(noteId, sess.pageIndex, copy)
                            } else {
                                repository.savePageBitmap(noteId, sess.pageIndex, copy)
                            }
                        }
                        copy.recycle()
                    }
                }
            }
            releaseBitmaps(sess)
        }
    }

    private fun releaseBitmaps(s: ContinuousInkSession) {
        // Do not call .recycle() on s.ink or s.background: they may still be
        // actively rendered or queued in Compose frames (Image/Canvas drawImage).
        // Clearing references allows GC and native finalizers to safely reclaim them.
        s.ink = null
        s.background = null
    }

    // ---- drawing -------------------------------------------------------

    /**
     * Draws the live (uncommitted) stroke into the page box. Public because the
     * reader draws it from a Compose Canvas, above the page bitmap.
     */
    fun drawPreview(canvas: Canvas, pageIndex: Int, viewWidth: Float, viewHeight: Float) {
        // The laser dot belongs to the page the pointer is on.
        if (laserPageIndex == pageIndex) {
            drawLaser(canvas, viewWidth, viewHeight)
        }
        val s = session ?: return
        if (s.pageIndex != pageIndex) return
        val stroke = s.live ?: return
        drawStroke(canvas, stroke, viewWidth, viewHeight, preview = true)
    }

    /** The red laser dot with its short tail. */
    private fun drawLaser(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        if (!laserActive || laserTrail.isEmpty()) return
        // The tail fades out, like the single-page laser.
        val now = System.currentTimeMillis()
        while (laserTrail.isNotEmpty() && now - laserTrail.first().time > LASER_TRAIL_MS) {
            laserTrail.removeAt(0)
        }
        if (laserTrail.isEmpty()) return
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0xFFFF3B30.toInt()
            alpha = 120
            strokeWidth = 6f * (viewWidth / 1600f).coerceAtLeast(0.6f)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFF3B30.toInt()
        }
        for (i in 1 until laserTrail.size) {
            val a = laserTrail[i - 1]
            val b = laserTrail[i]
            val age = (now - b.time).toFloat().coerceAtMost(LASER_TRAIL_MS.toFloat())
            glow.alpha = (120f * (1f - age / LASER_TRAIL_MS)).toInt().coerceIn(20, 120)
            canvas.drawLine(
                a.x * viewWidth, a.y * viewHeight,
                b.x * viewWidth, b.y * viewHeight,
                glow
            )
        }
        val head = laserTrail.last()
        val radius = 7f * (viewWidth / 1600f).coerceAtLeast(0.6f)
        canvas.drawCircle(head.x * viewWidth, head.y * viewHeight, radius, dot)
    }

    private fun drawStroke(
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
                    // The real erase clears pixels, which would punch a hole
                    // through the Compose layer; show a grey swath instead.
                    paint.color = 0x44222222
                    paint.strokeWidth = widthPx
                    canvas.drawPath(smoothPath(stroke, w, h), paint)
                } else {
                    paint.strokeWidth = widthPx
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    canvas.drawPath(smoothPath(stroke, w, h), paint)
                }
            }
            CanvasTool.FOUNTAIN_PEN -> {
                // Piecewise width, round caps: the dense sampling makes the
                // joins invisible, and the pressure taper stays.
                paint.strokeWidth = widthPx
                var previous = stroke.points.first()
                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    paint.strokeWidth = ((previous.w + p.w) / 2f) * w
                    canvas.drawLine(
                        previous.x * w, previous.y * h,
                        p.x * w, p.y * h, paint
                    )
                    previous = p
                }
            }
            else -> {
                paint.strokeWidth = widthPx
                canvas.drawPath(smoothPath(stroke, w, h), paint)
            }
        }
    }

    /** Quadratic midpoint path, the same smoothing single-page mode uses. */
    private fun smoothPath(stroke: ContinuousStroke, w: Float, h: Float): Path {
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

    private fun mutableCopy(bitmap: Bitmap): Bitmap {
        if (bitmap.isMutable && bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private companion object {
        /** How long the laser tail stays visible, in milliseconds. */
        const val LASER_TRAIL_MS = 700L
        /** ~48 MB of patches: a few dozen ordinary strokes, bounded. */
        const val MAX_UNDO_BYTES = 48L * 1024L * 1024L
    }
}
