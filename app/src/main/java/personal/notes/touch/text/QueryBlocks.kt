package personal.notes.touch.text

import personal.notes.touch.data.db.dao.BlockCandidate

/**
 * The embedded query block's tiny search language.
 *
 * A QUERY block stores one expression; its results are live blocks from every
 * text note, so a query is a view over the knowledge base rather than a copy:
 *
 * ```
 * tag:project alpha          blocks tagged #project# that also mention alpha
 * text:"launch plan"         phrase in the block text
 * title:Physics              blocks whose note title matches
 * offset:1250                (reserved; not implemented)
 * ```
 *
 * Supported prefixes: `tag:`, `text:`, `title:` (and `note:` as an alias of
 * title). A bare token matches text or title. Terms are ANDed. Everything is
 * case-insensitive. QUERY blocks themselves are never returned, so a query
 * cannot list itself.
 *
 * Pure Kotlin so the grammar and matching are testable without Room.
 */
object QueryBlocks {

    data class Term(val prefix: String?, val value: String)

    data class Parsed(
        val terms: List<Term> = emptyList(),
        val empty: Boolean = false
    )

    /** Splits on whitespace, keeping quoted strings together. */
    fun parse(expression: String): Parsed {
        val tokens = tokenize(expression.trim())
        if (tokens.isEmpty()) return Parsed(empty = true)
        val terms = tokens.map { token ->
            val colon = token.indexOf(':')
            if (colon > 0) {
                val prefix = token.substring(0, colon).lowercase()
                val value = token.substring(colon + 1)
                if (prefix in PREFIXES && value.isNotBlank()) {
                    Term(prefix, value)
                } else {
                    Term(null, token)
                }
            } else {
                Term(null, token)
            }
        }
        return Parsed(terms)
    }

    data class Result(
        val candidate: BlockCandidate,
        val score: Int
    )

    /**
     * Filters [candidates] (already ordered newest note first). [limit] caps
     * the list; the scoring only orders exact tag hits above plain text hits
     * inside the same note ordering window.
     */
    fun search(
        candidates: List<BlockCandidate>,
        expression: String,
        excludeBlockId: String? = null,
        limit: Int = 20
    ): List<BlockCandidate> {
        val parsed = parse(expression)
        if (parsed.empty) return emptyList()
        val out = ArrayList<Result>()
        for (candidate in candidates) {
            if (candidate.blockId == excludeBlockId) continue
            if (candidate.blockType == "QUERY") continue
            if (candidate.text.isBlank()) continue
            val score = score(candidate, parsed) ?: continue
            out += Result(candidate, score)
        }
        return out.sortedWith(
            compareByDescending<Result> { it.score }
                .thenBy { it.candidate.noteTitle.lowercase() }
                .thenBy { it.candidate.blockIndex }
        ).take(limit.coerceAtLeast(0)).map { it.candidate }
    }

    /** Null when the candidate does not satisfy every term. */
    private fun score(candidate: BlockCandidate, parsed: Parsed): Int? {
        val text = candidate.text.lowercase()
        val title = candidate.noteTitle.lowercase()
        val tags = NoteTags.parseLine(candidate.text).map { it.key }
        var score = 0
        for (term in parsed.terms) {
            when (term.prefix) {
                "tag" -> {
                    val wanted = term.value.lowercase().trimStart('#').trimEnd('#')
                    val hit = tags.any { it == wanted || it.startsWith("$wanted/") }
                    if (!hit) return null
                    score += 3
                }
                "title", "note" -> {
                    if (!title.contains(term.value.lowercase())) return null
                    score += 2
                }
                "text" -> {
                    if (!text.contains(term.value.lowercase())) return null
                    score += 1
                }
                else -> {
                    val needle = term.value.lowercase()
                    if (!text.contains(needle) && !title.contains(needle)) return null
                    score += 1
                }
            }
        }
        return score
    }

    private fun tokenize(input: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        for (c in input) {
            when {
                c == '"' -> quoted = !quoted
                c.isWhitespace() && !quoted -> {
                    if (current.isNotEmpty()) {
                        out += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private val PREFIXES = setOf("tag", "text", "title", "note")
}
