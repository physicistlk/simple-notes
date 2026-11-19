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
    val autoShapeEnabled: Boolean = false,
    val opacity: Float = 1.0f
) {
    /** Tools that write; lasso/image/text keep opening the page instead. */
    val isWritingTool: Boolean
        get() = when (tool) {
            CanvasTool.FOUNTAIN_PEN, CanvasTool.PEN, CanvasTool.PENCIL,
            CanvasTool.SHAPE_FILL, CanvasTool.HIGHLIGHTER,
            CanvasTool.ERASER, CanvasTool.SHAPE_RECT,
            CanvasTool.SHAPE_CIRCLE, CanvasTool.SHAPE_LINE -> true
            else -> false
        }

    /** The laser pointer shows a dot instead of writing. */
    val isLaserPointer: Boolean
        get() = tool == CanvasTool.LASER_POINTER
}

/** One sampled point of a stroke, in normalised page coordinates. */
class InkPoint(val x: Float, val y: Float, val w: Float, val tilt: Float = 0f)

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

    @Transient var cachedW: Float = -1f
    @Transient var cachedH: Float = -1f
    @Transient var cachedCount: Int = -1
    @Transient var cachedPath1: Path? = null
    @Transient var cachedPath2: Path? = null
    @Transient var cachedPath3: Path? = null
    @Transient var cachedAvgTilt: Float = 0f
    @Transient var boundsCount: Int = -1
    @Transient var minX: Float = 0f
    @Transient var minY: Float = 0f
    @Transient var maxX: Float = 0f
    @Transient var maxY: Float = 0f

    fun ensureBounds() {
        val count = points.size
        if (count == 0 || boundsCount == count) return
        var lx = Float.MAX_VALUE
        var ly = Float.MAX_VALUE
        var rx = -Float.MAX_VALUE
        var by = -Float.MAX_VALUE
        val pad = (baseWidthNorm * 2.5f).coerceAtLeast(0.015f)
        for (i in 0 until count) {
            val p = points[i]
            if (p.x - pad < lx) lx = p.x - pad
            if (p.y - pad < ly) ly = p.y - pad
            if (p.x + pad > rx) rx = p.x + pad
            if (p.y + pad > by) by = p.y + pad
        }
        minX = lx
        minY = ly
        maxX = rx
        maxY = by
        boundsCount = count
    }
}

/**
 * Pixel patch saved before a stroke so it can be undone (and redone).
 *
 * The layer is a grid of tiles, so a patch belongs to **one tile**: it holds
 * the rectangle inside that tile (in the tile's own pixels) and the pixels
 * before and after the wipe.
 *
 * [target] is the store the patch belongs to: a layer can hold more than
 * one store (the tiles of a layer that was merged into it), and the undo has
 * to put the pixels back into the one it took them from.
 */
internal class InkPatch(
    /** The column of the tile the patch belongs to. */
    val tx: Int,
    /** The row of the tile the patch belongs to. */
    val ty: Int,
    /** The left edge of the patch inside the tile. */
    val x: Int,
    /** The top edge of the patch inside the tile. */
    val y: Int,
    /** The width of the patch inside the tile. */
    val w: Int,
    /** The height of the patch inside the tile. */
    val h: Int,
    val before: IntArray,
    var after: IntArray? = null,
    val target: LayerTiles? = null
) {
    val bytes: Long get() = w.toLong() * h.toLong() * 4L * (if (after == null) 1 else 2)
}

/**
 * One committed operation of the ink layer.
 *
 * A [Stroke] is kept as a vector: it is drawn as a path on top of the page
 * image and never touches the session bitmap, so the reader does not have to
 * re-upload the layer after every finished stroke.
 *
 * An [Erase] is the one exception - it clears pixels, so it is rasterised into
 * the pixels of its layer as well (the annotation layer stays the only thing
 * the eraser can touch) and it stays in the list to cut the strokes drawn
 * before it.
 *
 * Every operation knows the [layer] it was drawn on: the writing goes on the
 * current layer of the page, and a stroke stays on its layer until the layer
 * is merged into the base layer.
 */
/** Radius (screen px) of the delete handle on the infinite-canvas lasso selection. */
internal const val LASSO_HANDLE_RADIUS_PX = 20f

internal sealed class InkOp {
    /**
     * The layer the operation belongs to.
     *
     * It is a var because a layer that is merged into the base layer hands
     * its strokes over to it.
     */
    abstract var layer: PageLayer

    /** A finished stroke, with the auto-shape path when one was computed. */
    class Stroke(
        val stroke: ContinuousStroke,
        val shape: Path?,
        override var layer: PageLayer
    ) : InkOp()

    /** An eraser stroke; [patches] holds the pixels before and after the wipe. */
    class Erase(
        val stroke: ContinuousStroke,
        val patches: List<InkPatch>,
        override var layer: PageLayer
    ) : InkOp()
}

/**
 * One undoable change of the ink layer.
 *
 * Undo works on this list and not on pixels: a stroke is undone by taking it
 * out of the committed list again, an eraser by putting back the pixels it
 * cleared.
 */
internal enum class LassoHandle {
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

internal class StrokeTransformSnapshot(
    val op: InkOp.Stroke,
    val beforePoints: List<InkPoint>,
    val afterPoints: List<InkPoint>,
    val beforeShape: Path?,
    val afterShape: Path?
)

internal sealed class InkAction {
    /** A stroke was added to the committed list. */
    class Add(val op: InkOp.Stroke) : InkAction()

    /** A batch of duplicated strokes was added (lasso/selection copy). */
    class AddBatch(val added: List<InkOp.Stroke>) : InkAction()

    /** An eraser was applied; its op holds the pixels before and after. */
    class Erase(val op: InkOp.Erase) : InkAction()

    /** The page was cleared: the patches keep the ink, [removed] the strokes. */
    class Clear(val patches: List<InkPatch>, val removed: List<OpEntry>) : InkAction()

    /** Strokes were moved by ([dx], [dy]) in normalised page units (lasso move). */
    class Move(val ops: List<InkOp.Stroke>, val dx: Float, val dy: Float) : InkAction()

    /** Strokes were transformed (rotate / scale / mirror / move) with before+after snapshots. */
    class Transform(val snapshots: List<StrokeTransformSnapshot>) : InkAction()

    /** A layer was added to the stack of the page. */
    class AddLayer(val layer: PageLayer) : InkAction()

    /**
     * A layer was removed from the stack of the page.
     *
     * A deleted layer takes its strokes with it ([merged] is false), a layer
     * that was merged into the base layer hands its strokes over to the base
     * layer ([merged] is true).
     */
    class RemoveLayer(
        val layer: PageLayer,
        /** The number the layer had: the undo puts it back at that place. */
        val position: Int,
        /** The strokes that were on the layer. */
        val entries: List<OpEntry>,
        val merged: Boolean,
        val wasCurrent: Boolean
    ) : InkAction()
}

/** One committed operation and the place it had in the list. */
internal class OpEntry(val position: Int, val op: InkOp)

/**
 * The committed operations of one page and their undo history.
 *
 * Pure bookkeeping - no Bitmap, no Canvas - so the order of the operations
 * and the undo/redo can be unit-tested without a device. The controller
 * feeds it the operations and does the pixel work (the eraser and the clear
 * touch the ink layer, so they are handed back to the controller).
 *
 * The history knows the [stack] of the page: a stroke belongs to a layer,
 * and the undo of a deleted or merged layer brings the layer back.
 */
internal class InkHistory(private val stack: PageLayerStack) {

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
            }
        )
        redoStack.clear()
    }

    /**
     * Commits a clear of a layer: the operations of the layer go away with
     * its ink, and both can be brought back.
     */
    fun commitClear(patches: List<InkPatch>, removed: List<OpEntry>) {
        removed.forEach { entry -> ops.removeAll { it === entry.op } }
        undoStack.addLast(InkAction.Clear(patches, removed))
        redoStack.clear()
    }

    /** Records that [moved] strokes were translated by ([dx], [dy]); the move itself is already applied. */
    fun commitMove(moved: List<InkOp.Stroke>, dx: Float, dy: Float) {
        undoStack.addLast(InkAction.Move(moved, dx, dy))
        redoStack.clear()
    }

    /** Records a batch of duplicated strokes added by lasso/selection copy. */
    fun commitAddBatch(added: List<InkOp.Stroke>) {
        if (added.isEmpty()) return
        undoStack.addLast(InkAction.AddBatch(added))
        redoStack.clear()
    }

    /** Records a snapshot-based stroke transform (rotate / scale / mirror). */
    fun commitTransform(snapshots: List<StrokeTransformSnapshot>) {
        if (snapshots.isEmpty()) return
        undoStack.addLast(InkAction.Transform(snapshots))
        redoStack.clear()
    }

    /** A layer was added to the stack of the page. */
    fun commitAddLayer(layer: PageLayer) {
        undoStack.addLast(InkAction.AddLayer(layer))
        redoStack.clear()
    }

    /**
     * A layer was removed from the stack of the page.
     *
     * The strokes of a deleted layer go away with it; the strokes of a layer
     * that was merged into the base layer become part of the base layer.
     */
    fun commitRemoveLayer(layer: PageLayer, merged: Boolean) {
        val position = layer.index
        val wasCurrent = stack.current === layer
        val entries = mutableListOf<OpEntry>()
        ops.forEachIndexed { index, op -> if (op.layer === layer) entries.add(OpEntry(index, op)) }
        if (merged) {
            val base = stack.layerAt(PageLayerStack.BASE_LAYER_INDEX)
            if (base != null) entries.forEach { it.op.layer = base }
        } else {
            entries.sortedByDescending { it.position }.forEach { ops.removeAt(it.position) }
        }
        stack.remove(position)
        undoStack.addLast(InkAction.RemoveLayer(layer, position, entries, merged, wasCurrent))
        redoStack.clear()
    }

    /**
     * Undoes the last operation.
     *
     * [onErase] and [onClear] do the pixel work: [restore] is true when the
     * pixels have to be put back, false when the wipe has to be done again.
     * [onLayer] is told about the layers: [restored] is true when a layer
     * comes back, false when it goes away.
     */
    fun undo(
        onErase: (InkOp.Erase, Boolean) -> Unit,
        onClear: (List<InkPatch>, Boolean) -> Unit,
        onLayer: (InkAction, Boolean) -> Unit = { _, _ -> },
        onMove: (List<InkOp.Stroke>, Float, Float) -> Unit = { _, _, _ -> },
        onTransform: (List<StrokeTransformSnapshot>, Boolean) -> Unit = { _, _ -> }
    ): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        when (action) {
            is InkAction.Add -> ops.removeLastOrNull()
            is InkAction.AddBatch -> action.added.forEach { copied -> ops.removeAll { it === copied } }
            is InkAction.Move -> onMove(action.ops, -action.dx, -action.dy)
            is InkAction.Transform -> onTransform(action.snapshots, true)
            is InkAction.Erase -> {
                ops.removeLastOrNull()
                onErase(action.op, true)
            }
            is InkAction.Clear -> {
                onClear(action.patches, true)
                action.removed.sortedBy { it.position }.forEach { entry ->
                    ops.add(entry.position.coerceIn(0, ops.size), entry.op)
                }
            }
            is InkAction.AddLayer -> {
                // The layer goes away again, with the strokes on it.
                ops.removeAll { it.layer === action.layer }
                stack.remove(action.layer.index)
                onLayer(action, false)
            }
            is InkAction.RemoveLayer -> {
                // The layer comes back at its old place.
                stack.restore(action.layer, action.position)
                if (action.merged) {
                    action.entries.forEach { it.op.layer = action.layer }
                } else {
                    action.entries.sortedBy { it.position }.forEach { entry ->
                        ops.add(entry.position.coerceIn(0, ops.size), entry.op)
                    }
                }
                if (action.wasCurrent) stack.select(action.layer.index)
                onLayer(action, true)
            }
        }
        redoStack.addLast(action)
        return true
    }

    /** Redoes the last undone operation. */
    fun redo(
        onErase: (InkOp.Erase, Boolean) -> Unit,
        onClear: (List<InkPatch>, Boolean) -> Unit,
        onLayer: (InkAction, Boolean) -> Unit = { _, _ -> },
        onMove: (List<InkOp.Stroke>, Float, Float) -> Unit = { _, _, _ -> },
        onTransform: (List<StrokeTransformSnapshot>, Boolean) -> Unit = { _, _ -> }
    ): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        when (action) {
            is InkAction.Add -> ops.add(action.op)
            is InkAction.AddBatch -> ops.addAll(action.added)
            is InkAction.Move -> onMove(action.ops, action.dx, action.dy)
            is InkAction.Transform -> onTransform(action.snapshots, false)
            is InkAction.Erase -> {
                ops.add(action.op)
                onErase(action.op, false)
            }
            is InkAction.Clear -> {
                onClear(action.patches, false)
                action.removed.forEach { entry -> ops.removeAll { it === entry.op } }
            }
            is InkAction.AddLayer -> {
                // The layer is there again.
                stack.restore(action.layer, action.layer.index)
                onLayer(action, true)
            }
            is InkAction.RemoveLayer -> {
                // The layer goes away again.
                if (action.merged) {
                    val base = stack.layerAt(PageLayerStack.BASE_LAYER_INDEX)
                    if (base != null) action.entries.forEach { it.op.layer = base }
                } else {
                    action.entries.forEach { entry -> ops.removeAll { it === entry.op } }
                }
                stack.remove(action.layer.index)
                onLayer(action, false)
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
        is InkAction.AddBatch -> 0L
        is InkAction.Move -> 0L
        is InkAction.Transform -> 0L
        is InkAction.Erase -> action.op.patches.sumOf { it.bytes }
        is InkAction.Clear -> action.patches.sumOf { it.bytes }
        is InkAction.AddLayer -> 0L
        is InkAction.RemoveLayer -> 0L
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
 * The writable annotation layer of one reader page.
 *
 * The writing of a page lives on its annotation layers, not on the page
 * image: every layer of the stack (the base layer, layer 1, and the two
 * layers the user can add on top of it) is a bitmap that reaches out to both
 * sides of the page, and the page image (the clean pdfium render of a PDF
 * page, the page PNG of a raster page) stays clean underneath them.
 *
 * The layer of a fixed page is as wide as the reader's page box (the page
 * plus the margin on both sides) and as tall as the page, so a stroke in the
 * margin is part of the layer.
 */
class ContinuousInkSession internal constructor(
    val pageIndex: Int,
    val isVector: Boolean,
    var inkWidth: Int,
    var inkHeight: Int,
    val pageType: String
) {
    /**
     * The layer stack of the page.
     *
     * The base layer (layer 1) is always there, the user can add two more,
     * and the writing goes on the current layer of the stack.
     */
    val stack = PageLayerStack()

    /**
     * True once the stack of the page has been read from the disk.
     *
     * A save that runs before that would write the *default* stack (the base
     * layer only) and delete the files of the other layers - the layers of the
     * page would be lost.
     */
    var stackLoaded: Boolean = false

    /**
     * The tiles of every layer of the page.
     *
     * A layer normally holds one store of tiles (the layer file of the page
     * cut into tiles); a layer that was merged into the base layer keeps its
     * own store, and the base layer draws it as part of itself.
     *
     * Only the tiles that carry ink are in memory: an empty layer costs
     * nothing at all (audit C50).
     */
    internal val layerTiles = mutableMapOf<PageLayer, LayerTiles>()

    /**
     * The layers whose file could not be read.
     *
     * The file of such a layer must not be written and must not be deleted:
     * the writing of the user is in that file, and the session simply does
     * not have it in memory (the decode failed, most likely because the
     * layer is big). Writing an empty layer over it would destroy it.
     */
    internal val unreadableLayers = mutableSetOf<Int>()

    /** The layers that were merged into a layer (only the base layer). */
    internal val merged = mutableMapOf<PageLayer, MutableList<PageLayer>>()

    /**
     * Bumped whenever the pixels of the tiles change.
     *
     * The small copy the glass samples is built from the tiles, so it has to
     * be built again when they change.
     */
    internal var tilesRevision: Int = 0

    /**
     * The small copy of the layer the glass samples.
     *
     * The glass wants the writing of the layer under the dock, and the layer
     * is a grid of tiles now - so the tiles are composed into one small
     * bitmap (a quarter of the size of the layer: the glass reads the page
     * at that resolution anyway).
     */
    internal var glassBitmap: Bitmap? = null

    /** The tiles revision the glass copy was built from. */
    internal var glassRevision: Int = -1

    /** The layer the glass copy belongs to. */
    internal var glassLayer: PageLayer? = null

    /** True while a new glass copy is being built (the old one is used). */
    internal var glassPending: Boolean = false

    /** The layer the copy that is being built belongs to. */
    internal var glassPendingLayer: PageLayer? = null

    /** The vector operations revision for the glass copy. */
    internal var opRevision: Int = 0

    /** World-space origin and scale of [glassBitmap] when [pageType] == "infinite". */
    internal var glassWorldLeft: Float = -0.25f
    internal var glassWorldTop: Float = -0.10f
    internal var glassPxPerUnit: Float = 540f

    internal var background: Bitmap? = null
    internal var backgroundWidth: Int = 0
    internal var ready: Boolean = false
    internal var loadJob: Job? = null
    internal var saveJob: Job? = null
    internal var dirty: Boolean = false
    internal var savedWhileActive: Boolean = false
    internal var live: ContinuousStroke? = null

    /**
     * The layer the writing goes on (the current layer of the stack).
     */
    val currentLayer: PageLayer? get() = stack.current

    /**
     * The tiles of a layer: its own and the ones of the layers that were
     * merged into it.
     *
     * Every placement knows where its tiles live in the layer's pixels, so
     * the drawing code can put each tile where it belongs.
     */
    fun tilesOf(layer: PageLayer): List<LayerTilePlacement> {
        val own = layerTiles[layer]?.let { listOf(LayerTilePlacement(it, it.grid)) }.orEmpty()
        val folded = merged[layer].orEmpty().mapNotNull { other ->
            layerTiles[other]?.let { LayerTilePlacement(it, it.grid) }
        }
        return own + folded
    }

    /**
     * The size of a tile of the layer, in pixels.
     */
    var tileSize: Int = LayerTileGrid.DEFAULT_TILE_SIZE

    /**
     * The size of the page box the strokes are normalised to, and the
     * aspect ratio of the page image inside it.
     *
     * The box is wider than the page image (a fixed page has a margin on both
     * sides), so the strokes can reach past the page: they belong to the
     * annotation layer, which reaches as far as the box.
     */
    var boxWidth: Float = 0f
    var boxHeight: Float = 0f
    var pageRatio: Float = ContinuousPageGeometry.defaultRatio(pageType)

    /**
     * The strokes that were finished in this session, in the order they were
     * committed.
     *
     * They are drawn as vectors on top of the page image, so the session
     * bitmap is never touched by a stroke: the reader does not have to
     * re-upload the layer (and the pen does not wait for it) after every
     * stroke.
     */
    internal val history = InkHistory(stack)

    internal val pending = mutableListOf<ContinuousStroke>()

    /**
     * Guards the pixels of the tiles.
     *
     * The eraser clears pixels in them on the main thread, and the save
     * takes a copy of them for the encoder - a copy of the whole layer must
     * not happen on the main thread, it blocks the pen for tens of
     * milliseconds.
     */
    internal val inkLock = Any()

    /**
     * Where the page image sits inside the page box, in normalised box
     * coordinates. Everything that maps between the box (the space the
     * strokes live in) and the page (the space the page image lives in)
     * goes through this rect.
     */
    val pageRectInBox: ContinuousPageGeometry.BoxRect
        get() = ContinuousPageGeometry.pageRect(pageType, pageRatio, boxWidth, boxHeight)

    /**
     * Where the annotation layer sits inside the page box, in normalised box
     * coordinates: the whole box for a fixed page (the page plus the margin
     * on both sides), the page image itself for an infinite page.
     */
    val layerRectInBox: ContinuousPageGeometry.BoxRect
        get() = ContinuousPageGeometry.layerRect(pageType, pageRatio, boxWidth, boxHeight)

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
 * small ink engine: this controller owns one page's annotation layer at a
 * time, draws the live stroke into the page box, commits it into the layer,
 * and persists it as the layer file of the page
 * (`layers/page_<i>_layer_<n>.png`).
 *
 * The layer is wider than the page (the reader's page box reaches out to
 * both sides), so the notes in the margin belong to it - the page image
 * itself (the PDF render, the page PNG) is never written to and stays the
 * clean document.
 *
 * One session, not one per page: a page layer is ~40 MB decoded and the
 * reader keeps two or three pages on screen. The session follows the stylus
 * from page to page; the previous page is flushed and released first.
 *
 * The strokes are kept as vectors: a finished stroke joins the committed
 * list of the session and is drawn as a path on top of the page image, so
 * the session bitmap is never rewritten (and never re-uploaded to the GPU)
 * while the user writes. The list is rasterised into the bitmap once, off
 * the main thread, when the page is saved.
 *
 * Undo/redo works on that list: a stroke leaves the list again, an eraser
 * puts back the pixels it cleared (dirty-rectangle patches, so a short
 * stroke costs a few hundred KB rather than the whole layer).
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
        settings: InkToolSettings,
        tilt: Float = 0f
    ): Boolean {
        if (!settings.isWritingTool) return false
        if (boxWidth <= 0f || boxHeight <= 0f) return false
        val s = ensureSession(page, pageIndex, boxWidth, boxHeight, renderWidthPx)
        if (s.pageType == "a4" && (x !in 0f..1f || y !in 0f..1f)) {
            return false
        }
        val widthNorm = if (settings.tool == CanvasTool.ERASER) {
            settings.eraserWidthPx / boxWidth
        } else {
            settings.strokeWidthPx / boxWidth
        }
        val effectiveColor = if (settings.tool != CanvasTool.ERASER && settings.tool != CanvasTool.HIGHLIGHTER && settings.opacity < 1f) {
            val baseAlpha = android.graphics.Color.alpha(settings.color)
            val newAlpha = (baseAlpha * settings.opacity.coerceIn(0.05f, 1f)).roundToInt().coerceIn(1, 255)
            (settings.color and 0x00FFFFFF) or (newAlpha shl 24)
        } else {
            settings.color
        }
        val stroke = ContinuousStroke(settings.tool, effectiveColor, widthNorm, settings.autoShapeEnabled)
        addPoint(stroke, x, y, pressure, settings, tilt)
        s.live = stroke
        isDrawing = true
        livePageIndex = pageIndex
        revision++
        return true
    }

    /** Adds a point to the stroke in progress (page-normalised). */
    fun extendStroke(x: Float, y: Float, pressure: Float, settings: InkToolSettings, tilt: Float = 0f) {
        val stroke = session?.live ?: return
        addPoint(stroke, x, y, pressure, settings, tilt)
        previewRevision++
    }

    /** Lifts the pen: the stroke joins the page's ink layer and is saved. */
    fun endStroke(x: Float, y: Float, pressure: Float, settings: InkToolSettings, tilt: Float = 0f) {
        val s = session ?: return
        isDrawing = false
        livePageIndex = -1
        val stroke = s.live ?: return
        addPoint(stroke, x, y, pressure, settings, tilt)
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
        settings: InkToolSettings,
        tilt: Float = 0f
    ) {
        // An A4 page is limited to its own size: the writing cannot
        // reach past it. PDF and infinite pages keep the margin, so a
        // stroke can land outside the page.
        val cx = if (session?.pageType == "a4") {
            x.coerceIn(0f, 1f)
        } else {
            x.coerceIn(-1000f, 1000f)
        }
        val cy = if (session?.pageType == "a4") {
            y.coerceIn(0f, 1f)
        } else {
            y.coerceIn(-1000f, 1000f)
        }
        val tiltNorm = ((tilt - 0.16f) / 0.90f).coerceIn(0f, 1f)
        val last = stroke.points.lastOrNull()
        var fx = cx
        var fy = cy
        if (last != null) {
            val dx = cx - last.x
            val dy = cy - last.y
            val distSq = dx * dx + dy * dy
            // ~1.5 px on a 2160 px page: dense enough to look smooth, sparse
            // enough to keep the path small while the pen moves quickly.
            if (distSq < 0.0000006f) return
            // Velocity-adaptive micro-jitter stabilizer for freehand tools:
            // smooths glass tremor on slow strokes while keeping fast curves responsive.
            if (stroke.tool == CanvasTool.PEN || stroke.tool == CanvasTool.FOUNTAIN_PEN || stroke.tool == CanvasTool.PENCIL) {
                val dist = kotlin.math.sqrt(distSq.toDouble()).toFloat()
                val alpha = (0.58f + (dist / 0.012f) * 0.34f).coerceIn(0.58f, 0.92f)
                fx = last.x + (cx - last.x) * alpha
                fy = last.y + (cy - last.y) * alpha
            }
        }
        val w = when (stroke.tool) {
            CanvasTool.FOUNTAIN_PEN ->
                fountainWidth(pressure, stroke.baseWidthNorm, settings, last?.w)
            CanvasTool.PEN -> {
                val target = PencilGrainShader.calculatePenWidth(
                    stroke.baseWidthNorm, pressure, settings.pressureSizeEnabled
                )
                last?.w?.let { ContinuousInkMath.smooth(it, target) } ?: target
            }
            CanvasTool.PENCIL -> {
                val target = PencilGrainShader.calculatePencilWidth(
                    stroke.baseWidthNorm, pressure, tilt, settings.pressureSizeEnabled
                )
                last?.w?.let { ContinuousInkMath.smooth(it, target) } ?: target
            }
            else -> stroke.baseWidthNorm
        }
        stroke.points.add(InkPoint(fx, fy, w, tiltNorm))
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
        val closing = closingSessions.remove(pageIndex)
        if (closing != null) {
            closing.boxWidth = boxWidth
            closing.boxHeight = boxHeight
            session = closing
            onUndoRedoChanged(closing.history.canUndo, closing.history.canRedo)
            return closing
        }
        val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
        // The annotation layer lives in the box's pixel space, not in the
        // page's: the box is wider than the page (it has a margin on both
        // sides), so the layer is as wide as the box and as tall as the page.
        val ratio = pageRatioOf(page, isVector)
        val size = ContinuousPageGeometry.layerSize(pageType, ratio)
        val created = ContinuousInkSession(pageIndex, isVector, size.width, size.height, pageType)
        created.boxWidth = boxWidth
        created.boxHeight = boxHeight
        created.pageRatio = ratio
        session = created
        onUndoRedoChanged(false, false)
        startLoad(created, page, renderWidthPx)
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
     * The aspect ratio (width / height) of the page image of a raster page.
     *
     * The page image is the description of the page: its size is the ratio
     * the reader draws the page (and the layer) with.
     */
    private fun rasterPageRatio(pageIndex: Int): Float {
        val file = repository.fileStore.getPageFile(noteId, pageIndex)
        if (!file.exists()) return 0f
        val bounds = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0f
        return bounds.outWidth.toFloat() / bounds.outHeight
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
        val ratio = s?.pageRatio ?: if (fallbackRatio > 0f) fallbackRatio else ContinuousPageGeometry.defaultRatio(pageType)
        return ContinuousPageGeometry.pageRect(pageType, ratio, boxWidth, boxHeight)
    }

    /**
     * Where the annotation layer sits inside the page box, in normalised box
     * coordinates.
     *
     * A fixed page: the layer covers the whole box (the page plus the
     * margin on both sides). An infinite page: the layer is the page image.
     */
    fun layerRectFor(
        pageIndex: Int,
        boxWidth: Float,
        boxHeight: Float,
        fallbackRatio: Float = 0f
    ): ContinuousPageGeometry.BoxRect {
        val s = getSession(pageIndex)
        val ratio = s?.pageRatio ?: if (fallbackRatio > 0f) fallbackRatio else ContinuousPageGeometry.defaultRatio(pageType)
        return ContinuousPageGeometry.layerRect(pageType, ratio, boxWidth, boxHeight)
    }

    private fun startLoad(s: ContinuousInkSession, page: PageEntity, renderWidthPx: Int) {
        if (s.loadJob != null) return
        s.loadJob = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    // A raster page is described by its page image: its size
                    // is the ratio of the page, so the layer is as tall as
                    // the page.
                    var pageRatio = 0f
                    if (!s.isVector) {
                        pageRatio = rasterPageRatio(s.pageIndex)
                        if (pageRatio > 0f) {
                            val size = ContinuousPageGeometry.layerSize(pageType, pageRatio)
                            if (size.width != s.inkWidth || size.height != s.inkHeight) {
                                s.inkWidth = size.width
                                s.inkHeight = size.height
                                s.pageRatio = pageRatio
                            }
                        }
                    }
                    // The layer stack of the page: the metadata file names
                    // the layers (their number, their name and whether they
                    // are hidden) and the layer the writing goes on.
                    val stack = readStack(s.pageIndex)
                    // The pixels of every layer: the persisted form of the
                    // writing. The page image itself is never touched by a
                    // stroke.
                    //
                    // The layer file is still ONE bitmap (the old format), so
                    // it is cut into tiles here and the big bitmap is given
                    // back: the session holds the tiles, not the whole layer
                    // (audit C50).
                    // Read the tile information of the layers from
                    // the metadata.
                    val tilesInfo = PageLayerMeta.decodeTiles(
                        try {
                            val f = repository.fileStore.getAnnotationMetaFile(noteId, s.pageIndex)
                            if (f.exists()) f.readText() else ""
                        } catch (e: Exception) {
                            ""
                        }
                    )
                    val stores = LinkedHashMap<PageLayer, LayerTiles>()
                    var loadedHeight = 0
                    for (layer in stack.layers) {
                        val info = tilesInfo[layer.index]
                        val store: LayerTiles?
                        if (info != null && info.tiles.isNotEmpty()) {
                            // Tiled layer: read the tile files.
                            val grid = LayerTileGrid(info.width, info.height, info.tileSize)
                            val t = LayerTiles(grid)
                            val loaded = try {
                                repository.loadPageLayerTiles(noteId, s.pageIndex, layer.index, info)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                emptyList()
                            }
                            for ((pos, bitmap) in loaded) {
                                val (tx, ty) = pos
                                if (tx < 0 || ty < 0 || tx >= grid.columns || ty >= grid.rows) continue
                                if (bitmap.isRecycled) continue
                                val tile = t.requireTile(tx, ty)
                                val canvas = Canvas(tile)
                                canvas.drawBitmap(bitmap, 0f, 0f, null)
                                bitmap.recycle()
                            }
                            for (tx in 0 until grid.columns) {
                                for (ty in 0 until grid.rows) {
                                    t.releaseIfEmpty(tx, ty)
                                }
                            }
                            store = t
                            loadedHeight = max(loadedHeight, info.height)
                            s.unreadableLayers.remove(layer.index)
                        } else {
                            // Legacy layer: one full-size PNG. Decode it and
                            // split it into tiles.
                            val file = repository.fileStore.getAnnotationLayerFile(
                                noteId, s.pageIndex, layer.index
                            )
                            val stored = decodeLayerBitmap(file)
                            if (stored != null) {
                                s.unreadableLayers.remove(layer.index)
                                val bitmap = if (stored.width == s.inkWidth && stored.height == s.inkHeight) {
                                    stored
                                } else {
                                    fitLayer(stored, s.inkWidth, s.inkHeight)
                                }
                                loadedHeight = max(loadedHeight, bitmap.height)
                                store = tilesFromBitmap(s, bitmap)
                                if (!bitmap.isRecycled) bitmap.recycle()
                            } else {
                                if (file.exists()) s.unreadableLayers.add(layer.index)
                                store = null
                            }
                        }
                        if (store != null) {
                            stores[layer] = store
                        }
                        // Read the ops of the layer (the strokes and the erases
                        // as vectors). They are drawn on top of the tiles.
                        val opsFile = repository.fileStore.getAnnotationLayerOpsFile(noteId, s.pageIndex, layer.index)
                        android.util.Log.d("TouchNotes", "load: page=${s.pageIndex} layer=${layer.index} opsFile=${opsFile.absolutePath} exists=${opsFile.exists()} size=${if (opsFile.exists()) opsFile.length() else 0}")
                        val ops = try {
                            repository.loadPageLayerOps(noteId, s.pageIndex, layer.index)
                        } catch (e: Exception) {
                            android.util.Log.e("TouchNotes", "load: exception reading ops", e)
                            null
                        }
                        if (ops != null) {
                            android.util.Log.d("TouchNotes", "load: page=${s.pageIndex} layer=${layer.index} ops=${ops.size}")
                            for (op in ops) {
                                when (op) {
                                    is InkOp.Stroke -> op.layer = layer
                                    is InkOp.Erase -> op.layer = layer
                                }
                            }
                            s.history.ops.addAll(ops)
                        } else {
                            android.util.Log.d("TouchNotes", "load: page=${s.pageIndex} layer=${layer.index} no ops file")
                        }
                    }
                    LoadedLayers(stack, stores, loadedHeight)
                }
                if (session !== s) {
                    loaded.stores.values.forEach { store -> store.release() }
                    return@launch
                }
                s.stack.load(loaded.stack.layers, loaded.stack.currentIndex)
                s.stackLoaded = true
                s.layerTiles.clear()
                s.layerTiles.putAll(loaded.stores)
                s.tilesRevision++
                if (loaded.height > 0) {
                    s.inkHeight = loaded.height
                    if (loaded.stack.layers.isNotEmpty() && !s.isVector && s.pageRatio <= 0f) {
                        // The page image is gone: the layer describes the
                        // page now.
                        s.pageRatio = ContinuousPageGeometry.ratioOfLayer(
                            pageType, s.inkWidth, s.inkHeight
                        )
                    }
                }
                if (s.isVector) {
                    s.background = withContext(Dispatchers.IO) {
                        renderContinuousPage(
                            page, s.pageIndex, noteId, pdfDocCache, repository,
                            renderWidthPx.coerceAtLeast(720), includeInk = true
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
                // The glass wants the writing of the layer: its copy is built
                // in the background, so it is there when the dock is drawn.
                scheduleGlassCopy(s)
                onUndoRedoChanged(s.canUndo, s.canRedo)
                revision++
                layersRevision++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** The layers of a page and their pixels, as they were read from the disk. */
    private class LoadedLayers(
        val stack: PageLayerStack,
        val stores: Map<PageLayer, LayerTiles>,
        val height: Int
    )

    /**
     * Decodes the layer file of a page.
     *
     * The bitmap is decoded as it is - it is not copied into a mutable one:
     * the tiles of the layer are mutable, so the big bitmap is only read and
     * given back right away. A copy of it would be 80 MB of memory for
     * nothing.
     */
    private fun decodeLayerBitmap(file: java.io.File): Bitmap? {
        return try {
            if (!file.exists()) return null
            val options = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Cuts a layer bitmap into the tiles of the layer.
     *
     * The layer file is still one bitmap (the old format), so it is read
     * once and cut into tiles here: only the tiles that hold ink stay in
     * memory, the empty ones are given back at once. The bitmap itself is
     * released by the caller - the session must not hold 80 MB.
     */
    private fun tilesFromBitmap(s: ContinuousInkSession, source: Bitmap): LayerTiles {
        val grid = LayerTileGrid(s.inkWidth, s.inkHeight, s.tileSize)
        val store = LayerTiles(grid)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (ty in 0 until grid.rows) {
            for (tx in 0 until grid.columns) {
                val left = grid.tileLeft(tx)
                val top = grid.tileTop(ty)
                val width = grid.tileWidth(tx)
                val height = grid.tileHeight(ty)
                if (width <= 0 || height <= 0) continue
                // The part of the tile the source covers (the source can be
                // smaller than the layer).
                val sx = max(0, left)
                val sy = max(0, top)
                val ex = min(source.width, left + width)
                val ey = min(source.height, top + height)
                if (ex <= sx || ey <= sy) continue
                val tile = store.requireTile(tx, ty)
                if (tile.isRecycled) continue
                val canvas = Canvas(tile)
                canvas.drawBitmap(
                    source,
                    Rect(sx, sy, ex, ey),
                    RectF(
                        (sx - left).toFloat(),
                        (sy - top).toFloat(),
                        (ex - left).toFloat(),
                        (ey - top).toFloat()
                    ),
                    paint
                )
                // A tile without ink costs nothing: it goes back to the
                // system right away.
                store.releaseIfEmpty(tx, ty)
            }
        }
        return store
    }

    /**
     * The layer stack of a page as it is on the disk.
     *
     * A page without a metadata file has the default stack: the base layer
     * only. The stack is copied: the session of a page owns its own layers,
     * and a layer that is renamed or removed there must not change the
     * stack that is on the disk.
     */
    private fun readStack(pageIndex: Int): PageLayerStack {
        val cached = storedStacks[pageIndex]
        if (cached != null) return copyStack(cached)
        val file = repository.fileStore.getAnnotationMetaFile(noteId, pageIndex)
        val text = try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        val stack = text?.let { PageLayerMeta.decode(it) } ?: PageLayerStack.base()
        storedStacks[pageIndex] = stack
        return copyStack(stack)
    }

    /**
     * Makes sure the stack of the page is the one on the disk.
     *
     * The session loads it in the background; a save that runs before that
     * would write the default stack (the base layer only) and delete the
     * files of the other layers.
     */
    private fun ensureStackLoaded(s: ContinuousInkSession) {
        if (s.stackLoaded) return
        val stack = readStack(s.pageIndex)
        s.stack.load(stack.layers, stack.currentIndex)
        s.stackLoaded = true
    }

    /** A copy of a stack: the layers of the copy are new objects. */
    private fun copyStack(stack: PageLayerStack): PageLayerStack {
        val copy = PageLayerStack()
        copy.load(
            stack.layers.map { PageLayer(it.index, it.name, it.visible) },
            stack.currentIndex
        )
        return copy
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
                    renderWidthPx, includeInk = true
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
     * Every stroke belongs to the current layer of the page - also the
     * ones that reach past the page image into the margin: the layer is
     * wider than the page, so the margin notes are part of it and the page
     * image stays clean.
     */
    private fun commit(s: ContinuousInkSession, stroke: ContinuousStroke, autoShapeEnabled: Boolean = false) {
        if (stroke.points.isEmpty()) return
        val layer = s.stack.current ?: return
        if (stroke.tool == CanvasTool.ERASER) {
            commitErase(s, stroke)
            return
        }
        var shape: Path? = null
        if (autoShapeEnabled && (stroke.tool == CanvasTool.PEN || stroke.tool == CanvasTool.FOUNTAIN_PEN)) {
            if (s.pageType == "infinite") {
                val pts = stroke.points.map { (it.x * 2160f) to (it.y * 2160f) }
                shape = autoShape(pts)
            } else {
                val rect = s.layerRectInBox
                if (!rect.empty) {
                    // The auto-shape works on points in pixels: the stroke is
                    // normalised to the box, the path lives in the layer's pixels.
                    val sx = s.inkWidth / rect.width
                    val sy = s.inkHeight / rect.height
                    val pts = stroke.points.map { ((it.x - rect.left) * sx) to ((it.y - rect.top) * sy) }
                    shape = autoShape(pts)
                }
            }
        }
        val op = InkOp.Stroke(stroke, shape, layer)
        s.history.commit(op)
        trimUndo(s)
        s.dirty = true
        s.opRevision++
        scheduleGlassCopy(s)
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
     * pixels, so it is drawn into the pixels of the current layer - the PDF
     * text lives in the background, not in that layer, so the text under the
     * eraser stays. The pixels it cleared are kept in a patch so the wipe
     * can be undone, and the operation stays in the committed list so it can
     * cut the strokes that were drawn before it.
     *
     * The eraser only touches the layer the writing goes on: the other
     * layers of the page keep their writing.
     *
     * The layer is a grid of tiles, so the wipe happens tile by tile: every
     * tile the stroke touches gets its own patch (the rectangle inside that
     * tile and the pixels before and after), and a tile that holds nothing
     * any more is given back to the system.
     */
    private fun commitErase(s: ContinuousInkSession, stroke: ContinuousStroke) {
        val layer = s.stack.current ?: return
        val patches = mutableListOf<InkPatch>()
        val bounds = strokeBoundsInInk(stroke, s)
        if (s.pageType != "infinite") {
            if (bounds == null) return
            for (placement in s.tilesOf(layer)) {
                val store = placement.tiles
                val grid = placement.grid
                val touched = grid.tilesForRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
                for (index in touched) {
                    val tx = grid.tileColumn(index)
                    val ty = grid.tileRow(index)
                    if (tx < 0 || ty < 0) continue
                    // Nothing to wipe in a tile that does not exist: it is not
                    // created just to be emptied again.
                    val tile = store.tile(tx, ty) ?: continue
                    if (tile.isRecycled) continue
                    val rect = LayerTilePatch.rectInTile(
                        grid, tx, ty, bounds.left, bounds.top, bounds.right, bounds.bottom
                    ) ?: continue
                    val before = capture(tile, Rect(rect[0], rect[1], rect[2], rect[3])) ?: continue
                    val after: IntArray?
                    synchronized(s.inkLock) {
                        val canvas = Canvas(tile)
                        drawEraseInTile(canvas, s, stroke, grid, tx, ty)
                        after = capture(tile, Rect(rect[0], rect[1], rect[2], rect[3]))
                    }
                    patches.add(
                        InkPatch(
                            tx, ty,
                            rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1],
                            before, after, store
                        )
                    )
                    // A tile without ink costs nothing: it goes back to the
                    // system right away.
                    store.releaseIfEmpty(tx, ty)
                }
            }
        }
        val op = InkOp.Erase(stroke, patches, layer)
        s.history.commit(op)
        trimUndo(s)
        s.dirty = true
        s.tilesRevision++
        s.opRevision++
        scheduleGlassCopy(s)
        // The erase changed the tiles, so the reader has to redraw them:
        // without this the wiped stroke stayed on the screen until something
        // else invalidated the page.
        revision++
    }

    /**
     * Draws an eraser stroke into ONE tile of a layer.
     *
     * The stroke is drawn in the layer's pixel space; the tile has its own
     * origin, so the canvas is moved by it: the part of the stroke that lies
     * in the tile is wiped and the rest of it falls outside the tile.
     */
    private fun drawEraseInTile(
        canvas: Canvas,
        s: ContinuousInkSession,
        stroke: ContinuousStroke,
        grid: LayerTileGrid,
        tx: Int,
        ty: Int
    ) {
        val rect = s.layerRectInBox
        if (rect.empty) return
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        val save = canvas.save()
        // Translate in layer pixels and draw directly at pixel dimensions (sx, sy).
        canvas.translate(-grid.tileLeft(tx).toFloat() - rect.left * sx, -grid.tileTop(ty).toFloat() - rect.top * sy)
        drawStroke(canvas, stroke, sx, sy, preview = false)
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
        val s = session
        android.util.Log.d("InkUndo", "undo session=${s != null} canUndo=${s?.canUndo} ops=${s?.history?.ops?.size}")
        if (s == null) return false
        val undone = s.history.undo(
            onErase = { op, restore -> restoreErase(s, op, restore) },
            onClear = { patches, restore -> restoreClear(s, patches, restore) },
            onLayer = { action, restored -> applyLayerAction(s, action, restored) },
            onMove = { moved, dx, dy -> moved.forEach { translateStroke(it, dx, dy) } },
            onTransform = { snapshots, isUndo -> applyStrokeSnapshots(snapshots, isUndo) }
        )
        if (!undone) return false
        lassoOps = emptyList()
        lassoRevision++
        s.dirty = true
        s.opRevision++
        scheduleGlassCopy(s)
        scheduleSave(s)
        revision++
        layersRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /** Redoes the last undone operation. */
    fun redo(): Boolean {
        val s = session
        android.util.Log.d("InkUndo", "redo session=${s != null} canRedo=${s?.canRedo}")
        if (s == null) return false
        val redone = s.history.redo(
            onErase = { op, restore -> restoreErase(s, op, restore) },
            onClear = { patches, restore -> restoreClear(s, patches, restore) },
            onLayer = { action, restored -> applyLayerAction(s, action, restored) },
            onMove = { moved, dx, dy -> moved.forEach { translateStroke(it, dx, dy) } },
            onTransform = { snapshots, isUndo -> applyStrokeSnapshots(snapshots, isUndo) }
        )
        if (!redone) return false
        lassoOps = emptyList()
        lassoRevision++
        s.dirty = true
        s.opRevision++
        scheduleGlassCopy(s)
        scheduleSave(s)
        revision++
        layersRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * The pixel side of a layer that was removed or brought back.
     *
     * A merged layer keeps its own pixels; the base layer draws them as
     * part of itself while the merge lasts. A layer that was added and then
     * undone away was empty, so its pixels are dropped.
     */
    private fun applyLayerAction(s: ContinuousInkSession, action: InkAction, restored: Boolean) {
        when (action) {
            is InkAction.RemoveLayer -> {
                // A deleted layer keeps its tiles (the undo brings the
                // writing back); a merged one is folded into the base layer.
                if (action.merged) {
                    val base = s.stack.layerAt(PageLayerStack.BASE_LAYER_INDEX)
                    if (base != null) {
                        val folded = s.merged.getOrPut(base) { mutableListOf() }
                        if (restored) {
                            folded.removeAll { it === action.layer }
                        } else if (!folded.any { it === action.layer }) {
                            folded.add(action.layer)
                        }
                    }
                }
            }
            is InkAction.AddLayer -> {
                s.layerTiles.remove(action.layer)
                s.merged.values.forEach { list -> list.removeAll { it === action.layer } }
            }
            else -> {}
        }
        // The layers of the page changed: the glass shows the writing of the
        // current one.
        s.tilesRevision++
        scheduleGlassCopy(s)
    }

    /**
     * Puts the pixels of an eraser back (undo) or wipes them again (redo).
     *
     * Every patch knows the store and the tile it belongs to: a layer can
     * hold more than one store (the tiles of a layer that was merged into
     * it).
     */
    private fun restoreErase(s: ContinuousInkSession, op: InkOp.Erase, restore: Boolean) {
        synchronized(s.inkLock) {
            op.patches.forEach { patch ->
                val store = patch.target ?: return@forEach
                restorePatch(store, patch, useAfter = !restore)
            }
        }
        s.tilesRevision++
        scheduleGlassCopy(s)
    }

    /**
     * Puts the ink of a cleared layer back (undo) or wipes it again (redo).
     *
     * The layer holds the writing of the page (also the notes in the
     * margin), so it goes with it. Every patch knows the store and the tile
     * it belongs to, so the ink of the right layer comes back.
     */
    private fun restoreClear(s: ContinuousInkSession, patches: List<InkPatch>, restore: Boolean) {
        synchronized(s.inkLock) {
            if (restore) {
                patches.forEach { patch ->
                    val store = patch.target ?: return@forEach
                    restorePatch(store, patch, useAfter = false)
                }
            } else {
                patches.forEach { patch ->
                    val store = patch.target ?: return@forEach
                    val tile = store.tile(patch.tx, patch.ty) ?: return@forEach
                    if (tile.isRecycled) return@forEach
                    tile.eraseColor(android.graphics.Color.TRANSPARENT)
                    store.markDirty(patch.tx, patch.ty)
                    store.releaseIfEmpty(patch.tx, patch.ty)
                }
            }
        }
        s.tilesRevision++
        scheduleGlassCopy(s)
    }

    /**
     * Puts the pixels of a patch back into its tile (or wipes them again).
     *
     * The patch holds the rectangle inside the tile and the pixels before
     * and after the wipe. A tile that was emptied by the wipe is not there
     * any more, so it is created again for the pixels that go back into it.
     */
    private fun restorePatch(store: LayerTiles, patch: InkPatch, useAfter: Boolean) {
        val pixels = if (useAfter) patch.after else patch.before
        if (pixels == null) {
            // Nothing to put back: the wipe is done again with CLEAR.
            if (useAfter) {
                // The patch holds the rectangle inside the tile; the store
                // wants it in the layer's pixels.
                val originX = store.grid.tileLeft(patch.tx)
                val originY = store.grid.tileTop(patch.ty)
                store.eraseRect(
                    originX + patch.x,
                    originY + patch.y,
                    originX + patch.x + patch.w,
                    originY + patch.y + patch.h
                )
            }
            return
        }
        val tile = store.requireTile(patch.tx, patch.ty)
        if (tile.isRecycled) return
        try {
            tile.setPixels(pixels, 0, patch.w, patch.x, patch.y, patch.w, patch.h)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        store.markDirty(patch.tx, patch.ty)
        // A tile without ink costs nothing: it goes back to the system.
        store.releaseIfEmpty(patch.tx, patch.ty)
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
        val size = ContinuousPageGeometry.layerSize(pageType, ratio)
        val created = ContinuousInkSession(pageIndex, isVector, size.width, size.height, pageType)
        created.pageRatio = ratio
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

    /**
     * Clears the current layer of the page.
     *
     * Only the layer the writing goes on is cleared: the other layers of the
     * page keep their writing. The undo brings the ink and the strokes back.
     *
     * The layer is a grid of tiles, so every tile is cleared on its own and
     * gets its own patch: the undo puts the pixels of each tile back.
     */
    private fun clearSession(s: ContinuousInkSession): Boolean {
        val layer = s.stack.current ?: return false
        val placements = s.tilesOf(layer)
        val layerOps = s.history.ops.filter { it.layer.index == layer.index }
        val hasInk = placements.any { !it.tiles.isEmpty() }
        if (!hasInk && layerOps.isEmpty()) return false
        val patches = mutableListOf<InkPatch>()
        for (placement in placements) {
            val store = placement.tiles
            val grid = placement.grid
            for (index in store.existingTiles()) {
                val tx = grid.tileColumn(index)
                val ty = grid.tileRow(index)
                if (tx < 0 || ty < 0) continue
                val tile = store.tile(tx, ty) ?: continue
                if (tile.isRecycled) continue
                val contentBounds = findContentBounds(tile) ?: continue
                val before = capture(tile, contentBounds) ?: continue
                synchronized(s.inkLock) { tile.eraseColor(android.graphics.Color.TRANSPARENT) }
                patches.add(
                    InkPatch(
                        tx, ty,
                        contentBounds.left, contentBounds.top,
                        contentBounds.width(), contentBounds.height(),
                        before, null, store
                    )
                )
                // A tile without ink costs nothing: it goes back to the
                // system right away.
                store.releaseIfEmpty(tx, ty)
            }
        }
        // The strokes of this layer go away with its ink; the undo keeps
        // both, so the clear can be taken back.
        val removed = mutableListOf<OpEntry>()
        s.history.ops.forEachIndexed { position, op ->
            if (op.layer.index == layer.index) removed.add(OpEntry(position, op))
        }
        s.history.commitClear(patches, removed)
        trimUndo(s)
        s.dirty = true
        s.tilesRevision++
        s.opRevision++
        scheduleGlassCopy(s)
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

    /** Bounds of what a stroke touches, in layer pixels, clamped to the layer. */
    private fun strokeBoundsInInk(stroke: ContinuousStroke, s: ContinuousInkSession): Rect? {
        val rect = s.layerRectInBox
        if (rect.empty) return null
        val bounds = strokeBoundsInBox(stroke, s) ?: return null
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        // The eraser reaches the whole layer: the notes in the margin are on
        // the layer too, so they can be wiped as well.
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
            // Long enough that a page of writing is saved once instead of
            // after every stroke - each save encodes the whole layer.
            delay(2500)
            persist(s)
        }
    }

    private suspend fun persist(s: ContinuousInkSession) {
        android.util.Log.d("TouchNotes", "persist: called for page=${s.pageIndex} dirty=${s.dirty} layers=${s.stack.layers.size}")
        // The stack of the page has to be known before the layers are
        // written: otherwise the save would write the base layer only and
        // delete the files of the other layers.
        withContext(Dispatchers.IO) { ensureStackLoaded(s) }
        if (s.stack.layers.isEmpty()) return
        // The page is only "clean" when the write actually succeeded: a
        // failed write must stay dirty so the next save tries again.
        s.dirty = false
        // Every layer of the page is written to its own files: the tiles
        // (one PNG per tile, only the ones that changed) and the ops (the
        // strokes and the erases as vectors). The stack (the layers, their
        // names and the current one) goes to the metadata file of the page.
        for (layer in s.stack.layers) {
            if (s.unreadableLayers.contains(layer.index)) {
                // The file of this layer could not be read: writing it now
                // would replace the writing of the user with an empty layer.
                // The file stays as it is.
                s.dirty = true
                android.util.Log.w(
                    "TouchNotes",
                    "Layer ${layer.index} of page ${s.pageIndex} could not be read - not saved"
                )
                continue
            }
            val store = s.layerTiles[layer]
            // Write the tiles that changed: each tile is already a small
            // bitmap in memory, so this is cheap (no 80 MB allocation, no
            // full-size PNG encode).
            if (store != null) {
                for (index in store.dirtyTiles()) {
                    val tx = store.grid.tileColumn(index)
                    val ty = store.grid.tileRow(index)
                    if (tx < 0 || ty < 0) continue
                    val bitmap = store.tile(tx, ty)
                    if (bitmap != null && !bitmap.isRecycled) {
                        val ok = withContext(Dispatchers.IO) {
                            repository.savePageLayerTile(
                                noteId, s.pageIndex, layer.index, tx, ty, bitmap
                            )
                        }
                        if (!ok) {
                            s.dirty = true
                            android.util.Log.w(
                                "TouchNotes",
                                "Could not save tile $tx,$ty of layer ${layer.index}"
                            )
                        }
                    } else {
                        // The tile was released (the eraser emptied it): its
                        // file must go, or the old writing would come back.
                        withContext(Dispatchers.IO) {
                            repository.fileStore.getAnnotationLayerTileFile(
                                noteId, s.pageIndex, layer.index, tx, ty
                            ).delete()
                        }
                    }
                }
                store.markClean()
            }
            // Write the ops of the layer (the strokes and the erases as
            // vectors). Only the ops of this layer are written.
            val ops = s.history.ops.filter { it.layer.index == layer.index }
            android.util.Log.d("TouchNotes", "persist: page=${s.pageIndex} layer=${layer.index} ops=${ops.size} dirty=${s.dirty}")
            val ok = withContext(Dispatchers.IO) {
                repository.savePageLayerOps(noteId, s.pageIndex, layer.index, ops)
            }
            if (!ok) {
                s.dirty = true
                android.util.Log.w("TouchNotes", "Could not save ops of layer ${layer.index}")
            }
            // The migration: a legacy layer (one full-size PNG) is now
            // stored as tiles + ops. The legacy PNG goes away, but only
            // after the new files are written.
            withContext(Dispatchers.IO) {
                val legacyFile = repository.fileStore.getAnnotationLayerFile(
                    noteId, s.pageIndex, layer.index
                )
                if (legacyFile.exists() && store != null && store.existingTiles().isNotEmpty()) {
                    // The tiles are already on the disk (written above); the
                    // legacy PNG is no longer needed.
                    legacyFile.delete()
                }
            }
        }
        // The metadata of the page: the stack and the tile information of
        // every layer.
        val tilesInfo = s.stack.layers.associate { layer ->
            val store = s.layerTiles[layer]
            val grid = store?.grid ?: LayerTileGrid(s.inkWidth, s.inkHeight)
            layer.index to PageLayerMeta.LayerTilesInfo(
                s.inkWidth, s.inkHeight, grid.tileSize,
                store?.existingTiles()?.map { index ->
                    grid.tileName(grid.tileColumn(index), grid.tileRow(index))
                } ?: emptyList()
            )
        }
        val previewOpsSnapshot = if (s.pageType == "infinite") {
            s.history.ops.toList()
        } else {
            emptyList()
        }
        val previewStackSnapshot = if (s.pageType == "infinite") {
            s.stack
        } else {
            null
        }
        withContext(Dispatchers.IO) {
            repository.savePageLayerMeta(
                noteId, s.pageIndex, PageLayerMeta.encode(s.stack, tilesInfo)
            )
            deleteStaleLayerFiles(s)
            if (s.pageType == "infinite" && previewStackSnapshot != null) {
                generateAndSavePagePreview(s.pageIndex, previewOpsSnapshot, previewStackSnapshot)
            }
        }
        storedStacks.remove(s.pageIndex)
        storedStacks[s.pageIndex] = s.stack
        s.savedWhileActive = true
        onSaved(s.pageIndex)
    }

    /**
     * Generates and writes the thumbnail/page preview PNG for an infinite canvas page,
     * including both placed [InfiniteCanvasItem]s (images and PDF pages) and visible
     * layer strokes so the home-screen card and page drawer show imported items.
     */
    internal suspend fun generateAndSavePagePreview(
        pageIndex: Int = 0,
        ops: List<InkOp>? = null,
        stack: PageLayerStack? = null
    ) {
        try {
            val pageFile = repository.fileStore.getPageFile(noteId, pageIndex)
            val resolvedStack = stack ?: layerStack(pageIndex)
            val resolvedOps = ops ?: getSession(pageIndex)?.history?.ops?.toList() ?: run {
                val loaded = mutableListOf<InkOp>()
                for (layer in resolvedStack.layers) {
                    val lOps = repository.loadPageLayerOps(noteId, pageIndex, layer.index)
                    if (lOps != null) {
                        for (op in lOps) {
                            when (op) {
                                is InkOp.Stroke -> op.layer = layer
                                is InkOp.Erase -> op.layer = layer
                            }
                        }
                        loaded.addAll(lOps)
                    }
                }
                loaded
            }
            val pageItems = InfiniteItemsStore.load(repository.fileStore, noteId)
                .filter { item ->
                    item.pageIndex == pageIndex &&
                        (resolvedStack.layerAt(item.layerIndex)?.visible != false)
                }

            val thumbW = 360
            val thumbH = 500
            val bitmap = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var count = 0
            for (item in pageItems) {
                val hw = item.width * 0.55f
                val hh = item.height * 0.55f
                if (item.centerX - hw < minX) minX = item.centerX - hw
                if (item.centerX + hw > maxX) maxX = item.centerX + hw
                if (item.centerY - hh < minY) minY = item.centerY - hh
                if (item.centerY + hh > maxY) maxY = item.centerY + hh
                count++
            }
            for (op in resolvedOps) {
                if (op is InkOp.Stroke && resolvedStack.layerAt(op.layer.index)?.visible != false) {
                    for (p in op.stroke.points) {
                        val px = p.x * 2160f
                        val py = p.y * 2160f
                        if (px < minX) minX = px
                        if (px > maxX) maxX = px
                        if (py < minY) minY = py
                        if (py > maxY) maxY = py
                        count++
                    }
                }
            }
            if (count > 0 && minX <= maxX && minY <= maxY) {
                val strokeW = (maxX - minX).coerceAtLeast(100f)
                val strokeH = (maxY - minY).coerceAtLeast(100f)
                val padding = 32f
                val scale = kotlin.math.min(
                    (thumbW - padding * 2f) / strokeW,
                    (thumbH - padding * 2f) / strokeH
                )
                val save = canvas.save()
                canvas.translate(
                    thumbW / 2f - (minX + maxX) / 2f * scale,
                    thumbH / 2f - (minY + maxY) / 2f * scale
                )
                canvas.scale(scale, scale)

                val itemPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                val paperFill = Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = Paint.Style.FILL
                }
                val paperBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFD0D5DD.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = (1.5f / scale.coerceAtLeast(0.05f)).coerceAtLeast(1f)
                }
                val dstRect = RectF()

                for (layer in resolvedStack.layers) {
                    if (!layer.visible) continue
                    for (item in pageItems) {
                        if (item.layerIndex != layer.index) continue
                        val bmp: Bitmap? = when (item) {
                            is InfiniteCanvasItem.ImageItem -> {
                                val file = repository.fileStore.getNoteAsset(noteId, item.fileName)
                                if (file != null && file.exists()) {
                                    personal.notes.touch.util.BitmapUtils.loadThumbnail(file, 360, 360)
                                } else null
                            }
                            is InfiniteCanvasItem.PdfPageItem -> {
                                val file = repository.fileStore.getNoteAsset(noteId, item.pdfFileName)
                                if (file != null && file.exists()) {
                                    val doc = personal.notes.touch.util.PdfiumDocument.open(file)
                                    try {
                                        val tw = 320
                                        val th = (320f * (item.height / item.width.coerceAtLeast(1f))).roundToInt().coerceAtLeast(1)
                                        doc?.renderPage(item.pdfPageIndex, tw, th)
                                    } finally {
                                        doc?.close()
                                    }
                                } else null
                            }
                        }
                        val itemSave = canvas.save()
                        canvas.translate(item.centerX, item.centerY)
                        if (item.rotationDeg != 0f) {
                            canvas.rotate(item.rotationDeg)
                        }
                        if (item.flipHorizontal) {
                            canvas.scale(-1f, 1f)
                        }
                        dstRect.set(-item.width / 2f, -item.height / 2f, item.width / 2f, item.height / 2f)
                        if (item is InfiniteCanvasItem.PdfPageItem) {
                            canvas.drawRect(dstRect, paperFill)
                        }
                        if (bmp != null && !bmp.isRecycled) {
                            canvas.drawBitmap(bmp, null, dstRect, itemPaint)
                            bmp.recycle()
                        }
                        if (item is InfiniteCanvasItem.PdfPageItem) {
                            canvas.drawRect(dstRect, paperBorder)
                        }
                        canvas.restoreToCount(itemSave)
                    }

                    val layerOps = resolvedOps.filter { it.layer.index == layer.index }
                    if (layerOps.isEmpty()) continue
                    val hasErase = layerOps.any { it is InkOp.Erase }
                    val layerSave = if (hasErase) {
                        canvas.saveLayer(minX - 200f, minY - 200f, maxX + 200f, maxY + 200f, null)
                    } else {
                        -1
                    }
                    for (op in layerOps) {
                        when (op) {
                            is InkOp.Stroke -> drawInfiniteOpStroke(canvas, op)
                            is InkOp.Erase -> drawStroke(canvas, op.stroke, 2160f, 2160f, preview = false)
                        }
                    }
                    if (layerSave >= 0) {
                        canvas.restoreToCount(layerSave)
                    }
                }
                canvas.restoreToCount(save)
            }
            pageFile.parentFile?.mkdirs()
            val tempFile = java.io.File(pageFile.parentFile, "${pageFile.name}.tmp")
            java.io.FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            tempFile.renameTo(pageFile)
            bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * The saved form of one layer: its pixels and the strokes that were
     * drawn on it.
     *
     * The strokes are normalised to the box and the layer lives in its own
     * pixels, so the canvas is scaled and moved: the normalised coordinates
     * land on the layer - a stroke in the margin lands in the margin of the
     * layer and never on the page image.
     *
     * The pixels of the layer are the tiles: every tile is drawn at its own
     * place in the layer, so the result is the same picture as the one the
     * old single bitmap produced.
     *
     * Returns null when the layer is empty (nothing to write).
     */
    private fun composeLayer(s: ContinuousInkSession, layer: PageLayer): Bitmap? {
        val placements = s.tilesOf(layer)
        val ops = s.history.ops.filter { it.layer.index == layer.index }
        val hasTiles = placements.any { it.tiles.existingTiles().isNotEmpty() }
        if (!hasTiles && ops.isEmpty()) return null
        val target = Bitmap.createBitmap(s.inkWidth, s.inkHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (placement in placements) {
            val grid = placement.grid
            for (index in placement.tiles.existingTiles()) {
                val tx = grid.tileColumn(index)
                val ty = grid.tileRow(index)
                if (tx < 0 || ty < 0) continue
                val bitmap = placement.tiles.tile(tx, ty) ?: continue
                if (bitmap.isRecycled) continue
                canvas.drawBitmap(bitmap, grid.tileLeft(tx).toFloat(), grid.tileTop(ty).toFloat(), paint)
            }
        }
        drawOps(canvas, s, ops)
        return target
    }

    /**
     * Deletes the layer files of the layers that are gone.
     *
     * A deleted layer leaves its file behind: the next save would write the
     * layer again under a number that belongs to another layer.
     */
    private suspend fun deleteStaleLayerFiles(s: ContinuousInkSession) {
        val dir = repository.fileStore.getLayersDir(noteId)
        val files = dir.listFiles() ?: return
        val keep = s.stack.layers.map { it.index }.toSet()
        files.forEach { file ->
            val name = file.name
            if (!name.startsWith("page_${s.pageIndex}_annot_") || !name.endsWith(".png")) return@forEach
            val number = name.removePrefix("page_${s.pageIndex}_annot_").removeSuffix(".png")
            val index = number.toIntOrNull() ?: return@forEach
            if (!keep.contains(index)) file.delete()
        }
    }

    /**
     * Puts a layer that was written with another size into the size of this
     * session.
     *
     * The page part of the old layer lands on the page part of the new one
     * (the margins on both sides of it), so the writing keeps its place when
     * the page changed its shape.
     */
    private fun fitLayer(source: Bitmap, width: Int, height: Int): Bitmap {
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        // The page part of a layer: a fixed page's layer is a third wider
        // than its page (the margin on both sides), an infinite page's layer
        // is its page.
        val pageFraction = if (ContinuousPageGeometry.isInfinite(pageType)) 1f else 1f / 1.5f
        val srcLeft = source.width * (1f - pageFraction) / 2f
        val srcWidth = source.width * pageFraction
        val dstLeft = width * (1f - pageFraction) / 2f
        val dstWidth = width * pageFraction
        canvas.drawBitmap(
            source,
            Rect(
                srcLeft.toInt().coerceAtLeast(0),
                0,
                (srcLeft + srcWidth).toInt().coerceAtMost(source.width),
                source.height
            ),
            RectF(dstLeft, 0f, dstLeft + dstWidth, height.toFloat()),
            paint
        )
        return target
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
                        if (session !== s) {
                            releaseBitmaps(s)
                        }
                        revision++
                        onSaved(s.pageIndex)
                    }
                }
            } else {
                if (session !== s) {
                    releaseBitmaps(s)
                }
            }
            return
        }
        s.savedWhileActive = true
        closingSessions[s.pageIndex] = s
        appScope.launch {
            // The copies of the layers and the strokes drawn into them
            // happen off the main thread: the pen must not wait for them.
            try {
                persist(s)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                s.dirty = true
            }
            withContext(Dispatchers.Main) {
                closingSessions.remove(s.pageIndex)
                if (session !== s) {
                    releaseBitmaps(s)
                }
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
        s.saveJob?.cancel()
        s.loadJob?.cancel()
        if (s.dirty) {
            s.dirty = false
            appScope.launch {
                // The copies of the layers and the strokes drawn into them
                // happen off the main thread.
                try {
                    persist(s)
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    s.dirty = true
                }
                releaseBitmaps(s)
                onSaved(s.pageIndex)
            }
        } else {
            releaseBitmaps(s)
        }
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
            if (sess.dirty) {
                appScope.launch {
                    // The copies of the layers and the strokes drawn into them
                    // happen off the main thread.
                    try {
                        persist(sess)
                    } catch (e: OutOfMemoryError) {
                        e.printStackTrace()
                        sess.dirty = true
                    }
                    releaseBitmaps(sess)
                }
            } else {
                releaseBitmaps(sess)
            }
        }
    }

    private fun releaseBitmaps(s: ContinuousInkSession) {
        // Do not call .recycle() on the tiles or the background: they may
        // still be actively rendered or queued in Compose frames
        // (Image/Canvas drawImage). Clearing references allows GC and native
        // finalizers to safely reclaim them.
        s.layerTiles.clear()
        s.merged.clear()
        s.glassBitmap = null
        s.glassRevision = -1
        s.glassLayer = null
        s.opRevision = 0
        s.background = null
        // The strokes are on disk now: the reader reloads the page from the
        // file, so the committed list must not draw them a second time.
        s.history.ops.clear()
    }

    // ---- layers ---------------------------------------------------------

    /**
     * Bumped when the layer stack of a page changes (a layer was added,
     * hidden, merged or deleted).
     *
     * The reader reads this, so a hidden layer disappears from the page and
     * a new one shows up.
     */
    var layersRevision by mutableIntStateOf(0)
        private set

    /** The layer stack of the pages that were read from the disk. */
    private val storedStacks = mutableMapOf<Int, PageLayerStack>()

    /**
     * The layer stack of a page: the one of the session, or the one that is
     * on the disk.
     *
     * The base layer (layer 1) is always there; the user can add two more.
     */
    /**
     * Where the annotation layer of a page sits on the screen.
     *
     * The glass of the floating dock samples it: the layer holds the writing,
     * so the strokes disperse through the lens - the page image alone has no
     * ink in it.
     *
     * The bitmap is the small copy of the layer (the tiles composed at the
     * resolution the glass reads the page at), so the scale is the scale of
     * THAT copy.
     */
    data class LayerScreenMapping(
        val bitmap: Bitmap,
        /** The screen position of the layer's top-left corner. */
        val originX: Float,
        val originY: Float,
        /** The bitmap's pixels per screen pixel. */
        val scale: Float
    )

    /**
     * Optional callback used by the infinite canvas to render placed items
     * (imported PDF pages and images) into the glass copy before strokes.
     * Receives `(canvas, worldLeft, worldTop, pxPerUnit)`.
     */
    var infiniteBackdropDrawer: ((Canvas, Float, Float, Float) -> Unit)? = null
    var infiniteWorldBounds: (() -> FloatArray?)? = null

    /** Triggers an immediate rebuild of the active session's glass backdrop copy. */
    fun refreshGlassCopy() {
        val s = session ?: return
        s.opRevision++
        scheduleGlassCopy(s)
    }

    /**
     * The screen mapping of the annotation layer of a page, or null when the
     * page has no layer (or the layer is masked).
     */
    fun layerScreenMapping(
        pageIndex: Int,
        panX: Float,
        panY: Float,
        infiniteScale: Float = 1f
    ): LayerScreenMapping? {
        val s = getSession(pageIndex) ?: return null
        val layer = s.stack.current ?: return null
        if (!layer.visible) return null
        val bitmap = glassCopy(s, layer) ?: return null
        if (bitmap.isRecycled) return null
        if (s.pageType == "infinite") {
            val effScale = infiniteScale.coerceAtLeast(0.1f)
            val originX = panX + s.glassWorldLeft * 2160f * effScale
            val originY = panY + s.glassWorldTop * 2160f * effScale
            val scale = s.glassPxPerUnit / (2160f * effScale)
            if (!scale.isFinite() || scale <= 0f) return null
            return LayerScreenMapping(bitmap, originX, originY, scale)
        }
        val rect = s.layerRectInBox
        if (rect.empty || s.boxWidth <= 0f || s.boxHeight <= 0f) return null
        val originX = panX + rect.left * s.boxWidth
        val originY = panY + rect.top * s.boxHeight
        // The copy is a quarter of the layer, so it has a quarter of its
        // pixels per screen pixel.
        val scale = s.inkWidth / (rect.width * s.boxWidth) / GLASS_COPY_SCALE
        if (!scale.isFinite() || scale <= 0f) return null
        return LayerScreenMapping(bitmap, originX, originY, scale)
    }

    /**
     * The small copy of the layer the glass samples.
     *
     * The layer is a grid of tiles and vector operations. The glass wants the
     * writing of the layer under the dock, so the tiles and strokes are
     * composed into one small bitmap - a quarter of the size of the layer,
     * because the glass reads the page at that resolution anyway (it samples
     * a quarter size copy of the backdrop).
     *
     * The copy is built in the background when the content changes: building it
     * on the draw thread would cost tens of milliseconds.
     */
    private fun glassCopy(s: ContinuousInkSession, layer: PageLayer): Bitmap? {
        val targetRev = s.tilesRevision + s.opRevision
        val cached = s.glassBitmap
        if (cached != null && !cached.isRecycled && s.glassRevision == targetRev && s.glassLayer === layer) {
            return cached
        }
        if (s.glassPending && s.glassPendingLayer === layer && cached != null && !cached.isRecycled) {
            // A new copy of this layer is on its way: the old one is used
            // until it is there.
            return cached
        }
        // Never block the UI draw thread building the glass backdrop for infinite canvas
        // or when a previous cached copy is already available; schedule it asynchronously.
        if (s.pageType == "infinite" || (cached != null && !cached.isRecycled)) {
            scheduleGlassCopy(s)
            return cached?.takeIf { !it.isRecycled }
        }
        val store = s.layerTiles[layer]
        return buildGlassCopy(s, layer, store, targetRev)
    }

    /**
     * Builds the small copy of a layer out of its tiles and vector operations.
     */
    private fun buildGlassCopy(
        s: ContinuousInkSession,
        layer: PageLayer,
        store: LayerTiles?,
        targetRev: Int
    ): Bitmap? {
        val copy = composeGlassCopy(s, store) ?: return null
        s.glassBitmap = copy
        s.glassRevision = targetRev
        s.glassLayer = layer
        return copy
    }

    /**
     * Composes the tiles and vector operations of a layer into one small bitmap.
     *
     * The copy is a quarter of the size of the layer: the glass samples the
     * page at that resolution anyway, so nothing is lost.
     */
    private fun composeGlassCopy(s: ContinuousInkSession, store: LayerTiles?): Bitmap? {
        val layer = s.glassPendingLayer ?: s.stack.current ?: return null
        if (s.pageType == "infinite") {
            val pxPerUnit = 2160f / GLASS_COPY_SCALE // 540f isotropic pixels per 2160f world unit
            var minX = -0.15f
            var minY = -0.10f
            var maxX = 1.35f
            var maxY = 1.35f
            val itemBounds = infiniteWorldBounds?.invoke()
            if (itemBounds != null && itemBounds.size >= 4) {
                if (itemBounds[0] < minX) minX = itemBounds[0]
                if (itemBounds[1] < minY) minY = itemBounds[1]
                if (itemBounds[2] > maxX) maxX = itemBounds[2]
                if (itemBounds[3] > maxY) maxY = itemBounds[3]
            }
            for (op in s.history.ops) {
                val stroke = when (op) {
                    is InkOp.Stroke -> op.stroke
                    is InkOp.Erase -> op.stroke
                }
                for (pt in stroke.points) {
                    if (pt.x - 0.08f < minX) minX = pt.x - 0.08f
                    if (pt.y - 0.08f < minY) minY = pt.y - 0.08f
                    if (pt.x + 0.08f > maxX) maxX = pt.x + 0.08f
                    if (pt.y + 0.08f > maxY) maxY = pt.y + 0.08f
                }
            }
            val spanX = (maxX - minX).coerceIn(1.0f, 3.2f)
            val spanY = (maxY - minY).coerceIn(1.0f, 3.2f)
            val width = (spanX * pxPerUnit).roundToInt().coerceAtLeast(1)
            val height = (spanY * pxPerUnit).roundToInt().coerceAtLeast(1)
            val out = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                null
            } ?: return null
            s.glassWorldLeft = minX
            s.glassWorldTop = minY
            s.glassPxPerUnit = pxPerUnit
            val canvas = Canvas(out)
            infiniteBackdropDrawer?.invoke(canvas, minX, minY, pxPerUnit)
            for (visLayer in s.stack.layers) {
                if (!visLayer.visible) continue
                val layerOps = s.history.ops.filter { it.layer.index == visLayer.index }
                if (layerOps.isEmpty()) continue
                val hasErase = layerOps.any { it is InkOp.Erase }
                val layerSave = if (hasErase) {
                    canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                } else -1
                val save = canvas.save()
                canvas.translate(-minX * pxPerUnit, -minY * pxPerUnit)
                for (op in layerOps) {
                    when (op) {
                        is InkOp.Stroke -> {
                            val shape = op.shape
                            if (shape == null) {
                                drawStroke(canvas, op.stroke, pxPerUnit, pxPerUnit, preview = false)
                            } else {
                                val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    color = op.stroke.color
                                    strokeWidth = op.stroke.baseWidthNorm * pxPerUnit
                                }
                                val sSave = canvas.save()
                                canvas.scale(pxPerUnit / 2160f, pxPerUnit / 2160f)
                                canvas.drawPath(shape, paint2)
                                canvas.restoreToCount(sSave)
                            }
                        }
                        is InkOp.Erase -> {
                            drawStroke(canvas, op.stroke, pxPerUnit, pxPerUnit, preview = false)
                        }
                    }
                }
                canvas.restoreToCount(save)
                if (layerSave >= 0) canvas.restoreToCount(layerSave)
            }
            return out
        }
        val layerWidth = if (store != null && store.grid.width > 0) store.grid.width else s.inkWidth
        val layerHeight = if (store != null && store.grid.height > 0) store.grid.height else s.inkHeight
        if (layerWidth <= 0 || layerHeight <= 0) return null
        val width = (layerWidth / GLASS_COPY_SCALE).toInt().coerceAtLeast(1)
        val height = (layerHeight / GLASS_COPY_SCALE).toInt().coerceAtLeast(1)
        val out = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } ?: return null
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // 1. Draw vector PDF background if present
        if (s.isVector) {
            val bg = s.background
            if (bg != null && !bg.isRecycled) {
                val ratio = s.pageRatio
                val boxR = ContinuousPageGeometry.boxRatio(s.pageType, ratio)
                val page = ContinuousPageGeometry.pageRect(s.pageType, ratio, boxR, 1f)
                val layerR = ContinuousPageGeometry.layerRect(s.pageType, ratio, boxR, 1f)
                if (!layerR.empty && !page.empty) {
                    val destLeft = (page.left - layerR.left) / layerR.width * width
                    val destTop = (page.top - layerR.top) / layerR.height * height
                    val destWidth = page.width / layerR.width * width
                    val destHeight = page.height / layerR.height * height
                    canvas.drawBitmap(
                        bg,
                        null,
                        RectF(destLeft, destTop, destLeft + destWidth, destTop + destHeight),
                        paint
                    )
                }
            }
        }

        // 2. Draw existing tiles (if any)
        if (store != null) {
            val grid = store.grid
            synchronized(s.inkLock) {
                for (index in store.existingTiles()) {
                    val tx = grid.tileColumn(index)
                    val ty = grid.tileRow(index)
                    if (tx < 0 || ty < 0) continue
                    val bitmap = store.tile(tx, ty) ?: continue
                    if (bitmap.isRecycled) continue
                    val left = grid.tileLeft(tx)
                    val top = grid.tileTop(ty)
                    val right = left + grid.tileWidth(tx)
                    val bottom = top + grid.tileHeight(ty)
                    canvas.drawBitmap(
                        bitmap,
                        // The source is the tile itself (its own pixels), the
                        // destination is its place in the small picture.
                        Rect(0, 0, grid.tileWidth(tx), grid.tileHeight(ty)),
                        RectF(
                            left / grid.width.toFloat() * width,
                            top / grid.height.toFloat() * height,
                            right / grid.width.toFloat() * width,
                            bottom / grid.height.toFloat() * height
                        ),
                        paint
                    )
                }
            }
        }

        // 3. Draw committed ops for this layer
        val ops = s.history.ops.filter { it.layer.index == layer.index }
        if (ops.isNotEmpty()) {
            val ratio = s.pageRatio
            val boxR = ContinuousPageGeometry.boxRatio(s.pageType, ratio)
            val rect = ContinuousPageGeometry.layerRect(s.pageType, ratio, boxR, 1f)
            if (!rect.empty) {
                val sx = width.toFloat() / rect.width
                val sy = height.toFloat() / rect.height
                for (op in ops) {
                    when (op) {
                        is InkOp.Stroke -> {
                            val shape = op.shape
                            if (shape == null) {
                                val save = canvas.save()
                                canvas.translate(-rect.left * sx, -rect.top * sy)
                                drawStroke(canvas, op.stroke, sx, sy, preview = false)
                                canvas.restoreToCount(save)
                            } else {
                                val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    color = op.stroke.color
                                    strokeWidth = op.stroke.baseWidthNorm * width / rect.width
                                }
                                val save = canvas.save()
                                canvas.scale(width.toFloat() / layerWidth, height.toFloat() / layerHeight)
                                canvas.drawPath(shape, paint2)
                                canvas.restoreToCount(save)
                            }
                        }
                        is InkOp.Erase -> {}
                    }
                }
            }
        }

        return out
    }

    /**
     * Schedules a new small copy of the layer the glass samples.
     *
     * The copy is built off the main thread: the eraser must not wait for
     * it.
     */
    private fun scheduleGlassCopy(s: ContinuousInkSession) {
        val layer = s.stack.current ?: return
        val store = s.layerTiles[layer]
        val targetRev = s.tilesRevision + s.opRevision
        if (s.glassPending && s.glassPendingLayer === layer) return
        s.glassPending = true
        s.glassPendingLayer = layer
        scope.launch {
            val copy = withContext(Dispatchers.IO) { composeGlassCopy(s, store) }
            s.glassPending = false
            s.glassPendingLayer = null
            if (session === s || closingSessions.containsValue(s)) {
                if (copy != null) {
                    s.glassBitmap = copy
                    s.glassRevision = targetRev
                    s.glassLayer = layer
                    revision++
                }
                if (s.tilesRevision + s.opRevision != targetRev) {
                    scheduleGlassCopy(s)
                }
            }
        }
    }

    fun layerStack(pageIndex: Int): PageLayerStack {
        val s = getSession(pageIndex)
        // A session that is still loading has no layers yet: the stack of the
        // disk is the one of the page until it is there.
        if (s != null && s.ready) return s.stack
        return readStack(pageIndex)
    }

    /**
     * Makes sure the page has an ink session.
     *
     * The layer stack of a page lives in its session, so the layer panel
     * needs one: without it the panel could only read the stack of the
     * disk.
     */
    fun ensureSessionForPage(page: PageEntity, pageIndex: Int): ContinuousInkSession? {
        val existing = session
        if (existing != null && existing.pageIndex == pageIndex) return existing
        if (existing != null) flushNow(existing)
        return ensureSession(page, pageIndex, 0f, 0f, 0)
    }

    /**
     * Adds a layer to the page (three at most) and makes it the current one:
     * the writing goes on the new layer.
     */
    fun addLayer(pageIndex: Int): Boolean {
        val s = getSession(pageIndex)
        if (s == null || !s.ready) return false
        val layer = s.stack.add() ?: return false
        s.history.commitAddLayer(layer)
        s.dirty = true
        scheduleSave(s)
        revision++
        layersRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /** Makes a layer the current one: the writing goes on it. */
    fun selectLayer(pageIndex: Int, index: Int): Boolean {
        val s = getSession(pageIndex)
        if (s == null || !s.ready) return false
        if (!s.stack.select(index)) return false
        // The glass shows the writing of the current layer: it needs the
        // copy of the new one.
        scheduleGlassCopy(s)
        revision++
        layersRevision++
        return true
    }

    /**
     * Hides a layer (it is masked, not deleted) or shows it again.
     *
     * The writing of a hidden layer is not drawn on the page; it comes back
     * when the eye is pressed again.
     */
    fun setLayerVisible(pageIndex: Int, index: Int, visible: Boolean): Boolean {
        val s = getSession(pageIndex)
        if (s == null || !s.ready) return false
        if (!s.stack.setVisible(index, visible)) return false
        s.dirty = true
        scheduleSave(s)
        revision++
        layersRevision++
        return true
    }

    /**
     * Deletes a layer.
     *
     * The base layer is never deleted (it can only be hidden); the undo
     * brings a deleted layer back.
     */
    fun deleteLayer(pageIndex: Int, index: Int): Boolean {
        val s = getSession(pageIndex)
        if (s == null || !s.ready) return false
        if (index == PageLayerStack.BASE_LAYER_INDEX) return false
        val layer = s.stack.layerAt(index) ?: return false
        s.history.commitRemoveLayer(layer, merged = false)
        s.dirty = true
        scheduleSave(s)
        scheduleGlassCopy(s)
        revision++
        layersRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * Merges a layer into the base layer: the writing of the layer becomes
     * part of the base layer and the layer itself is gone.
     *
     * The undo brings the layer back with its writing.
     */
    fun mergeLayer(pageIndex: Int, index: Int): Boolean {
        val s = getSession(pageIndex)
        if (s == null || !s.ready) return false
        if (index == PageLayerStack.BASE_LAYER_INDEX) return false
        val layer = s.stack.layerAt(index) ?: return false
        val base = s.stack.layerAt(PageLayerStack.BASE_LAYER_INDEX) ?: return false
        // The pixels of the layer become part of the base layer: the base
        // draws them as its own while the merge lasts.
        val folded = s.merged.getOrPut(base) { mutableListOf() }
        if (!folded.any { it === layer }) folded.add(layer)
        s.history.commitRemoveLayer(layer, merged = true)
        s.dirty = true
        // The writing of the layer is part of the base layer now: the tiles
        // of the base layer changed.
        s.tilesRevision++
        scheduleSave(s)
        scheduleGlassCopy(s)
        revision++
        layersRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * The tiles of a layer as they are on screen (the session's pixels).
     *
     * Every placement knows where its tiles live in the layer's pixels, so
     * the reader can draw each tile where it belongs.
     */
    fun layerTiles(pageIndex: Int, layer: PageLayer): List<LayerTilePlacement> {
        val s = getSession(pageIndex) ?: return emptyList()
        return s.tilesOf(layer)
    }

    /**
     * A small preview of a layer (null when the layer is empty).
     *
     * The preview is what the layer panel shows next to the name of the
     * layer: it is scaled down, so it must not run on the main thread.
     *
     * The layer is a grid of tiles, so the preview is composed from the
     * tiles: every tile is drawn at its own place in the small bitmap.
     */
    suspend fun layerThumbnail(pageIndex: Int, layer: PageLayer): Bitmap? {
        val s = getSession(pageIndex) ?: return null
        val store = s.layerTiles[layer]
        val layerOps = s.history.ops.filter { it.layer.index == layer.index }
        val pageItems = if (s.pageType == "infinite") {
            InfiniteItemsStore.load(repository.fileStore, noteId)
                .filter { it.pageIndex == pageIndex && it.layerIndex == layer.index }
        } else {
            emptyList()
        }
        if (s.pageType == "infinite") {
            if (layerOps.isEmpty() && pageItems.isEmpty()) return null
            return try {
                val width = THUMBNAIL_SIZE
                val height = THUMBNAIL_SIZE
                val thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(thumb)
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                var count = 0
                for (item in pageItems) {
                    val hw = item.width * 0.55f
                    val hh = item.height * 0.55f
                    if (item.centerX - hw < minX) minX = item.centerX - hw
                    if (item.centerX + hw > maxX) maxX = item.centerX + hw
                    if (item.centerY - hh < minY) minY = item.centerY - hh
                    if (item.centerY + hh > maxY) maxY = item.centerY + hh
                    count++
                }
                for (op in layerOps) {
                    if (op is InkOp.Stroke) {
                        for (p in op.stroke.points) {
                            val px = p.x * 2160f
                            val py = p.y * 2160f
                            if (px < minX) minX = px
                            if (px > maxX) maxX = px
                            if (py < minY) minY = py
                            if (py > maxY) maxY = py
                            count++
                        }
                    }
                }
                if (count > 0 && minX <= maxX && minY <= maxY) {
                    val spanW = (maxX - minX).coerceAtLeast(100f)
                    val spanH = (maxY - minY).coerceAtLeast(100f)
                    val pad = 12f
                    val sc = kotlin.math.min((width - pad * 2f) / spanW, (height - pad * 2f) / spanH)
                    canvas.save()
                    canvas.translate(
                        width / 2f - (minX + maxX) / 2f * sc,
                        height / 2f - (minY + maxY) / 2f * sc
                    )
                    canvas.scale(sc, sc)
                    val itemPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    val dst = RectF()
                    for (item in pageItems) {
                        val bmp = when (item) {
                            is InfiniteCanvasItem.ImageItem -> {
                                val f = repository.fileStore.getNoteAsset(noteId, item.fileName)
                                if (f != null && f.exists()) {
                                    personal.notes.touch.util.BitmapUtils.loadThumbnail(f, 120, 120)
                                } else null
                            }
                            is InfiniteCanvasItem.PdfPageItem -> {
                                val f = repository.fileStore.getNoteAsset(noteId, item.pdfFileName)
                                if (f != null && f.exists()) {
                                    val doc = personal.notes.touch.util.PdfiumDocument.open(f)
                                    try {
                                        doc?.renderPage(item.pdfPageIndex, 120, 160)
                                    } finally {
                                        doc?.close()
                                    }
                                } else null
                            }
                        }
                        if (bmp != null && !bmp.isRecycled) {
                            val sv = canvas.save()
                            canvas.translate(item.centerX, item.centerY)
                            if (item.rotationDeg != 0f) canvas.rotate(item.rotationDeg)
                            if (item.flipHorizontal) canvas.scale(-1f, 1f)
                            dst.set(-item.width / 2f, -item.height / 2f, item.width / 2f, item.height / 2f)
                            canvas.drawBitmap(bmp, null, dst, itemPaint)
                            bmp.recycle()
                            canvas.restoreToCount(sv)
                        }
                    }
                    for (op in layerOps) {
                        if (op is InkOp.Stroke) drawInfiniteOpStroke(canvas, op)
                    }
                    canvas.restore()
                }
                thumb
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
        }
        val grid = store?.grid
        val hasTiles = store != null && grid != null && grid.width > 0 && grid.height > 0 && store.existingTiles().isNotEmpty()
        if (!hasTiles && layerOps.isEmpty()) return null
        return try {
            val layerW = if (grid != null && grid.width > 0) grid.width else s.inkWidth.coerceAtLeast(1)
            val layerH = if (grid != null && grid.height > 0) grid.height else s.inkHeight.coerceAtLeast(1)
            val width = THUMBNAIL_SIZE
            val height = (width * layerH.toFloat() / layerW).roundToInt().coerceAtLeast(1)
            val thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(thumb)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            if (hasTiles && store != null && grid != null) {
                for (index in store.existingTiles()) {
                    val tx = grid.tileColumn(index)
                    val ty = grid.tileRow(index)
                    if (tx < 0 || ty < 0) continue
                    val bitmap = store.tile(tx, ty) ?: continue
                    if (bitmap.isRecycled) continue
                    val left = grid.tileLeft(tx)
                    val top = grid.tileTop(ty)
                    val right = left + grid.tileWidth(tx)
                    val bottom = top + grid.tileHeight(ty)
                    canvas.drawBitmap(
                        bitmap,
                        // The source is the tile itself (its own pixels), the
                        // destination is its place in the small picture.
                        Rect(0, 0, grid.tileWidth(tx), grid.tileHeight(ty)),
                        RectF(
                            left / grid.width.toFloat() * width,
                            top / grid.height.toFloat() * height,
                            right / grid.width.toFloat() * width,
                            bottom / grid.height.toFloat() * height
                        ),
                        paint
                    )
                }
            }
            if (layerOps.isNotEmpty()) {
                val rect = s.layerRectInBox
                if (!rect.empty) {
                    val sx = width.toFloat() / rect.width
                    val sy = height.toFloat() / rect.height
                    for (op in layerOps) {
                        if (op is InkOp.Stroke) {
                            val save = canvas.save()
                            canvas.translate(-rect.left * sx, -rect.top * sy)
                            drawStroke(canvas, op.stroke, sx, sy, preview = false)
                            canvas.restoreToCount(save)
                        }
                    }
                }
            }
            thumb
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    // ---- drawing -------------------------------------------------------

    private fun drawInfiniteOpStroke(canvas: Canvas, op: InkOp.Stroke) {
        val shape = op.shape
        if (shape == null) {
            drawStroke(canvas, op.stroke, 2160f, 2160f, preview = false)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = op.stroke.color
            strokeWidth = op.stroke.baseWidthNorm * 2160f
        }
        canvas.drawPath(shape, paint)
    }

    /**
     * Returns the bounding-box center (x, y) in infinite-world coordinates (2160f scale)
     * of all committed strokes on [pageIndex], or null if there are no strokes.
     */
    fun computeArtworkCenter(pageIndex: Int): Pair<Float, Float>? {
        val s = getSession(pageIndex) ?: return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var count = 0
        for (op in s.history.ops) {
            if (op is InkOp.Stroke) {
                for (p in op.stroke.points) {
                    val px = p.x * 2160f
                    val py = p.y * 2160f
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (py < minY) minY = py
                    if (py > maxY) maxY = py
                    count++
                }
            }
        }
        if (count == 0) return null
        return Pair((minX + maxX) / 2f, (minY + maxY) / 2f)
    }

    /**
     * Draws all committed operations of a layer into an unconstrained infinite canvas,
     * optionally culling strokes outside the visible world bounds `[minWorldX..maxWorldX, minWorldY..maxWorldY]`.
     */
    fun drawOpsInfinite(
        canvas: Canvas,
        pageIndex: Int,
        layer: PageLayer,
        minWorldX: Float = -Float.MAX_VALUE,
        minWorldY: Float = -Float.MAX_VALUE,
        maxWorldX: Float = Float.MAX_VALUE,
        maxWorldY: Float = Float.MAX_VALUE
    ) {
        val s = getSession(pageIndex) ?: return
        val hasBounds = minWorldX < maxWorldX && minWorldY < maxWorldY
        for (op in s.history.ops) {
            if (op.layer.index != layer.index) continue
            when (op) {
                is InkOp.Stroke -> {
                    if (hasBounds && op.shape == null) {
                        val st = op.stroke
                        st.ensureBounds()
                        if (st.maxX < minWorldX || st.minX > maxWorldX ||
                            st.maxY < minWorldY || st.minY > maxWorldY
                        ) {
                            continue
                        }
                    }
                    drawInfiniteOpStroke(canvas, op)
                }
                is InkOp.Erase -> drawStroke(canvas, op.stroke, 2160f, 2160f, preview = false)
            }
        }
    }

    /**
     * Draws the live uncommitted preview stroke on the infinite canvas.
     */
    fun drawPreviewInfinite(canvas: Canvas, pageIndex: Int, scale: Float = 1f) {
        drawLassoOverlay(canvas, scale)
        if (laserPageIndex == pageIndex) {
            val comp = if (scale > 0f) 1f / scale else 1f
            drawLaser(canvas, 2160f, 2160f, comp)
        }
        val s = session ?: return
        if (s.pageIndex != pageIndex) return
        val stroke = s.live ?: return
        drawStroke(canvas, stroke, 2160f, 2160f, preview = true)
    }

    // ---- Infinite canvas lasso & arrow selection (freehand strokes) -----------------

    private var lassoOps: List<InkOp.Stroke> = emptyList()
    private var lassoPoly: List<Pair<Float, Float>> = emptyList()
    private var lassoMoveDx = 0f
    private var lassoMoveDy = 0f
    private var lassoTransformBefore: List<Triple<InkOp.Stroke, List<InkPoint>, Path?>>? = null

    /** Bumped whenever the lasso path, selection or selected strokes change. */
    var lassoRevision by mutableIntStateOf(0)

    private val lassoPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFB6443.toInt()
    }
    private val lassoFillPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x22FB6443
    }
    private val lassoHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFDC2626.toInt()
    }
    private val lassoRotateHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2563EB.toInt()
    }
    private val lassoMirrorHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF0D9488.toInt()
    }
    private val lassoCopyHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFEA580C.toInt()
    }
    private val lassoWhiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    private val lassoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFB6443.toInt()
    }
    private val lassoCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun applyStrokeSnapshots(snapshots: List<StrokeTransformSnapshot>, isUndo: Boolean) {
        for (snap in snapshots) {
            val st = snap.op.stroke
            val pts = if (isUndo) snap.beforePoints else snap.afterPoints
            st.points.clear()
            pts.forEach { st.points.add(InkPoint(it.x, it.y, it.w, it.tilt)) }
            st.cachedCount = -1
            st.boundsCount = -1
            val srcShape = if (isUndo) snap.beforeShape else snap.afterShape
            val dstShape = snap.op.shape
            if (srcShape != null && dstShape != null) {
                dstShape.set(srcShape)
            }
        }
    }

    private fun translateStroke(op: InkOp.Stroke, dxNorm: Float, dyNorm: Float) {
        val st = op.stroke
        for (i in st.points.indices) {
            val p = st.points[i]
            st.points[i] = InkPoint(p.x + dxNorm, p.y + dyNorm, p.w, p.tilt)
        }
        st.cachedCount = -1
        st.boundsCount = -1
        op.shape?.offset(dxNorm * 2160f, dyNorm * 2160f)
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]
            val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Shows the lasso loop while it is being drawn (world pixels, 2160 scale). */
    fun lassoUpdatePath(points: List<Pair<Float, Float>>) {
        lassoPoly = points.toList()
        lassoRevision++
    }

    fun lassoClear() {
        if (lassoOps.isEmpty() && lassoPoly.isEmpty()) return
        lassoOps = emptyList()
        lassoPoly = emptyList()
        lassoMoveDx = 0f
        lassoMoveDy = 0f
        lassoTransformBefore = null
        lassoRevision++
    }

    fun hasLassoSelection(): Boolean = lassoOps.isNotEmpty()

    /**
     * Selects the committed strokes of [pageIndex] with at least half of their points inside
     * the closed loop [polygon] (world pixels). Returns how many strokes were selected.
     */
    fun lassoFinish(pageIndex: Int, polygon: List<Pair<Float, Float>>): Int {
        lassoPoly = emptyList()
        lassoMoveDx = 0f
        lassoMoveDy = 0f
        lassoTransformBefore = null
        val s = getSession(pageIndex)
        if (s == null || polygon.size < 3) {
            lassoOps = emptyList()
            lassoRevision++
            return 0
        }
        val picked = mutableListOf<InkOp.Stroke>()
        for (op in s.history.ops) {
            if (op !is InkOp.Stroke || !op.layer.visible) continue
            val pts = op.stroke.points
            if (pts.isEmpty()) continue
            var inside = 0
            for (p in pts) if (pointInPolygon(p.x * 2160f, p.y * 2160f, polygon)) inside++
            if (inside * 2 >= pts.size) picked.add(op)
        }
        lassoOps = picked
        lassoRevision++
        return picked.size
    }

    /**
     * Selects the single closest stroke at world coordinates ([wx], [wy]) on [pageIndex],
     * used by the Arrow Selection tool. Returns true if a stroke was selected.
     */
    fun selectSingleStrokeAt(pageIndex: Int, wx: Float, wy: Float, scale: Float): Boolean {
        val s = getSession(pageIndex) ?: return false
        val tol = 22f / scale.coerceAtLeast(0.1f)
        val tolSq = tol * tol
        var bestOp: InkOp.Stroke? = null
        var bestDistSq = Float.MAX_VALUE
        for (op in s.history.ops.asReversed()) {
            if (op !is InkOp.Stroke || !op.layer.visible) continue
            val st = op.stroke
            st.ensureBounds()
            if (wx < st.minX * 2160f - tol || wx > st.maxX * 2160f + tol ||
                wy < st.minY * 2160f - tol || wy > st.maxY * 2160f + tol
            ) {
                continue
            }
            for (p in st.points) {
                val dx = p.x * 2160f - wx
                val dy = p.y * 2160f - wy
                val d2 = dx * dx + dy * dy
                if (d2 <= tolSq && d2 < bestDistSq) {
                    bestDistSq = d2
                    bestOp = op
                }
            }
        }
        lassoPoly = emptyList()
        lassoMoveDx = 0f
        lassoMoveDy = 0f
        lassoTransformBefore = null
        lassoOps = if (bestOp != null) listOf(bestOp) else emptyList()
        lassoRevision++
        return bestOp != null
    }

    /** Bounds of the selection in world pixels (left, top, right, bottom), or null. */
    fun lassoBounds(): android.graphics.RectF? {
        if (lassoOps.isEmpty()) return null
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (op in lassoOps) {
            val st = op.stroke
            st.ensureBounds()
            if (st.minX * 2160f < l) l = st.minX * 2160f
            if (st.minY * 2160f < t) t = st.minY * 2160f
            if (st.maxX * 2160f > r) r = st.maxX * 2160f
            if (st.maxY * 2160f > b) b = st.maxY * 2160f
        }
        return android.graphics.RectF(l, t, r, b)
    }

    fun lassoContains(wx: Float, wy: Float): Boolean = lassoBounds()?.contains(wx, wy) == true

    private fun mirrorHandlePos(b: android.graphics.RectF, scale: Float): Pair<Float, Float> {
        val inv = 1f / scale.coerceAtLeast(0.1f)
        val gap = (LASSO_HANDLE_RADIUS_PX * 2.4f) * inv
        val mx = (b.left + gap).coerceAtMost(b.centerX().coerceAtLeast(b.left + gap * 0.5f))
        return mx to b.top
    }

    internal fun hitTestLassoHandle(wx: Float, wy: Float, scale: Float): LassoHandle {
        val b = lassoBounds() ?: return LassoHandle.NONE
        val inv = 1f / scale.coerceAtLeast(0.1f)
        val hitR = LASSO_HANDLE_RADIUS_PX * 1.8f * inv
        val hitR2 = hitR * hitR
        fun near(hx: Float, hy: Float): Boolean {
            val dx = wx - hx
            val dy = wy - hy
            return dx * dx + dy * dy <= hitR2
        }
        // Corner 2: Top-Right -> Cut / Delete
        if (near(b.right, b.top)) return LassoHandle.DELETE
        // Corner 1: Top-Left -> Rotate
        if (near(b.left, b.top)) return LassoHandle.ROTATE
        // Beside Rotate -> Mirror
        val (mx, my) = mirrorHandlePos(b, scale)
        if (near(mx, my)) return LassoHandle.MIRROR
        // Corner 3: Bottom-Left -> Copy (tap & drag)
        if (near(b.left, b.bottom)) return LassoHandle.COPY
        // Corner 4: Bottom-Right -> Free 1:1 Scale
        if (near(b.right, b.bottom)) return LassoHandle.SCALE_CORNER_BR
        // Edge midpoints -> single-axis stretch/squeeze
        if (near(b.left, b.centerY())) return LassoHandle.SCALE_LEFT
        if (near(b.right, b.centerY())) return LassoHandle.SCALE_RIGHT
        if (near(b.centerX(), b.top)) return LassoHandle.SCALE_TOP
        if (near(b.centerX(), b.bottom)) return LassoHandle.SCALE_BOTTOM
        if (b.contains(wx, wy)) return LassoHandle.MOVE
        return LassoHandle.NONE
    }

    /** Captures before-snapshots for a non-translation transform (rotate / scale). */
    fun lassoBeginTransform() {
        if (lassoOps.isEmpty()) return
        lassoTransformBefore = lassoOps.map { op ->
            Triple(
                op,
                op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) },
                op.shape?.let { Path(it) }
            )
        }
    }

    /** Rotates selected strokes by [deltaRad] around ([pivotX], [pivotY]) in world px. */
    fun lassoRotateTo(pageIndex: Int, deltaRad: Float, pivotX: Float, pivotY: Float) {
        val snap = lassoTransformBefore ?: return
        val cos = kotlin.math.cos(deltaRad.toDouble()).toFloat()
        val sin = kotlin.math.sin(deltaRad.toDouble()).toFloat()
        val pxNorm = pivotX / 2160f
        val pyNorm = pivotY / 2160f
        val matrix = android.graphics.Matrix().apply {
            setRotate(Math.toDegrees(deltaRad.toDouble()).toFloat(), pivotX, pivotY)
        }
        for ((op, beforePts, beforeShape) in snap) {
            val st = op.stroke
            st.points.clear()
            for (p in beforePts) {
                val dx = p.x - pxNorm
                val dy = p.y - pyNorm
                val rx = pxNorm + dx * cos - dy * sin
                val ry = pyNorm + dx * sin + dy * cos
                st.points.add(InkPoint(rx, ry, p.w, p.tilt))
            }
            st.cachedCount = -1
            st.boundsCount = -1
            if (beforeShape != null && op.shape != null) {
                op.shape.set(beforeShape)
                op.shape.transform(matrix)
            }
        }
        getSession(pageIndex)?.let { it.opRevision++ }
        revision++
        lassoRevision++
    }

    /** Scales selected strokes around ([anchorX], [anchorY]) in world px. */
    fun lassoScaleTo(pageIndex: Int, scaleX: Float, scaleY: Float, anchorX: Float, anchorY: Float) {
        val snap = lassoTransformBefore ?: return
        val sx = scaleX.coerceIn(0.15f, 8.0f)
        val sy = scaleY.coerceIn(0.15f, 8.0f)
        val axNorm = anchorX / 2160f
        val ayNorm = anchorY / 2160f
        val matrix = android.graphics.Matrix().apply {
            setScale(sx, sy, anchorX, anchorY)
        }
        for ((op, beforePts, beforeShape) in snap) {
            val st = op.stroke
            st.points.clear()
            for (p in beforePts) {
                val nx = axNorm + (p.x - axNorm) * sx
                val ny = ayNorm + (p.y - ayNorm) * sy
                st.points.add(InkPoint(nx, ny, p.w, p.tilt))
            }
            st.cachedCount = -1
            st.boundsCount = -1
            if (beforeShape != null && op.shape != null) {
                op.shape.set(beforeShape)
                op.shape.transform(matrix)
            }
        }
        getSession(pageIndex)?.let { it.opRevision++ }
        revision++
        lassoRevision++
    }

    /** Commits the active snapshot transform (rotate / scale) into undo history. */
    fun lassoCommitTransform(pageIndex: Int) {
        val snap = lassoTransformBefore ?: return
        lassoTransformBefore = null
        val s = getSession(pageIndex) ?: return
        val records = snap.map { (op, beforePts, beforeShape) ->
            StrokeTransformSnapshot(
                op = op,
                beforePoints = beforePts,
                afterPoints = op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) },
                beforeShape = beforeShape,
                afterShape = op.shape?.let { Path(it) }
            )
        }
        s.history.commitTransform(records)
        s.dirty = true
        s.opRevision++
        scheduleSave(s)
        revision++
        lassoRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
    }

    /** Mirrors the selected strokes horizontally across their bounding-box center X. */
    fun lassoMirrorHorizontal(pageIndex: Int): Boolean {
        val b = lassoBounds() ?: return false
        val s = getSession(pageIndex) ?: return false
        val cx = b.centerX()
        val cxNorm = cx / 2160f
        val matrix = android.graphics.Matrix().apply { setScale(-1f, 1f, cx, b.centerY()) }
        val records = lassoOps.map { op ->
            val beforePts = op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }
            val beforeShape = op.shape?.let { Path(it) }
            val st = op.stroke
            for (i in st.points.indices) {
                val p = st.points[i]
                st.points[i] = InkPoint(2f * cxNorm - p.x, p.y, p.w, p.tilt)
            }
            st.cachedCount = -1
            st.boundsCount = -1
            op.shape?.transform(matrix)
            StrokeTransformSnapshot(
                op = op,
                beforePoints = beforePts,
                afterPoints = op.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) },
                beforeShape = beforeShape,
                afterShape = op.shape?.let { Path(it) }
            )
        }
        s.history.commitTransform(records)
        s.dirty = true
        s.opRevision++
        scheduleSave(s)
        revision++
        lassoRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * Clones the currently selected strokes, adds the copies to [pageIndex],
     * switches the selection to the copies, and prepares them for immediate drag.
     */
    fun lassoStartCopy(pageIndex: Int): Boolean {
        val s = getSession(pageIndex) ?: return false
        if (lassoOps.isEmpty()) return false
        val copies = lassoOps.map { src ->
            val st = ContinuousStroke(
                tool = src.stroke.tool,
                color = src.stroke.color,
                baseWidthNorm = src.stroke.baseWidthNorm,
                autoShapeEnabled = src.stroke.autoShapeEnabled,
                points = src.stroke.points.map { InkPoint(it.x, it.y, it.w, it.tilt) }.toMutableList()
            )
            InkOp.Stroke(st, src.shape?.let { Path(it) }, src.layer)
        }
        s.history.ops.addAll(copies)
        s.history.commitAddBatch(copies)
        lassoOps = copies
        lassoMoveDx = 0f
        lassoMoveDy = 0f
        s.dirty = true
        s.opRevision++
        scheduleSave(s)
        revision++
        lassoRevision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /** Moves the selected strokes live by a world-pixel delta; [lassoCommitMove] makes it undoable. */
    fun lassoMove(pageIndex: Int, dxWorld: Float, dyWorld: Float) {
        if (lassoOps.isEmpty()) return
        val dx = dxWorld / 2160f
        val dy = dyWorld / 2160f
        lassoOps.forEach { translateStroke(it, dx, dy) }
        lassoMoveDx += dx
        lassoMoveDy += dy
        getSession(pageIndex)?.let { it.opRevision++ }
        revision++
        lassoRevision++
    }

    fun lassoCommitMove(pageIndex: Int) {
        val s = getSession(pageIndex) ?: return
        if (lassoOps.isEmpty() || (lassoMoveDx == 0f && lassoMoveDy == 0f)) return
        s.history.commitMove(lassoOps, lassoMoveDx, lassoMoveDy)
        lassoMoveDx = 0f
        lassoMoveDy = 0f
        s.dirty = true
        s.opRevision++
        scheduleSave(s)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
    }

    /** Deletes the selected strokes, with undo. */
    fun lassoDelete(pageIndex: Int): Boolean {
        val s = getSession(pageIndex) ?: return false
        if (lassoOps.isEmpty()) return false
        val removed = mutableListOf<OpEntry>()
        s.history.ops.forEachIndexed { idx, op ->
            if (lassoOps.any { it === op }) removed.add(OpEntry(idx, op))
        }
        lassoOps = emptyList()
        lassoRevision++
        if (removed.isEmpty()) return false
        s.history.commitClear(emptyList(), removed)
        s.dirty = true
        s.opRevision++
        scheduleSave(s)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * Draws the lasso loop, the selection frame, edge resize pills, Corner 4 (free 1:1 scale),
     * Corner 1 (Rotate), Mirror beside Rotate, Corner 2 (Cut), and Corner 3 (Copy).
     */
    fun drawLassoOverlay(canvas: Canvas, scale: Float) {
        val inv = if (scale > 0f) 1f / scale else 1f
        val poly = lassoPoly
        if (poly.size > 1) {
            val path = Path()
            path.moveTo(poly[0].first, poly[0].second)
            for (i in 1 until poly.size) path.lineTo(poly[i].first, poly[i].second)
            lassoPathPaint.strokeWidth = 3f * inv
            lassoPathPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f * inv, 10f * inv), 0f)
            canvas.drawPath(path, lassoPathPaint)
            path.close()
            canvas.drawPath(path, lassoFillPaint2)
        }
        val b = lassoBounds() ?: return
        lassoPathPaint.strokeWidth = 3f * inv
        lassoPathPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f * inv, 10f * inv), 0f)
        canvas.drawRect(b, lassoPathPaint)

        val r = LASSO_HANDLE_RADIUS_PX * inv
        val k = r * 0.42f
        lassoCrossPaint.strokeWidth = 2.8f * inv
        lassoBorderPaint.strokeWidth = 2.5f * inv

        // Edge midpoint resize pills (axis-constrained stretch/squeeze)
        val pillLong = r * 0.9f
        val pillShort = r * 0.34f
        fun drawPill(cx: Float, cy: Float, horizontal: Boolean) {
            val hw = if (horizontal) pillLong else pillShort
            val hh = if (horizontal) pillShort else pillLong
            val rect = android.graphics.RectF(cx - hw, cy - hh, cx + hw, cy + hh)
            canvas.drawRoundRect(rect, pillShort, pillShort, lassoWhiteFillPaint)
            canvas.drawRoundRect(rect, pillShort, pillShort, lassoBorderPaint)
        }
        drawPill(b.centerX(), b.top, true)
        drawPill(b.centerX(), b.bottom, true)
        drawPill(b.left, b.centerY(), false)
        drawPill(b.right, b.centerY(), false)

        // Corner 4 (Bottom-Right): free 1:1 proportional scale handle
        val c4r = r * 0.65f
        canvas.drawCircle(b.right, b.bottom, c4r, lassoWhiteFillPaint)
        canvas.drawCircle(b.right, b.bottom, c4r, lassoBorderPaint)

        // Corner 1 (Top-Left): Rotate handle
        canvas.drawCircle(b.left, b.top, r, lassoRotateHandlePaint)
        val arc = android.graphics.RectF(b.left - k, b.top - k, b.left + k, b.top + k)
        canvas.drawArc(arc, -40f, 260f, false, lassoCrossPaint)
        canvas.drawLine(b.left + k * 0.55f, b.top - k * 0.85f, b.left + k * 0.95f, b.top - k * 0.25f, lassoCrossPaint)

        // Beside Rotate: Mirror horizontal flip button
        val (mx, my) = mirrorHandlePos(b, scale)
        canvas.drawCircle(mx, my, r, lassoMirrorHandlePaint)
        canvas.drawLine(mx - k, my, mx + k, my, lassoCrossPaint)
        canvas.drawLine(mx - k, my, mx - k * 0.45f, my - k * 0.45f, lassoCrossPaint)
        canvas.drawLine(mx - k, my, mx - k * 0.45f, my + k * 0.45f, lassoCrossPaint)
        canvas.drawLine(mx + k, my, mx + k * 0.45f, my - k * 0.45f, lassoCrossPaint)
        canvas.drawLine(mx + k, my, mx + k * 0.45f, my + k * 0.45f, lassoCrossPaint)

        // Corner 2 (Top-Right): Cut / Delete (X)
        canvas.drawCircle(b.right, b.top, r, lassoHandlePaint)
        canvas.drawLine(b.right - k, b.top - k, b.right + k, b.top + k, lassoCrossPaint)
        canvas.drawLine(b.right - k, b.top + k, b.right + k, b.top - k, lassoCrossPaint)

        // Corner 3 (Bottom-Left): Copy (+)
        canvas.drawCircle(b.left, b.bottom, r, lassoCopyHandlePaint)
        canvas.drawLine(b.left - k * 0.85f, b.bottom, b.left + k * 0.85f, b.bottom, lassoCrossPaint)
        canvas.drawLine(b.left, b.bottom - k * 0.85f, b.left, b.bottom + k * 0.85f, lassoCrossPaint)
    }

    /**
     * Clears all strokes on the current layer of the given page, with undo support.
     * Implements Quick Clear from Concepts analysis §2.9.
     */
    fun clearCurrentLayer(pageIndex: Int): Boolean {
        val s = getSession(pageIndex) ?: return false
        val layer = s.stack.current ?: return false
        val removed = mutableListOf<OpEntry>()
        s.history.ops.forEachIndexed { idx, op ->
            if (op.layer.index == layer.index) removed.add(OpEntry(idx, op))
        }
        if (removed.isEmpty()) return false
        s.history.commitClear(emptyList(), removed)
        s.dirty = true
        s.opRevision++
        scheduleSave(s)
        revision++
        onUndoRedoChanged(s.canUndo, s.canRedo)
        return true
    }

    /**
     * Draws the committed strokes of one layer of a page into the page box.
     *
     * They are drawn as paths, with exactly the code the live preview uses,
     * so a finished stroke looks the same as the one the user just drew -
     * and the session bitmap is never touched, so the GPU never has to
     * take the whole layer again.
     *
     * Every layer is drawn on its own, so the eraser of a layer never cuts
     * the writing of the layer below it.
     */
    fun drawLayerOps(
        canvas: Canvas,
        pageIndex: Int,
        layer: PageLayer,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val s = getSession(pageIndex) ?: return
        for (op in s.history.ops) {
            if (op.layer.index != layer.index) continue
            when (op) {
                is InkOp.Stroke -> drawOpStroke(canvas, op, s, viewWidth, viewHeight)
                // The eraser clears pixels: the reader draws it into an
                // offscreen layer, so the wipe never reaches the page image.
                is InkOp.Erase -> drawStroke(canvas, op.stroke, viewWidth, viewHeight, preview = false)
            }
        }
    }

    /** The committed operations of one layer of a page. */
    internal fun layerOps(pageIndex: Int, layer: PageLayer): List<InkOp> {
        val s = getSession(pageIndex) ?: return emptyList()
        return s.history.ops.filter { it.layer.index == layer.index }
    }

    /**
     * Composes all visible continuous-mode layers (tiles + vector ops JSON) of [pageIndex]
     * into a single transparent [targetWidth]x[targetHeight] overlay for Single-Page mode.
     *
     * For A4 pages (`marginFor("a4") == 0f`), continuous strokes live directly in the
     * normalized `[0..1, 0..1]` box of the A4 page itself (`2160 x 3056`), so they map
     * 1:1 onto `[0..targetWidth, 0..targetHeight]` with zero horizontal cropping or skew!
     * For PDF pages (`marginFor("pdf") == 1f`), continuous layers are `3x` page width
     * (`1.0f` left margin + `1.0f` page + `1.0f` right margin), so the center `1/3`
     * (`1/3..2/3`) is mapped to the page rect.
     */
    suspend fun composeAllLayersForSinglePage(
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (targetWidth <= 0 || targetHeight <= 0) return@withContext null
        val stack = layerStack(pageIndex)
        val visibleLayers = stack.layers.filter { it.visible }
        if (visibleLayers.isEmpty()) return@withContext null

        val margin = ContinuousPageGeometry.marginFor(pageType)
        val layerSpanFactor = 1f + 2f * margin
        val composeWidth = (targetWidth * layerSpanFactor).roundToInt().coerceAtLeast(1)

        val composedLayers = mutableListOf<Bitmap>()
        for (layer in visibleLayers) {
            val bmp = composeDisplayLayer(pageIndex, layer, composeWidth)
            if (bmp != null) composedLayers.add(bmp)
        }
        if (composedLayers.isEmpty()) return@withContext null

        val target = personal.notes.touch.util.BitmapUtils.createSafeBitmap(targetWidth, targetHeight)
        val canvas = Canvas(target)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val dst = Rect(0, 0, targetWidth, targetHeight)
        val leftFrac = margin / layerSpanFactor
        val rightFrac = (margin + 1f) / layerSpanFactor
        for (layerBmp in composedLayers) {
            val srcLeft = (layerBmp.width * leftFrac).roundToInt().coerceIn(0, layerBmp.width)
            val srcRight = (layerBmp.width * rightFrac).roundToInt().coerceIn(srcLeft, layerBmp.width)
            val src = Rect(srcLeft, 0, srcRight, layerBmp.height)
            canvas.drawBitmap(layerBmp, src, dst, paint)
            layerBmp.recycle()
        }
        target
    }

    /**
     * Composes the display bitmap of a layer from the files on the disk.
     *
     * The tiles are composed into a downsampled bitmap (one tile at a
     * time, so no 80 MB allocation), and the ops of the layer are drawn
     * on top of them. Used by the reader when there is no session (the
     * user only reads).
     */
    suspend fun composeDisplayLayer(
        pageIndex: Int,
        layer: PageLayer,
        displayWidthPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val metaText = try {
            val f = repository.fileStore.getAnnotationMetaFile(noteId, pageIndex)
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            null
        }
        val tilesInfo = metaText?.let { PageLayerMeta.decodeTiles(it)[layer.index] }
        val ops = repository.loadPageLayerOps(noteId, pageIndex, layer.index)
        val hasOps = !ops.isNullOrEmpty()
        val hasTiles = tilesInfo != null && tilesInfo.tiles.isNotEmpty()
        val legacyFile = repository.fileStore.getAnnotationLayerFile(noteId, pageIndex, layer.index)
        val hasLegacy = legacyFile.exists()

        if (!hasOps && !hasTiles && !hasLegacy) return@withContext null

        val layerWidth: Int
        val layerHeight: Int
        val tiles: List<Pair<Pair<Int, Int>, Bitmap>>
        if (hasTiles && tilesInfo != null) {
            layerWidth = tilesInfo.width
            layerHeight = tilesInfo.height
            tiles = repository.loadPageLayerTiles(noteId, pageIndex, layer.index, tilesInfo)
        } else if (hasLegacy) {
            val bitmap = decodeLayerBitmap(legacyFile)
            if (bitmap != null) {
                layerWidth = bitmap.width
                layerHeight = bitmap.height
                tiles = listOf(Pair(Pair(0, 0), bitmap))
            } else {
                tiles = emptyList()
                val ratio = rasterPageRatio(pageIndex)
                val size = if (tilesInfo != null && tilesInfo.width > 0 && tilesInfo.height > 0) {
                    ContinuousPageGeometry.LayerSize(tilesInfo.width, tilesInfo.height)
                } else {
                    ContinuousPageGeometry.layerSize(pageType, ratio)
                }
                layerWidth = size.width
                layerHeight = size.height
            }
        } else {
            tiles = emptyList()
            val ratio = rasterPageRatio(pageIndex)
            val size = if (tilesInfo != null && tilesInfo.width > 0 && tilesInfo.height > 0) {
                ContinuousPageGeometry.LayerSize(tilesInfo.width, tilesInfo.height)
            } else {
                ContinuousPageGeometry.layerSize(pageType, ratio)
            }
            layerWidth = size.width
            layerHeight = size.height
        }
        if (layerWidth <= 0 || layerHeight <= 0) return@withContext null
        val displayHeight = (displayWidthPx * layerHeight / layerWidth).coerceAtLeast(1)
        val out = try {
            Bitmap.createBitmap(displayWidthPx, displayHeight, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } ?: return@withContext null
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Draw the tiles.
        for ((pos, bitmap) in tiles) {
            if (bitmap.isRecycled) continue
            val (tx, ty) = pos
            val tileSize = tilesInfo?.tileSize ?: layerWidth
            val left = tx * tileSize
            val top = ty * tileSize
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(
                left.toFloat() / layerWidth * displayWidthPx,
                top.toFloat() / layerHeight * displayHeight,
                (left + bitmap.width).toFloat() / layerWidth * displayWidthPx,
                (top + bitmap.height).toFloat() / layerHeight * displayHeight
            )
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            bitmap.recycle()
        }
        // Draw the ops (the strokes and the erases as vectors).
        if (!ops.isNullOrEmpty()) {
            if (ContinuousPageGeometry.isInfinite(pageType)) {
                val hasErase = ops.any { it is InkOp.Erase }
                val save = if (hasErase) {
                    canvas.saveLayer(0f, 0f, displayWidthPx.toFloat(), displayHeight.toFloat(), null)
                } else {
                    -1
                }
                for (op in ops) {
                    when (op) {
                        is InkOp.Stroke -> {
                            val shape = op.shape
                            if (shape == null) {
                                drawStroke(canvas, op.stroke, displayWidthPx.toFloat(), displayHeight.toFloat(), preview = false)
                            } else {
                                val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    color = op.stroke.color
                                    strokeWidth = op.stroke.baseWidthNorm * displayWidthPx
                                }
                                val sSave = canvas.save()
                                canvas.scale(displayWidthPx.toFloat() / 2160f, displayHeight.toFloat() / 2160f)
                                canvas.drawPath(shape, paint2)
                                canvas.restoreToCount(sSave)
                            }
                        }
                        is InkOp.Erase -> {
                            drawStroke(canvas, op.stroke, displayWidthPx.toFloat(), displayHeight.toFloat(), preview = false)
                        }
                    }
                }
                if (save >= 0) {
                    canvas.restoreToCount(save)
                }
            } else {
                val ratio = rasterPageRatio(pageIndex)
                val boxRatio = ContinuousPageGeometry.boxRatio(pageType, ratio)
                val rect = ContinuousPageGeometry.layerRect(pageType, ratio, boxRatio, 1f)
                if (!rect.empty) {
                    val sx = displayWidthPx.toFloat() / rect.width
                    val sy = displayHeight.toFloat() / rect.height
                    val hasErase = ops.any { it is InkOp.Erase }
                    val eraseSave = if (hasErase) {
                        canvas.saveLayer(0f, 0f, displayWidthPx.toFloat(), displayHeight.toFloat(), null)
                    } else {
                        -1
                    }
                    for (op in ops) {
                        when (op) {
                            is InkOp.Stroke -> {
                                val shape = op.shape
                                if (shape == null) {
                                    val save = canvas.save()
                                    canvas.translate(-rect.left * sx, -rect.top * sy)
                                    drawStroke(canvas, op.stroke, sx, sy, preview = false)
                                    canvas.restoreToCount(save)
                                } else {
                                    val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                        color = op.stroke.color
                                        strokeWidth = op.stroke.baseWidthNorm * displayWidthPx / rect.width
                                    }
                                    val save = canvas.save()
                                    canvas.scale(displayWidthPx.toFloat() / layerWidth, displayHeight.toFloat() / layerHeight)
                                    canvas.drawPath(shape, paint2)
                                    canvas.restoreToCount(save)
                                }
                            }
                            is InkOp.Erase -> {
                                val save = canvas.save()
                                canvas.translate(-rect.left * sx, -rect.top * sy)
                                drawStroke(canvas, op.stroke, sx, sy, preview = false)
                                canvas.restoreToCount(save)
                            }
                        }
                    }
                    if (eraseSave >= 0) {
                        canvas.restoreToCount(eraseSave)
                    }
                }
            }
        }
        return@withContext out
    }

    /**
     * Composes the display bitmaps of all the layers of a page from the
     * files on the disk. Used by the reader when there is no session (the
     * user only reads).
     */
    suspend fun composeDisplayLayers(
        pageIndex: Int,
        displayWidthPx: Int
    ): Map<Int, Bitmap?> {
        val result = LinkedHashMap<Int, Bitmap?>()
        try {
            val metaFile = repository.fileStore.getAnnotationMetaFile(noteId, pageIndex)
            val text = if (metaFile.exists()) metaFile.readText() else null
            val stack = text?.let { PageLayerMeta.decode(it) } ?: PageLayerStack.base()
            for (layer in stack.layers) {
                if (!layer.visible) continue
                result[layer.index] = composeDisplayLayer(pageIndex, layer, displayWidthPx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * True when the committed operations of a layer contain an eraser. The
     * reader needs an offscreen layer for those: a CLEAR stroke must not
     * reach the page image (the PDF text) under it.
     */
    internal fun layerHasErase(pageIndex: Int, layer: PageLayer): Boolean {
        val s = getSession(pageIndex) ?: return false
        return s.history.ops.any { it.layer.index == layer.index && it is InkOp.Erase }
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
        val rect = s.layerRectInBox
        if (shape == null || rect.empty || s.inkWidth <= 0 || s.inkHeight <= 0) {
            drawStroke(canvas, op.stroke, viewWidth, viewHeight, preview = false)
            return
        }
        // The auto-shape path lives in the layer's pixels: the canvas is
        // scaled and moved so the path lands on the layer inside the box.
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
     * Draws the committed operations into the annotation layer (the layer's
     * pixels). Used when the page is saved: the strokes are rasterised into a
     * copy of the layer, once, off the main thread.
     *
     * The strokes are normalised to the box and the layer lives in its own
     * pixels, so the canvas is scaled and moved: the normalised coordinates
     * land on the layer - a stroke in the margin lands in the margin of the
     * layer and never on the page image.
     */
    private fun drawOps(canvas: Canvas, s: ContinuousInkSession, ops: List<InkOp>) {
        val rect = s.layerRectInBox
        if (rect.empty) return
        val sx = s.inkWidth / rect.width
        val sy = s.inkHeight / rect.height
        for (op in ops) {
            when (op) {
                is InkOp.Stroke -> {
                    val shape = op.shape
                    if (shape == null) {
                        val save = canvas.save()
                        canvas.translate(-rect.left * sx, -rect.top * sy)
                        drawStroke(canvas, op.stroke, sx, sy, preview = false)
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
                    canvas.translate(-rect.left * sx, -rect.top * sy)
                    drawStroke(canvas, op.stroke, sx, sy, preview = false)
                    canvas.restoreToCount(save)
                }
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

    /** The red laser dot with a smooth fading gradient tail. */
    private fun drawLaser(
        canvas: Canvas,
        viewWidth: Float,
        viewHeight: Float,
        scaleCompensation: Float = 1f
    ) {
        if (!laserActive || laserTrail.isEmpty()) return
        val now = System.currentTimeMillis()
        while (laserTrail.isNotEmpty() && now - laserTrail.first().time > LASER_TRAIL_MS) {
            laserTrail.removeAt(0)
        }
        if (laserTrail.isEmpty()) return
        val unitScale = (viewWidth / 1600f).coerceAtLeast(0.6f) * scaleCompensation
        val strokeW = 6.5f * unitScale
        if (laserTrail.size >= 2) {
            val path = Path()
            val p0 = laserTrail[0]
            val p0x = p0.x * viewWidth
            val p0y = p0.y * viewHeight
            path.moveTo(p0x, p0y)
            if (laserTrail.size == 2) {
                val p1 = laserTrail[1]
                path.lineTo(p1.x * viewWidth, p1.y * viewHeight)
            } else {
                for (i in 1 until laserTrail.size - 1) {
                    val cur = laserTrail[i]
                    val next = laserTrail[i + 1]
                    val cx = cur.x * viewWidth
                    val cy = cur.y * viewHeight
                    val mx = (cur.x + next.x) * 0.5f * viewWidth
                    val my = (cur.y + next.y) * 0.5f * viewHeight
                    path.quadTo(cx, cy, mx, my)
                }
                val last = laserTrail.last()
                path.lineTo(last.x * viewWidth, last.y * viewHeight)
            }
            val tail = laserTrail.first()
            val head = laserTrail.last()
            val tx = tail.x * viewWidth
            val ty = tail.y * viewHeight
            val hx = head.x * viewWidth
            val hy = head.y * viewHeight
            val tailAge = (now - tail.time).toFloat().coerceAtMost(LASER_TRAIL_MS.toFloat())
            val tailAlpha = ((1f - tailAge / LASER_TRAIL_MS) * 60f).toInt().coerceIn(0, 60)
            val tailColor = (tailAlpha shl 24) or 0x00FF3B30
            val headColor = 0xC8FF3B30.toInt()
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = strokeW
                if (kotlin.math.hypot((hx - tx).toDouble(), (hy - ty).toDouble()) > 1.0) {
                    shader = android.graphics.LinearGradient(
                        tx, ty, hx, hy,
                        tailColor, headColor,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                } else {
                    color = headColor
                }
            }
            canvas.drawPath(path, glowPaint)
        }
        val head = laserTrail.last()
        val hx = head.x * viewWidth
        val hy = head.y * viewHeight
        val radius = 7.5f * unitScale
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x44FF3B30
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFF3B30.toInt()
        }
        canvas.drawCircle(hx, hy, radius * 1.9f, halo)
        canvas.drawCircle(hx, hy, radius, dot)
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
                // Concepts-style 3-layer concentric variable-width graphite ribbon:
                // zero vertex beading, fibrous stippled edges, and tilt-modulated core.
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

                // 1. Outer fibrous paper-tooth halo (catches high paper peaks at edges)
                paint.shader = PencilGrainShader.shaderEdge
                paint.alpha = (baseAlpha * (0.28f + 0.16f * avgTilt)).roundToInt().coerceIn(10, 255)
                canvas.drawPath(pathEdge, paint)

                // 2. Mid graphite body (textured tooth with transparent valleys)
                paint.shader = PencilGrainShader.shaderMid
                paint.alpha = (baseAlpha * (0.58f - 0.18f * avgTilt)).roundToInt().coerceIn(18, 255)
                canvas.drawPath(pathMid, paint)

                // 3. Inner graphite core (crisp when upright, dissolves into soft wash when tilted)
                val coreAlpha = (baseAlpha * (0.78f - 0.52f * avgTilt)).roundToInt()
                if (coreAlpha >= 14 && pathCore != null) {
                    paint.shader = PencilGrainShader.shaderCore
                    paint.alpha = coreAlpha.coerceIn(14, 255)
                    canvas.drawPath(pathCore, paint)
                }
            }
            CanvasTool.SHAPE_FILL -> {
                // Concepts-style Draw + Fill: dynamically closes from current endpoint to start point
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

    private companion object {
        /** How long the laser tail stays visible, in milliseconds. */
        const val LASER_TRAIL_MS = 700L
        /**
         * The size of the copy of the layer the glass samples, as a fraction
         * of the layer.
         *
         * The glass reads the page at a quarter of its resolution anyway (it
         * samples a quarter size copy of the backdrop), so the copy of the
         * layer is built at that size: nothing is lost and the layer itself
         * (80 MB) is not in memory.
         */
        const val GLASS_COPY_SCALE = 4f
        /** ~48 MB of patches: a few dozen ordinary strokes, bounded. */
        const val MAX_UNDO_BYTES = 48L * 1024L * 1024L
        /** The width of the preview of a layer in the layer panel. */
        const val THUMBNAIL_SIZE = 120
    }
}
