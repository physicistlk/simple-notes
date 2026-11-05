package personal.notes.touch.util

import android.graphics.Bitmap

/**
 * Raw JNI surface of the bundled pdfium engine (F-10 vector PDF import).
 *
 * Thin by design: every method maps 1:1 onto one `touchnotes_pdfium.c`
 * entry point, and all policy (fit math, caching, thread confinement) lives
 * in [PdfiumDocument] so it stays testable on the JVM. Do not call these
 * functions from arbitrary threads — [PdfiumDocument] serializes them.
 *
 * Engine: pdfium chromium/8138 (pdfium.googlesource.com, BSD-3-Clause /
 * Apache-2.0), prebuilt per-ABI binaries from bblanchon/pdfium-binaries (MIT).
 * See app/third_party/PDFIUM-NOTICE.md.
 */
object PdfiumBridge {

    init {
        System.loadLibrary("touchnotes_pdfium")
        initLibrary()
    }

    /** Initializes the global pdfium library once per process (idempotent). */
    private external fun initLibrary()

    /** Parses [data] into a document. Returns 0 on failure. */
    external fun openDocument(data: ByteArray): Long

    external fun closeDocument(handle: Long)

    external fun getPageCount(handle: Long): Int

    /** Page size in PDF points: [width, height]. Zeroes on failure. */
    external fun getPageSize(handle: Long, pageIndex: Int): FloatArray

    /**
     * Renders [pageIndex] into [bitmap] (must be ARGB_8888) such that page
     * point ([srcX], [srcY]) — pdfium user space, bottom-left origin, y up —
     * lands at the bitmap's top-left, at [pxPerPt] pixels per point.
     */
    external fun render(
        handle: Long,
        pageIndex: Int,
        bitmap: Bitmap,
        srcX: Float,
        srcY: Float,
        pxPerPt: Float,
        renderAnnots: Boolean
    ): Boolean

    external fun textCountChars(handle: Long, pageIndex: Int): Int

    /** UTF-16 text of [count] characters from [start]; null on failure. */
    external fun textGetChars(
        handle: Long,
        pageIndex: Int,
        start: Int,
        count: Int
    ): String?

    /** Number of selection rectangles for a char range (pdfium merges lines). */
    external fun textSelectionRectCount(
        handle: Long,
        pageIndex: Int,
        start: Int,
        count: Int
    ): Int

    /** {left, top, right, bottom} in user space (bottom-left origin, y up). */
    external fun textSelectionRect(handle: Long, pageIndex: Int, rectIndex: Int): FloatArray

    /** Char index at user-space point, -1 when nothing is close enough. */
    external fun textCharIndexAtPos(
        handle: Long,
        pageIndex: Int,
        x: Float,
        y: Float,
        toleranceX: Float,
        toleranceY: Float
    ): Int

    /** {left, top, right, bottom} of one character in user space. */
    external fun textCharBox(handle: Long, pageIndex: Int, charIndex: Int): FloatArray
}
