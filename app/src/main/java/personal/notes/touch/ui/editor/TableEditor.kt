package personal.notes.touch.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.text.TableModel

/**
 * Cell-level table editor for a TABLE block.
 *
 * The block's markdown is the source of truth: every cell edit rewrites the
 * whole table through [TextEditorViewModel.updateTableBlock], so undo,
 * autosave and export keep working exactly as they do for any other block.
 * Row/column add/remove are plain transforms on the parsed grid.
 *
 * Deliberate limit: the first row is the header, and a `|` typed inside a cell
 * splits the cell on the next parse (markdown escaping is not interpreted).
 */
@Composable
fun TableBlockEditor(
    block: EditableBlock,
    viewModel: TextEditorViewModel,
    themeColor: Color,
    readOnly: Boolean
) {
    val table = remember(block.field.text) { TableModel.parse(block.field.text) }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    if (table == null) {
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            Text(
                text = "Empty table",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!readOnly) {
                TextButton(onClick = {
                    viewModel.updateTableBlock(
                        block.id,
                        listOf(
                            listOf("Header 1", "Header 2"),
                            listOf("", "")
                        )
                    )
                }) {
                    Text("Create 2x2 table", fontSize = 12.sp)
                }
            }
        }
        return
    }

    val rows = table.rows
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, gridColor, RoundedCornerShape(6.dp))
        ) {
            rows.forEachIndexed { r, cells ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    cells.forEachIndexed { c, cell ->
                        // Per-cell caret state, keyed so recomposition from the
                        // block write does not jump the cursor.
                        var value by remember(block.id, r, c) {
                            mutableStateOf(TextFieldValue(cell))
                        }
                        LaunchedEffect(cell) {
                            if (value.text != cell) value = value.copy(text = cell)
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { new ->
                                value = new
                                viewModel.updateTableBlock(
                                    block.id,
                                    TableModel.setCell(rows, r, c, new.text)
                                )
                            },
                            readOnly = readOnly,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (r == 0) themeColor.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .border(0.5.dp, gridColor)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (!readOnly) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    viewModel.updateTableBlock(block.id, TableModel.addRow(rows))
                }) { Text("+ Row", fontSize = 11.sp) }
                TextButton(onClick = {
                    viewModel.updateTableBlock(block.id, TableModel.removeRow(rows, rows.lastIndex))
                }) { Text("− Row", fontSize = 11.sp) }
                TextButton(onClick = {
                    viewModel.updateTableBlock(block.id, TableModel.addColumn(rows))
                }) { Text("+ Col", fontSize = 11.sp) }
                TextButton(onClick = {
                    viewModel.updateTableBlock(block.id, TableModel.removeColumn(rows, rows.first().lastIndex))
                }) { Text("− Col", fontSize = 11.sp) }
            }
        }
    }
}
