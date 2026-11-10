package personal.notes.touch.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import personal.notes.touch.util.BitmapUtils
import personal.notes.touch.util.DebugGestureInjector
import personal.notes.touch.util.PdfRenderPolicy
import personal.notes.touch.util.PdfTextSelectionInfo
import personal.notes.touch.util.PdfTextSelectionMath
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CanvasTool {
    FOUNTAIN_PEN,
    PEN,
    HIGHLIGHTER,
    ERASER,
    SHAPE_RECT,
    SHAPE_CIRCLE,
    SHAPE_LINE,
    LASSO,
    /** Shows a red dot with a short tail where the pen is; writes nothing. */
    LASER_POINTER
}

enum class PaperPattern {
    BLANK,
    GRID,
    LINES,
    DOTTED;

    /** Stable code persisted in the DB (`pages.paperPattern`); BLANK = 0 means "unset". */
    val code: Int
        get() = ordinal

    companion object {
        fun fromCode(code: Int): PaperPattern = entries.firstOrNull { it.ordinal == code } ?: BLANK
    }
}

/**
 * A transparent crop of page ink.
 *
 * The page's own ink bitmap only covers the paper, so two kinds of marks
 * cannot live in it: the ink written on a layer (the eye button has to hide
 * it together with the layer) and the ink written outside the paper. Both are
 * kept as small crops with their position in page coordinates — a crop is
 * what the layer panel hides and what "merge to base" flattens back into the
 * page ink.
 */
class InkCrop(val bitmap: Bitmap, val x: Float, val y: Float)

data class ImageLayer(
    val bitmap: Bitmap,
    var x: Float,
    var y: Float,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    var cropRect: Rect? = null,
    var visible: Boolean = true,
    var name: String = "Layer",
    /** Ink written while this layer was the current one. */
    val ink: MutableList<InkCrop> = mutableListOf()
)

data class LayerStateSnapshot(
    val bitmap: Bitmap,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val cropRect: Rect? = null,
    val visible: Boolean = true,
    val name: String = "Layer",
    /** Ink written on the layer while it was the current one. */
    val ink: List<InkCrop> = emptyList()
)

data class UndoSnapshot(
    val bitmap: Bitmap,
    val layers: List<LayerStateSnapshot>,
    /** Ink written outside the paper (page-level crops). */
    val beyondInk: List<InkCrop> = emptyList()
)

class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var backingBitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    // View transform (pan/zoom)
    var panX: Float = 0f
        set(value) { field = value; updateViewMatrix() }
    var panY: Float = 0f
        set(value) { field = value; updateViewMatrix() }
    var scale: Float = 1f
        set(value) {
            field = value.coerceIn(minZoom.coerceAtMost(1f), 8f)
            updateViewMatrix()
            schedulePdfBackgroundRefresh()
        }
    var autoCenter: Boolean = true
    val viewMatrix: Matrix = Matrix()
    val pageInverseMatrix: Matrix = Matrix()

    // gesture tracking
    private var gestureActive = false
    private var gestureStartPanX = 0f
    private var gestureStartPanY = 0f
    private var gestureStartScale = 1f
    private var gestureStartMidX = 0f
    private var gestureStartMidY = 0f
    private var gestureStartDist = 0f
    private var gestureStartFocalCanvasX = 0f
    private var gestureStartFocalCanvasY = 0f

    // Non-destructive background (e.g. imported PDF page)
    var backgroundBitmap: Bitmap? = null
        set(value) {
            field = value
            // A caller-provided (non-pdfium) background is never recycled by
            // the vector renderer; remember it so a render swap can tell.
            if (pdfRenderScale == 0f) pdfSessionBackground = value
            invalidate()
        }

    // ---- F-10: live vector PDF background --------------------------------
    // The page is rendered from its source document at the current zoom level
    // instead of upscaling the import-time raster. The stroke layer
    // (backingBitmap) stays separate: annotations never bake PDF pixels.
    private var pdfPageWidthPts = 0f
    private var pdfPageHeightPts = 0f

    /** "Fit" canvas px per PDF point — the fixed page rect the background maps into. */
    private var pdfBasePxPerPt = 0f

    /** px-per-pt of the bitmap currently in [backgroundBitmap] (0 = not pdfium-rendered). */
    private var pdfRenderScale = 0f
    private var pdfRenderer: ((pxPerPt: Float, outW: Int, outH: Int) -> Bitmap?)? = null
    private var pdfRenderInFlight = false
    private var pdfSessionBackground: Bitmap? = null

    /**
     * Pixel budget for one pdfium background, scaled to the heap the process
     * was given (ARGB_8888 ⇒ 4 bytes/px). On a large-heap tablet this stays at
     * [PdfRenderPolicy.MAX_PIXELS]; a 256 MB device gets roughly half, which
     * keeps a re-render (old + new bitmap) out of OOM territory.
     */
    private val pdfPixelBudget: Long =
        (Runtime.getRuntime().maxMemory() / 24)
            .coerceIn(4_000_000L, PdfRenderPolicy.MAX_PIXELS)
    private val pdfRenderHandler = Handler(Looper.getMainLooper())
    private val pdfRenderTask = Runnable { renderPdfBackgroundNow() }

    /** True when a vector source is installed for the current page. */
    val hasPdfBackground: Boolean
        get() = pdfRenderer != null

    // ---- F-10: PDF text selection ----------------------------------------
    /**
     * Canvas-space (page-space) access to a vector page's text, implemented in
     * the Compose layer on top of [personal.notes.touch.util.PdfiumDocument].
     * All coordinates and rects are in the same space as [backingBitmap], so
     * selection highlights and baked marks line up with annotations at any
     * render resolution.
     */
    interface PdfTextSource {
        fun charCount(): Int
        fun charIndexAt(x: Float, y: Float, tolerance: Float): Int
        fun charBox(index: Int): FloatArray?
        fun selectionRects(start: Int, count: Int): List<FloatArray>
        fun text(start: Int, count: Int): String
    }

    private var pdfTextSource: PdfTextSource? = null
    private var pdfTextSelectionMode = false
    private var selAnchor = -1
    private var selFocus = -1
    private var selRects: List<FloatArray> = emptyList()
    private val pdfSelectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5533B5E5
        style = Paint.Style.FILL
    }

    /** Notified whenever the PDF text selection changes (null = nothing selected). */
    var onPdfSelectionChanged: ((PdfTextSelectionInfo?) -> Unit)? = null

    val isPdfTextSelectionMode: Boolean
        get() = pdfTextSelectionMode

    val hasPdfTextSource: Boolean
        get() = pdfTextSource != null

    /** Width of the page/annotation space in canvas units. */
    fun pageCanvasWidth(): Float = pdfPageRectWidth()

    /** Height of the page/annotation space in canvas units. */
    fun pageCanvasHeight(): Float = pdfPageRectHeight()

    /**
     * Installs the text provider of the current vector page (or clears it).
     * Any previous selection is dropped: it belongs to another page.
     */
    fun setPdfTextSource(source: PdfTextSource?) {
        pdfTextSource = source
        if (source == null) pdfTextSelectionMode = false
        clearPdfSelection()
        invalidate()
    }

    /** True when a selection gesture (tap/drag) should select page text. */
    fun setPdfTextSelectionMode(enabled: Boolean) {
        val next = enabled && pdfTextSource != null
        if (pdfTextSelectionMode == next) return
        pdfTextSelectionMode = next
        if (!next) clearPdfSelection()
        invalidate()
    }

    fun clearPdfSelection() {
        selAnchor = -1
        selFocus = -1
        selRects = emptyList()
        onPdfSelectionChanged?.invoke(null)
        invalidate()
    }

    /** Inclusive selected char range, or null when nothing is selected. */
    private fun pdfSelectionRange(): IntRange? =
        PdfTextSelectionMath.range(selAnchor, selFocus)

    /** Plain text of the current selection ("" when nothing usable is selected). */
    fun selectedPdfText(): String {
        val source = pdfTextSource ?: return ""
        val range = pdfSelectionRange() ?: return ""
        val count = PdfTextSelectionMath.count(range)
        if (count <= 0) return ""
        return source.text(range.first, count).trim()
    }

    fun hasPdfSelection(): Boolean =
        pdfSelectionRange() != null && selectedPdfText().isNotEmpty()

    /**
     * Bakes the current PDF text selection into the stroke layer as a
     * translucent marker ([mark] = true) or a solid underline band, so the
     * annotation survives independent of the PDF background. Returns true when
     * something was drawn.
     */
    fun commitPdfSelection(mark: Boolean): Boolean {
        val source = pdfTextSource ?: return false
        val range = pdfSelectionRange() ?: return false
        val count = PdfTextSelectionMath.count(range)
        if (count <= 0) return false
        val rects = try {
            source.selectionRects(range.first, count)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        if (rects.isEmpty()) return false
        val canvas = bitmapCanvas ?: return false
        saveUndoState()
        if (mark) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Highlighter yellow at ~40% alpha, like the highlighter tool.
                color = 0x66FFD600
                style = Paint.Style.FILL
            }
            for (r in rects) canvas.drawRect(r[0], r[1], r[2], r[3], paint)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1A73E8.toInt()
                style = Paint.Style.FILL
            }
            for (r in rects) {
                val thickness = maxOf(2f, (r[3] - r[1]) * 0.08f)
                val band = PdfTextSelectionMath.underlineRect(r, thickness)
                canvas.drawRect(band[0], band[1], band[2], band[3], paint)
            }
        }
        clearPdfSelection()
        onStrokeFinishedListener?.invoke()
        return true
    }

    /** Recomputes cached selection rects and reports the selection upward. */
    private fun notifyPdfSelection() {
        val source = pdfTextSource
        val range = pdfSelectionRange()
        if (source == null || range == null) {
            selRects = emptyList()
            onPdfSelectionChanged?.invoke(null)
            return
        }
        val count = PdfTextSelectionMath.count(range)
        val text = if (count > 0) {
            try {
                source.text(range.first, count).trim()
            } catch (e: Exception) {
                ""
            }
        } else ""
        selRects = try {
            source.selectionRects(range.first, count)
        } catch (e: Exception) {
            emptyList()
        }
        onPdfSelectionChanged?.invoke(
            if (text.isEmpty()) null else PdfTextSelectionInfo(range.first, range.last, text)
        )
    }

    /**
     * Installs a live vector PDF background for the current page, or clears it
     * when [renderer] is null. [basePxPerPt] is the import-time px-per-point
     * mapping, used only as a fallback page rect before the view has a backing
     * bitmap. The renderer runs on the shared background-render thread and
     * must return a bitmap [outW]×[outH] px.
     *
     * The rendered bitmap is stretched into the rect the page raster used to
     * occupy (the backing bitmap's box), so annotations drawn in view space
     * stay aligned with the page at every zoom level.
     */
    fun setPdfBackground(
        pageWidthPts: Float,
        pageHeightPts: Float,
        basePxPerPt: Float,
        renderer: ((pxPerPt: Float, outW: Int, outH: Int) -> Bitmap?)?
    ) {
        pdfRenderHandler.removeCallbacks(pdfRenderTask)
        // Drop any background belonging to another page/document so a page
        // flip never shows the previous PDF while the new render is pending.
        val previous = backgroundBitmap
        if (pdfRenderScale > 0f && previous != null && previous !== pdfSessionBackground && !previous.isRecycled) {
            previous.recycle()
        }
        pdfRenderScale = 0f
        pdfSessionBackground = null
        backgroundBitmap = null
        if (renderer == null || pageWidthPts <= 0f || pageHeightPts <= 0f || basePxPerPt <= 0f) {
            pdfRenderer = null
            pdfPageWidthPts = 0f
            pdfPageHeightPts = 0f
            pdfBasePxPerPt = 0f
            return
        }
        pdfPageWidthPts = pageWidthPts
        pdfPageHeightPts = pageHeightPts
        pdfBasePxPerPt = basePxPerPt
        pdfRenderer = renderer
        pdfRenderHandler.postDelayed(pdfRenderTask, PDF_RENDER_DEBOUNCE_MS)
    }

    /** Width of the rect the page (and thus its annotations) occupies, canvas units. */
    private fun pdfPageRectWidth(): Float {
        val bmp = backingBitmap
        if (bmp != null && bmp.width > 0) return bmp.width.toFloat()
        return pdfPageWidthPts * pdfBasePxPerPt
    }

    /** Height of the rect the page (and thus its annotations) occupies, canvas units. */
    private fun pdfPageRectHeight(): Float {
        val bmp = backingBitmap
        if (bmp != null && bmp.height > 0) return bmp.height.toFloat()
        return pdfPageHeightPts * pdfBasePxPerPt
    }

    /** Debounced upgrade of the vector background after a zoom change. */
    fun schedulePdfBackgroundRefresh() {
        if (pdfRenderer == null) return
        pdfRenderHandler.removeCallbacks(pdfRenderTask)
        pdfRenderHandler.postDelayed(pdfRenderTask, PDF_RENDER_DEBOUNCE_MS)
    }

    private fun renderPdfBackgroundNow() {
        val renderer = pdfRenderer ?: return
        if (pdfRenderInFlight) return
        // Screen pixels per PDF point at the current zoom: the on-screen width
        // of the page rect times the view scale, over the page's point width.
        if (pdfPageWidthPts <= 0f) return
        val needed = pdfPageRectWidth() * scale / pdfPageWidthPts
        if (!PdfRenderPolicy.shouldRender(needed, pdfRenderScale)) return
        val capped = PdfRenderPolicy.capPxPerPt(
            pdfPageWidthPts, pdfPageHeightPts, needed, maxPixels = pdfPixelBudget
        )
        if (capped <= 0f || capped <= pdfRenderScale) return
        val size = PdfRenderPolicy.outputSize(pdfPageWidthPts, pdfPageHeightPts, capped)
        val outW = size[0]
        val outH = size[1]
        pdfRenderInFlight = true
        pdfRenderExecutor.execute {
            val bitmap = try {
                renderer(capped, outW, outH)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            pdfRenderHandler.post {
                pdfRenderInFlight = false
                if (bitmap != null && !bitmap.isRecycled && pdfRenderer == renderer) {
                    val previous = backgroundBitmap
                    backgroundBitmap = bitmap
                    pdfRenderScale = capped
                    // Recycle the bitmap we rendered previously (never a
                    // caller-provided session background: those have scale 0).
                    if (previous != null && previous !== bitmap && !previous.isRecycled &&
                        previous !== pdfSessionBackground
                    ) {
                        previous.recycle()
                    }
                    pdfSessionBackground = null
                } else if (bitmap != null && !bitmap.isRecycled) {
                    // Render finished for a page/source that is no longer
                    // current: drop it.
                    bitmap.recycle()
                }
                // The zoom may have moved on while we rendered.
                if (pdfRenderer != null) schedulePdfBackgroundRefresh()
            }
        }
    }

    // Page type
    var beyondPaper: Boolean = true
        set(value) {
            field = value
            // When beyond-paper is enabled, allow panning freely without clamp.
            invalidate()
        }
    var pageType: String = "infinite"
    var minZoom: Float = 0.20f
    var horizontalResistance: Float = 0.4f
    private val paperBedPaint = Paint().apply { style = Paint.Style.FILL }
    private val pageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.rgb(0xCC, 0xCC, 0xCC)
    }

    // Transformable image layers
    val imageLayers: MutableList<ImageLayer> = mutableListOf()

    /**
     * Ink written outside the paper. The page's ink bitmap covers the paper
     * only, so marks beyond its edge are kept as transparent crops that are
     * painted over the canvas surround — that is what gives an imported PDF
     * room to write in the margins.
     */
    private val beyondPageInk = mutableListOf<InkCrop>()

    /** Index of the page this view shows; the beyond-paper ink is stored per page. */
    var pageIndex: Int = 0

    /**
     * True when a layer's geometry or content changed since the last
     * [saveImageLayers]. Layer edits are not part of the backing bitmap, so
     * this is what tells the screen a layer write is still owed when it is
     * disposed (system back, tab close) rather than left through
     * `saveAndExit`.
     */
    @Volatile
    var layersDirty: Boolean = false
    var selectedImageIndex: Int = -1
        set(value) {
            field = value
            onImageSelectionChanged?.invoke(value)
        }
    var onImageSelectionChanged: ((Int) -> Unit)? = null

    /**
     * Fired on the first pointer-down of a gesture on the page (stylus or
     * finger, before any stroke is committed). Used to auto-close the slide-
     * over menus: touching the page means the user is done configuring and is
     * writing again.
     */
    var onCanvasTouchStart: (() -> Unit)? = null
    // Touch Path & Shapes
    private val currentPath = Path()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var isDrawingShape = false

    // Stylus Mode & Pressure Sensitivity
    var stylusOnly: Boolean = false

    /** Called the first time a stylus is used on this canvas. */
    var onStylusDetected: (() -> Unit)? = null
    var readingMode: Boolean = false
    var pressureSensitivity: Float = 1.0f
    var pressureSizeEnabled: Boolean = true
    var autoShapeEnabled: Boolean = false

    private val sampledPoints = mutableListOf<Pair<Float, Float>>()

    /** One sample of the laser pointer trail (page coordinates). */
    private data class LaserPoint(val x: Float, val y: Float, val time: Long)

    /** The laser pointer trail; it fades out on its own. */
    private val laserTrail = mutableListOf<LaserPoint>()

    private val laserDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = LASER_COLOR
    }

    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = LASER_COLOR
        alpha = 60
    }

    data class StrokePoint(val x: Float, val y: Float, val width: Float)
    private val currentStrokePoints = mutableListOf<StrokePoint>()
    private var lastSmoothedWidth: Float = -1f

    private val fountainPenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    enum class LassoState {
        IDLE,
        SELECTING,
        SELECTED,
        DRAGGING,
        RESIZING
    }

    private var lassoState = LassoState.IDLE

    // Image layer interaction state: 0 none, 1 move, 2 pinch, 3 resize, 4 rotate
    private var imageInteractionMode = 0
    private var imageDragStartX = 0f
    private var imageDragStartY = 0f
    private var imageLayerStartX = 0f
    private var imageLayerStartY = 0f
    private var pinchStartDist = 0f
    private var pinchStartAngle = 0f
    private var pinchStartScale = 1f
    private var pinchStartRotation = 0f
    private var initialTouchDist = 0f
    private var initialTouchAngle = 0f
    private var initialLayerScale = 1f
    private var initialLayerRotation = 0f

    private val layerBboxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2196F3")
        strokeWidth = 2.5f
    }
    private val layerHandleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val layerHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2.5f
    }
    private val layerHandleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    var onZoomOutToContinuous: (() -> Unit)? = null
    private val lassoPath = Path()
    private val lassoBounds = RectF()
    /** Pre-allocated so the draw pass does not allocate on every frame. */
    private val lassoDoneRect = RectF()
    private val lassoDeleteRect = RectF()
    private val layerBboxPath = Path()
    private var selectedBitmap: Bitmap? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val initialLassoBounds = RectF()
    private val handleRadius = 16f

    private val lassoStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FB6443")
        strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val lassoFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1AFB6443")
    }

    private val lassoDonePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.FILL
    }

    private val lassoDeletePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE53935.toInt()
        style = Paint.Style.FILL
    }

    private val lassoPillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FB6443")
        strokeWidth = 3f
    }

    private val actionPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FB6443")
    }

    private val actionPillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val actionPillDeletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E53935")
    }

    private fun getCalibratedPressure(rawPressure: Float): Float {
        val clamped = rawPressure.coerceIn(0.01f, 1.0f)
        return Math.pow(clamped.toDouble(), (1.0 / pressureSensitivity.coerceIn(0.2f, 3.0f))).toFloat()
    }

    private fun calculateFountainPenWidth(rawPressure: Float): Float {
        if (!pressureSizeEnabled) return strokeWidthPx
        val targetWidth = if (rawPressure > 0.01f) {
            val calibrated = getCalibratedPressure(rawPressure)
            strokeWidthPx * (0.35f + calibrated * 1.35f)
        } else {
            if (lastSmoothedWidth > 0f) lastSmoothedWidth else strokeWidthPx
        }
        val smoothed = if (lastSmoothedWidth > 0f) {
            lastSmoothedWidth * 0.65f + targetWidth * 0.35f
        } else {
            targetWidth
        }
        lastSmoothedWidth = smoothed
        return smoothed
    }

    // Tool settings
    var currentTool: CanvasTool = CanvasTool.PEN
        set(value) {
            if (field == CanvasTool.LASSO && value != CanvasTool.LASSO) {
                commitLassoSelection()
            }
            field = value
            updatePaint()
        }

    var strokeColor: Int = Color.BLACK
        set(value) {
            field = value
            updatePaint()
        }

    var strokeWidthPx: Float = 6f
        set(value) {
            field = value
            updatePaint()
        }

    var eraserWidthPx: Float = 36f
        set(value) {
            field = value
            updatePaint()
        }

    var paperPattern: PaperPattern = PaperPattern.BLANK
        set(value) {
            field = value
            invalidate()
        }

    var backgroundMode: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var backgroundDensity: Int = 40
        set(value) {
            field = value
            invalidate()
        }

    var doublePage: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var paperColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    // Paints
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val shapePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1.5f
    }

    private val bitmapPaint = Paint(Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // Everything onDraw needs, allocated once: onDraw runs for every frame of
    // a stroke, so a RectF/Paint per frame is GC churn during writing.
    private val pdfPageDestRect = RectF()
    private val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shapePreviewRect = RectF()
    private val doublePageDividerPaint = Paint().apply {
        color = Color.parseColor("#808080")
        strokeWidth = 2f
        alpha = 80
    }
    private val handlePoint = FloatArray(2)
    private val dividerPoint = FloatArray(2)
    private val lassoCornerPoints = FloatArray(8)

    /**
     * Undo/redo depth derived from the heap and the page size. A 2160×3056
     * page snapshot is ~26 MB, so a fixed depth of 6 could hold ~158 MB on
     * its own; keep history inside roughly a sixth of the heap while still
     * allowing 3–6 steps for typical pages.
     */
    private val maxUndoSnapshots: Int
        get() {
            val bmp = backingBitmap ?: return 4
            if (bmp.width <= 0 || bmp.height <= 0) return 4
            val snapshotBytes = bmp.width.toLong() * bmp.height.toLong() * 4L
            val budget = Runtime.getRuntime().maxMemory() / 6
            return (budget / snapshotBytes).toInt().coerceIn(3, 6)
        }
    private val undoStack = ArrayDeque<UndoSnapshot>()
    private val redoStack = ArrayDeque<UndoSnapshot>()

    var onUndoRedoListener: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onStrokeFinishedListener: (() -> Unit)? = null

    init {
        updatePaint()
        updateViewMatrix()
    }

    private fun updatePaint() {
        when (currentTool) {
            CanvasTool.FOUNTAIN_PEN -> {
                strokePaint.xfermode = null
                strokePaint.color = strokeColor
                strokePaint.strokeWidth = strokeWidthPx
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeJoin = Paint.Join.ROUND
                strokePaint.alpha = 255
            }
            CanvasTool.PEN -> {
                strokePaint.xfermode = null
                strokePaint.color = strokeColor
                strokePaint.strokeWidth = strokeWidthPx
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeJoin = Paint.Join.ROUND
                strokePaint.alpha = 255
            }
            CanvasTool.HIGHLIGHTER -> {
                strokePaint.xfermode = null
                strokePaint.color = strokeColor
                strokePaint.strokeWidth = strokeWidthPx * 3.2f
                strokePaint.strokeCap = Paint.Cap.SQUARE
                strokePaint.strokeJoin = Paint.Join.BEVEL
                strokePaint.alpha = 110
            }
            CanvasTool.ERASER -> {
                strokePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                strokePaint.strokeWidth = eraserWidthPx
                strokePaint.strokeCap = Paint.Cap.ROUND
            }
            CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_CIRCLE, CanvasTool.SHAPE_LINE -> {
                strokePaint.xfermode = null
                strokePaint.color = strokeColor
                strokePaint.strokeWidth = strokeWidthPx
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeJoin = Paint.Join.ROUND
                strokePaint.alpha = 255

                shapePreviewPaint.color = strokeColor
                shapePreviewPaint.strokeWidth = strokeWidthPx
                shapePreviewPaint.alpha = 180
            }
            CanvasTool.LASSO -> {
                strokePaint.xfermode = null
                strokePaint.color = Color.parseColor("#FB6443")
                strokePaint.strokeWidth = 3f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = 200
            }
            CanvasTool.LASER_POINTER -> {
                strokePaint.xfermode = null
                strokePaint.color = LASER_COLOR
                strokePaint.strokeWidth = 6f
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.alpha = 255
            }
        }
    }

    private fun updateViewMatrix() {
        viewMatrix.reset()
        viewMatrix.postScale(scale, scale)
        viewMatrix.postTranslate(panX, panY)
        viewMatrix.invert(pageInverseMatrix)
    }

    fun fitOrCenterPage() {
        if (width <= 0 || height <= 0) return
        val bmp = backingBitmap
        val bmpW = bmp?.width ?: (if (pageType == "a4" || pageType == "pdf") 2160 else width)
        val bmpH = bmp?.height ?: (if (pageType == "a4" || pageType == "pdf") 3056 else height)
        val isFixedPage = pageType == "a4" || pageType == "pdf" || !beyondPaper
        if (isFixedPage) {
            val fitScale = minOf((width * 0.94f) / bmpW, (height * 0.94f) / bmpH, 1.0f)
            scale = fitScale.coerceIn(minZoom, 8f)
        } else {
            scale = 1f
        }
        if (autoCenter) {
            centerTransform(bmpW, bmpH)
        } else {
            panX = 0f
            panY = 0f
        }
        invalidate()
    }

    fun resetTransform() {
        fitOrCenterPage()
    }

    fun setZoomScale(targetScale: Float) {
        scale = targetScale.coerceIn(minZoom, 8f)
        updateViewMatrix()
        invalidate()
    }

    private fun handleCanvasGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    gestureActive = true
                    gestureStartPanX = panX
                    gestureStartPanY = panY
                    gestureStartScale = scale
                    val x0 = event.getX(0); val y0 = event.getY(0)
                    val x1 = event.getX(1); val y1 = event.getY(1)
                    gestureStartMidX = (x0 + x1) / 2f
                    gestureStartMidY = (y0 + y1) / 2f
                    gestureStartDist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                    val s = if (scale > 0.0001f) scale else 1f
                    gestureStartFocalCanvasX = (gestureStartMidX - panX) / s
                    gestureStartFocalCanvasY = (gestureStartMidY - panY) / s
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureActive && event.pointerCount >= 2) {
                    val x0 = event.getX(0); val y0 = event.getY(0)
                    val x1 = event.getX(1); val y1 = event.getY(1)
                    val midX = (x0 + x1) / 2f
                    val midY = (y0 + y1) / 2f
                    val dist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                    if (gestureStartDist > 0f) {
                        val newScale = (gestureStartScale * (dist / gestureStartDist)).coerceIn(minZoom, 8f)
                        scale = newScale
                        panX = midX - gestureStartFocalCanvasX * scale
                        panY = midY - gestureStartFocalCanvasY * scale
                    } else {
                        panX = gestureStartPanX + (midX - gestureStartMidX)
                        panY = gestureStartPanY + (midY - gestureStartMidY)
                    }
                    // Beyond-paper: allow panning freely; else clamp with margin to keep part visible
                    if (!beyondPaper) {
                        val bmpW = backingBitmap?.width?.toFloat() ?: width.toFloat()
                        val bmpH = backingBitmap?.height?.toFloat() ?: height.toFloat()
                        val scaledW = bmpW * scale
                        val scaledH = bmpH * scale
                        val marginX = width * horizontalResistance
                        val marginY = height * 0.4f
                        val minPanX = if (scaledW > width) width - scaledW - marginX else (width - scaledW) / 2f - marginX
                        val maxPanX = if (scaledW > width) marginX else (width - scaledW) / 2f + marginX
                        val minPanY = if (scaledH > height) height - scaledH - marginY else (height - scaledH) / 2f - marginY
                        val maxPanY = if (scaledH > height) marginY else (height - scaledH) / 2f + marginY
                        panX = panX.coerceIn(minPanX, maxPanX)
                        panY = panY.coerceIn(minPanY, maxPanY)
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remainingPointers = event.pointerCount - 1
                if (remainingPointers >= 2) {
                    val upIndex = event.actionIndex
                    var idx0 = 0
                    if (idx0 == upIndex) idx0++
                    var idx1 = idx0 + 1
                    if (idx1 == upIndex) idx1++
                    if (idx1 < event.pointerCount) {
                        gestureStartPanX = panX
                        gestureStartPanY = panY
                        gestureStartScale = scale
                        val x0 = event.getX(idx0); val y0 = event.getY(idx0)
                        val x1 = event.getX(idx1); val y1 = event.getY(idx1)
                        gestureStartMidX = (x0 + x1) / 2f
                        gestureStartMidY = (y0 + y1) / 2f
                        gestureStartDist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                        val s = if (scale > 0.0001f) scale else 1f
                        gestureStartFocalCanvasX = (gestureStartMidX - panX) / s
                        gestureStartFocalCanvasY = (gestureStartMidY - panY) / s
                        return true
                    }
                }
                gestureActive = false
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureActive = false
                return true
            }
        }
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (backingBitmap == null) {
                initBackingBitmap(w, h)
            } else if (oldw == 0 || oldh == 0) {
                fitOrCenterPage()
            }
        }
    }

    private fun initBackingBitmap(width: Int, height: Int) {
        val pageWidth = width.coerceIn(1, 4096)
        val safeHeight = height.coerceIn(1, 4096)
        val targetWidth = if (doublePage) (pageWidth * 2 + 40).coerceIn(1, 4096) else pageWidth
        val bmp = BitmapUtils.createSafeBitmap(targetWidth, safeHeight)
        backingBitmap = bmp
        bitmapCanvas = Canvas(bmp)
        if (autoCenter) centerTransform(targetWidth, safeHeight)
    }

    private fun centerTransform(bitmapWidth: Int, bitmapHeight: Int) {
        panX = (width - bitmapWidth * scale) / 2f
        panY = (height - bitmapHeight * scale) / 2f
    }

    fun loadBitmap(bitmap: Bitmap) {
        val loaded = if (bitmap.isMutable && bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        backingBitmap = loaded
        // Keep the previous bitmap alive for the GC: a debounced stroke save
        // may still hold a reference to it (recycling would abort that save).
        bitmapCanvas = Canvas(loaded)
        undoStack.clear()
        clearRedoStack()
        notifyUndoRedo()
        if (autoCenter && width > 0 && height > 0) {
            val isFixedPage = pageType == "a4" || pageType == "pdf" || !beyondPaper
            if (isFixedPage && (scale == 1f || scale <= 0f)) {
                val fitScale = minOf((width * 0.94f) / loaded.width, (height * 0.94f) / loaded.height, 1.0f)
                scale = fitScale.coerceIn(minZoom, 8f)
            }
            centerTransform(loaded.width, loaded.height)
        }
        invalidate()
    }

    fun setPageContent(bitmap: Bitmap?) {
        commitLassoSelection()
        if (bitmap != null) {
            loadBitmap(bitmap)
        } else {
            val w = if (pdfPageWidthPts > 0f && pdfBasePxPerPt > 0f) {
                (pdfPageWidthPts * pdfBasePxPerPt).roundToInt()
            } else if (pageType == "a4" || pageType == "pdf") {
                2160
            } else if (width > 0) width else 1080
            val h = if (pdfPageHeightPts > 0f && pdfBasePxPerPt > 0f) {
                (pdfPageHeightPts * pdfBasePxPerPt).roundToInt()
            } else if (pageType == "a4" || pageType == "pdf") {
                3056
            } else if (height > 0) height else 1920
            initBackingBitmap(w, h)
            undoStack.clear()
            clearRedoStack()
            notifyUndoRedo()
            invalidate()
        }
    }

    fun insertImageBitmap(image: Bitmap) {
        saveUndoState()
        val c = bitmapCanvas ?: return
        val w = width.toFloat().coerceAtLeast(400f)
        val h = height.toFloat().coerceAtLeast(400f)

        // Scale image to fit comfortably within 70% of canvas
        val maxImgW = w * 0.7f
        val maxImgH = h * 0.7f
        val scale = minOf(maxImgW / image.width, maxImgH / image.height, 1.0f)
        val dstW = image.width * scale
        val dstH = image.height * scale
        val left = (w - dstW) / 2f
        val top = (h - dstH) / 2f
        val dstRect = RectF(left, top, left + dstW, top + dstH)

        val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(image, null, dstRect, imgPaint)
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    fun getBackingBitmap(): Bitmap? = backingBitmap

    fun addImageLayer(image: Bitmap): Boolean = addImageLayerDirect(image, null, null)

    fun addImageLayerDirect(image: Bitmap, posX: Float? = null, posY: Float? = null): Boolean {
        if (imageLayers.size >= 8) return false
        saveUndoState()
        val w = if (pdfPageWidthPts > 0f && pdfBasePxPerPt > 0f) {
            pdfPageWidthPts * pdfBasePxPerPt
        } else if (pageType == "a4" || pageType == "pdf") {
            2160f
        } else if (width > 0) width.toFloat() else 1080f
        val h = if (pdfPageHeightPts > 0f && pdfBasePxPerPt > 0f) {
            pdfPageHeightPts * pdfBasePxPerPt
        } else if (pageType == "a4" || pageType == "pdf") {
            3056f
        } else if (height > 0) height.toFloat() else 1920f
        val maxImgW = w * 0.75f
        val maxImgH = h * 0.75f
        val scale = minOf(maxImgW / image.width, maxImgH / image.height, 1.0f)
        val dstW = image.width * scale
        val dstH = image.height * scale
        val left = if (posX != null) posX else {
            val centerPts = floatArrayOf(width / 2f, height / 2f)
            pageInverseMatrix.mapPoints(centerPts)
            centerPts[0] - dstW / 2f
        }
        val top = if (posY != null) posY else {
            val centerPts = floatArrayOf(width / 2f, height / 2f)
            pageInverseMatrix.mapPoints(centerPts)
            centerPts[1] - dstH / 2f
        }
        imageLayers.add(ImageLayer(image, left, top, scale, 0f, 1f, null, true, "Layer ${imageLayers.size + 1}"))
        layersDirty = true
        selectedImageIndex = imageLayers.lastIndex
        invalidate()
        onStrokeFinishedListener?.invoke()
        return true
    }

    /**
     * Add a pasted / dropped text block as a rendered text layer.
     * Wraps at [maxLineW] using greedy word wrap:
     * draws each line with a bold paint into a high-DPI bitmap, then adds it
     * at [posX]/[posY] (or centered in viewport) as an image layer.
     * Returns true on success.
     */
    fun addTextBlock(
        text: String,
        color: Int = Color.BLACK,
        fontSizeSp: Float = 24f,
        posX: Float? = null,
        posY: Float? = null
    ): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val w = if (pdfPageWidthPts > 0f && pdfBasePxPerPt > 0f) {
            pdfPageWidthPts * pdfBasePxPerPt
        } else if (pageType == "a4" || pageType == "pdf") {
            2160f
        } else if (width > 0) width.toFloat() else 1080f
        val maxLineW = (w * 0.75f).coerceAtLeast(160f)
        val textPx = (fontSizeSp * 2.2f).coerceIn(24f, 120f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = textPx
            isFakeBoldText = true
        }
        // Manual greedy word wrap
        val lines = mutableListOf<String>()
        for (para in clean.split("\n")) {
            val words = para.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) { lines.add(""); continue }
            var cur = ""
            for (word in words) {
                val trial = if (cur.isEmpty()) word else "$cur $word"
                if (paint.measureText(trial) <= maxLineW || cur.isEmpty()) {
                    cur = trial
                } else {
                    lines.add(cur)
                    cur = word
                }
            }
            if (cur.isNotEmpty()) lines.add(cur)
        }
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent + (textPx * 0.25f)
        val bmpW = lines.maxOfOrNull { paint.measureText(it).toInt() + 32 }?.coerceIn(60, 2400) ?: 200
        val bmpH = (lineH * lines.size + 24f).toInt().coerceIn(60, 3200)
        val bmp = BitmapUtils.createSafeBitmap(bmpW, bmpH)
        val c = Canvas(bmp)
        var y = -fm.ascent + 12f
        for (line in lines) {
            c.drawText(line, 16f, y, paint)
            y += lineH
        }
        return addImageLayerDirect(bmp, posX, posY)
    }

    /**
     * Convenience wrapper that does the whole load synchronously: the
     * pure-I/O read plus the main-thread apply. Prefer the split API
     * ([readLayersFromDisk] on Dispatchers.IO + [applyLoadedLayers] on main)
     * from Compose callers so PNG decoding never blocks the UI thread.
     */
    fun loadImageLayers(fileStore: personal.notes.touch.data.files.NoteFileStore, noteId: String) {
        applyLoadedLayers(readLayersFromDisk(fileStore, noteId))
    }

    /**
     * Pure-I/O part of [loadImageLayers]: reads the layer info JSON and
     * decodes the layer PNGs. Contains no view mutation, so it is safe to
     * call from a background thread (callers should use Dispatchers.IO).
     *
     * Returns `null` when the note has no layer file yet — the caller must
     * then leave the in-memory layers untouched (same early-return the
     * original [loadImageLayers] performed). A partially parsed list is
     * returned on parse failure (mirrors the original behavior of keeping
     * whatever was read); ordinary IO failures produce an empty list.
     */
    fun readLayersFromDisk(fileStore: personal.notes.touch.data.files.NoteFileStore, noteId: String): List<ImageLayer>? {
        val infoFile = fileStore.getLayerInfoFile(noteId)
        if (!infoFile.exists()) return null
        val layers = mutableListOf<ImageLayer>()
        try {
            val arr = org.json.JSONArray(infoFile.readText())
            val layersDir = fileStore.getLayersDir(noteId)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fileName = obj.getString("file")
                val bitmapFile = java.io.File(layersDir, fileName)
                val bitmap = android.graphics.BitmapFactory.decodeFile(bitmapFile.absolutePath)?.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                if (bitmap != null) {
                    val ink = mutableListOf<InkCrop>()
                    val inkArr = obj.optJSONArray("ink")
                    if (inkArr != null) {
                        for (k in 0 until inkArr.length()) {
                            val inkObj = inkArr.optJSONObject(k) ?: continue
                            val inkFile = inkObj.optString("file", "")
                            if (inkFile.isEmpty()) continue
                            val inkBitmap = android.graphics.BitmapFactory
                                .decodeFile(java.io.File(layersDir, inkFile).absolutePath)
                                ?.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: continue
                            ink.add(
                                InkCrop(
                                    inkBitmap,
                                    inkObj.optDouble("x", 0.0).toFloat(),
                                    inkObj.optDouble("y", 0.0).toFloat()
                                )
                            )
                        }
                    }
                    layers.add(ImageLayer(
                        bitmap = bitmap,
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        visible = obj.optBoolean("visible", true),
                        cropRect = null,
                        name = obj.optString("name", "Layer ${i + 1}"),
                        ink = ink
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return layers
    }

    /**
     * UI-apply part of [loadImageLayers] — main thread only. Extracts the
     * decoded data into the view state and repaints. A `null` input (no
     * layer file for this note) leaves the current layers untouched.
     */
    fun applyLoadedLayers(layers: List<ImageLayer>?) {
        if (layers == null) return
        imageLayers.clear()
        imageLayers.addAll(layers)
        layersDirty = false
        selectedImageIndex = -1
        invalidate()
    }

    /** Immutable copy of one image layer's geometry + bitmap reference. */
    private data class LayerSnapshot(
        val bitmap: Bitmap,
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotation: Float,
        val opacity: Float,
        val visible: Boolean,
        val name: String,
        /** Ink written on the layer while it was the current one. */
        val ink: List<InkCrop> = emptyList()
    )

    /**
     * Writes the ink that sits outside the paper of this page: one
     * transparent PNG per crop plus a small manifest that keeps their
     * positions. Stale crops of the same page are removed.
     */
    private fun writeBeyondPageInk(
        layersDir: java.io.File,
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String
    ) {
        try {
            val prefix = "beyond_${pageIndex}_"
            val manifest = fileStore.getBeyondInkFile(noteId, pageIndex)
            val arr = org.json.JSONArray()
            val written = mutableSetOf<String>()
            beyondPageInk.forEachIndexed { index, crop ->
                if (crop.bitmap.isRecycled) return@forEachIndexed
                val name = "${prefix}${index}.png"
                written.add(name)
                java.io.FileOutputStream(java.io.File(layersDir, name)).use { fos ->
                    crop.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                }
                val obj = org.json.JSONObject()
                obj.put("file", name)
                obj.put("x", crop.x.toDouble())
                obj.put("y", crop.y.toDouble())
                arr.put(obj)
            }
            manifest?.writeText(arr.toString())
            layersDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix) && file.name.endsWith(".png") && !written.contains(file.name)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Loads the ink written outside the paper of [pageIndex]. */
    fun readBeyondPageInk(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int
    ): List<InkCrop> {
        val manifest = fileStore.getBeyondInkFile(noteId, pageIndex)
        if (!manifest.exists()) return emptyList()
        val crops = mutableListOf<InkCrop>()
        try {
            val arr = org.json.JSONArray(manifest.readText())
            val layersDir = fileStore.getLayersDir(noteId)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("file", "")
                if (name.isEmpty()) continue
                val bmp = android.graphics.BitmapFactory
                    .decodeFile(java.io.File(layersDir, name).absolutePath)
                    ?.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: continue
                crops.add(
                    InkCrop(bmp, obj.optDouble("x", 0.0).toFloat(), obj.optDouble("y", 0.0).toFloat())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return crops
    }

    /** Replaces the ink outside the paper with the given crops. */
    fun applyBeyondPageInk(crops: List<InkCrop>) {
        beyondPageInk.clear()
        beyondPageInk.addAll(crops)
        invalidate()
    }

    /**
     * Disk part of the layer save (PNG compression + JSON write). Contains
     * no view mutation or repaint, so it is safe to run entirely on
     * Dispatchers.IO — which is what every CanvasScreen caller now does.
     * The layer geometry is snapshotted before the (slow) compressions so
     * concurrent main-thread edits during the save cannot skew the output.
     */
    fun saveImageLayers(fileStore: personal.notes.touch.data.files.NoteFileStore, noteId: String) {
        val layersDir = fileStore.getLayersDir(noteId)
        val infoFile = fileStore.getLayerInfoFile(noteId)
        try {
            val snapshot = imageLayers.map { layer ->
                LayerSnapshot(
                    bitmap = layer.bitmap,
                    x = layer.x,
                    y = layer.y,
                    scale = layer.scale,
                    rotation = layer.rotation,
                    opacity = layer.opacity,
                    visible = layer.visible,
                    name = layer.name,
                    ink = layer.ink.toList()
                )
            }
            val arr = org.json.JSONArray()
            val writtenFiles = mutableSetOf<String>()
            snapshot.forEachIndexed { index, layer ->
                val fileName = "layer_$index.png"
                writtenFiles.add(fileName)
                val file = java.io.File(layersDir, fileName)
                java.io.FileOutputStream(file).use { fos ->
                    layer.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                }
                val obj = org.json.JSONObject()
                obj.put("file", fileName)
                obj.put("x", layer.x.toDouble())
                obj.put("y", layer.y.toDouble())
                obj.put("scale", layer.scale.toDouble())
                obj.put("rotation", layer.rotation.toDouble())
                obj.put("opacity", layer.opacity.toDouble())
                obj.put("visible", layer.visible)
                obj.put("name", layer.name)
                // Ink written on this layer: one transparent PNG per crop.
                val inkArr = org.json.JSONArray()
                layer.ink.forEachIndexed { inkIdx, crop ->
                    if (crop.bitmap.isRecycled) return@forEachIndexed
                    val inkName = "layer_${index}_ink_${inkIdx}.png"
                    writtenFiles.add(inkName)
                    java.io.FileOutputStream(java.io.File(layersDir, inkName)).use { fos ->
                        crop.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    val inkObj = org.json.JSONObject()
                    inkObj.put("file", inkName)
                    inkObj.put("x", crop.x.toDouble())
                    inkObj.put("y", crop.y.toDouble())
                    inkArr.put(inkObj)
                }
                obj.put("ink", inkArr)
                arr.put(obj)
            }
            infoFile.writeText(arr.toString())
            layersDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("layer_") && file.name.endsWith(".png") && !writtenFiles.contains(file.name)) {
                    file.delete()
                }
            }
            writeBeyondPageInk(layersDir, fileStore, noteId)
            layersDirty = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        if (index in imageLayers.indices) {
            imageLayers[index].visible = visible
            layersDirty = true
            invalidate()
        }
    }

    fun layerCount(): Int = imageLayers.size

    fun deleteImageLayer(index: Int) {
        if (index in imageLayers.indices) {
            saveUndoState()
            imageLayers.removeAt(index)
            layersDirty = true
            if (selectedImageIndex == index) selectedImageIndex = -1
            else if (selectedImageIndex > index) selectedImageIndex -= 1
            invalidate()
            onStrokeFinishedListener?.invoke()
        }
    }

    fun moveImageLayer(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in imageLayers.indices || toIndex !in imageLayers.indices || fromIndex == toIndex) return false
        val layer = imageLayers.removeAt(fromIndex)
        imageLayers.add(toIndex, layer)
        layersDirty = true
        if (selectedImageIndex == fromIndex) selectedImageIndex = toIndex
        else if (selectedImageIndex > fromIndex && selectedImageIndex <= toIndex) selectedImageIndex -= 1
        else if (selectedImageIndex < fromIndex && selectedImageIndex >= toIndex) selectedImageIndex += 1
        invalidate()
        onStrokeFinishedListener?.invoke()
        return true
    }

    fun deleteSelectedImageLayer() {
        if (selectedImageIndex in imageLayers.indices) {
            deleteImageLayer(selectedImageIndex)
        }
    }

    fun mergeAllLayersToBase() {
        val c = bitmapCanvas ?: return
        // Merging flattens the layers into the page: it has to be undoable.
        saveUndoState()
        val paper = paperRect()
        for (layer in imageLayers) {
            if (!layer.visible) continue
            if (layer.bitmap.isRecycled) continue
            layerPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
            val w = layer.bitmap.width
            val h = layer.bitmap.height
            val cx = layer.x + w * layer.scale / 2f
            val cy = layer.y + h * layer.scale / 2f
            c.save()
            c.translate(cx, cy)
            c.rotate(layer.rotation)
            c.scale(layer.scale, layer.scale)
            c.drawBitmap(layer.bitmap, -w / 2f, -h / 2f, layerPaint)
            c.restore()
            // Flatten the ink written on the layer: marks that sit on the
            // paper become part of the page ink, marks beyond the paper stay
            // page-level crops so they survive the merge.
            for (crop in layer.ink) {
                if (crop.bitmap.isRecycled) continue
                val cropRect = Rect(
                    crop.x.toInt(), crop.y.toInt(),
                    (crop.x + crop.bitmap.width).toInt(), (crop.y + crop.bitmap.height).toInt()
                )
                if (paper.contains(cropRect)) {
                    c.drawBitmap(crop.bitmap, crop.x, crop.y, layerPaint)
                } else {
                    beyondPageInk.add(crop)
                }
            }
        }
        imageLayers.clear()
        layersDirty = true
        selectedImageIndex = -1
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    fun mergeSelectedLayersToBase() = mergeAllLayersToBase()

    fun setLayerOpacity(index: Int, value: Float) {
        if (index in imageLayers.indices) {
            imageLayers[index].opacity = value.coerceIn(0f, 1f)
            layersDirty = true
            invalidate()
        }
    }

    fun cropLayer(index: Int, rect: Rect) {
        if (index !in imageLayers.indices) return
        val layer = imageLayers[index]
        val src = layer.bitmap
        val left = rect.left.coerceIn(0, src.width - 1)
        val top = rect.top.coerceIn(0, src.height - 1)
        val right = rect.right.coerceIn(left + 1, src.width)
        val bottom = rect.bottom.coerceIn(top + 1, src.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return
        val cropped = Bitmap.createBitmap(src, left, top, w, h)
        imageLayers[index] = ImageLayer(cropped, layer.x, layer.y, layer.scale, layer.rotation, layer.opacity, rect, layer.visible, layer.name)
        invalidate()
    }

    fun cropSelectedImageLayer() {
        // Cropping changes the layer: keep it undoable.
        saveUndoState()
        val idx = selectedImageIndex
        if (idx !in imageLayers.indices) return
        val layer = imageLayers[idx]
        // No-op if full bounds
        if (layer.cropRect == null) return
    }

    fun layerBounds(layer: ImageLayer): RectF {
        val w = layer.bitmap.width * layer.scale
        val h = layer.bitmap.height * layer.scale
        return RectF(layer.x, layer.y, layer.x + w, layer.y + h)
    }

    private fun topmostImageLayerIndex(x: Float, y: Float): Int {
        for (i in imageLayers.indices.reversed()) {
            val layer = imageLayers[i]
            if (layer.bitmap.isRecycled) continue
            val w = layer.bitmap.width * layer.scale
            val h = layer.bitmap.height * layer.scale
            val cx = layer.x + w / 2f
            val cy = layer.y + h / 2f
            val dx = x - cx
            val dy = y - cy
            val ang = Math.toRadians((-layer.rotation).toDouble())
            val lx = dx * Math.cos(ang) - dy * Math.sin(ang)
            val ly = dx * Math.sin(ang) + dy * Math.cos(ang)
            val slop = 16f
            if (Math.abs(lx) <= (w / 2f + slop) && Math.abs(ly) <= (h / 2f + slop)) return i
        }
        return -1
    }


    fun commitLassoSelection() {
        val bmp = selectedBitmap
        if (bmp != null && !bmp.isRecycled) {
            val c = bitmapCanvas
            if (c != null) {
                c.drawBitmap(bmp, null, lassoBounds, null)
            }
            bmp.recycle()
            selectedBitmap = null
        }
        lassoState = LassoState.IDLE
        lassoPath.reset()
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    fun deleteLassoSelection() {
        val bmp = selectedBitmap
        if (bmp != null && !bmp.isRecycled) {
            bmp.recycle()
            selectedBitmap = null
        }
        lassoState = LassoState.IDLE
        lassoPath.reset()
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val isPageBounded = pageType != "infinite"
        if (isPageBounded) {
            canvas.drawColor(android.graphics.Color.rgb(0xEE, 0xEE, 0xEE))
        }

        canvas.save()
        canvas.concat(viewMatrix)

        val pageW = (backingBitmap?.width?.toFloat() ?: pdfPageRectWidth()).takeIf { it > 0f } ?: 2160f
        val pageH = (backingBitmap?.height?.toFloat() ?: pdfPageRectHeight()).takeIf { it > 0f } ?: 3056f

        if (isPageBounded) {
            paperBedPaint.color = paperColor
            canvas.drawRect(0f, 0f, pageW, pageH, paperBedPaint)
        } else if (backgroundBitmap == null) {
            canvas.drawColor(paperColor)
        }

        // 0. Draw non-destructive background (e.g. imported PDF page) first.
        // A vector-rendered background is drawn into the fixed page rect so a
        // higher render resolution never changes the page geometry.
        backgroundBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                if (pdfRenderScale > 0f && pdfPageWidthPts > 0f) {
                    // Stretch the vector render into the page rect (the same
                    // rect the page raster occupies) so annotations, which
                    // live in backing-bitmap space, stay aligned.
                    pdfPageDestRect.set(0f, 0f, pdfPageRectWidth(), pdfPageRectHeight())
                    canvas.drawBitmap(bmp, null, pdfPageDestRect, bitmapPaint)
                } else {
                    canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
                }
            }
        }

        // 2. Draw paper pattern
        drawPaperPattern(canvas)

        // 3. Draw committed bitmap
        backingBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        if (isPageBounded) {
            canvas.drawRect(0f, 0f, pageW, pageH, pageBorderPaint)
        }

        // 3a. F-10: PDF text selection highlight (cached rects, no native call
        // per frame; recomputed only when the selection changes).
        if (pdfTextSelectionMode && selRects.isNotEmpty()) {
            for (r in selRects) {
                canvas.drawRect(r[0], r[1], r[2], r[3], pdfSelectionPaint)
            }
        }

        // 3b. Draw transformable image layers
        for (i in imageLayers.indices) {
            val layer = imageLayers[i]
            if (!layer.visible) continue
            if (layer.bitmap.isRecycled) continue
            layerPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
            val w = layer.bitmap.width
            val h = layer.bitmap.height
            val cx = layer.x + w * layer.scale / 2f
            val cy = layer.y + h * layer.scale / 2f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(layer.rotation)
            canvas.scale(layer.scale, layer.scale)
            canvas.drawBitmap(layer.bitmap, -w / 2f, -h / 2f, layerPaint)
            // Ink written on this layer follows the layer's own transform, so
            // moving or rotating the layer carries its marks along.
            for (crop in layer.ink) {
                if (crop.bitmap.isRecycled) continue
                val iw = crop.bitmap.width
                val ih = crop.bitmap.height
                val dx = (crop.x + iw / 2f) - cx
                val dy = (crop.y + ih / 2f) - cy
                val rad = Math.toRadians((-layer.rotation).toDouble())
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                val lx = (dx * cos - dy * sin) / layer.scale - iw / 2f
                val ly = (dx * sin + dy * cos) / layer.scale - ih / 2f
                canvas.drawBitmap(crop.bitmap, lx, ly, layerPaint)
            }
            canvas.restore()
        }

        // 3c. Ink written outside the paper: page-level crops painted over the
        // canvas surround, so a PDF page can be annotated in its margins.
        for (crop in beyondPageInk) {
            if (crop.bitmap.isRecycled) continue
            canvas.drawBitmap(crop.bitmap, crop.x, crop.y, bitmapPaint)
        }

        // 4. Draw current in-progress stroke or shape preview
        if (isDrawingShape) {
            when (currentTool) {
                CanvasTool.SHAPE_RECT -> {
                    shapePreviewRect.set(
                        minOf(startTouchX, currentTouchX),
                        minOf(startTouchY, currentTouchY),
                        maxOf(startTouchX, currentTouchX),
                        maxOf(startTouchY, currentTouchY)
                    )
                    canvas.drawRect(shapePreviewRect, shapePreviewPaint)
                }
                CanvasTool.SHAPE_CIRCLE -> {
                    val radius = Math.hypot(
                        (currentTouchX - startTouchX).toDouble(),
                        (currentTouchY - startTouchY).toDouble()
                    ).toFloat() / 2f
                    val cx = (startTouchX + currentTouchX) / 2f
                    val cy = (startTouchY + currentTouchY) / 2f
                    canvas.drawCircle(cx, cy, radius, shapePreviewPaint)
                }
                CanvasTool.SHAPE_LINE -> {
                    canvas.drawLine(startTouchX, startTouchY, currentTouchX, currentTouchY, shapePreviewPaint)
                }
                else -> {}
            }
        } else if (currentTool == CanvasTool.FOUNTAIN_PEN && currentStrokePoints.isNotEmpty()) {
            drawFountainPenStroke(canvas, currentStrokePoints, strokePaint)
        } else if (!currentPath.isEmpty) {
            canvas.drawPath(currentPath, strokePaint)
        }

        // 5. Draw Lasso selection overlay (content)
        if (lassoState == LassoState.SELECTING) {
            canvas.drawPath(lassoPath, lassoFillPaint)
            canvas.drawPath(lassoPath, lassoStrokePaint)
        } else if (lassoState in listOf(LassoState.SELECTED, LassoState.DRAGGING, LassoState.RESIZING)) {
            selectedBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                    canvas.drawBitmap(bmp, null, lassoBounds, null)
                }
            }
            canvas.drawRect(lassoBounds, lassoStrokePaint)

            if (lassoState == LassoState.SELECTED) {
                val pillTop = if (lassoBounds.top < 70f) lassoBounds.bottom + 15f else lassoBounds.top - 65f
                val pillBottom = if (lassoBounds.top < 70f) lassoBounds.bottom + 65f else lassoBounds.top - 15f
                val doneRect = lassoDoneRect.apply {
                    set(lassoBounds.centerX() - 120f, pillTop, lassoBounds.centerX() - 10f, pillBottom)
                }
                val deleteRect = lassoDeleteRect.apply {
                    set(lassoBounds.centerX() + 10f, pillTop, lassoBounds.centerX() + 120f, pillBottom)
                }

                canvas.drawRoundRect(doneRect, 16f, 16f, lassoDonePillPaint)
                canvas.drawText("✓ Done", doneRect.centerX(), doneRect.centerY() + 10f, lassoPillTextPaint)

                canvas.drawRoundRect(deleteRect, 16f, 16f, lassoDeletePillPaint)
                canvas.drawText("✕ Cut", deleteRect.centerX(), deleteRect.centerY() + 10f, lassoPillTextPaint)
            }
        }

        canvas.restore()

        if (doublePage) {
            val bmpW = (backingBitmap?.width ?: width).toFloat()
            dividerPoint[0] = bmpW / 2f
            dividerPoint[1] = 0f
            viewMatrix.mapPoints(dividerPoint)
            canvas.drawLine(
                dividerPoint[0], 0f, dividerPoint[0], height.toFloat(), doublePageDividerPaint
            )
        }

        // Selection handles in view space
        if (selectedImageIndex in imageLayers.indices) {
            val layer = imageLayers[selectedImageIndex]
            if (!layer.bitmap.isRecycled) {
                val w = layer.bitmap.width * layer.scale
                val h = layer.bitmap.height * layer.scale
                val cx = layer.x + w / 2f
                val cy = layer.y + h / 2f
                val halfW = w / 2f
                val halfH = h / 2f
                val rad = Math.toRadians(layer.rotation.toDouble())
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                fun rotPt(dx: Float, dy: Float): Pair<Float, Float> {
                    return Pair(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
                }
                val pTL = rotPt(-halfW, -halfH)
                val pTR = rotPt(halfW, -halfH)
                val pBR = rotPt(halfW, halfH)
                val pBL = rotPt(-halfW, halfH)
                val pTC = rotPt(0f, -halfH)
                val pRot = rotPt(0f, -halfH - 45f)
                val pts = floatArrayOf(
                    pTL.first, pTL.second,
                    pTR.first, pTR.second,
                    pBR.first, pBR.second,
                    pBL.first, pBL.second,
                    pTC.first, pTC.second,
                    pRot.first, pRot.second
                )
                viewMatrix.mapPoints(pts)
                val vTLx = pts[0]; val vTLy = pts[1]
                val vTRx = pts[2]; val vTRy = pts[3]
                val vBRx = pts[4]; val vBRy = pts[5]
                val vBLx = pts[6]; val vBLy = pts[7]
                val vTCx = pts[8]; val vTCy = pts[9]
                val vRotX = pts[10]; val vRotY = pts[11]

                // Bounding box border
                val bboxPath = layerBboxPath.apply {
                    reset()
                    moveTo(vTLx, vTLy)
                    lineTo(vTRx, vTRy)
                    lineTo(vBRx, vBRy)
                    lineTo(vBLx, vBLy)
                    close()
                }
                canvas.drawPath(bboxPath, layerBboxPaint)

                // Rotate stem line
                canvas.drawLine(vTCx, vTCy, vRotX, vRotY, layerBboxPaint)

                val handleR = 18f

                // Rotate handle (Top, Green)
                layerHandleFillPaint.color = android.graphics.Color.rgb(0x4C, 0xAF, 0x50)
                canvas.drawCircle(vRotX, vRotY, handleR, layerHandleFillPaint)
                canvas.drawCircle(vRotX, vRotY, handleR, layerHandleStrokePaint)
                layerHandleTextPaint.textSize = 20f
                canvas.drawText("↻", vRotX, vRotY + 7f, layerHandleTextPaint)

                // Delete handle (Top-Left, Red)
                layerHandleFillPaint.color = android.graphics.Color.rgb(0xE5, 0x39, 0x35)
                canvas.drawCircle(vTLx, vTLy, handleR, layerHandleFillPaint)
                canvas.drawCircle(vTLx, vTLy, handleR, layerHandleStrokePaint)
                layerHandleTextPaint.textSize = 18f
                canvas.drawText("✕", vTLx, vTLy + 6f, layerHandleTextPaint)

                // Resize handle (Bottom-Right, Blue)
                layerHandleFillPaint.color = android.graphics.Color.rgb(0x21, 0x96, 0xF3)
                canvas.drawCircle(vBRx, vBRy, handleR, layerHandleFillPaint)
                canvas.drawCircle(vBRx, vBRy, handleR, layerHandleStrokePaint)
                layerHandleTextPaint.textSize = 18f
                canvas.drawText("⤡", vBRx, vBRy + 6f, layerHandleTextPaint)
            }
        }
        if (lassoState in listOf(LassoState.SELECTED, LassoState.DRAGGING, LassoState.RESIZING)) {
            lassoCornerPoints[0] = lassoBounds.left
            lassoCornerPoints[1] = lassoBounds.top
            lassoCornerPoints[2] = lassoBounds.right
            lassoCornerPoints[3] = lassoBounds.top
            lassoCornerPoints[4] = lassoBounds.left
            lassoCornerPoints[5] = lassoBounds.bottom
            lassoCornerPoints[6] = lassoBounds.right
            lassoCornerPoints[7] = lassoBounds.bottom
            viewMatrix.mapPoints(lassoCornerPoints)
            for (i in 0 until lassoCornerPoints.size step 2) {
                canvas.drawCircle(lassoCornerPoints[i], lassoCornerPoints[i + 1], handleRadius, handlePaint)
                canvas.drawCircle(
                    lassoCornerPoints[i], lassoCornerPoints[i + 1], handleRadius, handleBorderPaint
                )
            }
        }
    }

    private fun drawPaperPattern(canvas: Canvas) {
        if (backgroundBitmap != null) return

        val pageW = (backingBitmap?.width?.toFloat() ?: pdfPageRectWidth()).takeIf { it > 0f } ?: width.toFloat()
        val pageH = (backingBitmap?.height?.toFloat() ?: pdfPageRectHeight()).takeIf { it > 0f } ?: height.toFloat()
        if (pageW <= 0 || pageH <= 0) return

        // Compute visible viewport in page space so grid covers full view under pan/zoom
        val viewCorners = floatArrayOf(0f, 0f, width.toFloat(), height.toFloat())
        pageInverseMatrix.mapPoints(viewCorners)
        val viewLeft = minOf(viewCorners[0], viewCorners[2])
        val viewTop = minOf(viewCorners[1], viewCorners[3])
        val viewRight = maxOf(viewCorners[0], viewCorners[2])
        val viewBottom = maxOf(viewCorners[1], viewCorners[3])

        val isUnbounded = beyondPaper && pageType == "infinite"
        val left = if (isUnbounded) minOf(0f, viewLeft) else 0f
        val top = if (isUnbounded) minOf(0f, viewTop) else 0f
        val right = if (isUnbounded) maxOf(pageW, viewRight) else pageW
        val bottom = if (isUnbounded) maxOf(pageH, viewBottom) else pageH

        val step = backgroundDensity.toFloat().coerceIn(20f, 80f)
        val startX = (Math.floor((left / step).toDouble()) * step).toFloat()
        val startY = (Math.floor((top / step).toDouble()) * step).toFloat()

        // The Template (Paper Pattern) tool is the primary driver: GRID=square
        // grid, LINES=horizontal lines, DOTTED=dot grid. It is remapped onto
        // the legacy renderer branch numbering (1=lined, 2=grid, 3=dotted,
        // matching the "Selectable Paper Background" setting's labels), and the
        // legacy backgroundMode is kept as the fallback when the pattern is
        // BLANK so existing preference setups keep rendering. When both are
        // set (conflicting settings), paperPattern wins deterministically.
        // The pattern is drawn in page space (this canvas is viewMatrix
        // transformed) under the committed ink, and never baked into the
        // saved page bitmap because only onDraw runs this.
        val mode: Int = when (paperPattern) {
            PaperPattern.GRID -> 2    // square grid
            PaperPattern.LINES -> 1   // horizontal lined
            PaperPattern.DOTTED -> 3  // dot grid
            PaperPattern.BLANK -> backgroundMode
        }
        when (mode) {
            1 -> { // lined
                var y = startY
                while (y <= bottom) {
                    canvas.drawLine(left, y, right, y, patternPaint)
                    y += step
                }
            }
            2 -> { // grid
                var x = startX
                while (x <= right) {
                    canvas.drawLine(x, top, x, bottom, patternPaint)
                    x += step
                }
                var y = startY
                while (y <= bottom) {
                    canvas.drawLine(left, y, right, y, patternPaint)
                    y += step
                }
            }
            3 -> { // dotted
                val dotRadius = 2f
                var x = startX
                while (x <= right) {
                    var y = startY
                    while (y <= bottom) {
                        canvas.drawCircle(x, y, dotRadius, patternPaint)
                        y += step
                    }
                    x += step
                }
            }
            else -> { /* blank */ }
        }
    }

    private fun handleImageLayerTouchEvent(event: MotionEvent, x: Float, y: Float): Boolean {
        if (imageLayers.isEmpty()) return false

        var vTLx = 0f; var vTLy = 0f
        var vBRx = 0f; var vBRy = 0f
        var vRotX = 0f; var vRotY = 0f
        var vCX = 0f; var vCY = 0f
        val selected = imageLayers.getOrNull(selectedImageIndex)
        if (selected != null && !selected.bitmap.isRecycled) {
            val w = selected.bitmap.width * selected.scale
            val h = selected.bitmap.height * selected.scale
            val cx = selected.x + w / 2f
            val cy = selected.y + h / 2f
            val halfW = w / 2f
            val halfH = h / 2f
            val rad = Math.toRadians(selected.rotation.toDouble())
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            fun rotPt(dx: Float, dy: Float): Pair<Float, Float> {
                return Pair(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
            }
            val pTL = rotPt(-halfW, -halfH)
            val pBR = rotPt(halfW, halfH)
            val pRot = rotPt(0f, -halfH - 45f)
            val pts = floatArrayOf(
                pTL.first, pTL.second,
                pBR.first, pBR.second,
                pRot.first, pRot.second,
                cx, cy
            )
            viewMatrix.mapPoints(pts)
            vTLx = pts[0]; vTLy = pts[1]
            vBRx = pts[2]; vBRy = pts[3]
            vRotX = pts[4]; vRotY = pts[5]
            vCX = pts[6]; vCY = pts[7]
        }

        val handleTouchSlop = 48f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (selected != null) {
                    // 1. Check Delete handle (Top-Left, Red ✕)
                    if (Math.hypot((event.x - vTLx).toDouble(), (event.y - vTLy).toDouble()) <= handleTouchSlop) {
                        deleteImageLayer(selectedImageIndex)
                        return true
                    }
                    // 2. Check Rotate handle (Top stem, Green ↻)
                    if (Math.hypot((event.x - vRotX).toDouble(), (event.y - vRotY).toDouble()) <= handleTouchSlop) {
                        saveUndoState()
                        imageInteractionMode = 4 // ROTATE
                        initialTouchAngle = Math.toDegrees(Math.atan2((event.y - vCY).toDouble(), (event.x - vCX).toDouble())).toFloat()
                        initialLayerRotation = selected.rotation
                        return true
                    }
                    // 3. Check Resize handle (Bottom-Right, Blue ⤡)
                    if (Math.hypot((event.x - vBRx).toDouble(), (event.y - vBRy).toDouble()) <= handleTouchSlop) {
                        saveUndoState()
                        imageInteractionMode = 3 // RESIZE
                        initialTouchDist = Math.hypot((event.x - vCX).toDouble(), (event.y - vCY).toDouble()).toFloat()
                        initialLayerScale = selected.scale
                        return true
                    }
                }

                // 4. Check hit on any layer body to select & start drag/drop
                val idx = topmostImageLayerIndex(x, y)
                if (idx >= 0) {
                    saveUndoState()
                    selectedImageIndex = idx
                    val layer = imageLayers[idx]
                    imageInteractionMode = 1 // MOVE
                    imageDragStartX = x
                    imageDragStartY = y
                    imageLayerStartX = layer.x
                    imageLayerStartY = layer.y
                    invalidate()
                    return true
                }

                // Clicked outside any layer: deselect if previously selected
                if (selectedImageIndex >= 0) {
                    selectedImageIndex = -1
                    invalidate()
                }
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (selectedImageIndex in imageLayers.indices && event.pointerCount >= 2) {
                    val x0 = event.getX(0); val y0 = event.getY(0)
                    val x1 = event.getX(1); val y1 = event.getY(1)
                    pinchStartDist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                    pinchStartAngle = Math.toDegrees(Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
                    val layer = imageLayers[selectedImageIndex]
                    pinchStartScale = layer.scale
                    pinchStartRotation = layer.rotation
                    imageInteractionMode = 2 // PINCH
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedImageIndex !in imageLayers.indices) return false
                val layer = imageLayers[selectedImageIndex]
                when (imageInteractionMode) {
                    4 -> { // ROTATE
                        val curAngle = Math.toDegrees(Math.atan2((event.y - vCY).toDouble(), (event.x - vCX).toDouble())).toFloat()
                        layer.rotation = initialLayerRotation + (curAngle - initialTouchAngle)
                        invalidate()
                        return true
                    }
                    3 -> { // RESIZE
                        val curDist = Math.hypot((event.x - vCX).toDouble(), (event.y - vCY).toDouble()).toFloat()
                        if (initialTouchDist > 5f) {
                            layer.scale = (initialLayerScale * (curDist / initialTouchDist)).coerceIn(0.1f, 10f)
                        }
                        invalidate()
                        return true
                    }
                    2 -> { // PINCH
                        if (event.pointerCount >= 2) {
                            val x0 = event.getX(0); val y0 = event.getY(0)
                            val x1 = event.getX(1); val y1 = event.getY(1)
                            val dist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                            val angle = Math.toDegrees(Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
                            if (pinchStartDist > 0f) {
                                layer.scale = (pinchStartScale * (dist / pinchStartDist)).coerceIn(0.1f, 10f)
                                layer.rotation = pinchStartRotation + (angle - pinchStartAngle)
                            }
                            invalidate()
                            return true
                        }
                    }
                    1 -> { // MOVE
                        if (event.pointerCount == 1) {
                            val dx = x - imageDragStartX
                            val dy = y - imageDragStartY
                            layer.x = imageLayerStartX + dx
                            layer.y = imageLayerStartY + dy
                            invalidate()
                            return true
                        }
                    }
                }
                return false
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (imageInteractionMode != 0) {
                    imageInteractionMode = if (event.pointerCount <= 1) 0 else imageInteractionMode
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        imageInteractionMode = 0
                        layersDirty = true
                        onStrokeFinishedListener?.invoke()
                    }
                    return true
                }
                return false
            }
        }
        return false
    }

    /**
     * Drawing apps still need a click action for accessibility services and
     * keyboard/D-pad activation; a tap on the canvas is the natural one. See
     * the single-pointer tap detection at the end of [onTouchEvent]'s gesture
     * bookkeeping.
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Track multi-finger taps (3-finger paste) and tap/double-tap detection.
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    tapDownX = event.x
                    tapDownY = event.y
                }
                if (event.pointerCount >= 2) {
                    if (multiTouchStartMs == 0L) multiTouchStartMs = event.eventTime
                    multiTouchMaxPointers = maxOf(multiTouchMaxPointers, event.pointerCount)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (multiTouchMaxPointers >= 3) {
                    val dur = event.eventTime - multiTouchStartMs
                    if (multiTouchStartMs != 0L && dur in 1..500) {
                        onThreeFingerTap?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (multiTouchMaxPointers >= 3) {
                    val dur = event.eventTime - multiTouchStartMs
                    if (multiTouchStartMs != 0L && dur in 1..500) {
                        onThreeFingerTap?.invoke()
                    }
                }
                // A single-pointer tap that did not move is also a click, so
                // accessibility services and D-pad activation get an action.
                if (event.actionMasked == MotionEvent.ACTION_UP && multiTouchMaxPointers <= 1) {
                    val slop = touchSlopPx
                    if (Math.abs(event.x - tapDownX) <= slop && Math.abs(event.y - tapDownY) <= slop) {
                        val now = event.eventTime
                        if (now - lastTapUpMs in 1..350 && Math.abs(event.x - lastTapUpX) <= slop * 2 && Math.abs(event.y - lastTapUpY) <= slop * 2) {
                            // Double click / double tap: centering / fit page
                            resetTransform()
                            lastTapUpMs = 0L
                        } else {
                            lastTapUpMs = now
                            lastTapUpX = event.x
                            lastTapUpY = event.y
                            performClick()
                        }
                    }
                }
                multiTouchStartMs = 0L
                multiTouchMaxPointers = 0
            }
        }

        // Multi-touch gestures (2-finger pinch zoom and pan):
        // Always allowed in drawing mode, stylus-only mode, and reading mode!
        // Stylus mode: the pen writes, the hand only presses buttons. A
        // finger must therefore not draw, pinch or pan the canvas.
        val pointerToolType = event.getToolType(0)
        if (stylusOnly && pointerToolType != MotionEvent.TOOL_TYPE_STYLUS &&
            pointerToolType != MotionEvent.TOOL_TYPE_ERASER
        ) {
            return false
        }
        if (pointerToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            pointerToolType == MotionEvent.TOOL_TYPE_ERASER
        ) {
            onStylusDetected?.invoke()
        }

        if (event.pointerCount >= 2) {
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                // Pinch-zoom / two-finger pan: cancel any drawing
                // interaction started by the first finger alone so the
                // later single-pointer UP does not commit a stray stroke.
                cancelDrawingInteractionForMultiTouch()
            }
            handleCanvasGesture(event)
            return true
        }

        // Check if drawing / single-pointer editing is blocked (reading mode or non-stylus finger)
        val isDrawingBlocked = readingMode || (stylusOnly && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS)
        if (isDrawingBlocked) {
            // Return true on ACTION_DOWN and moves so multi-touch pinch/pan or tap up is delivered
            return true
        }

        // First touch of a gesture: let the screen close its slide-over menus
        // so the user does not need a separate "close" tap after configuring.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onCanvasTouchStart?.invoke()
            multiTouchCancelsStroke = false
        }

        val pts = floatArrayOf(event.x, event.y)
        pageInverseMatrix.mapPoints(pts)
        val x = pts[0]
        val y = pts[1]

        // F-10: PDF text selection intercepts single-pointer gestures while
        // the Text tool is active on a vector page (two-finger zoom/pan above
        // still works). Tap = caret hit, drag = extend from the anchor.
        if (pdfTextSelectionMode) {
            val source = pdfTextSource
            if (source != null) {
                val tolerance = PdfTextSelectionMath.tolerancePageUnits(
                    PDF_TEXT_HIT_TOLERANCE_PX, scale
                )
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val index = try {
                            source.charIndexAt(x, y, tolerance)
                        } catch (e: Exception) {
                            -1
                        }
                        selAnchor = index
                        selFocus = index
                        notifyPdfSelection()
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (selAnchor >= 0) {
                            val index = try {
                                source.charIndexAt(x, y, tolerance)
                            } catch (e: Exception) {
                                -1
                            }
                            if (index >= 0 && index != selFocus) {
                                selFocus = index
                                notifyPdfSelection()
                                invalidate()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        notifyPdfSelection()
                        invalidate()
                        return true
                    }
                }
            }
        }

        // Image layer interaction (tap/drag/pinch)
        if (handleImageLayerTouchEvent(event, x, y)) return true

        // Geometric shape tools
        if (currentTool in listOf(CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_CIRCLE, CanvasTool.SHAPE_LINE)) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    saveUndoState()
                    startTouchX = x
                    startTouchY = y
                    currentTouchX = x
                    currentTouchY = y
                    isDrawingShape = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentTouchX = x
                    currentTouchY = y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (multiTouchCancelsStroke) {
                        // Gesture became a canvas transform: the shape was
                        // cancelled, commit nothing to the bitmap.
                        multiTouchCancelsStroke = false
                        isDrawingShape = false
                        invalidate()
                        return true
                    }
                    currentTouchX = x
                    currentTouchY = y
                    isDrawingShape = false
                    // Commit shape to bitmap
                    val c = bitmapCanvas
                    if (c != null) {
                        when (currentTool) {
                            CanvasTool.SHAPE_RECT -> {
                                val rect = RectF(
                                    minOf(startTouchX, currentTouchX),
                                    minOf(startTouchY, currentTouchY),
                                    maxOf(startTouchX, currentTouchX),
                                    maxOf(startTouchY, currentTouchY)
                                )
                                c.drawRect(rect, strokePaint)
                            }
                            CanvasTool.SHAPE_CIRCLE -> {
                                val radius = Math.hypot(
                                    (currentTouchX - startTouchX).toDouble(),
                                    (currentTouchY - startTouchY).toDouble()
                                ).toFloat() / 2f
                                val cx = (startTouchX + currentTouchX) / 2f
                                val cy = (startTouchY + currentTouchY) / 2f
                                c.drawCircle(cx, cy, radius, strokePaint)
                            }
                            CanvasTool.SHAPE_LINE -> {
                                c.drawLine(startTouchX, startTouchY, currentTouchX, currentTouchY, strokePaint)
                            }
                            else -> {}
                        }
                    }
                    invalidate()
                    onStrokeFinishedListener?.invoke()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        // Laser pointer: the pen shows a dot instead of writing.
        if (currentTool == CanvasTool.LASER_POINTER) {
            handleLaserTouch(event)
            return true
        }

        // Lasso selection & transform tool
        if (currentTool == CanvasTool.LASSO) {
            val pillTop = if (lassoBounds.top < 70f) lassoBounds.bottom + 15f else lassoBounds.top - 65f
            val pillBottom = if (lassoBounds.top < 70f) lassoBounds.bottom + 65f else lassoBounds.top - 15f
            val pillDoneRect = RectF(lassoBounds.centerX() - 120f, pillTop, lassoBounds.centerX() - 10f, pillBottom)
            val pillDeleteRect = RectF(lassoBounds.centerX() + 10f, pillTop, lassoBounds.centerX() + 120f, pillBottom)
            val resizeHandleX = lassoBounds.right
            val resizeHandleY = lassoBounds.bottom

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (lassoState == LassoState.SELECTED) {
                        // Check if tapped on Done pill
                        if (pillDoneRect.contains(x, y)) {
                            commitLassoSelection()
                            return true
                        }
                        // Check if tapped on Delete/Cut pill
                        if (pillDeleteRect.contains(x, y)) {
                            deleteLassoSelection()
                            return true
                        }
                        // Check if tapped on resize handle (bottom-right)
                        val distToHandle = Math.hypot((x - resizeHandleX).toDouble(), (y - resizeHandleY).toDouble()).toFloat()
                        if (distToHandle <= handleRadius * 2.5f) {
                            lassoState = LassoState.RESIZING
                            dragStartX = x
                            dragStartY = y
                            initialLassoBounds.set(lassoBounds)
                            return true
                        }
                        // Check if tapped inside selection bounding box (to drag/move)
                        if (lassoBounds.contains(x, y)) {
                            lassoState = LassoState.DRAGGING
                            dragStartX = x
                            dragStartY = y
                            initialLassoBounds.set(lassoBounds)
                            return true
                        }
                        // Tapped outside -> commit current selection and start new selection
                        commitLassoSelection()
                    }

                    // Start new lasso selection loop
                    saveUndoState()
                    lassoPath.reset()
                    lassoPath.moveTo(x, y)
                    dragStartX = x
                    dragStartY = y
                    lassoState = LassoState.SELECTING
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    when (lassoState) {
                        LassoState.SELECTING -> {
                            lassoPath.lineTo(x, y)
                            invalidate()
                            return true
                        }
                        LassoState.DRAGGING -> {
                            val dx = x - dragStartX
                            val dy = y - dragStartY
                            lassoBounds.set(
                                initialLassoBounds.left + dx,
                                initialLassoBounds.top + dy,
                                initialLassoBounds.right + dx,
                                initialLassoBounds.bottom + dy
                            )
                            invalidate()
                            return true
                        }
                        LassoState.RESIZING -> {
                            val newWidth = (x - lassoBounds.left).coerceAtLeast(40f)
                            val newHeight = (y - lassoBounds.top).coerceAtLeast(40f)
                            lassoBounds.right = lassoBounds.left + newWidth
                            lassoBounds.bottom = lassoBounds.top + newHeight
                            invalidate()
                            return true
                        }
                        else -> {}
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when (lassoState) {
                        LassoState.SELECTING -> {
                            lassoPath.close()
                            val bounds = RectF()
                            lassoPath.computeBounds(bounds, true)

                            val bmp = backingBitmap
                            if (bounds.width() > 15f && bounds.height() > 15f && bmp != null && !bmp.isRecycled) {
                                val cropL = bounds.left.toInt().coerceIn(0, bmp.width - 1)
                                val cropT = bounds.top.toInt().coerceIn(0, bmp.height - 1)
                                val cropR = bounds.right.toInt().coerceIn(cropL + 1, bmp.width)
                                val cropB = bounds.bottom.toInt().coerceIn(cropT + 1, bmp.height)
                                val cropW = cropR - cropL
                                val cropH = cropB - cropT

                                if (cropW > 0 && cropH > 0) {
                                    val extracted = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                                    val extCanvas = Canvas(extracted)

                                    val localPath = Path(lassoPath)
                                    localPath.offset(-cropL.toFloat(), -cropT.toFloat())
                                    extCanvas.clipPath(localPath)
                                    extCanvas.drawBitmap(bmp, -cropL.toFloat(), -cropT.toFloat(), null)

                                    val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                    }
                                    bitmapCanvas?.drawPath(lassoPath, clearPaint)

                                    selectedBitmap = extracted
                                    lassoBounds.set(cropL.toFloat(), cropT.toFloat(), cropR.toFloat(), cropB.toFloat())
                                    lassoState = LassoState.SELECTED
                                } else {
                                    lassoState = LassoState.IDLE
                                }
                            } else {
                                lassoState = LassoState.IDLE
                            }
                            invalidate()
                            return true
                        }
                        LassoState.DRAGGING, LassoState.RESIZING -> {
                            lassoState = LassoState.SELECTED
                            invalidate()
                            return true
                        }
                        else -> {}
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        // Standard stroke drawing
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                saveUndoState()
                currentPath.reset()
                currentPath.moveTo(x, y)
                lastTouchX = x
                lastTouchY = y
                sampledPoints.clear()
                sampledPoints.add(x to y)
                currentStrokePoints.clear()
                lastSmoothedWidth = -1f
                if (currentTool == CanvasTool.FOUNTAIN_PEN) {
                    val initialWidth = calculateFountainPenWidth(event.pressure)
                    currentStrokePoints.add(StrokePoint(x, y, initialWidth))
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTouchCancelsStroke) {
                    // Stroke was cancelled by a second pointer: keep the
                    // in-progress path empty until the finger is lifted.
                    invalidate()
                    return true
                }
                if (currentTool == CanvasTool.FOUNTAIN_PEN) {
                    for (h in 0 until event.historySize) {
                        val hPts = floatArrayOf(event.getHistoricalX(h), event.getHistoricalY(h))
                        pageInverseMatrix.mapPoints(hPts)
                        val hx = hPts[0]
                        val hy = hPts[1]
                        val last = currentStrokePoints.lastOrNull()
                        val distSq = if (last != null) {
                            (hx - last.x) * (hx - last.x) + (hy - last.y) * (hy - last.y)
                        } else 999f
                        if (distSq >= 4f) {
                            val hw = calculateFountainPenWidth(event.getHistoricalPressure(h))
                            currentStrokePoints.add(StrokePoint(hx, hy, hw))
                            sampledPoints.add(hx to hy)
                        }
                    }
                    val last = currentStrokePoints.lastOrNull()
                    val distSq = if (last != null) {
                        (x - last.x) * (x - last.x) + (y - last.y) * (y - last.y)
                    } else 999f
                    if (distSq >= 4f) {
                        val w = calculateFountainPenWidth(event.pressure)
                        currentStrokePoints.add(StrokePoint(x, y, w))
                        sampledPoints.add(x to y)
                    }
                    invalidate()
                    return true
                }
                val dx = Math.abs(x - lastTouchX)
                val dy = Math.abs(y - lastTouchY)
                if (dx >= 2 || dy >= 2) {
                    currentPath.quadTo(lastTouchX, lastTouchY, (x + lastTouchX) / 2, (y + lastTouchY) / 2)
                    lastTouchX = x
                    lastTouchY = y
                    sampledPoints.add(x to y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (multiTouchCancelsStroke) {
                    // Gesture became a canvas transform: the stroke was
                    // cancelled, commit nothing to the bitmap.
                    multiTouchCancelsStroke = false
                    currentPath.reset()
                    sampledPoints.clear()
                    currentStrokePoints.clear()
                    lastSmoothedWidth = -1f
                    invalidate()
                    return true
                }
                val fountain = currentTool == CanvasTool.FOUNTAIN_PEN && currentStrokePoints.isNotEmpty()
                if (fountain) {
                    val last = currentStrokePoints.last()
                    val distSq = (x - last.x) * (x - last.x) + (y - last.y) * (y - last.y)
                    if (distSq > 1f) {
                        val finalWidth = if (lastSmoothedWidth > 0f) lastSmoothedWidth * 0.85f else strokeWidthPx
                        currentStrokePoints.add(StrokePoint(x, y, finalWidth))
                        sampledPoints.add(x to y)
                    }
                } else {
                    currentPath.lineTo(x, y)
                    sampledPoints.add(x to y)
                }

                // Where the marks of this stroke belong:
                //  - on a layer  -> they must hide with that layer, so they are
                //    rasterised into their own transparent crop;
                //  - off the paper -> the page ink bitmap does not reach there,
                //    so they become a page-level crop as well;
                //  - otherwise   -> straight into the page ink, as before.
                val marks = strokeInkBounds()
                val onLayer = currentLayerOwnsInk()
                val offPaper = marks != null && !paperRect().contains(marks)
                val drawStroke: (android.graphics.Canvas) -> Unit = { c ->
                    if (fountain) {
                        if (autoShapeEnabled) {
                            val shape = autoShape(sampledPoints)
                            if (shape != null) {
                                strokePaint.strokeWidth = strokeWidthPx
                                c.drawPath(shape, strokePaint)
                            } else {
                                drawFountainPenStroke(c, currentStrokePoints, strokePaint)
                            }
                        } else {
                            drawFountainPenStroke(c, currentStrokePoints, strokePaint)
                        }
                    } else {
                        if (autoShapeEnabled) {
                            val shape = autoShape(sampledPoints)
                            if (shape != null) {
                                c.drawPath(shape, strokePaint)
                            } else {
                                c.drawPath(currentPath, strokePaint)
                            }
                        } else {
                            c.drawPath(currentPath, strokePaint)
                        }
                    }
                }
                if ((onLayer || offPaper) && marks != null) {
                    val crop = newInkCropCanvas(marks)
                    if (crop != null) {
                        val (bmp, c) = crop
                        c.save()
                        c.translate(-marks.left.toFloat(), -marks.top.toFloat())
                        drawStroke(c)
                        c.restore()
                        val ink = InkCrop(bmp, marks.left.toFloat(), marks.top.toFloat())
                        if (onLayer) {
                            imageLayers[selectedImageIndex].ink.add(ink)
                            layersDirty = true
                        } else {
                            beyondPageInk.add(ink)
                        }
                    } else {
                        // No room for a crop: fall back to the page ink.
                        bitmapCanvas?.let { drawStroke(it) }
                    }
                } else {
                    bitmapCanvas?.let { drawStroke(it) }
                }
                currentStrokePoints.clear()
                currentPath.reset()
                sampledPoints.clear()
                lastSmoothedWidth = -1f
                invalidate()
                onStrokeFinishedListener?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** The paper rectangle in page coordinates (the ink bitmap covers exactly it). */
    private fun paperRect(): Rect {
        val bmp = backingBitmap
        val w = bmp?.width?.takeIf { it > 0 } ?: 2160
        val h = bmp?.height?.takeIf { it > 0 } ?: 3056
        return Rect(0, 0, w, h)
    }

    /**
     * Bounding box of the stroke in progress, in page coordinates, padded by
     * the nib so round caps and the wide highlighter stay inside the box.
     */
    private fun strokeInkBounds(): Rect? {
        val fountain = currentTool == CanvasTool.FOUNTAIN_PEN && currentStrokePoints.isNotEmpty()
        val pts: List<Pair<Float, Float>> =
            if (fountain) currentStrokePoints.map { it.x to it.y } else sampledPoints
        if (pts.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var widest = strokeWidthPx
        for ((px, py) in pts) {
            minX = min(minX, px); maxX = max(maxX, px)
            minY = min(minY, py); maxY = max(maxY, py)
        }
        if (fountain) for (p in currentStrokePoints) widest = max(widest, p.width)
        val pad = when (currentTool) {
            CanvasTool.HIGHLIGHTER -> widest * 1.7f + 6f
            CanvasTool.ERASER -> eraserWidthPx / 2f + 6f
            else -> widest / 2f + 6f
        }
        val left = floor(minX - pad).toInt()
        val top = floor(minY - pad).toInt()
        val right = ceil(maxX + pad).toInt()
        val bottom = ceil(maxY + pad).toInt()
        return Rect(left, top, max(right, left + 1), max(bottom, top + 1))
    }

    /** A transparent bitmap holding just the given rectangle of the page. */
    private fun newInkCropCanvas(rect: Rect): Pair<android.graphics.Bitmap, android.graphics.Canvas>? {
        val w = rect.width()
        val h = rect.height()
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null
        val bmp = try {
            android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        }
        return bmp to android.graphics.Canvas(bmp)
    }

    /** True when the layer panel's current layer owns the ink of a new stroke. */
    private fun currentLayerOwnsInk(): Boolean =
        selectedImageIndex in imageLayers.indices && currentTool != CanvasTool.ERASER

    /**
     * Moves the laser pointer: a red dot with a short fading tail. Nothing is
     * written to the page.
     */
    private fun handleLaserTouch(event: MotionEvent) {
        val pts = floatArrayOf(event.x, event.y)
        pageInverseMatrix.mapPoints(pts)
        val now = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                laserTrail.clear()
                laserTrail.add(LaserPoint(pts[0], pts[1], now))
                onCanvasTouchStart?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                laserTrail.add(LaserPoint(pts[0], pts[1], now))
                // Keep the tail short.
                while (laserTrail.size > 48) laserTrail.removeAt(0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // The dot stays for a moment and fades out on its own.
            }
        }
        invalidate()
    }

    /** Draws the laser dot and its tail (page coordinates). */
    private fun drawLaserPointer(canvas: Canvas) {
        if (laserTrail.isEmpty()) return
        val now = System.currentTimeMillis()
        val points = laserTrail.filter { now - it.time < LASER_TRAIL_MS }
        if (points.isEmpty()) {
            laserTrail.clear()
            return
        }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val age = (now - b.time).toFloat().coerceAtMost(LASER_TRAIL_MS.toFloat())
            laserGlowPaint.alpha = (90f * (1f - age / LASER_TRAIL_MS)).toInt().coerceIn(0, 255)
            canvas.drawLine(a.x, a.y, b.x, b.y, laserGlowPaint)
        }
        val head = points.last()
        laserGlowPaint.alpha = 70
        canvas.drawCircle(head.x, head.y, LASER_DOT_RADIUS * 2.4f, laserGlowPaint)
        laserDotPaint.alpha = 255
        canvas.drawCircle(head.x, head.y, LASER_DOT_RADIUS, laserDotPaint)
        // Keep the fade running.
        postInvalidateOnAnimation()
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
        // straight line
        if (points.size <= 8 || dist > (w + h) * 0.8f && (aspect > 4f || aspect < 0.25f)) {
            // extreme aspect -> line
            if (aspect > 4f || aspect < 0.25f) {
                val p = Path()
                p.moveTo(first.first, first.second)
                p.lineTo(last.first, last.second)
                return p
            }
        }
        // closed-ish?
        val closed = dist < Math.max(w, h) * 0.25f
        if (closed) {
            // circle if roughly square
            if (aspect in 0.7f..1.3f) {
                val cx = (minX + maxX) / 2f
                val cy = (minY + maxY) / 2f
                val r = (w + h) / 4f
                val p = Path()
                p.addCircle(cx, cy, r, Path.Direction.CW)
                return p
            }
            // rectangle (4 corners heuristic): just bbox
            val p = Path()
            p.addRect(RectF(minX, minY, maxX, maxY), Path.Direction.CW)
            return p
        }
        return null
    }

    private fun saveUndoState() {
        backingBitmap?.let { bmp ->
            if (undoStack.size >= maxUndoSnapshots) {
                val oldest = undoStack.removeFirst()
                if (!oldest.bitmap.isRecycled) oldest.bitmap.recycle()
            }
            val layerSnapshots = imageLayers.map {
                LayerStateSnapshot(it.bitmap, it.x, it.y, it.scale, it.rotation, it.opacity, it.cropRect, it.visible, it.name, it.ink.toList())
            }
            undoStack.addLast(
                UndoSnapshot(
                    bmp.copy(Bitmap.Config.ARGB_8888, true),
                    layerSnapshots,
                    beyondPageInk.toList()
                )
            )
            clearRedoStack()
            notifyUndoRedo()
        }
    }

    fun undo() {
        commitLassoSelection()
        if (undoStack.isNotEmpty()) {
            backingBitmap?.let { current ->
                if (redoStack.size >= maxUndoSnapshots) {
                    val oldest = redoStack.removeFirst()
                    if (!oldest.bitmap.isRecycled) oldest.bitmap.recycle()
                }
                val currentLayers = imageLayers.map {
                    LayerStateSnapshot(it.bitmap, it.x, it.y, it.scale, it.rotation, it.opacity, it.cropRect, it.visible, it.name, it.ink.toList())
                }
                redoStack.addLast(
                    UndoSnapshot(current.copy(Bitmap.Config.ARGB_8888, true), currentLayers, beyondPageInk.toList())
                )
            }
            val previous = undoStack.removeLast()
            backingBitmap = previous.bitmap
            bitmapCanvas = if (previous.bitmap != null && !previous.bitmap.isRecycled) Canvas(previous.bitmap) else null
            imageLayers.clear()
            imageLayers.addAll(previous.layers.map {
                ImageLayer(it.bitmap, it.x, it.y, it.scale, it.rotation, it.opacity, it.cropRect, it.visible, it.name, it.ink.toMutableList())
            })
            beyondPageInk.clear()
            beyondPageInk.addAll(previous.beyondInk)
            selectedImageIndex = -1
            layersDirty = true
            notifyUndoRedo()
            invalidate()
            onStrokeFinishedListener?.invoke()
        }
    }

    fun redo() {
        commitLassoSelection()
        if (redoStack.isNotEmpty()) {
            backingBitmap?.let { current ->
                if (undoStack.size >= maxUndoSnapshots) {
                    val oldest = undoStack.removeFirst()
                    if (!oldest.bitmap.isRecycled) oldest.bitmap.recycle()
                }
                val currentLayers = imageLayers.map {
                    LayerStateSnapshot(it.bitmap, it.x, it.y, it.scale, it.rotation, it.opacity, it.cropRect, it.visible, it.name, it.ink.toList())
                }
                undoStack.addLast(
                    UndoSnapshot(current.copy(Bitmap.Config.ARGB_8888, true), currentLayers, beyondPageInk.toList())
                )
            }
            val next = redoStack.removeLast()
            backingBitmap = next.bitmap
            bitmapCanvas = if (next.bitmap != null && !next.bitmap.isRecycled) Canvas(next.bitmap) else null
            imageLayers.clear()
            imageLayers.addAll(next.layers.map {
                ImageLayer(it.bitmap, it.x, it.y, it.scale, it.rotation, it.opacity, it.cropRect, it.visible, it.name, it.ink.toMutableList())
            })
            beyondPageInk.clear()
            beyondPageInk.addAll(next.beyondInk)
            selectedImageIndex = -1
            layersDirty = true
            notifyUndoRedo()
            invalidate()
            onStrokeFinishedListener?.invoke()
        }
    }

    fun clearCanvas() {
        commitLassoSelection()
        saveUndoState()
        bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    private fun clearRedoStack() {
        while (redoStack.isNotEmpty()) {
            val snapshot = redoStack.removeLast()
            if (!snapshot.bitmap.isRecycled) snapshot.bitmap.recycle()
        }
    }

    private fun notifyUndoRedo() {
        onUndoRedoListener?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    // Set when a drawing interaction that began with a single pointer
    // (stroke / shape / lasso-SELECTING) is cancelled because a second
    // pointer arrives (pinch-zoom / two-finger pan). The interaction start
    // pushed an undo snapshot but never wrote pixels to the backing bitmap,
    // so that snapshot is popped and the later single-pointer UP/CANCEL
    // commits nothing.
    private var multiTouchCancelsStroke = false

    private fun isDrawingInteractionInProgress(): Boolean {
        return !currentPath.isEmpty ||
            sampledPoints.isNotEmpty() ||
            currentStrokePoints.isNotEmpty() ||
            isDrawingShape ||
            lassoState == LassoState.SELECTING
    }

    private fun cancelDrawingInteractionForMultiTouch() {
        if (!isDrawingInteractionInProgress()) return
        multiTouchCancelsStroke = true
        currentPath.reset()
        sampledPoints.clear()
        currentStrokePoints.clear()
        lastSmoothedWidth = -1f
        isDrawingShape = false
        if (lassoState == LassoState.SELECTING) {
            lassoPath.reset()
            lassoState = LassoState.IDLE
        }
        // Drop the undo entry the interaction start pushed (nothing has been
        // committed to the bitmap yet), so undo stays enabled only for
        // real strokes.
        if (undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeLast()
            if (!snapshot.bitmap.isRecycled) snapshot.bitmap.recycle()
        }
        notifyUndoRedo()
        invalidate()
    }

    private fun drawFountainPenStroke(canvas: Canvas, points: List<StrokePoint>, basePaint: Paint) {
        if (points.isEmpty()) return
        fountainPenPaint.color = basePaint.color
        fountainPenPaint.alpha = basePaint.alpha
        fountainPenPaint.xfermode = basePaint.xfermode

        if (points.size == 1) {
            val p0 = points[0]
            fountainPenPaint.strokeWidth = p0.width
            canvas.drawPoint(p0.x, p0.y, fountainPenPaint)
            return
        }

        if (points.size == 2) {
            val p0 = points[0]
            val p1 = points[1]
            drawTaperedLine(canvas, p0.x, p0.y, p0.width, p1.x, p1.y, p1.width, fountainPenPaint)
            return
        }

        // Draw from first point to first midpoint
        val p0 = points[0]
        val p1 = points[1]
        val m0X = (p0.x + p1.x) / 2f
        val m0Y = (p0.y + p1.y) / 2f
        val m0W = (p0.width + p1.width) / 2f
        drawTaperedLine(canvas, p0.x, p0.y, p0.width, m0X, m0Y, m0W, fountainPenPaint)

        // Draw intermediate quadratic beziers between midpoints
        var prevMX = m0X
        var prevMY = m0Y
        var prevMW = m0W

        for (i in 1 until points.size - 1) {
            val curr = points[i]
            val next = points[i + 1]
            val nextMX = (curr.x + next.x) / 2f
            val nextMY = (curr.y + next.y) / 2f
            val nextMW = (curr.width + next.width) / 2f
            drawTaperedQuad(
                canvas,
                prevMX, prevMY, prevMW,
                curr.x, curr.y,
                nextMX, nextMY, nextMW,
                fountainPenPaint
            )
            prevMX = nextMX
            prevMY = nextMY
            prevMW = nextMW
        }

        // Draw from last midpoint to last point
        val pLast = points.last()
        drawTaperedLine(canvas, prevMX, prevMY, prevMW, pLast.x, pLast.y, pLast.width, fountainPenPaint)
    }

    private fun drawTaperedLine(
        canvas: Canvas,
        x0: Float, y0: Float, w0: Float,
        x1: Float, y1: Float, w1: Float,
        paint: Paint
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < 0.5f) {
            paint.strokeWidth = (w0 + w1) / 2f
            canvas.drawPoint(x0, y0, paint)
            return
        }
        val steps = maxOf(1, (dist / 3f).toInt())
        var prevX = x0
        var prevY = y0
        var prevW = w0
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val curX = x0 + dx * t
            val curY = y0 + dy * t
            val curW = w0 + (w1 - w0) * t
            paint.strokeWidth = (prevW + curW) / 2f
            canvas.drawLine(prevX, prevY, curX, curY, paint)
            prevX = curX
            prevY = curY
            prevW = curW
        }
    }

    private fun drawTaperedQuad(
        canvas: Canvas,
        x0: Float, y0: Float, w0: Float,
        cx: Float, cy: Float,
        x1: Float, y1: Float, w1: Float,
        paint: Paint
    ) {
        val chordDist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
        val controlDist = Math.hypot((cx - x0).toDouble(), (cy - y0).toDouble()).toFloat() +
                          Math.hypot((x1 - cx).toDouble(), (y1 - cy).toDouble()).toFloat()
        val approxLen = (chordDist + controlDist) / 2f
        if (approxLen < 0.5f) {
            paint.strokeWidth = (w0 + w1) / 2f
            canvas.drawPoint(x0, y0, paint)
            return
        }
        val steps = maxOf(2, (approxLen / 3f).toInt().coerceAtMost(32))
        var prevX = x0
        var prevY = y0
        var prevW = w0
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val invT = 1f - t
            val curX = invT * invT * x0 + 2f * invT * t * cx + t * t * x1
            val curY = invT * invT * y0 + 2f * invT * t * cy + t * t * y1
            val curW = invT * w0 + t * w1
            paint.strokeWidth = (prevW + curW) / 2f
            canvas.drawLine(prevX, prevY, curX, curY, paint)
            prevX = curX
            prevY = curY
            prevW = curW
        }
    }

    /**
     * Multi-finger tap hook: invoked on a quick 3+ finger tap (≤500 ms,
     * max pointers ≥ 3) before normal gesture handling. CanvasScreen uses
     * it for 3-finger-tap paste. Returning a non-null listener does not
     * consume the gesture — CanvasScreen may still show a toast.
     */
    var onThreeFingerTap: (() -> Unit)? = null
    private var multiTouchStartMs = 0L
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var lastTapUpMs = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f
    private val touchSlopPx: Float =
        android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var multiTouchMaxPointers = 0

    /**
     * Install a raw-Android drag listener on this view.
     *
     * This is the reliable path for split-screen drops from the browser,
     * Files and Photos, where the drag is an OS-level [android.view.DragEvent]
     * (ACTION_DRAG_STARTED / ACTION_DROP / …). Compose's
     * [androidx.compose.foundation.draganddrop.dragAndDropTarget] gives the
     * same session through [androidx.compose.ui.draganddrop.DragAndDropEvent],
     * but a View-level listener is simpler (and works even under an
     * AndroidView-interop surface) and lets the hotspot share the exact
     * ClipData.Item → bitmap / text mapping the clipboard paste uses.
     *
     * Call from CanvasScreen's AndroidView factory (update=false so it is
     * installed once). [onClipDropped] receives the DragEvent's ClipData;
     * [isAccepted] decides whether to accept the drag at STARTED
     * (default: ClipData non-null + has image/text content).
     */
    fun setDragDropListener(
        onClipDropped: (clip: android.content.ClipData) -> Unit,
        isAccepted: (clip: android.content.ClipData) -> Boolean = { clip ->
            clip.itemCount > 0 && (0 until clip.itemCount).any {
                clip.getItemAt(it).uri != null ||
                    !clip.getItemAt(it).text.isNullOrBlank() ||
                    clip.getItemAt(it).htmlText != null
            }
        }
    ) {
        setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED ->
                    event.clipData?.let { isAccepted(it) } ?: false
                android.view.DragEvent.ACTION_DRAG_ENDED -> true
                android.view.DragEvent.ACTION_DROP -> {
                    val clip = event.clipData
                    clip?.let { onClipDropped(it) }
                    true
                }
                else -> true // ENTERED / LOCATION / EXITED: accept pass-through
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // DEBUG-only: feed the adb gesture injector the newest canvas instance
        // (see util/DebugGestureInjector.kt; inert in release builds).
        DebugGestureInjector.register(this)
    }

    /**
     * A hidden window (screen locked, app behind the keyguard) must not spend
     * a PDF render: the vector pass can allocate the whole pixel budget and
     * render for seconds. The debounced task is cancelled while invisible and
     * re-armed when the window comes back.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            pdfRenderHandler.removeCallbacks(pdfRenderTask)
        } else if (pdfRenderer != null) {
            schedulePdfBackgroundRefresh()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pdfRenderHandler.removeCallbacks(pdfRenderTask)
        DebugGestureInjector.unregister(this)
        setOnDragListener(null)
        clearRedoStack()
        while (undoStack.isNotEmpty()) {
            val snapshot = undoStack.removeLast()
            if (!snapshot.bitmap.isRecycled) snapshot.bitmap.recycle()
        }
    }

    companion object {
        /** Red dot of the laser pointer. */
        private const val LASER_COLOR = 0xFFFF3B30.toInt()

        /** How long the laser tail stays visible, in milliseconds. */
        private const val LASER_TRAIL_MS = 700L

        /** Radius of the laser dot, in canvas units. */
        private const val LASER_DOT_RADIUS = 7f

        /** Quiet period after a zoom change before the vector re-render fires. */
        private const val PDF_RENDER_DEBOUNCE_MS = 180L

        /** Screen-pixel tolerance for hitting a glyph with the Text tool. */
        private const val PDF_TEXT_HIT_TOLERANCE_PX = 28f

        /**
         * One shared render thread: pdfium work for the visible page is
         * serialized and never runs on the UI or Compose thread.
         */
        private val pdfRenderExecutor: ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "pdf-background-render").apply { isDaemon = true }
            }
    }
}

