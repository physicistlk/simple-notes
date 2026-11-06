package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.InlineFormat
import personal.notes.touch.text.InlineKind
import personal.notes.touch.text.InlineScan
import personal.notes.touch.text.InlineSpan

class InlineFormatTest {

    @Test
    fun plainTextHasNoSpans() {
        assertEquals(InlineScan(), InlineFormat.scan("Just words, no markers."))
        assertEquals(InlineScan(), InlineFormat.scan(""))
    }

    @Test
    fun boldContentAndMarkers() {
        val scan = InlineFormat.scan("a **b** c")
        assertEquals(listOf(InlineSpan(4, 5, InlineKind.BOLD)), scan.spans)
        assertEquals(listOf(2..3, 5..6), scan.markers)
    }

    @Test
    fun italicContentAndMarkers() {
        val scan = InlineFormat.scan("a *b* c")
        assertEquals(listOf(InlineSpan(3, 4, InlineKind.ITALIC)), scan.spans)
        assertEquals(listOf(2..2, 4..4), scan.markers)
    }

    @Test
    fun boldItalicTripleMarkers() {
        val scan = InlineFormat.scan("***x***")
        assertEquals(listOf(InlineSpan(3, 4, InlineKind.BOLD_ITALIC)), scan.spans)
        assertEquals(listOf(0..2, 4..6), scan.markers)
    }

    @Test
    fun codeWinsOverEmphasisInside() {
        val scan = InlineFormat.scan("`**x**`")
        assertEquals(listOf(InlineSpan(1, 6, InlineKind.CODE)), scan.spans)
        assertEquals(listOf(0..0, 6..6), scan.markers)
    }

    @Test
    fun boldCanContainNestedItalic() {
        val scan = InlineFormat.scan("**a *b* c**")
        assertEquals(
            listOf(
                InlineSpan(2, 9, InlineKind.BOLD),
                InlineSpan(5, 6, InlineKind.ITALIC)
            ),
            scan.spans
        )
        assertEquals(listOf(0..1, 4..4, 6..6, 9..10), scan.markers)
    }

    @Test
    fun italicCanContainNestedBold() {
        val scan = InlineFormat.scan("*a **b** c*")
        assertEquals(
            listOf(
                InlineSpan(1, 10, InlineKind.ITALIC),
                InlineSpan(5, 6, InlineKind.BOLD)
            ),
            scan.spans
        )
        assertEquals(listOf(0..0, 3..4, 6..7, 10..10), scan.markers)
    }

    @Test
    fun strikeAndHighlight() {
        val strike = InlineFormat.scan("~~gone~~")
        assertEquals(listOf(InlineSpan(2, 6, InlineKind.STRIKE)), strike.spans)
        assertEquals(listOf(0..1, 6..7), strike.markers)

        val mark = InlineFormat.scan("x ==y== z")
        assertEquals(listOf(InlineSpan(4, 5, InlineKind.HIGHLIGHT)), mark.spans)
        assertEquals(listOf(2..3, 5..6), mark.markers)
    }

    @Test
    fun underlineTag() {
        val scan = InlineFormat.scan("x <u>y</u> z")
        assertEquals(listOf(InlineSpan(5, 6, InlineKind.UNDERLINE)), scan.spans)
        assertEquals(listOf(2..4, 6..9), scan.markers)
    }

    @Test
    fun unmatchedTagStaysLiteral() {
        assertTrue(InlineFormat.scan("<u>open").isEmpty)
        assertTrue(InlineFormat.scan("plain </u> tag").isEmpty)
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertTrue(InlineFormat.scan("**not bold").isEmpty)
        assertTrue(InlineFormat.scan("a `b").isEmpty)
        assertTrue(InlineFormat.scan("~~open").isEmpty)
        assertTrue(InlineFormat.scan("==open").isEmpty)
        assertTrue(InlineFormat.scan("***only").isEmpty)
    }

    @Test
    fun emptyPairsAreNotSpans() {
        assertTrue(InlineFormat.scan("**").isEmpty)
        assertTrue(InlineFormat.scan("``").isEmpty)
        // "****" is ambiguous: the scanner reads it as *<*>*, which still
        // renders the same one-character text.
        assertTrue(InlineFormat.scan("****").markers.isNotEmpty())
    }

    @Test
    fun markersAreAscendingAndDisjoint() {
        val scan = InlineFormat.scan("*a **b** c*")
        var last = -1
        for (range in scan.markers) {
            assertTrue("marker ${range} overlaps", range.first > last)
            last = range.last
        }
    }
}
