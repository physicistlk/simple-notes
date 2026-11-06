package personal.notes.touch.ui.editor

import androidx.compose.ui.text.input.TextFieldValue

/**
 * Phase-1 block types for the SiYuan-inspired block editor.
 *
 * The single source of truth is still `notes.content` (markdown text); blocks
 * are DERIVED from it and re-serialized back into it on every edit (see
 * [BlockModel.serializeBlocks]). Block ids are deterministic per
 * content+position ("blk<lineIndex>") — the real per-block Room table and
 * stable ids come in Phase 2 (note_blocks), nothing is stored here.
 *
 * SEAM for the follow-up worker (tags/emoji/images/read-only): add new types
 * here + one branch in [parseBlocks]/[serializeBlockLines] and a row in
 * BlockEditPane — the enum and the slash groups are the stable registries.
 */
enum class BlockType {
    PARAGRAPH,
    HEADING,
    BULLET,
    NUMBERED,
    TASK,
    QUOTE,
    CODE,
    DIVIDER,
    TABLE,
    IMAGE
}

/**
 * One editable block. [field] holds the block's text WITHOUT the block-level
 * marker (no "# ", "- ", "> "); inline markdown (**bold** etc.) stays as
 * literal text so editing round-trips are lossless.
 */
data class EditableBlock(
    val id: String,
    val type: BlockType,
    val field: TextFieldValue = TextFieldValue(""),
    val headingLevel: Int = 1,
    val indent: Int = 0,
    val order: Int = 1,
    val checked: Boolean = false,
    /** CODE only: is the closing fence present? */
    val closed: Boolean = true,
    /** HEADING only: whether the section below is folded away. */
    val collapsed: Boolean = false,
) {
    val isEmptyText: Boolean get() = this.field.text.isEmpty()
}

/**
 * Parses the note's markdown-ish content into typed blocks and serializes
 * blocks back into it. Round trip is (near) identity; legacy content written
 * by the old parser (RichTextParser grammars) parses in one pass and becomes
 * idempotent afterwards (e.g. "* " bullets normalize to "- ").
 */
object BlockModel {

    const val INDENT_UNIT = "  "
    const val CODE_FENCE = "```"
    /** Default 3x3 markdown table inserted from the slash menu. */
    val DEFAULT_TABLE_ROWS = listOf(
        "| Header 1 | Header 2 | Header 3 |",
        "| --- | --- | --- |",
        "|  |  |  |",
        "|  |  |  |",
        "|  |  |  |"
    )

    private val NUMBERED_LINE = Regex("""^(\d{1,9})\. (.*)""")
    private val HEADING_LINE = Regex("""^#{1,6} .*$""")
    private val IMAGE_LINE = Regex("""^!\[[^\]]*\]\([^)]*\)$""")

    fun blockId(startLine: Int): String = "blk$startLine"

    fun paragraph(id: String): EditableBlock = EditableBlock(id = id, type = BlockType.PARAGRAPH)

    /** The block-level markdown prefix that renders in front of [field]. */
    fun markerPrefix(b: EditableBlock): String = when (b.type) {
        BlockType.HEADING -> "#".repeat(b.headingLevel.coerceIn(1, 6)) + " "
        BlockType.BULLET -> INDENT_UNIT.repeat(b.indent) + "- "
        BlockType.NUMBERED -> INDENT_UNIT.repeat(b.indent) + "${b.order}. "
        BlockType.TASK -> INDENT_UNIT.repeat(b.indent) + if (b.checked) "- [x] " else "- [ ] "
        BlockType.QUOTE -> "> "
        else -> ""
    }

    fun serializeBlockLines(b: EditableBlock): List<String> = when (b.type) {
        BlockType.CODE -> buildList {
            add(CODE_FENCE)
            b.field.text.split('\n').forEach { add(it) }
            if (b.closed) add(CODE_FENCE)
        }
        BlockType.TABLE -> b.field.text.split('\n').filter { it.isNotBlank() }
        BlockType.IMAGE -> listOf(b.field.text)
        BlockType.DIVIDER -> listOf("---")
        else -> listOf(markerPrefix(b) + b.field.text)
    }

    fun serializeBlocks(blocks: List<EditableBlock>): String =
        blocks.joinToString("\n") { b -> serializeBlockLines(b).joinToString("\n") }

    fun parseBlocks(content: String): List<EditableBlock> {
        val lines = content.lines()
        val out = mutableListOf<EditableBlock>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val trimmed = raw.trim()
            when {
                trimmed.startsWith(CODE_FENCE) -> {
                    val buf = StringBuilder()
                    var j = i + 1
                    var closed = false
                    while (j < lines.size) {
                        if (lines[j].trim().startsWith(CODE_FENCE)) {
                            closed = true
                            break
                        }
                        buf.append(lines[j]).append('\n')
                        j++
                    }
                    out += EditableBlock(
                        id = blockId(i),
                        type = BlockType.CODE,
                        field = TextFieldValue(buf.toString().removeSuffix("\n")),
                        closed = closed
                    )
                    i = if (closed) j + 1 else lines.size
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    out += EditableBlock(id = blockId(i), type = BlockType.DIVIDER)
                    i++
                }
                trimmed.startsWith("|") -> {
                    // Group consecutive table rows into one TABLE block.
                    var j = i
                    val rows = mutableListOf<String>()
                    while (j < lines.size && lines[j].trim().startsWith("|")) {
                        rows += lines[j].trim()
                        j++
                    }
                    out += EditableBlock(
                        id = blockId(i),
                        type = BlockType.TABLE,
                        field = TextFieldValue(rows.joinToString("\n"))
                    )
                    i = j
                }
                IMAGE_LINE.matches(trimmed) -> {
                    out += EditableBlock(
                        id = blockId(i),
                        type = BlockType.IMAGE,
                        field = TextFieldValue(trimmed)
                    )
                    i++
                }
                HEADING_LINE.matches(trimmed) -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    out += EditableBlock(
                        id = blockId(i),
                        type = BlockType.HEADING,
                        field = TextFieldValue(trimmed.drop(level + 1)),
                        headingLevel = level
                    )
                    i++
                }
                trimmed.isEmpty() -> {
                    // A blank line is an explicit empty paragraph block so that
                    // legacy blank-line rhythm round-trips 1:1.
                    out += EditableBlock(id = blockId(i), type = BlockType.PARAGRAPH)
                    i++
                }
                else -> {
                    val indent = indentOf(raw)
                    val body = raw.trimStart()
                    when {
                        body.startsWith("- [ ] ") || body.startsWith("* [ ] ") -> {
                            out += EditableBlock(
                                id = blockId(i),
                                type = BlockType.TASK,
                                field = TextFieldValue(body.substring(6)),
                                indent = indent,
                                checked = false
                            )
                            i++
                        }
                        body.startsWith("- [x] ") || body.startsWith("- [X] ") ||
                            body.startsWith("* [x] ") || body.startsWith("* [X] ") -> {
                            out += EditableBlock(
                                id = blockId(i),
                                type = BlockType.TASK,
                                field = TextFieldValue(body.substring(6)),
                                indent = indent,
                                checked = true
                            )
                            i++
                        }
                        body.startsWith("> ") -> {
                            out += EditableBlock(
                                id = blockId(i),
                                type = BlockType.QUOTE,
                                field = TextFieldValue(body.substring(2)),
                                indent = indent
                            )
                            i++
                        }
                        body.startsWith("- ") || body.startsWith("* ") || body.startsWith("• ") -> {
                            out += EditableBlock(
                                id = blockId(i),
                                type = BlockType.BULLET,
                                field = TextFieldValue(body.substring(2)),
                                indent = indent
                            )
                            i++
                        }
                        NUMBERED_LINE.matches(body) -> {
                            val m = NUMBERED_LINE.find(body)
                            if (m != null) {
                                out += EditableBlock(
                                    id = blockId(i),
                                    type = BlockType.NUMBERED,
                                    field = TextFieldValue(m.groupValues[2]),
                                    indent = indent,
                                    order = m.groupValues[1].toIntOrNull() ?: 1
                                )
                            }
                            i++
                        }
                        else -> {
                            // Group consecutive plain lines into ONE paragraph.
                            val textLines = StringBuilder()
                            var k = i
                            while (k < lines.size) {
                                val plain = plainBody(lines[k]) ?: if (k == i) lines[k] else break
                                if (textLines.isNotEmpty()) textLines.append('\n')
                                textLines.append(plain)
                                k++
                            }
                            out += EditableBlock(
                                id = blockId(i),
                                type = BlockType.PARAGRAPH,
                                field = TextFieldValue(textLines.toString())
                            )
                            i = if (k > i) k else i + 1
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * Returns the raw line if it is a plain (non-marker) line, else null.
     * Leading whitespace is preserved for round-trip fidelity.
     */
    private fun plainBody(line: String): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        if (t.startsWith(CODE_FENCE)) return null
        if (t == "---" || t == "***" || t == "___") return null
        if (t.startsWith("#")) return null
        val indented = line.trimStart()
        if (indented.startsWith("> ")) return null
        if (indented.startsWith("- [ ") || indented.startsWith("* [ ")) return null
        if (indented.startsWith("- ") || indented.startsWith("* ") || indented.startsWith("• ")) return null
        if (NUMBERED_LINE.matches(indented)) return null
        if (t.startsWith("|")) return null
        if (IMAGE_LINE.matches(t)) return null
        return line
    }

    /**
     * Nesting level of [raw]: 2 spaces (or 1 tab) per level, like SiYuan lists.
     */
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
}

const val SLASH_GROUP_HEADINGS = "Headings"
const val SLASH_GROUP_BASIC = "Basic"
const val SLASH_GROUP_RICH = "Rich"

/**
 * Slash-menu / formatting-toolbar command registry (SiYuan menu groups).
 * SEAM for the follow-up worker: add commands here + a [BlockType] branch and
 * they appear in the grouped insert sheet automatically.
 */
enum class BlockCommand(val label: String, val groupName: String) {
    H1("Heading 1", SLASH_GROUP_HEADINGS),
    H2("Heading 2", SLASH_GROUP_HEADINGS),
    H3("Heading 3", SLASH_GROUP_HEADINGS),
    H4("Heading 4", SLASH_GROUP_HEADINGS),
    H5("Heading 5", SLASH_GROUP_HEADINGS),
    H6("Heading 6", SLASH_GROUP_HEADINGS),
    BULLET("Bulleted list", SLASH_GROUP_BASIC),
    NUMBERED("Ordered list", SLASH_GROUP_BASIC),
    TASK("Task", SLASH_GROUP_BASIC),
    QUOTE("Quote", SLASH_GROUP_BASIC),
    DIVIDER("Divider", SLASH_GROUP_BASIC),
    CODE("Code block", SLASH_GROUP_BASIC),
    TABLE("Table", SLASH_GROUP_RICH),
    IMAGE("Image", SLASH_GROUP_RICH),
    LINK("Link", SLASH_GROUP_RICH),
    TAG("Tag", SLASH_GROUP_RICH),
    HIGHLIGHT("Highlight", SLASH_GROUP_RICH),
    EMOJI("Emoji", SLASH_GROUP_RICH);

    companion object {
        const val GROUP_HEADINGS = SLASH_GROUP_HEADINGS
        const val GROUP_BASIC = SLASH_GROUP_BASIC
        const val GROUP_RICH = SLASH_GROUP_RICH

        /** Grouped menu structure exactly as the Phase-1 spec requires. */
        fun slashGroups(): List<Pair<String, List<BlockCommand>>> = listOf(
            GROUP_HEADINGS to listOf(H1, H2, H3, H4, H5, H6),
            GROUP_BASIC to listOf(BULLET, NUMBERED, TASK, QUOTE, DIVIDER, CODE),
            GROUP_RICH to listOf(TABLE, IMAGE, LINK, TAG, HIGHLIGHT, EMOJI)
        )
    }
}
