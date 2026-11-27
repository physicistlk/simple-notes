package personal.notes.touch

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.e2e.E2eTestHarness
import personal.notes.touch.ui.canvas.CanvasViewModel
import personal.notes.touch.ui.canvas.PaperPattern
import personal.notes.touch.ui.canvas.TocItem

/**
 * Unit tests verifying fixes from the continuous inking & full app audit
 * against genuine production APIs (`PaperPattern`, `CanvasViewModel`):
 * - Paper pattern density scaling
 * - Fallback paper pattern resolution
 * - Bookmark index shifting on page deletion
 * - TOC page index shifting on page deletion
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousModeAuditTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun awaitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            while (!predicate() && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    @Test
    fun paperPatternStepScalesWithCanvasWidth() {
        // At full canvas width (2160), step equals clamped density
        assertEquals(40f, PaperPattern.stepPx(40, 2160f), 0.001f)
        assertEquals(80f, PaperPattern.stepPx(80, 2160f), 0.001f)
        assertEquals(20f, PaperPattern.stepPx(20, 2160f), 0.001f)
        assertEquals(20f, PaperPattern.stepPx(5, 2160f), 0.001f)
        assertEquals(80f, PaperPattern.stepPx(150, 2160f), 0.001f)

        // At half canvas width (1080), step is halved
        assertEquals(20f, PaperPattern.stepPx(40, 1080f), 0.001f)

        // Minimum clamp is 8f (including zero/negative/NaN width)
        assertEquals(8f, PaperPattern.stepPx(20, 200f), 0.001f)
        assertEquals(8f, PaperPattern.stepPx(40, 0f), 0.001f)
        assertEquals(8f, PaperPattern.stepPx(40, Float.NaN), 0.001f)
    }

    @Test
    fun paperPatternFallbackResolvesFromBackgroundMode() {
        // When pagePattern is set (non-zero), pagePattern wins
        assertEquals(PaperPattern.GRID, PaperPattern.resolve(PaperPattern.GRID.code, 1))
        assertEquals(PaperPattern.LINES, PaperPattern.resolve(PaperPattern.LINES.code, 2))
        assertEquals(PaperPattern.DOTTED, PaperPattern.resolve(PaperPattern.DOTTED.code, 1))

        // When pagePattern is 0 (BLANK / unset), falls back to backgroundMode
        assertEquals(PaperPattern.LINES, PaperPattern.resolve(0, 1))
        assertEquals(PaperPattern.GRID, PaperPattern.resolve(0, 2))
        assertEquals(PaperPattern.DOTTED, PaperPattern.resolve(0, 3))
        assertEquals(PaperPattern.BLANK, PaperPattern.resolve(0, 0))
    }

    @Test
    fun deletePageShiftsBookmarksCorrectly() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("audit_bm"))
        val noteId = "audit-bm-note"
        val note = NoteEntity(id = noteId, title = "Bookmarks", pageCount = 8, pageType = "pdf")
        val pages = (0 until 8).map { i ->
            PageEntity(id = "$noteId-p$i", noteId = noteId, index = i, bitmapPath = "page_$i.png")
        }
        env.seedNoteAndPages(note, pages)
        env.fileStore.writeCatalogToc(
            noteId,
            listOf(TocItem(id = "seed", title = "Seed", pageIndex = 0))
        )
        val store = ViewModelStore()
        val vm = CanvasViewModel(noteId, env.repository, env.fileStore)
        store.put("vm", vm)
        val noteCollector = backgroundScope.launch(testDispatcher) { vm.note.collect {} }
        val pagesCollector = backgroundScope.launch(testDispatcher) { vm.pages.collect {} }

        try {
            // Wait for initial loadDrawerData to publish _uiState before toggling bookmarks
            awaitUntil { vm.uiState.value.tocEntries.isNotEmpty() }

            for (idx in listOf(0, 2, 4, 7)) {
                vm.toggleBookmark(idx)
            }
            assertEquals(setOf(0, 2, 4, 7), vm.uiState.value.bookmarkedPages)

            val done1 = CompletableDeferred<Unit>()
            vm.deletePage(2, isContinuous = true) { done1.complete(Unit) }
            done1.await()
            assertEquals(setOf(0, 3, 6), vm.uiState.value.bookmarkedPages)

            val done2 = CompletableDeferred<Unit>()
            vm.deletePage(0, isContinuous = true) { done2.complete(Unit) }
            done2.await()
            assertEquals(setOf(2, 5), vm.uiState.value.bookmarkedPages)
        } finally {
            noteCollector.cancel()
            pagesCollector.cancel()
            store.clear()
        }
    }

    @Test
    fun deletePageShiftsTocEntriesCorrectly() = runTest {
        val env = E2eTestHarness.createInMemoryNoteEnv(tempFolder.newFolder("audit_toc"))
        val noteId = "audit-toc-note"
        val note = NoteEntity(id = noteId, title = "TOC", pageCount = 6, pageType = "pdf")
        val pages = (0 until 6).map { i ->
            PageEntity(id = "$noteId-p$i", noteId = noteId, index = i, bitmapPath = "page_$i.png")
        }
        env.seedNoteAndPages(note, pages)
        env.fileStore.writeCatalogToc(
            noteId,
            listOf(
                TocItem(id = "1", title = "Intro", pageIndex = 0),
                TocItem(id = "2", title = "Chapter 1", pageIndex = 3),
                TocItem(id = "3", title = "Chapter 2", pageIndex = 5)
            )
        )
        val store = ViewModelStore()
        val vm = CanvasViewModel(noteId, env.repository, env.fileStore)
        store.put("vm", vm)
        val noteCollector = backgroundScope.launch(testDispatcher) { vm.note.collect {} }
        val pagesCollector = backgroundScope.launch(testDispatcher) { vm.pages.collect {} }

        try {
            awaitUntil { vm.uiState.value.tocEntries.size == 3 }

            val done = CompletableDeferred<Unit>()
            vm.deletePage(3, isContinuous = true) { done.complete(Unit) }
            done.await()

            val shifted = vm.uiState.value.tocEntries
            assertEquals(2, shifted.size)
            assertEquals("Intro", shifted[0].title)
            assertEquals(0, shifted[0].pageIndex)
            assertEquals("Chapter 2", shifted[1].title)
            assertEquals(4, shifted[1].pageIndex)
        } finally {
            noteCollector.cancel()
            pagesCollector.cancel()
            store.clear()
        }
    }
}
