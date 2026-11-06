package personal.notes.touch.text

/**
 * Turns one editor block into a flashcard: `front :: back`.
 *
 * The block editor's markdown is the source, so a card front and back are
 * written inline in the note and can be read back years later. Inline markers
 * (`**bold**`, `` `code` ``, `[[wiki]]`) are stripped here — a card shows text,
 * not markup.
 *
 * A `::` inside a backtick code span, or escaped as `\::`, is literal, so
 * `Use \:: for a card` describes the syntax instead of creating a card.
 * A block without a separator still yields a card: the whole block is the
 * front and the back is empty, ready to be filled in the dialog.
 *
 * Pure Kotlin (no Android, no Compose) so the split is unit tested once and
 * shared by the block menu and the repository.
 */
object BlockCards {

    const val SEPARATOR = "::"
    const val FALLBACK_DECK = "Notes"

    data class Split(
        val front: String,
        val back: String,
        /** True when the block actually contained an unescaped, unquoted `::`. */
        val hasSeparator: Boolean
    )

    fun split(text: String): Split {
        val at = separatorIndex(text)
        return if (at < 0) {
            Split(front = plain(text), back = "", hasSeparator = false)
        } else {
            Split(
                front = plain(text.substring(0, at)),
                back = plain(text.substring(at + SEPARATOR.length)),
                hasSeparator = true
            )
        }
    }

    /** Marker-free display text of one side of the card. */
    fun plain(text: String): String = InlineMarkdown.plain(text).trim()

    /** Deck name for a note; an untitled note still gets a usable deck. */
    fun deckName(noteTitle: String?): String =
        noteTitle?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_DECK

    private fun separatorIndex(text: String): Int {
        var i = 0
        var inCode = false
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                i += 2
                continue
            }
            if (c == '`') {
                inCode = !inCode
                i++
                continue
            }
            if (!inCode && c == ':' && i + 1 < text.length && text[i + 1] == ':') return i
            i++
        }
        return -1
    }
}
