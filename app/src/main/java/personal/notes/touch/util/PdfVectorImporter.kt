package personal.notes.touch.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Vector PDF import (defect F-10, upgraded by PDF-QOL-2).
 *
 * The imported PDF is streamed directly to disk and opened via native file-path
 * I/O (`FPDF_LoadDocument`) so large (100+ MB / 100+ page) PDFs never load
 * their bytes into a `ByteArray` on the Dalvik heap.
 *
 * Only page 0 renders a lightweight preview thumbnail (`page_0.png`) eagerly;
 * remaining pages render crisply on demand from pdfium.
 */
object PdfVectorImporter {

    /**
     * An opened source document backed by an on-disk file.
     * Closing deletes [sourceFile] when [isTempFile] is true.
     */
    data class ImportedPdf(
        val document: PdfiumDocument,
        val sourceFile: File,
        val pageCount: Int,
        val isTempFile: Boolean = true
    ) : Closeable {
        override fun close() {
            document.close()
            if (isTempFile && sourceFile.exists()) {
                sourceFile.delete()
            }
        }
    }

    /** Storage & thumbnail sink for [importPages] (keeps the page loop pure-JVM testable). */
    interface PdfImportSink {
        suspend fun nextPageIndex(): Int
        suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean
        suspend fun insertPageBatch(
            startNotePageIndex: Int,
            pdfPageOffset: Int,
            batchCount: Int,
            reuseFirstPage: Boolean,
            newTotalPageCount: Int
        )
        suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean)
    }

    data class PdfImportOutcome(
        val importedPages: Int,
        val cancelled: Boolean,
        val startNotePageIndex: Int = 0
    )

    data class ImportNoteResult(
        val firstPageBitmap: Bitmap?,
        val importedPages: Int,
        val cancelled: Boolean
    )

    /**
     * Pure-JVM chunked stream copy to [destFile]. Checks [isCancelled] before
     * and after each read, deleting [destFile] on cancellation, empty stream,
     * or I/O error.
     */
    fun copyStreamToFile(
        input: InputStream,
        destFile: File,
        isCancelled: () -> Boolean = { false },
        bufferSize: Int = 64 * 1024
    ): Boolean {
        if (isCancelled()) return false
        return try {
            destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val buf = ByteArray(bufferSize.coerceAtLeast(1024))
            var totalBytes = 0L
            var cancelled = false
            FileOutputStream(destFile).use { out ->
                while (true) {
                    if (isCancelled()) {
                        cancelled = true
                        break
                    }
                    val read = input.read(buf)
                    if (read < 0) break
                    if (isCancelled()) {
                        cancelled = true
                        break
                    }
                    if (read > 0) {
                        out.write(buf, 0, read)
                        totalBytes += read
                    }
                }
                if (!cancelled) {
                    out.flush()
                }
            }
            if (cancelled || totalBytes <= 0L) {
                destFile.delete()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            destFile.delete()
            false
        }
    }

    /** Streams [uri] directly into [destFile] without loading a heap `ByteArray`. */
    fun streamToFile(
        context: Context,
        uri: Uri,
        destFile: File,
        isCancelled: () -> Boolean = { false }
    ): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copyStreamToFile(input, destFile, isCancelled)
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            destFile.delete()
            false
        }
    }

    /**
     * Streams the picked PDF to [destFile] (or a temp file in `cacheDir` when
     * `destFile == null`) and opens it via native `FPDF_LoadDocument`.
     */
    fun readDocument(
        context: Context,
        uri: Uri,
        destFile: File? = null,
        isCancelled: () -> Boolean = { false }
    ): ImportedPdf? {
        val isTemp = destFile == null
        val targetFile = destFile ?: File(context.cacheDir, "pdf_import_${UUID.randomUUID()}.pdf")
        if (!streamToFile(context, uri, targetFile, isCancelled) || isCancelled()) {
            targetFile.delete()
            return null
        }
        val document = PdfiumDocument()
        return try {
            if (!document.open(targetFile) || document.pageCount <= 0 || isCancelled()) {
                document.close()
                targetFile.delete()
                null
            } else {
                ImportedPdf(document, targetFile, document.pageCount, isTemp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            targetFile.delete()
            null
        }
    }

    /**
     * Moves or persists [sourceFile] into [noteDir] (`note/<noteId>/pdf_<ts>.pdf`).
     * Uses O(1) `renameTo` when on the same filesystem, falling back to a chunked
     * stream copy + delete.
     */
    fun storeSourceFile(sourceFile: File, noteDir: File, isTempFile: Boolean): File? {
        if (!sourceFile.isFile) return null
        if (!noteDir.exists()) noteDir.mkdirs()
        if (!isTempFile && sourceFile.parentFile == noteDir) {
            return sourceFile
        }
        var dest = File(noteDir, "pdf_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.pdf")
        while (dest.exists()) {
            dest = File(noteDir, "pdf_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.pdf")
        }
        if (sourceFile.renameTo(dest)) {
            return dest
        }
        val copied = try {
            FileInputStream(sourceFile).use { input ->
                copyStreamToFile(input, dest)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        if (copied) {
            sourceFile.delete()
            return dest
        }
        dest.delete()
        return null
    }

    /** Resolves a `pages.pdfSourcePath` value back to a readable file. */
    fun resolveSource(context: Context, relativePath: String): File = File(context.filesDir, relativePath)

    /**
     * Pure-JVM batching & progress orchestrator for PDF page import.
     * Renders only page 0's thumbnail eagerly, flushes page batches every
     * [batchSize] pages, reports `(1, N)..(N, N)` progress, and rolls back via
     * [PdfImportSink.rollback] inside `NonCancellable` on cancellation.
     */
    suspend fun importPages(
        pageCount: Int,
        reuseFirstPage: Boolean,
        sink: PdfImportSink,
        batchSize: Int = 20,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): PdfImportOutcome {
        if (isCancelled() || !currentCoroutineContext().isActive) {
            return PdfImportOutcome(importedPages = 0, cancelled = true)
        }
        if (pageCount <= 0) return PdfImportOutcome(importedPages = 0, cancelled = false)
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val startIndex = if (reuseFirstPage) 0 else sink.nextPageIndex()
        if (isCancelled() || !currentCoroutineContext().isActive) {
            withContext(NonCancellable) { sink.rollback(startIndex, reuseFirstPage) }
            return PdfImportOutcome(importedPages = 0, cancelled = true)
        }
        var batchStartOffset = 0
        var batchCount = 0
        try {
            for (i in 0 until pageCount) {
                if (isCancelled() || !currentCoroutineContext().isActive) {
                    withContext(NonCancellable) { sink.rollback(startIndex, reuseFirstPage) }
                    return PdfImportOutcome(importedPages = 0, cancelled = true)
                }
                if (i == 0) {
                    val thumbPageIdx = if (reuseFirstPage) 0 else startIndex
                    if (!sink.renderAndSavePage0Thumbnail(thumbPageIdx)) {
                        withContext(NonCancellable) { sink.rollback(startIndex, reuseFirstPage) }
                        return PdfImportOutcome(importedPages = 0, cancelled = false, startNotePageIndex = startIndex)
                    }
                }
                batchCount++
                val isLast = (i == pageCount - 1)
                if (batchCount >= safeBatchSize || isLast) {
                    val newTotal = if (reuseFirstPage) i + 1 else startIndex + i + 1
                    sink.insertPageBatch(
                        startNotePageIndex = startIndex,
                        pdfPageOffset = batchStartOffset,
                        batchCount = batchCount,
                        reuseFirstPage = reuseFirstPage,
                        newTotalPageCount = newTotal
                    )
                    batchStartOffset = i + 1
                    batchCount = 0
                }
                onProgress(i + 1, pageCount)
            }
            if (isCancelled() || !currentCoroutineContext().isActive) {
                withContext(NonCancellable) { sink.rollback(startIndex, reuseFirstPage) }
                return PdfImportOutcome(importedPages = 0, cancelled = true)
            }
            return PdfImportOutcome(
                importedPages = pageCount,
                cancelled = false,
                startNotePageIndex = startIndex
            )
        } catch (t: Throwable) {
            withContext(NonCancellable) { sink.rollback(startIndex, reuseFirstPage) }
            throw t
        }
    }

    /**
     * Streams/stores the PDF into the note, inserts batched `PageEntity` rows,
     * renders page 0's aspect-correct thumbnail only, and cleans up on cancel.
     */
    suspend fun importIntoNote(
        @Suppress("UNUSED_PARAMETER") context: Context,
        imported: ImportedPdf,
        noteId: String,
        repository: NoteRepository,
        fileStore: NoteFileStore,
        targetWidth: Int,
        targetHeight: Int,
        reuseFirstPage: Boolean,
        onPageError: suspend (String) -> Unit = {},
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): ImportNoteResult {
        if (isCancelled()) {
            if (imported.isTempFile) {
                imported.close()
            }
            if (reuseFirstPage) {
                repository.permanentlyDeleteNote(noteId)
            } else if (!imported.isTempFile && imported.sourceFile.exists()) {
                imported.sourceFile.delete()
            }
            return ImportNoteResult(firstPageBitmap = null, importedPages = 0, cancelled = true)
        }
        if (imported.pageCount <= 0) {
            if (imported.isTempFile) {
                imported.close()
            }
            if (reuseFirstPage) {
                repository.permanentlyDeleteNote(noteId)
            } else if (imported.sourceFile.exists()) {
                imported.sourceFile.delete()
            }
            return ImportNoteResult(firstPageBitmap = null, importedPages = 0, cancelled = false)
        }

        val noteDir = fileStore.getNoteDir(noteId)
        if (imported.isTempFile) {
            imported.document.close()
        }
        val storedFile = storeSourceFile(imported.sourceFile, noteDir, imported.isTempFile)
        if (storedFile == null) {
            if (imported.isTempFile && imported.sourceFile.exists()) {
                imported.sourceFile.delete()
            }
            onPageError("PDF import error: could not store PDF file")
            if (reuseFirstPage) {
                repository.permanentlyDeleteNote(noteId)
            }
            return ImportNoteResult(firstPageBitmap = null, importedPages = 0, cancelled = false)
        }

        val sourcePath = "note/$noteId/${storedFile.name}"
        val openedForRender = if (imported.isTempFile) PdfiumDocument.open(storedFile) else imported.document
        if (openedForRender == null) {
            onPageError("Failed to render page 1 preview")
            if (reuseFirstPage) {
                repository.permanentlyDeleteNote(noteId)
            } else if (storedFile.exists()) {
                storedFile.delete()
            }
            return ImportNoteResult(firstPageBitmap = null, importedPages = 0, cancelled = false)
        }
        var firstPage: Bitmap? = null

        try {
            val sink = object : PdfImportSink {
                override suspend fun nextPageIndex(): Int = repository.getNextPageIndex(noteId)

                override suspend fun renderAndSavePage0Thumbnail(notePageIndex: Int): Boolean {
                    val doc = openedForRender
                    val size = doc.pageSize(0)
                    val aspect = PdfPageGeometry.aspect(size[0], size[1])
                    val longSide = minOf(maxOf(targetWidth, targetHeight), 1020)
                    val (thumbW, thumbH) = PdfPageGeometry.thumbnailSize(aspect, longSide)
                    val thumb = try {
                        doc.renderPage(0, thumbW, thumbH)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        null
                    }
                    if (thumb == null) {
                        onPageError("Failed to render page 1 preview")
                        return false
                    }
                    val written = fileStore.writePageFileAtomically(noteId, notePageIndex) { tmp ->
                        FileOutputStream(tmp).use { out ->
                            thumb.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                    }
                    if (!written) {
                        if (!thumb.isRecycled) thumb.recycle()
                        onPageError("Failed to save page 1 preview")
                        return false
                    }
                    firstPage = thumb
                    return true
                }

                override suspend fun insertPageBatch(
                    startNotePageIndex: Int,
                    pdfPageOffset: Int,
                    batchCount: Int,
                    reuseFirstPage: Boolean,
                    newTotalPageCount: Int
                ) {
                    repository.appendPdfPagesBatch(
                        noteId = noteId,
                        startNotePageIndex = startNotePageIndex,
                        pdfSourcePath = sourcePath,
                        pdfPageOffset = pdfPageOffset,
                        batchCount = batchCount,
                        reuseFirstPage = reuseFirstPage,
                        newTotalPageCount = newTotalPageCount
                    )
                }

                override suspend fun rollback(startNotePageIndex: Int, reuseFirstPage: Boolean) {
                    firstPage?.let { if (!it.isRecycled) it.recycle() }
                    firstPage = null
                    if (reuseFirstPage) {
                        repository.permanentlyDeleteNote(noteId)
                    } else {
                        repository.rollbackAppendedPdfPages(noteId, startNotePageIndex)
                        if (storedFile.exists()) {
                            storedFile.delete()
                        }
                    }
                }
            }

            val outcome = importPages(
                pageCount = imported.pageCount,
                reuseFirstPage = reuseFirstPage,
                sink = sink,
                batchSize = 20,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
            if (!outcome.cancelled && outcome.importedPages > 0) {
                try {
                    val rawOutline = openedForRender.outline()
                    val importedToc = PdfOutlineImporter
                        .importNodes(rawOutline, imported.pageCount)
                        .map { item ->
                            if (reuseFirstPage) item
                            else item.copy(pageIndex = item.pageIndex + outcome.startNotePageIndex)
                        }
                    val mergedToc = if (reuseFirstPage) {
                        importedToc
                    } else {
                        val existing = fileStore.readCatalogToc(noteId)
                        val preserved = existing.filter {
                            it.source != "pdf" || it.pageIndex < outcome.startNotePageIndex
                        }
                        (preserved + importedToc).sortedBy { it.pageIndex }
                    }
                    fileStore.writeCatalogToc(noteId, mergedToc)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return ImportNoteResult(
                firstPageBitmap = if (outcome.cancelled) null else firstPage,
                importedPages = outcome.importedPages,
                cancelled = outcome.cancelled
            )
        } finally {
            if (imported.isTempFile) {
                openedForRender.close()
            }
        }
    }
}
