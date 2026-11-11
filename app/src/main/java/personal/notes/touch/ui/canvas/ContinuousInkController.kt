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
 * One committed operation of the ink layer.
 *
 * A [Stroke] is kept as a vector: it is drawn as a path on top of the page
 * image and never touches the session bitmap, so the reader does not have to
 * re-upload the 26 MB layer after every finished stroke.
 *
 * An [Erase] is the one exception - it clears pixels, so it is rasterised into
 * the session bitmap as well (the ink layer stays the only thing the eraser
 * can touch) and it stays in the list to cut the strokes drawn before it.
 */
internal sealed class InkOp {
    /** A finished stroke, with the auto-shape path when one was computed. */
    class Stroke(val stroke: ContinuousStroke, val shape: Path?) : InkOp()

    /** An eraser stroke; [patch] holds the pixels before and after the wipe. */
    class Erase(val stroke: ContinuousStroke, val patch: InkPatch) : InkOp()

    /**
     * A stroke that left the page image.
     *
     * Such a stroke cannot stay a vector operation of the page: the page
     * has no pixels there. It is rasterised into a crop - a small
     * transparent bitmap - that is kept in the ledger of the page, at
     * [place], the position of the crop in the page's pixel space.
     */
    class Crop(val id: Int, val place: ContinuousPageGeometry.CropPlace) : InkOp()
}

/**
 * One undoable change of the ink layer.
 *
 * Undo works on this list and not on pixels: a stroke is undone by taking it
 * out of the committed list again, an eraser by putting back the pixels it
 * cleared.
 */
internal sealed class InkAction {
    /** A stroke was added to the committed list. */
    class Add(val op: InkOp.Stroke) : InkAction()

    /** An eraser was applied; its op holds the pixels before and after. */
    class Erase(val op: InkOp.Erase) : InkAction()

    /** The page was cleared: the patch keeps the ink, [removed] the strokes. */
    class Clear(val patch: InkPatch?, val removed: List<InkOp>) : InkAction()

    /**
     * A crop was added to the page (a stroke in the margin). Undoing it
     * takes the crop away again, redoing it brings it back.
     */
    class Crop(val op: InkOp.Crop) : InkAction()
}

/**
 * The committed operations of one page and their undo history.
 *
 * Pure bookkeeping - no Bitmap, no Canvas - so the order of the operations
 * and the undo/redo can be unit-tested without a device. The controller
 * feeds it the operations and does the pixel work (the eraser and the clear
 * touch the ink layer, so they are handed back to the controller).
 */
internal class InkHistory {

    /** The committed operations, in the order they were committed. */
    val ops = mutableListOf<InkOp>()

    private val undoStack = ArrayDeque<InkAction>()
    private val redoStack = ArrayDeque<InkAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Commits an operation and drops the redo history. */
    fun commit(op: InkOp) {
        ops.add(op)
        undoStack.addLast(
            when (op) {
                is InkOp.Stroke -> InkAction.Add(op)
                is InkOp.Erase -> InkAction.Erase(op)
                is InkOp.Crop -> InkAction.Crop(op)
            }
        )
        redoStack.clear()
    }

    /**
     * Commits a clear of the page: the operations of the session go away
     * with the ink of the page, and both can be brought back.
     */
    fun commitClear(patch: InkPatch?, removed: List<InkOp>) {
        ops.clear()
        undoStack.addLast(InkAction.Clear(patch, removed))
        redoStack.clear()
    }

    /**
     * Undoes the last operation.
     *
     * [onErase] and [onClear] do the pixel work: [restore] is true when the
     * pixels have to be put back, false when the wipe has to be done again.
     * [onCrop] does the same for a crop: an undo takes the crop out of the
     * page, a redo puts it back.
     */
    fun undo(
        onErase: (InkOp.Erase, Boolean) -> Unit,
        onClear: (InkPatch?, Boolean) -> Unit,
        onCrop: (InkOp.Crop, Boolean) -> Unit = { _, _ -> }
    ): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        when (action) {
            is InkAction.Add -> ops.removeLastOrNull()
            is InkAction.Erase -> {
                ops.removeLastOrNull()
                onErase(action.op, true)
            }
            is InkAction.Clear -> {
                onClear(action.patch, true)
                ops.addAll(0, action.removed)
            }
            is InkAction.Crop -> {
                ops.removeLastOrNull()
                onCrop(action.op, true)
            }
        }
        redoStack.addLast(action)
        return true
    }

    /** Redoes the last undone operation. */
    fun redo(
        onErase: (InkOp.Erase, Boolean) -> Unit,
        onClear: (InkPatch?, Boolean) -> Unit,
        onCrop: (InkOp.Crop, Boolean) -> Unit = { _, _ -> }
    ): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        when (action) {
            is InkAction.Add -> ops.add(action.op)
            is InkAction.Erase -> {
                ops.add(action.op)
                onErase(action.op, false)
            }
            is InkAction.Clear -> {
                onClear(action.patch, false)
                action.removed.forEach { op -> ops.remove(op) }
            }
            is InkAction.Crop -> {
                ops.add(action.op)
                onCrop(action.op, false)
            }
        }
        undoStack.addLast(action)
        return true
    }

    /**
     * Drops the oldest actions until the memory they keep is small enough.
     * The operations themselves stay: they are on the page.
     */
    fun trim(maxBytes: Long) {
        var total = undoStack.sumOf { actionBytes(it) }
        while (total > maxBytes && undoStack.size > 1) {
            total -= actionBytes(undoStack.removeFirst())
        }
    }

    /** How much memory one undo action keeps alive. */
    private fun actionBytes(action: InkAction): Long = when (action) {
        is InkAction.Add -> 0L
        is InkAction.Erase -> action.op.patch.bytes
        is InkAction.Clear -> action.patch?.bytes ?: 0L
        is InkAction.Crop -> action.op.place.width.toLong() * action.op.place.height.toLong() * 4L
    }
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
    var inkHeight: Int,
    val pageType: String
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

    /**
     * The size of the page box the strokes are normalised to, and the
     * aspect ratio of the page image inside it.
     *
     * The box is bigger than the page image (a fixed page has a margin, an
     * infinite page is the visible canvas), so the strokes can reach past
     * the page: what leaves it becomes a crop.
     */
    var boxWidth: Float = 0f
    var boxHeight: Float = 0f
    var pageRatio: Float = ContinuousPageGeometry.defaultRatio(pageType)

    /**
     * The crops of this page: the strokes that left the page image and are
     * therefore kept as small transparent bitmaps.
     */
    var crops: InkCropLedger<Bitmap> = InkCropLedger()

    /**
     * The crops a clear of the page took away. They come back when the
     * clear is undone.
     */
    internal var clearedCrops: List<InkOp.Crop> = emptyList()

    /**
     * The strokes that were finished in this session, in the order they were
     * committed.
     *
     * They are drawn as vectors on top of the page image, so the session
     * bitmap is never touched by a stroke: the reader does not have to
     * re-upload the 26 MB layer (and the pen does not wait for it) after
     * every stroke.
     */
    internal val history = InkHistory()

    internal val pending = mutableListOf<ContinuousStroke>()

    /**
     * Guards the ink bitmap.
     *
     * The eraser clears pixels in it on the main thread, and the save takes a
     * copy of it for the encoder - a copy of a 26 MB page must not happen on
     * the main thread, it blocks the pen for tens of milliseconds.
     */
    internal val inkLock = Any()

    /**
     * Where the page image sits inside the page box, in normalised box
     * coordinates. Everything that maps between the box (the space the
     * strokes live in) and the page (the space the ink layer lives in) goes
     * through this rect.
     */
    val pageRectInBox: ContinuousPageGeometry.BoxRect
        get() = ContinuousPageGeometry.pageRect(pageType, pageRatio, boxWidth, boxHeight)

    /** True once the layer is loaded and displayed; before that the reader's
     *  own page bitmap stays on screen and strokes are buffered. */
    val isReady: Boolean get() = ready

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
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
 * The strokes are kept as vectors: a finished stroke joins the committed
 * list of the session and is drawn as a path on top of the page image, so
 * the 26 MB session bitmap is never rewritten (and never re-uploaded to the
 * GPU) while the user writes. The list is rasterised into the bitmap once,
 * off the main thread, when the page is saved.
 *
 * Undo/redo works on that list: a stroke leaves the list again, an eraser
 * puts back the pixels it cleared (dirty-rectangle patches, so a short
 * stroke costs a few hundred KB rather than 26 MB).
 */
class ContinuousInkController(
    private val noteId: String,
    private val repository: NoteRepository,
    private val pdfDocCache: PdfDocumentCache,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    /** The type of the note's pages: an infinite page has no edge. */
    private val pageType: String = "infinite",
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

    /**
     * The crops of every page: the strokes that left the page image.
     *
     * They live in the controller (not in the session), so the margin notes
     * of a page stay on screen after the session that made them is gone.
     */
    private val cropLedgers = mutableMapOf<Int, InkCropLedger<Bitmap>>()

    /** The bitmaps of the crops, by the id of the crop. They outlive an undo. */
    private val cropBitmaps = mutableMapOf<Int, Bitmap>()

    /** The pages whose crops are being read at the moment. */
    private val loadingCrops = mutableSetOf<Int>()

    /** The ledger of a page (created on demand). */
    private fun ledgerFor(pageIndex: Int): InkCropLedger<Bitmap> =
        cropLedgers.getOrPut(pageIndex) { InkCropLedger() }

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

    /**
     * The page the stroke in progress belongs to (-1 when there is none).
     *
     * The reader reads it in the draw phase: only the page under the pen
     * has to repaint while the pen moves.
     */
    var livePageIndex by mutableIntStateOf(-1)
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
        livePageIndex = pageIndex
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
        livePageIndex = -1
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
        livePageIndex = -1
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
        if (existing != null && existing.pageIndex == pageIndex) {
            // The box may have grown (the zoom changed it): the strokes are
            // normalised to it, so the session has to know the new size.
            existing.boxWidth = boxWidth
            existing.boxHeight = boxHeight
            return existing
        }
        if (existing != null) {
            flushNow(existing)
        }
        val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
        // The ink layer lives in the page's pixel space, not in the box: the
        // box is bigger than the page (it has a margin), so the layer takes
        // the aspect ratio of the page itself.
        val ratio = pageRatioOf(page, isVector)
        val inkWidth = 2160
        val inkHeight = (inkWidth * ratio).roundToInt().coerceAtLeast(1)
        val created = ContinuousInkSession(pageIndex, isVector, inkWidth, inkHeight, pageType)
        created.boxWidth = boxWidth
        created.boxHeight = boxHeight
        created.pageRatio = ratio
        created.crops = ledgerFor(pageIndex)
        session = created
        onUndoRedoChanged(false, false)
        startLoad(created, page, renderWidthPx)
        // The margin notes of the page have to be there before the first
        // crop is written, or the save would drop the ones on the disk.
        scope.launch { loadCrops(page, pageIndex) }
        return created
    }

    /**
     * The aspect ratio (width / height) of the page image of a page.
     *
     * A vector page takes it from the PDF, a raster page from the default
     * of its note type - the loaded layer corrects it afterwards.
     */
    private fun pageRatioOf(page: PageEntity, isVector: Boolean): Float {
        if (isVector) {
            val size = pdfDocCache.document(page.pdfSourcePath)?.pageSize(page.pdfPageIndex)
            if (size != null && size[0] > 0f && size[1] > 0f) return size[0] / size[1]
        }
        return ContinuousPageGeometry.defaultRatio(pageType)
    }

    /**
     * Keeps the geometry of the session in step with the page box.
     *
     * The reader calls this when the box changed (a zoom, a rotation): the
     * strokes are normalised to the box, so the page image has to move
     * inside the new box with them.
     */
    fun updateGeometry(pageIndex: Int, boxWidth: Float, boxHeight: Float) {
        val s = getSession(pageIndex) ?: return
        if (boxWidth <= 0f || boxHeight <= 0f) return
        s.boxWidth = boxWidth
        s.boxHeight = boxHeight
    }

    /**
     * Where the page image sits inside the page box, in normalised box
     * coordinates.
     *
     * The session knows it (its strokes are normalised to the box); without
     * a session the ratio of the page image ([fallbackRatio]) is used.
     */
    fun pageRectFor(
        pageIndex: Int,
        boxWidth: Float,
        boxHeight: Float,
        fallbackRatio: Float = 0f
    ): ContinuousPageGeometry.BoxRect {
        val s = getSession(pageIndex)
        val ratio = s?.pageRatio ?: if (fallbackRatio > 0f) fallbackRatio else ratioOfLedger(pageIndex)
        return ContinuousPageGeometry.pageRect(pageType, ratio, boxWidth, boxHeight)
    }

    /** The aspect ratio of a page, taken from the crops of the page. */
    private fun ratioOfLedger(pageIndex: Int): Float {
        val ledger = cropLedgers[pageIndex]
        if (ledger != null && ledger.spaceWidth > 0 && ledger.spaceHeight > 0) {
            return ledger.spaceWidth.toFloat() / ledger.spaceHeight
        }
        return ContinuousPageGeometry.defaultRatio(pageType)
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
                // The ink layer describes the page: the page image sits in
                // the box with the aspect ratio of the layer.
                if (ink.height > 0) s.pageRatio = ink.width.toFloat() / ink.height
                s.crops.setSpace(s.inkWidth, s.inkHeight)
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

    /**
     * Commits a finished stroke.
     *
     * The stroke is NOT rasterised into the session bitmap: it joins the
     * committed list of the session and the reader draws it as a path on top
     * of the page image. The bitmap is only touched by the eraser, which
     * has to clear pixels.
     *
     * A stroke that leaves the page image cannot be a vector operation of
     * the page - the page has no pixels there. It is rasterised into a crop
     * instead (the mechanism the single-page engine uses for the ink outside
     * the paper).
     */
    private fun commit(s: ContinuousInkSession, stroke: ContinuousStroke, autoShapeEnabled: Boolean = false) {
        if (stroke.points.isEmpty()) return
        val ink = s.ink ?: return
        if (ink.isRecycled) return
        if (stroke.tool == CanvasTool.ERASER) {
            commitErase(s, stroke)
            return
        }
        val bounds = strokeBoundsInBox(stroke, s)
        if (bounds != null && !ContinuousPageGeometry.isInsidePage(s.pageRectInBox, bounds)) {
            // The stroke left the page: it becomes a crop of the page.
            if (commitCrop(s, stroke, bounds)) {
                revision++
                return
            }
            // No room for a crop: fall back to the vector stroke.
        }
        var shape: Path? = null
        val rect = s.pageRectInBox
        if (autoShapeEnabled && (stroke.tool == CanvasTool.PEN || stroke.tool == CanvasTool.FOUNTAIN_PEN) && !rect.empty) {
            // The auto-shape works on points in pixels: the stroke is
            // normalised to the box, the path lives in the page's pixels.
            val sx = s.inkWidth / rect.width
            val sy = s.inkHeight / rect.height
            val pts = stroke.points.map { ((it.x - rect.left) * sx) to ((it.y - rect.top) * sy) }
            shape = autoShape(pts)
        }
        val op = InkOp.Stroke(stroke, shape)
        s.history.commit(op)
        trimUndo(s)
        s.dirty = true
    }

    /**
     * Rasterises a stroke that left the page image into a crop: a small
     * transparent bitmap at the place the stroke has in the page's pixel
     * space.
     *
     * The crop is kept in the ledger of the page, so it can be drawn, saved
     * with the page and undone like any other operation.
     */
    private fun commitCrop(
        s: ContinuousInkSession,
        stroke: ContinuousStroke,
        bounds: ContinuousPageGeometry.BoxRect
    ): Boolean {
        val rect = s.pageRectInBox
        val place = ContinuousPageGeometry.cropPlace(rect, bounds, s.inkWidth, s.inkHeight) ?: return false
        if (place.width > MAX_CROP_SIZE || place.height > MAX_CROP_SIZE) return false
        val bitmap = try {
            Bitmap.createBitmap(place.width, place.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return false
        }
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        val canvas = Canvas(bitmap)
        // The stroke is normalised to the box: the canvas is scaled and
        // moved so the normalised coordinates land on the page, and the
        // crop's own origin is taken off.
        canvas.translate(-place.x, -place.y)
        canvas.scale(sx, sy)
        canvas.translate(-rect.left, -rect.top)
        drawStroke(canvas, stroke, 1f, 1f, preview = false)
        val crop = s.crops.add(place.x, place.y, place.width, place.height, bitmap)
        cropBitmaps[crop.id] = bitmap
        s.history.commit(InkOp.Crop(crop.id, place))
        trimUndo(s)
        s.dirty = true
        return true
    }

    /**
     * The bounds of a stroke in normalised box coordinates, nib included.
     */
    private fun strokeBoundsInBox(
        stroke: ContinuousStroke,
        s: ContinuousInkSession
    ): ContinuousPageGeometry.BoxRect? {
        val effectiveWidthNorm = if (stroke.tool == CanvasTool.HIGHLIGHTER) {
            stroke.baseWidthNorm * 3.2f
        } else {
            stroke.baseWidthNorm
        }
        val padFactor = if (stroke.tool == CanvasTool.HIGHLIGHTER) 0.85f else 0.6f
        return ContinuousPageGeometry.strokeBounds(
            stroke.points, s.boxWidth, s.boxHeight, padFactor, 4f
        )
    }

    /**
     * Commits an eraser stroke.
     *
     * The eraser is the one exception to the vector strokes: it clears
     * pixels, so it is drawn into the session bitmap (the ink layer) - the
     * PDF text lives in the background, not in that bitmap, so the text
     * under the eraser stays. The pixels it cleared are kept in a patch so
     * the wipe can be undone, and the operation stays in the committed list
     * so it can cut the strokes that were drawn before it.
     */
    private fun commitErase(s: ContinuousInkSession, stroke: ContinuousStroke) {
        val ink = s.ink ?: return
        if (ink.isRecycled) return
        val bounds = strokeBoundsInInk(stroke, s) ?: return
        val patch = snapshot(ink, bounds) ?: return
        synchronized(s.inkLock) {
            // A Canvas needs a mutable bitmap. The ink layer is mutable, but
            // if it ever is not, the erase must not take the app down.
            if (!ink.isMutable) {
                val mutable = ink.copy(Bitmap.Config.ARGB_8888, true) ?: return@synchronized
                s.ink = mutable
                drawStrokeInInk(Canvas(mutable), s, stroke)
                patch.after = capture(mutable, bounds)
            } else {
                val canvas = Canvas(ink)
                drawStrokeInInk(canvas, s, stroke)
                patch.after = capture(ink, bounds)
            }
        }
        val op = InkOp.Erase(stroke, patch)
        s.history.commit(op)
        trimUndo(s)
        s.dirty = true
        // The erase changed the ink bitmap, so the reader has to redraw it:
        // without this the wiped stroke stayed on the screen until something
        // else invalidated the page.
        revision++
    }

    /**
     * Draws a stroke into the ink layer (the page's pixel space).
     *
     * The strokes are normalised to the box and the ink layer lives in the
     * page's pixels, so the canvas is scaled and moved: the normalised
     * coordinates land on the page.
     */
    private fun drawStrokeInInk(canvas: Canvas, s: ContinuousInkSession, stroke: ContinuousStroke) {
        val rect = s.pageRectInBox
        if (rect.empty) return
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        val save = canvas.save()
        canvas.scale(sx, sy)
        canvas.translate(-rect.left, -rect.top)
        drawStroke(canvas, stroke, 1f, 1f, preview = false)
        canvas.restoreToCount(save)
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

    /**
     * Undoes the last committed operation.
     *
     * A stroke simply leaves the committed list again; an eraser also puts
     * back the pixels it cleared.
     */
    fun undo(): Boolean {
        val s = session ?: return false
        val undone = s.history.undo(
            onErase = { op, restore -> restoreErase(s, op, restore) },
            onClear = { patch, restore -> restoreClear(s, patch, restore) },
            onCrop = { op, restore -> restoreCrop(s, op, restore) }
        )
        if (!undone) return false
        s.dirty = true
        scheduleSave(s)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /** Redoes the last undone operation. */
    fun redo(): Boolean {
        val s = session ?: return false
        val redone = s.history.redo(
            onErase = { op, restore -> restoreErase(s, op, restore) },
            onClear = { patch, restore -> restoreClear(s, patch, restore) },
            onCrop = { op, restore -> restoreCrop(s, op, restore) }
        )
        if (!redone) return false
        s.dirty = true
        scheduleSave(s)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * Takes a crop out of the page (undo) or puts it back (redo).
     *
     * The bitmap of the crop outlives the undo: it is kept by its id, so
     * the redo can bring the very same crop back.
     */
    private fun restoreCrop(s: ContinuousInkSession, op: InkOp.Crop, restore: Boolean) {
        if (restore) {
            s.crops.remove(op.id)
            return
        }
        val bitmap = cropBitmaps[op.id] ?: return
        s.crops.restore(
            InkCropLedger.Crop(
                op.id, op.place.x, op.place.y, op.place.width, op.place.height, bitmap
            )
        )
    }

    /**
     * Puts the pixels of an eraser back (undo) or wipes them again (redo).
     */
    private fun restoreErase(s: ContinuousInkSession, op: InkOp.Erase, restore: Boolean) {
        val ink = s.ink ?: return
        if (ink.isRecycled) return
        synchronized(s.inkLock) { restore(ink, op.patch, useAfter = !restore) }
    }

    /**
     * Puts the ink of a cleared page back (undo) or wipes it again (redo).
     *
     * The margin notes of the page belong to the ink, so they go with it.
     */
    private fun restoreClear(s: ContinuousInkSession, patch: InkPatch?, restore: Boolean) {
        val ink = s.ink ?: return
        if (ink.isRecycled) return
        synchronized(s.inkLock) {
            if (restore) {
                if (patch != null) restore(ink, patch, useAfter = false)
            } else {
                ink.eraseColor(android.graphics.Color.TRANSPARENT)
            }
        }
        if (restore) {
            s.clearedCrops.forEach { op ->
                val bitmap = cropBitmaps[op.id] ?: return@forEach
                s.crops.restore(
                    InkCropLedger.Crop(
                        op.id, op.place.x, op.place.y, op.place.width, op.place.height, bitmap
                    )
                )
            }
        } else {
            s.clearedCrops.forEach { op -> s.crops.remove(op.id) }
        }
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
        val ratio = pageRatioOf(page, isVector)
        val created = ContinuousInkSession(
            pageIndex, isVector, 2160, (2160f * ratio).roundToInt().coerceAtLeast(1), pageType
        )
        created.pageRatio = ratio
        created.crops = ledgerFor(pageIndex)
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
        // The strokes of this session go away with the ink of the page; the
        // undo keeps both, so the clear can be taken back.
        val removed = s.history.ops.toList()
        // The margin notes are ink of the page too: they go with it.
        val removedCrops = removed.filterIsInstance<InkOp.Crop>()
        removedCrops.forEach { op -> s.crops.remove(op.id) }
        s.clearedCrops = removedCrops
        synchronized(s.inkLock) { ink.eraseColor(android.graphics.Color.TRANSPARENT) }
        s.history.commitClear(patch, removed)
        trimUndo(s)
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
    private fun strokeBoundsInInk(stroke: ContinuousStroke, s: ContinuousInkSession): Rect? {
        val rect = s.pageRectInBox
        if (rect.empty) return null
        val bounds = strokeBoundsInBox(stroke, s) ?: return null
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        // The eraser only reaches the ink of the page: a stroke in the
        // margin has nothing to erase there.
        val left = ((bounds.left - rect.left) * sx).toInt()
        val top = ((bounds.top - rect.top) * sy).toInt()
        val right = ((bounds.right - rect.left) * sx).toInt()
        val bottom = ((bounds.bottom - rect.top) * sy).toInt()
        val clippedLeft = max(0, left)
        val clippedTop = max(0, top)
        val clippedRight = min(s.inkWidth, right)
        val clippedBottom = min(s.inkHeight, bottom)
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null
        return Rect(clippedLeft, clippedTop, clippedRight, clippedBottom)
    }

    private fun trimUndo(s: ContinuousInkSession) {
        s.history.trim(MAX_UNDO_BYTES)
    }

    // ---- saving --------------------------------------------------------

    private fun scheduleSave(s: ContinuousInkSession) {
        s.saveJob?.cancel()
        s.saveJob = scope.launch {
            // Long enough that a page of writing is saved once, not after
            // every single stroke.
            delay(1200)
            persist(s)
        }
    }

    private suspend fun persist(s: ContinuousInkSession) {
        val ink = s.ink
        if (ink == null || ink.isRecycled) return
        // The page is only "clean" when the write actually succeeded: a
        // failed write must stay dirty so the next save tries again.
        s.dirty = false
        // The copy and the strokes drawn into it happen off the main thread:
        // copying a 26 MB page on the UI thread blocked the pen for tens of
        // milliseconds after every stroke. The lock keeps a stroke that lands
        // during the copy from tearing the snapshot.
        val copy = try {
            withContext(Dispatchers.IO) { copyWithOps(s, ink) }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            s.dirty = true
            return
        } ?: run { s.dirty = true; return }
        val ok = withContext(Dispatchers.IO) {
            savePage(s, copy)
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

    /**
     * Writes the ink layer of a page and the crops that belong to it.
     *
     * The crops are the ink outside the page image: they live in their own
     * files (the ones the single-page engine uses), so they survive a
     * restart and reach the export.
     */
    private suspend fun savePage(s: ContinuousInkSession, copy: Bitmap): Boolean {
        val ok = if (s.isVector) {
            repository.savePageStrokes(noteId, s.pageIndex, copy)
        } else {
            repository.savePageBitmap(noteId, s.pageIndex, copy)
        }
        writeCrops(s.pageIndex, s.crops)
        return ok
    }

    /**
     * Writes the crops of a page to the disk.
     *
     * The format is the one of the single-page engine: one transparent PNG
     * per crop (`beyond_<page>_<n>.png`) and a manifest that keeps their
     * places (`beyond_<page>.json`). Crops of the page that are gone are
     * removed, so a crop that is no longer in the ledger cannot come back.
     */
    private fun writeCrops(pageIndex: Int, ledger: InkCropLedger<Bitmap>) {
        try {
            val fileStore = repository.fileStore
            val layersDir = fileStore.getLayersDir(noteId)
            val prefix = "beyond_${pageIndex}_"
            val manifest = fileStore.getBeyondInkFile(noteId, pageIndex)
            val arr = org.json.JSONArray()
            val written = mutableSetOf<String>()
            ledger.all().forEachIndexed { index, crop ->
                val bitmap = crop.payload
                if (bitmap.isRecycled) return@forEachIndexed
                val name = "${prefix}${index}.png"
                written.add(name)
                java.io.FileOutputStream(java.io.File(layersDir, name)).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                val obj = org.json.JSONObject()
                obj.put("file", name)
                obj.put("x", crop.x.toDouble())
                obj.put("y", crop.y.toDouble())
                arr.put(obj)
            }
            manifest.writeText(arr.toString())
            layersDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix) && file.name.endsWith(".png") && !written.contains(file.name)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** One crop that was read from the disk. */
    private class LoadedCrop(
        val bitmap: Bitmap,
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int
    )

    /** The crops of a page, with the pixel space they live in. */
    private class LoadedCrops(
        val crops: List<LoadedCrop>,
        val spaceWidth: Int,
        val spaceHeight: Int
    )

    /**
     * Loads the crops of a page (the margin notes) from the disk.
     *
     * They are the files the single-page engine writes, so the notes of a
     * page survive a restart and are visible in both modes.
     */
    suspend fun loadCrops(page: PageEntity, pageIndex: Int) {
        val ledger = ledgerFor(pageIndex)
        // The user already drew in the margin: those strokes are newer than
        // the file, so they win and the load does not wipe them.
        if (ledger.count > 0) return
        if (!loadingCrops.add(pageIndex)) return
        try {
            val loaded = withContext(Dispatchers.IO) { readCropsFromDisk(page, pageIndex) } ?: return
            ledger.setSpace(loaded.spaceWidth, loaded.spaceHeight)
            for (crop in loaded.crops) {
                val added = ledger.add(crop.x, crop.y, crop.width, crop.height, crop.bitmap)
                cropBitmaps[added.id] = crop.bitmap
            }
            revision++
        } finally {
            loadingCrops.remove(pageIndex)
        }
    }

    /** Reads the crops of a page from the disk (the single-page format). */
    private fun readCropsFromDisk(page: PageEntity, pageIndex: Int): LoadedCrops? {
        val fileStore = repository.fileStore
        val manifest = fileStore.getBeyondInkFile(noteId, pageIndex)
        if (!manifest.exists()) return null
        val layersDir = fileStore.getLayersDir(noteId)
        val crops = mutableListOf<LoadedCrop>()
        try {
            val arr = org.json.JSONArray(manifest.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("file", "")
                if (name.isEmpty()) continue
                val bitmap = android.graphics.BitmapFactory
                    .decodeFile(java.io.File(layersDir, name).absolutePath)
                    ?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
                crops.add(
                    LoadedCrop(
                        bitmap,
                        obj.optDouble("x", 0.0).toFloat(),
                        obj.optDouble("y", 0.0).toFloat(),
                        bitmap.width,
                        bitmap.height
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (crops.isEmpty()) return null
        val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
        val space = inkSpaceSize(pageIndex, isVector)
        return LoadedCrops(crops, space.first, space.second)
    }

    /**
     * The pixel space of the ink of a page: the size of its ink layer (the
     * stroke layer of a PDF page, the page image of a raster page). The
     * crops are placed in it.
     */
    private fun inkSpaceSize(pageIndex: Int, isVector: Boolean): Pair<Int, Int> {
        val fileStore = repository.fileStore
        val file = if (isVector) {
            fileStore.getStrokesFile(noteId, pageIndex)
        } else {
            fileStore.getPageFile(noteId, pageIndex)
        }
        if (file.exists()) {
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                return bounds.outWidth to bounds.outHeight
            }
        }
        // No ink layer yet: the default of the note type is used, the same
        // size the session creates.
        val ratio = ContinuousPageGeometry.defaultRatio(pageType)
        return 2160 to (2160f * ratio).roundToInt().coerceAtLeast(1)
    }

    /**
     * Copies the session bitmap and draws the committed strokes into the
     * copy.
     *
     * The copy keeps the ink of the page as it was loaded; the strokes of
     * this session are rasterised into it here, once, off the main thread.
     * Must not run on the main thread: the layer is ~26 MB.
     *
     * [ops] is the list to draw; when it is null the list of the session is
     * taken under the lock, together with the copy (so the copy and the
     * strokes always match). A caller that releases the session before the
     * copy runs passes the list it took.
     */
    private fun copyWithOps(s: ContinuousInkSession, ink: Bitmap, ops: List<InkOp>? = null): Bitmap? {
        val prepared = synchronized(s.inkLock) {
            // The copy has to be MUTABLE: the strokes of this session are
            // drawn into it, and a Canvas over an immutable bitmap throws
            // ("Immutable bitmap passed to Canvas constructor") - that was
            // the crash on the first stroke.
            val copied = ink.copy(Bitmap.Config.ARGB_8888, true)
            if (copied == null) null else copied to (ops ?: s.history.ops.toList())
        } ?: return null
        drawOps(Canvas(prepared.first), s, prepared.second)
        return prepared.first
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
        closingSessions[s.pageIndex] = s
        appScope.launch {
            // The copy of the 26 MB layer and the strokes drawn into it
            // happen off the main thread: the pen must not wait for them.
            val copy = try {
                withContext(Dispatchers.IO) { copyWithOps(s, ink) }
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                null
            }
            if (copy == null) {
                s.dirty = true
                withContext(Dispatchers.Main) {
                    closingSessions.remove(s.pageIndex)
                    releaseBitmaps(s)
                    revision++
                }
                return@launch
            }
            withContext(Dispatchers.IO) {
                savePage(s, copy)
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
                // The strokes are taken now: the session is released below,
                // and the release drops the list.
                val ops = s.history.ops.toList()
                appScope.launch {
                    // The copy of the layer and the strokes drawn into it
                    // happen off the main thread.
                    val copy = try {
                        withContext(Dispatchers.IO) { copyWithOps(s, ink, ops) }
                    } catch (e: OutOfMemoryError) {
                        e.printStackTrace()
                        null
                    }
                    if (copy != null) {
                        withContext(Dispatchers.IO) {
                            savePage(s, copy)
                        }
                        copy.recycle()
                    }
                    onSaved(s.pageIndex)
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
                // The strokes are taken now: the session is released below,
                // and the release drops the list.
                val ops = sess.history.ops.toList()
                appScope.launch {
                    // The copy of the layer and the strokes drawn into it
                    // happen off the main thread.
                    val copy = try {
                        withContext(Dispatchers.IO) { copyWithOps(sess, ink, ops) }
                    } catch (e: OutOfMemoryError) {
                        e.printStackTrace()
                        null
                    }
                    if (copy != null) {
                        withContext(Dispatchers.IO) {
                            savePage(sess, copy)
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
        // The strokes are on disk now: the reader reloads the page from the
        // file, so the committed list must not draw them a second time.
        s.history.ops.clear()
    }

    // ---- drawing -------------------------------------------------------

    /**
     * Draws the committed strokes of a page into the page box.
     *
     * They are drawn as paths, with exactly the code the live preview uses,
     * so a finished stroke looks the same as the one the user just drew -
     * and the session bitmap is never touched, so the GPU never has to
     * take the 26 MB layer again.
     */
    fun drawCommitted(canvas: Canvas, pageIndex: Int, viewWidth: Float, viewHeight: Float) {
        val s = getSession(pageIndex) ?: return
        if (s.history.ops.isEmpty()) return
        for (op in s.history.ops) {
            when (op) {
                is InkOp.Stroke -> drawOpStroke(canvas, op, s, viewWidth, viewHeight)
                // The eraser clears pixels: the reader draws it into an
                // offscreen layer, so the wipe never reaches the page image.
                is InkOp.Erase -> drawStroke(canvas, op.stroke, viewWidth, viewHeight, preview = false)
                // The crops are the ink outside the page: they are drawn by
                // drawCrops, at the place they have in the page.
                is InkOp.Crop -> Unit
            }
        }
    }

    /** The committed operations of a page, in the order they were committed. */
    internal fun committedOps(pageIndex: Int): List<InkOp> {
        val s = getSession(pageIndex) ?: return emptyList()
        return s.history.ops
    }

    /**
     * True when the committed operations of a page contain an eraser. The
     * reader needs an offscreen layer for those: a CLEAR stroke must not
     * reach the page image (the PDF text) under it.
     */
    internal fun hasEraseOp(pageIndex: Int): Boolean {
        val s = getSession(pageIndex) ?: return false
        return s.history.ops.any { it is InkOp.Erase }
    }

    /** Draws a committed stroke into the page box (view coordinates). */
    private fun drawOpStroke(
        canvas: Canvas,
        op: InkOp.Stroke,
        s: ContinuousInkSession,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val shape = op.shape
        val rect = s.pageRectInBox
        if (shape == null || rect.empty || s.inkWidth <= 0 || s.inkHeight <= 0) {
            drawStroke(canvas, op.stroke, viewWidth, viewHeight, preview = false)
            return
        }
        // The auto-shape path lives in the page's pixels: the canvas is
        // scaled and moved so the path lands on the page inside the box.
        // The paint keeps the line weight in box pixels.
        val u = rect.width * viewWidth / s.inkWidth
        val v = rect.height * viewHeight / s.inkHeight
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = op.stroke.color
            strokeWidth = op.stroke.baseWidthNorm * viewWidth
        }
        val save = canvas.save()
        canvas.translate(rect.left * viewWidth, rect.top * viewHeight)
        canvas.scale(u, v)
        canvas.drawPath(shape, paint)
        canvas.restoreToCount(save)
    }

    /**
     * Draws the crops of a page: the strokes that left the page image.
     *
     * They are painted on the page (at the place they have in the page's
     * pixel space), under the vector operations of the session.
     */
    fun drawCrops(
        canvas: Canvas,
        pageIndex: Int,
        boxWidth: Float,
        boxHeight: Float,
        fallbackRatio: Float = 0f
    ) {
        val ledger = cropLedgers[pageIndex] ?: return
        if (ledger.count == 0) return
        if (boxWidth <= 0f || boxHeight <= 0f) return
        if (ledger.spaceWidth <= 0 || ledger.spaceHeight <= 0) return
        val rect = pageRectFor(pageIndex, boxWidth, boxHeight, fallbackRatio)
        if (rect.empty) return
        val sx = rect.width * boxWidth / ledger.spaceWidth
        val sy = rect.height * boxHeight / ledger.spaceHeight
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (crop in ledger.all()) {
            val bitmap = crop.payload
            if (bitmap.isRecycled) continue
            val left = rect.left * boxWidth + crop.x * sx
            val top = rect.top * boxHeight + crop.y * sy
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    left,
                    top,
                    left + crop.width * sx,
                    top + crop.height * sy
                ),
                paint
            )
        }
    }

    /**
     * Draws the committed operations into the ink layer (the page's pixels).
     * Used when the page is saved: the strokes are rasterised into a copy of
     * the layer, once, off the main thread.
     *
     * The strokes are normalised to the box and the layer lives in the
     * page's pixels, so the canvas is scaled and moved: the normalised
     * coordinates land on the page.
     */
    private fun drawOps(canvas: Canvas, s: ContinuousInkSession, ops: List<InkOp>) {
        val rect = s.pageRectInBox
        if (rect.empty) return
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        for (op in ops) {
            when (op) {
                is InkOp.Stroke -> {
                    val shape = op.shape
                    if (shape == null) {
                        val save = canvas.save()
                        canvas.scale(sx, sy)
                        canvas.translate(-rect.left, -rect.top)
                        drawStroke(canvas, op.stroke, 1f, 1f, preview = false)
                        canvas.restoreToCount(save)
                    } else {
                        // The auto-shape path already lives in the page's
                        // pixels: it is drawn as it is.
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            color = op.stroke.color
                            strokeWidth = op.stroke.baseWidthNorm * sx
                        }
                        canvas.drawPath(shape, paint)
                    }
                }
                is InkOp.Erase -> {
                    val save = canvas.save()
                    canvas.scale(sx, sy)
                    canvas.translate(-rect.left, -rect.top)
                    drawStroke(canvas, op.stroke, 1f, 1f, preview = false)
                    canvas.restoreToCount(save)
                }
                // The crops are not part of the ink layer: they are the ink
                // outside the page and live in their own files.
                is InkOp.Crop -> Unit
            }
        }
    }

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
        /** The biggest crop (a margin note) that is rasterised, in pixels. */
        const val MAX_CROP_SIZE = 4096
    }
}
