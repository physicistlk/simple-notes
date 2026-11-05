package personal.notes.touch.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Link target rules for inline `[label](url)` markdown.
 *
 * Notes are offline and the manifest declares no permissions; opening a link
 * only hands an `ACTION_VIEW` intent to another app. Anything that is not an
 * `http(s)`/`mailto` link (or a bare host) is rejected rather than opened, so a
 * pasted `javascript:`/`file:` URL can never be launched from a note.
 */
object LinkTarget {

    private val ALLOWED_SCHEME = Regex("""^(https?|mailto):""", RegexOption.IGNORE_CASE)
    private val BLOCKED_SCHEMES = listOf(
        "javascript:", "data:", "file:", "content:", "intent:", "android-app:"
    )

    /**
     * Returns the URL to store/open, or null when [raw] is empty/unsafe.
     * Bare hosts get `https://`, a bare e-mail address becomes `mailto:`.
     */
    fun normalize(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (BLOCKED_SCHEMES.any { text.startsWith(it, ignoreCase = true) }) return null

        val url = when {
            ALLOWED_SCHEME.containsMatchIn(text) -> text
            text.contains('@') && !text.contains('/') && !text.contains(' ') -> "mailto:$text"
            else -> "https://$text"
        }
        val remainder = if (url.startsWith("mailto:", ignoreCase = true)) {
            url.substring(7)
        } else {
            url.substringAfter("://", "")
        }
        return if (remainder.isBlank()) null else url
    }

    /** Opens [rawUrl] in another app; never throws, never starts non-http(s)/mailto. */
    fun open(context: Context, rawUrl: String): Boolean {
        val url = normalize(rawUrl) ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Builds the inline markdown for a link; null when the URL is not allowed. */
object LinkMarkdown {
    fun build(label: String, rawUrl: String): String? {
        val url = LinkTarget.normalize(rawUrl) ?: return null
        val text = label.trim().ifEmpty { url }
        return "[$text]($url)"
    }
}
