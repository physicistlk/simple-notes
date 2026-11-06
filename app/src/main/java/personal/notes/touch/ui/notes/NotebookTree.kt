package personal.notes.touch.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.text.FolderTree
import personal.notes.touch.ui.theme.OrangePrimary

/**
 * Notebook hierarchy for the library rail / drawer.
 *
 * The `folders` table has carried `parentId` since v3, but the library only
 * ever drew a flat chip row, so a subfolder could be created by the data layer
 * and never seen. This panel is the missing view: collapsible, indented,
 * with live note counts aggregated over descendants.
 *
 * Interaction mirrors a file manager: tap a row to filter, tap the chevron to
 * expand, the trailing menu renames/deletes or creates a child. "All Notes"
 * clears the filter.
 */
@Composable
fun NotebookTreePanel(
    folders: List<FolderEntity>,
    counts: Map<String?, Int>,
    selectedFolderId: String?,
    expanded: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onNewFolder: (parentId: String?) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputs = remember(folders) {
        folders.map { FolderTree.Input(id = it.id, name = it.name, parentId = it.parentId) }
    }
    val directCounts = remember(counts) {
        counts.mapNotNull { (id, count) -> id?.let { it to count } }.toMap()
    }
    val rows = remember(inputs, directCounts) { FolderTree.build(inputs, directCounts) }
    val visibleRows = remember(rows, expanded) { FolderTree.visible(rows, expanded) }
    val totalNotes = remember(counts) { counts.values.sum() }
    val byId = remember(folders) { folders.associateBy { it.id } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 2.dp, top = 4.dp, bottom = 2.dp)
        ) {
            Text(
                text = "NOTEBOOKS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onNewFolder(null) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New notebook",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "__all") {
                TreeRow(
                    label = "All Notes",
                    count = totalNotes,
                    depth = 0,
                    hasChildren = false,
                    expanded = false,
                    selected = selectedFolderId == null,
                    icon = if (selectedFolderId == null) Icons.Default.FolderOpen else Icons.Default.Folder,
                    onClick = { onSelectFolder(null) },
                    onToggleExpand = null,
                    menu = null
                )
            }
            items(visibleRows, key = { it.id }) { node ->
                val folder = byId[node.id]
                val isExpanded = node.id in expanded
                val hasChildren = rows.any { it.parentId == node.id }
                val expandAction: (() -> Unit)? = if (hasChildren) {
                    ({ onToggleExpand(node.id) })
                } else {
                    null
                }
                val menuContent: (@Composable () -> Unit)? = if (folder != null) {
                    ({
                        FolderTreeMenu(
                            onRename = { onRenameFolder(folder) },
                            onNewChild = { onNewFolder(folder.id) },
                            onDelete = { onDeleteFolder(folder) }
                        )
                    })
                } else {
                    null
                }
                TreeRow(
                    label = node.name,
                    count = node.totalCount,
                    depth = node.depth,
                    hasChildren = hasChildren,
                    expanded = isExpanded,
                    selected = selectedFolderId == node.id,
                    icon = if (isExpanded && hasChildren) Icons.Default.FolderOpen else Icons.Default.Folder,
                    onClick = { onSelectFolder(node.id) },
                    onToggleExpand = expandAction,
                    menu = menuContent
                )
            }
        }
    }
}

@Composable
private fun TreeRow(
    label: String,
    count: Int,
    depth: Int,
    hasChildren: Boolean,
    expanded: Boolean,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onToggleExpand: (() -> Unit)?,
    menu: (@Composable () -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OrangePrimary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                start = 4.dp + (depth.coerceAtMost(6) * 12).dp,
                end = 2.dp,
                top = 6.dp,
                bottom = 6.dp
            )
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (hasChildren) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(enabled = onToggleExpand != null) {
                            onToggleExpand?.invoke()
                        }
                )
            }
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) OrangePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) OrangePrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(
                text = "$count",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (menu != null) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                menu()
            }
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
    }
}

@Composable
private fun FolderTreeMenu(
    onRename: () -> Unit,
    onNewChild: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Notebook actions",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { open = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text("New subfolder", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { open = false; onNewChild() }
            )
            DropdownMenuItem(
                text = { Text("Delete", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}
