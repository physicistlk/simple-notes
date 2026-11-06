package personal.notes.touch.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import personal.notes.touch.data.db.entity.NoteBlockEntity
import java.util.UUID

/**
 * Mapping between editor blocks ([EditableBlock]) and the persisted
 * `note_blocks` rows, plus stable-id reconciliation for markdown-first edits.
 *
 * ## Invariants
 *
 * - A block id is minted once ([newBlockId], UUID) and never derived from the
 *   block's position. It is what references, backlinks, tags and per-block
 *   flashcards will point at.
 * - `notes.content` stays the text source of truth. On open, if the markdown
 *   and the stored rows disagree, the markdown wins and [reconcileIds] keeps
 *   every id whose block survived (matched by type + text, in order).
 * - Row order is [NoteBlockEntity.blockIndex]; ids never depend on it.
 */
object BlockPersistence {

    fun newBlockId(): String = UUID.randomUUID().toString()

    fun toEntity(
        noteId: String,
        index: Int,
        block: EditableBlock,
        now: Long = System.currentTimeMillis()
    ): NoteBlockEntity = NoteBlockEntity(
        id = block.id,
        noteId = noteId,
        blockIndex = index,
        blockType = block.type.name,
        text = block.field.text,
        headingLevel = block.headingLevel,
        indent = block.indent,
        orderNo = block.order,
        checked = block.checked,
        closed = block.closed,
        collapsed = block.collapsed,
        updatedAt = now
    )

    fun toEntities(
        noteId: String,
        blocks: List<EditableBlock>,
        now: Long = System.currentTimeMillis()
    ): List<NoteBlockEntity> = blocks.mapIndexed { index, block ->
        toEntity(noteId, index, block, now)
    }

    /**
     * Rows → blocks in `blockIndex` order. A `blockType` written by a newer
     * version (or corrupted) degrades to PARAGRAPH instead of crashing; the
     * text is kept so the user never loses content.
     */
    fun fromEntities(rows: List<NoteBlockEntity>): List<EditableBlock> =
        rows.sortedBy { it.blockIndex }.map(::fromEntity)

    fun fromEntity(row: NoteBlockEntity): EditableBlock {
        val type = BlockType.values().firstOrNull { it.name == row.blockType }
            ?: BlockType.PARAGRAPH
        return EditableBlock(
            id = row.id,
            type = type,
            field = TextFieldValue(row.text),
            headingLevel = row.headingLevel.coerceIn(1, 6),
            indent = row.indent.coerceAtLeast(0),
            order = row.orderNo.coerceAtLeast(1),
            checked = row.checked,
            closed = row.closed,
            collapsed = row.collapsed
        )
    }

    /**
     * Re-attaches ids to freshly parsed [parsed] blocks using [previous] as
     * the identity pool. Used when the markdown (not the block editor) was the
     * last writer — Split-mode typing, legacy toolbar helpers, or a crash
     * between the content and block writes.
     *
     * Two passes, both order-preserving:
     * 1. exact content matches (type + text + heading/indent/ordinal), resolved
     *    earliest-first by an LCS so duplicate identical blocks keep their
     *    respective ids in order;
     * 2. any still-unmatched block reuses the nearest unmatched block of the
     *    same [BlockType] — an in-place text edit keeps its identity.
     *
     * Everything else gets a fresh id from [newId]. An id is never reused twice.
     */
    fun reconcileIds(
        parsed: List<EditableBlock>,
        previous: List<EditableBlock>,
        newId: () -> String = ::newBlockId
    ): List<EditableBlock> {
        if (parsed.isEmpty()) return parsed
        if (previous.isEmpty()) return parsed.map { it.copy(id = newId()) }

        val n = parsed.size
        val m = previous.size

        // suf[i][j] = number of exact content matches available when aligning
        // parsed[i..] with previous[j..].
        val suf = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                suf[i][j] = if (sameContent(parsed[i], previous[j])) {
                    suf[i + 1][j + 1] + 1
                } else {
                    maxOf(suf[i + 1][j], suf[i][j + 1])
                }
            }
        }

        val reused = arrayOfNulls<String>(n)
        val consumed = BooleanArray(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                sameContent(parsed[i], previous[j]) && suf[i + 1][j + 1] + 1 == suf[i][j] -> {
                    reused[i] = previous[j].id
                    consumed[j] = true
                    i++
                    j++
                }
                // Skipping this parsed block keeps every remaining match.
                suf[i + 1][j] == suf[i][j] -> i++
                else -> j++
            }
        }

        // Pass 2: in-place edits — nearest unmatched block of the same type.
        for (idx in parsed.indices) {
            if (reused[idx] != null) continue
            var best = -1
            for (p in previous.indices) {
                if (consumed[p] || previous[p].type != parsed[idx].type) continue
                if (best < 0 || kotlin.math.abs(p - idx) < kotlin.math.abs(best - idx)) best = p
            }
            if (best >= 0) {
                reused[idx] = previous[best].id
                consumed[best] = true
            }
        }

        return parsed.mapIndexed { idx, block ->
            val id = reused[idx]
            if (id != null) block.copy(id = id) else block.copy(id = newId())
        }
    }

    private fun sameContent(a: EditableBlock, b: EditableBlock): Boolean =
        a.type == b.type &&
            a.field.text == b.field.text &&
            a.headingLevel == b.headingLevel &&
            a.indent == b.indent &&
            a.order == b.order
}
