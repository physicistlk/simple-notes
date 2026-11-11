package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.ui.canvas.PageRestore

/**
 * "Reopen on the page you left" (schema 13): the clamp between the saved
 * `notes.lastPageIndex` and the pages that still exist.
 *
 * The function under test is production code ([PageRestore.restoredPageIndex]),
 * not a copy: it is the only rule that can strand a reader on an index that no
 * longer exists, so it is pinned here against a Room/Compose stack.
 */
class LastPageRestoreTest {

    @Test
    fun normal_case_keeps_the_saved_page() {
        assertEquals(2, PageRestore.restoredPageIndex(2, 4))
        assertEquals(0, PageRestore.restoredPageIndex(0, 1))
        assertEquals(9, PageRestore.restoredPageIndex(9, 10))
    }

    @Test
    fun saved_page_past_the_end_falls_back_to_the_last_page() {
        assertEquals(2, PageRestore.restoredPageIndex(7, 3))
        assertEquals(0, PageRestore.restoredPageIndex(5, 1))
    }

    @Test
    fun negative_saved_page_falls_back_to_the_first_page() {
        assertEquals(0, PageRestore.restoredPageIndex(-1, 5))
        assertEquals(0, PageRestore.restoredPageIndex(Int.MIN_VALUE, 5))
    }

    @Test
    fun no_pages_never_crashes_or_goes_negative() {
        assertEquals(0, PageRestore.restoredPageIndex(4, 0))
        assertEquals(0, PageRestore.restoredPageIndex(0, 0))
        assertEquals(0, PageRestore.restoredPageIndex(-3, 0))
        assertEquals(0, PageRestore.restoredPageIndex(Int.MAX_VALUE, 0))
    }

    @Test
    fun result_is_always_a_valid_index_for_the_page_count() {
        val counts = listOf(0, 1, 2, 5, 17)
        val savedValues = listOf(Int.MIN_VALUE, -2, 0, 1, 4, 16, 99, Int.MAX_VALUE)
        for (count in counts) {
            for (saved in savedValues) {
                val restored = PageRestore.restoredPageIndex(saved, count)
                val lastPage = (count - 1).coerceAtLeast(0)
                assertEquals(
                    "restored index out of range for saved=$saved pageCount=$count",
                    true,
                    restored in 0..lastPage
                )
            }
        }
    }
}
