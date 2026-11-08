package personal.notes.touch.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.dao.BlockBacklink
import personal.notes.touch.data.db.dao.BlockCandidate
import personal.notes.touch.data.db.dao.BlockRefDao
import personal.notes.touch.data.db.dao.BlockRefTarget
import personal.notes.touch.data.db.dao.FolderCount
import personal.notes.touch.data.db.dao.FolderDao
import personal.notes.touch.data.db.dao.NoteBlockDao
import personal.notes.touch.data.db.dao.NoteDao
import personal.notes.touch.data.db.dao.NoteRefDao
import personal.notes.touch.data.db.dao.NoteTagDao
import personal.notes.touch.data.db.dao.PageDao
import personal.notes.touch.data.db.dao.TemplateDao
import personal.notes.touch.data.db.dao.RefTarget
import personal.notes.touch.data.db.dao.TagCount
import personal.notes.touch.data.db.entity.BlockRefEntity
import personal.notes.touch.data.db.entity.NoteBlockEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.TemplateEntity
import personal.notes.touch.data.db.entity.NoteRefEntity
import personal.notes.touch.data.db.entity.NoteTagEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.text.BlockRefs
import personal.notes.touch.text.BlockTransfer
import personal.notes.touch.text.GraphNoteInput
import personal.notes.touch.text.KnowledgeGraph
import personal.notes.touch.text.KnowledgeGraphBuilder
import personal.notes.touch.text.QueryBlocks
import personal.notes.touch.text.NoteTemplates
import personal.notes.touch.text.NoteTags
import personal.notes.touch.text.WikiLinks
import personal.notes.touch.ui.editor.BlockPersistence
import java.util.UUID

class NoteRepository(
    private val noteDao: NoteDao,
    private val noteBlockDao: NoteBlockDao,
    private val noteTagDao: NoteTagDao,
    private val noteRefDao: NoteRefDao,
    private val folderDao: FolderDao,
    private val pageDao: PageDao,
    private val templateDao: TemplateDao,
    private val blockRefDao: BlockRefDao,
    val fileStore: NoteFileStore
) {
    /** Emitted after a cross-document block move/copy has reached both notes. */
    private val _blockTransfers = MutableSharedFlow<BlockTransferCompleted>(
        extraBufferCapacity = 32
    )
    val blockTransfers: kotlinx.coroutines.flow.SharedFlow<BlockTransferCompleted> =
        _blockTransfers.asSharedFlow()

    /** Completion notification for open editors that own either note affected. */
    data class BlockTransferCompleted(
        val sourceNoteId: String,
        val targetNoteId: String,
        val move: Boolean,
        val count: Int
    )

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAll()

    fun getNoteById(id: String): Flow<NoteEntity?> = noteDao.getByIdFlow(id)

    suspend fun getNoteDirect(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getById(id)
    }

    fun getFavorites(): Flow<List<NoteEntity>> = noteDao.getFavorites()

    fun getRecycledNotes(): Flow<List<NoteEntity>> = noteDao.getRecycled()

    fun getNotesByFolder(folderId: String?): Flow<List<NoteEntity>> {
        return if (folderId == null) {
            noteDao.getAll()
        } else {
            noteDao.getByFolder(folderId)
        }
    }

    /** Notes filed in [folderIds]; the library passes a whole subtree. */
    fun getNotesByFolders(folderIds: List<String>): Flow<List<NoteEntity>> =
        noteDao.getByFolders(folderIds)

    // Folders
    fun getAllFolders(): Flow<List<personal.notes.touch.data.db.entity.FolderEntity>> = folderDao.getAll()

    /** Live-note counts per folder, for the notebook tree. */
    fun observeFolderCounts(): Flow<List<FolderCount>> = noteDao.observeFolderCounts()

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

    /**
     * Deletes a notebook without deleting anything inside it: subfolders and
     * notes move up to the deleted folder's own parent (root when it had none).
     * Two separate statements, so a crash between them can leave orphans — the
     * tree already renders an orphan as a root rather than losing it.
     */
    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getById(id) ?: return@withContext
        folderDao.reparentChildren(from = id, to = folder.parentId)
        noteDao.reassignFolder(from = id, to = folder.parentId)
        folderDao.delete(id)
    }

    suspend fun moveNoteToFolder(noteId: String, folderId: String?) = withContext(Dispatchers.IO) {
        noteDao.setFolder(noteId, folderId, System.currentTimeMillis())
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        noteBlockDao.deleteForRecycledNotes()
        noteTagDao.deleteForRecycledNotes()
        noteRefDao.deleteForRecycledNotes()
        blockRefDao.deleteForRecycledNotes()
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
            syncIndex(id, content)
        }
    }

    // ---- templates + daily notes (schema 9) --------------------------------

    fun observeTemplates(): Flow<List<TemplateEntity>> = templateDao.observeAll()

    /**
     * Creates or updates a template. Marking one as the daily template clears
     * the flag on the others, so "Today" always has one unambiguous source.
     */
    suspend fun saveTemplate(
        id: String?,
        name: String,
        content: String,
        isDaily: Boolean,
        now: Long = System.currentTimeMillis()
    ): TemplateEntity = withContext(Dispatchers.IO) {
        val existing = if (id != null) templateDao.getById(id) else null
        val template = TemplateEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Untitled template" },
            content = content,
            isDaily = isDaily,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        if (isDaily) templateDao.clearDailyFlags()
        templateDao.upsert(template)
        template
    }

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        templateDao.delete(id)
    }

    /** Two starters, offered from the empty templates dialog. No-op if any exist. */
    suspend fun addStarterTemplates(now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            if (templateDao.count() > 0) return@withContext
            templateDao.upsert(
                TemplateEntity(
                    id = UUID.randomUUID().toString(),
                    name = NoteTemplates.DEFAULT_DAILY_NAME,
                    content = NoteTemplates.DEFAULT_DAILY_CONTENT,
                    isDaily = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
            templateDao.upsert(
                TemplateEntity(
                    id = UUID.randomUUID().toString(),
                    name = NoteTemplates.STARTER_MEETING_NAME,
                    content = NoteTemplates.STARTER_MEETING_CONTENT,
                    createdAt = now + 1,
                    updatedAt = now + 1
                )
            )
        }

    /**
     * Opens today's note, creating it from the daily template when absent.
     * Title = the date (yyyy-MM-dd), so "Today" is idempotent per day and the
     * note can be referenced from other notes with `[[2026-11-06]]`.
     */
    suspend fun openOrCreateDailyNote(
        now: Long = System.currentTimeMillis()
    ): NoteEntity = withContext(Dispatchers.IO) {
        val title = NoteTemplates.dailyTitle(now)
        noteDao.getByTitle(title)?.let { return@withContext it }

        val content = templateDao.getDaily()
            ?.let { NoteTemplates.expand(it.content, title, now) }
            .orEmpty()
        val note = createNote(title = title, pageType = "text")
        if (content.isNotBlank()) updateNoteContent(note.id, title, content)
        noteDao.getById(note.id) ?: note
    }

    /** New text note whose body is the template with its variables resolved. */
    suspend fun createNoteFromTemplate(
        templateId: String,
        now: Long = System.currentTimeMillis()
    ): NoteEntity? = withContext(Dispatchers.IO) {
        val template = templateDao.getById(templateId) ?: return@withContext null
        val title = template.name
        val content = NoteTemplates.expand(template.content, title, now)
        val note = createNote(title = title, pageType = "text")
        if (content.isNotBlank()) updateNoteContent(note.id, title, content)
        noteDao.getById(note.id) ?: note
    }

    // note_blocks (schema 6): stable per-block ids persisted alongside content.
    // The markdown in `notes.content` stays the text source of truth; these
    // rows carry identity and are rewritten from the same block list.

    suspend fun loadNoteBlocks(noteId: String): List<NoteBlockEntity> = withContext(Dispatchers.IO) {
        noteBlockDao.getBlocksForNote(noteId)
    }

    /** Replaces the note's whole block row set atomically. */
    suspend fun saveNoteBlocks(noteId: String, blocks: List<NoteBlockEntity>) = withContext(Dispatchers.IO) {
        noteBlockDao.replaceBlocks(noteId, blocks)
        syncBlockRefs(noteId, blocks)
    }

    /** Live blocks for embedded QUERY blocks; Room observes both joined tables. */
    fun observeQueryCandidates(): Flow<List<BlockCandidate>> =
        noteBlockDao.observeQueryCandidates()

    /** Current knowledge graph from `[[...]]` and resolved `((...))` block refs. */
    suspend fun knowledgeGraphSnapshot(): KnowledgeGraph = withContext(Dispatchers.IO) {
        val notes = noteDao.getAllLive()
        val wikiPairs = noteDao.getWikiGraphEdges().map { it.sourceNoteId to it.targetNoteId }
        val blockPairs = blockRefDao.getGraphEdges().map { it.sourceNoteId to it.targetNoteId }
        KnowledgeGraphBuilder.build(
            notes = notes.map { GraphNoteInput(it.id, it.title, it.pageType) },
            links = wikiPairs + blockPairs
        )
    }

    /**
     * Moves or copies blocks between text notes. The target's rows and markdown
     * are written first, so a crash cannot leave a source deletion ahead of
     * the target append. Moves retain block UUIDs (block references survive);
     * copies mint new ids.
     */
    suspend fun transferBlocks(
        sourceNoteId: String,
        targetNoteId: String,
        blockIds: Set<String>,
        move: Boolean
    ): Int = withContext(Dispatchers.IO) {
        if (sourceNoteId.isBlank() || targetNoteId.isBlank() ||
            sourceNoteId == targetNoteId || blockIds.isEmpty()
        ) {
            return@withContext 0
        }

        val sourceNote = noteDao.getById(sourceNoteId) ?: return@withContext 0
        val targetNote = noteDao.getById(targetNoteId) ?: return@withContext 0
        if (sourceNote.pageType != "text" || targetNote.pageType != "text") {
            return@withContext 0
        }

        val incoming = BlockTransfer.selection(
            rows = noteBlockDao.getBlocksForNote(sourceNoteId),
            ids = blockIds
        )
        if (incoming.isEmpty()) return@withContext 0

        val nextTarget = BlockTransfer.append(
            target = noteBlockDao.getBlocksForNote(targetNoteId),
            incoming = incoming,
            targetNoteId = targetNoteId,
            copyIds = !move
        )
        noteBlockDao.replaceBlocks(targetNoteId, nextTarget)
        syncBlockRefs(targetNoteId, nextTarget)
        updateNoteContent(
            targetNoteId,
            targetNote.title.ifBlank { "Untitled Note" },
            BlockPersistence.serializeRows(nextTarget)
        )

        if (move) {
            val nextSource = BlockTransfer.remove(
                rows = noteBlockDao.getBlocksForNote(sourceNoteId),
                ids = incoming.map { it.id }.toSet()
            )
            noteBlockDao.replaceBlocks(sourceNoteId, nextSource)
            syncBlockRefs(sourceNoteId, nextSource)
            updateNoteContent(
                sourceNoteId,
                sourceNote.title.ifBlank { "Untitled Note" },
                BlockPersistence.serializeRows(nextSource)
            )
        }

        _blockTransfers.tryEmit(
            BlockTransferCompleted(sourceNoteId, targetNoteId, move, incoming.size)
        )
        incoming.size
    }

    // ---- block references (schema 11) --------------------------------------

    fun observeOutgoingBlockRefs(noteId: String): Flow<List<BlockRefTarget>> =
        blockRefDao.observeOutgoing(noteId)

    /** `((…))` in other notes that point at a block of [noteId]. */
    fun observeBlockBacklinks(noteId: String): Flow<List<BlockBacklink>> =
        blockRefDao.observeBacklinks(noteId)

    /** `((` autocomplete over every persisted block, newest notes first. */
    suspend fun searchBlocks(
        query: String,
        excludeBlockId: String,
        limit: Int = 20
    ): List<BlockCandidate> = withContext(Dispatchers.IO) {
        noteBlockDao.searchBlocks(likeEscape(query.trim()), excludeBlockId, limit)
    }

    /**
     * Derives the block-reference rows for one note from its persisted blocks.
     * CODE blocks are skipped: their body is quoted markup, so a documented
     * `((syntax))` sample must not create a live reference.
     */
    private suspend fun syncBlockRefs(noteId: String, blocks: List<NoteBlockEntity>) {
        val refs = blockRefsOf(noteId, blocks)
        if (!sameBlockRefs(blockRefDao.getForNote(noteId), refs)) {
            blockRefDao.replaceForNote(noteId, refs)
        }
    }

    private fun blockRefsOf(
        noteId: String,
        blocks: List<NoteBlockEntity>
    ): List<BlockRefEntity> = blocks
        .asSequence()
        .filter { it.blockType != "CODE" }
        .flatMap { block ->
            BlockRefs.parseLine(block.text).map { ref ->
                BlockRefEntity(
                    sourceNoteId = noteId,
                    sourceBlockId = block.id,
                    targetKey = ref.target,
                    targetAlias = ref.alias
                )
            }
        }
        .distinctBy { Triple(it.sourceNoteId, it.sourceBlockId, it.targetKey) }
        .toList()

    private fun sameBlockRefs(current: List<BlockRefEntity>, next: List<BlockRefEntity>): Boolean {
        if (current.size != next.size) return false
        val a = current.map { Triple(it.sourceBlockId, it.targetKey, it.targetAlias) }.toSet()
        return next.all { Triple(it.sourceBlockId, it.targetKey, it.targetAlias) in a }
    }

    /** `%`, `_` and `\` are literal in a search box, not LIKE wildcards. */
    private fun likeEscape(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

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
        noteBlockDao.deleteForNote(id)
        noteTagDao.deleteForNote(id)
        noteRefDao.deleteForNote(id)
        pageDao.deleteForNote(id)
        noteDao.delete(id)
        fileStore.deleteNoteFiles(id)
    }

    // ---------------------------------------------------------------------
    // Knowledge index (schema 7): tags + wiki-link references
    //
    // Both are projections of `notes.content` and are rewritten here, never by
    // the UI. Compare-before-write keeps the 1 s autosave from touching the
    // index when the user only edited prose.
    // ---------------------------------------------------------------------

    /** Tag bar data: tags of live notes with their note counts. */
    fun observeTagCounts(): Flow<List<TagCount>> = noteTagDao.observeTagCounts()

    /** Display tags per note id, used to filter the library and label cards. */
    fun observeTagsByNote(): Flow<Map<String, List<String>>> =
        noteTagDao.observeAll().map { rows ->
            rows.groupBy({ it.noteId }, { it.displayName })
                .mapValues { (_, names) -> names.distinct().sorted() }
        }

    /** Notes whose content links to [title] (backlinks), newest first. */
    fun observeBacklinks(noteId: String, title: String): Flow<List<NoteEntity>> =
        noteRefDao.observeBacklinks(WikiLinks.key(title), noteId)

    /** `[[…]]` written inside [noteId], resolved against live note titles. */
    fun observeOutgoingRefs(noteId: String): Flow<List<RefTarget>> =
        noteRefDao.observeOutgoing(noteId)

    /**
     * Re-derives the index for [noteId] from its content. Called on every save;
     * a write only happens when the derived rows actually changed.
     */
    suspend fun syncIndex(noteId: String, content: String) = withContext(Dispatchers.IO) {
        val tags = NoteTags.parse(content).map {
            NoteTagEntity(noteId = noteId, tag = it.key, displayName = it.display)
        }
        if (!sameTags(noteTagDao.getForNote(noteId), tags)) {
            noteTagDao.replaceTags(noteId, tags)
        }

        val refs = WikiLinks.parse(content).map {
            NoteRefEntity(noteId = noteId, targetKey = it.key, targetTitle = it.target)
        }
        if (!sameRefs(noteRefDao.getForNote(noteId), refs)) {
            noteRefDao.replaceRefs(noteId, refs)
        }
    }

    /**
     * One-time catch-up for notes written before the index existed, and a
     * self-healing pass in general: every note with content is re-derived,
     * orphan rows are dropped. Idempotent — returns how many notes were
     * (re)written, so the caller can log it.
     */
    suspend fun reindexAll(): Int = withContext(Dispatchers.IO) {
        noteTagDao.deleteOrphans()
        noteRefDao.deleteOrphans()
        blockRefDao.deleteForRecycledNotes()
        var changed = 0
        for (note in noteDao.getNotesWithContent()) {
            val content = note.content ?: continue
            if (content.isBlank()) continue
            val before = noteTagDao.getForNote(note.id) to noteRefDao.getForNote(note.id)
            syncIndex(note.id, content)
            val after = noteTagDao.getForNote(note.id) to noteRefDao.getForNote(note.id)
            if (before != after) changed++
        }
        // Block refs are derived from note_blocks rather than from content:
        // ids live only in the table, so this pass is what backfills notes
        // whose blocks predate the index.
        for ((noteId, blocks) in noteBlockDao.getAllForLiveNotes().groupBy { it.noteId }) {
            val before = blockRefDao.getForNote(noteId)
            val next = blockRefsOf(noteId, blocks)
            if (!sameBlockRefs(before, next)) {
                blockRefDao.replaceForNote(noteId, next)
                changed++
            }
        }
        changed
    }

    private fun sameTags(current: List<NoteTagEntity>, next: List<NoteTagEntity>): Boolean {
        if (current.size != next.size) return false
        val a = current.associate { it.tag to it.displayName }
        return next.all { a[it.tag] == it.displayName }
    }

    private fun sameRefs(current: List<NoteRefEntity>, next: List<NoteRefEntity>): Boolean {
        if (current.size != next.size) return false
        return current.map { it.targetKey }.toSet() == next.map { it.targetKey }.toSet()
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

    /**
     * Loads a legacy raster page downsampled for on-screen display.
     *
     * Page PNGs are written at full canvas size (2160×3056 ≈ 26 MB decoded)
     * and continuous reading keeps several alive while scrolling; the library
     * view only needs a few hundred pixels. Vector pages never use this — they
     * re-render through pdfium.
     */
    suspend fun loadPageBitmapForDisplay(
        noteId: String,
        pageIndex: Int,
        displayWidthPx: Int
    ): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val file = fileStore.getPageFile(noteId, pageIndex)
        if (displayWidthPx <= 0) {
            personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file)
        } else {
            personal.notes.touch.util.BitmapUtils.loadImage(
                file, maxDimension = displayWidthPx.coerceAtLeast(360)
            )
        }
    }

    /** F-10: loads the stroke-only layer of a vector-PDF page (null = none yet). */
    suspend fun loadPageStrokes(noteId: String, pageIndex: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(fileStore.getStrokesFile(noteId, pageIndex))
    }

    /**
     * F-10: persists the stroke-only layer of a vector-PDF page. The page PNG
     * (pdfium preview used by library thumbnails) is intentionally untouched.
     */
    suspend fun savePageStrokes(noteId: String, pageIndex: Int, bitmap: android.graphics.Bitmap): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.getStrokesFile(noteId, pageIndex)
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

    /** Persist the per-page paper pattern (PaperPattern.code) to the page row. */
    suspend fun savePagePattern(noteId: String, pageIndex: Int, patternCode: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val pages = pageDao.getPagesForNoteSync(noteId)
            val page = pages.getOrNull(pageIndex) ?: return@withContext false
            pageDao.update(page.copy(paperPattern = patternCode, updatedAt = System.currentTimeMillis()))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Read the persisted per-page paper pattern (0 = unset → app default applies). */
    suspend fun loadPagePattern(noteId: String, pageIndex: Int): Int = withContext(Dispatchers.IO) {
        try {
            pageDao.getPagesForNoteSync(noteId).getOrNull(pageIndex)?.paperPattern ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * F-10 vector PDF import: records where a page came from so the canvas
     * can re-render it from the source PDF at any zoom level.
     */
    suspend fun setPagePdfSource(
        noteId: String,
        pageIndex: Int,
        pdfSourcePath: String,
        pdfPageIndex: Int
    ) = withContext(Dispatchers.IO) {
        pageDao.setPdfSource(
            noteId = noteId,
            index = pageIndex,
            pdfSourcePath = pdfSourcePath,
            pdfPageIndex = pdfPageIndex,
            updatedAt = System.currentTimeMillis()
        )
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
