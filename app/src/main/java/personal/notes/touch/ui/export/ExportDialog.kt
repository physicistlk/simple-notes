package personal.notes.touch.ui.export

import personal.notes.touch.ui.theme.OrangePrimary
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.files.UiPrefs
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.ui.canvas.ImageLayer
import personal.notes.touch.ui.theme.LocalThemeColor
import personal.notes.touch.util.ExportManager
import personal.notes.touch.util.MarginExportMath
import java.util.concurrent.atomic.AtomicLong

private data class ExportPreviewState(
    val includeMargins: Boolean,
    val ready: Boolean,
    val bitmap: Bitmap?
)

enum class ExportTarget {
    PDF,
    PNG_CURRENT_PAGE,
    MARKDOWN,
    PLAIN_TEXT
}

@Composable
fun ExportDialog(
    note: NoteEntity,
    repository: NoteRepository,
    fileStore: NoteFileStore,
    currentBitmap: Bitmap? = null,
    backgroundBitmap: Bitmap? = null,
    imageLayers: List<ImageLayer> = emptyList(),
    currentPageIndex: Int = 0,
    initialTarget: ExportTarget? = null,
    includeMargins: Boolean? = null,
    onIncludeMarginsChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColor = LocalThemeColor.current

    var isExporting by remember { mutableStateOf(false) }
    var exportStatusText by remember { mutableStateOf("") }

    val supportsMarginExport = remember(note.pageType) {
        MarginExportMath.supportsMarginExport(note.pageType)
    }
    var selectedIncludeMargins by remember(note.id) {
        mutableStateOf(includeMargins ?: false)
    }
    var marginsPrefReady by remember(note.id, includeMargins, supportsMarginExport) {
        mutableStateOf(includeMargins != null || !supportsMarginExport)
    }
    var userEditedMargins by remember(note.id) {
        mutableStateOf(false)
    }
    val marginPersistMutex = remember(fileStore) { Mutex() }
    val marginWriteSeq = remember(fileStore) { AtomicLong(0L) }
    LaunchedEffect(note.id, includeMargins) {
        if (includeMargins != null) {
            selectedIncludeMargins = includeMargins
            marginsPrefReady = true
        }
    }
    LaunchedEffect(note.id, fileStore, includeMargins == null, supportsMarginExport) {
        if (includeMargins == null && supportsMarginExport) {
            val saved = withContext(Dispatchers.IO) {
                fileStore.uiPrefs().getBoolean(UiPrefs.KEY_EXPORT_INCLUDE_MARGINS, false)
            }
            if (!userEditedMargins) {
                selectedIncludeMargins = saved
            }
            marginsPrefReady = true
        }
    }

    val safeFilename = remember(note.title) { ExportManager.sanitizeFilename(note.title) }

    val resolvedMarkdownContent = remember(note.title, note.content) {
        note.content?.takeIf { it.isNotBlank() }
            ?: "# ${note.title.ifBlank { "Untitled Note" }}\n\nExported from Simple Notes.\n"
    }

    // Produces the composite flattened bitmap (including PDF background, image layers, and white paper bed)
    // for single-page PNG export across both Single-Page and Continuous Modes.
    val previewState by produceState(
        initialValue = ExportPreviewState(
            includeMargins = selectedIncludeMargins,
            ready = false,
            bitmap = null
        ),
        note.id, currentPageIndex, currentBitmap, backgroundBitmap, imageLayers, selectedIncludeMargins, marginsPrefReady
    ) {
        if (!marginsPrefReady) return@produceState
        val oldBitmap = value.bitmap
        value = ExportPreviewState(
            includeMargins = selectedIncludeMargins,
            ready = false,
            bitmap = null
        )
        if (oldBitmap != null &&
            oldBitmap !== currentBitmap &&
            oldBitmap !== backgroundBitmap &&
            !oldBitmap.isRecycled
        ) {
            oldBitmap.recycle()
        }
        val loaded = withContext(Dispatchers.IO) {
            if (currentBitmap != null && !currentBitmap.isRecycled) {
                ExportManager.flattenCurrentPageForExport(
                    note = note,
                    repository = repository,
                    fileStore = fileStore,
                    pageIndex = currentPageIndex,
                    currentEditingBitmap = currentBitmap,
                    currentEditingBackground = backgroundBitmap,
                    currentEditingImageLayers = imageLayers,
                    includeMargins = selectedIncludeMargins
                )
            } else {
                ExportManager.loadFlattenedPageBitmap(
                    fileStore = fileStore,
                    noteId = note.id,
                    pageIndex = currentPageIndex,
                    includeLayers = true,
                    pageType = note.pageType,
                    repository = repository,
                    includeMargins = selectedIncludeMargins
                )
            }
        }
        value = ExportPreviewState(
            includeMargins = selectedIncludeMargins,
            ready = true,
            bitmap = loaded
        )
    }
    val latestPreviewBitmap by rememberUpdatedState(previewState.bitmap)
    DisposableEffect(note.id) {
        onDispose {
            val bmp = latestPreviewBitmap
            if (bmp != null &&
                bmp !== currentBitmap &&
                bmp !== backgroundBitmap &&
                !bmp.isRecycled
            ) {
                bmp.recycle()
            }
        }
    }
    val pngReady = marginsPrefReady && previewState.ready && previewState.includeMargins == selectedIncludeMargins
    val effectivePageBitmap = previewState.bitmap?.takeIf { pngReady && !it.isRecycled }

    // Text notes get the markdown-rendering exporters (HTML / paginated PDF);
    // drawing notes keep the rasterised page exporter.
    val isTextNote = note.pageType == "text"

    // SAF Document Creator Launchers
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                exportStatusText = "Writing PDF to device storage..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        if (isTextNote) {
                            ExportManager.exportTextNoteToPdfStream(
                                title = note.title,
                                content = note.content.orEmpty(),
                                out = out
                            )
                        } else {
                            ExportManager.exportNoteToPdfStream(
                                note = note,
                                repository = repository,
                                fileStore = fileStore,
                                currentEditingBitmap = currentBitmap,
                                currentPageIndex = currentPageIndex,
                                outputStream = out,
                                currentEditingBackground = backgroundBitmap,
                                currentEditingImageLayers = imageLayers,
                                includeMargins = selectedIncludeMargins
                            )
                        }
                    }
                    Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val bmp = effectivePageBitmap
        if (uri != null && bmp != null) {
            scope.launch {
                isExporting = true
                exportStatusText = "Saving PNG image..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ExportManager.exportPageToOutputStream(bmp, out, backgroundBitmap = null, imageLayers = emptyList())
                    }
                    Toast.makeText(context, "Image saved successfully!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving image: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val createTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                exportStatusText = "Saving text note..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.bufferedWriter().use { writer ->
                            writer.write(resolvedMarkdownContent)
                        }
                    }
                    Toast.makeText(context, "Text note saved!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val createHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                exportStatusText = "Writing HTML page..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ExportManager.exportTextNoteToHtmlStream(
                            title = note.title.ifBlank { "Untitled Note" },
                            content = resolvedMarkdownContent,
                            out = out
                        )
                    }
                    Toast.makeText(context, "HTML page saved!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = themeColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Export Note",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                if (!isExporting) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        },
        text = {
            if (isExporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = themeColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = exportStatusText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose an export format and delivery destination:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    if (supportsMarginExport) {
                        val onSelectMargins: (Boolean) -> Unit = { next ->
                            userEditedMargins = true
                            selectedIncludeMargins = next
                            if (onIncludeMarginsChange != null) {
                                onIncludeMarginsChange(next)
                            } else {
                                val seq = marginWriteSeq.incrementAndGet()
                                scope.launch {
                                    marginPersistMutex.withLock {
                                        if (seq == marginWriteSeq.get()) {
                                            withContext(Dispatchers.IO) {
                                                fileStore.uiPrefs().putBoolean(
                                                    UiPrefs.KEY_EXPORT_INCLUDE_MARGINS,
                                                    next
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Margin notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MarginOptionChip(
                                        label = "Page only",
                                        selected = !selectedIncludeMargins,
                                        accentColor = themeColor,
                                        onClick = { onSelectMargins(false) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    MarginOptionChip(
                                        label = "Include margins",
                                        selected = selectedIncludeMargins,
                                        accentColor = themeColor,
                                        onClick = { onSelectMargins(true) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Option 1: PDF Document
                    ExportOptionCard(
                        title = "PDF Document (.pdf)",
                        subtitle = if (isTextNote) {
                            "Paginated A4 PDF rendered from the note text"
                        } else {
                            "High-resolution multi-page PDF document"
                        },
                        icon = Icons.Default.PictureAsPdf,
                        iconTint = Color(0xFFD32F2F),
                        isSelected = initialTarget == ExportTarget.PDF,
                        onShareClick = {
                            scope.launch {
                                isExporting = true
                                exportStatusText = "Rendering PDF pages..."
                                try {
                                    val file = if (isTextNote) {
                                        ExportManager.exportTextNoteToPdfFile(
                                            context = context,
                                            noteTitle = note.title,
                                            content = note.content.orEmpty()
                                        )
                                    } else {
                                        ExportManager.exportNoteToPdfFile(
                                            context = context,
                                            note = note,
                                            repository = repository,
                                            fileStore = fileStore,
                                            currentEditingBitmap = currentBitmap,
                                            currentPageIndex = currentPageIndex,
                                            currentEditingBackground = backgroundBitmap,
                                            currentEditingImageLayers = imageLayers,
                                            includeMargins = selectedIncludeMargins
                                        )
                                    }
                                    ExportManager.shareFile(context, file, "application/pdf", "Share PDF Note")
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        onSaveClick = {
                            createPdfLauncher.launch("$safeFilename.pdf")
                        }
                    )

                    // Option 2: PNG Image (When current page bitmap exists or is preparing)
                    val exportBmp = effectivePageBitmap
                    if (exportBmp != null || (!isTextNote && !pngReady)) {
                        ExportOptionCard(
                            title = "Current Page Image (.png)",
                            subtitle = if (!pngReady) {
                                "Preparing page image..."
                            } else {
                                "Lossless PNG picture of page ${currentPageIndex + 1}"
                            },
                            icon = Icons.Default.Image,
                            iconTint = Color(0xFF1976D2),
                            isSelected = initialTarget == ExportTarget.PNG_CURRENT_PAGE,
                            enabled = pngReady && exportBmp != null,
                            onShareClick = {
                                val targetBmp = exportBmp ?: return@ExportOptionCard
                                scope.launch {
                                    isExporting = true
                                    exportStatusText = "Exporting PNG..."
                                    try {
                                        val file = ExportManager.exportPageToPngFile(
                                            context = context,
                                            noteTitle = note.title,
                                            pageIndex = currentPageIndex,
                                            bitmap = targetBmp,
                                            backgroundBitmap = null,
                                            imageLayers = emptyList()
                                        )
                                        ExportManager.shareFile(context, file, "image/png", "Share Note Image")
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            onSaveClick = {
                                if (pngReady && exportBmp != null) {
                                    createPngLauncher.launch("${safeFilename}_p${currentPageIndex + 1}.png")
                                }
                            }
                        )
                    }

                    // Option 3: Markdown / Text (When note has text content or targeted)
                    if (!note.content.isNullOrBlank() || initialTarget == ExportTarget.MARKDOWN) {
                        val markdownContent = note.content?.takeIf { it.isNotBlank() }
                            ?: "# ${note.title.ifBlank { "Untitled Note" }}\n\nExported from Simple Notes.\n"
                        ExportOptionCard(
                            title = "Markdown Text (.md)",
                            subtitle = "Clean portable markdown file with formatting",
                            icon = Icons.Default.Description,
                            iconTint = Color(0xFF388E3C),
                            isSelected = initialTarget == ExportTarget.MARKDOWN,
                            onShareClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatusText = "Preparing markdown text..."
                                    try {
                                        val file = ExportManager.exportTextNoteToFile(
                                            context = context,
                                            noteTitle = note.title,
                                            content = markdownContent
                                        )
                                        ExportManager.shareFile(context, file, "text/plain", "Share Markdown Note")
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            onSaveClick = {
                                createTextLauncher.launch("$safeFilename.md")
                            }
                        )

                        ExportOptionCard(
                            title = "Web Page (.html)",
                            subtitle = "Standalone page with formatting, opens in any browser",
                            icon = Icons.Default.Language,
                            iconTint = Color(0xFF00796B),
                            onShareClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatusText = "Preparing HTML page..."
                                    try {
                                        val file = ExportManager.exportTextNoteToHtmlFile(
                                            context = context,
                                            noteTitle = note.title,
                                            content = resolvedMarkdownContent
                                        )
                                        ExportManager.shareFile(context, file, "text/html", "Share HTML Note")
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            onSaveClick = {
                                createHtmlLauncher.launch("$safeFilename.html")
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = themeColor)
                }
            }
        }
    )
}

@Composable
private fun MarginOptionChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, OrangePrimary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = OrangePrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Selected",
                                    color = OrangePrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 1f else 0.45f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled, onClick = onShareClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Share",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled, onClick = onSaveClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Save to Device",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save to File",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
