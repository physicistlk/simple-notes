package personal.notes.touch.data.files

import android.content.Context
import android.net.Uri
import personal.notes.touch.ui.canvas.LayerTileGrid
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
        // The page background and the margin ink belong to this page too.
        getPageBackgroundFile(noteId, pageIndex).delete()
        getBeyondInkFile(noteId, pageIndex).delete()
        getLayersDir(noteId).listFiles()?.forEach { f ->
            if (f.name.startsWith("beyond_${pageIndex}_") && f.name.endsWith(".png")) f.delete()
            // The layers of the page (the annotation layer and the floating
            // images) belong to it: they go with it, or a new page at the
            // same index would inherit them.
            if (f.name.startsWith("page_${pageIndex}_layer_") && f.name.endsWith(".png")) f.delete()
            // The annotation layers of the page (the writing of the user),
            // their tiles and their metadata belong to it as well: they go
            // with it, or the layers of a deleted page would come back on the
            // page that takes its place.
            if (isAnnotationFileOfPage(f.name, pageIndex)) f.delete()
        }
        return if (file.exists()) file.delete() else true
    }

    /**
     * True when a file of the layers folder belongs to the annotation layers
     * of a page.
     *
     * The files of a page are `page_<i>_annot_<n>.png` (its layers),
     * `page_<i>_annot_<n>_t<tx>_<ty>.png` (the tiles of a layer) and
     * `page_<i>_annot.json` (the metadata of the page: the names of its
     * layers and the layer the writing goes on).
     *
     * The metadata has no underscore behind "annot", so the prefix is
     * matched WITHOUT it: the metadata of a page has to follow its page, or
     * the layers of a deleted page come back on the page that takes its
     * place.
     */
    private fun isAnnotationFileOfPage(name: String, pageIndex: Int): Boolean {
        if (!name.startsWith("page_${pageIndex}_annot")) return false
        return name.endsWith(".png") || name.endsWith(".json")
    }

    fun renamePageFile(noteId: String, oldIndex: Int, newIndex: Int): Boolean {
        val oldStrokes = getStrokesFile(noteId, oldIndex)
        val newStrokes = getStrokesFile(noteId, newIndex)
        if (oldStrokes.exists()) {
            if (newStrokes.exists()) newStrokes.delete()
            oldStrokes.renameTo(newStrokes)
        }
        // The background and the margin ink are page scoped: they have to
        // move with the page, or they end up on the wrong one.
        val oldBackground = getPageBackgroundFile(noteId, oldIndex)
        val newBackground = getPageBackgroundFile(noteId, newIndex)
        if (oldBackground.exists()) {
            if (newBackground.exists()) newBackground.delete()
            oldBackground.renameTo(newBackground)
        }
        val layersDir = getLayersDir(noteId)
        val oldBeyond = getBeyondInkFile(noteId, oldIndex)
        val newBeyond = getBeyondInkFile(noteId, newIndex)
        layersDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("beyond_${oldIndex}_") && f.name.endsWith(".png")) {
                val target = java.io.File(layersDir, f.name.replace("beyond_${oldIndex}_", "beyond_${newIndex}_"))
                if (target.exists()) target.delete()
                f.renameTo(target)
            }
            // The layers of the page (the annotation layer and the floating
            // images) move with the page.
            if (f.name.startsWith("page_${oldIndex}_layer_") && f.name.endsWith(".png")) {
                val target = java.io.File(
                    layersDir,
                    f.name.replace("page_${oldIndex}_layer_", "page_${newIndex}_layer_")
                )
                if (target.exists()) target.delete()
                f.renameTo(target)
            }
            // The annotation layers of the page (the writing of the user),
            // their tiles and their metadata move with the page. The metadata
            // has no underscore behind "annot", so the prefix is matched
            // without it: the metadata of a page has to follow its page.
            if (isAnnotationFileOfPage(f.name, oldIndex)) {
                val target = java.io.File(
                    layersDir,
                    f.name.replace("page_${oldIndex}_annot", "page_${newIndex}_annot")
                )
                if (target.exists()) target.delete()
                f.renameTo(target)
            }
        }
        if (oldBeyond.exists()) {
            if (newBeyond.exists()) newBeyond.delete()
            oldBeyond.renameTo(newBeyond)
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

    /**
     * The layer list of a page. The layers are page scoped: a note can have
     * a different set of floating images on every page, so the manifest and
     * the layer bitmaps carry the page index.
     */
    fun getLayerInfoFile(noteId: String, pageIndex: Int): File =
        File(getLayersDir(noteId), "page_${pageIndex}_layers.json")

    /** The bitmap file of one floating image layer of a page. */
    fun getLayerFile(noteId: String, pageIndex: Int, layerIndex: Int): File =
        File(getLayersDir(noteId), "page_${pageIndex}_layer_${layerIndex}.png")

    /**
     * The bitmap file of the **annotation layer** of a page.
     *
     * The annotation layer is the surface the writing goes on: it reaches out
     * to both sides of the page, so the notes next to a text heavy document
     * have room. Its name is deliberately different from the floating image
     * layers (`page_<i>_layer_<n>.png`) - the single-page engine deletes the
     * floating layer files it does not know, and it must not delete the
     * notes of the page.
     */
    fun getAnnotationLayerFile(noteId: String, pageIndex: Int, layerIndex: Int = 0): File =
        File(getLayersDir(noteId), "page_${pageIndex}_annot_${layerIndex}.png")

    /**
     * The metadata of the annotation layers of a page.
     *
     * It names the layers of the page (their number, their name and whether
     * they are hidden) and the layer the writing goes on. It sits next to
     * the layer files (`page_<i>_annot_<n>.png`).
     */
    fun getAnnotationMetaFile(noteId: String, pageIndex: Int): File =
        File(getLayersDir(noteId), "page_${pageIndex}_annot.json")

    fun getAnnotationLayerOpsFile(noteId: String, pageIndex: Int, layerIndex: Int): File =
        File(getLayersDir(noteId), "page_${pageIndex}_annot_${layerIndex}_ops.json")

    /**
     * The bitmap file of ONE tile of an annotation layer (C50).
     *
     * A layer that is cut into tiles has one PNG per tile:
     * `page_<i>_annot_<n>_t<tx>_<ty>.png`. The name of the tile comes from
     * the grid ([LayerTileGrid]), so the file store and the metadata always
     * agree on it.
     *
     * The name keeps the prefix of the layer file (`page_<i>_annot_`), so
     * the files of a page are found and moved with it.
     */
    fun getAnnotationLayerTileFile(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int,
        tx: Int,
        ty: Int
    ): File =
        File(
            getLayersDir(noteId),
            "page_${pageIndex}_annot_${layerIndex}_${LayerTileGrid.tileName(tx, ty)}.png"
        )

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
