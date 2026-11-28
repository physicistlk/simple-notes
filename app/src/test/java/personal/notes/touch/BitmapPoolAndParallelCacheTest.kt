package personal.notes.touch

import android.graphics.Bitmap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import personal.notes.touch.util.BitmapPool
import java.io.File

class BitmapPoolAndParallelCacheTest {

    @Before
    fun setUp() {
        BitmapPool.clear(resetBudget = true)
    }

    @After
    fun tearDown() {
        BitmapPool.clear(resetBudget = true)
    }

    @Test
    fun bitmapPoolRequiredBytesComputesExactByteFootprintsAndHandlesNonPositiveDimensions() {
        assertEquals(0L, BitmapPool.requiredBytes(0, 64, Bitmap.Config.ARGB_8888))
        assertEquals(0L, BitmapPool.requiredBytes(64, -1, Bitmap.Config.ARGB_8888))
        assertEquals(1024L * 1024L * 4L, BitmapPool.requiredBytes(1024, 1024, Bitmap.Config.ARGB_8888))
        assertEquals(512L * 512L * 2L, BitmapPool.requiredBytes(512, 512, Bitmap.Config.RGB_565))
        assertEquals(256L * 256L * 1L, BitmapPool.requiredBytes(256, 256, Bitmap.Config.ALPHA_8))
    }

    @Test
    fun bitmapPoolHandlesEmptyAcquireNullPutAndNonExistentFileGracefully() {
        assertNull(BitmapPool.acquire(1024, 1024, Bitmap.Config.ARGB_8888))
        assertNull(BitmapPool.acquire(0, 0, Bitmap.Config.ARGB_8888))
        BitmapPool.put(null)
        assertEquals(0, BitmapPool.pooledCount())
        assertEquals(0L, BitmapPool.currentByteSize())
        assertNull(BitmapPool.decodeFileMutableArgb(File("/nonexistent/path/tile_0_0.png")))
    }

    @Test
    fun pdfDocumentCacheSupportsPooledHandlesAndLruRenderedPageCache() {
        val repoRoot = resolveRepoRoot()
        val cacheSrc = File(
            repoRoot,
            "app/src/main/java/personal/notes/touch/util/PdfDocumentCache.kt"
        ).readText()
        assertTrue(cacheSrc.contains("fun <T> withDocument("))
        assertTrue(cacheSrc.contains("MAX_POOL_HANDLES = 4"))
        assertTrue(cacheSrc.contains("fun getRenderedPage("))
        assertTrue(cacheSrc.contains("fun putRenderedPage("))

        val canvasSrc = File(
            repoRoot,
            "app/src/main/java/personal/notes/touch/ui/canvas/CanvasScreen.kt"
        ).readText()
        val renderStart = canvasSrc.indexOf("internal suspend fun renderContinuousPage(")
        val renderEnd = canvasSrc.indexOf("@Composable\nprivate fun PageThumbnailCard(", renderStart)
        val renderBlock = canvasSrc.substring(renderStart, renderEnd)
        assertTrue("renderContinuousPage must check LRU render cache", renderBlock.contains("pdfDocCache.getRenderedPage("))
        assertTrue("renderContinuousPage must use pooled Pdfium handles", renderBlock.contains("pdfDocCache.withDocument("))
        assertTrue("renderContinuousPage must populate LRU render cache", renderBlock.contains("pdfDocCache.putRenderedPage("))
    }

    @Test
    fun layerThumbnailTileDecodeAndLayerComposeRunOffMainThreadInParallel() {
        val repoRoot = resolveRepoRoot()
        val inkCtrl = File(
            repoRoot,
            "app/src/main/java/personal/notes/touch/ui/canvas/ContinuousInkController.kt"
        ).readText()
        val thumbStart = inkCtrl.indexOf("suspend fun layerThumbnail(")
        val thumbEnd = inkCtrl.indexOf("private fun drawInfiniteOpStroke(", thumbStart)
        val thumbBlock = inkCtrl.substring(thumbStart, thumbEnd)
        assertTrue("layerThumbnail must offload IO via withContext(Dispatchers.IO)", thumbBlock.contains("withContext(Dispatchers.IO)"))
        assertTrue("layerThumbnail must parallelize item decode", thumbBlock.contains("async(tileIoDispatcher)"))

        val composeLayersStart = inkCtrl.indexOf("suspend fun composeDisplayLayers(")
        val composeLayersEnd = inkCtrl.indexOf("internal fun liveEraserStroke(", composeLayersStart)
        val composeLayersBlock = inkCtrl.substring(composeLayersStart, composeLayersEnd)
        assertTrue("composeDisplayLayers must parallelize visible layers", composeLayersBlock.contains("async(tileIoDispatcher)"))

        val noteRepo = File(
            repoRoot,
            "app/src/main/java/personal/notes/touch/data/repo/NoteRepository.kt"
        ).readText()
        val loadTilesStart = noteRepo.indexOf("suspend fun loadPageLayerTiles(")
        val loadTilesEnd = noteRepo.indexOf("internal suspend fun savePageLayerOps(", loadTilesStart)
        val loadTilesBlock = noteRepo.substring(loadTilesStart, loadTilesEnd)
        assertTrue("loadPageLayerTiles must decode tiles in parallel", loadTilesBlock.contains("async(tileDecodeDispatcher)"))

        val bitmapUtils = File(
            repoRoot,
            "app/src/main/java/personal/notes/touch/util/BitmapUtils.kt"
        ).readText()
        assertTrue("BitmapUtils must use BitmapPool.decodeFileMutableArgb", bitmapUtils.contains("BitmapPool.decodeFileMutableArgb("))
        assertTrue("BitmapUtils must parallelize thumbnail tile decode", bitmapUtils.contains("async(tileDecodeDispatcher)"))
    }

    private fun resolveRepoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "app/src/main").isDirectory) cwd else cwd.parentFile ?: cwd
    }
}
