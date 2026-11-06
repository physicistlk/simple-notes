package personal.notes.touch.text

/**
 * An inline scan resolved against the text the editor will actually draw.
 *
 * [text] is the rendered string (markers removed when [markers] is empty and
 * [origToTrans] is non-null). [spans] are ranges in [text]. [markers] are the
 * visible, dimmed delimiter ranges in [text] — only present when markers are
 * shown.
 *
 * When markers are hidden, [origToTrans] / [transToOrig] carry the offset
 * mapping back to the original markdown, so tapping inside the rendered field
 * still puts the caret in the right place. Both are null when the text was not
 * changed.
 */
data class InlineRender(
    val text: String,
    val spans: List<InlineSpan> = emptyList(),
    val markers: List<IntRange> = emptyList(),
    val origToTrans: IntArray? = null,
    val transToOrig: IntArray? = null
) {
    val isEmpty: Boolean get() = spans.isEmpty() && markers.isEmpty()

    /** True when [text] is the original markdown, so offsets are identity. */
    val identity: Boolean get() = origToTrans == null
}

/**
 * Applies an [InlineFormat] scan to text and, optionally, removes the markers
 * so a block can be shown the way the preview shows it.
 *
 * Hiding never touches the source string: the editor keeps the raw markdown
 * (the value it saves), and only the visual copy loses the delimiters. Marker
 * removal is only used while a block is unfocused, so IME composition and the
 * caret never see a remapped text mid-typing.
 */
object InlineRenderer {

    fun render(text: String, hideMarkers: Boolean): InlineRender {
        val scan = InlineFormat.scan(text)
        if (scan.isEmpty) return InlineRender(text)
        if (!hideMarkers) return InlineRender(text, scan.spans, scan.markers)

        val hidden = BooleanArray(text.length)
        for (range in scan.markers) {
            for (i in range.first..range.last) {
                if (i in text.indices) hidden[i] = true
            }
        }

        val out = StringBuilder(text.length)
        val origToTrans = IntArray(text.length + 1)
        val transToOrig = IntArray(text.length)
        var kept = 0
        for (i in text.indices) {
            origToTrans[i] = out.length
            if (!hidden[i]) {
                out.append(text[i])
                transToOrig[kept++] = i
            }
        }
        origToTrans[text.length] = out.length

        val spans = scan.spans.map {
            InlineSpan(origToTrans[it.start], origToTrans[it.end], it.kind)
        }
        return InlineRender(
            text = out.toString(),
            spans = spans,
            markers = emptyList(),
            origToTrans = origToTrans,
            transToOrig = transToOrig.copyOf(kept)
        )
    }

    /** Offset mapping for a hidden-marker render, or null when identity. */
    fun originalToTransformed(render: InlineRender, offset: Int): Int {
        val map = render.origToTrans ?: return offset
        return map[offset.coerceIn(0, map.lastIndex)]
    }

    fun transformedToOriginal(render: InlineRender, offset: Int): Int {
        val map = render.transToOrig ?: return offset
        return when {
            map.isEmpty() -> render.origToTrans?.lastIndex ?: offset
            offset <= 0 -> map[0]
            offset >= map.size -> render.origToTrans?.lastIndex ?: offset
            else -> map[offset]
        }
    }
}
