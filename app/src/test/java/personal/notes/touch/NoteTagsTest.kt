package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.NoteTags

/**
 * Tag grammar: `#tag#` / `#category/tag#`, the form the block editor styles
 * and RichTextParser recognises. The index and the editor must agree, so the
 * negative cases matter as much as the positive ones.
 */
class NoteTagsTest {

    @Test
    fun parsesSimpleAndNestedTags() {
        val tags = NoteTags.parse("Plan for #work# and #Project/Alpha# today.")
        assertEquals(listOf("work", "project/alpha"), tags.map { it.key })
        assertEquals(listOf("work", "Project/Alpha"), tags.map { it.display })
    }

    @Test
    fun twoTagsOnOneLineAreBothFound() {
        val tags = NoteTags.parse("#one# #two#")
        assertEquals(listOf("one", "two"), tags.map { it.key })
    }

    @Test
    fun identityIsCaseInsensitiveAndFirstSpellingWins() {
        val tags = NoteTags.parse("#Work# then #work# then #WORK#")
        assertEquals(1, tags.size)
        assertEquals("work", tags[0].key)
        assertEquals("Work", tags[0].display)
    }

    @Test
    fun headingsAndBareHashesAreNotTags() {
        assertTrue(NoteTags.parse("# Heading one").isEmpty())
        assertTrue(NoteTags.parse("## Heading two").isEmpty())
        assertTrue(NoteTags.parse("a # b").isEmpty())
        assertTrue(NoteTags.parse("only a hash #").isEmpty())
        assertTrue(NoteTags.parse("## Heading with a #tag# in it").map { it.key } == listOf("tag"))
    }

    @Test
    fun unclosedTagIsIgnored() {
        assertTrue(NoteTags.parse("#todo buy milk").isEmpty())
    }

    @Test
    fun hashGluedToAWordIsProse() {
        // C#, F#, and "page#3" are not tags.
        assertTrue(NoteTags.parse("I write C# and F#").isEmpty())
        assertTrue(NoteTags.parse("see page#3 here").isEmpty())
    }

    @Test
    fun fencedCodeBlocksAreSkipped() {
        val content = """
            real #kept#

            ```kotlin
            val x = "#notatag#"
            ```

            ~~~
            #alsoNot#
            ~~~
        """.trimIndent()
        assertEquals(listOf("kept"), NoteTags.parse(content).map { it.key })
    }

    @Test
    fun rejectsEmptyAndOverlongTags() {
        assertTrue(NoteTags.parse("##").isEmpty())
        assertNull(NoteTags.normalise(""))
        assertNull(NoteTags.normalise("two words"))
        assertNull(NoteTags.normalise("x".repeat(NoteTags.MAX_LENGTH + 1)))
        assertNull(NoteTags.normalise("---"))
        assertEquals("ok", NoteTags.normalise("ok")?.key)
    }

    @Test
    fun tagSyntaxDoesNotLeakAcrossLines() {
        // An unclosed tag does not swallow the next line's tag.
        val tags = NoteTags.parse("#open line\n#closed#")
        assertEquals(listOf("closed"), tags.map { it.key })
    }

    @Test
    fun markdownRoundTripsThroughTheParser() {
        val display = "Project/Alpha"
        val tag = NoteTags.normalise(display)
        assertEquals("project/alpha", tag?.key)
        val parsed = NoteTags.parse("x ${NoteTags.markdown(display)} y")
        assertEquals(listOf("project/alpha"), parsed.map { it.key })
    }
}
