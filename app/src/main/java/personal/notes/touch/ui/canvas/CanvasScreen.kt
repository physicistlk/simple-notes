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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CenterFocusStrong
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import personal.notes.touch.util.PdfOutlineImporter
import personal.notes.touch.util.PdfTextIndex
import personal.notes.touch.util.PdfiumDocument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import personal.notes.touch.util.MarginExportMath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
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
import personal.notes.touch.ui.components.LiquidMorphSegmentedToggle
import personal.notes.touch.ui.components.liquidGlassRim
import personal.notes.touch.ui.components.liquidMorphDropletsBackground
import personal.notes.touch.ui.components.liquidStylusSpotlight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import personal.notes.touch.R
import personal.notes.touch.TouchNotesApp
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.ui.theme.OrangePrimary
import personal.notes.touch.util.ClipboardHelper
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfDocumentCache
import personal.notes.touch.util.PdfImportManager
import personal.notes.touch.util.PdfModeReconciliationPolicy
import personal.notes.touch.util.PdfPageGeometry
import personal.notes.touch.util.PdfTextSelectActivation
import personal.notes.touch.util.PdfTextSelectionInfo
import personal.notes.touch.util.PdfTextToolPolicy
import personal.notes.touch.util.PdfVectorImporter
import personal.notes.touch.util.TextDockTapAction

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

/**
 * The widest a page is rendered for the screen, in pixels.
 *
 * The reader draws the page as big as the zoom asks for, so a pinch that
 * goes in reveals detail instead of a blurry enlargement. A page rendered
 * at eight times the width of the screen would need more than a gigabyte of
 * memory, so the render stops here and the rest is enlarged.
 */
private const val MAX_PAGE_RENDER_WIDTH_PX = 4800

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

    var selectedImageIndex by remember { mutableStateOf(-1) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportTarget by remember { mutableStateOf<personal.notes.touch.ui.export.ExportTarget?>(null) }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var showPdfPagePicker by remember { mutableStateOf(false) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showInfinitePdfColumnPicker by remember { mutableStateOf(false) }
    var pendingInfinitePdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedInfinitePdfColumns by remember { mutableIntStateOf(2) }
    /** True while the next PDF is picked as a page background, not imported. */
    var pdfImportAsBackground by remember { mutableStateOf(false) }
    var pendingPdfPageCount by remember { mutableStateOf(0) }
    var pendingPdfPageIndex by remember { mutableStateOf("1") }
    val importProgressFlow = remember { MutableStateFlow(PdfImportProgressState()) }
    val importProgressState by importProgressFlow.collectAsState()
    var importCancelFlag by remember { mutableStateOf<AtomicBoolean?>(null) }
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
    var showSearchSheet by remember { mutableStateOf(false) }
    var isSearchSheetExpanded by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var pdfIndexData by remember(viewModel.noteId) { mutableStateOf<PdfTextIndex.PdfTextIndexData?>(null) }
    var isIndexingPdf by remember { mutableStateOf(false) }
    var pdfIndexDone by remember { mutableIntStateOf(0) }
    var pdfIndexTotal by remember { mutableIntStateOf(0) }
    var activeSearchHitIndex by remember { mutableIntStateOf(-1) }
    var searchHighlightTarget by remember { mutableStateOf<SearchHighlightTarget?>(null) }
    var searchHighlightRevision by remember { mutableIntStateOf(0) }
    var singlePageApplyRevision by remember { mutableIntStateOf(0) }
    var singlePageAppliedPageIndex by remember { mutableIntStateOf(-1) }
    val singlePageOverlayLoadSeq = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isScrubSettling by remember { mutableStateOf(false) }
    var scrubJumpToken by remember { mutableIntStateOf(0) }
    var scrubJumpJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var continuousVisibleIndex by remember { mutableIntStateOf(0) }
    var settledProgrammaticFirstVis by remember { mutableIntStateOf(-1) }
    var settledProgrammaticPage by remember { mutableIntStateOf(-1) }
    var pageToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    /** Which sticker the Tools menu drops next. */
    var stickerIndex by remember { mutableIntStateOf(0) }
    var pencaseOffsetX by remember { mutableFloatStateOf(0f) }
    var pencaseOffsetY by remember { mutableFloatStateOf(0f) }
    // Long-press popups of the dock (width slider / full colour palette).
    var showWidthPopupHoriz by remember { mutableStateOf(false) }
    /** The width chip the slider popup belongs to (only that one shows it). */
    var widthPopupChipHoriz by remember { mutableIntStateOf(-1) }
    var dockWidths by remember { mutableStateOf(TouchNotesWidthPresets.map { it.first }) }
    var dockEraserWidths by remember { mutableStateOf(TouchNotesEraserPresets.map { it.first }) }
    var showColorPopupHoriz by remember { mutableStateOf(false) }
    /**
     * The colours of the dock's colour chips.
     *
     * The chips are the user's palette: a colour that is picked in the
     * palette of a chip stays with that chip.
     */
    var dockColors by remember { mutableStateOf(TouchNotesDockColors.toList()) }
    /** The swatch the colour popup belongs to (only that one shows it). */
    var colorPopupChipHoriz by remember { mutableIntStateOf(-1) }
    var showWidthPopupVert by remember { mutableStateOf(false) }
    /** The width chip the slider popup belongs to (only that one shows it). */
    var widthPopupChipVert by remember { mutableIntStateOf(-1) }
    var showColorPopupVert by remember { mutableStateOf(false) }
    /** The swatch the colour popup belongs to (only that one shows it). */
    var colorPopupChipVert by remember { mutableIntStateOf(-1) }

    val isInfinite = (note?.pageType ?: "infinite") == "infinite"
    /** An infinite canvas is endless: no page frame, no desk, no edge. */
    val isInfiniteNote = note?.pageType == "infinite"
    val isContinuous = uiState.pageTurningMode == 0 || isInfiniteNote
    val isHorizontal = uiState.pageTurningDirection == 1

    var continuousScale by remember { mutableFloatStateOf(1f) }
    var continuousPanX by remember { mutableFloatStateOf(0f) }
    var continuousPanY by remember { mutableFloatStateOf(0f) }
    var singlePageScale by remember { mutableFloatStateOf(1f) }
    var showZoomHud by remember { mutableStateOf(false) }
    var zoomHudActivityTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(zoomHudActivityTick, showZoomHud) {
        if (showZoomHud) {
            kotlinx.coroutines.delay(2000L)
            showZoomHud = false
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var readerViewportWidthPx by remember { mutableFloatStateOf(screenWidthPx) }
    var marginLabelActive by remember { mutableStateOf(false) }
    var marginLabelActivityTick by remember { mutableIntStateOf(0) }
    val hasMarginGuides = !isInfiniteNote &&
        ContinuousPageGeometry.marginFor(note?.pageType) > 0f
    val marginLabelAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (marginLabelActive) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (marginLabelActive) 150 else 500
        ),
        label = "marginLabelAlpha"
    )

    val showTabsState = uiState.showTabs
    val fixPreviewPageState = uiState.fixPreviewPage
    val horizontalResistance = uiState.horizontalResistance
    val minimalInfiniteZoom = uiState.minimalInfiniteZoom

    var infiniteItems by remember(viewModel.noteId) {
        mutableStateOf<List<InfiniteCanvasItem>>(emptyList())
    }
    var selectedInfiniteItemId by remember(viewModel.noteId) {
        mutableStateOf<String?>(null)
    }
    val infiniteBitmapCache = remember(viewModel.noteId) {
        InfiniteItemBitmapCache(viewModel.fileStore, viewModel.noteId, scope)
    }
    DisposableEffect(infiniteBitmapCache) {
        onDispose { infiniteBitmapCache.close() }
    }
    LaunchedEffect(viewModel.noteId, isInfiniteNote) {
        if (isInfiniteNote) {
            infiniteItems = withContext(Dispatchers.IO) {
                InfiniteItemsStore.load(viewModel.fileStore, viewModel.noteId)
            }
        }
    }

    // F-10 PDF text selection: whether the current vector page has extractable
    // text, whether the Text tool is in selection mode, and the live selection.
    var pdfTextAvailable by remember { mutableStateOf(false) }
    var pdfTextMode by remember { mutableStateOf(false) }
    var pdfSelection by remember { mutableStateOf<PdfTextSelectionInfo?>(null) }
    var showTextMenuHoriz by remember { mutableStateOf(false) }
    var showTextMenuVert by remember { mutableStateOf(false) }
    var pendingSwitchForPdfText by remember { mutableStateOf(false) }

    val exitPdfTextMode: () -> Unit = {
        if (pdfTextMode || pdfSelection != null) {
            pdfTextMode = false
            pdfSelection = null
            canvasViewRef?.setPdfTextSelectionMode(false)
        }
    }
    val openLiveTextOverlay: () -> Unit = {
        exitPdfTextMode()
        liveTextContent = ""
        liveTextOffsetX = 0f
        liveTextOffsetY = 0f
        liveTextColor = if (uiState.selectedColor == Color.White) Color.Black else uiState.selectedColor
        isLiveTextActive = true
    }

    val totalPages = note?.pageCount ?: pages.size.coerceAtLeast(1)

    // Touching the page means "I am writing again": the slide-over menus
    // (page drawer, tools panel, layers, shapes menu) close themselves so the
    // user never has to tap a close button after changing a setting. The
    // pen-case dock is left alone — it is the tool palette, not a menu.
    val dismissCanvasOverlays: () -> Unit = {
        viewModel.dismissCanvasMenus()
        showLayersPanel = false
        showShapesMenu = false
        showTextMenuHoriz = false
        showTextMenuVert = false
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

    LaunchedEffect(viewModel) {
        viewModel.pageSavedEvents.collect { savedIdx ->
            savedPageGenerations[savedIdx] = (savedPageGenerations[savedIdx] ?: 0) + 1
        }
    }

    // Read-only layer cache of the continuous reader: decodes the same page
    // layer files the single-page engine writes, off the main thread.
    val readerLayers = remember(viewModel.noteId) {
        ReaderLayerCache(viewModel.fileStore, viewModel.noteId) { pageIdx ->
            val vp = viewModel.pages.value.getOrNull(pageIdx)
                ?.takeIf { it.pdfSourcePath.isNotEmpty() && it.pdfPageIndex >= 0 }
                ?: return@ReaderLayerCache null
            val aspect = PageAspectCache.default.getOrLoad(
                vp.pdfSourcePath,
                vp.pdfPageIndex,
                fallback = PdfPageGeometry.DEFAULT_ASPECT
            ) {
                pdfDocCache.document(vp.pdfSourcePath)?.pageSize(vp.pdfPageIndex)
            }
            PdfModeReconciliationPolicy.pagePixelSize(aspect)
        }
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

    LaunchedEffect(marginLabelActivityTick, marginLabelActive, inkController.isDrawing) {
        if (marginLabelActive && !inkController.isDrawing) {
            kotlinx.coroutines.delay(1500L)
            marginLabelActive = false
        }
    }
    LaunchedEffect(
        inkController.isDrawing,
        inkController.revision,
        inkController.marginPresenceRevision,
        hasMarginGuides,
        currentPageIndex
    ) {
        if (hasMarginGuides) {
            val presence = inkController.marginInkPresence(currentPageIndex)
            if ((inkController.isDrawing && presence.hasAnyMarginInk) ||
                (presence.hasAnyMarginInk && marginLabelActivityTick == 0)
            ) {
                marginLabelActive = true
                marginLabelActivityTick++
            }
        }
    }

    if (isInfiniteNote) {
        inkController.infiniteWorldBounds = {
            val activeIdx = currentPageIndex
            val activeStack = inkController.layerStack(activeIdx)
            val snapshot = infiniteItems.filter { item ->
                item.pageIndex == activeIdx &&
                    (activeStack.layerAt(item.layerIndex)?.visible != false)
            }
            if (snapshot.isEmpty()) {
                null
            } else {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (item in snapshot) {
                    val l = (item.centerX - item.width * 0.6f) / 2160f
                    val t = (item.centerY - item.height * 0.6f) / 2160f
                    val r = (item.centerX + item.width * 0.6f) / 2160f
                    val b = (item.centerY + item.height * 0.6f) / 2160f
                    if (l < minX) minX = l
                    if (t < minY) minY = t
                    if (r > maxX) maxX = r
                    if (b > maxY) maxY = b
                }
                floatArrayOf(minX, minY, maxX, maxY)
            }
        }
        inkController.infiniteBackdropDrawer = { canvas, worldLeft, worldTop, pxPerUnit ->
            val activeIdx = currentPageIndex
            val activeStack = inkController.layerStack(activeIdx)
            val snapshot = infiniteItems.filter { item ->
                item.pageIndex == activeIdx &&
                    (activeStack.layerAt(item.layerIndex)?.visible != false)
            }
            if (snapshot.isNotEmpty()) {
                val itemScale = pxPerUnit / 2160f
                val worldRight = worldLeft + canvas.width.toFloat() / pxPerUnit
                val worldBottom = worldTop + canvas.height.toFloat() / pxPerUnit
                val minWx = worldLeft * 2160f
                val minWy = worldTop * 2160f
                val maxWx = worldRight * 2160f
                val maxWy = worldBottom * 2160f
                val save = canvas.save()
                canvas.translate(-worldLeft * pxPerUnit, -worldTop * pxPerUnit)
                canvas.scale(itemScale, itemScale)
                val itemPaint = android.graphics.Paint(
                    android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
                )
                val paperFill = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.FILL
                }
                val dst = android.graphics.RectF()
                for (item in snapshot) {
                    val halfSpan = maxOf(item.width, item.height) * 0.72f
                    if (item.centerX + halfSpan < minWx || item.centerX - halfSpan > maxWx ||
                        item.centerY + halfSpan < minWy || item.centerY - halfSpan > maxWy
                    ) {
                        continue
                    }
                    val bmp = infiniteBitmapCache.getExistingBitmapForBackdrop(item)
                    val itemSave = canvas.save()
                    canvas.translate(item.centerX, item.centerY)
                    if (item.rotationDeg != 0f) {
                        canvas.rotate(item.rotationDeg)
                    }
                    if (item.flipHorizontal) {
                        canvas.scale(-1f, 1f)
                    }
                    dst.set(
                        -item.width / 2f,
                        -item.height / 2f,
                        item.width / 2f,
                        item.height / 2f
                    )
                    if (item is InfiniteCanvasItem.PdfPageItem) {
                        canvas.drawRect(dst, paperFill)
                    }
                    if (bmp != null && !bmp.isRecycled) {
                        canvas.drawBitmap(bmp, null, dst, itemPaint)
                    }
                    canvas.restoreToCount(itemSave)
                }
                canvas.restoreToCount(save)
            }
        }
    } else {
        inkController.infiniteWorldBounds = null
        inkController.infiniteBackdropDrawer = null
    }
    LaunchedEffect(isInfiniteNote, infiniteItems, infiniteBitmapCache.renderTick) {
        if (isInfiniteNote) {
            kotlinx.coroutines.delay(180)
            inkController.refreshGlassCopy()
        }
    }

    val glassBackdropAt: (GlassOrigin) -> GlassBackdrop? = { origin ->
        val view = canvasViewRef
        if (isContinuous) {
            // Observe ink revision and infinite item render ticks so the glass
            // redraws immediately when background copies finish building.
            @Suppress("UNUSED_EXPRESSION")
            inkController.revision
            @Suppress("UNUSED_EXPRESSION")
            infiniteBitmapCache.renderTick
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
            // The glass samples the **annotation layer** of the page: the
            // layer holds the writing, the page image does not - so with the
            // page image the strokes never dispersed through the lens.
            //
            // The layer is a grid of tiles now, so the glass samples a small
            // copy of it (the tiles composed at the resolution the glass
            // reads the page at). The origin of the copy is the top-left
            // corner of the layer, so the glass still looks at the writing
            // that lies under it.
            val localOriginX = origin.x - canvasOriginInRoot.x
            val localOriginY = origin.y - canvasOriginInRoot.y
            val panX = if (isInfiniteNote) continuousPanX else if (isHorizontal) 0f else continuousPanX
            val panY = if (isInfiniteNote) continuousPanY else if (isHorizontal) continuousPanY else 0f
            val gridStepPx = if (isInfiniteNote) {
                uiState.backgroundDensity.coerceIn(20, 80).toFloat() * 2f * continuousScale
            } else 0f
            val gridMode = if (isInfiniteNote) {
                when (uiState.infiniteGridPreset) {
                    InfiniteGridPreset.BLANK -> 0
                    InfiniteGridPreset.LINED -> 1
                    InfiniteGridPreset.GRAPH, InfiniteGridPreset.TRIANGLE -> 2
                    InfiniteGridPreset.DOTS, InfiniteGridPreset.ISOMETRIC -> 3
                }
            } else 0
            val gridOrigX = panX - localOriginX
            val gridOrigY = panY - localOriginY
            val mapping = inkController.layerScreenMapping(currentPageIndex, panX, panY, continuousScale)
            if (mapping != null) {
                val pBox = continuousPageMap.pageBox?.takeIf { !isInfiniteNote && it.valid && it.pageIndex == currentPageIndex }
                val pageOriginX: Float
                val pageOriginY: Float
                val invScale: Float
                if (pBox != null) {
                    val lRect = inkController.layerRectFor(
                        currentPageIndex, pBox.width.toFloat(), pBox.height.toFloat()
                    )
                    val layerRootX = pBox.originX + lRect.left * pBox.width
                    val layerRootY = pBox.originY + lRect.top * pBox.height
                    val layerScreenW = (lRect.width * pBox.width).coerceAtLeast(1f)
                    invScale = mapping.bitmap.width.toFloat() / layerScreenW
                    pageOriginX = (origin.x - layerRootX) * invScale
                    pageOriginY = (origin.y - layerRootY) * invScale
                } else {
                    val effOriginX = if (isInfiniteNote) localOriginX else {
                        val pm = continuousPageMap.map
                        if (pm != null && pm.pageIndex == currentPageIndex) origin.x - pm.offsetX + panX else localOriginX
                    }
                    val effOriginY = if (isInfiniteNote) localOriginY else {
                        val pm = continuousPageMap.map
                        if (pm != null && pm.pageIndex == currentPageIndex) origin.y - pm.offsetY + panY else localOriginY
                    }
                    invScale = mapping.scale
                    pageOriginX = (effOriginX - mapping.originX) * invScale
                    pageOriginY = (effOriginY - mapping.originY) * invScale
                }
                GlassBackdrop(
                    bitmap = mapping.bitmap,
                    // The origin of the glass in the pixels of the copy.
                    pageOriginX = pageOriginX,
                    pageOriginY = pageOriginY,
                    invScale = invScale,
                    // The copy is already as small as the glass reads the
                    // page: it is sampled as it is.
                    sampleScale = 1f,
                    gridStepPx = gridStepPx,
                    gridOriginX = gridOrigX,
                    gridOriginY = gridOrigY,
                    gridMode = gridMode
                )
            } else {
                // No layer (yet): the page image is the next best thing.
                val map = continuousPageMap.imageMap
                val bmp = glassReaderBitmap
                if (map == null || bmp == null || map.scale <= 0f ||
                    map.pageIndex != glassReaderBitmapPage
                ) {
                    GlassBackdrop(
                        bitmap = null,
                        pageOriginX = localOriginX - panX,
                        pageOriginY = localOriginY - panY,
                        invScale = 1f / continuousScale.coerceAtLeast(0.1f),
                        sampleScale = 1f,
                        gridStepPx = gridStepPx,
                        gridOriginX = gridOrigX,
                        gridOriginY = gridOrigY,
                        gridMode = gridMode
                    )
                } else {
                    GlassBackdrop(
                        bitmap = bmp,
                        pageOriginX = (origin.x - map.offsetX) / map.scale,
                        pageOriginY = (origin.y - map.offsetY) / map.scale,
                        invScale = 1f / map.scale,
                        gridStepPx = gridStepPx,
                        gridOriginX = gridOrigX,
                        gridOriginY = gridOrigY,
                        gridMode = gridMode
                    )
                }
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
                if (sx <= 0.0001f) null else GlassBackdrop(bmp, p0[0], p0[1], sx)
            }
        }
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
        // Same base geometry the import used, so pages keep their true aspect.
        val pageAspect = PageAspectCache.default.put(
            relPath, pdfPageIndex, size[0], size[1], fallback = PdfPageGeometry.DEFAULT_ASPECT
        )
        val targetWidth = 2160f
        val targetHeight = (2160f / pageAspect).coerceIn(1f, 4096f)
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
        fun currentPxPerPt(): Float {
            val rectWidth = canvas.pageCanvasWidth().takeIf { it > 0f } ?: baseOutWidth.toFloat()
            return rectWidth / pageW
        }
        canvas.setPdfTextSource(object : DrawingCanvasView.PdfTextSource {
            override fun charCount(): Int = document.textCharCount(pdfPageIndex)

            override fun charIndexAt(x: Float, y: Float, tolerance: Float): Int {
                val pxPerPt = currentPxPerPt()
                val user = PdfPageGeometry.canvasToUserPoint(x, y, pageH, pxPerPt)
                return document.charIndexAt(
                    pdfPageIndex, user[0], user[1], tolerance / pxPerPt
                )
            }

            override fun charBox(index: Int): FloatArray =
                PdfPageGeometry.userRectToCanvas(
                    document.charBox(pdfPageIndex, index), pageH, currentPxPerPt()
                )

            override fun selectionRects(start: Int, count: Int): List<FloatArray> {
                val pxPerPt = currentPxPerPt()
                return document.selectionRects(pdfPageIndex, start, count)
                    .map { PdfPageGeometry.userRectToCanvas(it, pageH, pxPerPt) }
            }

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

    // Shared single-page overlay + margin-ink + floating-layers reload, guarded by
    // monotonic [singlePageOverlayLoadSeq] so an older async load never overwrites a newer page.
    suspend fun reloadSinglePageOverlaysAndLayers(
        canvas: DrawingCanvasView,
        pageIndex: Int,
        vectorPage: PageEntity?,
        seq: Int
    ) {
        if (singlePageOverlayLoadSeq.get() != seq || isContinuous || canvasViewRef !== canvas) return
        val targetW = canvas.getBackingBitmap()?.width ?: 2160
        val targetH = canvas.getBackingBitmap()?.height ?: 3056
        val overlay = if (PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay(
                note?.pageType, vectorPage != null
            )
        ) {
            inkController.composeAllLayersForSinglePage(
                pageIndex = pageIndex,
                targetWidth = targetW,
                targetHeight = targetH,
                page = vectorPage
            )
        } else null
        if (singlePageOverlayLoadSeq.get() != seq ||
            isContinuous ||
            canvasViewRef !== canvas ||
            canvas.pageIndex != pageIndex
        ) {
            overlay?.recycle()
            return
        }
        canvas.annotationOverlay = overlay
        canvas.baseLayerVisible = if (vectorPage != null) {
            inkController.layerStack(pageIndex)
                .layerAt(PageLayerStack.BASE_LAYER_INDEX)?.visible != false
        } else {
            true
        }
        val countAtStart = canvas.beyondPageInkCount()
        val crops = withContext(Dispatchers.IO) {
            canvas.readBeyondPageInk(viewModel.fileStore, viewModel.noteId, pageIndex)
        }
        if (singlePageOverlayLoadSeq.get() != seq ||
            isContinuous ||
            canvasViewRef !== canvas ||
            canvas.pageIndex != pageIndex
        ) {
            for (crop in crops) {
                if (!crop.bitmap.isRecycled) crop.bitmap.recycle()
            }
            return
        }
        canvas.applyBeyondPageInk(crops, forPageIndex = pageIndex, expectedCount = countAtStart)
        try {
            // Layer PNG decode is file I/O: read on Dispatchers.IO, apply on
            // main. A page without a manifest has no layers, so the apply
            // clears the previous page's list instead of leaking it.
            canvas.reloadLayersFromDisk(viewModel.fileStore, viewModel.noteId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Shared page apply for every navigation callback: stroke layer of the
    // loaded page + (for vector PDF pages) the live vector background + continuous overlays.
    fun applyLoadedPage(pageIndex: Int, bitmap: Bitmap?) {
        val canvas = canvasViewRef ?: return
        if (isContinuous || pageIndex != viewModel.currentPageIndex.value) return
        val seq = singlePageOverlayLoadSeq.incrementAndGet()
        viewModel.setCanvasPageIndex(pageIndex)
        canvas.pageIndex = pageIndex
        selectedImageIndex = -1
        canvas.selectedImageIndex = -1
        engineLayerPage = -1
        canvas.backgroundBitmap = null
        canvas.annotationOverlay = null
        val page = vectorPdfPage(pageIndex)
        if (page != null) {
            viewModel.setPageLoadedFromDisk(true)
            applyPdfBackground(canvas, page)
            canvas.setPageContent(bitmap)
        } else {
            canvas.setPdfBackground(0f, 0f, 0f, null)
            canvas.setPdfTextSource(null)
            pdfTextAvailable = false
            exitPdfTextMode()
            canvas.setPageContent(bitmap)
            viewModel.setPageLoadedFromDisk(bitmap != null)
        }
        singlePageAppliedPageIndex = pageIndex
        singlePageApplyRevision++
        scope.launch {
            reloadSinglePageOverlaysAndLayers(canvas, pageIndex, page, seq)
        }
    }

    /**
     * Continuous reading is on for every note type now (the infinite canvas
     * included), so this is the single definition the screen uses.
     */
    val continuousMode = isContinuous

    // PDF-03: probe text availability in Continuous mode (where applyPdfBackground is skipped)
    LaunchedEffect(currentPageIndex, isContinuous, pages) {
        if (!isContinuous) return@LaunchedEffect
        val vp = vectorPdfPage(currentPageIndex)
        if (vp == null) {
            pdfTextAvailable = false
            return@LaunchedEffect
        }
        val count = withContext(Dispatchers.IO) {
            try {
                pdfDocCache.document(vp.pdfSourcePath)?.textCharCount(vp.pdfPageIndex) ?: 0
            } catch (_: Exception) {
                0
            }
        }
        pdfTextAvailable = PdfTextToolPolicy.isAvailable(true, count)
    }

    // PDF-03: sync pdfTextSelectionMode after applyPdfBackground installs PdfTextSource,
    // and exit pdfTextMode when readingMode or continuous mode is entered without pending switch.
    LaunchedEffect(pdfTextMode, singlePageApplyRevision, isContinuous, uiState.readingMode) {
        if (!isContinuous) {
            pendingSwitchForPdfText = false
            if (PdfTextToolPolicy.shouldExitPdfTextMode(
                    pdfTextMode = pdfTextMode,
                    readingMode = uiState.readingMode,
                    isContinuous = false,
                    switchingToSinglePage = false
                )
            ) {
                exitPdfTextMode()
            } else {
                canvasViewRef?.setPdfTextSelectionMode(pdfTextMode)
            }
        } else if (PdfTextToolPolicy.shouldExitPdfTextMode(
                pdfTextMode = pdfTextMode,
                readingMode = uiState.readingMode,
                isContinuous = true,
                switchingToSinglePage = pendingSwitchForPdfText
            )
        ) {
            exitPdfTextMode()
        }
    }

    LaunchedEffect(uiState.selectedTool) {
        exitPdfTextMode()
        if (uiState.selectedTool != CanvasTool.LASSO && uiState.selectedTool != CanvasTool.SELECT) {
            inkController.lassoClear()
        }
    }

    LaunchedEffect(isContinuous, uiState.readingMode) {
        inkController.lassoClear()
    }

    // PDF-05: flush dirty continuous session or single-page canvas before drawer thumbnails render.
    LaunchedEffect(uiState.isThumbnailDrawerOpen) {
        if (!uiState.isThumbnailDrawerOpen) return@LaunchedEffect
        if (isContinuous) {
            inkController.flushCurrentSuspend()
        } else {
            val canvas = canvasViewRef
            if (canvas != null) {
                canvas.getBackingBitmap()?.let { bmp ->
                    viewModel.saveCurrentPageBitmapDirect(bmp)
                }
                if (canvas.layersDirty) {
                    canvas.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                }
            }
        }
    }


    // Load/save layers on page change. The INITIAL page/layer load lives in
    // the AndroidView factory below, bound to the view instance it creates:
    // a LaunchedEffect-only loader can silently no-op on an reopen composition
    // (its canvasViewRef-guarded apply racing/cancelled in the entry's frame
    // burst — on-device the view then stayed blank at the inited Backing size),
    // while the factory's own coroutine always reaches its own view. This
    // effect therefore skips its first (initial-composition) run — page 0 is
    // applied exactly once — and only handles real page flips.
    LaunchedEffect(currentPageIndex) {
        selectedImageIndex = -1
        canvasViewRef?.selectedImageIndex = -1
        inkController.clearLassoIfNotOnPage(currentPageIndex)
    }

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
        if (continuousReading) {
            singlePageOverlayLoadSeq.incrementAndGet()
            singlePageAppliedPageIndex = -1
            return@LaunchedEffect
        }

        // When leaving continuous mode to single-page mode, flush and release
        // any active continuous session first so the latest strokes are on disk
        // before loadPageStrokes / loadCurrentPageBitmap reads them.
        val seq = singlePageOverlayLoadSeq.incrementAndGet()
        inkController.releaseSessionSuspend()
        if (singlePageOverlayLoadSeq.get() != seq || continuousMode || viewModel.currentPageIndex.value != currentPageIndex) {
            return@LaunchedEffect
        }

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
            if (singlePageOverlayLoadSeq.get() != seq || continuousMode || viewModel.currentPageIndex.value != currentPageIndex) {
                return@LaunchedEffect
            }
            val strokes = withContext(Dispatchers.IO) { viewModel.loadPageStrokes(currentPageIndex) }
            if (singlePageOverlayLoadSeq.get() != seq || continuousMode || viewModel.currentPageIndex.value != currentPageIndex) {
                return@LaunchedEffect
            }
            val canvas = canvasViewRef
            if (canvas != null) {
                canvas.backgroundBitmap = null
                canvas.annotationOverlay = null
                viewModel.setPageLoadedFromDisk(true)
                applyPdfBackground(canvas, vectorPage)
                canvas.setPageContent(strokes)
                singlePageAppliedPageIndex = currentPageIndex
                singlePageApplyRevision++
                reloadSinglePageOverlaysAndLayers(canvas, currentPageIndex, vectorPage, seq)
            }
        } else {
            val savedBitmap = viewModel.loadCurrentPageBitmap()
            if (singlePageOverlayLoadSeq.get() != seq || continuousMode || viewModel.currentPageIndex.value != currentPageIndex) {
                return@LaunchedEffect
            }
            val canvas = canvasViewRef
            if (canvas != null) {
                canvas.setPdfBackground(0f, 0f, 0f, null)
                canvas.setPdfTextSource(null)
                pdfTextAvailable = false
                exitPdfTextMode()
                canvas.setPageContent(savedBitmap)
                canvas.backgroundBitmap = null
                viewModel.setPageLoadedFromDisk(savedBitmap != null)
                singlePageAppliedPageIndex = currentPageIndex
                singlePageApplyRevision++
                reloadSinglePageOverlaysAndLayers(canvas, currentPageIndex, null, seq)
            } else {
                viewModel.setPageLoadedFromDisk(savedBitmap != null)
            }
        }
    }

    // Pen barrel buttons (see PenButtonBus): 1 = cycle non-eraser tools, 2 = toggle eraser.
    // Ownership-checked unregister prevents multi-tab switches from clearing the active tab's bus.
    androidx.compose.runtime.DisposableEffect(viewModel) {
        personal.notes.touch.PenButtonBus.register(
            owner = viewModel,
            primary = { viewModel.cyclePreviousTool() },
            secondary = { viewModel.selectEraserTool() }
        )
        onDispose {
            personal.notes.touch.PenButtonBus.unregister(viewModel)
        }
    }

    var preferredSelectTool by remember { mutableStateOf(CanvasTool.LASSO) }
    LaunchedEffect(uiState.selectedTool) {
        if (uiState.selectedTool == CanvasTool.LASSO || uiState.selectedTool == CanvasTool.SELECT) {
            preferredSelectTool = uiState.selectedTool
        }
    }

    val activatePdfTextSelect: () -> Unit = {
        when (PdfTextToolPolicy.selectPdfTextAction(pdfTextAvailable, isContinuous)) {
            PdfTextSelectActivation.UNAVAILABLE -> Unit
            PdfTextSelectActivation.ACTIVATE_IN_SINGLE_PAGE -> {
                isLiveTextActive = false
                pdfTextMode = true
                canvasViewRef?.setPdfTextSelectionMode(true)
            }
            PdfTextSelectActivation.SWITCH_TO_SINGLE_AND_ACTIVATE -> {
                isLiveTextActive = false
                scope.launch {
                    inkController.flushCurrentSuspend()
                    pendingSwitchForPdfText = true
                    pdfTextMode = true
                    viewModel.setPageTurningModeTransient(1)
                }
            }
        }
    }

    val selectLassoTool: () -> Unit = {
        exitPdfTextMode()
        viewModel.selectTool(CanvasTool.LASSO)
    }

    val selectArrowTool: () -> Unit = {
        exitPdfTextMode()
        viewModel.selectTool(CanvasTool.SELECT)
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
                val vp = vectorPdfPage(page)
                val (fallbackW, fallbackH) = if (vp != null) {
                    val aspect = withContext(Dispatchers.IO) {
                        PageAspectCache.default.getOrLoad(
                            vp.pdfSourcePath,
                            vp.pdfPageIndex,
                            fallback = PdfPageGeometry.DEFAULT_ASPECT
                        ) {
                            pdfDocCache.document(vp.pdfSourcePath)?.pageSize(vp.pdfPageIndex)
                        }
                    }
                    PdfModeReconciliationPolicy.pagePixelSize(aspect)
                } else {
                    2160 to 3056
                }
                val blank = withContext(Dispatchers.IO) {
                    personal.notes.touch.util.BitmapUtils.createSafeBitmap(fallbackW, fallbackH)
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
            if (isInfiniteNote) {
                scope.launch {
                    val targetPageIdx = viewModel.currentPageIndex.value
                    val targetLayerIdx = inkController.layerStack(targetPageIdx).current?.index ?: 1
                    val placed = withContext(Dispatchers.IO) {
                        val fileName = viewModel.fileStore.saveNoteImage(viewModel.noteId, it) ?: return@withContext null
                        val file = viewModel.fileStore.getNoteAsset(viewModel.noteId, fileName) ?: return@withContext null
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                        val rawW = opts.outWidth.coerceAtLeast(1).toFloat()
                        val rawH = opts.outHeight.coerceAtLeast(1).toFloat()
                        val maxWorld = 1200f
                        val ratio = maxWorld / maxOf(rawW, rawH)
                        val w = rawW * ratio
                        val h = rawH * ratio
                        val cx = (-continuousPanX + screenWidthPx / 2f) / continuousScale.coerceAtLeast(0.1f)
                        val cy = (-continuousPanY + screenHeightPx / 2f) / continuousScale.coerceAtLeast(0.1f)
                        InfiniteCanvasItem.ImageItem(
                            id = java.util.UUID.randomUUID().toString(),
                            fileName = fileName,
                            centerX = cx,
                            centerY = cy,
                            width = w,
                            height = h,
                            pageIndex = targetPageIdx,
                            layerIndex = targetLayerIdx
                        )
                    }
                    if (placed != null) {
                        val updated = infiniteItems + placed
                        infiniteItems = updated
                        selectedInfiniteItemId = placed.id
                        withContext(Dispatchers.IO) {
                            InfiniteItemsStore.save(viewModel.fileStore, viewModel.noteId, updated)
                            inkController.generateAndSavePagePreview(targetPageIdx)
                            viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                        }
                        savedPageGenerations[targetPageIdx] = (savedPageGenerations[targetPageIdx] ?: 0) + 1
                    } else {
                        android.widget.Toast.makeText(
                            context, "Could not load image", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return@let
            }
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

    val importPdfToInfiniteCanvas: (Uri, Int) -> Unit = { sourceUri, requestedCols ->
        scope.launch {
            val targetPageIdx = viewModel.currentPageIndex.value
            val targetLayerIdx = inkController.layerStack(targetPageIdx).current?.index ?: 1
            val newPages = withContext(Dispatchers.IO) {
                val pdfFileName = InfiniteItemsStore.copyPdfToNoteDir(
                    context, viewModel.fileStore, viewModel.noteId, sourceUri
                ) ?: return@withContext emptyList<InfiniteCanvasItem.PdfPageItem>()
                val pdfFile = viewModel.fileStore.getNoteAsset(viewModel.noteId, pdfFileName)
                    ?: return@withContext emptyList<InfiniteCanvasItem.PdfPageItem>()
                val doc = personal.notes.touch.util.PdfiumDocument.open(pdfFile)
                if (doc == null) {
                    pdfFile.delete()
                    return@withContext emptyList<InfiniteCanvasItem.PdfPageItem>()
                }
                try {
                    val count = doc.pageCount
                    if (count <= 0) {
                        pdfFile.delete()
                        return@withContext emptyList<InfiniteCanvasItem.PdfPageItem>()
                    }
                    val cols = minOf(count, requestedCols.coerceIn(1, 4)).coerceAtLeast(1)
                    val cellW = 1440f
                    val gap = 96f
                    val originX = (-continuousPanX + screenWidthPx / 2f) / continuousScale.coerceAtLeast(0.1f)
                    val originY = (-continuousPanY + screenHeightPx / 2f) / continuousScale.coerceAtLeast(0.1f)
                    val startX = originX - ((cols - 1) * (cellW + gap)) / 2f
                    var rowY = originY
                    var rowMaxH = 0f
                    val list = mutableListOf<InfiniteCanvasItem.PdfPageItem>()
                    for (p in 0 until count) {
                        val sz = doc.pageSize(p)
                        val ptsW = if (sz[0] > 0f) sz[0] else 595f
                        val ptsH = if (sz[1] > 0f) sz[1] else 842f
                        val w = cellW
                        val h = (cellW * (ptsH / ptsW)).coerceIn(360f, 4500f)
                        val col = p % cols
                        if (col == 0 && p > 0) {
                            rowY += rowMaxH + gap
                            rowMaxH = 0f
                        }
                        if (h > rowMaxH) rowMaxH = h
                        val cx = startX + col * (cellW + gap)
                        val cy = rowY
                        list.add(
                            InfiniteCanvasItem.PdfPageItem(
                                id = java.util.UUID.randomUUID().toString(),
                                pdfFileName = pdfFileName,
                                pdfPageIndex = p,
                                centerX = cx,
                                centerY = cy,
                                width = w,
                                height = h,
                                pageIndex = targetPageIdx,
                                layerIndex = targetLayerIdx
                            )
                        )
                    }
                    list
                } finally {
                    doc.close()
                }
            }
            if (newPages.isNotEmpty()) {
                val updated = infiniteItems + newPages
                infiniteItems = updated
                selectedInfiniteItemId = newPages.first().id
                withContext(Dispatchers.IO) {
                    InfiniteItemsStore.save(viewModel.fileStore, viewModel.noteId, updated)
                    inkController.generateAndSavePagePreview(targetPageIdx)
                    viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                }
                savedPageGenerations[targetPageIdx] = (savedPageGenerations[targetPageIdx] ?: 0) + 1
                android.widget.Toast.makeText(
                    context,
                    "Placed ${newPages.size} PDF pages in ${requestedCols.coerceIn(1, 4)} column(s)",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    context, "Could not open PDF", android.widget.Toast.LENGTH_SHORT
                ).show()
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
        val asBackground = pdfImportAsBackground
        pdfImportAsBackground = false
        if (uri == null) return@rememberLauncherForActivityResult
        uri.let {
            if (isInfiniteNote) {
                pendingInfinitePdfUri = it
                showInfinitePdfColumnPicker = true
                return@let
            }
            if (asBackground) {
                // "Set page background": the picked PDF is not imported, the
                // user chooses one of its pages for the current page.
                scope.launch {
                    val pageCount = try {
                        withContext(Dispatchers.IO) {
                            PdfVectorImporter.readDocument(context, it)?.use { imported ->
                                imported.pageCount
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    if (pageCount == null || pageCount <= 0) {
                        android.widget.Toast.makeText(context, "Could not open PDF", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    pendingPdfUri = it
                    pendingPdfPageCount = pageCount
                    pendingPdfPageIndex = "1"
                    showPdfPagePicker = true
                }
                return@let
            }
            val cancelFlag = AtomicBoolean(false)
            importCancelFlag = cancelFlag
            importProgressFlow.value = PdfImportProgressState(
                active = true,
                done = 0,
                total = 0,
                cancelling = false
            )
            scope.launch {
                val noteId = viewModel.noteId
                val destFile = File(
                    viewModel.fileStore.getNoteDir(noteId),
                    "pdf_${System.currentTimeMillis()}.pdf"
                )
                var imported: PdfVectorImporter.ImportedPdf? = null
                var importCommitted = false
                try {
                    imported = try {
                        withContext(Dispatchers.IO) {
                            PdfVectorImporter.readDocument(
                                context = context,
                                uri = it,
                                destFile = destFile,
                                isCancelled = { cancelFlag.get() }
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                        null
                    }
                    if (cancelFlag.get()) {
                        return@launch
                    }
                    if (imported == null) {
                        android.widget.Toast.makeText(context, "Could not open PDF", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    importProgressFlow.value = PdfImportProgressState(
                        active = true,
                        done = 0,
                        total = imported.pageCount,
                        cancelling = cancelFlag.get()
                    )
                    // Per-page render error toast: Toast.show() must run on a
                    // thread with a Looper, so bounce back to Main.
                    suspend fun showPageError(msg: String) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    val result = withContext(Dispatchers.IO) {
                        PdfVectorImporter.importIntoNote(
                            context = context,
                            imported = imported,
                            noteId = noteId,
                            repository = viewModel.repository,
                            fileStore = viewModel.fileStore,
                            targetWidth = 2160,
                            targetHeight = if ((note?.pageType ?: "infinite").let { it == "a4" || it == "pdf" }) 3056 else 3840,
                            reuseFirstPage = false,
                            onPageError = { msg -> showPageError(msg) },
                            onProgress = { done, total ->
                                importProgressFlow.value = PdfImportProgressState(
                                    active = true,
                                    done = done,
                                    total = total,
                                    cancelling = cancelFlag.get()
                                )
                            },
                            isCancelled = { cancelFlag.get() }
                        )
                    }
                    if (result.cancelled || result.importedPages <= 0) {
                        result.firstPageBitmap?.let { bmp -> if (!bmp.isRecycled) bmp.recycle() }
                        return@launch
                    }
                    importCommitted = true
                    viewModel.reloadDrawerData()
                    val canvas = canvasViewRef
                    val curIdx = viewModel.currentPageIndex.value
                    val curPage = vectorPdfPage(curIdx)
                    if (curPage != null) preloadPdfSource(curPage.pdfSourcePath)
                    if (canvas != null && curPage != null) {
                        result.firstPageBitmap?.let { bmp -> if (!bmp.isRecycled) bmp.recycle() }
                        applyPdfBackground(canvas, curPage)
                    } else if (result.firstPageBitmap != null) {
                        canvas?.backgroundBitmap = result.firstPageBitmap
                    }
                    android.widget.Toast.makeText(
                        context,
                        "Imported ${result.importedPages} PDF page(s)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "PDF import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    imported?.close()
                    if (!importCommitted && destFile.exists()) {
                        withContext(NonCancellable + Dispatchers.IO) {
                            if (destFile.exists()) destFile.delete()
                        }
                    }
                    importCancelFlag = null
                    importProgressFlow.value = PdfImportProgressState()
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
                viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
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


    suspend fun animateScrollToContinuousPage(
        targetIndex: Int,
        horizontal: Boolean? = null,
        doublePage: Boolean? = null
    ): Int {
        val safeIndex = targetIndex.coerceAtLeast(0)
        if (safeIndex >= pages.size) {
            // Yield for Room emission & Compose layout of newly added page
            kotlinx.coroutines.delay(60)
        }
        val resolvedHorizontal = horizontal ?: (uiState.pageTurningDirection == 1)
        val resolvedDoublePage = doublePage ?: uiState.doublePageMode
        val maxIdx = (pages.size - 1).coerceAtLeast(0)
        val finalIndex = safeIndex.coerceIn(0, maxIdx)
        if (resolvedHorizontal) {
            continuousRowState.animateScrollToItem(finalIndex)
        } else if (resolvedDoublePage) {
            continuousColState.animateScrollToItem(finalIndex / 2)
        } else {
            continuousColState.animateScrollToItem(finalIndex)
        }
        val firstVis = if (resolvedHorizontal) {
            continuousRowState.firstVisibleItemIndex
        } else {
            continuousColState.firstVisibleItemIndex
        }
        settledProgrammaticFirstVis = firstVis
        settledProgrammaticPage = finalIndex
        continuousVisibleIndex = firstVis
        return finalIndex
    }

    val scrollToContinuousPage: (Int) -> Unit = { targetIndex ->
        val token = ++scrubJumpToken
        isScrubSettling = true
        scrubJumpJob?.cancel()
        scrubJumpJob = scope.launch {
            try {
                val finalIndex = animateScrollToContinuousPage(targetIndex)
                viewModel.setCurrentPageIndex(finalIndex)
            } finally {
                if (scrubJumpToken == token) {
                    isScrubSettling = false
                }
            }
        }
    }

    val hasPdfPages = remember(note?.pageType, pages) {
        note?.pageType == "pdf" || pages.any { it.pdfSourcePath.isNotEmpty() && it.pdfPageIndex >= 0 }
    }
    val pdfPagesSignature = remember(pages) {
        pages.map { Triple(it.index, it.pdfSourcePath, it.pdfPageIndex) }
    }
    LaunchedEffect(pdfPagesSignature) {
        pdfIndexData = null
    }
    LaunchedEffect(showSearchSheet, pdfPagesSignature) {
        if (!showSearchSheet || pdfIndexData != null) return@LaunchedEffect
        val descriptors = pages.map { p ->
            PdfTextIndex.PdfPageDescriptor(
                notePageIndex = p.index,
                pdfSourcePath = p.pdfSourcePath,
                pdfPageIndex = p.pdfPageIndex
            )
        }
        val pdfOnlyCount = descriptors.count { it.pdfSourcePath.isNotEmpty() && it.pdfPageIndex >= 0 }
        if (pdfOnlyCount == 0) return@LaunchedEffect
        isIndexingPdf = true
        pdfIndexDone = 0
        pdfIndexTotal = pdfOnlyCount
        try {
            val built = withContext(Dispatchers.IO) {
                var openPath: String? = null
                var openDoc: PdfiumDocument? = null
                try {
                    val coroutineCtx = currentCoroutineContext()
                    PdfTextIndex.buildNoteIndex(
                        pages = descriptors,
                        cacheFile = viewModel.fileStore.getPdfTextIndexFile(viewModel.noteId),
                        sourceMetaProvider = { sourcePath ->
                            val f = viewModel.fileStore.resolveRelativeFile(sourcePath)
                            if (f.isFile) {
                                PdfTextIndex.PdfSourceMeta(sourcePath, f.length(), f.lastModified())
                            } else {
                                null
                            }
                        },
                        pageTextProvider = { sourcePath, pdfPageIdx ->
                            if (sourcePath != openPath) {
                                openDoc?.close()
                                val f = viewModel.fileStore.resolveRelativeFile(sourcePath)
                                openDoc = if (f.isFile) PdfiumDocument.open(f) else null
                                openPath = sourcePath
                            }
                            openDoc?.text(pdfPageIdx).orEmpty()
                        },
                        onProgress = { done, total ->
                            pdfIndexDone = done
                            pdfIndexTotal = total
                        },
                        isCancelled = { !coroutineCtx.isActive || !showSearchSheet }
                    )
                } finally {
                    openDoc?.close()
                }
            }
            if (built != null) {
                pdfIndexData = built
            }
        } finally {
            isIndexingPdf = false
        }
    }

    val searchHits = remember(pdfIndexData, searchQuery) {
        val idx = pdfIndexData
        if (idx == null || searchQuery.isBlank()) emptyList()
        else PdfTextIndex.search(idx, searchQuery)
    }
    LaunchedEffect(searchHits) {
        activeSearchHitIndex = if (searchHits.isNotEmpty()) 0 else -1
    }

    val jumpToPageFromUi: (Int) -> Unit = { targetPage ->
        val clamped = targetPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
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
    }

    val navigateToSearchHit: (Int, PdfTextIndex.PdfSearchHit) -> Unit = { idx, hit ->
        activeSearchHitIndex = idx
        searchHighlightRevision++
        searchHighlightTarget = SearchHighlightTarget(
            notePageIndex = hit.pageIndex,
            startOffset = hit.startOffset,
            length = hit.length,
            revision = searchHighlightRevision
        )
        jumpToPageFromUi(hit.pageIndex)
    }

    LaunchedEffect(searchHighlightTarget, currentPageIndex, isContinuous, pages, singlePageApplyRevision) {
        val canvas = canvasViewRef ?: return@LaunchedEffect
        val target = searchHighlightTarget
        if (isContinuous || target == null || target.notePageIndex != currentPageIndex) {
            canvas.setSearchHighlightRects(emptyList())
            return@LaunchedEffect
        }
        val page = vectorPdfPage(currentPageIndex)
        if (page == null) {
            canvas.setSearchHighlightRects(emptyList())
            return@LaunchedEffect
        }
        if (canvas.pageIndex != page.index || singlePageAppliedPageIndex != page.index) {
            canvas.setSearchHighlightRects(emptyList())
            return@LaunchedEffect
        }
        val rects = withContext(Dispatchers.IO) {
            val doc = pdfDocCache.document(page.pdfSourcePath) ?: return@withContext emptyList()
            val size = doc.pageSize(page.pdfPageIndex)
            val pageW = size[0]
            val pageH = size[1]
            if (pageW <= 0f || pageH <= 0f) return@withContext emptyList()
            val canvasW = canvas.pageCanvasWidth().takeIf { it > 0f } ?: 2160f
            val pxPerPt = canvasW / pageW
            doc.selectionRects(page.pdfPageIndex, target.startOffset, target.length)
                .map { PdfPageGeometry.userRectToCanvas(it, pageH, pxPerPt) }
        }
        if (searchHighlightTarget?.revision == target.revision &&
            !isContinuous &&
            viewModel.currentPageIndex.value == target.notePageIndex
        ) {
            canvas.setSearchHighlightRects(rects)
        }
    }

    LaunchedEffect(showSearchSheet, searchHighlightTarget?.revision) {
        if (!showSearchSheet && searchHighlightTarget != null) {
            kotlinx.coroutines.delay(5000L)
            searchHighlightTarget = null
            canvasViewRef?.setSearchHighlightRects(emptyList())
        }
    }

    LaunchedEffect(inkController.isDrawing) {
        if (inkController.isDrawing && searchHighlightTarget != null) {
            searchHighlightTarget = null
            canvasViewRef?.setSearchHighlightRects(emptyList())
        }
    }

    androidx.activity.compose.BackHandler(onBack = {
        when {
            showSearchSheet -> {
                showSearchSheet = false
                searchHighlightTarget = null
                canvasViewRef?.setSearchHighlightRects(emptyList())
            }
            pdfTextMode -> exitPdfTextMode()
            uiState.isThumbnailDrawerOpen -> viewModel.toggleThumbnailDrawer(false)
            uiState.isToolsPanelOpen -> viewModel.toggleToolsPanel(false)
            showLayersPanel -> showLayersPanel = false
            showLayerPanel -> {
                showLayerPanel = false
                layerActionIndex = -1
            }
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
                        inkController.releaseSessionSuspend()
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

    // Dialog: Infinite Canvas PDF Column Arrangement Picker (1, 2, 3, or 4 columns)
    if (showInfinitePdfColumnPicker && pendingInfinitePdfUri != null) {
        AlertDialog(
            onDismissRequest = {
                showInfinitePdfColumnPicker = false
                pendingInfinitePdfUri = null
            },
            title = { Text("Import PDF to Canvas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose how many columns to arrange the PDF pages in:",
                        fontSize = 14.sp,
                        color = Color(0xFF475467)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (colOption in 1..4) {
                            val isSelected = selectedInfinitePdfColumns == colOption
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OrangePrimary.copy(alpha = 0.12f) else Color(0xFFF2F4F7),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) OrangePrimary else Color(0xFFD0D5DD)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedInfinitePdfColumns = colOption }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$colOption",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) OrangePrimary else Color(0xFF1D2939)
                                    )
                                    Text(
                                        text = if (colOption == 1) "Column" else "Columns",
                                        fontSize = 11.sp,
                                        color = if (isSelected) OrangePrimary else Color(0xFF667085)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetUri = pendingInfinitePdfUri
                        val cols = selectedInfinitePdfColumns.coerceIn(1, 4)
                        showInfinitePdfColumnPicker = false
                        pendingInfinitePdfUri = null
                        if (targetUri != null) {
                            importPdfToInfiniteCanvas(targetUri, cols)
                        }
                    }
                ) {
                    Text("Import", color = OrangePrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInfinitePdfColumnPicker = false
                        pendingInfinitePdfUri = null
                    }
                ) {
                    Text("Cancel")
                }
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
                                    PdfVectorImporter.readDocument(context, uri)?.use { imported ->
                                        imported.document.renderPage(
                                            pageIndex = idx,
                                            targetWidth = if (w > 0) w else 2160,
                                            targetHeight = if (h > 0) h else if ((note?.pageType ?: "infinite").let { it == "a4" || it == "pdf" }) 3056 else 3840
                                        )
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

    ImportProgressDialog(
        state = importProgressState,
        onCancel = {
            importCancelFlag?.set(true)
            importProgressFlow.value = importProgressFlow.value.copy(cancelling = true)
        }
    )

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
            includeMargins = uiState.exportIncludeMargins,
            onIncludeMarginsChange = { viewModel.setExportIncludeMargins(it) },
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

    // Dialog: Custom Color Picker (Go-to-page / Full-text search moved to PdfSearchSheet overlay)
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            // Zoom / Reset / Center live in the auto-fading top-center
                            // HUD pill across all note types and modes.

                            Spacer(modifier = Modifier.width(8.dp))

                            // Note Title Pill (Clickable -> Change note name)
                            //
                            // The pill may shrink: the row of the top bar
                            // has more buttons than fit on a narrow screen,
                            // and a long title must not push the button of
                            // the right panel out of sight.
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
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
                                        color = Color(0xFF202124),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                    inkController.releaseSessionSuspend()
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
                            IconButton(onClick = {
                                showSearchSheet = true
                                isSearchSheetExpanded = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search / Go to Page",
                                    tint = if (showSearchSheet) OrangePrimary else Color(0xFF555555)
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
                            IconButton(onClick = {
                                if (PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote) ==
                                    PdfModeReconciliationPolicy.LayerEntryTarget.INFINITE_DRAWING_ONLY
                                ) {
                                    showLayerPanel = !showLayerPanel
                                    showLayersPanel = false
                                } else {
                                    showLayersPanel = !showLayersPanel
                                    showLayerPanel = false
                                }
                                layerActionIndex = -1
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Layers",
                                    tint = if (showLayersPanel || showLayerPanel) OrangePrimary else Color(0xFF555555)
                                )
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
                 * Zoom / pan of the reader.
                 *
                 * The gesture is one event of the pinch: the middle of the
                 * fingers, how far they moved and how much they spread. The
                 * maths is in [ReaderTransform] - the page under the fingers
                 * stays under the fingers, and the list is told how far it
                 * has to scroll.
                 *
                 * The page can be pulled aside at **every** zoom level - at
                 * the original size too (the page is then moved out of the
                 * way to write in the margin of a PDF, and the endless canvas
                 * is moved around).
                 */
                val handleReaderTransform: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Offset, Float) -> Unit =
                    { centroid, pan, zoom ->
                        if (isInfiniteNote) {
                            if (zoom.isFinite() && pan.x.isFinite() && pan.y.isFinite() &&
                                centroid.x.isFinite() && centroid.y.isFinite()
                            ) {
                                val oldScale = continuousScale.coerceIn(0.1f, 10.0f)
                                val newScale = if (uiState.zoomLocked) {
                                    oldScale
                                } else {
                                    (oldScale * zoom).coerceIn(0.1f, 10.0f)
                                }
                                val factor = newScale / oldScale
                                continuousScale = newScale
                                continuousPanX = (centroid.x - (centroid.x - continuousPanX) * factor + pan.x)
                                    .coerceIn(-1_000_000f, 1_000_000f)
                                continuousPanY = (centroid.y - (centroid.y - continuousPanY) * factor + pan.y)
                                    .coerceIn(-1_000_000f, 1_000_000f)
                                if (kotlin.math.abs(zoom - 1f) > 0.0005f) {
                                    showZoomHud = true
                                    zoomHudActivityTick++
                                }
                            }
                        } else {
                            val listState = if (isHorizontal) continuousRowState else continuousColState
                            val layout = readerLayout(
                                listState = listState,
                                centroid = centroid,
                                horizontal = isHorizontal,
                                doublePage = uiState.doublePageMode,
                                scale = continuousScale,
                                screenWidthPx = screenWidthPx,
                                screenHeightPx = screenHeightPx,
                                pageSpacingPx = with(density) { 16.dp.toPx() },
                                crossPan = if (isHorizontal) continuousPanY else continuousPanX
                            )
                            val state = ReaderTransform.State(
                                scale = continuousScale,
                                panX = continuousPanX,
                                panY = continuousPanY
                            )
                            val gesture = ReaderTransform.Gesture(
                                centroidX = centroid.x,
                                centroidY = centroid.y,
                                panX = pan.x,
                                panY = pan.y,
                                zoom = zoom
                            )
                            val result = ReaderTransform.apply(
                                state = state,
                                gesture = gesture,
                                layout = layout,
                                zoomLocked = uiState.zoomLocked
                            )
                            // The list scrolls first: the pages have to move
                            // before they are measured bigger. Any scroll delta not
                            // consumed by the list (e.g. single-page A4 notes or boundaries)
                            // translates into pan offset so the page can be panned in both axes.
                            val consumed = if (result.scrollDelta != 0f) {
                                listState.dispatchRawDelta(result.scrollDelta)
                            } else 0f
                            val unconsumed = result.scrollDelta - consumed
                            val pullLimit = ReaderTransform.pullLimit(
                                if (isHorizontal) screenHeightPx else screenWidthPx,
                                result.state.scale
                            )
                            continuousScale = result.state.scale
                            if (isHorizontal) {
                                continuousPanX = (result.state.panX - unconsumed).coerceIn(-pullLimit, pullLimit)
                                continuousPanY = result.state.panY
                            } else {
                                continuousPanX = result.state.panX
                                continuousPanY = (result.state.panY - unconsumed).coerceIn(-pullLimit, pullLimit)
                            }
                            showZoomHud = true
                            zoomHudActivityTick++
                            if (hasMarginGuides &&
                                (kotlin.math.abs(pan.x) > 0.5f ||
                                    kotlin.math.abs(continuousPanX) > 2f ||
                                    continuousScale < 0.99f)
                            ) {
                                marginLabelActive = true
                                marginLabelActivityTick++
                            }
                        }
                    }

                // Rendering key for the page bitmaps. Quantising the zoom keeps
                // a pinch gesture from launching a PDF render per frame while
                // still upgrading resolution as the user zooms in (P1 audit).
                // The buckets reach past the old limit of four times, because
                // the reader zooms in that far now (the render itself stops
                // at MAX_PAGE_RENDER_WIDTH_PX, so the memory stays the same).
                val continuousZoomBucket = when {
                    continuousScale < 1.25f -> 1f
                    continuousScale < 1.75f -> 1.5f
                    continuousScale < 2.5f -> 2f
                    continuousScale < 3.5f -> 3f
                    continuousScale < 5f -> 4f
                    else -> 6f
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
                    autoShapeEnabled = uiState.autoShapeEnabled,
                    opacity = uiState.strokeOpacity
                )


                LaunchedEffect(isContinuous, isHorizontal, uiState.doublePageMode) {
                    scrubJumpJob?.cancel()
                    scrubJumpJob = null
                    val token = ++scrubJumpToken
                    settledProgrammaticFirstVis = -1
                    settledProgrammaticPage = -1
                    if (isContinuous) {
                        continuousScale = 1f
                        continuousPanX = 0f
                        continuousPanY = 0f
                        // The canvas sits behind the reader, so drop its vector
                        // background (up to 72 MB) for the duration. Returning
                        // to single-page mode re-installs it via applyLoadedPage.
                        canvasViewRef?.backgroundBitmap = null
                        if (!isInfiniteNote && pages.isNotEmpty()) {
                            isScrubSettling = true
                            try {
                                val target = currentPageIndex.coerceIn(0, pages.size - 1)
                                val listState = if (isHorizontal) continuousRowState else continuousColState
                                val itemIdx = if (!isHorizontal && uiState.doublePageMode) target / 2 else target
                                val preLayoutInfo = listState.layoutInfo
                                listState.scrollToItem(itemIdx)
                                // Yield a frame so the newly mounted/reconfigured LazyList
                                // commits its layout & measure pass, then confirm layoutInfo
                                // replaced preLayoutInfo and matches expectedCount so
                                // bottom-of-list clamping is reflected in firstVisibleItemIndex.
                                withFrameNanos { }
                                snapshotFlow {
                                    val expectedCount = if (isHorizontal) {
                                        pages.size
                                    } else if (uiState.doublePageMode) {
                                        (pages.size + 1) / 2
                                    } else {
                                        pages.size
                                    }
                                    note?.pageType == "infinite" ||
                                        pages.isEmpty() ||
                                        (listState.layoutInfo !== preLayoutInfo &&
                                            listState.layoutInfo.visibleItemsInfo.isNotEmpty() &&
                                            listState.layoutInfo.totalItemsCount == expectedCount)
                                }.first { it }
                                if (note?.pageType != "infinite" && pages.isNotEmpty()) {
                                    val clampedTarget = target.coerceIn(0, pages.size - 1)
                                    val firstVis = listState.firstVisibleItemIndex
                                    settledProgrammaticFirstVis = firstVis
                                    settledProgrammaticPage = clampedTarget
                                    continuousVisibleIndex = firstVis
                                }
                            } finally {
                                if (scrubJumpToken == token) {
                                    isScrubSettling = false
                                }
                            }
                        } else {
                            isScrubSettling = false
                        }
                    } else {
                        isScrubSettling = false
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
                    restoredPageScrolled = true
                    if (currentPageIndex > 0) {
                        scrollToContinuousPage(currentPageIndex)
                    }
                }

                // The visible item index comes from a snapshotFlow: reading the
                // list state straight in composition would recompose the whole
                // screen on every scroll frame.
                LaunchedEffect(isContinuous, isHorizontal) {
                    if (!isContinuous) return@LaunchedEffect
                    snapshotFlow {
                        val state = if (isHorizontal) continuousRowState else continuousColState
                        state.firstVisibleItemIndex to state.isScrollInProgress
                    }.collect { (firstVis, scrollInProgress) ->
                        if (
                            settledProgrammaticFirstVis >= 0 &&
                            !isScrubbing &&
                            !isScrubSettling &&
                            (scrollInProgress || firstVis != settledProgrammaticFirstVis)
                        ) {
                            settledProgrammaticFirstVis = -1
                            settledProgrammaticPage = -1
                        }
                        continuousVisibleIndex = firstVis
                    }
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
                LaunchedEffect(isContinuous, currentContinuousVisible, isScrubbing, isScrubSettling) {
                    if (isContinuous && pages.isNotEmpty() && !isScrubbing && !isScrubSettling) {
                        val activeListState = if (uiState.pageTurningDirection == 1) continuousRowState else continuousColState
                        val isProgrammaticSettledTarget =
                            settledProgrammaticFirstVis >= 0 &&
                            settledProgrammaticPage in pages.indices &&
                            continuousVisibleIndex == settledProgrammaticFirstVis &&
                            viewModel.currentPageIndex.value == settledProgrammaticPage &&
                            !activeListState.isScrollInProgress
                        val curIdx = viewModel.currentPageIndex.value
                        val visibleItems = activeListState.layoutInfo.visibleItemsInfo
                        val currentStillVisibleOnScreen = visibleItems.isNotEmpty() && if (!isHorizontal && uiState.doublePageMode) {
                            val pairItemIdx = curIdx / 2
                            visibleItems.any { it.index == pairItemIdx }
                        } else {
                            visibleItems.any { it.index == curIdx }
                        }
                        if (!isProgrammaticSettledTarget && !currentStillVisibleOnScreen) {
                            val validIdx = currentContinuousVisible.coerceIn(0, pages.size - 1)
                            viewModel.setCurrentPageIndex(validIdx)
                        }
                    }
                }

                // Drawing Canvas (always mounted in composition)
                val currentIsContinuous by androidx.compose.runtime.rememberUpdatedState(isContinuous)
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
                            // Any touch on the page closes the open menus; only genuine ink/shape interaction clears search highlight.
                            onCanvasTouchStart = {
                                dismissCanvasOverlays()
                            }
                            onInkInteractionStart = {
                                if (searchHighlightTarget != null) {
                                    searchHighlightTarget = null
                                    setSearchHighlightRects(emptyList())
                                }
                            }
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
                                            inkController.releaseSessionSuspend()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        val cur = viewModel.currentPageIndex.value
                                        savedPageGenerations[cur] = (savedPageGenerations[cur] ?: 0) + 1
                                        viewModel.setPageTurningMode(0)
                                    }
                                }
                            }
                            onUndoRedoListener = { canUndo, canRedo ->
                                if (!currentIsContinuous) {
                                    viewModel.updateUndoRedo(canUndo, canRedo)
                                }
                            }
                            onViewportChanged = {
                                if (!currentIsContinuous) {
                                    singlePageScale = scale
                                    showZoomHud = true
                                    zoomHudActivityTick++
                                    glassViewportTick++
                                }
                            }
                            onImageLayersSaved = { savedPageIdx ->
                                viewModel.notifyPageModified(savedPageIdx)
                            }
                            onStrokeFinishedListener = {
                                glassViewportTick++
                                getBackingBitmap()?.let { bmp ->
                                    viewModel.scheduleDebouncedSave(bmp)
                                }
                                // Ink written outside the paper lives in its
                                // own files and has to be written with the
                                // stroke, not only when a layer changes.
                                val canvas = canvasViewRef
                                if (canvas != null && canvas.hasBeyondPageInk()) {
                                    val bpPage = canvas.pageIndex
                                    scope.launch(Dispatchers.IO) {
                                        canvas.saveBeyondPageInkNow(viewModel.fileStore, viewModel.noteId)
                                        viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                        viewModel.notifyPageModified(bpPage)
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
                                    val seq = singlePageOverlayLoadSeq.incrementAndGet()
                                    val pageIdx = viewModel.currentPageIndex.value
                                    viewModel.setCanvasPageIndex(pageIdx)
                                    pageIndex = pageIdx
                                    val vectorPage = vectorPdfPage(pageIdx)
                                    if (vectorPage != null) {
                                        preloadPdfSource(vectorPage.pdfSourcePath)
                                        if (singlePageOverlayLoadSeq.get() != seq) return@launch
                                        val strokes = withContext(Dispatchers.IO) {
                                            viewModel.loadPageStrokes(pageIdx)
                                        }
                                        if (singlePageOverlayLoadSeq.get() != seq) return@launch
                                        backgroundBitmap = null
                                        annotationOverlay = null
                                        viewModel.setPageLoadedFromDisk(true)
                                        applyPdfBackground(this@apply, vectorPage)
                                        setPageContent(strokes)
                                    } else {
                                        val savedBitmap = viewModel.loadCurrentPageBitmap()
                                        if (singlePageOverlayLoadSeq.get() != seq) return@launch
                                        setPageContent(savedBitmap)
                                        backgroundBitmap = null
                                        viewModel.setPageLoadedFromDisk(savedBitmap != null)
                                    }
                                    singlePageAppliedPageIndex = pageIdx
                                    singlePageApplyRevision++
                                    reloadSinglePageOverlaysAndLayers(this@apply, pageIdx, vectorPage, seq)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.pageType = note?.pageType ?: "infinite"
                        // The margin ink (the strokes outside the paper)
                        // belongs to the reader, where the page has a layer
                        // that reaches past it. In the single page editor the
                        // ink stays on the page.
                        view.beyondPaper = (note?.pageType == "infinite") && !uiState.fixPreviewPage
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

                val currentHandleReaderTransform by androidx.compose.runtime.rememberUpdatedState(handleReaderTransform)

                // Continuous page overlay when continuous mode is active
                if (isContinuous) {
                    val pageWidthDp = with(density) { (screenWidthPx * continuousScale).toDp() }
                    val pageHeightDp = with(density) { (screenHeightPx * continuousScale).toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                if (size.width > 0) readerViewportWidthPx = size.width.toFloat()
                            }
                            .onGloballyPositioned { canvasOriginInRoot = it.positionInRoot() }
                    ) {
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
                                    // arrives (never 1 palm + 1 stylus), so a
                                    // one finger scroll still scrolls the list.
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        var twoFingers = false
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val hasStylus = event.changes.any {
                                                it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                                            }
                                            val touchCount = event.changes.count {
                                                it.pressed && it.type == PointerType.Touch
                                            }
                                            if (!hasStylus && touchCount >= 2 && event.changes.count { it.pressed } >= 2) {
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
                                            if (event.changes.any {
                                                    it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                                                }
                                            ) {
                                                break
                                            }
                                            val pressed = event.changes.filter {
                                                it.pressed && it.type == PointerType.Touch
                                            }
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
                                                currentHandleReaderTransform(centroid, pan, zoom)
                                            }
                                        }
                                    }
                                }
                        ) {
                            if (isInfiniteNote) {
                                val activeInfPageIdx = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                                val page = pages.getOrNull(activeInfPageIdx) ?: pages.firstOrNull() ?: personal.notes.touch.data.db.entity.PageEntity(
                                    id = java.util.UUID.randomUUID().toString(),
                                    noteId = viewModel.noteId,
                                    index = activeInfPageIdx,
                                    bitmapPath = "",
                                    updatedAt = System.currentTimeMillis()
                                )
                                InfiniteCanvasView(
                                    page = page,
                                    pageIndex = activeInfPageIdx,
                                    noteId = viewModel.noteId,
                                    ink = inkController,
                                    inkSettings = inkSettings,
                                    scale = continuousScale,
                                    panX = continuousPanX,
                                    panY = continuousPanY,
                                    backgroundDensity = uiState.backgroundDensity,
                                    gridPreset = uiState.infiniteGridPreset,
                                    snapToGrid = uiState.gridSnapEnabled,
                                    strokeOpacity = uiState.strokeOpacity,
                                    items = infiniteItems,
                                    selectedItemId = selectedInfiniteItemId,
                                    bitmapCache = infiniteBitmapCache,
                                    onSelectItem = { selectedInfiniteItemId = it },
                                    onUpdateItem = { updated ->
                                        infiniteItems = infiniteItems.map { if (it.id == updated.id) updated else it }
                                    },
                                    onDuplicateItem = { copy ->
                                        val updated = infiniteItems + copy
                                        infiniteItems = updated
                                        selectedInfiniteItemId = copy.id
                                    },
                                    onCommitItems = {
                                        val snapshot = infiniteItems
                                        scope.launch(Dispatchers.IO) {
                                            InfiniteItemsStore.save(viewModel.fileStore, viewModel.noteId, snapshot)
                                            inkController.generateAndSavePagePreview(activeInfPageIdx)
                                            viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                        }
                                        savedPageGenerations[activeInfPageIdx] = (savedPageGenerations[activeInfPageIdx] ?: 0) + 1
                                    },
                                    onDeleteItem = { itemId ->
                                        val updated = infiniteItems.filterNot { it.id == itemId }
                                        infiniteItems = updated
                                        if (selectedInfiniteItemId == itemId) selectedInfiniteItemId = null
                                        scope.launch(Dispatchers.IO) {
                                            InfiniteItemsStore.save(viewModel.fileStore, viewModel.noteId, updated)
                                            inkController.generateAndSavePagePreview(activeInfPageIdx)
                                            viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                        }
                                        savedPageGenerations[activeInfPageIdx] = (savedPageGenerations[activeInfPageIdx] ?: 0) + 1
                                    },
                                    onResetTransform = {
                                        val center = inkController.computeArtworkCenter(activeInfPageIdx)
                                        continuousScale = 1.0f
                                        if (center != null) {
                                            continuousPanX = screenWidthPx / 2f - center.first
                                            continuousPanY = screenHeightPx / 2f - center.second
                                        } else {
                                            continuousPanX = 0f
                                            continuousPanY = 0f
                                        }
                                    },
                                    onCycleGridPreset = {
                                        viewModel.cycleInfiniteGridPreset()
                                    },
                                    onSnapToggle = {
                                        viewModel.toggleGridSnap()
                                    },
                                    onOpacityChange = { newAlpha ->
                                        viewModel.setStrokeOpacity(newAlpha)
                                    },
                                    onQuickClear = {
                                        inkController.clearCurrentLayer(activeInfPageIdx)
                                    },
                                    onStylusDetected = { viewModel.onStylusDetected() },
                                    onPanZoom = { dPanX, dPanY, dZoom, centroid ->
                                        if (!dPanX.isFinite() || !dPanY.isFinite() || !dZoom.isFinite() ||
                                            !centroid.x.isFinite() || !centroid.y.isFinite()
                                        ) {
                                            return@InfiniteCanvasView
                                        }
                                        val oldScale = continuousScale.coerceIn(0.1f, 10.0f)
                                        val newScale = if (uiState.zoomLocked) {
                                            oldScale
                                        } else {
                                            (oldScale * dZoom).coerceIn(0.1f, 10.0f)
                                        }
                                        val factor = newScale / oldScale
                                        continuousScale = newScale
                                        continuousPanX = (centroid.x - (centroid.x - continuousPanX) * factor + dPanX)
                                            .coerceIn(-1_000_000f, 1_000_000f)
                                        continuousPanY = (centroid.y - (centroid.y - continuousPanY) * factor + dPanY)
                                            .coerceIn(-1_000_000f, 1_000_000f)
                                        if (kotlin.math.abs(dZoom - 1f) > 0.0005f) {
                                            showZoomHud = true
                                            zoomHudActivityTick++
                                        }
                                    },
                                    onDismissOverlays = { dismissCanvasOverlays() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (isHorizontal) {
                                val horizontalPanReveal = MarginExportMath.hasHorizontalPanOrZoomReveal(
                                    continuousPanX = continuousPanX,
                                    continuousScale = continuousScale
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    state = continuousRowState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !inkController.isDrawing && !inkController.isManipulatingLasso,
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (isInfiniteNote) 0.dp else 16.dp),
                                    contentPadding = if (isInfiniteNote) androidx.compose.foundation.layout.PaddingValues(0.dp) else androidx.compose.foundation.layout.PaddingValues(16.dp)
                                ) {
                                    items(items = pages, key = { it.id }) { page ->
                                        val idx = pages.indexOf(page)
                                        Box(
                                            modifier = Modifier
                                                .requiredHeight(pageHeightDp)
                                                // An infinite page has no edge:
                                                // its box is the visible canvas,
                                                // not a rectangle with the
                                                // aspect ratio of the saved
                                                // bitmap.
                                                .then(if (isInfiniteNote) Modifier.requiredWidth(pageWidthDp) else Modifier)
                                                .offset { IntOffset(continuousPanX.roundToInt(), continuousPanY.roundToInt()) },
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
                                                onPageBox = { continuousPageMap.pageBox = it },
                                                zoom = continuousZoomBucket,
                                                ink = inkController,
                                                inkSettings = inkSettings,
                                                backgroundMode = uiState.backgroundMode,
                                                backgroundDensity = uiState.backgroundDensity,
                                                defaultPattern = uiState.paperPattern,
                                                savedGeneration = savedPageGenerations[idx] ?: 0,
                                                isCurrentPage = idx == currentPageIndex,
                                                isInfinite = isInfiniteNote,
                                                panOffsetX = continuousPanX,
                                                panOffsetY = continuousPanY,
                                                pageType = note?.pageType ?: "infinite",
                                                viewportWidthPx = readerViewportWidthPx,
                                                canvasOriginXProvider = { canvasOriginInRoot.x },
                                                marginLabelAlphaProvider = { marginLabelAlpha },
                                                allowLeftPanReveal = horizontalPanReveal,
                                                allowRightPanReveal = horizontalPanReveal,
                                                allowLeftMarginLabel = idx == 0 || (horizontalPanReveal && idx == currentPageIndex),
                                                allowRightMarginLabel = idx == pages.size - 1 || (horizontalPanReveal && idx == currentPageIndex),
                                                searchHighlightStart = if (searchHighlightTarget?.notePageIndex == idx) {
                                                    searchHighlightTarget?.startOffset ?: -1
                                                } else -1,
                                                searchHighlightLength = if (searchHighlightTarget?.notePageIndex == idx) {
                                                    searchHighlightTarget?.length ?: 0
                                                } else 0,
                                                searchHighlightRevision = searchHighlightTarget?.revision ?: 0,
                                                modifier = androidx.compose.ui.Modifier.then(
                                                    if (isInfiniteNote) {
                                                        Modifier.fillMaxSize()
                                                    } else {
                                                        Modifier.fillMaxHeight()
                                                    }
                                                ),
                                                onStylusDetected = { viewModel.onStylusDetected() },
                                                onPageBitmap = { idx, bmp ->
                                                    if (idx == currentPageIndex) {
                                                        glassReaderBitmap = bmp
                                                        glassReaderBitmapPage = idx
                                                    }
                                                },
                                                onActivatePage = { viewModel.setCurrentPageIndex(idx) },
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
                                    userScrollEnabled = !inkController.isDrawing && !inkController.isManipulatingLasso,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (isInfiniteNote) 0.dp else 16.dp),
                                    contentPadding = if (isInfiniteNote) androidx.compose.foundation.layout.PaddingValues(0.dp) else androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val pairs = (0 until pages.size).chunked(2)
                                    items(pairs.size) { pairIdx ->
                                        val pair = pairs[pairIdx]
                                        Row(
                                            modifier = Modifier
                                                .requiredWidth(pageWidthDp)
                                                // An infinite page has no edge:
                                                // its box is the visible canvas.
                                                .then(if (isInfiniteNote) Modifier.requiredHeight(pageHeightDp) else Modifier)
                                                .offset { IntOffset(continuousPanX.roundToInt(), continuousPanY.roundToInt()) },
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            for ((posInPair, idx) in pair.withIndex()) {
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
                                                        onPageBox = { continuousPageMap.pageBox = it },
                                                        zoom = continuousZoomBucket,
                                                        ink = inkController,
                                                        inkSettings = inkSettings,
                                                        backgroundMode = uiState.backgroundMode,
                                                        backgroundDensity = uiState.backgroundDensity,
                                                        defaultPattern = uiState.paperPattern,
                                                        savedGeneration = savedPageGenerations[idx] ?: 0,
                                                        isCurrentPage = idx == currentPageIndex,
                                                        isInfinite = isInfiniteNote,
                                                        panOffsetX = continuousPanX,
                                                        panOffsetY = continuousPanY,
                                                        pageType = note?.pageType ?: "infinite",
                                                        viewportWidthPx = readerViewportWidthPx,
                                                        canvasOriginXProvider = { canvasOriginInRoot.x },
                                                        marginLabelAlphaProvider = { marginLabelAlpha },
                                                        allowLeftPanReveal = posInPair == 0,
                                                        allowRightPanReveal = posInPair == 1 || pair.size == 1,
                                                        allowLeftMarginLabel = posInPair == 0,
                                                        allowRightMarginLabel = posInPair == 1 || pair.size == 1,
                                                        searchHighlightStart = if (searchHighlightTarget?.notePageIndex == idx) {
                                                            searchHighlightTarget?.startOffset ?: -1
                                                        } else -1,
                                                        searchHighlightLength = if (searchHighlightTarget?.notePageIndex == idx) {
                                                            searchHighlightTarget?.length ?: 0
                                                        } else 0,
                                                        searchHighlightRevision = searchHighlightTarget?.revision ?: 0,
                                                        modifier = androidx.compose.ui.Modifier.then(
                                                            if (isInfiniteNote) {
                                                                Modifier.fillMaxSize()
                                                            } else {
                                                                Modifier.fillMaxWidth()
                                                            }
                                                        ),
                                                        onStylusDetected = { viewModel.onStylusDetected() },
                                                        onPageBitmap = { idx, bmp ->
                                                            if (idx == currentPageIndex) {
                                                                glassReaderBitmap = bmp
                                                                glassReaderBitmapPage = idx
                                                            }
                                                        },
                                                        onActivatePage = { viewModel.setCurrentPageIndex(idx) },
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
                                val horizontalPanReveal = MarginExportMath.hasHorizontalPanOrZoomReveal(
                                    continuousPanX = continuousPanX,
                                    continuousScale = continuousScale
                                )
                                androidx.compose.foundation.lazy.LazyColumn(
                                    state = continuousColState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = !inkController.isDrawing && !inkController.isManipulatingLasso,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (isInfiniteNote) 0.dp else 16.dp),
                                    contentPadding = if (isInfiniteNote) androidx.compose.foundation.layout.PaddingValues(0.dp) else androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(items = pages, key = { it.id }) { page ->
                                        val idx = pages.indexOf(page)
                                        Box(
                                            modifier = Modifier
                                                .requiredWidth(pageWidthDp)
                                                // An infinite page has no edge:
                                                // its box is the visible canvas,
                                                // not a rectangle with the
                                                // aspect ratio of the saved
                                                // bitmap.
                                                .then(if (isInfiniteNote) Modifier.requiredHeight(pageHeightDp) else Modifier)
                                                .offset { IntOffset(continuousPanX.roundToInt(), continuousPanY.roundToInt()) },
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
                                                onPageBox = { continuousPageMap.pageBox = it },
                                                zoom = continuousZoomBucket,
                                                ink = inkController,
                                                inkSettings = inkSettings,
                                                backgroundMode = uiState.backgroundMode,
                                                backgroundDensity = uiState.backgroundDensity,
                                                defaultPattern = uiState.paperPattern,
                                                savedGeneration = savedPageGenerations[idx] ?: 0,
                                                isCurrentPage = idx == currentPageIndex,
                                                isInfinite = isInfiniteNote,
                                                panOffsetX = continuousPanX,
                                                panOffsetY = continuousPanY,
                                                pageType = note?.pageType ?: "infinite",
                                                viewportWidthPx = readerViewportWidthPx,
                                                canvasOriginXProvider = { canvasOriginInRoot.x },
                                                marginLabelAlphaProvider = { marginLabelAlpha },
                                                allowLeftPanReveal = horizontalPanReveal,
                                                allowRightPanReveal = horizontalPanReveal,
                                                allowLeftMarginLabel = true,
                                                allowRightMarginLabel = true,
                                                searchHighlightStart = if (searchHighlightTarget?.notePageIndex == idx) {
                                                    searchHighlightTarget?.startOffset ?: -1
                                                } else -1,
                                                searchHighlightLength = if (searchHighlightTarget?.notePageIndex == idx) {
                                                    searchHighlightTarget?.length ?: 0
                                                } else 0,
                                                searchHighlightRevision = searchHighlightTarget?.revision ?: 0,
                                                modifier = androidx.compose.ui.Modifier.then(
                                                    if (isInfiniteNote) {
                                                        Modifier.fillMaxSize()
                                                    } else {
                                                        Modifier.fillMaxWidth()
                                                    }
                                                ),
                                                onStylusDetected = { viewModel.onStylusDetected() },
                                                onPageBitmap = { idx, bmp ->
                                                    if (idx == currentPageIndex) {
                                                        glassReaderBitmap = bmp
                                                        glassReaderBitmapPage = idx
                                                    }
                                                },
                                                onActivatePage = { viewModel.setCurrentPageIndex(idx) },
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

                        if (hasMarginGuides) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val _rev = inkController.revision
                                val _layerRev = inkController.layersRevision
                                val _marginRev = inkController.marginPresenceRevision
                                val _pageIdx = currentPageIndex
                                val _panX = continuousPanX
                                val _scale = continuousScale
                                val _colOffset = continuousColState.firstVisibleItemScrollOffset
                                val _rowOffset = continuousRowState.firstVisibleItemScrollOffset
                                val vpW = if (readerViewportWidthPx > 0f) readerViewportWidthPx else size.width
                                if (vpW <= 0f || size.height <= 0f) return@Canvas

                                val pageBox = continuousPageMap.pageBox?.takeIf {
                                    it.valid && it.pageIndex == _pageIdx
                                }
                                val pageLeftPx: Float
                                val pageWidthPx: Float
                                if (pageBox != null) {
                                    pageLeftPx = pageBox.originX - canvasOriginInRoot.x
                                    pageWidthPx = pageBox.width.toFloat()
                                } else {
                                    val padPx = 16.dp.toPx()
                                    val approxW = if (uiState.doublePageMode && !isHorizontal) {
                                        ((screenWidthPx * _scale - padPx * 3f) / 2f).coerceAtLeast(1f)
                                    } else {
                                        (screenWidthPx * _scale - padPx * 2f).coerceAtLeast(1f)
                                    }
                                    pageWidthPx = approxW
                                    pageLeftPx = (vpW - approxW) / 2f + _panX
                                }

                                val presence = inkController.marginInkPresence(_pageIdx)
                                val outerPanReveal = MarginExportMath.hasHorizontalPanOrZoomReveal(
                                    continuousPanX = _panX,
                                    continuousScale = _scale
                                )
                                val allowOuterPanReveal = if (uiState.doublePageMode && !isHorizontal) true else outerPanReveal
                                val guideState = MarginExportMath.computeGuideState(
                                    pageType = note?.pageType,
                                    pageLeftInViewportPx = pageLeftPx,
                                    pageWidthPx = pageWidthPx,
                                    viewportWidthPx = vpW,
                                    presence = presence,
                                    allowLeftPanReveal = allowOuterPanReveal,
                                    allowRightPanReveal = allowOuterPanReveal
                                )
                                if (!guideState.showLeftOffscreenChevron &&
                                    !guideState.showRightOffscreenChevron
                                ) {
                                    return@Canvas
                                }

                                val pillW = 22.dp.toPx()
                                val pillH = 28.dp.toPx()
                                val cornerR = 8.dp.toPx()
                                val marginPad = 4.dp.toPx()
                                val chevronY = if (uiState.dockHorizontal &&
                                    uiState.showPencase &&
                                    !uiState.readingMode
                                ) {
                                    84.dp.toPx()
                                } else {
                                    20.dp.toPx()
                                }
                                val bgColor = Color.White.copy(alpha = 0.90f)
                                val borderColor = Color(0xFFE2E8F0)
                                val iconColor = OrangePrimary
                                val chevronStroke = 2.dp.toPx()

                                fun drawChevronPill(x: Float, pointLeft: Boolean) {
                                    val topLeft = androidx.compose.ui.geometry.Offset(x, chevronY)
                                    val pillSize = androidx.compose.ui.geometry.Size(pillW, pillH)
                                    drawRoundRect(
                                        color = bgColor,
                                        topLeft = topLeft,
                                        size = pillSize,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                            cornerR,
                                            cornerR
                                        )
                                    )
                                    drawRoundRect(
                                        color = borderColor,
                                        topLeft = topLeft,
                                        size = pillSize,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                            cornerR,
                                            cornerR
                                        ),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                    val cx = x + pillW / 2f
                                    val cy = chevronY + pillH / 2f
                                    val dx = 3.5.dp.toPx()
                                    val dy = 5.dp.toPx()
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        if (pointLeft) {
                                            moveTo(cx + dx * 0.6f, cy - dy)
                                            lineTo(cx - dx * 0.8f, cy)
                                            lineTo(cx + dx * 0.6f, cy + dy)
                                        } else {
                                            moveTo(cx - dx * 0.6f, cy - dy)
                                            lineTo(cx + dx * 0.8f, cy)
                                            lineTo(cx - dx * 0.6f, cy + dy)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = iconColor,
                                        style = Stroke(
                                            width = chevronStroke,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                if (guideState.showLeftOffscreenChevron) {
                                    val maxX = (vpW - pillW - marginPad).coerceAtLeast(marginPad)
                                    val leftX = kotlin.math.max(marginPad, pageLeftPx + marginPad)
                                        .coerceIn(marginPad, maxX)
                                    drawChevronPill(leftX, pointLeft = true)
                                }
                                if (guideState.showRightOffscreenChevron) {
                                    val maxX = (vpW - pillW - marginPad).coerceAtLeast(marginPad)
                                    val rightX = kotlin.math.min(
                                        vpW - pillW - marginPad,
                                        pageLeftPx + pageWidthPx - pillW - marginPad
                                    ).coerceIn(marginPad, maxX)
                                    drawChevronPill(rightX, pointLeft = false)
                                }
                            }
                        }

                    }
                }

                // Floating HUD: Zoom / Reset / Center pill (auto-hides after pan/zoom inactivity)
                // Works across Infinite Canvas, A4 notes, and PDF notes in both continuous and single-page modes.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (uiState.dockHorizontal) 74.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayScale = if (isContinuous) continuousScale else singlePageScale
                    AnimatedVisibility(
                        visible = showZoomHud,
                        enter = fadeIn(androidx.compose.animation.core.tween(150)),
                        exit = fadeOut(androidx.compose.animation.core.tween(1000))
                    ) {
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
                                IconButton(
                                    onClick = {
                                        viewModel.setZoomLocked(!uiState.zoomLocked)
                                        zoomHudActivityTick++
                                    },
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
                                val doResetZoom = {
                                    if (isContinuous) {
                                        val reset = ReaderTransform.resetZoom(
                                            ReaderTransform.State(
                                                continuousScale,
                                                continuousPanX,
                                                continuousPanY
                                            )
                                        )
                                        continuousScale = reset.scale
                                        continuousPanX = reset.panX
                                        continuousPanY = reset.panY
                                    } else {
                                        canvasViewRef?.resetZoom()
                                        singlePageScale = 1f
                                    }
                                    zoomHudActivityTick++
                                    android.widget.Toast.makeText(
                                        context,
                                        "Zoom reset to 100%",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                Text(
                                    text = "${(displayScale * 100).roundToInt()}%",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { doResetZoom() }
                                )
                                IconButton(
                                    onClick = { doResetZoom() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset zoom to 100%",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (!isContinuous) {
                                            canvasViewRef?.resetPan()
                                        } else if (isInfiniteNote) {
                                            val center = inkController.computeArtworkCenter(0)
                                            continuousScale = 1f
                                            if (center != null) {
                                                continuousPanX = screenWidthPx / 2f - center.first
                                                continuousPanY = screenHeightPx / 2f - center.second
                                            } else {
                                                continuousPanX = 0f
                                                continuousPanY = 0f
                                            }
                                        } else {
                                            val centred = ReaderTransform.centre(
                                                ReaderTransform.State(
                                                    continuousScale,
                                                    continuousPanX,
                                                    continuousPanY
                                                )
                                            )
                                            continuousScale = centred.scale
                                            continuousPanX = centred.panX
                                            continuousPanY = centred.panY
                                        }
                                        zoomHudActivityTick++
                                        android.widget.Toast.makeText(
                                            context,
                                            "Canvas centred",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = "Centre the canvas",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
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

                // Unified Layers panel for PDF & A4 notes (Drawing Layers 1-3 + Floating Image/Text Layers 0-3)
                if (showLayersPanel && !isInfiniteNote) {
                    val _tick = layersTick
                    val _drawingTick = inkController.layersRevision
                    val panelPage = pages.getOrNull(currentPageIndex)
                    LaunchedEffect(currentPageIndex, panelPage?.id) {
                        if (panelPage != null) {
                            inkController.ensureSessionForPage(panelPage, currentPageIndex)
                        }
                    }
                    val drawingStack = inkController.layerStack(currentPageIndex)
                    val layers = if (isContinuous) {
                        readerLayers.layers(currentPageIndex)
                    } else {
                        canvasViewRef?.imageLayers ?: emptyList()
                    }
                    val syncSinglePageAfterLayerEdit: suspend () -> Unit = {
                        inkController.flushCurrentSuspend()
                        val canvas = canvasViewRef
                        if (!isContinuous && canvas != null) {
                            val vp = vectorPdfPage(currentPageIndex)
                            canvas.baseLayerVisible = inkController.layerStack(currentPageIndex)
                                .layerAt(PageLayerStack.BASE_LAYER_INDEX)?.visible != false
                            val bmp = canvas.getBackingBitmap()
                            val tw = bmp?.width ?: 2160
                            val th = bmp?.height ?: 3056
                            canvas.annotationOverlay = if (PdfModeReconciliationPolicy.shouldLoadAnnotationOverlay(
                                    note?.pageType, vp != null
                                )
                            ) {
                                inkController.composeAllLayersForSinglePage(
                                    pageIndex = currentPageIndex,
                                    targetWidth = tw,
                                    targetHeight = th,
                                    page = vp
                                )
                            } else null
                            canvas.invalidate()
                            savedPageGenerations[currentPageIndex] = (savedPageGenerations[currentPageIndex] ?: 0) + 1
                        }
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
                            .widthIn(min = 240.dp, max = 300.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Layers",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF222222)
                                )
                                IconButton(
                                    onClick = { showLayersPanel = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Drawing Layers (${drawingStack.size}/3)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                if (drawingStack.canAdd) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val targetPage = panelPage ?: return@launch
                                                inkController.ensureSessionReady(targetPage, currentPageIndex)
                                                inkController.addLayer(currentPageIndex)
                                                syncSinglePageAfterLayerEdit()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("+ Drawing", fontSize = 11.sp, color = OrangePrimary)
                                    }
                                }
                            }

                            drawingStack.layers.forEach { dLayer ->
                                val isCurrentDrawing = dLayer.index == drawingStack.currentIndex
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            scope.launch {
                                                val targetPage = panelPage ?: return@launch
                                                inkController.ensureSessionReady(targetPage, currentPageIndex)
                                                inkController.selectLayer(currentPageIndex, dLayer.index)
                                                if (!isContinuous) {
                                                    inkController.flushCurrentSuspend()
                                                }
                                            }
                                        }
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val targetPage = panelPage ?: return@launch
                                                inkController.ensureSessionReady(targetPage, currentPageIndex)
                                                inkController.setLayerVisible(currentPageIndex, dLayer.index, !dLayer.visible)
                                                syncSinglePageAfterLayerEdit()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (dLayer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "visibility",
                                            tint = if (dLayer.visible) Color.DarkGray else Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = buildString {
                                            append(dLayer.name)
                                            if (isCurrentDrawing) append(" · active")
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrentDrawing) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (dLayer.visible) Color(0xFF222222) else Color(0xFF999999),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (dLayer.index != PageLayerStack.BASE_LAYER_INDEX) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    val targetPage = panelPage ?: return@launch
                                                    inkController.ensureSessionReady(targetPage, currentPageIndex)
                                                    inkController.mergeLayer(currentPageIndex, dLayer.index)
                                                    syncSinglePageAfterLayerEdit()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Merge", fontSize = 11.sp, color = Color(0xFF444444))
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val targetPage = panelPage ?: return@launch
                                                    inkController.ensureSessionReady(targetPage, currentPageIndex)
                                                    inkController.deleteLayer(currentPageIndex, dLayer.index)
                                                    syncSinglePageAfterLayerEdit()
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "delete drawing layer",
                                                tint = Color.Red,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Divider(color = Color(0xFFEEEEEE))
                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "Floating Layers (${layers.size}/3)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            if (layers.isEmpty()) {
                                Spacer(Modifier.height(4.dp))
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
                                            modifier = Modifier.size(32.dp)
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
                                            modifier = Modifier.size(30.dp)
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
                                            modifier = Modifier.size(30.dp)
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
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "delete", tint = Color.Red, modifier = Modifier.size(15.dp))
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
                                        openLiveTextOverlay()
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
                                                inkController.flushCurrentSuspend()
                                                val view = canvasViewRef
                                                if (view != null) {
                                                    view.pageIndex = page
                                                    val vp = vectorPdfPage(page)
                                                    val mergeTarget = PdfModeReconciliationPolicy.resolveMergeTarget(vp != null)
                                                    val base: Bitmap = when (mergeTarget) {
                                                        PdfModeReconciliationPolicy.MergeTarget.STROKES_FILE -> {
                                                            val aspect = if (vp != null) {
                                                                withContext(Dispatchers.IO) {
                                                                    PageAspectCache.default.getOrLoad(
                                                                        vp.pdfSourcePath,
                                                                        vp.pdfPageIndex,
                                                                        fallback = PdfPageGeometry.DEFAULT_ASPECT
                                                                    ) {
                                                                        pdfDocCache.document(vp.pdfSourcePath)
                                                                            ?.pageSize(vp.pdfPageIndex)
                                                                    }
                                                                }
                                                            } else PdfPageGeometry.DEFAULT_ASPECT
                                                            val (pw, ph) = PdfModeReconciliationPolicy.pagePixelSize(aspect)
                                                            withContext(Dispatchers.IO) {
                                                                viewModel.loadPageStrokes(page)
                                                            } ?: withContext(Dispatchers.IO) {
                                                                personal.notes.touch.util.BitmapUtils.createSafeBitmap(pw, ph)
                                                            }
                                                        }
                                                        PdfModeReconciliationPolicy.MergeTarget.PAGE_BITMAP -> {
                                                            withContext(Dispatchers.IO) {
                                                                viewModel.repository.loadPageBitmap(viewModel.noteId, page)
                                                            } ?: withContext(Dispatchers.IO) {
                                                                personal.notes.touch.util.BitmapUtils.createSafeBitmap(2160, 3056)
                                                            }
                                                        }
                                                    }
                                                    view.setPageContent(base)
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
                                                            when (mergeTarget) {
                                                                PdfModeReconciliationPolicy.MergeTarget.STROKES_FILE ->
                                                                    viewModel.repository.savePageStrokes(viewModel.noteId, page, merged)
                                                                PdfModeReconciliationPolicy.MergeTarget.PAGE_BITMAP ->
                                                                    viewModel.repository.savePageBitmap(viewModel.noteId, page, merged)
                                                            }
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
                                                    when (mergeTarget) {
                                                        PdfModeReconciliationPolicy.MergeTarget.STROKES_FILE -> {
                                                            inkController.reloadSinglePageStrokes(page)
                                                        }
                                                        PdfModeReconciliationPolicy.MergeTarget.PAGE_BITMAP -> {
                                                            if (inkController.getSession(page) != null) {
                                                                inkController.releaseSessionSuspend()
                                                            }
                                                        }
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
                                                if (inkController.getSession(page) != null) {
                                                    if (vectorPdfPage(page) != null) {
                                                        inkController.reloadSinglePageStrokes(page)
                                                    } else {
                                                        inkController.releaseSessionSuspend()
                                                    }
                                                }
                                                savedPageGenerations[page] =
                                                    (savedPageGenerations[page] ?: 0) + 1
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
                                            .height(22.dp)
                                            .width(5.dp)
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.78f),
                                                        Color(0x331E293B)
                                                    )
                                                )
                                            )
                                            .border(0.6.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.5.dp))
                                    )
                                }


                                // Fountain Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fountain_pen,
                                    label = "Fountain Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.FOUNTAIN_PEN,
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.FOUNTAIN_PEN)
                                    }
                                )

                                // Fineliner / Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fineliner,
                                    label = "Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.PEN,
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.PEN)
                                    }
                                )

                                // Graphite Pencil (abrasion + stylus tilt)
                                PencaseToolIcon(
                                    icon = Icons.Default.Edit,
                                    label = "Pencil",
                                    isSelected = uiState.selectedTool == CanvasTool.PENCIL,
                                    selectedTint = Color(0xFF475569),
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.PENCIL)
                                    }
                                )

                                // Shape Draw + Fill (live closed curve fill)
                                PencaseToolIcon(
                                    icon = Icons.Default.FormatColorFill,
                                    label = "Fill",
                                    isSelected = uiState.selectedTool == CanvasTool.SHAPE_FILL,
                                    selectedTint = OrangePrimary,
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.SHAPE_FILL)
                                    }
                                )

                                // Yellow Highlighter
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_highlighter,
                                    label = "Highlighter",
                                    isSelected = uiState.selectedTool == CanvasTool.HIGHLIGHTER,
                                    selectedTint = Color(0xFFFFD600),
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.HIGHLIGHTER)
                                    }
                                )

                                // Pink Eraser
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_eraser,
                                    label = "Eraser",
                                    isSelected = uiState.selectedTool == CanvasTool.ERASER,
                                    selectedTint = Color(0xFFFF4081),
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.ERASER)
                                    }
                                )

                                // Text Tool [T] (OneNote-style live text preview or F-10 PDF text select)
                                Box {
                                    PencaseToolIcon(
                                        icon = Icons.Default.TextFields,
                                        label = "Text",
                                        isSelected = isLiveTextActive || pdfTextMode,
                                        onClick = {
                                            when (PdfTextToolPolicy.dockTapAction(pdfTextAvailable, pdfTextMode)) {
                                                TextDockTapAction.OPEN_LIVE_TEXT -> openLiveTextOverlay()
                                                TextDockTapAction.OPEN_TEXT_MENU,
                                                TextDockTapAction.EXIT_PDF_TEXT_MODE -> showTextMenuHoriz = true
                                            }
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = showTextMenuHoriz,
                                        onDismissRequest = { showTextMenuHoriz = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Add Text Box") },
                                            leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                            onClick = {
                                                showTextMenuHoriz = false
                                                openLiveTextOverlay()
                                            }
                                        )
                                        if (pdfTextMode) {
                                            DropdownMenuItem(
                                                text = { Text("Exit PDF Text Select") },
                                                leadingIcon = { Icon(Icons.Default.Close, null) },
                                                onClick = {
                                                    showTextMenuHoriz = false
                                                    exitPdfTextMode()
                                                }
                                            )
                                        } else if (pdfTextAvailable) {
                                            DropdownMenuItem(
                                                text = { Text("Select PDF Text") },
                                                leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                                onClick = {
                                                    showTextMenuHoriz = false
                                                    activatePdfTextSelect()
                                                }
                                            )
                                        }
                                    }
                                }

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
                                                exitPdfTextMode()
                                                viewModel.selectTool(CanvasTool.SHAPE_RECT)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Circle / Ellipse") },
                                            leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, null) },
                                            onClick = {
                                                exitPdfTextMode()
                                                viewModel.selectTool(CanvasTool.SHAPE_CIRCLE)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Straight Line") },
                                            leadingIcon = { Icon(Icons.Default.Timeline, null) },
                                            onClick = {
                                                exitPdfTextMode()
                                                viewModel.selectTool(CanvasTool.SHAPE_LINE)
                                                showShapesMenu = false
                                            }
                                        )
                                    }
                                }

                                // Lasso / Arrow Selection Tool Slot (Horizontal dock -> morphs downward)
                                SelectionToolSlot(
                                    activeTool = preferredSelectTool,
                                    isSelected = uiState.selectedTool == CanvasTool.LASSO ||
                                        uiState.selectedTool == CanvasTool.SELECT,
                                    selectedTint = OrangePrimary,
                                    expandDown = true,
                                    onSelectLasso = selectLassoTool,
                                    onSelectArrow = selectArrowTool
                                )

                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(1.5.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0x241E293B),
                                                    Color.White.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )

                                // 3 Stroke Width Presets (Horizontal bar line thickness)
                                // Short tap picks the preset, long press opens the slider popup.
                                val currentWidths =
                                    (if (uiState.selectedTool == CanvasTool.ERASER) dockEraserWidths
                                    else dockWidths).take(3)
                                val widthPresets = currentWidths.map { it to "${it.toInt()}px" }
                                val activeWidth = uiState.activeStrokeWidth()
                                val isEraserTool = uiState.selectedTool == CanvasTool.ERASER
                                currentWidths.forEachIndexed { wIdx, widthPx ->
                                    // The chip that is in use is the closest
                                    // one: a thickness that was set with the
                                    // slider belongs to no preset, and the
                                    // user has to see which one is in use.
                                    val isSelected = currentWidths.minByOrNull {
                                        kotlin.math.abs(it - activeWidth)
                                    } == widthPx
                                    val barHeight = when (wIdx) {
                                        0 -> 2.dp
                                        1 -> 4.dp
                                        else -> 6.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.94f),
                                                            Color.White.copy(alpha = 0.58f)
                                                        )
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.30f),
                                                            Color.White.copy(alpha = 0.12f)
                                                        )
                                                    )
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 1.4.dp else 0.7.dp,
                                                brush = if (isSelected) {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White,
                                                            OrangePrimary.copy(alpha = 0.75f),
                                                            Color.White.copy(alpha = 0.90f)
                                                        )
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.65f),
                                                            Color.White.copy(alpha = 0.18f)
                                                        )
                                                    )
                                                },
                                                shape = CircleShape
                                            )
                                            .combinedClickable(
                                                onClick = { viewModel.setStrokeWidth(widthPx) },
                                                onLongClick = {
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
                                            onWidthChange = { newW ->
                                                if (isEraserTool) {
                                                    dockEraserWidths = dockEraserWidths.toMutableList().also { it[wIdx] = newW }
                                                } else {
                                                    dockWidths = dockWidths.toMutableList().also { it[wIdx] = newW }
                                                }
                                                viewModel.setStrokeWidth(newW)
                                            }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(1.5.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0x241E293B),
                                                    Color.White.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )

                                // 3 Rounded-Square Color Swatches with checkmark
                                // Short tap picks the colour, long press opens the full palette.
                                dockColors.forEachIndexed { cIdx, c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(25.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c)
                                            .drawWithContent {
                                                drawContent()
                                                // Liquid glass bead top-left specular gloss
                                                drawRect(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.46f),
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.12f)
                                                        )
                                                    )
                                                )
                                            }
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                brush = if (isSelected) {
                                                    Brush.linearGradient(
                                                        colors = listOf(Color.White, OrangePrimary, Color.White)
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.80f),
                                                            Color(0x33000000)
                                                        )
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp)
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
                                            selectedColor = dockColors.getOrElse(cIdx) { uiState.selectedColor },
                                            preferRight = false,
                                            onColorSelected = { colour ->
                                                // The chip the palette was
                                                // opened from keeps the
                                                // colour that was picked.
                                                dockColors = dockColors.toMutableList()
                                                    .also { list -> list[cIdx] = colour }
                                                viewModel.selectColor(colour)
                                            },
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
                                            .width(22.dp)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.78f),
                                                        Color(0x331E293B)
                                                    )
                                                )
                                            )
                                            .border(0.6.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.5.dp))
                                    )
                                }


                                // Fountain Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fountain_pen,
                                    label = "Fountain Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.FOUNTAIN_PEN,
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.FOUNTAIN_PEN)
                                    }
                                )

                                // Fineliner / Pen
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_fineliner,
                                    label = "Pen",
                                    isSelected = uiState.selectedTool == CanvasTool.PEN,
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.PEN)
                                    }
                                )

                                // Graphite Pencil (abrasion + stylus tilt)
                                PencaseToolIcon(
                                    icon = Icons.Default.Edit,
                                    label = "Pencil",
                                    isSelected = uiState.selectedTool == CanvasTool.PENCIL,
                                    selectedTint = Color(0xFF475569),
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.PENCIL)
                                    }
                                )

                                // Shape Draw + Fill (live closed curve fill)
                                PencaseToolIcon(
                                    icon = Icons.Default.FormatColorFill,
                                    label = "Fill",
                                    isSelected = uiState.selectedTool == CanvasTool.SHAPE_FILL,
                                    selectedTint = OrangePrimary,
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.SHAPE_FILL)
                                    }
                                )

                                // Yellow Highlighter
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_highlighter,
                                    label = "Highlighter",
                                    isSelected = uiState.selectedTool == CanvasTool.HIGHLIGHTER,
                                    selectedTint = Color(0xFFFFD600),
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.HIGHLIGHTER)
                                    }
                                )

                                // Pink Eraser
                                PencaseToolIcon(
                                    drawableRes = R.drawable.ic_tool_eraser,
                                    label = "Eraser",
                                    isSelected = uiState.selectedTool == CanvasTool.ERASER,
                                    selectedTint = Color(0xFFFF4081),
                                    onClick = {
                                        exitPdfTextMode()
                                        viewModel.selectTool(CanvasTool.ERASER)
                                    }
                                )

                                // Text Tool [T] (OneNote-style live text preview or F-10 PDF text select)
                                Box {
                                    PencaseToolIcon(
                                        icon = Icons.Default.TextFields,
                                        label = "Text",
                                        isSelected = isLiveTextActive || pdfTextMode,
                                        onClick = {
                                            when (PdfTextToolPolicy.dockTapAction(pdfTextAvailable, pdfTextMode)) {
                                                TextDockTapAction.OPEN_LIVE_TEXT -> openLiveTextOverlay()
                                                TextDockTapAction.OPEN_TEXT_MENU,
                                                TextDockTapAction.EXIT_PDF_TEXT_MODE -> showTextMenuVert = true
                                            }
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = showTextMenuVert,
                                        onDismissRequest = { showTextMenuVert = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Add Text Box") },
                                            leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                            onClick = {
                                                showTextMenuVert = false
                                                openLiveTextOverlay()
                                            }
                                        )
                                        if (pdfTextMode) {
                                            DropdownMenuItem(
                                                text = { Text("Exit PDF Text Select") },
                                                leadingIcon = { Icon(Icons.Default.Close, null) },
                                                onClick = {
                                                    showTextMenuVert = false
                                                    exitPdfTextMode()
                                                }
                                            )
                                        } else if (pdfTextAvailable) {
                                            DropdownMenuItem(
                                                text = { Text("Select PDF Text") },
                                                leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                                onClick = {
                                                    showTextMenuVert = false
                                                    activatePdfTextSelect()
                                                }
                                            )
                                        }
                                    }
                                }

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
                                                exitPdfTextMode()
                                                viewModel.selectTool(CanvasTool.SHAPE_RECT)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Circle") },
                                            leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, null) },
                                            onClick = {
                                                exitPdfTextMode()
                                                viewModel.selectTool(CanvasTool.SHAPE_CIRCLE)
                                                showShapesMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Line") },
                                            leadingIcon = { Icon(Icons.Default.Timeline, null) },
                                            onClick = {
                                                exitPdfTextMode()
                                                viewModel.selectTool(CanvasTool.SHAPE_LINE)
                                                showShapesMenu = false
                                            }
                                        )
                                    }
                                }

                                // Lasso / Arrow Selection Tool Slot (Vertical dock -> morphs to the right)
                                SelectionToolSlot(
                                    activeTool = preferredSelectTool,
                                    isSelected = uiState.selectedTool == CanvasTool.LASSO ||
                                        uiState.selectedTool == CanvasTool.SELECT,
                                    selectedTint = OrangePrimary,
                                    expandDown = false,
                                    onSelectLasso = selectLassoTool,
                                    onSelectArrow = selectArrowTool
                                )

                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(1.5.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0x241E293B),
                                                    Color.White.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )

                                // 3 Stroke Width Presets (vertical line thickness bars)
                                // Short tap picks the preset, long press opens the slider popup.
                                val currentWidthsVert =
                                    if (uiState.selectedTool == CanvasTool.ERASER) dockEraserWidths
                                    else dockWidths
                                val verticalPresets = currentWidthsVert.map { it to "${it.toInt()}px" }
                                val verticalActive = uiState.activeStrokeWidth()
                                val isEraserToolVert = uiState.selectedTool == CanvasTool.ERASER
                                currentWidthsVert.take(3).forEachIndexed { vIdx, widthPx ->
                                    val isSelected = currentWidthsVert.minByOrNull {
                                        kotlin.math.abs(it - verticalActive)
                                    } == widthPx
                                    val barH = when (vIdx) {
                                        0 -> 2.dp
                                        1 -> 4.dp
                                        else -> 6.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.94f),
                                                            Color.White.copy(alpha = 0.58f)
                                                        )
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.30f),
                                                            Color.White.copy(alpha = 0.12f)
                                                        )
                                                    )
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 1.4.dp else 0.7.dp,
                                                brush = if (isSelected) {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White,
                                                            OrangePrimary.copy(alpha = 0.75f),
                                                            Color.White.copy(alpha = 0.90f)
                                                        )
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.65f),
                                                            Color.White.copy(alpha = 0.18f)
                                                        )
                                                    )
                                                },
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
                                            onWidthChange = { newW ->
                                                if (isEraserToolVert) {
                                                    dockEraserWidths = dockEraserWidths.toMutableList().also { it[vIdx] = newW }
                                                } else {
                                                    dockWidths = dockWidths.toMutableList().also { it[vIdx] = newW }
                                                }
                                                viewModel.setStrokeWidth(newW)
                                            }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(1.5.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0x241E293B),
                                                    Color.White.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )

                                // 3 Rounded-Square Color Swatches with checkmark
                                // Short tap picks the colour, long press opens the full palette.
                                dockColors.forEachIndexed { cIdx, c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(25.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c)
                                            .drawWithContent {
                                                drawContent()
                                                // Liquid glass bead top-left specular gloss
                                                drawRect(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.46f),
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.12f)
                                                        )
                                                    )
                                                )
                                            }
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                brush = if (isSelected) {
                                                    Brush.linearGradient(
                                                        colors = listOf(Color.White, OrangePrimary, Color.White)
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.80f),
                                                            Color(0x33000000)
                                                        )
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp)
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
                                            selectedColor = dockColors.getOrElse(cIdx) { uiState.selectedColor },
                                            preferRight = true,
                                            onColorSelected = { colour ->
                                                dockColors = dockColors.toMutableList()
                                                    .also { list -> list[cIdx] = colour }
                                                viewModel.selectColor(colour)
                                            },
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
                // 4. FLOATING VERTICAL PAGE STRIP (Right Edge) + SCRUBBER & BOOKMARK TOGGLE
                // ==========================================
                val scrubberEnabled = PageScrubberMath.isScrubberEnabled(totalPages)
                var scrubFraction by remember { mutableFloatStateOf(0f) }
                var scrubTargetPage by remember { mutableIntStateOf(0) }
                var capsuleSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                var lastThrottledJumpMs by remember { mutableStateOf(0L) }
                var lastJumpedPage by remember { mutableIntStateOf(-1) }
                var scrubFlushJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                val latestTotalPages by rememberUpdatedState(totalPages)
                val latestCurrentPageIndex by rememberUpdatedState(currentPageIndex)
                val latestIsContinuous by rememberUpdatedState(isContinuous)
                val latestIsHorizontal by rememberUpdatedState(isHorizontal)
                val latestDirection by rememberUpdatedState(uiState.pageTurningDirection)
                val latestDoublePage by rememberUpdatedState(uiState.doublePageMode)
                val latestContinuousVisible by rememberUpdatedState(currentContinuousVisible)
                val latestDensity by rememberUpdatedState(density)

                fun performScrubJump(targetPage: Int, animate: Boolean) {
                    val clamped = targetPage.coerceIn(0, (latestTotalPages - 1).coerceAtLeast(0))
                    if (latestIsContinuous) {
                        val token = ++scrubJumpToken
                        isScrubSettling = true
                        scrubJumpJob?.cancel()
                        scrubJumpJob = scope.launch {
                            try {
                                val finalIndex = if (animate) {
                                    animateScrollToContinuousPage(
                                        targetIndex = clamped,
                                        horizontal = latestIsHorizontal,
                                        doublePage = latestDoublePage
                                    )
                                } else {
                                    val maxIdx = (pages.size - 1).coerceAtLeast(0)
                                    val resolved = clamped.coerceIn(0, maxIdx)
                                    val horizontal = latestIsHorizontal
                                    val doublePage = latestDoublePage
                                    if (horizontal) {
                                        continuousRowState.scrollToItem(resolved)
                                    } else if (doublePage) {
                                        continuousColState.scrollToItem(resolved / 2)
                                    } else {
                                        continuousColState.scrollToItem(resolved)
                                    }
                                    val firstVis = if (horizontal) {
                                        continuousRowState.firstVisibleItemIndex
                                    } else {
                                        continuousColState.firstVisibleItemIndex
                                    }
                                    settledProgrammaticFirstVis = firstVis
                                    settledProgrammaticPage = resolved
                                    continuousVisibleIndex = firstVis
                                    resolved
                                }
                                viewModel.setCurrentPageIndex(finalIndex)
                            } finally {
                                if (scrubJumpToken == token) {
                                    isScrubSettling = false
                                }
                            }
                        }
                    } else {
                        scrubJumpJob?.cancel()
                        scrubJumpJob = scope.launch {
                            scrubFlushJob?.join()
                            viewModel.setCurrentPageIndex(clamped)
                        }
                    }
                }

                if (!uiState.isToolsPanelOpen && !uiState.readingMode) {
                    Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(16.dp))
                        .onSizeChanged { capsuleSizePx = it }
                        .pointerInput(scrubberEnabled) {
                            if (!scrubberEnabled) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    val startPage = latestCurrentPageIndex.coerceIn(
                                        0,
                                        (latestTotalPages - 1).coerceAtLeast(0)
                                    )
                                    scrubFraction = PageScrubberMath.pageToScrubFraction(
                                        startPage,
                                        latestTotalPages
                                    )
                                    scrubTargetPage = startPage
                                    lastJumpedPage = startPage
                                    lastThrottledJumpMs = android.os.SystemClock.uptimeMillis()
                                    isScrubbing = true
                                    if (latestIsContinuous) {
                                        scrubJumpJob?.cancel()
                                        scrubJumpJob = null
                                        scrubJumpToken++
                                        isScrubSettling = false
                                        settledProgrammaticFirstVis = -1
                                        settledProgrammaticPage = -1
                                        scrubFlushJob = scope.launch {
                                            try {
                                                inkController.flushCurrentSuspend()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    } else {
                                        scrubFlushJob = scope.launch {
                                            try {
                                                val canvas = canvasViewRef
                                                if (canvas != null) {
                                                    canvas.getBackingBitmap()?.let { bmp ->
                                                        viewModel.saveCurrentPageBitmapDirect(bmp)
                                                    }
                                                    if (canvas.layersDirty) {
                                                        canvas.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                    }
                                                    if (canvas.hasBeyondPageInk()) {
                                                        withContext(Dispatchers.IO) {
                                                            canvas.saveBeyondPageInkNow(viewModel.fileStore, viewModel.noteId)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val minSpan = with(latestDensity) { 160.dp.toPx() }
                                    val trackSpan = maxOf(capsuleSizePx.height.toFloat(), minSpan)
                                    scrubFraction = PageScrubberMath.applyScrubDelta(
                                        currentFraction = scrubFraction,
                                        deltaX = dragAmount.x,
                                        deltaY = dragAmount.y,
                                        pageTurningDirection = latestDirection,
                                        trackSpanPx = trackSpan
                                    )
                                    val nextTarget = PageScrubberMath.scrubFractionToPage(
                                        scrubFraction,
                                        latestTotalPages
                                    )
                                    scrubTargetPage = nextTarget
                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (nextTarget != lastJumpedPage && (now - lastThrottledJumpMs) >= 80L) {
                                        lastThrottledJumpMs = now
                                        lastJumpedPage = nextTarget
                                        performScrubJump(nextTarget, animate = false)
                                    }
                                },
                                onDragEnd = {
                                    val finalTarget = scrubTargetPage
                                    isScrubbing = false
                                    if (finalTarget != lastJumpedPage || !latestIsContinuous || (settledProgrammaticPage != finalTarget && finalTarget != latestContinuousVisible)) {
                                        performScrubJump(finalTarget, animate = true)
                                    }
                                },
                                onDragCancel = {
                                    val finalTarget = scrubTargetPage
                                    isScrubbing = false
                                    if (finalTarget != lastJumpedPage || (latestIsContinuous && settledProgrammaticPage != finalTarget && finalTarget != latestContinuousVisible)) {
                                        performScrubJump(finalTarget, animate = true)
                                    }
                                }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle pill (highlighted when scrubber is active)
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(3.dp)
                                .background(
                                    if (isScrubbing) OrangePrimary else Color(0xFFCCCCCC),
                                    RoundedCornerShape(1.5.dp)
                                )
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

                        // One-tap Bookmark Toggle on the right-edge capsule
                        val isCurrentPageBookmarked = uiState.bookmarkedPages.contains(currentPageIndex)
                        IconButton(
                            onClick = { viewModel.toggleBookmark(currentPageIndex) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isCurrentPageBookmarked) "Remove Bookmark" else "Bookmark Page",
                                tint = if (isCurrentPageBookmarked) OrangePrimary else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Layers popover toggle inside right-edge capsule
                        IconButton(
                            onClick = {
                                if (PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote) ==
                                    PdfModeReconciliationPolicy.LayerEntryTarget.INFINITE_DRAWING_ONLY
                                ) {
                                    showLayerPanel = !showLayerPanel
                                    showLayersPanel = false
                                } else {
                                    showLayersPanel = !showLayersPanel
                                    showLayerPanel = false
                                }
                                layerActionIndex = -1
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

                // Floating scrubber preview bubble (thumbnail + page badge)
                AnimatedVisibility(
                    visible = isScrubbing && scrubberEnabled,
                    enter = fadeIn(androidx.compose.animation.core.tween(100)),
                    exit = fadeOut(androidx.compose.animation.core.tween(150)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 68.dp)
                        .zIndex(15f)
                ) {
                    val previewPage = pages.getOrNull(scrubTargetPage)
                    val previewGen = savedPageGenerations[scrubTargetPage] ?: 0
                    val previewPageType = note?.pageType ?: "a4"
                    val initialPreviewBmp = remember(previewPage?.id, scrubTargetPage, previewGen, previewPageType) {
                        if (previewPage != null) {
                            personal.notes.touch.util.BitmapUtils.getCachedPageThumbnailIfPresent(
                                noteId = viewModel.noteId,
                                fileStore = viewModel.fileStore,
                                page = previewPage,
                                pageType = previewPageType,
                                longSidePx = 340,
                                savedGeneration = previewGen
                            )
                        } else null
                    }
                    var loadedPreviewPair by remember(previewPage?.id, scrubTargetPage, previewGen, previewPageType) {
                        val aspect = if (previewPage != null &&
                            previewPage.pdfSourcePath.isNotEmpty() &&
                            previewPage.pdfPageIndex >= 0
                        ) {
                            PageAspectCache.default.get(
                                previewPage.pdfSourcePath,
                                previewPage.pdfPageIndex
                            ) ?: PdfPageGeometry.DEFAULT_ASPECT
                        } else {
                            PdfPageGeometry.DEFAULT_ASPECT
                        }
                        mutableStateOf(initialPreviewBmp to aspect)
                    }
                    LaunchedEffect(isScrubbing, scrubTargetPage, previewPage?.id, previewGen, previewPageType) {
                        if (!isScrubbing || previewPage == null) return@LaunchedEffect
                        if (loadedPreviewPair.first == null) {
                            kotlinx.coroutines.delay(40)
                            loadedPreviewPair = personal.notes.touch.util.BitmapUtils.loadOrRenderPageThumbnail(
                                noteId = viewModel.noteId,
                                fileStore = viewModel.fileStore,
                                page = previewPage,
                                pdfDocCache = pdfDocCache,
                                aspectCache = PageAspectCache.default,
                                pageType = previewPageType,
                                longSidePx = 340,
                                savedGeneration = previewGen
                            )
                        }
                    }
                    val previewBmp = loadedPreviewPair.first
                    val previewAspect = loadedPreviewPair.second
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(88.dp)
                                    .height((88f / previewAspect).coerceIn(48f, 124f).dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                    .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                            ) {
                                if (previewBmp != null && !previewBmp.isRecycled) {
                                    androidx.compose.foundation.Image(
                                        bitmap = previewBmp.asImageBitmap(),
                                        contentDescription = "Page ${scrubTargetPage + 1} preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        text = "${scrubTargetPage + 1}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (uiState.bookmarkedPages.contains(scrubTargetPage)) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = OrangePrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = "Page ${scrubTargetPage + 1} / $totalPages",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF212529)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 4b. THE LAYER PANEL OF THE PAGE
                // ==========================================
                // The floating card of the original: the layers of the
                // current page, the eye that masks a layer and the menu
                // with the actions of a layer.
                // Dedicated drawing LayerPanel is retained for Infinite notes; PDF/A4 use Unified showLayersPanel.
                if (showLayerPanel && isContinuous && isInfiniteNote) {
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
                                val nextVis = !layer.visible
                                inkController.setLayerVisible(currentPageIndex, index, nextVis)
                                if (!nextVis) {
                                    val sel = infiniteItems.firstOrNull { it.id == selectedInfiniteItemId }
                                    if (sel != null && sel.pageIndex == currentPageIndex && sel.layerIndex == index) {
                                        selectedInfiniteItemId = null
                                    }
                                }
                                if (isInfiniteNote) {
                                    scope.launch(Dispatchers.IO) {
                                        inkController.generateAndSavePagePreview(currentPageIndex)
                                        viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                    }
                                    savedPageGenerations[currentPageIndex] = (savedPageGenerations[currentPageIndex] ?: 0) + 1
                                }
                            }
                        },
                        onMore = { index -> layerActionIndex = index },
                        onAdd = { inkController.addLayer(currentPageIndex) },
                        onMerge = { index ->
                            if (inkController.mergeLayer(currentPageIndex, index)) {
                                val updated = infiniteItems.map { item ->
                                    if (item.pageIndex == currentPageIndex && item.layerIndex == index) {
                                        item.withTransform(layerIndex = 1)
                                    } else item
                                }
                                if (updated != infiniteItems) {
                                    infiniteItems = updated
                                    scope.launch(Dispatchers.IO) {
                                        InfiniteItemsStore.save(viewModel.fileStore, viewModel.noteId, updated)
                                        inkController.generateAndSavePagePreview(currentPageIndex)
                                        viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                    }
                                }
                            }
                            layerActionIndex = -1
                        },
                        onDelete = { index ->
                            if (inkController.deleteLayer(currentPageIndex, index)) {
                                val updated = infiniteItems.filterNot {
                                    it.pageIndex == currentPageIndex && it.layerIndex == index
                                }
                                if (updated.size != infiniteItems.size) {
                                    infiniteItems = updated
                                    if ( updated.none { it.id == selectedInfiniteItemId } ) {
                                        selectedInfiniteItemId = null
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        InfiniteItemsStore.save(viewModel.fileStore, viewModel.noteId, updated)
                                        inkController.generateAndSavePagePreview(currentPageIndex)
                                        viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                    }
                                }
                            }
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

                // Full-text Search / Go-to-Page sheet overlay (PDF-QOL-3)
                if (showSearchSheet) {
                    PdfSearchSheet(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        totalPages = totalPages,
                        hasPdfPages = hasPdfPages,
                        isExpanded = isSearchSheetExpanded,
                        onExpandedChange = { isSearchSheetExpanded = it },
                        isIndexing = isIndexingPdf,
                        indexDone = pdfIndexDone,
                        indexTotal = pdfIndexTotal,
                        isScannedPdf = pdfIndexData?.isScannedPdf == true,
                        hits = searchHits,
                        activeHitIndex = activeSearchHitIndex,
                        onJumpToPage = { targetIdx ->
                            searchHighlightTarget = null
                            canvasViewRef?.setSearchHighlightRects(emptyList())
                            jumpToPageFromUi(targetIdx)
                        },
                        onSelectHit = { idx, hit ->
                            navigateToSearchHit(idx, hit)
                        },
                        onPrevHit = {
                            if (searchHits.isNotEmpty()) {
                                val nextIdx = if (activeSearchHitIndex <= 0) {
                                    searchHits.lastIndex
                                } else {
                                    activeSearchHitIndex - 1
                                }
                                navigateToSearchHit(nextIdx, searchHits[nextIdx])
                            }
                        },
                        onNextHit = {
                            if (searchHits.isNotEmpty()) {
                                val nextIdx = if (activeSearchHitIndex !in searchHits.indices ||
                                    activeSearchHitIndex >= searchHits.lastIndex
                                ) {
                                    0
                                } else {
                                    activeSearchHitIndex + 1
                                }
                                navigateToSearchHit(nextIdx, searchHits[nextIdx])
                            }
                        },
                        onDismiss = {
                            showSearchSheet = false
                            searchHighlightTarget = null
                            canvasViewRef?.setSearchHighlightRects(emptyList())
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(20f)
                    )
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
                                        exitPdfTextMode()
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
                                        exitPdfTextMode()
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
                                                        inkController.releaseSessionSuspend()
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
                                                        pageType = note?.pageType ?: "a4",
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
                                                            pageType = note?.pageType ?: "a4",
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
                                        var showOutlineMenu by remember { mutableStateOf(false) }
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
                                                color = Color(0xFF212529),
                                                modifier = Modifier.weight(1f)
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
                                            if (hasPdfPages) {
                                                Box {
                                                    IconButton(
                                                        onClick = { showOutlineMenu = true },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "Outline Options",
                                                            tint = Color.DarkGray
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = showOutlineMenu,
                                                        onDismissRequest = { showOutlineMenu = false }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Re-import PDF outline") },
                                                            leadingIcon = {
                                                                Icon(
                                                                    imageVector = Icons.Default.Refresh,
                                                                    contentDescription = null
                                                                )
                                                            },
                                                            onClick = {
                                                                showOutlineMenu = false
                                                                viewModel.reimportPdfOutline { count ->
                                                                    val msg = if (count > 0) {
                                                                        "Imported $count outline entries"
                                                                    } else {
                                                                        "No outline found in PDF"
                                                                    }
                                                                    android.widget.Toast.makeText(
                                                                        context,
                                                                        msg,
                                                                        android.widget.Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
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
                                                        text = if (hasPdfPages) {
                                                            "No PDF outline found. Tap + to add chapters or re-import from the menu."
                                                        } else {
                                                            "Tap + to add document chapters and sections."
                                                        },
                                                        fontSize = 12.sp,
                                                        color = Color.Gray,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    if (hasPdfPages) {
                                                        TextButton(
                                                            onClick = {
                                                                viewModel.reimportPdfOutline { count ->
                                                                    val msg = if (count > 0) {
                                                                        "Imported $count outline entries"
                                                                    } else {
                                                                        "No outline found in PDF"
                                                                    }
                                                                    android.widget.Toast.makeText(
                                                                        context,
                                                                        msg,
                                                                        android.widget.Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        ) {
                                                            Text(
                                                                text = "Re-import PDF outline",
                                                                color = OrangePrimary,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val activeTocId = remember(uiState.tocEntries, currentPageIndex) {
                                                PdfOutlineImporter.activeEntryId(uiState.tocEntries, currentPageIndex)
                                            }
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(uiState.tocEntries, key = { it.id }) { tocEntry ->
                                                    val isCurrentPage = tocEntry.id == activeTocId
                                                    val indentDp = (tocEntry.level.coerceIn(0, 6) * 12).dp
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
                                                                val targetPage = tocEntry.pageIndex.coerceIn(
                                                                    0,
                                                                    (totalPages - 1).coerceAtLeast(0)
                                                                )
                                                                if (targetPage != currentPageIndex) {
                                                                    if (isContinuous) {
                                                                        viewModel.setCurrentPageIndex(targetPage)
                                                                        scrollToContinuousPage(targetPage)
                                                                    } else {
                                                                        scope.launch {
                                                                            if (canvasViewRef?.layersDirty == true) {
                                                                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                                            }
                                                                            val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                            viewModel.goToPage(targetPage, currentBmp) { newBmp ->
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
                                                                .padding(
                                                                    start = 12.dp + indentDp,
                                                                    end = 12.dp,
                                                                    top = 10.dp,
                                                                    bottom = 10.dp
                                                                ),
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
                                                                    color = if (isCurrentPage) OrangePrimary else Color(0xFF212529),
                                                                    maxLines = 2,
                                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                                                inkController.releaseSessionSuspend()
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
                                                        val templateLabel = if (isInfiniteNote) {
                                                            uiState.infiniteGridPreset.label
                                                        } else {
                                                            uiState.paperPattern.name
                                                        }
                                                        ToolsPanelItem(Icons.Default.GridOn, "Template ($templateLabel)") {
                                                            if (isInfiniteNote) {
                                                                // The infinite canvas draws its own
                                                                // grid presets (Graph/Lined/Dots/Iso/
                                                                // Tri). The paper pattern is the A4
                                                                // page template, so on an infinite
                                                                // note this row has to drive the same
                                                                // state the Precision HUD drives.
                                                                val values = InfiniteGridPreset.values()
                                                                val next = values[(uiState.infiniteGridPreset.ordinal + 1) % values.size]
                                                                viewModel.setInfiniteGridPreset(next)
                                                                android.widget.Toast.makeText(context, "Grid: ${next.label}", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                val nextPattern = when (uiState.paperPattern) {
                                                                    PaperPattern.BLANK -> PaperPattern.GRID
                                                                    PaperPattern.GRID -> PaperPattern.LINES
                                                                    PaperPattern.LINES -> PaperPattern.DOTTED
                                                                    PaperPattern.DOTTED -> PaperPattern.BLANK
                                                                }
                                                                viewModel.setPaperPattern(nextPattern)
                                                                android.widget.Toast.makeText(context, "Paper pattern: ${nextPattern.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                                                        ToolsPanelItem(Icons.Default.Layers, "Layer") {
                                                            if (PdfModeReconciliationPolicy.resolveLayerEntryTarget(isInfiniteNote) ==
                                                                PdfModeReconciliationPolicy.LayerEntryTarget.INFINITE_DRAWING_ONLY
                                                            ) {
                                                                showLayerPanel = true
                                                                showLayersPanel = false
                                                            } else {
                                                                showLayersPanel = true
                                                                showLayerPanel = false
                                                            }
                                                            layerActionIndex = -1
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
                                                            exitPdfTextMode()
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
                                                    subtitle = if (uiState.stylusOnly) {
                                                        "Only the pen writes; the hand presses buttons"
                                                    } else {
                                                        "Hand writes and presses buttons; stylus only when active"
                                                    },
                                                    checked = uiState.stylusOnly,
                                                    enabled = true,
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
                                                // An infinite canvas has no page
                                                // edges: the continuous reader
                                                // would cut it into sheets, so
                                                // the option is not offered.
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
                                                LiquidMorphSegmentedToggle(
                                                    leftLabel = "Vertical",
                                                    rightLabel = "Horizontal",
                                                    isRightSelected = !isVert,
                                                    accentColor = OrangePrimary,
                                                    enabled = true,
                                                    onSelectLeft = { viewModel.setPageTurningDirection(0) },
                                                    onSelectRight = { viewModel.setPageTurningDirection(1) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(34.dp)
                                                )

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
                                                LiquidMorphSegmentedToggle(
                                                    leftLabel = "Continuous",
                                                    rightLabel = "Single",
                                                    isRightSelected = isSingleMode,
                                                    accentColor = OrangePrimary,
                                                    enabled = !isInfiniteNote,
                                                    onSelectLeft = {
                                                        if (!isContinuousMode) {
                                                            scope.launch {
                                                                canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                                    viewModel.saveCurrentPageBitmapDirect(bmp, force = true)
                                                                }
                                                                if (canvasViewRef?.layersDirty == true) {
                                                                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                                                }
                                                                val inkCanvas = canvasViewRef
                                                                if (inkCanvas != null && inkCanvas.hasBeyondPageInk()) {
                                                                    withContext(Dispatchers.IO) {
                                                                        inkCanvas.saveBeyondPageInkNow(viewModel.fileStore, viewModel.noteId)
                                                                        viewModel.repository.touchNoteUpdatedAt(viewModel.noteId)
                                                                    }
                                                                }
                                                                inkController.releaseSessionSuspend()
                                                                viewModel.setPageTurningMode(0)
                                                                val cur = viewModel.currentPageIndex.value
                                                                savedPageGenerations[cur] = (savedPageGenerations[cur] ?: 0) + 1
                                                            }
                                                        }
                                                    },
                                                    onSelectRight = {
                                                        if (!isSingleMode) {
                                                            val maxIdx = (pages.size - 1).coerceAtLeast(0)
                                                            val isHoriz = uiState.pageTurningDirection == 1
                                                            val isDouble = !isHoriz && uiState.doublePageMode
                                                            val activeListState = if (isHoriz) continuousRowState else continuousColState
                                                            val visibleItems = activeListState.layoutInfo.visibleItemsInfo
                                                            val isSettledTarget =
                                                                settledProgrammaticPage in pages.indices &&
                                                                continuousVisibleIndex == settledProgrammaticFirstVis &&
                                                                currentPageIndex == settledProgrammaticPage
                                                            val isCurrentVisible = currentPageIndex in pages.indices && visibleItems.any { item ->
                                                                if (isDouble) {
                                                                    val start = item.index * 2
                                                                    val end = minOf(pages.lastIndex, start + 1)
                                                                    currentPageIndex in start..end
                                                                } else {
                                                                    item.index == currentPageIndex
                                                                }
                                                            }
                                                            val targetIdx = if (isSettledTarget || isCurrentVisible) {
                                                                currentPageIndex.coerceIn(0, maxIdx)
                                                            } else if (isHoriz) {
                                                                continuousRowState.firstVisibleItemIndex.coerceIn(0, maxIdx)
                                                            } else if (isDouble) {
                                                                val pairIdx = continuousColState.firstVisibleItemIndex
                                                                val first = pairIdx * 2
                                                                if (currentPageIndex == first || currentPageIndex == first + 1) {
                                                                    currentPageIndex.coerceIn(0, maxIdx)
                                                                } else {
                                                                    first.coerceIn(0, maxIdx)
                                                                }
                                                            } else {
                                                                continuousColState.firstVisibleItemIndex.coerceIn(0, maxIdx)
                                                            }
                                                            scope.launch {
                                                                inkController.flushCurrentSuspend()
                                                                inkController.releaseSessionSuspend()
                                                                viewModel.setCurrentPageIndex(targetIdx)
                                                                viewModel.setPageTurningMode(1)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(34.dp)
                                                )


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
        targetValue = if (isSelected) 1.10f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "toolScale"
    )
    val liftY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) -2.5f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "toolLift"
    )

    val slotShape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = liftY
            }
            .clip(slotShape)
            .background(
                if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color.White.copy(alpha = 0.62f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    )
                }
            )
            .drawWithContent {
                if (isSelected) {
                    // Luminous inner color bloom under the active tool icon
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                selectedTint.copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.55f),
                            radius = size.minDimension * 0.58f
                        ),
                        radius = size.minDimension * 0.58f,
                        center = Offset(size.width * 0.5f, size.height * 0.55f)
                    )
                }
                drawContent()
            }
            .border(
                width = if (isSelected) 1.25.dp else 0.7.dp,
                brush = if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            selectedTint.copy(alpha = 0.58f),
                            Color.White.copy(alpha = 0.90f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.62f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    )
                },
                shape = slotShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = label,
                tint = if (isSelected) selectedTint else Color(0xFF1E293B),
                modifier = Modifier.size(20.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) selectedTint else Color(0xFF1E293B),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Illustrator/Inkscape-style arrow pointer icon for the Selection tool ([CanvasTool.SELECT]).
 */
@Composable
fun ArrowPointerIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.24f, h * 0.14f)
            lineTo(w * 0.24f, h * 0.82f)
            lineTo(w * 0.43f, h * 0.64f)
            lineTo(w * 0.56f, h * 0.88f)
            lineTo(w * 0.67f, h * 0.82f)
            lineTo(w * 0.54f, h * 0.58f)
            lineTo(w * 0.80f, h * 0.56f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

/**
 * Combined Lasso / Arrow Selection slot with in-place AGSL Liquid Glass Morphing:
 * - Short tap activates [activeTool].
 * - Press & hold (or stylus hover) morphs ONLY the alternate icon out horizontally
 *   to the right via an SDF `smin` liquid droplet bridge.
 * - Sliding your finger right onto the popped-out droplet and releasing (or tapping
 *   it while hovered) switches tools immediately with zero separate Popup window.
 */
@Composable
fun SelectionToolSlot(
    activeTool: CanvasTool,
    isSelected: Boolean,
    selectedTint: Color = OrangePrimary,
    expandDown: Boolean = true,
    onSelectLasso: () -> Unit,
    onSelectArrow: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var hoverSecond by remember { mutableStateOf(false) }
    val alternateTool = if (activeTool == CanvasTool.SELECT) CanvasTool.LASSO else CanvasTool.SELECT

    val density = LocalDensity.current
    val slotDp = 36.dp
    val gapDp = 8.dp
    val slotSizePx = with(density) { slotDp.toPx() }
    val gapPx = with(density) { gapDp.toPx() }
    val radiusPx = with(density) { 13.dp.toPx() }

    val morphProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.52f,
            stiffness = 360f
        ),
        label = "liquidSelectionMorph"
    )
    val hoverSecondAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (hoverSecond) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.65f,
            stiffness = 450f
        ),
        label = "liquidSelectionHoverSecond"
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "selectSlotScale"
    )

    val clampedMorph = morphProgress.coerceAtLeast(0f)
    val iconTint = if (isSelected) selectedTint else Color(0xFF1E293B)
    val slotShape = RoundedCornerShape(13.dp)

    fun triggerTool(tool: CanvasTool) {
        if (tool == CanvasTool.SELECT) onSelectArrow() else onSelectLasso()
    }

    // Anchor stays a fixed 36x36dp slot so neither horizontal nor vertical dock ever shifts or clips.
    Box(
        modifier = Modifier
            .size(slotDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(slotShape)
            .background(
                if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.62f))
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.10f))
                    )
                }
            )
            .border(
                width = if (isSelected) 1.25.dp else 0.7.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                shape = slotShape
            )
            // Stylus hover opens the liquid droplet overlay; leaving collapses it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val isStylusHover = event.changes.any {
                            it.type == PointerType.Stylus || it.type == PointerType.Eraser
                        }
                        if (isStylusHover && event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter) {
                            expanded = true
                        }
                    }
                }
            }
            // Local finger hold + slide (down in horizontal dock, right in vertical dock).
            .pointerInput(activeTool, expandDown) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    var released = false
                    val held = withTimeoutOrNull(longPressTimeout) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                released = true
                                break
                            }
                        }
                    }
                    if (released) {
                        triggerTool(activeTool)
                        expanded = false
                        hoverSecond = false
                        return@awaitEachGesture
                    }
                    if (held == null) {
                        expanded = true
                        hoverSecond = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            val axisPos = if (expandDown) ch.position.y else ch.position.x
                            hoverSecond = axisPos > slotSizePx * 0.72f
                            ch.consume()
                            if (!ch.pressed) {
                                if (hoverSecond) {
                                    triggerTool(alternateTool)
                                } else {
                                    triggerTool(activeTool)
                                }
                                expanded = false
                                hoverSecond = false
                                break
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (activeTool == CanvasTool.SELECT) {
            ArrowPointerIcon(tint = iconTint, modifier = Modifier.size(20.dp))
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_tool_lasso),
                contentDescription = "Lasso",
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        // Unclipped liquid morphing overlay anchored directly over this 36x36dp slot:
        // pops downward in Horizontal dock, or horizontally to the right in Vertical dock.
        if (morphProgress > 0.02f) {
            val fullSpanDp = slotDp + gapDp + slotDp
            val overlayW = if (expandDown) slotDp else fullSpanDp
            val overlayH = if (expandDown) fullSpanDp else slotDp
            val anchorPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.top)
                }
            }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = anchorPositionProvider,
                onDismissRequest = {
                    expanded = false
                    hoverSecond = false
                },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = false,
                    clippingEnabled = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .width(overlayW)
                        .height(overlayH)
                        .liquidMorphDropletsBackground(
                            slotSizePx = slotSizePx,
                            gapPx = gapPx,
                            radiusPx = radiusPx,
                            morphProgress = morphProgress,
                            hoverSecond = hoverSecondAnim,
                            accentColor = selectedTint,
                            expandDown = expandDown
                        )
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val isStylusHover = event.changes.any {
                                        it.type == PointerType.Stylus || it.type == PointerType.Eraser
                                    }
                                    if (isStylusHover) {
                                        when (event.type) {
                                            androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                                val p = event.changes.firstOrNull()?.position ?: Offset.Zero
                                                hoverSecond = (if (expandDown) p.y else p.x) > slotSizePx * 0.85f
                                            }
                                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> {
                                                expanded = false
                                                hoverSecond = false
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // Anchor icon (keeps active icon crisp on top of the liquid bridge)
                    Box(
                        modifier = Modifier
                            .size(slotDp)
                            .align(Alignment.TopStart)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerTool(activeTool)
                                expanded = false
                                hoverSecond = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeTool == CanvasTool.SELECT) {
                            ArrowPointerIcon(tint = iconTint, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tool_lasso),
                                contentDescription = "Lasso",
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Extended alternate droplet (ONLY the alternate icon)
                    val altTint = if (hoverSecond) selectedTint else Color(0xFF334155)
                    val altScale = (0.55f + 0.45f * morphProgress.coerceIn(0f, 1.15f)) *
                        (if (hoverSecond) 1.14f else 1.0f)
                    val altOffsetX = if (expandDown) 0.dp else (slotDp + gapDp) * clampedMorph
                    val altOffsetY = if (expandDown) (slotDp + gapDp) * clampedMorph else 0.dp
                    Box(
                        modifier = Modifier
                            .size(slotDp)
                            .align(Alignment.TopStart)
                            .offset(x = altOffsetX, y = altOffsetY)
                            .graphicsLayer {
                                scaleX = altScale
                                scaleY = altScale
                                alpha = morphProgress.coerceIn(0f, 1f)
                            }
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerTool(alternateTool)
                                expanded = false
                                hoverSecond = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (alternateTool == CanvasTool.SELECT) {
                            ArrowPointerIcon(tint = altTint, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tool_lasso),
                                contentDescription = "Lasso",
                                tint = altTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
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
    /** Root-space bounding box of the current page item in the continuous reader. */
    var pageBox: ReaderPageBox? = null
}

/**
 * On-screen placement of one reader page, in root pixels. Plain fields: the
 * value is written from `onGloballyPositioned`, which runs on every scroll
 * frame, and must not invalidate composition.
 */
class ReaderPageBox {
    var pageIndex = -1
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

/**
 * What a pinch needs to know about the reader's layout: the page under the
 * fingers, where it is and how big it is.
 *
 * The pages are measured at the zoom (`pageWidthDp = screenWidth *
 * continuousScale`), so the page under the fingers is as big as the zoom
 * says. Its place in the window comes from the list: the list lays the
 * pages out one after the other and centres them across the window, so the
 * page under the fingers is centred too - and the two finger pan moves it
 * away from there.
 *
 * The list knows where the pages are: [androidx.compose.foundation.lazy.LazyListLayoutInfo]
 * holds the visible pages with their place in the window.
 */
private fun readerLayout(
    listState: androidx.compose.foundation.lazy.LazyListState,
    centroid: androidx.compose.ui.geometry.Offset,
    horizontal: Boolean,
    doublePage: Boolean,
    scale: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    pageSpacingPx: Float,
    crossPan: Float
): ReaderTransform.Layout {
    val info = listState.layoutInfo
    val viewport = info.viewportSize
    val items = info.visibleItemsInfo
    val main = if (horizontal) centroid.x else centroid.y
    val item = items.firstOrNull { main >= it.offset && main < it.offset + it.size }
    val firstIndex = items.firstOrNull()?.index ?: 0
    // The page under the fingers: as wide as the screen in a column, as tall
    // as the screen in a row - and as long as the list measured it on the
    // other axis.
    val pageWidth = if (horizontal) {
        (item?.size ?: 0).toFloat()
    } else {
        screenWidthPx * scale
    }
    val pageHeight = if (horizontal) {
        screenHeightPx * scale
    } else {
        (item?.size ?: 0).toFloat()
    }
    // The page is centred in the window and pulled aside by the pan.
    val crossCentre = if (horizontal) viewport.height / 2f else viewport.width / 2f
    var crossSize = if (horizontal) pageHeight else pageWidth
    var crossOrigin = crossCentre - crossSize / 2f + crossPan
    if (doublePage && !horizontal) {
        // Two pages share one row: the page under the fingers is one of the
        // two halves of it, so the row is not the page.
        val rowWidth = screenWidthPx * scale
        val halfWidth = (rowWidth - pageSpacingPx) / 2f
        val rowLeft = crossCentre - rowWidth / 2f + crossPan
        val column = if (centroid.x < rowLeft + halfWidth + pageSpacingPx / 2f) 0 else 1
        crossSize = halfWidth
        crossOrigin = rowLeft + column * (halfWidth + pageSpacingPx)
    }
    // The list scrolls along the other axis: the place of the page under the
    // fingers is the place the list put it.
    val scrollOrigin = (item?.offset ?: 0).toFloat()
    return ReaderTransform.Layout(
        viewportWidth = viewport.width.toFloat(),
        viewportHeight = viewport.height.toFloat(),
        horizontal = horizontal,
        pageWidth = if (horizontal) pageWidth else crossSize,
        pageHeight = if (horizontal) crossSize else pageHeight,
        originX = if (horizontal) scrollOrigin else crossOrigin,
        originY = if (horizontal) crossOrigin else scrollOrigin,
        // The pages above the one under the fingers grow with the zoom and
        // push it down.
        pagesBefore = (item?.index ?: firstIndex) - firstIndex
    )
}

private enum class PrecisionHudStage {
    EXPANDED,
    COLLAPSED,
    MINI_COLLAPSED
}

@Composable
private fun PrecisionDotsIcon(
    tint: Color = Color(0xFF1F2937),
    modifier: Modifier = Modifier.size(18.dp)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val r = size.minDimension * 0.10f
        val step = size.minDimension * 0.30f
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (row in -1..1) {
            for (col in -1..1) {
                drawCircle(
                    color = tint,
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(cx + col * step, cy + row * step)
                )
            }
        }
    }
}

@Composable
private fun PrecisionOptionRow(
    isChecked: Boolean,
    label: String,
    valueText: String,
    accentColor: Color = Color(0xFF1F2937),
    valueColor: Color = Color(0xFF4B5563),
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFDCDFE6)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isChecked) accentColor else Color.Transparent)
                    .border(1.2.dp, accentColor, RoundedCornerShape(3.dp))
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color(0xFFE5E7EB))
            )
            Text(
                text = valueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = valueColor
            )
        }
    }
}

@Composable
fun InfiniteCanvasHud(
    scale: Float,
    panX: Float,
    panY: Float,
    gridPreset: InfiniteGridPreset,
    snapToGrid: Boolean,
    strokeOpacity: Float,
    onResetTransform: () -> Unit,
    onCycleGridPreset: () -> Unit,
    onToggleSnap: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onQuickClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by remember { mutableStateOf(PrecisionHudStage.COLLAPSED) }
    var lastInteractionTick by remember { mutableIntStateOf(0) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(stage, lastInteractionTick) {
        if (stage == PrecisionHudStage.COLLAPSED) {
            kotlinx.coroutines.delay(1200L)
            stage = PrecisionHudStage.MINI_COLLAPSED
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (stage) {
            PrecisionHudStage.MINI_COLLAPSED -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color(0xFFDCDFE6)),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            stage = PrecisionHudStage.EXPANDED
                            lastInteractionTick++
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrecisionDotsIcon(modifier = Modifier.size(20.dp))
                    }
                }
            }

            PrecisionHudStage.COLLAPSED -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color(0xFFDCDFE6)),
                    modifier = Modifier.clickable {
                        stage = PrecisionHudStage.EXPANDED
                        lastInteractionTick++
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrecisionDotsIcon(modifier = Modifier.size(18.dp))
                        Text(
                            text = "Precision",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                    }
                }
            }

            PrecisionHudStage.EXPANDED -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color(0xFFDCDFE6)),
                    modifier = Modifier.clickable {
                        showOpacitySlider = false
                        stage = PrecisionHudStage.COLLAPSED
                        lastInteractionTick++
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrecisionDotsIcon(modifier = Modifier.size(18.dp))
                        Text(
                            text = "Precision",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                    }
                }

                PrecisionOptionRow(
                    isChecked = gridPreset != InfiniteGridPreset.BLANK,
                    label = "Grid",
                    valueText = gridPreset.label,
                    onClick = {
                        lastInteractionTick++
                        onCycleGridPreset()
                    }
                )

                PrecisionOptionRow(
                    isChecked = snapToGrid,
                    label = "Snap",
                    valueText = if (snapToGrid) "ON" else "OFF",
                    accentColor = if (snapToGrid) OrangePrimary else Color(0xFF1F2937),
                    onClick = {
                        lastInteractionTick++
                        onToggleSnap()
                    }
                )

                PrecisionOptionRow(
                    isChecked = strokeOpacity < 0.99f || showOpacitySlider,
                    label = "Alpha",
                    valueText = "${(strokeOpacity * 100).roundToInt()}%",
                    accentColor = if (showOpacitySlider) OrangePrimary else Color(0xFF1F2937),
                    onClick = {
                        lastInteractionTick++
                        showOpacitySlider = !showOpacitySlider
                    }
                )

                if (showOpacitySlider) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.96f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, Color(0xFFDCDFE6))
                    ) {
                        Row(
                            modifier = Modifier
                                .width(200.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = strokeOpacity,
                                onValueChange = {
                                    lastInteractionTick++
                                    onOpacityChange(it)
                                },
                                valueRange = 0.05f..1.0f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                PrecisionOptionRow(
                    isChecked = false,
                    label = "Clear",
                    valueText = "Layer",
                    accentColor = Color(0xFFDC2626),
                    valueColor = Color(0xFFDC2626),
                    onClick = {
                        lastInteractionTick++
                        showClearConfirm = true
                    }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Current Layer?") },
            text = { Text("All strokes on the active layer will be cleared. You can undo this action.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onQuickClear()
                    }
                ) {
                    Text("Clear", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val SNAP_AXES_LINED = arrayOf(1f to 0f)
private val SNAP_AXES_ORTHO = arrayOf(1f to 0f, 0f to 1f)
private val SNAP_AXES_TRIANGLE = arrayOf(
    1f to 0f,
    0.5f to 0.8660254f,
    -0.5f to 0.8660254f
)
private val SNAP_AXES_ISOMETRIC = arrayOf(
    1f to 0f,
    0f to 1f,
    0.5f to 0.8660254f,
    -0.5f to 0.8660254f,
    0.8660254f to 0.5f,
    0.8660254f to -0.5f
)

private fun gridSnapAxes(preset: InfiniteGridPreset): Array<Pair<Float, Float>> = when (preset) {
    InfiniteGridPreset.LINED -> SNAP_AXES_LINED
    InfiniteGridPreset.GRAPH, InfiniteGridPreset.DOTS, InfiniteGridPreset.BLANK -> SNAP_AXES_ORTHO
    InfiniteGridPreset.TRIANGLE -> SNAP_AXES_TRIANGLE
    InfiniteGridPreset.ISOMETRIC -> SNAP_AXES_ISOMETRIC
}

private fun bestGridAxis(dx: Float, dy: Float, axes: Array<Pair<Float, Float>>): Pair<Float, Float> {
    var best = axes[0]
    var bestScore = -1f
    for (axis in axes) {
        val score = kotlin.math.abs(dx * axis.first + dy * axis.second)
        if (score > bestScore) {
            bestScore = score
            best = axis
        }
    }
    return best
}

private fun snapToInfiniteGrid(
    x: Float,
    y: Float,
    step: Float,
    panX: Float,
    panY: Float,
    preset: InfiniteGridPreset
): Pair<Float, Float> {
    if (step < 8f || preset == InfiniteGridPreset.BLANK) return Pair(x, y)
    return when (preset) {
        InfiniteGridPreset.LINED -> {
            val sy = ((y - panY) / step).roundToInt() * step + panY
            Pair(x, sy)
        }
        InfiniteGridPreset.GRAPH, InfiniteGridPreset.DOTS -> {
            val sx = ((x - panX) / step).roundToInt() * step + panX
            val sy = ((y - panY) / step).roundToInt() * step + panY
            Pair(sx, sy)
        }
        InfiniteGridPreset.ISOMETRIC, InfiniteGridPreset.TRIANGLE -> {
            val rowH = step * 0.8660254f
            val row = ((y - panY) / rowH).roundToInt()
            val sy = row * rowH + panY
            val offsetX = if (row % 2 == 0) 0f else step * 0.5f
            val sx = ((x - panX - offsetX) / step).roundToInt() * step + panX + offsetX
            Pair(sx, sy)
        }
        InfiniteGridPreset.BLANK -> Pair(x, y)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun InfiniteCanvasView(
    page: personal.notes.touch.data.db.entity.PageEntity,
    pageIndex: Int,
    noteId: String,
    ink: ContinuousInkController,
    inkSettings: InkToolSettings,
    scale: Float,
    panX: Float,
    panY: Float,
    backgroundDensity: Int,
    gridPreset: InfiniteGridPreset,
    snapToGrid: Boolean,
    strokeOpacity: Float,
    items: List<InfiniteCanvasItem>,
    selectedItemId: String?,
    bitmapCache: InfiniteItemBitmapCache,
    onSelectItem: (String?) -> Unit,
    onUpdateItem: (InfiniteCanvasItem) -> Unit,
    onDuplicateItem: (InfiniteCanvasItem) -> Unit,
    onCommitItems: () -> Unit,
    onDeleteItem: (String) -> Unit,
    onResetTransform: () -> Unit,
    onCycleGridPreset: () -> Unit,
    onSnapToggle: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onQuickClear: () -> Unit,
    onStylusDetected: () -> Unit,
    onPanZoom: (Float, Float, Float, androidx.compose.ui.geometry.Offset) -> Unit,
    onDismissOverlays: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_EXPRESSION")
    ink.layersRevision
    val layerStack = ink.layerStack(pageIndex)

    // The lasso selection belongs to the lasso tool: leaving the tool drops it.
    androidx.compose.runtime.LaunchedEffect(inkSettings.tool) {
        if (inkSettings.tool != CanvasTool.LASSO && inkSettings.tool != CanvasTool.SELECT) {
            ink.lassoClear()
        }
        if (inkSettings.tool == CanvasTool.LASSO) {
            onSelectItem(null)
        }
    }
    androidx.compose.runtime.LaunchedEffect(pageIndex) {
        ink.lassoClear()
        onSelectItem(null)
    }

    val currentPageIndexState by androidx.compose.runtime.rememberUpdatedState(pageIndex)
    val currentScale by androidx.compose.runtime.rememberUpdatedState(scale)
    val currentPanX by androidx.compose.runtime.rememberUpdatedState(panX)
    val currentPanY by androidx.compose.runtime.rememberUpdatedState(panY)
    val currentSnap by androidx.compose.runtime.rememberUpdatedState(snapToGrid)
    val currentGridPreset by androidx.compose.runtime.rememberUpdatedState(gridPreset)
    val currentDensity by androidx.compose.runtime.rememberUpdatedState(backgroundDensity)
    val currentInkSettings by androidx.compose.runtime.rememberUpdatedState(inkSettings)
    val currentItems by androidx.compose.runtime.rememberUpdatedState(items)
    val currentSelectedItemId by androidx.compose.runtime.rememberUpdatedState(selectedItemId)

    val itemPaint = remember {
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    }
    val paperFill = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
    }
    val paperBorder = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD0D5DD.toInt()
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val selStroke = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFB6443.toInt()
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val handleFill = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
    }
    val deleteFill = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFDC2626.toInt()
            style = android.graphics.Paint.Style.FILL
        }
    }
    val rotateFill = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2563EB.toInt()
            style = android.graphics.Paint.Style.FILL
        }
    }
    val mirrorFill = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0D9488.toInt()
            style = android.graphics.Paint.Style.FILL
        }
    }
    val copyFill = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEA580C.toInt()
            style = android.graphics.Paint.Style.FILL
        }
    }
    val iconPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val itemDstRect = remember { android.graphics.RectF() }

    LaunchedEffect(page.id, pageIndex, noteId) {
        ink.ensureSessionForPage(page, pageIndex)
    }

    Box(modifier = modifier) {
        // 1. Base Canvas: Grid + Frustum-Culled Items + Frustum-Culled Committed Strokes.
        // Does NOT observe previewRevision, so live tool strokes never redraw PDFs or committed ink.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            ink.revision
            @Suppress("UNUSED_EXPRESSION")
            bitmapCache.renderTick

            ink.updateGeometry(pageIndex, 2160f * scale, 2160f * scale)
            drawRect(color = Color.White)

            if (gridPreset != InfiniteGridPreset.BLANK) {
                val density = backgroundDensity.coerceIn(20, 80).toFloat()
                val step = density * 2f * scale
                if (step >= 8f) {
                    val gridColor = Color(0xFFE2E4E8)
                    val startX = ((panX % step) + step) % step
                    val startY = ((panY % step) + step) % step
                    when (gridPreset) {
                        InfiniteGridPreset.LINED -> {
                            var y = startY
                            while (y < size.height) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                                y += step
                            }
                        }
                        InfiniteGridPreset.GRAPH -> {
                            var x = startX
                            while (x < size.width) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                                x += step
                            }
                            var y = startY
                            while (y < size.height) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                                y += step
                            }
                        }
                        InfiniteGridPreset.DOTS -> {
                            var x = startX
                            while (x < size.width) {
                                var y = startY
                                while (y < size.height) {
                                    drawCircle(
                                        color = gridColor,
                                        radius = 1.8f,
                                        center = androidx.compose.ui.geometry.Offset(x, y)
                                    )
                                    y += step
                                }
                                x += step
                            }
                        }
                        InfiniteGridPreset.ISOMETRIC -> {
                            val rowH = step * 0.8660254f
                            val startIsoY = ((panY % rowH) + rowH) % rowH
                            var row = 0
                            var y = startIsoY
                            while (y < size.height) {
                                val offsetX = if (row % 2 == 0) 0f else step * 0.5f
                                var x = ((startX + offsetX) % step)
                                while (x < size.width) {
                                    drawCircle(
                                        color = gridColor,
                                        radius = 1.8f,
                                        center = androidx.compose.ui.geometry.Offset(x, y)
                                    )
                                    x += step
                                }
                                y += rowH
                                row++
                            }
                        }
                        InfiniteGridPreset.TRIANGLE -> {
                            val rowH = step * 0.8660254f
                            val startTriY = ((panY % rowH) + rowH) % rowH
                            var y = startTriY
                            while (y < size.height) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 0.8f
                                )
                                y += rowH
                            }
                            val slopeDx = size.height / 1.7320508f
                            var x = startX - slopeDx
                            while (x < size.width + slopeDx) {
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x + slopeDx, size.height),
                                    strokeWidth = 0.8f
                                )
                                drawLine(
                                    color = gridColor,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x - slopeDx, size.height),
                                    strokeWidth = 0.8f
                                )
                                x += step
                            }
                        }
                        InfiniteGridPreset.BLANK -> {}
                    }
                }
            }

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val safeScale = scale.coerceAtLeast(0.05f)
                val viewLeft = -panX / safeScale
                val viewTop = -panY / safeScale
                val viewRight = (size.width - panX) / safeScale
                val viewBottom = (size.height - panY) / safeScale
                val count = native.save()
                native.translate(panX, panY)
                native.scale(scale, scale)

                paperBorder.strokeWidth = (1.5f / safeScale).coerceAtLeast(0.5f)

                val normMinX = viewLeft / 2160f
                val normMinY = viewTop / 2160f
                val normMaxX = viewRight / 2160f
                val normMaxY = viewBottom / 2160f

                layerStack.layers.forEach { layer ->
                    if (layer.visible) {
                        for (item in items) {
                            if (item.pageIndex != pageIndex || item.layerIndex != layer.index) continue
                            val halfSpan = if (item.rotationDeg == 0f) 0f else maxOf(item.width, item.height) * 0.72f
                            val halfW = if (item.rotationDeg == 0f) item.width * 0.5f + 32f else halfSpan
                            val halfH = if (item.rotationDeg == 0f) item.height * 0.5f + 32f else halfSpan
                            if (item.centerX + halfW < viewLeft || item.centerX - halfW > viewRight ||
                                item.centerY + halfH < viewTop || item.centerY - halfH > viewBottom
                            ) {
                                continue
                            }

                            val bmp = when (item) {
                                is InfiniteCanvasItem.ImageItem -> bitmapCache.getImageBitmap(item.fileName)
                                is InfiniteCanvasItem.PdfPageItem -> bitmapCache.getPdfPageBitmap(item, scale)
                            }
                            val itemSave = native.save()
                            native.translate(item.centerX, item.centerY)
                            if (item.rotationDeg != 0f) {
                                native.rotate(item.rotationDeg)
                            }
                            itemDstRect.set(
                                -item.width / 2f,
                                -item.height / 2f,
                                item.width / 2f,
                                item.height / 2f
                            )
                            val bmpSave = native.save()
                            if (item.flipHorizontal) {
                                native.scale(-1f, 1f)
                            }
                            if (item is InfiniteCanvasItem.PdfPageItem) {
                                native.drawRect(itemDstRect, paperFill)
                            }
                            if (bmp != null && !bmp.isRecycled) {
                                native.drawBitmap(bmp, null, itemDstRect, itemPaint)
                            }
                            if (item is InfiniteCanvasItem.PdfPageItem) {
                                native.drawRect(itemDstRect, paperBorder)
                            }
                            native.restoreToCount(bmpSave)
                            if (item.id == selectedItemId) {
                                val inv = 1f / safeScale.coerceAtLeast(0.1f)
                                selStroke.strokeWidth = (2.5f * inv).coerceAtLeast(1f)
                                native.drawRect(itemDstRect, selStroke)
                                val r = 16f * inv
                                iconPaint.textSize = 17f * inv
                                // Edge midpoint resize pills
                                val pillLong = r * 0.9f
                                val pillShort = r * 0.34f
                                fun drawEdgePill(cx: Float, cy: Float, horiz: Boolean) {
                                    val hw = if (horiz) pillLong else pillShort
                                    val hh = if (horiz) pillShort else pillLong
                                    val pr = android.graphics.RectF(cx - hw, cy - hh, cx + hw, cy + hh)
                                    native.drawRoundRect(pr, pillShort, pillShort, handleFill)
                                    native.drawRoundRect(pr, pillShort, pillShort, selStroke)
                                }
                                drawEdgePill(0f, itemDstRect.top, true)
                                drawEdgePill(0f, itemDstRect.bottom, true)
                                drawEdgePill(itemDstRect.left, 0f, false)
                                drawEdgePill(itemDstRect.right, 0f, false)

                                // Corner 1 (Top-Left, Blue): Rotate
                                native.drawCircle(itemDstRect.left, itemDstRect.top, r, rotateFill)
                                native.drawText("↻", itemDstRect.left, itemDstRect.top + r * 0.35f, iconPaint)

                                // Beside Corner 1 (Teal): Mirror horizontal flip
                                val mx = (itemDstRect.left + r * 2.4f).coerceAtMost(0f)
                                native.drawCircle(mx, itemDstRect.top, r, mirrorFill)
                                native.drawText("⇄", mx, itemDstRect.top + r * 0.35f, iconPaint)

                                // Corner 2 (Top-Right, Red): Delete / Cut
                                native.drawCircle(itemDstRect.right, itemDstRect.top, r, deleteFill)
                                native.drawText("✕", itemDstRect.right, itemDstRect.top + r * 0.35f, iconPaint)

                                // Corner 3 (Bottom-Left, Orange): Copy (tap & drag)
                                native.drawCircle(itemDstRect.left, itemDstRect.bottom, r, copyFill)
                                native.drawText("+", itemDstRect.left, itemDstRect.bottom + r * 0.35f, iconPaint)

                                // Corner 4 (Bottom-Right, White): 1:1 Resize
                                native.drawCircle(itemDstRect.right, itemDstRect.bottom, r * 0.68f, handleFill)
                                native.drawCircle(itemDstRect.right, itemDstRect.bottom, r * 0.68f, selStroke)
                            }
                            native.restoreToCount(itemSave)
                        }

                        val liveErase = ink.liveEraserStroke(pageIndex, layer)
                        if (liveErase != null) {
                            @Suppress("UNUSED_EXPRESSION")
                            ink.previewRevision
                        }
                        val hasErase = ink.layerHasErase(pageIndex, layer) || liveErase != null
                        val layerSave = if (hasErase) {
                            native.saveLayer(
                                viewLeft,
                                viewTop,
                                viewRight,
                                viewBottom,
                                null
                            )
                        } else {
                            -1
                        }
                        ink.drawOpsInfinite(
                            native,
                            pageIndex,
                            layer,
                            normMinX,
                            normMinY,
                            normMaxX,
                            normMaxY
                        )
                        if (layerSave >= 0) {
                            native.restoreToCount(layerSave)
                        }
                    }
                }

                native.restoreToCount(count)
            }
        }

        // 2. Live Preview Overlay Canvas: only redraws the single active stroke in progress
        // or the laser pointer dot, keeping stylus responsiveness at 120Hz regardless of zoom or PDFs.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            ink.previewRevision
            @Suppress("UNUSED_EXPRESSION")
            ink.laserRevision
            @Suppress("UNUSED_EXPRESSION")
            ink.lassoRevision
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val count = native.save()
                native.translate(panX, panY)
                native.scale(scale, scale)
                ink.drawPreviewInfinite(native, pageIndex, scale)
                native.restoreToCount(count)
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(page.id, pageIndex) {
                    try {
                        awaitEachGesture {
                            var pendingDown: androidx.compose.ui.input.pointer.PointerInputChange? =
                                awaitFirstDown(requireUnconsumed = false)
                            while (pendingDown != null) {
                            val down = pendingDown
                            pendingDown = null
                            // Read latest ink settings AFTER awaitFirstDown so tool/color/width
                            // changes made while idle take effect immediately on the first stroke.
                            val settings = currentInkSettings
                            if (settings.readingMode) return@awaitEachGesture
                            onDismissOverlays()
                            val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                            if (isStylus) onStylusDetected()
                            val isAllowedPointer = isStylus || (!settings.stylusOnly && down.type == PointerType.Touch)

                            val downWorldX = (down.position.x - currentPanX) / currentScale.coerceAtLeast(0.1f)
                            val downWorldY = (down.position.y - currentPanY) / currentScale.coerceAtLeast(0.1f)
                            val curPageIdx = currentPageIndexState
                            val curLayerStack = ink.layerStack(curPageIdx)
                            val isLayerVis: (Int) -> Boolean = { lIdx ->
                                curLayerStack.layerAt(lIdx)?.visible != false
                            }
                            val selItem = currentSelectedItemId?.let { id ->
                                currentItems.firstOrNull {
                                    it.id == id && it.pageIndex == curPageIdx && isLayerVis(it.layerIndex)
                                }
                            }
                            var activeItem: InfiniteCanvasItem? = null
                            var activeHandle = InfiniteItemHandle.NONE
                            if (selItem != null) {
                                val h = hitTestInfiniteItemHandle(downWorldX, downWorldY, selItem, currentScale)
                                if (h != InfiniteItemHandle.NONE) {
                                    activeItem = selItem
                                    activeHandle = h
                                }
                            }
                            val preHitLassoHandle = ink.hitTestLassoHandle(downWorldX, downWorldY, currentScale)
                            if (activeHandle == InfiniteItemHandle.NONE && settings.tool == CanvasTool.SELECT &&
                                preHitLassoHandle == LassoHandle.NONE
                            ) {
                                val hit = hitTestInfiniteItems(
                                    downWorldX,
                                    downWorldY,
                                    currentItems,
                                    pageIndex = curPageIdx,
                                    isLayerVisible = isLayerVis
                                )
                                if (hit != null) {
                                    ink.lassoClear()
                                    onSelectItem(hit.id)
                                    activeItem = hit
                                    activeHandle = InfiniteItemHandle.MOVE
                                } else {
                                    onSelectItem(null)
                                }
                            }
                            if (activeItem != null && activeHandle != InfiniteItemHandle.NONE) {
                                down.consume()
                                if (activeHandle == InfiniteItemHandle.DELETE) {
                                    onDeleteItem(activeItem.id)
                                    return@awaitEachGesture
                                }
                                if (activeHandle == InfiniteItemHandle.MIRROR) {
                                    onUpdateItem(activeItem.withTransform(flipHorizontal = !activeItem.flipHorizontal))
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        change.consume()
                                        if (change.changedToUpIgnoreConsumed()) break
                                    }
                                    onCommitItems()
                                    return@awaitEachGesture
                                }
                                val startItem = if (activeHandle == InfiniteItemHandle.COPY) {
                                    val copy = activeItem.duplicateWithNewId()
                                    onDuplicateItem(copy)
                                    copy
                                } else {
                                    activeItem
                                }
                                val effectiveHandle = if (activeHandle == InfiniteItemHandle.COPY) {
                                    InfiniteItemHandle.MOVE
                                } else {
                                    activeHandle
                                }
                                val startCx = startItem.centerX
                                val startCy = startItem.centerY
                                val startW = startItem.width
                                val startH = startItem.height
                                val startRot = startItem.rotationDeg
                                val startDist = kotlin.math.hypot(
                                    (downWorldX - startCx).toDouble(),
                                    (downWorldY - startCy).toDouble()
                                ).toFloat().coerceAtLeast(10f)
                                val startAngle = Math.toDegrees(
                                    kotlin.math.atan2((downWorldY - startCy).toDouble(), (downWorldX - startCx).toDouble())
                                ).toFloat()
                                var movedDist = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val curWx = (change.position.x - currentPanX) / currentScale.coerceAtLeast(0.1f)
                                    val curWy = (change.position.y - currentPanY) / currentScale.coerceAtLeast(0.1f)
                                    movedDist = maxOf(
                                        movedDist,
                                        kotlin.math.hypot((curWx - downWorldX).toDouble(), (curWy - downWorldY).toDouble()).toFloat()
                                    )
                                    val updated = when (effectiveHandle) {
                                        InfiniteItemHandle.MOVE -> startItem.withTransform(
                                            centerX = startCx + (curWx - downWorldX),
                                            centerY = startCy + (curWy - downWorldY)
                                        )
                                        InfiniteItemHandle.RESIZE -> {
                                            val curDist = kotlin.math.hypot(
                                                (curWx - startCx).toDouble(),
                                                (curWy - startCy).toDouble()
                                            ).toFloat().coerceAtLeast(10f)
                                            val factor = (curDist / startDist).coerceIn(0.15f, 8.0f)
                                            startItem.withTransform(
                                                width = (startW * factor).coerceIn(80f, 8000f),
                                                height = (startH * factor).coerceIn(80f, 8000f)
                                            )
                                        }
                                        InfiniteItemHandle.SCALE_LEFT,
                                        InfiniteItemHandle.SCALE_RIGHT -> {
                                            val (lx, _) = worldToItemLocal(curWx, curWy, startItem)
                                            val newW = (kotlin.math.abs(lx) * 2f).coerceIn(80f, 8000f)
                                            startItem.withTransform(width = newW)
                                        }
                                        InfiniteItemHandle.SCALE_TOP,
                                        InfiniteItemHandle.SCALE_BOTTOM -> {
                                            val (_, ly) = worldToItemLocal(curWx, curWy, startItem)
                                            val newH = (kotlin.math.abs(ly) * 2f).coerceIn(80f, 8000f)
                                            startItem.withTransform(height = newH)
                                        }
                                        InfiniteItemHandle.ROTATE -> {
                                            val curAngle = Math.toDegrees(
                                                kotlin.math.atan2((curWy - startCy).toDouble(), (curWx - startCx).toDouble())
                                            ).toFloat()
                                            startItem.withTransform(rotationDeg = startRot + (curAngle - startAngle))
                                        }
                                        else -> startItem
                                    }
                                    onUpdateItem(updated)
                                    change.consume()
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                                if (activeHandle == InfiniteItemHandle.COPY && movedDist < 8f) {
                                    val step = 36f / currentScale.coerceAtLeast(0.1f)
                                    onUpdateItem(
                                        startItem.withTransform(
                                            centerX = startCx + step,
                                            centerY = startCy + step
                                        )
                                    )
                                }
                                onCommitItems()
                                return@awaitEachGesture
                            } else if (selItem != null && settings.isWritingTool) {
                                onSelectItem(null)
                            }

                            if (settings.isLaserPointer) {
                                val sScale = currentScale
                                val sPanX = currentPanX
                                val sPanY = currentPanY
                                val baseW = 2160f
                                val wx = (down.position.x - sPanX) / (sScale * baseW)
                                val wy = (down.position.y - sPanY) / (sScale * baseW)
                                ink.updateLaser(pageIndex, wx, wy)
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    for (hist in change.historical) {
                                        val hwx = (hist.position.x - currentPanX) / (currentScale * baseW)
                                        val hwy = (hist.position.y - currentPanY) / (currentScale * baseW)
                                        ink.updateLaser(pageIndex, hwx, hwy)
                                    }
                                    val cwx = (change.position.x - currentPanX) / (currentScale * baseW)
                                    val cwy = (change.position.y - currentPanY) / (currentScale * baseW)
                                    ink.updateLaser(pageIndex, cwx, cwy)
                                    change.consume()
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                                ink.clearLaser()
                                return@awaitEachGesture
                            }

                            val isSelectionTool = settings.tool == CanvasTool.LASSO || settings.tool == CanvasTool.SELECT
                            if (isSelectionTool && (isAllowedPointer || preHitLassoHandle != LassoHandle.NONE)) {
                                down.consume()
                                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleStrokeSelectionHandle(
                                    handle: LassoHandle
                                ) {
                                    val b = ink.lassoBounds(pageIndex, 2160f, 2160f, currentScale) ?: return
                                    ink.isManipulatingLasso = true
                                    try {
                                    when (handle) {
                                        LassoHandle.DELETE -> {
                                            ink.lassoDelete(pageIndex)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                        }
                                        LassoHandle.MIRROR -> {
                                            ink.lassoMirrorHorizontal(pageIndex)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                        }
                                        LassoHandle.COPY -> {
                                            if (ink.lassoStartCopy(pageIndex)) {
                                                var lastPos = down.position
                                                var movedDist = 0f
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    val delta = change.position - lastPos
                                                    lastPos = change.position
                                                    movedDist += delta.getDistance()
                                                    ink.lassoMove(
                                                        pageIndex,
                                                        delta.x / currentScale.coerceAtLeast(0.1f),
                                                        delta.y / currentScale.coerceAtLeast(0.1f)
                                                    )
                                                    change.consume()
                                                    if (change.changedToUpIgnoreConsumed()) break
                                                }
                                                if (movedDist < 8f) {
                                                    val step = 28f / currentScale.coerceAtLeast(0.1f)
                                                    ink.lassoMove(pageIndex, step, step)
                                                }
                                                ink.lassoCommitMove(pageIndex)
                                            }
                                        }
                                        LassoHandle.ROTATE -> {
                                            val cx = b.centerX()
                                            val cy = b.centerY()
                                            val startAngle = kotlin.math.atan2(
                                                (downWorldY - cy).toDouble(),
                                                (downWorldX - cx).toDouble()
                                            ).toFloat()
                                            ink.lassoBeginTransform()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val curWx = (change.position.x - currentPanX) / currentScale.coerceAtLeast(0.1f)
                                                val curWy = (change.position.y - currentPanY) / currentScale.coerceAtLeast(0.1f)
                                                val curAngle = kotlin.math.atan2(
                                                    (curWy - cy).toDouble(),
                                                    (curWx - cx).toDouble()
                                                ).toFloat()
                                                ink.lassoRotateTo(pageIndex, curAngle - startAngle, cx, cy)
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitTransform(pageIndex)
                                        }
                                        LassoHandle.SCALE_CORNER_BR -> {
                                            val ax = b.left
                                            val ay = b.top
                                            val baseW = 2160f
                                            ink.lassoBeginTransform()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val curWx = (change.position.x - currentPanX) / currentScale.coerceAtLeast(0.1f)
                                                val curWy = (change.position.y - currentPanY) / currentScale.coerceAtLeast(0.1f)
                                                val f = ContinuousStrokeTransformMath.proportionalScaleFactor(
                                                    startXNorm = downWorldX / baseW,
                                                    startYNorm = downWorldY / baseW,
                                                    curXNorm = curWx / baseW,
                                                    curYNorm = curWy / baseW,
                                                    anchorXNorm = ax / baseW,
                                                    anchorYNorm = ay / baseW,
                                                    boxRatio = 1f
                                                )
                                                ink.lassoScaleTo(pageIndex, f, f, ax, ay)
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitTransform(pageIndex)
                                        }
                                        LassoHandle.SCALE_LEFT,
                                        LassoHandle.SCALE_RIGHT,
                                        LassoHandle.SCALE_TOP,
                                        LassoHandle.SCALE_BOTTOM -> {
                                            val baseW = 2160f
                                            val normBounds = NormRect(
                                                b.left / baseW,
                                                b.top / baseW,
                                                b.right / baseW,
                                                b.bottom / baseW
                                            )
                                            ink.lassoBeginTransform()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val curWx = (change.position.x - currentPanX) / currentScale.coerceAtLeast(0.1f)
                                                val curWy = (change.position.y - currentPanY) / currentScale.coerceAtLeast(0.1f)
                                                val spec = ContinuousStrokeTransformMath.edgeScaleFactors(
                                                    handle = handle,
                                                    bounds = normBounds,
                                                    startXNorm = downWorldX / baseW,
                                                    startYNorm = downWorldY / baseW,
                                                    curXNorm = curWx / baseW,
                                                    curYNorm = curWy / baseW
                                                )
                                                if (spec != null) {
                                                    ink.lassoScaleTo(
                                                        pageIndex,
                                                        spec.scaleX,
                                                        spec.scaleY,
                                                        spec.anchorXNorm * baseW,
                                                        spec.anchorYNorm * baseW
                                                    )
                                                }
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitTransform(pageIndex)
                                        }
                                        LassoHandle.MOVE -> {
                                            var lastPos = down.position
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val delta = change.position - lastPos
                                                lastPos = change.position
                                                ink.lassoMove(
                                                    pageIndex,
                                                    delta.x / currentScale.coerceAtLeast(0.1f),
                                                    delta.y / currentScale.coerceAtLeast(0.1f)
                                                )
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitMove(pageIndex)
                                        }
                                        LassoHandle.NONE -> Unit
                                    }
                                    } finally {
                                        ink.isManipulatingLasso = false
                                    }
                                }

                                if (preHitLassoHandle != LassoHandle.NONE) {
                                    handleStrokeSelectionHandle(preHitLassoHandle)
                                    return@awaitEachGesture
                                }

                                if (settings.tool == CanvasTool.SELECT) {
                                    if (ink.selectSingleStrokeAt(pageIndex, downWorldX, downWorldY, currentScale)) {
                                        handleStrokeSelectionHandle(LassoHandle.MOVE)
                                    } else {
                                        ink.lassoClear()
                                    }
                                    return@awaitEachGesture
                                }

                                ink.lassoClear()
                                ink.isManipulatingLasso = true
                                val loop = mutableListOf(Pair(downWorldX, downWorldY))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    loop.add(
                                        Pair(
                                            (change.position.x - currentPanX) / currentScale.coerceAtLeast(0.1f),
                                            (change.position.y - currentPanY) / currentScale.coerceAtLeast(0.1f)
                                        )
                                    )
                                    ink.lassoUpdatePath(pageIndex, loop)
                                    change.consume()
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                                ink.isManipulatingLasso = false
                                val picked = ink.lassoFinish(pageIndex, loop)
                                if (picked == 0) ink.lassoClear()
                                return@awaitEachGesture
                            }

                            if (!settings.isWritingTool || !isAllowedPointer) {
                                down.consume()
                                var lastPos = down.position
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val stylusDown = event.changes.firstOrNull {
                                        it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                                    }
                                    if (stylusDown != null) {
                                        pendingDown = stylusDown
                                        break
                                    }
                                    if (event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) break
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val delta = change.position - lastPos
                                    lastPos = change.position
                                    onPanZoom(delta.x, delta.y, 1f, down.position)
                                    change.consume()
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                                if (pendingDown != null) continue
                                return@awaitEachGesture
                            }

                            down.consume()
                            val sScale = currentScale
                            val sPanX = currentPanX
                            val sPanY = currentPanY
                            val sSnap = currentSnap
                            val sPreset = currentGridPreset
                            val sStep = currentDensity.coerceIn(20, 80).toFloat() * 2f * sScale
                            val baseW = 2160f
                            val (sx, sy) = if (sSnap) {
                                snapToInfiniteGrid(down.position.x, down.position.y, sStep, sPanX, sPanY, sPreset)
                            } else {
                                Pair(down.position.x, down.position.y)
                            }
                            var snapAnchorX = sx
                            var snapAnchorY = sy
                            val allowedAxes = gridSnapAxes(sPreset)
                            var snapUx = allowedAxes[0].first
                            var snapUy = allowedAxes[0].second
                            var snapLocked = false
                            val wx = (sx - sPanX) / (sScale * baseW)
                            val wy = (sy - sPanY) / (sScale * baseW)

                            ink.beginStroke(
                                page = page,
                                pageIndex = pageIndex,
                                x = wx,
                                y = wy,
                                pressure = down.pressure,
                                boxWidth = baseW,
                                boxHeight = baseW,
                                renderWidthPx = 2160,
                                settings = settings,
                                tilt = personal.notes.touch.StylusTiltTracker.currentTiltRad
                            )

                            while (true) {
                                val event = awaitPointerEvent()
                                if (!isStylus && event.changes.count { it.pressed && it.type == PointerType.Touch } > 1) {
                                    ink.cancelStroke()
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    ink.cancelStroke()
                                    break
                                }
                                val isShapeSnapTool = settings.tool == CanvasTool.SHAPE_LINE ||
                                    settings.tool == CanvasTool.SHAPE_RECT ||
                                    settings.tool == CanvasTool.SHAPE_CIRCLE
                                val (cx, cy) = if (sSnap && isShapeSnapTool) {
                                    snapToInfiniteGrid(change.position.x, change.position.y, sStep, sPanX, sPanY, sPreset)
                                } else if (sSnap) {
                                    val rawX = change.position.x
                                    val rawY = change.position.y
                                    val dx = rawX - snapAnchorX
                                    val dy = rawY - snapAnchorY
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                    if (!snapLocked) {
                                        val chosen = bestGridAxis(dx, dy, allowedAxes)
                                        snapUx = chosen.first
                                        snapUy = chosen.second
                                        if (dist >= 10f) {
                                            snapLocked = true
                                        }
                                    } else {
                                        val projNow = dx * snapUx + dy * snapUy
                                        val perpNow = kotlin.math.abs(-dx * snapUy + dy * snapUx)
                                        val turnThreshold = kotlin.math.max(26f, sStep * 0.45f)
                                        if (sPreset != InfiniteGridPreset.LINED &&
                                            perpNow > turnThreshold &&
                                            kotlin.math.abs(projNow) > sStep * 0.35f
                                        ) {
                                            // Commit corner on grid and switch to the new grid line direction
                                            val rawCornerX = snapAnchorX + projNow * snapUx
                                            val rawCornerY = snapAnchorY + projNow * snapUy
                                            val (cornerX, cornerY) = snapToInfiniteGrid(
                                                rawCornerX, rawCornerY, sStep, sPanX, sPanY, sPreset
                                            )
                                            val cwx = (cornerX - sPanX) / (sScale * baseW)
                                            val cwy = (cornerY - sPanY) / (sScale * baseW)
                                            ink.extendStroke(
                                                cwx, cwy, change.pressure, settings,
                                                personal.notes.touch.StylusTiltTracker.currentTiltRad
                                            )
                                            snapAnchorX = cornerX
                                            snapAnchorY = cornerY
                                            val newAxis = bestGridAxis(rawX - snapAnchorX, rawY - snapAnchorY, allowedAxes)
                                            snapUx = newAxis.first
                                            snapUy = newAxis.second
                                        }
                                    }
                                    val finalDx = rawX - snapAnchorX
                                    val finalDy = rawY - snapAnchorY
                                    val proj = finalDx * snapUx + finalDy * snapUy
                                    Pair(snapAnchorX + proj * snapUx, snapAnchorY + proj * snapUy)
                                } else {
                                    Pair(change.position.x, change.position.y)
                                }
                                val curWx = (cx - sPanX) / (sScale * baseW)
                                val curWy = (cy - sPanY) / (sScale * baseW)

                                if (!isShapeSnapTool && !sSnap) {
                                    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                                    val hist = change.historical
                                    val tiltNow = personal.notes.touch.StylusTiltTracker.currentTiltRad
                                    for (hIdx in 0 until hist.size) {
                                        val hp = hist[hIdx].position
                                        val hwx = (hp.x - sPanX) / (sScale * baseW)
                                        val hwy = (hp.y - sPanY) / (sScale * baseW)
                                        ink.extendStroke(hwx, hwy, change.pressure, settings, tiltNow)
                                    }
                                }
                                if (change.pressed) {
                                    ink.extendStroke(curWx, curWy, change.pressure, settings, personal.notes.touch.StylusTiltTracker.currentTiltRad)
                                    change.consume()
                                } else {
                                    ink.endStroke(curWx, curWy, change.pressure, settings, personal.notes.touch.StylusTiltTracker.currentTiltRad)
                                    change.consume()
                                    break
                                }
                            }
                            }
                        }
                    } finally {
                        ink.cancelStroke()
                        ink.cancelLassoGesture()
                    }
                }
        )

        InfiniteCanvasHud(
            scale = scale,
            panX = panX,
            panY = panY,
            gridPreset = gridPreset,
            snapToGrid = snapToGrid,
            strokeOpacity = strokeOpacity,
            onResetTransform = onResetTransform,
            onCycleGridPreset = onCycleGridPreset,
            onToggleSnap = onSnapToggle,
            onOpacityChange = onOpacityChange,
            onQuickClear = onQuickClear,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
        )
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
    readerLayers: ReaderLayerCache,
    zoom: Float,
    ink: ContinuousInkController,
    inkSettings: InkToolSettings,
    backgroundMode: Int = 0,
    backgroundDensity: Int = 40,
    /**
     * The pattern the canvas should use when the page row has no pattern of
     * its own. This is [CanvasUiState.paperPattern]: it carries the app
     * default and the in-canvas Template tap, so a pattern change is visible
     * immediately in continuous mode (the page row catches up through the
     * database).
     */
    defaultPattern: PaperPattern = PaperPattern.BLANK,
    savedGeneration: Int = 0,
    isCurrentPage: Boolean = false,
    /** An infinite canvas has no page edge: no frame, no desk. */
    isInfinite: Boolean = false,
    /**
     * How far the page was pulled aside by the two finger pan (in pixels).
     * The writing is read in the page's own space, so a stroke lands where
     * the page is - not where it was.
     */
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    /** The type of the note's pages ("infinite", "a4", "pdf"). */
    pageType: String = if (isInfinite) "infinite" else "a4",
    viewportWidthPx: Float = 0f,
    canvasOriginXProvider: () -> Float = { 0f },
    marginLabelAlphaProvider: () -> Float = { 0f },
    allowLeftPanReveal: Boolean = true,
    allowRightPanReveal: Boolean = true,
    allowLeftMarginLabel: Boolean = true,
    allowRightMarginLabel: Boolean = true,
    searchHighlightStart: Int = -1,
    searchHighlightLength: Int = 0,
    searchHighlightRevision: Int = 0,
    modifier: Modifier = Modifier,
    onStylusDetected: () -> Unit = {},
    onPageMapping: (ContinuousPageMap) -> Unit = {},
    onImageMapping: (ContinuousImageMap) -> Unit = {},
    onPageBox: (ReaderPageBox) -> Unit = {},
    onPageBitmap: (Int, android.graphics.Bitmap?) -> Unit = { _, _ -> },
    onActivatePage: () -> Unit = {},
    onClick: () -> Unit
) {
    val isVector = page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0
    val coroutineScope = rememberCoroutineScope()
    val latestInkSettings by rememberUpdatedState(inkSettings)

    LaunchedEffect(page.id, pageIndex, noteId) {
        ink.prewarmSessionForPage(page, pageIndex)
    }

    LaunchedEffect(page.id, pageIndex, isCurrentPage, inkSettings.tool) {
        if (isCurrentPage && (inkSettings.tool == CanvasTool.LASSO || inkSettings.tool == CanvasTool.SELECT)) {
            ink.ensureSessionReady(page, pageIndex)
        }
    }

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
    // thread, so seed from PageAspectCache or fall back to A4 until it arrives.
    val initialAspect = if (!isVector) {
        PdfPageGeometry.DEFAULT_ASPECT
    } else {
        PageAspectCache.default.get(page.pdfSourcePath, page.pdfPageIndex) ?: PdfPageGeometry.DEFAULT_ASPECT
    }
    val pageRatio by produceState(
        initialValue = initialAspect,
        page.id, page.pdfSourcePath, page.pdfPageIndex
    ) {
        value = withContext(Dispatchers.IO) {
            if (!isVector) {
                PdfPageGeometry.DEFAULT_ASPECT
            } else {
                PageAspectCache.default.getOrLoad(
                    page.pdfSourcePath,
                    page.pdfPageIndex,
                    fallback = PdfPageGeometry.DEFAULT_ASPECT
                ) {
                    pdfDocCache.document(page.pdfSourcePath)?.pageSize(page.pdfPageIndex)
                }
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
                pageBox.pageIndex = pageIndex
                pageBox.originX = coords.positionInRoot().x
                pageBox.originY = coords.positionInRoot().y
                pageBox.width = coords.size.width
                pageBox.height = coords.size.height
                if (isCurrentPage) {
                    onPageBox(pageBox)
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
        //
        // The render stops at MAX_PAGE_RENDER_WIDTH_PX: a page drawn at eight
        // times the width of the screen would need more than a gigabyte of
        // memory, so past that width the page is enlarged instead.
        val desiredWidthPx = (displayWidthPx * zoom).roundToInt()
            .coerceIn(1, MAX_PAGE_RENDER_WIDTH_PX)

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
        var savedSinglePageStrokes by remember(page.id, noteId) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        LaunchedEffect(page.id, noteId, savedGeneration, activeSessionReady, isVector) {
            if (!isVector) {
                savedSinglePageStrokes = null
                return@LaunchedEffect
            }
            if (activeSessionReady) {
                return@LaunchedEffect
            }
            val strokesFile = repository.fileStore.getStrokesFile(noteId, pageIndex)
            val targetW = desiredWidthPx.coerceAtLeast(720)
            val targetH = (targetW / pageRatio.coerceAtLeast(0.1f)).roundToInt().coerceAtLeast(1)
            val loaded = if (strokesFile.exists()) {
                personal.notes.touch.util.BitmapUtils.loadAlphaThumbnail(
                    strokesFile,
                    targetWidth = targetW,
                    targetHeight = targetH
                )
            } else null
            if (!ink.isActiveSession(pageIndex)) {
                savedSinglePageStrokes = loaded
            }
        }
        LaunchedEffect(page.id, noteId, savedGeneration, activeSessionReady, layerTick) {
            if (!isInfinite && ContinuousPageGeometry.marginFor(pageType) > 0f) {
                ink.refreshMarginInkPresence(pageIndex)
            }
            if (activeSessionReady) {
                return@LaunchedEffect
            }
            val loaded = ink.composeDisplayLayers(
                pageIndex, displayWidthPx.roundToInt(), page = page
            )
            if (!ink.isActiveSession(pageIndex)) {
                savedLayers = loaded
            }
        }

        LaunchedEffect(page.id, page.updatedAt, renderWidthKey, noteId, savedGeneration) {
            ink.refreshBackground(page, pageIndex, desiredWidthPx)
            // Let a pinch settle on its final bucket before allocating a
            // full-page bitmap; an intermediate key cancels this coroutine
            // while it is still cancellable (a native pdfium render is not).
            kotlinx.coroutines.delay(80)
            pageBitmap = withContext(Dispatchers.IO) {
                try {
                    if (isVector) {
                        renderContinuousPage(
                            page, pageIndex, noteId, pdfDocCache, repository, desiredWidthPx,
                            includeInk = PdfModeReconciliationPolicy.continuousPdfRenderIncludesInk()
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
            sessionReady && inkSession?.isVector == true && inkSession.background != null -> inkSession.background
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
                shape = androidx.compose.ui.graphics.RectangleShape
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
            // The page on screen follows the canvas state (the app default or
            // the Template tap) so the pattern changes on the next frame; the
            // page row is written in the background and is the source of truth
            // for the other pages of the reader.
            val patternCode = PaperPattern.effectiveContinuousCode(
                pagePatternCode = page.paperPattern,
                isCurrentPage = isCurrentPage,
                defaultPatternCode = defaultPattern.code
            )
            val effectivePattern = PaperPattern.resolve(patternCode, backgroundMode)
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
                    val step = PaperPattern.stepPx(backgroundDensity, w)
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
            // user writes in. An infinite canvas fills the visible box so
            // the endless canvas has no bounding frame.
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Page ${pageIndex + 1}" }
            ) {
                val rect = if (isInfinite) {
                    ContinuousPageGeometry.BoxRect(0f, 0f, 1f, 1f)
                } else {
                    pageRectOf(pageIndex, size.width, size.height)
                }
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

        // Non-destructive Single-Page strokes (`strokes_<i>.png`) over clean PDF render,
        // gated by Layer1 visibility in PageLayerStack.
        val shownSinglePageStrokes = if (sessionReady && inkSession?.isVector == true) {
            inkSession.singlePageStrokes
        } else {
            savedSinglePageStrokes
        }
        val baseLayerObj = layerStack.layerAt(PageLayerStack.BASE_LAYER_INDEX)
        val baseLayerVisible = baseLayerObj?.visible != false
        val liveBaseEraser = baseLayerObj?.let { ink.liveEraserStroke(pageIndex, it) }
        if (isVector && PdfModeReconciliationPolicy.shouldDrawSinglePageStrokes(
                hasStrokes = shownSinglePageStrokes != null && !shownSinglePageStrokes.isRecycled,
                baseLayerVisible = baseLayerVisible
            )
        ) {
            val spBmp = shownSinglePageStrokes
            if (spBmp != null && !spBmp.isRecycled) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (liveBaseEraser != null) {
                                Modifier.graphicsLayer(
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val _rev = ink.revision
                    val _previewRev = if (liveBaseEraser != null && ink.livePageIndex == pageIndex) ink.previewRevision else 0
                    val rect = pageRectOf(pageIndex, size.width, size.height)
                    if (rect.empty) return@Canvas
                    val dest = android.graphics.RectF(
                        rect.left * size.width,
                        rect.top * size.height,
                        rect.right * size.width,
                        rect.bottom * size.height
                    )
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.drawBitmap(spBmp, null, dest, layerPaint)
                        if (liveBaseEraser != null) {
                            val save = native.save()
                            native.clipRect(dest)
                            ink.drawLiveEraserCut(native, liveBaseEraser, size.width, size.height)
                            native.restoreToCount(save)
                        }
                    }
                }
            }
        }

        // PDF-QOL-3: Transient full-text search hit highlight overlay in Continuous mode.
        val hasActiveSearchHighlight = isVector && searchHighlightStart >= 0 && searchHighlightLength > 0
        val searchHighlightUserRects by produceState<List<FloatArray>>(
            initialValue = emptyList(),
            hasActiveSearchHighlight,
            page.pdfSourcePath,
            page.pdfPageIndex,
            searchHighlightStart,
            searchHighlightLength,
            searchHighlightRevision
        ) {
            value = if (!hasActiveSearchHighlight) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    pdfDocCache.document(page.pdfSourcePath)
                        ?.selectionRects(page.pdfPageIndex, searchHighlightStart, searchHighlightLength)
                        .orEmpty()
                }
            }
        }
        var searchPagePts by remember(page.id, page.pdfSourcePath, page.pdfPageIndex) {
            mutableStateOf<Pair<Float, Float>?>(null)
        }
        LaunchedEffect(hasActiveSearchHighlight, page.pdfSourcePath, page.pdfPageIndex) {
            if (hasActiveSearchHighlight && searchPagePts == null) {
                searchPagePts = withContext(Dispatchers.IO) {
                    val s = pdfDocCache.document(page.pdfSourcePath)?.pageSize(page.pdfPageIndex)
                    if (s != null && s.size >= 2 && s[0] > 0f && s[1] > 0f) s[0] to s[1] else null
                }
            }
        }
        val ptsForHighlight = searchPagePts
        if (searchHighlightUserRects.isNotEmpty() && ptsForHighlight != null) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = if (isInfinite) {
                    ContinuousPageGeometry.BoxRect(0f, 0f, 1f, 1f)
                } else {
                    pageRectOf(pageIndex, size.width, size.height)
                }
                if (rect.empty) return@Canvas
                val boxRects = PdfTextIndex.userRectsToBoxCanvasRects(
                    userRects = searchHighlightUserRects,
                    pageWidthPts = ptsForHighlight.first,
                    pageHeightPts = ptsForHighlight.second,
                    pageLeftPx = rect.left * size.width,
                    pageTopPx = rect.top * size.height,
                    pageWidthPx = rect.width * size.width
                )
                for (r in boxRects) {
                    val w = (r[2] - r[0]).coerceAtLeast(1f)
                    val h = (r[3] - r[1]).coerceAtLeast(1f)
                    drawRect(
                        color = Color(0x66FF9800),
                        topLeft = androidx.compose.ui.geometry.Offset(r[0], r[1]),
                        size = androidx.compose.ui.geometry.Size(w, h)
                    )
                    drawRect(
                        color = Color(0xCCEF6C00),
                        topLeft = androidx.compose.ui.geometry.Offset(r[0], r[1]),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
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
            val liveEraser = ink.liveEraserStroke(pageIndex, layer)
            // The layer of the session is a grid of tiles: every tile is
            // drawn into its own part of the layer's rect. Without a session
            // (the user only reads) the layer comes from the disk as one
            // bitmap and is drawn as it is.
            val placements = if (sessionReady) {
                ink.layerTiles(pageIndex, layer)
            } else {
                emptyList()
            }
            val saved = if (sessionReady) {
                emptyList()
            } else {
                listOfNotNull(savedLayers[layer.index])
            }
            val ops = ink.layerOps(pageIndex, layer)
            if (placements.isEmpty() && saved.isEmpty() && ops.isEmpty()) return@forEach
            if (placements.isNotEmpty() || saved.isNotEmpty()) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (liveEraser != null) {
                                Modifier.graphicsLayer(
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val _revision = ink.revision
                    val _previewRev = if (liveEraser != null && ink.livePageIndex == pageIndex) ink.previewRevision else 0
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
                        saved.forEach { bitmap ->
                            if (!bitmap.isRecycled) {
                                native.drawBitmap(bitmap, null, dest, layerPaint)
                            }
                        }
                        placements.forEach placementLoop@ { placement ->
                            val grid = placement.grid
                            placement.tiles.existingTiles().forEach tileLoop@ { index ->
                                val tx = grid.tileColumn(index)
                                val ty = grid.tileRow(index)
                                if (tx < 0 || ty < 0) return@tileLoop
                                val bitmap = placement.tiles.tile(tx, ty) ?: return@tileLoop
                                if (bitmap.isRecycled) return@tileLoop
                                val tile = LayerTileMapping.tileRect(
                                    grid, tx, ty, rect, size.width, size.height
                                )
                                if (tile.empty) return@tileLoop
                                native.drawBitmap(
                                    bitmap,
                                    null,
                                    android.graphics.RectF(
                                        tile.left, tile.top, tile.right, tile.bottom
                                    ),
                                    layerPaint
                                )
                            }
                        }
                        if (liveEraser != null) {
                            ink.drawLiveEraserCut(native, liveEraser, size.width, size.height)
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
                val eraseLayer = ink.layerHasErase(pageIndex, layer) || liveEraser != null
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
                    val _previewRev = if (liveEraser != null && ink.livePageIndex == pageIndex) ink.previewRevision else 0
                    drawIntoCanvas { canvas ->
                        if (pageType == "a4") {
                            canvas.save()
                            canvas.clipRect(0f, 0f, size.width, size.height)
                            ink.drawLayerOps(
                                canvas.nativeCanvas, pageIndex, layer, size.width, size.height
                            )
                            canvas.restore()
                        } else {
                            ink.drawLayerOps(
                                canvas.nativeCanvas, pageIndex, layer, size.width, size.height
                            )
                        }
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
            val _lassoRevision = if (ink.lassoPageIndex == pageIndex || ink.lassoPageIndex == -1) ink.lassoRevision else 0
            // The strokes are normalised to the box: the session has to know
            // the size of the box (the zoom changed it).
            ink.updateGeometry(pageIndex, size.width, size.height)
            drawIntoCanvas { canvas ->
                if (pageType == "a4") {
                    canvas.save()
                    canvas.clipRect(0f, 0f, size.width, size.height)
                    ink.drawPreview(canvas.nativeCanvas, pageIndex, size.width, size.height, includeLasso = false)
                    canvas.restore()
                    ink.drawLassoOverlayForPage(canvas.nativeCanvas, pageIndex, size.width, size.height, scale = 1f)
                } else {
                    ink.drawPreview(canvas.nativeCanvas, pageIndex, size.width, size.height)
                }
            }
        }

        if (!isInfinite && ContinuousPageGeometry.marginFor(pageType) > 0f) {
            val localDensity = LocalDensity.current
            val guideLineColor = Color(0xFF94A3B8).copy(alpha = 0.38f)
            val labelBgColor = Color(0xFF1E293B)
            val labelPaint = remember(localDensity) {
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = with(localDensity) { 10.sp.toPx() }
                    typeface = android.graphics.Typeface.DEFAULT
                    isFakeBoldText = true
                    color = android.graphics.Color.WHITE
                }
            }
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val _rev = ink.revision
                val _layerRev = ink.layersRevision
                val _marginRev = ink.marginPresenceRevision
                val _pan = panOffsetX
                val _zoom = zoom
                val vpW = if (viewportWidthPx > 0f) viewportWidthPx else size.width
                val pageLeftInViewport = if (pageBox.valid) {
                    pageBox.originX - canvasOriginXProvider()
                } else {
                    (vpW - size.width) / 2f + _pan
                }
                val presence = ink.marginInkPresence(pageIndex)
                val guideState = MarginExportMath.computeGuideState(
                    pageType = pageType,
                    pageLeftInViewportPx = pageLeftInViewport,
                    pageWidthPx = size.width,
                    viewportWidthPx = vpW,
                    presence = presence,
                    allowLeftPanReveal = allowLeftPanReveal,
                    allowRightPanReveal = allowRightPanReveal
                )
                val dashPx = 6.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx), 0f)
                val strokePx = 1.dp.toPx()
                if (guideState.showLeftGuide) {
                    drawLine(
                        color = guideLineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, size.height),
                        strokeWidth = strokePx,
                        pathEffect = dashEffect
                    )
                }
                if (guideState.showRightGuide) {
                    drawLine(
                        color = guideLineColor,
                        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = strokePx,
                        pathEffect = dashEffect
                    )
                }
                val labelAlpha = marginLabelAlphaProvider().coerceIn(0f, 1f)
                if (labelAlpha > 0.01f) {
                    val minReveal = 44.dp.toPx()
                    val padX = 6.dp.toPx()
                    val padY = 3.dp.toPx()
                    val gap = 8.dp.toPx()
                    val topY = 20.dp.toPx()
                    val text = "margin"
                    val textW = labelPaint.measureText(text)
                    val fm = labelPaint.fontMetrics
                    val textH = fm.descent - fm.ascent
                    val pillW = textW + padX * 2f
                    val pillH = textH + padY * 2f
                    val cornerR = 6.dp.toPx()
                    labelPaint.alpha = (230 * labelAlpha).roundToInt().coerceIn(0, 255)
                    if (guideState.showLeftGuide && allowLeftMarginLabel &&
                        guideState.leftRevealedPx >= minReveal
                    ) {
                        val pillLeft = -gap - pillW
                        drawRoundRect(
                            color = labelBgColor.copy(alpha = 0.65f * labelAlpha),
                            topLeft = androidx.compose.ui.geometry.Offset(pillLeft, topY),
                            size = androidx.compose.ui.geometry.Size(pillW, pillH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR)
                        )
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                text,
                                pillLeft + padX,
                                topY + padY - fm.ascent,
                                labelPaint
                            )
                        }
                    }
                    if (guideState.showRightGuide && allowRightMarginLabel &&
                        guideState.rightRevealedPx >= minReveal
                    ) {
                        val pillLeft = size.width + gap
                        drawRoundRect(
                            color = labelBgColor.copy(alpha = 0.65f * labelAlpha),
                            topLeft = androidx.compose.ui.geometry.Offset(pillLeft, topY),
                            size = androidx.compose.ui.geometry.Size(pillW, pillH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR)
                        )
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                text,
                                pillLeft + padX,
                                topY + padY - fm.ascent,
                                labelPaint
                            )
                        }
                    }
                }
            }
        }

        if (!isInfinite) {
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
                .pointerInput(page.id, pageIndex, inkSettings) {
                    if (inkSettings.readingMode) return@pointerInput
                    try {
                    awaitEachGesture {
                        var pendingDown: androidx.compose.ui.input.pointer.PointerInputChange? =
                            awaitFirstDown(requireUnconsumed = false)
                        while (pendingDown != null) {
                        val down = pendingDown
                        pendingDown = null
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
                                for (hist in change.historical) {
                                    ink.updateLaser(pageIndex, hist.position.x / lBoxW, hist.position.y / lBoxH)
                                }
                                ink.updateLaser(pageIndex, change.position.x / lBoxW, change.position.y / lBoxH)
                                change.consume()
                                if (change.changedToUpIgnoreConsumed()) break
                            }
                            ink.clearLaser()
                            return@awaitEachGesture
                        }

                        val preHitLassoHandle = if (inkSettings.tool == CanvasTool.LASSO || inkSettings.tool == CanvasTool.SELECT) {
                            val bw = size.width.toFloat()
                            val bh = size.height.toFloat()
                            ink.updateGeometry(pageIndex, bw, bh)
                            ink.hitTestLassoHandle(pageIndex, down.position.x, down.position.y, bw, bh, scale = 1f)
                        } else {
                            LassoHandle.NONE
                        }

                        // Stylus mode: the hand may scroll, pinch and manipulate
                        // active Lasso handles, while watching for a stylus touchdown
                        // even if the palm rested on the screen first.
                        if (!isAllowedPointer && preHitLassoHandle == LassoHandle.NONE) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val stylusDown = event.changes.firstOrNull {
                                    it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                                }
                                if (stylusDown != null) {
                                    pendingDown = stylusDown
                                    break
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            continue
                        }

                        if (inkSettings.tool == CanvasTool.LASSO || inkSettings.tool == CanvasTool.SELECT) {
                            val boxW = size.width.toFloat()
                            val boxH = size.height.toFloat()
                            val downX = down.position.x
                            val downY = down.position.y
                            ink.updateGeometry(pageIndex, boxW, boxH)

                            suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleContinuousStrokeSelectionHandle(
                                handle: LassoHandle
                            ) {
                                val b = ink.lassoBounds(pageIndex, boxW, boxH, 1f) ?: return
                                val boundsNorm = NormRect(
                                    b.left / boxW,
                                    b.top / boxH,
                                    b.right / boxW,
                                    b.bottom / boxH
                                )
                                ink.isManipulatingLasso = true
                                try {
                                    when (handle) {
                                        LassoHandle.DELETE -> {
                                            ink.lassoDelete(pageIndex)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus) {
                                                    if (event.changes.count { it.pressed } > 1) break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                        }
                                        LassoHandle.MIRROR -> {
                                            ink.lassoMirrorHorizontal(pageIndex, boxW, boxH)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus) {
                                                    if (event.changes.count { it.pressed } > 1) break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                        }
                                        LassoHandle.COPY -> {
                                            if (ink.lassoStartCopy(pageIndex)) {
                                                var lastPos = down.position
                                                var movedDist = 0f
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (!isStylus) {
                                                        if (event.changes.count { it.pressed } > 1) break
                                                    }
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    val delta = change.position - lastPos
                                                    lastPos = change.position
                                                    movedDist += delta.getDistance()
                                                    ink.lassoMove(pageIndex, delta.x, delta.y, boxW, boxH)
                                                    change.consume()
                                                    if (change.changedToUpIgnoreConsumed()) break
                                                }
                                                if (movedDist < 8f) {
                                                    ink.lassoMove(pageIndex, 28f, 28f, boxW, boxH)
                                                }
                                                ink.lassoCommitMove(pageIndex)
                                            }
                                        }
                                        LassoHandle.ROTATE -> {
                                            val cx = b.centerX()
                                            val cy = b.centerY()
                                            val startAngle = kotlin.math.atan2(
                                                (downY - cy).toDouble(),
                                                (downX - cx).toDouble()
                                            ).toFloat()
                                            ink.lassoBeginTransform()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus) {
                                                    if (event.changes.count { it.pressed } > 1) break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val curAngle = kotlin.math.atan2(
                                                    (change.position.y - cy).toDouble(),
                                                    (change.position.x - cx).toDouble()
                                                ).toFloat()
                                                ink.lassoRotateTo(
                                                    pageIndex,
                                                    curAngle - startAngle,
                                                    cx,
                                                    cy,
                                                    boxW,
                                                    boxH
                                                )
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitTransform(pageIndex)
                                        }
                                        LassoHandle.SCALE_CORNER_BR -> {
                                            val boxRatio = ContinuousStrokeTransformMath.safeBoxRatio(boxW / boxH)
                                            ink.lassoBeginTransform()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus) {
                                                    if (event.changes.count { it.pressed } > 1) break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val f = ContinuousStrokeTransformMath.proportionalScaleFactor(
                                                    startXNorm = downX / boxW,
                                                    startYNorm = downY / boxH,
                                                    curXNorm = change.position.x / boxW,
                                                    curYNorm = change.position.y / boxH,
                                                    anchorXNorm = boundsNorm.left,
                                                    anchorYNorm = boundsNorm.top,
                                                    boxRatio = boxRatio
                                                )
                                                ink.lassoScaleTo(pageIndex, f, f, b.left, b.top, boxW, boxH)
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitTransform(pageIndex)
                                        }
                                        LassoHandle.SCALE_LEFT,
                                        LassoHandle.SCALE_RIGHT,
                                        LassoHandle.SCALE_TOP,
                                        LassoHandle.SCALE_BOTTOM -> {
                                            ink.lassoBeginTransform()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus) {
                                                    if (event.changes.count { it.pressed } > 1) break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val spec = ContinuousStrokeTransformMath.edgeScaleFactors(
                                                    handle = handle,
                                                    bounds = boundsNorm,
                                                    startXNorm = downX / boxW,
                                                    startYNorm = downY / boxH,
                                                    curXNorm = change.position.x / boxW,
                                                    curYNorm = change.position.y / boxH
                                                )
                                                if (spec != null) {
                                                    ink.lassoScaleTo(
                                                        pageIndex,
                                                        spec.scaleX,
                                                        spec.scaleY,
                                                        spec.anchorXNorm * boxW,
                                                        spec.anchorYNorm * boxH,
                                                        boxW,
                                                        boxH
                                                    )
                                                }
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitTransform(pageIndex)
                                        }
                                        LassoHandle.MOVE -> {
                                            var lastPos = down.position
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!isStylus) {
                                                    if (event.changes.count { it.pressed } > 1) break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val delta = change.position - lastPos
                                                lastPos = change.position
                                                ink.lassoMove(pageIndex, delta.x, delta.y, boxW, boxH)
                                                change.consume()
                                                if (change.changedToUpIgnoreConsumed()) break
                                            }
                                            ink.lassoCommitMove(pageIndex)
                                        }
                                        LassoHandle.NONE -> Unit
                                    }
                                } finally {
                                    ink.isManipulatingLasso = false
                                }
                            }

                            val hitHandle = preHitLassoHandle
                            if (hitHandle != LassoHandle.NONE) {
                                down.consume()
                                onActivatePage()
                                onClick()
                                handleContinuousStrokeSelectionHandle(hitHandle)
                                return@awaitEachGesture
                            }

                            if (inkSettings.tool == CanvasTool.SELECT) {
                                val activeSession = ink.getSession(pageIndex)
                                if (activeSession != null && activeSession.ready) {
                                    if (ink.selectSingleStrokeAt(pageIndex, downX, downY, 1f, boxW, boxH)) {
                                        down.consume()
                                        onActivatePage()
                                        onClick()
                                        handleContinuousStrokeSelectionHandle(LassoHandle.MOVE)
                                    } else {
                                        ink.lassoClear()
                                    }
                                } else {
                                    var dragged = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (!isStylus) {
                                            if (event.changes.count { it.pressed } > 1) {
                                                dragged = true
                                                break
                                            }
                                        }
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if ((change.position - down.position).getDistance() >= 12f) {
                                            dragged = true
                                            break
                                        }
                                        if (change.changedToUpIgnoreConsumed()) break
                                    }
                                    if (!dragged) {
                                        onActivatePage()
                                        coroutineScope.launch {
                                            val ready = ink.ensureSessionReady(page, pageIndex) ?: return@launch
                                            if (!ready.ready ||
                                                latestInkSettings.readingMode ||
                                                latestInkSettings.tool != CanvasTool.SELECT
                                            ) {
                                                return@launch
                                            }
                                            ink.updateGeometry(pageIndex, boxW, boxH)
                                            ink.selectSingleStrokeAt(pageIndex, downX, downY, 1f, boxW, boxH)
                                        }
                                    }
                                }
                                return@awaitEachGesture
                            }

                            down.consume()
                            onActivatePage()
                            onClick()
                            ink.lassoClear()
                            ink.ensureSessionForPage(page, pageIndex)
                            ink.updateGeometry(pageIndex, boxW, boxH)
                            val loop = mutableListOf(Pair(downX, downY))
                            var aborted = false
                            ink.isManipulatingLasso = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (!isStylus) {
                                        if (event.changes.count { it.pressed } > 1) {
                                            aborted = true
                                            break
                                        }
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        aborted = true
                                        break
                                    }
                                    loop.add(Pair(change.position.x, change.position.y))
                                    ink.lassoUpdatePath(pageIndex, loop, boxW, boxH)
                                    change.consume()
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                            } finally {
                                ink.isManipulatingLasso = false
                            }
                            if (aborted) {
                                ink.lassoClear()
                            } else if (ink.getSession(pageIndex)?.ready == true) {
                                val picked = ink.lassoFinish(pageIndex, loop, boxW, boxH)
                                if (picked == 0) ink.lassoClear()
                            } else {
                                val finalLoop = loop.toList()
                                coroutineScope.launch {
                                    val ready = ink.ensureSessionReady(page, pageIndex)
                                    if (ready == null ||
                                        !ready.ready ||
                                        latestInkSettings.readingMode ||
                                        latestInkSettings.tool != CanvasTool.LASSO
                                    ) {
                                        if (ink.lassoPageIndex == pageIndex) ink.lassoClear()
                                        return@launch
                                    }
                                    ink.updateGeometry(pageIndex, boxW, boxH)
                                    val picked = ink.lassoFinish(pageIndex, finalLoop, boxW, boxH)
                                    if (picked == 0) ink.lassoClear()
                                }
                            }
                            return@awaitEachGesture
                        }

                        if (!inkSettings.isWritingTool) return@awaitEachGesture
                        
                        down.consume()
                        onActivatePage()
                        onClick()
                        val boxW = size.width.toFloat()
                        val boxH = size.height.toFloat()
                        // The enclosing container is already offset on screen by continuousPanX/Y,
                        // so down.position is already in this page box's local coordinate space.
                        val x = down.position.x / boxW
                        val y = down.position.y / boxH
                        val started = ink.beginStroke(
                            page = page,
                            pageIndex = pageIndex,
                            x = x,
                            y = y,
                            pressure = down.pressure,
                            boxWidth = boxW,
                            boxHeight = boxH,
                            renderWidthPx = desiredWidthPx,
                            settings = inkSettings,
                            tilt = personal.notes.touch.StylusTiltTracker.currentTiltRad
                        )
                        if (!started) return@awaitEachGesture
                        while (true) {
                            val event = awaitPointerEvent()
                            // If a second finger lands (pinch-to-zoom / multi-touch pan gesture),
                            // abort the stroke immediately so gestures take over smoothly.
                            // Stylus strokes ignore palm contacts.
                            if (!isStylus) {
                                if (event.changes.count { it.pressed } > 1) {
                                    ink.cancelStroke()
                                    break
                                }
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                // The pointer is gone: end the stroke, or the
                                // reader would stay "drawing" and refuse to
                                // scroll.
                                ink.cancelStroke()
                                break
                            }
                            val curX = change.position.x / boxW
                            val curY = change.position.y / boxH
                            val tiltNow = personal.notes.touch.StylusTiltTracker.currentTiltRad
                            @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                            val hist = change.historical
                            for (hIdx in 0 until hist.size) {
                                val hp = hist[hIdx].position
                                ink.extendStroke(hp.x / boxW, hp.y / boxH, change.pressure, inkSettings, tiltNow)
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                ink.endStroke(curX, curY, change.pressure, inkSettings, tiltNow)
                                change.consume()
                                break
                            }
                            ink.extendStroke(curX, curY, change.pressure, inkSettings, tiltNow)
                            change.consume()
                        }
                        }
                    }
                    } finally {
                        // The gesture handler can be cancelled (the tool
                        // changed, the item left the list): the reader must
                        // not stay in the "drawing" state, or it would refuse
                        // to scroll.
                        ink.cancelStroke()
                        ink.cancelLassoGesture()
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
    pageType: String = "a4",
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

    val isVector = pageItem.pdfSourcePath.isNotEmpty() && pageItem.pdfPageIndex >= 0
    val initialAspect = remember(pageItem.id, pageItem.pdfSourcePath, pageItem.pdfPageIndex) {
        if (isVector) {
            PageAspectCache.default.get(pageItem.pdfSourcePath, pageItem.pdfPageIndex)
                ?: PdfPageGeometry.DEFAULT_ASPECT
        } else {
            PdfPageGeometry.DEFAULT_ASPECT
        }
    }

    val thumbnailResult by produceState<Pair<Bitmap?, Float>>(
        initialValue = null to initialAspect,
        noteId, pageItem.index, pageItem.updatedAt, pageItem.pdfSourcePath, pageItem.pdfPageIndex, pageType, savedGeneration
    ) {
        value = personal.notes.touch.util.BitmapUtils.loadOrRenderPageThumbnail(
            noteId = noteId,
            fileStore = fileStore,
            page = pageItem,
            pdfDocCache = pdfDocCache,
            aspectCache = PageAspectCache.default,
            pageType = pageType,
            longSidePx = 340,
            savedGeneration = savedGeneration
        )
    }
    val thumbnailBmp = thumbnailResult.first
    val pageAspect = thumbnailResult.second

    Column(
        modifier = modifier.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page preview box matching the page's true aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(pageAspect)
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

private data class SearchHighlightTarget(
    val notePageIndex: Int,
    val startOffset: Int,
    val length: Int,
    val revision: Int
)
