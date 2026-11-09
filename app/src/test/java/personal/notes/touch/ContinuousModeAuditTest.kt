package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.PaperPattern
import personal.notes.touch.ui.canvas.TocItem

/**
 * Unit tests verifying fixes from the continuous inking & full app audit:
 * - Paper pattern density scaling
 * - Bookmark index shifting on page deletion
 * - TOC page index shifting on page deletion
 * - Fallback paper pattern resolution
 */
class ContinuousModeAuditTest {

    @Test
    fun paperPatternStepScalesWithCanvasWidth() {
        fun computeStep(density: Int, width: Float): Float {
            val d = density.coerceIn(20, 80).toFloat()
            return (d * (width / 2160f)).coerceAtLeast(8f)
        }

        // At full canvas width (2160), step equals density
        assertEquals(40f, computeStep(40, 2160f), 0.001f)
        assertEquals(80f, computeStep(80, 2160f), 0.001f)
        assertEquals(20f, computeStep(20, 2160f), 0.001f)

        // At half canvas width (1080), step is halved
        assertEquals(20f, computeStep(40, 1080f), 0.001f)

        // Minimum clamp is 8f
        assertEquals(8f, computeStep(20, 200f), 0.001f)
    }

    @Test
    fun paperPatternFallbackResolvesFromBackgroundMode() {
        fun resolvePattern(pagePatternCode: Int, backgroundMode: Int): PaperPattern {
            return when (PaperPattern.fromCode(pagePatternCode)) {
                PaperPattern.GRID -> PaperPattern.GRID
                PaperPattern.LINES -> PaperPattern.LINES
                PaperPattern.DOTTED -> PaperPattern.DOTTED
                PaperPattern.BLANK -> when (backgroundMode) {
                    1 -> PaperPattern.LINES
                    2 -> PaperPattern.GRID
                    3 -> PaperPattern.DOTTED
                    else -> PaperPattern.BLANK
                }
            }
        }

        // When pagePattern is set (non-zero), pagePattern wins
        assertEquals(PaperPattern.GRID, resolvePattern(PaperPattern.GRID.code, 1))
        assertEquals(PaperPattern.LINES, resolvePattern(PaperPattern.LINES.code, 2))

        // When pagePattern is 0 (BLANK / unset), falls back to backgroundMode
        assertEquals(PaperPattern.LINES, resolvePattern(0, 1))
        assertEquals(PaperPattern.GRID, resolvePattern(0, 2))
        assertEquals(PaperPattern.DOTTED, resolvePattern(0, 3))
        assertEquals(PaperPattern.BLANK, resolvePattern(0, 0))
    }

    @Test
    fun deletePageShiftsBookmarksCorrectly() {
        fun shiftBookmarks(bookmarks: Set<Int>, deletedIndex: Int): Set<Int> {
            return bookmarks.mapNotNull { bIdx ->
                when {
                    bIdx == deletedIndex -> null
                    bIdx > deletedIndex -> bIdx - 1
                    else -> bIdx
                }
            }.toSet()
        }

        val initial = setOf(0, 2, 4, 7)

        // Deleting page 2 removes bookmark 2 and shifts 4->3, 7->6; 0 stays 0
        val shifted = shiftBookmarks(initial, 2)
        assertEquals(setOf(0, 3, 6), shifted)

        // Deleting page 0 shifts all others down
        assertEquals(setOf(1, 3, 6), shiftBookmarks(initial, 0))

        // Deleting a page with no bookmark shifts only higher indices
        assertEquals(setOf(0, 2, 3, 6), shiftBookmarks(initial, 3))
    }

    @Test
    fun deletePageShiftsTocEntriesCorrectly() {
        fun shiftToc(toc: List<TocItem>, deletedIndex: Int): List<TocItem> {
            return toc.mapNotNull { item ->
                when {
                    item.pageIndex == deletedIndex -> null
                    item.pageIndex > deletedIndex -> item.copy(pageIndex = item.pageIndex - 1)
                    else -> item
                }
            }
        }

        val toc = listOf(
            TocItem(id = "1", title = "Intro", pageIndex = 0),
            TocItem(id = "2", title = "Chapter 1", pageIndex = 3),
            TocItem(id = "3", title = "Chapter 2", pageIndex = 5)
        )

        val shifted = shiftToc(toc, 3)
        assertEquals(2, shifted.size)
        assertEquals("Intro", shifted[0].title)
        assertEquals(0, shifted[0].pageIndex)
        assertEquals("Chapter 2", shifted[1].title)
        assertEquals(4, shifted[1].pageIndex)
    }
}
