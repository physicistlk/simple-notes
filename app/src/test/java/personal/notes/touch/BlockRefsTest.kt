package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.BlockRefs
import personal.notes.touch.ui.editor.RichTextParser

/**
 * Block-reference grammar, caret completion and display fallbacks.
 */
class BlockRefsTest {

    @Test
    fun plainReferenceParses() {
        val refs = BlockRefs.parse("See ((1a2b3c4d)) for the derivation.")
        assertEquals(1, refs.size)
        assertEquals("1a2b3c4d", refs[0].target)
        assertNull(refs[0].alias)
    }

    @Test
    fun aliasParsesAndIsTrimmed() {
        val refs = BlockRefs.parse("See ((1a2b3c4d|  the derivation  ))")
        assertEquals("the derivation", refs[0].alias)
        assertEquals("the derivation", refs[0].display)
    }

    @Test
    fun keysAreCaseInsensitive() {
        assertEquals("1a2b3c4d", BlockRefs.parse("((1A2B3C4D))")[0].target)
    }

    @Test
    fun fullUuidAlsoMatches() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val refs = BlockRefs.parse("(($uuid))")
        assertEquals(1, refs.size)
        assertEquals(uuid, refs[0].target)
    }

    @Test
    fun nonHexTokensAreNotReferences() {
        // A prose parenthetical, a wildcard attempt and a too-short key.
        assertTrue(BlockRefs.parse("((a note about cards))").isEmpty())
        assertTrue(BlockRefs.parse("((%))").isEmpty())
        assertTrue(BlockRefs.parse("((abc))").isEmpty())
    }

    @Test
    fun unterminatedReferenceIsNotParsed() {
        assertTrue(BlockRefs.parse("((1a2b3c4d is not closed").isEmpty())
    }

    @Test
    fun fencedCodeIsNotAnIndexEntry() {
        val content = "```\n((1a2b3c4d))\n```\n\nOutside ((deadbeef))"
        val refs = BlockRefs.parse(content)
        assertEquals(1, refs.size)
        assertEquals("deadbeef", refs[0].target)
    }

    @Test
    fun duplicatesCollapseToTheFirstAlias() {
        val refs = BlockRefs.parse("((deadbeef|first)) and ((deadbeef|second))")
        assertEquals(1, refs.size)
        assertEquals("first", refs[0].alias)
    }

    @Test
    fun parseLineIgnoresTheRestOfTheLine() {
        val refs = BlockRefs.parseLine("a ((11112222)) b ((33334444|third))")
        assertEquals(listOf("11112222", "33334444"), refs.map { it.target })
    }

    // ------------------------------------------------------------ caret ----

    @Test
    fun pendingFindsAnOpenReference() {
        val pending = BlockRefs.pendingAt("Text ((dead", 11)
        assertEquals(5, pending?.start)
        assertEquals("dead", pending?.query)
        assertEquals(11, pending?.end)
    }

    @Test
    fun pendingIsNullForAliasedOrClosedReferences() {
        assertNull(BlockRefs.pendingAt("((dead|alias", 11))
        assertNull(BlockRefs.pendingAt("((dead))", 8))
        assertNull(BlockRefs.pendingAt("plain text", 10))
    }

    @Test
    fun completionWritesTheShortKeyAndLabel() {
        val text = "See ((dead"
        val pending = BlockRefs.pendingAt(text, text.length)!!
        val (completed, caret) = BlockRefs.complete(
            text,
            pending,
            blockId = "deadbeef-1111-2222-3333-444455556666",
            label = "The  derivation"
        )
        assertEquals("See ((deadbeef|The derivation))", completed)
        assertEquals(completed.length, caret)
    }

    @Test
    fun completionWithoutALabelWritesABareKey() {
        val text = "(("
        val pending = BlockRefs.pendingAt(text, 2)!!
        val (completed, _) = BlockRefs.complete(text, pending, "deadbeef-1", "   ")
        assertEquals("((deadbeef))", completed)
    }

    @Test
    fun labelStripsParensSoTheTokenStaysClosed() {
        val label = BlockRefs.labelFromText("A (nested) point")
        assertEquals("A nested point", label)
    }

    @Test
    fun shortKeyIsAPrefixOfTheUuid() {
        val id = "1A2B3C4D-0000-0000-0000-000000000000"
        val key = BlockRefs.shortKey(id)
        assertEquals("1a2b3c4d", key)
        assertTrue(id.lowercase().startsWith(key))
    }

    @Test
    fun displayLabelFallsBackFromAliasToLiveTextToMissing() {
        assertEquals("alias", BlockRefs.displayLabel("alias", "live text", "deadbeef"))
        assertEquals("live text", BlockRefs.displayLabel(null, "live text", "deadbeef"))
        assertEquals("Missing block deadbeef", BlockRefs.displayLabel(null, null, "deadbeef"))
        assertFalse(BlockRefs.displayLabel("", "", "deadbeef").isEmpty())
    }

    // --------------------------------------------------------- inline ------

    @Test
    fun matchAtOnlyMatchesExactlyAtTheIndex() {
        val text = "x((deadbeef))"
        assertEquals("deadbeef", BlockRefs.matchAt(text, 1)?.ref?.target)
        assertNull(BlockRefs.matchAt(text, 2))
        assertNull(BlockRefs.matchAt(text, 0))
    }

    @Test
    fun previewRendersALiveLabelAndTagsTheTarget() {
        val parsed = RichTextParser.parseInline(
            "see ((deadbeef)) and ((cafebabe|short label))"
        ) { key -> if (key == "deadbeef") "live block text" else null }
        assertTrue(parsed.text.contains("live block text"))
        assertTrue(parsed.text.contains("short label"))
        val targets = parsed
            .getStringAnnotations(RichTextParser.BLOCK_REF_TAG, 0, parsed.length)
            .map { it.item }
        assertEquals(listOf("deadbeef", "cafebabe"), targets)
    }

    @Test
    fun previewKeepsTheAliasWhenTheTargetIsMissing() {
        val parsed = RichTextParser.parseInline("((deadbeef|still here))")
        assertTrue(parsed.text.contains("still here"))
        val targets = parsed
            .getStringAnnotations(RichTextParser.BLOCK_REF_TAG, 0, parsed.length)
        assertEquals(1, targets.size)
    }

    @Test
    fun stripKeepsAliasesAndDropsBareTokens() {
        assertEquals("about the proof", BlockRefs.strip("about ((deadbeef|the proof))"))
        assertEquals("about", BlockRefs.strip("about ((deadbeef))").trim())
    }
}
