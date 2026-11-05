package personal.notes.touch.util

/**
 * One PDF text selection (defect F-10): inclusive character range plus the
 * extracted text, as reported by [personal.notes.touch.ui.canvas.DrawingCanvasView].
 */
data class PdfTextSelectionInfo(
    val start: Int,
    val end: Int,
    val text: String
) {
    val isEmpty: Boolean
        get() = end < start || text.isEmpty()
}

/**
 * Pure selection math for PDF text (F-10), kept JVM-testable: the canvas and
 * pdfium both only provide primitives (hit testing, char boxes, selection
 * rectangles), so ordering/clamping/snapping lives here.
 */
object PdfTextSelectionMath {

    /**
     * Inclusive char range for an anchor/focus pair (either drag direction).
     * Null when either index is invalid (-1 = no hit).
     */
    fun range(anchor: Int, focus: Int): IntRange? {
        if (anchor < 0 || focus < 0) return null
        return if (anchor <= focus) anchor..focus else focus..anchor
    }

    /** Characters covered by [range]; 0 when nothing is selected. */
    fun count(range: IntRange?): Int = range?.let { it.last - it.first + 1 } ?: 0

    /** Clamps [index] into `0..charCount-1`; -1 for an empty page or no hit. */
    fun clampIndex(index: Int, charCount: Int): Int = when {
        charCount <= 0 -> -1
        index < 0 -> -1
        index >= charCount -> charCount - 1
        else -> index
    }

    /**
     * Underline band at the bottom of a text rect `{left, top, right, bottom}`
     * (canvas space, y down), [thickness] px high (at least 1 px).
     */
    fun underlineRect(rect: FloatArray, thickness: Float): FloatArray {
        val t = thickness.coerceAtLeast(1f)
        val bottom = rect[3]
        return floatArrayOf(rect[0], bottom - t, rect[2], bottom)
    }

    /** Converts a screen-pixel hit tolerance into page units at view [scale]. */
    fun tolerancePageUnits(screenPx: Float, scale: Float): Float =
        if (scale <= 0f) screenPx else screenPx / scale
}
