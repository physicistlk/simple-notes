package personal.notes.touch.text

/** A matched excerpt: [text] to show, [ranges] to emphasise inside it. */
data class SearchSnippet(val text: String, val ranges: List<IntRange>)

/**
 * Content search over the note's markdown (title search is a separate,
 * cheaper check). Pure Kotlin so it is testable on the JVM.
 *
 * Matching is a plain case-insensitive AND over whitespace-separated tokens.
 * SQL `LIKE` was deliberately not used: it is ASCII-case-insensitive only and
 * treats `%`/`_` in the query as wildcards, so a note search for "100%" would
 * match everything.
 */
object NoteSearch {

    fun tokens(query: String): List<String> =
        query.trim()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun containsAll(haystack: String, tokens: List<String>): Boolean =
        tokens.isNotEmpty() && tokens.all { haystack.contains(it, ignoreCase = true) }

    /** True when every token appears in the title or in the content. */
    fun matches(tokens: List<String>, title: String, content: String): Boolean {
        if (tokens.isEmpty()) return true
        return tokens.all { token ->
            title.contains(token, ignoreCase = true) || content.contains(token, ignoreCase = true)
        }
    }

    /**
     * First matching line of [content] flattened to plain text, windowed around
     * the first hit. Null when no line matches (a title-only match).
     */
    fun snippet(content: String, tokens: List<String>, radius: Int = 44): SearchSnippet? {
        if (tokens.isEmpty() || content.isBlank()) return null
        for (rawLine in content.split('\n')) {
            val line = MarkdownText.plain(rawLine)
            if (line.isEmpty()) continue
            val first = tokens
                .mapNotNull { token ->
                    val at = line.indexOf(token, ignoreCase = true)
                    if (at < 0) null else at to token.length
                }
                .minByOrNull { it.first }
                ?: continue
            return window(line, first.first, first.second, tokens, radius)
        }
        return null
    }

    private fun window(
        line: String,
        hitAt: Int,
        hitLength: Int,
        tokens: List<String>,
        radius: Int
    ): SearchSnippet {
        var start = (hitAt - radius).coerceAtLeast(0)
        var end = (hitAt + hitLength + radius).coerceAtMost(line.length)
        // Do not cut words in half at either edge.
        if (start > 0) {
            val space = line.indexOf(' ', start)
            if (space in 1 until hitAt) start = space + 1
        }
        if (end < line.length) {
            val space = line.lastIndexOf(' ', end)
            if (space > hitAt + hitLength) end = space
        }
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < line.length) "…" else ""
        val body = line.substring(start, end).trim()
        val text = prefix + body + suffix
        return SearchSnippet(text = text, ranges = rangesIn(text, tokens))
    }

    /** All occurrences of [tokens] inside [text], merged and sorted. */
    fun rangesIn(text: String, tokens: List<String>): List<IntRange> {
        val found = ArrayList<IntRange>()
        for (token in tokens) {
            if (token.isEmpty()) continue
            var from = 0
            while (from <= text.length - token.length) {
                val at = text.indexOf(token, from, ignoreCase = true)
                if (at < 0) break
                found.add(at until (at + token.length))
                from = at + 1
            }
        }
        if (found.isEmpty()) return emptyList()
        found.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        var current = found.first()
        for (range in found.drop(1)) {
            current = if (range.first <= current.last + 1) {
                current.first..maxOf(current.last, range.last)
            } else {
                merged.add(current)
                range
            }
        }
        merged.add(current)
        return merged
    }
}
