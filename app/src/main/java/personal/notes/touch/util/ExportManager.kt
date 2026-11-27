package personal.notes.touch.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.text.MarkdownExport
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.ui.canvas.ImageLayer
import personal.notes.touch.ui.canvas.InkCrop
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

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
        val flattened = if (backgroundBitmap == null && imageLayers.isEmpty()) {
            bitmap
        } else {
            flattenBitmap(bitmap, backgroundBitmap, imageLayers)
        }
        val safeTitle = sanitizeFilename(noteTitle)
        val file = File(getExportCacheDir(context), "${safeTitle}_page_${pageIndex + 1}.png")
        FileOutputStream(file).use { out ->
            flattened.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (flattened !== bitmap && !flattened.isRecycled) {
            flattened.recycle()
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
        val flattened = if (backgroundBitmap == null && imageLayers.isEmpty()) {
            bitmap
        } else {
            flattenBitmap(bitmap, backgroundBitmap, imageLayers)
        }
        outputStream.use { out ->
            flattened.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (flattened !== bitmap && !flattened.isRecycled) {
            flattened.recycle()
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
        currentEditingImageLayers: List<ImageLayer> = emptyList(),
        includeMargins: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val safeTitle = sanitizeFilename(note.title)
        val pdfFile = File(getExportCacheDir(context), "$safeTitle.pdf")
        FileOutputStream(pdfFile).use { out ->
            exportNoteToPdfStream(
                note = note,
                repository = repository,
                fileStore = fileStore,
                currentEditingBitmap = currentEditingBitmap,
                currentPageIndex = currentPageIndex,
                outputStream = out,
                currentEditingBackground = currentEditingBackground,
                currentEditingImageLayers = currentEditingImageLayers,
                includeMargins = includeMargins
            )
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
        currentEditingImageLayers: List<ImageLayer> = emptyList(),
        includeMargins: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val pageCount = note.pageCount.coerceAtLeast(1)
        val pagesByIndex = repository.getPagesForNoteSync(note.id).associateBy { it.index }
        val pdfDocs = HashMap<String, PdfiumDocument>()

        val hasTextContent = !note.content.isNullOrBlank()

        // Standard A4 dimensions in Points (72 dpi) for rich-text cover page
        val coverWidth = 595
        val coverHeight = 842

        var pdfPageNumber = 1

        try {
            // If rich text note exists, render text cover/first page
            if (hasTextContent) {
                val pageInfo = PdfDocument.PageInfo.Builder(coverWidth, coverHeight, pdfPageNumber++).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                renderTextContentToCanvas(
                    canvas = canvas,
                    title = note.title,
                    content = note.content ?: "",
                    width = coverWidth,
                    height = coverHeight
                )

                document.finishPage(page)
            }

            // Render each canvas page
            for (pageIdx in 0 until pageCount) {
                val pageEntity = pagesByIndex[pageIdx]
                val bitmap = if (pageIdx == currentPageIndex && currentEditingBitmap != null) {
                    flattenCurrentPageForExport(
                        note = note,
                        repository = repository,
                        fileStore = fileStore,
                        pageIndex = pageIdx,
                        currentEditingBitmap = currentEditingBitmap,
                        currentEditingBackground = currentEditingBackground,
                        currentEditingImageLayers = currentEditingImageLayers,
                        pageEntity = pageEntity,
                        pdfDocuments = pdfDocs,
                        includeMargins = includeMargins
                    )
                } else {
                    loadFlattenedPageBitmap(
                        fileStore = fileStore,
                        noteId = note.id,
                        pageIndex = pageIdx,
                        pageType = note.pageType,
                        pageEntity = pageEntity,
                        pdfDocuments = pdfDocs,
                        repository = repository,
                        includeMargins = includeMargins
                    )
                }

                val pdfSource = pageEntity?.let {
                    if (it.pdfSourcePath.isNotBlank() && it.pdfPageIndex >= 0) {
                        PdfExportPolicy.PdfPageSource(it.pdfSourcePath, it.pdfPageIndex)
                    } else {
                        null
                    }
                }
                val pdfPts = if (pdfSource != null) {
                    getOrOpenPdfDocument(fileStore, pdfSource.pdfSourcePath, pdfDocs)
                        ?.pageSize(pdfSource.pdfPageIndex)
                } else {
                    null
                }
                val (pdfPageWidth, pdfPageHeight) = if (bitmap != null) {
                    PdfExportPolicy.resolveExportPageSizePts(
                        bitmapWidth = bitmap.width,
                        bitmapHeight = bitmap.height,
                        pdfPageWidthPts = pdfPts?.getOrNull(0) ?: 0f,
                        pdfPageHeightPts = pdfPts?.getOrNull(1) ?: 0f
                    )
                } else if (pdfPts != null && pdfPts.size >= 2 && pdfPts[0] > 0f && pdfPts[1] > 0f) {
                    MarginExportMath.exportPdfPageSizePts(
                        pageAspect = PdfPageGeometry.aspect(pdfPts[0], pdfPts[1]),
                        margin = ContinuousPageGeometry.marginFor(note.pageType),
                        includeMargins = includeMargins
                    )
                } else {
                    PdfExportPolicy.resolveExportPageSizePts(
                        bitmapWidth = 0,
                        bitmapHeight = 0,
                        pdfPageWidthPts = 0f,
                        pdfPageHeightPts = 0f
                    )
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pdfPageWidth, pdfPageHeight, pdfPageNumber++).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Fill page background white
                canvas.drawColor(Color.WHITE)

                if (bitmap != null) {
                    val destRect = Rect(0, 0, pdfPageWidth, pdfPageHeight)
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
        } finally {
            pdfDocs.values.forEach { it.close() }
            document.close()
        }
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
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to share this file", Toast.LENGTH_SHORT).show()
        }
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

    private fun getOrOpenPdfDocument(
        fileStore: NoteFileStore,
        pdfSourcePath: String,
        pdfDocs: MutableMap<String, PdfiumDocument>
    ): PdfiumDocument? {
        if (pdfSourcePath.isBlank()) return null
        val cached = pdfDocs[pdfSourcePath]
        if (cached != null && cached.isOpen) return cached
        val file = fileStore.resolveRelativeFile(pdfSourcePath)
        val opened = PdfiumDocument.open(file) ?: return null
        pdfDocs[pdfSourcePath] = opened
        return opened
    }

    suspend fun flattenCurrentPageForExport(
        note: NoteEntity,
        repository: NoteRepository,
        fileStore: NoteFileStore,
        pageIndex: Int,
        currentEditingBitmap: Bitmap,
        currentEditingBackground: Bitmap? = null,
        currentEditingImageLayers: List<ImageLayer> = emptyList(),
        pageEntity: PageEntity? = null,
        pdfDocuments: MutableMap<String, PdfiumDocument>? = null,
        includeMargins: Boolean = false
    ): Bitmap = withContext(Dispatchers.IO) {
        if (ContinuousPageGeometry.isInfinite(note.pageType)) {
            return@withContext loadInfiniteExportBitmap(
                fileStore = fileStore,
                noteId = note.id,
                pageIndex = pageIndex,
                currentEditingBitmap = currentEditingBitmap
            )
        }
        val effectivePage = pageEntity ?: repository.getPage(note.id, pageIndex)
        val source = effectivePage?.let {
            if (it.pdfSourcePath.isNotBlank() && it.pdfPageIndex >= 0) {
                PdfExportPolicy.PdfPageSource(it.pdfSourcePath, it.pdfPageIndex)
            } else null
        }
        val backgroundOverride = loadPageBackgroundOverride(fileStore, note.id, pageIndex)
        val localDocs = if (pdfDocuments == null) HashMap<String, PdfiumDocument>() else null
        val docsMap = pdfDocuments ?: localDocs
        var pdfBackground: Bitmap? = null
        var pageAspect = ContinuousPageGeometry.defaultRatio(note.pageType)

        try {
            if (PdfExportPolicy.shouldUseVectorPdfBackground(source, backgroundOverride != null) &&
                source != null && docsMap != null
            ) {
                val doc = getOrOpenPdfDocument(fileStore, source.pdfSourcePath, docsMap)
                val pts = doc?.pageSize(source.pdfPageIndex)
                if (doc != null && pts != null && pts.size >= 2 && pts[0] > 0f && pts[1] > 0f) {
                    pageAspect = PdfPageGeometry.aspect(pts[0], pts[1], pageAspect)
                    val (targetW, targetH) = PdfExportPolicy.exportRenderTargetSize(pts[0], pts[1])
                    pdfBackground = doc.renderPage(source.pdfPageIndex, targetW, targetH)
                }
            } else if (backgroundOverride != null && !backgroundOverride.isRecycled &&
                backgroundOverride.width > 0 && backgroundOverride.height > 0
            ) {
                pageAspect = PdfPageGeometry.aspect(
                    backgroundOverride.width.toFloat(),
                    backgroundOverride.height.toFloat(),
                    pageAspect
                )
            } else if (currentEditingBackground != null && !currentEditingBackground.isRecycled &&
                currentEditingBackground.width > 0 && currentEditingBackground.height > 0
            ) {
                pageAspect = PdfPageGeometry.aspect(
                    currentEditingBackground.width.toFloat(),
                    currentEditingBackground.height.toFloat(),
                    pageAspect
                )
            } else if (!currentEditingBitmap.isRecycled &&
                currentEditingBitmap.width > 0 && currentEditingBitmap.height > 0
            ) {
                pageAspect = PdfPageGeometry.aspect(
                    currentEditingBitmap.width.toFloat(),
                    currentEditingBitmap.height.toFloat(),
                    pageAspect
                )
            }
        } finally {
            localDocs?.values?.forEach { it.close() }
        }

        val stack = readAnnotationStack(fileStore, note.id, pageIndex)
        val baseLayerVisible = stack.layerAt(
            personal.notes.touch.ui.canvas.PageLayerStack.BASE_LAYER_INDEX
        )?.visible != false
        val isVectorPdf = source != null
        val effectiveEditingBitmap = if (isVectorPdf && !baseLayerVisible) {
            Bitmap.createBitmap(
                currentEditingBitmap.width.coerceAtLeast(1),
                currentEditingBitmap.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
        } else {
            currentEditingBitmap
        }
        val beyondInk = readDiskBeyondInk(fileStore, note.id, pageIndex)
        val annotationLayer = loadAnnotationLayer(fileStore, note.id, pageIndex, note.pageType, pageAspect)
        val effectiveBg = backgroundOverride ?: pdfBackground ?: currentEditingBackground
        val flat = flattenBitmap(
            bitmap = effectiveEditingBitmap,
            backgroundBitmap = effectiveBg,
            imageLayers = currentEditingImageLayers,
            beyondInk = beyondInk,
            annotationLayer = annotationLayer,
            pageType = note.pageType,
            includeMargins = includeMargins
        )
        if (effectiveEditingBitmap !== currentEditingBitmap &&
            effectiveEditingBitmap !== flat &&
            !effectiveEditingBitmap.isRecycled
        ) {
            effectiveEditingBitmap.recycle()
        }
        for (crop in beyondInk) {
            if (!crop.bitmap.isRecycled) crop.bitmap.recycle()
        }
        if (annotationLayer != null && annotationLayer !== flat && !annotationLayer.isRecycled) {
            annotationLayer.recycle()
        }
        if (pdfBackground != null && pdfBackground !== flat && !pdfBackground.isRecycled) {
            pdfBackground.recycle()
        }
        if (backgroundOverride != null && backgroundOverride !== flat && !backgroundOverride.isRecycled) {
            backgroundOverride.recycle()
        }
        flat
    }

    suspend fun loadFlattenedPageBitmap(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int,
        // The layers are page scoped, so every page has its own.
        includeLayers: Boolean = true,
        pageType: String = "a4",
        pageEntity: PageEntity? = null,
        pdfDocuments: MutableMap<String, PdfiumDocument>? = null,
        repository: NoteRepository? = null,
        includeMargins: Boolean = false
    ): Bitmap? {
        if (pageType == "infinite") {
            return loadInfiniteExportBitmap(fileStore, noteId, pageIndex)
        }
        val effectivePage = pageEntity ?: repository?.getPage(noteId, pageIndex)
        val source = effectivePage?.let {
            if (it.pdfSourcePath.isNotBlank() && it.pdfPageIndex >= 0) {
                PdfExportPolicy.PdfPageSource(it.pdfSourcePath, it.pdfPageIndex)
            } else null
        }
        val pageFile = fileStore.getPageFile(noteId, pageIndex)
        val strokesFile = fileStore.getStrokesFile(noteId, pageIndex)
        val layers = if (includeLayers) readDiskImageLayers(fileStore, noteId, pageIndex) else emptyList()
        // Ink written outside the paper and a picked page background belong
        // to the page and have to be part of the export as well.
        val beyondInk = readDiskBeyondInk(fileStore, noteId, pageIndex)
        val backgroundOverride = loadPageBackgroundOverride(fileStore, noteId, pageIndex)

        val localDocs = if (pdfDocuments == null) HashMap<String, PdfiumDocument>() else null
        val docsMap = pdfDocuments ?: localDocs
        var pdfBackground: Bitmap? = null
        var pageAspect = ContinuousPageGeometry.defaultRatio(pageType)
        try {
            if (PdfExportPolicy.shouldUseVectorPdfBackground(source, backgroundOverride != null) &&
                source != null && docsMap != null
            ) {
                val doc = getOrOpenPdfDocument(fileStore, source.pdfSourcePath, docsMap)
                val pts = doc?.pageSize(source.pdfPageIndex)
                if (doc != null && pts != null && pts.size >= 2 && pts[0] > 0f && pts[1] > 0f) {
                    pageAspect = PdfPageGeometry.aspect(pts[0], pts[1], pageAspect)
                    val (targetW, targetH) = PdfExportPolicy.exportRenderTargetSize(pts[0], pts[1])
                    pdfBackground = doc.renderPage(source.pdfPageIndex, targetW, targetH)
                }
            } else if (backgroundOverride != null && !backgroundOverride.isRecycled &&
                backgroundOverride.width > 0 && backgroundOverride.height > 0
            ) {
                pageAspect = PdfPageGeometry.aspect(
                    backgroundOverride.width.toFloat(),
                    backgroundOverride.height.toFloat(),
                    pageAspect
                )
            } else if (pageFile.exists()) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(pageFile.absolutePath, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    pageAspect = PdfPageGeometry.aspect(
                        bounds.outWidth.toFloat(),
                        bounds.outHeight.toFloat(),
                        pageAspect
                    )
                }
            }
        } finally {
            localDocs?.values?.forEach { it.close() }
        }

        val stack = readAnnotationStack(fileStore, noteId, pageIndex)
        val baseLayerVisible = stack.layerAt(
            personal.notes.touch.ui.canvas.PageLayerStack.BASE_LAYER_INDEX
        )?.visible != false
        val isVectorPdf = source != null
        val allowStrokesFile = strokesFile.exists() && (!isVectorPdf || baseLayerVisible)

        // The annotation layer of the page (layer 1): the writing of the user,
        // also the notes in the margin beside the page.
        val annotationLayer = loadAnnotationLayer(fileStore, noteId, pageIndex, pageType, pageAspect)
        val result = if (pdfBackground != null) {
            val strokeBmp = if (allowStrokesFile) BitmapUtils.loadBitmapFromFile(strokesFile) else null
            if (strokeBmp != null) {
                val flat = flattenBitmap(
                    strokeBmp, pdfBackground, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
                if (strokeBmp !== flat && !strokeBmp.isRecycled) strokeBmp.recycle()
                flat
            } else {
                flattenBitmap(
                    pdfBackground, null, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
            }
        } else if (allowStrokesFile) {
            val baseBmp = BitmapUtils.loadBitmapFromFile(pageFile)
            val strokeBmp = BitmapUtils.loadBitmapFromFile(strokesFile)
            if (baseBmp != null && strokeBmp != null) {
                val flat = flattenBitmap(
                    strokeBmp, backgroundOverride ?: baseBmp, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
                if (baseBmp !== flat && !baseBmp.isRecycled) baseBmp.recycle()
                if (strokeBmp !== flat && !strokeBmp.isRecycled) strokeBmp.recycle()
                flat
            } else if (strokeBmp != null) {
                val flat = flattenBitmap(
                    strokeBmp, backgroundOverride, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
                if (strokeBmp !== flat && !strokeBmp.isRecycled) strokeBmp.recycle()
                flat
            } else if (baseBmp != null) {
                val flat = flattenBitmap(
                    baseBmp, backgroundOverride, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
                if (baseBmp !== flat && !baseBmp.isRecycled) baseBmp.recycle()
                flat
            } else null
        } else {
            val base = BitmapUtils.loadBitmapFromFile(pageFile)
            if (base != null) {
                val flat = flattenBitmap(
                    base, backgroundOverride, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
                if (base !== flat && !base.isRecycled) base.recycle()
                flat
            } else if (backgroundOverride != null || layers.isNotEmpty() ||
                annotationLayer != null || beyondInk.isNotEmpty()
            ) {
                val safeAspect = if (pageAspect.isFinite() && pageAspect > 0f) {
                    pageAspect
                } else {
                    PdfPageGeometry.DEFAULT_ASPECT
                }
                val blankW = if (backgroundOverride != null && !backgroundOverride.isRecycled && backgroundOverride.width > 0) {
                    backgroundOverride.width
                } else {
                    2160
                }
                val blankH = if (backgroundOverride != null && !backgroundOverride.isRecycled && backgroundOverride.height > 0) {
                    backgroundOverride.height
                } else {
                    (2160f / safeAspect).roundToInt().coerceIn(1, 4096)
                }
                val blank = Bitmap.createBitmap(blankW, blankH, Bitmap.Config.ARGB_8888)
                val flat = flattenBitmap(
                    blank, backgroundOverride, layers, beyondInk, annotationLayer, pageType, includeMargins
                )
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
        if (pdfBackground != null && pdfBackground !== result && !pdfBackground.isRecycled) pdfBackground.recycle()
        if (annotationLayer != null && annotationLayer !== result && !annotationLayer.isRecycled) annotationLayer.recycle()
        if (backgroundOverride != null && backgroundOverride !== result && !backgroundOverride.isRecycled) backgroundOverride.recycle()
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
    private fun hasNonTransparentPixels(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) != 0) return true
            }
        }
        return false
    }

    private suspend fun loadInfiniteExportBitmap(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int,
        currentEditingBitmap: Bitmap? = null
    ): Bitmap {
        val outSize = 2160
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val stack = readAnnotationStack(fileStore, noteId, pageIndex)
        val baseLayerVisible = stack.layerAt(
            personal.notes.touch.ui.canvas.PageLayerStack.BASE_LAYER_INDEX
        )?.visible != false
        val placedItems = personal.notes.touch.ui.canvas.InfiniteItemsStore.load(fileStore, noteId)
            .filter { item ->
                item.pageIndex == pageIndex && (stack.layerAt(item.layerIndex)?.visible != false)
            }
        val layerOpsMap = HashMap<Int, List<personal.notes.touch.ui.canvas.InkOp>>(stack.layers.size)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var count = 0

        for (item in placedItems) {
            val hw = item.width / 2f
            val hh = item.height / 2f
            minX = minOf(minX, item.centerX - hw)
            maxX = maxOf(maxX, item.centerX + hw)
            minY = minOf(minY, item.centerY - hh)
            maxY = maxOf(maxY, item.centerY + hh)
            count++
        }

        val editingBmp = if (baseLayerVisible && currentEditingBitmap != null && hasNonTransparentPixels(currentEditingBitmap)) {
            currentEditingBitmap
        } else {
            null
        }
        if (editingBmp != null) {
            val worldW = 2160f
            val worldH = 2160f * editingBmp.height.toFloat() / editingBmp.width.coerceAtLeast(1).toFloat()
            minX = minOf(minX, 0f)
            minY = minOf(minY, 0f)
            maxX = maxOf(maxX, worldW)
            maxY = maxOf(maxY, worldH)
            count++
        }

        for (layer in stack.layers) {
            if (!layer.visible) {
                layerOpsMap[layer.index] = emptyList()
                continue
            }
            val opsFile = fileStore.getAnnotationLayerOpsFile(noteId, pageIndex, layer.index)
            val ops = try {
                if (opsFile.exists()) {
                    personal.notes.touch.ui.canvas.ContinuousOpCodec.decode(opsFile.readText()) ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
            layerOpsMap[layer.index] = ops
            for (op in ops) {
                if (op is personal.notes.touch.ui.canvas.InkOp.Stroke) {
                    for (p in op.stroke.points) {
                        val px = p.x * 2160f
                        val py = p.y * 2160f
                        if (px < minX) minX = px
                        if (px > maxX) maxX = px
                        if (py < minY) minY = py
                        if (py > maxY) maxY = py
                        count++
                    }
                }
            }
        }

        if (count > 0 && minX <= maxX && minY <= maxY) {
            val contentW = (maxX - minX).coerceAtLeast(100f)
            val contentH = (maxY - minY).coerceAtLeast(100f)
            val padding = 120f
            val scale = kotlin.math.min(
                (outSize - padding * 2f) / contentW,
                (outSize - padding * 2f) / contentH
            )
            val save = canvas.save()
            canvas.translate(
                outSize / 2f - (minX + maxX) / 2f * scale,
                outSize / 2f - (minY + maxY) / 2f * scale
            )
            canvas.scale(scale, scale)

            if (editingBmp != null && !editingBmp.isRecycled) {
                val worldW = 2160f
                val worldH = 2160f * editingBmp.height.toFloat() / editingBmp.width.coerceAtLeast(1).toFloat()
                canvas.drawBitmap(
                    editingBmp,
                    null,
                    android.graphics.RectF(0f, 0f, worldW, worldH),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                )
            }

            val itemPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val pdfDocs = HashMap<String, PdfiumDocument>()
            try {
                for (layer in stack.layers) {
                    if (!layer.visible) continue
                    for (item in placedItems) {
                        if (item.layerIndex != layer.index) continue
                        val bmp: Bitmap? = when (item) {
                            is personal.notes.touch.ui.canvas.InfiniteCanvasItem.ImageItem -> {
                                fileStore.getNoteAsset(noteId, item.fileName)?.let { BitmapUtils.loadBitmapFromFile(it) }
                            }
                            is personal.notes.touch.ui.canvas.InfiniteCanvasItem.PdfPageItem -> {
                                val doc = pdfDocs[item.pdfFileName] ?: run {
                                    val f = fileStore.getNoteAsset(noteId, item.pdfFileName)
                                    val d = if (f != null) PdfiumDocument.open(f) else null
                                    if (d != null) pdfDocs[item.pdfFileName] = d
                                    d
                                }
                                doc?.renderPage(item.pdfPageIndex, 2048, 2048)
                            }
                        }
                        if (bmp != null && !bmp.isRecycled) {
                            val itemSave = canvas.save()
                            canvas.translate(item.centerX, item.centerY)
                            if (item.rotationDeg != 0f) canvas.rotate(item.rotationDeg)
                            if (item.flipHorizontal) canvas.scale(-1f, 1f)
                            val dst = android.graphics.RectF(-item.width / 2f, -item.height / 2f, item.width / 2f, item.height / 2f)
                            canvas.drawBitmap(bmp, null, dst, itemPaint)
                            canvas.restoreToCount(itemSave)
                            bmp.recycle()
                        }
                    }

                    val ops = layerOpsMap[layer.index].orEmpty()
                    if (ops.isEmpty()) continue
                    val hasErase = ops.any { it is personal.notes.touch.ui.canvas.InkOp.Erase }
                    val layerSave = if (hasErase) {
                        canvas.saveLayer(minX - 200f, minY - 200f, maxX + 200f, maxY + 200f, null)
                    } else {
                        -1
                    }
                    for (op in ops) {
                        when (op) {
                            is personal.notes.touch.ui.canvas.InkOp.Stroke -> {
                                val shape = op.shape
                                if (shape != null) {
                                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                        color = op.stroke.color
                                        strokeWidth = op.stroke.baseWidthNorm * 2160f
                                    }
                                    canvas.drawPath(shape, paint)
                                } else {
                                    personal.notes.touch.ui.canvas.ContinuousStrokeRenderer.drawStroke(
                                        canvas, op.stroke, 2160f, 2160f, preview = false
                                    )
                                }
                            }
                            is personal.notes.touch.ui.canvas.InkOp.Erase -> {
                                personal.notes.touch.ui.canvas.ContinuousStrokeRenderer.drawStroke(
                                    canvas, op.stroke, 2160f, 2160f, preview = false
                                )
                            }
                        }
                    }
                    if (layerSave >= 0) {
                        canvas.restoreToCount(layerSave)
                    }
                }
            } finally {
                pdfDocs.values.forEach { it.close() }
            }
            canvas.restoreToCount(save)
        }
        return out
    }

    private suspend fun loadAnnotationLayer(
        fileStore: personal.notes.touch.data.files.NoteFileStore,
        noteId: String,
        pageIndex: Int,
        pageType: String = "a4",
        pageAspect: Float = ContinuousPageGeometry.defaultRatio(pageType)
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
                val resolvedSize = PdfExportPolicy.resolveAnnotationLayerSize(
                    pageType = pageType,
                    pageAspect = pageAspect,
                    tilesWidth = tilesInfo?.width ?: 0,
                    tilesHeight = tilesInfo?.height ?: 0
                )
                val layerWidth = resolvedSize.width
                val layerHeight = resolvedSize.height
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
                    val ratio = if (layerWidth > 0 && layerHeight > 0) {
                        ContinuousPageGeometry.ratioOfLayer(pageType, layerWidth, layerHeight)
                    } else {
                        pageAspect
                    }
                    val boxRatio = ContinuousPageGeometry.boxRatio(pageType, ratio)
                    val rect = ContinuousPageGeometry.layerRect(pageType, ratio, boxRatio, 1f)
                    if (!rect.empty) {
                        val sx = layerWidth.toFloat() / rect.width
                        val sy = layerHeight.toFloat() / rect.height
                        val hasErase = ops.any { it is personal.notes.touch.ui.canvas.InkOp.Erase }
                        val eraseSave = if (hasErase) {
                            canvas.saveLayer(0f, 0f, layerWidth.toFloat(), layerHeight.toFloat(), null)
                        } else {
                            -1
                        }
                        for (op in ops) {
                            when (op) {
                                is personal.notes.touch.ui.canvas.InkOp.Stroke -> {
                                    val shape = op.shape
                                    if (shape == null) {
                                        val save = canvas.save()
                                        canvas.translate(-rect.left * sx, -rect.top * sy)
                                        personal.notes.touch.ui.canvas.ContinuousStrokeRenderer.drawStroke(
                                            canvas, op.stroke, sx, sy, preview = false
                                        )
                                        canvas.restoreToCount(save)
                                    } else {
                                        val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            style = Paint.Style.STROKE
                                            strokeCap = Paint.Cap.ROUND
                                            strokeJoin = Paint.Join.ROUND
                                            color = op.stroke.color
                                            strokeWidth = op.stroke.baseWidthNorm * sx
                                        }
                                        canvas.drawPath(shape, paint2)
                                    }
                                }
                                is personal.notes.touch.ui.canvas.InkOp.Erase -> {
                                    val save = canvas.save()
                                    canvas.translate(-rect.left * sx, -rect.top * sy)
                                    personal.notes.touch.ui.canvas.ContinuousStrokeRenderer.drawStroke(
                                        canvas, op.stroke, sx, sy, preview = false
                                    )
                                    canvas.restoreToCount(save)
                                }
                            }
                        }
                        if (eraseSave >= 0) {
                            canvas.restoreToCount(eraseSave)
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
        annotationLayer: Bitmap? = null,
        pageType: String = "a4",
        includeMargins: Boolean = false
    ): Bitmap {
        val bgW = backgroundBitmap?.width ?: 0
        val bgH = backgroundBitmap?.height ?: 0
        val w = bitmap.width.coerceAtLeast(bgW).coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(bgH).coerceAtLeast(1)
        val margin = ContinuousPageGeometry.marginFor(pageType)
        val effectiveIncludeMargins = includeMargins && margin > 0f
        // Ink written outside the paper enlarges the sheet only when margins are included.
        var minX = 0f
        var minY = 0f
        var maxX = w.toFloat()
        var maxY = h.toFloat()
        if (effectiveIncludeMargins) {
            for (crop in beyondInk) {
                if (crop.bitmap.isRecycled) continue
                minX = minOf(minX, crop.x)
                minY = minOf(minY, crop.y)
                maxX = maxOf(maxX, crop.x + crop.bitmap.width)
                maxY = maxOf(maxY, crop.y + crop.bitmap.height)
            }
        }
        val contentW = (maxX - minX).toInt().coerceAtLeast(1)
        val contentH = (maxY - minY).toInt().coerceAtLeast(1)
        // The annotation layer of the page is wider than the page (the margin
        // on both sides): when includeMargins is true it is the sheet of the export
        // with the page centred in it; when false it is cropped to the page box.
        val layer = annotationLayer
        val pageAspect = PdfPageGeometry.aspect(
            w.toFloat(),
            h.toFloat(),
            ContinuousPageGeometry.defaultRatio(pageType)
        )
        val layerCrop = layer?.let {
            MarginExportMath.cropRect(
                layerWidth = it.width,
                layerHeight = it.height,
                pageAspect = pageAspect,
                margin = margin,
                includeMargins = effectiveIncludeMargins
            )
        }
        val expandForLayer = effectiveIncludeMargins
        val sheetW = if (layerCrop != null) {
            layerCrop.outputWidth
        } else {
            (w * (1f + 2f * margin)).roundToInt().coerceAtLeast(w)
        }
        val sheetH = if (layerCrop != null) {
            layerCrop.outputHeight
        } else {
            h
        }
        val outW = if (expandForLayer) {
            sheetW.coerceAtLeast(contentW)
        } else {
            contentW
        }
        val outH = if (expandForLayer) {
            sheetH.coerceAtLeast(contentH)
        } else {
            contentH
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        // The place of the page in the output and the scale it is drawn in.
        var pageLeft = (outW - contentW) / 2f
        var pageTop = (outH - contentH) / 2f
        var pageWidth = w.toFloat()
        var pageHeight = h.toFloat()
        if (expandForLayer && outH > 0 && h > 0 && contentH > 0) {
            val fullScale = minOf(
                outH.toFloat() / contentH.toFloat(),
                outW.toFloat() / (w.toFloat() * (1f + 2f * margin)).coerceAtLeast(1f)
            )
            pageHeight = h.toFloat() * fullScale
            pageWidth = w.toFloat() * fullScale
            pageLeft = (outW - pageWidth) / 2f
            pageTop = -minY * fullScale + (outH - contentH * fullScale) / 2f
        }
        val scaleX = if (w > 0) pageWidth / w else 1f
        val scaleY = if (h > 0) pageHeight / h else 1f
        val offX = if (expandForLayer) pageLeft else pageLeft - minX * scaleX
        val offY = if (expandForLayer) pageTop else pageTop - minY * scaleY
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
        // The annotation layer is drawn over the page: when includeMargins is
        // true the full margin sheet is composited; when false only the cropped
        // page rect is drawn over the page bounds.
        if (layer != null && !layer.isRecycled && layerCrop != null && layerCrop.width > 0 && layerCrop.height > 0) {
            val layerDstLeft = if (expandForLayer) {
                (pageLeft - margin * pageWidth).roundToInt().coerceIn(0, outW)
            } else {
                0
            }
            val layerDstRight = if (expandForLayer) {
                (pageLeft + (1f + margin) * pageWidth).roundToInt().coerceIn(layerDstLeft, outW)
            } else {
                outW
            }
            val layerDstTop = if (expandForLayer) pageTop.roundToInt().coerceIn(0, outH) else 0
            val layerDstBottom = if (expandForLayer) {
                (pageTop + pageHeight).roundToInt().coerceIn(layerDstTop, outH)
            } else {
                outH
            }
            if (!layerCrop.cropped &&
                layer.width == (layerDstRight - layerDstLeft) &&
                layer.height == (layerDstBottom - layerDstTop) &&
                layerDstLeft == 0 &&
                layerDstTop == 0
            ) {
                canvas.drawBitmap(layer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                val srcRect = Rect(
                    layerCrop.left,
                    layerCrop.top,
                    layerCrop.left + layerCrop.width,
                    layerCrop.top + layerCrop.height
                )
                canvas.drawBitmap(
                    layer,
                    srcRect,
                    Rect(layerDstLeft, layerDstTop, layerDstRight, layerDstBottom),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            }
        }
        return out
    }

    fun sanitizeFilename(name: String): String {
        val cleaned = name
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]"""), "_")
            .trim()
            .take(120)
            .trim()
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") "TouchNote" else cleaned
    }
}
