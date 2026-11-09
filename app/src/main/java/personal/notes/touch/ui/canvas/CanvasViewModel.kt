package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    val pageTurningDirection: Int = 0,
    val pageTurningMode: Int = 0,
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
    val tocEntries: List<TocItem> = emptyList(),
    val showTabs: Boolean = true,
    val fixPreviewPage: Boolean = false,
    val horizontalResistance: Float = 0.4f,
    val minimalInfiniteZoom: Float = 0.20f,
    val autoCenter: Boolean = true,
    val autoSaveIntervalMs: Int = 1000
)

/**
 * The size chips act on the tool that is currently selected. The eraser has
 * its own (much larger) range, so storing its size in [strokeWidth] would
 * either make the pen unusable or the eraser invisible.
 */
fun CanvasUiState.withStrokeWidth(width: Float): CanvasUiState =
    if (selectedTool == CanvasTool.ERASER) copy(eraserWidth = width) else copy(strokeWidth = width)

/** Size the chips should highlight and the canvas should apply for the active tool. */
fun CanvasUiState.activeStrokeWidth(): Float =
    if (selectedTool == CanvasTool.ERASER) eraserWidth else strokeWidth

class CanvasViewModel(
    val noteId: String,
    val repository: NoteRepository,
    val fileStore: NoteFileStore,
    val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private var defaultPaperPattern: PaperPattern = PaperPattern.BLANK
    private var lastPenDefaultsJson: String? = null

    init {
        if (settingsRepository != null) {
            viewModelScope.launch {
                settingsRepository.getSettings().collect { nullableSettings ->
                    val settings = nullableSettings ?: return@collect
                    val pattern = when (settings.defaultBackground) {
                        1 -> PaperPattern.GRID
                        2 -> PaperPattern.LINES
                        3 -> PaperPattern.DOTTED
                        else -> PaperPattern.BLANK
                    }
                    defaultPaperPattern = pattern
                    // Only seed from global settings until a page-owned pattern is
                    // applied (syncPagePattern); the page row wins once it's known.
                    if (!pagePatternHydrated) {
                        _uiState.value = _uiState.value.copy(paperPattern = pattern)
                    }
                    val penDefaultsJson = settings.penDefaultsJson
                    val penDefaultsChanged = penDefaultsJson != lastPenDefaultsJson
                    if (penDefaultsChanged) {
                        lastPenDefaultsJson = penDefaultsJson
                        val pen = parsePenDefaults(penDefaultsJson)
                        _uiState.value = _uiState.value.copy(
                            selectedColor = Color(pen.color),
                            strokeWidth = pen.strokeWidth,
                            pressureSensitivity = pen.pressureSensitivity
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        pressureSizeEnabled = settings.pressureSizeEnabled,
                        autoShapeEnabled = settings.autoShapeEnabled,
                        volumeForPageEnabled = settings.volumeForPage,
                        penBoxEnabled = settings.showPenBox,
                        penSlot1Color = settings.penSlot1Color,
                        penSlot1Width = settings.penSlot1Width,
                        penSlot2Color = settings.penSlot2Color,
                        penSlot2Width = settings.penSlot2Width,
                        backgroundMode = settings.backgroundMode,
                        backgroundDensity = settings.backgroundDensity,
                        pageTurningDirection = settings.pageTurningDirection,
                        pageTurningMode = settings.pageTurningMode,
                        doublePageMode = settings.doublePage,
                        autoCenter = settings.autoCenter,
                        autoSaveIntervalMs = settings.autoSaveIntervalMs
                    )
                }
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

    val allNotesMap: StateFlow<Map<String, String>> = repository.getAllNotes()
        .map { list ->
            list.associate { it.id to (it.title.ifBlank { "Untitled Note" }) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val pages: StateFlow<List<PageEntity>> = repository.getPagesForNote(noteId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private var pagePatternHydrated = false
    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    fun selectTool(tool: CanvasTool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectColor(color: Color) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
    }

    fun setStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.withStrokeWidth(width)
    }

    fun setPaperPattern(pattern: PaperPattern) {
        _uiState.value = _uiState.value.copy(paperPattern = pattern)
        // Persist per page so the pattern survives reopen (defect F9).
        pagePatternHydrated = true
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePagePattern(noteId, _currentPageIndex.value, pattern.code)
        }
    }

    /**
     * Apply a page-row pattern on page load/switch. `code != 0` means the page
     * owns its pattern (defect F9 persistence); 0 = unset → app default stays.
     */
    fun syncPagePattern(code: Int) {
        if (code == 0) {
            if (_uiState.value.paperPattern != defaultPaperPattern) {
                _uiState.value = _uiState.value.copy(paperPattern = defaultPaperPattern)
            }
            return
        }
        pagePatternHydrated = true
        val pattern = PaperPattern.fromCode(code)
        if (_uiState.value.paperPattern != pattern) {
            _uiState.value = _uiState.value.copy(paperPattern = pattern)
        }
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

    /**
     * Closes the page drawer / tools panel / page manager because the user
     * touched the page again. No-op (and no state emission) when none is open.
     */
    fun dismissCanvasMenus() {
        CanvasMenus.dismiss(_uiState.value)?.let { _uiState.value = it }
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

    fun setPageTurningDirection(direction: Int) {
        _uiState.value = _uiState.value.copy(pageTurningDirection = direction)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updatePageTurningDirection(direction)
        }
    }

    fun setPageTurningMode(mode: Int) {
        if (mode == 0) {
            debouncedSaveJob?.cancel()
            debouncedSaveJob = null
            pendingSave = null
            canvasPageIndex = -1
        }
        _uiState.value = _uiState.value.copy(pageTurningMode = mode)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updatePageTurningMode(mode)
        }
    }

    fun toggleBookmark(pageIndex: Int) {
        val current = _uiState.value.bookmarkedPages.toMutableSet()
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
        } else {
            current.add(pageIndex)
        }
        _uiState.value = _uiState.value.copy(bookmarkedPages = current)
        saveBookmarksJson(current)
    }

    private fun saveBookmarksJson(bookmarks: Set<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                bookmarks.sorted().forEach { arr.put(it) }
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

    fun togglePenBox(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.penBoxEnabled
        _uiState.value = _uiState.value.copy(penBoxEnabled = next)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updateShowPenBox(next)
        }
    }

    fun toggleStylusOnly(enabled: Boolean? = null) {
        _uiState.value = _uiState.value.copy(stylusOnly = enabled ?: !_uiState.value.stylusOnly)
    }

    fun toggleAutoShape(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.autoShapeEnabled
        _uiState.value = _uiState.value.copy(autoShapeEnabled = next)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updateAutoShapeEnabled(next)
        }
    }

    fun toggleReadingMode(enabled: Boolean? = null) {
        _uiState.value = _uiState.value.copy(readingMode = enabled ?: !_uiState.value.readingMode)
    }

    fun toggleDoublePageMode(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.doublePageMode
        _uiState.value = _uiState.value.copy(doublePageMode = next)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updateDoublePage(next)
        }
    }

    fun setShowTabs(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTabs = show)
    }

    fun setFixPreviewPage(fix: Boolean) {
        _uiState.value = _uiState.value.copy(fixPreviewPage = fix)
    }

    fun setHorizontalResistance(resistance: Float) {
        _uiState.value = _uiState.value.copy(horizontalResistance = resistance)
    }

    fun setMinimalInfiniteZoom(zoom: Float) {
        _uiState.value = _uiState.value.copy(minimalInfiniteZoom = zoom)
    }

    var isNoteDeleted: Boolean = false
        private set

    fun deleteCurrentNote() {
        isNoteDeleted = true
        debouncedSaveJob?.cancel()
        debouncedSaveJob = null
        pendingSave = null
        viewModelScope.launch {
            repository.softDeleteNote(noteId, true)
        }
    }

    suspend fun deleteCurrentNoteSuspend() {
        isNoteDeleted = true
        debouncedSaveJob?.cancel()
        debouncedSaveJob = null
        pendingSave = null
        withContext(Dispatchers.IO) {
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

    /**
     * True when the page bitmap currently shown in the canvas view came from
     * this session's disk load (initial open or a page flip). False means the
     * view's backing bitmap was minted blank in this session: either a fresh
     * note (no page file yet — saving is wanted) or a session where the disk
     * load never applied to the view.
     */
    private var pageLoadedFromDisk = false

    fun setPageLoadedFromDisk(loaded: Boolean) {
        pageLoadedFromDisk = loaded
    }

    /**
     * Index of the page whose content the drawing canvas currently holds.
     *
     * This is NOT always [_currentPageIndex]: in continuous reading mode,
     * scrolling keeps updating the current page while the hidden canvas keeps
     * whatever page was loaded into it last. A save must name the page the
     * bitmap actually holds, or leaving the note would write e.g. page 1's
     * strokes over the page the reader had scrolled to.
     */
    private var canvasPageIndex = 0

    /** Records the page whose content the canvas is holding right now. */
    fun setCanvasPageIndex(index: Int) {
        canvasPageIndex = index
    }

    /** F-10: true when [index] is a page rendered from a vector PDF source. */
    private fun pageIsVectorPdf(index: Int): Boolean {
        val page = pages.value.getOrNull(index) ?: return false
        return page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
    }

    /** F-10: stroke-layer bitmap of [index] (vector pages keep it in a separate file). */
    suspend fun loadPageStrokes(index: Int): Bitmap? = repository.loadPageStrokes(noteId, index)

    /** F-10: persists the stroke layer the way the page's type dictates. */
    private suspend fun saveContentLayer(index: Int, bitmap: Bitmap) {
        if (pageIsVectorPdf(index)) {
            repository.savePageStrokes(noteId, index, bitmap)
        } else {
            repository.savePageBitmap(noteId, index, bitmap)
        }
    }

    private suspend fun loadContentLayer(index: Int): Bitmap? =
        if (pageIsVectorPdf(index)) repository.loadPageStrokes(noteId, index)
        else repository.loadPageBitmap(noteId, index)

    private var debouncedSaveJob: kotlinx.coroutines.Job? = null

    /**
     * [pageIndex] defaults to the page the canvas is holding right now and is
     * evaluated at the call site. Both halves matter: a debounced save runs up
     * to 1.5 s later, and in continuous reading the "current" page moves with
     * scrolling while the canvas keeps its own page. Capturing the canvas page
     * here is what keeps page N's strokes out of page N+1's file.
     */
    fun scheduleDebouncedSave(
        bitmap: Bitmap,
        delayMs: Long = 1500L,
        pageIndex: Int = canvasPageIndex
    ) {
        debouncedSaveJob?.cancel()
        pendingSave = pageIndex to bitmap
        debouncedSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            // Do NOT call the public save here: it cancels the pending job,
            // and this coroutine IS that job (the save would cancel itself
            // before the write and silently drop the stroke).
            persistPageBitmap(pageIndex, bitmap)
        }
    }

    /** Content of the debounced save that has not been written yet. */
    private var pendingSave: Pair<Int, Bitmap>? = null

    /**
     * Writes a pending debounced save on a caller-provided scope.
     *
     * Used when the screen leaves composition (system back, tab close, note
     * switch, opening the text editor): `viewModelScope` is cancelled at that
     * moment, so the pending job would never write. Called with the bitmap and
     * page index captured when the stroke finished, so the write still lands on
     * the right page even though the screen is already gone.
     */
    fun flushPendingSave(scope: kotlinx.coroutines.CoroutineScope) {
        if (isNoteDeleted) {
            pendingSave = null
            debouncedSaveJob?.cancel()
            debouncedSaveJob = null
            return
        }
        val pending = pendingSave ?: return
        pendingSave = null
        debouncedSaveJob?.cancel()
        debouncedSaveJob = null
        scope.launch { persistPageBitmap(pending.first, pending.second) }
    }

    /** Cancels any pending debounced save and writes [bitmap] now. */
    suspend fun saveCurrentPageBitmapDirect(
        bitmap: Bitmap,
        pageIndex: Int = canvasPageIndex,
        force: Boolean = false
    ) {
        debouncedSaveJob?.cancel()
        debouncedSaveJob = null
        pendingSave = null
        persistPageBitmap(pageIndex, bitmap, force = force)
    }

    fun saveCurrentPageBitmap(
        bitmap: Bitmap,
        pageIndex: Int = canvasPageIndex
    ) {
        debouncedSaveJob?.cancel()
        pendingSave = null
        viewModelScope.launch {
            persistPageBitmap(pageIndex, bitmap)
        }
    }

    private suspend fun persistPageBitmap(pageIdx: Int, bitmap: Bitmap, force: Boolean = false) {
        // The view no longer recycles backing bitmaps, but a stale job can
        // still outlive one; never hand a recycled bitmap to the encoder.
        if (bitmap.isRecycled) return

        // In Continuous Mode, the hidden single-page DrawingCanvasView must never overwrite
        // disk files; ContinuousMode ink is persisted exclusively by ContinuousInkController.
        val isInfinite = (note.value?.pageType ?: "infinite") == "infinite"
        val isContinuous = !isInfinite && _uiState.value.pageTurningMode == 0
        if (!force && isContinuous) return

        val totalPages = note.value?.pageCount ?: 1
        if (pageIdx !in 0 until totalPages) return
        val guardFile = if (pageIsVectorPdf(pageIdx)) {
            fileStore.getStrokesFile(noteId, pageIdx)
        } else {
            fileStore.getPageFile(noteId, pageIdx)
        }
        if (!pageLoadedFromDisk && guardFile.exists()) return
        saveContentLayer(pageIdx, bitmap)
        if (pendingSave?.second === bitmap) pendingSave = null
    }

    fun setCurrentPageIndex(index: Int) {
        val totalPages = note.value?.pageCount ?: 1
        if (index in 0 until totalPages) {
            _currentPageIndex.value = index
        }
    }

    fun goToPage(index: Int, currentBitmap: Bitmap?, onBitmapLoaded: (Bitmap?) -> Unit) {
        goToPage(index, currentBitmap, forceReload = false, onBitmapLoaded = onBitmapLoaded)
    }

    fun goToPage(
        index: Int,
        currentBitmap: Bitmap?,
        forceReload: Boolean = false,
        onBitmapLoaded: (Bitmap?) -> Unit
    ) {
        val totalPages = note.value?.pageCount ?: 1
        if (index !in 0 until totalPages) return
        if (index == _currentPageIndex.value && !forceReload) return

        viewModelScope.launch {
            // Save the page the canvas holds (not necessarily the scrolled
            // page in continuous reading mode).
            if (currentBitmap != null) {
                saveContentLayer(canvasPageIndex, currentBitmap)
            }
            _currentPageIndex.value = index
            canvasPageIndex = index
            val newBitmap = loadContentLayer(index)
            // The save above guarded the OLD page with the OLD flag state;
            // from here on the flag describes the NEW page's load result.
            pageLoadedFromDisk = newBitmap != null
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
            // Save the page the canvas holds first.
            if (currentBitmap != null) {
                saveContentLayer(canvasPageIndex, currentBitmap)
            }
            val newPage = repository.addPage(noteId)
            _currentPageIndex.value = newPage.index
            canvasPageIndex = newPage.index
            pageLoadedFromDisk = false // new page starts blank, no disk load
            onBitmapLoaded(null) // New page starts blank
        }
    }

    fun deletePage(
        pageIndex: Int,
        isContinuous: Boolean = false,
        currentBitmap: Bitmap? = null,
        onBitmapLoaded: (Bitmap?) -> Unit
    ) {
        viewModelScope.launch {
            val totalPages = note.value?.pageCount ?: 1
            if (totalPages <= 1) return@launch

            // Cancel any pending debounced save so it doesn't write to stale indices
            debouncedSaveJob?.cancel()
            debouncedSaveJob = null
            pendingSave = null

            // If deleting another page while single-page canvas has content, persist it first
            if (!isContinuous && currentBitmap != null && canvasPageIndex >= 0 && canvasPageIndex != pageIndex) {
                saveContentLayer(canvasPageIndex, currentBitmap)
            }

            val wasDeleted = repository.deletePage(noteId, pageIndex)
            if (wasDeleted) {
                // Shift bookmarks
                val oldBookmarks = _uiState.value.bookmarkedPages
                val newBookmarks = oldBookmarks.mapNotNull { bIdx ->
                    when {
                        bIdx == pageIndex -> null
                        bIdx > pageIndex -> bIdx - 1
                        else -> bIdx
                    }
                }.toSet()
                _uiState.value = _uiState.value.copy(bookmarkedPages = newBookmarks)
                saveBookmarksJson(newBookmarks)

                // Shift TOC entries
                val oldToc = _uiState.value.tocEntries
                val newToc = oldToc.mapNotNull { item ->
                    when {
                        item.pageIndex == pageIndex -> null
                        item.pageIndex > pageIndex -> item.copy(pageIndex = item.pageIndex - 1)
                        else -> item
                    }
                }
                _uiState.value = _uiState.value.copy(tocEntries = newToc)
                saveCatalogJson(newToc)

                val newIndex = when {
                    _currentPageIndex.value >= totalPages - 1 -> totalPages - 2
                    _currentPageIndex.value > pageIndex -> _currentPageIndex.value - 1
                    else -> _currentPageIndex.value
                }.coerceAtLeast(0)

                _currentPageIndex.value = newIndex
                if (!isContinuous) {
                    canvasPageIndex = newIndex
                    val newBitmap = loadContentLayer(newIndex)
                    pageLoadedFromDisk = newBitmap != null
                    onBitmapLoaded(newBitmap)
                } else {
                    onBitmapLoaded(null)
                }
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
