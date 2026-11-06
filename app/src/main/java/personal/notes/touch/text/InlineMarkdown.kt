package personal.notes.touch.text

/**
 * Inline markdown → styled runs, shared by the HTML and PDF text-note
 * exporters (the editor itself styles through Compose spans, which a file
 * exporter cannot use).
 *
 * Supported: `**bold**` / `__bold__`, `*italic*` / `_italic_`, `~~strike~~`,
 * `` `code` ``, `==highlight==`, `[label](url)` and `[[Note]]` /
 * `[[Note|alias]]`. A delimiter without a closing partner stays literal text,
 * so half-typed markdown never eats the rest of the line. Backslash escapes
 * the next character.
 *
 * Pure Kotlin (no Android, no Compose) so the exporters and their tests agree
 * on one parse.
 */
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val highlight: Boolean = false,
    /** External link target (`http(s)`, `mailto`, bare host) or wiki title. */
    val link: String? = null,
    /** True when [link] names another note instead of a URL. */
    val wiki: Boolean = false
)

object InlineMarkdown {

    fun parse(text: String): List<InlineRun> {
        val out = ArrayList<InlineRun>()
        parseInto(text, InlineRun(""), out)
        return merge(out)
    }

    /** Marker-free text, for alt text, captions and search-like uses. */
    fun plain(text: String): String = parse(text).joinToString("") { it.text }

    private fun merge(runs: List<InlineRun>): List<InlineRun> {
        if (runs.isEmpty()) return runs
        val out = ArrayList<InlineRun>(runs.size)
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && sameStyle(last, run)) {
                out[out.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                out.add(run)
            }
        }
        return out
    }

    private fun sameStyle(a: InlineRun, b: InlineRun): Boolean =
        a.bold == b.bold && a.italic == b.italic && a.strike == b.strike &&
            a.code == b.code && a.highlight == b.highlight &&
            a.link == b.link && a.wiki == b.wiki

    private fun parseInto(src: String, style: InlineRun, out: MutableList<InlineRun>) {
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                out.add(style.copy(text = buffer.toString()))
                buffer.clear()
            }
        }

        fun emit(run: InlineRun) {
            flush()
            out.add(run)
        }

        var i = 0
        while (i < src.length) {
            val c = src[i]

            // Backslash escape: the next character is literal.
            if (c == '\\' && i + 1 < src.length) {
                buffer.append(src[i + 1])
                i += 2
                continue
            }

            // `code` — contents are literal.
            if (c == '`') {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    emit(style.copy(text = src.substring(i + 1, end), code = true))
                    i = end + 1
                    continue
                }
                buffer.append(c)
                i++
                continue
            }

            // [[Note]] / [[Note|alias]]
            if (c == '[' && i + 1 < src.length && src[i + 1] == '[') {
                val end = src.indexOf("]]", i + 2)
                if (end > i + 2) {
                    val inner = src.substring(i + 2, end)
                    val bar = inner.indexOf('|')
                    val target = (if (bar >= 0) inner.substring(0, bar) else inner).trim()
                    val label = (if (bar >= 0) inner.substring(bar + 1) else inner).trim()
                    if (target.isNotEmpty()) {
                        emit(style.copy(text = label.ifEmpty { target }, wiki = true, link = target))
                        i = end + 2
                        continue
                    }
                }
                buffer.append(c)
                i++
                continue
            }

            // [label](url)
            if (c == '[') {
                val close = src.indexOf(']', i + 1)
                if (close > i + 1 && close + 1 < src.length && src[close + 1] == '(') {
                    val paren = src.indexOf(')', close + 2)
                    if (paren > close + 1) {
                        val label = src.substring(i + 1, close)
                        val url = src.substring(close + 2, paren).trim()
                        emit(style.copy(text = label, link = url.ifEmpty { null }))
                        i = paren + 1
                        continue
                    }
                }
                buffer.append(c)
                i++
                continue
            }

            // **bold** / __bold__
            if (c == '*' || c == '_') {
                val doubled = i + 1 < src.length && src[i + 1] == c
                val marker = if (doubled) "$c$c" else c.toString()
                val end = src.indexOf(marker, i + marker.length)
                if (end > i + marker.length) {
                    val inner = src.substring(i + marker.length, end)
                    flush() // plain text before the marker keeps its own run
                    val nested = ArrayList<InlineRun>()
                    parseInto(
                        inner,
                        if (doubled) style.copy(bold = true) else style.copy(italic = true),
                        nested
                    )
                    out.addAll(nested)
                    i = end + marker.length
                    continue
                }
                buffer.append(c)
                i++
                continue
            }

            // ~~strike~~
            if (c == '~' && i + 1 < src.length && src[i + 1] == '~') {
                val end = src.indexOf("~~", i + 2)
                if (end > i + 2) {
                    emit(style.copy(text = src.substring(i + 2, end), strike = true))
                    i = end + 2
                    continue
                }
                buffer.append(c)
                i++
                continue
            }

            // ==highlight==
            if (c == '=' && i + 1 < src.length && src[i + 1] == '=') {
                val end = src.indexOf("==", i + 2)
                if (end > i + 2) {
                    emit(style.copy(text = src.substring(i + 2, end), highlight = true))
                    i = end + 2
                    continue
                }
                buffer.append(c)
                i++
                continue
            }

            buffer.append(c)
            i++
        }
        flush()
    }
}
