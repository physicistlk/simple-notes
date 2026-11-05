package personal.notes.touch

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.entity.NoteBlockEntity
import personal.notes.touch.ui.editor.BlockModel
import personal.notes.touch.ui.editor.BlockPersistence
import personal.notes.touch.ui.editor.BlockType
import personal.notes.touch.ui.editor.EditableBlock

/**
 * note_blocks foundation (schema 6): row mapping and stable-id reconciliation.
 * Plain JVM tests — no Room/Robolectric needed, the mapping is pure.
 */
class NoteBlockPersistenceTest {

    private val now = 1_700_000_000_000L

    private fun block(
        id: String,
        type: BlockType,
        text: String = "",
        headingLevel: Int = 1,
        indent: Int = 0,
        order: Int = 1,
        checked: Boolean = false,
        closed: Boolean = true
    ) = EditableBlock(
        id = id,
        type = type,
        field = TextFieldValue(text),
        headingLevel = headingLevel,
        indent = indent,
        order = order,
        checked = checked,
        closed = closed
    )

    @Test
    fun rowsRoundTripEveryField() {
        val blocks = listOf(
            block("a", BlockType.HEADING, "Title", headingLevel = 2),
            block("b", BlockType.BULLET, "item", indent = 1),
            block("c", BlockType.NUMBERED, "third", order = 3),
            block("d", BlockType.TASK, "done", checked = true),
            block("e", BlockType.CODE, "val x = 1", closed = false),
            block("f", BlockType.PARAGRAPH, "plain")
        )

        val rows = BlockPersistence.toEntities("note-1", blocks, now)
        val restored = BlockPersistence.fromEntities(rows)

        assertEquals(blocks, restored)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), rows.map { it.blockIndex })
        assertTrue(rows.all { it.noteId == "note-1" && it.updatedAt == now })
    }

    @Test
    fun blockIndexIsRewrittenOnEverySave() {
        val blocks = listOf(
            block("a", BlockType.PARAGRAPH, "one"),
            block("b", BlockType.PARAGRAPH, "two")
        )
        // Stale indices (e.g. rows written before a reorder) are ignored on read
        // and regenerated from list order on write.
        val restored = BlockPersistence.fromEntities(
            listOf(
                NoteBlockEntity("b", "n", 7, "PARAGRAPH", "two"),
                NoteBlockEntity("a", "n", 2, "PARAGRAPH", "one")
            )
        )
        assertEquals(listOf("a", "b"), restored.map { it.id })

        val rows = BlockPersistence.toEntities("n", blocks, now)
        assertEquals(listOf(0, 1), rows.map { it.blockIndex })
    }

    @Test
    fun unknownBlockTypeDegradesToParagraphWithoutLosingText() {
        val restored = BlockPersistence.fromEntity(
            NoteBlockEntity(
                id = "x",
                noteId = "n",
                blockIndex = 0,
                blockType = "FUTURE_CALLOUT",
                text = "keep me"
            )
        )
        assertEquals(BlockType.PARAGRAPH, restored.type)
        assertEquals("keep me", restored.field.text)
    }

    @Test
    fun reconcileKeepsIdsWhenMarkdownIsUnchanged() {
        val stored = listOf(
            block("uuid-1", BlockType.HEADING, "Title", headingLevel = 1),
            block("uuid-2", BlockType.PARAGRAPH, "body"),
            block("uuid-3", BlockType.BULLET, "item")
        )
        val parsed = BlockModel.parseBlocks(BlockModel.serializeBlocks(stored))

        val reconciled = BlockPersistence.reconcileIds(parsed, stored) { "fresh" }

        assertEquals(listOf("uuid-1", "uuid-2", "uuid-3"), reconciled.map { it.id })
    }

    @Test
    fun reconcileReusesIdForInPlaceTextEdit() {
        val previous = listOf(
            block("uuid-1", BlockType.PARAGRAPH, "before"),
            block("uuid-2", BlockType.PARAGRAPH, "tail")
        )
        val parsed = listOf(
            block("blk0", BlockType.PARAGRAPH, "after"),
            block("blk1", BlockType.PARAGRAPH, "tail")
        )

        val reconciled = BlockPersistence.reconcileIds(parsed, previous)

        assertEquals(listOf("uuid-1", "uuid-2"), reconciled.map { it.id })
        assertEquals("after", reconciled[0].field.text)
    }

    @Test
    fun reconcileMintsIdsOnlyForInsertedBlocks() {
        val previous = listOf(
            block("uuid-1", BlockType.PARAGRAPH, "one"),
            block("uuid-2", BlockType.PARAGRAPH, "three")
        )
        val parsed = listOf(
            block("blk0", BlockType.PARAGRAPH, "one"),
            block("blk1", BlockType.PARAGRAPH, "two"),
            block("blk2", BlockType.PARAGRAPH, "three")
        )
        var counter = 0

        val reconciled = BlockPersistence.reconcileIds(parsed, previous) { "new-${counter++}" }

        assertEquals("uuid-1", reconciled[0].id)
        assertEquals("new-0", reconciled[1].id)
        assertEquals("uuid-2", reconciled[2].id)
        assertEquals(1, counter)
    }

    @Test
    fun reconcileKeepsDuplicateIdenticalBlocksDistinctAndOrdered() {
        val previous = listOf(
            block("uuid-1", BlockType.PARAGRAPH, "same"),
            block("uuid-2", BlockType.PARAGRAPH, "same")
        )
        val parsed = listOf(
            block("blk0", BlockType.PARAGRAPH, "same"),
            block("blk1", BlockType.PARAGRAPH, "same"),
            block("blk2", BlockType.PARAGRAPH, "same")
        )

        val reconciled = BlockPersistence.reconcileIds(parsed, previous) { "new" }

        assertEquals(listOf("uuid-1", "uuid-2", "new"), reconciled.map { it.id })
    }

    @Test
    fun reconcileEmptyPreviousMintsOneIdPerBlock() {
        val parsed = BlockModel.parseBlocks("# Title\n\nbody")
        var counter = 0

        val reconciled = BlockPersistence.reconcileIds(parsed, emptyList()) { "new-${counter++}" }

        assertEquals(parsed.size, reconciled.size)
        assertEquals(parsed.size, counter)
        assertEquals(reconciled.size, reconciled.map { it.id }.toSet().size)
        reconciled.forEach { assertNotEquals("blk", it.id.take(3)) }
    }

    @Test
    fun reconcileNeverReusesAnIdTwice() {
        val previous = listOf(block("uuid-1", BlockType.PARAGRAPH, "a"))
        val parsed = listOf(
            block("blk0", BlockType.PARAGRAPH, "a"),
            block("blk1", BlockType.PARAGRAPH, "a")
        )

        val reconciled = BlockPersistence.reconcileIds(parsed, previous) { "new" }

        assertEquals(2, reconciled.map { it.id }.toSet().size)
        assertTrue(reconciled.all { it.id == "uuid-1" || it.id == "new" })
    }

    @Test
    fun reconcileLeavesEmptyParsedListAlone() {
        assertTrue(BlockPersistence.reconcileIds(emptyList(), listOf(block("u", BlockType.PARAGRAPH, "x"))).isEmpty())
    }
}
