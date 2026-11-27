package personal.notes.touch.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.ui.theme.OrangePrimary
import personal.notes.touch.util.PdfTextIndex

/**
 * Collapsible Search & Go-to-Page sheet (`PDF-QOL-3`).
 * Supports numeric page jump, full-text search with snippet highlights,
 * next/prev hit stepping in a compact mini-navigator bar, indexing progress,
 * and scanned PDF status messaging.
 */
@Composable
fun PdfSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    totalPages: Int,
    hasPdfPages: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isIndexing: Boolean,
    indexDone: Int,
    indexTotal: Int,
    isScannedPdf: Boolean,
    hits: List<PdfTextIndex.PdfSearchHit>,
    activeHitIndex: Int,
    onJumpToPage: (Int) -> Unit,
    onSelectHit: (Int, PdfTextIndex.PdfSearchHit) -> Unit,
    onPrevHit: () -> Unit,
    onNextHit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var forceTextSearch by remember(query) { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Ignore if window not ready yet.
            }
        }
    }

    val action = remember(query, totalPages, forceTextSearch) {
        if (forceTextSearch) {
            PdfTextIndex.SearchInputAction.SearchText(query.trim())
        } else {
            PdfTextIndex.classifySearchInput(query, totalPages)
        }
    }

    if (!isExpanded) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.97f),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
            modifier = modifier
                .widthIn(min = 240.dp, max = 440.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = OrangePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onExpandedChange(true) }
                ) {
                    Text(
                        text = query.trim().ifEmpty { "Search" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF212529),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hits.isNotEmpty() && activeHitIndex in hits.indices) {
                        Text(
                            text = "${activeHitIndex + 1} / ${hits.size} · P.${hits[activeHitIndex].pageIndex + 1}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                IconButton(
                    onClick = onPrevHit,
                    enabled = hits.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Match",
                        tint = if (hits.isNotEmpty()) Color(0xFF333333) else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNextHit,
                    enabled = hits.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next Match",
                        tint = if (hits.isNotEmpty()) Color(0xFF333333) else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Expand Search",
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Search",
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = if (hasPdfPages) {
                                "Page (1–$totalPages) or search text"
                            } else {
                                "Go to page (1–$totalPages)"
                            },
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            when (action) {
                                is PdfTextIndex.SearchInputAction.JumpToPage -> {
                                    onJumpToPage(action.pageIndex)
                                    onDismiss()
                                }
                                is PdfTextIndex.SearchInputAction.SearchText -> {
                                    if (hits.isNotEmpty()) {
                                        val idx = if (activeHitIndex in hits.indices) activeHitIndex else 0
                                        onSelectHit(idx, hits[idx])
                                        onExpandedChange(false)
                                    }
                                }
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangePrimary,
                        unfocusedBorderColor = Color(0xFFDADCE0),
                        cursorColor = OrangePrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                if (hits.isNotEmpty()) {
                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Collapse Search",
                            tint = Color(0xFF555555)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Search",
                        tint = Color(0xFF555555)
                    )
                }
            }

            if (isIndexing) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (indexTotal > 0) {
                        LinearProgressIndicator(
                            progress = { (indexDone.toFloat() / indexTotal.toFloat()).coerceIn(0f, 1f) },
                            color = OrangePrimary,
                            trackColor = Color(0xFFEEEEEE),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(
                            color = OrangePrimary,
                            trackColor = Color(0xFFEEEEEE),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = if (indexTotal > 0) {
                            "Indexing pages $indexDone / $indexTotal…"
                        } else {
                            "Indexing PDF text…"
                        },
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            when (action) {
                is PdfTextIndex.SearchInputAction.JumpToPage -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = OrangePrimary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Page ${action.pageIndex + 1} of $totalPages",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF212529)
                            )
                            Button(
                                onClick = {
                                    onJumpToPage(action.pageIndex)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Go to page ${action.pageIndex + 1}", fontSize = 12.sp)
                            }
                        }
                    }
                    if (hasPdfPages && !isScannedPdf) {
                        TextButton(
                            onClick = { forceTextSearch = true },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text(
                                text = "Search \"${query.trim()}\" in document text",
                                fontSize = 12.sp,
                                color = OrangePrimary
                            )
                        }
                    }
                }
                is PdfTextIndex.SearchInputAction.SearchText -> {
                    val trimmed = action.query
                    when {
                        trimmed.isEmpty() -> {
                            Text(
                                text = if (hasPdfPages && isScannedPdf && !isIndexing) {
                                    PdfTextIndex.SCANNED_PDF_MESSAGE
                                } else if (hasPdfPages) {
                                    "Type a page number (1–$totalPages) or keywords to search."
                                } else {
                                    "Enter a page number between 1 and $totalPages."
                                },
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        !hasPdfPages -> {
                            Text(
                                text = "Enter page number (1–$totalPages) to jump.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        isScannedPdf && !isIndexing -> {
                            Text(
                                text = PdfTextIndex.SCANNED_PDF_MESSAGE,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFD84315)
                            )
                        }
                        hits.isEmpty() && !isIndexing -> {
                            Text(
                                text = "No matches found for \"$trimmed\"",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        hits.isNotEmpty() -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (activeHitIndex in hits.indices) {
                                        "${activeHitIndex + 1} / ${hits.size} matches"
                                    } else {
                                        "${hits.size} matches"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF444444)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onPrevHit,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Previous Match",
                                            tint = Color(0xFF333333)
                                        )
                                    }
                                    IconButton(
                                        onClick = onNextHit,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Next Match",
                                            tint = Color(0xFF333333)
                                        )
                                    }
                                }
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                            ) {
                                itemsIndexed(hits) { idx, hit ->
                                    val isActive = idx == activeHitIndex
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) OrangePrimary.copy(alpha = 0.08f) else Color(0xFFFAFAFA),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) OrangePrimary else Color(0xFFEAEAEA)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectHit(idx, hit)
                                                onExpandedChange(false)
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isActive) OrangePrimary else Color(0xFFEAEAEA)
                                            ) {
                                                Text(
                                                    text = "P.${hit.pageIndex + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) Color.White else Color.DarkGray,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = highlightSearchHitSnippet(hit),
                                                fontSize = 12.sp,
                                                color = Color(0xFF212529),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
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
}

private fun highlightSearchHitSnippet(hit: PdfTextIndex.PdfSearchHit): AnnotatedString = buildAnnotatedString {
    append(hit.snippet)
    hit.matchRanges.forEach { range ->
        val end = (range.last + 1).coerceAtMost(hit.snippet.length)
        if (range.first in 0 until end) {
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = OrangePrimary
                ),
                range.first,
                end
            )
        }
    }
}
