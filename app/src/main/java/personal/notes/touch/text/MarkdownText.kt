package personal.notes.touch.text

/**
 * Line-oriented scanning shared by the tag index, the wiki-link index and
 * content search.
 *
 * All three read user markdown, and all three must agree on one thing: text
 * inside a fenced code block is *quoted* markup, not markup. A `#tag#` or a
 * `[[Note]]` inside a ``` sample would otherwise show up in the tag list or
 * create a phantom backlink.
 */
object MarkdownText {

    private fun isFence(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("```") || trimmed.startsWith("~~~")
    }

    /**
     * Same line count as [content], with every fenced line (fences included)
     * replaced by an empty line. Line count is preserved so callers can still
     * report positions by line.
     */
    fun outsideFences(content: String): String {
        if (!content.contains("```") && !content.contains("~~~")) return content
        val lines = content.split('\n')
        val out = ArrayList<String>(lines.size)
        var fenced = false
        for (line in lines) {
            if (isFence(line)) {
                fenced = !fenced
                out.add("")
            } else {
                out.add(if (fenced) "" else line)
            }
        }
        return out.joinToString("\n")
    }

    /** Single-line, marker-free text suitable for a search snippet. */
    fun plain(line: String): String = line
        .trim()
        .replace(Regex("^#{1,6}\\s*"), "")
        .replace(Regex("^[-*+]\\s+\\[[ xX]]\\s*"), "")
        .replace(Regex("^[-*+]\\s+"), "")
        .replace(Regex("^\\d+[.)]\\s+"), "")
        .replace(Regex("^>\\s*"), "")
        // [[Target|alias]] -> alias, [[Target]] -> Target
        .replace(Regex("\\[\\[([^\\[\\]|\\n]+)(?:\\|([^\\[\\]\\n]+))?]]")) { m ->
            m.groupValues[2].ifBlank { m.groupValues[1] }.trim()
        }
        // ![alt](src) -> alt (or [Image] when the alt is empty)
        .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)")) { m ->
            m.groupValues[1].ifBlank { "[Image]" }
        }
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("==([^=]+)=="), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("~~([^~]+)~~"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}
