package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import personal.notes.touch.text.TableModel

/**
 * Table cell editor math: parse/serialize round trips, header handling, cell
 * updates and row/column add/remove.
 */
class TableModelTest {

    @Test
    fun parsesHeaderAndBodyRows() {
        val table = requireNotNull(
            TableModel.parse(
                """
                | Name | Qty |
                | --- | --- |
                | Milk | 2 |
                """.trimIndent()
            )
        )
        assertEquals(listOf("Name", "Qty"), table.rows[0])
        assertEquals(listOf("Milk", "2"), table.rows[1])
        assertEquals(2, table.rows.size)
    }

    @Test
    fun serializesWithSeparatorAfterHeader() {
        val markdown = TableModel.serialize(listOf(listOf("A", "B"), listOf("1", "2")))
        assertEquals("| A | B |\n| --- | --- |\n| 1 | 2 |", markdown)
    }

    @Test
    fun roundTripIsStable() {
        val source = "| A | B |\n| --- | --- |\n| 1 | 2 |"
        val parsed = requireNotNull(TableModel.parse(source))
        assertEquals(source, TableModel.serialize(parsed.rows))
    }

    @Test
    fun raggedRowsArePaddedToTheWidestRow() {
        val table = requireNotNull(TableModel.parse("| A | B |\n| --- | --- |\n| only |"))
        assertEquals(listOf("only", ""), table.rows[1])
    }

    @Test
    fun emptyInputIsNull() {
        assertNull(TableModel.parse(""))
        assertNull(TableModel.parse("no table here"))
    }

    @Test
    fun setCellKeepsTheGridShape() {
        val rows = listOf(listOf("A", "B"), listOf("1", "2"))
        val updated = TableModel.setCell(rows, 1, 0, "9")
        assertEquals(listOf(listOf("A", "B"), listOf("9", "2")), updated)
        // Out-of-range edits are ignored.
        assertEquals(rows, TableModel.setCell(rows, 5, 5, "x"))
    }

    @Test
    fun addAndRemoveRow() {
        val rows = listOf(listOf("A", "B"), listOf("1", "2"))
        val added = TableModel.addRow(rows)
        assertEquals(3, added.size)
        assertEquals(listOf("", ""), added.last())
        assertEquals(2, TableModel.removeRow(added, added.lastIndex).size)
        // Removing the header promotes the next row to be the header.
        assertEquals(1, TableModel.removeRow(rows, 0).size)
        // The last remaining row is never removed.
        assertEquals(1, TableModel.removeRow(listOf(listOf("A")), 0).size)
    }

    @Test
    fun addAndRemoveColumn() {
        val rows = listOf(listOf("A", "B"), listOf("1", "2"))
        val added = TableModel.addColumn(rows)
        assertEquals(listOf("A", "B", ""), added[0])
        assertEquals(listOf("A", "B"), TableModel.removeColumn(added, 2)[0])
        // The last column cannot be removed.
        assertEquals(listOf(listOf("A")), TableModel.removeColumn(listOf(listOf("A")), 0))
    }

    @Test
    fun singleColumnTableSerializes() {
        assertEquals(
            "| A |\n| --- |\n| 1 |",
            TableModel.serialize(listOf(listOf("A"), listOf("1")))
        )
    }
}
