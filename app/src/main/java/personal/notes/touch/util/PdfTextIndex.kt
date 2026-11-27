package personal.notes.touch.util

import personal.notes.touch.text.NoteSearch
import personal.notes.touch.text.SearchSnippet
import personal.notes.touch.ui.canvas.MiniJson
import java.io.File

/**
 * Pure-JVM PDF full-text index, query router, snippet builder, and disk/memory cache (`PDF-QOL-3`).
 *
 * Extracted page strings are kept verbatim in raw PDFium UCS-2 character order so `startOffset`
 * and `length` on every [PdfSearchHit] map 1:1 to `PdfiumDocument.selectionRects`.
 */
object PdfTextIndex {

    const val SCANNED_PDF_MESSAGE = "No searchable text (scanned PDF)"
    private const val CACHE_VERSION = 2
    private const val MAX_MEMORY_CACHE_SOURCES = 16

    data class PdfSearchHit(
        val pageIndex: Int,
        val startOffset: Int,
        val length: Int,
        val snippet: String,
        val matchRanges: List<IntRange>
    )

    sealed interface SearchInputAction {
        data class JumpToPage(val pageIndex: Int) : SearchInputAction
        data class SearchText(val query: String) : SearchInputAction
    }

    data class PdfPageDescriptor(
        val notePageIndex: Int,
        val pdfSourcePath: String,
        val pdfPageIndex: Int
    )

    data class PdfSourceMeta(
        val sourcePath: String,
        val fileSize: Long,
        val lastModified: Long
    )

    data class IndexedPage(
        val notePageIndex: Int,
        val pdfSourcePath: String,
        val pdfPageIndex: Int,
        val rawText: String
    )

    data class PdfTextIndexData(
        val pages: List<IndexedPage>,
        val isScannedPdf: Boolean,
        val totalCharCount: Int,
        val indexedPdfPageCount: Int
    )

    private data class CachedSource(
        val sourcePath: String,
        val fileSize: Long,
        val lastModified: Long,
        val pages: Map<Int, String>
    )

    private val diskLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun diskLockFor(file: File): Any {
        val key = try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
        return diskLocks.getOrPut(key) { Any() }
    }

    private val memoryCache: MutableMap<String, Map<Int, String>> =
        object : LinkedHashMap<String, Map<Int, String>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<Int, String>>?): Boolean {
                return size > MAX_MEMORY_CACHE_SOURCES
            }
        }

    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.clear()
        }
    }

    private fun sourceKey(meta: PdfSourceMeta): String =
        "${meta.sourcePath}|${meta.fileSize}|${meta.lastModified}"

    /**
     * Routes numeric-only queries in `1..totalPages` to [SearchInputAction.JumpToPage] (0-based)
     * and everything else to [SearchInputAction.SearchText].
     */
    fun classifySearchInput(query: String, totalPages: Int): SearchInputAction {
        val trimmed = query.trim()
        if (totalPages > 0 && trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }) {
            val oneBased = trimmed.toIntOrNull()
            if (oneBased != null && oneBased in 1..totalPages) {
                return SearchInputAction.JumpToPage(oneBased - 1)
            }
        }
        return SearchInputAction.SearchText(trimmed)
    }

    /** Convenience pure-JVM index builder for single-source tests. */
    fun buildIndex(
        pageCount: Int,
        pageTextProvider: (Int) -> String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): PdfTextIndexData? {
        if (pageCount <= 0) {
            return PdfTextIndexData(
                pages = emptyList(),
                isScannedPdf = false,
                totalCharCount = 0,
                indexedPdfPageCount = 0
            )
        }
        val descriptors = (0 until pageCount).map { idx ->
            PdfPageDescriptor(notePageIndex = idx, pdfSourcePath = "mem://doc", pdfPageIndex = idx)
        }
        return buildNoteIndex(
            pages = descriptors,
            cacheFile = null,
            sourceMetaProvider = { null },
            pageTextProvider = { _, pdfPageIdx -> pageTextProvider(pdfPageIdx) },
            onProgress = onProgress,
            isCancelled = isCancelled
        )
    }

    /**
     * Incrementally indexes all PDF pages in [pages], reusing memory LRU and [cacheFile]
     * (`pdf_text.json`) entries whose `(sourcePath, fileSize, lastModified)` still match.
     * Returns `null` without mutating caches if [isCancelled] becomes true.
     */
    fun buildNoteIndex(
        pages: List<PdfPageDescriptor>,
        cacheFile: File? = null,
        sourceMetaProvider: (String) -> PdfSourceMeta? = { null },
        pageTextProvider: (String, Int) -> String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): PdfTextIndexData? {
        if (isCancelled()) return null
        val pdfPages = pages
            .filter { it.pdfSourcePath.isNotEmpty() && it.pdfPageIndex >= 0 }
            .sortedBy { it.notePageIndex }
        if (pdfPages.isEmpty()) {
            return PdfTextIndexData(
                pages = emptyList(),
                isScannedPdf = false,
                totalCharCount = 0,
                indexedPdfPageCount = 0
            )
        }

        val totalPdfPages = pdfPages.size
        val diskSources = if (cacheFile != null) {
            synchronized(diskLockFor(cacheFile)) {
                if (cacheFile.isFile) readDiskCache(cacheFile).toMutableMap() else mutableMapOf()
            }
        } else {
            mutableMapOf()
        }

        val grouped = LinkedHashMap<String, MutableList<PdfPageDescriptor>>()
        for (desc in pdfPages) {
            grouped.getOrPut(desc.pdfSourcePath) { ArrayList() }.add(desc)
        }

        val resolvedByNotePage = HashMap<Int, IndexedPage>(pdfPages.size)
        val pendingMemoryUpdates = ArrayList<Pair<String, Map<Int, String>>>()
        var doneCount = 0
        var cacheUpdated = false

        for ((sourcePath, group) in grouped) {
            if (isCancelled()) return null
            val meta = sourceMetaProvider(sourcePath)

            val memCached = if (meta != null) {
                synchronized(memoryCache) { memoryCache[sourceKey(meta)] }
            } else {
                null
            }
            val diskCached = if (memCached == null && meta != null) {
                diskSources[sourcePath]?.takeIf {
                    it.fileSize == meta.fileSize && it.lastModified == meta.lastModified
                }?.pages
            } else {
                null
            }

            val workingPages = HashMap<Int, String>((memCached ?: diskCached).orEmpty())
            var sourceUpdated = false

            for (desc in group) {
                if (isCancelled()) return null
                val cachedText = workingPages[desc.pdfPageIndex]
                val raw = if (cachedText != null) {
                    cachedText
                } else {
                    pageTextProvider(sourcePath, desc.pdfPageIndex).also { extracted ->
                        workingPages[desc.pdfPageIndex] = extracted
                        sourceUpdated = true
                    }
                }
                resolvedByNotePage[desc.notePageIndex] = IndexedPage(
                    notePageIndex = desc.notePageIndex,
                    pdfSourcePath = sourcePath,
                    pdfPageIndex = desc.pdfPageIndex,
                    rawText = raw
                )
                doneCount++
                onProgress(doneCount, totalPdfPages)
            }

            if (isCancelled()) return null
            if (meta != null && (sourceUpdated || (memCached == null && diskCached != null))) {
                val immutablePages = workingPages.toMap()
                pendingMemoryUpdates.add(sourceKey(meta) to immutablePages)
                if (sourceUpdated) {
                    diskSources[sourcePath] = CachedSource(
                        sourcePath = sourcePath,
                        fileSize = meta.fileSize,
                        lastModified = meta.lastModified,
                        pages = immutablePages
                    )
                    cacheUpdated = true
                }
            }
        }

        if (isCancelled()) return null

        if (pendingMemoryUpdates.isNotEmpty()) {
            synchronized(memoryCache) {
                for ((key, immutablePages) in pendingMemoryUpdates) {
                    memoryCache[key] = immutablePages
                }
            }
        }

        if (cacheFile != null && cacheUpdated) {
            val activePaths = grouped.keys
            synchronized(diskLockFor(cacheFile)) {
                val merged = if (cacheFile.isFile) readDiskCache(cacheFile).toMutableMap() else mutableMapOf()
                for ((path, src) in diskSources) {
                    if (path in activePaths) {
                        merged[path] = src
                    }
                }
                val retained = merged.values.filter { it.sourcePath in activePaths }
                writeDiskCache(cacheFile, retained)
            }
        }

        val indexedPages = pdfPages.mapNotNull { resolvedByNotePage[it.notePageIndex] }
        val totalChars = indexedPages.sumOf { it.rawText.length }
        return PdfTextIndexData(
            pages = indexedPages,
            isScannedPdf = indexedPages.isNotEmpty() && totalChars == 0,
            totalCharCount = totalChars,
            indexedPdfPageCount = indexedPages.size
        )
    }

    /**
     * Searches [index] using [NoteSearch.tokens] + [NoteSearch.containsAll] (case-insensitive,
     * non-ASCII safe, literal `%`/`_`), keeping exact offsets into `IndexedPage.rawText`.
     */
    fun search(
        index: PdfTextIndexData,
        query: String,
        maxHits: Int = 500,
        snippetRadius: Int = 44
    ): List<PdfSearchHit> {
        val tokens = NoteSearch.tokens(query)
        if (tokens.isEmpty() || maxHits <= 0) return emptyList()
        val trimmedQuery = query.trim()
        val hits = ArrayList<PdfSearchHit>()

        for (page in index.pages) {
            if (hits.size >= maxHits) break
            val raw = page.rawText
            if (!NoteSearch.containsAll(raw, tokens)) continue

            val positions = ArrayList<Pair<Int, Int>>()
            if (tokens.size == 1) {
                val token = tokens[0]
                collectOccurrences(raw, token, positions, maxPerPage = maxHits - hits.size)
            } else {
                collectOccurrences(raw, trimmedQuery, positions, maxPerPage = maxHits - hits.size)
                if (positions.isEmpty()) {
                    val anchor = tokens.maxByOrNull { it.length } ?: tokens[0]
                    collectOccurrences(raw, anchor, positions, maxPerPage = maxHits - hits.size)
                }
            }

            for ((startOffset, len) in positions) {
                if (hits.size >= maxHits) break
                val snippet = buildPlainSnippet(raw, startOffset, len, tokens, snippetRadius)
                hits.add(
                    PdfSearchHit(
                        pageIndex = page.notePageIndex,
                        startOffset = startOffset,
                        length = len,
                        snippet = snippet.text,
                        matchRanges = snippet.ranges
                    )
                )
            }
        }
        return hits
    }

    private fun collectOccurrences(
        haystack: String,
        needle: String,
        out: MutableList<Pair<Int, Int>>,
        maxPerPage: Int
    ) {
        if (needle.isEmpty() || maxPerPage <= 0) return
        var from = 0
        val step = needle.length.coerceAtLeast(1)
        while (from <= haystack.length - needle.length && out.size < maxPerPage) {
            val at = haystack.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            out.add(at to needle.length)
            from = at + step
        }
    }

    /**
     * Builds a readable plain-text excerpt around `[hitAt, hitAt + hitLen)` without altering
     * the underlying `rawText` offsets or applying markdown stripping.
     */
    fun buildPlainSnippet(
        rawText: String,
        hitAt: Int,
        hitLen: Int,
        tokens: List<String>,
        radius: Int = 44
    ): SearchSnippet {
        if (rawText.isEmpty()) return SearchSnippet("", emptyList())
        val safeRadius = radius.coerceAtLeast(0)
        val safeHitAt = hitAt.coerceIn(0, rawText.length)
        val safeHitEnd = (safeHitAt + hitLen.coerceAtLeast(0)).coerceIn(safeHitAt, rawText.length)
        val pad = (safeRadius * 2).coerceAtLeast(24)
        var winStart = (safeHitAt - pad).coerceAtLeast(0)
        var winEnd = (safeHitEnd + pad).coerceAtMost(rawText.length)

        while (winStart < winEnd && rawText[winStart].isLowSurrogate()) {
            winStart++
        }
        while (winEnd > winStart && winEnd < rawText.length && rawText[winEnd - 1].isHighSurrogate()) {
            winEnd--
        }

        val sb = StringBuilder((winEnd - winStart).coerceAtLeast(0))
        var lastWasSpace = true
        var mappedHitStart = -1
        var mappedHitEnd = -1

        for (i in winStart until winEnd) {
            if (i >= safeHitAt && mappedHitStart < 0) {
                mappedHitStart = sb.length
            }
            if (i >= safeHitEnd && mappedHitEnd < 0) {
                mappedHitEnd = sb.length
            }
            val ch = rawText[i]
            val isWs = ch == '\r' || ch == '\n' || ch == '\t' || ch.isWhitespace() || ch.code < 0x20
            if (isWs) {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(ch)
                lastWasSpace = false
            }
        }
        if (mappedHitStart < 0 && safeHitAt >= winEnd) {
            mappedHitStart = sb.length
        }
        if (mappedHitEnd < 0 && safeHitEnd == winEnd) {
            mappedHitEnd = sb.length
        }
        val collapsed = sb.toString().trimEnd()
        if (collapsed.isEmpty()) return SearchSnippet("", emptyList())

        val anchorStart = (if (mappedHitStart >= 0) mappedHitStart else 0).coerceIn(0, collapsed.length)
        val anchorEnd = (if (mappedHitEnd >= anchorStart) mappedHitEnd else anchorStart)
            .coerceIn(anchorStart, collapsed.length)

        var start = (anchorStart - safeRadius).coerceAtLeast(0)
        var end = (anchorEnd + safeRadius).coerceAtMost(collapsed.length)
        if (end < start) end = start

        if (start > 0) {
            val space = collapsed.indexOf(' ', start)
            if (space in 1 until anchorStart) start = space + 1
        }
        if (end < collapsed.length) {
            val space = collapsed.lastIndexOf(' ', end)
            if (space >= anchorEnd) end = space
        }

        while (start < end && collapsed[start].isLowSurrogate()) {
            start++
        }
        while (end > start && collapsed[end - 1].isHighSurrogate()) {
            end--
        }

        val body = collapsed.substring(start, end).trim()
        val prefix = if (winStart > 0 || start > 0) "…" else ""
        val suffix = if (winEnd < rawText.length || end < collapsed.length) "…" else ""
        val text = prefix + body + suffix
        return SearchSnippet(text = text, ranges = NoteSearch.rangesIn(text, tokens))
    }

    /**
     * Converts PDFium user-space `{left, top, right, bottom}` rects into canvas pixel rects
     * inside a continuous-mode box whose page rect sits at `(pageLeftPx, pageTopPx)` with
     * width `pageWidthPx`.
     */
    fun userRectsToBoxCanvasRects(
        userRects: List<FloatArray>,
        pageWidthPts: Float,
        pageHeightPts: Float,
        pageLeftPx: Float,
        pageTopPx: Float,
        pageWidthPx: Float
    ): List<FloatArray> {
        if (userRects.isEmpty() || pageWidthPts <= 0f || pageHeightPts <= 0f || pageWidthPx <= 0f) {
            return emptyList()
        }
        val pxPerPt = pageWidthPx / pageWidthPts
        return userRects.mapNotNull { rect ->
            if (rect.size < 4) return@mapNotNull null
            val c = PdfPageGeometry.userRectToCanvas(rect, pageHeightPts, pxPerPt)
            floatArrayOf(
                c[0] + pageLeftPx,
                c[1] + pageTopPx,
                c[2] + pageLeftPx,
                c[3] + pageTopPx
            )
        }
    }

    private fun readDiskCache(file: File): Map<String, CachedSource> {
        return try {
            val root = MiniJson.parse(file.readText(Charsets.UTF_8)) as? Map<*, *> ?: return emptyMap()
            val version = (root["version"] as? Number)?.toInt() ?: -1
            if (version != CACHE_VERSION) return emptyMap()
            val sources = root["sources"] as? List<*> ?: return emptyMap()
            val out = LinkedHashMap<String, CachedSource>()
            for (rawEntry in sources) {
                val entry = rawEntry as? Map<*, *> ?: continue
                val sourcePath = entry["sourcePath"] as? String ?: continue
                val fileSize = (entry["fileSize"] as? Number)?.toLong() ?: continue
                val lastModified = (entry["lastModified"] as? Number)?.toLong() ?: continue
                val pageMap = LinkedHashMap<Int, String>()
                when (val rawPages = entry["pages"]) {
                    is Map<*, *> -> {
                        for ((k, v) in rawPages) {
                            val pageIdx = (k as? String)?.toIntOrNull() ?: continue
                            if (pageIdx >= 0 && v is String) {
                                pageMap[pageIdx] = v
                            }
                        }
                    }
                    is List<*> -> {
                        // Backwards-compatible reader for array-style caches (skip null holes).
                        for (idx in rawPages.indices) {
                            val p = rawPages[idx]
                            if (p is String) {
                                pageMap[idx] = p
                            }
                        }
                    }
                    else -> continue
                }
                out[sourcePath] = CachedSource(
                    sourcePath = sourcePath,
                    fileSize = fileSize,
                    lastModified = lastModified,
                    pages = pageMap
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeDiskCache(file: File, sources: List<CachedSource>) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val sb = StringBuilder()
            sb.append("{\"version\":").append(CACHE_VERSION).append(",\"sources\":[")
            sources.forEachIndexed { idx, src ->
                if (idx > 0) sb.append(',')
                sb.append("{\"sourcePath\":")
                appendJsonString(sb, src.sourcePath)
                sb.append(",\"fileSize\":").append(src.fileSize)
                sb.append(",\"lastModified\":").append(src.lastModified)
                sb.append(",\"pages\":{")
                val sortedPages = src.pages.entries.sortedBy { it.key }
                sortedPages.forEachIndexed { pIdx, (pageNum, pageText) ->
                    if (pIdx > 0) sb.append(',')
                    appendJsonString(sb, pageNum.toString())
                    sb.append(':')
                    appendJsonString(sb, pageText)
                }
                sb.append("}}")
            }
            sb.append("]}")

            val tmp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
            tmp.writeText(sb.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                }
            }
        } catch (_: Exception) {
            // Disk cache failure is non-fatal; in-memory index remains valid.
        }
    }

    private fun appendJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (i in value.indices) {
            val c = value[i]
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> {
                    val hex = c.code.toString(16).padStart(4, '0')
                    sb.append("\\u").append(hex)
                }
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
