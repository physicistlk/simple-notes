package personal.notes.touch.ui.editor

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import personal.notes.touch.ui.theme.LocalThemeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    viewModel: TextEditorViewModel,
    onBackClick: () -> Unit
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
    val listState = rememberLazyListState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                    onOutlineClick = { showOutlineSheet = true }
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
                            onToggleChecklist = { lineIdx, checked ->
                                viewModel.toggleChecklist(lineIdx, checked)
                            },
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
                                    onToggleChecklist = { lineIdx, checked ->
                                        viewModel.toggleChecklist(lineIdx, checked)
                                    },
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
    onOutlineClick: () -> Unit
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
        ToolbarButton(text = "🖼 Image", onClick = {
            viewModel.applyBlockCommand(BlockCommand.IMAGE)
        })
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
    onToggleChecklist: (lineIndex: Int, isChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { RichTextParser.parseMarkdown(content) }
    val themeColor = LocalThemeColor.current

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
                    Text(
                        text = block.text,
                        fontSize = size.sp,
                        fontWeight = weight,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                is RichBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
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
                        Text(
                            text = block.text,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
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
                        Text(
                            text = block.text,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
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
                        Text(
                            text = block.text,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
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
                        Text(
                            text = block.text,
                            fontSize = 15.sp,
                            textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (block.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is RichBlock.Table -> {
                    BorderedEditorTable(rows = block.rows, themeColor = themeColor)
                }
                is RichBlock.Image -> {
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
