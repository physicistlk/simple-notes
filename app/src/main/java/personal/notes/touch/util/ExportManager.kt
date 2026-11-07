package personal.notes.touch.util

import android.content.Context
import android.content.Intent
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
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.text.MarkdownExport
import personal.notes.touch.ui.canvas.ImageLayer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ExportManager {

    private const val AUTHORITY = "personal.notes.touch.fileprovider"
    private const val MAX_BITMAP_DIMENSION = 2048

    private fun getExportCacheDir(context: Context): File {
        return File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }
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
                val pageFile = fileStore.getPageFile(note.id, pageIdx)
                val strokesFile = fileStore.getStrokesFile(note.id, pageIdx)
                if (strokesFile.exists()) {
                    val baseBmp = BitmapUtils.loadBitmapFromFile(pageFile)
                    val strokeBmp = BitmapUtils.loadBitmapFromFile(strokesFile)
                    if (baseBmp != null && strokeBmp != null) {
                        flattenBitmap(strokeBmp, baseBmp)
                    } else {
                        strokeBmp ?: baseBmp
                    }
                } else {
                    BitmapUtils.loadBitmapFromFile(pageFile)
                }
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


    private fun flattenBitmap(bitmap: Bitmap, backgroundBitmap: Bitmap?, imageLayers: List<ImageLayer> = emptyList()): Bitmap {
        val bgW = backgroundBitmap?.width ?: 0
        val bgH = backgroundBitmap?.height ?: 0
        val w = bitmap.width.coerceAtLeast(bgW).coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(bgH).coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled) {
            val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(backgroundBitmap, null, android.graphics.Rect(0, 0, w, h), bgPaint)
        }
        // The page raster and the stroke layer can differ in size (vector PDF
        // pages: pdfium render at zoom resolution vs. view-sized stroke layer).
        // Both describe the same page rect, so stretch the stroke layer to the
        // output size instead of pinning it to the top-left corner.
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        for (layer in imageLayers) {
            if (layer.bitmap.isRecycled) continue
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
            }
            val bw = layer.bitmap.width
            val bh = layer.bitmap.height
            val cx = layer.x + bw * layer.scale / 2f
            val cy = layer.y + bh * layer.scale / 2f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(layer.rotation)
            canvas.scale(layer.scale, layer.scale)
            canvas.drawBitmap(layer.bitmap, -bw / 2f, -bh / 2f, paint)
            canvas.restore()
        }
        return out
    }

    fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.ifBlank { "TouchNote" }
    }
}
