package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import android.graphics.ImageDecoder
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel,
    onBackClick: () -> Unit,
    onOpenEditorClick: (String) -> Unit,
    openNotes: List<String> = emptyList(),
    onSwitchNote: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val note by viewModel.note.collectAsState()
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

    // The page row when [index] renders from a vector PDF source.
    fun vectorPdfPage(index: Int): PageEntity? {
        val page = pages.getOrNull(index) ?: return null
        return if (page.pdfSourcePath.isNotEmpty() && page.pdfPageIndex >= 0) page else null
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
        val targetHeight = if (pageTypeName == "a4" || pageTypeName == "pdf") 1528 else 1920
        val (baseOutWidth, _) = PdfImportManager.calculateOutputSize(
            size[0].roundToInt().coerceAtLeast(1),
            size[1].roundToInt().coerceAtLeast(1),
            1080,
            targetHeight
        )
        val basePxPerPt = baseOutWidth / size[0]
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
            canvas.setPageContent(bitmap)
            applyPdfBackground(canvas, page)
        } else {
            canvas.setPdfBackground(0f, 0f, 0f, null)
            canvas.setPdfTextSource(null)
            pdfTextAvailable = false
            pdfTextMode = false
            pdfSelection = null
            canvas.setPageContent(bitmap)
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
    var pageEffectFirstRun by remember { mutableStateOf(true) }
    LaunchedEffect(currentPageIndex) {
        if (pageEffectFirstRun) {
            pageEffectFirstRun = false
            return@LaunchedEffect
        }
        // F-10: vector PDF pages load their stroke-only layer and get the live
        // pdfium background; every other page keeps the raster path.
        val vectorPage = vectorPdfPage(currentPageIndex)
        if (vectorPage != null) {
            val strokes = withContext(Dispatchers.IO) { viewModel.loadPageStrokes(currentPageIndex) }
            val canvas = canvasViewRef
            if (canvas != null) {
                canvas.setPageContent(strokes)
                // The saved page PNG is the raster ground truth of the stroke
                // layer (applied by setPageContent above). Assigning it also to
                // backgroundBitmap painted every baked stroke a second time on
                // reopen — keep a single layer: vector pages re-render their
                // background from the PDF source instead.
                canvas.backgroundBitmap = null
                viewModel.setPageLoadedFromDisk(true)
                applyPdfBackground(canvas, vectorPage)
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
                val layers = withContext(Dispatchers.IO) {
                    view.readLayersFromDisk(viewModel.fileStore, viewModel.noteId)
                }
                view.applyLoadedLayers(layers)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                    // The decoded bitmap is applied on main and then owned by
                    // the image layer (addImageLayer stores it as-is), so it
                    // must not be recycled here.
                    bitmap?.let { bmp ->
                        val added = canvasViewRef?.addImageLayer(bmp) == true
                        if (added) {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
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
                            targetWidth = 1080,
                            targetHeight = if ((note?.pageType ?: "infinite").let { it == "a4" || it == "pdf" }) 1528 else 1920,
                            reuseFirstPage = false,
                            onPageError = { msg -> showPageError(msg) }
                        )
                    }
                    // Non-destructive: background layer only, strokes untouched.
                    firstPageBitmap?.let { bmp ->
                        canvasViewRef?.backgroundBitmap = bmp
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
                val added = canvasViewRef?.addImageLayer(extracted.bitmap) == true
                if (added) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    android.widget.Toast.makeText(
                        context, "Max 3 image layers", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            !extracted.text.isNullOrBlank() -> {
                val inserted = canvasViewRef?.addTextBlock(extracted.text) == true
                if (inserted) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                val preview = extracted.text.trim().replace("\n", " ").take(60)
                android.widget.Toast.makeText(
                    context, "Pasted text: $preview...", android.widget.Toast.LENGTH_SHORT
                ).show()
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

    // Save on leave helper
    val saveAndExit: () -> Unit = {
        scope.launch {
            try {
                canvasViewRef?.getBackingBitmap()?.let { bmp ->
                    viewModel.saveCurrentPageBitmap(bmp)
                }
                // Layer save is disk I/O (PNG compress + JSON write):
                // run it on Dispatchers.IO and await it, then navigate.
                // Navigation lives inside the coroutine so the screen can
                // not leave composition mid-save (the launcher scope is
                // cancelled on disposal) — no lost layers on exit.
                withContext(Dispatchers.IO) {
                    canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onBackClick()
        }
    }

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
                    canvasViewRef?.clearCanvas()
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
                                            targetWidth = if (w > 0) w else 1080,
                                            targetHeight = if (h > 0) h else if ((note?.pageType ?: "infinite").let { it == "a4" || it == "pdf" }) 1528 else 1920
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
                                canvasViewRef?.let { it.backgroundBitmap = bmp }
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
    if (showExportDialog && note != null) {
        personal.notes.touch.ui.export.ExportDialog(
            note = note!!,
            repository = viewModel.repository,
            fileStore = viewModel.fileStore,
            currentBitmap = canvasViewRef?.getBackingBitmap(),
            backgroundBitmap = canvasViewRef?.backgroundBitmap,
            imageLayers = canvasViewRef?.imageLayers ?: emptyList(),
            currentPageIndex = currentPageIndex,
            onDismiss = { showExportDialog = false }
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
                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                viewModel.saveCurrentPageBitmap(bmp)
                            }
                            val inserted = canvasViewRef?.addTextBlock(text) == true
                            if (inserted) {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            canvasViewRef?.saveImageLayers(viewModel.fileStore, viewModel.noteId)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context, "Could not insert text", android.widget.Toast.LENGTH_SHORT
                                ).show()
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
                // Tab bar for open notes
                if (openNotes.isNotEmpty()) {
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .background(Color(0xFFEEEEEE))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        openNotes.forEach { id ->
                            val title = if (id == viewModel.noteId) (note?.title?.ifBlank { null } ?: id.take(8)) else id.take(8)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (id == viewModel.noteId) Color.White else Color(0xFFDDDDDD),
                                modifier = Modifier.clickable { onSwitchNote(id) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = title, fontSize = 12.sp, maxLines = 1)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = Color.Gray,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onCloseTab(id) }
                                    )
                                }
                            }
                        }
                    }
                }
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
                        // Back / Menu
                        IconButton(onClick = saveAndExit) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Back / Menu",
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
                            onClick = { canvasViewRef?.undo() },
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
                            onClick = { canvasViewRef?.redo() },
                            enabled = uiState.canRedo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (uiState.canRedo) Color(0xFF333333) else Color(0xFFCCCCCC)
                            )
                        }

                        // Reset zoom/pan
                        IconButton(onClick = { canvasViewRef?.resetTransform() }) {
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
                                    text = note?.title?.ifBlank { "gemni_note" } ?: "Note",
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
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Options",
                                    tint = Color(0xFF757575),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Right Action Group
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Switch to Text Editor
                        IconButton(onClick = {
                            canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                viewModel.saveCurrentPageBitmap(bmp)
                            }
                            onOpenEditorClick(viewModel.noteId)
                        }) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Text Note",
                                tint = Color(0xFF555555)
                            )
                        }

                        // Document Outline / List
                        IconButton(onClick = { viewModel.toggleThumbnailDrawer(true) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Outline",
                                tint = Color(0xFF555555)
                            )
                        }

                        // Search
                        IconButton(onClick = { /* Search within note */ }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF555555)
                            )
                        }

                        // History / Clock
                        IconButton(onClick = { /* History */ }) {
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
        }
    }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                    if (uiState.volumeForPageEnabled && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
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
                val isContinuous = uiState.pageTurningMode == 1
                val isHorizontal = uiState.pageTurningDirection == 1

                if (isContinuous) {
                    // Continuous scroll mode: all pages in a scrollable list
                    val pageBitmapsState = remember { mutableStateOf<List<Pair<Int, android.graphics.Bitmap?>>>(emptyList()) }
                    LaunchedEffect(pages, viewModel.noteId) {
                        val bitmaps = pages.mapIndexed { idx, page ->
                            val bmp = try {
                                viewModel.repository.loadPageBitmap(viewModel.noteId, idx)
                            } catch (e: Exception) { null }
                            idx to bmp
                        }
                        pageBitmapsState.value = bitmaps
                    }
                    val pageBitmaps = pageBitmapsState.value
                    if (isHorizontal) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            items(pageBitmaps.size) { idx ->
                                val bmp = pageBitmaps.getOrNull(idx)?.second
                                androidx.compose.foundation.Image(
                                    bitmap = bmp?.asImageBitmap() ?: androidx.compose.ui.graphics.ImageBitmap(1, 1),
                                    contentDescription = "Page ${idx + 1}",
                                    modifier = androidx.compose.ui.Modifier
                                        .fillParentMaxHeight()
                                        .aspectRatio(0.707f)
                                        .clickable {
                                            // Switch to single mode to edit this page
                                            viewModel.goToPage(idx, canvasViewRef?.getBackingBitmap()) { newBmp ->
                                                applyLoadedPage(idx, newBmp)
                                            }
                                        }
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            items(pageBitmaps.size) { idx ->
                                val bmp = pageBitmaps.getOrNull(idx)?.second
                                androidx.compose.foundation.Image(
                                    bitmap = bmp?.asImageBitmap() ?: androidx.compose.ui.graphics.ImageBitmap(1, 1),
                                    contentDescription = "Page ${idx + 1}",
                                    modifier = androidx.compose.ui.Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.707f)
                                        .clickable {
                                            viewModel.goToPage(idx, canvasViewRef?.getBackingBitmap()) { newBmp ->
                                                applyLoadedPage(idx, newBmp)
                                            }
                                        }
                                )
                            }
                        }
                    }
                } else {
                // Single page mode (default)
                // Drawing Canvas
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
                                    viewModel.saveCurrentPageBitmap(bmp)
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
                                    val pageIdx = viewModel.currentPageIndex.value
                                    val vectorPage = vectorPdfPage(pageIdx)
                                    if (vectorPage != null) {
                                        val strokes = withContext(Dispatchers.IO) {
                                            viewModel.loadPageStrokes(pageIdx)
                                        }
                                        setPageContent(strokes)
                                        backgroundBitmap = null
                                        viewModel.setPageLoadedFromDisk(true)
                                        applyPdfBackground(this@apply, vectorPage)
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
                        view.currentTool = uiState.selectedTool
                        view.strokeColor = uiState.selectedColor.toArgb()
                        view.strokeWidthPx = uiState.strokeWidth
                        view.paperPattern = uiState.paperPattern
                        view.backgroundMode = uiState.backgroundMode
                        view.backgroundDensity = uiState.backgroundDensity
                        view.stylusOnly = uiState.stylusOnly
                        view.readingMode = uiState.readingMode
                        view.pressureSensitivity = uiState.pressureSensitivity
                        view.pressureSizeEnabled = uiState.pressureSizeEnabled
                        view.autoShapeEnabled = uiState.autoShapeEnabled
                        view.doublePage = uiState.doublePageMode
                    },
                    modifier = Modifier.fillMaxSize()
                )
                } // end single page mode

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
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.96f),
                        shadowElevation = 6.dp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Layers ${layers.size}/3", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            layers.forEachIndexed { idx, layer ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        canvasViewRef?.setLayerVisible(idx, !layer.visible)
                                        layersTick++
                                    }) {
                                        Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "visibility")
                                    }
                                    Text("Layer ${idx + 1}", fontSize = 12.sp)
                                    IconButton(onClick = {
                                        if (idx > 0) canvasViewRef?.moveImageLayer(idx, idx - 1)
                                        layersTick++
                                    }, enabled = idx > 0) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "move up")
                                    }
                                    IconButton(onClick = {
                                        if (idx < layers.size - 1) canvasViewRef?.moveImageLayer(idx, idx + 1)
                                        layersTick++
                                    }, enabled = idx < layers.size - 1) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "move down")
                                    }
                                    IconButton(onClick = {
                                        canvasViewRef?.deleteImageLayer(idx)
                                        layersTick++
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "delete", tint = Color.Red)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                canvasViewRef?.mergeAllLayersToBase()
                                layersTick++
                            }) { Text("Merge to base", fontSize = 11.sp) }
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

                if (uiState.showPencase) {
                    if (uiState.dockHorizontal) {
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

                                // Text Tool [T] — on a vector PDF page with a
                                // text layer this toggles PDF text selection
                                // (copy / mark / underline); everywhere else it
                                // opens the text note editor as before.
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
                                Box {
                                    PencaseToolIcon(
                                        icon = Icons.Default.FormatShapes,
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
                                    onClick = { viewModel.selectTool(CanvasTool.LASSO) }
                                )

                                // Dock Orientation Toggle
                                IconButton(
                                    onClick = { viewModel.toggleDockOrientation() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChangeCircle,
                                        contentDescription = "Dock Left",
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

                                // Width Presets
                                TouchNotesWidthPresets.forEach { (widthPx, label) ->
                                    val isSelected = uiState.strokeWidth == widthPx
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrangePrimary else Color(0xFFF0F0F0))
                                            .clickable { viewModel.setStrokeWidth(widthPx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((widthPx / 3f).coerceIn(4f, 16f).dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else Color.DarkGray)
                                        )
                                    }
                                }

                                Divider(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(1.dp),
                                    color = Color.LightGray
                                )

                                // Color Swatches
                                TouchNotesColors.take(4).forEach { c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 26.dp else 22.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                if (isSelected) 2.5.dp else 1.dp,
                                                if (isSelected) OrangePrimary else Color.LightGray,
                                                CircleShape
                                            )
                                            .clickable { viewModel.selectColor(c) }
                                    )
                                }

                                // Color Picker (+) Button
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(OrangePrimary)
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Drag handle pill
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(4.dp)
                                        .background(Color.LightGray, RoundedCornerShape(2.dp))
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

                                // Text Tool [T] — on a vector PDF page with a
                                // text layer this toggles PDF text selection
                                // (copy / mark / underline); everywhere else it
                                // opens the text note editor as before.
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
                                Box {
                                    PencaseToolIcon(
                                        icon = Icons.Default.FormatShapes,
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
                                    onClick = { viewModel.selectTool(CanvasTool.LASSO) }
                                )

                                // Dock Orientation Toggle
                                IconButton(
                                    onClick = { viewModel.toggleDockOrientation() },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChangeCircle,
                                        contentDescription = "Dock Top",
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

                                // Width Presets (vertical dots)
                                TouchNotesWidthPresets.take(3).forEach { (widthPx, _) ->
                                    val isSelected = uiState.strokeWidth == widthPx
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) OrangePrimary else Color(0xFFF0F0F0))
                                            .clickable { viewModel.setStrokeWidth(widthPx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((widthPx / 3f).coerceIn(3f, 12f).dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else Color.DarkGray)
                                        )
                                    }
                                }

                                Divider(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(1.dp),
                                    color = Color.LightGray
                                )

                                // Color Swatches (vertical)
                                TouchNotesColors.take(3).forEach { c ->
                                    val isSelected = uiState.selectedColor == c
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 24.dp else 20.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                if (isSelected) 2.5.dp else 1.dp,
                                                if (isSelected) OrangePrimary else Color.LightGray,
                                                CircleShape
                                            )
                                            .clickable { viewModel.selectColor(c) }
                                    )
                                }

                                // Color Picker (+) Button
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(OrangePrimary)
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
                        // Previous Page Arrow
                        IconButton(
                            onClick = {
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
                            },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Prev Page",
                                tint = if (currentPageIndex > 0) Color.DarkGray else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Current Page Number
                        Text(
                            text = "${currentPageIndex + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )

                        Divider(
                            modifier = Modifier
                                .width(16.dp)
                                .padding(vertical = 3.dp),
                            color = Color.LightGray
                        )

                        // Total Pages
                        Text(
                            text = "$totalPages",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        // Next Page Arrow
                        IconButton(
                            onClick = {
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
                            },
                            enabled = currentPageIndex < totalPages - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
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

                        // Layers / Outline Toggle
                        IconButton(
                            onClick = { viewModel.toggleThumbnailDrawer() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Layers",
                                tint = if (uiState.isThumbnailDrawerOpen) OrangePrimary else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 5. BOTTOM-LEFT FLOATING PAGE JUMPER
                // ==========================================
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "<  ${currentPageIndex + 1}  >",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray,
                            modifier = Modifier
                                .clickable { viewModel.toggleThumbnailDrawer(true) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        // Layers panel toggle next to page number
                        IconButton(
                            onClick = { showLayersPanel = !showLayersPanel },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Layers",
                                tint = if (showLayersPanel) OrangePrimary else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 5b. FLOATING PEN BOX
                // ==========================================
                if (uiState.penBoxEnabled) {
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
                                                    val currentBmp = canvasViewRef?.getBackingBitmap()
                                                    viewModel.addNewPage(currentBmp) { newBmp ->
                                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
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
                                                                val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                viewModel.goToPage(pageItem.index, currentBmp) { newBmp ->
                                                                    applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
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
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            IconButton(
                                                                onClick = { viewModel.toggleBookmark(pageItem.index) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                                    contentDescription = if (isBookmarked) "Remove Bookmark" else "Bookmark",
                                                                    tint = if (isBookmarked) OrangePrimary else Color.Gray,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = "${pageItem.index + 1}",
                                                                fontSize = 13.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) OrangePrimary else Color.DarkGray
                                                            )
                                                            Icon(
                                                                imageVector = Icons.Default.MoreVert,
                                                                contentDescription = "More",
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        // Page paper thumbnail representation
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(110.dp)
                                                                .background(Color.White, RoundedCornerShape(4.dp))
                                                                .border(0.5.dp, Color(0xFFEAEAEA), RoundedCornerShape(4.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "Page ${pageItem.index + 1}",
                                                                fontSize = 12.sp,
                                                                color = Color.LightGray
                                                            )
                                                        }
                                                    }
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
                                                                    val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                    viewModel.goToPage(pageItem.index, currentBmp) { newBmp ->
                                                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
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
                                                        Column(modifier = Modifier.padding(8.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                IconButton(
                                                                    onClick = { viewModel.toggleBookmark(pageItem.index) },
                                                                    modifier = Modifier.size(24.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Bookmark,
                                                                        contentDescription = "Remove Bookmark",
                                                                        tint = OrangePrimary,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = "Page ${pageItem.index + 1}",
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (isSelected) OrangePrimary else Color.DarkGray
                                                                )
                                                                Spacer(modifier = Modifier.width(16.dp))
                                                            }

                                                            Spacer(modifier = Modifier.height(4.dp))

                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(100.dp)
                                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                                    .border(0.5.dp, Color(0xFFEAEAEA), RoundedCornerShape(4.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "Page ${pageItem.index + 1}",
                                                                    fontSize = 12.sp,
                                                                    color = Color.LightGray
                                                                )
                                                            }
                                                        }
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
                                                                    val currentBmp = canvasViewRef?.getBackingBitmap()
                                                                    viewModel.goToPage(tocEntry.pageIndex, currentBmp) { newBmp ->
                                                                        applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
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
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(320.dp)
                            .fillMaxHeight(),
                        color = Color.White,
                        shadowElevation = 16.dp
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Tabs Header: Tools | Export | Settings | Manager
                            val tabs = listOf("Tools", "Export", "Settings", "Manager")
                            TabRow(
                                selectedTabIndex = uiState.activeToolsTab,
                                containerColor = Color.White,
                                contentColor = OrangePrimary,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[uiState.activeToolsTab]),
                                        color = OrangePrimary
                                    )
                                }
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = uiState.activeToolsTab == index,
                                        onClick = { viewModel.setActiveToolsTab(index) },
                                        text = {
                                            Text(
                                                text = title,
                                                fontSize = 13.sp,
                                                fontWeight = if (uiState.activeToolsTab == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (uiState.activeToolsTab == index) OrangePrimary else Color.DarkGray
                                            )
                                        }
                                    )
                                }
                            }

                            Divider(color = Color(0xFFEAEAEA), thickness = 1.dp)

                            // Tab Content
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                when (uiState.activeToolsTab) {
                                    0 -> {
                                        // TOOLS TAB
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            // Productivity Integration
                                            Text("Productivity Integration", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Category, "Web Excerpt") { /* Web excerpt */ }
                                            ToolsPanelItem(Icons.Default.Category, "Open in CardsMind") { /* CardsMind */ }

                                            // Add Section
                                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Image, "Image") {
                                                imagePickerLauncher.launch("image/*")
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Description, "Import PDF") {
                                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Category, "Sticker") { /* Sticker */ }
                                            ToolsPanelItem(Icons.Default.Timeline, "Add Mind Map") { /* MindMap */ }
                                            ToolsPanelItem(Icons.Default.Category, "Flashcard") { /* Flashcard */ }

                                            // Page Section
                                            Text("Page", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Add, "Add Page") {
                                                val currentBmp = canvasViewRef?.getBackingBitmap()
                                                viewModel.addNewPage(currentBmp) { newBmp ->
                                                    applyLoadedPage(viewModel.currentPageIndex.value, newBmp)
                                                }
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.GridOn, "Template (Paper Pattern)") {
                                                // Cycle paper pattern
                                                val nextPattern = when (uiState.paperPattern) {
                                                    PaperPattern.BLANK -> PaperPattern.GRID
                                                    PaperPattern.GRID -> PaperPattern.LINES
                                                    PaperPattern.LINES -> PaperPattern.DOTTED
                                                    PaperPattern.DOTTED -> PaperPattern.BLANK
                                                }
                                                viewModel.setPaperPattern(nextPattern)
                                            }

                                            // Write Section
                                            Text("Write", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Brush, "Zoom Write") { /* Zoom write */ }
                                            ToolsPanelItem(Icons.Default.AutoFixHigh, "Auto Recognition") { /* OCR */ }
                                            ToolsPanelItem(Icons.Default.Timeline, "Laser Pointer") { /* Pointer */ }

                                            // Other Section
                                            Text("Other", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Save, "Save Note") {
                                                canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                    viewModel.saveCurrentPageBitmap(bmp)
                                                }
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Delete, "Clear Canvas") {
                                                showClearDialog = true
                                                viewModel.toggleToolsPanel(false)
                                            }
                                        }
                                    }
                                    1 -> {
                                        // EXPORT TAB
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text("Document Export", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Description, "Export as Multi-Page PDF") {
                                                canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                    viewModel.saveCurrentPageBitmap(bmp)
                                                }
                                                showExportDialog = true
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Image, "Export Current Page as PNG") {
                                                canvasViewRef?.getBackingBitmap()?.let { bmp ->
                                                    viewModel.saveCurrentPageBitmap(bmp)
                                                }
                                                showExportDialog = true
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.TextFields, "Export Plain Text / Markdown") {
                                                showExportDialog = true
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Share, "Share via Android Sheet") {
                                                showExportDialog = true
                                                viewModel.toggleToolsPanel(false)
                                            }
                                        }
                                    }
                                    2 -> {
                                        // SETTINGS TAB (matches m2_04.png)
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            SettingsCheckboxRow(
                                                title = "Stylus Mode",
                                                subtitle = "Only allow stylus writing permitted",
                                                checked = uiState.stylusOnly,
                                                onCheckedChange = { viewModel.toggleStylusOnly(it) }
                                            )
                                            SettingsCheckboxRow(
                                                title = "Show Pencase",
                                                subtitle = "Display floating toolbar dock",
                                                checked = uiState.showPencase,
                                                onCheckedChange = { viewModel.toggleShowPencase(it) }
                                            )
                                            SettingsCheckboxRow(
                                                title = "Double Page Mode",
                                                subtitle = "Side-by-side pages on landscape",
                                                checked = uiState.doublePageMode,
                                                onCheckedChange = { viewModel.toggleDoublePageMode(it) }
                                            )
                                            SettingsCheckboxRow(
                                                title = "Reading Mode",
                                                subtitle = "Editing notes is not allowed",
                                                checked = uiState.readingMode,
                                                onCheckedChange = { viewModel.toggleReadingMode(it) }
                                            )

                                            Divider(color = Color(0xFFEEEEEE))

                                            Text("Page Turning Direction", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.DarkGray)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = OrangePrimary,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        "Vertical",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF0F0F0),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        "Horizontal",
                                                        color = Color.DarkGray,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text("Horizontal Move Resistance", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.DarkGray)
                                            var resistanceVal by remember { mutableFloatStateOf(0.4f) }
                                            Slider(
                                                value = resistanceVal,
                                                onValueChange = { resistanceVal = it },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = OrangePrimary,
                                                    activeTrackColor = OrangePrimary
                                                )
                                            )

                                            Text("Minimal Zoom of Infinite: 5%", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.DarkGray)
                                            var zoomVal by remember { mutableFloatStateOf(0.05f) }
                                            Slider(
                                                value = zoomVal,
                                                onValueChange = { zoomVal = it },
                                                colors = SliderDefaults.colors(
                                                    thumbColor = OrangePrimary,
                                                    activeTrackColor = OrangePrimary
                                                )
                                            )
                                        }
                                    }
                                    3 -> {
                                        // MANAGER TAB
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text("Note Manager", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                            ToolsPanelItem(Icons.Default.Edit, "Rename Note") {
                                                newTitleText = note?.title ?: ""
                                                showRenameDialog = true
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Layers, "Page Manager Dialog") {
                                                viewModel.toggleThumbnailDrawer(true)
                                                viewModel.toggleToolsPanel(false)
                                            }
                                            ToolsPanelItem(Icons.Default.Delete, "Delete Note") {
                                                viewModel.deleteCurrentNote()
                                                saveAndExit()
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
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = OrangePrimary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF212529)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}
