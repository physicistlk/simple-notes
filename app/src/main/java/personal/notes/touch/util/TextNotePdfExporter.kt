package personal.notes.touch.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import personal.notes.touch.text.InlineMarkdown
import personal.notes.touch.text.MarkdownExport
import java.io.OutputStream

/**
 * Text-note PDF export: renders the markdown document itself (headings, lists,
 * tasks, quotes, code, tables, inline styles) onto A4 pages, instead of
 * rasterising drawing canvases.
 *
 * Layout is a single [StaticLayout] per block, drawn in page-sized vertical
 * slices, so a paragraph longer than a page breaks cleanly between its lines.
 * Uses the API 1 `StaticLayout` constructor (not `Builder`, which needs API 23)
 * so minSdk 21 keeps working. Attachment images are local files outside the
 * export, so they are named rather than embedded.
 */
object TextNotePdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val FOOTER_H = 34f

    private const val INK = 0xFF1F2328.toInt()
    private const val MUTED = 0xFF57606A.toInt()
    private const val RULE = 0xFFD0D7DE.toInt()
    private const val CODE_BG = 0xFFF6F8FA.toInt()

    fun write(title: String, content: String, out: OutputStream) {
        val document = PdfDocument()
        val contentWidth = PAGE_W - 2 * MARGIN
        val bodyBottom = PAGE_H - MARGIN - FOOTER_H

        var page: PdfDocument.Page? = null
        var canvas: Canvas = Canvas()
        var y = 0f
        var pageIndex = 0

        val footerPaint = paint(9f, MUTED)

        fun startPage() {
            pageIndex++
            val created = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex).create()
            )
            page = created
            canvas = created.canvas
            y = MARGIN
        }

        fun endPage() {
            val current = page ?: return
            current.canvas.drawText(
                pageIndex.toString(),
                PAGE_W / 2f,
                PAGE_H - 20f,
                footerPaint
            )
            document.finishPage(current)
            page = null
        }

        fun newPage() {
            endPage()
            startPage()
        }

        fun ensureSpace(height: Float) {
            if (y + height > bodyBottom) newPage()
        }

        /** Draws [layout] at [indent], breaking between lines across pages. */
        fun drawLayout(layout: StaticLayout, indent: Float, background: Int? = null) {
            val x = MARGIN + indent
            var line = 0
            while (line < layout.lineCount) {
                if (y >= bodyBottom) newPage()
                var last = line
                var slice = 0f
                while (last < layout.lineCount) {
                    val lineHeight =
                        (layout.getLineBottom(last) - layout.getLineTop(last)).toFloat()
                    if (slice + lineHeight > bodyBottom - y && last > line) break
                    slice += lineHeight
                    last++
                }
                if (background != null) {
                    val fill = Paint().apply {
                        color = background
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawRect(x - 6f, y, x + layout.width + 6f, y + slice, fill)
                }
                canvas.save()
                canvas.clipRect(x, y, x + layout.width, y + slice)
                canvas.translate(x, y - layout.getLineTop(line))
                layout.draw(canvas)
                canvas.restore()
                y += slice
                line = last
                if (line < layout.lineCount) newPage()
            }
        }

        fun layoutOf(
            text: CharSequence,
            paint: TextPaint,
            width: Int,
            spacingAdd: Float = 2f
        ): StaticLayout {
            @Suppress("DEPRECATION")
            return StaticLayout(
                text,
                paint,
                width.coerceAtLeast(32),
                Layout.Alignment.ALIGN_NORMAL,
                1f,
                spacingAdd,
                false
            )
        }

        startPage()

        // Title: the note name, then a hairline rule.
        if (title.isNotBlank()) {
            val titlePaint = paint(21f, INK, bold = true)
            val layout = layoutOf(spannable(title), titlePaint, contentWidth.toInt(), 4f)
            ensureSpace(layout.height.toFloat())
            drawLayout(layout, 0f)
            y += 8f
            val rule = Paint().apply {
                color = RULE
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            ensureSpace(12f)
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 14f
        }

        // A leading H1 that merely repeats the title would print it twice.
        val blocks = MarkdownExport.blocks(content)
        val first = blocks.firstOrNull()
        val skipFirst = title.isNotBlank() &&
            first != null &&
            first.kind == MarkdownExport.Kind.HEADING &&
            first.level == 1 &&
            InlineMarkdown.plain(first.text).trim().equals(title.trim(), ignoreCase = true)

        for ((index, block) in blocks.withIndex()) {
            if (index == 0 && skipFirst) continue
            when (block.kind) {
                MarkdownExport.Kind.HEADING -> {
                    val size = when (block.level) {
                        1 -> 19f
                        2 -> 16.5f
                        3 -> 14.5f
                        4 -> 13f
                        else -> 12f
                    }
                    val paint = paint(size, INK, bold = true)
                    val layout = layoutOf(spannable(block.text), paint, contentWidth.toInt(), 3f)
                    ensureSpace(10f)
                    y += if (block.level <= 2) 10f else 6f
                    ensureSpace(layout.height.toFloat())
                    drawLayout(layout, 0f)
                    y += 4f
                }
                MarkdownExport.Kind.PARAGRAPH -> {
                    val layout = layoutOf(
                        spannable(block.text),
                        paint(11.5f, INK),
                        contentWidth.toInt()
                    )
                    ensureSpace(layout.height.toFloat())
                    drawLayout(layout, 0f)
                    y += 4f
                }
                MarkdownExport.Kind.BULLET,
                MarkdownExport.Kind.NUMBERED,
                MarkdownExport.Kind.TASK -> {
                    val marker = when (block.kind) {
                        MarkdownExport.Kind.BULLET -> "\u2022"
                        MarkdownExport.Kind.NUMBERED -> "${block.number}."
                        else -> if (block.checked) "\u2611" else "\u2610"
                    }
                    // Hanging indent: the marker sits in the margin, wrapped
                    // lines line up under the text rather than under the bullet.
                    val hanging = (16f + block.indent * 16f).toInt()
                    val line = SpannableStringBuilder()
                    line.append(marker).append("  ").append(spannable(block.text))
                    line.setSpan(
                        ForegroundColorSpan(MUTED), 0, marker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    line.setSpan(
                        LeadingMarginSpan.Standard(hanging, hanging),
                        0,
                        line.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    val layout = layoutOf(line, paint(11.5f, INK), contentWidth.toInt())
                    ensureSpace(layout.height.toFloat())
                    drawLayout(layout, 0f)
                    y += 2f
                }
                MarkdownExport.Kind.QUOTE -> {
                    val indent = 14f
                    val layout = layoutOf(
                        spannable(block.text),
                        paint(11.5f, MUTED, italic = true),
                        (contentWidth - indent).toInt()
                    )
                    ensureSpace(layout.height.toFloat() + 6f)
                    val bar = Paint().apply {
                        color = RULE
                        strokeWidth = 3f
                        style = Paint.Style.STROKE
                    }
                    canvas.drawLine(
                        MARGIN + 3f,
                        y,
                        MARGIN + 3f,
                        y + layout.height.toFloat(),
                        bar
                    )
                    drawLayout(layout, indent)
                    y += 6f
                }
                MarkdownExport.Kind.CODE -> {
                    val layout = layoutOf(
                        spannable(block.text.ifBlank { " " }),
                        paint(10f, INK, mono = true),
                        (contentWidth - 16f).toInt(),
                        1f
                    )
                    ensureSpace(layout.height.toFloat() + 16f)
                    y += 6f
                    drawLayout(layout, 8f, background = CODE_BG)
                    y += 10f
                }
                MarkdownExport.Kind.DIVIDER -> {
                    ensureSpace(18f)
                    val rule = Paint().apply {
                        color = RULE
                        strokeWidth = 1f
                        style = Paint.Style.STROKE
                    }
                    canvas.drawLine(MARGIN, y + 8f, PAGE_W - MARGIN, y + 8f, rule)
                    y += 18f
                }
                MarkdownExport.Kind.TABLE -> {
                    val rows = block.rows
                    if (rows.isEmpty()) continue
                    rows.forEachIndexed { rowIndex, row ->
                        val lineText = row.joinToString("  \u2502  ") { InlineMarkdown.plain(it) }
                        val rowPaint = paint(
                            9.5f,
                            INK,
                            bold = rowIndex == 0,
                            mono = true
                        )
                        val layout = layoutOf(lineText.ifBlank { " " }, rowPaint, contentWidth.toInt(), 1f)
                        ensureSpace(layout.height.toFloat() + 4f)
                        drawLayout(layout, 0f)
                        y += 2f
                        if (rowIndex == 0) {
                            val rule = Paint().apply {
                                color = RULE
                                strokeWidth = 1f
                                style = Paint.Style.STROKE
                            }
                            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
                            y += 4f
                        }
                    }
                    y += 6f
                }
                MarkdownExport.Kind.IMAGE -> {
                    val alt = InlineMarkdown.plain(block.text).ifBlank { "image" }
                    val layout = layoutOf(
                        spannable("\uD83D\uDCF7 $alt"),
                        paint(10.5f, MUTED, italic = true),
                        contentWidth.toInt()
                    )
                    ensureSpace(layout.height.toFloat())
                    drawLayout(layout, 0f)
                    y += 4f
                }
            }
        }

        endPage()
        document.writeTo(out)
        document.close()
        out.flush()
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false, italic: Boolean = false, mono: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when {
                mono -> Typeface.MONOSPACE
                bold && italic -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                italic -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                else -> Typeface.SANS_SERIF
            }
        }

    /** Inline markdown → Android spans, so bold/code/links survive in the PDF. */
    private fun spannable(text: String): CharSequence {
        val runs = InlineMarkdown.parse(text)
        if (runs.isEmpty()) return ""
        val builder = SpannableStringBuilder()
        for (run in runs) {
            val start = builder.length
            builder.append(run.text)
            val end = builder.length
            if (start == end) continue
            if (run.code) {
                builder.setSpan(
                    TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    BackgroundColorSpan(CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (run.bold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.italic) {
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.strike) {
                builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.highlight) {
                builder.setSpan(
                    BackgroundColorSpan(0xFFFFF176.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (run.wiki) {
                builder.setSpan(
                    ForegroundColorSpan(0xFF6A1B9A.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    BackgroundColorSpan(0xFFF3E5F5.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (!run.link.isNullOrBlank()) {
                builder.setSpan(
                    ForegroundColorSpan(0xFF0969DA.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }
}
