package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.LayerTileMapping
import personal.notes.touch.ui.canvas.LayerTilePatch

/**
 * Tests of the geometry of the tiles of an annotation layer (audit C50).
 *
 * The layer is a grid of tiles now, and two things have to be right:
 *
 *  - where a tile lands on the screen (the reader draws every tile into its
 *    own part of the layer's rect),
 *  - where the rectangle of an eraser stroke lies inside a tile (the undo
 *    keeps the pixels of every tile it touched).
 *
 * Both are pure maths - no Bitmap, no Canvas - so they are checked here on a
 * plain JVM. The pixels themselves live in
 * [personal.notes.touch.ui.canvas.LayerTiles], which needs a device.
 */
class LayerTileMappingTest {

    private val tolerance = 0.001f

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(
            "expected $expected but was $actual",
            kotlin.math.abs(expected - actual) < tolerance
        )
    }

    /** The layer of a fixed page covers the whole box. */
    private val wholeBox = ContinuousPageGeometry.BoxRect(0f, 0f, 1f, 1f)

    @Test
    fun aTileLandsInItsOwnPartOfTheLayerRect() {
        // A layer of 2500 x 1500 pixels, cut into tiles of 1000.
        val grid = LayerTileGrid(2500, 1500, 1000)
        // The layer is drawn into a box of 1000 x 800 pixels.
        val first = LayerTileMapping.tileRect(grid, 0, 0, wholeBox, 1000f, 800f)
        assertClose(0f, first.left)
        assertClose(0f, first.top)
        // The first tile is 1000 of the 2500 pixels: two fifths of the box.
        assertClose(400f, first.right)
        assertClose(800f * 1000f / 1500f, first.bottom)

        val second = LayerTileMapping.tileRect(grid, 1, 0, wholeBox, 1000f, 800f)
        assertClose(400f, second.left)
        assertClose(800f, second.right)

        // The last column is shorter: it ends at the edge of the layer.
        val last = LayerTileMapping.tileRect(grid, 2, 0, wholeBox, 1000f, 800f)
        assertClose(800f, last.left)
        assertClose(1000f, last.right)

        // The second row is shorter as well.
        val row = LayerTileMapping.tileRect(grid, 0, 1, wholeBox, 1000f, 800f)
        assertClose(800f * 1000f / 1500f, row.top)
        assertClose(800f, row.bottom)
    }

    @Test
    fun theTilesCoverTheLayerRectWithoutAGap() {
        val grid = LayerTileGrid(2500, 1500, 1000)
        var x = 0f
        for (tx in 0 until grid.columns) {
            val rect = LayerTileMapping.tileRect(grid, tx, 0, wholeBox, 1000f, 800f)
            assertClose(x, rect.left)
            x = rect.right
        }
        // The last tile ends at the edge of the layer.
        assertClose(1000f, x)
    }

    @Test
    fun aLayerThatIsOneTileIsDrawnLikeTheOldBitmap() {
        // A layer that fits into one tile is drawn into the whole layer rect
        // - exactly like the one big bitmap before the tiles.
        val grid = LayerTileGrid(800, 600, 1024)
        val rect = ContinuousPageGeometry.BoxRect(0.25f, 0.5f, 1.25f, 1.5f)
        val tile = LayerTileMapping.tileRect(grid, 0, 0, rect, 1000f, 800f)
        assertClose(0.25f * 1000f, tile.left)
        assertClose(0.5f * 800f, tile.top)
        assertClose(1.25f * 1000f, tile.right)
        assertClose(1.5f * 800f, tile.bottom)
    }

    @Test
    fun aTileThatIsNotATileHasNoRect() {
        val grid = LayerTileGrid(2500, 1500, 1000)
        val nowhere = LayerTileMapping.tileRect(grid, 7, 7, wholeBox, 1000f, 800f)
        assertTrue(nowhere.empty)
        // A layer without a size has no tiles either.
        val empty = LayerTileMapping.tileRect(
            LayerTileGrid(0, 0), 0, 0, wholeBox, 1000f, 800f
        )
        assertTrue(empty.empty)
    }

    @Test
    fun theRectOfAStrokeInsideATileIsThePartThatLiesInIt() {
        val grid = LayerTileGrid(2500, 1500, 1000)
        // The stroke reaches from the first tile into the second one.
        val rect = LayerTilePatch.rectInTile(grid, 1, 0, 500, 500, 1500, 1200)
        assertTrue(rect != null)
        val inside = rect ?: return
        // The tile (1, 0) starts at x = 1000: the part of the stroke that is
        // in it starts there.
        assertEquals(0, inside[0])
        assertEquals(500, inside[1])
        assertEquals(500, inside[2])
        assertEquals(1000, inside[3])
    }

    @Test
    fun aStrokeThatDoesNotReachATileHasNothingInIt() {
        val grid = LayerTileGrid(2500, 1500, 1000)
        // The stroke is left of the tile.
        assertNull(LayerTilePatch.rectInTile(grid, 1, 0, 0, 0, 500, 500))
        // The stroke is below the tile.
        assertNull(LayerTilePatch.rectInTile(grid, 0, 0, 0, 1200, 500, 1400))
        // The tile does not exist.
        assertNull(LayerTilePatch.rectInTile(grid, 9, 9, 0, 0, 100, 100))
    }

    @Test
    fun theRectOfATileIsAlsoKnownInTheLayersPixels() {
        val grid = LayerTileGrid(2500, 1500, 1000)
        val inTile = LayerTilePatch.rectInTile(grid, 1, 0, 500, 500, 1500, 1200)
        assertTrue(inTile != null)
        val inLayer = LayerTilePatch.rectInLayer(grid, 1, 0, 500, 500, 1500, 1200)
        assertTrue(inLayer != null)
        val tile = inTile ?: return
        val layer = inLayer ?: return
        // The same rectangle, in the layer's pixels and in the tile's.
        assertEquals(1000 + tile[0], layer[0])
        assertEquals(tile[1], layer[1])
        assertEquals(1000 + tile[2], layer[2])
        assertEquals(tile[3], layer[3])
    }

    @Test
    fun theTilesOfAnA4LayerAreCutIntoSevenColumnsAndThreeRows() {
        // The annotation layer of an A4 page: the page plus a page width of
        // margin on each side.
        val grid = LayerTileGrid(6480, 3056, 1024)
        assertEquals(7, grid.columns)
        assertEquals(3, grid.rows)
        // The last column and the last row are shorter, so the tiles fit the
        // layer exactly.
        assertEquals(grid.width, grid.tileLeft(6) + grid.tileWidth(6))
        assertEquals(grid.height, grid.tileTop(2) + grid.tileHeight(2))
        // The tile at the far right of the bottom row is inside the layer.
        val corner = LayerTileMapping.tileRect(grid, 6, 2, wholeBox, 1000f, 800f)
        assertFalse(corner.empty)
        assertClose(1000f, corner.right)
        assertClose(800f, corner.bottom)
    }
}
