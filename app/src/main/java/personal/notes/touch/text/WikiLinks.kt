package personal.notes.touch.text

/** One `[[Target]]` / `[[Target|alias]]` occurrence. */
data class WikiRef(val target: String, val alias: String?, val key: String) {
    val display: String get() = alias ?: target
}

/**
 * Wiki links: `[[Note title]]` with an optional `[[Note title|what the reader
 * sees]]`.
 *
 * This is the deliberate, bounded half of the knowledge-graph idea: references
 * are resolved **by title**, not by block id. Renaming a note therefore leaves
 * older references unresolved instead of silently retargeting them, which the
 * references sheet shows explicitly. Fenced code blocks are skipped.
 */
object WikiLinks {

    private const val MAX_TARGET = 120

    private val REF = Regex("""\[\[([^\[\]|\n]{1,120})(?:\|([^\[\]\n]{1,120}))?]]""")

    /** Matching key: trimmed, lowercased, inner whitespace collapsed. */
    fun key(target: String): String =
        target.trim().lowercase().replace(Regex("\\s+"), " ")

    fun parse(content: String): List<WikiRef> {
        if (!content.contains("[[")) return emptyList()
        val seen = LinkedHashMap<String, WikiRef>()
        for (line in MarkdownText.outsideFences(content).split('\n')) {
            for (ref in parseLine(line)) {
                // First occurrence wins (putIfAbsent is API 24, minSdk 21).
                if (!seen.containsKey(ref.key)) seen[ref.key] = ref
            }
        }
        return seen.values.toList()
    }

    fun parseLine(line: String): List<WikiRef> {
        if (!line.contains("[[")) return emptyList()
        return REF.findAll(line).mapNotNull { match ->
            val target = match.groupValues[1].trim()
            if (target.isEmpty()) return@mapNotNull null
            val alias = match.groupValues[2].trim().ifEmpty { null }
            WikiRef(target = target, alias = alias, key = key(target))
        }.toList()
    }

    /** An opened, not yet closed `[[query` ending at the caret (autocomplete). */
    data class Pending(val start: Int, val query: String, val end: Int)

    fun pendingAt(text: String, caret: Int): Pending? {
        if (caret < 2 || caret > text.length) return null
        val start = text.lastIndexOf("[[", caret - 2)
        if (start < 0) return null
        val between = text.substring(start + 2, caret)
        if (between.contains("]]") || between.contains('|') || between.contains('\n')) return null
        if (between.length > MAX_TARGET) return null
        // Already closed right after the caret: nothing to complete.
        if (text.startsWith("]]", caret)) return null
        return Pending(start = start, query = between, end = caret)
    }

    /** Replaces the pending `[[query` with a complete `[[title]]`. */
    fun complete(text: String, pending: Pending, title: String): Pair<String, Int> {
        val safe = title.replace("[", "").replace("]", "").trim()
        val insertion = "[[$safe]]"
        val newText = text.substring(0, pending.start) + insertion + text.substring(pending.end)
        return newText to (pending.start + insertion.length)
    }

    /** Titles that match [query] (case-insensitive substring), best first. */
    fun suggestions(query: String, titles: List<String>, exclude: String? = null, limit: Int = 6): List<String> {
        val q = query.trim().lowercase()
        val skip = exclude?.let { key(it) }
        return titles.asSequence()
            .filter { it.isNotBlank() }
            .filter { skip == null || key(it) != skip }
            .filter { q.isEmpty() || it.lowercase().contains(q) }
            .sortedWith(compareBy({ !it.lowercase().startsWith(q) }, { it.lowercase() }))
            .distinctBy { key(it) }
            .take(limit)
            .toList()
    }
}
