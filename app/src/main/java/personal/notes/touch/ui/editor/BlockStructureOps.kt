package personal.notes.touch.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Structural block operations behind the block menu and the indent/outdent
 * buttons. Pure list transforms: the ViewModel owns persistence and focus.
 */
object BlockStructureOps {

    const val MAX_INDENT = 6

    /** Only list blocks have a persisted nesting level (see BlockModel). */
    fun isListBlock(type: BlockType): Boolean =
        type == BlockType.BULLET || type == BlockType.NUMBERED || type == BlockType.TASK

    fun shiftIndent(blocks: List<EditableBlock>, id: String, delta: Int): List<EditableBlock> {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return blocks
        val current = blocks[index]
        if (!isListBlock(current.type)) return blocks
        val next = (current.indent + delta).coerceIn(0, MAX_INDENT)
        if (next == current.indent) return blocks
        val mutable = blocks.toMutableList()
        mutable[index] = current.copy(indent = next)
        return mutable
    }

    /** Copies [id] right below itself with a new stable id; caret at the end. */
    fun duplicate(
        blocks: List<EditableBlock>,
        id: String,
        newId: () -> String
    ): List<EditableBlock> {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return blocks
        val source = blocks[index]
        val copy = source.copy(
            id = newId(),
            field = source.field.copy(selection = TextRange(source.field.text.length))
        )
        val mutable = blocks.toMutableList()
        mutable.add(index + 1, copy)
        return mutable
    }

    /** Removes [id]; the editor never becomes blockless (one empty paragraph stays). */
    fun delete(
        blocks: List<EditableBlock>,
        id: String,
        newId: () -> String
    ): List<EditableBlock> {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return blocks
        val mutable = blocks.toMutableList()
        mutable.removeAt(index)
        if (mutable.isEmpty()) {
            mutable += EditableBlock(id = newId(), type = BlockType.PARAGRAPH)
        }
        return mutable
    }

    /**
     * Blocks visible in the editor when heading folding is applied: after a
     * collapsed heading, everything is hidden until the next heading whose
     * level is equal or higher (1 = H1). Headings themselves always stay
     * visible so they can be unfolded.
     */
    fun visibleBlocks(blocks: List<EditableBlock>): List<EditableBlock> {
        val result = ArrayList<EditableBlock>(blocks.size)
        var collapseLevel: Int? = null
        for (block in blocks) {
            if (collapseLevel != null) {
                if (block.type == BlockType.HEADING && block.headingLevel <= collapseLevel) {
                    collapseLevel = null
                } else {
                    continue
                }
            }
            result.add(block)
            if (block.type == BlockType.HEADING && block.collapsed) {
                collapseLevel = block.headingLevel
            }
        }
        return result
    }

    /** Moves [id] by [delta] positions (clamped); returns the same list when out of range. */
    fun move(blocks: List<EditableBlock>, id: String, delta: Int): List<EditableBlock> {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return blocks
        val target = index + delta
        if (target !in blocks.indices) return blocks
        val mutable = blocks.toMutableList()
        val block = mutable.removeAt(index)
        mutable.add(target, block)
        return mutable
    }

    /**
     * Turns [id] into [type] while keeping the block's identity (text rules
     * live in [textForTarget]) and, when it stays a list, its nesting.
     */
    fun transform(
        blocks: List<EditableBlock>,
        id: String,
        type: BlockType,
        headingLevel: Int = 1
    ): List<EditableBlock> {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return blocks
        val current = blocks[index]
        if (current.type == type && (type != BlockType.HEADING || current.headingLevel == headingLevel)) {
            return blocks
        }

        val keepIndent = isListBlock(type) && isListBlock(current.type)
        val text = textForTarget(current, type)
        val updated = current.copy(
            type = type,
            headingLevel = headingLevel.coerceIn(1, 6),
            indent = if (keepIndent) current.indent else 0,
            order = if (type == BlockType.NUMBERED) 1 else current.order,
            checked = if (type == BlockType.TASK) current.checked else false,
            closed = true,
            field = TextFieldValue(text, TextRange(text.length))
        )
        val mutable = blocks.toMutableList()
        mutable[index] = updated
        return mutable
    }

    /**
     * Text stored in the block after a type change. Structural sources are
     * flattened so the line cannot re-parse as its old type on the next open:
     * an image keeps its alt text, a table becomes readable `a · b` lines, a
     * divider becomes empty. Targets that need syntax (table, image) get
     * starter content when the source text is not already valid for them.
     */
    private fun textForTarget(current: EditableBlock, type: BlockType): String {
        val source = current.field.text
        return when (type) {
            BlockType.IMAGE -> when {
                ImageBlockRefParser.parse(source) != null -> source
                source.isBlank() -> "![image](https://)"
                else -> "![${altFromText(source)}](https://)"
            }
            BlockType.TABLE -> {
                if (source.trimStart().startsWith("|")) source
                else BlockModel.DEFAULT_TABLE_ROWS.joinToString("\n")
            }
            BlockType.DIVIDER -> ""
            BlockType.CODE -> source
            else -> when (current.type) {
                BlockType.IMAGE -> ImageBlockRefParser.parse(source)?.alt ?: ""
                BlockType.TABLE -> tableToPlainText(source)
                BlockType.DIVIDER -> ""
                else -> source
            }
        }
    }

    /** Alt text for an IMAGE block built from prose; strips markdown brackets. */
    private fun altFromText(text: String): String {
        val cleaned = text
            .replace(Regex("[\\[\\]()\\r\\n]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(80)
        return cleaned.ifEmpty { "image" }
    }

    /** Table markdown flattened to `cell · cell` lines (separator rows dropped). */
    private fun tableToPlainText(text: String): String = text.lines()
        .map { it.trim() }
        .filter { it.startsWith("|") }
        .filterNot { row -> row.trim('|').split('|').all { it.trim().matches(Regex("^:?-{1,}:?$")) } }
        .map { row ->
            row.trim('|').split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}
