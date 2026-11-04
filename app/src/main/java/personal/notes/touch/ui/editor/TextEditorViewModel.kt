package personal.notes.touch.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.data.repo.SettingsRepository

enum class EditorMode {
    EDIT,
    PREVIEW,
    SPLIT
}

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

    private val _contentValue = MutableStateFlow(TextFieldValue(""))
    val contentValue: StateFlow<TextFieldValue> = _contentValue.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editorMode = MutableStateFlow(EditorMode.EDIT)
    val editorMode: StateFlow<EditorMode> = _editorMode.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Black)
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private var isInitialized = false

    init {
        // Initialize from note entity once loaded
        viewModelScope.launch {
            repository.getNoteById(noteId).collect { loadedNote ->
                if (loadedNote != null && !isInitialized) {
                    _title.value = loadedNote.title
                    _contentValue.value = TextFieldValue(loadedNote.content ?: "")
                    isInitialized = true
                }
            }
        }

        // Setup debounced autosave (M2 spec: debounce 1s)
        combine(_title, _contentValue) { t, c -> t to c.text }
            .drop(1)
            .filter { isInitialized }
            .debounce(1000)
            .onEach { (t, c) ->
                performSave(t, c)
            }
            .launchIn(viewModelScope)
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
    }

    fun onContentChange(newVal: TextFieldValue) {
        _contentValue.value = newVal
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
        } finally {
            _isSaving.value = false
        }
    }

    // --- Rich Text Insertion & Formatting Helpers ---

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

        _contentValue.value = TextFieldValue(newText, newSelection)
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

        _contentValue.value = TextFieldValue(newText, TextRange(newCursor))
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
        _contentValue.value = TextFieldValue(newText, TextRange(newCursor))
    }

    fun insertCodeBlock() {
        val current = _contentValue.value
        val text = current.text
        val cursor = current.selection.start
        val insert = "\n```\ncode\n```\n"
        val newText = text.substring(0, cursor) + insert + text.substring(cursor)
        val newCursor = cursor + 5
        _contentValue.value = TextFieldValue(newText, TextRange(newCursor, newCursor + 4))
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
            _contentValue.value = current.copy(text = newText)
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
