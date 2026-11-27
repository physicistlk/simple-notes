package personal.notes.touch.util

import personal.notes.touch.ui.canvas.TocItem

/**
 * A raw outline entry extracted from a PDF document (or flattened tree).
 * Kept in this pure-JVM file so unit tests never trigger `PdfiumBridge`'s
 * `System.loadLibrary` static initializer.
 */
data class OutlineNode(
    val title: String,
    val pageIndex: Int,
    val level: Int
)

/** Hierarchical outline node for pure-JVM tree-flattening tests and helpers. */
data class OutlineTreeNode(
    val title: String,
    val pageIndex: Int,
    val children: List<OutlineTreeNode> = emptyList()
)

/**
 * Pure-JVM PDF outline normalization, capping, merging, and active-chapter lookup (`PDF-QOL-1`).
 */
object PdfOutlineImporter {

    const val DEFAULT_MAX_ENTRIES = 500
    const val MAX_LEVEL = 16
    const val SOURCE_PDF = "pdf"
    const val SOURCE_USER = "user"

    /** Flattens [roots] in DFS pre-order, bounding depth at [maxDepth] and total nodes at [maxNodes]. */
    fun flattenTree(
        roots: List<OutlineTreeNode>,
        maxDepth: Int = MAX_LEVEL,
        maxNodes: Int = DEFAULT_MAX_ENTRIES
    ): List<OutlineNode> {
        if (roots.isEmpty() || maxDepth < 0 || maxNodes <= 0) return emptyList()
        val out = ArrayList<OutlineNode>()
        fun dfs(node: OutlineTreeNode, level: Int) {
            if (level > maxDepth || out.size >= maxNodes) return
            out.add(OutlineNode(title = node.title, pageIndex = node.pageIndex, level = level))
            if (level < maxDepth && out.size < maxNodes) {
                for (child in node.children) {
                    if (out.size >= maxNodes) break
                    dfs(child, level + 1)
                }
            }
        }
        for (root in roots) {
            if (out.size >= maxNodes) break
            dfs(root, 0)
        }
        return out
    }

    fun normalizeTitle(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()

    /**
     * Converts raw [nodes] into [TocItem] entries (`source = "pdf"`), dropping out-of-range
     * `pageIndex !in 0 until pageCount` and blank titles, clamping levels to `0..MAX_LEVEL`,
     * and capping results at [maxEntries].
     */
    fun importNodes(
        nodes: List<OutlineNode>,
        pageCount: Int,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ): List<TocItem> {
        if (pageCount <= 0 || maxEntries <= 0 || nodes.isEmpty()) return emptyList()
        val out = ArrayList<TocItem>(minOf(nodes.size, maxEntries))
        for (node in nodes) {
            if (out.size >= maxEntries) break
            if (node.pageIndex !in 0 until pageCount) continue
            val cleanTitle = normalizeTitle(node.title)
            if (cleanTitle.isEmpty()) continue
            out.add(
                TocItem(
                    title = cleanTitle,
                    pageIndex = node.pageIndex,
                    level = node.level.coerceIn(0, MAX_LEVEL),
                    source = SOURCE_PDF
                )
            )
        }
        return out
    }

    /**
     * Retains non-PDF (user-added) entries from [existing], replaces old PDF entries with
     * [imported], and stably sorts the combined list by `pageIndex`.
     */
    fun mergeWithUserEntries(existing: List<TocItem>, imported: List<TocItem>): List<TocItem> {
        val userEntries = existing.filter { it.source != SOURCE_PDF }
        return (userEntries + imported).sortedBy { it.pageIndex }
    }

    /**
     * Returns the ID of the active chapter for [currentPageIndex]: the last entry in [entries]
     * with the greatest `pageIndex <= currentPageIndex`, or `null` if none precedes or matches it.
     */
    fun activeEntryId(entries: List<TocItem>, currentPageIndex: Int): String? {
        var bestPage = Int.MIN_VALUE
        var bestId: String? = null
        for (entry in entries) {
            if (entry.pageIndex <= currentPageIndex && entry.pageIndex >= bestPage) {
                bestPage = entry.pageIndex
                bestId = entry.id
            }
        }
        return bestId
    }
}
