package personal.notes.touch

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.editor.BlockModel
import personal.notes.touch.ui.editor.BlockStructureOps
import personal.notes.touch.ui.editor.BlockType
import personal.notes.touch.ui.editor.EditableBlock
import personal.notes.touch.ui.editor.ImageBlockRefParser
import personal.notes.touch.ui.editor.LinkMarkdown
import personal.notes.touch.ui.editor.LinkTarget
import personal.notes.touch.ui.editor.RichTextParser

/**
 * Cheap editor gaps: indent/outdent, block menu operations, link targets and
 * image block references. Pure JVM — no Android/Robolectric.
 */
class BlockEditorActionsTest {

    private fun block(
        id: String,
        type: BlockType,
        text: String = "",
        headingLevel: Int = 1,
        indent: Int = 0,
        order: Int = 1,
        checked: Boolean = false
    ) = EditableBlock(
        id = id,
        type = type,
        field = TextFieldValue(text),
        headingLevel = headingLevel,
        indent = indent,
        order = order,
        checked = checked
    )

    private fun ids(vararg values: String): () -> String {
        var index = 0
        return { values.getOrElse(index++) { "extra-$index" } }
    }

    // --- indent / outdent -------------------------------------------------

    @Test
    fun indentOnlyAppliesToListsAndClamps() {
        val list = listOf(
            block("a", BlockType.BULLET, "one"),
            block("b", BlockType.PARAGRAPH, "text")
        )
        val indented = BlockStructureOps.shiftIndent(list, "a", 1)
        assertEquals(1, indented[0].indent)

        // Paragraphs have no nesting in the markdown grammar.
        assertSame(list, BlockStructureOps.shiftIndent(list, "b", 1))

        var deep = listOf(block("c", BlockType.TASK, "x", indent = BlockStructureOps.MAX_INDENT))
        assertSame(deep, BlockStructureOps.shiftIndent(deep, "c", 1))
        deep = BlockStructureOps.shiftIndent(deep, "c", -1)
        assertEquals(BlockStructureOps.MAX_INDENT - 1, deep[0].indent)
    }

    @Test
    fun outdentAtZeroIsNoOp() {
        val list = listOf(block("a", BlockType.NUMBERED, "one", indent = 0))
        assertSame(list, BlockStructureOps.shiftIndent(list, "a", -1))
    }

    @Test
    fun indentRoundTripsThroughMarkdown() {
        val list = listOf(block("a", BlockType.BULLET, "nested", indent = 2))
        val md = BlockModel.serializeBlocks(list)
        assertEquals("    - nested", md)
        val parsed = BlockModel.parseBlocks(md)
        assertEquals(2, parsed[0].indent)
    }

    // --- block menu -------------------------------------------------------

    @Test
    fun duplicateInsertsCopyWithNewIdAndCaretAtEnd() {
        val list = listOf(
            block("a", BlockType.PARAGRAPH, "first"),
            block("b", BlockType.PARAGRAPH, "second")
        )
        val result = BlockStructureOps.duplicate(list, "a", ids("copy"))
        assertEquals(3, result.size)
        assertEquals("copy", result[1].id)
        assertEquals("first", result[1].field.text)
        assertEquals(TextRange(5), result[1].field.selection)
        assertEquals("b", result[2].id)
    }

    @Test
    fun deleteKeepsAnEditableBlock() {
        val list = listOf(block("a", BlockType.PARAGRAPH, "only"))
        val result = BlockStructureOps.delete(list, "a", ids("fresh"))
        assertEquals(1, result.size)
        assertEquals("fresh", result[0].id)
        assertEquals(BlockType.PARAGRAPH, result[0].type)

        val two = listOf(block("a", BlockType.PARAGRAPH, "a"), block("b", BlockType.PARAGRAPH, "b"))
        val after = BlockStructureOps.delete(two, "a", ids("unused"))
        assertEquals(listOf("b"), after.map { it.id })
    }

    @Test
    fun moveRespectsBounds() {
        val list = listOf(
            block("a", BlockType.PARAGRAPH, "a"),
            block("b", BlockType.PARAGRAPH, "b"),
            block("c", BlockType.PARAGRAPH, "c")
        )
        assertEquals(listOf("b", "a", "c"), BlockStructureOps.move(list, "a", 1).map { it.id })
        assertEquals(listOf("a", "c", "b"), BlockStructureOps.move(list, "b", 1).map { it.id })
        assertSame(list, BlockStructureOps.move(list, "a", -1))
        assertSame(list, BlockStructureOps.move(list, "c", 1))
    }

    @Test
    fun transformKeepsIdentityAndText() {
        val list = listOf(block("a", BlockType.PARAGRAPH, "Title"))
        val heading = BlockStructureOps.transform(list, "a", BlockType.HEADING, 2)
        assertEquals("a", heading[0].id)
        assertEquals(BlockType.HEADING, heading[0].type)
        assertEquals(2, heading[0].headingLevel)
        assertEquals("Title", heading[0].field.text)

        // Same type/level is a no-op (returns the same list instance).
        assertSame(heading, BlockStructureOps.transform(heading, "a", BlockType.HEADING, 2))
    }

    @Test
    fun transformResetsIndentUnlessStayingAList() {
        val list = listOf(block("a", BlockType.BULLET, "item", indent = 3))
        val heading = BlockStructureOps.transform(list, "a", BlockType.HEADING, 1)
        assertEquals(0, heading[0].indent)

        val numbered = BlockStructureOps.transform(list, "a", BlockType.NUMBERED, 1)
        assertEquals(3, numbered[0].indent)
        assertEquals(1, numbered[0].order)
    }

    @Test
    fun transformToTableAndImageSeedsStarterContent() {
        val list = listOf(block("a", BlockType.PARAGRAPH, ""))
        val table = BlockStructureOps.transform(list, "a", BlockType.TABLE)
        assertTrue(table[0].field.text.startsWith("| Header 1 |"))
        val image = BlockStructureOps.transform(list, "a", BlockType.IMAGE)
        assertEquals("![image](https://)", image[0].field.text)
    }

    @Test
    fun imageToTextKeepsAltAndDropsSyntax() {
        val list = listOf(block("a", BlockType.IMAGE, "![sunset over the bay](img_7.jpg)"))
        val text = BlockStructureOps.transform(list, "a", BlockType.PARAGRAPH)
        assertEquals("sunset over the bay", text[0].field.text)
        // Round trips as a plain paragraph, not back into an image.
        assertEquals(BlockType.PARAGRAPH, BlockModel.parseBlocks(BlockModel.serializeBlocks(text))[0].type)

        val bullet = BlockStructureOps.transform(list, "a", BlockType.BULLET)
        assertEquals("sunset over the bay", bullet[0].field.text)
        assertNull(ImageBlockRefParser.parse(bullet[0].field.text))
    }

    @Test
    fun tableToTextFlattensCellsWithoutPipes() {
        val table = block(
            "a",
            BlockType.TABLE,
            "| Name | Qty |\n| --- | --- |\n| Apples | 3 |"
        )
        val text = BlockStructureOps.transform(listOf(table), "a", BlockType.PARAGRAPH)
        assertEquals("Name · Qty\nApples · 3", text[0].field.text)
        assertTrue(!text[0].field.text.contains("|"))
        assertEquals(BlockType.PARAGRAPH, BlockModel.parseBlocks(BlockModel.serializeBlocks(text))[0].type)
    }

    @Test
    fun textToImageProducesValidReference() {
        val list = listOf(block("a", BlockType.PARAGRAPH, "A photo of [the] garden"))
        val image = BlockStructureOps.transform(list, "a", BlockType.IMAGE)
        val ref = ImageBlockRefParser.parse(image[0].field.text)
        assertNotNull(ref)
        assertEquals("A photo of the garden", ref?.alt)
        assertEquals("https://", ref?.target)
    }

    @Test
    fun textToTableUsesDefaultRowsAndDividerToTextIsEmpty() {
        val list = listOf(block("a", BlockType.PARAGRAPH, "prose"))
        val table = BlockStructureOps.transform(list, "a", BlockType.TABLE)
        assertEquals(BlockModel.DEFAULT_TABLE_ROWS.joinToString("\n"), table[0].field.text)

        val divider = block("b", BlockType.DIVIDER, "")
        val text = BlockStructureOps.transform(listOf(divider), "b", BlockType.PARAGRAPH)
        assertEquals("", text[0].field.text)
    }

    @Test
    fun listToImageKeepsAltOnly() {
        // Lists drop nesting when they stop being lists.
        val list = listOf(block("a", BlockType.BULLET, "item text", indent = 2))
        val image = BlockStructureOps.transform(list, "a", BlockType.IMAGE)
        assertEquals(0, image[0].indent)
        assertEquals("![item text](https://)", image[0].field.text)
    }

    // --- links ------------------------------------------------------------

    @Test
    fun linkNormalizationAddsSchemeAndRejectsUnsafeTargets() {
        assertEquals("https://example.com", LinkTarget.normalize("example.com"))
        assertEquals("http://example.com/a?b=1", LinkTarget.normalize("http://example.com/a?b=1"))
        assertEquals("mailto:user@example.com", LinkTarget.normalize("user@example.com"))
        assertEquals("mailto:user@example.com", LinkTarget.normalize("mailto:user@example.com"))
        assertNull(LinkTarget.normalize(""))
        assertNull(LinkTarget.normalize("   "))
        assertNull(LinkTarget.normalize("javascript:alert(1)"))
        assertNull(LinkTarget.normalize("file:///etc/passwd"))
        assertNull(LinkTarget.normalize("content://x/y"))
        assertNull(LinkTarget.normalize("https://"))
    }

    @Test
    fun linkMarkdownUsesLabelOrUrlFallback() {
        assertEquals("[Docs](https://example.com)", LinkMarkdown.build("Docs", "example.com"))
        assertEquals(
            "[https://a.b](https://a.b)",
            LinkMarkdown.build("  ", "https://a.b")
        )
        assertNull(LinkMarkdown.build("x", "javascript:alert(1)"))
    }

    @Test
    fun parserStylesAndTagsInlineLinks() {
        val parsed = RichTextParser.parseInline("see [docs](https://example.com) now")
        assertEquals("see docs now", parsed.text)
        val annotations = parsed.getStringAnnotations(RichTextParser.LINK_TAG, 0, parsed.length)
        assertEquals(1, annotations.size)
        assertEquals("https://example.com", annotations[0].item)
    }

    @Test
    fun parserLeavesImageSyntaxAloneAtInlineLevel() {
        val parsed = RichTextParser.parseInline("![alt](img_1.jpg)")
        assertEquals("![alt](img_1.jpg)", parsed.text)
        assertTrue(
            parsed.getStringAnnotations(RichTextParser.LINK_TAG, 0, parsed.length).isEmpty()
        )
    }

    // --- images -----------------------------------------------------------

    @Test
    fun imageReferenceParserAcceptsOnlyCompleteSyntax() {
        val ref = ImageBlockRefParser.parse("![cat](img_1.jpg)")
        assertNotNull(ref)
        assertEquals("cat", ref?.alt)
        assertEquals("img_1.jpg", ref?.target)

        assertNull(ImageBlockRefParser.parse("![alt]("))
        assertNull(ImageBlockRefParser.parse("just text"))
        assertNull(ImageBlockRefParser.parse("![a](b) trailing"))
    }
}
