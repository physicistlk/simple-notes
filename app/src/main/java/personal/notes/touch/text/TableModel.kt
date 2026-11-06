package personal.notes.touch.text

/**
 * Markdown table model behind the table-cell editor (schema-independent).
 *
 * The block's text stays the source of truth: `| a | b |` lines with a GFM
 * separator row after the header. The editor renders one text field per cell
 * and writes the whole table back through [serialize], so a cell edit is just
 * a normal block-text edit (undo, autosave and export keep working).
 *
 * Deliberate limits: a `|` inside a cell is treated as a column separator
 * (markdown's own escaping, `\|`, is not interpreted); the first row is always
 * the header.
 */
object TableModel {

    data class Table(val rows: List<List<String>>)

    private val SEPARATOR = Regex("""^:?-{1,}:?$""")

    /** Parses [markdown]; null when there is no usable row at all. */
    fun parse(markdown: String): Table? {
        val rows = ArrayList<List<String>>()
        for (raw in markdown.split('\n')) {
            val line = raw.trim()
            if (!line.startsWith("|")) continue
            val cells = line.removePrefix("|").removeSuffix("|")
                .split('|')
                .map { it.trim() }
            if (cells.all { SEPARATOR.matches(it) }) continue
            if (cells.isEmpty()) continue
            rows.add(cells)
        }
        if (rows.isEmpty()) return null
        val width = rows.maxOf { it.size }
        return Table(rows.map { row -> List(width) { row.getOrElse(it) { "" } } })
    }

    /** GFM markdown for [rows] (header first, separator second). */
    fun serialize(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val width = rows.maxOf { it.size }
        fun line(cells: List<String>): String =
            "| " + (0 until width).joinToString(" | ") { cells.getOrElse(it) { "" } } + " |"
        val out = ArrayList<String>(rows.size + 1)
        out.add(line(rows.first()))
        out.add("| " + (0 until width).joinToString(" | ") { "---" } + " |")
        for (row in rows.drop(1)) out.add(line(row))
        return out.joinToString("\n")
    }

    fun setCell(rows: List<List<String>>, row: Int, column: Int, value: String): List<List<String>> {
        if (row !in rows.indices) return rows
        val width = rows.maxOf { it.size }
        if (column !in 0 until width) return rows
        return rows.mapIndexed { r, cells ->
            if (r != row) cells
            else List(width) { c -> if (c == column) value else cells.getOrElse(c) { "" } }
        }
    }

    /** Appends an empty body row (or a header-only row when the table is empty). */
    fun addRow(rows: List<List<String>>): List<List<String>> {
        val width = rows.maxOfOrNull { it.size } ?: 1
        return rows + listOf(List(width) { "" })
    }

    fun removeRow(rows: List<List<String>>, row: Int): List<List<String>> {
        if (rows.size <= 1 || row !in rows.indices) return rows
        return rows.filterIndexed { index, _ -> index != row }
    }

    fun addColumn(rows: List<List<String>>): List<List<String>> =
        rows.map { it + "" }

    fun removeColumn(rows: List<List<String>>, column: Int): List<List<String>> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        if (width <= 1 || column !in 0 until width) return rows
        return rows.map { cells -> cells.filterIndexed { index, _ -> index != column } }
    }
}
