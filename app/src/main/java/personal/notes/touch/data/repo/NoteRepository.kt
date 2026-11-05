package personal.notes.touch.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.dao.FolderDao
import personal.notes.touch.data.db.dao.NoteDao
import personal.notes.touch.data.db.dao.PageDao
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import java.util.UUID

class NoteRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val pageDao: PageDao,
    val fileStore: NoteFileStore
) {
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAll()

    fun getNoteById(id: String): Flow<NoteEntity?> = noteDao.getByIdFlow(id)

    suspend fun getNoteDirect(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getById(id)
    }

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    fun getFavorites(): Flow<List<NoteEntity>> = noteDao.getFavorites()

    fun getRecycledNotes(): Flow<List<NoteEntity>> = noteDao.getRecycled()

    fun getNotesByFolder(folderId: String?): Flow<List<NoteEntity>> {
        return if (folderId == null) {
            noteDao.getAll()
        } else {
            noteDao.getByFolder(folderId)
        }
    }

    // Folders
    fun getAllFolders(): Flow<List<personal.notes.touch.data.db.entity.FolderEntity>> = folderDao.getAll()

    suspend fun createFolder(name: String, parentId: String? = null): personal.notes.touch.data.db.entity.FolderEntity = withContext(Dispatchers.IO) {
        val folder = personal.notes.touch.data.db.entity.FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            parentId = parentId,
            createdAt = System.currentTimeMillis()
        )
        folderDao.insert(folder)
        folder
    }

    suspend fun renameFolder(id: String, newName: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getById(id)
        if (folder != null) {
            folderDao.update(folder.copy(name = newName.trim()))
        }
    }

    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        folderDao.delete(id)
    }

    suspend fun moveNoteToFolder(noteId: String, folderId: String?) = withContext(Dispatchers.IO) {
        noteDao.setFolder(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        noteDao.clearRecycled()
    }

    suspend fun createNote(
        title: String = "Untitled Note",
        folderId: String? = null,
        pageType: String = "infinite"
    ): NoteEntity = withContext(Dispatchers.IO) {
        val noteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = noteId,
            title = title,
            folderId = folderId,
            favorite = false,
            deleted = false,
            createdAt = now,
            updatedAt = now,
            pageCount = 1,
            pageType = pageType
        )
        noteDao.insert(note)

        // Create initial blank page record
        val pageId = UUID.randomUUID().toString()
        val pageFile = fileStore.getPageFile(noteId, 0)
        val initialPage = PageEntity(
            id = pageId,
            noteId = noteId,
            index = 0,
            bitmapPath = pageFile.absolutePath,
            updatedAt = now
        )
        pageDao.insert(initialPage)

        note
    }

    suspend fun updateNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateNoteContent(id: String, title: String, content: String) = withContext(Dispatchers.IO) {
        val existing = noteDao.getById(id)
        if (existing != null) {
            noteDao.update(
                existing.copy(
                    title = title,
                    content = content,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateTitle(id: String, title: String) = withContext(Dispatchers.IO) {
        val existing = noteDao.getById(id)
        if (existing != null) {
            noteDao.update(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun softDeleteNote(id: String, deleted: Boolean = true) = withContext(Dispatchers.IO) {
        noteDao.setDeleted(id, deleted, System.currentTimeMillis())
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    suspend fun permanentlyDeleteNote(id: String) = withContext(Dispatchers.IO) {
        pageDao.deleteForNote(id)
        noteDao.delete(id)
        fileStore.deleteNoteFiles(id)
    }

    // Pages (M4)
    fun getPagesForNote(noteId: String): Flow<List<PageEntity>> = pageDao.getPagesForNote(noteId)

    suspend fun addPage(noteId: String): PageEntity = withContext(Dispatchers.IO) {
        val existing = pageDao.getPagesForNoteSync(noteId)
        val newIndex = existing.size
        val pageFile = fileStore.getPageFile(noteId, newIndex)
        val newPage = PageEntity(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            index = newIndex,
            bitmapPath = pageFile.absolutePath,
            updatedAt = System.currentTimeMillis()
        )
        pageDao.insert(newPage)
        val note = noteDao.getById(noteId)
        if (note != null) {
            noteDao.update(note.copy(pageCount = newIndex + 1, updatedAt = System.currentTimeMillis()))
        }
        newPage
    }

    suspend fun deletePage(noteId: String, pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesForNoteSync(noteId).toMutableList()
        if (pages.size <= 1 || pageIndex !in pages.indices) {
            return@withContext false
        }
        val target = pages.removeAt(pageIndex)
        pageDao.delete(target.id)
        fileStore.deletePageFile(noteId, pageIndex)

        // Re-index remaining pages
        pages.forEachIndexed { idx, page ->
            if (page.index != idx) {
                fileStore.renamePageFile(noteId, page.index, idx)
                val updated = page.copy(
                    index = idx,
                    bitmapPath = fileStore.getPageFile(noteId, idx).absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
                pageDao.update(updated)
            }
        }

        val note = noteDao.getById(noteId)
        if (note != null) {
            noteDao.update(note.copy(pageCount = pages.size, updatedAt = System.currentTimeMillis()))
        }
        true
    }

    suspend fun loadPageBitmap(noteId: String, pageIndex: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val file = fileStore.getPageFile(noteId, pageIndex)
        personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file)
    }

    suspend fun savePageBitmap(noteId: String, pageIndex: Int, bitmap: android.graphics.Bitmap): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.getPageFile(noteId, pageIndex)
        val ok = personal.notes.touch.util.BitmapUtils.saveBitmapToPng(bitmap, file)
        if (ok) {
            try {
                val note = noteDao.getById(noteId)
                if (note != null) {
                    noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                // Ignore if note lookup fails
            }
        }
        ok
    }
}
