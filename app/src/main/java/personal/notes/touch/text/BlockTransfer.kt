package personal.notes.touch.text

import personal.notes.touch.data.db.entity.NoteBlockEntity

/**
 * Pure selection/rewrite helpers for moving or copying blocks between notes.
 *
 * The editor never loses the markdown source of truth: both notes are
 * re-serialized from their block rows after a transfer, and the caller writes
 * the rows before the markdown so a crash between the two writes duplicates a
 * block instead of deleting it.
 *
 * A **move** keeps the block UUIDs, so `((block refs))` written anywhere keep
 * pointing at the moved block. A **copy** mints fresh ids, because two rows may
 * not share a primary key and a copied block is a different block.
 */
object BlockTransfer {

    /**
     * Rows to transfer: the selected rows plus, for list-like blocks, the
     * descendants that follow them with a larger indent. A selection of
     * `- parent` therefore drags `- child` along, which is what the outline
     * implies; selecting a paragraph only takes the paragraph.
     */
    fun selection(
        rows: List<NoteBlockEntity>,
        ids: Set<String>,
        includeChildren: Boolean = true
    ): List<NoteBlockEntity> {
        if (ids.isEmpty()) return emptyList()
        val ordered = rows.sortedBy { it.blockIndex }
        val chosen = LinkedHashSet<String>()
        ordered.forEachIndexed { index, row ->
            if (row.id !in ids) return@forEachIndexed
            chosen += row.id
            if (!includeChildren) return@forEachIndexed
            var j = index + 1
            while (j < ordered.size) {
                val next = ordered[j]
                val startsSibling = next.indent <= row.indent
                if (startsSibling) break
                chosen += next.id
                j++
            }
        }
        return ordered.filter { it.id in chosen }
    }

    /** Rows without [ids], re-indexed so the note's order stays dense. */
    fun remove(rows: List<NoteBlockEntity>, ids: Set<String>): List<NoteBlockEntity> =
        reindex(rows.filterNot { it.id in ids })

    /**
     * [incoming] appended to [target], re-indexed. [copyIds] mints new UUIDs
     * (copy) or keeps them (move, so block references survive).
     */
    fun append(
        target: List<NoteBlockEntity>,
        incoming: List<NoteBlockEntity>,
        targetNoteId: String,
        copyIds: Boolean,
        newId: () -> String = { java.util.UUID.randomUUID().toString() },
        now: Long = System.currentTimeMillis()
    ): List<NoteBlockEntity> {
        if (incoming.isEmpty()) return reindex(target)
        val base = (target.maxOfOrNull { it.blockIndex } ?: -1) + 1
        val additions = incoming.mapIndexed { index, row ->
            row.copy(
                id = if (copyIds) newId() else row.id,
                noteId = targetNoteId,
                blockIndex = base + index,
                updatedAt = now
            )
        }
        return reindex(target + additions)
    }

    fun reindex(rows: List<NoteBlockEntity>): List<NoteBlockEntity> =
        rows.sortedBy { it.blockIndex }
            .mapIndexed { index, row -> if (row.blockIndex == index) row else row.copy(blockIndex = index) }
}
