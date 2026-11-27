package personal.notes.touch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.util.ThumbnailPolicy
import java.io.File

/**
 * Pure-JVM unit tests for `PDF-05` (page drawer & scrubber thumbnails) and
 * `PDF-06` (Home screen library card thumbnail composition & invalidation).
 */
class ThumbnailPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun continuousCropFractionAndPixelRect_cropsPdfCenterThird_andFullA4() {
        val (pdfL, pdfR) = ThumbnailPolicy.continuousCropFraction("pdf")
        assertEquals(1f / 3f, pdfL, 1e-4f)
        assertEquals(2f / 3f, pdfR, 1e-4f)
        assertArrayEquals(
            intArrayOf(2160, 0, 4320, 1215),
            ThumbnailPolicy.continuousPixelCropRect(6480, 1215, "pdf")
        )

        val (a4L, a4R) = ThumbnailPolicy.continuousCropFraction("a4")
        assertEquals(0f, a4L, 1e-5f)
        assertEquals(1f, a4R, 1e-5f)
        assertArrayEquals(
            intArrayOf(0, 0, 2160, 3056),
            ThumbnailPolicy.continuousPixelCropRect(2160, 3056, "a4")
        )

        assertArrayEquals(
            intArrayOf(0, 0, 0, 0),
            ThumbnailPolicy.continuousPixelCropRect(0, 0, "pdf")
        )
    }

    @Test
    fun continuousPixelCropRect_tinyWidthsProduceNonZeroPositiveCrop() {
        val w2 = ThumbnailPolicy.continuousPixelCropRect(2, 100, "pdf")
        assertArrayEquals(intArrayOf(1, 0, 2, 100), w2)
        assertTrue(w2[2] > w2[0])

        val w1 = ThumbnailPolicy.continuousPixelCropRect(1, 100, "pdf")
        assertArrayEquals(intArrayOf(0, 0, 1, 100), w1)
        assertTrue(w1[2] > w1[0])

        for (w in 1..50) {
            val crop = ThumbnailPolicy.continuousPixelCropRect(w, 80, "pdf")
            assertTrue("Expected positive width for w=$w", crop[2] > crop[0])
            assertTrue(crop[0] >= 0 && crop[2] <= w)
            assertEquals(0, crop[1])
            assertEquals(80, crop[3])
        }
    }

    @Test
    fun continuousComposeWidthPx_scalesByMarginSpan() {
        assertEquals(1020, ThumbnailPolicy.continuousComposeWidthPx(340, "pdf"))
        assertEquals(340, ThumbnailPolicy.continuousComposeWidthPx(340, "a4"))
        assertEquals(720, ThumbnailPolicy.continuousComposeWidthPx(240, "pdf"))
    }

    @Test
    fun planThumbnailComposition_handlesPdfA4InfiniteCoverAndBackgroundOverrides() {
        val pdfPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = true,
            hasAnnotationLayers = true,
            hasImageLayers = true
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.VECTOR_PDF, pdfPlan.background)
        assertEquals(
            listOf(
                ThumbnailPolicy.OverlayStep.STROKES_PNG,
                ThumbnailPolicy.OverlayStep.IMAGE_LAYERS,
                ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS
            ),
            pdfPlan.overlays
        )

        val pdfHiddenBasePlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = true,
            hasAnnotationLayers = true,
            hasImageLayers = false,
            baseLayerVisible = false
        )
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), pdfHiddenBasePlan.overlays)

        val continuousA4BlankPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "a4",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = false,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.BLANK_WHITE, continuousA4BlankPlan.background)
        assertEquals(listOf(ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS), continuousA4BlankPlan.overlays)

        val infinitePlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "infinite",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = true,
            hasBackgroundOverride = false,
            hasStrokesFile = false,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.PAGE_FILE, infinitePlan.background)
        assertTrue(infinitePlan.overlays.isEmpty())

        val coverPlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "pdf",
            isVectorPdf = true,
            hasCoverOverride = true,
            hasPageFile = true,
            hasBackgroundOverride = true,
            hasStrokesFile = true,
            hasAnnotationLayers = true,
            hasImageLayers = true
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.COVER_FILE, coverPlan.background)
        assertTrue(coverPlan.overlays.isEmpty())

        val bgOverridePlan = ThumbnailPolicy.planThumbnailComposition(
            pageType = "a4",
            isVectorPdf = false,
            hasCoverOverride = false,
            hasPageFile = true,
            hasBackgroundOverride = true,
            hasStrokesFile = true,
            hasAnnotationLayers = true,
            hasImageLayers = false
        )
        assertEquals(ThumbnailPolicy.BackgroundSource.BACKGROUND_OVERRIDE, bgOverridePlan.background)
        assertEquals(
            listOf(
                ThumbnailPolicy.OverlayStep.STROKES_PNG,
                ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS
            ),
            bgOverridePlan.overlays
        )
    }

    @Test
    fun readPageDiskMtimesAndCacheKey_invalidatesOnAnnotOpsMetaTileLayersStrokesAndBackground() {
        val noteDir = tempFolder.newFolder("note_1")
        val layersDir = File(noteDir, "layers").apply { mkdirs() }

        fun keyNow(updatedAt: Long = 0L): String {
            val mtimes = ThumbnailPolicy.readPageDiskMtimes(noteDir, pageIndex = 0)
            return ThumbnailPolicy.buildPageThumbnailCacheKey(
                noteId = "note_1",
                pageIndex = 0,
                pageType = "pdf",
                pdfSourcePath = "note/note_1/doc.pdf",
                pdfPageIndex = 0,
                mtimes = mtimes,
                savedGeneration = 0,
                longSidePx = 340,
                noteUpdatedAt = updatedAt
            )
        }

        val baseKey = keyNow()
        assertFalse(ThumbnailPolicy.readPageDiskMtimes(noteDir, 0).hasAnyAnnotationFiles)

        val opsFile = File(layersDir, "page_0_annot_1_ops.json").apply {
            writeText("[]")
            setLastModified(10_000L)
        }
        val afterOpsKey = keyNow()
        assertNotEquals(baseKey, afterOpsKey)
        assertTrue(ThumbnailPolicy.readPageDiskMtimes(noteDir, 0).hasAnyAnnotationFiles)

        opsFile.setLastModified(20_000L)
        assertNotEquals(afterOpsKey, keyNow())

        val metaFile = File(layersDir, "page_0_annot.json").apply {
            writeText("{}")
            setLastModified(30_000L)
        }
        val afterMetaKey = keyNow()
        metaFile.setLastModified(35_000L)
        assertNotEquals(afterMetaKey, keyNow())

        File(layersDir, "page_0_annot_1_t0_0.png").apply {
            writeText("png")
            setLastModified(40_000L)
        }
        val afterTileKey = keyNow()
        assertNotEquals(afterMetaKey, afterTileKey)

        File(layersDir, "page_0_layers.json").apply {
            writeText("[]")
            setLastModified(50_000L)
        }
        val afterImgLayersKey = keyNow()
        assertNotEquals(afterTileKey, afterImgLayersKey)

        File(noteDir, "strokes_0.png").apply {
            writeText("strokes")
            setLastModified(60_000L)
        }
        val afterStrokesKey = keyNow()
        assertNotEquals(afterImgLayersKey, afterStrokesKey)

        File(noteDir, "background_0.png").apply {
            writeText("bg")
            setLastModified(70_000L)
        }
        val afterBgKey = keyNow()
        assertNotEquals(afterStrokesKey, afterBgKey)

        // Page 1 edits do not collide with page 0 mtimes
        File(layersDir, "page_1_annot_1_ops.json").apply {
            writeText("[]")
            setLastModified(90_000L)
        }
        assertEquals(afterBgKey, keyNow())
    }

    @Test
    fun readPageDiskMtimesAndCacheKey_invalidatesOnSameMtimeLengthChanges() {
        val noteDir = tempFolder.newFolder("note_same_mtime")
        val layersDir = File(noteDir, "layers").apply { mkdirs() }
        val coverFile = File(noteDir, "cover.png")
        val fixedMtime = 1_700_000_000_000L

        fun keyNow(): String {
            val mtimes = ThumbnailPolicy.readPageDiskMtimes(
                noteDir = noteDir,
                pageIndex = 0,
                coverFile = coverFile
            )
            return ThumbnailPolicy.buildPageThumbnailCacheKey(
                noteId = "note_same_mtime",
                pageIndex = 0,
                pageType = "pdf",
                pdfSourcePath = "note/note_same_mtime/doc.pdf",
                pdfPageIndex = 0,
                mtimes = mtimes,
                savedGeneration = 0,
                longSidePx = 340,
                noteUpdatedAt = 1234L
            )
        }

        val opsFile = File(layersDir, "page_0_annot_1_ops.json").apply {
            writeText("[]")
            setLastModified(fixedMtime)
        }
        val kOps1 = keyNow()
        opsFile.writeText("[{\"type\":\"stroke\",\"points\":[0.5,0.5]}]")
        opsFile.setLastModified(fixedMtime)
        val kOps2 = keyNow()
        assertNotEquals("Same-mtime _ops.json length change must invalidate cache key", kOps1, kOps2)

        val tileFile = File(layersDir, "page_0_annot_1_t0_0.png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            setLastModified(fixedMtime)
        }
        val kTile1 = keyNow()
        tileFile.writeBytes(ByteArray(26) { it.toByte() })
        tileFile.setLastModified(fixedMtime)
        val kTile2 = keyNow()
        assertNotEquals("Same-mtime tile png length change must invalidate cache key", kTile1, kTile2)

        val pageFile = File(noteDir, "page_0.png").apply {
            writeBytes(ByteArray(8))
            setLastModified(fixedMtime)
        }
        val kPage1 = keyNow()
        pageFile.writeBytes(ByteArray(24))
        pageFile.setLastModified(fixedMtime)
        assertNotEquals("Same-mtime page_0.png length change must invalidate cache key", kPage1, keyNow())

        val strokesFile = File(noteDir, "strokes_0.png").apply {
            writeBytes(ByteArray(6))
            setLastModified(fixedMtime)
        }
        val kStrokes1 = keyNow()
        strokesFile.writeBytes(ByteArray(18))
        strokesFile.setLastModified(fixedMtime)
        assertNotEquals("Same-mtime strokes_0.png length change must invalidate cache key", kStrokes1, keyNow())

        val bgFile = File(noteDir, "background_0.png").apply {
            writeBytes(ByteArray(5))
            setLastModified(fixedMtime)
        }
        val kBg1 = keyNow()
        bgFile.writeBytes(ByteArray(15))
        bgFile.setLastModified(fixedMtime)
        assertNotEquals("Same-mtime background_0.png length change must invalidate cache key", kBg1, keyNow())

        val metaFile = File(layersDir, "page_0_annot.json").apply {
            writeText("{}")
            setLastModified(fixedMtime)
        }
        val kMeta1 = keyNow()
        metaFile.writeText("{\"current\":1,\"layers\":[]}")
        metaFile.setLastModified(fixedMtime)
        assertNotEquals("Same-mtime annot meta length change must invalidate cache key", kMeta1, keyNow())

        val imgLayersFile = File(layersDir, "page_0_layers.json").apply {
            writeText("[]")
            setLastModified(fixedMtime)
        }
        val kImg1 = keyNow()
        imgLayersFile.writeText("[{\"file\":\"p0.png\"}]")
        imgLayersFile.setLastModified(fixedMtime)
        assertNotEquals("Same-mtime image layers length change must invalidate cache key", kImg1, keyNow())

        coverFile.writeBytes(ByteArray(7))
        coverFile.setLastModified(fixedMtime)
        val kCover1 = keyNow()
        coverFile.writeBytes(ByteArray(19))
        coverFile.setLastModified(fixedMtime)
        assertNotEquals("Same-mtime cover length change must invalidate cache key", kCover1, keyNow())
    }

    @Test
    fun homePreviewSeedKey_distinguishesUpdatedAtCoverPathAndPageType() {
        val k1 = ThumbnailPolicy.homePreviewSeedKey("n1", 100L, null, "pdf")
        val k2 = ThumbnailPolicy.homePreviewSeedKey("n1", 200L, null, "pdf")
        val k3 = ThumbnailPolicy.homePreviewSeedKey("n1", 200L, "/cover.png", "pdf")
        val k4 = ThumbnailPolicy.homePreviewSeedKey("n1", 200L, null, "a4")
        assertNotEquals(k1, k2)
        assertNotEquals(k2, k3)
        assertNotEquals(k2, k4)
    }
}
