package personal.notes.touch.text

/** Inline emphasis family, as far as the editor surface can draw it. */
enum class InlineKind {
    BOLD,
    ITALIC,
    BOLD_ITALIC,
    CODE,
    STRIKE,
    HIGHLIGHT,
    UNDERLINE
}

/** A styled content range `[start, end)` in the text it belongs to. */
data class InlineSpan(val start: Int, val end: Int, val kind: InlineKind)

/**
 * Result of one inline scan.
 *
 * [markers] holds the delimiter ranges (inclusive) in ascending order. They are
 * the characters the editing surface can hide to look like the preview; the
 * content ranges never include them.
 */
data class InlineScan(
    val spans: List<InlineSpan> = emptyList(),
    val markers: List<IntRange> = emptyList()
) {
    val isEmpty: Boolean get() = spans.isEmpty() && markers.isEmpty()
}

/**
 * Inline markdown scanner used by the editing surface.
 *
 * Deliberately mirrors the subset `ui.editor.RichTextParser` already renders,
 * so the editor shows what the preview will show:
 * `**bold**`, `*italic*`, `***bold italic***`, `` `code` ``, `~~strike~~`,
 * `==highlight==` and `<u>underline</u>`. Unmatched markers are left alone — a half-typed `**` must
 * not make the rest of the block disappear. Code spans win over everything
 * inside them; emphasis can nest (`**bold *italic* text**`), and the nested
 * markers are collected so they can be hidden as well.
 *
 * Pure Kotlin: the ranges are unit tested without a device or a Compose host.
 * Exporter markdown parsing lives in `InlineMarkdown`, which also understands
 * links and `<u>`; this file is only about what the block field can draw.
 */
object InlineFormat {

    fun scan(text: String): InlineScan {
        if (text.isEmpty()) return InlineScan()
        val spans = ArrayList<InlineSpan>()
        val markers = ArrayList<IntRange>()
        scanRange(text, 0, text.length, spans, markers)
        if (spans.isEmpty()) return InlineScan(emptyList(), emptyList())
        spans.sortBy { it.start }
        markers.sortBy { it.first }
        return InlineScan(spans, markers)
    }

    private fun scanRange(
        text: String,
        from: Int,
        to: Int,
        spans: MutableList<InlineSpan>,
        markers: MutableList<IntRange>
    ) {
        var i = from
        while (i < to) {
            val c = text[i]

            // `code` — highest priority: nothing inside is interpreted.
            if (c == '`') {
                val close = text.indexOf('`', i + 1)
                if (close in (i + 2) until to) {
                    markers += i..i
                    spans += InlineSpan(i + 1, close, InlineKind.CODE)
                    markers += close..close
                    i = close + 1
                    continue
                }
            }

            // <u>underline</u> — the one inline tag the toolbar inserts.
            if (c == '<' && text.startsWith("<u>", i)) {
                val close = text.indexOf("</u>", i + 3)
                if (close != -1 && close + 4 <= to && close > i + 3) {
                    markers += i..i + 2
                    scanRange(text, i + 3, close, spans, markers)
                    markers += close..close + 3
                    spans += InlineSpan(i + 3, close, InlineKind.UNDERLINE)
                    i = close + 4
                    continue
                }
            }

            // ***bold italic*** before ** so the outer three stay together.
            if (c == '*' && text.startsWith("***", i)) {
                val close = text.indexOf("***", i + 3)
                if (close != -1 && close + 3 <= to && close > i + 3) {
                    markers += i..i + 2
                    scanRange(text, i + 3, close, spans, markers)
                    markers += close..close + 2
                    spans += InlineSpan(i + 3, close, InlineKind.BOLD_ITALIC)
                    i = close + 3
                    continue
                }
            }

            // **bold**
            if (c == '*' && text.startsWith("**", i) && i + 2 < to) {
                val close = text.indexOf("**", i + 2)
                if (close != -1 && close + 2 <= to && close > i + 2) {
                    markers += i..i + 1
                    scanRange(text, i + 2, close, spans, markers)
                    markers += close..close + 1
                    spans += InlineSpan(i + 2, close, InlineKind.BOLD)
                    i = close + 2
                    continue
                }
            }

            // ~~strike~~
            if (c == '~' && text.startsWith("~~", i) && i + 2 < to) {
                val close = text.indexOf("~~", i + 2)
                if (close != -1 && close + 2 <= to && close > i + 2) {
                    markers += i..i + 1
                    scanRange(text, i + 2, close, spans, markers)
                    markers += close..close + 1
                    spans += InlineSpan(i + 2, close, InlineKind.STRIKE)
                    i = close + 2
                    continue
                }
            }

            // ==highlight==
            if (c == '=' && text.startsWith("==", i) && i + 2 < to) {
                val close = text.indexOf("==", i + 2)
                if (close != -1 && close + 2 <= to && close > i + 2) {
                    markers += i..i + 1
                    scanRange(text, i + 2, close, spans, markers)
                    markers += close..close + 1
                    spans += InlineSpan(i + 2, close, InlineKind.HIGHLIGHT)
                    i = close + 2
                    continue
                }
            }

            // *italic* — skip ** pairs so `*a **b** c*` closes on the real
            // outer star instead of the first half of a nested bold marker.
            if (c == '*') {
                val close = findItalicClose(text, i + 1, to)
                if (close in (i + 2) until to) {
                    markers += i..i
                    scanRange(text, i + 1, close, spans, markers)
                    markers += close..close
                    spans += InlineSpan(i + 1, close, InlineKind.ITALIC)
                    i = close + 1
                    continue
                }
            }

            i++
        }
    }

    /** First single `*` in `[from, to)`, stepping over `**` pairs. */
    private fun findItalicClose(text: String, from: Int, to: Int): Int {
        var j = from
        while (j < to) {
            if (text[j] == '*') {
                if (j + 1 < to && text[j + 1] == '*') {
                    j += 2
                    continue
                }
                return j
            }
            j++
        }
        return -1
    }
}
