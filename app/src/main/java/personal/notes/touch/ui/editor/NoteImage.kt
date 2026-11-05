package personal.notes.touch.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import personal.notes.touch.util.BitmapUtils
import java.io.File

/** Result of parsing an IMAGE block's `![alt](target)` text. */
data class ImageBlockRef(val alt: String, val target: String)

/**
 * Tolerant parser for the markdown stored in an IMAGE block. The block field is
 * user-editable text, so a half-typed `![alt](` must not crash: it simply
 * yields null and the row stays a plain text field.
 */
object ImageBlockRefParser {

    private val PATTERN = Regex("""^\s*!\[([^\]]*)]\(([^)]*)\)\s*$""")

    fun parse(text: String): ImageBlockRef? {
        val match = PATTERN.find(text) ?: return null
        return ImageBlockRef(
            alt = match.groupValues[1],
            target = match.groupValues[2].trim()
        )
    }
}

/**
 * Renders a local note image (a file inside the note's own directory) with a
 * downsampled decode. Remote URLs are never fetched — offline-only app — the
 * placeholder keeps the alt text visible instead.
 */
@Composable
fun NoteImage(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 260.dp
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = BitmapUtils.getCachedImage(file.absolutePath, file.lastModified()),
        key1 = file.absolutePath,
        key2 = file.lastModified()
    ) {
        value = BitmapUtils.loadImage(file, maxDimension = 1024)
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
