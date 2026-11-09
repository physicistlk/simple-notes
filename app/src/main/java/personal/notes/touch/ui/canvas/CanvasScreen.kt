package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.runtime.mutableStateMapOf
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BorderClear
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import personal.notes.touch.R
import personal.notes.touch.TouchNotesApp
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.ui.theme.OrangePrimary
import personal.notes.touch.util.ClipboardHelper
import personal.notes.touch.util.PdfDocumentCache
import personal.notes.touch.util.PdfImportManager
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfTextSelectionInfo
import personal.notes.touch.util.PdfVectorImporter

val TouchNotesColors = listOf(
    Color(0xFF000000), // Black
    Color(0xFFFB6443), // TouchNotes Orange
    Color(0xFFD32F2F), // Crimson Red
    Color(0xFF1565C0), // Royal Blue
    Color(0xFF0097A7), // Cyan/Teal
    Color(0xFF388E3C), // Forest Green
    Color(0xFFFFB300), // Warm Amber
    Color(0xFF8E24AA)  // Purple
)

val PaletteColors = TouchNotesColors

val TouchNotesWidthPresets = listOf(
    2.5f to "Thin",
    6f to "Regular",
    12f to "Medium",
    24f to "Thick"
)

val WidthPresets = TouchNotesWidthPresets

/**
 * Eraser sizes are in canvas pixels like the pen, but much larger: the eraser
 * has to clear a visible swath, not a hairline. Default (36) stays in the list
 * so the selected chip is always one of them.
 */
val TouchNotesEraserPresets = listOf(
    12f to "Thin",
    24f to "Medium",
    36f to "Thick",
    56f to "X-Thick"
)

/** Continuous reader zoom limits: below 1 the pages are smaller than fit. */
private const val MIN_CONTINUOUS_SCALE = 0.4f
private const val MAX_CONTINUOUS_SCALE = 4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel,
    onBackClick: () -> Unit,
    onOpenEditorClick: (String) -> Unit,
    openNotes: List<String> = emptyList(),
    onSwitchNote: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onSettingsClick: (() -> Unit)? = null,
    onFlashcardsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val note by viewModel.note.collectAsState()
    val noteTitlesMap by viewModel.allNotesMap.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Defect F9: hydrate the per-page paper pattern from the page row on
    // page load / switch so the Template (Paper Pattern) choice survives reopen.
    LaunchedEffect(pages, currentPageIndex) {
        viewModel.syncPagePattern(pages.getOrNull(currentPageIndex)?.paperPattern ?: 0)
    }
    val scope = rememberCoroutineScope()

    var canvasViewRef by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var selectedImageIndex by remember { mutableStateOf(-1) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportTarget by remember { mutableStateOf<personal.notes.touch.ui.export.ExportTarget?>(null) }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var showPdfPagePicker by remember { mutableStateOf(false) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPdfPageCount by remember { mutableStateOf(0) }
    var pendingPdfPageIndex by remember { mutableStateOf("1") }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showShapesMenu by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var layersTick by remember { mutableStateOf(0) }
    var showAddChapterDialog by remember { mutableStateOf(false) }
    var newChapterTitle by remember { mutableStateOf("") }
    var newTitleText by remember { mutableStateOf("") }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var textInputText by remember { mutableStateOf("") }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var pageToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var isPencaseCollapsed by remember { mutableStateOf(false) }

    val isInfinite = (note?.pageType ?: "infinite") == "infinite"
    val isContinuous = !isInfinite && uiState.pageTurningMode == 0
    val isHorizontal = uiState.pageTurningDirection == 1

    var continuousScale by remember { mutableFloatStateOf(1f) }
    var continuousPanX by remember { mutableFloatStateOf(0f) }
    var continuousPanY by remember { mutableFloatStateOf(0f) }

    val showTabsState = uiState.showTabs
    val fixPreviewPageState = uiState.fixPreviewPage
    val horizontalResistance = uiState.horizontalResistance
    val minimalInfiniteZoom = uiState.minimalInfiniteZoom

    // F-10 PDF text selection: whether the current vector page has extractable
    // text, whether the Text tool is in selection mode, and the live selection.
    var pdfTextAvailable by remember { mutableStateOf(false) }
    var pdfTextMode by remember { mutableStateOf(false) }
    var pdfSelection by remember { mutableStateOf<PdfTextSelectionInfo?>(null) }

    val totalPages = note?.pageCount ?: pages.size.coerceAtLeast(1)

    // Touching the page means "I am writing again": the slide-over menus
    // (page drawer, tools panel, layers, shapes menu) close themselves so the
    // user never has to tap a close button after changing a setting. The
    // pen-case dock is left alone — it is the tool palette, not a menu.
    val dismissCanvasOverlays: () -> Unit = {
        viewModel.dismissCanvasMenus()
        showLayersPanel = false
        showShapesMenu = false
    }

    // ---- F-10: vector PDF page plumbing -----------------------------------
    // One pdfium source document per note, opened lazily and closed when the
    // screen leaves composition.
    val pdfDocCache = remember(viewModel.noteId) { PdfDocumentCache(context) }
    DisposableEffect(pdfDocCache) {
        onDispose { pdfDocCache.close() }
    }

    // Saved generation map to re-render pages when continuous inking finishes writing to disk
    val savedPageGenerations = remember { mutableStateMapOf<Int, Int>() }

    // In-place stylus inking for the continuous reader: one page layer at a
    // time, saved through the same repository calls single-page mode uses.
    val inkController = remember(viewModel.noteId) {
        val app = context.applicationContext as? TouchNotesApp
        ContinuousInkController(
            noteId = viewModel.noteId,
            repository = viewModel.repository,
            pdfDocCache = pdfDocCache,
            scope = scope,
            appScope = app?.appScope ?: scope,
            onSaved = { savedPage ->
                savedPageGenerations[savedPage] = (savedPageGenerations[savedPage] ?: 0) + 1
            },
            onUndoRedoChanged = { canUndo, canRedo ->
                viewModel.updateUndoRedo(canUndo, canRedo)
            }
        )
    }
    DisposableEffect(inkController) {
        onDispose {
            if (!viewModel.isNoteDeleted) {
                val app = context.applicationContext as? TouchNotesApp
                inkController.dispose(app?.appScope ?: scope)
            }
        }
    }

    // The page row when [index] renders from a vector PDF source.
    fun vectorPdfPage(index: Int): PageEntity? {
        val page = pages.getOrNull(index) ?: return null
        return if (page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0) page else null
    }

    /**
     * Opens (and parses) a vector source off the main thread.
     *
     * `applyPdfBackground` has to read pages of the open document synchronously
     * so the page rect is installed before the stroke layer lands, and opening
     * a large PDF in pdfium can take a while. Suspend callers therefore warm
     * the one-document cache here first; the apply itself is then a cache hit.
     */
    suspend fun preloadPdfSource(relativePath: String) {
        if (relativePath.isEmpty()) return
        withContext(Dispatchers.IO) { pdfDocCache.document(relativePath) }
    }

    // Installs the live pdfium background of a vector PDF page. The renderer
    // runs on the canvas render thread and re-renders at higher resolution as
    // the user zooms in; the page rect stays fixed at the import geometry so
    // stroke coordinates never shift. Also installs the text layer used by the
    // Text tool (selection, copy, mark, underline).
    fun applyPdfBackground(canvas: DrawingCanvasView, page: PageEntity) {
        val relPath = page.pdfSourcePath
        val pdfPageIndex = page.pdfPageIndex
        pdfTextAvailable = false
        val document = pdfDocCache.document(relPath)
        if (document == null) {
            canvas.setPdfBackground(0f, 0f, 0f, null)
            canvas.setPdfTextSource(null)
            return
        }
        val size = document.pageSize(pdfPageIndex)
        if (size[0] <= 0f || size[1] <= 0f) {
            canvas.setPdfBackground(0f, 0f, 0f, null)
            canvas.setPdfTextSource(null)
            return
        }
        // Same base geometry the import used, so pages keep their size/aspect.
        val pageTypeName = note?.pageType ?: "infinite"
        val targetHeight = if (pageTypeName == "a4" || pageTypeName == "pdf") 3056f else 3840f
        val targetWidth = 2160f
        val scale = minOf(targetWidth / size[0], targetHeight / size[1])
        val baseOutWidth = (size[0] * scale).roundToInt().coerceAtLeast(1)
        val basePxPerPt = scale
        canvas.setPdfBackground(size[0], size[1], basePxPerPt) { pxPerPt, outW, outH ->
            pdfDocCache.document(relPath)?.renderPageAt(pdfPageIndex, pxPerPt, outW, outH)
        }
        // Text layer: pdfium user space (bottom-left origin) → page rect space
        // (top-left origin), the same space the background and annotations use.
        val pageW = size[0]
        val pageH = size[1]
        val rectWidth = canvas.pageCanvasWidth().takeIf { it > 0f } ?: baseOutWidth.toFloat()
        val pxPerPt = rectWidth / pageW
        canvas.setPdfTextSource(object : DrawingCanvasView.PdfTextSource {
            override fun charCount(): Int = document.textCharCount(pdfPageIndex)

            override fun charIndexAt(x: Float, y: Float, tolerance: Float): Int {
                val user = PdfPageGeometry.canvasToUserPoint(x, y, pageH, pxPerPt)
                return document.charIndexAt(
                    pdfPageIndex, user[0], user[1], tolerance / pxPerPt
                )
            }

            override fun charBox(index: Int): FloatArray =
                PdfPageGeometry.userRectToCanvas(
                    document.charBox(pdfPageIndex, index), pageH, pxPerPt
                )

            override fun selectionRects(start: Int, count: Int): List<FloatArray> =
                document.selectionRects(pdfPageIndex, start, count)
                    .map { PdfPageGeometry.userRectToCanvas(it, pageH, pxPerPt) }

            override fun text(start: Int, count: Int): String =
                document.text(pdfPageIndex, start, count)
        })
        // Text-page extraction can take a moment on complex PDFs: probe off the
        // main thread, and leave Text mode when the page has no text layer.
        scope.launch {
            val available = try {
                withContext(Dispatchers.IO) { document.textCharCount(pdfPageIndex) > 0 }
            } catch (e: Exception) {
                false
            }
            pdfTextAvailable = available
            if (!available && pdfTextMode) {
                pdfTextMode = false
                pdfSelection = null
                canvas.setPdfTextSelectionMode(false)
            }
        }
    }

    // Shared page apply for every navigation callback: stroke layer of the
    // loaded page + (for vector PDF pages) the live vector background.
    fun applyLoadedPage(pageIndex: Int, bitmap: Bitmap?) {
        val canvas = canvasViewRef ?: return
        val page = vectorPdfPage(pageIndex)
        if (page != null) {
            applyPdfBackground(canvas, page)
            canvas.setPageContent(bitmap)
        } else {
            canvas.setPdfBackground(0f, 0f, 0f, null)
            canvas.setPdfTextSource(null)
            pdfTextAvailable = false
            pdfTextMode = false
            pdfSelection = null
            canvas.setPageContent(bitmap)
        }
    }

    val continuousColState = androidx.compose.foundation.lazy.rememberLazyListState()
    val continuousRowState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Load/save layers on page change. The INITIAL page/layer load lives in
    // the AndroidView factory below, bound to the view instance it creates:
    // a LaunchedEffect-only loader can silently no-op on an reopen composition
    // (its canvasViewRef-guarded apply racing/cancelled in the entry's frame
    // burst — on-device the view then stayed blank at the inited Backing size),
    // while the factory's own coroutine always reaches its own view. This
    // effect therefore skips its first (initial-composition) run — page 0 is
    // applied exactly once — and only handles real page flips.
    var pageEffectFirstRun by remember { mutableStateOf(true) }
    LaunchedEffect(currentPageIndex, uiState.pageTurningMode) {
        if (pageEffectFirstRun) {
            pageEffectFirstRun = false
            return@LaunchedEffect
        }
        // Continuous reading renders every page itself; mirroring the scrolled
        // page into the hidden canvas as well would decode each scrolled page
        // twice, so leave the canvas alone until the reader returns to single
        // page mode (P1 audit).
        val continuousReading = (note?.pageType ?: "infinite") != "infinite" &&
            uiState.pageTurningMode == 0
        if (continuousReading) return@LaunchedEffect

        // When leaving continuous mode to single-page mode, flush and release
        // any active continuous session first so the latest strokes are on disk
        // before loadPageStrokes / loadCurrentPageBitmap reads them.
        inkController.releaseSessionSuspend()

        // From here the canvas holds currentPageIndex, so saves triggered
        // before the next page flip must be filed under this index.
        viewModel.setCanvasPageIndex(currentPageIndex)
        // F-10: vector PDF pages load their stroke-only layer and get the live
        // pdfium background; every other page keeps the raster path.
        val vectorPage = vectorPdfPage(currentPageIndex)
        if (vectorPage != null) {
            preloadPdfSource(vectorPage.pdfSourcePath)
            val strokes = withContext(Dispatchers.IO) { viewModel.loadPageStrokes(currentPageIndex) }
            val canvas = canvasViewRef
            if (canvas != null) {
                canvas.backgroundBitmap = null
                viewModel.setPageLoadedFromDisk(true)
                applyPdfBackground(canvas, vectorPage)
                canvas.setPageContent(strokes)
            }
        } else {
            val savedBitmap = viewModel.loadCurrentPageBitmap()
            canvasViewRef?.setPageContent(savedBitmap)
            canvasViewRef?.backgroundBitmap = null
            viewModel.setPageLoadedFromDisk(savedBitmap != null)
        }
        try {
            // Layer PNG decode is file I/O: read on Dispatchers.IO, apply
            // on main via applyLoadedLayers (invalidate() included).
            canvasViewRef?.let { view ->
                // Layers belong to the note, not the page, so a reload here is
                // only a safety net. Skip it while edits are unsaved: applying
                // the on-disk copy would silently undo a just-dragged image.
                if (!view.layersDirty) {
                    val layers = withContext(Dispatchers.IO) {
                        view.readLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                    }
                    view.applyLoadedLayers(layers)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val selectLassoTool: () -> Unit = {
        if (isContinuous) {
            scope.launch {
                inkController.flushCurrentSuspend()
                viewModel.setPageTurningMode(1)
                viewModel.selectTool(CanvasTool.LASSO)
                android.widget.Toast.makeText(context, "Switched to Single-Page Mode for Lasso", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.selectTool(CanvasTool.LASSO)
        }
    }

    // Shared layer insertion helper: handles continuous mode flush, switch to
    // single-page mode, disk hydration, and IO persistence safely.
    val insertLayerInCurrentNote: suspend (action: (DrawingCanvasView) -> Boolean) -> Boolean = { action ->
        val view = canvasViewRef
        if (view != null) {
            if (isContinuous) {
                inkController.flushCurrentSuspend()
                viewModel.setPageTurningMode(1)
                if (view.imageLayers.isEmpty() && !view.layersDirty) {
                    val diskLayers = withContext(Dispatchers.IO) {
                        view.readLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                    }
                    view.applyLoadedLayers(diskLayers)
                }
            }
            val success = action(view)
            if (success) {
                try {
                    withContext(Dispatchers.IO) {
                        view.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            success
        } else false
    }

    // Image Picker Launcher (for Add -> Image in Tools panel)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    // Decode off the main thread (same pattern as
                    // NoteListScreen) — ImageDecoder/MediaStore file reads
                    // never block the UI thread.
                    val bitmap = withContext(Dispatchers.IO) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                        }
                    }
                    bitmap?.let { bmp ->
                        val added = insertLayerInCurrentNote { it.addImageLayer(bmp) }
                        if (!added) {
                            android.widget.Toast.makeText(
                                context, "Max 3 image layers", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // PDF Picker Launcher — vector import (defect F-10): the source PDF is
    // kept with the note and pages are re-rendered from pdfium at any zoom
    // level (sharp text, no raster upscaling). The stroke layer is never
    // touched: the rendered page is a background/preview artifact only.
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val imported = try {
                    withContext(Dispatchers.IO) { PdfVectorImporter.readDocument(context, it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                if (imported == null) {
                    android.widget.Toast.makeText(context, "Could not open PDF", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                try {
                    val noteId = viewModel.noteId
                    // Per-page render error toast: Toast.show() must run on a
                    // thread with a Looper, so bounce back to Main.
                    suspend fun showPageError(msg: String) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    // Append one page per PDF page. Rendering, PNG compression
                    // and the atomic file write all run on Dispatchers.IO; the
                    // loop no longer blocks the main thread (no progress UI
                    // exists, so nothing is posted per page except the error
                    // toast above).
                    val firstPageBitmap = withContext(Dispatchers.IO) {
                        PdfVectorImporter.importIntoNote(
                            context = context,
                            imported = imported,
                            noteId = noteId,
                            repository = viewModel.repository,
                            fileStore = viewModel.fileStore,
                            targetWidth = 2160,
                            targetHeight = if ((note?.pageType ?: "infinite").let { it == "a4" || it == "pdf" }) 3056 else 3840,
                            reuseFirstPage = false,
                            onPageError = { msg -> showPageError(msg) }
                        )
                    }
                    val canvas = canvasViewRef
                    val curIdx = viewModel.currentPageIndex.value
                    val curPage = vectorPdfPage(curIdx)
                    if (curPage != null) preloadPdfSource(curPage.pdfSourcePath)
                    if (canvas != null && curPage != null) {
                        applyPdfBackground(canvas, curPage)
                    } else if (firstPageBitmap != null) {
                        canvas?.backgroundBitmap = firstPageBitmap
                    }
                    android.widget.Toast.makeText(
                        context,
                        "Imported ${imported.pageCount} PDF page(s)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "PDF import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    imported.document.close()
                }
            }
        }
    }

    // Drag & drop + 3-finger-paste insertion: shared path with Add → Image
    // (gallery picker). Bitmaps become centered image layers via addImageLayer;
    // text becomes a text block layer via the existing addTextBlock API and
    // additionally shows a toast preview. URI decoding runs on Dispatchers.IO
    // inside ClipboardHelper — the main thread is never blocked.
    val handleExtractedContent: (ClipboardHelper.ExtractedContent) -> Unit = { extracted ->
        when {
            extracted.bitmap != null -> {
                scope.launch {
                    val added = insertLayerInCurrentNote { it.addImageLayer(extracted.bitmap) }
                    if (!added) {
                        android.widget.Toast.makeText(
                            context, "Max 3 image layers", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            !extracted.text.isNullOrBlank() -> {
                scope.launch {
                    val inserted = insertLayerInCurrentNote { it.addTextBlock(extracted.text) }
                    if (inserted) {
                        val preview = extracted.text.trim().replace("\n", " ").take(60)
                        android.widget.Toast.makeText(
                            context, "Pasted text: $preview...", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context, "Could not insert text", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> android.widget.Toast.makeText(
                context, "Nothing pasteable", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ACTION_DROP from split-screen browser / Files / Photos: accepts any clip
    // with items ("*/*" descriptions included); extraction happens on IO and
    // resumes on the main dispatcher of [scope].
    val handleDroppedClip: (android.content.ClipData) -> Unit = { clip ->
        scope.launch {
            val extracted = ClipboardHelper.extractContent(context, null, clip)
            handleExtractedContent(extracted)
        }
    }

    // 3-finger tap: paste the primary clipboard item through the same path.
    val handleClipboardPaste: () -> Unit = {
        scope.launch {
            val item = ClipboardHelper.pasteFromClipboard(context)
            val extracted = ClipboardHelper.extractContent(context, item)
            handleExtractedContent(extracted)
        }
    }

    // Navigation (system back, tab close, note switch, opening the text
    // editor) disposes this screen, which cancels viewModelScope. A stroke
    // finished less than the 1.5 s debounce before that would never be
    // written, so hand the pending save to the application scope, which
    // outlives the screen.
    DisposableEffect(viewModel) {
        onDispose {
            if (viewModel.isNoteDeleted) return@onDispose
            val app = context.applicationContext as? TouchNotesApp ?: return@onDispose
            viewModel.flushPendingSave(app.appScope)
            // Layer edits live outside the backing bitmap, so a moved or
            // hidden image would otherwise be lost on a system back (only the
            // toolbar path calls saveAndExit). `layersDirty` keeps unchanged
            // notes from re-writing their PNGs on every exit.
            val canvas = canvasViewRef
            if (canvas != null && canvas.layersDirty) {
                app.appScope.launch {
                    canvas.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                }
            }
        }
    }

    val flushCurrentNote: suspend () -> Unit = {
        val continuousReading = (note?.pageType ?: "infinite") != "infinite" &&
            uiState.pageTurningMode == 0
        if (continuousReading) {
            inkController.flushCurrentSuspend()
        } else {
            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                viewModel.saveCurrentPageBitmapDirect(bmp)
            }
        }
        val canvas = canvasViewRef
        if (canvas != null && canvas.layersDirty) {
            withContext(Dispatchers.IO) {
                canvas.saveImageLayers(viewModel.fileStore, viewModel.noteId)
            }
        }
    }

    // Save on leave helper
    val saveAndExit: () -> Unit = {
        scope.launch {
            try {
                flushCurrentNote()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onBackClick()
        }
    }


    val scrollToContinuousPage: (Int) -> Unit = { targetIndex ->
        scope.launch {
            val safeIndex = targetIndex.coerceAtLeast(0)
            if (safeIndex >= pages.size) {
                // Yield for Room emission & Compose layout of newly added page
                kotlinx.coroutines.delay(60)
            }
            val maxIdx = (pages.size - 1).coerceAtLeast(0)
            val finalIndex = safeIndex.coerceIn(0, maxIdx)
            if (isHorizontal) {
                continuousRowState.animateScrollToItem(finalIndex)
            } else if (uiState.doublePageMode) {
                continuousColState.animateScrollToItem(finalIndex / 2)
            } else {
                continuousColState.animateScrollToItem(finalIndex)
            }
        }
    }

    androidx.activity.compose.BackHandler(onBack = {
        when {
            uiState.isThumbnailDrawerOpen -> viewModel.toggleThumbnailDrawer(false)
            uiState.isToolsPanelOpen -> viewModel.toggleToolsPanel(false)
            showLayersPanel -> showLayersPanel = false
            showShapesMenu -> showShapesMenu = false
            else -> saveAndExit()
        }
    })

    // Dialog: "Change note name" (matches Screenshot_20261102-184531_TouchNotes.png)
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    text = "Change note name",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = Color(0xFF212529)
                )
            },
            text = {
                Column {
                    TextField(
                        value = newTitleText,
                        onValueChange = { newTitleText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = OrangePrimary,
                            unfocusedIndicatorColor = Color(0xFFCCCCCC),
                            cursorColor = OrangePrimary
                        )
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", color = Color.Gray, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Divider(
                        modifier = Modifier
                            .height(20.dp)
                            .width(1.dp),
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = {
                        if (newTitleText.isNotBlank()) {
                            viewModel.updateTitle(newTitleText)
                        }
                        showRenameDialog = false
                    }) {
                        Text(
                            "Ok",
                            color = OrangePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            },
            dismissButton = null,
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog: Clear Page Confirmation
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Current Page") },
            text = { Text("Clear all drawings on page ${currentPageIndex + 1}? (Undo is still available)") },
            confirmButton = {
                TextButton(onClick = {
                    if (isContinuous) {
                        inkController.clearPage(currentPageIndex, pages.getOrNull(currentPageIndex), renderWidthPx = 2160)
                    } else {
                        canvasViewRef?.clearCanvas()
                    }
                    showClearDialog = false
                }) {
                    Text("Clear", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete Page Confirmation
    pageToDeleteIndex?.let { targetIndex ->
        AlertDialog(
            onDismissRequest = { pageToDeleteIndex = null },
            title = { Text("Delete Page") },
            text = { Text("Are you sure you want to delete page ${targetIndex + 1}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val idx = targetIndex
                    pageToDeleteIndex = null
                    scope.launch {
                        if (isContinuous) {
                            inkController.releaseSessionSuspend()
                        }
                        val currentBmp = if (isContinuous) null else canvasViewRef?.getBackingBitmap()
                        viewModel.deletePage(idx, isContinuous = isContinuous, currentBitmap = currentBmp) { newBmp ->
                            if (!isContinuous) {
                                applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                            } else {
                                savedPageGenerations.clear()
                            }
                        }
                    }
                }) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToDeleteIndex = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete Note Confirmation
    if (showDeleteNoteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteNoteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Move this note to the recycle bin?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteNoteDialog = false
                    scope.launch {
                        inkController.discardAll()
                        canvasViewRef?.layersDirty = false
                        viewModel.deleteCurrentNoteSuspend()
                        onCloseTab(viewModel.noteId)
                    }
                }) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: PDF Page Picker
    if (showPdfPagePicker && pendingPdfUri != null) {
        AlertDialog(
            onDismissRequest = { showPdfPagePicker = false },
            title = { Text("Import PDF Page") },
            text = {
                Column {
                    Text("PDF has $pendingPdfPageCount page(s). Enter page number:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pendingPdfPageIndex,
                        onValueChange = { pendingPdfPageIndex = it },
                        label = { Text("Page (1-$pendingPdfPageCount)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingPdfUri
                    val idx = (pendingPdfPageIndex.toIntOrNull() ?: 1) - 1
                    if (uri != null && idx in 0 until pendingPdfPageCount) {
                        val w = canvasViewRef?.width ?: 0
                        val h = canvasViewRef?.height ?: 0
                        scope.launch {
                            // PdfRenderer work is file I/O: run it on
                            // Dispatchers.IO, apply the result on main. The
                            // dialog stays open until the page has actually
                            // been rendered (as before), so it never closes
                            // early.
                            val bmp = try {
                                withContext(Dispatchers.IO) {
                                    // F-10: render the chosen page from the
                                    // vector source (pdfium) at the view's
                                    // pixel size instead of a one-time raster.
                                    val imported = PdfVectorImporter.readDocument(context, uri)
                                        ?: return@withContext null
                                    try {
                                        imported.document.renderPage(
                                            pageIndex = idx,
                                            targetWidth = if (w > 0) w else 2160,
                                            targetHeight = if (h > 0) h else if ((note?.pageType ?: "infinite").let { it == "a4" || it == "pdf" }) 3056 else 3840
                                        )
                                    } finally {
                                        imported.document.close()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "PDF import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                null
                            }
                            if (bmp != null) {
                                // Non-destructive: set background only, leave strokes alone.
                                if (isContinuous) {
                                    inkController.flushCurrentSuspend()
                                    viewModel.saveCurrentPageBitmapDirect(bmp, force = true)
                                    val cur = viewModel.currentPageIndex.value
                                    savedPageGenerations[cur] = (savedPageGenerations[cur] ?: 0) + 1
                                } else {
                                    canvasViewRef?.let { it.backgroundBitmap = bmp }
                                    viewModel.saveCurrentPageBitmapDirect(bmp, force = true)
                                    val cur = viewModel.currentPageIndex.value
                                    savedPageGenerations[cur] = (savedPageGenerations[cur] ?: 0) + 1
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Failed to render PDF page", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            showPdfPagePicker = false
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Invalid page number", android.widget.Toast.LENGTH_SHORT).show()
                        showPdfPagePicker = false
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showPdfPagePicker = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Export Dialog
    val exportTargetNote = note
    if (showExportDialog && exportTargetNote != null) {
        personal.notes.touch.ui.export.ExportDialog(
            note = exportTargetNote,
            repository = viewModel.repository,
            fileStore = viewModel.fileStore,
            currentBitmap = if (isContinuous) null else canvasViewRef?.getBackingBitmap(),
            backgroundBitmap = if (isContinuous) null else canvasViewRef?.backgroundBitmap,
            imageLayers = if (isContinuous) emptyList() else (canvasViewRef?.imageLayers ?: emptyList()),
            currentPageIndex = currentPageIndex,
            initialTarget = selectedExportTarget,
            onDismiss = {
                showExportDialog = false
                selectedExportTarget = null
            }
        )
    }

    // Text Input Dialog (insert text as a layer)
    if (showTextInputDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { androidx.compose.material3.Text("Insert text") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = textInputText,
                    onValueChange = { textInputText = it },
                    label = { androidx.compose.material3.Text("Text") },
                    minLines = 3,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val text = textInputText.trim()
                        if (text.isNotEmpty()) {
                            scope.launch {
                                val inserted = insertLayerInCurrentNote { it.addTextBlock(text) }
                                if (!inserted) {
                                    android.widget.Toast.makeText(
                                        context, "Could not insert text", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        showTextInputDialog = false
                    }
                ) {
                    androidx.compose.material3.Text("Insert")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTextInputDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    // Dialog: Go to Page / Jump
    if (showGoToPageDialog) {
        var pageTargetStr by remember { mutableStateOf((currentPageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showGoToPageDialog = false },
            title = { Text("Go to Page") },
            text = {
                Column {
                    Text("Enter page number (1 to $totalPages):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageTargetStr,
                        onValueChange = { pageTargetStr = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetPage = (pageTargetStr.toIntOrNull() ?: 1) - 1
                    val clamped = targetPage.coerceIn(0, totalPages - 1)
                    if (isContinuous) {
                        viewModel.setCurrentPageIndex(clamped)
                        scrollToContinuousPage(clamped)
                    } else {
                        scope.launch {
                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                viewModel.saveCurrentPageBitmap(bmp)
                            }
                            if (canvasViewRef?.layersDirty == true) {
                                withContext(Dispatchers.IO) {
                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                }
                            }
                            viewModel.goToPage(clamped, canvasViewRef?.getBackingBitmap()) { newBmp ->
                                applyLoadedPage(clamped, newBmp)
                            }
                        }
                    }
                    showGoToPageDialog = false
                }) {
                    Text("Jump", color = OrangePrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Custom Color Picker Dialog
    if (showCustomColorDialog) {
        AlertDialog(
            onDismissRequest = { showCustomColorDialog = false },
            title = { Text("Select Color") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val colorsGrid = listOf(
                        Color(0xFF000000), Color(0xFF424242), Color(0xFF757575), Color(0xFFBDBDBD),
                        Color(0xFFD32F2F), Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
                        Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
                        Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A),
                        Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
                        Color(0xFFFB6443), Color(0xFFFF5722), Color(0xFF795548), Color(0xFF607D8B)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorsGrid.chunked(4).forEach { colColors ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                colColors.forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(1.dp, Color.LightGray, CircleShape)
                                            .clickable {
                                                viewModel.selectColor(c)
                                                showCustomColorDialog = false
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomColorDialog = false }) { Text("Close") }
            }
        )
    }

    // Add Outline / TOC Chapter Dialog
    if (showAddChapterDialog) {
        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = {
                Text(
                    text = "Add Outline Chapter",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF212529)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Mark a section or chapter header for page ${currentPageIndex + 1}:",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    TextField(
                        value = newChapterTitle,
                        onValueChange = { newChapterTitle = it },
                        placeholder = { Text("e.g. Chapter 1: Introduction") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = OrangePrimary,
                            unfocusedIndicatorColor = Color.LightGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newChapterTitle.isNotBlank()) {
                            viewModel.addTocEntry(newChapterTitle, currentPageIndex)
                            newChapterTitle = ""
                            showAddChapterDialog = false
                        }
                    }
                ) {
                    Text("Add", color = OrangePrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                // ==========================================
                // 1. AUTHENTIC TOUCHNOTES TOP BAR
                // ==========================================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Action Group
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Back
                            IconButton(onClick = saveAndExit) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Notes",
                                    tint = Color(0xFF333333)
                                )
                            }

                            // Left Thumbnail Sidebar Toggle
                            IconButton(onClick = { viewModel.toggleThumbnailDrawer() }) {
                                Icon(
                                    imageVector = Icons.Default.ViewSidebar,
                                    contentDescription = "Page Thumbnails",
                                    tint = if (uiState.isThumbnailDrawerOpen) OrangePrimary else Color(0xFF555555)
                                )
                            }

                            // Undo
                            IconButton(
                                onClick = {
                                    val continuous = (note?.pageType ?: "infinite") != "infinite" &&
                                        uiState.pageTurningMode == 0
                                    if (!continuous || !inkController.undo()) {
                                        if (!continuous) canvasViewRef?.undo()
                                    }
                                },
                                enabled = uiState.canUndo
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (uiState.canUndo) Color(0xFF333333) else Color(0xFFCCCCCC)
                                )
                            }

                            // Redo
                            IconButton(
                                onClick = {
                                    val continuous = (note?.pageType ?: "infinite") != "infinite" &&
                                        uiState.pageTurningMode == 0
                                    if (!continuous || !inkController.redo()) {
                                        if (!continuous) canvasViewRef?.redo()
                                    }
                                },
                                enabled = uiState.canRedo
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (uiState.canRedo) Color(0xFF333333) else Color(0xFFCCCCCC)
                                )
                            }

                            // Reset zoom/pan
                            IconButton(onClick = {
                                if (isContinuous) {
                                    continuousScale = 1f
                                    continuousPanX = 0f
                                    continuousPanY = 0f
                                } else {
                                    canvasViewRef?.resetTransform()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset zoom",
                                    tint = Color(0xFF555555)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Note Title Pill (Clickable -> Change note name)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF1F3F4),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        newTitleText = note?.title ?: ""
                                        showRenameDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note?.title?.ifBlank { "Untitled Note" } ?: "Untitled Note",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF202124)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Create,
                                        contentDescription = "Edit title",
                                        tint = Color(0xFF757575),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // One-tap tabs collapse / expand toggle arrow (▲ / ▼)
                            IconButton(
                                onClick = { viewModel.setShowTabs(!showTabsState) },
                                modifier = Modifier.size(28.dp).padding(start = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (showTabsState) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showTabsState) "Hide tabs" else "Show tabs",
                                    tint = Color(0xFF555555),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (uiState.readingMode) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = OrangePrimary.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewModel.toggleReadingMode(false) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Reading Mode",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrangePrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Exit Reading Mode",
                                            tint = OrangePrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Right Action Group
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Switch to Text Editor
                            IconButton(onClick = {
                                scope.launch {
                                    flushCurrentNote()
                                    onOpenEditorClick(viewModel.noteId)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = "Text Note",
                                    tint = Color(0xFF555555)
                                )
                            }

                            // Document Outline / List
                            IconButton(onClick = {
                                viewModel.setDrawerTab(DrawerTab.OUTLINE)
                                viewModel.toggleThumbnailDrawer(true)
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Outline",
                                    tint = Color(0xFF555555)
                                )
                            }

                            // Search Outline / Chapters / Go to Page
                            IconButton(onClick = { showGoToPageDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Go to Page",
                                    tint = Color(0xFF555555)
                                )
                            }

                            // History / Clock
                            IconButton(onClick = {
                                android.widget.Toast.makeText(context, "Note history: All changes are saved locally", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    tint = Color(0xFF555555)
                                )
                            }

                            // Layers panel toggle
                            IconButton(onClick = { showLayersPanel = !showLayersPanel }) {
                                Icon(imageVector = Icons.Default.Layers, contentDescription = "Layers", tint = if (showLayersPanel) OrangePrimary else Color(0xFF555555))
                            }

                            // Import Image
                            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Import Image",
                                    tint = Color(0xFF555555)
                                )
                            }

                            // Tools & Settings (Right Drawer Toggle)
                            IconButton(onClick = { viewModel.toggleToolsPanel() }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Tools & Settings",
                                    tint = if (uiState.isToolsPanelOpen) OrangePrimary else Color(0xFF555555)
                                )
                            }
                        }
                    }
                }

                // Tab bar for open notes (positioned below Top Bar matching TouchNotes)
                val visibleOpenNotes = openNotes.filter { it == viewModel.noteId || noteTitlesMap.containsKey(it) }
                if (visibleOpenNotes.isNotEmpty() && showTabsState) {
                    val scrollState = rememberScrollState()
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        color = Color.White,
                        border = BorderStroke(0.5.dp, Color(0xFFEAEAEA))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(scrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleOpenNotes.forEachIndexed { tabIdx, id ->
                                val isSelected = id == viewModel.noteId
                                val title = noteTitlesMap[id]
                                    ?: if (isSelected) (note?.title?.ifBlank { "Untitled Note" } ?: "Untitled Note") else id.take(8)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .background(if (isSelected) Color(0xFFFFF7F2) else Color.White)
                                        .clickable {
                                            if (id != viewModel.noteId) {
                                                scope.launch {
                                                    try { flushCurrentNote() } catch (e: Exception) { e.printStackTrace() }
                                                    onSwitchNote(id)
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) OrangePrimary else Color(0xFF444444),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Close tab",
                                        tint = if (isSelected) OrangePrimary.copy(alpha = 0.7f) else Color.LightGray,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable {
                                                if (id == viewModel.noteId) {
                                                    scope.launch {
                                                        try { flushCurrentNote() } catch (e: Exception) { e.printStackTrace() }
                                                        onCloseTab(id)
                                                    }
                                                } else {
                                                    onCloseTab(id)
                                                }
                                            }
                                    )
                                }
                                if (tabIdx < visibleOpenNotes.size - 1) {
                                    Divider(
                                        modifier = Modifier
                                            .height(18.dp)
                                            .width(1.dp),
                                        color = Color(0xFFEAEAEA)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                    val continuousReading = (note?.pageType ?: "infinite") != "infinite" &&
                        uiState.pageTurningMode == 0
                    val isHorizontalReading = uiState.pageTurningDirection == 1
                    if (uiState.volumeForPageEnabled && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                                // Continuous reading has no "next page" to
                                // load: move the reader by one page instead.
                                // The hidden canvas is not touched, so a volume
                                // press cannot decode a page nobody sees.
                                if (continuousReading) {
                                    scope.launch {
                                        val state = if (isHorizontalReading) continuousRowState else continuousColState
                                        val from = state.firstVisibleItemIndex
                                        state.animateScrollToItem((from - 1).coerceAtLeast(0))
                                    }
                                    return@onKeyEvent true
                                }
                                // Save current layers and backing; the page
                                // switch is issued from the same coroutine
                                // only after the awaited layer save (IO) has
                                // landed — same save-then-switch order the
                                // baseline's main-dispatcher queue produced.
                                scope.launch {
                                    try {
                                        canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                            viewModel.saveCurrentPageBitmap(bmp)
                                        }
                                        withContext(Dispatchers.IO) {
                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                    viewModel.previousPage(canvasViewRef?.getBackingBitmap()) { newBmp ->
                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                    }
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                if (continuousReading) {
                                    scope.launch {
                                        val state = if (isHorizontalReading) continuousRowState else continuousColState
                                        val from = state.firstVisibleItemIndex
                                        val maxItems = if (isHorizontalReading) pages.size else if (uiState.doublePageMode) (pages.size + 1) / 2 else pages.size
                                        state.animateScrollToItem(
                                            (from + 1).coerceAtMost((maxItems - 1).coerceAtLeast(0))
                                        )
                                    }
                                    return@onKeyEvent true
                                }
                                scope.launch {
                                    try {
                                        canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                            viewModel.saveCurrentPageBitmap(bmp)
                                        }
                                        withContext(Dispatchers.IO) {
                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                    viewModel.nextPage(canvasViewRef?.getBackingBitmap()) { newBmp ->
                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
                // Rendering key for the page bitmaps. Quantising the zoom keeps
                // a pinch gesture from launching a PDF render per frame while
                // still upgrading resolution as the user zooms in (P1 audit).
                val continuousZoomBucket = when {
                    continuousScale < 1.25f -> 1f
                    continuousScale < 1.75f -> 1.5f
                    continuousScale < 2.5f -> 2f
                    else -> 3f
                }

                // When entering continuous mode — or flipping the reading
                // direction while in it — scroll to the current page index.
                // Without the direction key the new Lazy list started at page
                // 1 while the rest of the UI still pointed at page N.
                // Tool settings the reader's ink engine draws with.
                val inkSettings = InkToolSettings(
                    tool = uiState.selectedTool,
                    color = uiState.selectedColor.toArgb(),
                    strokeWidthPx = uiState.strokeWidth,
                    eraserWidthPx = uiState.eraserWidth,
                    pressureSensitivity = uiState.pressureSensitivity,
                    pressureSizeEnabled = uiState.pressureSizeEnabled,
                    stylusOnly = uiState.stylusOnly,
                    readingMode = uiState.readingMode,
                    autoShapeEnabled = uiState.autoShapeEnabled
                )


                LaunchedEffect(isContinuous, isHorizontal, uiState.doublePageMode) {
                    if (isContinuous) {
                        continuousScale = 1f
                        continuousPanX = 0f
                        continuousPanY = 0f
                        // The canvas sits behind the reader, so drop its vector
                        // background (up to 72 MB) for the duration. Returning
                        // to single-page mode re-installs it via applyLoadedPage.
                        canvasViewRef?.backgroundBitmap = null
                        val target = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        if (isHorizontal) {
                            continuousRowState.scrollToItem(target)
                        } else if (uiState.doublePageMode) {
                            continuousColState.scrollToItem(target / 2)
                        } else {
                            continuousColState.scrollToItem(target)
                        }
                    }
                }

                // Sync current visible item index in continuous mode to viewModel
                val currentContinuousVisible = if (isHorizontal) {
                    continuousRowState.firstVisibleItemIndex
                } else if (uiState.doublePageMode) {
                    val pairIdx = continuousColState.firstVisibleItemIndex
                    val firstPageInPair = pairIdx * 2
                    if (currentPageIndex == firstPageInPair || currentPageIndex == firstPageInPair + 1) {
                        currentPageIndex
                    } else {
                        firstPageInPair
                    }
                } else {
                    continuousColState.firstVisibleItemIndex
                }
                LaunchedEffect(isContinuous, currentContinuousVisible) {
                    if (isContinuous && pages.isNotEmpty()) {
                        val validIdx = currentContinuousVisible.coerceIn(0, pages.size - 1)
                        viewModel.setCurrentPageIndex(validIdx)
                    }
                }

                // Drawing Canvas (always mounted in composition)
                AndroidView(
                    factory = { ctx ->
                        DrawingCanvasView(ctx).apply {
                            canvasViewRef = this
                            // Wire the previously-dead drag & drop helper:
                            // install once, in factory (not update).
                            setDragDropListener(
                                onClipDropped = { clip -> handleDroppedClip(clip) },
                                isAccepted = { clip -> clip.itemCount > 0 }
                            )
                            // Wire the previously-dead 3-finger-tap paste hook.
                            onThreeFingerTap = { handleClipboardPaste() }
                            // Any touch on the page closes the open menus.
                            onCanvasTouchStart = { dismissCanvasOverlays() }
                            onImageSelectionChanged = { selectedImageIndex = it }
                            onPdfSelectionChanged = { selection -> pdfSelection = selection }
                            onUndoRedoListener = { canUndo, canRedo ->
                                viewModel.updateUndoRedo(canUndo, canRedo)
                            }
                            onStrokeFinishedListener = {
                                getBackingBitmap()?.let { bmp ->
                                    viewModel.scheduleDebouncedSave(bmp)
                                }
                            }
                            // Initial load, bound to THIS view instance: the
                            // coroutine captures `this`, so it can never miss
                            // the view via canvasViewRef read timing (the
                            // reopen-blank regression when loading lived only
                            // in the LaunchedEffect). The page PNG is applied
                            // exactly once — to the stroke layer via
                            // setPageContent, never also as backgroundBitmap —
                            // keeping retained strokes from rendering twice,
                            // and the LaunchedEffect above skips its first run
                            // so page 0 is not loaded a second time. F-10:
                            // vector PDF pages load their stroke-only layer
                            // and get the live pdfium background instead.
                            scope.launch {
                                try {
                                    if (isContinuous) {
                                        viewModel.setCanvasPageIndex(-1)
                                        viewModel.setPageLoadedFromDisk(false)
                                        val layers = withContext(Dispatchers.IO) {
                                            readLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                                        }
                                        applyLoadedLayers(layers)
                                        return@launch
                                    }
                                    val pageIdx = viewModel.currentPageIndex.value
                                    viewModel.setCanvasPageIndex(pageIdx)
                                    val vectorPage = vectorPdfPage(pageIdx)
                                    if (vectorPage != null) {
                                        preloadPdfSource(vectorPage.pdfSourcePath)
                                        backgroundBitmap = null
                                        viewModel.setPageLoadedFromDisk(true)
                                        applyPdfBackground(this@apply, vectorPage)
                                        val strokes = withContext(Dispatchers.IO) {
                                            viewModel.loadPageStrokes(pageIdx)
                                        }
                                        setPageContent(strokes)
                                    } else {
                                        val savedBitmap = viewModel.loadCurrentPageBitmap()
                                        setPageContent(savedBitmap)
                                        backgroundBitmap = null
                                        viewModel.setPageLoadedFromDisk(savedBitmap != null)
                                    }
                                    val layers = withContext(Dispatchers.IO) {
                                        readLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                                    }
                                    applyLoadedLayers(layers)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.pageType = note?.pageType ?: "infinite"
                        view.beyondPaper = (note?.pageType == "infinite") && !uiState.fixPreviewPage
                        view.currentTool = uiState.selectedTool
                        view.strokeColor = uiState.selectedColor.toArgb()
                        view.strokeWidthPx = uiState.strokeWidth
                        view.eraserWidthPx = uiState.eraserWidth
                        view.paperPattern = uiState.paperPattern
                        view.backgroundMode = uiState.backgroundMode
                        view.backgroundDensity = uiState.backgroundDensity
                        view.stylusOnly = uiState.stylusOnly
                        view.readingMode = uiState.readingMode
                        view.pressureSensitivity = uiState.pressureSensitivity
                        view.pressureSizeEnabled = uiState.pressureSizeEnabled
                        view.autoShapeEnabled = uiState.autoShapeEnabled
                        view.doublePage = uiState.doublePageMode
                        view.minZoom = uiState.minimalInfiniteZoom
                        view.horizontalResistance = uiState.horizontalResistance
                        view.autoCenter = uiState.autoCenter
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Continuous page overlay when continuous mode is active
                if (isContinuous) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFEEEEEE))
                                .pointerInput(Unit) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val oldScale = continuousScale
                                        val newScale = (continuousScale * zoom)
                                            .coerceIn(MIN_CONTINUOUS_SCALE, MAX_CONTINUOUS_SCALE)
                                        val factor = newScale / oldScale
                                        if (newScale < 1f) {
                                            continuousPanX = size.width * (1f - newScale) / 2f
                                            continuousPanY = 0f
                                            if (isHorizontal) {
                                                continuousRowState.dispatchRawDelta(-pan.x)
                                            } else {
                                                continuousColState.dispatchRawDelta(-pan.y)
                                            }
                                        } else {
                                            val maxPanX = size.width * (newScale - 1f)
                                            val maxPanY = size.height * (newScale - 1f)
                                            val prevPanX = continuousPanX
                                            val prevPanY = continuousPanY
                                            continuousPanX = (centroid.x - (centroid.x - continuousPanX) * factor + pan.x)
                                                .coerceIn(-maxPanX, 0f)
                                            continuousPanY = (centroid.y - (centroid.y - continuousPanY) * factor + pan.y)
                                                .coerceIn(-maxPanY, 0f)
                                            val consumedX = continuousPanX - prevPanX
                                            val consumedY = continuousPanY - prevPanY
                                            val unabsorbedX = pan.x - consumedX
                                            val unabsorbedY = pan.y - consumedY
                                            if (isHorizontal && unabsorbedX != 0f) {
                                                continuousRowState.dispatchRawDelta(-unabsorbedX)
                                            } else if (!isHorizontal && unabsorbedY != 0f) {
                                                continuousColState.dispatchRawDelta(-unabsorbedY)
                                            }
                                            // Back at fit-to-width: drop the offsets so
                                            // pages sit centred again.
                                            if (newScale <= 1.02f) {
                                                continuousPanX = 0f
                                                continuousPanY = 0f
                                            }
                                        }
                                        continuousScale = newScale
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = continuousScale
                                    scaleY = continuousScale
                                    translationX = continuousPanX
                                    translationY = continuousPanY
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                }
                        ) {
                            if (isHorizontal) {
                                androidx.compose.foundation.lazy.LazyRow(
                                    state = continuousRowState,
                                    modifier = Modifier.fillMaxSize(),
                                    // A pen down on a page must not be turned into
                                    // a scroll: Compose's interop touch bridge does
                                    // not consume the initial down, so the list
                                    // would cancel the stroke and scroll instead.
                                    userScrollEnabled = !inkController.isDrawing,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                                ) {
                                    items(pages.size) { idx ->
                                        val page = pages[idx]
                                        ContinuousPageView(
                                            pageIndex = idx,
                                            page = page,
                                            noteId = viewModel.noteId,
                                            pdfDocCache = pdfDocCache,
                                            repository = viewModel.repository,
                                            zoom = continuousZoomBucket,
                                            ink = inkController,
                                            inkSettings = inkSettings,
                                            backgroundMode = uiState.backgroundMode,
                                            backgroundDensity = uiState.backgroundDensity,
                                            savedGeneration = savedPageGenerations[idx] ?: 0,
                                            isCurrentPage = idx == currentPageIndex,
                                            modifier = androidx.compose.ui.Modifier.fillParentMaxHeight(),
                                            onClick = {
                                                viewModel.setCurrentPageIndex(idx)
                                                dismissCanvasOverlays()
                                            }
                                        )
                                    }
                                }
                            } else if (uiState.doublePageMode) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    state = continuousColState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !inkController.isDrawing,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 16.dp)
                                ) {
                                    val pairs = (0 until pages.size).chunked(2)
                                    items(pairs.size) { pairIdx ->
                                        val pair = pairs[pairIdx]
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            for (idx in pair) {
                                                val page = pages[idx]
                                                Box(modifier = Modifier.weight(1f)) {
                                                    ContinuousPageView(
                                                        pageIndex = idx,
                                                        page = page,
                                                        noteId = viewModel.noteId,
                                                        pdfDocCache = pdfDocCache,
                                                        repository = viewModel.repository,
                                                        zoom = continuousZoomBucket,
                                                        ink = inkController,
                                                        inkSettings = inkSettings,
                                                        backgroundMode = uiState.backgroundMode,
                                                        backgroundDensity = uiState.backgroundDensity,
                                                        savedGeneration = savedPageGenerations[idx] ?: 0,
                                                        isCurrentPage = idx == currentPageIndex,
                                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                                        onClick = {
                                                            viewModel.setCurrentPageIndex(idx)
                                                            dismissCanvasOverlays()
                                                        }
                                                    )
                                                }
                                            }
                                            if (pair.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    state = continuousColState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !inkController.isDrawing,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 32.dp)
                                ) {
                                    items(pages.size) { idx ->
                                        val page = pages[idx]
                                        ContinuousPageView(
                                            pageIndex = idx,
                                            page = page,
                                            noteId = viewModel.noteId,
                                            pdfDocCache = pdfDocCache,
                                            repository = viewModel.repository,
                                            zoom = continuousZoomBucket,
                                            ink = inkController,
                                            inkSettings = inkSettings,
                                            backgroundMode = uiState.backgroundMode,
                                            backgroundDensity = uiState.backgroundDensity,
                                            savedGeneration = savedPageGenerations[idx] ?: 0,
                                            isCurrentPage = idx == currentPageIndex,
                                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                            onClick = {
                                                viewModel.setCurrentPageIndex(idx)
                                                dismissCanvasOverlays()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Continuous Mode Floating HUD: Stylus/Finger Mode toggle and Zoom Reset pill
                        // Placed outside graphicsLayer so it stays fixed at TopCenter at 1x scale
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.stylusOnly) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            viewModel.toggleStylusOnly()
                                            android.widget.Toast.makeText(
                                                context,
                                                "Finger Draw ON: 2 fingers to scroll",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Stylus Mode",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (kotlin.math.abs(continuousScale - 1f) > 0.05f) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            continuousScale = 1f
                                            continuousPanX = 0f
                                            continuousPanY = 0f
                                        }
                                ) {
                                    Text(
                                        text = "${(continuousScale * 100).roundToInt()}% · Reset",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Image layer controls
                if (selectedImageIndex >= 0) {
                    var layerOpacity by remember(selectedImageIndex) {
                        mutableFloatStateOf(canvasViewRef?.imageLayers?.getOrNull(selectedImageIndex)?.opacity ?: 1f)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.96f),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Opacity", fontSize = 12.sp)
                            Slider(
                                value = layerOpacity,
                                onValueChange = { v ->
                                    layerOpacity = v
                                    canvasViewRef?.setLayerOpacity(selectedImageIndex, v)
                                },
                                modifier = Modifier.width(120.dp)
                            )
                            IconButton(onClick = {
                                canvasViewRef?.deleteSelectedImageLayer()
                                selectedImageIndex = -1
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete layer", tint = Color.Red)
                            }
                            IconButton(onClick = {
                                canvasViewRef?.cropSelectedImageLayer()
                            }) {
                                Icon(Icons.Default.CropSquare, contentDescription = "Crop layer")
                            }
                        }
                    }
                }

                // Layers panel
                if (showLayersPanel) {
                    val _tick = layersTick
                    val layers = canvasViewRef?.imageLayers ?: emptyList()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.98f),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color(0xFFE5E5E5)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 12.dp)
                            .widthIn(min = 220.dp, max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Layers (${layers.size}/3)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF222222)
                                )
                                IconButton(
                                    onClick = { showLayersPanel = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (layers.isEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (isContinuous) "Switch to Single-Page to edit layers." else "No floating layers on this page.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                                layers.forEachIndexed { idx, layer ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                canvasViewRef?.setLayerVisible(idx, !layer.visible)
                                                layersTick++
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                        }
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "visibility",
                                                tint = Color.DarkGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text("Layer ${idx + 1}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                if (idx > 0) {
                                                    canvasViewRef?.moveImageLayer(idx, idx - 1)
                                                    layersTick++
                                                    scope.launch {
                                                        try {
                                                            withContext(Dispatchers.IO) {
                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                            }
                                                        } catch (e: Exception) { e.printStackTrace() }
                                                    }
                                                }
                                            },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "move up", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (idx < layers.size - 1) {
                                                    canvasViewRef?.moveImageLayer(idx, idx + 1)
                                                    layersTick++
                                                    scope.launch {
                                                        try {
                                                            withContext(Dispatchers.IO) {
                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                            }
                                                        } catch (e: Exception) { e.printStackTrace() }
                                                    }
                                                }
                                            },
                                            enabled = idx < layers.size - 1,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "move down", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                canvasViewRef?.deleteImageLayer(idx)
                                                layersTick++
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                        }
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Divider(color = Color(0xFFEEEEEE))
                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showLayersPanel = false
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("+ Image", fontSize = 11.sp, color = OrangePrimary)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showLayersPanel = false
                                        textInputText = ""
                                        showTextInputDialog = true
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("+ Text", fontSize = 11.sp, color = OrangePrimary)
                                }
                            }

                            if (layers.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        canvasViewRef?.mergeAllLayersToBase()
                                        layersTick++
                                        scope.launch {
                                            try {
                                                canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                    viewModel.saveCurrentPageBitmapDirect(bmp)
                                                }
                                                withContext(Dispatchers.IO) {
                                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Merge to base", fontSize = 11.sp, color = OrangePrimary)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. FLOATING PENCASE (TOOL DOCK)
                // ==========================================
                // F-10: PDF text selection actions. Shown while the Text tool
                // is in selection mode on a vector page; the selection itself
                // is painted by DrawingCanvasView.
                if (pdfTextMode) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.97f),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp)
                            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val hasSelection = pdfSelection?.isEmpty == false
                            TextButton(
                                enabled = hasSelection,
                                onClick = {
                                    val text = canvasViewRef?.selectedPdfText().orEmpty()
                                    if (text.isNotEmpty()) {
                                        ClipboardHelper.copyText(context, text)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Copied ${text.length} character(s)",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) { Text("Copy", fontSize = 12.sp) }
                            TextButton(
                                enabled = hasSelection,
                                onClick = {
                                    if (canvasViewRef?.commitPdfSelection(mark = true) == true) {
                                        pdfSelection = null
                                    }
                                }
                            ) { Text("Mark", fontSize = 12.sp) }
                            TextButton(
                                enabled = hasSelection,
                                onClick = {
                                    if (canvasViewRef?.commitPdfSelection(mark = false) == true) {
                                        pdfSelection = null
                                    }
                                }
                            ) { Text("Underline", fontSize = 12.sp) }
                            TextButton(onClick = {
                                pdfTextMode = false
                                pdfSelection = null
                                canvasViewRef?.setPdfTextSelectionMode(false)
                            }) { Text("Done", fontSize = 12.sp) }
                        }
                    }
                }

                if (uiState.showPencase && !uiState.readingMode) {
                    if (isPencaseCollapsed) {
                        // Collapsed mini restore pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.95f),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(if (uiState.dockHorizontal) Alignment.TopCenter else Alignment.CenterStart)
                                .padding(top = if (uiState.dockHorizontal) 10.dp else 0.dp, start = if (!uiState.dockHorizontal) 12.dp else 0.dp)
                                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
                                .clickable { isPencaseCollapsed = false }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tool_fineliner),
                                    contentDescription = "Expand Pens",
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    imageVector = if (uiState.dockHorizontal) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Expand",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else if (uiState.dockHorizontal) {
                        // Horizontal Dock (Top under bar)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.96f),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Drag handle pill (Tap to switch orientation)
                                Box(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.LightGray)
                                        .clickable { viewModel.toggleDockOrientation() }
                                )

                                // Fountain Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fountain_pen,
                                    label = "Fountain Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.FOUNTAIN_PEN,
                                    onClick = { viewModel.selectTool(CanvasTool.FOUNTAIN_PEN) }
                                )

                                // Fineliner / Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fineliner,
                                    label = "Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.PEN,
                                    onClick = { viewModel.selectTool(CanvasTool.PEN) }
                                )

                                // Yellow Highlighter
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_highlighter,
                                    label = "Highlighter",
                                    isSelected = uiState.selectedTool == CanvasTool.HIGHLIGHTER,
                                    selectedTint = Color(0xFFFFD600),
                                    onClick = { viewModel.selectTool(CanvasTool.HIGHLIGHTER) }
                                )

                                // Pink Eraser
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_eraser,
                                    label = "Eraser",
                                    isSelected = uiState.selectedTool == CanvasTool.ERASER,
                                    selectedTint = Color(0xFFFF4081),
                                    onClick = { viewModel.selectTool(CanvasTool.ERASER) }
                                )

                                // Text Tool [T]
                                PencaseToolIcon(
                                    icon = Icons.Default.TextFields,
                                    label = "Text",
                                    isSelected = pdfTextMode,
                                    onClick = {
                                        if (pdfTextAvailable && vectorPdfPage(viewModel.currentPageIndex.value) != null) {
                                            val next = !pdfTextMode
                                            pdfTextMode = next
                                            if (!next) pdfSelection = null
                                            canvasViewRef?.setPdfTextSelectionMode(next)
                                        } else {
                                            textInputText = ""
                                            showTextInputDialog = true
                                        }
                                    }
                                )

                                // Shapes Tool
                                val shapeIconHoriz = when (uiState.selectedTool) {
                                    CanvasTool.SHAPE_RECT -> Icons.Default.CropSquare
                                    CanvasTool.SHAPE_CIRCLE -> Icons.Default.RadioButtonUnchecked
                                    CanvasTool.SHAPE_LINE -> Icons.Default.Timeline
                                    else -> Icons.Default.FormatShapes
                                }
                                Box {
                                    PencaseToolIcon(
                                        icon = shapeIconHoriz,
                                        label = "Shapes",
                                        isSelected = uiState.selectedTool in listOf(
                                            CanvasTool.SHAPE_RECT,
                                            CanvasTool.SHAPE_CIRCLE,
                                            CanvasTool.SHAPE_LINE
                                        ),
                                        onClick = { showShapesMenu = true }
                                    )
                                    DropdownMenu(
                                        expanded = showShapesMenu,
                                        onDismissRequest = { showShapesMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rectangle") },
                                            leadingIcon = { Icon(Icons.Default.CropSquare, null) },
                                            onClick = {
                                                viewModel.selectTool(CanvasTool.SHAPE_RECT)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Circle / Ellipse") },
                                            leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, null) },
                                            onClick = {
                                                viewModel.selectTool(CanvasTool.SHAPE_CIRCLE)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Straight Line") },
                                            leadingIcon = { Icon(Icons.Default.Timeline, null) },
                                            onClick = {
                                                viewModel.selectTool(CanvasTool.SHAPE_LINE)
                                                showShapesMenu = false
                                            }
                                        )
                                    }
                                }

                                // Lasso Selection Tool
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_lasso,
                                    label = "Lasso",
                                    isSelected = uiState.selectedTool == CanvasTool.LASSO,
                                    selectedTint = OrangePrimary,
                                    onClick = selectLassoTool
                                )

                                // Collapse Chevron (<)
                                IconButton(
                                    onClick = { isPencaseCollapsed = true },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Collapse Dock",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Divider(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(1.dp),
                                    color = Color.LightGray
                                )

                                // 3 Stroke Width Presets (Horizontal bar line thickness)
                                val widthPresets =
                                    if (uiState.selectedTool == CanvasTool.ERASER) TouchNotesEraserPresets
                                    else TouchNotesWidthPresets
                                val activeWidth = uiState.activeStrokeWidth()
                                widthPresets.take(3).forEachIndexed { wIdx, (widthPx, _) ->
                                    val isSelected = activeWidth == widthPx
                                    val barHeight = when (wIdx) {
                                        0 -> 2.dp
                                        1 -> 4.dp
                                        else -> 6.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrangePrimary.copy(alpha = 0.12f) else Color.Transparent)
                                            .border(
                                                if (isSelected) 1.5.dp else 0.dp,
                                                if (isSelected) OrangePrimary else Color.Transparent,
                                                CircleShape
                                            )
                                            .clickable { viewModel.setStrokeWidth(widthPx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .height(barHeight)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(if (isSelected) OrangePrimary else Color(0xFF444444))
                                        )
                                    }
                                }

                                Divider(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(1.dp),
                                    color = Color.LightGray
                                )

                                // 3 Rounded-Square Color Swatches with checkmark
                                TouchNotesColors.take(3).forEach { c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c)
                                            .border(
                                                if (isSelected) 2.dp else 0.5.dp,
                                                if (isSelected) OrangePrimary else Color.LightGray,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.selectColor(c) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                // Color Picker (+) Button
                                val isCustomColorActive = uiState.selectedColor !in TouchNotesColors.take(3)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isCustomColorActive) uiState.selectedColor else OrangePrimary)
                                        .border(
                                            if (isCustomColorActive) 2.dp else 0.dp,
                                            if (isCustomColorActive) OrangePrimary else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { showCustomColorDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Custom Color",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Vertical Dock (Docked on Left)
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White.copy(alpha = 0.96f),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(22.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Drag handle pill (Tap to toggle orientation)
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.LightGray)
                                        .clickable { viewModel.toggleDockOrientation() }
                                )

                                // Fountain Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fountain_pen,
                                    label = "Fountain Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.FOUNTAIN_PEN,
                                    onClick = { viewModel.selectTool(CanvasTool.FOUNTAIN_PEN) }
                                )

                                // Fineliner / Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fineliner,
                                    label = "Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.PEN,
                                    onClick = { viewModel.selectTool(CanvasTool.PEN) }
                                )

                                // Yellow Highlighter
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_highlighter,
                                    label = "Highlighter",
                                    isSelected = uiState.selectedTool == CanvasTool.HIGHLIGHTER,
                                    selectedTint = Color(0xFFFFD600),
                                    onClick = { viewModel.selectTool(CanvasTool.HIGHLIGHTER) }
                                )

                                // Pink Eraser
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_eraser,
                                    label = "Eraser",
                                    isSelected = uiState.selectedTool == CanvasTool.ERASER,
                                    selectedTint = Color(0xFFFF4081),
                                    onClick = { viewModel.selectTool(CanvasTool.ERASER) }
                                )

                                // Text Tool [T]
                                PencaseToolIcon(
                                    icon = Icons.Default.TextFields,
                                    label = "Text",
                                    isSelected = pdfTextMode,
                                    onClick = {
                                        if (pdfTextAvailable && vectorPdfPage(viewModel.currentPageIndex.value) != null) {
                                            val next = !pdfTextMode
                                            pdfTextMode = next
                                            if (!next) pdfSelection = null
                                            canvasViewRef?.setPdfTextSelectionMode(next)
                                        } else {
                                            textInputText = ""
                                            showTextInputDialog = true
                                        }
                                    }
                                )

                                // Shapes Tool
                                val shapeIconHoriz = when (uiState.selectedTool) {
                                    CanvasTool.SHAPE_RECT -> Icons.Default.CropSquare
                                    CanvasTool.SHAPE_CIRCLE -> Icons.Default.RadioButtonUnchecked
                                    CanvasTool.SHAPE_LINE -> Icons.Default.Timeline
                                    else -> Icons.Default.FormatShapes
                                }
                                Box {
                                    PencaseToolIcon(
                                        icon = shapeIconHoriz,
                                        label = "Shapes",
                                        isSelected = uiState.selectedTool in listOf(
                                            CanvasTool.SHAPE_RECT,
                                            CanvasTool.SHAPE_CIRCLE,
                                            CanvasTool.SHAPE_LINE
                                        ),
                                        onClick = { showShapesMenu = true }
                                    )
                                    DropdownMenu(
                                        expanded = showShapesMenu,
                                        onDismissRequest = { showShapesMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rectangle") },
                                            leadingIcon = { Icon(Icons.Default.CropSquare, null) },
                                            onClick = {
                                                viewModel.selectTool(CanvasTool.SHAPE_RECT)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Circle") },
                                            leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, null) },
                                            onClick = {
                                                viewModel.selectTool(CanvasTool.SHAPE_CIRCLE)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Line") },
                                            leadingIcon = { Icon(Icons.Default.Timeline, null) },
                                            onClick = {
                                                viewModel.selectTool(CanvasTool.SHAPE_LINE)
                                                showShapesMenu = false
                                            }
                                        )
                                    }
                                }

                                // Lasso Selection Tool
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_lasso,
                                    label = "Lasso",
                                    isSelected = uiState.selectedTool == CanvasTool.LASSO,
                                    selectedTint = OrangePrimary,
                                    onClick = selectLassoTool
                                )

                                // Collapse Chevron (^)
                                IconButton(
                                    onClick = { isPencaseCollapsed = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Collapse Dock",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Divider(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(1.dp),
                                    color = Color.LightGray
                                )

                                // 3 Stroke Width Presets (vertical line thickness bars)
                                val verticalPresets =
                                    if (uiState.selectedTool == CanvasTool.ERASER) TouchNotesEraserPresets
                                    else TouchNotesWidthPresets
                                val verticalActive = uiState.activeStrokeWidth()
                                verticalPresets.take(3).forEachIndexed { vIdx, (widthPx, _) ->
                                    val isSelected = verticalActive == widthPx
                                    val barH = when (vIdx) {
                                        0 -> 2.dp
                                        1 -> 4.dp
                                        else -> 6.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrangePrimary.copy(alpha = 0.12f) else Color.Transparent)
                                            .border(
                                                if (isSelected) 1.5.dp else 0.dp,
                                                if (isSelected) OrangePrimary else Color.Transparent,
                                                CircleShape
                                            )
                                            .clickable { viewModel.setStrokeWidth(widthPx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(13.dp)
                                                .height(barH)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(if (isSelected) OrangePrimary else Color(0xFF444444))
                                        )
                                    }
                                }

                                Divider(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(1.dp),
                                    color = Color.LightGray
                                )

                                // 3 Rounded-Square Color Swatches with checkmark
                                TouchNotesColors.take(3).forEach { c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c)
                                            .border(
                                                if (isSelected) 2.dp else 0.5.dp,
                                                if (isSelected) OrangePrimary else Color.LightGray,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.selectColor(c) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                // Color Picker (+) Button
                                val isCustomColorActiveVert = uiState.selectedColor !in TouchNotesColors.take(3)
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isCustomColorActiveVert) uiState.selectedColor else OrangePrimary)
                                        .border(
                                            if (isCustomColorActiveVert) 2.dp else 0.dp,
                                            if (isCustomColorActiveVert) OrangePrimary else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { showCustomColorDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Custom Color",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 4. FLOATING VERTICAL PAGE STRIP (Right Edge)
                // ==========================================
                if (!uiState.isToolsPanelOpen && !uiState.readingMode) {
                    Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle pill
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(3.dp)
                                .background(Color(0xFFCCCCCC), RoundedCornerShape(1.5.dp))
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Previous Page Arrow
                        IconButton(
                            onClick = {
                                if (isContinuous) {
                                    val target = (currentPageIndex - 1).coerceAtLeast(0)
                                    viewModel.setCurrentPageIndex(target)
                                    scrollToContinuousPage(target)
                                } else {
                                    scope.launch {
                                        try {
                                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                viewModel.saveCurrentPageBitmap(bmp)
                                            }
                                            // Await the layer save (IO) so nothing
                                            // writes layers while the page switch
                                            // below reloads them.
                                            withContext(Dispatchers.IO) {
                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                        val currentBmp = canvasViewRef?.getBackingBitmap()
                                        viewModel.previousPage(currentBmp) { newBmp ->
                                            applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                        }
                                    }
                                }
                            },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.pageTurningDirection == 1) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.ArrowUpward,
                                contentDescription = "Prev Page",
                                tint = if (currentPageIndex > 0) Color.DarkGray else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Current Page Number (Clickable to open drawer)
                        Text(
                            text = "${currentPageIndex + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary,
                            modifier = Modifier
                                .clickable { viewModel.toggleThumbnailDrawer(true) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        Divider(
                            modifier = Modifier
                                .width(16.dp)
                                .padding(vertical = 3.dp),
                            color = Color.LightGray
                        )

                        // Total Pages (Clickable to open drawer)
                        Text(
                            text = "$totalPages",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier
                                .clickable { viewModel.toggleThumbnailDrawer(true) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        // Next Page Arrow
                        IconButton(
                            onClick = {
                                if (isContinuous) {
                                    val target = (currentPageIndex + 1).coerceAtMost(totalPages - 1)
                                    viewModel.setCurrentPageIndex(target)
                                    scrollToContinuousPage(target)
                                } else {
                                    scope.launch {
                                        try {
                                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                viewModel.saveCurrentPageBitmap(bmp)
                                            }
                                            // Await the layer save (IO) so nothing
                                            // writes layers while the page switch
                                            // below reloads them.
                                            withContext(Dispatchers.IO) {
                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                        val currentBmp = canvasViewRef?.getBackingBitmap()
                                        viewModel.nextPage(currentBmp) { newBmp ->
                                            applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                        }
                                    }
                                }
                            },
                            enabled = currentPageIndex < totalPages - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.pageTurningDirection == 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.ArrowDownward,
                                contentDescription = "Next Page",
                                tint = if (currentPageIndex < totalPages - 1) Color.DarkGray else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Divider(
                            modifier = Modifier
                                .width(16.dp)
                                .padding(vertical = 4.dp),
                            color = Color.LightGray
                        )

                        // Layers popover toggle inside right-edge capsule
                        IconButton(
                            onClick = { showLayersPanel = !showLayersPanel },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Layers",
                                tint = if (showLayersPanel) OrangePrimary else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

                // ==========================================
                // 5. BOTTOM-LEFT FLOATING PAGE JUMPER (< | > split pill)
                // ==========================================
                if (totalPages > 1 && !uiState.isThumbnailDrawerOpen && !uiState.readingMode) {
                    Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isContinuous) {
                                    val target = (currentPageIndex - 1).coerceAtLeast(0)
                                    viewModel.setCurrentPageIndex(target)
                                    scrollToContinuousPage(target)
                                } else {
                                    scope.launch {
                                        canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                            viewModel.saveCurrentPageBitmap(bmp)
                                        }
                                        if (canvasViewRef?.layersDirty == true) {
                                            withContext(Dispatchers.IO) {
                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                            }
                                        }
                                        viewModel.previousPage(canvasViewRef?.getBackingBitmap()) { newBmp ->
                                            applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                        }
                                    }
                                }
                            },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page", modifier = Modifier.size(14.dp), tint = if (currentPageIndex > 0) Color.DarkGray else Color.LightGray)
                        }

                        Divider(
                            modifier = Modifier
                                .height(16.dp)
                                .width(1.dp)
                                .padding(horizontal = 2.dp),
                            color = Color(0xFFEEEEEE)
                        )

                        IconButton(
                            onClick = {
                                if (isContinuous) {
                                    val target = (currentPageIndex + 1).coerceAtMost(totalPages - 1)
                                    viewModel.setCurrentPageIndex(target)
                                    scrollToContinuousPage(target)
                                } else {
                                    scope.launch {
                                        canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                            viewModel.saveCurrentPageBitmap(bmp)
                                        }
                                        if (canvasViewRef?.layersDirty == true) {
                                            withContext(Dispatchers.IO) {
                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                            }
                                        }
                                        viewModel.nextPage(canvasViewRef?.getBackingBitmap()) { newBmp ->
                                            applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                        }
                                    }
                                }
                            },
                            enabled = currentPageIndex < totalPages - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page", modifier = Modifier.size(14.dp), tint = if (currentPageIndex < totalPages - 1) Color.DarkGray else Color.LightGray)
                        }
                    }
                }
            }

                // ==========================================
                // 5b. FLOATING PEN BOX
                // ==========================================
                if (uiState.penBoxEnabled && !uiState.readingMode && !uiState.isThumbnailDrawerOpen) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 64.dp)
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(uiState.penSlot1Color))
                                    .clickable {
                                        viewModel.selectColor(Color(uiState.penSlot1Color))
                                        viewModel.setStrokeWidth(uiState.penSlot1Width)
                                        viewModel.selectTool(CanvasTool.PEN)
                                    }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(uiState.penSlot2Color))
                                    .clickable {
                                        viewModel.selectColor(Color(uiState.penSlot2Color))
                                        viewModel.setStrokeWidth(uiState.penSlot2Width)
                                        viewModel.selectTool(CanvasTool.PEN)
                                    }
                            )
                        }
                    }
                }

                // ==========================================
                // 6. LEFT SLIDE-OUT PAGE THUMBNAIL DRAWER
                // ==========================================
                if (uiState.isThumbnailDrawerOpen) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(300.dp)
                            .fillMaxHeight(),
                        color = Color.White,
                        shadowElevation = 12.dp
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Column: Drawer Tab Content
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 14.dp, top = 16.dp, end = 10.dp, bottom = 16.dp)
                            ) {
                                when (uiState.drawerTab) {
                                    DrawerTab.THUMBNAILS -> {
                                        // Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Pages ($totalPages)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color(0xFF212529)
                                            )
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        if (isContinuous) {
                                                            inkController.flushCurrentSuspend()
                                                        }
                                                        val currentBmp = if (isContinuous) null else canvasViewRef?.getBackingBitmap()
                                                        viewModel.addNewPage(currentBmp) { newBmp ->
                                                            if (!isContinuous) {
                                                                applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                            } else {
                                                                scrollToContinuousPage(viewModel.currentPageIndex.value)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Page",
                                                    tint = OrangePrimary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(pages, key = { it.id }) { pageItem ->
                                                val isSelected = pageItem.index == currentPageIndex
                                                val isBookmarked = uiState.bookmarkedPages.contains(pageItem.index)
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            if (pageItem.index != currentPageIndex) {
                                                                if (isContinuous) {
                                                                    viewModel.setCurrentPageIndex(pageItem.index)
                                                                    scrollToContinuousPage(pageItem.index)
                                                                } else {
                                                                    scope.launch {
                                                                        if (canvasViewRef?.layersDirty == true) {
                                                                            withContext(Dispatchers.IO) {
                                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                                            }
                                                                        }
                                                                        val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                        viewModel.goToPage(pageItem.index, currentBmp) { newBmp ->
                                                                            applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            viewModel.toggleThumbnailDrawer(false)
                                                        },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) OrangePrimary.copy(alpha = 0.08f) else Color(0xFFFAFAFA)
                                                    ),
                                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, OrangePrimary) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                                                ) {
                                                PageThumbnailCard(
                                                        noteId = viewModel.noteId,
                                                        fileStore = viewModel.fileStore,
                                                        pageItem = pageItem,
                                                        pdfDocCache = pdfDocCache,
                                                        isSelected = isSelected,
                                                        isBookmarked = isBookmarked,
                                                        totalPages = totalPages,
                                                        savedGeneration = savedPageGenerations[pageItem.index] ?: 0,
                                                        onToggleBookmark = { viewModel.toggleBookmark(pageItem.index) },
                                                        onDeletePage = {
                                                            pageToDeleteIndex = pageItem.index
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    DrawerTab.BOOKMARKS -> {
                                        // Bookmarks Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Bookmarks (${uiState.bookmarkedPages.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color(0xFF212529)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val bookmarkedList = pages.filter { it.index in uiState.bookmarkedPages }
                                        if (bookmarkedList.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(16.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.BookmarkBorder,
                                                        contentDescription = null,
                                                        tint = Color.LightGray,
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Text(
                                                        text = "No Bookmarks Yet",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp,
                                                        color = Color.DarkGray
                                                    )
                                                    Text(
                                                        text = "Tap the bookmark icon on any page card to pin it here.",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(bookmarkedList, key = { it.id }) { pageItem ->
                                                    val isSelected = pageItem.index == currentPageIndex
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (pageItem.index != currentPageIndex) {
                                                                    if (isContinuous) {
                                                                        viewModel.setCurrentPageIndex(pageItem.index)
                                                                        scrollToContinuousPage(pageItem.index)
                                                                    } else {
                                                                        scope.launch {
                                                                            if (canvasViewRef?.layersDirty == true) {
                                                                                withContext(Dispatchers.IO) {
                                                                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                                                }
                                                                            }
                                                                            val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                            viewModel.goToPage(pageItem.index, currentBmp) { newBmp ->
                                                                                applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                viewModel.toggleThumbnailDrawer(false)
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isSelected) OrangePrimary.copy(alpha = 0.08f) else Color(0xFFFAFAFA)
                                                        ),
                                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, OrangePrimary) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                                                    ) {
                                                        PageThumbnailCard(
                                                            noteId = viewModel.noteId,
                                                            fileStore = viewModel.fileStore,
                                                            pageItem = pageItem,
                                                            pdfDocCache = pdfDocCache,
                                                            isSelected = isSelected,
                                                            isBookmarked = true,
                                                            totalPages = totalPages,
                                                            savedGeneration = savedPageGenerations[pageItem.index] ?: 0,
                                                            onToggleBookmark = { viewModel.toggleBookmark(pageItem.index) },
                                                            onDeletePage = {
                                                                pageToDeleteIndex = pageItem.index
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    DrawerTab.OUTLINE -> {
                                        // Outline (TOC) Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Outline (TOC)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color(0xFF212529)
                                            )
                                            IconButton(
                                                onClick = {
                                                    newChapterTitle = "Chapter ${uiState.tocEntries.size + 1}"
                                                    showAddChapterDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Chapter",
                                                    tint = OrangePrimary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (uiState.tocEntries.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(16.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                        contentDescription = null,
                                                        tint = Color.LightGray,
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Text(
                                                        text = "No Outline Entries",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp,
                                                        color = Color.DarkGray
                                                    )
                                                    Text(
                                                        text = "Tap + to add document chapters and sections.",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(uiState.tocEntries, key = { it.id }) { tocEntry ->
                                                    val isCurrentPage = tocEntry.pageIndex == currentPageIndex
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isCurrentPage) OrangePrimary.copy(alpha = 0.08f) else Color(0xFFFAFAFA),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            1.dp,
                                                            if (isCurrentPage) OrangePrimary else Color(0xFFE0E0E0)
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (tocEntry.pageIndex != currentPageIndex) {
                                                                    if (isContinuous) {
                                                                        viewModel.setCurrentPageIndex(tocEntry.pageIndex)
                                                                        scrollToContinuousPage(tocEntry.pageIndex)
                                                                    } else {
                                                                        scope.launch {
                                                                            if (canvasViewRef?.layersDirty == true) {
                                                                                withContext(Dispatchers.IO) {
                                                                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                                                }
                                                                            }
                                                                            val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                            viewModel.goToPage(tocEntry.pageIndex, currentBmp) { newBmp ->
                                                                                applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                viewModel.toggleThumbnailDrawer(false)
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                                    contentDescription = null,
                                                                    tint = if (isCurrentPage) OrangePrimary else Color.Gray,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = tocEntry.title,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (isCurrentPage) FontWeight.Bold else FontWeight.Medium,
                                                                    color = if (isCurrentPage) OrangePrimary else Color(0xFF212529)
                                                                )
                                                            }

                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = if (isCurrentPage) OrangePrimary else Color(0xFFEAEAEA)
                                                                ) {
                                                                    Text(
                                                                        text = "P.${tocEntry.pageIndex + 1}",
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isCurrentPage) Color.White else Color.DarkGray,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                                IconButton(
                                                                    onClick = { viewModel.deleteTocEntry(tocEntry.id) },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Close,
                                                                        contentDescription = "Delete",
                                                                        tint = Color.LightGray,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Side Action Strip (matching TouchNotes thumbnail drawer)
                            Surface(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight(),
                                color = Color(0xFFF7F7F7)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.setDrawerTab(DrawerTab.THUMBNAILS) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = "Preview",
                                            tint = if (uiState.drawerTab == DrawerTab.THUMBNAILS) OrangePrimary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.setDrawerTab(DrawerTab.BOOKMARKS) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Bookmarks",
                                            tint = if (uiState.drawerTab == DrawerTab.BOOKMARKS) OrangePrimary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.setDrawerTab(DrawerTab.OUTLINE) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = "Outline",
                                            tint = if (uiState.drawerTab == DrawerTab.OUTLINE) OrangePrimary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showClearDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Trash",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Close handle
                                    IconButton(
                                        onClick = { viewModel.toggleThumbnailDrawer(false) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Collapse",
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 7. RIGHT SLIDE-OVER MENU (TOOLS PANEL)
                // ==========================================
                if (uiState.isToolsPanelOpen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 14.dp, bottom = 16.dp)
                            .width(320.dp)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Tabs Pill Card: Tools | Export | Settings | Manager
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color.White,
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    val tabs = listOf("Tools", "Export", "Settings", "Manager")
                                    tabs.forEachIndexed { index, title ->
                                        val isSelected = uiState.activeToolsTab == index
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { viewModel.setActiveToolsTab(index) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) OrangePrimary else Color(0xFF333333)
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .width(24.dp)
                                                            .height(2.dp)
                                                            .background(OrangePrimary, RoundedCornerShape(1.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. More Settings Card (shown when on Settings tab)
                            if (uiState.activeToolsTab == 2) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            if (onSettingsClick != null) {
                                                viewModel.toggleToolsPanel(false)
                                                onSettingsClick()
                                            } else {
                                                android.widget.Toast.makeText(context, "Settings accessible from Note List", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("•••", fontSize = 16.sp, color = Color(0xFF333333), fontWeight = FontWeight.Bold)
                                        Text("More Settings", fontSize = 14.sp, color = Color(0xFF333333), fontWeight = FontWeight.Normal)
                                    }
                                }
                            }

                            // 3. Tab Content Card
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    when (uiState.activeToolsTab) {
                                        0 -> {
                                            // TOOLS TAB (Grouped cards matching TouchNotes Screenshot_20261107-132752)
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                // Productivity Integration
                                                Text("Productivity Integration", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFBFBFB),
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Language, "Web Excerpt") {
                                                            android.widget.Toast.makeText(context, "Web Excerpt: Share text or images directly to this note from browser or any app", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.AccountTree, "Open in CardsMind") {
                                                            viewModel.toggleThumbnailDrawer(true)
                                                            viewModel.setDrawerTab(DrawerTab.OUTLINE)
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                    }
                                                }

                                                // Add Section
                                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFBFBFB),
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Image, "Image") {
                                                            imagePickerLauncher.launch("image/*")
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Description, "Import PDF") {
                                                            scope.launch {
                                                                if (!isContinuous) {
                                                                    canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                                        viewModel.saveCurrentPageBitmap(bmp)
                                                                    }
                                                                } else {
                                                                    inkController.flushCurrentSuspend()
                                                                }
                                                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                                                viewModel.toggleToolsPanel(false)
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.EmojiEmotions, "Sticker") {
                                                            scope.launch {
                                                                insertLayerInCurrentNote { it.addTextBlock("★") }
                                                                viewModel.toggleToolsPanel(false)
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Timeline, "Add Mind Map") {
                                                            scope.launch {
                                                                insertLayerInCurrentNote { it.addTextBlock("○ Central Topic\n  ├ Idea 1\n  └ Idea 2") }
                                                                viewModel.toggleToolsPanel(false)
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Style, "Flashcard") {
                                                            viewModel.toggleToolsPanel(false)
                                                            onFlashcardsClick?.invoke()
                                                        }
                                                    }
                                                }

                                                // Page Section
                                                Text("Page", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFBFBFB),
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Add, "Add Page") {
                                                            scope.launch {
                                                                if (isContinuous) {
                                                                    inkController.flushCurrentSuspend()
                                                                }
                                                                val currentBmp = if (isContinuous) null else canvasViewRef?.getBackingBitmap()
                                                                viewModel.addNewPage(currentBmp) { newBmp ->
                                                                    if (!isContinuous) {
                                                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                                    } else {
                                                                        scrollToContinuousPage(viewModel.currentPageIndex.value)
                                                                    }
                                                                }
                                                            }
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.GridOn, "Template (${uiState.paperPattern.name})") {
                                                            val nextPattern = when (uiState.paperPattern) {
                                                                PaperPattern.BLANK -> PaperPattern.GRID
                                                                PaperPattern.GRID -> PaperPattern.LINES
                                                                PaperPattern.LINES -> PaperPattern.DOTTED
                                                                PaperPattern.DOTTED -> PaperPattern.BLANK
                                                            }
                                                            viewModel.setPaperPattern(nextPattern)
                                                            android.widget.Toast.makeText(context, "Paper pattern: ${nextPattern.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Layers, "Layer") {
                                                            showLayersPanel = true
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                    }
                                                }

                                                // Write Section
                                                Text("Write", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFBFBFB),
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Brush, "Zoom Write") {
                                                            if (isContinuous) {
                                                                continuousScale = 2.0f
                                                            } else {
                                                                canvasViewRef?.setZoomScale(2.0f)
                                                            }
                                                            viewModel.toggleToolsPanel(false)
                                                            android.widget.Toast.makeText(context, "Zoom Write: 200% detail view", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.AutoFixHigh, "Auto Recognition") {
                                                            viewModel.toggleAutoShape()
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                if (!uiState.autoShapeEnabled) "Auto shape recognition enabled" else "Auto shape recognition disabled",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Highlight, "Laser Pointer") {
                                                            viewModel.selectTool(CanvasTool.HIGHLIGHTER)
                                                            viewModel.toggleToolsPanel(false)
                                                            android.widget.Toast.makeText(context, "Highlighter pointer active", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }

                                                // Other Section
                                                Text("Other", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFBFBFB),
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Fullscreen, "FullScreen (Reading Mode)") {
                                                            viewModel.toggleReadingMode(true)
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Save, "Save Note") {
                                                            scope.launch {
                                                                flushCurrentNote()
                                                                viewModel.toggleToolsPanel(false)
                                                                android.widget.Toast.makeText(context, "Note saved", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Delete, "Clear Canvas") {
                                                            showClearDialog = true
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        1 -> {
                                            // EXPORT TAB
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Text("Document Export", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                                ToolsPanelItem(Icons.Default.Description, "Export as Multi-Page PDF") {
                                                    scope.launch {
                                                        flushCurrentNote()
                                                        selectedExportTarget = personal.notes.touch.ui.export.ExportTarget.PDF
                                                        showExportDialog = true
                                                        viewModel.toggleToolsPanel(false)
                                                    }
                                                }
                                                ToolsPanelItem(Icons.Default.Image, "Export Current Page as PNG") {
                                                    scope.launch {
                                                        flushCurrentNote()
                                                        selectedExportTarget = personal.notes.touch.ui.export.ExportTarget.PNG_CURRENT_PAGE
                                                        showExportDialog = true
                                                        viewModel.toggleToolsPanel(false)
                                                    }
                                                }
                                                ToolsPanelItem(Icons.Default.TextFields, "Export Plain Text / Markdown") {
                                                    scope.launch {
                                                        flushCurrentNote()
                                                        selectedExportTarget = personal.notes.touch.ui.export.ExportTarget.MARKDOWN
                                                        showExportDialog = true
                                                        viewModel.toggleToolsPanel(false)
                                                    }
                                                }
                                                ToolsPanelItem(Icons.Default.Share, "Share via Android Sheet") {
                                                    scope.launch {
                                                        flushCurrentNote()
                                                        selectedExportTarget = null
                                                        showExportDialog = true
                                                        viewModel.toggleToolsPanel(false)
                                                    }
                                                }
                                            }
                                        }
                                        2 -> {
                                            // SETTINGS TAB (1-to-1 match with TouchNotes Screenshot_20261107-133240)
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // 1. Stylus Mode
                                                SettingsCheckboxRow(
                                                    title = "Stylus Mode",
                                                    subtitle = "Only allow stylus writing permitted",
                                                    checked = uiState.stylusOnly,
                                                    onCheckedChange = { viewModel.toggleStylusOnly(it) }
                                                )
                                                // 2. Show Pencase
                                                SettingsCheckboxRow(
                                                    title = "Show Pencase",
                                                    subtitle = "",
                                                    checked = uiState.showPencase,
                                                    onCheckedChange = { viewModel.toggleShowPencase(it) }
                                                )
                                                // 3. Double Page Mode
                                                SettingsCheckboxRow(
                                                    title = "Double Page Mode",
                                                    subtitle = "Only work for vertical continuous scroll",
                                                    checked = uiState.doublePageMode,
                                                    onCheckedChange = { viewModel.toggleDoublePageMode(it) }
                                                )
                                                // 4. Pen-Tool
                                                SettingsCheckboxRow(
                                                    title = "Pen-Tool",
                                                    subtitle = "",
                                                    checked = uiState.penBoxEnabled,
                                                    onCheckedChange = { viewModel.togglePenBox(it) }
                                                )
                                                // 5. Reading Mode
                                                SettingsCheckboxRow(
                                                    title = "Reading Mode",
                                                    subtitle = "Editing notes is not allowed",
                                                    checked = uiState.readingMode,
                                                    onCheckedChange = { viewModel.toggleReadingMode(it) }
                                                )
                                                // 6. Show tabs
                                                SettingsCheckboxRow(
                                                    title = "Show tabs",
                                                    subtitle = "",
                                                    checked = showTabsState,
                                                    onCheckedChange = { viewModel.setShowTabs(it) }
                                                )
                                                // 7. Fix preview page
                                                SettingsCheckboxRow(
                                                    title = "Fix preview page",
                                                    subtitle = "",
                                                    checked = fixPreviewPageState,
                                                    onCheckedChange = { viewModel.setFixPreviewPage(it) }
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // 8. PAGE TURNING DIRECTION
                                                Text(
                                                    text = "Page Turning Direction",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFF333333),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val isVert = uiState.pageTurningDirection == 0
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .border(1.5.dp, OrangePrimary, RoundedCornerShape(16.dp))
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        // Vertical button (left half)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(if (isVert) OrangePrimary else Color.White)
                                                                .clickable { viewModel.setPageTurningDirection(0) },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "Vertical",
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isVert) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isVert) Color.White else OrangePrimary
                                                            )
                                                        }
                                                        // Horizontal button (right half)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(if (!isVert) OrangePrimary else Color.White)
                                                                .clickable { viewModel.setPageTurningDirection(1) },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "Horizontal",
                                                                fontSize = 12.sp,
                                                                fontWeight = if (!isVert) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (!isVert) Color.White else OrangePrimary
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                // 9. PAGE TURNING MODE
                                                val isInfiniteNote = (note?.pageType ?: "infinite") == "infinite"
                                                val isContinuousMode = !isInfiniteNote && uiState.pageTurningMode == 0
                                                val isSingleMode = isInfiniteNote || uiState.pageTurningMode == 1

                                                Text(
                                                    text = "Page Turning Mode",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFF333333),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .border(1.5.dp, OrangePrimary, RoundedCornerShape(16.dp))
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        // Continuous button (left half)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(if (isContinuousMode) OrangePrimary else Color.White)
                                                                .clickable(enabled = !isInfiniteNote) {
                                                                    if (!isContinuousMode) {
                                                                        scope.launch {
                                                                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                                                viewModel.saveCurrentPageBitmapDirect(bmp, force = true)
                                                                            }
                                                                            if (canvasViewRef?.layersDirty == true) {
                                                                                withContext(Dispatchers.IO) {
                                                                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                                                }
                                                                            }
                                                                            viewModel.setPageTurningMode(0)
                                                                            val cur = viewModel.currentPageIndex.value
                                                                            savedPageGenerations[cur] = (savedPageGenerations[cur] ?: 0) + 1
                                                                        }
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "Continuous",
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isContinuousMode) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isInfiniteNote) Color.LightGray else if (isContinuousMode) Color.White else OrangePrimary
                                                            )
                                                        }
                                                        // Single button (right half)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(if (isSingleMode) OrangePrimary else Color.White)
                                                                .clickable {
                                                                    if (!isSingleMode) {
                                                                        val targetIdx = (if (uiState.pageTurningDirection == 1) {
                                                                            continuousRowState.firstVisibleItemIndex
                                                                        } else if (uiState.doublePageMode) {
                                                                            val pairIdx = continuousColState.firstVisibleItemIndex
                                                                            val first = pairIdx * 2
                                                                            if (currentPageIndex == first || currentPageIndex == first + 1) currentPageIndex else first
                                                                        } else {
                                                                            continuousColState.firstVisibleItemIndex
                                                                        }).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                                                                        viewModel.setCurrentPageIndex(targetIdx)
                                                                        viewModel.setPageTurningMode(1)
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "Single",
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isSingleMode) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSingleMode) Color.White else OrangePrimary
                                                            )
                                                        }
                                                    }
                                                }
                                                if (isInfiniteNote) {
                                                    Text(
                                                        text = "Continuous not required for infinite",
                                                        fontSize = 10.sp,
                                                        color = Color.Gray,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 1.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                // 10. Horizontal Move Resistance
                                                Text(
                                                    text = "Horizontal Move Resistance(Double click for centering)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFF222222)
                                                )
                                                Slider(
                                                    value = horizontalResistance,
                                                    onValueChange = { viewModel.setHorizontalResistance(it) },
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = OrangePrimary,
                                                        activeTrackColor = OrangePrimary,
                                                        inactiveTrackColor = Color(0xFFEEEEEE)
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // 11. Minimal Zoom of Infinite
                                                Text(
                                                    text = "Minimal Zoom of Infinite: ${(minimalInfiniteZoom * 100).toInt()}%",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFF222222)
                                                )
                                                Slider(
                                                    value = minimalInfiniteZoom,
                                                    onValueChange = { viewModel.setMinimalInfiniteZoom(it) },
                                                    valueRange = 0.05f..1.0f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = OrangePrimary,
                                                        activeTrackColor = OrangePrimary,
                                                        inactiveTrackColor = Color(0xFFEEEEEE)
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                        3 -> {
                                            // MANAGER TAB (Grouped card matching TouchNotes Screenshot_20261107-132818)
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text("Note Manager", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFBFBFB),
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Edit, "Rename Note") {
                                                            newTitleText = note?.title ?: ""
                                                            showRenameDialog = true
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Layers, "Page Manager Dialog") {
                                                            viewModel.toggleThumbnailDrawer(true)
                                                            viewModel.toggleToolsPanel(false)
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Delete, "Delete Note") {
                                                            viewModel.toggleToolsPanel(false)
                                                            showDeleteNoteDialog = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun PencaseToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    drawableRes: Int? = null,
    label: String,
    isSelected: Boolean,
    selectedTint: Color = OrangePrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedTint.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = label,
                tint = if (isSelected) selectedTint else Color(0xFF444444),
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) selectedTint else Color(0xFF444444),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ToolsPanelItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF555555),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color(0xFF212529)
        )
    }
}

@Composable
fun SettingsCheckboxRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = OrangePrimary,
                uncheckedColor = Color(0xFF888888),
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF222222),
                textAlign = TextAlign.End
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF888888),
                    lineHeight = 12.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ContinuousPageView(
    pageIndex: Int,
    page: personal.notes.touch.data.db.entity.PageEntity,
    noteId: String,
    pdfDocCache: personal.notes.touch.util.PdfDocumentCache,
    repository: personal.notes.touch.data.repo.NoteRepository,
    zoom: Float,
    ink: ContinuousInkController,
    inkSettings: InkToolSettings,
    backgroundMode: Int = 0,
    backgroundDensity: Int = 40,
    savedGeneration: Int = 0,
    isCurrentPage: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0

    // Opening the PDF (read + parse in pdfium) has to happen off the main
    // thread, so the page lays out at the A4 ratio until the real one arrives.
    val pageRatio by produceState(
        initialValue = 0.707f,
        page.id, page.pdfSourcePath, page.pdfPageIndex
    ) {
        value = withContext(Dispatchers.IO) {
            if (!isVector) {
                0.707f
            } else {
                val size = pdfDocCache.document(page.pdfSourcePath)?.pageSize(page.pdfPageIndex)
                if (size != null && size[0] > 0f && size[1] > 0f) size[0] / size[1] else 0.707f
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(pageRatio)
            .clickable { onClick() }
    ) {
        val displayWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // The page is drawn at displayWidthPx * zoom screen pixels, so the PDF
        // is rendered at that width: pinching up reveals detail instead of
        // upscaling a fixed low-resolution bitmap (P1 audit). The caller
        // quantises zoom, so this key changes a few times per gesture rather
        // than on every frame.
        val desiredWidthPx = (displayWidthPx * zoom).roundToInt().coerceAtLeast(1)

        // Keep the previous bitmap on screen while a zoom bucket re-renders,
        // so pinching never flashes a placeholder.
        var pageBitmap by remember(page.id, page.updatedAt) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        var loadCompleted by remember(page.id, page.updatedAt) {
            mutableStateOf(false)
        }
        // A raster page is a fixed-size PNG: re-decoding it for every zoom
        // bucket buys nothing, so only vector pages key on the render width.
        val renderWidthKey = if (isVector) desiredWidthPx else 0

        // The inking session owns this page while the stylus is writing on it:
        // it holds the stroke layer (and, for vector pages, a clean pdfium
        // render to put it on), so the reader's own composite would double the
        // ink. Once it lets go, the composite is re-rendered from disk.
        ink.revision // observable: recompose when a stroke or layer changes
        val inkSession = ink.getSession(pageIndex)
        val sessionReady = inkSession?.isReady == true
        val activeSessionReady = ink.isActiveSession(pageIndex)

        LaunchedEffect(page.id, page.updatedAt, renderWidthKey, noteId, activeSessionReady, savedGeneration) {
            ink.refreshBackground(page, pageIndex, desiredWidthPx)
            if (activeSessionReady) {
                loadCompleted = true
                return@LaunchedEffect
            }
            // Let a pinch settle on its final bucket before allocating a
            // full-page bitmap; an intermediate key cancels this coroutine
            // while it is still cancellable (a native pdfium render is not).
            kotlinx.coroutines.delay(80)
            pageBitmap = withContext(Dispatchers.IO) {
                try {
                    if (isVector) {
                        renderContinuousPage(
                            page, pageIndex, noteId, pdfDocCache, repository, desiredWidthPx
                        )
                    } else {
                        // Legacy raster page: decode at the width actually
                        // shown instead of the full canvas size.
                        repository.loadPageBitmapForDisplay(
                            noteId, pageIndex, displayWidthPx.roundToInt()
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            loadCompleted = true
        }

        val shown = when {
            sessionReady && inkSession?.isVector == true -> inkSession.background
            sessionReady -> inkSession?.ink
            else -> pageBitmap
        }

        // White page bed: an erased raster pixel has to show paper, not the
        // reader's grey backdrop.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            shape = RoundedCornerShape(4.dp),
            border = if (isCurrentPage) {
                BorderStroke(1.5.dp, OrangePrimary.copy(alpha = 0.6f))
            } else {
                BorderStroke(1.dp, Color(0xFFDCDFE6))
            }
        ) {}

        // Draw paper pattern for non-vector pages (or vector pages with no background)
        if (!isVector) {
            val effectivePattern = when (PaperPattern.fromCode(page.paperPattern)) {
                PaperPattern.GRID -> PaperPattern.GRID
                PaperPattern.LINES -> PaperPattern.LINES
                PaperPattern.DOTTED -> PaperPattern.DOTTED
                PaperPattern.BLANK -> when (backgroundMode) {
                    1 -> PaperPattern.LINES
                    2 -> PaperPattern.GRID
                    3 -> PaperPattern.DOTTED
                    else -> PaperPattern.BLANK
                }
            }
            if (effectivePattern != PaperPattern.BLANK) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val density = backgroundDensity.coerceIn(20, 80).toFloat()
                    val step = (density * (size.width / 2160f)).coerceAtLeast(8f)
                    val w = size.width
                    val h = size.height
                    val gridColor = Color(0xFFE0E0E0)
                    when (effectivePattern) {
                        PaperPattern.LINES -> {
                            var y = step
                            while (y < h) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(w, y),
                                    strokeWidth = 1f
                                )
                                y += step
                            }
                        }
                        PaperPattern.GRID -> {
                            var x = step
                            while (x < w) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x, h),
                                    strokeWidth = 1f
                                )
                                x += step
                            }
                            var y = step
                            while (y < h) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(w, y),
                                    strokeWidth = 1f
                                )
                                y += step
                            }
                        }
                        PaperPattern.DOTTED -> {
                            var x = step
                            while (x < w) {
                                var y = step
                                while (y < h) {
                                    drawCircle(
                                        color = gridColor,
                                        radius = 1.5f,
                                        center = androidx.compose.ui.geometry.Offset(x, y)
                                    )
                                    y += step
                                }
                                x += step
                            }
                        }
                        PaperPattern.BLANK -> {}
                    }
                }
            }
        }

        if (shown != null && !shown.isRecycled) {
            androidx.compose.foundation.Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High
            )
        } else if (!loadCompleted && !sessionReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = OrangePrimary,
                    strokeWidth = 2.dp
                )
            }
        }

        // Vector pages: the annotations are a separate transparent layer drawn
        // over the clean page, exactly like single-page mode composites them.
        if (sessionReady && inkSession?.isVector == true) {
            val layer = inkSession.ink
            if (layer != null && !layer.isRecycled) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val _revision = ink.revision
                    drawImage(
                        image = layer.asImageBitmap(),
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(layer.width, layer.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        dstSize = androidx.compose.ui.unit.IntSize(
                            size.width.roundToInt().coerceAtLeast(1),
                            size.height.roundToInt().coerceAtLeast(1)
                        ),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                    )
                }
            }
        }

        // The in-progress stroke (and the eraser's swath hint) paints above
        // the committed layer; the commit lands on ACTION_UP.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val _revision = ink.revision
            drawIntoCanvas { canvas ->
                ink.drawPreview(canvas.nativeCanvas, pageIndex, size.width, size.height)
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.38f),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = "${pageIndex + 1}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Stylus input goes to the ink engine; fingers fall through to the
        // reader (scroll, pinch, tap-to-open). This is what makes writing in
        // continuous mode possible without giving up page turning.
        //
        // The pen stroke is handled here, in Compose's pointer pipeline, and
        // every change of it is consumed: this node is the leaf, so it runs
        // before the enclosing Lazy list and the pinch handler, and a consumed
        // change makes them give up the gesture. A MotionEvent interop bridge
        // could not do that (it leaves the initial down unconsumed so parent
        // scrollables keep working), which on device turned a stroke into a
        // page scroll one frame later.
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(page.id, inkSettings) {
                    if (inkSettings.readingMode) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        val isAllowedPointer = isStylus || (!inkSettings.stylusOnly && down.type == PointerType.Touch)
                        if (!isAllowedPointer || !inkSettings.isWritingTool) return@awaitEachGesture
                        if (size.width <= 0 || size.height <= 0) return@awaitEachGesture
                        
                        down.consume()
                        val boxW = size.width.toFloat()
                        val boxH = size.height.toFloat()
                        ink.beginStroke(
                            page = page,
                            pageIndex = pageIndex,
                            x = down.position.x / boxW,
                            y = down.position.y / boxH,
                            pressure = down.pressure,
                            boxWidth = boxW,
                            boxHeight = boxH,
                            renderWidthPx = desiredWidthPx,
                            settings = inkSettings
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            // If a second finger lands (pinch-to-zoom / multi-touch pan gesture),
                            // abort the stroke immediately so gestures take over smoothly.
                            if (event.changes.count { it.pressed } > 1) {
                                ink.cancelStroke()
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val x = change.position.x / boxW
                            val y = change.position.y / boxH
                            if (change.changedToUpIgnoreConsumed()) {
                                ink.endStroke(x, y, change.pressure, inkSettings)
                                change.consume()
                                break
                            }
                            ink.extendStroke(x, y, change.pressure, inkSettings)
                            change.consume()
                        }
                    }
                }
        )
    }
}

/**
 * Composites one vector-PDF page for continuous reading: the page is rendered
 * directly into the output bitmap (pdfium fills it white) and the annotation
 * layer is drawn on top, so only one full-page allocation exists per page.
 *
 * Bitmaps are deliberately not recycled here: the composable cannot know when
 * Compose has finished drawing a frame, and recycling a bitmap still
 * referenced by an ImageBitmap crashes ("trying to use a recycled bitmap").
 * The GC reclaims them once the composable drops its reference.
 */
internal suspend fun renderContinuousPage(
    page: personal.notes.touch.data.db.entity.PageEntity,
    pageIndex: Int,
    noteId: String,
    pdfDocCache: personal.notes.touch.util.PdfDocumentCache,
    repository: personal.notes.touch.data.repo.NoteRepository,
    desiredWidthPx: Int,
    includeInk: Boolean = true
): android.graphics.Bitmap? {
    val document = pdfDocCache.document(page.pdfSourcePath)
        ?: return repository.loadPageBitmap(noteId, pageIndex)
    val size = document.pageSize(page.pdfPageIndex)
    if (size[0] <= 0f || size[1] <= 0f) return repository.loadPageBitmap(noteId, pageIndex)

    val width = personal.notes.touch.util.PdfRenderPolicy
        .continuousRenderWidth(size[0], size[1], desiredWidthPx)
    if (width <= 0) return repository.loadPageBitmap(noteId, pageIndex)
    val height = (width * size[1] / size[0]).roundToInt().coerceAtLeast(1)

    val composite = try {
        android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ARGB_8888
        )
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        return null
    }
    if (!document.renderPageInto(page.pdfPageIndex, composite, width / size[0])) {
        composite.recycle()
        return repository.loadPageBitmap(noteId, pageIndex)
    }
    if (!includeInk) return composite
    val strokes = repository.loadPageStrokes(noteId, pageIndex)
    if (strokes != null && !strokes.isRecycled) {
        val canvas = android.graphics.Canvas(composite)
        val paint = android.graphics.Paint(
            android.graphics.Paint.FILTER_BITMAP_FLAG or
                android.graphics.Paint.ANTI_ALIAS_FLAG
        )
        canvas.drawBitmap(strokes, null, android.graphics.Rect(0, 0, width, height), paint)
    }
    return composite
}

@Composable
private fun PageThumbnailCard(
    noteId: String,
    fileStore: personal.notes.touch.data.files.NoteFileStore,
    pageItem: personal.notes.touch.data.db.entity.PageEntity,
    pdfDocCache: personal.notes.touch.util.PdfDocumentCache,
    isSelected: Boolean,
    isBookmarked: Boolean,
    totalPages: Int,
    savedGeneration: Int = 0,
    onToggleBookmark: () -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val pageFile = remember(noteId, pageItem.index) {
        fileStore.getPageFile(noteId, pageItem.index)
    }
    val strokesFile = remember(noteId, pageItem.index) {
        fileStore.getStrokesFile(noteId, pageItem.index)
    }

    val thumbnailBmp by produceState<Bitmap?>(initialValue = null, noteId, pageItem.index, pageItem.updatedAt, savedGeneration) {
        value = withContext(Dispatchers.IO) {
            val base = if (pageItem.pdfSourcePath.isNotEmpty() && pageItem.pdfPageIndex >= 0) {
                try {
                    val doc = pdfDocCache.document(pageItem.pdfSourcePath)
                    doc?.renderPage(pageItem.pdfPageIndex, targetWidth = 240, targetHeight = 340)
                } catch (e: Exception) {
                    null
                }
            } else if (pageFile.exists()) {
                personal.notes.touch.util.BitmapUtils.loadThumbnail(pageFile, targetWidth = 240, targetHeight = 340)
            } else null

            val strokes = if (strokesFile.exists()) {
                personal.notes.touch.util.BitmapUtils.loadAlphaThumbnail(strokesFile, targetWidth = 240, targetHeight = 340)
            } else null

            when {
                base != null && strokes != null -> {
                    val comp = base.copy(Bitmap.Config.ARGB_8888, true)
                    if (comp != null) {
                        android.graphics.Canvas(comp).drawBitmap(
                            strokes,
                            null,
                            android.graphics.Rect(0, 0, comp.width, comp.height),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        )
                        comp
                    } else base
                }
                base != null -> base
                strokes != null -> strokes
                else -> null
            }
        }
    }

    Column(
        modifier = modifier.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page preview box (portrait aspect ratio 0.707f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0xFFEAEAEA), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnailBmp
            if (bmp != null && !bmp.isRecycled) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Thumbnail for page ${pageItem.index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Text(
                    text = "Page ${pageItem.index + 1}",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            // Bookmark Ribbon overlay (TopStart)
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove Bookmark" else "Bookmark",
                    tint = if (isBookmarked) OrangePrimary else Color(0xFF888888).copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Footer Row: Centered Page Number + Overflow Menu on right
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(20.dp)) // balance width

            Text(
                text = "${pageItem.index + 1}",
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) OrangePrimary else Color(0xFF444444),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove Bookmark" else "Add Bookmark") },
                        onClick = {
                            showMenu = false
                            onToggleBookmark()
                        }
                    )
                    if (totalPages > 1) {
                        DropdownMenuItem(
                            text = { Text("Delete Page", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDeletePage()
                            }
                        )
                    }
                }
            }
        }
    }
}
