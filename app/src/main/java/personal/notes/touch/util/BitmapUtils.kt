package personal.notes.touch.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ContinuousStrokeRenderer
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.drawImageLayers
import personal.notes.touch.ui.canvas.readPageLayersFromDisk
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.roundToInt

object BitmapUtils {

    private val thumbnailCache: MutableMap<String, Bitmap> =
        Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
                return size > 64
            }
        })

    fun getCachedThumbnail(key: String): Bitmap? {
        val bmp = thumbnailCache[key] ?: return null
        if (bmp.isRecycled) {
            thumbnailCache.remove(key)
            return null
        }
        return bmp
    }

    fun putCachedThumbnail(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            thumbnailCache[key] = bitmap
        }
    }

    fun invalidateThumbnail(key: String) {
        thumbnailCache.remove(key)
    }

    fun pageThumbnailCacheKey(
        noteId: String,
        fileStore: NoteFileStore,
        page: PageEntity,
        pageType: String = "a4",
        longSidePx: Int = 340,
        savedGeneration: Int = 0,
        coverFile: File? = null,
        noteUpdatedAt: Long = 0L
    ): String {
        val mtimes = ThumbnailPolicy.readPageDiskMtimes(
            noteDir = fileStore.getNoteDir(noteId),
            pageIndex = page.index,
            coverFile = coverFile
        )
        return ThumbnailPolicy.buildPageThumbnailCacheKey(
            noteId = noteId,
            pageIndex = page.index,
            pageType = pageType,
            pdfSourcePath = page.pdfSourcePath,
            pdfPageIndex = page.pdfPageIndex,
            mtimes = mtimes,
            savedGeneration = savedGeneration,
            longSidePx = longSidePx,
            noteUpdatedAt = noteUpdatedAt
        )
    }

    /** Fast non-suspending lookup of an already-cached page thumbnail (e.g. for scrubber bubble). */
    fun getCachedPageThumbnailIfPresent(
        noteId: String,
        fileStore: NoteFileStore,
        page: PageEntity,
        pageType: String = "a4",
        longSidePx: Int = 340,
        savedGeneration: Int = 0,
        coverFile: File? = null,
        noteUpdatedAt: Long = 0L
    ): Bitmap? = getCachedThumbnail(
        pageThumbnailCacheKey(
            noteId = noteId,
            fileStore = fileStore,
            page = page,
            pageType = pageType,
            longSidePx = longSidePx,
            savedGeneration = savedGeneration,
            coverFile = coverFile,
            noteUpdatedAt = noteUpdatedAt
        )
    )

    /**
     * Loads or renders a page thumbnail (compositing background override / vector PDF / page PNG
     * with `strokes_<i>.png`, floating `ImageLayer`s, and visible continuous annotation layers)
     * and caches the result in [thumbnailCache], returning `(bitmapOrNull, pageAspect)`.
     */
    suspend fun loadOrRenderPageThumbnail(
        noteId: String,
        fileStore: NoteFileStore,
        page: PageEntity,
        pdfDocCache: PdfDocumentCache?,
        aspectCache: PageAspectCache = PageAspectCache.default,
        pageType: String = "a4",
        longSidePx: Int = 340,
        savedGeneration: Int = 0,
        coverFile: File? = null,
        noteUpdatedAt: Long = 0L
    ): Pair<Bitmap?, Float> = withContext(Dispatchers.IO) {
        val validCover = coverFile?.takeIf { it.isFile }
        val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
        val pageFile = fileStore.getPageFile(noteId, page.index)
        val bgFile = fileStore.getPageBackgroundFile(noteId, page.index)
        val strokesFile = fileStore.getStrokesFile(noteId, page.index)

        val ratio = when {
            validCover != null -> readImageAspect(validCover, PdfPageGeometry.DEFAULT_ASPECT)
            isVector -> aspectCache.getOrLoad(
                page.pdfSourcePath,
                page.pdfPageIndex,
                fallback = PdfPageGeometry.DEFAULT_ASPECT
            ) {
                pdfDocCache?.document(page.pdfSourcePath)?.pageSize(page.pdfPageIndex)
            }
            bgFile.isFile -> readImageAspect(bgFile, PdfPageGeometry.DEFAULT_ASPECT)
            pageFile.isFile -> readImageAspect(pageFile, PdfPageGeometry.DEFAULT_ASPECT)
            else -> PdfPageGeometry.DEFAULT_ASPECT
        }

        val mtimes = ThumbnailPolicy.readPageDiskMtimes(
            noteDir = fileStore.getNoteDir(noteId),
            pageIndex = page.index,
            coverFile = validCover
        )
        val cacheKey = ThumbnailPolicy.buildPageThumbnailCacheKey(
            noteId = noteId,
            pageIndex = page.index,
            pageType = pageType,
            pdfSourcePath = page.pdfSourcePath,
            pdfPageIndex = page.pdfPageIndex,
            mtimes = mtimes,
            savedGeneration = savedGeneration,
            longSidePx = longSidePx,
            noteUpdatedAt = noteUpdatedAt
        )
        getCachedThumbnail(cacheKey)?.let { cached ->
            return@withContext cached to ratio
        }

        val annotMetaFile = fileStore.getAnnotationMetaFile(noteId, page.index)
        val stack = readAnnotationStack(annotMetaFile)
        val baseLayerVisible = stack.layerAt(PageLayerStack.BASE_LAYER_INDEX)?.visible != false

        val plan = ThumbnailPolicy.planThumbnailComposition(
            pageType = pageType,
            isVectorPdf = isVector,
            hasCoverOverride = validCover != null,
            hasPageFile = pageFile.isFile,
            hasBackgroundOverride = bgFile.isFile,
            hasStrokesFile = strokesFile.isFile,
            hasAnnotationLayers = mtimes.hasAnyAnnotationFiles,
            hasImageLayers = mtimes.imageLayersMtime > 0L,
            baseLayerVisible = baseLayerVisible
        )

        val (thumbW, thumbH) = PdfPageGeometry.thumbnailSize(ratio, longSidePx = longSidePx)

        val base = when (plan.background) {
            ThumbnailPolicy.BackgroundSource.COVER_FILE -> {
                if (validCover != null) loadThumbnail(validCover, targetWidth = thumbW, targetHeight = thumbH) else null
            }
            ThumbnailPolicy.BackgroundSource.BACKGROUND_OVERRIDE -> {
                loadThumbnail(bgFile, targetWidth = thumbW, targetHeight = thumbH)
            }
            ThumbnailPolicy.BackgroundSource.VECTOR_PDF -> {
                val baseKey = "pdf_base:${page.pdfSourcePath}#${page.pdfPageIndex}@${thumbW}x${thumbH}"
                getCachedThumbnail(baseKey) ?: try {
                    pdfDocCache?.document(page.pdfSourcePath)
                        ?.renderPage(page.pdfPageIndex, targetWidth = thumbW, targetHeight = thumbH)
                        ?.also { rendered -> putCachedThumbnail(baseKey, rendered) }
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            ThumbnailPolicy.BackgroundSource.PAGE_FILE -> {
                loadThumbnail(pageFile, targetWidth = thumbW, targetHeight = thumbH)
            }
            ThumbnailPolicy.BackgroundSource.BLANK_WHITE -> {
                try {
                    createSafeBitmap(thumbW, thumbH).apply { eraseColor(Color.WHITE) }
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    null
                }
            }
            ThumbnailPolicy.BackgroundSource.NONE -> null
        }

        if (plan.overlays.isEmpty()) {
            if (base != null && !base.isRecycled) {
                putCachedThumbnail(cacheKey, base)
            }
            return@withContext base to ratio
        }

        val composed: Bitmap = try {
            if (base != null && !base.isRecycled) {
                val copied = base.copy(Bitmap.Config.ARGB_8888, true)
                if (plan.background == ThumbnailPolicy.BackgroundSource.BLANK_WHITE && !base.isRecycled) {
                    base.recycle()
                }
                copied ?: return@withContext base to ratio
            } else {
                createSafeBitmap(thumbW, thumbH).apply { eraseColor(Color.WHITE) }
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return@withContext base to ratio
        }

        val canvas = android.graphics.Canvas(composed)
        val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val fullDst = Rect(0, 0, composed.width, composed.height)

        for (step in plan.overlays) {
            when (step) {
                ThumbnailPolicy.OverlayStep.STROKES_PNG -> {
                    val strokes = decodeDownsampledArgb(strokesFile, composed.width, composed.height)
                    if (strokes != null && !strokes.isRecycled) {
                        canvas.drawBitmap(strokes, null, fullDst, filterPaint)
                        strokes.recycle()
                    }
                }
                ThumbnailPolicy.OverlayStep.IMAGE_LAYERS -> {
                    val layers = readPageLayersFromDisk(fileStore, noteId, page.index)
                    if (!layers.isNullOrEmpty()) {
                        val (layerPageW, layerPageH) = readPagePixelDimensions(pageFile, ratio)
                        val save = canvas.save()
                        canvas.scale(
                            composed.width.toFloat() / layerPageW.toFloat(),
                            composed.height.toFloat() / layerPageH.toFloat()
                        )
                        drawImageLayers(canvas, layers, filterPaint)
                        canvas.restoreToCount(save)
                        for (layer in layers) {
                            if (!layer.bitmap.isRecycled) layer.bitmap.recycle()
                            for (crop in layer.ink) {
                                if (!crop.bitmap.isRecycled) crop.bitmap.recycle()
                            }
                        }
                    }
                }
                ThumbnailPolicy.OverlayStep.CONTINUOUS_ANNOTATIONS -> {
                    drawContinuousAnnotationsOntoThumbnail(
                        canvas = canvas,
                        fileStore = fileStore,
                        noteId = noteId,
                        pageIndex = page.index,
                        pageType = pageType,
                        pageAspect = ratio,
                        targetW = composed.width,
                        targetH = composed.height,
                        stack = stack,
                        metaFile = annotMetaFile,
                        paint = filterPaint
                    )
                }
            }
        }

        if (!composed.isRecycled) {
            putCachedThumbnail(cacheKey, composed)
        }
        composed to ratio
    }

    private fun readImageAspect(file: File, fallback: Float): Float {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            PdfPageGeometry.aspect(bounds.outWidth.toFloat(), bounds.outHeight.toFloat(), fallback)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun readPagePixelDimensions(pageFile: File, pageAspect: Float): Pair<Int, Int> {
        if (pageFile.isFile) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(pageFile.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                return bounds.outWidth to bounds.outHeight
            }
        }
        return PdfModeReconciliationPolicy.pagePixelSize(pageAspect)
    }

    private fun decodeDownsampledArgb(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            if (!file.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            val reqW = targetWidth.coerceAtLeast(1)
            val reqH = targetHeight.coerceAtLeast(1)
            while ((w / (sample * 2)) >= reqW && (h / (sample * 2)) >= reqH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readAnnotationStack(metaFile: File): PageLayerStack {
        return try {
            val text = if (metaFile.isFile) metaFile.readText() else null
            text?.let { PageLayerMeta.decode(it) } ?: PageLayerStack.base()
        } catch (_: Exception) {
            PageLayerStack.base()
        }
    }

    private fun drawContinuousAnnotationsOntoThumbnail(
        canvas: android.graphics.Canvas,
        fileStore: NoteFileStore,
        noteId: String,
        pageIndex: Int,
        pageType: String,
        pageAspect: Float,
        targetW: Int,
        targetH: Int,
        stack: PageLayerStack,
        metaFile: File,
        paint: Paint
    ) {
        val metaText = try {
            if (metaFile.isFile) metaFile.readText() else null
        } catch (_: Exception) {
            null
        }
        val tilesMap = metaText?.let { PageLayerMeta.decodeTiles(it) }.orEmpty()
        val isInfinite = ContinuousPageGeometry.isInfinite(pageType)
        val composeWidth = if (isInfinite) {
            targetW.coerceAtLeast(1)
        } else {
            ThumbnailPolicy.continuousComposeWidthPx(targetW, pageType)
        }
        val dstPageRect = Rect(0, 0, targetW, targetH)

        for (layer in stack.layers) {
            if (!layer.visible) continue
            val tilesInfo = tilesMap[layer.index]
            val opsFile = fileStore.getAnnotationLayerOpsFile(noteId, pageIndex, layer.index)
            val ops = try {
                if (opsFile.isFile) ContinuousOpCodec.decode(opsFile.readText()) else null
            } catch (_: Exception) {
                null
            }
            val legacyFile = fileStore.getAnnotationLayerFile(noteId, pageIndex, layer.index)
            val hasTiles = tilesInfo != null && tilesInfo.tiles.isNotEmpty()
            val hasOps = !ops.isNullOrEmpty()
            val hasLegacy = legacyFile.isFile
            if (!hasTiles && !hasOps && !hasLegacy) continue

            val (layerWidth, layerHeight) = when {
                tilesInfo != null && tilesInfo.width > 0 && tilesInfo.height > 0 ->
                    tilesInfo.width to tilesInfo.height
                hasLegacy -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(legacyFile.absolutePath, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        bounds.outWidth to bounds.outHeight
                    } else {
                        val sz = ContinuousPageGeometry.layerSize(pageType, pageAspect)
                        sz.width to sz.height
                    }
                }
                else -> {
                    val sz = ContinuousPageGeometry.layerSize(pageType, pageAspect)
                    sz.width to sz.height
                }
            }
            if (layerWidth <= 0 || layerHeight <= 0) continue

            val composeHeight = if (isInfinite && !hasTiles && !hasLegacy) {
                targetH.coerceAtLeast(1)
            } else {
                (composeWidth.toFloat() * layerHeight / layerWidth)
                    .roundToInt()
                    .coerceAtLeast(1)
            }
            val layerBmp = try {
                createSafeBitmap(composeWidth, composeHeight)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                continue
            }
            val layerCanvas = android.graphics.Canvas(layerBmp)

            if (hasTiles && tilesInfo != null) {
                for (pos in tilesInfo.positions()) {
                    val tx = pos.first
                    val ty = pos.second
                    val tileFile = fileStore.getAnnotationLayerTileFile(noteId, pageIndex, layer.index, tx, ty)
                    if (!tileFile.isFile) continue
                    val tileW = tilesInfo.tileSize.coerceAtLeast(1)
                    val tileLeft = tx * tileW
                    val tileTop = ty * tileW
                    val reqW = ((tileW.toFloat() / layerWidth) * composeWidth).roundToInt().coerceAtLeast(1)
                    val reqH = ((tileW.toFloat() / layerHeight) * composeHeight).roundToInt().coerceAtLeast(1)
                    val tileBmp = decodeDownsampledArgb(tileFile, reqW, reqH) ?: continue
                    if (!tileBmp.isRecycled) {
                        val srcBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(tileFile.absolutePath, srcBounds)
                        val origW = if (srcBounds.outWidth > 0) srcBounds.outWidth else tileBmp.width
                        val origH = if (srcBounds.outHeight > 0) srcBounds.outHeight else tileBmp.height
                        val dstTileRect = RectF(
                            tileLeft.toFloat() / layerWidth * composeWidth,
                            tileTop.toFloat() / layerHeight * composeHeight,
                            (tileLeft + origW).toFloat() / layerWidth * composeWidth,
                            (tileTop + origH).toFloat() / layerHeight * composeHeight
                        )
                        layerCanvas.drawBitmap(tileBmp, null, dstTileRect, paint)
                        tileBmp.recycle()
                    }
                }
            } else if (hasLegacy) {
                val legacyBmp = decodeDownsampledArgb(legacyFile, composeWidth, composeHeight)
                if (legacyBmp != null && !legacyBmp.isRecycled) {
                    layerCanvas.drawBitmap(
                        legacyBmp,
                        null,
                        RectF(0f, 0f, composeWidth.toFloat(), composeHeight.toFloat()),
                        paint
                    )
                    legacyBmp.recycle()
                }
            }

            if (!ops.isNullOrEmpty()) {
                if (ContinuousPageGeometry.isInfinite(pageType)) {
                    val hasErase = ops.any { it is InkOp.Erase }
                    val save = if (hasErase) {
                        layerCanvas.saveLayer(0f, 0f, composeWidth.toFloat(), composeHeight.toFloat(), null)
                    } else -1
                    for (op in ops) {
                        when (op) {
                            is InkOp.Stroke -> {
                                val shape = op.shape
                                if (shape == null) {
                                    ContinuousStrokeRenderer.drawStroke(
                                        layerCanvas, op.stroke, composeWidth.toFloat(), composeHeight.toFloat(), preview = false
                                    )
                                } else {
                                    val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                        color = op.stroke.color
                                        strokeWidth = op.stroke.baseWidthNorm * composeWidth
                                    }
                                    val sSave = layerCanvas.save()
                                    layerCanvas.scale(composeWidth.toFloat() / 2160f, composeHeight.toFloat() / 2160f)
                                    layerCanvas.drawPath(shape, shapePaint)
                                    layerCanvas.restoreToCount(sSave)
                                }
                            }
                            is InkOp.Erase -> {
                                ContinuousStrokeRenderer.drawStroke(
                                    layerCanvas, op.stroke, composeWidth.toFloat(), composeHeight.toFloat(), preview = false
                                )
                            }
                        }
                    }
                    if (save >= 0) layerCanvas.restoreToCount(save)
                } else {
                    val ratio = ContinuousPageGeometry.ratioOfLayer(pageType, layerWidth, layerHeight)
                    val boxRatio = ContinuousPageGeometry.boxRatio(pageType, ratio)
                    val rect = ContinuousPageGeometry.layerRect(pageType, ratio, boxRatio, 1f)
                    if (!rect.empty) {
                        val sx = composeWidth.toFloat() / rect.width
                        val sy = composeHeight.toFloat() / rect.height
                        val hasErase = ops.any { it is InkOp.Erase }
                        val eraseSave = if (hasErase) {
                            layerCanvas.saveLayer(0f, 0f, composeWidth.toFloat(), composeHeight.toFloat(), null)
                        } else -1
                        for (op in ops) {
                            when (op) {
                                is InkOp.Stroke -> {
                                    val shape = op.shape
                                    if (shape == null) {
                                        val save = layerCanvas.save()
                                        layerCanvas.translate(-rect.left * sx, -rect.top * sy)
                                        ContinuousStrokeRenderer.drawStroke(
                                            layerCanvas, op.stroke, sx, sy, preview = false
                                        )
                                        layerCanvas.restoreToCount(save)
                                    } else {
                                        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            style = Paint.Style.STROKE
                                            strokeCap = Paint.Cap.ROUND
                                            strokeJoin = Paint.Join.ROUND
                                            color = op.stroke.color
                                            strokeWidth = op.stroke.baseWidthNorm * composeWidth / rect.width
                                        }
                                        val save = layerCanvas.save()
                                        layerCanvas.scale(
                                            composeWidth.toFloat() / layerWidth,
                                            composeHeight.toFloat() / layerHeight
                                        )
                                        layerCanvas.drawPath(shape, shapePaint)
                                        layerCanvas.restoreToCount(save)
                                    }
                                }
                                is InkOp.Erase -> {
                                    val save = layerCanvas.save()
                                    layerCanvas.translate(-rect.left * sx, -rect.top * sy)
                                    ContinuousStrokeRenderer.drawStroke(
                                        layerCanvas, op.stroke, sx, sy, preview = false
                                    )
                                    layerCanvas.restoreToCount(save)
                                }
                            }
                        }
                        if (eraseSave >= 0) layerCanvas.restoreToCount(eraseSave)
                    }
                }
            }

            val crop = if (isInfinite) {
                intArrayOf(0, 0, layerBmp.width, layerBmp.height)
            } else {
                ThumbnailPolicy.continuousPixelCropRect(layerBmp.width, layerBmp.height, pageType)
            }
            if (crop[2] > crop[0] && crop[3] > crop[1]) {
                canvas.drawBitmap(
                    layerBmp,
                    Rect(crop[0], crop[1], crop[2], crop[3]),
                    dstPageRect,
                    paint
                )
            }
            if (!layerBmp.isRecycled) layerBmp.recycle()
        }
    }

    suspend fun loadThumbnail(file: File, targetWidth: Int = 360, targetHeight: Int = 500): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:${targetWidth}x${targetHeight}"
        getCachedThumbnail(cacheKey)?.let { return@withContext it }

        try {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null

            var sample = 1
            while ((w / (sample * 2)) >= targetWidth && (h / (sample * 2)) >= targetHeight) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            val out = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(raw, 0f, 0f, null)
            raw.recycle()
            putCachedThumbnail(cacheKey, out)
            out
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Same downsampling as [loadThumbnail] but keeps the alpha channel.
     *
     * [loadThumbnail] decodes RGB_565 for memory, which flattens transparency
     * to black — fine for a page, wrong for an overlay such as the saved ink
     * layer. Cached under a distinct key so the two configs cannot collide.
     */
    suspend fun loadAlphaThumbnail(
        file: File,
        targetWidth: Int = 360,
        targetHeight: Int = 500
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "alpha:${file.absolutePath}:${file.lastModified()}:${file.length()}"
        getCachedThumbnail(cacheKey)?.let { return@withContext it }
        try {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null
            var sample = 1
            while ((w / (sample * 2)) >= targetWidth && (h / (sample * 2)) >= targetHeight) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            putCachedThumbnail(cacheKey, decoded)
            decoded
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val imageCache: MutableMap<String, Bitmap> =
        Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(12, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
                return size > 12
            }
        })

    fun getCachedImage(path: String, lastModified: Long): Bitmap? =
        imageCache["$path:$lastModified"]

    fun invalidateImageCache(file: File) {
        val prefix = "${file.absolutePath}:"
        synchronized(imageCache) {
            val keys = imageCache.keys.filter { it.startsWith(prefix) }
            for (k in keys) {
                imageCache.remove(k)
            }
        }
    }

    /**
     * Decodes an image block attachment downsampled so its longest side is at
     * most [maxDimension] px (RGB_565). Small bounded cache: editor rows are
     * recycled by the LazyColumn and 12 entries cover what is on screen.
     */
    suspend fun loadImage(file: File, maxDimension: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        imageCache[cacheKey]?.let { return@withContext it }
        try {
            if (!file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null

            var sample = 1
            val longest = maxOf(w, h)
            while (longest / (sample * 2) >= maxDimension) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            val out = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(raw, 0f, 0f, null)
            raw.recycle()
            imageCache[cacheKey] = out
            out
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveBitmapToPng(bitmap: Bitmap, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            // A unique temp name: two writers (the debounced save and a page
            // flip) must never write into the same temp file, or the second
            // one truncates the first one's stream and the published page
            // file ends up half-written.
            val tempFile = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
            val compressed = FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            if (!compressed || !tempFile.exists()) {
                // Nothing was written: keep the file that is already there.
                tempFile.delete()
                return@withContext false
            }
            // Rename over the old file: the old content stays readable until
            // the new one is in place (deleting it first would leave the
            // page empty if the process dies here).
            if (!tempFile.renameTo(file)) {
                // Some filesystems refuse to rename onto an existing file.
                if (!file.delete() || !tempFile.renameTo(file)) {
                    tempFile.delete()
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadBitmapFromFile(file: File): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)?.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createSafeBitmap(width: Int, height: Int, maxWidth: Int = 4096, maxHeight: Int = 4096): Bitmap {
        val w = width.coerceIn(1, maxWidth)
        val h = height.coerceIn(1, maxHeight)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }
}
