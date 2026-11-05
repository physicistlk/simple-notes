package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.WikiLinks

/**
 * Wiki links (`[[Title]]` / `[[Title|alias]]`) and the `[[` completion state
 * machine used by the block editor.
 */
class WikiLinksTest {

    @Test
    fun parsesPlainAndAliasedLinks() {
        val refs = WikiLinks.parse("See [[Roadmap]] and [[Budget|the budget]].")
        assertEquals(2, refs.size)
        assertEquals("Roadmap", refs[0].target)
        assertNull(refs[0].alias)
        assertEquals("Roadmap", refs[0].display)
        assertEquals("Budget", refs[1].target)
        assertEquals("the budget", refs[1].display)
        assertEquals("budget", refs[1].key)
    }

    @Test
    fun keyIsCaseAndSpaceInsensitive() {
        assertEquals("my note", WikiLinks.key("  My   NOTE "))
        assertEquals(WikiLinks.key("Roadmap"), WikiLinks.key("roadmap"))
        // Same target spelled differently is one reference.
        val refs = WikiLinks.parse("[[Roadmap]] [[ roadmap ]]")
        assertEquals(1, refs.size)
    }

    @Test
    fun fencedCodeBlocksAreSkipped() {
        val content = """
            [[Real]]

            ```
            [[Not a link]]
            ```
        """.trimIndent()
        assertEquals(listOf("real"), WikiLinks.parse(content).map { it.key })
    }

    @Test
    fun rejectsEmptyAndUnclosedLinks() {
        assertTrue(WikiLinks.parse("[[]]").isEmpty())
        assertTrue(WikiLinks.parse("[[unclosed").isEmpty())
        assertTrue(WikiLinks.parse("[[a\nb]]").isEmpty())
    }

    @Test
    fun pendingQueryTracksTheOpenBracketRun() {
        val text = "See [[Pro"
        val pending = WikiLinks.pendingAt(text, text.length)
        assertEquals("Pro", pending?.query)
        assertEquals(4, pending?.start)
        assertEquals(text.length, pending?.end)
    }

    @Test
    fun pendingQueryIsNullWhenTheLinkIsClosedOrAliased() {
        assertNull(WikiLinks.pendingAt("[[Pro]]", 7))
        assertNull(WikiLinks.pendingAt("[[Pro|alias", 12))
        assertNull(WikiLinks.pendingAt("line one\n[[Pro", 16))
        assertNull(WikiLinks.pendingAt("no brackets", 4))
        // Caret before the opening brackets.
        assertNull(WikiLinks.pendingAt("[[Pro", 1))
    }

    @Test
    fun completionReplacesOnlyTheOpenRun() {
        val text = "See [[Pro and more"
        val pending = requireNotNull(WikiLinks.pendingAt(text, 9))
        val (result, caret) = WikiLinks.complete(text, pending, "Project Alpha")
        assertEquals("See [[Project Alpha]] and more", result)
        assertEquals(4 + "[[Project Alpha]]".length, caret)
    }

    @Test
    fun suggestionsPreferPrefixMatchesAndSkipSelf() {
        val titles = listOf("Alpha", "Project Alpha", "Beta", "alphabet")
        val result = WikiLinks.suggestions("al", titles, exclude = "Alpha")
        assertEquals(listOf("alphabet", "Project Alpha"), result)

        // Empty query lists everything, alphabetically, excluding self.
        val all = WikiLinks.suggestions("", titles + "Alpha", exclude = "Alpha")
        assertEquals(listOf("alphabet", "Beta", "Project Alpha"), all)
        assertEquals(6, WikiLinks.suggestions("", List(12) { "Note $it" }).size)
    }
}
