package personal.notes.touch.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import personal.notes.touch.text.InlineKind
import personal.notes.touch.text.InlineRender
import personal.notes.touch.text.InlineRenderer
import personal.notes.touch.text.InlineSpan

/**
 * Draws inline markdown inside the block editor.
 *
 * Two modes, both driven from the same scan:
 *  - markers visible but dimmed while the block has focus (so the caret and
 *    the IME always edit the real markdown);
 *  - markers hidden when the block does not have focus (or the note is
 *    read-only), which is the WYSIWYG look of the preview.
 *
 * Hidden markers come with a real [OffsetMapping] so a tap in the rendered
 * text still lands on the right source offset when the block gains focus.
 */
object InlineEditorStyle {

    data class Colors(
        val marker: Color,
        val codeBackground: Color,
        val highlightBackground: Color
    )

    @Composable
    fun colors(): Colors = Colors(
        marker = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
        codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        highlightBackground = Color(0xFFFFF176)
    )

    fun styleFor(kind: InlineKind, colors: Colors): SpanStyle = when (kind) {
        InlineKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        InlineKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        InlineKind.BOLD_ITALIC -> SpanStyle(
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
        InlineKind.CODE -> SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = colors.codeBackground
        )
        InlineKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        InlineKind.HIGHLIGHT -> SpanStyle(background = colors.highlightBackground)
        InlineKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    }

    fun transformation(hideMarkers: Boolean, colors: Colors): VisualTransformation =
        VisualTransformation { annotated ->
            val render = InlineRenderer.render(annotated.text, hideMarkers)
            if (render.isEmpty && render.identity) {
                TransformedText(annotated, OffsetMapping.Identity)
            } else {
                TransformedText(
                    buildAnnotated(annotated, render, colors),
                    if (render.identity) OffsetMapping.Identity else Mapping(render)
                )
            }
        }

    private fun buildAnnotated(
        source: AnnotatedString,
        render: InlineRender,
        colors: Colors
    ): AnnotatedString {
        // Keep any annotations the field already carries when the text itself
        // is unchanged; only a hidden-marker render has to rebuild the string.
        val builder = if (render.identity) {
            AnnotatedString.Builder(source)
        } else {
            AnnotatedString.Builder(render.text)
        }
        for (span in render.spans) add(builder, span, colors)
        for (marker in render.markers) {
            builder.addStyle(
                SpanStyle(color = colors.marker),
                marker.first,
                marker.last + 1
            )
        }
        return builder.toAnnotatedString()
    }

    private fun add(
        builder: AnnotatedString.Builder,
        span: InlineSpan,
        colors: Colors
    ) {
        if (span.end <= span.start) return
        builder.addStyle(styleFor(span.kind, colors), span.start, span.end)
    }

    private class Mapping(private val render: InlineRender) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            InlineRenderer.originalToTransformed(render, offset)

        override fun transformedToOriginal(offset: Int): Int =
            InlineRenderer.transformedToOriginal(render, offset)
    }
}
