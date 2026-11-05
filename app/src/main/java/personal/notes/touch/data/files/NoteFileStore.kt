package personal.notes.touch.data.files

import android.content.Context
import java.io.File

class NoteFileStore(private val context: Context) {
    private val notesDir: File
        get() = File(context.filesDir, "note").apply { if (!exists()) mkdirs() }

    fun getNoteDir(noteId: String): File {
        return File(notesDir, noteId).apply { if (!exists()) mkdirs() }
    }

    fun getPageFile(noteId: String, pageIndex: Int): File {
        return File(getNoteDir(noteId), "page_${pageIndex}.png")
    }

    /**
     * F-10: stroke layer of a vector-PDF page. The `page_<index>.png` file of
     * such a page stays the pdfium-rendered preview (library thumbnails); live
     * annotations are persisted separately here so the vector background can
     * be re-rendered from the source at any zoom without baking a raster.
     */
    fun getStrokesFile(noteId: String, pageIndex: Int): File {
        return File(getNoteDir(noteId), "strokes_${pageIndex}.png")
    }

    /**
     * Atomic per-page write: the bitmap is written to a temp file inside the
     * note's temp dir (created on demand), then atomically renamed to
     * page_<index>.png. A crash mid-write never leaves a truncated
     * page file in place. Kotlin-only; no native libs.
     *
     * @return true if the final page file is in place.
     */
    fun writePageFileAtomically(noteId: String, pageIndex: Int, writeBlock: (File) -> Unit): Boolean {
        return try {
            val tmpDir = getTmpDir(noteId)
            val tempFile = File(tmpDir, "temp_page_${pageIndex}.png")
            val targetFile = getPageFile(noteId, pageIndex)
            writeBlock(tempFile)
            if (!tempFile.exists()) return false
            if (targetFile.exists() && !targetFile.delete()) {
                // Fall back to rename-only if delete is not permitted.
            }
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Temp scratch dir for atomic per-page saves (one per note). */
    fun getTmpDir(noteId: String): File {
        return File(getNoteDir(noteId), "tmp").apply { if (!exists()) mkdirs() }
    }

    /** Temp file used by the atomic page writer. */
    fun getTempPageFile(noteId: String, pageIndex: Int): File {
        return File(getTmpDir(noteId), "temp_page_${pageIndex}.png")
    }

    fun deletePageFile(noteId: String, pageIndex: Int): Boolean {
        val file = getPageFile(noteId, pageIndex)
        val strokes = getStrokesFile(noteId, pageIndex)
        if (strokes.exists()) strokes.delete()
        return if (file.exists()) file.delete() else true
    }

    fun renamePageFile(noteId: String, oldIndex: Int, newIndex: Int): Boolean {
        val oldStrokes = getStrokesFile(noteId, oldIndex)
        val newStrokes = getStrokesFile(noteId, newIndex)
        if (oldStrokes.exists()) {
            if (newStrokes.exists()) newStrokes.delete()
            oldStrokes.renameTo(newStrokes)
        }
        val oldFile = getPageFile(noteId, oldIndex)
        val newFile = getPageFile(noteId, newIndex)
        if (oldFile.exists()) {
            if (newFile.exists()) newFile.delete()
            return oldFile.renameTo(newFile)
        }
        return false
    }

    fun getLayersDir(noteId: String): File = File(getNoteDir(noteId), "layers").apply { if (!exists()) mkdirs() }

    fun getLayerInfoFile(noteId: String): File = File(getLayersDir(noteId), "layers.json")

    fun deleteNoteFiles(noteId: String): Boolean {
        val dir = File(notesDir, noteId)
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    // JSON Index files (M8 persistence)
    fun getPathInfoFile(): File {
        val pathInfoDir = File(notesDir, "pathinfo").apply { if (!exists()) mkdirs() }
        return File(pathInfoDir, "pathinfo.json")
    }

    fun savePathInfoJson(json: String) {
        val file = getPathInfoFile()
        file.writeText(json)
    }

    fun readPathInfoJson(): String? {
        val file = getPathInfoFile()
        return if (file.exists()) file.readText() else null
    }

    fun getUserSettingsFile(): File {
        return File(notesDir, "user.json")
    }

    fun saveUserSettingsJson(json: String) {
        val file = getUserSettingsFile()
        file.writeText(json)
    }

    fun readUserSettingsJson(): String? {
        val file = getUserSettingsFile()
        return if (file.exists()) file.readText() else null
    }
}
