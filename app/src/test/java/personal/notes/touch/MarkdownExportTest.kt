package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.InlineMarkdown
import personal.notes.touch.text.MarkdownExport

/**
 * Export markdown layer: inline runs shared by the HTML and PDF writers, and
 * the block/HTML conversion.
 */
class MarkdownExportTest {

    // ------------------------------------------------------ inline runs ----

    @Test
    fun plainTextIsOneRun() {
        val runs = InlineMarkdown.parse("Just words")
        assertEquals(1, runs.size)
        assertEquals("Just words", runs[0].text)
    }

    @Test
    fun boldItalicStrikeCodeHighlight() {
        val runs = InlineMarkdown.parse("a **b** c *d* e ~~f~~ g `h` i ==j==")
        assertEquals("b", runs.first { it.bold }.text)
        assertEquals("d", runs.first { it.italic }.text)
        assertEquals("f", runs.first { it.strike }.text)
        assertEquals("h", runs.first { it.code }.text)
        assertEquals("j", runs.first { it.highlight }.text)
    }

    @Test
    fun boldCanContainItalic() {
        val runs = InlineMarkdown.parse("**bold *and italic* here**")
        val all = runs.joinToString("") { it.text }
        assertEquals("bold and italic here", all)
        assertTrue(runs.any { it.bold && !it.italic })
        assertTrue(runs.any { it.bold && it.italic })
    }

    @Test
    fun externalLinksAndWikiLinks() {
        val runs = InlineMarkdown.parse("see [site](https://example.com) and [[Other Note|alias]]")
        val link = runs.first { it.link == "https://example.com" }
        assertEquals("site", link.text)
        val wiki = runs.first { it.wiki }
        assertEquals("alias", wiki.text)
        assertEquals("Other Note", wiki.link)
    }

    @Test
    fun unterminatedMarkersStayLiteral() {
        assertEquals("**not bold", InlineMarkdown.plain("**not bold"))
        assertEquals("a [b", InlineMarkdown.plain("a [b"))
        assertEquals("a `b", InlineMarkdown.plain("a `b"))
    }

    @Test
    fun backslashEscapesADelimiter() {
        assertEquals("**literal**", InlineMarkdown.plain("\\*\\*literal\\*\\*"))
    }

    // ------------------------------------------------------------ html ----

    @Test
    fun headingsAndParagraphsBecomeTags() {
        val html = MarkdownExport.htmlBody("# Title\n\nBody text")
        assertTrue(html.contains("<h1>Title</h1>"))
        assertTrue(html.contains("<p>Body text</p>"))
    }

    @Test
    fun listsGroupConsecutively() {
        val html = MarkdownExport.htmlBody("- one\n- two\n\n1. first\n2. second")
        assertTrue(html.contains("<ul>\n<li>one</li>\n<li>two</li>\n</ul>"))
        assertTrue(html.contains("<ol>\n<li value=\"1\">first</li>\n<li value=\"2\">second</li>\n</ol>"))
    }

    @Test
    fun taskItemsRenderCheckboxes() {
        val html = MarkdownExport.htmlBody("- [x] done\n- [ ] open")
        assertTrue(html.contains("checked"))
        assertTrue(html.contains("class=\"task\""))
    }

    @Test
    fun fencedCodeKeepsMarkupLiteralAndEscaped() {
        val html = MarkdownExport.htmlBody("```\n<b>not bold</b>\n# not a heading\n```")
        assertTrue(html.contains("&lt;b&gt;not bold&lt;/b&gt;"))
        assertTrue(html.contains("# not a heading"))
        assertTrue(!html.contains("<h1>"))
    }

    @Test
    fun tableGetsHeaderAndBody() {
        val html = MarkdownExport.htmlBody("| A | B |\n| --- | --- |\n| 1 | 2 |")
        assertTrue(html.contains("<th>A</th>"))
        assertTrue(html.contains("<td>1</td>"))
    }

    @Test
    fun inlineMarkupInsideParagraph() {
        val html = MarkdownExport.htmlBody("a **bold** and [site](https://example.com)")
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<a href=\"https://example.com\">site</a>"))
    }

    @Test
    fun htmlInUserTextIsEscaped() {
        val html = MarkdownExport.htmlBody("5 < 6 & 7 > 2")
        assertTrue(html.contains("5 &lt; 6 &amp; 7 &gt; 2"))
        assertTrue(!html.contains("< 6"))
    }

    @Test
    fun wikiLinksBecomeChips() {
        val html = MarkdownExport.htmlBody("see [[Roadmap]]")
        assertTrue(html.contains("class=\"wiki\""))
        assertTrue(html.contains("title=\"Roadmap\""))
    }

    @Test
    fun localAttachmentsAreNamedNotLinked() {
        val html = MarkdownExport.htmlBody("![chart](img_123.png)")
        assertTrue(html.contains("class=\"attachment\""))
        assertTrue(!html.contains("<img"))
    }

    @Test
    fun documentIsSelfContainedAndTitled() {
        val doc = MarkdownExport.htmlDocument("My <Note>", "# Hi")
        assertTrue(doc.startsWith("<!DOCTYPE html>"))
        assertTrue(doc.contains("<title>My &lt;Note&gt;</title>"))
        assertTrue(doc.contains("<article>"))
        assertTrue(!doc.contains("<link "))
        assertTrue(!doc.contains("<script"))
    }

    @Test
    fun dividersAndQuotes() {
        val html = MarkdownExport.htmlBody("---\n> quoted")
        assertTrue(html.contains("<hr>"))
        assertTrue(html.contains("<blockquote><p>quoted</p></blockquote>"))
    }
}
