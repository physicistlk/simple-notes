package personal.notes.touch.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

sealed class RichBlock {
    data class Header(val level: Int, val text: AnnotatedString) : RichBlock()
    data class Paragraph(val text: AnnotatedString) : RichBlock()
    data class Quote(val text: AnnotatedString) : RichBlock()
    data class BulletItem(val text: AnnotatedString) : RichBlock()
    data class NumberedItem(val number: String, val text: AnnotatedString) : RichBlock()
    data class ChecklistItem(val isChecked: Boolean, val text: AnnotatedString, val lineIndex: Int) : RichBlock()
    object Divider : RichBlock()
    data class CodeBlock(val code: String) : RichBlock()
}

object RichTextParser {

    fun parseMarkdown(content: String): List<RichBlock> {
        val lines = content.lines()
        val blocks = mutableListOf<RichBlock>()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        for ((lineIdx, line) in lines.withIndex()) {
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
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                continue
            }

            // Horizontal Divider
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(RichBlock.Divider)
                continue
            }

            // Headers
            if (trimmed.startsWith("# ")) {
                blocks.add(RichBlock.Header(1, parseInline(trimmed.removePrefix("# "))))
                continue
            }
            if (trimmed.startsWith("## ")) {
                blocks.add(RichBlock.Header(2, parseInline(trimmed.removePrefix("## "))))
                continue
            }
            if (trimmed.startsWith("### ")) {
                blocks.add(RichBlock.Header(3, parseInline(trimmed.removePrefix("### "))))
                continue
            }

            // Quotes
            if (trimmed.startsWith("> ")) {
                blocks.add(RichBlock.Quote(parseInline(trimmed.removePrefix("> "))))
                continue
            }

            // Checklist
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ")) {
                blocks.add(RichBlock.ChecklistItem(false, parseInline(trimmed.substring(6)), lineIdx))
                continue
            }
            if (trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
                trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ")
            ) {
                blocks.add(RichBlock.ChecklistItem(true, parseInline(trimmed.substring(6)), lineIdx))
                continue
            }

            // Bullet list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                blocks.add(RichBlock.BulletItem(parseInline(trimmed.substring(2))))
                continue
            }

            // Numbered list: e.g. "1. " or "12. "
            val numberedMatch = Regex("""^(\d+)\.\s+(.*)""").find(trimmed)
            if (numberedMatch != null) {
                val num = numberedMatch.groupValues[1]
                val rest = numberedMatch.groupValues[2]
                blocks.add(RichBlock.NumberedItem(num, parseInline(rest)))
                continue
            }

            // Regular paragraph or empty line
            blocks.add(RichBlock.Paragraph(parseInline(line)))
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(RichBlock.CodeBlock(codeBuffer.toString().trimEnd()))
        }

        return blocks
    }

    /**
     * Parses inline styles:
     * - **bold**
     * - *italic*
     * - <u>underline</u>
     * - ~~strikethrough~~
     * - `code`
     * - <font color="#hex">colored text</font>
     */
    fun parseInline(text: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            var cursor = 0
            val len = text.length

            while (cursor < len) {
                // Check for <font color="#hex">text</font>
                if (text.startsWith("<font color=\"", cursor, ignoreCase = true)) {
                    val colorEnd = text.indexOf("\">", cursor)
                    val fontClose = text.indexOf("</font>", cursor, ignoreCase = true)
                    if (colorEnd != -1 && fontClose != -1 && fontClose > colorEnd) {
                        val hex = text.substring(cursor + 13, colorEnd)
                        val inner = text.substring(colorEnd + 2, fontClose)
                        val parsedColor = parseColor(hex)
                        withStyle(SpanStyle(color = parsedColor)) {
                            append(parseInline(inner))
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
                            append(parseInline(inner))
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
                            append(parseInline(inner))
                        }
                        cursor = closeTag + 4
                        continue
                    }
                }

                // Check for **bold**
                if (text.startsWith("**", cursor)) {
                    val next = text.indexOf("**", cursor + 2)
                    if (next != -1) {
                        val inner = text.substring(cursor + 2, next)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(parseInline(inner))
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
                            append(parseInline(inner))
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

                // Check for *italic*
                if (text[cursor] == '*') {
                    val next = text.indexOf('*', cursor + 1)
                    if (next != -1) {
                        val inner = text.substring(cursor + 1, next)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(parseInline(inner))
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
