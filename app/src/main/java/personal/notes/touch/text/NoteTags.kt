package personal.notes.touch.text

/** One `#tag#` found in a note. [key] is the identity, [display] the casing. */
data class NoteTag(val key: String, val display: String)

/**
 * Tag index parser for the note grammar shipped with the block editor:
 * `#tag#` and `#category/tag#` (see RichTextParser).
 *
 * Rules:
 * - a tag is closed by a second `#` on the same line, has no whitespace, and
 *   is at most [MAX_LENGTH] characters;
 * - `# Heading`, bare `##`, an unclosed `#`, and `C#`-style text are not tags;
 * - fenced code blocks are skipped ([MarkdownText.outsideFences]);
 * - identity is case-insensitive (`#Work#` and `#work#` are one tag), the first
 *   spelling seen is kept for display.
 */
object NoteTags {

    const val MAX_LENGTH = 64

    private val DISALLOWED = charArrayOf('#', '[', ']', '(', ')', '|', '\n', '\r', '\t')

    fun parse(content: String): List<NoteTag> {
        if (!content.contains('#')) return emptyList()
        val seen = LinkedHashMap<String, String>()
        for (line in MarkdownText.outsideFences(content).split('\n')) {
            for (tag in parseLine(line)) {
                // First occurrence wins (putIfAbsent is API 24, minSdk 21).
                if (!seen.containsKey(tag.key)) seen[tag.key] = tag.display
            }
        }
        return seen.map { (key, display) -> NoteTag(key, display) }
    }

    fun parseLine(line: String): List<NoteTag> {
        if (line.indexOf('#') < 0) return emptyList()
        val out = ArrayList<NoteTag>(2)
        var i = 0
        while (i < line.length) {
            if (line[i] != '#') {
                i++
                continue
            }
            // "C#" / "a#b": a # glued to a word is prose, not a tag opener.
            if (i > 0 && (line[i - 1].isLetterOrDigit() || line[i - 1] == '/')) {
                i++
                continue
            }
            val end = line.indexOf('#', startIndex = i + 1)
            if (end <= i + 1) {
                i++
                continue
            }
            val raw = line.substring(i + 1, end)
            val tag = normalise(raw)
            if (tag == null) {
                i++
            } else {
                out.add(tag)
                i = end + 1
            }
        }
        return out
    }

    /** Null when [raw] cannot be a tag (empty, too long, or containing syntax). */
    fun normalise(raw: String): NoteTag? {
        if (raw.isEmpty() || raw.length > MAX_LENGTH) return null
        if (raw.any { it.isWhitespace() }) return null
        if (raw.any { c -> DISALLOWED.contains(c) }) return null
        if (raw.none { it.isLetterOrDigit() }) return null
        return NoteTag(key = raw.lowercase(), display = raw)
    }

    /** `#tag#` markdown for [display], the form the editor inserts everywhere. */
    fun markdown(display: String): String = "#$display#"
}
