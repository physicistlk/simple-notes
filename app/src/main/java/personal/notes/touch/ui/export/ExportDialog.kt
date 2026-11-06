package personal.notes.touch.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.ui.canvas.ImageLayer
import personal.notes.touch.ui.theme.LocalThemeColor
import personal.notes.touch.util.ExportManager

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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColor = LocalThemeColor.current

    var isExporting by remember { mutableStateOf(false) }
    var exportStatusText by remember { mutableStateOf("") }

    val safeFilename = remember(note.title) { ExportManager.sanitizeFilename(note.title) }

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
                                currentEditingImageLayers = imageLayers
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
        if (uri != null && currentBitmap != null) {
            scope.launch {
                isExporting = true
                exportStatusText = "Saving PNG image..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ExportManager.exportPageToOutputStream(currentBitmap, out, backgroundBitmap, imageLayers)
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
                            if (note.title.isNotBlank()) {
                                writer.write("# ${note.title}\n\n")
                            }
                            writer.write(note.content ?: "")
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
                            title = note.title,
                            content = note.content.orEmpty(),
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose an export format and delivery destination:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

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
                                            currentEditingImageLayers = imageLayers
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

                    // Option 2: PNG Image (When current page bitmap exists)
                    if (currentBitmap != null) {
                        ExportOptionCard(
                            title = "Current Page Image (.png)",
                            subtitle = "Lossless PNG picture of page ${currentPageIndex + 1}",
                            icon = Icons.Default.Image,
                            iconTint = Color(0xFF1976D2),
                            onShareClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatusText = "Exporting PNG..."
                                    try {
                                        val file = ExportManager.exportPageToPngFile(
                                            context = context,
                                            noteTitle = note.title,
                                            pageIndex = currentPageIndex,
                                            bitmap = currentBitmap,
                                            backgroundBitmap = backgroundBitmap
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
                                createPngLauncher.launch("${safeFilename}_p${currentPageIndex + 1}.png")
                            }
                        )
                    }

                    // Option 3: Markdown / Text (When note has text content)
                    if (!note.content.isNullOrBlank()) {
                        ExportOptionCard(
                            title = "Markdown Text (.md)",
                            subtitle = "Clean portable markdown file with formatting",
                            icon = Icons.Default.Description,
                            iconTint = Color(0xFF388E3C),
                            onShareClick = {
                                scope.launch {
                                    isExporting = true
                                    exportStatusText = "Preparing markdown text..."
                                    try {
                                        val file = ExportManager.exportTextNoteToFile(
                                            context = context,
                                            noteTitle = note.title,
                                            content = note.content
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
                                            content = note.content.orEmpty()
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
private fun ExportOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShareClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Share",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSaveClick)
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
