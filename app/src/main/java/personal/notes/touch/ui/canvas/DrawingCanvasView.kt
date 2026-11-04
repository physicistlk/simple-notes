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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import personal.notes.touch.util.BitmapUtils
import java.util.ArrayDeque

enum class CanvasTool {
    FOUNTAIN_PEN,
    PEN,
    HIGHLIGHTER,
    ERASER,
    SHAPE_RECT,
    SHAPE_CIRCLE,
    SHAPE_LINE,
    LASSO
}

enum class PaperPattern {
    BLANK,
    GRID,
    LINES,
    DOTTED
}

data class ImageLayer(
    val bitmap: Bitmap,
    var x: Float,
    var y: Float,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var opacity: Float = 1f,
    var cropRect: Rect? = null,
    var visible: Boolean = true,
    var name: String = "Layer"
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
        set(value) { field = value.coerceIn(0.25f, 4f); updateViewMatrix() }
    var autoCenter: Boolean = true
    val viewMatrix: Matrix = Matrix()
    val inverseMatrix: Matrix = Matrix()

    // gesture tracking
    private var gestureActive = false
    private var gestureStartPanX = 0f
    private var gestureStartPanY = 0f
    private var gestureStartScale = 1f
    private var gestureStartMidX = 0f
    private var gestureStartMidY = 0f
    private var gestureStartDist = 0f

    // Non-destructive background (e.g. imported PDF page)
    var backgroundBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // Overflow layer for drawing outside the PDF page (moves with pan/zoom)
    var overflowBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // Page type
    var beyondPaper: Boolean = true
        set(value) {
            field = value
            // When beyond-paper is enabled, allow panning freely without clamp.
            invalidate()
        }
    var pageType: String = "infinite"

    // Transformable image layers
    val imageLayers: MutableList<ImageLayer> = mutableListOf()
    var selectedImageIndex: Int = -1
        set(value) {
            field = value
            onImageSelectionChanged?.invoke(value)
        }
    var onImageSelectionChanged: ((Int) -> Unit)? = null
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
    var readingMode: Boolean = false
    var pressureSensitivity: Float = 1.0f
    var pressureSizeEnabled: Boolean = true
    var autoShapeEnabled: Boolean = false

    private val sampledPoints = mutableListOf<Pair<Float, Float>>()

    enum class LassoState {
        IDLE,
        SELECTING,
        SELECTED,
        DRAGGING,
        RESIZING
    }

    private var lassoState = LassoState.IDLE

    // Image layer interaction state
    private var imageInteractionMode = 0 // 0 none, 1 move, 2 pinch
    private var imageDragStartX = 0f
    private var imageDragStartY = 0f
    private var imageLayerStartX = 0f
    private var imageLayerStartY = 0f
    private var pinchStartDist = 0f
    private var pinchStartAngle = 0f
    private var pinchStartScale = 1f
    private var pinchStartRotation = 0f
    private val lassoPath = Path()
    private val lassoBounds = RectF()
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

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    // Undo / Redo
    private val maxUndoSnapshots = 20
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()

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
                strokePaint.strokeWidth = strokeWidthPx * 1.1f
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
        }
    }

    private fun updateViewMatrix() {
        viewMatrix.reset()
        viewMatrix.postScale(scale, scale)
        viewMatrix.postTranslate(panX, panY)
        viewMatrix.invert(inverseMatrix)
    }

    fun resetTransform() {
        panX = 0f
        panY = 0f
        scale = 1f
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
                    panX = gestureStartPanX + (midX - gestureStartMidX)
                    panY = gestureStartPanY + (midY - gestureStartMidY)
                    if (gestureStartDist > 0f) {
                        scale = (gestureStartScale * (dist / gestureStartDist)).coerceIn(0.25f, 4f)
                    }
                    // Beyond-paper: allow panning freely; else clamp to keep at least part visible
                    if (!beyondPaper) {
                        val bmpW = backingBitmap?.width?.toFloat() ?: width.toFloat()
                        val bmpH = backingBitmap?.height?.toFloat() ?: height.toFloat()
                        val scaledW = bmpW * scale
                        val scaledH = bmpH * scale
                        val minPanX = -(scaledW - width).coerceAtLeast(0f)
                        val maxPanX = 0f
                        val minPanY = -(scaledH - height).coerceAtLeast(0f)
                        val maxPanY = 0f
                        panX = panX.coerceIn(minPanX, maxPanX)
                        panY = panY.coerceIn(minPanY, maxPanY)
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            }
        }
    }

    private fun initBackingBitmap(width: Int, height: Int) {
        val pageWidth = width.coerceIn(1, 2048)
        val safeHeight = height.coerceIn(1, 2048)
        val targetWidth = if (doublePage) (pageWidth * 2 + 40).coerceIn(1, 4096) else pageWidth
        val bmp = BitmapUtils.createSafeBitmap(targetWidth, safeHeight)
        backingBitmap = bmp
        bitmapCanvas = Canvas(bmp)
        // Initialize overflow layer sized 2x the page area so drawing outside
        // the PDF page has room; stays sticky during pan/zoom (drawn in onDraw
        // under the same viewMatrix; overflow pixels are trimmed on export).
        if (backgroundBitmap != null && overflowBitmap == null) {
            val ovW = (targetWidth * 2).coerceIn(1, 4096)
            val ovH = (safeHeight * 2).coerceIn(1, 4096)
            overflowBitmap = BitmapUtils.createSafeBitmap(ovW, ovH)
        }
        if (autoCenter) centerTransform(targetWidth, safeHeight)
    }

    private fun centerTransform(bitmapWidth: Int, bitmapHeight: Int) {
        panX = (width - bitmapWidth * scale) / 2f
        panY = (height - bitmapHeight * scale) / 2f
    }

    fun loadBitmap(bitmap: Bitmap) {
        val w = if (width > 0) width else bitmap.width
        val h = if (height > 0) height else bitmap.height
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        backingBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
        bitmapCanvas = Canvas(backingBitmap!!)
        undoStack.clear()
        clearRedoStack()
        notifyUndoRedo()
        invalidate()
    }

    fun setPageContent(bitmap: Bitmap?) {
        commitLassoSelection()
        if (bitmap != null) {
            loadBitmap(bitmap)
        } else {
            val w = if (width > 0) width else 1080
            val h = if (height > 0) height else if (pageType == "a4") 1528 else 1920
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

    fun addImageLayer(image: Bitmap): Boolean {
        if (imageLayers.size >= 3) return false
        val w = width.toFloat().coerceAtLeast(400f)
        val h = height.toFloat().coerceAtLeast(400f)
        val maxImgW = w * 0.7f
        val maxImgH = h * 0.7f
        val scale = minOf(maxImgW / image.width, maxImgH / image.height, 1.0f)
        val dstW = image.width * scale
        val dstH = image.height * scale
        val left = (w - dstW) / 2f
        val top = (h - dstH) / 2f
        imageLayers.add(ImageLayer(image, left, top, scale, 0f, 1f, null, true, "Layer ${imageLayers.size + 1}"))
        selectedImageIndex = imageLayers.lastIndex
        invalidate()
        return true
    }

    /**
     * Add a pasted / dropped text block as a rendered text layer.
     * Wraps at [wrapWidthPx] (default ~70% of canvas width) using Android
     * StaticLayout-free manual wrapping: splits on whitespace/newlines,
     * draws each line with a bold paint into a small bitmap, then adds it
     * centered as an image layer (existing transformable layer machinery).
     * Returns true on success.
     */
    fun addTextBlock(text: String, color: Int = Color.BLACK): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val w = width.toFloat().coerceAtLeast(400f)
        val h = height.toFloat().coerceAtLeast(400f)
        val maxLineW = (w * 0.66f).coerceAtLeast(120f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 48f
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
        val lineH = paint.fontMetrics.descent - paint.fontMetrics.ascent + 10f
        val bmpW = lines.maxOfOrNull { paint.measureText(it).toInt() + 24 }?.coerceIn(40, 2048) ?: 200
        val bmpH = (lineH * lines.size + 16f).toInt().coerceIn(40, 2048)
        val bmp = BitmapUtils.createSafeBitmap(bmpW, bmpH)
        val c = Canvas(bmp)
        var y = lineH - paint.fontMetrics.ascent
        for (line in lines) {
            c.drawText(line, 12f, y, paint)
            y += lineH
        }
        return addImageLayer(bmp)
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
                    layers.add(ImageLayer(
                        bitmap = bitmap,
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        visible = obj.optBoolean("visible", true),
                        cropRect = null,
                        name = obj.optString("name", "Layer ${i + 1}")
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
        val name: String
    )

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
                    name = layer.name
                )
            }
            val arr = org.json.JSONArray()
            snapshot.forEachIndexed { index, layer ->
                val fileName = "layer_$index.png"
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
                arr.put(obj)
            }
            infoFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        if (index in imageLayers.indices) {
            imageLayers[index].visible = visible
            invalidate()
        }
    }

    fun layerCount(): Int = imageLayers.size

    fun deleteImageLayer(index: Int) {
        if (index in imageLayers.indices) {
            imageLayers.removeAt(index)
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
        for (layer in imageLayers) {
            if (!layer.visible) continue
            if (layer.bitmap.isRecycled) continue
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
            }
            val w = layer.bitmap.width
            val h = layer.bitmap.height
            val cx = layer.x + w * layer.scale / 2f
            val cy = layer.y + h * layer.scale / 2f
            c.save()
            c.translate(cx, cy)
            c.rotate(layer.rotation)
            c.scale(layer.scale, layer.scale)
            c.drawBitmap(layer.bitmap, -w / 2f, -h / 2f, paint)
            c.restore()
        }
        imageLayers.clear()
        selectedImageIndex = -1
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    fun mergeSelectedLayersToBase() = mergeAllLayersToBase()

    fun setLayerOpacity(index: Int, value: Float) {
        if (index in imageLayers.indices) {
            imageLayers[index].opacity = value.coerceIn(0f, 1f)
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
            if (Math.abs(lx) <= w / 2f && Math.abs(ly) <= h / 2f) return i
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

        canvas.save()
        canvas.concat(viewMatrix)

        // 0. Draw non-destructive background (e.g. imported PDF page) first
        backgroundBitmap?.let { bmp ->
            if (!bmp.isRecycled) canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        // 0b. Draw overflow layer (drawing outside the PDF page; sticky with pan/zoom)
        overflowBitmap?.let { bmp ->
            if (!bmp.isRecycled) canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        // 1. Draw paper background (only when no background bitmap)
        if (backgroundBitmap == null) canvas.drawColor(paperColor)

        // 2. Draw paper pattern
        drawPaperPattern(canvas)

        // 3. Draw committed bitmap
        backingBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        // 3b. Draw transformable image layers
        for (i in imageLayers.indices) {
            val layer = imageLayers[i]
            if (!layer.visible) continue
            if (layer.bitmap.isRecycled) continue
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
            }
            val w = layer.bitmap.width
            val h = layer.bitmap.height
            val cx = layer.x + w * layer.scale / 2f
            val cy = layer.y + h * layer.scale / 2f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(layer.rotation)
            canvas.scale(layer.scale, layer.scale)
            canvas.drawBitmap(layer.bitmap, -w / 2f, -h / 2f, paint)
            canvas.restore()
        }

        // 4. Draw current in-progress stroke or shape preview
        if (isDrawingShape) {
            when (currentTool) {
                CanvasTool.SHAPE_RECT -> {
                    val rect = RectF(
                        minOf(startTouchX, currentTouchX),
                        minOf(startTouchY, currentTouchY),
                        maxOf(startTouchX, currentTouchX),
                        maxOf(startTouchY, currentTouchY)
                    )
                    canvas.drawRect(rect, shapePreviewPaint)
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
        }

        canvas.restore()

        if (doublePage) {
            val bmpW = (backingBitmap?.width ?: width).toFloat()
            val pts = floatArrayOf(bmpW / 2f, 0f)
            viewMatrix.mapPoints(pts)
            val linePaint = Paint().apply {
                color = Color.parseColor("#808080")
                strokeWidth = 2f
                alpha = 80
            }
            canvas.drawLine(pts[0], 0f, pts[0], height.toFloat(), linePaint)
        }

        // Selection handles in view space
        if (selectedImageIndex in imageLayers.indices) {
            val layer = imageLayers[selectedImageIndex]
            if (!layer.bitmap.isRecycled) {
                val w = layer.bitmap.width
                val h = layer.bitmap.height
                val cx = layer.x + w * layer.scale / 2f
                val cy = layer.y + h * layer.scale / 2f
                val pts = floatArrayOf(cx, cy)
                viewMatrix.mapPoints(pts)
                canvas.drawCircle(pts[0], pts[1], handleRadius, handlePaint)
            }
        }
        if (lassoState in listOf(LassoState.SELECTED, LassoState.DRAGGING, LassoState.RESIZING)) {
            val corners = floatArrayOf(lassoBounds.left, lassoBounds.top, lassoBounds.right, lassoBounds.top, lassoBounds.left, lassoBounds.bottom, lassoBounds.right, lassoBounds.bottom)
            viewMatrix.mapPoints(corners)
            for (i in 0 until corners.size step 2) {
                canvas.drawCircle(corners[i], corners[i+1], handleRadius, handlePaint)
                canvas.drawCircle(corners[i], corners[i+1], handleRadius, handleBorderPaint)
            }
        }
    }

    private fun drawPaperPattern(canvas: Canvas) {
        if (backgroundBitmap != null) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val step = backgroundDensity.toFloat().coerceIn(20f, 80f)
        when (backgroundMode) {
            1 -> { // lined
                var y = step
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, patternPaint)
                    y += step
                }
            }
            2 -> { // grid
                var x = step
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, patternPaint)
                    x += step
                }
                var y = step
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, patternPaint)
                    y += step
                }
            }
            3 -> { // dotted
                val dotRadius = 2f
                var x = step
                while (x < w) {
                    var y = step
                    while (y < h) {
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

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = topmostImageLayerIndex(x, y)
                if (idx >= 0) {
                    selectedImageIndex = idx
                    val layer = imageLayers[idx]
                    imageInteractionMode = 1
                    imageDragStartX = x
                    imageDragStartY = y
                    imageLayerStartX = layer.x
                    imageLayerStartY = layer.y
                    invalidate()
                    return true
                }
                // outside layers: if selected exists, deselect? keep selection; normal drawing proceeds
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (selectedImageIndex in imageLayers.indices && event.pointerCount >= 2) {
                    val i0 = 0
                    val i1 = 1
                    val x0 = event.getX(i0); val y0 = event.getY(i0)
                    val x1 = event.getX(i1); val y1 = event.getY(i1)
                    pinchStartDist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                    pinchStartAngle = Math.toDegrees(Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
                    val layer = imageLayers[selectedImageIndex]
                    pinchStartScale = layer.scale
                    pinchStartRotation = layer.rotation
                    imageInteractionMode = 2
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedImageIndex !in imageLayers.indices) return false
                val layer = imageLayers[selectedImageIndex]
                if (imageInteractionMode == 2 && event.pointerCount >= 2) {
                    val x0 = event.getX(0); val y0 = event.getY(0)
                    val x1 = event.getX(1); val y1 = event.getY(1)
                    val dist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
                    val angle = Math.toDegrees(Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
                    if (pinchStartDist > 0f) {
                        val scaleRatio = dist / pinchStartDist
                        layer.scale = (pinchStartScale * scaleRatio).coerceIn(0.1f, 10f)
                        layer.rotation = pinchStartRotation + (angle - pinchStartAngle)
                    }
                    invalidate()
                    return true
                }
                if (imageInteractionMode == 1 && event.pointerCount == 1) {
                    val dx = x - imageDragStartX
                    val dy = y - imageDragStartY
                    layer.x = imageLayerStartX + dx
                    layer.y = imageLayerStartY + dy
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (imageInteractionMode != 0) {
                    imageInteractionMode = if (event.pointerCount <= 1) 0 else imageInteractionMode
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        imageInteractionMode = 0
                        onStrokeFinishedListener?.invoke()
                    }
                    return true
                }
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (readingMode) return false

        // Track multi-finger taps (3-finger paste) before other handling.
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
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
                multiTouchStartMs = 0L
                multiTouchMaxPointers = 0
            }
        }

        // Check stylus-only filter
        if (stylusOnly && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        if (event.pointerCount >= 2) {
            handleCanvasGesture(event)
            return true
        }

        val pts = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(pts)
        val x = pts[0]
        val y = pts[1]

        // Pressure sensitivity modulation for all drawing tools when enabled
        if (pressureSizeEnabled && event.pressure > 0.05f) {
            val calibrated = getCalibratedPressure(event.pressure)
            val dynamicWidth = if (currentTool == CanvasTool.FOUNTAIN_PEN) {
                strokeWidthPx * (0.35f + calibrated * 1.3f)
            } else {
                strokeWidthPx * (0.4f + calibrated * 0.8f)
            }
            strokePaint.strokeWidth = dynamicWidth
        } else {
            strokePaint.strokeWidth = strokeWidthPx
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

        // Lasso selection & transform tool
        if (currentTool == CanvasTool.LASSO) {
            val pillDoneRect = RectF(lassoBounds.centerX() - 120f, lassoBounds.top - 65f, lassoBounds.centerX() - 10f, lassoBounds.top - 15f)
            val pillDeleteRect = RectF(lassoBounds.centerX() + 10f, lassoBounds.top - 65f, lassoBounds.centerX() + 120f, lassoBounds.top - 15f)
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
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
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
                currentPath.lineTo(x, y)
                sampledPoints.add(x to y)
                // Commit current path to backing bitmap
                val c = bitmapCanvas
                if (c != null) {
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
                currentPath.reset()
                sampledPoints.clear()
                invalidate()
                onStrokeFinishedListener?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
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
                if (!oldest.isRecycled) oldest.recycle()
            }
            undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, true))
            clearRedoStack()
            notifyUndoRedo()
        }
    }

    fun undo() {
        commitLassoSelection()
        if (undoStack.isNotEmpty()) {
            backingBitmap?.let { current ->
                redoStack.addLast(current.copy(Bitmap.Config.ARGB_8888, true))
            }
            val previous = undoStack.removeLast()
            backingBitmap = previous.copy(Bitmap.Config.ARGB_8888, true)
            bitmapCanvas = Canvas(backingBitmap!!)
            notifyUndoRedo()
            invalidate()
            onStrokeFinishedListener?.invoke()
        }
    }

    fun redo() {
        commitLassoSelection()
        if (redoStack.isNotEmpty()) {
            backingBitmap?.let { current ->
                undoStack.addLast(current.copy(Bitmap.Config.ARGB_8888, true))
            }
            val next = redoStack.removeLast()
            backingBitmap = next.copy(Bitmap.Config.ARGB_8888, true)
            bitmapCanvas = Canvas(backingBitmap!!)
            notifyUndoRedo()
            invalidate()
            onStrokeFinishedListener?.invoke()
        }
    }

    fun clearCanvas() {
        commitLassoSelection()
        saveUndoState()
        bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        // Clear overflow too (non-destructive PDF background is preserved).
        overflowBitmap?.let { ov ->
            try {
                val c = android.graphics.Canvas(ov)
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            } catch (_: Exception) { /* ignore */ }
        }
        invalidate()
        onStrokeFinishedListener?.invoke()
    }

    private fun clearRedoStack() {
        while (redoStack.isNotEmpty()) {
            val bmp = redoStack.removeLast()
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    private fun notifyUndoRedo() {
        onUndoRedoListener?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    /**
     * Multi-finger tap hook: invoked on a quick 3+ finger tap (≤500 ms,
     * max pointers ≥ 3) before normal gesture handling. CanvasScreen uses
     * it for 3-finger-tap paste. Returning a non-null listener does not
     * consume the gesture — CanvasScreen may still show a toast.
     */
    var onThreeFingerTap: (() -> Unit)? = null
    private var multiTouchStartMs = 0L
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setOnDragListener(null)
        clearRedoStack()
        while (undoStack.isNotEmpty()) {
            val bmp = undoStack.removeLast()
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}

