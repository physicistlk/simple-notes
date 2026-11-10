package personal.notes.touch.text

/**
 * Block-level markdown export for text notes: turns `notes.content` into a
 * standalone HTML page and into the paragraph list the PDF writer renders.
 *
 * The grammar is the editor's own (see `BlockModel`): a heading, list item,
 * task, quote, table or code fence per line, plus inline formatting handled by
 * [InlineMarkdown]. Fenced code is quoted, so markup inside a sample stays
 * literal (same rule the tag/search index uses).
 *
 * Pure Kotlin — no Android, no Compose — so the HTML output is unit tested.
 */
object MarkdownExport {

    enum class Kind { HEADING, PARAGRAPH, BULLET, NUMBERED, TASK, QUOTE, CODE, DIVIDER, TABLE, IMAGE }

    data class Block(
        val kind: Kind,
        /** Raw block text with its marker removed (paragraph/heading body, code, alt text...). */
        val text: String = "",
        val level: Int = 1,
        val indent: Int = 0,
        val number: Int = 1,
        val checked: Boolean = false,
        val rows: List<List<String>> = emptyList(),
        val url: String = ""
    )

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val TASK = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val QUOTE = Regex("^(\\s*)>\\s?(.*)$")
    private val DIVIDER = Regex("^\\s*([-*_])\\1{2,}\\s*$")
    private val IMAGE = Regex("^!\\[([^\\]]*)]\\(([^)]*)\\)\\s*$")

    fun blocks(content: String): List<Block> {
        val lines = content.lines()
        val out = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val trimmed = raw.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = trimmed.take(3)
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    body.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++ // closing fence
                out.add(Block(Kind.CODE, text = body.toString().trimEnd('\n')))
                continue
            }

            if (trimmed.startsWith("|")) {
                val table = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    table.append(lines[i].trim()).append('\n')
                    i++
                }
                val parsed = TableModel.parse(table.toString())
                if (parsed != null) out.add(Block(Kind.TABLE, rows = parsed.rows))
                continue
            }

            val image = IMAGE.matchEntire(trimmed)
            if (image != null) {
                out.add(Block(Kind.IMAGE, text = image.groupValues[1], url = image.groupValues[2]))
                i++
                continue
            }

            val heading = HEADING.matchEntire(trimmed)
            if (heading != null) {
                out.add(
                    Block(
                        kind = Kind.HEADING,
                        text = heading.groupValues[2],
                        level = heading.groupValues[1].length
                    )
                )
                i++
                continue
            }

            if (DIVIDER.matches(trimmed)) {
                out.add(Block(Kind.DIVIDER))
                i++
                continue
            }

            val task = TASK.matchEntire(raw)
            if (task != null) {
                out.add(
                    Block(
                        kind = Kind.TASK,
                        text = task.groupValues[3],
                        checked = task.groupValues[2].equals("x", ignoreCase = true),
                        indent = indentOf(task.groupValues[1])
                    )
                )
                i++
                continue
            }

            val bullet = BULLET.matchEntire(raw)
            if (bullet != null) {
                out.add(
                    Block(
                        kind = Kind.BULLET,
                        text = bullet.groupValues[2],
                        indent = indentOf(bullet.groupValues[1])
                    )
                )
                i++
                continue
            }

            val numbered = NUMBERED.matchEntire(raw)
            if (numbered != null) {
                out.add(
                    Block(
                        kind = Kind.NUMBERED,
                        text = numbered.groupValues[3],
                        number = numbered.groupValues[2].toIntOrNull() ?: 1,
                        indent = indentOf(numbered.groupValues[1])
                    )
                )
                i++
                continue
            }

            val quote = QUOTE.matchEntire(raw)
            if (quote != null) {
                out.add(Block(Kind.QUOTE, text = quote.groupValues[2]))
                i++
                continue
            }

            out.add(Block(Kind.PARAGRAPH, text = trimmed))
            i++
        }
        return out
    }

    /** Two leading spaces = one nesting level, clamped like the editor. */
    private fun indentOf(spaces: String): Int = (spaces.length / 2).coerceIn(0, 6)

    // ---------------------------------------------------------------- HTML --

    fun htmlBody(content: String): String {
        val blocks = blocks(content)
        val sb = StringBuilder()
        var openList: String? = null

        fun closeList() {
            openList?.let { sb.append("</").append(it).append(">\n") }
            openList = null
        }

        fun ensureList(tag: String) {
            if (openList != tag) {
                closeList()
                sb.append("<").append(tag).append(">\n")
                openList = tag
            }
        }

        for (block in blocks) {
            when (block.kind) {
                Kind.BULLET -> {
                    ensureList("ul")
                    sb.append("<li>").append(inline(block.text)).append("</li>\n")
                }
                Kind.TASK -> {
                    ensureList("ul")
                    val checked = if (block.checked) " checked" else ""
                    sb.append("<li class=\"task\"><input type=\"checkbox\" disabled")
                        .append(checked).append("> ")
                        .append(inline(block.text)).append("</li>\n")
                }
                Kind.NUMBERED -> {
                    ensureList("ol")
                    sb.append("<li value=\"").append(block.number).append("\">")
                        .append(inline(block.text)).append("</li>\n")
                }
                Kind.HEADING -> {
                    closeList()
                    val level = block.level.coerceIn(1, 6)
                    sb.append("<h").append(level).append(">")
                        .append(inline(block.text))
                        .append("</h").append(level).append(">\n")
                }
                Kind.PARAGRAPH -> {
                    closeList()
                    sb.append("<p>").append(inline(block.text)).append("</p>\n")
                }
                Kind.QUOTE -> {
                    closeList()
                    sb.append("<blockquote><p>").append(inline(block.text)).append("</p></blockquote>\n")
                }
                Kind.CODE -> {
                    closeList()
                    sb.append("<pre><code>").append(escape(block.text)).append("</code></pre>\n")
                }
                Kind.DIVIDER -> {
                    closeList()
                    sb.append("<hr>\n")
                }
                Kind.TABLE -> {
                    closeList()
                    sb.append(renderTable(block.rows))
                }
                Kind.IMAGE -> {
                    closeList()
                    sb.append(renderImage(block))
                }
            }
        }
        closeList()
        return sb.toString().trimEnd()
    }

    private fun renderTable(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val header = rows.first()
        val body = rows.drop(1)
        val sb = StringBuilder("<table>\n<thead><tr>")
        header.forEach { sb.append("<th>").append(inline(it)).append("</th>") }
        sb.append("</tr></thead>\n<tbody>\n")
        body.forEach { row ->
            sb.append("<tr>")
            row.forEach { cell -> sb.append("<td>").append(inline(cell)).append("</td>") }
            sb.append("</tr>\n")
        }
        sb.append("</tbody>\n</table>\n")
        return sb.toString()
    }

    private fun renderImage(block: Block): String {
        val alt = escape(InlineMarkdown.plain(block.text).ifBlank { "image" })
        return if (block.url.startsWith("http://") || block.url.startsWith("https://")) {
            "<figure><img src=\"${escapeAttr(block.url)}\" alt=\"$alt\"><figcaption>$alt</figcaption></figure>\n"
        } else {
            // Local attachments live in app-private storage and are not part of
            // the export, so the page names the image instead of a broken <img>.
            "<p class=\"attachment\">&#128247; $alt</p>\n"
        }
    }

    fun inline(text: String): String = InlineMarkdown.parse(text).joinToString("") { run ->
        var html = escape(run.text)
        if (run.code) html = "<code>$html</code>"
        if (run.highlight) html = "<mark>$html</mark>"
        if (run.bold) html = "<strong>$html</strong>"
        if (run.italic) html = "<em>$html</em>"
        if (run.strike) html = "<del>$html</del>"
        when {
            run.wiki -> html =
                "<span class=\"wiki\" title=\"${escapeAttr(run.link.orEmpty())}\">$html</span>"
            !run.link.isNullOrBlank() -> {
                // Only safe schemes become a link: a note that contains
                // [x](javascript:...) must not turn into a script in a file
                // the user shares with other people.
                val link = run.link
                val safe = link.startsWith("http://") || link.startsWith("https://") ||
                    link.startsWith("mailto:") || link.startsWith("#")
                html = if (safe) {
                    "<a href=\"${escapeAttr(link)}\">$html</a>"
                } else {
                    "<span>$html</span>"
                }
            }
        }
        html
    }

    /** A complete, self-contained page: inline CSS, no external assets. */
    fun htmlDocument(title: String, content: String): String {
        val safeTitle = escape(title.ifBlank { "Untitled note" })
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$safeTitle</title>
<style>
:root { color-scheme: light; }
body { margin: 0; background: #ffffff; color: #1f2328; }
article { max-width: 780px; margin: 0 auto; padding: 48px 24px 64px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  font-size: 16px; line-height: 1.65; }
h1, h2, h3, h4, h5, h6 { line-height: 1.25; margin: 1.6em 0 0.6em; font-weight: 650; }
h1 { font-size: 2em; margin-top: 0; border-bottom: 1px solid #e3e5e8; padding-bottom: 0.3em; }
h2 { font-size: 1.55em; }
h3 { font-size: 1.3em; }
h4 { font-size: 1.12em; }
h5, h6 { font-size: 1em; color: #57606a; }
p { margin: 0.7em 0; }
ul, ol { padding-left: 1.6em; margin: 0.7em 0; }
li { margin: 0.28em 0; }
li.task { list-style: none; margin-left: -1.4em; }
li.task input { margin-right: 0.45em; }
blockquote { margin: 1em 0; padding: 0.1em 1em; color: #57606a; border-left: 3px solid #d0d7de; }
code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 0.92em;
  background: #f6f8fa; padding: 0.15em 0.35em; border-radius: 4px; }
pre { background: #f6f8fa; padding: 14px 16px; border-radius: 8px; overflow-x: auto; }
pre code { background: none; padding: 0; font-size: 0.9em; line-height: 1.5; }
mark { background: #fff176; }
table { border-collapse: collapse; margin: 1em 0; width: 100%; font-size: 0.95em; }
th, td { border: 1px solid #d0d7de; padding: 7px 11px; text-align: left; vertical-align: top; }
th { background: #f6f8fa; font-weight: 650; }
hr { border: none; border-top: 1px solid #d0d7de; margin: 1.6em 0; }
a { color: #0969da; text-decoration: underline; }
.wiki { color: #6a1b9a; background: #f3e5f5; border-radius: 4px; padding: 0.05em 0.3em; }
.attachment { color: #57606a; font-style: italic; }
figure { margin: 1em 0; }
figure img { max-width: 100%; border-radius: 6px; }
figcaption { color: #57606a; font-size: 0.9em; margin-top: 0.4em; }
@media print { article { max-width: none; padding: 0; } a { color: inherit; } }
</style>
</head>
<body>
<article>
${htmlBody(content)}
</article>
</body>
</html>
"""
    }

    fun escape(text: String): String = buildString(text.length + 8) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private fun escapeAttr(text: String): String = escape(text)
}
