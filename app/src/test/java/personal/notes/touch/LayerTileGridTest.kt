package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack

/**
 * Tests of the grid of tiles of an annotation layer (audit C50).
 *
 * The grid is the pure geometry of the tiles: where they are, how big they
 * are and what they are called. It knows no pixels and no disk, so it can
 * be checked on a plain JVM - the same way the layer stack is checked.
 *
 * The tile store itself ([personal.notes.touch.ui.canvas.LayerTiles]) holds
 * bitmaps, and `android.graphics.Bitmap` does not exist in these tests: it
 * is covered by the device pass, not here.
 */
class LayerTileGridTest {

    @Test
    fun aGridIsCutIntoColumnsAndRows() {
        // The annotation layer of an A4 page: the page plus a page width of
        // margin on each side.
        val grid = LayerTileGrid(6480, 3056, 1024)
        assertEquals(7, grid.columns)
        assertEquals(3, grid.rows)
        assertEquals(21, grid.tileCount())
    }

    @Test
    fun theLastColumnAndTheLastRowAreShorter() {
        val grid = LayerTileGrid(6480, 3056, 1024)
        assertEquals(1024, grid.tileWidth(0))
        assertEquals(6480 - 6 * 1024, grid.tileWidth(6))
        assertEquals(1024, grid.tileHeight(0))
        assertEquals(3056 - 2 * 1024, grid.tileHeight(2))
        // The tiles fit the layer exactly.
        assertEquals(grid.width, grid.tileLeft(6) + grid.tileWidth(6))
        assertEquals(grid.height, grid.tileTop(2) + grid.tileHeight(2))
    }

    @Test
    fun aTileHasANameAndANumber() {
        val grid = LayerTileGrid(6480, 3056, 1024)
        assertEquals("t0_0", grid.tileName(0, 0))
        assertEquals("t6_2", grid.tileName(6, 2))
        assertEquals(Pair(0, 0), grid.parseTileName("t0_0"))
        assertEquals(Pair(6, 2), grid.parseTileName("t6_2"))
        assertEquals(Pair(12, 7), grid.parseTileName("t12_7"))
        // The number of a tile is row by row.
        assertEquals(0, grid.tileIndex(0, 0))
        assertEquals(7, grid.tileIndex(0, 1))
        assertEquals(6, grid.tileColumn(6))
        assertEquals(1, grid.tileRow(7))
        assertEquals(0, grid.tileLeft(0))
        assertEquals(1024, grid.tileLeft(1))
        assertEquals(2 * 1024, grid.tileTop(2))
    }

    @Test
    fun aNameThatIsNotATileNameIsRejected() {
        val grid = LayerTileGrid(6480, 3056, 1024)
        assertNull(grid.parseTileName("t-1_0"))
        assertNull(grid.parseTileName("t+1_0"))
        assertNull(grid.parseTileName("t 1_0"))
        assertNull(grid.parseTileName("t1_0 "))
        assertNull(grid.parseTileName("t1_0_2"))
        assertNull(grid.parseTileName("t1"))
        assertNull(grid.parseTileName("t1_"))
        assertNull(grid.parseTileName("_1_0"))
        assertNull(grid.parseTileName("T1_0"))
        assertNull(grid.parseTileName(""))
        assertNull(grid.parseTileName("page_1_annot_0.png"))
        // A tile that is not in the grid has no number.
        assertEquals(-1, grid.tileIndex(7, 0))
        assertEquals(-1, grid.tileIndex(0, 3))
    }

    @Test
    fun aRectangleTouchesTheTilesItCovers() {
        val grid = LayerTileGrid(3000, 2000, 1000)
        assertEquals(listOf(0), grid.tilesForRect(0, 0, 1000, 1000))
        assertEquals(listOf(0, 1), grid.tilesForRect(0, 0, 1001, 1000))
        assertEquals(listOf(0, 1), grid.tilesForRect(900, 0, 1100, 500))
        // A rectangle that reaches into the next row touches the tiles of
        // both rows, row by row.
        assertEquals(listOf(0, 3), grid.tilesForRect(0, 900, 1000, 1100))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), grid.tilesForRect(0, 0, 3000, 2000))
    }

    @Test
    fun aRectangleThatHangsOverTheEdgeIsClipped() {
        val grid = LayerTileGrid(3000, 2000, 1000)
        // The whole layer, and more.
        assertEquals(listOf(0, 1, 2, 3, 4, 5), grid.tilesForRect(-500, -500, 3500, 2500))
        // Nothing of the layer is left: no tile at all.
        assertTrue(grid.tilesForRect(3000, 0, 4000, 1000).isEmpty())
        assertTrue(grid.tilesForRect(0, 2000, 1000, 3000).isEmpty())
        assertTrue(grid.tilesForRect(0, 0, 0, 0).isEmpty())
    }

    @Test
    fun anEmptyGridDoesNotCrash() {
        val empty = LayerTileGrid(0, 0)
        assertEquals(0, empty.columns)
        assertEquals(0, empty.rows)
        assertEquals(0, empty.tileCount())
        assertEquals(0, empty.tileWidth(0))
        assertEquals(0, empty.tileHeight(0))
        assertEquals(0, empty.tileLeft(0))
        assertEquals(0, empty.tileTop(0))
        assertEquals(-1, empty.tileIndex(0, 0))
        assertEquals(-1, empty.tileColumn(0))
        assertEquals(-1, empty.tileRow(0))
        assertTrue(empty.tilesForRect(0, 0, 100, 100).isEmpty())

        // A layer without a size, and a grid without a tile size, are empty
        // as well - and they do not throw.
        val negative = LayerTileGrid(-10, -10, 1024)
        assertEquals(0, negative.columns)
        assertEquals(0, negative.rows)
        val noTile = LayerTileGrid(1000, 1000, 0)
        assertEquals(0, noTile.columns)
        assertEquals(0, noTile.rows)
        assertEquals(0, noTile.tileCount())
        assertTrue(noTile.tilesForRect(0, 0, 100, 100).isEmpty())
    }

    @Test
    fun theMetadataOfATiledLayerSurvivesTheRoundTrip() {
        val stack = PageLayerStack.base()
        stack.add()
        val tiles = mapOf(
            1 to PageLayerMeta.LayerTilesInfo(6480, 3056, 1024, listOf("t0_0", "t1_0", "t2_2")),
            2 to PageLayerMeta.LayerTilesInfo(6480, 3056, 1024, listOf("t0_0"))
        )
        val text = PageLayerMeta.encode(stack, tiles)

        val back = PageLayerMeta.decodeTiles(text)
        assertEquals(2, back.size)
        val first = back[1]
        assertNotNull(first)
        val info = first ?: return
        assertEquals(6480, info.width)
        assertEquals(3056, info.height)
        assertEquals(1024, info.tileSize)
        assertEquals(listOf("t0_0", "t1_0", "t2_2"), info.tiles)
        assertEquals(listOf(Pair(0, 0), Pair(1, 0), Pair(2, 2)), info.positions())
        assertEquals(listOf("t0_0"), back[2]?.tiles)

        // The stack of the page is still there.
        val read = PageLayerMeta.decode(text)
        assertNotNull(read)
        val decoded = read ?: return
        assertEquals(2, decoded.size)
        // The layer that was added is the current one.
        assertEquals(2, decoded.currentIndex)
    }

    @Test
    fun theMetadataOfTheOldFormatStillDecodes() {
        // The exact file the app writes today: no tile information at all.
        val old = "{\"version\":1,\"current\":2,\"layers\":[" +
            "{\"index\":1,\"name\":\"Layer1\",\"visible\":true}," +
            "{\"index\":2,\"name\":\"Layer2\",\"visible\":true}]}"
        val stack = PageLayerMeta.decode(old)
        assertNotNull(stack)
        val read = stack ?: return
        assertEquals(2, read.size)
        assertEquals(2, read.currentIndex)
        assertEquals("Layer2", read.layers[1].name)
        // No layer has tile information: they are all legacy layers.
        assertTrue(PageLayerMeta.decodeTiles(old).isEmpty())

        // And the file the app writes today is still the file it writes.
        val written = PageLayerMeta.encode(PageLayerStack.base())
        assertEquals(
            "{\"version\":1,\"current\":1,\"layers\":[" +
                "{\"index\":1,\"name\":\"Layer1\",\"visible\":true}]}",
            written
        )
    }

    @Test
    fun aLayerWithoutTilesIsALegacyLayer() {
        // The first layer is cut into tiles, the second one is an old
        // full-size layer.
        val text = "{\"version\":1,\"current\":1,\"layers\":[" +
            "{\"index\":1,\"name\":\"Layer1\",\"visible\":true," +
            "\"w\":6480,\"h\":3056,\"tile\":1024,\"tiles\":[\"t0_0\"]}," +
            "{\"index\":2,\"name\":\"Layer2\",\"visible\":true}]}"
        val tiles = PageLayerMeta.decodeTiles(text)
        assertEquals(1, tiles.size)
        assertTrue(tiles.containsKey(1))
        assertFalse(PageLayerMeta.isLegacyLayer(tiles, 1))
        // Layer 2 has no tile information: it is a legacy layer.
        assertFalse(tiles.containsKey(2))
        assertTrue(PageLayerMeta.isLegacyLayer(tiles, 2))
    }

    @Test
    fun brokenMetadataIsStillNothing() {
        assertNull(PageLayerMeta.decode("this is not json"))
        assertNull(PageLayerMeta.decode("{\"version\":1,\"layers\":[]}"))
        assertTrue(PageLayerMeta.decodeTiles("this is not json").isEmpty())
        assertTrue(PageLayerMeta.decodeTiles("{\"version\":1,\"layers\":[]}").isEmpty())
    }
}
