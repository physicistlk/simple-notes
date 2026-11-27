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
    fun count(range: IntRange?): Int = range?.let { if (it.isEmpty()) 0 else it.last - it.first + 1 } ?: 0

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
        val t = if (thickness.isFinite()) thickness.coerceAtLeast(1f) else 1f
        val bottom = rect[3]
        return floatArrayOf(rect[0], bottom - t, rect[2], bottom)
    }

    /** Converts a screen-pixel hit tolerance into page units at view [scale]. */
    fun tolerancePageUnits(screenPx: Float, scale: Float): Float {
        if (!screenPx.isFinite()) return 0f
        if (!scale.isFinite() || scale <= 0f) return screenPx
        val res = screenPx / scale
        return if (res.isFinite()) res else screenPx
    }
}

enum class TextDockTapAction {
    OPEN_LIVE_TEXT,
    OPEN_TEXT_MENU,
    EXIT_PDF_TEXT_MODE
}

enum class PdfTextSelectActivation {
    UNAVAILABLE,
    ACTIVATE_IN_SINGLE_PAGE,
    SWITCH_TO_SINGLE_AND_ACTIVATE
}

/**
 * Pure-JVM routing and availability policy for dock `[T]` PDF text selection (`PDF-03`).
 */
object PdfTextToolPolicy {

    fun isAvailable(isVectorPdfPage: Boolean, charCount: Int): Boolean =
        isVectorPdfPage && charCount > 0

    fun dockTapAction(pdfTextAvailable: Boolean, pdfTextMode: Boolean): TextDockTapAction = when {
        pdfTextMode -> TextDockTapAction.EXIT_PDF_TEXT_MODE
        pdfTextAvailable -> TextDockTapAction.OPEN_TEXT_MENU
        else -> TextDockTapAction.OPEN_LIVE_TEXT
    }

    fun selectPdfTextAction(pdfTextAvailable: Boolean, isContinuous: Boolean): PdfTextSelectActivation = when {
        !pdfTextAvailable -> PdfTextSelectActivation.UNAVAILABLE
        isContinuous -> PdfTextSelectActivation.SWITCH_TO_SINGLE_AND_ACTIVATE
        else -> PdfTextSelectActivation.ACTIVATE_IN_SINGLE_PAGE
    }

    fun shouldExitPdfTextMode(
        pdfTextMode: Boolean,
        readingMode: Boolean,
        isContinuous: Boolean,
        switchingToSinglePage: Boolean
    ): Boolean = pdfTextMode && (readingMode || (isContinuous && !switchingToSinglePage))
}
