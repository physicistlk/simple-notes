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
import personal.notes.touch.data.db.entity.FolderEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.repo.NoteRepository

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
    val isSearchOpen: Boolean = false
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

    val notes: StateFlow<List<NoteEntity>> = _uiState
        .flatMapLatest { state ->
            when {
                state.searchQuery.isNotBlank() -> repository.searchNotes(state.searchQuery.trim())
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

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchOpen = isOpen,
            searchQuery = if (!isOpen) "" else _uiState.value.searchQuery
        )
    }

    fun setFilter(filter: NoteFilter) {
        _uiState.value = _uiState.value.copy(
            currentFilter = filter,
            selectedFolderId = null,
            searchQuery = ""
        )
    }

    fun selectFolder(folderId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedFolderId = folderId,
            currentFilter = NoteFilter.ALL,
            searchQuery = ""
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
