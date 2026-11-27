package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.TocItem
import personal.notes.touch.util.OutlineNode
import personal.notes.touch.util.OutlineTreeNode
import personal.notes.touch.util.PdfOutlineImporter

/**
 * Pure-JVM unit tests for `PDF-QOL-1` (`PdfOutlineImporter`, `OutlineNode`,
 * `OutlineTreeNode`, `TocItem`).
 */
class PdfOutlineImporterTest {

    @Test
    fun importNodes_preservesOrderLevelsAndSetsPdfSource() {
        val nodes = listOf(
            OutlineNode(title = "Chapter 1", pageIndex = 0, level = 0),
            OutlineNode(title = "Section 1.1", pageIndex = 2, level = 1),
            OutlineNode(title = "Subsection 1.1.1", pageIndex = 5, level = 2),
            OutlineNode(title = "Chapter 2", pageIndex = 10, level = 0)
        )

        val result = PdfOutlineImporter.importNodes(nodes, pageCount = 20)

        assertEquals(4, result.size)
        assertEquals(listOf("Chapter 1", "Section 1.1", "Subsection 1.1.1", "Chapter 2"), result.map { it.title })
        assertEquals(listOf(0, 2, 5, 10), result.map { it.pageIndex })
        assertEquals(listOf(0, 1, 2, 0), result.map { it.level })
        assertTrue(result.all { it.source == PdfOutlineImporter.SOURCE_PDF })
    }

    @Test
    fun importNodes_filtersInvalidPageIndicesAndZeroPageCount() {
        val nodes = listOf(
            OutlineNode(title = "Negative", pageIndex = -1, level = 0),
            OutlineNode(title = "Valid First", pageIndex = 0, level = 0),
            OutlineNode(title = "Valid Last", pageIndex = 4, level = 1),
            OutlineNode(title = "Past End", pageIndex = 5, level = 0),
            OutlineNode(title = "Way Out", pageIndex = 999, level = 0)
        )

        val valid = PdfOutlineImporter.importNodes(nodes, pageCount = 5)
        assertEquals(listOf("Valid First", "Valid Last"), valid.map { it.title })
        assertEquals(listOf(0, 4), valid.map { it.pageIndex })

        assertTrue(PdfOutlineImporter.importNodes(nodes, pageCount = 0).isEmpty())
        assertTrue(PdfOutlineImporter.importNodes(nodes, pageCount = -3).isEmpty())
    }

    @Test
    fun importNodes_normalizesWhitespaceAndDropsBlankTitles() {
        val nodes = listOf(
            OutlineNode(title = "   \t \n  ", pageIndex = 0, level = 0),
            OutlineNode(title = "", pageIndex = 1, level = 0),
            OutlineNode(title = "  Chapter   1:\n  Introduction \t ", pageIndex = 2, level = 0)
        )

        val result = PdfOutlineImporter.importNodes(nodes, pageCount = 10)

        assertEquals(1, result.size)
        assertEquals("Chapter 1: Introduction", result[0].title)
        assertEquals(2, result[0].pageIndex)
    }

    @Test
    fun importNodes_clampsNegativeAndExcessiveLevels() {
        val nodes = listOf(
            OutlineNode(title = "Neg Level", pageIndex = 0, level = -4),
            OutlineNode(title = "Max Level", pageIndex = 1, level = 16),
            OutlineNode(title = "Too Deep", pageIndex = 2, level = 42)
        )

        val result = PdfOutlineImporter.importNodes(nodes, pageCount = 5)

        assertEquals(listOf(0, 16, PdfOutlineImporter.MAX_LEVEL), result.map { it.level })
    }

    @Test
    fun importNodes_capsAtMaxEntriesAfterFilteringInvalidNodes() {
        val nodes = buildList {
            repeat(50) { add(OutlineNode(title = "Bad $it", pageIndex = -1, level = 0)) }
            repeat(600) { i -> add(OutlineNode(title = "Item $i", pageIndex = i % 100, level = i % 3)) }
        }

        val result = PdfOutlineImporter.importNodes(nodes, pageCount = 100)

        assertEquals(PdfOutlineImporter.DEFAULT_MAX_ENTRIES, result.size)
        assertEquals("Item 0", result.first().title)
        assertEquals("Item 499", result.last().title)

        val customCap = PdfOutlineImporter.importNodes(nodes, pageCount = 100, maxEntries = 10)
        assertEquals(10, customCap.size)
    }

    @Test
    fun flattenTree_respectsMaxDepthAndMaxNodesInPreorder() {
        var chain = OutlineTreeNode(title = "Level 4", pageIndex = 4)
        chain = OutlineTreeNode(title = "Level 3", pageIndex = 3, children = listOf(chain))
        chain = OutlineTreeNode(title = "Level 2", pageIndex = 2, children = listOf(chain))
        chain = OutlineTreeNode(title = "Level 1", pageIndex = 1, children = listOf(chain))
        val root1 = OutlineTreeNode(
            title = "Root 1",
            pageIndex = -1,
            children = listOf(chain, OutlineTreeNode(title = "Sibling 1b", pageIndex = 5))
        )
        val root2 = OutlineTreeNode(title = "Root 2", pageIndex = 6)

        val depthLimited = PdfOutlineImporter.flattenTree(
            roots = listOf(root1, root2),
            maxDepth = 2,
            maxNodes = 500
        )
        assertEquals(
            listOf(
                OutlineNode("Root 1", -1, 0),
                OutlineNode("Level 1", 1, 1),
                OutlineNode("Level 2", 2, 2),
                OutlineNode("Sibling 1b", 5, 1),
                OutlineNode("Root 2", 6, 0)
            ),
            depthLimited
        )

        val imported = PdfOutlineImporter.importNodes(depthLimited, pageCount = 10)
        assertEquals(listOf("Level 1", "Level 2", "Sibling 1b", "Root 2"), imported.map { it.title })

        val nodeCapped = PdfOutlineImporter.flattenTree(
            roots = listOf(root1, root2),
            maxDepth = 16,
            maxNodes = 3
        )
        assertEquals(3, nodeCapped.size)
        assertEquals(listOf("Root 1", "Level 1", "Level 2"), nodeCapped.map { it.title })
    }

    @Test
    fun mergeWithUserEntries_preservesUserEntriesReplacesPdfAndSortsStably() {
        val existing = listOf(
            TocItem(id = "u1", title = "My Note P2", pageIndex = 2, level = 0, source = "user"),
            TocItem(id = "old_pdf", title = "Stale PDF Chapter", pageIndex = 0, level = 0, source = "pdf"),
            TocItem(id = "u2", title = "My Note P5", pageIndex = 5, level = 1, source = "user")
        )
        val newPdf = listOf(
            TocItem(id = "p0", title = "Intro", pageIndex = 0, level = 0, source = "pdf"),
            TocItem(id = "p2", title = "PDF Section at P2", pageIndex = 2, level = 1, source = "pdf"),
            TocItem(id = "p8", title = "Appendix", pageIndex = 8, level = 0, source = "pdf")
        )

        val merged = PdfOutlineImporter.mergeWithUserEntries(existing, newPdf)

        assertEquals(listOf("p0", "u1", "p2", "u2", "p8"), merged.map { it.id })
        assertTrue(merged.none { it.id == "old_pdf" })
    }

    @Test
    fun activeEntryId_matchesExactBetweenChaptersAndBeforeFirstChapter() {
        val entries = listOf(
            TocItem(id = "c1", title = "Chapter 1", pageIndex = 2),
            TocItem(id = "c2", title = "Chapter 2", pageIndex = 6),
            TocItem(id = "c2a", title = "Section 2.1", pageIndex = 6),
            TocItem(id = "c3", title = "Chapter 3", pageIndex = 12)
        )

        assertNull(PdfOutlineImporter.activeEntryId(emptyList(), 0))
        assertNull(PdfOutlineImporter.activeEntryId(entries, 0))
        assertNull(PdfOutlineImporter.activeEntryId(entries, 1))
        assertEquals("c1", PdfOutlineImporter.activeEntryId(entries, 2))
        assertEquals("c1", PdfOutlineImporter.activeEntryId(entries, 5))
        // Tie at page 6 selects the last entry at that page
        assertEquals("c2a", PdfOutlineImporter.activeEntryId(entries, 6))
        assertEquals("c2a", PdfOutlineImporter.activeEntryId(entries, 11))
        assertEquals("c3", PdfOutlineImporter.activeEntryId(entries, 12))
        assertEquals("c3", PdfOutlineImporter.activeEntryId(entries, 99))
    }
}
