package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.entity.NoteBlockEntity
import personal.notes.touch.text.BlockTransfer

class BlockTransferTest {

    private fun row(id: String, index: Int, indent: Int = 0, text: String = "text $id"): NoteBlockEntity =
        NoteBlockEntity(id = id, noteId = "src", blockIndex = index, blockType = "PARAGRAPH", text = text, indent = indent)

    @Test
    fun emptySelectionReturnsEmpty() {
        val rows = listOf(row("a", 0), row("b", 1))
        assertEquals(emptyList<NoteBlockEntity>(), BlockTransfer.selection(rows, emptySet()))
    }

    @Test
    fun singleParagraphSelection() {
        val rows = listOf(row("a", 0), row("b", 1), row("c", 2))
        val sel = BlockTransfer.selection(rows, setOf("b"))
        assertEquals(listOf("b"), sel.map { it.id })
    }

    @Test
    fun listWithChildrenDragsDescendants() {
        val rows = listOf(
            row("p", 0, 0),
            row("c1", 1, 1),
            row("c2", 2, 1),
            row("sib", 3, 0)
        )
        val sel = BlockTransfer.selection(rows, setOf("p"))
        assertEquals(listOf("p", "c1", "c2"), sel.map { it.id })
    }

    @Test
    fun nestedChildrenDragsAllDescendants() {
        val rows = listOf(
            row("p", 0, 0),
            row("c1", 1, 1),
            row("g1", 2, 2),
            row("c2", 3, 1),
            row("sib", 4, 0)
        )
        val sel = BlockTransfer.selection(rows, setOf("p"))
        assertEquals(listOf("p", "c1", "g1", "c2"), sel.map { it.id })
    }

    @Test
    fun includeChildrenFalse() {
        val rows = listOf(
            row("p", 0, 0),
            row("c1", 1, 1)
        )
        val sel = BlockTransfer.selection(rows, setOf("p"), includeChildren = false)
        assertEquals(listOf("p"), sel.map { it.id })
    }

    @Test
    fun removeReindexes() {
        val rows = listOf(row("a", 0), row("b", 1), row("c", 2))
        val out = BlockTransfer.remove(rows, setOf("b"))
        assertEquals(listOf("a", "c"), out.map { it.id })
        assertEquals(listOf(0, 1), out.map { it.blockIndex })
    }

    @Test
    fun appendMoveKeepsIds() {
        val target = listOf(row("x", 0, 0).copy(noteId = "target"))
        val incoming = listOf(row("a", 0, 0), row("b", 1, 0))
        val out = BlockTransfer.append(target, incoming, "target", copyIds = false)
        assertEquals(listOf("x", "a", "b"), out.map { it.id })
        assertEquals(listOf(0, 1, 2), out.map { it.blockIndex })
        assertTrue(out.all { it.noteId == "target" })
    }

    @Test
    fun appendCopyMintsNewIds() {
        val target = listOf(row("x", 0, 0).copy(noteId = "target"))
        val incoming = listOf(row("a", 0, 0))
        val out = BlockTransfer.append(target, incoming, "target", copyIds = true, newId = { "new-0" })
        assertEquals(listOf("x", "new-0"), out.map { it.id })
    }

    @Test
    fun appendEmptyReturnsReindexedTarget() {
        val target = listOf(row("x", 2, 0).copy(noteId = "target"))
        val out = BlockTransfer.append(target, emptyList(), "target", copyIds = false)
        assertEquals(listOf("x"), out.map { it.id })
        assertEquals(listOf(0), out.map { it.blockIndex })
    }

    @Test
    fun reindexSortsAndDensifies() {
        val rows = listOf(row("a", 2), row("b", 5))
        val out = BlockTransfer.reindex(rows)
        assertEquals(listOf(0, 1), out.map { it.blockIndex })
    }
}
