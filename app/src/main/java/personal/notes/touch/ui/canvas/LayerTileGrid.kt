package personal.notes.touch.ui.canvas

/**
 * The grid of tiles of an annotation layer.
 *
 * The annotation layer of a page is the surface the writing goes on: it is
 * as wide as the page plus a whole page width of margin on each side (for an
 * A4 page 6480 x 3056 px - about 80 MB of pixels). Keeping it as ONE bitmap
 * means every save encodes all of it, and that is what starves the UI thread
 * on a big drawing (audit C50).
 *
 * The grid is the **pure geometry** of the tiles: where they are, how big
 * they are and what they are called. It knows nothing about pixels (that is
 * [LayerTiles]) and nothing about the disk, so every rule of the tiling can
 * be unit tested on a plain JVM.
 *
 * The layer is cut into squares of [tileSize] pixels. The last column and
 * the last row are shorter, so the tiles fit the layer exactly.
 *
 * A degenerate grid (no layer, or no tile size) is not an error: it has no
 * columns and no rows, and every accessor of it answers safely (0 or null)
 * instead of throwing.
 */
data class LayerTileGrid(
    /** The width of the layer in pixels. */
    val width: Int,
    /** The height of the layer in pixels. */
    val height: Int,
    /** The size of a tile in pixels (the last column and row are shorter). */
    val tileSize: Int = DEFAULT_TILE_SIZE
) {

    companion object {
        /** The size of a tile in pixels: 1024 x 1024 = 4 MB of pixels. */
        const val DEFAULT_TILE_SIZE = 1024

        /**
         * The name of a tile: `t<tx>_<ty>`.
         *
         * The name is the file name of the tile without its folder, so the
         * file store and the metadata always agree on it.
         */
        fun tileName(tx: Int, ty: Int): String = "t${tx}_${ty}"

        /**
         * Reads a tile name back: `t3_4` becomes (3, 4).
         *
         * Returns null when the name is not a tile name: only
         * `t<digits>_<digits>` is one. A negative number, a `+`, a space, a
         * second underscore or anything else is not a tile name.
         */
        fun parseTileName(name: String): Pair<Int, Int>? {
            if (name.length < 4) return null
            if (name[0] != 't') return null
            val separator = name.indexOf('_', 1)
            if (separator <= 1 || separator == name.length - 1) return null
            if (name.indexOf('_', separator + 1) >= 0) return null
            val tx = parseDigits(name.substring(1, separator)) ?: return null
            val ty = parseDigits(name.substring(separator + 1)) ?: return null
            return Pair(tx, ty)
        }

        /** The digits of a tile name (null when they are not digits). */
        private fun parseDigits(text: String): Int? {
            if (text.isEmpty()) return null
            var value = 0L
            for (c in text) {
                if (c < '0' || c > '9') return null
                value = value * 10L + (c - '0')
                if (value > Int.MAX_VALUE) return null
            }
            return value.toInt()
        }
    }

    /** How many columns the layer has (0 when the layer is empty). */
    val columns: Int =
        if (width <= 0 || tileSize <= 0) 0 else ((width.toLong() + tileSize - 1L) / tileSize).toInt()

    /** How many rows the layer has (0 when the layer is empty). */
    val rows: Int =
        if (height <= 0 || tileSize <= 0) 0 else ((height.toLong() + tileSize - 1L) / tileSize).toInt()

    /** How many tiles the layer is cut into. */
    fun tileCount(): Int = columns.toLong().let { it * rows.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** True when the tile (tx, ty) is a tile of this layer. */
    fun isTile(tx: Int, ty: Int): Boolean = tx in 0 until columns && ty in 0 until rows

    /**
     * The number of the tile (tx, ty), row by row.
     *
     * -1 when there is no such tile (outside the layer, or the layer has no
     * tiles at all).
     */
    fun tileIndex(tx: Int, ty: Int): Int {
        if (!isTile(tx, ty)) return -1
        return ty * columns + tx
    }

    /** The column of the tile with this number (-1 when there is none). */
    fun tileColumn(index: Int): Int {
        if (index < 0 || index >= tileCount() || columns <= 0) return -1
        return index % columns
    }

    /** The row of the tile with this number (-1 when there is none). */
    fun tileRow(index: Int): Int {
        if (index < 0 || index >= tileCount() || columns <= 0) return -1
        return index / columns
    }

    /** The left edge of the tile in layer pixels (0 when there is none). */
    fun tileLeft(tx: Int): Int {
        if (tx < 0 || tx >= columns) return 0
        return tx * tileSize
    }

    /** The top edge of the tile in layer pixels (0 when there is none). */
    fun tileTop(ty: Int): Int {
        if (ty < 0 || ty >= rows) return 0
        return ty * tileSize
    }

    /** The width of the tile in pixels (the last column is shorter). */
    fun tileWidth(tx: Int): Int {
        if (tx < 0 || tx >= columns) return 0
        return (width - tx * tileSize).coerceAtMost(tileSize).coerceAtLeast(0)
    }

    /** The height of the tile in pixels (the last row is shorter). */
    fun tileHeight(ty: Int): Int {
        if (ty < 0 || ty >= rows) return 0
        return (height - ty * tileSize).coerceAtMost(tileSize).coerceAtLeast(0)
    }

    /** The name of the tile (the same name the file store and the metadata use). */
    fun tileName(tx: Int, ty: Int): String = Companion.tileName(tx, ty)

    /** Reads a tile name back (null when it is not a tile name). */
    fun parseTileName(name: String): Pair<Int, Int>? = Companion.parseTileName(name)

    /**
     * The numbers of the tiles a rectangle of the layer touches.
     *
     * The rectangle is in layer pixels; its right and bottom edges are
     * exclusive (like `android.graphics.Rect`), and it is clipped to the
     * layer, so a rectangle that hangs over the edge asks for the tiles it
     * really touches. The numbers come row by row.
     */
    fun tilesForRect(left: Int, top: Int, right: Int, bottom: Int): List<Int> {
        if (columns <= 0 || rows <= 0) return emptyList()
        val l = left.coerceAtLeast(0)
        val t = top.coerceAtLeast(0)
        val r = right.coerceAtMost(width)
        val b = bottom.coerceAtMost(height)
        if (r <= l || b <= t) return emptyList()
        val firstColumn = l / tileSize
        val lastColumn = (r - 1) / tileSize
        val firstRow = t / tileSize
        val lastRow = (b - 1) / tileSize
        val out = ArrayList<Int>((lastColumn - firstColumn + 1) * (lastRow - firstRow + 1))
        for (ty in firstRow..lastRow) {
            for (tx in firstColumn..lastColumn) {
                out.add(ty * columns + tx)
            }
        }
        return out
    }
}
