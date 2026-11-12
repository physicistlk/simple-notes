package personal.notes.touch.ui.canvas

/**
 * Where the tiles of an annotation layer land on the screen.
 *
 * The annotation layer is a grid of tiles (see [LayerTileGrid]) and the
 * reader draws the layer into the rect it has in the page box. Before the
 * tiles there was ONE bitmap of the layer and it was drawn into the whole
 * rect; now every tile covers its own part of it: the tile at (tx, ty)
 * starts at `left + tileLeft / layerWidth * width` and ends at
 * `left + (tileLeft + tileWidth) / layerWidth * width` (the same for the
 * height).
 *
 * The maths is **pure** - no Bitmap, no Canvas - so it can be unit tested on
 * a plain JVM, the same way the grid itself is checked. The pixels of the
 * tiles live in [LayerTiles]; this file only says where they go.
 */
object LayerTileMapping {

    /** A rectangle in screen pixels. */
    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        /** True when the rectangle has no area (nothing to draw). */
        val empty: Boolean get() = width <= 0f || height <= 0f
    }

    /**
     * The part of the layer's rect the tile (tx, ty) covers, in screen
     * pixels.
     *
     * [layerRect] is the rect the layer has in the page box (normalised box
     * coordinates) and [viewWidth] / [viewHeight] are the size of the canvas
     * the layer is drawn into. The answer is empty when the tile is not a
     * tile of the layer or when the layer has no size.
     */
    fun tileRect(
        grid: LayerTileGrid,
        tx: Int,
        ty: Int,
        layerRect: ContinuousPageGeometry.BoxRect,
        viewWidth: Float,
        viewHeight: Float
    ): Rect {
        if (grid.width <= 0 || grid.height <= 0 || layerRect.empty) {
            return Rect(0f, 0f, 0f, 0f)
        }
        if (!grid.isTile(tx, ty)) return Rect(0f, 0f, 0f, 0f)
        val left = grid.tileLeft(tx)
        val top = grid.tileTop(ty)
        val right = left + grid.tileWidth(tx)
        val bottom = top + grid.tileHeight(ty)
        return Rect(
            (layerRect.left + left / grid.width.toFloat() * layerRect.width) * viewWidth,
            (layerRect.top + top / grid.height.toFloat() * layerRect.height) * viewHeight,
            (layerRect.left + right / grid.width.toFloat() * layerRect.width) * viewWidth,
            (layerRect.top + bottom / grid.height.toFloat() * layerRect.height) * viewHeight
        )
    }
}

/**
 * The geometry of the pixel patches the eraser leaves behind.
 *
 * The eraser clears pixels, so the undo needs them: the patch of a tile
 * holds the rectangle **inside that tile** and the pixels before and after
 * the wipe. The rectangle is the part of the stroke's bounds that lies in the
 * tile, in the tile's own pixels.
 *
 * Pure maths again: the pixels themselves live in the tiles.
 */
object LayerTilePatch {

    /**
     * The part of a rectangle of the layer that lies in the tile (tx, ty),
     * in the tile's own pixels.
     *
     * The answer is `[left, top, right, bottom]` (the right and the bottom
     * edge are exclusive, like `android.graphics.Rect`) or null when the
     * rectangle and the tile do not meet.
     */
    fun rectInTile(
        grid: LayerTileGrid,
        tx: Int,
        ty: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): IntArray? {
        if (!grid.isTile(tx, ty)) return null
        val originX = grid.tileLeft(tx)
        val originY = grid.tileTop(ty)
        val l = maxOf(left, originX)
        val t = maxOf(top, originY)
        val r = minOf(right, originX + grid.tileWidth(tx))
        val b = minOf(bottom, originY + grid.tileHeight(ty))
        if (r <= l || b <= t) return null
        return intArrayOf(l - originX, t - originY, r - originX, b - originY)
    }

    /**
     * The same rectangle in the layer's pixels (the tile's origin added
     * back), so it can be handed to the store.
     */
    fun rectInLayer(
        grid: LayerTileGrid,
        tx: Int,
        ty: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): IntArray? {
        val rect = rectInTile(grid, tx, ty, left, top, right, bottom) ?: return null
        return intArrayOf(
            rect[0] + grid.tileLeft(tx),
            rect[1] + grid.tileTop(ty),
            rect[2] + grid.tileLeft(tx),
            rect[3] + grid.tileTop(ty)
        )
    }
}
