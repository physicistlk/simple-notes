package personal.notes.touch.util

import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import java.io.File
import kotlin.math.roundToInt

/**
 * Pure-JVM thumbnail composition, crop geometry, and disk-signature cache key
 * policy shared by page drawer thumbnails (`PDF-05`), the M2 scrubber preview,
 * and Home library card previews (`PDF-06`).
 */
object ThumbnailPolicy {

    enum class BackgroundSource {
        COVER_FILE,
        BACKGROUND_OVERRIDE,
        VECTOR_PDF,
        PAGE_FILE,
        BLANK_WHITE,
        NONE
    }

    enum class OverlayStep {
        STROKES_PNG,
        IMAGE_LAYERS,
        CONTINUOUS_ANNOTATIONS
    }

    data class ThumbnailCompositionPlan(
        val background: BackgroundSource,
        val overlays: List<OverlayStep>
    )

    data class PageDiskMtimes(
        val pageMtime: Long = 0L,
        val backgroundMtime: Long = 0L,
        val strokesMtime: Long = 0L,
        val annotMetaMtime: Long = 0L,
        val maxAnnotLayerMtime: Long = 0L,
        val annotFileCount: Int = 0,
        val imageLayersMtime: Long = 0L,
        val coverMtime: Long = 0L,
        val pageBytes: Long = 0L,
        val backgroundBytes: Long = 0L,
        val strokesBytes: Long = 0L,
        val annotMetaBytes: Long = 0L,
        val annotTotalBytes: Long = 0L,
        val imageLayersBytes: Long = 0L,
        val coverBytes: Long = 0L
    ) {
        val hasAnyAnnotationFiles: Boolean
            get() = annotMetaMtime > 0L || maxAnnotLayerMtime > 0L || annotFileCount > 0 ||
                annotMetaBytes > 0L || annotTotalBytes > 0L
    }

    fun planThumbnailComposition(
        pageType: String?,
        isVectorPdf: Boolean,
        hasCoverOverride: Boolean,
        hasPageFile: Boolean,
        hasBackgroundOverride: Boolean,
        hasStrokesFile: Boolean,
        hasAnnotationLayers: Boolean,
        hasImageLayers: Boolean,
        baseLayerVisible: Boolean = true
    ): ThumbnailCompositionPlan {
        if (hasCoverOverride) {
            return ThumbnailCompositionPlan(BackgroundSource.COVER_FILE, emptyList())
        }
        // Infinite previews are pre-rendered into page_<i>.png by generateAndSavePagePreview
        // (including placed items and world-fitted ops); re-compositing normalized ops would double-draw.
        if (pageType == "infinite" && hasPageFile) {
            return ThumbnailCompositionPlan(BackgroundSource.PAGE_FILE, emptyList())
        }

        val overlays = buildList {
            if (hasStrokesFile && (!isVectorPdf || baseLayerVisible)) {
                add(OverlayStep.STROKES_PNG)
            }
            if (hasImageLayers) {
                add(OverlayStep.IMAGE_LAYERS)
            }
            if (hasAnnotationLayers) {
                add(OverlayStep.CONTINUOUS_ANNOTATIONS)
            }
        }

        val background = when {
            hasBackgroundOverride -> BackgroundSource.BACKGROUND_OVERRIDE
            isVectorPdf -> BackgroundSource.VECTOR_PDF
            hasPageFile -> BackgroundSource.PAGE_FILE
            overlays.isNotEmpty() -> BackgroundSource.BLANK_WHITE
            else -> BackgroundSource.NONE
        }

        return ThumbnailCompositionPlan(background = background, overlays = overlays)
    }

    fun continuousCropFraction(pageType: String?): Pair<Float, Float> {
        val margin = ContinuousPageGeometry.marginFor(pageType)
        val span = 1f + 2f * margin
        return (margin / span) to ((margin + 1f) / span)
    }

    fun continuousPixelCropRect(layerWidth: Int, layerHeight: Int, pageType: String?): IntArray {
        if (layerWidth <= 0 || layerHeight <= 0) return intArrayOf(0, 0, 0, 0)
        val (leftFrac, rightFrac) = continuousCropFraction(pageType)
        val left = (layerWidth * leftFrac).roundToInt().coerceIn(0, (layerWidth - 1).coerceAtLeast(0))
        val right = (layerWidth * rightFrac).roundToInt().coerceIn((left + 1).coerceAtMost(layerWidth), layerWidth)
        return intArrayOf(left, 0, right, layerHeight)
    }

    fun continuousComposeWidthPx(targetPageWidthPx: Int, pageType: String?): Int {
        val span = 1f + 2f * ContinuousPageGeometry.marginFor(pageType)
        return (targetPageWidthPx.coerceAtLeast(1) * span).roundToInt().coerceAtLeast(1)
    }

    fun readPageDiskMtimes(
        noteDir: File,
        pageIndex: Int,
        coverFile: File? = null
    ): PageDiskMtimes {
        val pageFile = File(noteDir, "page_${pageIndex}.png")
        val bgFile = File(noteDir, "background_${pageIndex}.png")
        val strokesFile = File(noteDir, "strokes_${pageIndex}.png")
        val layersDir = File(noteDir, "layers")
        val imageLayersFile = File(layersDir, "page_${pageIndex}_layers.json")
        val annotMetaFile = File(layersDir, "page_${pageIndex}_annot.json")

        val annotPrefix = "page_${pageIndex}_annot_"
        val layerPngPrefix = "page_${pageIndex}_layer_"
        var maxAnnotMtime = 0L
        var annotTotalBytes = 0L
        var annotCount = 0
        var maxImageLayerMtime = if (imageLayersFile.isFile) imageLayersFile.lastModified() else 0L
        var totalImageLayerBytes = if (imageLayersFile.isFile) imageLayersFile.length() else 0L

        if (layersDir.isDirectory) {
            layersDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith(annotPrefix) && (name.endsWith(".png") || name.endsWith(".json"))) {
                    annotCount++
                    val m = file.lastModified()
                    if (m > maxAnnotMtime) maxAnnotMtime = m
                    annotTotalBytes += file.length()
                } else if (name.startsWith(layerPngPrefix) && name.endsWith(".png")) {
                    val m = file.lastModified()
                    if (m > maxImageLayerMtime) maxImageLayerMtime = m
                    totalImageLayerBytes += file.length()
                }
            }
        }

        return PageDiskMtimes(
            pageMtime = if (pageFile.isFile) pageFile.lastModified() else 0L,
            backgroundMtime = if (bgFile.isFile) bgFile.lastModified() else 0L,
            strokesMtime = if (strokesFile.isFile) strokesFile.lastModified() else 0L,
            annotMetaMtime = if (annotMetaFile.isFile) annotMetaFile.lastModified() else 0L,
            maxAnnotLayerMtime = maxAnnotMtime,
            annotFileCount = annotCount,
            imageLayersMtime = maxImageLayerMtime,
            coverMtime = if (coverFile != null && coverFile.isFile) coverFile.lastModified() else 0L,
            pageBytes = if (pageFile.isFile) pageFile.length() else 0L,
            backgroundBytes = if (bgFile.isFile) bgFile.length() else 0L,
            strokesBytes = if (strokesFile.isFile) strokesFile.length() else 0L,
            annotMetaBytes = if (annotMetaFile.isFile) annotMetaFile.length() else 0L,
            annotTotalBytes = annotTotalBytes,
            imageLayersBytes = totalImageLayerBytes,
            coverBytes = if (coverFile != null && coverFile.isFile) coverFile.length() else 0L
        )
    }

    fun buildPageThumbnailCacheKey(
        noteId: String,
        pageIndex: Int,
        pageType: String,
        pdfSourcePath: String,
        pdfPageIndex: Int,
        mtimes: PageDiskMtimes,
        savedGeneration: Int,
        longSidePx: Int,
        noteUpdatedAt: Long = 0L
    ): String = buildString {
        append("thumb:")
        append(noteId)
        append(':')
        append(pageIndex)
        append(':')
        append(pageType)
        append(':')
        append(pdfSourcePath)
        append('#')
        append(pdfPageIndex)
        append(":p").append(mtimes.pageMtime).append('.').append(mtimes.pageBytes)
        append(":b").append(mtimes.backgroundMtime).append('.').append(mtimes.backgroundBytes)
        append(":s").append(mtimes.strokesMtime).append('.').append(mtimes.strokesBytes)
        append(":am").append(mtimes.annotMetaMtime).append('.').append(mtimes.annotMetaBytes)
        append(":al").append(mtimes.maxAnnotLayerMtime).append('.').append(mtimes.annotTotalBytes)
        append(":ac").append(mtimes.annotFileCount)
        append(":il").append(mtimes.imageLayersMtime).append('.').append(mtimes.imageLayersBytes)
        append(":cv").append(mtimes.coverMtime).append('.').append(mtimes.coverBytes)
        append(":g").append(savedGeneration)
        append(":l").append(longSidePx)
        append(":u").append(noteUpdatedAt)
    }

    fun homePreviewSeedKey(
        noteId: String,
        noteUpdatedAt: Long,
        coverPath: String?,
        pageType: String
    ): String = "home_seed:$noteId:$noteUpdatedAt:${coverPath.orEmpty()}:$pageType"
}
