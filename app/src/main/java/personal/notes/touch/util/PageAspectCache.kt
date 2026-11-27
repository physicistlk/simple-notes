package personal.notes.touch.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, pure-JVM cache of PDF page aspect ratios (`width / height`) keyed
 * by `"$pdfSourcePath#$pdfPageIndex"`.
 *
 * Invalid, `0x0`, or unopened lookups return the requested fallback without
 * writing to the map, so transient unopened-document reads never poison a
 * landscape or mixed-size page as `0.707f`.
 */
class PageAspectCache(private val maxEntries: Int = 2048) {

    private val entries = ConcurrentHashMap<String, Float>()

    fun key(pdfSourcePath: String, pdfPageIndex: Int): String = "$pdfSourcePath#$pdfPageIndex"

    fun get(pdfSourcePath: String, pdfPageIndex: Int): Float? {
        if (pdfSourcePath.isEmpty() || pdfPageIndex < 0) return null
        return entries[key(pdfSourcePath, pdfPageIndex)]
    }

    fun put(
        pdfSourcePath: String,
        pdfPageIndex: Int,
        widthPts: Float,
        heightPts: Float,
        fallback: Float = PdfPageGeometry.DEFAULT_ASPECT
    ): Float {
        if (pdfSourcePath.isEmpty() || pdfPageIndex < 0) return fallback
        if (!widthPts.isFinite() || !heightPts.isFinite() || widthPts <= 0f || heightPts <= 0f) {
            return fallback
        }
        val ratio = widthPts / heightPts
        if (!ratio.isFinite() || ratio <= 0f) return fallback

        val k = key(pdfSourcePath, pdfPageIndex)
        val limit = maxEntries.coerceAtLeast(1)
        synchronized(entries) {
            if (!entries.containsKey(k) && entries.size >= limit) {
                entries.clear()
            }
            entries[k] = ratio
        }
        return ratio
    }

    inline fun getOrLoad(
        pdfSourcePath: String,
        pdfPageIndex: Int,
        fallback: Float = PdfPageGeometry.DEFAULT_ASPECT,
        loadSize: () -> FloatArray?
    ): Float {
        if (pdfSourcePath.isEmpty() || pdfPageIndex < 0) return fallback
        get(pdfSourcePath, pdfPageIndex)?.let { return it }
        val size = loadSize() ?: return fallback
        if (size.size < 2) return fallback
        return put(pdfSourcePath, pdfPageIndex, size[0], size[1], fallback)
    }

    fun invalidate(pdfSourcePath: String) {
        if (pdfSourcePath.isEmpty()) return
        synchronized(entries) {
            val it = entries.keys.iterator()
            while (it.hasNext()) {
                val k = it.next()
                val sep = k.lastIndexOf('#')
                if (sep > 0 && k.substring(0, sep) == pdfSourcePath) {
                    it.remove()
                }
            }
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    fun size(): Int = entries.size

    companion object {
        val default = PageAspectCache()
    }
}
