package personal.notes.touch.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository

/**
 * Vector PDF import (defect F-10).
 *
 * The imported PDF is kept inside the note (`note/<noteId>/pdf_<ts>.pdf`) and
 * every imported page row remembers its source document + page index, so the
 * canvas can re-render the page from the vector source at whatever zoom the
 * user reaches instead of upscaling a one-time raster.
 *
 * Only the *preview* raster (`page_<index>.png`) is written at import time:
 * it feeds the library's note-card thumbnails and is replaced by the
 * stroke-only layer once the user starts annotating that page. Canvas pages
 * that carry `pdfSourcePath` never load it as stroke content.
 */
object PdfVectorImporter {

    /** An opened source document plus the bytes it was parsed from. */
    data class ImportedPdf(
        val document: PdfiumDocument,
        val bytes: ByteArray,
        val pageCount: Int
    )

    /** Reads and parses the picked PDF entirely on the calling thread (IO). */
    fun readDocument(context: Context, uri: Uri): ImportedPdf? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null
        if (bytes.isEmpty()) return null
        val document = PdfiumDocument()
        return try {
            if (!document.openBytes(bytes) || document.pageCount <= 0) {
                document.close()
                null
            } else {
                ImportedPdf(document, bytes, document.pageCount)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    /**
     * Stores the source PDF inside the note's directory and returns its path
     * **relative to `filesDir`** (what `pages.pdfSourcePath` persists, so a
     * backup restored under a different install path keeps working).
     */
    fun storeSource(context: Context, noteId: String, bytes: ByteArray): String? {
        val dir = File(context.filesDir, "note/$noteId").apply { mkdirs() }
        val file = File(dir, "pdf_${System.currentTimeMillis()}.pdf")
        return try {
            file.writeBytes(bytes)
            "note/$noteId/${file.name}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Resolves a `pages.pdfSourcePath` value back to a readable file. */
    fun resolveSource(context: Context, relativePath: String): File = File(context.filesDir, relativePath)

    /**
     * Renders every PDF page into the note and records its vector source.
     *
     * @param reuseFirstPage true when the note was just created and already
     *   owns an empty page 0 that the first PDF page should replace;
     *   false appends one page per PDF page (import into an existing note).
     * @return the first rendered page (callers may show it as the live
     *   background while the imported note is open); null when nothing could
     *   be rendered.
     */
    suspend fun importIntoNote(
        context: Context,
        imported: ImportedPdf,
        noteId: String,
        repository: NoteRepository,
        fileStore: NoteFileStore,
        targetWidth: Int,
        targetHeight: Int,
        reuseFirstPage: Boolean,
        onPageError: suspend (String) -> Unit = {}
    ): Bitmap? {
        val sourcePath = storeSource(context, noteId, imported.bytes)
        var firstPage: Bitmap? = null
        for (i in 0 until imported.pageCount) {
            val bitmap = try {
                imported.document.renderPage(i, targetWidth, targetHeight)
            } catch (e: Exception) {
                e.printStackTrace()
                onPageError("PDF import error: ${e.message}")
                null
            } ?: continue
            val pageIndex = if (i == 0 && reuseFirstPage) 0 else repository.addPage(noteId).index
            fileStore.writePageFileAtomically(noteId, pageIndex) { tmp ->
                FileOutputStream(tmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            if (sourcePath != null) {
                repository.setPagePdfSource(noteId, pageIndex, sourcePath, i)
            }
            if (firstPage == null) firstPage = bitmap
        }
        return firstPage
    }
}
