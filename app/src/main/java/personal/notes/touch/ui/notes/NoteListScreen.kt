package personal.notes.touch.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.data.db.dao.TagCount
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.TemplateEntity
import personal.notes.touch.text.SearchSnippet
import personal.notes.touch.ui.theme.OrangePrimary
import personal.notes.touch.ui.theme.TouchNotesSwatches
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import personal.notes.touch.util.BitmapUtils
import personal.notes.touch.util.PdfVectorImporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onNoteClick: (String) -> Unit,
    onOpenEditorClick: ((String) -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onFlashcardsClick: (() -> Unit)? = null
) {
    val notes by viewModel.notes.collectAsState()
    val tagCounts by viewModel.tagCounts.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val folderCounts by viewModel.folderCounts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var isLeftRailCollapsed by remember { mutableStateOf(false) }
    var isModalDrawerOpen by remember { mutableStateOf(false) }
    var showUserManualDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }

    // Dialog States
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameToCreate by remember { mutableStateOf("") }
    // null = a new root notebook; otherwise the parent chosen in the tree.
    var newFolderParentId by remember { mutableStateOf<String?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }

    // Notebook tree: which folders are open. Everything starts expanded so the
    // hierarchy that existed in the database but was never drawn is visible on
    // first open; the chevrons collapse from there.
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var treeInitialised by remember { mutableStateOf(false) }
    LaunchedEffect(folders) {
        if (!treeInitialised && folders.isNotEmpty()) {
            expandedFolders = folders.map { it.id }.toSet()
            treeInitialised = true
        }
    }

    var renameFolderTarget by remember { mutableStateOf<FolderEntity?>(null) }
    var newFolderNameText by remember { mutableStateOf("") }

    var renameNoteTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var newNoteTitleText by remember { mutableStateOf("") }

    var moveNoteTarget by remember { mutableStateOf<NoteEntity?>(null) }
    var exportTargetNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showEmptyRecycleDialog by remember { mutableStateOf(false) }

    // Templates + daily notes (schema 9)
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateEntity?>(null) }
    val templates by viewModel.templates.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                // Decode off the main thread.
                val decoded = withContext(Dispatchers.IO) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // A phone photo is 12 MP: decoding it at full size
                            // costs ~48 MB, so the decoder is asked for a
                            // sample that fits the page.
                            val source = android.graphics.ImageDecoder.createSource(
                                context.contentResolver, it
                            )
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                                val size = info.size
                                val maxSide = 2160
                                if (size.width > maxSide || size.height > maxSide) {
                                    val sample = maxOf(
                                        size.width / maxSide,
                                        size.height / maxSide
                                    )
                                    decoder.setTargetSampleSize(sample)
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                decoded?.let { bmp ->
                    // Create the note and save the bitmap as its page
                    // background (page_0.png) so "Imported Image" notes show
                    // the image when opened; the source bitmap is recycled
                    // by the ViewModel after the file is written.
                    viewModel.createImageNote(bmp) { id ->
                        onNoteClick(id)
                    }
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val imported = try {
                    withContext(Dispatchers.IO) { PdfVectorImporter.readDocument(context, it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                if (imported == null) {
                    android.widget.Toast.makeText(context, "Could not open PDF", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                try {
                    val note = viewModel.repository.createNote(
                        title = "Imported PDF",
                        folderId = uiState.selectedFolderId,
                        pageType = "pdf"
                    )
                    val noteId = note.id
                    val fileStore = viewModel.repository.fileStore
                    // Per-page render error toast: Toast.show() must run on a
                    // thread with a Looper, so bounce back to Main.
                    suspend fun showPageError(msg: String) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    // F-10 vector import: renders each page from pdfium and
                    // records the source PDF so pages re-render sharply at any
                    // zoom. The whole loop (render, PNG compression, file
                    // writes) runs on Dispatchers.IO.
                    withContext(Dispatchers.IO) {
                        PdfVectorImporter.importIntoNote(
                            context = context,
                            imported = imported,
                            noteId = noteId,
                            repository = viewModel.repository,
                            fileStore = fileStore,
                            targetWidth = 2160,
                            targetHeight = 3056,
                            reuseFirstPage = true,
                            onPageError = { msg -> showPageError(msg) }
                        )
                    }
                    // Update note page count
                    viewModel.repository.updateNotePageCount(note.id, imported.pageCount)
                    onNoteClick(noteId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "PDF import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    imported.document.close()
                }
            }
        }
    }

    // Formatter matching screenshot: "2026-10-23 10:36"
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // Dialog: Create Folder
    if (showCreateFolderDialog) {
        val parentName = newFolderParentId?.let { id -> folders.firstOrNull { it.id == id }?.name }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = {
                Text(
                    if (parentName != null) "New subfolder" else "New Folder",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (parentName != null) {
                        Text(
                            "Inside \"$parentName\"",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    OutlinedTextField(
                        value = folderNameToCreate,
                        onValueChange = { folderNameToCreate = it },
                        singleLine = true,
                        label = { Text("Folder Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parentId = newFolderParentId
                    if (folderNameToCreate.isNotBlank()) {
                        viewModel.createFolder(folderNameToCreate, parentId)
                        // A fresh subfolder is invisible while its parent is
                        // collapsed, so open the parent right away.
                        if (parentId != null) expandedFolders = expandedFolders + parentId
                        folderNameToCreate = ""
                        newFolderParentId = null
                        showCreateFolderDialog = false
                    }
                }) { Text("Create", color = OrangePrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    newFolderParentId = null
                }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete Folder (children and notes move up one level)
    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("Delete notebook", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete \"${folder.name}\"? Its notes and subfolders move up one " +
                        "level; nothing is deleted from the notes themselves."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder.id)
                    deleteFolderTarget = null
                }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Rename Folder
    renameFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderNameText,
                    onValueChange = { newFolderNameText = it },
                    singleLine = true,
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderNameText.isNotBlank()) {
                        viewModel.renameFolder(folder.id, newFolderNameText)
                        renameFolderTarget = null
                    }
                }) { Text("Save", color = OrangePrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renameFolderTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: "Change note name" (matches Screenshot_20261102-184531_TouchNotes.png)
    renameNoteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { renameNoteTarget = null },
            title = {
                Text(
                    text = "Change note name",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = Color(0xFF212529)
                )
            },
            text = {
                TextField(
                    value = newNoteTitleText,
                    onValueChange = { newNoteTitleText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = OrangePrimary,
                        unfocusedIndicatorColor = Color(0xFFCCCCCC),
                        cursorColor = OrangePrimary
                    )
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { renameNoteTarget = null }) {
                        Text("Cancel", color = Color.Gray, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Divider(
                        modifier = Modifier
                            .height(20.dp)
                            .width(1.dp),
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = {
                        if (newNoteTitleText.isNotBlank()) {
                            viewModel.renameNote(note.id, newNoteTitleText)
                            renameNoteTarget = null
                        }
                    }) {
                        Text(
                            "Ok",
                            color = OrangePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            },
            dismissButton = null,
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Move Note
    moveNoteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { moveNoteTarget = null },
            title = { Text("Move to Folder") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.moveNoteToFolder(note.id, null)
                                moveNoteTarget = null
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = note.folderId == null,
                            onClick = {
                                viewModel.moveNoteToFolder(note.id, null)
                                moveNoteTarget = null
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Notes (Root)")
                    }
                    folders.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveNoteToFolder(note.id, f.id)
                                    moveNoteTarget = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = note.folderId == f.id,
                                onClick = {
                                    viewModel.moveNoteToFolder(note.id, f.id)
                                    moveNoteTarget = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(f.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveNoteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Theme Color Quick Picker (M6)
    if (showThemeColorDialog) {
        AlertDialog(
            onDismissRequest = { showThemeColorDialog = false },
            title = { Text("Theme Color", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchNotesSwatches.chunked(5).forEach { rowSwatches ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowSwatches.forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(swatch.color)
                                        .border(1.dp, Color.LightGray, CircleShape)
                                        .clickable {
                                            viewModel.setThemeColor(swatch.color.toArgb())
                                            showThemeColorDialog = false
                                        }
                                        // The swatches are the only way to pick
                                        // the theme colour: they need a name for
                                        // the screen reader.
                                        .semantics {
                                            contentDescription = "Theme colour ${swatch.color.toArgb().toUInt().toString(16)}"
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeColorDialog = false }) { Text("Done") }
            }
        )
    }

    // Dialog: Empty Recycle Bin
    if (showEmptyRecycleDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyRecycleDialog = false },
            title = { Text("Empty Recycle Bin") },
            text = { Text("Permanently delete all notes in the recycle bin?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyRecycleBin()
                    showEmptyRecycleDialog = false
                }) { Text("Empty", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyRecycleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Export Note
    val activeExportNote = exportTargetNote
    if (activeExportNote != null) {
        personal.notes.touch.ui.export.ExportDialog(
            note = activeExportNote,
            repository = viewModel.repository,
            fileStore = viewModel.repository.fileStore,
            currentBitmap = null,
            currentPageIndex = 0,
            onDismiss = { exportTargetNote = null }
        )
    }

    // Daily notes + templates
    if (showTemplatesDialog) {
        TemplatesDialog(
            templates = templates,
            onDismiss = { showTemplatesDialog = false },
            onUse = { template ->
                showTemplatesDialog = false
                viewModel.createFromTemplate(template.id) { id ->
                    onOpenEditorClick?.invoke(id) ?: onNoteClick(id)
                }
            },
            onEdit = { template ->
                editingTemplate = template
                showTemplatesDialog = false
                showTemplateEditor = true
            },
            onSetDaily = { template ->
                viewModel.saveTemplate(template.id, template.name, template.content, true)
            },
            onDelete = { viewModel.deleteTemplate(it.id) },
            onAddStarters = { viewModel.addStarterTemplates() }
        )
    }

    if (showTemplateEditor) {
        TemplateEditDialog(
            template = editingTemplate,
            onDismiss = {
                showTemplateEditor = false
                editingTemplate = null
            },
            onSave = { name, content, daily ->
                viewModel.saveTemplate(editingTemplate?.id, name, content, daily)
                showTemplateEditor = false
                editingTemplate = null
                showTemplatesDialog = true
            }
        )
    }

    // Dialog: User Manual & Gestures (Screen 2 parity)
    if (showUserManualDialog) {
        AlertDialog(
            onDismissRequest = { showUserManualDialog = false },
            title = { Text("Simple Notes Manual & Gestures") },
            text = {
                Column {
                    Text("• Stylus Inking: Fluid pressure-sensitive fountain pen, fineliner, highlighter, eraser.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Undo / Redo: 2-finger tap on canvas to undo, 3-finger tap to redo.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Tool Dock: Toggle between vertical floating dock and horizontal top dock.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Thumbnail Drawer: Tap left book icon on top bar to manage note pages.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Reading Mode: Tap top dropdown arrow for full-screen distraction-free study.", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserManualDialog = false }) { Text("Got It") }
            }
        )
    }

    // Dialog: About TouchNotes (Screen 2 parity)
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Simple Notes") },
            text = {
                Column {
                    Text("Simple Notes v0.1.0", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("100% Private & Offline-first notebook with Room SQLite persistence, native hardware canvas acceleration, and zero network tracking.", fontSize = 13.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }

    // Root layout: Persistent Left Rail + Main Content Area + Slide-over Modal Drawer
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        // ==========================================
        // 1. LEFT NAVIGATION RAIL (Tablet Landscape)
        // ==========================================
        if (!isLeftRailCollapsed) {
            Surface(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 16.dp)
                ) {
                    // Header Category Pill
                    Surface(
                        onClick = { isModalDrawerOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF0EC),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account & More",
                                tint = OrangePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Simple Notes",
                                color = OrangePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Navigation Rail Items
                    NavRailItem(
                        icon = Icons.Default.Description,
                        label = "Notes",
                        isSelected = uiState.currentFilter == NoteFilter.ALL && uiState.selectedFolderId == null,
                        onClick = { viewModel.setFilter(NoteFilter.ALL) }
                    )

                    NavRailItem(
                        icon = Icons.Default.Today,
                        label = "Today",
                        isSelected = false,
                        onClick = {
                            viewModel.openToday { id ->
                                onOpenEditorClick?.invoke(id) ?: onNoteClick(id)
                            }
                        }
                    )

                    NavRailItem(
                        icon = Icons.Default.Widgets,
                        label = "Templates",
                        isSelected = false,
                        onClick = { showTemplatesDialog = true }
                    )

                    NavRailItem(
                        icon = Icons.Default.Style,
                        label = "Flashcards",
                        isSelected = false,
                        onClick = { onFlashcardsClick?.invoke() }
                    )

                    NavRailItem(
                        icon = Icons.Default.Star,
                        label = "Favorites",
                        isSelected = uiState.currentFilter == NoteFilter.FAVORITES,
                        onClick = { viewModel.setFilter(NoteFilter.FAVORITES) }
                    )

                    NavRailItem(
                        icon = Icons.Default.Delete,
                        label = "Trash",
                        isSelected = uiState.currentFilter == NoteFilter.RECYCLE_BIN,
                        onClick = { viewModel.setFilter(NoteFilter.RECYCLE_BIN) }
                    )

                    NavRailItem(
                        icon = Icons.Default.History,
                        label = "Local History Backup",
                        isSelected = false,
                        onClick = { onSettingsClick() }
                    )

                    NavRailItem(
                        icon = Icons.Default.Save,
                        label = "Local Backup",
                        isSelected = false,
                        onClick = { onSettingsClick() }
                    )

                    NavRailItem(
                        icon = Icons.Default.Palette,
                        label = "Theme Color",
                        isSelected = false,
                        onClick = { showThemeColorDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notebook hierarchy (folders.parentId has existed since
                    // v3; this is the view that finally exposes it).
                    NotebookTreePanel(
                        folders = folders,
                        counts = folderCounts,
                        selectedFolderId = uiState.selectedFolderId,
                        expanded = expandedFolders,
                        onToggleExpand = { id ->
                            expandedFolders = if (id in expandedFolders) {
                                expandedFolders - id
                            } else {
                                expandedFolders + id
                            }
                        },
                        onSelectFolder = { viewModel.selectFolder(it) },
                        onNewFolder = { parentId ->
                            newFolderParentId = parentId
                            folderNameToCreate = ""
                            showCreateFolderDialog = true
                        },
                        onRenameFolder = { folder ->
                            renameFolderTarget = folder
                            newFolderNameText = folder.name
                        },
                        onDeleteFolder = { deleteFolderTarget = it },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Collapse button (<)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isLeftRailCollapsed = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBackIosNew,
                            contentDescription = "Collapse Rail",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Collapse Sidebar",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = Color(0xFFEBEBEB)
            )
        } else {
            // Collapsed rail expand tab
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .background(Color.White)
                    .clickable { isLeftRailCollapsed = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = "Expand Rail",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = Color(0xFFEBEBEB)
            )
        }

        // ==========================================
        // 2. MAIN CONTENT AREA
        // ==========================================
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar (matching TouchNotes Screenshot_20261102-184136_TouchNotes.png)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Hamburger + Notes pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isLeftRailCollapsed = !isLeftRailCollapsed }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Toggle Sidebar",
                                    tint = Color(0xFF333333)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = { isModalDrawerOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFF0EC)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = when (uiState.currentFilter) {
                                            NoteFilter.FAVORITES -> "Favorites"
                                            NoteFilter.RECYCLE_BIN -> "Trash"
                                            else -> "Notes"
                                        },
                                        color = OrangePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Account & More",
                                        tint = OrangePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Right action buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.currentFilter == NoteFilter.RECYCLE_BIN && notes.isNotEmpty()) {
                                Surface(
                                    onClick = { showEmptyRecycleDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFEBEE),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Empty Trash", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            // Cloud icon
                            IconButton(onClick = {
                                Toast.makeText(context, "All notes stored locally (100% offline & private)", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Sync",
                                    tint = Color(0xFF666666)
                                )
                            }

                            // Sort icon & dropdown
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = Color(0xFF666666)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Updated: Newest First") },
                                        onClick = {
                                            viewModel.setSortOrder(NoteSortOrder.UPDATED_DESC)
                                            showSortMenu = false
                                        },
                                        leadingIcon = if (uiState.sortOrder == NoteSortOrder.UPDATED_DESC) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = OrangePrimary) }
                                        } else null
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Updated: Oldest First") },
                                        onClick = {
                                            viewModel.setSortOrder(NoteSortOrder.UPDATED_ASC)
                                            showSortMenu = false
                                        },
                                        leadingIcon = if (uiState.sortOrder == NoteSortOrder.UPDATED_ASC) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = OrangePrimary) }
                                        } else null
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Title: A to Z") },
                                        onClick = {
                                            viewModel.setSortOrder(NoteSortOrder.TITLE_ASC)
                                            showSortMenu = false
                                        },
                                        leadingIcon = if (uiState.sortOrder == NoteSortOrder.TITLE_ASC) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = OrangePrimary) }
                                        } else null
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Title: Z to A") },
                                        onClick = {
                                            viewModel.setSortOrder(NoteSortOrder.TITLE_DESC)
                                            showSortMenu = false
                                        },
                                        leadingIcon = if (uiState.sortOrder == NoteSortOrder.TITLE_DESC) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = OrangePrimary) }
                                        } else null
                                    )
                                }
                            }

                            // Search icon
                            IconButton(onClick = { viewModel.toggleSearch(!uiState.isSearchOpen) }) {
                                Icon(
                                    imageVector = if (uiState.isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF666666)
                                )
                            }

                            // Multi-select icon
                            IconButton(onClick = {
                                android.widget.Toast.makeText(context, "${notes.size} note(s) shown", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Select",
                                    tint = Color(0xFF666666)
                                )
                            }

                            // New Folder icon
                            IconButton(onClick = {
                                newFolderParentId = null
                                showCreateFolderDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "New Folder",
                                    tint = Color(0xFF666666)
                                )
                            }

                            // Grid/List toggle
                            IconButton(onClick = viewModel::toggleViewMode) {
                                Icon(
                                    imageVector = if (uiState.viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List",
                                    tint = Color(0xFF666666)
                                )
                            }

                            // Settings gear
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFFEBEBEB), thickness = 1.dp)

                // Search input when opened
                if (uiState.isSearchOpen) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text("Search titles and content...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        if (uiState.searchQuery.isNotBlank()) {
                            Text(
                                text = if (notes.isEmpty()) {
                                    "No matches for \"${uiState.searchQuery.trim()}\""
                                } else {
                                    "${notes.size} ${if (notes.size == 1) "note" else "notes"} match"
                                },
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                            )
                        }
                    }
                }

                // Tag bar (schema 7 index): one chip per tag, with note counts.
                if (tagCounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.selectedTag != null) {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.toggleTag(null) },
                                label = { Text("All tags") }
                            )
                        }
                        tagCounts.forEach { tag ->
                            TagChip(
                                tag = tag,
                                selected = uiState.selectedTag == tag.tag,
                                onClick = { viewModel.toggleTag(tag.tag) }
                            )
                        }
                    }
                }

                // Folder Filter Chips
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedFolderId == null,
                            onClick = { viewModel.selectFolder(null) },
                            label = { Text("All Folders") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangePrimary.copy(alpha = 0.12f),
                                selectedLabelColor = OrangePrimary
                            )
                        )
                        folders.forEach { folder ->
                            FilterChip(
                                selected = uiState.selectedFolderId == folder.id,
                                onClick = { viewModel.selectFolder(folder.id) },
                                // The flat row stays for fast filtering; the
                                // marker keeps nested folders distinguishable.
                                label = {
                                    Text(
                                        if (folder.parentId != null) "\u21B3 ${folder.name}" else folder.name
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangePrimary.copy(alpha = 0.12f),
                                    selectedLabelColor = OrangePrimary
                                )
                            )
                        }
                    }
                }

                // Main Note Cards Grid (3 Columns on tablet landscape)
                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No notes found",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (uiState.viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize().weight(1f)
                    ) {
                        items(notes, key = { it.note.id }) { row ->
                            val note = row.note
                            val isSelected = selectedNoteId == note.id
                            TouchNotesCard(
                                note = note,
                                tags = row.tags,
                                snippet = row.snippet,
                                isSelected = isSelected,
                                dateFormatter = dateFormatter,
                                onCardClick = {
                                    if (uiState.currentFilter == NoteFilter.RECYCLE_BIN) {
                                        Toast.makeText(context, "Note is in trash. Use 'Restore' from menu to edit.", Toast.LENGTH_SHORT).show()
                                        return@TouchNotesCard
                                    }
                                    selectedNoteId = note.id
                                    // Route dispatch: "text" notes open in the
                                    // text (block) editor; canvas page types
                                    // stay on the canvas route.
                                    if (note.pageType == "text") {
                                        onOpenEditorClick?.invoke(note.id) ?: onNoteClick(note.id)
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(note) },
                                onRenameClick = {
                                    renameNoteTarget = note
                                    newNoteTitleText = note.title
                                },
                                onMoveClick = { moveNoteTarget = note },
                                onDeleteClick = { viewModel.deleteNote(note.id) },
                                onRestoreClick = { viewModel.restoreNote(note.id) },
                                onPermanentDeleteClick = { viewModel.permanentlyDeleteNote(note.id) },
                                onExportClick = { exportTargetNote = note },
                                isRecycleBin = uiState.currentFilter == NoteFilter.RECYCLE_BIN,
                                isGrid = true
                            )
                        }
                    }
                } else {
                    // List View
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().weight(1f)
                    ) {
                        items(notes, key = { it.note.id }) { row ->
                            val note = row.note
                            val isSelected = selectedNoteId == note.id
                            TouchNotesCard(
                                note = note,
                                tags = row.tags,
                                snippet = row.snippet,
                                isSelected = isSelected,
                                dateFormatter = dateFormatter,
                                onCardClick = {
                                    if (uiState.currentFilter == NoteFilter.RECYCLE_BIN) {
                                        Toast.makeText(context, "Note is in trash. Use 'Restore' from menu to edit.", Toast.LENGTH_SHORT).show()
                                        return@TouchNotesCard
                                    }
                                    selectedNoteId = note.id
                                    // Route dispatch: "text" notes open in the
                                    // text (block) editor; canvas page types
                                    // stay on the canvas route.
                                    if (note.pageType == "text") {
                                        onOpenEditorClick?.invoke(note.id) ?: onNoteClick(note.id)
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(note) },
                                onRenameClick = {
                                    renameNoteTarget = note
                                    newNoteTitleText = note.title
                                },
                                onMoveClick = { moveNoteTarget = note },
                                onDeleteClick = { viewModel.deleteNote(note.id) },
                                onRestoreClick = { viewModel.restoreNote(note.id) },
                                onPermanentDeleteClick = { viewModel.permanentlyDeleteNote(note.id) },
                                onExportClick = { exportTargetNote = note },
                                isRecycleBin = uiState.currentFilter == NoteFilter.RECYCLE_BIN,
                                isGrid = false
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. FAB & POPUP CREATION MENU
            // (Screenshot_20261102-184549_TouchNotes.png)
            // ==========================================
            // Floating Popup Menu Card
            if (isFabMenuExpanded) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 84.dp)
                        .width(220.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        FabMenuItem(Icons.Default.Description, "Infinite Canvas") {
                            isFabMenuExpanded = false
                            viewModel.createNote(pageType = "infinite") { id -> onNoteClick(id) }
                        }
                        FabMenuItem(Icons.Default.Description, "A4 Page") {
                            isFabMenuExpanded = false
                            viewModel.createNote(pageType = "a4") { id -> onNoteClick(id) }
                        }
                        FabMenuItem(Icons.Default.Folder, "Folder") {
                            isFabMenuExpanded = false
                            newFolderParentId = uiState.selectedFolderId
                            showCreateFolderDialog = true
                        }
                        FabMenuItem(Icons.Default.TextFields, "Text Note") {
                            isFabMenuExpanded = false
                            // pageType "text" marks this note for the text
                            // (block) editor at dispatch time — no Room change
                            // needed, the column already exists.
                            viewModel.createNote(pageType = "text") { id ->
                                onOpenEditorClick?.invoke(id) ?: onNoteClick(id)
                            }
                        }
                        FabMenuItem(Icons.Default.Image, "Import Image") {
                            isFabMenuExpanded = false
                            imagePickerLauncher.launch("image/*")
                        }
                        FabMenuItem(Icons.Default.PictureAsPdf, "Import PDF") {
                            isFabMenuExpanded = false
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        }
                        FabMenuItem(Icons.Default.Timeline, "MindMap") {
                            isFabMenuExpanded = false
                            viewModel.createNote(title = "MindMap") { id -> onNoteClick(id) }
                        }
                        FabMenuItem(Icons.Default.Create, "Create Live Journal") {
                            isFabMenuExpanded = false
                            viewModel.createNote(title = "Journal Note") { id -> onNoteClick(id) }
                        }
                        FabMenuItem(Icons.Default.Style, "Flashcards & Study") {
                            isFabMenuExpanded = false
                            onFlashcardsClick?.invoke()
                        }
                    }
                }
            }

            // Main Coral / Orange Circular FAB
            FloatingActionButton(
                onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                containerColor = OrangePrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(58.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New",
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(if (isFabMenuExpanded) 45f else 0f)
                )
            }
        }
    }

    // ==========================================
    // 3. SLIDE-OVER MODAL NAVIGATION DRAWER (Screen 2 Parity)
    // ==========================================
    if (isModalDrawerOpen) {
        // Dark scrim background (#292929 / 50% alpha)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { isModalDrawerOpen = false }
        )

        // Pure white drawer surface sliding from left
        Surface(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight(),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 24.dp)
            ) {
                // User Account Section (Matching Screenshot_20261102-184146_TouchNotes.png)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TouchNotes User",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF222222)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF0EC)
                        ) {
                            Text(
                                text = "Offline Edition",
                                color = OrangePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Menu items matching Screen 2
                DrawerMenuItem(
                    icon = Icons.Default.CloudQueue,
                    label = "Cloud Backup (Offline Only)",
                    badge = "Offline",
                    onClick = {
                        isModalDrawerOpen = false
                        onSettingsClick()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Settings,
                    label = "More Settings",
                    onClick = {
                        isModalDrawerOpen = false
                        onSettingsClick()
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.HelpOutline,
                    label = "User Manual & Gestures",
                    onClick = {
                        isModalDrawerOpen = false
                        showUserManualDialog = true
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Send,
                    label = "TN News & Updates",
                    badge = "v7.0.4",
                    onClick = {
                        isModalDrawerOpen = false
                    }
                )

                DrawerMenuItem(
                    icon = Icons.Default.Info,
                    label = "About note",
                    onClick = {
                        isModalDrawerOpen = false
                        showAboutDialog = true
                    }
                )

                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                NotebookTreePanel(
                    folders = folders,
                    counts = folderCounts,
                    selectedFolderId = uiState.selectedFolderId,
                    expanded = expandedFolders,
                    onToggleExpand = { id ->
                        expandedFolders = if (id in expandedFolders) {
                            expandedFolders - id
                        } else {
                            expandedFolders + id
                        }
                    },
                    onSelectFolder = { folderId ->
                        viewModel.selectFolder(folderId)
                        isModalDrawerOpen = false
                    },
                    onNewFolder = { parentId ->
                        newFolderParentId = parentId
                        folderNameToCreate = ""
                        showCreateFolderDialog = true
                    },
                    onRenameFolder = { folder ->
                        renameFolderTarget = folder
                        newFolderNameText = folder.name
                    },
                    onDeleteFolder = { deleteFolderTarget = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Footer capsule: "Offline All Features Unlocked"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF5F2),
                    border = BorderStroke(1.dp, Color(0xFFFFD5CC))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline All Features Unlocked",
                            color = OrangePrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun NavRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) OrangePrimary else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else OrangePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFF2C3E50)
            )
        }
    }
}

@Composable
fun TouchNotesCard(
    note: NoteEntity,
    tags: List<String> = emptyList(),
    snippet: SearchSnippet? = null,
    isSelected: Boolean,
    dateFormatter: SimpleDateFormat,
    onCardClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onPermanentDeleteClick: () -> Unit,
    onExportClick: () -> Unit,
    isRecycleBin: Boolean,
    isGrid: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        // Thumbnail Card Surface (Aspect ratio ~1:1.414 A4 paper proportions)
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, OrangePrimary) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E8E8)),
            modifier = if (isGrid) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Inside Note Preview (Drawing canvas thumbnail or formatted text preview)
                NoteInsidePreview(
                    note = note,
                    modifier = Modifier.fillMaxSize()
                )

                // Favorite Star in Top Right Corner (matching TouchNotes)
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (note.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (note.favorite) OrangePrimary else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title and 3-dot Menu Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) OrangePrimary else Color(0xFF212529),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Actions",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!isRecycleBin) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Folder") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                            onClick = {
                                showMenu = false
                                onMoveClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export") },
                            leadingIcon = { Icon(Icons.Default.Description, null) },
                            onClick = {
                                showMenu = false
                                onExportClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Trash", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            leadingIcon = { Icon(Icons.Default.Restore, null) },
                            onClick = {
                                showMenu = false
                                onRestoreClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Permanently", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = Color.Red) },
                            onClick = {
                                showMenu = false
                                onPermanentDeleteClick()
                            }
                        )
                    }
                }
            }
        }

        // Timestamp formatted: "2026-10-23 10:36"
        Text(
            text = dateFormatter.format(Date(note.updatedAt)),
            fontSize = 12.sp,
            color = Color.Gray
        )

        // Search hit: the matched excerpt, tokens emphasised.
        if (snippet != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = highlightSnippet(snippet),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color(0xFF495057),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Tags derived from the note body (#tag#), at most three on a card.
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tags.take(3).joinToString("  ") { "#$it" } +
                    if (tags.size > 3) "  +${tags.size - 3}" else "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OrangePrimary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Tag bar chip: `#name` plus its live-note count. */
@Composable
private fun TagChip(tag: TagCount, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "#${tag.displayName}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Text(text = "${tag.count}", fontSize = 11.sp, color = Color.Gray)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = OrangePrimary.copy(alpha = 0.12f),
            selectedLabelColor = OrangePrimary
        )
    )
}

/** Search snippet with the query tokens in bold orange (no ellipsis loss). */
private fun highlightSnippet(snippet: SearchSnippet): AnnotatedString = buildAnnotatedString {
    append(snippet.text)
    snippet.ranges.forEach { range ->
        val end = (range.last + 1).coerceAtMost(snippet.text.length)
        if (range.first < end) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = OrangePrimary
                ),
                range.first,
                end
            )
        }
    }
}

@Composable
fun FabMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF555555),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF212529)
        )
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF555555),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = Color(0xFF333333),
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF0F0F0)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Draws the page's saved ink (a transparent, full-page PNG) onto the page
 * thumbnail. Both images share the page aspect ratio. Returns [base] when the
 * ink cannot be decoded.
 */
private suspend fun composeThumbnailWithInk(
    base: android.graphics.Bitmap,
    strokesFile: File
): android.graphics.Bitmap {
    val strokes = BitmapUtils.loadAlphaThumbnail(strokesFile, 360, 500) ?: return base
    val composite = base.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return base
    android.graphics.Canvas(composite).drawBitmap(
        strokes,
        null,
        android.graphics.Rect(0, 0, composite.width, composite.height),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    )
    return composite
}

@Composable
fun NoteInsidePreview(
    note: NoteEntity,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val firstPageFile = remember(note.id) {
        File(File(context.filesDir, "note"), "${note.id}/page_0.png")
    }
    // Vector PDF pages keep the clean page render in page_0.png and the ink in
    // strokes_0.png, so the card has to compose them (the original shows the
    // annotated page). Raster pages have no strokes file: their page PNG
    // already contains the writing.
    val firstStrokesFile = remember(note.id) {
        File(File(context.filesDir, "note"), "${note.id}/strokes_0.png")
    }
    val targetFile = remember(note.id, note.coverPath) {
        val cover = note.coverPath?.let { File(it) }
        if (cover != null && cover.exists()) {
            cover
        } else {
            firstPageFile
        }
    }

    var thumbnailBitmap by remember(note.id, note.updatedAt) {
        val cached = if (targetFile.exists()) {
            BitmapUtils.getCachedThumbnail("${targetFile.absolutePath}:${targetFile.lastModified()}")
        } else null
        mutableStateOf(cached)
    }

    LaunchedEffect(note.id, note.updatedAt) {
        val base = BitmapUtils.loadThumbnail(targetFile, targetWidth = 360, targetHeight = 500)
            ?: return@LaunchedEffect
        thumbnailBitmap = if (targetFile == firstPageFile && firstStrokesFile.exists()) {
            composeThumbnailWithInk(base, firstStrokesFile)
        } else {
            base
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val previewBmp = thumbnailBitmap
        if (previewBmp != null && !previewBmp.isRecycled) {
            Image(
                bitmap = previewBmp.asImageBitmap(),
                contentDescription = "Note preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (note.pageType == "text" || !note.content.isNullOrBlank()) {
            // Text Note Inside Preview
            val previewLines = remember(note.content) {
                extractPreviewLines(note.content.orEmpty(), 8)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFCFCFD))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Notebook paper header rule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFE5E7EB), RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (previewLines.isNotEmpty()) {
                    previewLines.forEachIndexed { idx, line ->
                        Text(
                            text = line,
                            fontSize = if (idx == 0) 11.sp else 10.sp,
                            fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 14.sp,
                            color = if (idx == 0) Color(0xFF1F2937) else Color(0xFF4B5563),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Empty text note",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        } else {
            // Blank canvas paper preview with subtle ruled lines
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                val step = 24.dp.toPx()
                var y = step
                val linePaintColor = Color(0xFFF0F0F2)
                while (y < size.height) {
                    drawLine(
                        color = linePaintColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
            }
        }

        // Subdued Page Count or Type badge in bottom-right corner
        if (note.pageType == "text") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xCCF3F4F6), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "TEXT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280)
                )
            }
        } else if (note.pageCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xCCF3F4F6), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${note.pageCount}p",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

fun extractPreviewLines(rawContent: String, maxLines: Int = 8): List<String> {
    if (rawContent.isBlank()) return emptyList()
    val lines = rawContent.lines()
    val cleaned = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        var cleanedLine = trimmed
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^[-*+]\\s+\\[ \\]\\s*"), "☐ ")
            .replace(Regex("^[-*+]\\s+\\[[xX]\\]\\s*"), "☑ ")
            .replace(Regex("^- \\[ \\]\\s*"), "☐ ")
            .replace(Regex("^- \\[[xX]\\]\\s*"), "☑ ")
            .replace(Regex("^[-*+]\\s+"), "• ")
            .replace(Regex("^\\d+\\.\\s+"), "")
            .replace(Regex("^>\\s*"), "")
            // Only the highlighted part is replaced - the rest of the line
            // has to survive.
            .replace(Regex("==(.*?)=="), "$1")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("`{1,3}(.*?)`{1,3}"), "$1")
            // [[Target|alias]] -> alias, [[Target]] -> Target
            .replace(Regex("\\[\\[([^\\[\\]|]+)(?:\\|([^\\[\\]\\n]+))?]]")) { m ->
                m.groupValues[2].ifBlank { m.groupValues[1] }.trim()
            }
            // ((blockId|alias)) -> alias, ((blockId)) -> blockId
            .replace(Regex("\\(\\(([^)|]+)(?:\\|([^)]+))?\\)\\)")) { m ->
                m.groupValues[2].ifBlank { m.groupValues[1] }.trim()
            }
            .replace(Regex("!\\[.*?\\]\\(.*?\\)"), "[Image]")
            .replace(Regex("\\[([^\\]]+)]\\(([^)]*)\\)"), "$1")
            .trim()
        if (cleanedLine.startsWith("|") && cleanedLine.endsWith("|")) {
            cleanedLine = cleanedLine.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("---") }
                .joinToString("   ")
        }
        if (cleanedLine.isNotBlank()) {
            cleaned.add(cleanedLine)
            if (cleaned.size >= maxLines) break
        }
    }
    return cleaned
}

// ---------------------------------------------------------------- templates --

/**
 * Template picker: tap a row to create a note from it, star to make it the
 * "Today" template, edit to change its body, delete to remove it. Variables in
 * the body are resolved at creation time by [personal.notes.touch.text.NoteTemplates].
 */
@Composable
private fun TemplatesDialog(
    templates: List<TemplateEntity>,
    onDismiss: () -> Unit,
    onUse: (TemplateEntity) -> Unit,
    onEdit: (TemplateEntity?) -> Unit,
    onSetDaily: (TemplateEntity) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
    onAddStarters: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Templates", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Tap a template to start a new note. Body variables: " +
                        "{{date}}, {{time}}, {{title}}, {{date:yyyy/MM}}.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (templates.isEmpty()) {
                    Text("No templates yet.", fontSize = 13.sp)
                    TextButton(onClick = onAddStarters) {
                        Text("Add starter templates", color = OrangePrimary)
                    }
                } else {
                    // A plain scrolling Column, not LazyColumn: an AlertDialog's
                    // text slot can measure children with an unbounded height and
                    // a lazy list would throw on that constraint.
                    Column(
                        modifier = Modifier
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        templates.forEach { template ->
                            TemplateRow(
                                template = template,
                                onUse = { onUse(template) },
                                onSetDaily = { onSetDaily(template) },
                                onEdit = { onEdit(template) },
                                onDelete = { onDelete(template) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEdit(null) }) {
                Text("New template", color = OrangePrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun TemplateRow(
    template: TemplateEntity,
    onUse: () -> Unit,
    onSetDaily: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUse)
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val preview = template.content.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.take(48)
                .orEmpty()
            Text(
                text = if (template.isDaily) "Daily template · $preview" else preview,
                fontSize = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onSetDaily, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = if (template.isDaily) "Daily template" else "Use for Today",
                tint = if (template.isDaily) OrangePrimary else Color(0xFFBDBDBD),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit template",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete template",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TemplateEditDialog(
    template: TemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, content: String, isDaily: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var content by remember { mutableStateOf(template?.content.orEmpty()) }
    var daily by remember { mutableStateOf(template?.isDaily ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (template == null) "New template" else "Edit template",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Body (markdown)") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 240.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = daily, onCheckedChange = { daily = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use for Today", fontSize = 13.sp)
                }
                Text(
                    "Variables: {{date}} {{time}} {{datetime}} {{title}} {{date:yyyy/MM}}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, content, daily) }
            ) {
                Text("Save", color = OrangePrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
