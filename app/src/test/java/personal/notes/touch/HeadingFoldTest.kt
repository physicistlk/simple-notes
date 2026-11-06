package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.ui.editor.BlockStructureOps
import personal.notes.touch.ui.editor.BlockType
import personal.notes.touch.ui.editor.EditableBlock

/**
 * Heading folding: a collapsed heading hides the blocks below it until the
 * next heading of equal or higher level; the heading itself always stays
 * visible so it can be unfolded.
 */
class HeadingFoldTest {

    private fun heading(id: String, level: Int, collapsed: Boolean = false) =
        EditableBlock(id = id, type = BlockType.HEADING, headingLevel = level, collapsed = collapsed)

    private fun paragraph(id: String) = EditableBlock(id = id, type = BlockType.PARAGRAPH)

    @Test
    fun collapsedHeadingHidesItsSectionAndStopsAtTheNextHeading() {
        val blocks = listOf(
            heading("h1", 1, collapsed = true),
            paragraph("p1"),
            paragraph("p2"),
            heading("h2", 1),
            paragraph("p3")
        )
        assertEquals(
            listOf("h1", "h2", "p3"),
            BlockStructureOps.visibleBlocks(blocks).map { it.id }
        )
    }

    @Test
    fun nestedHeadingStaysInsideAFoldedParent() {
        val blocks = listOf(
            heading("h1", 1, collapsed = true),
            heading("h2", 2),
            paragraph("p1"),
            heading("h3", 2),
            heading("h4", 1),
            paragraph("p2")
        )
        // H2 is hidden (inside H1), and H1 reopens at H4.
        assertEquals(
            listOf("h1", "h4", "p2"),
            BlockStructureOps.visibleBlocks(blocks).map { it.id }
        )
    }

    @Test
    fun expandedHeadingsShowEverything() {
        val blocks = listOf(
            heading("h1", 1),
            paragraph("p1"),
            heading("h2", 2),
            paragraph("p2")
        )
        assertEquals(listOf("h1", "p1", "h2", "p2"),
            BlockStructureOps.visibleBlocks(blocks).map { it.id })
    }

    @Test
    fun foldedSectionAtTheEndHidesOnlyWhatFollows() {
        val blocks = listOf(
            paragraph("p0"),
            heading("h1", 2, collapsed = true),
            paragraph("p1")
        )
        assertEquals(listOf("p0", "h1"),
            BlockStructureOps.visibleBlocks(blocks).map { it.id })
    }
}
