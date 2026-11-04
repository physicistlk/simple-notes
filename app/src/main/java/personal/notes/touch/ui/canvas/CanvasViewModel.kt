package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.data.repo.SettingsRepository
import personal.notes.touch.ui.settings.parsePenDefaults
import personal.notes.touch.util.BitmapUtils
import java.io.File

data class CanvasUiState(
    val selectedTool: CanvasTool = CanvasTool.PEN,
    val selectedColor: Color = Color.Black,
    val strokeWidth: Float = 6f,
    val eraserWidth: Float = 36f,
    val paperPattern: PaperPattern = PaperPattern.BLANK,
    val backgroundMode: Int = 0,
    val backgroundDensity: Int = 40,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isColorPickerOpen: Boolean = false,
    val isPatternPickerOpen: Boolean = false,
    val isPageManagerOpen: Boolean = false,
    val isThumbnailDrawerOpen: Boolean = false,
    val isToolsPanelOpen: Boolean = false,
    val activeToolsTab: Int = 0, // 0: Tools, 1: Export, 2: Settings, 3: Manager
    val dockHorizontal: Boolean = false,
    val showPencase: Boolean = true,
    val penBoxEnabled: Boolean = false,
    val penSlot1Color: Int = 0xFF000000.toInt(),
    val penSlot1Width: Float = 6f,
    val penSlot2Color: Int = 0xFFFB6443.toInt(),
    val penSlot2Width: Float = 3f,
    val stylusOnly: Boolean = false,
    val readingMode: Boolean = false,
    val doublePageMode: Boolean = false,
    val pressureSensitivity: Float = 1.0f,
    val pressureSizeEnabled: Boolean = true,
    val autoShapeEnabled: Boolean = false,
    val volumeForPageEnabled: Boolean = false,
    val drawerTab: DrawerTab = DrawerTab.THUMBNAILS,
    val bookmarkedPages: Set<Int> = emptySet(),
    val tocEntries: List<TocItem> = emptyList()
)

class CanvasViewModel(
    val noteId: String,
    val repository: NoteRepository,
    val fileStore: NoteFileStore,
    val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    init {
        if (settingsRepository != null) {
            viewModelScope.launch {
                val settings = settingsRepository.getSettingsDirect()
                val pattern = when (settings.defaultBackground) {
                    1 -> PaperPattern.GRID
                    2 -> PaperPattern.LINES
                    3 -> PaperPattern.DOTTED
                    else -> PaperPattern.BLANK
                }
                val pen = parsePenDefaults(settings.penDefaultsJson)
                _uiState.value = _uiState.value.copy(
                    paperPattern = pattern,
                    selectedColor = Color(pen.color),
                    strokeWidth = pen.strokeWidth,
                    pressureSensitivity = pen.pressureSensitivity,
                    pressureSizeEnabled = settings.pressureSizeEnabled,
                    autoShapeEnabled = settings.autoShapeEnabled,
                    volumeForPageEnabled = settings.volumeForPage,
                    penBoxEnabled = settings.showPenBox,
                    penSlot1Color = settings.penSlot1Color,
                    penSlot1Width = settings.penSlot1Width,
                    penSlot2Color = settings.penSlot2Color,
                    penSlot2Width = settings.penSlot2Width,
                    backgroundMode = settings.backgroundMode,
                    backgroundDensity = settings.backgroundDensity
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadDrawerData()
        }
    }

    val note: StateFlow<NoteEntity?> = repository.getNoteById(noteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pages: StateFlow<List<PageEntity>> = repository.getPagesForNote(noteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    fun selectTool(tool: CanvasTool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectColor(color: Color) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
    }

    fun setStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(strokeWidth = width)
    }

    fun setPaperPattern(pattern: PaperPattern) {
        _uiState.value = _uiState.value.copy(paperPattern = pattern)
    }

    fun updateUndoRedo(canUndo: Boolean, canRedo: Boolean) {
        _uiState.value = _uiState.value.copy(canUndo = canUndo, canRedo = canRedo)
    }

    fun setPageManagerOpen(isOpen: Boolean) {
        _uiState.value = _uiState.value.copy(isPageManagerOpen = isOpen)
    }

    fun toggleThumbnailDrawer(isOpen: Boolean? = null) {
        val newState = isOpen ?: !_uiState.value.isThumbnailDrawerOpen
        _uiState.value = _uiState.value.copy(
            isThumbnailDrawerOpen = newState,
            isToolsPanelOpen = if (newState) false else _uiState.value.isToolsPanelOpen
        )
    }

    fun toggleToolsPanel(isOpen: Boolean? = null) {
        val newState = isOpen ?: !_uiState.value.isToolsPanelOpen
        _uiState.value = _uiState.value.copy(
            isToolsPanelOpen = newState,
            isThumbnailDrawerOpen = if (newState) false else _uiState.value.isThumbnailDrawerOpen
        )
    }

    fun setActiveToolsTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeToolsTab = tab)
    }

    fun toggleDockOrientation() {
        _uiState.value = _uiState.value.copy(dockHorizontal = !_uiState.value.dockHorizontal)
    }

    private fun getBookmarksFile(): File = File(fileStore.getNoteDir(noteId), "bookmarks.json")
    private fun getCatalogFile(): File = File(fileStore.getNoteDir(noteId), "catalog.json")

    private fun loadDrawerData() {
        try {
            val bmFile = getBookmarksFile()
            val bookmarks = mutableSetOf<Int>()
            if (bmFile.exists()) {
                val jsonArr = JSONArray(bmFile.readText())
                for (i in 0 until jsonArr.length()) {
                    bookmarks.add(jsonArr.getInt(i))
                }
            }

            val catFile = getCatalogFile()
            val tocList = mutableListOf<TocItem>()
            if (catFile.exists()) {
                val jsonArr = JSONArray(catFile.readText())
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    tocList.add(
                        TocItem(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            pageIndex = obj.getInt("pageIndex"),
                            level = obj.optInt("level", 0)
                        )
                    )
                }
            } else {
                tocList.add(
                    TocItem(
                        title = "Chapter 1: Overview",
                        pageIndex = 0,
                        level = 0
                    )
                )
            }

            _uiState.value = _uiState.value.copy(
                bookmarkedPages = bookmarks,
                tocEntries = tocList.sortedBy { it.pageIndex }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setDrawerTab(tab: DrawerTab) {
        _uiState.value = _uiState.value.copy(drawerTab = tab)
    }

    fun toggleBookmark(pageIndex: Int) {
        val current = _uiState.value.bookmarkedPages.toMutableSet()
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
        } else {
            current.add(pageIndex)
        }
        _uiState.value = _uiState.value.copy(bookmarkedPages = current)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                current.sorted().forEach { arr.put(it) }
                getBookmarksFile().writeText(arr.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTocEntry(title: String, pageIndex: Int, level: Int = 0) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val item = TocItem(title = cleanTitle, pageIndex = pageIndex, level = level)
        val updated = (_uiState.value.tocEntries + item).sortedBy { it.pageIndex }
        _uiState.value = _uiState.value.copy(tocEntries = updated)
        saveCatalogJson(updated)
    }

    fun deleteTocEntry(id: String) {
        val updated = _uiState.value.tocEntries.filter { it.id != id }
        _uiState.value = _uiState.value.copy(tocEntries = updated)
        saveCatalogJson(updated)
    }

    private fun saveCatalogJson(list: List<TocItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                list.forEach { item ->
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("pageIndex", item.pageIndex)
                        put("level", item.level)
                    }
                    arr.put(obj)
                }
                getCatalogFile().writeText(arr.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleShowPencase(show: Boolean? = null) {
        _uiState.value = _uiState.value.copy(showPencase = show ?: !_uiState.value.showPencase)
    }

    fun toggleStylusOnly(enabled: Boolean? = null) {
        _uiState.value = _uiState.value.copy(stylusOnly = enabled ?: !_uiState.value.stylusOnly)
    }

    fun toggleReadingMode(enabled: Boolean? = null) {
        _uiState.value = _uiState.value.copy(readingMode = enabled ?: !_uiState.value.readingMode)
    }

    fun toggleDoublePageMode(enabled: Boolean? = null) {
        _uiState.value = _uiState.value.copy(doublePageMode = enabled ?: !_uiState.value.doublePageMode)
    }

    fun deleteCurrentNote() {
        viewModelScope.launch {
            repository.softDeleteNote(noteId, true)
        }
    }

    fun updateTitle(newTitle: String) {
        viewModelScope.launch {
            repository.updateTitle(noteId, newTitle.trim())
        }
    }

    suspend fun loadCurrentPageBitmap(): Bitmap? {
        val file = fileStore.getPageFile(noteId, _currentPageIndex.value)
        return BitmapUtils.loadBitmapFromFile(file)
    }

    fun saveCurrentPageBitmap(bitmap: Bitmap) {
        val pageIdx = _currentPageIndex.value
        viewModelScope.launch {
            repository.savePageBitmap(noteId, pageIdx, bitmap)
        }
    }

    fun goToPage(index: Int, currentBitmap: Bitmap?, onBitmapLoaded: (Bitmap?) -> Unit) {
        val totalPages = note.value?.pageCount ?: 1
        if (index !in 0 until totalPages || index == _currentPageIndex.value) return

        viewModelScope.launch {
            // Save current page
            if (currentBitmap != null) {
                repository.savePageBitmap(noteId, _currentPageIndex.value, currentBitmap)
            }
            _currentPageIndex.value = index
            val newBitmap = repository.loadPageBitmap(noteId, index)
            onBitmapLoaded(newBitmap)
        }
    }

    fun nextPage(currentBitmap: Bitmap?, onBitmapLoaded: (Bitmap?) -> Unit) {
        val totalPages = note.value?.pageCount ?: 1
        if (_currentPageIndex.value < totalPages - 1) {
            goToPage(_currentPageIndex.value + 1, currentBitmap, onBitmapLoaded)
        }
    }

    fun previousPage(currentBitmap: Bitmap?, onBitmapLoaded: (Bitmap?) -> Unit) {
        if (_currentPageIndex.value > 0) {
            goToPage(_currentPageIndex.value - 1, currentBitmap, onBitmapLoaded)
        }
    }

    fun addNewPage(currentBitmap: Bitmap?, onBitmapLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            // Save current page first
            if (currentBitmap != null) {
                repository.savePageBitmap(noteId, _currentPageIndex.value, currentBitmap)
            }
            val newPage = repository.addPage(noteId)
            _currentPageIndex.value = newPage.index
            onBitmapLoaded(null) // New page starts blank
        }
    }

    fun deletePage(pageIndex: Int, onBitmapLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            val totalPages = note.value?.pageCount ?: 1
            if (totalPages <= 1) return@launch

            val wasDeleted = repository.deletePage(noteId, pageIndex)
            if (wasDeleted) {
                val newIndex = when {
                    _currentPageIndex.value >= totalPages - 1 -> totalPages - 2
                    _currentPageIndex.value > pageIndex -> _currentPageIndex.value - 1
                    else -> _currentPageIndex.value
                }.coerceAtLeast(0)

                _currentPageIndex.value = newIndex
                val newBitmap = repository.loadPageBitmap(noteId, newIndex)
                onBitmapLoaded(newBitmap)
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
            return CanvasViewModel(noteId, repository, fileStore, settingsRepository) as T
        }
    }
}
