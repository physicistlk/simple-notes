package personal.notes.touch.ui.editor

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.dao.RefTarget
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.text.TableModel
import personal.notes.touch.text.WikiLinks
import personal.notes.touch.data.repo.SettingsRepository

enum class EditorMode {
    EDIT,
    PREVIEW,
    SPLIT
}

/**
 * Focus hand-off after structural block edits (Enter split/continue, Backspace
 * merge/demote, slash picks). The op already sets the block field's selection;
 * the row only has to place real IME focus on it.
 */
data class FocusTarget(val blockId: String)

data class OutlineEntry(val blockIndex: Int, val level: Int, val text: String)

data class WordStats(val wordCount: Int, val charCount: Int)

@OptIn(FlowPreview::class)
class TextEditorViewModel(
    val noteId: String,
    val repository: NoteRepository,
    val fileStore: NoteFileStore,
    val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    val note: StateFlow<NoteEntity?> = repository.getNoteById(noteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    /**
     * Serialized markdown of the whole note — the single source of truth.
     * Block edits re-write it here; raw edits (SPLIT pane, legacy toolbar
     * fallbacks, preview checkbox toggle) re-derive [blocks] from it.
     */
    private val _contentValue = MutableStateFlow(TextFieldValue(""))
    val contentValue: StateFlow<TextFieldValue> = _contentValue.asStateFlow()

    /** Derived block list for the block editor (kept in sync both ways). */
    private val _blocks = MutableStateFlow<List<EditableBlock>>(emptyList())
    val blocks: StateFlow<List<EditableBlock>> = _blocks.asStateFlow()

    private val _activeBlockId = MutableStateFlow<String?>(null)
    val activeBlockId: StateFlow<String?> = _activeBlockId.asStateFlow()

    private val _pendingFocus = MutableStateFlow<FocusTarget?>(null)
    val pendingFocus: StateFlow<FocusTarget?> = _pendingFocus.asStateFlow()

    /** Outline of headings 1–6 in current blocks (SiYuan document outline). */
    val outline: StateFlow<List<OutlineEntry>> = _blocks
        .map { list ->
            list.mapIndexedNotNull { i, b ->
                if (b.type == BlockType.HEADING && b.headingLevel in 1..6 && b.field.text.isNotBlank()) {
                    OutlineEntry(blockIndex = i, level = b.headingLevel, text = b.field.text)
                } else {
                    null
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Real-time document statistics: words and characters count. */
    val wordStats: StateFlow<WordStats> = _contentValue
        .map { tfv ->
            val text = tfv.text
            val charCount = text.length
            val wordCount = if (text.isBlank()) 0 else text.trim().split(Regex("""\s+""")).size
            WordStats(wordCount = wordCount, charCount = charCount)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WordStats(0, 0)
        )

    /** SiYuan read-only view mode toggle. */
    private val _isReadOnly = MutableStateFlow(false)
    val isReadOnly: StateFlow<Boolean> = _isReadOnly.asStateFlow()

    fun toggleReadOnly() {
        _isReadOnly.value = !_isReadOnly.value
    }

    /** Emoji sheet visibility. */
    private val _showEmojiSheet = MutableStateFlow(false)
    val showEmojiSheet: StateFlow<Boolean> = _showEmojiSheet.asStateFlow()

    fun setShowEmojiSheet(show: Boolean) {
        _showEmojiSheet.value = show
    }

    // ---------------------------------------------------------------------
    // Wiki links + backlinks (knowledge index, schema 7)
    // ---------------------------------------------------------------------

    /** Note id + title pairs for `[[` completion and preview taps. */
    data class NoteIndexEntry(val id: String, val title: String)

    val noteIndex: StateFlow<List<NoteIndexEntry>> = repository.getAllNotes()
        .map { notes ->
            notes.map { NoteIndexEntry(id = it.id, title = it.title.ifBlank { "Untitled Note" }) }
                .distinctBy { it.title.lowercase() }
                .sortedBy { it.title.lowercase() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Notes whose content links to this note (`[[this title]]`). */
    val backlinks: StateFlow<List<NoteEntity>> = _title
        .map { it.ifBlank { "Untitled Note" } }
        .distinctUntilChanged()
        .flatMapLatest { current -> repository.observeBacklinks(noteId, current) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** `[[…]]` written in this note, resolved or not. */
    val outgoingRefs: StateFlow<List<RefTarget>> = repository.observeOutgoingRefs(noteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Replaces the open `[[query` of [blockId] with `[[title]]`. Returns false
     * when the caret is no longer inside a pending link (e.g. the popup was
     * tapped after the field moved on), so the popup can close itself.
     */
    fun completeWikiLink(blockId: String, title: String): Boolean {
        val list = _blocks.value
        val index = list.indexOfFirst { it.id == blockId }
        if (index < 0) return false
        val block = list[index]
        val pending = WikiLinks.pendingAt(block.field.text, block.field.selection.end) ?: return false
        val (text, caret) = WikiLinks.complete(block.field.text, pending, title)
        val mutable = list.toMutableList()
        mutable[index] = block.copy(field = TextFieldValue(text, TextRange(caret)))
        publishBlocks(mutable, FocusTarget(blockId))
        return true
    }

    /** Toolbar entry point: opens `[[` at the caret so the popup can suggest. */
    fun insertWikiBracket() {
        val id = _activeBlockId.value ?: return
        val list = _blocks.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val block = list[index]
        val sel = block.field.selection
        val text = block.field.text.substring(0, sel.min) + "[[" + block.field.text.substring(sel.max)
        val mutable = list.toMutableList()
        mutable[index] = block.copy(field = TextFieldValue(text, TextRange(sel.min + 2)))
        publishBlocks(mutable, FocusTarget(id))
    }

    /** Resolves a `[[Title]]` tap in preview to a note id, if the note exists. */
    fun noteIdForTitle(title: String): String? {
        val key = WikiLinks.key(title)
        return noteIndex.value.firstOrNull { WikiLinks.key(it.title) == key }?.id
    }

    // ---------------------------------------------------------------------
    // Inline links + image attachments (cheap editor gaps)
    // ---------------------------------------------------------------------

    /** Current link-dialog request; null = dialog closed. */
    data class LinkDraft(val blockId: String?, val label: String)

    private val _linkDraft = MutableStateFlow<LinkDraft?>(null)
    val linkDraft: StateFlow<LinkDraft?> = _linkDraft.asStateFlow()

    /** Captures the active block's selection as the link label. */
    fun openLinkDialog() {
        // Only the block editor targets a block; Split mode formats the raw
        // markdown selection instead, so its toolbar keeps working as before.
        val block = if (_editorMode.value == EditorMode.EDIT) {
            _blocks.value.firstOrNull { it.id == _activeBlockId.value }
        } else {
            null
        }
        val selection = block?.field?.selection
        val label = if (block != null && selection != null && !selection.collapsed) {
            block.field.text.substring(selection.min, selection.max)
        } else {
            ""
        }
        _linkDraft.value = LinkDraft(block?.id, label)
    }

    fun dismissLinkDialog() {
        _linkDraft.value = null
    }

    /**
     * Wraps the current selection (or inserts [label]) as `[label](url)`.
     * Returns false for an empty/unsafe URL so the dialog can stay open and
     * show an error. Falls back to the raw markdown surface in Split mode.
     */
    fun applyLink(label: String, url: String): Boolean {
        val normalized = LinkTarget.normalize(url) ?: return false
        val draft = _linkDraft.value
        val blockId = draft?.blockId

        if (blockId != null) {
            val list = _blocks.value
            val index = list.indexOfFirst { it.id == blockId }
            if (index >= 0) {
                val block = list[index]
                val selection = block.field.selection
                val selected = if (!selection.collapsed) {
                    block.field.text.substring(selection.min, selection.max)
                } else {
                    ""
                }
                // The dialog field wins (it is prefilled from the selection),
                // then the selection, then the URL itself.
                val markdown = LinkMarkdown.build(label.trim().ifEmpty { selected }, normalized)
                    ?: return false
                val newText = block.field.text.substring(0, selection.min) + markdown +
                    block.field.text.substring(selection.max)
                val mutable = list.toMutableList()
                mutable[index] = block.copy(
                    field = TextFieldValue(newText, TextRange(selection.min + markdown.length))
                )
                publishBlocks(mutable, FocusTarget(block.id))
                _linkDraft.value = null
                return true
            }
        }

        val current = _contentValue.value
        val selection = current.selection
        val selected = if (!selection.collapsed) {
            current.text.substring(selection.min, selection.max)
        } else {
            ""
        }
        val markdown = LinkMarkdown.build(label.trim().ifEmpty { selected }, normalized)
            ?: return false
        val newText = current.text.substring(0, selection.min) + markdown +
            current.text.substring(selection.max)
        onContentChange(TextFieldValue(newText, TextRange(selection.min + markdown.length)))
        _linkDraft.value = null
        return true
    }

    /**
     * Bumped when the image picker should open (toolbar button or the slash
     * "Image" command). The screen observes it and launches GetContent.
     */
    private val _imagePickRequest = MutableStateFlow(0L)
    val imagePickRequest: StateFlow<Long> = _imagePickRequest.asStateFlow()

    fun requestImagePick() {
        _imagePickRequest.value = System.nanoTime()
    }

    /** Called once the screen has launched the picker (keeps rotation cheap). */
    fun consumeImagePickRequest() {
        _imagePickRequest.value = 0L
    }

    /** Copies the picked image into the note dir (IO) and returns its file name. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        fileStore.saveNoteImage(noteId, uri)
    }

    /** Inserts `![image](fileName)` after the active block, then a prose row. */
    fun insertImageBlock(fileName: String) {
        if (fileName.isBlank()) return
        val safe = fileName.replace("]", "").replace(")", "")
        val image = EditableBlock(
            id = BlockPersistence.newBlockId(),
            type = BlockType.IMAGE,
            field = TextFieldValue("![image]($safe)")
        )
        val list = _blocks.value
        val activeIndex = list.indexOfFirst { it.id == _activeBlockId.value }
        val mutable = list.toMutableList()
        val paragraph = freshParagraph()
        if (activeIndex >= 0 &&
            list[activeIndex].type == BlockType.PARAGRAPH &&
            list[activeIndex].field.text.isEmpty()
        ) {
            mutable[activeIndex] = image
            mutable.add(activeIndex + 1, paragraph)
        } else {
            val at = if (activeIndex >= 0) activeIndex + 1 else mutable.size
            mutable.add(at, image)
            mutable.add(at + 1, paragraph)
        }
        publishBlocks(mutable, FocusTarget(paragraph.id))
    }

    fun insertEmoji(emoji: String) {
        val activeId = _activeBlockId.value
        val list = _blocks.value
        val idx = if (activeId != null) list.indexOfFirst { it.id == activeId } else -1

        if (idx >= 0) {
            val cur = list[idx]
            val f = cur.field
            val at = f.selection.start.coerceIn(0, f.text.length)
            val newText = f.text.substring(0, at) + emoji + f.text.substring(at)
            val newSel = TextRange(at + emoji.length)
            val mutable = list.toMutableList()
            mutable[idx] = cur.copy(field = TextFieldValue(newText, newSel))
            publishBlocks(mutable, FocusTarget(cur.id))
        } else {
            val cur = _contentValue.value
            val at = cur.selection.start.coerceIn(0, cur.text.length)
            val newText = cur.text.substring(0, at) + emoji + cur.text.substring(at)
            val newSel = TextRange(at + emoji.length)
            onContentChange(TextFieldValue(newText, newSel))
        }
    }

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editorMode = MutableStateFlow(EditorMode.EDIT)
    val editorMode: StateFlow<EditorMode> = _editorMode.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Black)
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private var isInitialized = false

    init {
        // Initialize from note entity once loaded. Block ids live in
        // `note_blocks` (schema 6), so load them BEFORE deriving blocks from the
        // markdown: the editor must never surface position-derived ids.
        viewModelScope.launch {
            val storedBlocks = BlockPersistence.fromEntities(repository.loadNoteBlocks(noteId))
            repository.getNoteById(noteId).collect { loadedNote ->
                if (loadedNote != null && !isInitialized) {
                    _title.value = loadedNote.title
                    val md = loadedNote.content ?: ""
                    val parsed = BlockModel.parseBlocks(md)
                    val normalized = BlockModel.serializeBlocks(parsed)

                    _blocks.value = when {
                        storedBlocks.isEmpty() -> {
                            // Lazy migration for notes written before v6: mint
                            // stable ids once and persist immediately, so a kill
                            // before the first autosave cannot lose them.
                            val minted = parsed.map { it.copy(id = BlockPersistence.newBlockId()) }
                            repository.saveNoteBlocks(noteId, BlockPersistence.toEntities(noteId, minted))
                            minted
                        }
                        BlockModel.serializeBlocks(storedBlocks) == normalized -> storedBlocks
                        // Markdown is authoritative (two writes, one crash):
                        // keep every id whose block survived the edit.
                        else -> BlockPersistence.reconcileIds(parsed, storedBlocks)
                    }
                    _contentValue.value = TextFieldValue(md)
                    isInitialized = true
                }
            }
        }

        // Setup debounced autosave (M2 spec: debounce 1s) — unchanged persistence
        // path. Block edits funnel into _contentValue, so this still covers them;
        // TextEditorScreen additionally calls saveNow() on ON_PAUSE / back /
        // check so notes survive an app kill.
        combine(_title, _contentValue) { t, c -> t to c.text }
            .drop(1)
            .filter { isInitialized }
            .debounce(1000)
            .onEach { (t, c) ->
                performSave(t, c)
            }
            .launchIn(viewModelScope)
    }

    // ---------------------------------------------------------------------
    // Title / mode / persistence
    // ---------------------------------------------------------------------

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
    }

    fun setEditorMode(mode: EditorMode) {
        _editorMode.value = mode
    }

    fun setSelectedColor(color: Color) {
        _selectedColor.value = color
    }

    fun saveNow(onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            performSave(_title.value, _contentValue.value.text)
            onSaved?.invoke()
        }
    }

    private suspend fun performSave(titleText: String, contentText: String) {
        _isSaving.value = true
        try {
            repository.updateNoteContent(
                id = noteId,
                title = titleText.ifBlank { "Untitled Note" },
                content = contentText
            )
            persistBlocks(contentText)
        } finally {
            _isSaving.value = false
        }
    }

    /**
     * Writes the note's block rows for the content being saved. Skipped when
     * the block list does not describe [contentText] (e.g. a save fired before
     * the first derivation) or is empty — the next open then falls back to the
     * markdown and reconciles ids instead of persisting a wrong shape.
     */
    private suspend fun persistBlocks(contentText: String) {
        val blocks = _blocks.value
        if (blocks.isEmpty()) return
        val expected = BlockModel.serializeBlocks(BlockModel.parseBlocks(contentText))
        if (BlockModel.serializeBlocks(blocks) != expected) return
        repository.saveNoteBlocks(noteId, BlockPersistence.toEntities(noteId, blocks))
    }

    // ---------------------------------------------------------------------
    // Block editor surface
    // ---------------------------------------------------------------------

    fun setActiveBlock(id: String?) {
        _activeBlockId.value = id
    }

    fun consumePendingFocus() {
        _pendingFocus.value = null
    }

    /**
     * Re-derive blocks from markdown (Split pane, legacy toolbar helpers).
     * Ids are reconciled against the current list instead of re-derived from
     * line numbers, so an in-place raw edit keeps its block identity; drop the
     * active id only if its block really vanished.
     */
    private fun rederiveFromContent(text: String) {
        val parsed = BlockModel.parseBlocks(text)
        val previous = _blocks.value
        val reconciled = if (previous.isEmpty()) {
            parsed.map { it.copy(id = BlockPersistence.newBlockId()) }
        } else {
            BlockPersistence.reconcileIds(parsed, previous)
        }
        _blocks.value = reconciled
        val active = _activeBlockId.value
        if (active != null && reconciled.none { it.id == active }) _activeBlockId.value = null
    }

    /** Update blocks + serialized markdown (persistence lane). */
    private fun publishBlocks(newList: List<EditableBlock>, focus: FocusTarget? = null) {
        _blocks.value = newList
        if (focus != null) {
            _activeBlockId.value = focus.blockId
            _pendingFocus.value = focus
        }
        val md = BlockModel.serializeBlocks(newList)
        val cur = _contentValue.value
        if (cur.text != md) {
            _contentValue.value = cur.copy(
                text = md,
                selection = TextRange(
                    cur.selection.start.coerceIn(0, md.length),
                    cur.selection.end.coerceIn(0, md.length)
                )
            )
        }
    }

    /** Plain per-keystroke change of one block's text field; also detects Enter. */
    fun onBlockValueChange(id: String, newValue: TextFieldValue) {
        val list = _blocks.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cur = list[idx]

        val sel = newValue.selection
        val grew = newValue.text.length > cur.field.text.length
        val enteredNewline = cur.type != BlockType.CODE &&
            grew &&
            sel.collapsed &&
            sel.start in 1..newValue.text.length &&
            newValue.text[sel.start - 1] == '\n'

        if (enteredNewline) {
            val caret = sel.start
            val pre = newValue.text.substring(0, caret - 1)
            val post = newValue.text.substring(caret)
            val atEnd = caret - 1 >= cur.field.text.length
            val newList = BlockOps.enterSplit(list, id, pre, post, atEnd)
            val nextId = newList.getOrNull(idx + 1)?.id
            publishBlocks(newList, nextId?.let { FocusTarget(it) })
            return
        }

        val updated = cur.copy(field = newValue)
        val mutable = list.toMutableList()
        mutable[idx] = updated
        publishBlocks(mutable)
    }

    /** Enter key handled through the key-event path (hardware / some IMEs). */
    fun onEnterPressed(id: String) {
        val list = _blocks.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cur = list[idx]
        if (cur.type == BlockType.CODE) return // code blocks stay multi-line
        val f = cur.field
        if (f.selection.collapsed) {
            val caret = f.selection.start
            splitAt(list, idx, pre = f.text.substring(0, caret), post = f.text.substring(caret))
        } else {
            val s = f.selection.start
            val e = f.selection.end
            splitAt(list, idx, pre = f.text.substring(0, s), post = f.text.substring(e))
        }
    }

    private fun splitAt(list: List<EditableBlock>, idx: Int, pre: String, post: String) {
        val blockId = list[idx].id
        val atEnd = list[idx].field.selection.end >= list[idx].field.text.length
        val newList = BlockOps.enterSplit(list, blockId, pre, post, atEnd)
        val nextId = newList.getOrNull(idx + 1)?.id
        publishBlocks(newList, nextId?.let { FocusTarget(it) })
    }

    /**
     * Backspace at column 0 of a block. Returns true when the editor consumed
     * the event (via Modifier.onPreviewKeyEvent — soft IMEs vary in whether
     * they emit it, see known edge cases in the handover).
     */
    fun onBackspaceAtBlockStart(id: String): Boolean {
        val list = _blocks.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val cur = list[idx]
        if (!cur.field.selection.collapsed || cur.field.selection.start != 0) return false
        // Atomic fenced/static blocks keep native behaviour only.
        if (cur.type == BlockType.CODE || cur.type == BlockType.TABLE || cur.type == BlockType.IMAGE) {
            return false
        }

        val result = BlockOps.backspaceAtStart(list, id)
        if (result.blocks === list && result.focusId == null) {
            // Sensible stay per spec: divider or empty heading before, or an
            // unchanged first block. Consume so nothing destructive happens.
            return true
        }
        if (result.focusId != null) {
            publishBlocks(result.blocks, FocusTarget(result.focusId))
        } else {
            publishBlocks(result.blocks)
        }
        return true
    }

    /** Checkbox tap inside a TASK block row. */
    fun onToggleTaskChecked(id: String, checked: Boolean? = null) {
        val list = _blocks.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cur = list[idx]
        if (cur.type != BlockType.TASK) return
        val updated = cur.copy(checked = checked ?: !cur.checked)
        val mutable = list.toMutableList()
        mutable[idx] = updated
        publishBlocks(mutable)
    }

    /** Inline markdown wrapper on the ACTIVE block's selection (formatting bar). */
    fun applyInlineToActiveBlock(
        prefix: String,
        suffix: String = prefix,
        placeholder: String = "text"
    ) {
        val list = _blocks.value
        val id = _activeBlockId.value ?: run {
            applyWrapFormatting(prefix, suffix, placeholder)
            return
        }
        val idx = list.indexOfFirst { b -> b.id == id }
        if (idx < 0) {
            applyWrapFormatting(prefix, suffix, placeholder)
            return
        }
        val updated = BlockOps.wrapInline(list[idx], prefix, suffix, placeholder)
        val mutable = list.toMutableList()
        mutable[idx] = updated
        publishBlocks(mutable)
    }

    private fun freshParagraph(): EditableBlock =
        EditableBlock(
            id = BlockPersistence.newBlockId(),
            type = BlockType.PARAGRAPH
        )

    // ---------------------------------------------------------------------
    // Block menu operations (indent, duplicate, move, delete, turn into)
    // ---------------------------------------------------------------------

    fun indentActiveBlock() {
        _activeBlockId.value?.let(::indentBlock)
    }

    fun outdentActiveBlock() {
        _activeBlockId.value?.let(::outdentBlock)
    }

    fun indentBlock(id: String) {
        val updated = BlockStructureOps.shiftIndent(_blocks.value, id, 1)
        if (updated !== _blocks.value) publishBlocks(updated)
    }

    fun outdentBlock(id: String) {
        val updated = BlockStructureOps.shiftIndent(_blocks.value, id, -1)
        if (updated !== _blocks.value) publishBlocks(updated)
    }

    /** Toggles the fold state of a heading block. */
    fun toggleFold(id: String) {
        val list = _blocks.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0 || list[index].type != BlockType.HEADING) return
        val updated = list.toMutableList()
        updated[index] = list[index].copy(collapsed = !list[index].collapsed)
        publishBlocks(updated)
    }

    /** Replaces a TABLE block's markdown (cells, add/remove row or column). */
    fun updateTableBlock(id: String, rows: List<List<String>>) {
        val list = _blocks.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0 || list[index].type != BlockType.TABLE) return
        val markdown = TableModel.serialize(rows)
        if (markdown == list[index].field.text) return
        val updated = list.toMutableList()
        updated[index] = list[index].copy(field = TextFieldValue(markdown))
        publishBlocks(updated)
    }

    fun duplicateBlock(id: String) {
        val before = _blocks.value
        val updated = BlockStructureOps.duplicate(before, id) { BlockPersistence.newBlockId() }
        if (updated === before) return
        val copyIndex = before.indexOfFirst { it.id == id } + 1
        val copy = updated.getOrNull(copyIndex) ?: return
        val focus = when (copy.type) {
            BlockType.DIVIDER, BlockType.IMAGE -> null
            else -> FocusTarget(copy.id)
        }
        publishBlocks(updated, focus)
    }

    fun deleteBlock(id: String) {
        val list = _blocks.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = BlockStructureOps.delete(list, id) { BlockPersistence.newBlockId() }
        val focusId = updated.getOrNull(index)?.id ?: updated.lastOrNull()?.id
        publishBlocks(updated, focusId?.let { FocusTarget(it) })
    }

    fun moveBlock(id: String, delta: Int) {
        val updated = BlockStructureOps.move(_blocks.value, id, delta)
        if (updated !== _blocks.value) publishBlocks(updated, FocusTarget(id))
    }

    fun transformBlock(id: String, type: BlockType, headingLevel: Int = 1) {
        val updated = BlockStructureOps.transform(_blocks.value, id, type, headingLevel)
        if (updated !== _blocks.value) publishBlocks(updated, FocusTarget(id))
    }

    /** Markdown of one block, used by the menu's "Copy block" action. */
    fun blockMarkdown(id: String): String? {
        val block = _blocks.value.firstOrNull { it.id == id } ?: return null
        return BlockModel.serializeBlockLines(block).joinToString("\n")
    }

    /**
     * One command path for the slash menu AND the formatting toolbar:
     * H1-H3 / lists / quote transform the active block, Divider/Code/Table/
     * Image insert blocks, Tag/Highlight write inline markdown. The "Rich"
     * inserts are simple markdown placeholders — the follow-up worker swaps in
     * real pickers (image assets, tag chips) at this same seam.
     */
    fun applyBlockCommand(command: BlockCommand) {
        val list = _blocks.value
        val id = _activeBlockId.value ?: return
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cur = list[idx]
        val (clean, body) = BlockOps.stripSlashQuery(cur)
        val bodyField = TextFieldValue(body, TextRange(body.length))
        val mutable = list.toMutableList()
        mutable[idx] = clean
        val freshEmpty = body.isEmpty() && cur.type == BlockType.PARAGRAPH

        val finish: (List<EditableBlock>, FocusTarget?) -> Unit = { l, focus ->
            publishBlocks(l, focus)
        }

        fun insertAfter(index: Int, block: EditableBlock) {
            mutable.add(index + 1, block)
        }

        when (command) {
            BlockCommand.H1, BlockCommand.H2, BlockCommand.H3,
            BlockCommand.H4, BlockCommand.H5, BlockCommand.H6 -> {
                mutable[idx] = clean.copy(
                    type = BlockType.HEADING,
                    headingLevel = when (command) {
                        BlockCommand.H1 -> 1
                        BlockCommand.H2 -> 2
                        BlockCommand.H3 -> 3
                        BlockCommand.H4 -> 4
                        BlockCommand.H5 -> 5
                        else -> 6
                    },
                    indent = 0,
                    field = bodyField
                )
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.BULLET -> {
                mutable[idx] = clean.copy(
                    type = BlockType.BULLET,
                    indent = cur.indent,
                    field = bodyField
                )
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.NUMBERED -> {
                mutable[idx] = clean.copy(
                    type = BlockType.NUMBERED,
                    order = 1,
                    indent = cur.indent,
                    field = bodyField
                )
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.TASK -> {
                mutable[idx] = clean.copy(
                    type = BlockType.TASK,
                    checked = false,
                    indent = cur.indent,
                    field = bodyField
                )
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.QUOTE -> {
                mutable[idx] = clean.copy(
                    type = BlockType.QUOTE,
                    indent = cur.indent,
                    field = bodyField
                )
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.IMAGE -> {
                // The picker owns insertion now: clear the slash text, then let
                // the screen launch GetContent; insertImageBlock() adds the row.
                mutable[idx] = clean.copy(field = bodyField)
                finish(mutable, FocusTarget(clean.id))
                requestImagePick()
            }
            BlockCommand.LINK -> {
                mutable[idx] = clean.copy(field = bodyField)
                finish(mutable, FocusTarget(clean.id))
                openLinkDialog()
            }
            BlockCommand.DIVIDER, BlockCommand.CODE, BlockCommand.TABLE -> {
                val insertedId = BlockPersistence.newBlockId()
                val inserted = when (command) {
                    BlockCommand.DIVIDER -> EditableBlock(id = insertedId, type = BlockType.DIVIDER)
                    BlockCommand.CODE -> EditableBlock(
                        id = insertedId,
                        type = BlockType.CODE,
                        field = TextFieldValue(""),
                        closed = true
                    )
                    // Only DIVIDER/CODE/TABLE reach this branch: IMAGE and LINK
                    // are handled above (they need the picker/dialog, not a row),
                    // so the fallback is the table.
                    else -> EditableBlock(
                        id = insertedId,
                        type = BlockType.TABLE,
                        field = TextFieldValue(BlockModel.DEFAULT_TABLE_ROWS.joinToString("\n"))
                    )
                }
                if (freshEmpty) {
                    // "/"-only brand-new block → becomes the inserted block itself.
                    mutable[idx] = inserted.copy(id = clean.id)
                } else {
                    mutable[idx] = clean.copy(field = bodyField)
                    insertAfter(idx, inserted)
                }
                // Prose row right after the static block so Enter/caret have a home.
                val afterIdx = if (freshEmpty) idx else idx + 1
                insertAfter(afterIdx, freshParagraph())
                // Focus HOME: Divider → the prose row; Code/Table/Image → the
                // content block itself (its field carries the caret/selection).
                val focusId = when (command) {
                    BlockCommand.CODE, BlockCommand.TABLE, BlockCommand.IMAGE ->
                        if (freshEmpty) clean.id else insertedId
                    else -> mutable.getOrNull(afterIdx + 1)?.id
                }
                finish(mutable, focusId?.let { FocusTarget(it) })
            }
            BlockCommand.TAG -> {
                val text = "#tag#" + body
                mutable[idx] = clean.copy(field = TextFieldValue(text, TextRange(1, 4)))
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.HIGHLIGHT -> {
                val text = if (body.isEmpty()) "====" else "==" + body + "=="
                val sel = if (body.isEmpty()) TextRange(2, 2) else TextRange(2, 2 + body.length)
                mutable[idx] = clean.copy(field = TextFieldValue(text, sel))
                finish(mutable, FocusTarget(clean.id))
            }
            BlockCommand.EMOJI -> {
                _showEmojiSheet.value = true
                mutable[idx] = clean.copy(field = bodyField)
                finish(mutable, FocusTarget(clean.id))
            }
        }
    }

    // ---------------------------------------------------------------------
    // Legacy raw-surface helpers (SPLIT source editing / no active block).
    // They all funnel into onContentChange so blocks re-derive from the same
    // markdown source.
    // ---------------------------------------------------------------------

    fun onContentChange(value: TextFieldValue) {
        _contentValue.value = value
        rederiveFromContent(value.text)
    }

    fun applyWrapFormatting(prefix: String, suffix: String = prefix, placeholder: String = "text") {
        val current = _contentValue.value
        val text = current.text
        val selection = current.selection

        val (newText, newSelection) = if (selection.collapsed) {
            val inserted = "$prefix$placeholder$suffix"
            val before = text.substring(0, selection.start)
            val after = text.substring(selection.start)
            (before + inserted + after) to TextRange(
                selection.start + prefix.length,
                selection.start + prefix.length + placeholder.length
            )
        } else {
            val selectedText = text.substring(selection.start, selection.end)
            val wrapped = "$prefix$selectedText$suffix"
            val before = text.substring(0, selection.start)
            val after = text.substring(selection.end)
            (before + wrapped + after) to TextRange(
                selection.start,
                selection.start + wrapped.length
            )
        }

        onContentChange(TextFieldValue(newText, newSelection))
    }

    fun applyBold() = applyWrapFormatting("**")
    fun applyItalic() = applyWrapFormatting("*")
    fun applyUnderline() = applyWrapFormatting("<u>", "</u>")
    fun applyStrikethrough() = applyWrapFormatting("~~")
    fun applyInlineCode() = applyWrapFormatting("`")

    fun applyColor(color: Color) {
        val hex = String.format("#%06X", (0xFFFFFF and color.toArgb()))
        applyWrapFormatting("""<font color="$hex">""", "</font>", "text")
    }

    fun applyLinePrefix(prefix: String) {
        val current = _contentValue.value
        val text = current.text
        val selectionStart = current.selection.start

        // Find start of current line
        val lineStart = text.lastIndexOf('\n', (selectionStart - 1).coerceAtLeast(0))
        val insertIndex = if (lineStart == -1) 0 else lineStart + 1

        val before = text.substring(0, insertIndex)
        val after = text.substring(insertIndex)
        val newText = before + prefix + after
        val newCursor = selectionStart + prefix.length

        onContentChange(TextFieldValue(newText, TextRange(newCursor)))
    }

    fun applyH1() = applyLinePrefix("# ")
    fun applyH2() = applyLinePrefix("## ")
    fun applyH3() = applyLinePrefix("### ")
    fun applyBulletList() = applyLinePrefix("- ")
    fun applyNumberedList() = applyLinePrefix("1. ")
    fun applyChecklist() = applyLinePrefix("- [ ] ")
    fun applyQuote() = applyLinePrefix("> ")

    fun insertDivider() {
        val current = _contentValue.value
        val text = current.text
        val cursor = current.selection.start
        val insert = "\n---\n"
        val newText = text.substring(0, cursor) + insert + text.substring(cursor)
        val newCursor = cursor + insert.length
        onContentChange(TextFieldValue(newText, TextRange(newCursor)))
    }

    fun insertCodeBlock() {
        val current = _contentValue.value
        val text = current.text
        val cursor = current.selection.start
        val insert = "\n```\ncode\n```\n"
        val newText = text.substring(0, cursor) + insert + text.substring(cursor)
        val newCursor = cursor + 5
        onContentChange(TextFieldValue(newText, TextRange(newCursor, newCursor + 4)))
    }

    fun toggleChecklist(lineIndex: Int, isChecked: Boolean) {
        val current = _contentValue.value
        val lines = current.text.lines().toMutableList()
        if (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            val updatedLine = if (isChecked) {
                line.replaceFirst(Regex("""^([\s*-]*\[)[ ](\])"""), "$1x$2")
            } else {
                line.replaceFirst(Regex("""^([\s*-]*\[)[xX](\])"""), "$1 $2")
            }
            lines[lineIndex] = updatedLine
            val newText = lines.joinToString("\n")
            onContentChange(current.copy(text = newText))
            viewModelScope.launch {
                performSave(_title.value, newText)
            }
        }
    }

    class Factory(
        private val noteId: String,
        private val repository: NoteRepository,
        private val fileStore: NoteFileStore,
        private val settingsRepository: SettingsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TextEditorViewModel(noteId, repository, fileStore, settingsRepository) as T
        }
    }
}
