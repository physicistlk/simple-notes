package personal.notes.touch.ui.editor

import personal.notes.touch.text.BlockRefs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

sealed class RichBlock {
    data class Header(val level: Int, val text: AnnotatedString) : RichBlock()
    data class Paragraph(val text: AnnotatedString) : RichBlock()
    data class Quote(val text: AnnotatedString) : RichBlock()
    /** [indent] = nesting level, parsed from leading spaces (parser v2). */
    data class BulletItem(val text: AnnotatedString, val indent: Int = 0) : RichBlock()
    data class NumberedItem(val number: String, val text: AnnotatedString, val indent: Int = 0) : RichBlock()
    data class ChecklistItem(val isChecked: Boolean, val text: AnnotatedString, val lineIndex: Int) : RichBlock()
    /** GFM-style table, kept verbatim per row (parser v2, P1.4). */
    data class Table(val rows: List<String>) : RichBlock()
    /** Image block (v2, P1.5): ![alt](url). */
    data class Image(val alt: String, val url: String) : RichBlock()
    object Divider : RichBlock()
    data class CodeBlock(val code: String) : RichBlock()
}

/**
 * Parser v2 (Phase-1 P1.2): headings 1-6, nested lists by indent, GFM tables,
 * ==highlight==, <sup>/<sub>; tolerant to legacy content — old notes keep
 * rendering through the same constructs the original parser accepted.
 */
object RichTextParser {

    /** Highlight (==mark==) background — SiYuan default yellow-ish. */
    private val MARK_BG = Color(0xFFFFF176)

    /** String annotation tag carrying a link target inside an AnnotatedString. */
    const val LINK_TAG = "touchnotes.link"

    /** Annotation tag carrying a wiki-link target (note title) in preview text. */
    const val WIKI_TAG = "touchnotes.wiki"

    /** Annotation tag carrying a block target (short block key) in preview text. */
    const val BLOCK_REF_TAG = "touchnotes.blockref"

    /** Wiki-link tint: violet, to read differently from an external link. */
    private val WIKI_COLOR = Color(0xFF6A1B9A)
    private val WIKI_BG = Color(0xFFF3E5F5)

    /** Block-reference tint: teal, distinct from a note link. */
    private val BLOCK_REF_COLOR = Color(0xFF00695C)
    private val BLOCK_REF_BG = Color(0xFFE0F2F1)

    /** Link colour (Material blue 700), underlined like a browser link. */
    private val LINK_COLOR = Color(0xFF1565C0)

    fun parseMarkdown(
        content: String,
        blockRefLabel: (String) -> String? = { null }
    ): List<RichBlock> {
        val lines = content.lines()
        val blocks = mutableListOf<RichBlock>()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Code block toggle
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    blocks.add(RichBlock.CodeBlock(codeBuffer.toString().trimEnd()))
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                i++
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                i++
                continue
            }

            // Horizontal Divider
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(RichBlock.Divider)
                i++
                continue
            }

            // Image (v2): ![alt](url)
            val imageMatch = Regex("""^!\[([^\]]*)\]\(([^)]*)\)$""").find(trimmed)
            if (imageMatch != null) {
                blocks.add(RichBlock.Image(imageMatch.groupValues[1], imageMatch.groupValues[2]))
                i++
                continue
            }

            // Table (v2): group consecutive "|" rows into one bordered block
            if (trimmed.startsWith("|")) {
                val rows = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith("|")) {
                    rows.add(lines[j].trim())
                    j++
                }
                blocks.add(RichBlock.Table(rows))
                i = j
                continue
            }

            // Headers (v2: levels 1-6)
            val headingMatch = Regex("""^(#{1,6}) (.*)$""").find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                blocks.add(RichBlock.Header(level, parseInline(headingMatch.groupValues[2], blockRefLabel)))
                i++
                continue
            }

            // Quotes
            if (trimmed.startsWith("> ")) {
                blocks.add(RichBlock.Quote(parseInline(trimmed.removePrefix("> "), blockRefLabel)))
                i++
                continue
            }

            // Nested list indent (v2): two spaces or one tab per level
            val indent = indentOf(line)
            val body = line.trimStart()

            // Checklist
            if (body.startsWith("- [ ] ") || body.startsWith("* [ ] ")) {
                blocks.add(RichBlock.ChecklistItem(false, parseInline(body.substring(6), blockRefLabel), i))
                i++
                continue
            }
            if (body.startsWith("- [x] ") || body.startsWith("- [X] ") ||
                body.startsWith("* [x] ") || body.startsWith("* [X] ")
            ) {
                blocks.add(RichBlock.ChecklistItem(true, parseInline(body.substring(6), blockRefLabel), i))
                i++
                continue
            }

            // Bullet list
            if (body.startsWith("- ") || body.startsWith("* ") || body.startsWith("• ")) {
                blocks.add(RichBlock.BulletItem(parseInline(body.substring(2), blockRefLabel), indent))
                i++
                continue
            }

            // Numbered list: e.g. "1. " or "12. "
            val numberedMatch = Regex("""^(\d+)\.\s+(.*)""").find(body)
            if (numberedMatch != null) {
                val num = numberedMatch.groupValues[1]
                val rest = numberedMatch.groupValues[2]
                blocks.add(RichBlock.NumberedItem(num, parseInline(rest, blockRefLabel), indent))
                i++
                continue
            }

            // Regular paragraph or empty line
            blocks.add(RichBlock.Paragraph(parseInline(line, blockRefLabel)))
            i++
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(RichBlock.CodeBlock(codeBuffer.toString().trimEnd()))
        }

        return blocks
    }

    private fun indentOf(raw: String): Int {
        var spaces = 0
        for (ch in raw) {
            when (ch) {
                ' ' -> spaces++
                '\t' -> spaces += 2
                else -> break
            }
        }
        return spaces / 2
    }

    /**
     * Parses inline styles:
     * - **bold**
     * - *italic*
     * - <u>underline</u>
     * - ~~strikethrough~~
     * - ==highlight== (v2, ==mark==)
     * - <sup>/<sub> (v2)
     * - `code`
     * - <font color="#hex">colored text</font>
     */
    fun parseInline(
        text: String,
        blockRefLabel: (String) -> String? = { null }
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            var cursor = 0
            val len = text.length

            while (cursor < len) {
                // ((blockId)) / ((blockId|alias)) — a block reference. The
                // grammar lives in BlockRefs so the editor, the index and the
                // tests cannot drift apart.
                if (text[cursor] == '(') {
                    val token = BlockRefs.matchAt(text, cursor)
                    if (token != null) {
                        val label = blockRefLabel(token.ref.target)
                            ?: token.ref.alias
                            ?: "Block ${token.ref.target}"
                        val start = length
                        withStyle(
                            SpanStyle(
                                color = BLOCK_REF_COLOR,
                                background = BLOCK_REF_BG,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(label)
                        }
                        addStringAnnotation(BLOCK_REF_TAG, token.ref.target, start, length)
                        cursor = token.end
                        continue
                    }
                }

                // Check for <font color="#hex">text</font>
                if (text.startsWith("<font color=\"", cursor, ignoreCase = true)) {
                    val colorEnd = text.indexOf("\">", cursor)
                    val fontClose = text.indexOf("</font>", cursor, ignoreCase = true)
                    if (colorEnd != -1 && fontClose != -1 && fontClose > colorEnd) {
                        val hex = text.substring(cursor + 13, colorEnd)
                        val inner = text.substring(colorEnd + 2, fontClose)
                        val parsedColor = parseColor(hex)
                        withStyle(SpanStyle(color = parsedColor)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = fontClose + 7
                        continue
                    }
                }

                // Check for <u>text</u>
                if (text.startsWith("<u>", cursor, ignoreCase = true)) {
                    val closeTag = text.indexOf("</u>", cursor, ignoreCase = true)
                    if (closeTag != -1) {
                        val inner = text.substring(cursor + 3, closeTag)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = closeTag + 4
                        continue
                    }
                }

                // Check for <s> or <strike>
                if (text.startsWith("<s>", cursor, ignoreCase = true)) {
                    val closeTag = text.indexOf("</s>", cursor, ignoreCase = true)
                    if (closeTag != -1) {
                        val inner = text.substring(cursor + 3, closeTag)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = closeTag + 4
                        continue
                    }
                }

                // v2: <sup> / <sub>
                if (text.startsWith("<sup>", cursor, ignoreCase = true)) {
                    val closeTag = text.indexOf("</sup>", cursor, ignoreCase = true)
                    if (closeTag != -1) {
                        val inner = text.substring(cursor + 5, closeTag)
                        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = closeTag + 6
                        continue
                    }
                }
                if (text.startsWith("<sub>", cursor, ignoreCase = true)) {
                    val closeTag = text.indexOf("</sub>", cursor, ignoreCase = true)
                    if (closeTag != -1) {
                        val inner = text.substring(cursor + 5, closeTag)
                        withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = closeTag + 6
                        continue
                    }
                }

                // v2: ==highlight==
                if (text.startsWith("==", cursor)) {
                    val next = text.indexOf("==", cursor + 2)
                    if (next != -1) {
                        val inner = text.substring(cursor + 2, next)
                        withStyle(SpanStyle(background = MARK_BG)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = next + 2
                        continue
                    }
                }

                // v2: #tag# or #category/tag#
                if (text[cursor] == '#' && cursor + 1 < len) {
                    val nextHash = text.indexOf('#', cursor + 1)
                    if (nextHash != -1) {
                        val candidate = text.substring(cursor + 1, nextHash)
                        if (candidate.isNotEmpty() && !candidate.contains(' ') && !candidate.contains('\n')) {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF1565C0),
                                    background = Color(0xFFE3F2FD),
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append("#$candidate#")
                            }
                            cursor = nextHash + 1
                            continue
                        }
                    }
                }

                // Check for **bold**
                if (text.startsWith("**", cursor)) {
                    val next = text.indexOf("**", cursor + 2)
                    if (next != -1) {
                        val inner = text.substring(cursor + 2, next)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = next + 2
                        continue
                    }
                }

                // Check for ~~strikethrough~~
                if (text.startsWith("~~", cursor)) {
                    val next = text.indexOf("~~", cursor + 2)
                    if (next != -1) {
                        val inner = text.substring(cursor + 2, next)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = next + 2
                        continue
                    }
                }

                // Check for `inline code`
                if (text[cursor] == '`') {
                    val next = text.indexOf('`', cursor + 1)
                    if (next != -1) {
                        val inner = text.substring(cursor + 1, next)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x22808080)
                            )
                        ) {
                            append(inner)
                        }
                        cursor = next + 1
                        continue
                    }
                }

                // [[Note title]] / [[Note title|alias]] — wiki link (v2).
                // Styled as a chip and tagged with the TARGET title so the
                // preview can open the note; the alias is what gets drawn.
                if (text.startsWith("[[", cursor)) {
                    val close = text.indexOf("]]", cursor + 2)
                    if (close != -1) {
                        val inner = text.substring(cursor + 2, close)
                        if (inner.isNotBlank() &&
                            inner.length <= 160 &&
                            !inner.contains('\n') &&
                            !inner.contains("[[")
                        ) {
                            val target = inner.substringBefore('|').trim()
                            val alias = inner.substringAfter('|', "").trim()
                            if (target.isNotEmpty()) {
                                val start = length
                                withStyle(
                                    SpanStyle(
                                        color = WIKI_COLOR,
                                        background = WIKI_BG,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append(alias.ifEmpty { target })
                                }
                                addStringAnnotation(WIKI_TAG, target, start, length)
                                cursor = close + 2
                                continue
                            }
                        }
                    }
                }

                // [label](url) — inline link (v2): styled + tagged so the
                // preview can hand the target to ACTION_VIEW on tap.
                if (text[cursor] == '[' && (cursor == 0 || text[cursor - 1] != '!')) {
                    val labelEnd = text.indexOf("](", cursor + 1)
                    if (labelEnd != -1) {
                        val urlEnd = text.indexOf(')', labelEnd + 2)
                        if (urlEnd != -1) {
                            val label = text.substring(cursor + 1, labelEnd)
                            val url = text.substring(labelEnd + 2, urlEnd).trim()
                            if (label.isNotEmpty() && url.isNotEmpty()) {
                                val start = length
                                withStyle(
                                    SpanStyle(
                                        color = LINK_COLOR,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append(label)
                                }
                                addStringAnnotation(LINK_TAG, url, start, length)
                                cursor = urlEnd + 1
                                continue
                            }
                        }
                    }
                }

                // Check for *italic*
                if (text[cursor] == '*') {
                    val next = text.indexOf('*', cursor + 1)
                    if (next != -1) {
                        val inner = text.substring(cursor + 1, next)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(parseInline(inner, blockRefLabel))
                        }
                        cursor = next + 1
                        continue
                    }
                }

                // Plain character
                append(text[cursor])
                cursor++
            }
        }
    }

    private fun parseColor(hex: String): Color {
        return try {
            val cleanHex = hex.trim().removePrefix("#")
            val colorInt = when (cleanHex.length) {
                6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
                8 -> cleanHex.toLong(16).toInt()
                else -> 0xFF212529.toInt()
            }
            Color(colorInt)
        } catch (_: Exception) {
            Color.Black
        }
    }
}
