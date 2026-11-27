package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.util.PdfTextSelectActivation
import personal.notes.touch.util.PdfTextToolPolicy
import personal.notes.touch.util.TextDockTapAction

/**
 * Pure-JVM unit tests for `PDF-03` dock `[T]` availability, menu routing,
 * Continuous -> Single-Page switching, and lifecycle exit rules.
 */
class PdfTextToolPolicyTest {

    @Test
    fun isAvailable_trueOnlyForVectorPdfWithPositiveCharCount() {
        assertTrue(PdfTextToolPolicy.isAvailable(isVectorPdfPage = true, charCount = 42))
        assertFalse(PdfTextToolPolicy.isAvailable(isVectorPdfPage = true, charCount = 0))
        assertFalse(PdfTextToolPolicy.isAvailable(isVectorPdfPage = false, charCount = 100))
    }

    @Test
    fun dockTapAction_opensLiveTextWhenUnavailable_menuWhenAvailable_exitWhenActive() {
        assertEquals(
            TextDockTapAction.OPEN_LIVE_TEXT,
            PdfTextToolPolicy.dockTapAction(pdfTextAvailable = false, pdfTextMode = false)
        )
        assertEquals(
            TextDockTapAction.OPEN_TEXT_MENU,
            PdfTextToolPolicy.dockTapAction(pdfTextAvailable = true, pdfTextMode = false)
        )
        assertEquals(
            TextDockTapAction.EXIT_PDF_TEXT_MODE,
            PdfTextToolPolicy.dockTapAction(pdfTextAvailable = true, pdfTextMode = true)
        )
        assertEquals(
            TextDockTapAction.EXIT_PDF_TEXT_MODE,
            PdfTextToolPolicy.dockTapAction(pdfTextAvailable = false, pdfTextMode = true)
        )
    }

    @Test
    fun selectPdfTextAction_routesByAvailabilityAndTurningMode() {
        assertEquals(
            PdfTextSelectActivation.UNAVAILABLE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = false, isContinuous = true)
        )
        assertEquals(
            PdfTextSelectActivation.UNAVAILABLE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = false, isContinuous = false)
        )
        assertEquals(
            PdfTextSelectActivation.SWITCH_TO_SINGLE_AND_ACTIVATE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = true, isContinuous = true)
        )
        assertEquals(
            PdfTextSelectActivation.ACTIVATE_IN_SINGLE_PAGE,
            PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable = true, isContinuous = false)
        )
    }

    @Test
    fun shouldExitPdfTextMode_handlesReadingModeAndContinuousTransitions() {
        assertFalse(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = false,
                readingMode = true,
                isContinuous = true,
                switchingToSinglePage = false
            )
        )
        assertTrue(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true,
                readingMode = true,
                isContinuous = false,
                switchingToSinglePage = false
            )
        )
        // Pending Continuous -> Single-Page switch must NOT prematurely cancel pdfTextMode
        assertFalse(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true,
                readingMode = false,
                isContinuous = true,
                switchingToSinglePage = true
            )
        )
        // Returning to Continuous mode later exits pdfTextMode
        assertTrue(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true,
                readingMode = false,
                isContinuous = true,
                switchingToSinglePage = false
            )
        )
        assertFalse(
            PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = true,
                readingMode = false,
                isContinuous = false,
                switchingToSinglePage = false
            )
        )
    }
}
