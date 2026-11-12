package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * The tiles of one layer and the grid they live in.
 *
 * A layer normally holds one store (the tiles of the layer file of the
 * page); a layer that was merged into the base layer keeps its own store,
 * and the base layer draws it as part of itself.
 */
data class LayerTilePlacement(
    /** The tiles of the layer. */
    val tiles: LayerTiles,
    /** The geometry of the tiles (where they are in the layer's pixels). */
    val grid: LayerTileGrid
)

/**
 * The tiles of an annotation layer.
 *
 * The layer is not one bitmap any more (audit C50): it is a grid of tiles
 * (see [LayerTileGrid]), each of them a small bitmap that holds only the
 * part of the layer the user actually wrote on. A tile that is written on
 * costs 4 MB of pixels, a tile that is empty costs nothing at all - and an
 * erased tile is given back to the system as soon as it is empty again.
 *
 * The store knows where the tiles are ([LayerTileGrid]) and holds the pixels;
 * it does not know the disk (that is the file store and the repository) and
 * it does not know about strokes (that is the ink engine).
 *
 * Everything here is safe: a rectangle that lies outside the layer is
 * clipped, a tile that does not exist is created on demand, and nothing
 * throws.
 */
class LayerTiles(val grid: LayerTileGrid) {

    /** The tiles that are in memory, by their number. */
    private val tiles = LinkedHashMap<Int, Bitmap>()

    /** The tiles that changed since the last [markClean]. */
    private val dirty = LinkedHashSet<Int>()

    /**
     * The bitmap of the tile (tx, ty) - null when nothing was written there.
     */
    @Synchronized
    fun tile(tx: Int, ty: Int): Bitmap? {
        val index = grid.tileIndex(tx, ty)
        if (index < 0) return null
        return tiles[index]
    }

    /**
     * The bitmap of the tile (tx, ty), created when it is not there yet.
     *
     * A new tile is transparent and counts as changed: the saver has to
     * write it.
     */
    @Synchronized
    fun requireTile(tx: Int, ty: Int): Bitmap {
        val index = grid.tileIndex(tx, ty)
        if (index < 0) {
            // Outside the layer: there is no tile to hold the pixels, so the
            // caller gets an empty one instead of an exception.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val existing = tiles[index]
        if (existing != null && !existing.isRecycled) return existing
        val created = Bitmap.createBitmap(
            grid.tileWidth(tx).coerceAtLeast(1),
            grid.tileHeight(ty).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        tiles[index] = created
        dirty.add(index)
        return created
    }

    /**
     * Puts a bitmap into the store (the loader reads the tiles of a layer
     * back from the disk).
     *
     * The bitmap is taken over: the store owns it from now on. A tile that
     * is put in is not "changed" - it is what the disk already holds.
     */
    @Synchronized
    fun putTile(tx: Int, ty: Int, bitmap: Bitmap): Boolean {
        val index = grid.tileIndex(tx, ty)
        if (index < 0) return false
        val old = tiles.put(index, bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
        return true
    }

    /** The numbers of the tiles a rectangle of the layer touches. */
    fun tilesFor(left: Int, top: Int, right: Int, bottom: Int): List<Int> =
        grid.tilesForRect(left, top, right, bottom)

    /**
     * The numbers of the tiles that are in the store.
     *
     * This is the list the metadata of the page carries ("tiles"): the
     * tiles that exist, not only the ones that changed.
     */
    @Synchronized
    fun existingTiles(): Set<Int> = LinkedHashSet(tiles.keys)

    /**
     * Draws into the tiles a rectangle of the layer touches.
     *
     * Every tile the rectangle touches is created when it is not there yet,
     * and the caller gets its canvas and its origin (the left and the top of
     * the tile in layer pixels). The canvas is **not** moved: the caller
     * draws in LAYER coordinates and translates the canvas by the origin
     * itself:
     *
     * ```
     * tiles.drawInTiles(left, top, right, bottom) { canvas, originX, originY ->
     *     canvas.translate(-originX.toFloat(), -originY.toFloat())
     *     canvas.drawCircle(x, y, radius, paint)
     * }
     * ```
     */
    @Synchronized
    fun drawInTiles(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        draw: (Canvas, Int, Int) -> Unit
    ) {
        for (index in grid.tilesForRect(left, top, right, bottom)) {
            val tx = grid.tileColumn(index)
            val ty = grid.tileRow(index)
            if (tx < 0 || ty < 0) continue
            val bitmap = requireTile(tx, ty)
            if (bitmap.isRecycled) continue
            draw(Canvas(bitmap), grid.tileLeft(tx), grid.tileTop(ty))
            dirty.add(index)
        }
    }

    /**
     * Wipes a rectangle of the layer away.
     *
     * The pixels of the rectangle are cleared (PorterDuff CLEAR). A tile that
     * is empty afterwards is given back to the system and dropped from the
     * store: an erased area costs nothing.
     */
    @Synchronized
    fun eraseRect(left: Int, top: Int, right: Int, bottom: Int) {
        if (tiles.isEmpty()) return
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        for (index in grid.tilesForRect(left, top, right, bottom)) {
            val bitmap = tiles[index] ?: continue
            if (bitmap.isRecycled) continue
            val tx = grid.tileColumn(index)
            val ty = grid.tileRow(index)
            val originX = grid.tileLeft(tx)
            val originY = grid.tileTop(ty)
            val l = maxOf(left, originX)
            val t = maxOf(top, originY)
            val r = minOf(right, originX + bitmap.width)
            val b = minOf(bottom, originY + bitmap.height)
            if (r <= l || b <= t) continue
            val canvas = Canvas(bitmap)
            canvas.drawRect(
                (l - originX).toFloat(),
                (t - originY).toFloat(),
                (r - originX).toFloat(),
                (b - originY).toFloat(),
                paint
            )
            // The tile changed - and if it is empty now, the file of it has
            // to go: the saver still needs to hear about it.
            dirty.add(index)
            if (isBitmapEmpty(bitmap)) {
                bitmap.recycle()
                tiles.remove(index)
            }
        }
    }

    /**
     * Gives a tile back to the system when it holds nothing any more.
     *
     * An erased tile that is empty costs nothing, so it goes: the pixels are
     * given back and the tile leaves the store. A tile that still holds ink
     * stays where it is.
     */
    @Synchronized
    fun releaseIfEmpty(tx: Int, ty: Int) {
        val index = grid.tileIndex(tx, ty)
        if (index < 0) return
        val bitmap = tiles[index] ?: return
        if (!bitmap.isRecycled && !isBitmapEmpty(bitmap)) return
        if (!bitmap.isRecycled) bitmap.recycle()
        tiles.remove(index)
        // The tile changed: the file of it has to go (or be written again).
        dirty.add(index)
    }

    /**
     * The tiles that changed since the last [markClean].
     *
     * A tile that was erased to nothing stays in the list (its file has to
     * go), even though it is not in the store any more.
     */
    @Synchronized
    fun dirtyTiles(): Set<Int> = LinkedHashSet(dirty)

    /** Forgets the changes: the tiles on the disk are the ones in memory. */
    @Synchronized
    fun markClean() {
        dirty.clear()
    }

    /** Marks one tile as changed. */
    @Synchronized
    fun markDirty(tx: Int, ty: Int) {
        val index = grid.tileIndex(tx, ty)
        if (index >= 0) dirty.add(index)
    }

    /**
     * True when the layer holds no ink at all.
     *
     * The pixels are looked at, not only the tiles: a tile that holds nothing
     * (it was cleared, or it was only touched) does not count.
     */
    @Synchronized
    fun isEmpty(): Boolean {
        for (bitmap in tiles.values) {
            if (!isBitmapEmpty(bitmap)) return false
        }
        return true
    }

    /**
     * Composes a rectangle of the layer into ONE bitmap (transparent
     * background).
     *
     * The bitmap is never bigger than the rectangle that was asked for: the
     * rectangle is clipped to the layer first. Null when the rectangle holds
     * no ink - then nothing is allocated at all.
     */
    @Synchronized
    fun compose(left: Int, top: Int, right: Int, bottom: Int): Bitmap? {
        val l = left.coerceAtLeast(0)
        val t = top.coerceAtLeast(0)
        val r = right.coerceAtMost(grid.width)
        val b = bottom.coerceAtMost(grid.height)
        if (r <= l || b <= t) return null
        if (!hasContent(l, t, r, b)) return null
        val out = Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for (index in grid.tilesForRect(l, t, r, b)) {
            val bitmap = tiles[index] ?: continue
            if (bitmap.isRecycled) continue
            val tx = grid.tileColumn(index)
            val ty = grid.tileRow(index)
            canvas.drawBitmap(
                bitmap,
                (grid.tileLeft(tx) - l).toFloat(),
                (grid.tileTop(ty) - t).toFloat(),
                null
            )
        }
        return out
    }

    /** Gives the tiles back to the system. */
    @Synchronized
    fun release() {
        for (bitmap in tiles.values) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        tiles.clear()
        dirty.clear()
    }

    /** True when the tile holds a pixel that is not transparent. */
    private fun hasContent(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        for (index in grid.tilesForRect(left, top, right, bottom)) {
            val bitmap = tiles[index] ?: continue
            if (bitmap.isRecycled) continue
            val tx = grid.tileColumn(index)
            val ty = grid.tileRow(index)
            val originX = grid.tileLeft(tx)
            val originY = grid.tileTop(ty)
            val l = maxOf(left, originX)
            val t = maxOf(top, originY)
            val r = minOf(right, originX + bitmap.width)
            val b = minOf(bottom, originY + bitmap.height)
            if (r <= l || b <= t) continue
            if (hasContentIn(bitmap, l - originX, t - originY, r - originX, b - originY)) return true
        }
        return false
    }

    /** True when the pixels of a rectangle of a bitmap hold any ink. */
    private fun hasContentIn(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val width = right - left
        if (width <= 0) return false
        val row = IntArray(width)
        for (y in top until bottom) {
            bitmap.getPixels(row, 0, width, left, y, width, 1)
            for (value in row) {
                if ((value ushr 24) != 0) return true
            }
        }
        return false
    }

    /** True when a bitmap holds nothing but transparent pixels. */
    private fun isBitmapEmpty(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled) return true
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true
        return !hasContentIn(bitmap, 0, 0, width, height)
    }
}
