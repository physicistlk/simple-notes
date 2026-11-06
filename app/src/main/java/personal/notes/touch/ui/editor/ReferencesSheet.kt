package personal.notes.touch.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.data.db.dao.BlockBacklink
import personal.notes.touch.data.db.dao.BlockRefTarget
import personal.notes.touch.data.db.dao.RefTarget
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.text.BlockRefs
import personal.notes.touch.ui.theme.LocalThemeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Two-way reference panel for the open note.
 *
 * *Referenced by* lists notes that contain `[[this title]]`; *Links from this
 * note* lists what this note points at, marking targets that no longer exist.
 * Deliberate scope: matching is by title, so a renamed note leaves older
 * references unresolved rather than silently re-pointing them — the "not
 * found" rows are the honest signal for that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferencesSheet(
    noteTitle: String,
    backlinks: List<NoteEntity>,
    outgoing: List<RefTarget>,
    blockOutgoing: List<BlockRefTarget> = emptyList(),
    blockBacklinks: List<BlockBacklink> = emptyList(),
    onOpenNote: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColor = LocalThemeColor.current
    val dateFormat = androidx.compose.runtime.remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "References",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                text = "[[${noteTitle}]]",
                fontSize = 12.sp,
                color = themeColor,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .padding(top = 10.dp)
            ) {
                item {
                    SectionHeader("REFERENCED BY", backlinks.size)
                }
                if (backlinks.isEmpty()) {
                    item {
                        Hint(
                            "No note links here yet. Type [[${noteTitle}]] in another note, " +
                                "or open a [[ link from this one."
                        )
                    }
                } else {
                    items(backlinks, key = { it.id }) { note ->
                        BacklinkRow(
                            note = note,
                            dateText = dateFormat.format(Date(note.updatedAt)),
                            onClick = { onOpenNote(note.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionHeader("LINKS FROM THIS NOTE", outgoing.size)
                }
                if (outgoing.isEmpty()) {
                    item {
                        Hint("This note has no [[wiki links]] yet. Type [[ and pick a note.")
                    }
                } else {
                    items(outgoing, key = { it.targetKey }) { ref ->
                        OutgoingRow(
                            ref = ref,
                            themeColor = themeColor,
                            onClick = { ref.noteId?.let(onOpenNote) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionHeader("BLOCK BACKLINKS", blockBacklinks.size)
                }
                if (blockBacklinks.isEmpty()) {
                    item {
                        Hint(
                            "No note points at a block here yet. Type (( in a block " +
                                "to pick one from any note."
                        )
                    }
                } else {
                    items(
                        blockBacklinks,
                        key = { it.sourceNoteId + "|" + it.targetKey }
                    ) { ref ->
                        BlockBacklinkRow(
                            ref = ref,
                            themeColor = themeColor,
                            onClick = { onOpenNote(ref.sourceNoteId) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionHeader("BLOCKS LINKED FROM HERE", blockOutgoing.size)
                }
                if (blockOutgoing.isEmpty()) {
                    item {
                        Hint("This note has no ((block references)) yet.")
                    }
                } else {
                    items(blockOutgoing, key = { it.targetKey }) { ref ->
                        BlockRefRow(
                            ref = ref,
                            themeColor = themeColor,
                            onClick = { ref.targetNoteId?.let(onOpenNote) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        Text(
            text = "$count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun BacklinkRow(
    note: NoteEntity,
    dateText: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun OutgoingRow(
    ref: RefTarget,
    themeColor: Color,
    onClick: () -> Unit
) {
    val resolved = ref.noteId != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = resolved, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = if (resolved) Icons.Default.CheckCircleOutline else Icons.Default.WarningAmber,
            contentDescription = null,
            tint = if (resolved) themeColor else Color(0xFFE0A800),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ref.resolvedTitle ?: ref.targetTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (resolved) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!resolved) {
                Text(
                    text = "No note with this title",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun BlockBacklinkRow(
    ref: BlockBacklink,
    themeColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = themeColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ref.sourceTitle.ifBlank { "Untitled Note" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "→ " + BlockRefs.labelFromText(ref.targetText)
                    .ifEmpty { "Missing block" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun BlockRefRow(
    ref: BlockRefTarget,
    themeColor: Color,
    onClick: () -> Unit
) {
    val resolved = ref.targetBlockId != null && ref.targetTitle != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = resolved, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = if (resolved) Icons.Default.CheckCircleOutline else Icons.Default.WarningAmber,
            contentDescription = null,
            tint = if (resolved) themeColor else Color(0xFFE0A800),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BlockRefs.displayLabel(ref.targetAlias, ref.targetText, ref.targetKey),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (resolved) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (resolved) {
                    ref.targetTitle ?: "Untitled Note"
                } else {
                    "No block with this id"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
