package personal.notes.touch.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.AppDatabase
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
import personal.notes.touch.util.PdfModeReconciliationPolicy
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** How many parents the breadcrumb walks up before it gives up. */
private const val MAX_NESTING_DEPTH = 32

/** Upper bound for one cascade delete/restore of a sub-page tree. */
private const val MAX_SUBTREE_NOTES = 10_000

class NoteRepository(
    private val noteDao: NoteDao,
    private val noteBlockDao: NoteBlockDao,
    private val noteTagDao: NoteTagDao,
    private val noteRefDao: NoteRefDao,
    private val folderDao: FolderDao,
    private val pageDao: PageDao,
    private val templateDao: TemplateDao,
    private val blockRefDao: BlockRefDao,
    val fileStore: NoteFileStore,
    private val database: AppDatabase? = null
) {
    private val saveMutex = Mutex()
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

    // ---- Nested pages ------------------------------------------------------------

    /** Every live note including sub-pages (used while searching). */
    fun getAllNotesIncludingSubPages(): Flow<List<NoteEntity>> = noteDao.getAllIncludingSubPages()

    /** Live sub-pages of [parentId], oldest first. */
    fun observeSubPages(parentId: String): Flow<List<NoteEntity>> = noteDao.observeSubPages(parentId)

    fun observeSubPageCounts(): Flow<List<personal.notes.touch.data.db.dao.SubPageCount>> =
        noteDao.observeSubPageCounts()

    /**
     * Creates an empty text note as a sub-page of [parentId], filed in the
     * parent's notebook. Returns null when the parent does not exist.
     */
    suspend fun createSubPage(parentId: String, title: String = "Untitled"): NoteEntity? =
        withContext(Dispatchers.IO) {
            val parent = noteDao.getById(parentId) ?: return@withContext null
            createNote(
                title = title,
                folderId = parent.folderId,
                pageType = "text",
                parentNoteId = parent.id
            )
        }

    /**
     * The parents of [noteId], outermost first (the breadcrumb). A guard stops
     * at [MAX_NESTING_DEPTH] and on a cycle, so a corrupted parent link can
     * never loop forever.
     */
    suspend fun getAncestors(noteId: String): List<NoteEntity> = withContext(Dispatchers.IO) {
        val chain = ArrayList<NoteEntity>()
        val seen = HashSet<String>()
        seen.add(noteId)
        var current = noteDao.getById(noteId)?.parentNoteId
        while (current != null && chain.size < MAX_NESTING_DEPTH && seen.add(current)) {
            val parent = noteDao.getById(current) ?: break
            chain.add(parent)
            current = parent.parentNoteId
        }
        chain.reversed()
    }

    /** [id] and every sub-page below it, parents before children. */
    private suspend fun subtreeIds(id: String): List<String> {
        val result = ArrayList<String>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(id)
        while (queue.isNotEmpty() && result.size < MAX_SUBTREE_NOTES) {
            val next = queue.removeFirst()
            if (!seen.add(next)) continue
            result.add(next)
            noteDao.getChildIds(next).forEach { queue.add(it) }
        }
        return result
    }

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
        // The page images of the deleted notes are files: without this they
        // stay on disk forever and are packed into every later backup.
        noteDao.getRecycledIds().forEach { id -> fileStore.deleteNoteFiles(id) }
        noteDao.clearRecycled()
    }

    /** Titles of live notes in [folderId] (null = unfiled). Used to mint default names. */
    suspend fun existingTitlesInFolder(folderId: String?): List<String> = withContext(Dispatchers.IO) {
        noteDao.getAllLive().filter { it.folderId == folderId }.map { it.title }
    }

    suspend fun createNote(
        title: String = "Untitled Note",
        folderId: String? = null,
        pageType: String = "infinite",
        parentNoteId: String? = null
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
            pageType = pageType,
            parentNoteId = parentNoteId
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

    /**
     * Changes the page count of a note (PDF import).
     *
     * A narrow UPDATE on purpose: writing the whole row would put back a
     * `lastPageIndex` that was read before the user turned the page, and the
     * note would reopen on the wrong page.
     */
    suspend fun updateNotePageCount(noteId: String, pageCount: Int) = withContext(Dispatchers.IO) {
        noteDao.updatePageCount(noteId, pageCount, System.currentTimeMillis())
    }

    suspend fun updateNoteContent(id: String, title: String, content: String) = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val action: suspend () -> Unit = {
                val existing = noteDao.getById(id)
                if (existing != null) {
                    noteDao.updateContentRow(id, title, content, System.currentTimeMillis())
                    syncIndex(id, content)
                }
            }
            if (database != null) {
                database.withTransaction { action() }
            } else {
                action()
            }
        }
    }

    suspend fun updateNoteContentAndBlocks(
        id: String,
        title: String,
        content: String,
        blocks: List<NoteBlockEntity>?
    ) = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val action: suspend () -> Unit = {
                val existing = noteDao.getById(id)
                if (existing != null) {
                    noteDao.updateContentRow(id, title, content, System.currentTimeMillis())
                    syncIndex(id, content)
                    if (blocks != null) {
                        noteBlockDao.replaceBlocks(id, blocks)
                        syncBlockRefs(id, blocks)
                    }
                }
            }
            if (database != null) {
                database.withTransaction { action() }
            } else {
                action()
            }
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

        saveMutex.withLock {
            val sourceNote = noteDao.getById(sourceNoteId) ?: return@withLock 0
            val targetNote = noteDao.getById(targetNoteId) ?: return@withLock 0
            if (sourceNote.pageType != "text" || targetNote.pageType != "text") {
                return@withLock 0
            }

            val incoming = BlockTransfer.selection(
                rows = noteBlockDao.getBlocksForNote(sourceNoteId),
                ids = blockIds
            )
            if (incoming.isEmpty()) return@withLock 0

            val action: suspend () -> Int = {
                val nextTarget = BlockTransfer.append(
                    target = noteBlockDao.getBlocksForNote(targetNoteId),
                    incoming = incoming,
                    targetNoteId = targetNoteId,
                    copyIds = !move
                )
                noteBlockDao.replaceBlocks(targetNoteId, nextTarget)
                syncBlockRefs(targetNoteId, nextTarget)
                val targetContent = BlockPersistence.serializeRows(nextTarget)
                noteDao.updateContentRow(
                    targetNoteId,
                    targetNote.title.ifBlank { "Untitled Note" },
                    targetContent,
                    System.currentTimeMillis()
                )
                syncIndex(targetNoteId, targetContent)

                if (move) {
                    val nextSource = BlockTransfer.remove(
                        rows = noteBlockDao.getBlocksForNote(sourceNoteId),
                        ids = incoming.map { it.id }.toSet()
                    )
                    noteBlockDao.replaceBlocks(sourceNoteId, nextSource)
                    syncBlockRefs(sourceNoteId, nextSource)
                    val sourceContent = BlockPersistence.serializeRows(nextSource)
                    noteDao.updateContentRow(
                        sourceNoteId,
                        sourceNote.title.ifBlank { "Untitled Note" },
                        sourceContent,
                        System.currentTimeMillis()
                    )
                    syncIndex(sourceNoteId, sourceContent)
                }
                incoming.size
            }

            val count = if (database != null) {
                database.withTransaction { action() }
            } else {
                action()
            }

            if (count > 0) {
                _blockTransfers.tryEmit(
                    BlockTransferCompleted(sourceNoteId, targetNoteId, move, count)
                )
            }
            count
        }
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
            noteDao.updateTitleRow(id, title, System.currentTimeMillis())
        }
    }

    suspend fun softDeleteNote(id: String, deleted: Boolean = true) = withContext(Dispatchers.IO) {
        // Sub-pages follow their parent into the recycle bin and back out.
        val now = System.currentTimeMillis()
        subtreeIds(id).forEach { noteDao.setDeleted(it, deleted, now) }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    /**
     * Remembers the page [noteId] was left on, so the canvas can reopen there.
     *
     * Negative values are ignored (a caller that has no page yet must not pin
     * the note to a bogus index) and the write is a narrow UPDATE of
     * `lastPageIndex` only: `updatedAt` deliberately does not move, otherwise
     * merely opening and closing a note would reorder the library.
     */
    suspend fun saveLastPage(noteId: String, pageIndex: Int) = withContext(Dispatchers.IO) {
        if (pageIndex < 0) return@withContext
        noteDao.updateLastPage(noteId, pageIndex.coerceAtLeast(0))
    }

    suspend fun permanentlyDeleteNote(id: String) = withContext(Dispatchers.IO) {
        // Sub-pages go with their parent, deepest first.
        for (noteId in subtreeIds(id).reversed()) {
            // The references into the note go with it, otherwise the table grows
            // with every permanent delete.
            blockRefDao.deleteForNote(noteId)
            noteBlockDao.deleteForNote(noteId)
            noteTagDao.deleteForNote(noteId)
            noteRefDao.deleteForNote(noteId)
            pageDao.deleteForNote(noteId)
            noteDao.delete(noteId)
            fileStore.deleteNoteFiles(noteId)
        }
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

    suspend fun getPagesForNoteSync(noteId: String): List<PageEntity> = withContext(Dispatchers.IO) {
        pageDao.getPagesForNoteSync(noteId)
    }

    suspend fun getPage(noteId: String, pageIndex: Int): PageEntity? = withContext(Dispatchers.IO) {
        pageDao.getPage(noteId, pageIndex)
    }

    suspend fun getNextPageIndex(noteId: String): Int = withContext(Dispatchers.IO) {
        pageDao.getPagesForNoteSync(noteId).size
    }

    suspend fun appendPdfPagesBatch(
        noteId: String,
        startNotePageIndex: Int,
        pdfSourcePath: String,
        pdfPageOffset: Int,
        batchCount: Int,
        reuseFirstPage: Boolean,
        newTotalPageCount: Int
    ) = withContext(Dispatchers.IO) {
        if (batchCount <= 0) return@withContext
        val action: suspend () -> Unit = {
            val now = System.currentTimeMillis()
            val toInsert = ArrayList<PageEntity>(batchCount)
            for (k in 0 until batchCount) {
                val pdfIdx = pdfPageOffset + k
                if (reuseFirstPage && pdfIdx == 0) {
                    pageDao.setPdfSource(noteId, 0, pdfSourcePath, 0, now)
                } else {
                    val notePageIdx = if (reuseFirstPage) pdfIdx else startNotePageIndex + pdfIdx
                    toInsert.add(
                        PageEntity(
                            id = UUID.randomUUID().toString(),
                            noteId = noteId,
                            index = notePageIdx,
                            bitmapPath = fileStore.getPageFile(noteId, notePageIdx).absolutePath,
                            paperPattern = 0,
                            pdfSourcePath = pdfSourcePath,
                            pdfPageIndex = pdfIdx,
                            updatedAt = now
                        )
                    )
                }
            }
            if (toInsert.isNotEmpty()) {
                pageDao.insertAll(toInsert)
            }
            noteDao.updatePageCount(noteId, newTotalPageCount, now)
        }
        if (database != null) database.withTransaction { action() } else action()
    }

    suspend fun rollbackAppendedPdfPages(noteId: String, fromIndex: Int) = withContext(Dispatchers.IO) {
        val safeFrom = fromIndex.coerceAtLeast(0)
        val action: suspend () -> Unit = {
            pageDao.deleteFromIndex(noteId, safeFrom)
            noteDao.updatePageCount(noteId, safeFrom, System.currentTimeMillis())
        }
        if (database != null) database.withTransaction { action() } else action()
        fileStore.deletePageFile(noteId, safeFrom)
    }

    suspend fun insertPage(noteId: String, atIndex: Int): PageEntity = withContext(Dispatchers.IO) {
        val existing = pageDao.getPagesForNoteSync(noteId).sortedBy { it.index }
        val targetIndex = atIndex.coerceIn(0, existing.size)
        val now = System.currentTimeMillis()
        val inheritedPattern = existing.firstOrNull { it.index == targetIndex }?.paperPattern
            ?: existing.lastOrNull()?.paperPattern
            ?: 0

        // If targetIndex < existing.size, shift existing pages backwards
        if (targetIndex < existing.size) {
            // 1. Shift files backwards from highest existing index down to targetIndex
            for (page in existing.filter { it.index >= targetIndex }.sortedByDescending { it.index }) {
                fileStore.renamePageFile(noteId, page.index, page.index + 1)
            }
        }
        // Clean any leftover files at targetIndex
        fileStore.deletePageFile(noteId, targetIndex)

        val newPage = PageEntity(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            index = targetIndex,
            bitmapPath = fileStore.getPageFile(noteId, targetIndex).absolutePath,
            // The new sheet looks like its neighbours: it inherits the paper
            // pattern of the page it is inserted in front of.
            paperPattern = inheritedPattern,
            pdfSourcePath = "",
            pdfPageIndex = -1,
            updatedAt = now
        )
        val dbAction: suspend () -> Unit = {
            if (targetIndex < existing.size) {
                for (page in existing.filter { it.index >= targetIndex }.sortedByDescending { it.index }) {
                    val shiftedIndex = page.index + 1
                    val updated = page.copy(
                        index = shiftedIndex,
                        bitmapPath = fileStore.getPageFile(noteId, shiftedIndex).absolutePath,
                        updatedAt = now
                    )
                    pageDao.update(updated)
                }
            }
            pageDao.insert(newPage)
            noteDao.updatePageCount(noteId, existing.size + 1, now)
        }
        if (database != null) database.withTransaction { dbAction() } else dbAction()
        newPage
    }

    suspend fun addPage(noteId: String): PageEntity = withContext(Dispatchers.IO) {
        val existing = pageDao.getPagesForNoteSync(noteId)
        insertPage(noteId, existing.size)
    }

    suspend fun deletePage(noteId: String, pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesForNoteSync(noteId).sortedBy { it.index }.toMutableList()
        val targetPos = pages.indexOfFirst { it.index == pageIndex }
        if (pages.size <= 1 || targetPos < 0) {
            return@withContext false
        }
        val target = pages.removeAt(targetPos)
        val now = System.currentTimeMillis()
        val dbAction: suspend () -> Unit = {
            pageDao.delete(target.id)
            pages.forEachIndexed { idx, page ->
                if (page.index != idx) {
                    val updated = page.copy(
                        index = idx,
                        bitmapPath = fileStore.getPageFile(noteId, idx).absolutePath,
                        updatedAt = now
                    )
                    pageDao.update(updated)
                }
            }
            noteDao.updatePageCount(noteId, pages.size, now)
        }
        if (database != null) database.withTransaction { dbAction() } else dbAction()

        fileStore.deletePageFile(noteId, target.index)
        pages.forEachIndexed { idx, page ->
            if (page.index != idx) {
                fileStore.renamePageFile(noteId, page.index, idx)
            }
        }
        true
    }

    suspend fun loadPageBitmap(noteId: String, pageIndex: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val file = fileStore.getPageFile(noteId, pageIndex)
        personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file)
    }

    /**
     * Composites the continuous-mode annotation layers (`page_{index}_annot_{layer}.png`)
     * of a page into one transparent [width]x[height] bitmap, so single-page mode can show
     * them. It is a read-only overlay: it is never merged into the page image, so the same
     * strokes are not stored twice. Returns null when the page has no annotation layers.
     */
    suspend fun loadAnnotationOverlay(
        noteId: String,
        pageIndex: Int,
        width: Int,
        height: Int,
        pageType: String = "a4"
    ): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        if (width <= 0 || height <= 0) return@withContext null
        val metaFile = fileStore.getAnnotationMetaFile(noteId, pageIndex)
        val stack = if (metaFile.exists()) {
            try {
                personal.notes.touch.ui.canvas.PageLayerMeta.decode(metaFile.readText())
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val visibleIndices = stack?.visibleLayers()?.map { it.index }?.toSet()
        val prefix = "page_${pageIndex}_annot_"
        val annotFiles = fileStore.getLayersDir(noteId).listFiles()
            ?.mapNotNull { file ->
                if (!file.name.startsWith(prefix) || !file.name.endsWith(".png")) return@mapNotNull null
                val layerIdx = file.name.removePrefix(prefix).removeSuffix(".png").toIntOrNull()
                    ?: return@mapNotNull null
                if (visibleIndices != null && layerIdx !in visibleIndices) return@mapNotNull null
                layerIdx to file
            }
            ?.sortedBy { it.first }
            ?.map { it.second }
            ?: emptyList()
        if (annotFiles.isEmpty()) return@withContext null

        val target = personal.notes.touch.util.BitmapUtils.createSafeBitmap(width, height)
        val canvas = android.graphics.Canvas(target)
        val paint = android.graphics.Paint(
            android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
        )
        val dst = android.graphics.Rect(0, 0, width, height)
        val (leftFrac, rightFrac) = PdfModeReconciliationPolicy.singlePageCropFractions(pageType)
        var drawn = false
        for (file in annotFiles) {
            val layerBmp = personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file) ?: continue
            val srcLeft = (layerBmp.width * leftFrac).roundToInt().coerceIn(0, layerBmp.width)
            val srcRight = (layerBmp.width * rightFrac).roundToInt().coerceIn(srcLeft, layerBmp.width)
            canvas.drawBitmap(layerBmp, android.graphics.Rect(srcLeft, 0, srcRight, layerBmp.height), dst, paint)
            layerBmp.recycle()
            drawn = true
        }
        if (!drawn) {
            target.recycle()
            return@withContext null
        }
        target
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
                // A narrow touch: the whole row must not be written back,
                // or the page the user is on would be lost.
                noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
            } catch (e: Exception) {
                // Ignore if the note row is gone
            }
        }
        ok
    }

    /**
     * Writes the annotation layer of a page (layer 1 is the base layer).
     *
     * The layer is the persisted form of the writing: it is as wide as the
     * reader's page box (the page plus the margin on both sides) and as tall
     * as the page, so the notes in the margin are part of it. The page image
     * (the PDF render, the page PNG) is never touched by a stroke.
     */
    suspend fun savePageLayer(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int,
        bitmap: android.graphics.Bitmap
    ): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.getAnnotationLayerFile(noteId, pageIndex, layerIndex)
        val ok = personal.notes.touch.util.BitmapUtils.saveBitmapToPng(bitmap, file)
        if (ok) {
            try {
                // A narrow touch: the whole row must not be written back,
                // or the page the user is on would be lost.
                noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
            } catch (e: Exception) {
                // Ignore if the note row is gone
            }
        }
        ok
    }

    suspend fun touchNoteUpdatedAt(noteId: String) = withContext(Dispatchers.IO) {
        try {
            noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
        } catch (_: Exception) {
            // Ignore if the note row is gone
        }
    }

    /**
     * Writes the metadata of the annotation layers of a page.
     *
     * The metadata names the layers of the page (their number, their name
     * and whether they are hidden) and the layer the writing goes on.
     */
    private fun writeTextAtomically(noteId: String, target: File, content: String): Boolean {
        var tmp: File? = null
        return try {
            target.parentFile?.mkdirs()
            val tmpFile = File(fileStore.getTmpDir(noteId), "${target.name}.${System.nanoTime()}.tmp")
            tmp = tmpFile
            tmpFile.writeText(content)
            if (!tmpFile.renameTo(target)) {
                if (target.exists()) target.delete()
                if (!tmpFile.renameTo(target)) {
                    target.writeText(content)
                    tmpFile.delete()
                }
            }
            true
        } catch (e: Exception) {
            tmp?.let { if (it.exists()) it.delete() }
            e.printStackTrace()
            false
        }
    }

    suspend fun savePageLayerMeta(
        noteId: String,
        pageIndex: Int,
        json: String
    ): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.getAnnotationMetaFile(noteId, pageIndex)
        val ok = writeTextAtomically(noteId, file, json)
        if (ok) {
            try {
                noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
            } catch (_: Exception) {}
        }
        ok
    }

    /**
     * Writes ONE tile of an annotation layer (C50).
     *
     * A layer that is cut into tiles has one PNG per tile: only the tiles
     * that changed are written, so a save no longer encodes the whole
     * layer. The note row is touched like every other write of a layer.
     */
    suspend fun savePageLayerTile(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int,
        tx: Int,
        ty: Int,
        bitmap: android.graphics.Bitmap
    ): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.getAnnotationLayerTileFile(noteId, pageIndex, layerIndex, tx, ty)
        val ok = personal.notes.touch.util.BitmapUtils.saveBitmapToPng(bitmap, file)
        if (ok) {
            try {
                // A narrow touch: the whole row must not be written back,
                // or the page the user is on would be lost.
                noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
            } catch (e: Exception) {
                // Ignore if the note row is gone
            }
        }
        ok
    }

    /**
     * Reads the tiles of a layer back from the disk.
     *
     * The metadata says which tiles exist; every one of them is decoded
     * with its alpha (ARGB_8888), so the ink keeps its transparency. The
     * answer holds the place of the tile and its pixels.
     *
     * A layer without tile information (a legacy layer) has no tiles: the
     * answer is empty and the layer is read from its full-size PNG.
     */
    suspend fun loadPageLayerTiles(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int,
        meta: personal.notes.touch.ui.canvas.PageLayerMeta.LayerTilesInfo?
    ): List<Pair<Pair<Int, Int>, android.graphics.Bitmap>> = withContext(Dispatchers.IO) {
        val info = meta ?: return@withContext emptyList()
        val out = mutableListOf<Pair<Pair<Int, Int>, android.graphics.Bitmap>>()
        for (position in info.positions()) {
            val file = fileStore.getAnnotationLayerTileFile(
                noteId, pageIndex, layerIndex, position.first, position.second
            )
            val bitmap = personal.notes.touch.util.BitmapUtils.loadBitmapFromFile(file) ?: continue
            out.add(Pair(position, bitmap))
        }
        out
    }

    internal suspend fun savePageLayerOps(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int,
        ops: List<personal.notes.touch.ui.canvas.InkOp>
    ): Boolean = withContext(Dispatchers.IO) {
        val file = fileStore.getAnnotationLayerOpsFile(noteId, pageIndex, layerIndex)
        val encoded = personal.notes.touch.ui.canvas.ContinuousOpCodec.encode(ops)
        val ok = writeTextAtomically(noteId, file, encoded)
        if (ok) {
            try {
                noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
            } catch (_: Exception) {}
        }
        ok
    }

    internal suspend fun loadPageLayerOps(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int
    ): List<personal.notes.touch.ui.canvas.InkOp>? = withContext(Dispatchers.IO) {
        val file = fileStore.getAnnotationLayerOpsFile(noteId, pageIndex, layerIndex)
        if (!file.exists()) return@withContext null
        try {
            personal.notes.touch.ui.canvas.ContinuousOpCodec.decode(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal suspend fun deletePageLayerOps(noteId: String, pageIndex: Int, layerIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val deleted = fileStore.getAnnotationLayerOpsFile(noteId, pageIndex, layerIndex).delete()
                if (deleted) {
                    try {
                        noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
                    } catch (_: Exception) {}
                }
                deleted
            } catch (e: Exception) {
                false
            }
        }

    suspend fun deletePageLayerTile(
        noteId: String,
        pageIndex: Int,
        layerIndex: Int,
        tx: Int,
        ty: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = fileStore.getAnnotationLayerTileFile(noteId, pageIndex, layerIndex, tx, ty)
            val deleted = if (file.exists()) file.delete() else false
            if (deleted) {
                try {
                    noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
                } catch (_: Exception) {}
            }
            deleted
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes the tile files of a layer (C50).
     *
     * The full-size PNG of the layer is not touched: it belongs to the
     * legacy form of a layer.
     */
    suspend fun deletePageLayerTiles(noteId: String, pageIndex: Int, layerIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            val dir = fileStore.getLayersDir(noteId)
            val prefix = "page_${pageIndex}_annot_${layerIndex}_t"
            var ok = true
            var anyDeleted = false
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix) && file.name.endsWith(".png")) {
                    if (file.delete()) {
                        anyDeleted = true
                    } else {
                        ok = false
                    }
                }
            }
            if (anyDeleted) {
                try {
                    noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
                } catch (_: Exception) {}
            }
            ok
        }

    /** Persist the per-page paper pattern (PaperPattern.code) to the page row. */
    suspend fun savePagePattern(noteId: String, pageIndex: Int, patternCode: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val page = pageDao.getPage(noteId, pageIndex) ?: return@withContext false
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
            pageDao.getPage(noteId, pageIndex)?.paperPattern ?: 0
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
            personal.notes.touch.util.BitmapUtils.invalidateImageCache(file)
            try {
                // A narrow touch: the whole row must not be written back,
                // or the page the user is on would be lost.
                noteDao.touchUpdatedAt(noteId, System.currentTimeMillis())
            } catch (e: Exception) {
                // Ignore if the note row is gone
            }
        }
        ok
    }
}
