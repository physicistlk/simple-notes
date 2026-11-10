package personal.notes.touch.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import personal.notes.touch.MainActivity
import personal.notes.touch.ui.theme.LocalThemeColor
import personal.notes.touch.ui.theme.TouchNotesSwatches
import personal.notes.touch.util.BackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val themeColor = LocalThemeColor.current
    val penDefaults = parsePenDefaults(settings.penDefaultsJson)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isBackupRunning by remember { mutableStateOf(false) }
    var backupStatusText by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    // SAF Document Creator for Backup (.zip)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isBackupRunning = true
                backupStatusText = "Packing database and note files..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        val result = BackupManager.createBackup(context, out) { filename, count ->
                            backupStatusText = "Backing up: $filename ($count files)"
                        }
                        if (result.success) {
                            val sizeKb = result.totalBytes / 1024
                            Toast.makeText(context, "Backup complete! (${result.fileCount} files, $sizeKb KB)", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Backup failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isBackupRunning = false
                }
            }
        }
    }

    // SAF Document Picker for Restore (.zip / .np)
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
            },
            title = { Text("Restore Backup Archive?") },
            text = {
                Text("Restoring will replace all current notes, pages, and folders with the contents of the selected backup file. Any notes not in the backup will be overwritten. Continue?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRestoreUri ?: return@TextButton
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                        scope.launch {
                            isBackupRunning = true
                            backupStatusText = "Restoring notes and database..."
                            val result = BackupManager.restoreBackup(context, uri) { file ->
                                backupStatusText = "Restoring: $file"
                            }
                            isBackupRunning = false
                            if (result.success) {
                                Toast.makeText(context, "Restore complete! Restarting...", Toast.LENGTH_SHORT).show()
                                val restartIntent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                context.startActivity(restartIntent)
                            } else {
                                Toast.makeText(context, "Restore failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Restore & Overwrite", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isBackupRunning) {
        AlertDialog(
            // The user must not be trapped behind the dialog if the write
            // hangs: it can be closed, the work continues in the background.
            onDismissRequest = { isBackupRunning = false },
            title = { Text("Processing Backup...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = themeColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(backupStatusText, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { isBackupRunning = false }) { Text("Run in background") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColor
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Appearance & Theme Section
            item {
                SettingsSectionHeader(title = "Appearance & Theme")
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = themeColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Theme Accent Color",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Choose from the 10 signature color swatches:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TouchNotesSwatches.forEach { swatch ->
                                val swatchArgb = swatch.color.toArgb()
                                val isSelected = settings.themeColor == swatchArgb
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setThemeColor(swatchArgb) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(swatch.color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.LightGray.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = swatch.name.substringBefore(" ("),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Dark Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val modes = listOf(
                                0 to "System Default",
                                1 to "Light",
                                2 to "Dark"
                            )
                            modes.forEach { (modeVal, label) ->
                                val isSelected = settings.darkMode == modeVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setDarkMode(modeVal) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Canvas & Note Defaults Section
            item {
                SettingsSectionHeader(title = "Canvas & Note Defaults")
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Default Paper Background Pattern",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val patterns = listOf(
                                0 to "Blank",
                                1 to "Grid",
                                2 to "Lines",
                                3 to "Dotted"
                            )
                            patterns.forEach { (patternVal, label) ->
                                val isSelected = settings.defaultBackground == patternVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setDefaultBackground(patternVal) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Selectable Paper Background",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val modes = listOf(0 to "Blank", 1 to "Lined", 2 to "Grid", 3 to "Dotted")
                            modes.forEach { (modeVal, label) ->
                                val isSelected = settings.backgroundMode == modeVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setBackgroundMode(modeVal) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Density (spacing): ${settings.backgroundDensity}", fontSize = 13.sp)
                        Slider(
                            value = settings.backgroundDensity.toFloat(),
                            onValueChange = { viewModel.setBackgroundDensity(it.toInt()) },
                            valueRange = 20f..80f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Default Pen Stroke Width",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val widths = listOf(
                                2f to "Fine (2px)",
                                4f to "Normal (4px)",
                                6f to "Bold (6px)",
                                10f to "Thick (10px)"
                            )
                            widths.forEach { (w, label) ->
                                val isSelected = (penDefaults.strokeWidth - w) in -0.1f..0.1f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setPenDefaults(penDefaults.color, w) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Default Pen Color",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val penColors = listOf(
                                0xFF000000.toInt() to "Black",
                                0xFF333333.toInt() to "Dark Gray",
                                0xFFD32F2F.toInt() to "Red",
                                0xFF1976D2.toInt() to "Blue",
                                0xFF388E3C.toInt() to "Green",
                                themeColor.toArgb() to "Theme"
                            )
                            penColors.forEach { (colorVal, _) ->
                                val isSelected = penDefaults.color == colorVal
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorVal))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setPenDefaults(colorVal, penDefaults.strokeWidth) }
                                        // The swatch is the only way to pick a
                                        // colour: it needs a name for the
                                        // screen reader.
                                        .semantics { contentDescription = "Pen colour ${colorVal.toULong().toString(16)}" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (Color(colorVal) == Color.White) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stylus Pressure Curve Calibration",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = when {
                                    penDefaults.pressureSensitivity < 0.7f -> "Soft (Light Touch)"
                                    penDefaults.pressureSensitivity > 1.3f -> "Firm (Heavy Touch)"
                                    else -> "Standard (1.0x)"
                                },
                                fontSize = 13.sp,
                                color = themeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calibrates quadratic Bézier inking dynamics for active stylus pens.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = penDefaults.pressureSensitivity,
                            onValueChange = { viewModel.setPressureSensitivity(it) },
                            valueRange = 0.3f..2.0f,
                            steps = 17,
                            colors = SliderDefaults.colors(
                                thumbColor = themeColor,
                                activeTrackColor = themeColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pressure-sensitive Width",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Vary stroke width with stylus pressure for all drawing tools",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings.pressureSizeEnabled,
                                onCheckedChange = { viewModel.setPressureSizeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColor
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        // Auto-shape toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-shape Strokes",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Straighten freehand strokes into lines/shapes",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings.autoShapeEnabled,
                                onCheckedChange = { viewModel.setAutoShapeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColor
                                )
                            )
                        }
                    }
                }
            }

            // 3. Behavior Section
            item {
                SettingsSectionHeader(title = "Behavior & Auto-Save")
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep Screen On",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Prevent tablet screen from sleeping while writing or drawing",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings.keepScreenOn,
                                onCheckedChange = { viewModel.setKeepScreenOn(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColor
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Double-Page Spread",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Draw across two pages side by side with a gutter",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings.doublePage,
                                onCheckedChange = { viewModel.setDoublePage(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColor
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Page Turning Direction",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val directions = listOf(0 to "Vertical", 1 to "Horizontal")
                            directions.forEach { (dirVal, label) ->
                                val isSelected = settings.pageTurningDirection == dirVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setPageTurningDirection(dirVal) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Page Turning Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val modes = listOf(0 to "Continuous", 1 to "Single")
                            modes.forEach { (modeVal, label) ->
                                val isSelected = settings.pageTurningMode == modeVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setPageTurningMode(modeVal) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Volume Keys for Page Turn",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Use volume up/down to navigate pages on canvas",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings.volumeForPage,
                                onCheckedChange = { viewModel.setVolumeForPage(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColor
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show Pen Box",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Floating quick-switch palette with 2 pen slots",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings.showPenBox,
                                onCheckedChange = { viewModel.setShowPenBox(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = themeColor
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Full-Screen Mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(text = "Hide system bars on the canvas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Switch(checked = settings.fullScreen, onCheckedChange = { viewModel.setFullScreen(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = themeColor))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Auto-Center Page", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(text = "Center page on canvas open", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Switch(checked = settings.autoCenter, onCheckedChange = { viewModel.setAutoCenter(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = themeColor))
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        Text(
                            text = "Auto-Save Interval",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Frequency for saving drawing bitmaps and note edits to disk:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val intervals = listOf(
                                500 to "500 ms",
                                1000 to "1 sec (Default)",
                                2000 to "2 sec",
                                5000 to "5 sec"
                            )
                            intervals.forEach { (ms, label) ->
                                val isSelected = settings.autoSaveIntervalMs == ms
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setAutoSaveInterval(ms) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = themeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 4. Backup & Restore Section (M9)
            item {
                SettingsSectionHeader(title = "Backup & Restore (M9)")
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = themeColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline Backup & Migration",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Export your entire notes library (SQLite database + PNG canvas pages + rich text) to a compressed ZIP archive (.np / .zip) on device storage, or restore an existing backup.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                    createBackupLauncher.launch("touchnotes_backup_$timestamp.zip")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Backup", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    restorePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore Backup", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 5. Storage & About Section
            item {
                SettingsSectionHeader(title = "About & Storage")
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = themeColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Local Storage & Persistence (M8)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Room SQLite Database + PNG File Store",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = themeColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Simple Notes v0.1.0",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "100% Offline • Target: Xiaomi Pad 5 (256 GB internal storage, 6 GB RAM)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Simple Notes • Phase 6",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = LocalThemeColor.current,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}
