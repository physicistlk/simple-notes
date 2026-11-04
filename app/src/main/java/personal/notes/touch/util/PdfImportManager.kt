package personal.notes.touch.util

// PDF import rendering hardening.
//
// IDEAS ATTRIBUTED TO storytold/pdfcraft (MIT/Apache-2.0):
//   - aspect-ratio-preserving page rasterization (fit the requested target
//     box, never stretch the page to fill it),
//   - never-crash guards: bounds-check page indices, guard division by
//     zero, cap allocations to avoid OOM, and return null/0 on any error.
// No code, assets, or native libraries from pdfcraft were copied; this is
// Kotlin-only Android (PdfRenderer) code written for TouchNotes.
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfImportManager {

    /** Max output dimension in either axis; guards against OOM on large pages. */
    const val MAX_DIMENSION = 2048

    /**
     * Pure, testable aspect-ratio math (pdfcraft-style fit):
     * scale = min(targetW/pageW, targetH/pageH), capped so the output never
     * exceeds [MAX_DIMENSION] in either dimension. When target dims are <= 0
     * the native page size is used (also capped to [MAX_DIMENSION]).
     */
    fun calculateOutputSize(
        pageW: Int,
        pageH: Int,
        targetW: Int,
        targetH: Int
    ): Pair<Int, Int> {
        val pW = pageW.coerceAtLeast(1)
        val pH = pageH.coerceAtLeast(1)
        val tW = targetW.coerceIn(1, MAX_DIMENSION)
        val tH = targetH.coerceIn(1, MAX_DIMENSION)
        val scale = if (targetW <= 0 || targetH <= 0) {
            // No target box: fit native page size inside the cap.
            minOf(MAX_DIMENSION.toFloat() / pW, MAX_DIMENSION.toFloat() / pH, 1f)
        } else {
            minOf(tW.toFloat() / pW, tH.toFloat() / pH)
        }
        val outW = (pW * scale).roundToInt().coerceIn(1, MAX_DIMENSION)
        val outH = (pH * scale).roundToInt().coerceIn(1, MAX_DIMENSION)
        return outW to outH
    }

    fun getPageCount(context: Context, uri: Uri): Int =
        getPageCount(context.contentResolver, uri)

    fun getPageCount(contentResolver: ContentResolver, uri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = contentResolver.openFileDescriptor(uri, "r") ?: return 0
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun renderPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = renderPage(context.contentResolver, uri, pageIndex, targetWidth, targetHeight)

    /**
     * Renders [pageIndex] at the largest size that fits inside
     * ([targetWidth], [targetHeight]) while preserving the page aspect
     * ratio, capped to [MAX_DIMENSION] per axis. Returns null on any error
     * (never throws). Rendered directly at the computed output size — no
     * post-scaling bitmap is created.
     */
    fun renderPage(
        contentResolver: ContentResolver,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
            page = renderer.openPage(pageIndex)

            val pageWidth = page.width.coerceAtLeast(1)
            val pageHeight = page.height.coerceAtLeast(1)
            val (outW, outH) = calculateOutputSize(pageWidth, pageHeight, targetWidth, targetHeight)

            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val clip = Rect(0, 0, outW, outH)
            page.render(bitmap, clip, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
