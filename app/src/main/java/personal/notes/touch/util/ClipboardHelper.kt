package personal.notes.touch.util

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ClipboardHelper {

    /** Puts plain [text] on the clipboard (F-10 copy of selected PDF text). */
    fun copyText(context: Context, text: String, label: String = "Simple Notes"): Boolean {
        if (text.isEmpty()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return false
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Primary-clip item, used by paste-from-clipboard. */
    fun pasteFromClipboard(context: Context): ClipData.Item? {
        // ClipboardManager.getPrimaryClip().getItemAt(0)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return null
        return try {
            clipboard.primaryClip?.getItemAt(0)
        } catch (_: Exception) {
            null
        }
    }

    /** Full primary clip, used by drag event handlers. */
    fun primaryClip(context: Context): ClipData? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager ?: return null
        return try {
            clipboard.primaryClip
        } catch (_: Exception) {
            null
        }
    }

    /** True when a ClipData (drag or clipboard) contains anything reusable. */
    fun clipHasContent(clip: ClipData?): Boolean {
        if (clip == null || clip.itemCount <= 0) return false
        val item = clip.getItemAt(0) ?: return false
        return item.uri != null || !item.text.isNullOrBlank() || item.htmlText != null
    }

    /** Extract text fallback for an item: plain text → html → uri string. */
    fun itemText(item: ClipData.Item?): String? {
        if (item == null) return null
        item.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        item.htmlText?.takeIf { it.isNotBlank() }?.let { return it }
        return item.uri?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Decode an image URI (from drag ClipData.Item.getUri() or clipboard)
     * into a Bitmap. Handles content:// (browser/Files/Photos) and file://.
     */
    suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, false)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: raw stream decode (Photos bitmap stream / browser blobs).
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.copy(Bitmap.Config.ARGB_8888, false)
                }
            } catch (_: Exception) { null }
        }
    }

    suspend fun extractContent(
        context: Context,
        item: ClipData.Item?,
        clip: ClipData? = null
    ): ExtractedContent {
        val first = item ?: clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        if (first == null) return ExtractedContent()
        val uri = first.uri
        val bitmap = uri?.let { runCatching { decodeBitmapFromUri(context, it) }.getOrNull() }
        val text = itemText(first)
        // Browser drags sometimes carry the image on item 0 and text on item 1;
        // merge: prefer item 0 text, fall back to any later item's text.
        val extraText = if (text.isNullOrBlank() && clip != null && clip.itemCount > 1) {
            (1 until clip.itemCount).firstNotNullOfOrNull { itemText(clip.getItemAt(it)) }
        } else null
        return ExtractedContent(bitmap = bitmap, text = text ?: extraText, uri = uri)
    }

    data class ExtractedContent(
        val bitmap: Bitmap? = null,
        val text: String? = null,
        val uri: Uri? = null
    )
}
