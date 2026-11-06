package personal.notes.touch.text

/** One `((blockId))` / `((blockId|alias))` occurrence. */
data class BlockRef(
    /** Normalised short block key (lowercase): the first 8 chars of the UUID. */
    val target: String,
    val alias: String?,
    val raw: String
) {
    val display: String get() = alias ?: target
}

/**
 * Block references: `((blockId))` with an optional `((blockId|alias))`.
 *
 * Blocks already have stable ids (`note_blocks.id`, a UUID), so unlike note
 * links this syntax does not depend on a title that may be renamed. The token
 * carries only the first [SHORT_KEY_LENGTH] characters — enough to be readable
 * when the markdown is visible — and resolution matches `id LIKE key || '%'`.
 *
 * Scope and honesty:
 * - The key must be hex/dashes ([KEY]): a `((` in prose does not become a
 *   reference by accident, and a wildcard can never reach SQL.
 * - Fenced code is skipped, like tags and wiki links; a sample that documents
 *   the syntax is not an actual reference.
 * - An unknown key still renders (as *Missing block*), because a reference to
 *   a deleted block should be visible, not silently dropped.
 *
 * Pure Kotlin (no Android, no Room) so the grammar and the caret completion are
 * unit tested in isolation.
 */
object BlockRefs {

    const val SHORT_KEY_LENGTH = 8
    const val MAX_ALIAS = 120

    private const val MAX_PENDING_QUERY = 40

    /** `((key))` or `((key|alias))`; the alias may not contain parens or a newline. */
    private val REF = Regex("""\(\(([0-9a-fA-F-]{4,36})(?:\|([^()\n]{1,$MAX_ALIAS}))?\)\)""")

    /** A key that can be looked up: starts with a hex digit, hex/dashes after. */
    private val KEY = Regex("""^[0-9a-f][0-9a-f-]{3,35}$""")

    /** Every distinct reference in [content], first occurrence wins. */
    fun parse(content: String): List<BlockRef> {
        if (!content.contains("((")) return emptyList()
        val seen = LinkedHashMap<String, BlockRef>()
        for (line in MarkdownText.outsideFences(content).split('\n')) {
            for (ref in parseLine(line)) {
                seen.putIfAbsent(ref.target, ref)
            }
        }
        return seen.values.toList()
    }

    fun parseLine(line: String): List<BlockRef> {
        if (!line.contains("((")) return emptyList()
        return REF.findAll(line).mapNotNull { match ->
            val target = match.groupValues[1].trim().lowercase()
            if (!KEY.matches(target)) return@mapNotNull null
            val alias = match.groupValues[2].trim().ifEmpty { null }
            BlockRef(target = target, alias = alias, raw = match.value)
        }.toList()
    }

    /** A reference matched exactly at [index], for inline renderers. */
    data class Token(val ref: BlockRef, val start: Int, val end: Int)

    fun matchAt(text: String, index: Int): Token? {
        val match = REF.find(text, index) ?: return null
        if (match.range.first != index) return null
        val target = match.groupValues[1].trim().lowercase()
        if (!KEY.matches(target)) return null
        val alias = match.groupValues[2].trim().ifEmpty { null }
        return Token(
            ref = BlockRef(target = target, alias = alias, raw = match.value),
            start = match.range.first,
            end = match.range.last + 1
        )
    }

    /**
     * The short key written into the note for [blockId]. The first 8 chars of a
     * UUID are hex (its first dash is at index 8), so the result is a valid
     * [KEY] and still a literal prefix of the stored id.
     */
    fun shortKey(blockId: String): String =
        blockId.take(SHORT_KEY_LENGTH).lowercase().trimEnd('-')

    /** An opened, not yet closed `((query` ending at the caret (autocomplete). */
    data class Pending(val start: Int, val query: String, val end: Int)

    fun pendingAt(text: String, caret: Int): Pending? {
        if (caret < 2 || caret > text.length) return null
        val start = text.lastIndexOf("((", caret - 2)
        if (start < 0) return null
        val between = text.substring(start + 2, caret)
        if (between.contains("))") || between.contains('|') || between.contains('\n')) return null
        if (between.length > MAX_PENDING_QUERY) return null
        if (text.startsWith("))", caret)) return null
        return Pending(start = start, query = between, end = caret)
    }

    /** Replaces the pending `((query` with a complete reference. */
    fun complete(
        text: String,
        pending: Pending,
        blockId: String,
        label: String
    ): Pair<String, Int> {
        val key = shortKey(blockId)
        val safeLabel = labelFromText(label)
        val insertion = if (safeLabel.isEmpty()) "(($key))" else "(($key|$safeLabel))"
        val newText = text.substring(0, pending.start) + insertion + text.substring(pending.end)
        return newText to (pending.start + insertion.length)
    }

    /** One-line label for a referenced block; empty when the text has no words. */
    fun labelFromText(text: String?): String =
        text.orEmpty()
            .replace('(', ' ')
            .replace(')', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)

    /**
     * Inline text with references removed, keeping an alias when one was
     * written. Used where a token would be noise (flashcard text, snippets).
     */
    fun strip(text: String): String {
        if (!text.contains("((")) return text
        return REF.replace(text) { match ->
            match.groupValues[2].trim()
        }.trim()
    }

    /**
     * What the reader sees for a reference: the alias, else the target block's
     * current text, else an explicit missing marker.
     */
    fun displayLabel(alias: String?, targetText: String?, key: String): String =
        alias?.takeIf { it.isNotBlank() }
            ?: labelFromText(targetText).takeIf { it.isNotEmpty() }
            ?: "Missing block $key"
}
