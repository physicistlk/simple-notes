package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.notes.extractPreviewLines

class NotePreviewTest {

    @Test
    fun testExtractPreviewLinesFromMarkdown() {
        val markdown = """
            # Project Overview
            This is the main introduction to the project.
            
            ## Key Deliverables
            * Feature 1: Canvas Drawing
            * Feature 2: SiYuan Block Text
            * Feature 3: Inside Note Previews
            
            > Important note on offline privacy
            
            ==Highlighted content==
            **Bold text** and *italic*
        """.trimIndent()

        val lines = extractPreviewLines(markdown, maxLines = 6)
        assertTrue(lines.isNotEmpty())
        assertEquals("Project Overview", lines[0])
        assertEquals("This is the main introduction to the project.", lines[1])
        assertEquals("Key Deliverables", lines[2])
        assertEquals("• Feature 1: Canvas Drawing", lines[3])
        assertEquals("• Feature 2: SiYuan Block Text", lines[4])
        assertEquals("• Feature 3: Inside Note Previews", lines[5])
    }

    @Test
    fun testExtractPreviewLinesFromTableAndChecklist() {
        val content = """
            - [ ] Task 1: Fix pressure pen
            - [x] Task 2: Block editor
            | Name | Value |
            | --- | --- |
            | Speed | 120Hz |
        """.trimIndent()

        val lines = extractPreviewLines(content, maxLines = 5)
        assertEquals(4, lines.size)
        assertEquals("☐ Task 1: Fix pressure pen", lines[0])
        assertEquals("☐ Task 2: Block editor", lines[1])
        assertTrue(lines[2].contains("Name") && lines[2].contains("Value"))
        assertTrue(lines[3].contains("Speed") && lines[3].contains("120Hz"))
    }

    @Test
    fun testExtractPreviewLinesEmptyContent() {
        val lines = extractPreviewLines("", maxLines = 5)
        assertTrue(lines.isEmpty())
        val whitespaceLines = extractPreviewLines("   \n\n  ", maxLines = 5)
        assertTrue(whitespaceLines.isEmpty())
    }
}
