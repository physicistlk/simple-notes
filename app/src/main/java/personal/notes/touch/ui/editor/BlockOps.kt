package personal.notes.touch.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Pure editing operations over [EditableBlock] lists (unit-testable without
 * Android). The ViewModel orchestrates these and re-serializes via
 * [BlockModel.serializeBlocks]; block state itself stays UI-free data.
 *
 * SEAM: the follow-up worker (tags/emoji/images/read-only) can add new
 * conversions here without touching the ViewModel wiring.
 */
object BlockOps {

    /**
     * Enter pressed in [block] with the (already sanitized) pre/post text.
     * At the very end of a list/quote/task block the SAME type continues
     * (Notion/SiYuan behaviour); an empty item ends the list by demoting to a
     * plain paragraph instead; a heading always continues into a paragraph.
     * A caret in the middle splits the block into two blocks of the same type
     * (the new TASK item is unchecked).
     *
     * [atEnd] true = caret sat at the end of the block text.
     */
    fun enterSplit(
        blocks: List<EditableBlock>,
        blockId: String,
        preText: String,
        postText: String,
        atEnd: Boolean
    ): List<EditableBlock> {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return blocks
        val cur = blocks[idx]
        val caretAtEnd = (atEnd && postText.isEmpty())

        // Empty item + Enter at its end exits the list (demote to paragraph) without adding extra block.
        if (caretAtEnd && cur.isEmptyTextRaw(preText) && isItemLike(cur)) {
            val mutable = blocks.toMutableList()
            mutable[idx] = cur.copy(
                type = BlockType.PARAGRAPH,
                indent = 0,
                checked = false,
                field = TextFieldValue("", TextRange(0))
            )
            return mutable
        }

        val updatedCurrent = cur.copy(field = TextFieldValue(preText, TextRange(preText.length)))

        val continueItem = isItemLike(cur) && preText.isNotEmpty()
        val newFieldText = if (caretAtEnd) "" else postText
        val newBlock: EditableBlock = if (continueItem) when (cur.type) {
                BlockType.NUMBERED -> EditableBlock(
                    id = newId(blocks, idx),
                    type = BlockType.NUMBERED,
                    field = TextFieldValue(newFieldText, TextRange(newFieldText.length)),
                    indent = cur.indent,
                    order = cur.order + 1
                )
                BlockType.TASK -> EditableBlock(
                    id = newId(blocks, idx),
                    type = BlockType.TASK,
                    field = TextFieldValue(newFieldText, TextRange(newFieldText.length)),
                    indent = cur.indent,
                    checked = false
                )
                BlockType.QUOTE -> EditableBlock(
                    id = newId(blocks, idx),
                    type = BlockType.QUOTE,
                    field = TextFieldValue(newFieldText, TextRange(newFieldText.length)),
                    indent = cur.indent
                )
                else -> EditableBlock( // BULLET
                    id = newId(blocks, idx),
                    type = BlockType.BULLET,
                    field = TextFieldValue(newFieldText, TextRange(newFieldText.length)),
                    indent = cur.indent
                )
            } else {
                EditableBlock(
                    id = newId(blocks, idx),
                    type = BlockType.PARAGRAPH,
                    field = TextFieldValue(postText, TextRange(postText.length))
                )
            }

        val mutable = blocks.toMutableList()
        mutable[idx] = updatedCurrent
        mutable.add(idx + 1, newBlock)
        return mutable
    }

    private fun EditableBlock.isEmptyTextRaw(preText: String) = preText.isEmpty()

    private fun isItemLike(b: EditableBlock): Boolean =
        b.type == BlockType.BULLET || b.type == BlockType.NUMBERED ||
            b.type == BlockType.TASK || b.type == BlockType.QUOTE

    /** Deterministic id for a block inserted after [idx]: shifts nothing else,
     *  and ids only need uniqueness within one parse pass (content+position). */
    private fun newId(blocks: List<EditableBlock>, idx: Int): String {
        var attempt = 0
        val base = blocks[idx].id
        var candidate = base + "_n$attempt"
        while (blocks.any { it.id == candidate }) {
            attempt++
            candidate = base + "_n$attempt"
        }
        return candidate
    }

    /**
     * Backspace with the caret at column 0 of [blockId].
     * Returns the new list (same instance if nothing should change) and the id
     * of the block that should receive focus afterwards.
     */
    data class BackspaceResult(val blocks: List<EditableBlock>, val focusId: String?)

    fun backspaceAtStart(blocks: List<EditableBlock>, blockId: String): BackspaceResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return BackspaceResult(blocks, null)
        val cur = blocks[idx]
        val prevIdx = idx - 1
        val prev = if (prevIdx >= 0) blocks[prevIdx] else null

        fun demote(): List<EditableBlock> {
            val m = blocks.toMutableList()
            m[idx] = cur.copy(
                type = BlockType.PARAGRAPH,
                indent = 0,
                checked = false,
                headingLevel = 1,
                field = TextFieldValue(cur.field.text, TextRange(cur.field.text.length))
            )
            return m
        }

        // First block: empty text stays; items demote; paragraphs/code stay.
        if (prev == null) {
            return BackspaceResult(
                if (cur.isEmptyText || cur.type.notDemotable()) blocks else demote(),
                null
            )
        }

        // Sensible no-ops per spec: divider or empty heading before → just stay.
        if (prev.type == BlockType.DIVIDER ||
            (prev.type == BlockType.HEADING && prev.field.text.isEmpty())
        ) {
            return BackspaceResult(blocks, null)
        }

        return if (cur.isEmptyText) {
            // Empty item → demote marker away; empty paragraph → delete+focus prev.
            if (cur.type == BlockType.PARAGRAPH) {
                val m = blocks.toMutableList()
                m.removeAt(idx)
                val merged = prev.copy(
                    field = prev.field.copy(
                        selection = TextRange(prev.field.text.length)
                    )
                )
                m[prevIdx] = merged
                BackspaceResult(m, prev.id)
            } else {
                BackspaceResult(demote(), cur.id)
            }
        } else {
            // Merge text into the previous block (its type wins), remove this one.
            val mergedText = prev.field.text + cur.field.text
            val caret = prev.field.text.length
            val merged = prev.copy(
                field = TextFieldValue(mergedText, TextRange(caret)),
                closed = prev.closed && cur.closed
            )
            val m = blocks.toMutableList()
            m[prevIdx] = merged
            m.removeAt(idx)
            BackspaceResult(m, prev.id)
        }
    }

    private fun BlockType.notDemotable(): Boolean =
        this == BlockType.CODE || this == BlockType.TABLE ||
            this == BlockType.IMAGE || this == BlockType.DIVIDER

    /**
     * Wraps the selection (or inserts a placeholder) of this block's field with
     * inline markdown markers. Keeps the selection over the wrapped text so a
     * second tap can be judged visually; selection bookkeeping mirrors the
     * legacy applyWrapFormatting helper.
     */
    fun wrapInline(
        block: EditableBlock,
        prefix: String,
        suffix: String = prefix,
        placeholder: String = "text"
    ): EditableBlock {
        val f = block.field
        val text = f.text
        val sel = f.selection
        val newValue: TextFieldValue = if (sel.collapsed) {
            val at = sel.start.coerceIn(0, text.length)
            val before = text.substring(0, at)
            val after = text.substring(at)
            val inserted = prefix + placeholder + suffix
            TextFieldValue(
                before + inserted + after,
                TextRange(at + prefix.length, at + prefix.length + placeholder.length)
            )
        } else {
            val start = sel.start.coerceIn(0, text.length)
            val end = sel.end.coerceIn(0, text.length)
            val selText = text.substring(start, end)
            val wrapped = prefix + selText + suffix
            val before = text.substring(0, start)
            val after = text.substring(end)
            TextFieldValue(
                before + wrapped + after,
                TextRange(start, start + wrapped.length)
            )
        }
        return block.copy(field = newValue)
    }

    /**
     * The mid-split behaviour for the slash-menu pick on a fresh block:
     * "/" + typed filter chars before the caret are removed first.
     */
    fun stripSlashQuery(block: EditableBlock): Pair<EditableBlock, String> {
        val text = block.field.text
        val caret = block.field.selection.start.coerceIn(0, text.length)
        if (caret < 1 || !text.startsWith('/')) return block to text
        val body = text.substring(caret)
        return block.copy(field = TextFieldValue(body, TextRange(body.length))) to body
    }
}
