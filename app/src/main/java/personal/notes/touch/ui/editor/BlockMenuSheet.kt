package personal.notes.touch.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.ui.theme.LocalThemeColor

/**
 * Long-press/⋮ block menu: turn a block into another type, indent/outdent,
 * reorder, duplicate, copy its markdown or delete it. Shown as a bottom sheet
 * so it never competes with the text-field caret.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockMenuSheet(
    block: EditableBlock,
    onDismiss: () -> Unit,
    onTransform: (BlockType, Int) -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val themeColor = LocalThemeColor.current
    val isList = BlockStructureOps.isListBlock(block.type)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Block actions",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                text = typeLabel(block),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )

            Text(
                text = "TURN INTO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                turnIntoOptions().forEach { option ->
                    val selected = block.type == option.type &&
                        (option.type != BlockType.HEADING || block.headingLevel == option.level)
                    TurnChip(
                        label = option.label,
                        selected = selected,
                        themeColor = themeColor,
                        onClick = { if (!selected) onTransform(option.type, option.level) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            if (isList) {
                MenuRow(
                    label = "Indent",
                    hint = "⇥",
                    onClick = onIndent
                )
                MenuRow(
                    label = "Outdent",
                    hint = "⇤",
                    onClick = onOutdent
                )
            }
            MenuRow(label = "Move up", hint = "↑", onClick = onMoveUp)
            MenuRow(label = "Move down", hint = "↓", onClick = onMoveDown)
            MenuRow(label = "Duplicate", hint = "⧉", onClick = onDuplicate)
            MenuRow(label = "Copy block", hint = "📋", onClick = onCopy)
            MenuRow(label = "Delete block", hint = "🗑", danger = true, onClick = onDelete)
        }
    }
}

private data class TurnIntoOption(val label: String, val type: BlockType, val level: Int = 1)

private fun turnIntoOptions(): List<TurnIntoOption> = listOf(
    TurnIntoOption("Text", BlockType.PARAGRAPH),
    TurnIntoOption("H1", BlockType.HEADING, 1),
    TurnIntoOption("H2", BlockType.HEADING, 2),
    TurnIntoOption("H3", BlockType.HEADING, 3),
    TurnIntoOption("Bullet", BlockType.BULLET),
    TurnIntoOption("Numbered", BlockType.NUMBERED),
    TurnIntoOption("Task", BlockType.TASK),
    TurnIntoOption("Quote", BlockType.QUOTE)
)

private fun typeLabel(block: EditableBlock): String = when (block.type) {
    BlockType.HEADING -> "Heading ${block.headingLevel}"
    BlockType.PARAGRAPH -> "Paragraph"
    BlockType.BULLET -> "Bulleted list"
    BlockType.NUMBERED -> "Numbered list"
    BlockType.TASK -> "Task"
    BlockType.QUOTE -> "Quote"
    BlockType.CODE -> "Code block"
    BlockType.TABLE -> "Table"
    BlockType.IMAGE -> "Image"
    BlockType.DIVIDER -> "Divider"
}

@Composable
private fun TurnChip(
    label: String,
    selected: Boolean,
    themeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) themeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MenuRow(
    label: String,
    hint: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = hint,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (danger) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
        )
    }
}
