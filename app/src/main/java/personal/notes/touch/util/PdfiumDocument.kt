package personal.notes.touch.util

import android.graphics.Bitmap
import java.io.File
import kotlin.math.roundToInt

/**
 * One open PDF document on the bundled pdfium engine (defect F-10: vector
 * import instead of one-time rasterization).
 *
 * Every call is serialized on this instance — pdfium documents are not
 * thread-safe, and the canvas (main thread), the import loop (IO) and text
 * queries can all target the same document.
 *
 * Rendering is resolution-independent: pass the target box and the page is
 * vector-rendered at a matching pixel size, so zooming re-renders crisp
 * glyphs instead of upscaling pixels.
 */
class PdfiumDocument : AutoCloseable {

    private var handle: Long = 0L
    private val lock = Any()

    /** Number of pages; 0 until [open] succeeded. */
    @Volatile
    var pageCount: Int = 0
        private set

    /** True while a native document handle is open. */
    val isOpen: Boolean
        get() = handle != 0L

    fun open(file: File): Boolean {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return openBytes(bytes)
    }

    /** Parses the whole PDF from [bytes]; false when the document is unusable. */
    fun openBytes(bytes: ByteArray): Boolean = synchronized(lock) {
        if (bytes.isEmpty()) return false
        if (handle != 0L) closeLocked()
        val h = PdfiumBridge.openDocument(bytes)
        if (h == 0L) return false
        handle = h
        pageCount = PdfiumBridge.getPageCount(h)
        if (pageCount <= 0) {
            closeLocked()
            return false
        }
        return true
    }

    /** Page size in PDF points (72 pt = 1 inch): [width, height]. */
    fun pageSize(pageIndex: Int): FloatArray = synchronized(lock) {
        if (handle == 0L || pageIndex !in 0 until pageCount) return FloatArray(2)
        PdfiumBridge.getPageSize(handle, pageIndex) ?: FloatArray(2)
    }

    /**
     * Renders [pageIndex] fitted into [targetWidth]×[targetHeight], keeping
     * the page aspect ratio and the same [PdfImportManager.MAX_DIMENSION]
     * cap as the historical raster importer (so page geometry does not jump
     * between builds).
     */
    fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val size = pageSize(pageIndex)
        val ptsW = size[0]
        val ptsH = size[1]
        if (ptsW <= 0f || ptsH <= 0f) return null
        val (outW, outH) = PdfImportManager.calculateOutputSize(
            ptsW.roundToInt().coerceAtLeast(1),
            ptsH.roundToInt().coerceAtLeast(1),
            targetWidth,
            targetHeight
        )
        return renderPageAt(pageIndex, outW.toFloat() / ptsW, outW, outH)
    }

    /**
     * Renders [pageIndex] into a fresh [outW]×[outH] ARGB_8888 bitmap at
     * [pxPerPt] pixels per PDF point (white paper background). Returns null
     * when the page cannot be rendered or the allocation fails.
     */
    fun renderPageAt(pageIndex: Int, pxPerPt: Float, outW: Int, outH: Int): Bitmap? {
        if (outW <= 0 || outH <= 0 || pxPerPt <= 0f) return null
        val bitmap = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        }
        val ok = synchronized(lock) {
            if (handle == 0L || pageIndex !in 0 until pageCount) {
                false
            } else {
                PdfiumBridge.render(handle, pageIndex, bitmap, 0f, 0f, pxPerPt, true)
            }
        }
        if (!ok) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** Number of extractable characters on the page (0 for image-only PDFs). */
    fun textCharCount(pageIndex: Int): Int = synchronized(lock) {
        if (handle == 0L || pageIndex !in 0 until pageCount) return 0
        PdfiumBridge.textCountChars(handle, pageIndex)
    }

    /** Text of [count] chars from [start] (count < 0 = to the end). */
    fun text(pageIndex: Int, start: Int = 0, count: Int = -1): String = synchronized(lock) {
        if (handle == 0L || pageIndex !in 0 until pageCount) return ""
        val total = PdfiumBridge.textCountChars(handle, pageIndex)
        if (total <= 0 || start !in 0 until total) return ""
        val n = if (count < 0) total - start else count.coerceAtMost(total - start)
        if (n <= 0) return ""
        PdfiumBridge.textGetChars(handle, pageIndex, start, n) ?: ""
    }

    /**
     * Selection rectangles covering chars [start]..[start]+[count)-1, each
     * {left, top, right, bottom} in pdfium user space (bottom-left origin,
     * y up). pdfium merges same-line characters into one rect per line.
     */
    fun selectionRects(pageIndex: Int, start: Int, count: Int): List<FloatArray> =
        synchronized(lock) {
            if (handle == 0L || pageIndex !in 0 until pageCount || count <= 0) return emptyList()
            val n = PdfiumBridge.textSelectionRectCount(handle, pageIndex, start, count)
            if (n <= 0) return emptyList()
            (0 until n).map { i ->
                PdfiumBridge.textSelectionRect(handle, pageIndex, i) ?: FloatArray(4)
            }
        }

    /** Char index at user-space point (x, y); -1 when no glyph is close. */
    fun charIndexAt(pageIndex: Int, xPts: Float, yPts: Float, tolerancePts: Float): Int =
        synchronized(lock) {
            if (handle == 0L || pageIndex !in 0 until pageCount) return -1
            PdfiumBridge.textCharIndexAtPos(
                handle, pageIndex, xPts, yPts, tolerancePts, tolerancePts
            )
        }

    /** {left, top, right, bottom} of one char in user space. */
    fun charBox(pageIndex: Int, charIndex: Int): FloatArray = synchronized(lock) {
        if (handle == 0L || pageIndex !in 0 until pageCount) return FloatArray(4)
        PdfiumBridge.textCharBox(handle, pageIndex, charIndex) ?: FloatArray(4)
    }

    override fun close() {
        synchronized(lock) { closeLocked() }
    }

    private fun closeLocked() {
        if (handle != 0L) {
            PdfiumBridge.closeDocument(handle)
            handle = 0L
        }
        pageCount = 0
    }

    companion object {
        /** Opens [file]; null when the document cannot be parsed. */
        fun open(file: File): PdfiumDocument? {
            val document = PdfiumDocument()
            return if (document.open(file)) {
                document
            } else {
                document.close()
                null
            }
        }
    }
}
