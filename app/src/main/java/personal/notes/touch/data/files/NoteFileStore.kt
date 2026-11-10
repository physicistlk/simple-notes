package personal.notes.touch.data.files

import android.content.Context
import android.net.Uri
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
     * Copies a picked image into the note's own directory and returns its
     * file name (what the IMAGE block stores as `![alt](name)`). Returns null
     * for unreadable/empty sources. No permission is needed: the picker hands
     * over a read-grant URI.
     */
    fun saveNoteImage(noteId: String, source: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val extension = when (resolver.getType(source)?.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
            val fileName = "img_${System.currentTimeMillis()}.$extension"
            val target = File(getNoteDir(noteId), fileName)
            val copied = resolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || target.length() <= 0L) {
                target.delete()
                null
            } else {
                fileName
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resolves a note-local asset referenced by markdown (`![alt](name)`).
     * Only a plain file name inside this note's directory is accepted —
     * separators and `..` are rejected so content can never escape the dir.
     */
    fun getNoteAsset(noteId: String, relativeName: String): File? {
        val name = relativeName.trim()
        if (name.isEmpty()) return null
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val file = File(getNoteDir(noteId), name)
        return file.takeIf { it.isFile }
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

    /** Workspace preferences (tab bar state, dock orientation) live beside the notes. */
    fun getUiPrefsFile(): File = File(context.filesDir, "ui_prefs.json")

    /** Convenience accessor for the workspace preferences. */
    fun uiPrefs(): UiPrefs = UiPrefs(getUiPrefsFile())

    /**
     * Optional raster background of a page (a page picked out of a PDF).
     *
     * A vector page draws its PDF live and a raster page draws its ink on
     * white paper, so neither has a place for "this is the picture of the
     * page". The override keeps the picked page without touching the
     * annotations.
     */
    fun getPageBackgroundFile(noteId: String, pageIndex: Int): File =
        File(getNoteDir(noteId), "background_${pageIndex}.png")

    /**
     * Manifest of the ink a page carries outside its paper (the margins of an
     * imported PDF). The crops themselves are PNGs next to it, named
     * `beyond_<pageIndex>_<n>.png`.
     */
    fun getBeyondInkFile(noteId: String, pageIndex: Int): File =
        File(getLayersDir(noteId), "beyond_$pageIndex.json")

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
