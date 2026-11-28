package personal.notes.touch.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.LinkedHashMap

/**
 * Pooled pdfium document cache + byte-budgeted LRU cache for rendered continuous PDF pages.
 *
 * - Keeps a primary [document] handle for backwards-compatible callers and single-page queries.
 * - Provides [withDocument] backed by a pool of up to [MAX_POOL_HANDLES] concurrent
 *   [PdfiumDocument] handles per file so multi-core devices (e.g. Snapdragon 860 octa-core)
 *   can render visible PDF pages in parallel without serializing on a single native lock.
 * - Maintains an in-memory LRU cache of recently rendered continuous pages so scrolling back
 *   and forth across pages is instantaneous without re-invoking Pdfium.
 *
 * Rendered bitmaps evicted from the LRU cache are deliberately NOT recycled: Compose
 * `ImageBitmap` may still reference a bitmap on the active draw frame, so GC reclaims them
 * safely once the composable drops its reference.
 */
class PdfDocumentCache(private val context: Context) : AutoCloseable {

    companion object {
        private const val MAX_POOL_HANDLES = 4
        private const val MAX_RENDER_CACHE_BYTES: Long = 48L * 1024L * 1024L // 48 MiB
    }

    private var path: String? = null
    private var document: PdfiumDocument? = null
    private val idlePool = ArrayDeque<PdfiumDocument>()
    private val allOpened = mutableListOf<PdfiumDocument>()

    private val renderCacheLock = Any()
    private val renderCacheMaxBytes: Long = run {
        val heapBudget = Runtime.getRuntime().maxMemory() / 6L
        if (heapBudget > 0L) minOf(MAX_RENDER_CACHE_BYTES, heapBudget) else MAX_RENDER_CACHE_BYTES
    }
    private var renderCacheBytes: Long = 0L
    private val renderCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

    /** Returns the primary open document for [relativePath] (relative to filesDir). */
    @Synchronized
    fun document(relativePath: String): PdfiumDocument? {
        if (relativePath.isEmpty()) return null
        val current = document
        if (current != null && path == relativePath && current.isOpen) return current
        closeDocumentsLocked()
        val file: File = PdfVectorImporter.resolveSource(context, relativePath)
        val opened = PdfiumDocument.open(file) ?: return null
        document = opened
        path = relativePath
        allOpened.add(opened)
        return opened
    }

    /**
     * Borrows a [PdfiumDocument] handle from the per-file pool (up to [MAX_POOL_HANDLES]
     * concurrent handles) so callers on `Dispatchers.IO` can render distinct PDF pages in
     * parallel across multiple CPU cores.
     */
    fun <T> withDocument(relativePath: String, block: (PdfiumDocument) -> T): T? {
        if (relativePath.isEmpty()) return null
        val doc = acquirePooledDocument(relativePath) ?: return null
        return try {
            block(doc)
        } finally {
            releasePooledDocument(relativePath, doc)
        }
    }

    @Synchronized
    private fun acquirePooledDocument(relativePath: String): PdfiumDocument? {
        if (path != relativePath) {
            closeDocumentsLocked()
            path = relativePath
        }
        while (idlePool.isNotEmpty()) {
            val pooled = idlePool.removeFirst()
            if (pooled.isOpen) return pooled
            allOpened.remove(pooled)
        }
        val file: File = PdfVectorImporter.resolveSource(context, relativePath)
        val opened = PdfiumDocument.open(file) ?: return null
        allOpened.add(opened)
        if (document == null) {
            document = opened
        }
        return opened
    }

    @Synchronized
    private fun releasePooledDocument(relativePath: String, doc: PdfiumDocument) {
        if (!doc.isOpen) {
            allOpened.remove(doc)
            if (document === doc) document = null
            return
        }
        if (path != relativePath) {
            allOpened.remove(doc)
            if (document === doc) document = null
            doc.close()
            return
        }
        if (idlePool.size < MAX_POOL_HANDLES) {
            idlePool.addLast(doc)
        } else if (document !== doc) {
            allOpened.remove(doc)
            doc.close()
        }
    }

    /** Returns a cached rendered continuous PDF page bitmap, or `null` on miss. */
    fun getRenderedPage(cacheKey: String): Bitmap? = synchronized(renderCacheLock) {
        val bmp = renderCache[cacheKey] ?: return null
        if (bmp.isRecycled) {
            renderCache.remove(cacheKey)
            renderCacheBytes = (renderCacheBytes - BitmapPool.bitmapBytes(bmp)).coerceAtLeast(0L)
            return null
        }
        bmp
    }

    /** Stores a rendered continuous PDF page bitmap in the byte-budgeted LRU cache. */
    fun putRenderedPage(cacheKey: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val bytes = BitmapPool.bitmapBytes(bitmap)
        if (bytes <= 0L || bytes > renderCacheMaxBytes) return
        synchronized(renderCacheLock) {
            val prev = renderCache.put(cacheKey, bitmap)
            if (prev != null) {
                renderCacheBytes = (renderCacheBytes - BitmapPool.bitmapBytes(prev)).coerceAtLeast(0L)
            }
            renderCacheBytes += bytes
            val iter = renderCache.entries.iterator()
            while (renderCacheBytes > renderCacheMaxBytes && iter.hasNext()) {
                val eldest = iter.next()
                renderCacheBytes = (renderCacheBytes - BitmapPool.bitmapBytes(eldest.value)).coerceAtLeast(0L)
                iter.remove()
            }
        }
    }

    /** Clears all cached rendered continuous PDF pages (without recycling active UI bitmaps). */
    fun clearRenderedPages() = synchronized(renderCacheLock) {
        renderCache.clear()
        renderCacheBytes = 0L
    }

    private fun closeDocumentsLocked() {
        val toClose = allOpened.toList()
        allOpened.clear()
        idlePool.clear()
        document = null
        path = null
        for (doc in toClose) {
            try {
                doc.close()
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    override fun close() {
        closeDocumentsLocked()
        clearRenderedPages()
    }
}
