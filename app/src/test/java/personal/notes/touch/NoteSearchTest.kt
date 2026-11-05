package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.NoteSearch

/**
 * Content search: all tokens must match, matching is case-insensitive (also
 * outside ASCII), `%`/`_` are literal, and the snippet points at the hit.
 */
class NoteSearchTest {

    @Test
    fun tokensSplitOnWhitespaceAndDeduplicate() {
        assertEquals(listOf("alpha", "beta"), NoteSearch.tokens("  alpha   beta  "))
        assertEquals(listOf("same"), NoteSearch.tokens("same SAME"))
        assertTrue(NoteSearch.tokens("   ").isEmpty())
    }

    @Test
    fun everyTokenMustAppearSomewhere() {
        val tokens = NoteSearch.tokens("budget march")
        assertTrue(NoteSearch.matches(tokens, "March plan", "the budget is set"))
        // "march" is missing everywhere…
        assertFalse(NoteSearch.matches(tokens, "April plan", "the budget is set"))
        // …and so is "budget" here.
        assertFalse(NoteSearch.matches(tokens, "March plan", "no numbers here"))
        // Split across title and content is fine.
        assertTrue(NoteSearch.matches(NoteSearch.tokens("budget plan"), "Plan", "budget"))
    }

    @Test
    fun matchingIsCaseInsensitiveForNonAsciiText() {
        assertTrue(NoteSearch.matches(NoteSearch.tokens("ÄPFEL"), "Notizen", "Wir kaufen äpfel."))
        assertTrue(NoteSearch.matches(NoteSearch.tokens("straße"), "STRASSE", "").not())
        assertTrue(NoteSearch.matches(NoteSearch.tokens("où"), "", "Où est la gare ?"))
    }

    @Test
    fun percentAndUnderscoreAreLiteral() {
        val tokens = NoteSearch.tokens("100%")
        assertTrue(NoteSearch.matches(tokens, "", "saved 100% of it"))
        assertFalse(NoteSearch.matches(tokens, "", "saved 100 of it"))

        val underscore = NoteSearch.tokens("a_b")
        assertTrue(NoteSearch.matches(underscore, "", "a_b"))
        assertFalse(NoteSearch.matches(underscore, "", "axb"))
    }

    @Test
    fun snippetShowsTheMatchingLineWithoutMarkup() {
        val content = """
            # Groceries
            - milk and **bread**
            - coffee beans
        """.trimIndent()
        val snippet = NoteSearch.snippet(content, NoteSearch.tokens("bread"))
        assertEquals("milk and bread", snippet?.text)
        assertEquals(listOf(9..13), snippet?.ranges)
    }

    @Test
    fun snippetIsWindowedWithEllipsesAndMergedRanges() {
        val long = "start " + "x".repeat(120) + " needle " + "y".repeat(120) + " end"
        val snippet = NoteSearch.snippet(long, NoteSearch.tokens("needle"), radius = 20)
        requireNotNull(snippet)
        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("…"))
        assertTrue(snippet.text.contains("needle"))
        // Highlight covers exactly the token.
        val range = snippet.ranges.single()
        assertEquals("needle", snippet.text.substring(range.first, range.last + 1))
    }

    @Test
    fun snippetIsNullWhenOnlyTheTitleMatches() {
        assertNull(NoteSearch.snippet("unrelated body", NoteSearch.tokens("title")))
        assertNull(NoteSearch.snippet("", NoteSearch.tokens("x")))
    }

    @Test
    fun rangesMergeOverlapsAndIgnoreCase() {
        val ranges = NoteSearch.rangesIn("aaa aa", listOf("aa", "AA"))
        assertEquals(listOf(0..2, 4..5), ranges)
    }

    @Test
    fun wikiAndImageMarkupCollapseInSnippets() {
        val content = "See [[Roadmap|the plan]] and ![cat](img_1.jpg) for #work# details"
        val snippet = requireNotNull(NoteSearch.snippet(content, NoteSearch.tokens("plan")))
        assertEquals("See the plan and cat for #work# details", snippet.text)
        assertFalse(snippet.text.contains("[["))
        assertFalse(snippet.text.contains("]("))
    }
}
