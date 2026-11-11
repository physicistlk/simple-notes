package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    // Pen mode by default: the pen writes, the hand scrolls and uses the
    // tools. It switches off when the user wants to write with a finger.
    val stylusOnly: Boolean = true,
    /** True once a stylus has been seen in this session. */
    val stylusDetected: Boolean = false,
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
    /** True while the pinch-to-zoom is locked: two fingers only move the page. */
    val zoomLocked: Boolean = false,
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

/**
 * Where a note reopens: the saved page clamped into the pages that still
 * exist ("reopen on the page you left", schema 13).
 *
 * Pure on purpose — the clamp is the only rule that can strand a reader, so it
 * is unit-tested directly instead of through a Room/Compose stack:
 * - no pages at all → 0 (the canvas has nothing to show; it must not go
 *   negative and must not crash),
 * - a negative saved value (legacy/garbage row) → 0,
 * - a saved page past the end (pages were deleted since) → the last page,
 * - otherwise the saved page itself.
 */
object PageRestore {
    fun restoredPageIndex(saved: Int, pageCount: Int): Int {
        val lastPage = (pageCount - 1).coerceAtLeast(0)
        return saved.coerceIn(0, lastPage)
    }
}

@OptIn(FlowPreview::class)
class CanvasViewModel(
    val noteId: String,
    val repository: NoteRepository,
    val fileStore: NoteFileStore,
    val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private var defaultPaperPattern: PaperPattern = PaperPattern.BLANK
    private var lastPenDefaultsJson: String? = null

    /**
     * Workspace preferences that outlive the screen (tab bar state, dock
     * orientation). They are UI choices, not note data, so they live in a
     * small file instead of the settings table.
     */
    private val uiPrefs = fileStore.uiPrefs()

    /** True once the user picked stylus mode by hand (auto-switch stops). */
    private var stylusOnlyChosen = false

    private var pagePatternHydrated = false
    private val _uiState = MutableStateFlow(CanvasUiState())
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

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

    /**
     * True once [restoreLastPage] has resolved the page the note was closed on.
     * Until then nothing may be written: on a slow cold start the initial
     * `0` would otherwise reach the debounced saver before the restore read
     * has landed and overwrite the remembered page.
     */
    private var lastPageRestored = false

    /**
     * Scope for the last page write when the ViewModel is cleared. [onCleared]
     * runs after `viewModelScope` is cancelled, so the debounced saver can no
     * longer run there; this scope is independent of the ViewModel's lifecycle
     * and is used only for that single, bounded write.
     */
    private val finalPageSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * "Reopen on the page you left" (schema 13), restore half.
     *
     * The saved index lives on the note row (`lastPageIndex`); the number of
     * pages that still exist lives in `pages`. Restoring therefore needs both:
     * wait for the note row, then for the first non-empty page list, then clamp
     * with [PageRestore]. A note whose page rows are genuinely gone times out
     * into the 0-page clamp instead of hanging.
     */
    private fun restoreLastPage() {
        viewModelScope.launch {
            val noteRow = note.filterNotNull().first()
            val loadedPages = withTimeoutOrNull(PAGE_RESTORE_TIMEOUT_MS) {
                pages.first { it.isNotEmpty() }
            }
            val pageCount = loadedPages?.size ?: pages.value.size
            val saved = PageRestore.restoredPageIndex(noteRow.lastPageIndex, pageCount)
            // Only while the reader is still on the page it opened on: if the
            // user already navigated (continuous scrolling can move the index
            // before the DB read returns), their page wins over the saved one.
            if (_currentPageIndex.value == 0) {
                _currentPageIndex.value = saved
            }
            lastPageRestored = true
            observePageIndexForSave()
        }
    }

    /**
     * "Reopen on the page you left", save half: one central saver instead of a
     * write at every assignment of [_currentPageIndex].
     *
     * It covers every path that moves the page — [setCurrentPageIndex] (the
     * continuous reader's scroll position), [goToPage] / next / previous, and
     * the index shifts of insert and delete — because they all end in the same
     * StateFlow. The 400 ms debounce keeps a scroll burst to one UPDATE, which
     * is still well inside the ~500 ms budget for "turning a page is saved".
     */
    private fun observePageIndexForSave() {
        viewModelScope.launch {
            _currentPageIndex
                .debounce(PAGE_SAVE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { persistLastPage(it) }
        }
    }

    /** Writes one page index to the note row, unless the note is in the bin. */
    private suspend fun persistLastPage(pageIndex: Int) {
        if (pageIndex < 0) return
        if (isNoteDeleted) return
        // Deleted is a column on the row, not a session flag: a note opened
        // from the recycle bin must not remember a page either.
        if (note.value?.deleted == true) return
        repository.saveLastPage(noteId, pageIndex)
    }

    /**
     * Final page write when the screen is left.
     *
     * [onCleared] runs after `viewModelScope` is cancelled, so a page turned
     * less than [PAGE_SAVE_DEBOUNCE_MS] before leaving would be dropped. The
     * write is launched on [finalPageSaveScope] (not the cancelled
     * `viewModelScope`) and awaited for at most
     * [FINAL_PAGE_SAVE_TIMEOUT_MS] so it normally lands before the process can
     * die — the wait is hard-bounded so it can never hold the main thread long
     * enough to matter. A timed-out write is left running on IO (the scope is
     * not cancelled here), because cancelling would lose exactly the value
     * this method exists to keep.
     */
    override fun onCleared() {
        if (lastPageRestored && !isNoteDeleted && note.value?.deleted != true) {
            val pageIndex = _currentPageIndex.value
            if (pageIndex >= 0) {
                val write = finalPageSaveScope.launch {
                    repository.saveLastPage(noteId, pageIndex)
                }
                runBlocking {
                    withTimeoutOrNull(FINAL_PAGE_SAVE_TIMEOUT_MS) { write.join() }
                }
            }
        }
        super.onCleared()
    }

    init {
        // Workspace preferences that survive the screen: the open-notes tab
        // bar state and the pen dock orientation.
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                showTabs = uiPrefs.getBoolean(KEY_SHOW_TABS, true),
                dockHorizontal = uiPrefs.getBoolean(KEY_DOCK_HORIZONTAL, false),
                showPencase = uiPrefs.getBoolean(KEY_SHOW_PENCASE, true),
                readingMode = uiPrefs.getBoolean(KEY_READING_MODE, false),
                zoomLocked = uiPrefs.getBoolean(KEY_ZOOM_LOCKED, false),
                fixPreviewPage = uiPrefs.getBoolean(KEY_FIX_PREVIEW_PAGE, false),
                horizontalResistance = uiPrefs.getFloat(KEY_HORIZONTAL_RESISTANCE, 0.4f),
                minimalInfiniteZoom = uiPrefs.getFloat(KEY_MINIMAL_INFINITE_ZOOM, 0.20f)
            )
        }
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
        restoreLastPage()
    }

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
        val next = !_uiState.value.dockHorizontal
        _uiState.value = _uiState.value.copy(dockHorizontal = next)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putBoolean(KEY_DOCK_HORIZONTAL, next) }
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

    /**
     * Switches the page turning mode for this session only.
     *
     * Inserting a layer or picking a lasso moves the note into single-page
     * mode; that is an internal step and must not overwrite the user's
     * preference for the next time they open the note.
     */
    fun setPageTurningModeTransient(mode: Int) {
        if (mode == 0) {
            debouncedSaveJob?.cancel()
            debouncedSaveJob = null
            pendingSave = null
            canvasPageIndex = -1
        }
        _uiState.value = _uiState.value.copy(pageTurningMode = mode)
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
        val next = show ?: !_uiState.value.showPencase
        _uiState.value = _uiState.value.copy(showPencase = next)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putBoolean(KEY_SHOW_PENCASE, next) }
    }

    fun togglePenBox(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.penBoxEnabled
        _uiState.value = _uiState.value.copy(penBoxEnabled = next)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updateShowPenBox(next)
        }
    }

    /**
     * Stylus mode: only the pen writes, the hand is for buttons and tools.
     *
     * It switches itself on as soon as a stylus is actually used, so a
     * tablet with a pen never draws a stray palm stroke. Once the user has
     * chosen the mode by hand, that choice wins and the auto switch stops.
     */
    fun toggleStylusOnly(enabled: Boolean? = null) {
        stylusOnlyChosen = true
        _uiState.value = _uiState.value.copy(stylusOnly = enabled ?: !_uiState.value.stylusOnly)
    }

    /** Called when a stylus event arrives: arm stylus mode for this session. */
    fun onStylusDetected() {
        val state = _uiState.value
        if (state.stylusDetected && state.stylusOnly) return
        _uiState.value = state.copy(
            stylusDetected = true,
            stylusOnly = if (stylusOnlyChosen) state.stylusOnly else true
        )
    }

    fun toggleAutoShape(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.autoShapeEnabled
        _uiState.value = _uiState.value.copy(autoShapeEnabled = next)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updateAutoShapeEnabled(next)
        }
    }

    fun toggleReadingMode(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.readingMode
        _uiState.value = _uiState.value.copy(readingMode = next)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putBoolean(KEY_READING_MODE, next) }
    }

    fun toggleDoublePageMode(enabled: Boolean? = null) {
        val next = enabled ?: !_uiState.value.doublePageMode
        _uiState.value = _uiState.value.copy(doublePageMode = next)
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository?.updateDoublePage(next)
        }
    }

    fun setZoomLocked(locked: Boolean) {
        _uiState.value = _uiState.value.copy(zoomLocked = locked)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putBoolean(KEY_ZOOM_LOCKED, locked) }
    }

    fun setShowTabs(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTabs = show)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putBoolean(KEY_SHOW_TABS, show) }
    }

    fun setFixPreviewPage(fix: Boolean) {
        _uiState.value = _uiState.value.copy(fixPreviewPage = fix)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putBoolean(KEY_FIX_PREVIEW_PAGE, fix) }
    }

    fun setHorizontalResistance(resistance: Float) {
        _uiState.value = _uiState.value.copy(horizontalResistance = resistance)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putFloat(KEY_HORIZONTAL_RESISTANCE, resistance) }
    }

    fun setMinimalInfiniteZoom(zoom: Float) {
        _uiState.value = _uiState.value.copy(minimalInfiniteZoom = zoom)
        viewModelScope.launch(Dispatchers.IO) { uiPrefs.putFloat(KEY_MINIMAL_INFINITE_ZOOM, zoom) }
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
    /**
     * Writes a page bitmap to disk.
     *
     * The bitmap is copied first: the encoder runs on another thread while
     * the canvas keeps drawing into the same bitmap, and a torn page file
     * loses the whole page's ink.
     */
    private suspend fun saveContentLayer(index: Int, bitmap: Bitmap) {
        val copy = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } ?: return
        saveContentLayerDirect(index, copy)
        copy.recycle()
    }

    private suspend fun saveContentLayerDirect(index: Int, bitmap: Bitmap) {
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
        // Continuous reading is available for every note type, so the hidden
        // canvas must step aside whenever the reader is on.
        val isContinuous = _uiState.value.pageTurningMode == 0
        if (!force && isContinuous) return

        val totalPages = note.value?.pageCount ?: 1
        if (pageIdx !in 0 until totalPages) return
        val guardFile = if (pageIsVectorPdf(pageIdx)) {
            fileStore.getStrokesFile(noteId, pageIdx)
        } else {
            fileStore.getPageFile(noteId, pageIdx)
        }
        if (!pageLoadedFromDisk && guardFile.exists()) return
        // The encoder runs off the main thread while the canvas keeps drawing
        // into the same bitmap: encode a copy so the file can never be torn.
        val copy = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } ?: return
        // The copy is already made here; do not copy a second time.
        saveContentLayerDirect(pageIdx, copy)
        copy.recycle()
        if (pendingSave?.second === bitmap) pendingSave = null
    }

    /**
     * Stores a raster page background (a page picked out of a PDF) without
     * touching the page's ink: the picture lives in its own file and is
     * drawn under the annotations.
     */
    suspend fun savePageBackgroundOverride(pageIndex: Int, bitmap: Bitmap) {
        val file = fileStore.getPageBackgroundFile(noteId, pageIndex)
        withContext(Dispatchers.IO) {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                java.io.FileOutputStream(tmp).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Loads the raster background override of a page, if there is one. */
    suspend fun loadPageBackgroundOverride(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileStore.getPageBackgroundFile(noteId, pageIndex)
        if (!file.exists()) return@withContext null
        personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file)
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

    fun insertNewPage(
        atIndex: Int? = null,
        isContinuous: Boolean = false,
        currentBitmap: Bitmap? = null,
        onBitmapLoaded: (Bitmap?) -> Unit
    ) {
        viewModelScope.launch {
            // Cancel any pending debounced save
            debouncedSaveJob?.cancel()
            debouncedSaveJob = null
            pendingSave = null

            // Save the page the canvas holds in single-page mode
            if (!isContinuous && currentBitmap != null && canvasPageIndex >= 0) {
                saveContentLayer(canvasPageIndex, currentBitmap)
            }

            val totalPages = note.value?.pageCount ?: 1
            val targetIndex = (atIndex ?: (_currentPageIndex.value + 1)).coerceIn(0, totalPages)
            val newPage = repository.insertPage(noteId, targetIndex)

            // Shift bookmarks at or after targetIndex
            val oldBookmarks = _uiState.value.bookmarkedPages
            val newBookmarks = oldBookmarks.map { bIdx ->
                if (bIdx >= targetIndex) bIdx + 1 else bIdx
            }.toSet()
            _uiState.value = _uiState.value.copy(bookmarkedPages = newBookmarks)
            saveBookmarksJson(newBookmarks)

            // Shift TOC entries at or after targetIndex
            val oldToc = _uiState.value.tocEntries
            val newToc = oldToc.map { item ->
                if (item.pageIndex >= targetIndex) item.copy(pageIndex = item.pageIndex + 1) else item
            }
            _uiState.value = _uiState.value.copy(tocEntries = newToc)
            saveCatalogJson(newToc)

            _currentPageIndex.value = newPage.index
            canvasPageIndex = newPage.index
            pageLoadedFromDisk = false // New page starts blank
            onBitmapLoaded(null)
        }
    }

    fun addNewPage(currentBitmap: Bitmap?, onBitmapLoaded: (Bitmap?) -> Unit) {
        insertNewPage(
            atIndex = null,
            isContinuous = false,
            currentBitmap = currentBitmap,
            onBitmapLoaded = onBitmapLoaded
        )
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

    private companion object {
        /** Quiet window before a page change is written to the note row. */
        const val PAGE_SAVE_DEBOUNCE_MS = 400L

        /** Max wait for the first non-empty page list when restoring. */
        const val PAGE_RESTORE_TIMEOUT_MS = 2_000L

        /** Upper bound on the blocking final write in [onCleared]. */
        const val FINAL_PAGE_SAVE_TIMEOUT_MS = 250L

        const val KEY_SHOW_TABS = "show_tabs"
        const val KEY_DOCK_HORIZONTAL = "dock_horizontal"
        const val KEY_SHOW_PENCASE = "show_pencase"
        const val KEY_READING_MODE = "reading_mode"
        const val KEY_ZOOM_LOCKED = "zoom_locked"
        const val KEY_FIX_PREVIEW_PAGE = "fix_preview_page"
        const val KEY_HORIZONTAL_RESISTANCE = "horizontal_resistance"
        const val KEY_MINIMAL_INFINITE_ZOOM = "minimal_infinite_zoom"
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
