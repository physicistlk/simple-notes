package personal.notes.touch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import personal.notes.touch.util.PdfVectorImporter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure-JVM unit tests for `PDF-QOL-2` streaming PDF import (`copyStreamToFile`
 * and `importPages` batching, eager page-0-only thumbnail, progress ordering,
 * and cancellation rollback).
 */
class PdfVectorImporterTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private class FakePdfImportSink(private val startIndex: Int = 0) : PdfVectorImporter.PdfImportSink {
        val thumbnailCalls = mutableListOf<Int>()
        val batchCounts = mutableListOf<Int>()
        val committedIndices = mutableListOf<Int>()
        var totalPageCount: Int = startIndex
        var rollbackCalled: Boolean = false
        var rollbackFromIndex: Int = -1
        var thumbnailExists: Boolean = false

        override suspend fun nextPageIndex(): Int = startIndex

        override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean {
            thumbnailCalls.add(notePageIndex)
            thumbnailExists = true
            return true
        }

        override suspend fun insertPageBatch(
            startNotePageIndex: Int,
            pdfPageOffset: Int,
            batchCount: Int,
            reuseFirstPage: Boolean,
            newTotalPageCount: Int
        ) {
            batchCounts.add(batchCount)
            for (k in 0 until batchCount) {
                val pdfIdx = pdfPageOffset + k
                val noteIdx = if (reuseFirstPage) pdfIdx else startNotePageIndex + pdfIdx
                committedIndices.add(noteIdx)
            }
            totalPageCount = newTotalPageCount
        }

        override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
            rollbackCalled = true
            rollbackFromIndex = startNotePageIndex
            committedIndices.clear()
            thumbnailExists = false
            totalPageCount = startNotePageIndex
        }
    }

    @Test
    fun copyStreamToFile_writesMultiChunkStreamToDisk() {
        val payload = ByteArray(300 * 1024) { (it % 251).toByte() }
        val dest = File(tempFolder.root, "nested/output.pdf")

        val ok = PdfVectorImporter.copyStreamToFile(ByteArrayInputStream(payload), dest)

        assertTrue(ok)
        assertTrue(dest.exists())
        assertEquals(payload.size.toLong(), dest.length())
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun copyStreamToFile_emptyStream_returnsFalseAndLeavesNoFile() {
        val dest = File(tempFolder.root, "empty.pdf")

        val ok = PdfVectorImporter.copyStreamToFile(ByteArrayInputStream(ByteArray(0)), dest)

        assertFalse(ok)
        assertFalse(dest.exists())
    }

    @Test
    fun copyStreamToFile_cancelledMidStream_returnsFalseAndDeletesPartialFile() {
        val payload = ByteArray(256 * 1024) { 0x2A }
        val dest = File(tempFolder.root, "cancelled.pdf")
        val reads = AtomicInteger(0)
        val cancelled = AtomicBoolean(false)
        val chunkedStream = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < payload.size) payload[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= payload.size) return -1
                val step = minOf(len, 16 * 1024, payload.size - pos)
                System.arraycopy(payload, pos, b, off, step)
                pos += step
                if (reads.incrementAndGet() >= 2) {
                    cancelled.set(true)
                }
                return step
            }
        }

        val ok = PdfVectorImporter.copyStreamToFile(
            input = chunkedStream,
            destFile = dest,
            isCancelled = { cancelled.get() },
            bufferSize = 16 * 1024
        )

        assertFalse(ok)
        assertFalse(dest.exists())
    }

    @Test
    fun copyStreamToFile_ioException_returnsFalseAndDeletesPartialFile() {
        val dest = File(tempFolder.root, "broken.pdf")
        val failingStream = object : InputStream() {
            private var count = 0
            override fun read(): Int = throw IOException("simulated read failure")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (count == 0) {
                    count++
                    b[off] = 0x25
                    return 1
                }
                throw IOException("simulated mid-stream failure")
            }
        }

        val ok = PdfVectorImporter.copyStreamToFile(failingStream, dest)

        assertFalse(ok)
        assertFalse(dest.exists())
    }

    @Test
    fun importPages_reportsProgressInOrder_1ToN_andRendersOnlyPage0Thumbnail() = runTest {
        val sink = FakePdfImportSink(startIndex = 0)
        val progressEvents = mutableListOf<Pair<Int, Int>>()

        val outcome = PdfVectorImporter.importPages(
            pageCount = 45,
            reuseFirstPage = true,
            sink = sink,
            batchSize = 20,
            onProgress = { done, total -> progressEvents.add(done to total) }
        )

        assertFalse(outcome.cancelled)
        assertEquals(45, outcome.importedPages)
        assertEquals((1..45).map { it to 45 }, progressEvents)
        assertEquals(listOf(0), sink.thumbnailCalls)
        assertEquals(listOf(20, 20, 5), sink.batchCounts)
        assertEquals((0..44).toList(), sink.committedIndices)
        assertEquals(45, sink.totalPageCount)
    }

    @Test
    fun importPages_existingNote_startsAtNextPageIndex_andPreservesExistingPages() = runTest {
        val sink = FakePdfImportSink(startIndex = 5)
        val progressEvents = mutableListOf<Pair<Int, Int>>()

        val outcome = PdfVectorImporter.importPages(
            pageCount = 25,
            reuseFirstPage = false,
            sink = sink,
            batchSize = 20,
            onProgress = { done, total -> progressEvents.add(done to total) }
        )

        assertFalse(outcome.cancelled)
        assertEquals(25, outcome.importedPages)
        assertEquals(listOf(5), sink.thumbnailCalls)
        assertEquals(listOf(20, 5), sink.batchCounts)
        assertEquals((5..29).toList(), sink.committedIndices)
        assertEquals(30, sink.totalPageCount)
        assertEquals(25, progressEvents.size)
    }

    @Test
    fun importPages_cancelledMidImport_rollsBackPagesAndThumbnail() = runTest {
        val sink = FakePdfImportSink(startIndex = 3)
        val cancelFlag = AtomicBoolean(false)

        val outcome = PdfVectorImporter.importPages(
            pageCount = 50,
            reuseFirstPage = false,
            sink = sink,
            batchSize = 20,
            onProgress = { done, _ ->
                if (done >= 25) cancelFlag.set(true)
            },
            isCancelled = { cancelFlag.get() }
        )

        assertTrue(outcome.cancelled)
        assertEquals(0, outcome.importedPages)
        assertTrue(sink.rollbackCalled)
        assertEquals(3, sink.rollbackFromIndex)
        assertTrue(sink.committedIndices.isEmpty())
        assertFalse(sink.thumbnailExists)
        assertEquals(3, sink.totalPageCount)
    }

    @Test
    fun importPages_coroutineCancelled_rollsBackViaNonCancellable() = runTest {
        val sink = FakePdfImportSink(startIndex = 2)
        var caughtCancellation = false

        val job = launch {
            try {
                PdfVectorImporter.importPages(
                    pageCount = 100,
                    reuseFirstPage = false,
                    sink = sink,
                    batchSize = 20,
                    onProgress = { done, _ ->
                        if (done == 15) {
                            yield()
                        }
                    }
                )
            } catch (_: CancellationException) {
                caughtCancellation = true
            }
        }
        // Let it progress past page 15, then cancel the coroutine.
        while (sink.thumbnailCalls.isEmpty()) {
            yield()
        }
        job.cancel()
        job.join()

        assertTrue(caughtCancellation)
        assertTrue(sink.rollbackCalled)
        assertEquals(2, sink.rollbackFromIndex)
        assertTrue(sink.committedIndices.isEmpty())
    }

    @Test
    fun copyStreamToFile_preCancelled_preservesExistingDestFile() {
        val existingBytes = "original-pdf-content".toByteArray()
        val dest = File(tempFolder.root, "existing.pdf")
        dest.writeBytes(existingBytes)

        val ok = PdfVectorImporter.copyStreamToFile(
            input = ByteArrayInputStream("new-content".toByteArray()),
            destFile = dest,
            isCancelled = { true }
        )

        assertFalse(ok)
        assertTrue(dest.exists())
        assertArrayEquals(existingBytes, dest.readBytes())
    }

    @Test
    fun importPages_cancelledAtLastPage_rollsBackAllPages() = runTest {
        for (pageCount in listOf(40, 45)) {
            val sink = FakePdfImportSink(startIndex = 3)
            val cancelFlag = AtomicBoolean(false)

            val outcome = PdfVectorImporter.importPages(
                pageCount = pageCount,
                reuseFirstPage = false,
                sink = sink,
                batchSize = 20,
                onProgress = { done, total ->
                    if (done == total) cancelFlag.set(true)
                },
                isCancelled = { cancelFlag.get() }
            )

            assertTrue("pageCount=$pageCount should report cancelled", outcome.cancelled)
            assertEquals(0, outcome.importedPages)
            assertTrue("pageCount=$pageCount should roll back", sink.rollbackCalled)
            assertTrue("pageCount=$pageCount committedIndices must be empty", sink.committedIndices.isEmpty())
            assertFalse("pageCount=$pageCount thumb must be cleaned up", sink.thumbnailExists)
            assertEquals(3, sink.totalPageCount)
        }
    }

    @Test
    fun importPages_exceptionInSecondBatch_rollsBackFirstBatchAndThumbnail() = runTest {
        val baseSink = FakePdfImportSink(startIndex = 4)
        var batchCalls = 0
        val failingSink = object : PdfVectorImporter.PdfImportSink by baseSink {
            override suspend fun insertPageBatch(
                startNotePageIndex: Int,
                pdfPageOffset: Int,
                batchCount: Int,
                reuseFirstPage: Boolean,
                newTotalPageCount: Int
            ) {
                batchCalls++
                if (batchCalls == 2) {
                    throw RuntimeException("simulated disk full in batch 2")
                }
                baseSink.insertPageBatch(
                    startNotePageIndex,
                    pdfPageOffset,
                    batchCount,
                    reuseFirstPage,
                    newTotalPageCount
                )
            }
        }

        var caught: RuntimeException? = null
        try {
            PdfVectorImporter.importPages(
                pageCount = 45,
                reuseFirstPage = false,
                sink = failingSink,
                batchSize = 20
            )
        } catch (e: RuntimeException) {
            caught = e
        }

        assertEquals("simulated disk full in batch 2", caught?.message)
        assertTrue(baseSink.rollbackCalled)
        assertEquals(4, baseSink.rollbackFromIndex)
        assertTrue(baseSink.committedIndices.isEmpty())
        assertFalse(baseSink.thumbnailExists)
        assertEquals(4, baseSink.totalPageCount)
    }

    @Test
    fun importPages_exceptionInThumbnail_rollsBack() = runTest {
        val baseSink = FakePdfImportSink(startIndex = 1)
        val failingSink = object : PdfVectorImporter.PdfImportSink by baseSink {
            override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean {
                throw RuntimeException("simulated thumbnail write failure")
            }
        }

        var caught: RuntimeException? = null
        try {
            PdfVectorImporter.importPages(
                pageCount = 10,
                reuseFirstPage = false,
                sink = failingSink,
                batchSize = 20
            )
        } catch (e: RuntimeException) {
            caught = e
        }

        assertEquals("simulated thumbnail write failure", caught?.message)
        assertTrue(baseSink.rollbackCalled)
        assertEquals(1, baseSink.rollbackFromIndex)
        assertTrue(baseSink.committedIndices.isEmpty())
        assertFalse(baseSink.thumbnailExists)
    }
}
