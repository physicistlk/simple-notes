package personal.notes.touch.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which half of a two-pane workspace a callback refers to. */
enum class SplitSide { LEFT, RIGHT }

/**
 * Two text notes side by side — the multi-document workspace.
 *
 * Each visible pane is a full [TextEditorScreen] with `embedded = true`, so
 * every editor feature (block editing, preview, references, export) works
 * inside a pane without a second implementation. The screen itself only owns
 * the workspace bar (back / swap), the divider and the empty-pane rules.
 *
 * Panes are **state, not routes**: swap / close / replace only change which
 * notes are passed in, so no destination is destroyed and no editor ViewModel
 * is cleared mid-edit. That matters because the editor's autosave is debounced
 * — tearing the destination down would race the save.
 *
 * Limits, stated so the UI can be honest:
 * - at most two panes; closing one leaves the survivor full width;
 * - text notes only (drawing notes have their own full-screen canvas);
 * - the two panes are always different notes, so one note never has two editors
 *   sharing a ViewModel and fighting over focus.
 */
@Composable
fun SplitWorkspaceScreen(
    leftViewModel: TextEditorViewModel?,
    rightViewModel: TextEditorViewModel?,
    /** Close the workspace (back to the previous screen). */
    onExit: () -> Unit,
    onSwap: () -> Unit,
    onClosePane: (SplitSide) -> Unit,
    /** Put [String]'s note into one half (wiki tap, or "open beside"). */
    onReplacePane: (SplitSide, String) -> Unit,
    /** Give one pane the whole width by closing the other. */
    onMaximizePane: (SplitSide) -> Unit
) {
    val showLeft = leftViewModel != null
    val showRight = rightViewModel != null

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onExit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave workspace",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = if (showLeft && showRight) "Split view" else "Workspace",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (showLeft && showRight) {
                    IconButton(onClick = onSwap, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap panes",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                TextButton(onClick = onExit) {
                    Text(
                        text = "Done",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        Row(modifier = Modifier.fillMaxSize()) {
            if (showLeft && leftViewModel != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TextEditorScreen(
                        viewModel = leftViewModel,
                        onBackClick = onExit,
                        // A link inside a pane replaces that pane; it does not
                        // stack a third document on top of the workspace.
                        onOpenNote = { id -> onReplacePane(SplitSide.LEFT, id) },
                        onOpenBeside = { id -> onReplacePane(SplitSide.RIGHT, id) },
                        embedded = true,
                        onClosePane = { onClosePane(SplitSide.LEFT) },
                        onMaximizePane = if (showRight) {
                            { onMaximizePane(SplitSide.LEFT) }
                        } else {
                            null
                        }
                    )
                }
            }

            if (showLeft && showRight) {
                // A one-pixel rule is invisible at arm's length on a tablet;
                // give it a few quiet pixels of gutter instead.
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                }
            }

            if (showRight && rightViewModel != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TextEditorScreen(
                        viewModel = rightViewModel,
                        onBackClick = onExit,
                        onOpenNote = { id -> onReplacePane(SplitSide.RIGHT, id) },
                        onOpenBeside = { id -> onReplacePane(SplitSide.LEFT, id) },
                        embedded = true,
                        onClosePane = { onClosePane(SplitSide.RIGHT) },
                        onMaximizePane = if (showLeft) {
                            { onMaximizePane(SplitSide.RIGHT) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}
