package personal.notes.touch.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.text.MarkdownExport
import personal.notes.touch.ui.canvas.ImageLayer
import personal.notes.touch.ui.canvas.InkCrop
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ExportManager {

    private const val AUTHORITY = "personal.notes.touch.fileprovider"
    private const val MAX_BITMAP_DIMENSION = 2048

    /** How many exported files stay in the cache. */
    private const val MAX_EXPORT_FILES = 20

    private fun getExportCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }
        // Every export adds a file and the cache is never emptied by the
        // system in a predictable way: keep only the last few.
        try {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return dir
            files.drop(MAX_EXPORT_FILES).forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dir
    }

    /**
     * Exports a single page bitmap as a PNG image file.
     */
    suspend fun exportPageToPngFile(
        context: Context,
        noteTitle: String,
        pageIndex: Int,
        bitmap: Bitmap,
        backgroundBitmap: Bitmap? = null,
        imageLayers: List<ImageLayer> = emptyList()
    ): File = withContext(Dispatchers.IO) {
        val flattened = flattenBitmap(bitmap, backgroundBitmap, imageLayers)
        val safeTitle = sanitizeFilename(noteTitle)
        val file = File(getExportCacheDir(context), "${safeTitle}_page_${pageIndex + 1}.png")
        FileOutputStream(file).use { out ->
            flattened.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    /**
     * Exports a page bitmap directly to a destination OutputStream (e.g. SAF Uri).
     */
    suspend fun exportPageToOutputStream(
        bitmap: Bitmap,
        outputStream: OutputStream,
        backgroundBitmap: Bitmap? = null,
        imageLayers: List<ImageLayer> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val flattened = flattenBitmap(bitmap, backgroundBitmap, imageLayers)
        outputStream.use { out ->
            flattened.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Exports an entire multi-page note to a PDF file in the cache directory.
     */
    suspend fun exportNoteToPdfFile(
        context: Context,
        note: NoteEntity,
        repository: NoteRepository,
        fileStore: NoteFileStore,
        currentEditingBitmap: Bitmap? = null,
        currentPageIndex: Int = 0,
        currentEditingBackground: Bitmap? = null,
        currentEditingImageLayers: List<ImageLayer> = emptyList()
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(note.title)
        val pdfFile = File(getExportCacheDir(context), "$safeTitle.pdf")
        FileOutputStream(pdfFile).use { out ->
            exportNoteToPdfStream(note, repository, fileStore, currentEditingBitmap, currentPageIndex, out, currentEditingBackground, currentEditingImageLayers)
        }
        pdfFile
    }

    /**
     * Exports an entire multi-page note to an arbitrary destination OutputStream (e.g. SAF Document Uri).
     */
    suspend fun exportNoteToPdfStream(
        note: NoteEntity,
        repository: NoteRepository,
        fileStore: NoteFileStore,
        currentEditingBitmap: Bitmap?,
        currentPageIndex: Int,
        outputStream: OutputStream,
        currentEditingBackground: Bitmap? = null,
        currentEditingImageLayers: List<ImageLayer> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val pageCount = note.pageCount.coerceAtLeast(1)

        val hasTextContent = !note.content.isNullOrBlank()

        // Standard A4 dimensions in Points (72 dpi): 595 x 842 pt
        val pdfPageWidth = 595
        val pdfPageHeight = 842

        var pdfPageNumber = 1

        // If rich text note exists, render text cover/first page
        if (hasTextContent) {
            val pageInfo = PdfDocument.PageInfo.Builder(pdfPageWidth, pdfPageHeight, pdfPageNumber++).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            renderTextContentToCanvas(
                canvas = canvas,
                title = note.title,
                content = note.content ?: "",
                width = pdfPageWidth,
                height = pdfPageHeight
            )

            document.finishPage(page)
        }

        // Render each canvas page
        for (pageIdx in 0 until pageCount) {
            val bitmap = if (pageIdx == currentPageIndex && currentEditingBitmap != null) {
                flattenBitmap(currentEditingBitmap, currentEditingBackground, currentEditingImageLayers)
            } else {
                loadFlattenedPageBitmap(fileStore, note.id, pageIdx)
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pdfPageWidth, pdfPageHeight, pdfPageNumber++).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Fill page background white
            canvas.drawColor(Color.WHITE)

            if (bitmap != null) {
                val (scaledW, scaledH) = calculateScaledDimensions(
                    bitmap.width,
                    bitmap.height,
                    pdfPageWidth,
                    pdfPageHeight
                )
                val left = (pdfPageWidth - scaledW) / 2
                val top = (pdfPageHeight - scaledH) / 2
                val destRect = Rect(left, top, left + scaledW, top + scaledH)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(bitmap, null, destRect, paint)
            } else {
                // If page is blank, draw page number footer
                val footerPaint = Paint().apply {
                    color = Color.GRAY
                    textSize = 12f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("- Page ${pageIdx + 1} -", pdfPageWidth / 2f, pdfPageHeight - 30f, footerPaint)
            }

            document.finishPage(page)
            if (bitmap !== currentEditingBitmap && bitmap?.isRecycled == false) {
                bitmap.recycle()
            }
        }

        outputStream.use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    /**
     * Exports a text note as Markdown (.md) or Plain Text (.txt) file in cache.
     */
    suspend fun exportTextNoteToFile(
        context: Context,
        noteTitle: String,
        content: String,
        extension: String = "md"
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(noteTitle)
        val file = File(getExportCacheDir(context), "$safeTitle.$extension")
        val fullText = buildString {
            if (noteTitle.isNotBlank()) {
                appendLine("# $noteTitle")
                appendLine()
            }
            append(content)
        }
        file.writeText(fullText)
        file
    }

    /**
     * Text note → standalone HTML page (inline CSS, no external assets).
     * Returns the written file so it can be shared straight from the dialog.
     */
    suspend fun exportTextNoteToHtmlFile(
        context: Context,
        noteTitle: String,
        content: String
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(noteTitle)
        val file = File(getExportCacheDir(context), "$safeTitle.html")
        file.outputStream().use { out -> exportTextNoteToHtmlStream(noteTitle, content, out) }
        file
    }

    fun exportTextNoteToHtmlStream(title: String, content: String, out: OutputStream) {
        out.write(MarkdownExport.htmlDocument(title, content).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Text note → multi-page A4 PDF rendered from the markdown itself. */
    suspend fun exportTextNoteToPdfFile(
        context: Context,
        noteTitle: String,
        content: String
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(noteTitle)
        val file = File(getExportCacheDir(context), "$safeTitle.pdf")
        file.outputStream().use { out -> exportTextNoteToPdfStream(noteTitle, content, out) }
        file
    }

    fun exportTextNoteToPdfStream(title: String, content: String, out: OutputStream) {
        TextNotePdfExporter.write(title, content, out)
    }

    /**
     * Shares any file using Android's system share sheet with FileProvider.
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String = "Share Note"
    ) {
        val uri: Uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun renderTextContentToCanvas(
        canvas: Canvas,
        title: String,
        content: String,
        width: Int,
        height: Int
    ) {
        canvas.drawColor(Color.WHITE)

        val margin = 50f
        var y = 60f

        // Title
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(title.ifBlank { "Untitled Note" }, margin, y, titlePaint)
        y += 20f

        // Horizontal line
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1.5f
        }
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 24f

        // Body text lines
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            isAntiAlias = true
        }

        val lines = content.lines()
        for (line in lines) {
            if (y > height - margin) break // Stop if exceeding page height
            val cleanLine = line.replace(Regex("""[*_~`#>]"""), "")
            canvas.drawText(cleanLine, margin, y, bodyPaint)
            y += 18f
        }
    }

    private fun calculateScaledDimensions(srcW: Int, srcH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        val aspect = srcW.toFloat() / srcH.toFloat()
        return if (aspect > (maxW.toFloat() / maxH.toFloat())) {
            val w = maxW
            val h = (maxW / aspect).toInt()
            w to h
        } else {
            val h = maxH
            val w = (maxH * aspect).toInt()
            w to h
        }
    }


    /**
     * The ink a page carries outside its paper (the margins of an imported
     * PDF). It is exported with the page, otherwise the export would lose
     * everything the user wrote there.
     */
    private fun readDiskBeyondInk(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int
    ): List<InkCrop> {
        val manifest = fileStore.getBeyondInkFile(noteId, pageIndex)
        if (!manifest.exists()) return emptyList()
        val crops = mutableListOf<InkCrop>()
        try {
            val arr = org.json.JSONArray(manifest.readText())
            val layersDir = fileStore.getLayersDir(noteId)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("file", "")
                if (name.isEmpty()) continue
                val bmp = android.graphics.BitmapFactory
                    .decodeFile(java.io.File(layersDir, name).absolutePath) ?: continue
                crops.add(
                    InkCrop(bmp, obj.optDouble("x", 0.0).toFloat(), obj.optDouble("y", 0.0).toFloat())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return crops
    }

    /** The raster background a page picked out of a PDF (if there is one). */
    private suspend fun loadPageBackgroundOverride(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int
    ): Bitmap? {
        val file = fileStore.getPageBackgroundFile(noteId, pageIndex)
        if (!file.exists()) return null
        return personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file)
    }

    private fun readDiskImageLayers(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int
    ): List<ImageLayer> {
        val infoFile = fileStore.getLayerInfoFile(noteId, pageIndex)
        if (!infoFile.exists()) return emptyList()
        val layers = mutableListOf<ImageLayer>()
        try {
            val arr = org.json.JSONArray(infoFile.readText())
            val layersDir = fileStore.getLayersDir(noteId)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fileName = obj.getString("file")
                val bitmapFile = java.io.File(layersDir, fileName)
                val bitmap = android.graphics.BitmapFactory.decodeFile(bitmapFile.absolutePath)
                if (bitmap != null) {
                    // Ink written on the layer travels with it.
                    val ink = mutableListOf<InkCrop>()
                    val inkArr = obj.optJSONArray("ink")
                    if (inkArr != null) {
                        for (k in 0 until inkArr.length()) {
                            val inkObj = inkArr.optJSONObject(k) ?: continue
                            val inkName = inkObj.optString("file", "")
                            if (inkName.isEmpty()) continue
                            val inkBmp = android.graphics.BitmapFactory
                                .decodeFile(java.io.File(layersDir, inkName).absolutePath) ?: continue
                            ink.add(
                                InkCrop(
                                    inkBmp,
                                    inkObj.optDouble("x", 0.0).toFloat(),
                                    inkObj.optDouble("y", 0.0).toFloat()
                                )
                            )
                        }
                    }
                    layers.add(ImageLayer(
                        bitmap = bitmap,
                        x = obj.optDouble("x", 0.0).toFloat(),
                        y = obj.optDouble("y", 0.0).toFloat(),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        visible = obj.optBoolean("visible", true),
                        cropRect = null,
                        name = obj.optString("name", "Layer ${i + 1}"),
                        ink = ink
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return layers
    }

    suspend fun loadFlattenedPageBitmap(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int,
        // The layers are page scoped, so every page has its own.
        includeLayers: Boolean = true
    ): Bitmap? {
        val pageFile = fileStore.getPageFile(noteId, pageIndex)
        val strokesFile = fileStore.getStrokesFile(noteId, pageIndex)
        val layers = if (includeLayers) readDiskImageLayers(fileStore, noteId, pageIndex) else emptyList()
        // Ink written outside the paper and a picked page background belong
        // to the page and have to be part of the export as well.
        val beyondInk = readDiskBeyondInk(fileStore, noteId, pageIndex)
        val backgroundOverride = loadPageBackgroundOverride(fileStore, noteId, pageIndex)
        // The annotation layer of the page (layer 1): the writing of the user,
        // also the notes in the margin beside the page.
        val annotationLayer = loadAnnotationLayer(fileStore, noteId, pageIndex)
        val result = if (strokesFile.exists()) {
            val baseBmp = BitmapUtils.loadBitmapFromFile(pageFile)
            val strokeBmp = BitmapUtils.loadBitmapFromFile(strokesFile)
            if (baseBmp != null && strokeBmp != null) {
                val flat = flattenBitmap(strokeBmp, backgroundOverride ?: baseBmp, layers, beyondInk, annotationLayer)
                if (baseBmp !== flat && !baseBmp.isRecycled) baseBmp.recycle()
                if (strokeBmp !== flat && !strokeBmp.isRecycled) strokeBmp.recycle()
                flat
            } else if (strokeBmp != null) {
                val flat = flattenBitmap(strokeBmp, backgroundOverride, layers, beyondInk, annotationLayer)
                if (strokeBmp !== flat && !strokeBmp.isRecycled) strokeBmp.recycle()
                flat
            } else if (baseBmp != null) {
                val flat = flattenBitmap(baseBmp, backgroundOverride, layers, beyondInk, annotationLayer)
                if (baseBmp !== flat && !baseBmp.isRecycled) baseBmp.recycle()
                flat
            } else null
        } else {
            val base = BitmapUtils.loadBitmapFromFile(pageFile)
            if (base != null) {
                val flat = flattenBitmap(base, backgroundOverride, layers, beyondInk, annotationLayer)
                if (base !== flat && !base.isRecycled) base.recycle()
                flat
            } else if (layers.isNotEmpty() || annotationLayer != null) {
                val blank = Bitmap.createBitmap(2160, 3056, Bitmap.Config.ARGB_8888)
                val flat = flattenBitmap(blank, backgroundOverride, layers, beyondInk, annotationLayer)
                if (blank !== flat && !blank.isRecycled) blank.recycle()
                flat
            } else null
        }
        for (l in layers) {
            if (!l.bitmap.isRecycled) l.bitmap.recycle()
            // The ink crops of a layer are decoded bitmaps as well.
            for (crop in l.ink) {
                if (!crop.bitmap.isRecycled) crop.bitmap.recycle()
            }
        }
        for (crop in beyondInk) {
            if (!crop.bitmap.isRecycled) crop.bitmap.recycle()
        }
        if (annotationLayer != null && !annotationLayer.isRecycled) annotationLayer.recycle()
        if (backgroundOverride != null && !backgroundOverride.isRecycled) backgroundOverride.recycle()
        return result
    }

    /**
     * The annotation layers of a page, drawn on top of each other.
     *
     * The layers are as wide as the reader's page box - the page plus the
     * margin on both sides - so the notes the user wrote beside the page are
     * part of them. A hidden layer is not part of the export: it is not
     * drawn on the page either.
     */
    private suspend fun loadAnnotationLayer(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int
    ): Bitmap? {
        val stack = readAnnotationStack(fileStore, noteId, pageIndex)
        var result: Bitmap? = null
        for (layer in stack.layers) {
            if (!layer.visible) continue
            val file = fileStore.getAnnotationLayerFile(noteId, pageIndex, layer.index)
            val metaFile = fileStore.getAnnotationMetaFile(noteId, pageIndex)
            val metaText = try { if (metaFile.exists()) metaFile.readText() else null } catch (e: Exception) { null }
            val tilesInfo = metaText?.let { personal.notes.touch.ui.canvas.PageLayerMeta.decodeTiles(it)[layer.index] }
            val opsFile = fileStore.getAnnotationLayerOpsFile(noteId, pageIndex, layer.index)
            val ops = try { if (opsFile.exists()) personal.notes.touch.ui.canvas.ContinuousOpCodec.decode(opsFile.readText()) else null } catch (e: Exception) { null }
            val bitmap: Bitmap = (if (file.exists()) {
                BitmapUtils.loadBitmapFromFile(file)
            } else if ((tilesInfo != null && tilesInfo.tiles.isNotEmpty()) || !ops.isNullOrEmpty()) {
                val layerWidth = tilesInfo?.width ?: 2160
                val layerHeight = tilesInfo?.height ?: 3056
                val target = Bitmap.createBitmap(layerWidth, layerHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(target)
                if (tilesInfo != null) {
                    for (name in tilesInfo.tiles) {
                        val pos = personal.notes.touch.ui.canvas.LayerTileGrid.parseTileName(name) ?: continue
                        val tileFile = fileStore.getAnnotationLayerTileFile(noteId, pageIndex, layer.index, pos.first, pos.second)
                        if (tileFile.exists()) {
                            val tileBmp = BitmapUtils.loadBitmapFromFile(tileFile)
                            if (tileBmp != null) {
                                canvas.drawBitmap(tileBmp, (pos.first * tilesInfo.tileSize).toFloat(), (pos.second * tilesInfo.tileSize).toFloat(), null)
                                tileBmp.recycle()
                            }
                        }
                    }
                }
                if (!ops.isNullOrEmpty()) {
                    for (op in ops) {
                        if (op is personal.notes.touch.ui.canvas.InkOp.Stroke) {
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                                color = op.stroke.color
                                strokeWidth = op.stroke.baseWidthNorm * layerWidth
                            }
                            val path = Path()
                            val points = op.stroke.points
                            if (points.isNotEmpty()) {
                                path.moveTo(points[0].x * layerWidth, points[0].y * layerHeight)
                                for (i in 1 until points.size) {
                                    path.lineTo(points[i].x * layerWidth, points[i].y * layerHeight)
                                }
                            }
                            canvas.drawPath(path, paint)
                        }
                    }
                }
                target
            } else {
                null
            }) ?: continue
            val current = result
            if (current == null) {
                result = bitmap
                continue
            }
            // The layer above the first one goes on top of it.
            val canvas = Canvas(current)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return result
    }

    /**
     * The layer stack of a page as it is on the disk.
     *
     * A page without a metadata file has the base layer only.
     */
    private fun readAnnotationStack(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int
    ): personal.notes.touch.ui.canvas.PageLayerStack {
        val file = fileStore.getAnnotationMetaFile(noteId, pageIndex)
        val text = try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        return text?.let { personal.notes.touch.ui.canvas.PageLayerMeta.decode(it) }
            ?: personal.notes.touch.ui.canvas.PageLayerStack.base()
    }

    internal fun flattenBitmap(
        bitmap: Bitmap,
        backgroundBitmap: Bitmap?,
        imageLayers: List<ImageLayer> = emptyList(),
        beyondInk: List<InkCrop> = emptyList(),
        annotationLayer: Bitmap? = null
    ): Bitmap {
        val bgW = backgroundBitmap?.width ?: 0
        val bgH = backgroundBitmap?.height ?: 0
        val w = bitmap.width.coerceAtLeast(bgW).coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(bgH).coerceAtLeast(1)
        // Ink written outside the paper enlarges the sheet, so the export
        // keeps the margins the user wrote in.
        var minX = 0f
        var minY = 0f
        var maxX = w.toFloat()
        var maxY = h.toFloat()
        for (crop in beyondInk) {
            if (crop.bitmap.isRecycled) continue
            minX = minOf(minX, crop.x)
            minY = minOf(minY, crop.y)
            maxX = maxOf(maxX, crop.x + crop.bitmap.width)
            maxY = maxOf(maxY, crop.y + crop.bitmap.height)
        }
        val contentW = (maxX - minX).toInt().coerceAtLeast(1)
        val contentH = (maxY - minY).toInt().coerceAtLeast(1)
        // The annotation layer of the page is wider than the page (the margin
        // on both sides): it is the sheet of the export, and the page sits
        // centred in it - so the writing on the layer lines up with the
        // document.
        val layer = annotationLayer
        val outW = if (layer != null) layer.width.coerceAtLeast(contentW) else contentW
        val outH = if (layer != null) layer.height.coerceAtLeast(contentH) else contentH
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        // The place of the page in the output and the scale it is drawn in.
        var pageLeft = (outW - contentW) / 2f
        var pageTop = (outH - contentH) / 2f
        var pageWidth = w.toFloat()
        var pageHeight = h.toFloat()
        if (layer != null && layer.height > 0 && h > 0) {
            pageWidth = (layer.height.toFloat() * w / h).coerceAtMost(layer.width.toFloat())
            pageHeight = layer.height.toFloat()
            pageLeft = (layer.width - pageWidth) / 2f
            pageTop = 0f
        }
        val scaleX = if (w > 0) pageWidth / w else 1f
        val scaleY = if (h > 0) pageHeight / h else 1f
        val offX = pageLeft - minX * scaleX
        val offY = pageTop - minY * scaleY
        val save = canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scaleX, scaleY)
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled) {
            val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(backgroundBitmap, null, android.graphics.Rect(0, 0, w, h), bgPaint)
        }
        // The page raster and the stroke layer can differ in size (vector PDF
        // pages: pdfium render at zoom resolution vs. view-sized stroke layer).
        // Both describe the same page rect, so the stroke layer is stretched
        // to the size of the page instead of being pinned to the top-left
        // corner.
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        for (crop in beyondInk) {
            if (crop.bitmap.isRecycled) continue
            canvas.drawBitmap(crop.bitmap, crop.x, crop.y, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        for (layerItem in imageLayers) {
            if (!layerItem.visible || layerItem.bitmap.isRecycled) continue
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layerItem.opacity * 255f).toInt().coerceIn(0, 255)
            }
            val bw = layerItem.bitmap.width
            val bh = layerItem.bitmap.height
            val cx = layerItem.x + bw * layerItem.scale / 2f
            val cy = layerItem.y + bh * layerItem.scale / 2f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(layerItem.rotation)
            canvas.scale(layerItem.scale, layerItem.scale)
            canvas.drawBitmap(layerItem.bitmap, -bw / 2f, -bh / 2f, paint)
            // The ink written on the layer follows the same transform.
            for (crop in layerItem.ink) {
                if (crop.bitmap.isRecycled) continue
                val iw = crop.bitmap.width
                val ih = crop.bitmap.height
                val dx = (crop.x + iw / 2f) - cx
                val dy = (crop.y + ih / 2f) - cy
                val rad = Math.toRadians((-layerItem.rotation).toDouble())
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                val lx = (dx * cos - dy * sin) / layerItem.scale - iw / 2f
                val ly = (dx * sin + dy * cos) / layerItem.scale - ih / 2f
                canvas.drawBitmap(crop.bitmap, lx, ly, paint)
            }
            canvas.restore()
        }
        canvas.restoreToCount(save)
        // The annotation layer is drawn over the page: the writing of the
        // user (also the notes in the margin beside the page) is part of the
        // export.
        if (layer != null && !layer.isRecycled) {
            canvas.drawBitmap(layer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.ifBlank { "TouchNote" }
    }
}
