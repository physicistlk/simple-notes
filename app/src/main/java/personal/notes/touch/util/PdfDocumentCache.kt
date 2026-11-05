package personal.notes.touch.util

import android.content.Context
import java.io.File

/**
 * One lazily-opened pdfium document at a time (F-10). The canvas re-renders
 * vector PDF pages on zoom; opening the document per render would re-parse
 * the PDF every time, so it is cached per note directory path and closed when
 * the note screen leaves composition.
 *
 * Thread-safe: renders arrive on the canvas render thread while page switches
 * may call [document] from the main thread. [PdfiumDocument] itself serializes
 * its native calls, and this cache serializes open/close transitions.
 */
class PdfDocumentCache(private val context: Context) : AutoCloseable {

    private var path: String? = null
    private var document: PdfiumDocument? = null

    /** Returns the open document for [relativePath] (relative to filesDir). */
    @Synchronized
    fun document(relativePath: String): PdfiumDocument? {
        if (relativePath.isEmpty()) return null
        val current = document
        if (current != null && path == relativePath && current.isOpen) return current
        current?.close()
        document = null
        path = null
        val file: File = PdfVectorImporter.resolveSource(context, relativePath)
        val opened = PdfiumDocument.open(file) ?: return null
        document = opened
        path = relativePath
        return opened
    }

    @Synchronized
    override fun close() {
        document?.close()
        document = null
        path = null
    }
}
