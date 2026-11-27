package personal.notes.touch.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import personal.notes.touch.ui.theme.OrangePrimary

data class PdfImportProgressState(
    val active: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val cancelling: Boolean = false
)

@Composable
fun ImportProgressDialog(
    state: PdfImportProgressState,
    onCancel: () -> Unit,
    accentColor: Color = OrangePrimary
) {
    if (!state.active) return
    val statusText = when {
        state.cancelling -> "Cancelling Import..."
        state.total > 0 -> "Importing page ${state.done.coerceIn(0, state.total)} / ${state.total}"
        else -> "Preparing PDF..."
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(
                text = "Importing PDF",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (state.total > 0 && !state.cancelling) {
                    val fraction = (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !state.cancelling
            ) {
                Text(
                    text = if (state.cancelling) "Cancelling..." else "Cancel",
                    color = if (state.cancelling) Color.Gray else accentColor
                )
            }
        }
    )
}
