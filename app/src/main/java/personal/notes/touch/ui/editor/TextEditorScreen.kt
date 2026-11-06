package personal.notes.touch.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import personal.notes.touch.data.db.entity.NoteEntity
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.ui.theme.LocalThemeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    viewModel: TextEditorViewModel,
    onBackClick: () -> Unit,
    /** Opens another note (wiki link / backlink tap); defaults to a no-op. */
    onOpenNote: (String) -> Unit = {},
    /** Starts a two-pane workspace with the picked note in the other pane. */
    onOpenBeside: (String) -> Unit = {},
    /** Split-pane presentation: a compact header instead of the full top bar. */
    embedded: Boolean = false,
    /** Closes this pane (embedded only); null hides the control. */
    onClosePane: (() -> Unit)? = null,
    /** Gives this pane the whole width by closing the other (embedded only). */
    onMaximizePane: (() -> Unit)? = null
) {
    val themeColor = LocalThemeColor.current
    val note by viewModel.note.collectAsState()
    val title by viewModel.title.collectAsState()
    val contentValue by viewModel.contentValue.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val editorMode by viewModel.editorMode.collectAsState()
    val activeBlockId by viewModel.activeBlockId.collectAsState()
    val outline by viewModel.outline.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val wordStats by viewModel.wordStats.collectAsState()
    val showEmojiSheet by viewModel.showEmojiSheet.collectAsState()
    val backlinks by viewModel.backlinks.collectAsState()
    val outgoingRefs by viewModel.outgoingRefs.collectAsState()
    val outgoingBlockRefs by viewModel.outgoingBlockRefs.collectAsState()
    val blockBacklinks by viewModel.blockBacklinks.collectAsState()
    // key -> what the chip shows, and key -> note to open on tap. Both derive
    // from the saved index; a token typed a second ago renders its alias until
    // the debounced save catches up.
    val blockRefLabels = remember(outgoingBlockRefs) {
        outgoingBlockRefs.associate {
            it.targetKey to personal.notes.touch.text.BlockRefs.displayLabel(
                it.targetAlias,
                it.targetText,
                it.targetKey
            )
        }
    }
    val blockRefNotes = remember(outgoingBlockRefs) {
        // Only a live note is tappable: a block whose note sits in the recycle
        // bin still renders its text, but there is nothing to open.
        outgoingBlockRefs.mapNotNull { ref ->
            if (ref.targetTitle == null) return@mapNotNull null
            ref.targetNoteId?.let { ref.targetKey to it }
        }.toMap()
    }
    val listState = rememberLazyListState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showReferences by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showOpenBeside by remember { mutableStateOf(false) }
    val noteChoices by viewModel.noteChoices.collectAsState()
    val linkDraft by viewModel.linkDraft.collectAsState()
    val imagePickRequest by viewModel.imagePickRequest.collectAsState()

    // SAF photo picker: no storage permission, the URI carries a read grant.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val fileName = viewModel.importImage(uri)
                if (fileName != null) {
                    viewModel.insertImageBlock(fileName)
                } else {
                    Toast.makeText(context, "Could not import image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(imagePickRequest) {
        if (imagePickRequest > 0L) {
            viewModel.consumeImagePickRequest()
            imagePicker.launch("image/*")
        }
    }

    // Lifecycle flush: the 1s debounce covers typing; ON_PAUSE/ON_STOP make a
    // hard app-kill keep the latest markdown (Phase-1 persistence contract).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.saveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showOpenBeside) {
        OpenBesideDialog(
            notes = noteChoices,
            onDismiss = { showOpenBeside = false },
            onPick = { id ->
                showOpenBeside = false
                onOpenBeside(id)
            }
        )
    }

    val exportTarget = note
    if (showExportDialog && exportTarget != null) {
        personal.notes.touch.ui.export.ExportDialog(
            note = exportTarget,
            repository = viewModel.repository,
            fileStore = viewModel.fileStore,
            currentBitmap = null,
            currentPageIndex = 0,
            onDismiss = { showExportDialog = false }
        )
    }

    Scaffold(
        topBar = {
            if (embedded) {
                // A split pane cannot afford the full top bar (title block,
                // three mode tabs, four actions). The compact header keeps the
                // title, a one-tap mode cycle and everything else behind “⋮”.
                SplitPaneHeader(
                    title = title,
                    isSaving = isSaving,
                    isReadOnly = isReadOnly,
                    editorMode = editorMode,
                    themeColor = themeColor,
                    onClosePane = onClosePane,
                    onCycleMode = {
                        viewModel.setEditorMode(
                            when (editorMode) {
                                EditorMode.EDIT -> EditorMode.SPLIT
                                EditorMode.SPLIT -> EditorMode.PREVIEW
                                EditorMode.PREVIEW -> EditorMode.EDIT
                            }
                        )
                    },
                    onOpenBeside = {
                        viewModel.saveNow()
                        showOpenBeside = true
                    },
                    onMaximizePane = onMaximizePane,
                    onToggleReadOnly = { viewModel.toggleReadOnly() },
                    onReferences = {
                        viewModel.saveNow()
                        showReferences = true
                    },
                    onExport = {
                        viewModel.saveNow()
                        showExportDialog = true
                    },
                    onSave = { viewModel.saveNow() }
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.saveNow { onBackClick() }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = if (title.isBlank()) "Untitled Note" else title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isReadOnly && isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                val statusText = if (isReadOnly) "Read-only" else if (isSaving) "Saving..." else "Auto-saved (1s)"
                                Text(
                                    text = "${wordStats.wordCount} words · ${wordStats.charCount} chars · $statusText",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    },
                    actions = {
                        // Mode Switcher: Edit, Split, Preview
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModeTabButton(
                                label = "Edit",
                                isSelected = editorMode == EditorMode.EDIT,
                                themeColor = themeColor,
                                onClick = { viewModel.setEditorMode(EditorMode.EDIT) }
                            )
                            ModeTabButton(
                                label = "Split",
                                isSelected = editorMode == EditorMode.SPLIT,
                                themeColor = themeColor,
                                onClick = { viewModel.setEditorMode(EditorMode.SPLIT) }
                            )
                            ModeTabButton(
                                label = "Preview",
                                isSelected = editorMode == EditorMode.PREVIEW,
                                themeColor = themeColor,
                                onClick = { viewModel.setEditorMode(EditorMode.PREVIEW) }
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(onClick = { viewModel.toggleReadOnly() }) {
                            Icon(
                                imageVector = if (isReadOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isReadOnly) "Read-only mode" else "Editable mode",
                                tint = if (isReadOnly) Color(0xFFFFD54F) else Color.White
                            )
                        }

                        // Two-pane workspace: this note + one picked from a searchable list.
                        IconButton(onClick = {
                            viewModel.saveNow()
                            showOpenBeside = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.VerticalSplit,
                                contentDescription = "Open beside",
                                tint = Color.White
                            )
                        }

                        // References: backlinks + outgoing wiki links (schema 7 index).
                        IconButton(onClick = {
                            viewModel.saveNow()
                            showReferences = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "References",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            viewModel.saveNow()
                            showExportDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Note",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = { viewModel.saveNow() }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save Note",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeColor
                    )
            )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Formatting Toolbar (Visible in EDIT and SPLIT modes when not read-only)
            if (editorMode != EditorMode.PREVIEW && !isReadOnly) {
                // In the block editor the toolbar transforms the ACTIVE block;
                // in Split (no focused block) it falls back to the raw-source
                // helpers so old behaviour is preserved.
                val blockCommandsActive = activeBlockId != null && editorMode == EditorMode.EDIT
                FormattingToolbar(
                    viewModel = viewModel,
                    themeColor = themeColor,
                    blockCommandsActive = blockCommandsActive,
                    onOutlineClick = { showOutlineSheet = true },
                    onPickImage = { viewModel.requestImagePick() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            // Note Title input field
            if (editorMode != EditorMode.PREVIEW) {
                TextField(
                    value = title,
                    onValueChange = { if (!isReadOnly) viewModel.onTitleChange(it) },
                    readOnly = isReadOnly,
                    placeholder = {
                        Text(
                            text = "Note Title...",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            }

            // Editor / Preview Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (editorMode) {
                    EditorMode.EDIT -> {
                        // Phase-1 block editor: one styled field per block.
                        BlockEditPane(
                            viewModel = viewModel,
                            listState = listState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    EditorMode.PREVIEW -> {
                        PreviewPane(
                            title = title,
                            content = contentValue.text,
                            noteId = viewModel.noteId,
                            fileStore = viewModel.fileStore,
                            onToggleChecklist = { lineIdx, checked ->
                                viewModel.toggleChecklist(lineIdx, checked)
                            },
                            onWikiClick = { wikiTitle ->
                                viewModel.noteIdForTitle(wikiTitle)?.let(onOpenNote)
                            },
                            blockRefLabels = blockRefLabels,
                            onBlockRefClick = { key -> blockRefNotes[key]?.let(onOpenNote) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                    EditorMode.SPLIT -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left: raw markdown source (legacy editing surface)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(16.dp)
                            ) {
                                EditPane(
                                    value = contentValue,
                                    onValueChange = { viewModel.onContentChange(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            )

                            // Right: Live Rich Preview
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(16.dp)
                            ) {
                                PreviewPane(
                                    title = title,
                                    content = contentValue.text,
                                    noteId = viewModel.noteId,
                                    fileStore = viewModel.fileStore,
                                    onToggleChecklist = { lineIdx, checked ->
                                        viewModel.toggleChecklist(lineIdx, checked)
                                    },
                                    onWikiClick = { wikiTitle ->
                                        viewModel.noteIdForTitle(wikiTitle)?.let(onOpenNote)
                                    },
                                    blockRefLabels = blockRefLabels,
                                    onBlockRefClick = { key -> blockRefNotes[key]?.let(onOpenNote) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOutlineSheet) {
        OutlineSheet(
            entries = outline,
            onEntryClick = { entry ->
                showOutlineSheet = false
                coroutineScope.launch {
                    runCatching { listState.animateScrollToItem(entry.blockIndex) }
                }
            },
            onDismiss = { showOutlineSheet = false }
        )
    }

    if (showEmojiSheet) {
        EmojiPickerSheet(
            onSelectEmoji = { emoji ->
                viewModel.insertEmoji(emoji)
                viewModel.setShowEmojiSheet(false)
            },
            onDismiss = { viewModel.setShowEmojiSheet(false) }
        )
    }

    if (showReferences) {
        ReferencesSheet(
            noteTitle = title.ifBlank { "Untitled Note" },
            backlinks = backlinks,
            outgoing = outgoingRefs,
            blockOutgoing = outgoingBlockRefs,
            blockBacklinks = blockBacklinks,
            onOpenNote = { id ->
                showReferences = false
                onOpenNote(id)
            },
            onDismiss = { showReferences = false }
        )
    }

    val draft = linkDraft
    if (draft != null) {
        LinkDialog(
            initialLabel = draft.label,
            onConfirm = { label, url -> viewModel.applyLink(label, url) },
            onDismiss = { viewModel.dismissLinkDialog() }
        )
    }
}

@Composable
private fun ModeTabButton(
    label: String,
    isSelected: Boolean,
    themeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color.White else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) themeColor else Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FormattingToolbar(
    viewModel: TextEditorViewModel,
    themeColor: Color,
    blockCommandsActive: Boolean,
    onOutlineClick: () -> Unit,
    onPickImage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group 1: Text Styles — routed to the active block when one is focused
        ToolbarButton(
            text = "B", bold = true,
            onClick = { viewModel.applyInlineToActiveBlock("**") }
        )
        ToolbarButton(
            text = "I", italic = true,
            onClick = { viewModel.applyInlineToActiveBlock("*") }
        )
        ToolbarButton(
            text = "U", underline = true,
            onClick = { viewModel.applyInlineToActiveBlock("<u>", "</u>") }
        )
        ToolbarButton(
            text = "S", strike = true,
            onClick = { viewModel.applyInlineToActiveBlock("~~") }
        )
        ToolbarButton(
            text = "==",
            onClick = { viewModel.applyInlineToActiveBlock("==") }
        )
        ToolbarButton(
            text = "`code`", mono = true,
            onClick = { viewModel.applyInlineToActiveBlock("`") }
        )
        ToolbarButton(
            text = "🔗 Link",
            onClick = { viewModel.openLinkDialog() }
        )
        ToolbarButton(
            text = "[[ Note]]",
            onClick = { viewModel.insertWikiBracket() }
        )

        ToolbarDivider()

        // Group 2: Headings (block transforms; raw fallback in Split mode)
        ToolbarButton(text = "H1", bold = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.H1) else viewModel.applyH1()
        })
        ToolbarButton(text = "H2", bold = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.H2) else viewModel.applyH2()
        })
        ToolbarButton(text = "H3", bold = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.H3) else viewModel.applyH3()
        })
        ToolbarButton(text = "H4", bold = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.H4) else viewModel.applyLinePrefix("#### ")
        })
        ToolbarButton(text = "H5", bold = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.H5) else viewModel.applyLinePrefix("##### ")
        })
        ToolbarButton(text = "H6", bold = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.H6) else viewModel.applyLinePrefix("###### ")
        })

        ToolbarDivider()

        // Group 3: Lists & Quotes & Blocks
        ToolbarButton(text = "• Bullet", onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.BULLET) else viewModel.applyBulletList()
        })
        ToolbarButton(text = "1. Number", onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.NUMBERED) else viewModel.applyNumberedList()
        })
        ToolbarButton(text = "☑ Check", onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.TASK) else viewModel.applyChecklist()
        })
        ToolbarButton(text = "⇤", onClick = { viewModel.outdentActiveBlock() })
        ToolbarButton(text = "⇥", onClick = { viewModel.indentActiveBlock() })
        ToolbarButton(text = "❝ Quote", onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.QUOTE) else viewModel.applyQuote()
        })
        ToolbarButton(text = "``` Block", mono = true, onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.CODE) else viewModel.insertCodeBlock()
        })
        ToolbarButton(text = "― Line", onClick = {
            if (blockCommandsActive) viewModel.applyBlockCommand(BlockCommand.DIVIDER) else viewModel.insertDivider()
        })
        ToolbarButton(text = "⊞ Table", onClick = {
            viewModel.applyBlockCommand(BlockCommand.TABLE)
        })
        ToolbarButton(text = "🖼 Image", onClick = onPickImage)
        ToolbarButton(text = "# Tag", onClick = {
            viewModel.applyBlockCommand(BlockCommand.TAG)
        })
        ToolbarButton(text = "🙂 Emoji", onClick = {
            viewModel.setShowEmojiSheet(true)
        })

        ToolbarDivider()

        // Group 4: Text Color Swatches
        val colors = listOf(
            Color(0xFF212529) to "Black",
            Color(0xFF6C757D) to "Gray",
            Color(0xFFD32F2F) to "Red",
            Color(0xFF1976D2) to "Blue",
            Color(0xFF388E3C) to "Green",
            themeColor to "Theme"
        )
        colors.forEach { (color, _) ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.LightGray, CircleShape)
                    .clickable { viewModel.applyColor(color) }
            )
        }

        ToolbarDivider()

        // Group 5: Outline (Phase-1 P1.7)
        ToolbarButton(text = "☰ Outline", onClick = onOutlineClick)
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    mono: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            textDecoration = when {
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> TextDecoration.None
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            .padding(horizontal = 2.dp)
    )
}

@Composable
private fun EditPane(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(
                    text = "Markdown source…",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
            innerTextField()
        }
    )
}

@Composable
private fun PreviewPane(
    title: String,
    content: String,
    noteId: String,
    fileStore: NoteFileStore,
    onToggleChecklist: (lineIndex: Int, isChecked: Boolean) -> Unit,
    onWikiClick: (String) -> Unit = {},
    blockRefLabels: Map<String, String> = emptyMap(),
    onBlockRefClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val blocks = remember(content, blockRefLabels) {
        RichTextParser.parseMarkdown(content) { key -> blockRefLabels[key] }
    }
    val themeColor = LocalThemeColor.current
    val context = LocalContext.current
    val onLinkClick: (String) -> Unit = { url -> LinkTarget.open(context, url) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title.isNotBlank()) {
            item {
                Text(
                    text = title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider(color = themeColor.copy(alpha = 0.3f), thickness = 2.dp)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (blocks.isEmpty() || (blocks.size == 1 && blocks[0] is RichBlock.Paragraph && (blocks[0] as RichBlock.Paragraph).text.isEmpty())) {
            item {
                Text(
                    text = "Empty Note. Switch to Edit mode to write content.",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        itemsIndexed(blocks) { _, block ->
            when (block) {
                is RichBlock.Header -> {
                    val (size, weight) = when (block.level) {
                        1 -> 22 to FontWeight.ExtraBold
                        2 -> 19 to FontWeight.Bold
                        3 -> 16 to FontWeight.SemiBold
                        4 -> 15 to FontWeight.SemiBold
                        5 -> 14 to FontWeight.Medium
                        else -> 13 to FontWeight.Medium
                    }
                    RichTextBlock(
                        text = block.text,
                        style = TextStyle(
                            fontSize = size.sp,
                            fontWeight = weight,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick,
                        onWikiClick = onWikiClick, onBlockRefClick = onBlockRefClick
                    )
                }
                is RichBlock.Paragraph -> {
                    RichTextBlock(
                        text = block.text,
                        style = TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onLinkClick = onLinkClick,
                        onWikiClick = onWikiClick, onBlockRefClick = onBlockRefClick
                    )
                }
                is RichBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(themeColor.copy(alpha = 0.08f))
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(themeColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        RichTextBlock(
                            text = block.text,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                            onLinkClick = onLinkClick,
                            onWikiClick = onWikiClick, onBlockRefClick = onBlockRefClick
                        )
                    }
                }
                is RichBlock.BulletItem -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(start = (8 + block.indent * 16).dp)
                    ) {
                        Text(
                            text = "•",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        RichTextBlock(
                            text = block.text,
                            style = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            onLinkClick = onLinkClick,
                            onWikiClick = onWikiClick, onBlockRefClick = onBlockRefClick
                        )
                    }
                }
                is RichBlock.NumberedItem -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(start = (8 + block.indent * 16).dp)
                    ) {
                        Text(
                            text = "${block.number}.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themeColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        RichTextBlock(
                            text = block.text,
                            style = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            onLinkClick = onLinkClick,
                            onWikiClick = onWikiClick, onBlockRefClick = onBlockRefClick
                        )
                    }
                }
                is RichBlock.ChecklistItem -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onToggleChecklist(block.lineIndex, !block.isChecked)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = block.isChecked,
                            onCheckedChange = { checked ->
                                onToggleChecklist(block.lineIndex, checked)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = themeColor,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        RichTextBlock(
                            text = block.text,
                            style = TextStyle(
                                fontSize = 15.sp,
                                textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (block.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
                            ),
                            onLinkClick = onLinkClick,
                            onWikiClick = onWikiClick, onBlockRefClick = onBlockRefClick
                        )
                    }
                }
                is RichBlock.Table -> {
                    BorderedEditorTable(rows = block.rows, themeColor = themeColor)
                }
                is RichBlock.Image -> {
                    val localFile = remember(block.url, noteId) {
                        fileStore.getNoteAsset(noteId, block.url)
                    }
                    if (localFile != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NoteImage(file = localFile, contentDescription = block.alt.ifBlank { "Image" })
                            if (block.alt.isNotBlank()) {
                                Text(
                                    text = block.alt,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = block.alt,
                                    tint = themeColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = block.alt.ifBlank { "Image" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (block.url.isNotBlank()) {
                                    Text(
                                        text = block.url,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                is RichBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                }
                is RichBlock.CodeBlock -> {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Bordered 2.5D-lite markdown table render (P1.4): separator rows become the
 *  header divider; long cells wrap left-aligned inside their column. */
@Composable
private fun BorderedEditorTable(rows: List<String>, themeColor: Color) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val dataRows = rows.filter { it.isNotBlank() }
    if (dataRows.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, gridColor, RoundedCornerShape(4.dp))
    ) {
        dataRows.forEachIndexed { index, row ->
            val cells = row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            // GFM separator rows are dashes like "---" with optional colons; the
            // first real row above them renders as the bold header.
            val isSeparatorRow = cells.isNotEmpty() &&
                cells.all { it.matches(Regex("""^:?-{1,}:?$""")) }
            if (!isSeparatorRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index == 0) themeColor.copy(alpha = 0.10f)
                            else Color.Transparent
                        )
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = cell,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                HorizontalDivider(color = gridColor, thickness = 0.7.dp)
            }
        }
    }
}

/**
 * Categorized emoji picker sheet for quick inline emoji insertion (SiYuan emoji support).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onSelectEmoji: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf(
        "Smileys" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘"),
        "Gestures" to listOf("👍", "👎", "👏", "🙌", "🤝", "✍️", "🙏", "✌️", "🤞", "🤟", "🤘", "👌", "🤌", "👈", "👉", "👆"),
        "Work & Notes" to listOf("📝", "📌", "📍", "📎", "🏷️", "📅", "📆", "⏰", "⏱️", "⌛", "📊", "📈", "📉", "📁", "📂", "📄"),
        "Status & Symbols" to listOf("✅", "❌", "⚠️", "❓", "❗", "💡", "⚡", "🔥", "⭐", "🌟", "💥", "🎯", "🚀", "🛑", "🟢", "🔴"),
        "Tech & Tools" to listOf("💻", "🖥️", "📱", "⌨️", "💾", "💿", "🖨️", "🔧", "🔨", "⚙️", "🔒", "🔓", "🔑", "🛡️", "🌐", "📦")
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Insert Emoji",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                categories.forEach { (catName, emojis) ->
                    Text(
                        text = catName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojis.forEach { emoji ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectEmoji(emoji) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rich text with tappable inline links: annotations are attached by
 * [RichTextParser.parseInline] under [RichTextParser.LINK_TAG].
 */
@Composable
private fun RichTextBlock(
    text: AnnotatedString,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    onWikiClick: (String) -> Unit = {},
    onBlockRefClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            val blockRef = text.getStringAnnotations(
                RichTextParser.BLOCK_REF_TAG, offset, offset
            ).firstOrNull()
            val wiki = text.getStringAnnotations(RichTextParser.WIKI_TAG, offset, offset)
                .firstOrNull()
            when {
                blockRef != null -> onBlockRefClick(blockRef.item)
                wiki != null -> onWikiClick(wiki.item)
                else -> text.getStringAnnotations(RichTextParser.LINK_TAG, offset, offset)
                    .firstOrNull()
                    ?.let { annotation -> onLinkClick(annotation.item) }
            }
        }
    )
}

/** Minimal link dialog: label + URL, validates before closing. */
@Composable
private fun LinkDialog(
    initialLabel: String,
    onConfirm: (String, String) -> Boolean,
    onDismiss: () -> Unit
) {
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert link") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { message ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(message, fontSize = 12.sp, color = Color(0xFFD32F2F))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onConfirm(label, url)) {
                    error = "Enter a valid http(s):// or mailto: link"
                }
            }) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Split-pane chrome + "open beside" picker
// ---------------------------------------------------------------------------

/**
 * Compact header for one pane of [SplitWorkspaceScreen]. The full top bar
 * (title block, three mode tabs, four actions) does not fit half a tablet, so
 * this keeps the title, a one-tap mode cycle, and hides the rest behind “⋮”.
 */
@Composable
private fun SplitPaneHeader(
    title: String,
    isSaving: Boolean,
    isReadOnly: Boolean,
    editorMode: EditorMode,
    themeColor: Color,
    onClosePane: (() -> Unit)?,
    onCycleMode: () -> Unit,
    onOpenBeside: () -> Unit,
    onMaximizePane: (() -> Unit)?,
    onToggleReadOnly: () -> Unit,
    onReferences: () -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(color = themeColor, shadowElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 4.dp)
        ) {
            if (onClosePane != null) {
                IconButton(onClick = onClosePane, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close pane",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = title.ifBlank { "Untitled Note" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isReadOnly -> "Read-only"
                        isSaving -> "Saving…"
                        else -> "Pane · ${editorMode.name.lowercase()}"
                    },
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            IconButton(onClick = onCycleMode, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = when (editorMode) {
                        EditorMode.EDIT -> Icons.Default.Edit
                        EditorMode.SPLIT -> Icons.Default.VerticalSplit
                        EditorMode.PREVIEW -> Icons.Default.Visibility
                    },
                    contentDescription = "Switch editor mode",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(17.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Pane actions",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    PaneMenuItem("Open beside…") { menuOpen = false; onOpenBeside() }
                    if (onMaximizePane != null) {
                        PaneMenuItem("Maximize pane") { menuOpen = false; onMaximizePane() }
                    }
                    PaneMenuItem(if (isReadOnly) "Editable mode" else "Read-only mode") {
                        menuOpen = false
                        onToggleReadOnly()
                    }
                    PaneMenuItem("References") { menuOpen = false; onReferences() }
                    PaneMenuItem("Export") { menuOpen = false; onExport() }
                    PaneMenuItem("Save now") { menuOpen = false; onSave() }
                    if (onClosePane != null) {
                        PaneMenuItem("Close pane") { menuOpen = false; onClosePane() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 13.sp) },
        onClick = onClick
    )
}

/**
 * Searchable note picker for "Open beside". Only text notes are offered: a
 * drawing note's canvas owns the full screen and cannot be embedded in a pane
 * yet, so showing it here would promise something the split cannot deliver.
 */
@Composable
private fun OpenBesideDialog(
    notes: List<NoteEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val filtered = remember(notes, query) {
        val needle = query.trim()
        notes
            .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open beside", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Pick a text note for the other pane. Drawing notes still open full screen.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search notes", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = if (notes.isEmpty()) {
                            "No other text notes yet. Create one first."
                        } else {
                            "No note matches \"${query.trim()}\"."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        itemsIndexed(filtered, key = { _, n -> n.id }) { _, note ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(note.id) }
                                    .padding(vertical = 9.dp)
                            ) {
                                Text(
                                    text = note.title.ifBlank { "Untitled Note" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dateFormat.format(Date(note.updatedAt)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
