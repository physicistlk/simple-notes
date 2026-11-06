package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.dao.BlockCandidate
import personal.notes.touch.text.QueryBlocks

class QueryBlocksTest {

    private fun cand(
        id: String,
        noteId: String = "n1",
        text: String = "",
        title: String = "Note",
        index: Int = 0,
        type: String = "PARAGRAPH"
    ): BlockCandidate = BlockCandidate(id, noteId, text, title, index, type)

    @Test
    fun emptyExpressionReturnsNothing() {
        val cands = listOf(cand("a", text = "hello"))
        assertEquals(emptyList<BlockCandidate>(), QueryBlocks.search(cands, ""))
        assertEquals(emptyList<BlockCandidate>(), QueryBlocks.search(cands, "   "))
    }

    @Test
    fun bareTokenMatchesTextOrTitle() {
        val cands = listOf(
            cand("a", text = "launch plan", title = "Roadmap"),
            cand("b", text = "other", title = "Launch Notes")
        )
        val out = QueryBlocks.search(cands, "launch")
        assertEquals(listOf("b", "a"), out.map { it.blockId })
    }

    @Test
    fun textPrefixMatchesTextOnly() {
        val cands = listOf(
            cand("a", text = "launch plan", title = "Roadmap"),
            cand("b", text = "other", title = "Launch Notes")
        )
        val out = QueryBlocks.search(cands, "text:launch")
        assertEquals(listOf("a"), out.map { it.blockId })
    }

    @Test
    fun titlePrefixMatchesTitleOnly() {
        val cands = listOf(
            cand("a", text = "launch plan", title = "Roadmap"),
            cand("b", text = "other", title = "Launch Notes")
        )
        val out = QueryBlocks.search(cands, "title:launch")
        assertEquals(listOf("b"), out.map { it.blockId })
    }

    @Test
    fun tagPrefixMatchesTagInBlock() {
        val cands = listOf(
            cand("a", text = "stuff #project# here"),
            cand("b", text = "no tag here")
        )
        val out = QueryBlocks.search(cands, "tag:project")
        assertEquals(listOf("a"), out.map { it.blockId })
    }

    @Test
    fun tagPrefixMatchesSubtag() {
        val cands = listOf(
            cand("a", text = "stuff #project/alpha# here")
        )
        val out = QueryBlocks.search(cands, "tag:project")
        assertEquals(listOf("a"), out.map { it.blockId })
    }

    @Test
    fun multipleTermsAreAnded() {
        val cands = listOf(
            cand("a", text = "alpha beta"),
            cand("b", text = "alpha only"),
            cand("c", text = "beta only")
        )
        val out = QueryBlocks.search(cands, "alpha beta")
        assertEquals(listOf("a"), out.map { it.blockId })
    }

    @Test
    fun quotedPhrasesStayTogether() {
        val cands = listOf(
            cand("a", text = "launch plan today"),
            cand("b", text = "launch tomorrow")
        )
        val out = QueryBlocks.search(cands, "\"launch plan\"")
        assertEquals(listOf("a"), out.map { it.blockId })
    }

    @Test
    fun excludeBlockIdRemovesIt() {
        val cands = listOf(
            cand("a", text = "hello"),
            cand("b", text = "hello")
        )
        val out = QueryBlocks.search(cands, "hello", excludeBlockId = "a")
        assertEquals(listOf("b"), out.map { it.blockId })
    }

    @Test
    fun queryBlocksAreExcluded() {
        val cands = listOf(
            cand("a", text = "hello", type = "QUERY"),
            cand("b", text = "hello", type = "PARAGRAPH")
        )
        val out = QueryBlocks.search(cands, "hello")
        assertEquals(listOf("b"), out.map { it.blockId })
    }

    @Test
    fun limitCapsResults() {
        val cands = (1..10).map { cand("b$it", text = "match") }
        val out = QueryBlocks.search(cands, "match", limit = 3)
        assertEquals(3, out.size)
    }

    @Test
    fun parseEmpty() {
        val parsed = QueryBlocks.parse("")
        assertTrue(parsed.empty)
    }

    @Test
    fun parseTerms() {
        val parsed = QueryBlocks.parse("tag:foo bar \"baz qux\"")
        assertEquals(3, parsed.terms.size)
        assertEquals("tag", parsed.terms[0].prefix)
        assertEquals("foo", parsed.terms[0].value)
        assertEquals(null, parsed.terms[1].prefix)
        assertEquals("bar", parsed.terms[1].value)
        assertEquals(null, parsed.terms[2].prefix)
        assertEquals("baz qux", parsed.terms[2].value)
    }

    @Test
    fun noteAliasWorks() {
        val cands = listOf(
            cand("a", text = "stuff", title = "Physics")
        )
        val out = QueryBlocks.search(cands, "note:physics")
        assertEquals(listOf("a"), out.map { it.blockId })
    }

    @Test
    fun noMatchReturnsEmpty() {
        val cands = listOf(cand("a", text = "hello"))
        val out = QueryBlocks.search(cands, "zzzzz")
        assertEquals(emptyList<BlockCandidate>(), out)
    }
}
