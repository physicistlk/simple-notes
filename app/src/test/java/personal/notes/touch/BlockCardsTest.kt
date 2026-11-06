package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.BlockCards

/**
 * The block → flashcard split: `front :: back`, with the note's markdown
 * treated as syntax rather than card text.
 */
class BlockCardsTest {

    @Test
    fun separatorSplitsFrontAndBack() {
        val split = BlockCards.split("What is SM-2? :: A spaced repetition algorithm")
        assertEquals("What is SM-2?", split.front)
        assertEquals("A spaced repetition algorithm", split.back)
        assertTrue(split.hasSeparator)
    }

    @Test
    fun whitespaceAroundBothSidesIsTrimmed() {
        val split = BlockCards.split("   front   ::   back   ")
        assertEquals("front", split.front)
        assertEquals("back", split.back)
    }

    @Test
    fun blockWithoutSeparatorBecomesAFrontOnlyCard() {
        val split = BlockCards.split("Only a question?")
        assertEquals("Only a question?", split.front)
        assertEquals("", split.back)
        assertFalse(split.hasSeparator)
    }

    @Test
    fun onlyTheFirstSeparatorSplits() {
        val split = BlockCards.split("a :: b :: c")
        assertEquals("a", split.front)
        assertEquals("b :: c", split.back)
    }

    @Test
    fun separatorInsideCodeSpanIsLiteral() {
        val split = BlockCards.split("`a::b` :: c")
        assertEquals("a::b", split.front)
        assertEquals("c", split.back)
        assertTrue(split.hasSeparator)
    }

    @Test
    fun escapedSeparatorIsLiteral() {
        val split = BlockCards.split("Use \\:: to split a card")
        assertFalse(split.hasSeparator)
        // The backslash is a markdown escape, so it is not part of the text.
        assertEquals("Use :: to split a card", split.front)
    }

    @Test
    fun inlineMarkdownIsStrippedFromBothSides() {
        val split = BlockCards.split("**Bold** `code` :: [[Other Note|alias]] and *italic*")
        assertEquals("Bold code", split.front)
        assertEquals("alias and italic", split.back)
    }

    @Test
    fun emptyBlockYieldsEmptyCard() {
        val split = BlockCards.split("   ")
        assertEquals("", split.front)
        assertEquals("", split.back)
        assertFalse(split.hasSeparator)
    }

    @Test
    fun deckNameFallsBackWhenTheNoteIsUntitled() {
        assertEquals("Meeting notes", BlockCards.deckName("  Meeting notes  "))
        assertEquals(BlockCards.FALLBACK_DECK, BlockCards.deckName(""))
        assertEquals(BlockCards.FALLBACK_DECK, BlockCards.deckName(null))
    }

    @Test
    fun separatorAtTheStartOrEndStillSplits() {
        assertEquals("", BlockCards.split(":: answer").front)
        assertEquals("answer", BlockCards.split(":: answer").back)
        assertEquals("question", BlockCards.split("question ::").front)
        assertEquals("", BlockCards.split("question ::").back)
    }
}
