package personal.notes.touch.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.ui.theme.LocalThemeColor

/**
 * Phase-1 block editor (SiYuan flavour, touch edition).
 *
 * A LazyColumn of [EditableBlock] rows, each an individually styled text
 * field. All block state lives in [TextEditorViewModel] (derived from the
 * markdown source); this file only renders rows and routes gestures:
 *
 *  - Enter at end of a list/quote/task block continues that type (and splits
 *    paragraphs into two blocks) — text insertion path in the ViewModel.
 *  - Backspace at column 0 merges into the previous block / demotes markers.
 *  - "/" typed as the first character of a fresh or caret-at-start block opens
 *    the grouped slash insert sheet, anchored to the block row.
 *  - Selecting text inside a block reveals the [SelectionFormatBar].
 */
@Composable
fun BlockEditPane(
    viewModel: TextEditorViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val themeColor = LocalThemeColor.current
    val blocks by viewModel.blocks.collectAsState()
    val activeId by viewModel.activeBlockId.collectAsState()
    val pendingFocus by viewModel.pendingFocus.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    val activeBlock = blocks.firstOrNull { it.id == activeId }
    val hasSelection = activeBlock != null && !activeBlock.field.selection.collapsed

    LaunchedEffect(pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        var attempt = 0
        while (attempt < 5) {
            val requester = focusRequesters[target.blockId]
            if (requester != null) {
                runCatching { requester.requestFocus() }
                viewModel.consumePendingFocus()
                return@LaunchedEffect
            }
            attempt++
            withFrameNanos { }
        }
        // Target vanished (block deleted twice, etc.) — never crash, drop it.
        viewModel.consumePendingFocus()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (hasSelection && !isReadOnly) {
            SelectionFormatBar(viewModel = viewModel, themeColor = themeColor)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(blocks, key = { _, b -> b.id }) { _, block ->
                BlockRow(
                    block = block,
                    isActive = block.id == activeId,
                    isOnlyBlock = blocks.size == 1,
                    isReadOnly = isReadOnly,
                    viewModel = viewModel,
                    themeColor = themeColor,
                    focusRequesters = focusRequesters
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Block rows
// ---------------------------------------------------------------------------

/** Slash menu is not offered inside atomic fenced/table blocks. */
private val slashForbiddenTypes: Set<BlockType> =
    setOf(BlockType.CODE, BlockType.TABLE)

@Composable
private fun BlockRow(
    block: EditableBlock,
    isActive: Boolean,
    isOnlyBlock: Boolean,
    isReadOnly: Boolean,
    viewModel: TextEditorViewModel,
    themeColor: Color,
    focusRequesters: MutableMap<String, FocusRequester>
) {
    val focus = remember(block.id) { FocusRequester() }
    DisposableEffect(block.id) {
        focusRequesters[block.id] = focus
        onDispose { focusRequesters.remove(block.id) }
    }

    // Slash menu state: armed the moment a "/" is typed as this block's first
    // character (fresh block, or caret-at-start insert); disarmed once the text
    // no longer begins with "/" — cancelling keeps the literal "/".
    var slashArmed by remember { mutableStateOf(false) }
    val slashOpen = !isReadOnly &&
        isActive &&
        slashArmed &&
        block.field.text.startsWith("/") &&
        block.field.selection.end >= 1 &&
        block.type !in slashForbiddenTypes

    Box(modifier = Modifier.fillMaxWidth()) {
        when (block.type) {
            BlockType.DIVIDER -> {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                )
            }
            BlockType.CODE -> {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BlockTextField(
                        block = block,
                        viewModel = viewModel,
                        focusRequester = focus,
                        onSlashArmed = { slashArmed = it },
                        readOnly = isReadOnly,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
            BlockType.TABLE -> {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(themeColor.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Markdown table",
                                fontSize = 11.sp,
                                color = themeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        BlockTextField(
                            block = block,
                            viewModel = viewModel,
                            focusRequester = focus,
                            onSlashArmed = { slashArmed = it },
                            readOnly = isReadOnly,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }
            }
            BlockType.IMAGE -> {
                BlockTextField(
                    block = block,
                    viewModel = viewModel,
                    focusRequester = focus,
                    onSlashArmed = { slashArmed = it },
                    readOnly = isReadOnly,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = themeColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            BlockType.HEADING -> {
                val (size, weight) = headingStyle(block.headingLevel)
                BlockTextField(
                    block = block,
                    viewModel = viewModel,
                    focusRequester = focus,
                    onSlashArmed = { slashArmed = it },
                    readOnly = isReadOnly,
                    textStyle = TextStyle(
                        fontSize = size.sp,
                        fontWeight = weight,
                        lineHeight = (size + 6).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BlockType.BULLET, BlockType.NUMBERED -> {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (block.indent * 16).dp)
                ) {
                    Text(
                        text = if (block.type == BlockType.BULLET) "•" else "${block.order}.",
                        fontSize = if (block.type == BlockType.BULLET) 16.sp else 14.sp,
                        fontWeight = if (block.type == BlockType.BULLET) FontWeight.Bold else FontWeight.SemiBold,
                        color = themeColor,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .padding(top = 2.dp)
                    )
                    BlockTextField(
                        block = block,
                        viewModel = viewModel,
                        focusRequester = focus,
                        onSlashArmed = { slashArmed = it },
                        readOnly = isReadOnly,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            BlockType.TASK -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (block.indent * 16).dp)
                ) {
                    Checkbox(
                        checked = block.checked,
                        onCheckedChange = if (isReadOnly) null else { { viewModel.onToggleTaskChecked(block.id, it) } },
                        colors = CheckboxDefaults.colors(
                            checkedColor = themeColor,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BlockTextField(
                        block = block,
                        viewModel = viewModel,
                        focusRequester = focus,
                        onSlashArmed = { slashArmed = it },
                        readOnly = isReadOnly,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (block.checked) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            BlockType.QUOTE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(themeColor.copy(alpha = 0.08f))
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .background(themeColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BlockTextField(
                        block = block,
                        viewModel = viewModel,
                        focusRequester = focus,
                        onSlashArmed = { slashArmed = it },
                        readOnly = isReadOnly,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    )
                }
            }
            BlockType.PARAGRAPH -> {
                BlockTextField(
                    block = block,
                    viewModel = viewModel,
                    focusRequester = focus,
                    onSlashArmed = { slashArmed = it },
                    readOnly = isReadOnly,
                    isOnlyBlock = isOnlyBlock,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (slashOpen) {
            SlashMenuPopup {
                viewModel.applyBlockCommand(it)
            }
        }
    }
}

/** Heading text size (sp) + weight per level 1..6, mirroring the preview. */
private fun headingStyle(level: Int): Pair<Int, FontWeight> = when (level) {
    1 -> 22 to FontWeight.ExtraBold
    2 -> 19 to FontWeight.Bold
    3 -> 16 to FontWeight.SemiBold
    4 -> 15 to FontWeight.SemiBold
    5 -> 14 to FontWeight.Medium
    else -> 13 to FontWeight.Medium
}

/**
 * The one editable text field of a block. Routes value changes into the
 * ViewModel (which detects Enter), tracks the active block on focus, informs
 * the row about slash-menu arming, and intercepts the column-0 Backspace so it
 * can merge/demote instead of doing nothing (soft IMEs often don't emit the
 * key event; the merge also relies on it when they do).
 */
@Composable
private fun BlockTextField(
    block: EditableBlock,
    viewModel: TextEditorViewModel,
    focusRequester: FocusRequester,
    onSlashArmed: (Boolean) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isOnlyBlock: Boolean = false
) {
    BasicTextField(
        value = block.field,
        onValueChange = { value ->
            if (!readOnly) {
                if (value.text.startsWith("/") && !block.field.text.startsWith("/")) {
                    onSlashArmed(true)
                } else if (!value.text.startsWith("/")) {
                    onSlashArmed(false)
                }
                viewModel.onBlockValueChange(block.id, value)
            }
        },
        readOnly = readOnly,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .then(modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) viewModel.setActiveBlock(block.id)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace &&
                    block.field.selection.collapsed && block.field.selection.start == 0
                ) {
                    viewModel.onBackspaceAtBlockStart(block.id)
                } else {
                    false
                }
            },
        decorationBox = { innerField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isOnlyBlock && block.type == BlockType.PARAGRAPH && block.field.text.isEmpty()) {
                    Text(
                        text = "Write anything, or press '/' (slash) for blocks…",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
                innerField()
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Slash insert menu
// ---------------------------------------------------------------------------

/**
 * Grouped insert sheet anchored just under the active block row. Picks call
 * [TextEditorViewModel.applyBlockCommand]; the menu closes itself because the
 * picked text no longer starts with "/".
 */
@Composable
private fun SlashMenuPopup(
    onPick: (BlockCommand) -> Unit
) {
    Popup(
        alignment = Alignment.BottomStart,
        offset = IntOffset(0, 8),
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            )
        ) {
            Column(
                modifier = Modifier
                    .width(250.dp)
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
            ) {
                BlockCommand.slashGroups().forEach { (group, commands) ->
                    Text(
                        text = group.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    commands.forEach { command ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(command) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = slashGlyph(command),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(26.dp)
                            )
                            Text(
                                text = command.label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun slashGlyph(command: BlockCommand): String = when (command) {
    BlockCommand.H1 -> "H1"
    BlockCommand.H2 -> "H2"
    BlockCommand.H3 -> "H3"
    BlockCommand.H4 -> "H4"
    BlockCommand.H5 -> "H5"
    BlockCommand.H6 -> "H6"
    BlockCommand.BULLET -> "•"
    BlockCommand.NUMBERED -> "1."
    BlockCommand.TASK -> "☑"
    BlockCommand.QUOTE -> "❝"
    BlockCommand.DIVIDER -> "―"
    BlockCommand.CODE -> "{ }"
    BlockCommand.TABLE -> "⊞"
    BlockCommand.IMAGE -> "🖼"
    BlockCommand.TAG -> "#"
    BlockCommand.HIGHLIGHT -> "≡"
    BlockCommand.EMOJI -> "🙂"
}

// ---------------------------------------------------------------------------
// Selection formatting bar
// ---------------------------------------------------------------------------

/**
 * Small inline bar shown while text is selected inside the active block:
 * bold / italic / strike / highlight / color, writing markdown inline markers
 * (==…== for highlight, <font color="#hex">…</font> for color storage).
 */
@Composable
fun SelectionFormatBar(
    viewModel: TextEditorViewModel,
    themeColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            FormatChip(
                label = "B",
                bold = true,
                onClick = { viewModel.applyInlineToActiveBlock("**") }
            )
            FormatChip(
                label = "I",
                italic = true,
                onClick = { viewModel.applyInlineToActiveBlock("*") }
            )
            FormatChip(
                label = "S",
                strike = true,
                onClick = { viewModel.applyInlineToActiveBlock("~~") }
            )
            FormatChip(
                label = "==",
                onClick = { viewModel.applyInlineToActiveBlock("==") }
            )
            Spacer(modifier = Modifier.width(6.dp))
            listOf(
                Color(0xFF212121),           // black
                themeColor,                  // theme accent
                Color(0xFFD32F2F),           // red
                Color(0xFF1976D2)            // blue
            ).forEach { color ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { viewModel.applyColor(color) }
                )
            }
        }
    }
}

@Composable
private fun FormatChip(
    label: String,
    bold: Boolean = false,
    italic: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Outline pane (H1–H3 jump sheet)
// ---------------------------------------------------------------------------

/**
 * Outline of the current note as a bottom sheet: taps request a scroll to the
 * matching block index; empty state keeps the pane navigable when there is
 * nothing to list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    entries: List<OutlineEntry>,
    onEntryClick: (OutlineEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val themeColor = LocalThemeColor.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Outline",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (entries.isEmpty()) {
                Text(
                    text = "No headings yet.\nType “# Heading” or use the slash menu to add H1–H3 headings.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEach { entry ->
                        Text(
                            text = entry.text,
                            fontSize = if (entry.level == 1) 15.sp else 14.sp,
                            fontWeight = if (entry.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (entry.level == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(entry) }
                                .padding(
                                    start = (20 + (entry.level - 1) * 16).dp,
                                    top = 10.dp,
                                    bottom = 10.dp,
                                    end = 20.dp
                                )
                        )
                        if (entry.level == 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = themeColor.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}
