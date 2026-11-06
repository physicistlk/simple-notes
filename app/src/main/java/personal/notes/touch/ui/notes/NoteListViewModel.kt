package personal.notes.touch.ui.notes

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import personal.notes.touch.data.db.dao.TagCount
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.TemplateEntity
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.text.NoteSearch
import personal.notes.touch.text.SearchSnippet

enum class NoteFilter {
    ALL,
    FAVORITES,
    RECYCLE_BIN
}

enum class ViewMode {
    LIST,
    GRID
}

data class NoteListUiState(
    val currentFilter: NoteFilter = NoteFilter.ALL,
    val selectedFolderId: String? = null,
    val viewMode: ViewMode = ViewMode.GRID,
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    /** Lowercased tag identity from the tag bar; null = all tags. */
    val selectedTag: String? = null
)

/**
 * A note as the library shows it: the entity, its tags and — while a search is
 * active — the matched excerpt to render under the title.
 */
data class NoteRow(
    val note: NoteEntity,
    val tags: List<String>,
    val snippet: SearchSnippet? = null,
    val titleMatch: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModel(
    val repository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteListUiState())
    val uiState: StateFlow<NoteListUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Tag bar: every tag of a live note, with counts (schema 7 index). */
    val tagCounts: StateFlow<List<TagCount>> = repository.observeTagCounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val tagsByNote: StateFlow<Map<String, List<String>>> = repository.observeTagsByNote()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** Notes of the selected section/folder — the pre-search list. */
    private val baseNotes: StateFlow<List<NoteEntity>> = _uiState
        .flatMapLatest { state ->
            when {
                state.currentFilter == NoteFilter.FAVORITES -> repository.getFavorites()
                state.currentFilter == NoteFilter.RECYCLE_BIN -> repository.getRecycledNotes()
                state.selectedFolderId != null -> repository.getNotesByFolder(state.selectedFolderId)
                else -> repository.getAllNotes()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Search + tag filtering happens here, not in SQL: matching must be
     * case-insensitive for non-ASCII text and must treat `%`/`_` literally,
     * and the snippet needs the raw markdown anyway. Tag identity is the
     * lowercased key, display casing comes from the note.
     */
    val notes: StateFlow<List<NoteRow>> =
        combine(baseNotes, _uiState, tagsByNote) { base, state, tags ->
            val tokens = NoteSearch.tokens(state.searchQuery)
            val tag = state.selectedTag
            val filtered = base.filter { note ->
                val noteTags = tags[note.id].orEmpty()
                val tagOk = tag == null || noteTags.any { it.equals(tag, ignoreCase = true) }
                val queryOk = tokens.isEmpty() ||
                    NoteSearch.matches(tokens, note.title, note.content.orEmpty())
                tagOk && queryOk
            }
            filtered
                .map { note ->
                    NoteRow(
                        note = note,
                        tags = tags[note.id].orEmpty(),
                        snippet = if (tokens.isEmpty()) {
                            null
                        } else {
                            NoteSearch.snippet(note.content.orEmpty(), tokens)
                        },
                        titleMatch = tokens.isNotEmpty() &&
                            NoteSearch.containsAll(note.title, tokens)
                    )
                }
                // Notes whose title matched come before content-only matches.
                .sortedByDescending { it.titleMatch }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        // Catch-up for notes written before the tag/reference index existed
        // (schema 6 → 7 upgrade). Idempotent and cheap: it only writes when the
        // derived rows differ.
        viewModelScope.launch {
            runCatching { repository.reindexAll() }
                .onFailure { it.printStackTrace() }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchOpen = isOpen,
            searchQuery = if (!isOpen) "" else _uiState.value.searchQuery
        )
    }

    /** Tap a tag chip; tapping the selected one clears the filter. */
    fun toggleTag(tag: String?) {
        _uiState.value = _uiState.value.copy(
            selectedTag = if (tag == _uiState.value.selectedTag) null else tag
        )
    }

    fun setFilter(filter: NoteFilter) {
        _uiState.value = _uiState.value.copy(
            currentFilter = filter,
            selectedFolderId = null,
            searchQuery = "",
            selectedTag = null
        )
    }

    fun selectFolder(folderId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedFolderId = folderId,
            currentFilter = NoteFilter.ALL,
            searchQuery = "",
            selectedTag = null
        )
    }

    fun toggleViewMode() {
        val newMode = if (_uiState.value.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        _uiState.value = _uiState.value.copy(viewMode = newMode)
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createFolder(name = name)
            }
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.renameFolder(folderId, newName)
            }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_uiState.value.selectedFolderId == folderId) {
                _uiState.value = _uiState.value.copy(selectedFolderId = null)
            }
        }
    }

    // ---- templates + daily notes ------------------------------------------

    val templates: StateFlow<List<TemplateEntity>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Opens today's note (created from the daily template when it does not exist). */
    fun openToday(onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val note = repository.openOrCreateDailyNote()
            onOpened(note.id)
        }
    }

    fun createFromTemplate(templateId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repository.createNoteFromTemplate(templateId)?.let { onCreated(it.id) }
        }
    }

    fun saveTemplate(id: String?, name: String, content: String, isDaily: Boolean) {
        viewModelScope.launch { repository.saveTemplate(id, name, content, isDaily) }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }

    fun addStarterTemplates() {
        viewModelScope.launch { repository.addStarterTemplates() }
    }

    fun createNote(title: String = "Untitled Note", pageType: String = "infinite", onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val folderId = _uiState.value.selectedFolderId
            val newNote = repository.createNote(title = title, folderId = folderId, pageType = pageType)
            onCreated(newNote.id)
        }
    }

    /**
     * Create an "Imported Image" note and persist the picked bitmap as the
     * note's page background: the PNG is written to page_0.png of the note
     * directory — the exact same file CanvasScreen loads via
     * [CanvasViewModel.loadCurrentPageBitmap] and sets as backgroundBitmap
     * when the note opens. Callers supply the already-decoded bitmap; the
     * file save runs on Dispatchers.IO (inside NoteRepository), the source
     * bitmap is recycled after saving, and navigation happens only once the
     * page file exists so the opened note shows the image immediately.
     */
    fun createImageNote(bitmap: Bitmap, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val folderId = _uiState.value.selectedFolderId
            val newNote = try {
                repository.createNote(title = "Imported Image", folderId = folderId, pageType = "image_bg")
            } catch (e: Exception) {
                e.printStackTrace()
                if (!bitmap.isRecycled) bitmap.recycle()
                return@launch
            }
            try {
                repository.savePageBitmap(newNote.id, 0, bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            onCreated(newNote.id)
        }
    }

    fun renameNote(noteId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateTitle(noteId, newTitle)
        }
    }

    fun moveNoteToFolder(noteId: String, folderId: String?) {
        viewModelScope.launch {
            repository.moveNoteToFolder(noteId, folderId)
        }
    }

    fun toggleFavorite(note: NoteEntity) {
        viewModelScope.launch {
            repository.setFavorite(note.id, !note.favorite)
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            repository.softDeleteNote(id, true)
        }
    }

    fun restoreNote(id: String) {
        viewModelScope.launch {
            repository.softDeleteNote(id, false)
        }
    }

    fun permanentlyDeleteNote(id: String) {
        viewModelScope.launch {
            repository.permanentlyDeleteNote(id)
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
        }
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteListViewModel(repository) as T
        }
    }
}
