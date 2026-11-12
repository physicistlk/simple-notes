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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import personal.notes.touch.data.db.dao.FolderCount
import personal.notes.touch.data.db.dao.TagCount
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.TemplateEntity
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.data.repo.SettingsRepository
import personal.notes.touch.text.FolderTree
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

enum class NoteSortOrder {
    UPDATED_DESC,
    UPDATED_ASC,
    TITLE_ASC,
    TITLE_DESC
}

data class NoteListUiState(
    val currentFilter: NoteFilter = NoteFilter.ALL,
    val selectedFolderId: String? = null,
    val viewMode: ViewMode = ViewMode.GRID,
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    /** Lowercased tag identity from the tag bar; null = all tags. */
    val selectedTag: String? = null,
    val sortOrder: NoteSortOrder = NoteSortOrder.UPDATED_DESC
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
    val repository: NoteRepository,
    val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteListUiState())
    val uiState: StateFlow<NoteListUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * `folderId -> live note count` (null key = unfiled notes). The tree adds
     * descendants on top, so a collapsed notebook still shows its real size.
     */
    val folderCounts: StateFlow<Map<String?, Int>> = repository.observeFolderCounts()
        .map { rows -> rows.associate { it.folderId to it.count } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
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

    /**
     * Notes of the selected section/notebook — the pre-search list.
     *
     * A notebook selection covers its whole subtree: picking "Physics" shows
     * the notes of Physics and of every folder below it, which is exactly the
     * population its tree count advertises. `folders` is part of the pipeline
     * so a rename/move/re-parent re-resolves the subtree.
     */
    private val baseNotes: StateFlow<List<NoteEntity>> =
        combine(_uiState, folders) { state, all -> state to all }
            .flatMapLatest { (state, all) ->
                when {
                    state.currentFilter == NoteFilter.FAVORITES -> repository.getFavorites()
                    state.currentFilter == NoteFilter.RECYCLE_BIN -> repository.getRecycledNotes()
                    state.selectedFolderId != null -> {
                        val ids = FolderTree.subtreeIds(
                            input = all.map {
                                FolderTree.Input(id = it.id, name = it.name, parentId = it.parentId)
                            },
                            id = state.selectedFolderId
                        )
                        repository.getNotesByFolders(ids)
                    }
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
            val rows = filtered
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
            when (state.sortOrder) {
                NoteSortOrder.UPDATED_DESC -> rows.sortedWith(
                    compareByDescending<NoteRow> { it.titleMatch }.thenByDescending { it.note.updatedAt }
                )
                NoteSortOrder.UPDATED_ASC -> rows.sortedWith(
                    compareByDescending<NoteRow> { it.titleMatch }.thenBy { it.note.updatedAt }
                )
                NoteSortOrder.TITLE_ASC -> rows.sortedWith(
                    compareByDescending<NoteRow> { it.titleMatch }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.note.title }
                )
                NoteSortOrder.TITLE_DESC -> rows.sortedWith(
                    compareByDescending<NoteRow> { it.titleMatch }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.note.title }
                )
            }
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

    /** [parentId] non-null creates a sub-notebook under that folder. */
    fun createFolder(name: String, parentId: String? = null) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createFolder(name = name, parentId = parentId)
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
            val resolvedTitle = if (title == "Untitled Note") {
                when (pageType) {
                    "infinite" -> nextAvailableName(folderId, "canvas")
                    "a4" -> nextAvailableName(folderId, "note")
                    else -> title
                }
            } else title
            val newNote = repository.createNote(title = resolvedTitle, folderId = folderId, pageType = pageType)
            onCreated(newNote.id)
        }
    }

    private suspend fun nextAvailableName(folderId: String?, base: String): String {
        val existing = repository.existingTitlesInFolder(folderId).toSet()
        var n = 1
        while ("$base $n" in existing) n++
        return "$base $n"
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

    fun setThemeColor(colorArgb: Int) {
        viewModelScope.launch {
            settingsRepository?.updateThemeColor(colorArgb)
        }
    }

    fun setSortOrder(order: NoteSortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
    }

    class Factory(
        private val repository: NoteRepository,
        private val settingsRepository: SettingsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteListViewModel(repository, settingsRepository) as T
        }
    }
}
