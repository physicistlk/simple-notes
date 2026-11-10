package personal.notes.touch.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import personal.notes.touch.data.db.dao.BlockCandidate
import personal.notes.touch.text.BlockRefs
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.text.BlockCards
import personal.notes.touch.text.WikiLinks
import personal.notes.touch.ui.theme.LocalThemeColor
import personal.notes.touch.util.ClipboardHelper

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
    modifier: Modifier = Modifier,
    onOpenNote: (String) -> Unit = {}
) {
    val themeColor = LocalThemeColor.current
    val blocks by viewModel.blocks.collectAsState()
    val activeId by viewModel.activeBlockId.collectAsState()
    val pendingFocus by viewModel.pendingFocus.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    val activeBlock = blocks.firstOrNull { it.id == activeId }
    val hasSelection = activeBlock != null && !activeBlock.field.selection.collapsed

    val noteIndex by viewModel.noteIndex.collectAsState()
    val wikiTitles = remember(noteIndex) { noteIndex.map { it.title } }
    // A note linking to itself is never useful; the popup hides the open note.
    val selfTitle by viewModel.title.collectAsState()

    val noteChoices by viewModel.noteChoices.collectAsState()
    var menuBlockId by remember { mutableStateOf<String?>(null) }
    val menuBlock = blocks.firstOrNull { it.id == menuBlockId }
    var transferBlockId by remember { mutableStateOf<String?>(null) }
    var transferMove by remember { mutableStateOf(true) }
    val transferBlock = blocks.firstOrNull { it.id == transferBlockId }
    var cardBlockId by remember { mutableStateOf<String?>(null) }
    val cardBlock = blocks.firstOrNull { it.id == cardBlockId }
    LaunchedEffect(menuBlockId, blocks) {
        if (menuBlockId != null && blocks.none { it.id == menuBlockId }) menuBlockId = null
    }
    val context = LocalContext.current

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

        // Heading folding: a collapsed heading hides every block below it until
        // the next heading of equal or higher level (pure logic in BlockStructureOps).
        val visibleBlocks = remember(blocks) { BlockStructureOps.visibleBlocks(blocks) }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(visibleBlocks, key = { _, b -> b.id }) { _, block ->
                BlockRow(
                    block = block,
                    isActive = block.id == activeId,
                    isOnlyBlock = visibleBlocks.size == 1,
                    isReadOnly = isReadOnly,
                    viewModel = viewModel,
                    themeColor = themeColor,
                    focusRequesters = focusRequesters,
                    wikiTitles = wikiTitles,
                    selfTitle = selfTitle,
                    onOpenNote = onOpenNote,
                    onOpenMenu = { menuBlockId = it }
                )
            }
        }
    }

    if (menuBlock != null) {
        BlockMenuSheet(
            block = menuBlock,
            onDismiss = { menuBlockId = null },
            onTransform = { type, level ->
                viewModel.transformBlock(menuBlock.id, type, level)
                menuBlockId = null
            },
            onIndent = { viewModel.indentBlock(menuBlock.id); menuBlockId = null },
            onOutdent = { viewModel.outdentBlock(menuBlock.id); menuBlockId = null },
            onMoveUp = { viewModel.moveBlock(menuBlock.id, -1); menuBlockId = null },
            onMoveDown = { viewModel.moveBlock(menuBlock.id, 1); menuBlockId = null },
            onDuplicate = { viewModel.duplicateBlock(menuBlock.id); menuBlockId = null },
            onCopy = {
                val markdown = viewModel.blockMarkdown(menuBlock.id)
                val copied = markdown != null && ClipboardHelper.copyText(context, markdown, "Block")
                Toast.makeText(
                    context,
                    if (copied) "Block copied" else "Block is empty",
                    Toast.LENGTH_SHORT
                ).show()
                menuBlockId = null
            },
            onAddToDeck = {
                cardBlockId = menuBlock.id
                menuBlockId = null
            },
            onMoveToNote = {
                transferBlockId = menuBlock.id
                transferMove = true
                menuBlockId = null
            },
            onCopyToNote = {
                transferBlockId = menuBlock.id
                transferMove = false
                menuBlockId = null
            },
            onDelete = { viewModel.deleteBlock(menuBlock.id); menuBlockId = null }
        )
    }

    if (transferBlock != null) {
        TransferBlockDialog(
            move = transferMove,
            targets = noteChoices,
            onDismiss = { transferBlockId = null },
            onPick = { targetId ->
                val id = transferBlock.id
                val move = transferMove
                transferBlockId = null
                viewModel.transferBlock(id, targetId, move) { count ->
                    Toast.makeText(
                        context,
                        if (count > 0) {
                            (if (move) "Moved " else "Copied ") + "$count block(s)"
                        } else {
                            "Could not transfer block"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    if (cardBlock != null) {
        AddBlockCardDialog(
            block = cardBlock,
            noteTitle = selfTitle,
            onDismiss = { cardBlockId = null },
            onConfirm = { front, back, deck ->
                val id = cardBlock.id
                cardBlockId = null
                viewModel.addBlockCard(id, front, back, deck) { result ->
                    Toast.makeText(
                        context,
                        if (result.created) {
                            "Card added to ${result.card.cName}"
                        } else {
                            "Card updated in ${result.card.cName}"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

/**
 * Flashcard prefill for one block. `front :: back` in the note is suggested
 * automatically; a block without the separator still yields a card whose
 * answer the user fills in here. Editing the fields does not rewrite the note —
 * the block keeps its markdown, the card keeps its own text.
 */
@Composable
private fun AddBlockCardDialog(
    block: EditableBlock,
    noteTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (front: String, back: String, deck: String) -> Unit
) {
    val initial = remember(block.id) { BlockCards.split(block.field.text) }
    var front by remember(block.id) { mutableStateOf(initial.front) }
    var back by remember(block.id) { mutableStateOf(initial.back) }
    var deck by remember(block.id) { mutableStateOf(BlockCards.deckName(noteTitle)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add to deck", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (initial.hasSeparator) {
                        "Split from the block at `::`. Edit either side if needed."
                    } else {
                        "Tip: write `question :: answer` in the block to split it automatically."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text("Question") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text("Answer") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deck,
                    onValueChange = { deck = it },
                    label = { Text("Deck") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = front.isNotBlank(),
                onClick = { onConfirm(front, back, deck) }
            ) {
                Text(
                    "Save card",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TransferBlockDialog(
    move: Boolean,
    targets: List<NoteEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, targets) {
        if (query.isBlank()) targets
        else targets.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (move) "Move block to note" else "Copy block to note", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (move) {
                        "The block and indented descendants keep their block ids across notes."
                    } else {
                        "The block and indented descendants are copied with new ids."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "No other text notes",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(filtered.size, key = { filtered[it].id }) { index ->
                            val target = filtered[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(target.id) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = target.title.ifBlank { "Untitled Note" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (target.pageType == "text") "Text note" else "Drawing note",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
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

// ---------------------------------------------------------------------------
// Block rows
// ---------------------------------------------------------------------------

/** Slash menu is not offered inside atomic fenced/table blocks. */
private val slashForbiddenTypes: Set<BlockType> =
    setOf(BlockType.CODE, BlockType.TABLE, BlockType.QUERY)

/** Vertical travel (in dp) that moves a dragged block by one position. */
private val REORDER_STEP = 44.dp

@Composable
private fun BlockRow(
    block: EditableBlock,
    isActive: Boolean,
    isOnlyBlock: Boolean,
    isReadOnly: Boolean,
    viewModel: TextEditorViewModel,
    themeColor: Color,
    focusRequesters: MutableMap<String, FocusRequester>,
    wikiTitles: List<String>,
    selfTitle: String,
    onOpenMenu: (String) -> Unit,
    onOpenNote: (String) -> Unit = {}
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

    // `[[` completion: an opened, unterminated wiki link at the caret.
    val wikiPending = if (!isReadOnly && isActive) {
        WikiLinks.pendingAt(block.field.text, block.field.selection.end)
    } else {
        null
    }
    val wikiMatches = remember(wikiPending?.query, wikiTitles, selfTitle) {
        if (wikiPending == null) {
            emptyList()
        } else {
            WikiLinks.suggestions(wikiPending.query, wikiTitles, exclude = selfTitle)
        }
    }
    // `((` completion: an opened, unterminated block reference at the caret.
    val blockRefPending = if (!isReadOnly && isActive) {
        BlockRefs.pendingAt(block.field.text, block.field.selection.end)
    } else {
        null
    }
    val refQuery = blockRefPending?.query
    val refMatches by produceState(emptyList<BlockCandidate>(), refQuery, block.id) {
        value = if (refQuery == null) {
            emptyList()
        } else {
            viewModel.searchBlocks(refQuery, block.id).first()
        }
    }

    val slashOpen = !isReadOnly &&
        isActive &&
        slashArmed &&
        block.field.text.startsWith("/") &&
        block.field.selection.end >= 1 &&
        block.type !in slashForbiddenTypes

    val queryCandidates by viewModel.queryCandidates.collectAsState()
    val queryResults = remember(block.field.text, queryCandidates) {
        if (block.type == BlockType.QUERY) {
            viewModel.resultsForQuery(block.field.text, block.id)
        } else {
            emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isReadOnly) {
                // Gutter handle, one gesture owner for both actions so neither
                // steals the pointer from the other:
                //  * tap (down + up before the long-press timeout) opens the
                //    block menu;
                //  * hold still, then drag up/down reorders, one block per
                //    `ReorderDrag` bucket (routes vary in height, so there is
                //    no row measurement);
                //  * moving before the timeout is left alone so the LazyColumn
                //    still scrolls normally from the gutter.
                val dragStepPx = with(LocalDensity.current) { REORDER_STEP.toPx() }
                Text(
                    text = "⋮",
                    fontSize = 16.sp,
                    // Faint gutter at rest, clearer on the block being edited;
                    // the space is always reserved so text never jumps.
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (isActive) 0.55f else 0.22f),
                    modifier = Modifier
                        .width(22.dp)
                        .pointerInput(block.id, dragStepPx) {
                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var released = false
                                var movedAway = false
                                val heldStill = withTimeoutOrNull(longPressTimeout) {
                                    var travel = 0f
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes
                                            .firstOrNull { it.id == down.id }
                                            ?: return@withTimeoutOrNull false
                                        if (!change.pressed) {
                                            released = true
                                            return@withTimeoutOrNull false
                                        }
                                        travel += change.positionChange().getDistance()
                                        if (travel > slop) {
                                            movedAway = true
                                            return@withTimeoutOrNull false
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE") false
                                }
                                when {
                                    heldStill == null -> {
                                        // Long press reached: reorder while held.
                                        val reorder = ReorderDrag(dragStepPx)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            val step = reorder.add(change.positionChange().y)
                                            if (step != 0) viewModel.moveBlock(block.id, step)
                                        }
                                    }
                                    released -> onOpenMenu(block.id)
                                    movedAway -> Unit
                                }
                            }
                        }
                        .padding(top = 1.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when (block.type) {
            BlockType.QUERY -> {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "QUERY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        BlockTextField(
                            block = block,
                            viewModel = viewModel,
                            focusRequester = focus,
                            onSlashArmed = { slashArmed = it },
                            readOnly = isReadOnly,
                            inlineStyling = false,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        if (queryResults.isEmpty()) {
                            Text(
                                text = "No matching blocks",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            queryResults.take(10).forEach { candidate ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenNote(candidate.noteId) }
                                        .padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        text = candidate.noteTitle.ifBlank { "Untitled Note" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = candidate.text,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                                    )
                                }
                            }
                            if (queryResults.size > 10) {
                                Text(
                                    text = "+${queryResults.size - 10} more",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }
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
                        // Code is literal; markdown markers have no meaning there.
                        inlineStyling = false,
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
                TableBlockEditor(
                    block = block,
                    viewModel = viewModel,
                    themeColor = themeColor,
                    readOnly = isReadOnly
                )
            }
            BlockType.IMAGE -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Stat the file once per text change, not per recomposition.
                    val ref = remember(block.field.text) {
                        ImageBlockRefParser.parse(block.field.text)
                    }
                    val localFile = remember(ref?.target, viewModel.noteId) {
                        ref?.let { viewModel.fileStore.getNoteAsset(viewModel.noteId, it.target) }
                    }
                    if (localFile != null) {
                        NoteImage(
                            file = localFile,
                            contentDescription = ref?.alt?.ifBlank { "Image" } ?: "Image",
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
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
            }
            BlockType.HEADING -> {
                val (size, weight) = headingStyle(block.headingLevel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Fold toggle: collapse/expand the section below this heading.
                    IconButton(
                        onClick = { if (!isReadOnly) viewModel.toggleFold(block.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (block.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = if (block.collapsed) "Expand" else "Collapse",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        modifier = Modifier.weight(1f)
                    )
                }
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
            }
        }

        if (slashOpen) {
            SlashMenuPopup {
                // The slash menu: the "/query" prefix belongs to the menu.
                viewModel.applyBlockCommand(it, fromSlashMenu = true)
            }
        }

        if (wikiPending != null && wikiMatches.isNotEmpty()) {
            WikiLinkPopup(
                query = wikiPending.query,
                titles = wikiMatches,
                onPick = { title -> viewModel.completeWikiLink(block.id, title) }
            )
        }

        if (blockRefPending != null && refMatches.isNotEmpty()) {
            BlockRefPopup(
                query = blockRefPending.query,
                candidates = refMatches,
                onPick = { candidate -> viewModel.completeBlockRef(block.id, candidate) }
            )
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
    isOnlyBlock: Boolean = false,
    inlineStyling: Boolean = true
) {
    var hasFocus by remember { mutableStateOf(false) }
    val inlineColors = InlineEditorStyle.colors()
    // Markers stay visible (dimmed) while the block is being edited; the
    // rendered text then matches the stored markdown exactly, so caret and IME
    // offsets are never remapped during typing. Unfocused or read-only blocks
    // drop the markers for the WYSIWYG look.
    val hideInlineMarkers = readOnly || !hasFocus
    val visualTransformation = remember(inlineStyling, hideInlineMarkers, inlineColors) {
        if (inlineStyling) {
            InlineEditorStyle.transformation(hideInlineMarkers, inlineColors)
        } else {
            VisualTransformation.None
        }
    }
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
        visualTransformation = visualTransformation,
        modifier = Modifier
            .then(modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                hasFocus = state.isFocused
                if (state.isFocused) viewModel.setActiveBlock(block.id)
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.Tab &&
                        BlockStructureOps.isListBlock(block.type) -> {
                        if (event.isShiftPressed) viewModel.outdentBlock(block.id)
                        else viewModel.indentBlock(block.id)
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.Backspace &&
                        block.field.selection.collapsed && block.field.selection.start == 0 -> {
                        viewModel.onBackspaceAtBlockStart(block.id)
                    }
                    // Ctrl/Cmd+K opens the inline-link dialog (parser + toolbar already
                    // cover insert; this is the keyboard entry point that was missing).
                    event.type == KeyEventType.KeyDown && event.key == Key.K &&
                        (event.isCtrlPressed || event.isMetaPressed) -> {
                        viewModel.openLinkDialog()
                        true
                    }
                    else -> false
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
// Wiki-link completion
// ---------------------------------------------------------------------------

/**
 * Suggestions for an open `[[` in the active block. Picking one closes the
 * link (`[[Title]]`); the list also says what happens with no match, because
 * v1 deliberately does not create notes from a reference.
 */
@Composable
private fun WikiLinkPopup(
    query: String,
    titles: List<String>,
    onPick: (String) -> Unit
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
                    .width(260.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = if (query.isBlank()) "LINK TO NOTE" else "LINK TO \"$query\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                titles.forEach { title ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(title) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "[[" + "]]",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(26.dp)
                        )
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
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
    BlockCommand.LINK -> "🔗"
    BlockCommand.TAG -> "#"
    BlockCommand.HIGHLIGHT -> "≡"
    BlockCommand.EMOJI -> "🙂"
    BlockCommand.QUERY -> "⌕"
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

/**
 * `((` autocomplete: every persisted block, with the note it lives in and its
 * first line, so two similar paragraphs are still tellable apart. Picking one
 * writes `((shortKey|label))`; the label keeps the raw markdown readable.
 */
@Composable
private fun BlockRefPopup(
    query: String,
    candidates: List<BlockCandidate>,
    onPick: (BlockCandidate) -> Unit
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
                    .width(300.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = if (query.isBlank()) "LINK TO BLOCK" else "BLOCK \"$query\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                candidates.forEach { candidate ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(candidate) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "(( ))",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(34.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = BlockRefs.labelFromText(candidate.text),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = candidate.noteTitle.ifBlank { "Untitled Note" },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
