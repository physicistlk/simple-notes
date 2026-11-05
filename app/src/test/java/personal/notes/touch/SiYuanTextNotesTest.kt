package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.editor.BlockCommand
import personal.notes.touch.ui.editor.BlockModel
import personal.notes.touch.ui.editor.BlockOps
import personal.notes.touch.ui.editor.BlockType
import personal.notes.touch.ui.editor.EditableBlock
import personal.notes.touch.ui.editor.RichBlock
import personal.notes.touch.ui.editor.RichTextParser
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

class SiYuanTextNotesTest {

    @Test
    fun testRichTextParserHeadingsH1ToH6() {
        val md = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            #### Heading 4
            ##### Heading 5
            ###### Heading 6
        """.trimIndent()

        val blocks = RichTextParser.parseMarkdown(md)
        assertEquals(6, blocks.size)
        for (i in 1..6) {
            val h = blocks[i - 1] as RichBlock.Header
            assertEquals(i, h.level)
            assertEquals("Heading $i", h.text.text)
        }
    }

    @Test
    fun testRichTextParserTableAndImage() {
        val md = """
            | Name | Age |
            | --- | --- |
            | Alice | 25 |
            
            ![Graph](note_assets:img1.png)
        """.trimIndent()

        val blocks = RichTextParser.parseMarkdown(md)
        val table = blocks.filterIsInstance<RichBlock.Table>().firstOrNull()
        assertNotNull(table)
        assertEquals(3, table?.rows?.size)

        val image = blocks.filterIsInstance<RichBlock.Image>().firstOrNull()
        assertNotNull(image)
        assertEquals("Graph", image?.alt)
        assertEquals("note_assets:img1.png", image?.url)
    }

    @Test
    fun testRichTextParserInlineHighlightAndTags() {
        val inlineText = "This is ==highlighted== and this is #work/project# and #ideas#"
        val parsed = RichTextParser.parseInline(inlineText)
        assertEquals("This is highlighted and this is #work/project# and #ideas#", parsed.text)
        // Check span styles
        assertTrue(parsed.spanStyles.isNotEmpty())
    }

    @Test
    fun testRichTextParserChecklistsAndLists() {
        val md = """
            - [ ] Task 1
            - [x] Task 2
            - Bullet item
            1. Numbered item
            > Blockquote
            ---
        """.trimIndent()

        val blocks = RichTextParser.parseMarkdown(md)
        assertEquals(6, blocks.size)
        assertTrue(blocks[0] is RichBlock.ChecklistItem && !(blocks[0] as RichBlock.ChecklistItem).isChecked)
        assertTrue(blocks[1] is RichBlock.ChecklistItem && (blocks[1] as RichBlock.ChecklistItem).isChecked)
        assertTrue(blocks[2] is RichBlock.BulletItem)
        assertTrue(blocks[3] is RichBlock.NumberedItem)
        assertTrue(blocks[4] is RichBlock.Quote)
        assertTrue(blocks[5] is RichBlock.Divider)
    }

    @Test
    fun testBlockModelParsingAndRoundTrip() {
        val originalMarkdown = """
            # Project Plan
            This is a paragraph with **bold** text.
            - Bullet 1
            - Bullet 2
            1. First step
            - [ ] Complete documentation
            - [x] Write tests
            > A quote from SiYuan
            ---
            | Col 1 | Col 2 |
            | --- | --- |
            | Val 1 | Val 2 |
            ```
            println("Hello")
            ```
            ![diagram](assets/diag.png)
        """.trimIndent()

        val blocks = BlockModel.parseBlocks(originalMarkdown)
        assertTrue(blocks.isNotEmpty())

        val serialized = BlockModel.serializeBlocks(blocks)
        val reparsed = BlockModel.parseBlocks(serialized)

        assertEquals(blocks.size, reparsed.size)
        for (i in blocks.indices) {
            assertEquals(blocks[i].type, reparsed[i].type)
            assertEquals(blocks[i].field.text, reparsed[i].field.text)
        }
    }

    @Test
    fun testBlockOpsEnterSplits() {
        // Splitting a bullet continues the bullet
        val initialBlocks = listOf(
            EditableBlock(id = "b1", type = BlockType.BULLET, field = TextFieldValue("item one"))
        )
        val split = BlockOps.enterSplit(
            blocks = initialBlocks,
            blockId = "b1",
            preText = "item one",
            postText = "",
            atEnd = true
        )
        assertEquals(2, split.size)
        assertEquals(BlockType.BULLET, split[0].type)
        assertEquals(BlockType.BULLET, split[1].type)

        // Empty bullet item demotes to paragraph
        val demoted = BlockOps.enterSplit(
            blocks = split,
            blockId = split[1].id,
            preText = "",
            postText = "",
            atEnd = true
        )
        assertEquals(2, demoted.size)
        assertEquals(BlockType.PARAGRAPH, demoted[1].type)
    }

    @Test
    fun testBlockOpsBackspaceAtStart() {
        val blocks = listOf(
            EditableBlock(id = "b1", type = BlockType.PARAGRAPH, field = TextFieldValue("Hello ")),
            EditableBlock(id = "b2", type = BlockType.PARAGRAPH, field = TextFieldValue("World"))
        )
        val result = BlockOps.backspaceAtStart(blocks, "b2")
        assertEquals(1, result.blocks.size)
        assertEquals("Hello World", result.blocks[0].field.text)
        assertEquals("b1", result.focusId)
    }

    @Test
    fun testBlockCommandSlashGroups() {
        val groups = BlockCommand.slashGroups()
        assertEquals(3, groups.size)
        val headingGroup = groups.first { it.first == BlockCommand.GROUP_HEADINGS }.second
        assertTrue(headingGroup.contains(BlockCommand.H1))
        assertTrue(headingGroup.contains(BlockCommand.H6))

        val richGroup = groups.first { it.first == BlockCommand.GROUP_RICH }.second
        assertTrue(richGroup.contains(BlockCommand.TABLE))
        assertTrue(richGroup.contains(BlockCommand.IMAGE))
        assertTrue(richGroup.contains(BlockCommand.TAG))
        assertTrue(richGroup.contains(BlockCommand.HIGHLIGHT))
        assertTrue(richGroup.contains(BlockCommand.EMOJI))
    }
}
