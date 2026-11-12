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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import personal.notes.touch.ui.components.GlassBackdrop
import personal.notes.touch.ui.components.GlassOrigin
import personal.notes.touch.ui.components.LiquidGlassShader
import personal.notes.touch.ui.components.liquidGlassBackdrop
import personal.notes.touch.ui.components.LiquidGlassStyle
import personal.notes.touch.ui.components.liquidGlassRim
import personal.notes.touch.ui.components.liquidStylusSpotlight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/**
 * The three colour presets the original dock shows: black, red and navy.
 * The orange of the app is the accent colour, not a preset.
 */
val TouchNotesDockColors = listOf(
    TouchNotesColors[0], // Black
    TouchNotesColors[2], // Crimson Red
    TouchNotesColors[3]  // Royal Blue
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

/**
 * Liquid glass, in two layers, the way a real glass panel behaves.
 *
 * The shell is genuinely see-through: its alpha runs from 85% at the lit
 * corner to 55% at the base, so paper, ink and the desk colour behind the
 * dock all read through it. An opaque body (the previous 93-98%) looked like
 * a plastic bar and produced a hard edge against the page.
 *
 * The gradient also carries a soft second reflection, so the body has depth
 * instead of one flat wash.
 */
private val GlassBodyBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color(0x8CFFFFFF), // 55% lit top-left shoulder
        0.22f to Color(0x73FFFFFF), // 45%
        0.46f to Color(0x5CFFFFFF), // 36% the optical body
        0.68f to Color(0x70FFFFFF), // 44% second, softer reflection
        1.00f to Color(0x4DFFFFFF)  // 30% base: the page shows through
    )
)

/** The dark variant of the glass, for the HUD pills over the reader. */
private val GlassDarkBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color(0xB01A1A1E),
        0.55f to Color(0x8C121214),
        1.00f to Color(0xA61A1A1E)
    )
)

/**
 * The brighter capsule the tools sit on. The shell alone lets dense ink run
 * under the icons and makes them unreadable; the lens keeps the centre of the
 * dock crisp while the rim stays transparent.
 */
private val GlassLensBrush = Brush.linearGradient(
    listOf(
        Color(0xA6FFFFFF), // 65% top of the lens
        Color(0x99FFFFFF), // 60%
        Color(0x8CFFFFFF)  // 55% bottom
    )
)

/**
 * Specular rim: a bright glint on the lit edge, a darker ambient-occlusion
 * rim on the shadow side so the pill still has an edge over white paper.
 */
private val GlassEdgeBrush = Brush.linearGradient(
    listOf(
        Color(0xF2FFFFFF), // specular glint, lit edge
        Color(0x99FFFFFF),
        Color(0x24000000), // ambient occlusion on the shadow side
        Color(0xB3FFFFFF)  // rim bounce
    )
)

/** Stroke width slider ranges (canvas px); the eraser works on a bigger scale. */
private const val PEN_WIDTH_MIN = 1f
private const val PEN_WIDTH_MAX = 40f
private const val ERASER_WIDTH_MIN = 8f
private const val ERASER_WIDTH_MAX = 80f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    /**
     * Loads the ink a page carries outside its paper (the margins of an
     * imported PDF). Reading the crops is file I/O, so it runs off the main
     * thread; the view is updated back on it.
     */
    val loadBeyondPageInk: (Int) -> Unit = { pageIdx ->
        val view = canvasViewRef
        if (view != null) {
            // The count is the guard: if the user drew in the margin while the
            // file was read, those strokes are newer and must not be dropped.
            val countAtStart = view.beyondPageInkCount()
            scope.launch {
                val crops = withContext(Dispatchers.IO) {
                    view.readBeyondPageInk(viewModel.fileStore, viewModel.noteId, pageIdx)
                }
                view.applyBeyondPageInk(crops, forPageIndex = pageIdx, expectedCount = countAtStart)
            }
        }
    }
    var selectedImageIndex by remember { mutableStateOf(-1) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportTarget by remember { mutableStateOf<personal.notes.touch.ui.export.ExportTarget?>(null) }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var showPdfPagePicker by remember { mutableStateOf(false) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    /** True while the next PDF is picked as a page background, not imported. */
    var pdfImportAsBackground by remember { mutableStateOf(false) }
    var pendingPdfPageCount by remember { mutableStateOf(0) }
    var pendingPdfPageIndex by remember { mutableStateOf("1") }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showShapesMenu by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var layersTick by remember { mutableStateOf(0) }
    /**
     * The layer panel of the reader: the drawing layers of the current page
     * (the base layer and the ones the user added on top of it).
     */
    var showLayerPanel by remember { mutableStateOf(false) }
    /** The layer whose menu is open in the layer panel (-1 = none). */
    var layerActionIndex by remember { mutableIntStateOf(-1) }
    /** Where the user dragged the layer panel to. */
    var layerPanelOffset by remember { mutableStateOf(Offset.Zero) }
    var showAddChapterDialog by remember { mutableStateOf(false) }
    var newChapterTitle by remember { mutableStateOf("") }
    var newTitleText by remember { mutableStateOf("") }
    var isLiveTextActive by remember { mutableStateOf(false) }
    var liveTextContent by remember { mutableStateOf("") }
    var liveTextOffsetX by remember { mutableFloatStateOf(0f) }
    var liveTextOffsetY by remember { mutableFloatStateOf(0f) }
    var liveTextSizeSp by remember { mutableFloatStateOf(20f) }
    var liveTextColor by remember { mutableStateOf(Color.Black) }
    val liveTextFocusRequester = remember { FocusRequester() }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var pageToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    /** Which sticker the Tools menu drops next. */
    var stickerIndex by remember { mutableIntStateOf(0) }
    var pencaseOffsetX by remember { mutableFloatStateOf(0f) }
    var pencaseOffsetY by remember { mutableFloatStateOf(0f) }
    // Long-press popups of the dock (width slider / full colour palette).
    var showWidthPopupHoriz by remember { mutableStateOf(false) }
    /** The width chip the slider popup belongs to (only that one shows it). */
    var widthPopupChipHoriz by remember { mutableIntStateOf(-1) }
    var showColorPopupHoriz by remember { mutableStateOf(false) }
    /** The swatch the colour popup belongs to (only that one shows it). */
    var colorPopupChipHoriz by remember { mutableIntStateOf(-1) }
    var showWidthPopupVert by remember { mutableStateOf(false) }
    /** The width chip the slider popup belongs to (only that one shows it). */
    var widthPopupChipVert by remember { mutableIntStateOf(-1) }
    var showColorPopupVert by remember { mutableStateOf(false) }
    /** The swatch the colour popup belongs to (only that one shows it). */
    var colorPopupChipVert by remember { mutableIntStateOf(-1) }

    val isInfinite = (note?.pageType ?: "infinite") == "infinite"
    val isContinuous = uiState.pageTurningMode == 0
    /** An infinite canvas is endless: no page frame, no desk, no edge. */
    val isInfiniteNote = note?.pageType == "infinite"
    val isHorizontal = uiState.pageTurningDirection == 1

    var continuousScale by remember { mutableFloatStateOf(1f) }
    var continuousPanX by remember { mutableFloatStateOf(0f) }
    var continuousPanY by remember { mutableFloatStateOf(0f) }

    val showTabsState = uiState.showTabs
    val fixPreviewPageState = uiState.fixPreviewPage
    val horizontalResistance = uiState.horizontalResistance
    val minimalInfiniteZoom = uiState.minimalInfiniteZoom

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

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
        showLayerPanel = false
        layerActionIndex = -1
        // The width and colour popups are floating windows: a touch in the
        // canvas has to close them as well, or they stay on top of the page.
        showWidthPopupHoriz = false
        showWidthPopupVert = false
        widthPopupChipHoriz = -1
        widthPopupChipVert = -1
        showColorPopupHoriz = false
        showColorPopupVert = false
        colorPopupChipHoriz = -1
        colorPopupChipVert = -1
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

    // Read-only layer cache of the continuous reader: decodes the same page
    // layer files the single-page engine writes, off the main thread.
    val readerLayers = remember(viewModel.noteId) {
        ReaderLayerCache(viewModel.fileStore, viewModel.noteId)
    }

    /**
     * Page whose pixels are currently loaded in the hidden canvas for layer
     * editing in continuous mode (-1 = none). Reset on every page change.
     */
    var engineLayerPage by remember { mutableStateOf(-1) }

    /**
     * How the reader's current page maps page pixels onto the screen
     * (root coordinates): `root = page * scale + offset`. The live text box
     * is placed in screen coordinates, so committing it needs this map; the
     * hidden canvas has its own transform and must not be used for it.
     */
    val continuousPageMap = remember { ContinuousPageMapHolder() }

    /** Top-left of the drawing canvas in root coordinates (single-page mode). */
    var canvasOriginInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // The reader's scroll state: the glass watches its offset, so the dock
    // redraws while the page scrolls.
    val continuousColState = androidx.compose.foundation.lazy.rememberLazyListState()
    val continuousRowState = androidx.compose.foundation.lazy.rememberLazyListState()

    // ---- Liquid glass ----------------------------------------------------
    // The dock is refractive: the page under it is sampled through an AGSL
    // shader, so the ink bends at the bezel instead of being hidden by a
    // white slab. The shader is compiled once, only its uniforms change.
    val glassShader = remember { LiquidGlassShader() }
    /** Where the floating dock sits on the screen (root pixels). */
    val glassDockOrigin = remember { GlassOrigin() }
    /** The page bitmap the reader draws: the backdrop in reader mode. */
    var glassReaderBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    /** The page the reader bitmap belongs to. */
    var glassReaderBitmapPage by remember { mutableIntStateOf(-1) }
    /** The origins of the floating glass elements. */
    val glassDockOriginHolder = remember { GlassOrigin() }
    /**
     * Bumped by the engine on every pan and zoom.
     *
     * The glass samples the page under the dock, so the dock has to redraw
     * when the page moves. The tick is read inside the draw lambda: that
     * invalidates the draw phase only, not the composition.
     */
    var glassViewportTick by remember { mutableIntStateOf(0) }
    val glassStylusOrigin = remember { GlassOrigin() }
    val glassZoomOrigin = remember { GlassOrigin() }
    /** The screen position of the width popup windows. */
    val glassWidthPopupHoriz = remember { GlassOrigin() }
    val glassWidthPopupVert = remember { GlassOrigin() }
    /**
     * The offset of the app's root view inside the window.
     *
     * A popup lives in its own window and is positioned in window
     * coordinates, while the glass works in root coordinates: the two have to
     * be brought into the same system.
     */
    val localRootView = LocalView.current
    val rootViewOffset = remember(localRootView) {
        val loc = IntArray(2)
        localRootView.getLocationInWindow(loc)
        Offset(loc[0].toFloat(), loc[1].toFloat())
    }

    /**
     * The page pixels under the dock, in page coordinates.
     *
     * In the reader the page map is known (page pixels -> root pixels); in
     * single-page mode the canvas engine maps its own view space to the page.
     */
    val glassBackdropAt: (GlassOrigin) -> GlassBackdrop? = { origin ->
        val view = canvasViewRef
        if (isContinuous) {
            // The reader scrolls in a Lazy list; its offset is the signal
            // that the page under the dock moved.
            @Suppress("UNUSED_EXPRESSION")
            if (uiState.doublePageMode || isHorizontal) {
                continuousRowState.firstVisibleItemIndex
                continuousRowState.firstVisibleItemScrollOffset
            } else {
                continuousColState.firstVisibleItemIndex
                continuousColState.firstVisibleItemScrollOffset
            }
            // The image map: the bitmap the glass samples is the page image,
            // and it is rendered at the display resolution - the ink space
            // (the map above) has different pixels.
            val map = continuousPageMap.imageMap
            val bmp = glassReaderBitmap
            if (map == null || bmp == null || map.scale <= 0f ||
                map.pageIndex != glassReaderBitmapPage
            ) {
                null
            } else {
                GlassBackdrop(
                    bitmap = bmp,
                    pageOriginX = (origin.x - map.offsetX) / map.scale,
                    pageOriginY = (origin.y - map.offsetY) / map.scale,
                    invScale = 1f / map.scale
                )
            }
        } else {
            // Read inside the draw: the draw phase watches the tick and
            // redraws the glass when the page was panned or zoomed.
            @Suppress("UNUSED_EXPRESSION")
            glassViewportTick
            val bmp = view?.getBackingBitmap()
            if (view == null || bmp == null) {
                null
            } else {
                val lx = origin.x - canvasOriginInRoot.x
                val ly = origin.y - canvasOriginInRoot.y
                val p0 = view.viewPointToPage(lx, ly)
                val p1 = view.viewPointToPage(lx + 1f, ly)
                val sx = p1[0] - p0[0]
                if (sx <= 0.0001f) null else GlassBackdrop(bmp, p0[0], p0[1], 1f / sx)
            }
        }
    }

    /** Top-left of the live text field's typing area, in root coordinates. */
    var liveTextAnchorInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Single-page edits change the layer files: drop the reader copy when the
    // user comes back to the reader so it shows the current files.
    LaunchedEffect(uiState.pageTurningMode) {
        if (uiState.pageTurningMode == 0) readerLayers.invalidateAll()
        // The reader's page map is meaningless in single-page mode.
        continuousPageMap.map = null
    }

    /**
     * Converts a point in root/screen coordinates to page pixels, the space
     * [ImageLayer] positions and page ink use.
     *
     * Continuous mode: the reader draws the page with its own fit rect, so the
     * map it published for the current page is inverted. Single-page mode: the
     * canvas view is on screen, so its own matrix is inverted.
     */
    val screenPointToPage: (androidx.compose.ui.geometry.Offset) -> androidx.compose.ui.geometry.Offset? =
        { point ->
            if (isContinuous) {
                val map = continuousPageMap.map?.takeIf { it.pageIndex == currentPageIndex }
                if (map != null && map.scale > 0f) {
                    androidx.compose.ui.geometry.Offset(
                        (point.x - map.offsetX) / map.scale,
                        (point.y - map.offsetY) / map.scale
                    )
                } else null
            } else {
                val view = canvasViewRef
                if (view == null) null else {
                    val pts = view.viewPointToPage(
                        point.x - canvasOriginInRoot.x,
                        point.y - canvasOriginInRoot.y
                    )
                    androidx.compose.ui.geometry.Offset(pts[0], pts[1])
                }
            }
        }

    // A layer selection and the hidden engine's page belong to one page only.
    LaunchedEffect(currentPageIndex) {
        engineLayerPage = -1
        selectedImageIndex = -1
        canvasViewRef?.selectedImageIndex = -1
    }

    // In-place stylus inking for the continuous reader: one page layer at a
    // time, saved through the same repository calls single-page mode uses.
    // The note (and with it the type of its pages) is loaded asynchronously:
    // the controller has to be rebuilt when it arrives, or every note would
    // be treated as an infinite canvas (no margin, no page edge).
    val inkController = remember(viewModel.noteId, note?.pageType) {
        val app = context.applicationContext as? TouchNotesApp
        ContinuousInkController(
            noteId = viewModel.noteId,
            repository = viewModel.repository,
            pdfDocCache = pdfDocCache,
            scope = scope,
            appScope = app?.appScope ?: scope,
            // The type of the note decides the shape of the page box: an
            // infinite page has no edge, a fixed page has a margin.
            pageType = note?.pageType ?: "infinite",
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

    /**
     * Continuous reading is on for every note type now (the infinite canvas
     * included), so this is the single definition the screen uses.
     */
    val continuousMode = uiState.pageTurningMode == 0


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
        val continuousReading = continuousMode
        if (continuousReading) return@LaunchedEffect

        // When leaving continuous mode to single-page mode, flush and release
        // any active continuous session first so the latest strokes are on disk
        // before loadPageStrokes / loadCurrentPageBitmap reads them.
        inkController.releaseSessionSuspend()

        // From here the canvas holds currentPageIndex, so saves triggered
        // before the next page flip must be filed under this index.
        viewModel.setCanvasPageIndex(currentPageIndex)
        // The layers are page scoped: the view must name the page before it
        // reads them, or it decodes the previous page's manifest.
        canvasViewRef?.pageIndex = currentPageIndex
        // A layer selection belongs to the old page.
        selectedImageIndex = -1
        canvasViewRef?.selectedImageIndex = -1
        engineLayerPage = -1
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
            // The margins of the page carry their own ink.
            loadBeyondPageInk(currentPageIndex)
        }
        try {
            // Layer PNG decode is file I/O: read on Dispatchers.IO, apply on
            // main. A page without a manifest has no layers, so the apply
            // clears the previous page's list instead of leaking it.
            canvasViewRef?.reloadLayersFromDisk(viewModel.fileStore, viewModel.noteId)
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

    /**
     * Points the hidden engine at [page] so layer reads/writes name that
     * page. In continuous mode [withPagePixels] also installs the page's own
     * pixel size in the view first: a layer inserted then gets page
     * coordinates, which is what the reader maps back onto the page.
     */
    suspend fun prepareLayerEngine(page: Int, withPagePixels: Boolean) {
        val view = canvasViewRef ?: return
        if (view.pageIndex != page) {
            // Flush the outgoing page before the index moves: the layer save
            // files the list under imageLayersPageIndex, but the margin ink
            // under pageIndex, so both still have to name the old page.
            if (view.layersDirty) {
                view.saveImageLayers(viewModel.fileStore, viewModel.noteId)
            }
            view.pageIndex = page
        }
        if (withPagePixels && engineLayerPage != page) {
            val pageBitmap = withContext(Dispatchers.IO) {
                viewModel.repository.loadPageBitmap(viewModel.noteId, page)
            }
            if (pageBitmap != null) {
                view.setPageContent(pageBitmap)
            } else {
                // A page that has never been rasterised: the continuous
                // reader's page box is A4-like, so use that page space.
                val blank = withContext(Dispatchers.IO) {
                    personal.notes.touch.util.BitmapUtils.createSafeBitmap(2160, 3056)
                }
                view.setPageContent(blank)
            }
            engineLayerPage = page
        }
        if (view.imageLayersPageIndex != page) {
            // The margin crops in memory belong to the page being left, and
            // the next save writes whatever is in memory under this page:
            // install this page's own crops before reloading its layers.
            val ownInk = withContext(Dispatchers.IO) {
                view.readBeyondPageInk(viewModel.fileStore, viewModel.noteId, page)
            }
            view.applyBeyondPageInk(ownInk, forPageIndex = page)
            view.reloadLayersFromDisk(viewModel.fileStore, viewModel.noteId)
        }
    }

    // Shared layer insertion helper: hydrates the current page's layers (and
    // page pixel space in continuous mode), appends, persists, and publishes
    // the result to the reader so it repaints without a disk round trip.
    val insertLayerInCurrentNote: suspend (action: (DrawingCanvasView) -> Boolean) -> Boolean = { action ->
        val view = canvasViewRef
        if (view != null) {
            val page = viewModel.currentPageIndex.value
            if (isContinuous) {
                // The page may hold unsaved strokes in its ink session.
                inkController.flushCurrentSuspend()
                prepareLayerEngine(page, withPagePixels = true)
            } else {
                view.pageIndex = page
            }
            val success = action(view)
            if (success) {
                try {
                    view.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                    if (isContinuous) {
                        val bmp = view.getBackingBitmap()
                        readerLayers.put(
                            page,
                            view.imageLayers.toList(),
                            bmp?.width ?: 0,
                            bmp?.height ?: 0
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            success
        } else false
    }

    /**
     * Applies one layer edit to the current page, persists it, and (in
     * continuous mode) publishes the engine's list to the reader cache so the
     * page repaints immediately.
     */
    val editCurrentLayers: suspend (Boolean, (DrawingCanvasView) -> Unit) -> Unit = { withPagePixels, action ->
        val view = canvasViewRef
        if (view != null) {
            val page = viewModel.currentPageIndex.value
            if (isContinuous) {
                prepareLayerEngine(page, withPagePixels)
            } else {
                view.pageIndex = page
            }
            action(view)
            try {
                view.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                if (isContinuous) {
                    if (engineLayerPage == page) {
                        val bmp = view.getBackingBitmap()
                        readerLayers.put(
                            page,
                            view.imageLayers.toList(),
                            bmp?.width ?: 0,
                            bmp?.height ?: 0
                        )
                    } else {
                        // The backing bitmap is not this page's: keep the
                        // page size the cache read from the page file.
                        readerLayers.put(page, view.imageLayers.toList())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
            if (pdfImportAsBackground) {
                // "Set page background": the picked PDF is not imported, the
                // user chooses one of its pages for the current page.
                pdfImportAsBackground = false
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
                        pendingPdfUri = it
                        pendingPdfPageCount = imported.pageCount
                        pendingPdfPageIndex = "1"
                        showPdfPagePicker = true
                    } finally {
                        imported.document.close()
                    }
                }
                return@let
            }
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
        val continuousReading = continuousMode
        if (continuousReading) {
            inkController.flushCurrentSuspend()
        } else {
            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                viewModel.saveCurrentPageBitmapDirect(bmp)
            }
        }
        val canvas = canvasViewRef
        if (canvas != null && canvas.layersDirty) {
            canvas.saveImageLayers(viewModel.fileStore, viewModel.noteId)
        }
        // The margin ink is saved on its own: it exists without any layer
        // change, so a layer save must not be the only way out.
        val inkCanvas = canvasViewRef
        if (inkCanvas != null && inkCanvas.hasBeyondPageInk()) {
            withContext(Dispatchers.IO) {
                inkCanvas.saveBeyondPageInkNow(viewModel.fileStore, viewModel.noteId)
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
                                // Non-destructive: the picked page becomes the
                                // page's background. It is stored in its own
                                // file, so the annotations of the page stay
                                // untouched (writing it into the page or the
                                // stroke file would have destroyed them).
                                val cur = viewModel.currentPageIndex.value
                                if (isContinuous) {
                                    inkController.flushCurrentSuspend()
                                }
                                viewModel.savePageBackgroundOverride(cur, bmp)
                                canvasViewRef?.let {
                                    it.backgroundBitmap = bmp
                                    it.invalidate()
                                }
                                savedPageGenerations[cur] = (savedPageGenerations[cur] ?: 0) + 1
                                android.widget.Toast.makeText(
                                    context,
                                    "Page background set",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
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

    // Auto-focus keyboard when OneNote live text box opens
    LaunchedEffect(isLiveTextActive) {
        if (isLiveTextActive) {
            delay(150)
            try {
                liveTextFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failure
            }
        }
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
                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                    val continuous = continuousMode
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
                                    val continuous = continuousMode
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

                            // Insert New Page (Works for infinite, A4, and PDF; inserts a blank sheet after current page)
                            IconButton(onClick = {
                                scope.launch {
                                    if (isContinuous) {
                                        inkController.flushCurrentSuspend()
                                        inkController.releaseSessionSuspend()
                                    }
                                    val currentBmp = if (isContinuous) null else canvasViewRef?.getBackingBitmap()
                                    viewModel.insertNewPage(
                                        atIndex = currentPageIndex + 1,
                                        isContinuous = isContinuous,
                                        currentBitmap = currentBmp
                                    ) { newBmp ->
                                        if (!isContinuous) {
                                            applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                        } else {
                                            savedPageGenerations.clear()
                                            scrollToContinuousPage(viewModel.currentPageIndex.value)
                                        }
                                        android.widget.Toast.makeText(
                                            context,
                                            "Inserted blank page ${viewModel.currentPageIndex.value + 1}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.NoteAdd,
                                    contentDescription = "Insert Page",
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
                    val continuousReading = continuousMode
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
                                        canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                        canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                /**
                 * Zoom / pan of the reader. Shared by the gesture detector on
                 * the backdrop and by the two-finger pinch inside a page, so
                 * both do the same thing.
                 */
                val handleReaderTransform: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Offset, Float) -> Unit =
                    { centroid, pan, zoom ->
                        val oldScale = continuousScale
                        // With the zoom locked the pinch only moves the page.
                        val newScale = if (uiState.zoomLocked) {
                            oldScale
                        } else {
                            (continuousScale * zoom)
                                .coerceIn(MIN_CONTINUOUS_SCALE, MAX_CONTINUOUS_SCALE)
                        }
                        val factor = newScale / oldScale
                        if (isHorizontal) {
                            val scrollDelta = (centroid.x * (factor - 1f)) - pan.x
                            continuousRowState.dispatchRawDelta(scrollDelta)
                            if (newScale > 1f) {
                                val maxPanY = (screenHeightPx * (newScale - 1f)) / 2f
                                continuousPanY = (centroid.y - (centroid.y - continuousPanY) * factor + pan.y).coerceIn(-maxPanY, maxPanY)
                            } else {
                                continuousPanY = 0f
                            }
                        } else {
                            val scrollDelta = (centroid.y * (factor - 1f)) - pan.y
                            continuousColState.dispatchRawDelta(scrollDelta)
                            if (newScale > 1f) {
                                val maxPanX = (screenWidthPx * (newScale - 1f)) / 2f
                                continuousPanX = (centroid.x - (centroid.x - continuousPanX) * factor + pan.x).coerceIn(-maxPanX, maxPanX)
                            } else {
                                continuousPanX = 0f
                            }
                        }
                        continuousScale = newScale
                    }

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

                // The note reopens on the page it was left on. The page index
                // is restored from the database after the reader was built, so
                // the list has to be told to scroll there once it arrives.
                var restoredPageScrolled by remember(viewModel.noteId) { mutableStateOf(false) }
                LaunchedEffect(currentPageIndex, pages.size) {
                    if (restoredPageScrolled || !isContinuous || pages.isEmpty()) {
                        return@LaunchedEffect
                    }
                    if (currentPageIndex > 0) {
                        restoredPageScrolled = true
                        scrollToContinuousPage(currentPageIndex)
                    }
                }

                // The visible item index comes from a snapshotFlow: reading the
                // list state straight in composition would recompose the whole
                // screen on every scroll frame.
                var continuousVisibleIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(isContinuous, isHorizontal) {
                    if (!isContinuous) return@LaunchedEffect
                    snapshotFlow {
                        if (isHorizontal) continuousRowState.firstVisibleItemIndex
                        else continuousColState.firstVisibleItemIndex
                    }.collect { continuousVisibleIndex = it }
                }

                // Sync current visible item index in continuous mode to viewModel
                val currentContinuousVisible = if (isHorizontal) {
                    continuousVisibleIndex
                } else if (uiState.doublePageMode) {
                    val firstPageInPair = continuousVisibleIndex * 2
                    if (currentPageIndex == firstPageInPair || currentPageIndex == firstPageInPair + 1) {
                        currentPageIndex
                    } else {
                        firstPageInPair
                    }
                } else {
                    continuousVisibleIndex
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
                            // The glass samples the page under the dock: it
                            // has to redraw when the page moves.
                            onViewportChanged = { glassViewportTick++ }
                            onImageSelectionChanged = {
                                selectedImageIndex = it
                            }
                            onLayersChanged = {
                                // One signal for every engine mutation: the
                                // panel and the reader never read a stale list.
                                layersTick++
                            }
                            onPdfSelectionChanged = { selection -> pdfSelection = selection }
                            onStylusDetected = {
                                viewModel.onStylusDetected()
                            }
                            onZoomOutToContinuous = {
                                if (pages.size > 1) {
                                    scope.launch {
                                        // The canvas is about to be hidden and
                                        // its page index dropped (-1), so its
                                        // strokes have to be written first —
                                        // otherwise the last stroke is lost.
                                        try {
                                            flushCurrentNote()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        viewModel.setPageTurningMode(0)
                                    }
                                }
                            }
                            onUndoRedoListener = { canUndo, canRedo ->
                                viewModel.updateUndoRedo(canUndo, canRedo)
                            }
                            onStrokeFinishedListener = {
                                getBackingBitmap()?.let { bmp ->
                                    viewModel.scheduleDebouncedSave(bmp)
                                }
                                // Ink written outside the paper lives in its
                                // own files and has to be written with the
                                // stroke, not only when a layer changes.
                                val canvas = canvasViewRef
                                if (canvas != null && canvas.hasBeyondPageInk()) {
                                    scope.launch(Dispatchers.IO) {
                                        canvas.saveBeyondPageInkNow(viewModel.fileStore, viewModel.noteId)
                                    }
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
                                        // The reader shows the pages, but the
                                        // hidden engine still names the page
                                        // the user is on for layer inserts.
                                        pageIndex = viewModel.currentPageIndex.value
                                        reloadLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                                        return@launch
                                    }
                                    val pageIdx = viewModel.currentPageIndex.value
                                    viewModel.setCanvasPageIndex(pageIdx)
                                    pageIndex = pageIdx
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
                                        loadBeyondPageInk(pageIdx)
                                    } else {
                                        val savedBitmap = viewModel.loadCurrentPageBitmap()
                                        setPageContent(savedBitmap)
                                        backgroundBitmap = null
                                        viewModel.setPageLoadedFromDisk(savedBitmap != null)
                                        loadBeyondPageInk(pageIdx)
                                    }
                                    reloadLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.pageType = note?.pageType ?: "infinite"
                        // The margin ink (the strokes outside the paper) is
                        // kept for every page type: on a PDF the notes in the
                        // margin are the most useful ones.
                        view.beyondPaper = !uiState.fixPreviewPage
                        view.currentTool = uiState.selectedTool
                        view.strokeColor = uiState.selectedColor.toArgb()
                        view.strokeWidthPx = uiState.strokeWidth
                        view.eraserWidthPx = uiState.eraserWidth
                        view.paperPattern = uiState.paperPattern
                        view.backgroundMode = uiState.backgroundMode
                        view.backgroundDensity = uiState.backgroundDensity
                        view.stylusOnly = uiState.stylusOnly
                        view.pageIndex = currentPageIndex
                        view.readingMode = uiState.readingMode
                        view.pressureSensitivity = uiState.pressureSensitivity
                        view.pressureSizeEnabled = uiState.pressureSizeEnabled
                        view.autoShapeEnabled = uiState.autoShapeEnabled
                        view.doublePage = uiState.doublePageMode
                        view.minZoom = uiState.minimalInfiniteZoom
                        view.horizontalResistance = uiState.horizontalResistance
                        view.autoCenter = uiState.autoCenter
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { canvasOriginInRoot = it.positionInRoot() }
                )

                // Continuous page overlay when continuous mode is active
                if (isContinuous) {
                    val pageWidthDp = with(density) { (screenWidthPx * continuousScale).toDp() }
                    val pageHeightDp = with(density) { (screenHeightPx * continuousScale).toDp() }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // An infinite canvas has no desk: the paper
                                // goes to the edge of the screen.
                                .background(if (isInfiniteNote) Color.White else Color(0xFFEEEEEE))
                                .pointerInput(Unit) {
                                    // A touch in the reader closes the open
                                    // menus, the same way a touch on the page
                                    // does in single-page mode.
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        dismissCanvasOverlays()
                                    }
                                }
                                .pointerInput(Unit) {
                                    // The pages below take the pointers, so
                                    // the gesture has to see them first
                                    // (PointerEventPass.Initial) - and it may
                                    // only take over when a SECOND finger
                                    // arrives, so a one finger scroll still
                                    // scrolls the list.
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        var twoFingers = false
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.changes.count { it.pressed } >= 2) {
                                                twoFingers = true
                                                break
                                            }
                                            if (event.changes.none { it.pressed }) break
                                        }
                                        if (!twoFingers) return@awaitEachGesture

                                        var previousCentroid: androidx.compose.ui.geometry.Offset? = null
                                        var previousDistance = 0f
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.size < 2) break
                                            var sumX = 0f
                                            var sumY = 0f
                                            pressed.forEach {
                                                sumX += it.position.x
                                                sumY += it.position.y
                                            }
                                            val centroid = androidx.compose.ui.geometry.Offset(
                                                sumX / pressed.size,
                                                sumY / pressed.size
                                            )
                                            val distance = (pressed[0].position - pressed[1].position).getDistance()
                                            var pan = androidx.compose.ui.geometry.Offset.Zero
                                            var zoom = 1f
                                            previousCentroid?.let { previous ->
                                                pan = centroid - previous
                                                if (previousDistance > 0f && distance > 0f) {
                                                    zoom = distance / previousDistance
                                                }
                                            }
                                            previousCentroid = centroid
                                            previousDistance = distance
                                            if (pan != androidx.compose.ui.geometry.Offset.Zero || zoom != 1f) {
                                                event.changes.forEach { it.consume() }
                                                handleReaderTransform(centroid, pan, zoom)
                                            }
                                        }
                                    }
                                }
                        ) {
                            if (isHorizontal) {
                                androidx.compose.foundation.lazy.LazyRow(
                                    state = continuousRowState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !inkController.isDrawing,
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                                ) {
                                    items(items = pages, key = { it.id }) { page ->
                                        val idx = pages.indexOf(page)
                                        Box(
                                            modifier = Modifier
                                                .height(pageHeightDp)
                                                // An infinite page has no edge:
                                                // its box is the visible canvas,
                                                // not a rectangle with the
                                                // aspect ratio of the saved
                                                // bitmap.
                                                .then(if (isInfiniteNote) Modifier.width(pageWidthDp) else Modifier)
                                                .offset { IntOffset(0, continuousPanY.roundToInt()) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ContinuousPageView(
                                                pageIndex = idx,
                                                page = page,
                                                noteId = viewModel.noteId,
                                                pdfDocCache = pdfDocCache,
                                                repository = viewModel.repository,
                                                readerLayers = readerLayers,
                                                onPageMapping = { continuousPageMap.map = it },
                                                onImageMapping = { continuousPageMap.imageMap = it },
                                                zoom = continuousZoomBucket,
                                                ink = inkController,
                                                inkSettings = inkSettings,
                                                backgroundMode = uiState.backgroundMode,
                                                backgroundDensity = uiState.backgroundDensity,
                                                savedGeneration = savedPageGenerations[idx] ?: 0,
                                                isCurrentPage = idx == currentPageIndex,
                                                isInfinite = isInfiniteNote,
                                                pageType = note?.pageType ?: "infinite",
                                                modifier = androidx.compose.ui.Modifier.then(
                                                    if (isInfiniteNote) {
                                                        Modifier.fillMaxSize()
                                                    } else {
                                                        Modifier.fillMaxHeight()
                                                    }
                                                ),
                                                onPageBitmap = { idx, bmp ->
                                                    if (idx == currentPageIndex) {
                                                        glassReaderBitmap = bmp
                                                        glassReaderBitmapPage = idx
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setCurrentPageIndex(idx)
                                                    dismissCanvasOverlays()
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (uiState.doublePageMode) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    state = continuousColState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !inkController.isDrawing,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val pairs = (0 until pages.size).chunked(2)
                                    items(pairs.size) { pairIdx ->
                                        val pair = pairs[pairIdx]
                                        Row(
                                            modifier = Modifier
                                                .width(pageWidthDp)
                                                // An infinite page has no edge:
                                                // its box is the visible canvas.
                                                .then(if (isInfiniteNote) Modifier.height(pageHeightDp) else Modifier)
                                                .offset { IntOffset(continuousPanX.roundToInt(), 0) },
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            for (idx in pair) {
                                                val page = pages[idx]
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .then(if (isInfiniteNote) Modifier.fillMaxHeight() else Modifier)
                                                ) {
                                                    ContinuousPageView(
                                                        pageIndex = idx,
                                                        page = page,
                                                        noteId = viewModel.noteId,
                                                        pdfDocCache = pdfDocCache,
                                                        repository = viewModel.repository,
                                                        readerLayers = readerLayers,
                                                        onPageMapping = { continuousPageMap.map = it },
                                                        onImageMapping = { continuousPageMap.imageMap = it },
                                                        zoom = continuousZoomBucket,
                                                        ink = inkController,
                                                        inkSettings = inkSettings,
                                                        backgroundMode = uiState.backgroundMode,
                                                        backgroundDensity = uiState.backgroundDensity,
                                                        savedGeneration = savedPageGenerations[idx] ?: 0,
                                                        isCurrentPage = idx == currentPageIndex,
                                                        isInfinite = isInfiniteNote,
                                                        pageType = note?.pageType ?: "infinite",
                                                        modifier = androidx.compose.ui.Modifier.then(
                                                            if (isInfiniteNote) {
                                                                Modifier.fillMaxSize()
                                                            } else {
                                                                Modifier.fillMaxWidth()
                                                            }
                                                        ),
                                                        onPageBitmap = { idx, bmp ->
                                                            if (idx == currentPageIndex) {
                                                                glassReaderBitmap = bmp
                                                                glassReaderBitmapPage = idx
                                                            }
                                                        },
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
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(items = pages, key = { it.id }) { page ->
                                        val idx = pages.indexOf(page)
                                        Box(
                                            modifier = Modifier
                                                .width(pageWidthDp)
                                                // An infinite page has no edge:
                                                // its box is the visible canvas,
                                                // not a rectangle with the
                                                // aspect ratio of the saved
                                                // bitmap.
                                                .then(if (isInfiniteNote) Modifier.height(pageHeightDp) else Modifier)
                                                .offset { IntOffset(continuousPanX.roundToInt(), 0) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ContinuousPageView(
                                                pageIndex = idx,
                                                page = page,
                                                noteId = viewModel.noteId,
                                                pdfDocCache = pdfDocCache,
                                                repository = viewModel.repository,
                                                readerLayers = readerLayers,
                                                onPageMapping = { continuousPageMap.map = it },
                                                onImageMapping = { continuousPageMap.imageMap = it },
                                                zoom = continuousZoomBucket,
                                                ink = inkController,
                                                inkSettings = inkSettings,
                                                backgroundMode = uiState.backgroundMode,
                                                backgroundDensity = uiState.backgroundDensity,
                                                savedGeneration = savedPageGenerations[idx] ?: 0,
                                                isCurrentPage = idx == currentPageIndex,
                                                isInfinite = isInfiniteNote,
                                                pageType = note?.pageType ?: "infinite",
                                                modifier = androidx.compose.ui.Modifier.then(
                                                    if (isInfiniteNote) {
                                                        Modifier.fillMaxSize()
                                                    } else {
                                                        Modifier.fillMaxWidth()
                                                    }
                                                ),
                                                onStylusDetected = { viewModel.onStylusDetected() },
                                        onPinch = { centroid, pan, zoom -> handleReaderTransform(centroid, pan, zoom) },
                                                onPageBitmap = { idx, bmp ->
                                                    if (idx == currentPageIndex) {
                                                        glassReaderBitmap = bmp
                                                        glassReaderBitmapPage = idx
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setCurrentPageIndex(idx)
                                                    dismissCanvasOverlays()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Continuous Mode Floating HUD: Stylus/Finger Mode toggle and Zoom Reset pill
                        // Placed outside graphicsLayer so it stays fixed at TopCenter at 1x scale
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                // The horizontal dock sits at the top as
                                // well: the pills move below it.
                                .padding(top = if (uiState.dockHorizontal) 74.dp else 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.stylusOnly) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 12.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = Color(0x26000000),
                                            spotColor = Color(0x40000000)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .onGloballyPositioned {
                                            glassStylusOrigin.x = it.positionInRoot().x
                                            glassStylusOrigin.y = it.positionInRoot().y
                                        }
                                        .clickable {
                                            viewModel.toggleStylusOnly()
                                            android.widget.Toast.makeText(
                                                context,
                                                "Finger Draw ON: 2 fingers to scroll",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .liquidGlassBackdrop(
                                                glassShader, 16.dp, LiquidGlassStyle.Dark
                                            ) { glassBackdropAt(glassStylusOrigin) }
                                            .liquidGlassRim(16.dp, RoundedCornerShape(16.dp))
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
                            }

                            if (uiState.zoomLocked || kotlin.math.abs(continuousScale - 1f) > 0.05f) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 12.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = Color(0x26000000),
                                            spotColor = Color(0x40000000)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .onGloballyPositioned {
                                            glassZoomOrigin.x = it.positionInRoot().x
                                            glassZoomOrigin.y = it.positionInRoot().y
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .liquidGlassBackdrop(
                                                glassShader, 16.dp, LiquidGlassStyle.Dark
                                            ) { glassBackdropAt(glassZoomOrigin) }
                                            .liquidGlassRim(16.dp, RoundedCornerShape(16.dp))
                                    ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(start = 2.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        // Lock the pinch: two fingers then only
                                        // move the page around.
                                        IconButton(
                                            onClick = { viewModel.setZoomLocked(!uiState.zoomLocked) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.zoomLocked) {
                                                    Icons.Default.Lock
                                                } else {
                                                    Icons.Outlined.LockOpen
                                                },
                                                contentDescription = if (uiState.zoomLocked) {
                                                    "Unlock pinch zoom"
                                                } else {
                                                    "Lock pinch zoom"
                                                },
                                                tint = if (uiState.zoomLocked) OrangePrimary else Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = "${(continuousScale * 100).roundToInt()}% · Reset",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable {
                                                continuousScale = 1f
                                                continuousPanX = 0f
                                                continuousPanY = 0f
                                            }
                                        )
                                    }
                                    }
                                }
                            }
                        }
                    }
                }

                // Image layer controls
                if (selectedImageIndex >= 0) {
                    val selectedLayer = if (isContinuous) {
                        readerLayers.layers(currentPageIndex).getOrNull(selectedImageIndex)
                    } else {
                        canvasViewRef?.imageLayers?.getOrNull(selectedImageIndex)
                    }
                    var layerOpacity by remember(selectedImageIndex, currentPageIndex) {
                        mutableFloatStateOf(selectedLayer?.opacity ?: 1f)
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
                                    val view = canvasViewRef
                                    view?.setLayerOpacity(selectedImageIndex, v)
                                    // Live feedback in the reader: the engine
                                    // mutates the same layer objects.
                                    if (isContinuous && view != null &&
                                        view.imageLayersPageIndex == currentPageIndex
                                    ) {
                                        readerLayers.put(currentPageIndex, view.imageLayers.toList())
                                    }
                                },
                                onValueChangeFinished = {
                                    scope.launch {
                                        val view = canvasViewRef ?: return@launch
                                        if (isContinuous &&
                                            view.imageLayersPageIndex != currentPageIndex
                                        ) {
                                            return@launch
                                        }
                                        try {
                                            view.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                            if (isContinuous) {
                                                readerLayers.put(
                                                    currentPageIndex,
                                                    view.imageLayers.toList()
                                                )
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    editCurrentLayers(false) { view ->
                                        view.deleteSelectedImageLayer()
                                    }
                                    selectedImageIndex = -1
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete layer", tint = Color.Red)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    // Cropping needs the layer bitmap, which
                                    // the hydrated engine already holds.
                                    editCurrentLayers(false) { view ->
                                        view.cropSelectedImageLayer()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.CropSquare, contentDescription = "Crop layer")
                            }
                        }
                    }
                }

                // Layers panel
                if (showLayersPanel) {
                    val _tick = layersTick
                    // The reader draws its own decoded copy of the page's
                    // layers; single-page mode reads the engine directly.
                    val layers = if (isContinuous) {
                        readerLayers.layers(currentPageIndex)
                    } else {
                        canvasViewRef?.imageLayers ?: emptyList()
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.98f),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color(0xFFE5E5E5)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // The floating dock is drawn over the canvas: the
                            // panel has to sit above it, or the horizontal
                            // dock hides its header and its buttons.
                            .zIndex(3f)
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
                                    modifier = Modifier.size(36.dp)
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
                                    text = "No floating layers on this page.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                                layers.forEachIndexed { idx, layer ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable {
                                                // Picking a row makes it the
                                                // layer the opacity/crop bar
                                                // edits, in both modes.
                                                selectedImageIndex = idx
                                                if (isContinuous) {
                                                    scope.launch {
                                                        prepareLayerEngine(
                                                            viewModel.currentPageIndex.value,
                                                            withPagePixels = false
                                                        )
                                                        canvasViewRef?.selectedImageIndex = idx
                                                    }
                                                } else {
                                                    canvasViewRef?.selectedImageIndex = idx
                                                }
                                            }
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    editCurrentLayers(false) { view ->
                                                        view.setLayerVisible(idx, !layer.visible)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "visibility",
                                                tint = Color.DarkGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = buildString {
                                                append("Layer ${idx + 1}")
                                                // The layer that owns the ink: strokes
                                                // written while it was current hide
                                                // with it.
                                                if (idx == selectedImageIndex) append(" · current")
                                                if (layer.ink.isNotEmpty()) {
                                                    append(" · ${layer.ink.size} mark")
                                                    if (layer.ink.size > 1) append("s")
                                                }
                                            },
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (idx > 0) {
                                                    scope.launch {
                                                        editCurrentLayers(false) { view ->
                                                            view.moveImageLayer(idx, idx - 1)
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "move up", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (idx < layers.size - 1) {
                                                    scope.launch {
                                                        editCurrentLayers(false) { view ->
                                                            view.moveImageLayer(idx, idx + 1)
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = idx < layers.size - 1,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "move down", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    editCurrentLayers(false) { view ->
                                                        view.deleteImageLayer(idx)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
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
                                        // Keep the panel open: the new row
                                        // must appear immediately.
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("+ Image", fontSize = 11.sp, color = OrangePrimary)
                                }
                                OutlinedButton(
                                    onClick = {
                                        liveTextContent = ""
                                        liveTextOffsetX = 0f
                                        liveTextOffsetY = 0f
                                        liveTextColor = if (uiState.selectedColor == Color.White) Color.Black else uiState.selectedColor
                                        isLiveTextActive = true
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
                                        scope.launch {
                                            val page = viewModel.currentPageIndex.value
                                            if (isContinuous) {
                                                // The reader owns the page image,
                                                // so the merge has to write the
                                                // flattened pixels itself - it
                                                // must never just delete the
                                                // layer files.
                                                inkController.flushCurrentSuspend()
                                                val view = canvasViewRef
                                                if (view != null) {
                                                    view.pageIndex = page
                                                    val pageBitmap = withContext(Dispatchers.IO) {
                                                        viewModel.repository.loadPageBitmap(viewModel.noteId, page)
                                                    }
                                                    val base = pageBitmap ?: withContext(Dispatchers.IO) {
                                                        personal.notes.touch.util.BitmapUtils
                                                            .createSafeBitmap(2160, 3056)
                                                    }
                                                    view.setPageContent(base)
                                                    // The in-memory margin crops may
                                                    // belong to another page; the
                                                    // save below files whatever is
                                                    // in memory under this page,
                                                    // so install this page's own.
                                                    val ownInk = withContext(Dispatchers.IO) {
                                                        view.readBeyondPageInk(
                                                            viewModel.fileStore,
                                                            viewModel.noteId,
                                                            page
                                                        )
                                                    }
                                                    view.applyBeyondPageInk(
                                                        ownInk,
                                                        forPageIndex = page
                                                    )
                                                    view.reloadLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                                                    view.mergeAllLayersToBase()
                                                    val merged = view.getBackingBitmap()
                                                    if (merged != null) {
                                                        withContext(Dispatchers.IO) {
                                                            viewModel.repository.savePageBitmap(
                                                                viewModel.noteId, page, merged
                                                            )
                                                        }
                                                    }
                                                    view.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                    readerLayers.put(
                                                        page,
                                                        emptyList(),
                                                        merged?.width ?: 0,
                                                        merged?.height ?: 0
                                                    )
                                                    engineLayerPage = page
                                                    // The ink session's bitmap
                                                    // predates the merge: drop
                                                    // it and let the reader
                                                    // reload the page.
                                                    if (inkController.getSession(page) != null) {
                                                        inkController.releaseSessionSuspend()
                                                    }
                                                    savedPageGenerations[page] =
                                                        (savedPageGenerations[page] ?: 0) + 1
                                                }
                                            } else {
                                                canvasViewRef?.mergeAllLayersToBase()
                                                try {
                                                    canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                        viewModel.saveCurrentPageBitmapDirect(bmp)
                                                    }
                                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
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
                // ONENOTE-STYLE LIVE PREVIEW TEXT BOX
                // ==========================================
                if (isLiveTextActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.95f),
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.5.dp, OrangePrimary),
                            modifier = Modifier
                                .offset { IntOffset(liveTextOffsetX.roundToInt(), liveTextOffsetY.roundToInt()) }
                                .widthIn(min = 320.dp, max = 560.dp)
                                .padding(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // OneNote-style Top Bar: Drag handle + Formatting controls + Done/Cancel
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Drag Handle (Grabbable with finger/stylus to position live text on page)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    liveTextOffsetX += dragAmount.x
                                                    liveTextOffsetY += dragAmount.y
                                                }
                                            }
                                            .padding(end = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.Gray.copy(alpha = 0.5f))
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Text Box",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF444444)
                                        )
                                    }

                                    // Formatting Toolbar
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Font Size Controls (A- / A+)
                                        IconButton(
                                            onClick = { liveTextSizeSp = (liveTextSizeSp - 4f).coerceAtLeast(12f) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("A-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF555555))
                                        }
                                        Text(
                                            "${liveTextSizeSp.roundToInt()}sp",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 1.dp)
                                        )
                                        IconButton(
                                            onClick = { liveTextSizeSp = (liveTextSizeSp + 4f).coerceAtMost(72f) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("A+", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF555555))
                                        }

                                        Spacer(Modifier.width(2.dp))

                                        // Color swatches (Black, Orange, Red, Blue)
                                        val textSwatches = listOf(Color.Black, OrangePrimary, Color(0xFFD32F2F), Color(0xFF1565C0))
                                        textSwatches.forEach { c ->
                                            val isSelected = liveTextColor == c
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .border(
                                                        if (isSelected) 2.dp else 0.5.dp,
                                                        if (isSelected) OrangePrimary else Color.LightGray,
                                                        CircleShape
                                                    )
                                                    .clickable { liveTextColor = c }
                                            )
                                        }

                                        Spacer(Modifier.width(4.dp))

                                        // Done button (Commit text layer to canvas)
                                        IconButton(
                                            onClick = {
                                                val text = liveTextContent.trim()
                                                if (text.isNotEmpty()) {
                                                    // The layer is anchored where the
                                                    // user typed: the text field's
                                                    // top-left in screen coordinates,
                                                    // converted to page pixels by the
                                                    // mapping of the page actually on
                                                    // screen (the reader's fit in
                                                    // continuous mode, the canvas'
                                                    // own matrix in single-page).
                                                    val anchor = screenPointToPage(liveTextAnchorInRoot)
                                                    scope.launch {
                                                        if (anchor != null) {
                                                            val inserted = insertLayerInCurrentNote {
                                                                it.addTextBlock(
                                                                    text = text,
                                                                    color = liveTextColor.toArgb(),
                                                                    fontSizeSp = liveTextSizeSp,
                                                                    // The text bitmap keeps a 16 px
                                                                    // left / 12 px top inset; anchor
                                                                    // its glyphs, not its edge.
                                                                    posX = anchor.x - 16f,
                                                                    posY = anchor.y - 12f
                                                                )
                                                            }
                                                            if (!inserted) {
                                                                android.widget.Toast.makeText(
                                                                    context,
                                                                    "Could not insert text",
                                                                    android.widget.Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        } else {
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "Could not place text on this page",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                }
                                                isLiveTextActive = false
                                                liveTextContent = ""
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFF4CAF50), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Done",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Cancel button
                                        IconButton(
                                            onClick = {
                                                isLiveTextActive = false
                                                liveTextContent = ""
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFFEEEEEE), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = Color.DarkGray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Divider(
                                    color = Color(0xFFEEEEEE),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )

                                // Live typing field directly on canvas
                                BasicTextField(
                                    value = liveTextContent,
                                    onValueChange = { liveTextContent = it },
                                    textStyle = TextStyle(
                                        color = liveTextColor,
                                        fontSize = liveTextSizeSp.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = (liveTextSizeSp * 1.35f).sp
                                    ),
                                    cursorBrush = SolidColor(OrangePrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 72.dp)
                                        .focusRequester(liveTextFocusRequester),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                .border(BorderStroke(1.dp, Color(0xFFE0E0E0)), RoundedCornerShape(6.dp))
                                                .padding(8.dp)
                                                // Where typing actually starts: the
                                                // committed layer is anchored here,
                                                // not at the card's outer box.
                                                .onGloballyPositioned { liveTextAnchorInRoot = it.positionInRoot() }
                                        ) {
                                            if (liveTextContent.isEmpty()) {
                                                Text(
                                                    "Type text here...",
                                                    color = Color.LightGray,
                                                    fontSize = liveTextSizeSp.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
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
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color(0x14000000),
                                spotColor = Color(0x28000000)
                            )
                            .background(GlassBodyBrush, RoundedCornerShape(24.dp))
                            .border(1.dp, GlassEdgeBrush, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(3.dp)
                                .background(GlassLensBrush, RoundedCornerShape(21.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
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
                    if (uiState.dockHorizontal) {
                        // Horizontal Dock (Top under bar) with luminous liquid glass styling
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset { IntOffset(pencaseOffsetX.roundToInt(), pencaseOffsetY.roundToInt()) }
                                .padding(top = 10.dp)
                                .shadow(
                                    elevation = 14.dp,
                                    shape = RoundedCornerShape(26.dp),
                                    ambientColor = Color(0x14000000),
                                    spotColor = Color(0x26000000)
                                )
                                .onGloballyPositioned {
                                    glassDockOriginHolder.x = it.positionInRoot().x
                                    glassDockOriginHolder.y = it.positionInRoot().y
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    // The page under the dock, bent by the
                                    // glass: drawn first, so the tint and the
                                    // icons stay on top of it.
                                    .liquidGlassBackdrop(glassShader, 26.dp) {
                                        glassBackdropAt(glassDockOriginHolder)
                                    }
                                    .liquidGlassRim(26.dp, RoundedCornerShape(26.dp))
                                    .liquidStylusSpotlight()
                            ) {
                            Row(
                                modifier = Modifier
                                    // The lens is inset from the shell, so the
                                    // rim of the dock stays transparent.
                                    .padding(3.dp)
                                    .height(46.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Drag & Rotate Handle Pill (Tap to switch orientation, Tap & Hold to drag)
                                Box(
                                    modifier = Modifier
                                        .size(width = 20.dp, height = 34.dp)
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var isDrag = false
                                                val touchSlop = viewConfiguration.touchSlop
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (change.changedToUp()) {
                                                        if (!isDrag) {
                                                            viewModel.toggleDockOrientation()
                                                            pencaseOffsetX = 0f
                                                            pencaseOffsetY = 0f
                                                        }
                                                        change.consume()
                                                        break
                                                    }
                                                    val deltaX = change.position.x - down.position.x
                                                    val deltaY = change.position.y - down.position.y
                                                    if (!isDrag && kotlin.math.hypot(deltaX, deltaY) > touchSlop) {
                                                        isDrag = true
                                                    }
                                                    if (isDrag) {
                                                        val dragDelta = change.position - change.previousPosition
                                                        change.consume()
                                                        val minX = -screenWidthPx / 2f + 80f
                                                        val maxX = screenWidthPx / 2f - 80f
                                                        val minY = -10f
                                                        val maxY = screenHeightPx - 150f
                                                        pencaseOffsetX = (pencaseOffsetX + dragDelta.x).coerceIn(minX, maxX)
                                                        pencaseOffsetY = (pencaseOffsetY + dragDelta.y).coerceIn(minY, maxY)
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(20.dp)
                                            .width(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0x38000000))
                                    )
                                }


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

                                // Text Tool [T] (OneNote-style live text preview)
                                PencaseToolIcon(
                                    icon = Icons.Default.TextFields,
                                    label = "Text",
                                    isSelected = isLiveTextActive || pdfTextMode,
                                    onClick = {
                                        liveTextContent = ""
                                        liveTextOffsetX = 0f
                                        liveTextOffsetY = 0f
                                        liveTextColor = if (uiState.selectedColor == Color.White) Color.Black else uiState.selectedColor
                                        isLiveTextActive = true
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

                                Box(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .width(1.dp)
                                        .background(Color(0x1F000000))
                                )

                                // 3 Stroke Width Presets (Horizontal bar line thickness)
                                // Short tap picks the preset, long press opens the slider popup.
                                val widthPresets =
                                    if (uiState.selectedTool == CanvasTool.ERASER) TouchNotesEraserPresets
                                    else TouchNotesWidthPresets
                                val activeWidth = uiState.activeStrokeWidth()
                                val isEraserTool = uiState.selectedTool == CanvasTool.ERASER
                                widthPresets.forEachIndexed { wIdx, (widthPx, _) ->
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
                                            .background(if (isSelected) OrangePrimary.copy(alpha = 0.14f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) OrangePrimary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .combinedClickable(
                                                onClick = { viewModel.setStrokeWidth(widthPx) },
                                                onLongClick = {
                                                    // Only the chip that was
                                                    // held down opens the
                                                    // slider: the popup used
                                                    // to be built once per
                                                    // chip, so three of them
                                                    // opened at once.
                                                    widthPopupChipHoriz = wIdx
                                                    showWidthPopupHoriz = true
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .height(barHeight)
                                                .clip(RoundedCornerShape(barHeight / 2))
                                                .background(if (isSelected) OrangePrimary else Color(0xFF374151))
                                        )
                                        WidthPickerPopup(
                                            expanded = showWidthPopupHoriz && widthPopupChipHoriz == wIdx,
                                            onDismiss = {
                                                showWidthPopupHoriz = false
                                                widthPopupChipHoriz = -1
                                            },
                                            presets = widthPresets,
                                            activeWidth = activeWidth,
                                            defaultWidth = uiState.defaultWidthForTool(),
                                            isEraser = isEraserTool,
                                            preferRight = false,
                                            glassOrigin = glassWidthPopupHoriz,
                                            rootOffset = rootViewOffset,
                                            glassShader = glassShader,
                                            glassBackdropAt = glassBackdropAt,
                                            onWidthChange = { viewModel.setStrokeWidth(it) }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .width(1.dp)
                                        .background(Color(0x1F000000))
                                )

                                // 3 Rounded-Square Color Swatches with checkmark
                                // Short tap picks the colour, long press opens the full palette.
                                TouchNotesDockColors.forEachIndexed { cIdx, c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(c)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.8.dp,
                                                color = if (isSelected) OrangePrimary else Color(0x28000000),
                                                shape = RoundedCornerShape(7.dp)
                                            )
                                            .combinedClickable(
                                                onClick = { viewModel.selectColor(c) },
                                                onLongClick = {
                                                    // Only the swatch that was
                                                    // held down opens the
                                                    // palette: the popup used to
                                                    // be built once per swatch,
                                                    // so three of them opened
                                                    // on top of each other.
                                                    colorPopupChipHoriz = cIdx
                                                    showColorPopupHoriz = true
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (c == Color.White) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        ColorPalettePopup(
                                            expanded = showColorPopupHoriz && colorPopupChipHoriz == cIdx,
                                            onDismiss = {
                                                showColorPopupHoriz = false
                                                colorPopupChipHoriz = -1
                                            },
                                            selectedColor = uiState.selectedColor,
                                            preferRight = false,
                                            onColorSelected = { viewModel.selectColor(it) },
                                            onCustomColor = { showCustomColorDialog = true }
                                        )
                                    }
                                }

                                // Color Picker (+) Button
                                val isCustomColorActive = uiState.selectedColor !in TouchNotesDockColors
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isCustomColorActive) uiState.selectedColor else OrangePrimary)
                                        .border(
                                            width = if (isCustomColorActive) 2.dp else 0.8.dp,
                                            color = if (isCustomColorActive) OrangePrimary else Color(0x20000000),
                                            shape = CircleShape
                                        )
                                        .clickable { showCustomColorDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Custom Color",
                                        tint = if (isCustomColorActive && uiState.selectedColor == Color.White) Color.Black else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            }
                        }
                    } else {
                        // Vertical Dock (Docked on Left) with luminous liquid glass styling
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset { IntOffset(pencaseOffsetX.roundToInt(), pencaseOffsetY.roundToInt()) }
                                .padding(start = 12.dp)
                                .shadow(
                                    elevation = 14.dp,
                                    shape = RoundedCornerShape(26.dp),
                                    ambientColor = Color(0x14000000),
                                    spotColor = Color(0x26000000)
                                )
                                .onGloballyPositioned {
                                    glassDockOriginHolder.x = it.positionInRoot().x
                                    glassDockOriginHolder.y = it.positionInRoot().y
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .liquidGlassBackdrop(glassShader, 26.dp) {
                                        glassBackdropAt(glassDockOriginHolder)
                                    }
                                    .liquidGlassRim(26.dp, RoundedCornerShape(26.dp))
                                    .liquidStylusSpotlight()
                            ) {
                            Column(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .width(46.dp)
                                    .padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Drag & Rotate Handle Pill (Tap to toggle orientation, Tap & Hold to drag)
                                Box(
                                    modifier = Modifier
                                        .size(width = 34.dp, height = 20.dp)
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var isDrag = false
                                                val touchSlop = viewConfiguration.touchSlop
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (change.changedToUp()) {
                                                        if (!isDrag) {
                                                            viewModel.toggleDockOrientation()
                                                            pencaseOffsetX = 0f
                                                            pencaseOffsetY = 0f
                                                        }
                                                        change.consume()
                                                        break
                                                    }
                                                    val deltaX = change.position.x - down.position.x
                                                    val deltaY = change.position.y - down.position.y
                                                    if (!isDrag && kotlin.math.hypot(deltaX, deltaY) > touchSlop) {
                                                        isDrag = true
                                                    }
                                                    if (isDrag) {
                                                        val dragDelta = change.position - change.previousPosition
                                                        change.consume()
                                                        val minX = 0f
                                                        // Keep the whole dock on screen, so it
                                                        // can never be dragged out of reach.
                                                        val maxX = (screenWidthPx - 220f).coerceAtLeast(0f)
                                                        val minY = -screenHeightPx / 2f + 80f
                                                        val maxY = screenHeightPx / 2f - 80f
                                                        pencaseOffsetX = (pencaseOffsetX + dragDelta.x).coerceIn(minX, maxX)
                                                        pencaseOffsetY = (pencaseOffsetY + dragDelta.y).coerceIn(minY, maxY)
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0x38000000))
                                    )
                                }


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

                                // Text Tool [T] (OneNote-style live text preview)
                                PencaseToolIcon(
                                    icon = Icons.Default.TextFields,
                                    label = "Text",
                                    isSelected = isLiveTextActive || pdfTextMode,
                                    onClick = {
                                        liveTextContent = ""
                                        liveTextOffsetX = 0f
                                        liveTextOffsetY = 0f
                                        liveTextColor = if (uiState.selectedColor == Color.White) Color.Black else uiState.selectedColor
                                        isLiveTextActive = true
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

                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(1.dp)
                                        .background(Color(0x1F000000))
                                )

                                // 3 Stroke Width Presets (vertical line thickness bars)
                                // Short tap picks the preset, long press opens the slider popup.
                                val verticalPresets =
                                    if (uiState.selectedTool == CanvasTool.ERASER) TouchNotesEraserPresets
                                    else TouchNotesWidthPresets
                                val verticalActive = uiState.activeStrokeWidth()
                                val isEraserToolVert = uiState.selectedTool == CanvasTool.ERASER
                                verticalPresets.take(3).forEachIndexed { vIdx, (widthPx, _) ->
                                    val isSelected = verticalActive == widthPx
                                    val barH = when (vIdx) {
                                        0 -> 2.dp
                                        1 -> 4.dp
                                        else -> 6.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrangePrimary.copy(alpha = 0.14f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) OrangePrimary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .combinedClickable(
                                                onClick = { viewModel.setStrokeWidth(widthPx) },
                                                onLongClick = {
                                                    widthPopupChipVert = vIdx
                                                    showWidthPopupVert = true
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .height(barH)
                                                .clip(RoundedCornerShape(barH / 2))
                                                .background(if (isSelected) OrangePrimary else Color(0xFF374151))
                                        )
                                        WidthPickerPopup(
                                            expanded = showWidthPopupVert && widthPopupChipVert == vIdx,
                                            onDismiss = {
                                                showWidthPopupVert = false
                                                widthPopupChipVert = -1
                                            },
                                            presets = verticalPresets,
                                            activeWidth = verticalActive,
                                            defaultWidth = uiState.defaultWidthForTool(),
                                            isEraser = isEraserToolVert,
                                            preferRight = true,
                                            glassOrigin = glassWidthPopupVert,
                                            rootOffset = rootViewOffset,
                                            glassShader = glassShader,
                                            glassBackdropAt = glassBackdropAt,
                                            onWidthChange = { viewModel.setStrokeWidth(it) }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(1.dp)
                                        .background(Color(0x1F000000))
                                )

                                // 3 Rounded-Square Color Swatches with checkmark
                                // Short tap picks the colour, long press opens the full palette.
                                TouchNotesDockColors.forEachIndexed { cIdx, c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(c)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.8.dp,
                                                color = if (isSelected) OrangePrimary else Color(0x28000000),
                                                shape = RoundedCornerShape(7.dp)
                                            )
                                            .combinedClickable(
                                                onClick = { viewModel.selectColor(c) },
                                                onLongClick = {
                                                    // Only the swatch that was
                                                    // held down opens the
                                                    // palette: the popup used to
                                                    // be built once per swatch,
                                                    // so three of them opened
                                                    // on top of each other.
                                                    colorPopupChipVert = cIdx
                                                    showColorPopupVert = true
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (c == Color.White) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        ColorPalettePopup(
                                            expanded = showColorPopupVert && colorPopupChipVert == cIdx,
                                            onDismiss = {
                                                showColorPopupVert = false
                                                colorPopupChipVert = -1
                                            },
                                            selectedColor = uiState.selectedColor,
                                            preferRight = true,
                                            onColorSelected = { viewModel.selectColor(it) },
                                            onCustomColor = { showCustomColorDialog = true }
                                        )
                                    }
                                }

                                // Color Picker (+) Button
                                val isCustomColorActiveVert = uiState.selectedColor !in TouchNotesDockColors
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isCustomColorActiveVert) uiState.selectedColor else OrangePrimary)
                                        .border(
                                            width = if (isCustomColorActiveVert) 2.dp else 0.8.dp,
                                            color = if (isCustomColorActiveVert) OrangePrimary else Color(0x20000000),
                                            shape = CircleShape
                                        )
                                        .clickable { showCustomColorDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Custom Color",
                                        tint = if (isCustomColorActiveVert && uiState.selectedColor == Color.White) Color.Black else Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
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
                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                            onClick = {
                                // The reader opens the drawing layers of the
                                // page (the layer panel); the single page
                                // mode opens the floating images.
                                if (isContinuous) {
                                    showLayerPanel = !showLayerPanel
                                    layerActionIndex = -1
                                } else {
                                    showLayersPanel = !showLayersPanel
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Layers",
                                tint = if (showLayerPanel || showLayersPanel) OrangePrimary else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 4b. THE LAYER PANEL OF THE PAGE
                // ==========================================
                // The floating card of the original: the layers of the
                // current page, the eye that masks a layer and the menu
                // with the actions of a layer.
                if (showLayerPanel && isContinuous) {
                    val panelPage = pages.getOrNull(currentPageIndex)
                    // The layer stack of a page lives in its ink session, so
                    // the panel needs one.
                    LaunchedEffect(currentPageIndex, panelPage?.id) {
                        if (panelPage != null) {
                            inkController.ensureSessionForPage(panelPage, currentPageIndex)
                        }
                    }
                    val layerTick = inkController.layersRevision
                    val layerStack = inkController.layerStack(currentPageIndex)
                    val layerThumbs by produceState<Map<Int, android.graphics.Bitmap?>>(
                        emptyMap(), currentPageIndex, layerTick
                    ) {
                        value = withContext(Dispatchers.IO) {
                            val map = LinkedHashMap<Int, android.graphics.Bitmap?>()
                            layerStack.layers.forEach { layer ->
                                map[layer.index] = inkController.layerThumbnail(currentPageIndex, layer)
                            }
                            map
                        }
                    }
                    // The panel is dragged by its handle, but it never leaves
                    // the screen.
                    val maxDragX = with(density) { (screenWidthPx - 260.dp.toPx()).coerceAtLeast(0f) }
                    val maxDragY = with(density) { (screenHeightPx - 360.dp.toPx()).coerceAtLeast(0f) }
                    LayerPanel(
                        stack = layerStack,
                        thumbnails = layerThumbs,
                        actionIndex = layerActionIndex,
                        onSelect = { index -> inkController.selectLayer(currentPageIndex, index) },
                        onToggleVisible = { index ->
                            val layer = layerStack.layerAt(index)
                            if (layer != null) {
                                inkController.setLayerVisible(currentPageIndex, index, !layer.visible)
                            }
                        },
                        onMore = { index -> layerActionIndex = index },
                        onAdd = { inkController.addLayer(currentPageIndex) },
                        onMerge = { index ->
                            inkController.mergeLayer(currentPageIndex, index)
                            layerActionIndex = -1
                        },
                        onDelete = { index ->
                            inkController.deleteLayer(currentPageIndex, index)
                            layerActionIndex = -1
                        },
                        onCloseActions = { layerActionIndex = -1 },
                        onClose = {
                            showLayerPanel = false
                            layerActionIndex = -1
                        },
                        onDrag = { drag ->
                            layerPanelOffset = Offset(
                                (layerPanelOffset.x + drag.x).coerceIn(-maxDragX, 0f),
                                (layerPanelOffset.y + drag.y).coerceIn(-maxDragY, 0f)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            // The pen box sits in the same corner: the panel
                            // goes above it.
                            .padding(
                                end = 16.dp,
                                bottom = if (uiState.penBoxEnabled) 116.dp else 16.dp
                            )
                            .offset {
                                IntOffset(
                                    layerPanelOffset.x.roundToInt(),
                                    layerPanelOffset.y.roundToInt()
                                )
                            }
                            .zIndex(4f)
                    )
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
                            // The vertical dock sits in the middle of the
                            // left edge: the pen panel moves to the other
                            // corner so it can never cover the tools.
                            .align(if (uiState.dockHorizontal) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 64.dp
                            )
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
                                                            inkController.releaseSessionSuspend()
                                                        }
                                                        val currentBmp = if (isContinuous) null else canvasViewRef?.getBackingBitmap()
                                                        viewModel.insertNewPage(
                                                            atIndex = currentPageIndex + 1,
                                                            isContinuous = isContinuous,
                                                            currentBitmap = currentBmp
                                                        ) { newBmp ->
                                                            if (!isContinuous) {
                                                                applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                            } else {
                                                                savedPageGenerations.clear()
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
                                                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                // Light grey panel, like the original: the white
                                // group cards inside it are what makes the
                                // grouping readable.
                                color = Color(0xFFF5F6F8),
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
                                                    color = Color.White,
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Language, "Web Excerpt") {
                                                            // The app has no share target yet, so the
                                                            // hint has to name the way that works:
                                                            // copy the text or the image and paste it
                                                            // into the note (three-finger tap on the
                                                            // canvas, or the clipboard button).
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "Copy the text or the image, then paste it into the note",
                                                                android.widget.Toast.LENGTH_LONG
                                                            ).show()
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
                                                    color = Color.White,
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
                                                        ToolsPanelItem(Icons.Default.PictureAsPdf, "Page Background from PDF") {
                                                            scope.launch {
                                                                if (!isContinuous) {
                                                                    canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                                        viewModel.saveCurrentPageBitmap(bmp)
                                                                    }
                                                                } else {
                                                                    inkController.flushCurrentSuspend()
                                                                }
                                                                // The picked page becomes the background of
                                                                // the current page; the annotations stay.
                                                                pdfImportAsBackground = true
                                                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                                                viewModel.toggleToolsPanel(false)
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.EmojiEmotions, "Sticker") {
                                                            scope.launch {
                                                                // A sticker is a small drawing, not a
                                                                // text symbol: it is inserted as a layer
                                                                // so it can be moved, scaled and deleted
                                                                // like any other layer.
                                                                val kind = StickerArt.Kind.entries[
                                            stickerIndex % StickerArt.Kind.entries.size
                                        ]
                                        stickerIndex++
                                                                val sticker = StickerArt.draw(kind, 120)
                                                                insertLayerInCurrentNote { it.addImageLayer(sticker) }
                                                                viewModel.toggleToolsPanel(false)
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Timeline, "Add Mind Map") {
                                                            // Not implemented yet: the mind map has to
                                                            // live in the markup database (the nodes are
                                                            // blocks of a note) while the drawing stays a
                                                            // scribble page - that is still an open design
                                                            // question, so nothing is faked here.
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "Mind map: not implemented yet",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
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
                                                    color = Color.White,
                                                    border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        ToolsPanelItem(Icons.Default.Add, "Add Page") {
                                                            scope.launch {
                                                                if (isContinuous) {
                                                                    inkController.flushCurrentSuspend()
                                                                    inkController.releaseSessionSuspend()
                                                                }
                                                                val currentBmp = if (isContinuous) null else canvasViewRef?.getBackingBitmap()
                                                                viewModel.insertNewPage(
                                                                    atIndex = currentPageIndex + 1,
                                                                    isContinuous = isContinuous,
                                                                    currentBitmap = currentBmp
                                                                ) { newBmp ->
                                                                    if (!isContinuous) {
                                                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                                    } else {
                                                                        savedPageGenerations.clear()
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
                                                    color = Color.White,
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
                                                            // The laser pointer shows a red dot
                                                            // where the pen is; it writes nothing.
                                                            viewModel.selectTool(CanvasTool.LASER_POINTER)
                                                            viewModel.toggleToolsPanel(false)
                                                            android.widget.Toast.makeText(context, "Laser pointer: move the pen to point", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }

                                                // Other Section
                                                Text("Other", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color.White,
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
                                                ToolsPanelItem(Icons.Default.PictureAsPdf, "Export as Multi-Page PDF") {
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
                                                // 1. Stylus Mode — only offered once a
                                                // stylus has actually been used.
                                                SettingsCheckboxRow(
                                                    title = "Stylus Mode",
                                                    subtitle = if (uiState.stylusDetected) {
                                                        "Only the pen writes; the hand presses buttons"
                                                    } else {
                                                        "Turns on by itself when a stylus is used"
                                                    },
                                                    checked = uiState.stylusOnly,
                                                    enabled = uiState.stylusDetected,
                                                    onCheckedChange = { viewModel.toggleStylusOnly(it) }
                                                )
                                                // 2. Show Pencase - the floating tool dock
                                                SettingsCheckboxRow(
                                                    title = "Show Pencase",
                                                    subtitle = "The floating tool dock with the pens",
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
                                                // 4. Pen-Tool - the small pen panel in the
                                                // corner (a second, smaller palette)
                                                SettingsCheckboxRow(
                                                    title = "Pen-Tool",
                                                    subtitle = "A small pen panel in the corner",
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
                                                val isContinuousMode = uiState.pageTurningMode == 0
                                                val isSingleMode = uiState.pageTurningMode == 1

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
                                                                .clickable {
                                                                    if (!isContinuousMode) {
                                                                        scope.launch {
                                                                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                                                viewModel.saveCurrentPageBitmapDirect(bmp, force = true)
                                                                            }
                                                                            if (canvasViewRef?.layersDirty == true) {
                                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
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
                                                                color = if (isContinuousMode) Color.White else OrangePrimary
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


                                                Spacer(modifier = Modifier.height(6.dp))

                                                // 10. Horizontal Move Resistance
                                                Text(
                                                    text = "Horizontal move resistance (double click for centring)",
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
                                                    color = Color.White,
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
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "toolScale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (isSelected) selectedTint.copy(alpha = 0.16f) else Color.Transparent
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) selectedTint.copy(alpha = 0.40f) else Color.Transparent,
                shape = RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = label,
                tint = if (isSelected) selectedTint else Color(0xFF374151),
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) selectedTint else Color(0xFF374151),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Places a long-press popup next to the chip that opened it: below the chip
 * when there is room, otherwise above it. The vertical dock opens its popups
 * to the right of the chip so they do not sit on top of the dock itself.
 */
private class ChipPopupPositionProvider(
    private val preferRight: Boolean = false,
    /**
     * Where the popup window ended up on the screen. The popup lives in its
     * own window, so the glass inside it needs the screen position of that
     * window to know which part of the page lies underneath.
     */
    private val glassOrigin: GlassOrigin? = null,
    /** The offset of the app's root view inside the window. */
    private val rootOffset: Offset = Offset.Zero
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gap = 8
        val rawX = if (preferRight) {
            anchorBounds.right + gap
        } else {
            anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        }
        val x = rawX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - popupContentSize.height - gap
        val y = if (below + popupContentSize.height <= windowSize.height) below else above.coerceAtLeast(0)
        glassOrigin?.let {
            it.x = x - rootOffset.x
            it.y = y - rootOffset.y
        }
        return IntOffset(x, y)
    }
}

/** "6" for whole widths, "6.5" for fractional ones. */
private fun formatPx(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

/**
 * Long-press popup of the stroke-width chips: a slider that updates the width
 * live while dragging, the current size in px and a reset button that puts
 * the size back to the default of the active tool (the pen default from the
 * settings, the eraser default for the eraser). The range follows the tool -
 * the eraser is much larger than the pen.
 */
@Composable
private fun WidthPickerPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    presets: List<Pair<Float, String>>,
    activeWidth: Float,
    defaultWidth: Float,
    isEraser: Boolean,
    preferRight: Boolean,
    glassOrigin: GlassOrigin,
    rootOffset: Offset,
    glassShader: LiquidGlassShader,
    glassBackdropAt: (GlassOrigin) -> GlassBackdrop?,
    onWidthChange: (Float) -> Unit
) {
    if (!expanded) return
    val minWidth = if (isEraser) ERASER_WIDTH_MIN else PEN_WIDTH_MIN
    val maxWidth = if (isEraser) ERASER_WIDTH_MAX else PEN_WIDTH_MAX
    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = ChipPopupPositionProvider(
            preferRight = preferRight,
            glassOrigin = glassOrigin,
            rootOffset = rootOffset
        ),
        properties = PopupProperties(
            // Not focusable: a focusable popup swallows every touch outside
            // it, so a touch in the canvas could never close it. The canvas
            // closes it instead (see dismissCanvasOverlays).
            focusable = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        )
    ) {
        // Local thumb state keeps the drag smooth; the view model is updated
        // on every change so the canvas preview follows the slider.
        var sliderValue by remember { mutableFloatStateOf(activeWidth.coerceIn(minWidth, maxWidth)) }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            shadowElevation = 12.dp,
            modifier = Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(18.dp))
        ) {
            Box(
                modifier = Modifier
                    .liquidGlassBackdrop(glassShader, 18.dp) {
                        glassBackdropAt(glassOrigin)
                    }
                    .liquidGlassRim(18.dp, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Width", fontSize = 12.sp, color = Color(0xFF444444))
                            // Reset: back to the default of the active tool.
                            // 32 dp is the smallest touch target we allow.
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        onClickLabel = "Reset thickness",
                                        role = Role.Button
                                    ) {
                                        val resetTo = defaultWidth.coerceIn(minWidth, maxWidth)
                                        sliderValue = resetTo
                                        onWidthChange(resetTo)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset thickness",
                                    tint = Color(0xFF444444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "${formatPx(sliderValue)} px",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            onWidthChange(it)
                        },
                        valueRange = minWidth..maxWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * The colour palette is a grid of four columns. The cell size, the gap and
 * the padding are fixed so the popup can be exactly as wide as the grid:
 * a cell can never overlap its neighbour and no row can be cut off.
 */
private const val PALETTE_COLUMNS = 4
private val PaletteCellSize = 32.dp
private val PaletteCellGap = 8.dp
private val PalettePadding = 12.dp

/**
 * Long-press popup of the colour swatches: the full preset palette in a 4x2
 * grid (the active colour is ticked) plus a custom colour entry that opens
 * the existing colour picker dialog.
 */
@Composable
private fun ColorPalettePopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selectedColor: Color,
    preferRight: Boolean,
    onColorSelected: (Color) -> Unit,
    onCustomColor: () -> Unit
) {
    if (!expanded) return
    // As wide as the grid is: four cells and three gaps, plus the padding.
    val paletteWidth = PalettePadding * 2 +
        PaletteCellSize * PALETTE_COLUMNS +
        PaletteCellGap * (PALETTE_COLUMNS - 1)
    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = ChipPopupPositionProvider(preferRight),
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.97f),
            shadowElevation = 10.dp,
            modifier = Modifier.width(paletteWidth)
        ) {
            Column(
                modifier = Modifier.padding(PalettePadding),
                verticalArrangement = Arrangement.spacedBy(PaletteCellGap)
            ) {
                TouchNotesColors.chunked(PALETTE_COLUMNS).forEach { paletteRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PaletteCellGap)) {
                        paletteRow.forEach { c ->
                            val isSelected = selectedColor == c
                            Box(
                                modifier = Modifier
                                    .size(PaletteCellSize)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c)
                                    .border(
                                        if (isSelected) 2.dp else 0.5.dp,
                                        if (isSelected) OrangePrimary else Color.LightGray,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onColorSelected(c)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F3F3))
                        .clickable {
                            onDismiss()
                            onCustomColor()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Custom colour", fontSize = 12.sp, color = Color(0xFF444444))
                }
            }
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
            color = Color(0xFF212529),
            // The original puts the label at the right edge of the row.
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun SettingsCheckboxRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
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
/**
 * Re-reads the layers of a note from disk.
 *
 * The layers that are still only in memory (a layer that was just inserted,
 * or ink written on a layer) are written first, so applying the disk copy
 * can never drop them.
 */
private suspend fun DrawingCanvasView.reloadLayersFromDisk(
    fileStore: personal.notes.touch.data.files.NoteFileStore,
    noteId: String
) {
    if (layersDirty) {
        withContext(Dispatchers.IO) { saveImageLayers(fileStore, noteId) }
    }
    val disk = withContext(Dispatchers.IO) { readLayersFromDisk(fileStore, noteId) }
    // No manifest means the page has no layers. Applying the empty list
    // (rather than null, which applyLoadedLayers ignores) is what stops one
    // page's images from being shown - and then saved - on the next page.
    applyLoadedLayers(disk ?: emptyList())
}

/**
 * Two-finger pinch inside a page of the continuous reader.
 *
 * The list's own scroll takes the gesture away from a detector that sits
 * behind it, so the pinch is handled here — inside the page, where this
 * node runs before the list. It only reacts once a second pointer is down:
 * one finger keeps scrolling the reader and drawing.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPagePinch(
    onPinch: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Offset, Float) -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop
    awaitPointerEventScope {
        var pastSlop = false
        var zoom = 1f
        var pan = androidx.compose.ui.geometry.Offset.Zero
        var prevCentroid = androidx.compose.ui.geometry.Offset.Zero
        var prevDistance = 0f
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size < 2) {
                // The pinch is over: give the list back the gesture.
                if (pressed.isEmpty() || pastSlop) return@awaitPointerEventScope
                continue
            }
            val sumX = pressed.sumOf { it.position.x.toDouble() }.toFloat()
            val sumY = pressed.sumOf { it.position.y.toDouble() }.toFloat()
            val centroid = androidx.compose.ui.geometry.Offset(sumX / pressed.size, sumY / pressed.size)
            val distance = (pressed[0].position - pressed[1].position).getDistance()
            val zoomChange = if (prevDistance > 0f) distance / prevDistance else 1f
            val panChange = centroid - prevCentroid
            if (!pastSlop) {
                zoom *= zoomChange
                pan += panChange
                val zoomMotion = kotlin.math.abs(1f - zoom) * centroid.getDistance()
                if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) pastSlop = true
            }
            if (pastSlop) {
                // Take the gesture from the list.
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero) {
                    onPinch(centroid, panChange, zoomChange)
                }
            }
            prevCentroid = centroid
            prevDistance = distance
        }
    }
}

/**
 * Maps the reader page's pixel space onto the screen (root coordinates):
 * `root = pagePx * scale + offset`. Published by [ContinuousPageView] for the
 * page the user is on, so screen gestures (the live text box) can be turned
 * into the page pixels the layer engine stores.
 */
data class ContinuousPageMap(
    val pageIndex: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Where the page *image* of the reader sits on the screen.
 *
 * The map above is the ink space (the page pixels the strokes live in), but
 * the glass samples the page image - and the image is rendered at the
 * display resolution, not in the ink space. Without a second map the glass
 * sampled the wrong part of the page.
 */
data class ContinuousImageMap(
    val pageIndex: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Latest page-pixel -> screen map of the reader page the user is on. A plain
 * holder: it is written from the layout phase on every scroll frame and only
 * read when a gesture is committed, so it must not invalidate composition.
 */
class ContinuousPageMapHolder {
    var map: ContinuousPageMap? = null
    /** The same, for the page image the glass samples. */
    var imageMap: ContinuousImageMap? = null
}

/**
 * On-screen placement of one reader page, in root pixels. Plain fields: the
 * value is written from `onGloballyPositioned`, which runs on every scroll
 * frame, and must not invalidate composition.
 */
class ReaderPageBox {
    var originX = 0f
    var originY = 0f
    var width = 0
    var height = 0

    val valid: Boolean get() = width > 0 && height > 0
}

/**
 * Publishes the page-pixel -> root map of one reader page, using the same
 * ContentScale.Fit rect the page image and its layer canvas are drawn with.
 */
/**
 * Publishes the map of the page *image* (the bitmap the reader draws and the
 * glass samples).
 *
 * The image sits inside the page box (a fixed page has a margin around it),
 * so the map describes the rect the image is drawn in - not the whole box.
 */
private fun publishImageMap(
    pageBox: ReaderPageBox,
    pageIndex: Int,
    pageRect: ContinuousPageGeometry.BoxRect,
    imageWidth: Int,
    imageHeight: Int,
    onImageMapping: (ContinuousImageMap) -> Unit
) {
    if (!pageBox.valid || imageWidth <= 0 || imageHeight <= 0) return
    if (pageRect.empty) return
    // The image is drawn into the page rect: one pixel of the image is
    // pageRect.width * pageBox.width / imageWidth screen pixels.
    val scale = pageRect.width * pageBox.width / imageWidth
    onImageMapping(
        ContinuousImageMap(
            pageIndex = pageIndex,
            scale = scale,
            offsetX = pageBox.originX + pageRect.left * pageBox.width,
            offsetY = pageBox.originY + pageRect.top * pageBox.height
        )
    )
}

private fun publishPageMap(
    pageBox: ReaderPageBox,
    pageIndex: Int,
    pageRect: ContinuousPageGeometry.BoxRect,
    pageWidth: Int,
    pageHeight: Int,
    onPageMapping: (ContinuousPageMap) -> Unit
) {
    if (!pageBox.valid || pageWidth <= 0 || pageHeight <= 0) return
    if (pageRect.empty) return
    val scale = pageRect.width * pageBox.width / pageWidth
    onPageMapping(
        ContinuousPageMap(
            pageIndex = pageIndex,
            scale = scale,
            offsetX = pageBox.originX + pageRect.left * pageBox.width,
            offsetY = pageBox.originY + pageRect.top * pageBox.height
        )
    )
}

@Composable
fun ContinuousPageView(
    pageIndex: Int,
    page: personal.notes.touch.data.db.entity.PageEntity,
    noteId: String,
    pdfDocCache: personal.notes.touch.util.PdfDocumentCache,
    repository: personal.notes.touch.data.repo.NoteRepository,
    readerLayers: ReaderLayerCache,
    zoom: Float,
    ink: ContinuousInkController,
    inkSettings: InkToolSettings,
    backgroundMode: Int = 0,
    backgroundDensity: Int = 40,
    savedGeneration: Int = 0,
    isCurrentPage: Boolean = false,
    /** An infinite canvas has no page edge: no frame, no desk. */
    isInfinite: Boolean = false,
    /** The type of the note's pages ("infinite", "a4", "pdf"). */
    pageType: String = if (isInfinite) "infinite" else "a4",
    modifier: Modifier = Modifier,
    onStylusDetected: () -> Unit = {},
    onPageMapping: (ContinuousPageMap) -> Unit = {},
    onImageMapping: (ContinuousImageMap) -> Unit = {},
    onPageBitmap: (Int, android.graphics.Bitmap?) -> Unit = { _, _ -> },
    onPinch: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Offset, Float) -> Unit = { _, _, _ -> },
    onClick: () -> Unit
) {
    val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0

    // Where the page sits on screen: the live text box is placed in screen
    // coordinates, so its commit needs the page's on-screen rect. Kept out of
    // snapshot state on purpose — positionInRoot changes on every scroll
    // frame and recomposing the page for that would stutter the reader.
    val pageBox = remember(page.id) { ReaderPageBox() }

    /**
     * The size of the page image.
     *
     * The glass samples the image and maps it in the image's own pixels, not
     * in the ink space - and the image arrives after the first layout, so the
     * size is kept here and published with the next layout pass.
     */
    var pageImageWidth by remember(page.id) { mutableIntStateOf(0) }
    var pageImageHeight by remember(page.id) { mutableIntStateOf(0) }

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

    /**
     * The aspect ratio of the page image of this page.
     *
     * The image that is on screen wins; an infinite page that was never
     * drawn on keeps the shape of the saved canvas, every other page takes
     * the ratio of its PDF.
     */
    fun pageRatioOf(): Float {
        if (pageImageWidth > 0 && pageImageHeight > 0) {
            return pageImageWidth.toFloat() / pageImageHeight
        }
        if (isInfinite) {
            // An infinite page that was never drawn on has no image yet: the
            // page keeps the shape of the saved canvas.
            return ContinuousPageGeometry.INFINITE_PAGE_RATIO
        }
        return pageRatio
    }

    /**
     * Where the page image sits inside the page box.
     *
     * A fixed page is centred in its box and keeps a margin on both sides
     * (the room to write in the margin of an imported PDF); an infinite page
     * is fitted into the box, so the endless canvas is not bounded by the
     * saved bitmap. The ink session knows the same rect - the strokes are
     * normalised to the box.
     */
    fun pageRectOf(
        pageIndex: Int,
        boxWidth: Float,
        boxHeight: Float
    ): ContinuousPageGeometry.BoxRect {
        return ink.pageRectFor(pageIndex, boxWidth, boxHeight, pageRatioOf())
    }

    BoxWithConstraints(
        modifier = modifier
            // An infinite page has no edge: its box is the visible canvas,
            // not a rectangle with the aspect ratio of the saved bitmap.
            // A fixed page has a margin on both sides: its box is wider than
            // the page (the page plus a quarter of its width on each side).
            .then(
                if (isInfinite) {
                    Modifier
                } else {
                    Modifier.aspectRatio(
                        ContinuousPageGeometry.boxRatio(pageType, pageRatio)
                    )
                }
            )
            .onGloballyPositioned { coords ->
                pageBox.originX = coords.positionInRoot().x
                pageBox.originY = coords.positionInRoot().y
                pageBox.width = coords.size.width
                pageBox.height = coords.size.height
                if (isCurrentPage) {
                    // The page image sits inside the box (a fixed page has a
                    // margin, an infinite page is fitted into it), so the
                    // maps have to describe that rect and not the whole box.
                    val rect = pageRectOf(pageIndex, pageBox.width.toFloat(), pageBox.height.toFloat())
                    publishPageMap(
                        pageBox = pageBox,
                        pageIndex = pageIndex,
                        pageRect = rect,
                        pageWidth = readerLayers.entry(pageIndex)?.pageWidth ?: 0,
                        pageHeight = readerLayers.entry(pageIndex)?.pageHeight ?: 0,
                        onPageMapping = onPageMapping
                    )
                    // The glass samples the page image: it needs the map of
                    // the image, not of the ink space.
                    publishImageMap(
                        pageBox = pageBox,
                        pageIndex = pageIndex,
                        pageRect = rect,
                        imageWidth = pageImageWidth,
                        imageHeight = pageImageHeight,
                        onImageMapping = onImageMapping
                    )
                }
            }
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
        // The glass of the dock samples the page under it: the reader hands
        // its page image over so the glass bends the real pixels.
        LaunchedEffect(page.id, pageBitmap) {
            if (isCurrentPage) onPageBitmap(pageIndex, pageBitmap)
        }
        var loadCompleted by remember(page.id, page.updatedAt) {
            mutableStateOf(false)
        }
        LaunchedEffect(page.id, pageBitmap) {
            pageImageWidth = pageBitmap?.width ?: 0
            pageImageHeight = pageBitmap?.height ?: 0
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

        // The layer stack of the page: the layers are drawn in order (the
        // base layer first), and a hidden layer is not drawn at all - that
        // is the mask of the eye button.
        val layerTick = ink.layersRevision
        val layerStack = ink.layerStack(pageIndex)

        // Floating layers of this page. The cache decodes them off the main
        // thread; the entry is observable, so an edit repaints the page.
        val layerRevision = readerLayers.revision(pageIndex)
        val layerEntry = readerLayers.entry(pageIndex)
        LaunchedEffect(page.id, layerRevision) {
            readerLayers.load(pageIndex)
        }

        // Publish the page-pixel -> screen map of the page the user is on.
        // The layer canvas below uses exactly this fit, so a point committed
        // through it lands where the same pixels are drawn.
        LaunchedEffect(isCurrentPage, layerEntry) {
            if (isCurrentPage) {
                publishPageMap(
                    pageBox = pageBox,
                    pageIndex = pageIndex,
                    pageRect = pageRectOf(pageIndex, pageBox.width.toFloat(), pageBox.height.toFloat()),
                    pageWidth = layerEntry?.pageWidth ?: 0,
                    pageHeight = layerEntry?.pageHeight ?: 0,
                    onPageMapping = onPageMapping
                )
            }
        }
        val layerPaint = remember {
            android.graphics.Paint(
                android.graphics.Paint.FILTER_BITMAP_FLAG or
                    android.graphics.Paint.ANTI_ALIAS_FLAG
            )
        }

        // The annotation layers of the page as they are on the disk.
        //
        // The ink session holds the layers it writes on; without a session
        // (the user only reads) the layers of the page are loaded here, so
        // the writing of an earlier session stays on the screen.
        var savedLayers by remember(page.id, noteId) {
            mutableStateOf<Map<Int, android.graphics.Bitmap?>>(emptyMap())
        }
        LaunchedEffect(page.id, noteId, savedGeneration, activeSessionReady, layerTick) {
            if (activeSessionReady) {
                // The session has the page: it holds the layers it writes on.
                savedLayers = emptyMap()
                return@LaunchedEffect
            }
            savedLayers = loadPageLayersForDisplay(
                repository.fileStore, noteId, pageIndex, displayWidthPx.roundToInt()
            )
        }

        LaunchedEffect(page.id, page.updatedAt, renderWidthKey, noteId, activeSessionReady, savedGeneration) {
            ink.refreshBackground(page, pageIndex, desiredWidthPx)
            if (activeSessionReady && isVector) {
                // A vector page is drawn by the session (the clean pdfium
                // render under the layer), so the page is not rendered twice.
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
            else -> pageBitmap
        }

        // The desk: the part of the box that is not the page is not paper.
        // The user writes in it, so it has to look like the desk (the grey
        // of the reader), not like the document.
        if (!isInfinite) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFFEEEEEE))
            }
        }

        // White page bed: an erased raster pixel has to show paper, not the
        // reader's grey backdrop. It covers the page only - the margin
        // beside the page stays the desk.
        if (isInfinite) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                shape = RoundedCornerShape(4.dp)
            ) {}
        } else {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = pageRectOf(pageIndex, size.width, size.height)
                if (rect.empty) return@Canvas
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        rect.left * size.width,
                        rect.top * size.height
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        rect.width * size.width,
                        rect.height * size.height
                    )
                )
            }
        }

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
                    // The pattern belongs to the paper: on a fixed page it
                    // stops at the edge of the page (the margin is the
                    // desk), on an infinite page it fills the canvas.
                    val rect = if (isInfinite) null else pageRectOf(pageIndex, size.width, size.height)
                    if (rect != null && rect.empty) return@Canvas
                    val left = rect?.left?.times(size.width) ?: 0f
                    val top = rect?.top?.times(size.height) ?: 0f
                    val w = rect?.width?.times(size.width) ?: size.width
                    val h = rect?.height?.times(size.height) ?: size.height
                    val density = backgroundDensity.coerceIn(20, 80).toFloat()
                    val step = (density * (w / 2160f)).coerceAtLeast(8f)
                    val gridColor = Color(0xFFE0E0E0)
                    clipRect(left, top, left + w, top + h) {
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
        }

        if (shown != null && !shown.isRecycled) {
            // The page image is drawn into the page rect (the box minus the
            // margin), not into the whole box: the margin is the room the
            // user writes in.
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Page ${pageIndex + 1}" }
            ) {
                val rect = pageRectOf(pageIndex, size.width, size.height)
                if (rect.empty) return@Canvas
                val dest = android.graphics.RectF(
                    rect.left * size.width,
                    rect.top * size.height,
                    rect.right * size.width,
                    rect.bottom * size.height
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(shown, null, dest, layerPaint)
                }
            }
        }

        // Floating layers, composited in the page's own pixel space so they
        // land exactly where the single-page engine placed them. The page is
        // drawn into the page rect, so the same rect is used here.
        val pageLayers = layerEntry?.layers.orEmpty()
        val layerPageWidth = layerEntry?.pageWidth ?: 0
        val layerPageHeight = layerEntry?.pageHeight ?: 0
        if (pageLayers.isNotEmpty() && layerPageWidth > 0 && layerPageHeight > 0) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = pageRectOf(pageIndex, size.width, size.height)
                if (rect.empty) return@Canvas
                val scaleX = rect.width * size.width / layerPageWidth
                val scaleY = rect.height * size.height / layerPageHeight
                val offsetX = rect.left * size.width
                val offsetY = rect.top * size.height
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saveCount = native.save()
                    native.translate(offsetX, offsetY)
                    native.scale(scaleX, scaleY)
                    drawImageLayers(native, pageLayers, layerPaint)
                    native.restoreToCount(saveCount)
                }
            }
        }

        // Page border: drawn after the page so it stays visible on top of an
        // opaque PDF page (the image would otherwise cover it).
        // An infinite canvas has no edge at all - a frame there would turn
        // the endless canvas into a sheet of paper.
        // The frame surrounds the page image, not the whole box: the margin
        // around it belongs to the desk.
        if (!isInfinite) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = if (isCurrentPage) 1.5.dp.toPx() else 1.dp.toPx()
            val borderColor = if (isCurrentPage) {
                OrangePrimary.copy(alpha = 0.6f)
            } else {
                Color(0xFFDCDFE6)
            }
            val rect = pageRectOf(pageIndex, size.width, size.height)
            val left = rect.left * size.width + stroke / 2f
            val top = rect.top * size.height + stroke / 2f
            drawRect(
                color = borderColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(
                    (rect.width * size.width - stroke).coerceAtLeast(1f),
                    (rect.height * size.height - stroke).coerceAtLeast(1f)
                )
            )
        }
        }

        if (shown == null && !loadCompleted && !sessionReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = OrangePrimary,
                    strokeWidth = 2.dp
                )
            }
        }

        // The annotation layers of the page: the writing of the user lives on
        // them, and they reach out to the left and to the right of the page -
        // that is the room to write in the margin of an imported PDF. They
        // are drawn over the page image, so the document underneath stays
        // clean.
        //
        // The layers are drawn in order (the base layer first, the layer the
        // user added on top of it last) and a hidden layer is not drawn at
        // all: that is the mask of the eye button.
        layerStack.layers.forEach { layer ->
            if (!layer.visible) return@forEach
            val pixels = if (sessionReady) {
                ink.layerBitmaps(pageIndex, layer)
            } else {
                listOfNotNull(savedLayers[layer.index])
            }
            val ops = ink.layerOps(pageIndex, layer)
            if (pixels.isEmpty() && ops.isEmpty()) return@forEach
            if (pixels.isNotEmpty()) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val _revision = ink.revision
                    val rect = ink.layerRectFor(pageIndex, size.width, size.height, pageRatioOf())
                    if (rect.empty) return@Canvas
                    val dest = android.graphics.RectF(
                        rect.left * size.width,
                        rect.top * size.height,
                        rect.right * size.width,
                        rect.bottom * size.height
                    )
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        pixels.forEach { bitmap ->
                            if (!bitmap.isRecycled) {
                                native.drawBitmap(bitmap, null, dest, layerPaint)
                            }
                        }
                    }
                }
            }

            // The strokes that were finished in this session: they are drawn
            // as vectors on top of the page image, so a finished stroke never
            // rewrites the session bitmap (and the GPU never has to take
            // that bitmap again - the pen stops lagging behind the finger).
            //
            // An eraser clears pixels, so it needs an offscreen layer: the
            // wipe must cut the strokes of this layer, never the page image
            // (the PDF text) or the layer below it.
            if (ops.isNotEmpty()) {
                val eraseLayer = ink.layerHasErase(pageIndex, layer)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (eraseLayer) {
                                Modifier.graphicsLayer(
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val _revision = ink.revision
                    drawIntoCanvas { canvas ->
                        ink.drawLayerOps(
                            canvas.nativeCanvas, pageIndex, layer, size.width, size.height
                        )
                    }
                }
            }
        }

        // The in-progress stroke (and the eraser's swath hint) paints above
        // the committed layer; the commit lands on ACTION_UP.
        // The preview revision is read here so the stroke follows the pen:
        // without it the page would keep the first point of the stroke until
        // the pen lifts.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val _revision = ink.revision
            val _previewRevision = if (ink.livePageIndex == pageIndex) ink.previewRevision else 0
            val _laserRevision = ink.laserRevision
            // The strokes are normalised to the box: the session has to know
            // the size of the box (the zoom changed it).
            ink.updateGeometry(pageIndex, size.width, size.height)
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
                .pointerInput(Unit) {
                    // Two fingers zoom the reader; one finger still scrolls.
                    detectPagePinch { centroid, pan, zoom -> onPinch(centroid, pan, zoom) }
                }
                .pointerInput(page.id, inkSettings) {
                    if (inkSettings.readingMode) return@pointerInput
                    try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        if (isStylus) onStylusDetected()
                        val isAllowedPointer = isStylus || (!inkSettings.stylusOnly && down.type == PointerType.Touch)
                        if (size.width <= 0 || size.height <= 0) return@awaitEachGesture

                        // The laser pointer: a dot follows the pointer, nothing
                        // is written to the page. It works with a finger as
                        // well, in every mode.
                        if (inkSettings.isLaserPointer) {
                            val lBoxW = size.width.toFloat()
                            val lBoxH = size.height.toFloat()
                            ink.updateLaser(pageIndex, down.position.x / lBoxW, down.position.y / lBoxH)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                ink.updateLaser(pageIndex, change.position.x / lBoxW, change.position.y / lBoxH)
                                change.consume()
                                if (change.changedToUpIgnoreConsumed()) break
                            }
                            ink.clearLaser()
                            return@awaitEachGesture
                        }
                        // Stylus mode: the hand may scroll, pinch and use the
                        // tools, but it does not write.
                        if (!isAllowedPointer) return@awaitEachGesture
                        if (!inkSettings.isWritingTool) return@awaitEachGesture
                        
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
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                // The pointer is gone: end the stroke, or the
                                // reader would stay "drawing" and refuse to
                                // scroll.
                                ink.cancelStroke()
                                break
                            }
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
                    } finally {
                        // The gesture handler can be cancelled (the tool
                        // changed, the item left the list): the reader must
                        // not stay in the "drawing" state, or it would refuse
                        // to scroll.
                        ink.cancelStroke()
                    }
                }
        )
    }
}

/**
 * The annotation layers of a page, decoded for the screen.
 *
 * The layers are as wide as the reader's page box (the page plus the margin
 * on both sides), so they are bigger than the screen: they are decoded
 * downsampled, but with their alpha - they are an overlay on the page, not a
 * picture.
 *
 * Only the visible layers are decoded: a hidden layer is not drawn at all.
 * The key of the map is the number of the layer.
 */
internal suspend fun loadPageLayersForDisplay(
    fileStore: personal.notes.touch.data.files.NoteFileStore,
    noteId: String,
    pageIndex: Int,
    displayWidthPx: Int
): Map<Int, android.graphics.Bitmap?> = withContext(Dispatchers.IO) {
    val result = LinkedHashMap<Int, android.graphics.Bitmap?>()
    try {
        val metaFile = fileStore.getAnnotationMetaFile(noteId, pageIndex)
        val text = if (metaFile.exists()) metaFile.readText() else null
        val stack = text?.let { PageLayerMeta.decode(it) } ?: PageLayerStack.base()
        for (layer in stack.layers) {
            if (!layer.visible) continue
            result[layer.index] = loadAnnotationLayerForDisplay(
                fileStore, noteId, pageIndex, displayWidthPx, layer.index
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    result
}

/**
 * The annotation layer of a page, decoded for the screen.
 *
 * The layer is as wide as the reader's page box (the page plus the margin on
 * both sides), so it is bigger than the screen: it is decoded downsampled,
 * but with its alpha - it is an overlay on the page, not a picture.
 */
internal suspend fun loadAnnotationLayerForDisplay(
    fileStore: personal.notes.touch.data.files.NoteFileStore,
    noteId: String,
    pageIndex: Int,
    displayWidthPx: Int,
    layerIndex: Int = ContinuousPageGeometry.BASE_LAYER_INDEX
): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    try {
        val file = fileStore.getAnnotationLayerFile(noteId, pageIndex, layerIndex)
        if (!file.exists()) return@withContext null
        val bounds = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        val target = displayWidthPx.coerceAtLeast(360)
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (e: Exception) {
        e.printStackTrace()
        null
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
