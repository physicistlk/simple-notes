package personal.notes.touch.e2e

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import personal.notes.touch.data.db.dao.BlockBacklink
import personal.notes.touch.data.db.dao.BlockCandidate
import personal.notes.touch.data.db.dao.BlockRefDao
import personal.notes.touch.data.db.dao.BlockRefTarget
import personal.notes.touch.data.db.dao.FolderDao
import personal.notes.touch.data.db.dao.NoteBlockDao
import personal.notes.touch.data.db.dao.NoteDao
import personal.notes.touch.data.db.dao.NoteGraphEdge
import personal.notes.touch.data.db.dao.NoteRefDao
import personal.notes.touch.data.db.dao.NoteTagDao
import personal.notes.touch.data.db.dao.PageDao
import personal.notes.touch.data.db.dao.RefTarget
import personal.notes.touch.data.db.dao.SettingsDao
import personal.notes.touch.data.db.dao.TagCount
import personal.notes.touch.data.db.dao.TemplateDao
import personal.notes.touch.data.db.entity.BlockRefEntity
import personal.notes.touch.data.db.entity.NoteBlockEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.NoteRefEntity
import personal.notes.touch.data.db.entity.NoteTagEntity
import personal.notes.touch.data.db.entity.PageEntity
import personal.notes.touch.data.db.entity.SettingsEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.data.repo.SettingsRepository
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPatch
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.LayerTileGrid
import personal.notes.touch.ui.canvas.PageLayer
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack
import personal.notes.touch.ui.canvas.TocItem
import java.io.File
import java.lang.reflect.Proxy

/**
 * Shared opaque-box E2E test harness for `touchnotes-clone` running on plain JVM.
 *
 * Strictly contains only workspace scaffolding, repo-root discovery, entity/op
 * fixture builders, and disk round-trip helpers. Never duplicates production
 * algorithms or invokes Android framework / JNI stubs.
 */
internal object E2eTestHarness {

    /** Forbidden double-bang operator built without literal token in source. */
    val FORBIDDEN_NOT_NULL_OP: String = "\u0021\u0021"

    val ALL_15_FEATURE_IDS: List<String> = listOf(
        "PDF-01", "PDF-02", "PDF-03", "PDF-04", "PDF-05", "PDF-06", "PDF-07", "PDF-08",
        "PDF-QOL-1", "PDF-QOL-2", "PDF-QOL-3", "PDF-QOL-4", "PDF-QOL-5", "PDF-QOL-6", "PDF-QOL-7"
    )

    fun readRepoFile(relativePath: String): String =
        File(locateRepoRoot(), relativePath).readText()

    fun extractAuditSection(auditText: String, featureId: String): String {
        val header = "### `$featureId`"
        val start = auditText.indexOf(header)
        if (start < 0) return ""
        val next = auditText.indexOf("\n### `", start + header.length)
        return if (next < 0) auditText.substring(start) else auditText.substring(start, next)
    }

    /**
     * Plain-JVM `NoteRepository` + `NoteFileStore(filesDir)` backed by in-memory
     * `Proxy` DAOs (matching the verified `NoteRepositoryTouchTest` pattern).
     */
    class InMemoryNoteEnv(val filesDir: File) {
        val fileStore = NoteFileStore(filesDir)
        val notes = LinkedHashMap<String, NoteEntity>()
        val pages = mutableListOf<PageEntity>()
        val touchedUpdatedAtCalls = mutableListOf<Pair<String, Long>>()
        val insertAllBatches = mutableListOf<List<PageEntity>>()
        val setPdfSourceCalls = mutableListOf<Triple<String, Int, String>>()
        val deletedFromIndexCalls = mutableListOf<Pair<String, Int>>()
        private val revisionFlow = MutableStateFlow(0)
        private val settingsState = MutableStateFlow<SettingsEntity?>(SettingsEntity())

        private val noteDao: NoteDao = Proxy.newProxyInstance(
            NoteDao::class.java.classLoader,
            arrayOf(NoteDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> {
                    val entity = args?.getOrNull(0) as? NoteEntity
                    if (entity != null) {
                        notes[entity.id] = entity
                        revisionFlow.value++
                    }
                    Unit
                }
                "getById" -> {
                    val id = args?.getOrNull(0) as? String
                    if (id != null) notes[id] else null
                }
                "getByIdFlow" -> {
                    val id = args?.getOrNull(0) as? String
                    revisionFlow.map { if (id != null) notes[id] else null }
                }
                "getAll" -> {
                    revisionFlow.map {
                        notes.values.filter { !it.deleted && it.parentNoteId == null }
                            .sortedByDescending { it.updatedAt }
                    }
                }
                "getChildIds" -> {
                    val parentId = args?.getOrNull(0) as? String
                    notes.values.filter { it.parentNoteId == parentId }.map { it.id }
                }
                "touchUpdatedAt" -> {
                    val id = args?.getOrNull(0) as? String
                    val ts = args?.getOrNull(1) as? Long
                    if (id != null && ts != null) {
                        touchedUpdatedAtCalls.add(id to ts)
                        notes[id]?.let { notes[id] = it.copy(updatedAt = ts) }
                        revisionFlow.value++
                    }
                    Unit
                }
                "updatePageCount" -> {
                    val id = args?.getOrNull(0) as? String
                    val count = args?.getOrNull(1) as? Int
                    val ts = args?.getOrNull(2) as? Long
                    if (id != null && count != null && ts != null) {
                        notes[id]?.let { notes[id] = it.copy(pageCount = count, updatedAt = ts) }
                        revisionFlow.value++
                    }
                    Unit
                }
                "updateLastPage" -> {
                    val id = args?.getOrNull(0) as? String
                    val pageIndex = args?.getOrNull(1) as? Int
                    if (id != null && pageIndex != null) {
                        notes[id]?.let { notes[id] = it.copy(lastPageIndex = pageIndex) }
                        revisionFlow.value++
                    }
                    Unit
                }
                "setDeleted" -> {
                    val id = args?.getOrNull(0) as? String
                    val deleted = args?.getOrNull(1) as? Boolean
                    val ts = args?.getOrNull(2) as? Long ?: System.currentTimeMillis()
                    if (id != null && deleted != null) {
                        notes[id]?.let { notes[id] = it.copy(deleted = deleted, updatedAt = ts) }
                        revisionFlow.value++
                    }
                    Unit
                }
                "updateTitleRow" -> {
                    val id = args?.getOrNull(0) as? String
                    val title = args?.getOrNull(1) as? String
                    val ts = args?.getOrNull(2) as? Long ?: System.currentTimeMillis()
                    if (id != null && title != null) {
                        notes[id]?.let { notes[id] = it.copy(title = title, updatedAt = ts) }
                        revisionFlow.value++
                    }
                    Unit
                }
                "delete" -> {
                    val id = args?.getOrNull(0) as? String
                    if (id != null) {
                        notes.remove(id)
                        revisionFlow.value++
                    }
                    Unit
                }
                "getAllLive" -> notes.values.filter { !it.deleted }
                "count" -> notes.values.count { !it.deleted }
                else -> defaultProxyReturn(method.returnType)
            }
        } as NoteDao

        private val pageDao: PageDao = Proxy.newProxyInstance(
            PageDao::class.java.classLoader,
            arrayOf(PageDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPagesForNote" -> {
                    val noteId = args?.getOrNull(0) as? String
                    revisionFlow.map { pages.filter { it.noteId == noteId }.sortedBy { it.index } }
                }
                "getPagesForNoteSync" -> {
                    val noteId = args?.getOrNull(0) as? String
                    pages.filter { it.noteId == noteId }.sortedBy { it.index }
                }
                "getPage" -> {
                    val noteId = args?.getOrNull(0) as? String
                    val idx = args?.getOrNull(1) as? Int
                    pages.firstOrNull { it.noteId == noteId && it.index == idx }
                }
                "getById" -> {
                    val id = args?.getOrNull(0) as? String
                    pages.firstOrNull { it.id == id }
                }
                "insert" -> {
                    val p = args?.getOrNull(0) as? PageEntity
                    if (p != null) {
                        pages.removeAll { it.id == p.id }
                        pages.add(p)
                        revisionFlow.value++
                    }
                    Unit
                }
                "insertAll" -> {
                    @Suppress("UNCHECKED_CAST")
                    val batch = (args?.getOrNull(0) as? List<PageEntity>).orEmpty()
                    insertAllBatches.add(batch)
                    for (p in batch) {
                        pages.removeAll { it.id == p.id }
                        pages.add(p)
                    }
                    revisionFlow.value++
                    Unit
                }
                "update" -> {
                    val p = args?.getOrNull(0) as? PageEntity
                    if (p != null) {
                        val idx = pages.indexOfFirst { it.id == p.id }
                        if (idx >= 0) pages[idx] = p else pages.add(p)
                        revisionFlow.value++
                    }
                    Unit
                }
                "delete" -> {
                    val id = args?.getOrNull(0) as? String
                    if (id != null) {
                        pages.removeAll { it.id == id }
                        revisionFlow.value++
                    }
                    Unit
                }
                "deleteForNote" -> {
                    val noteId = args?.getOrNull(0) as? String
                    if (noteId != null) {
                        pages.removeAll { it.noteId == noteId }
                        revisionFlow.value++
                    }
                    Unit
                }
                "deleteFromIndex" -> {
                    val noteId = args?.getOrNull(0) as? String
                    val fromIdx = args?.getOrNull(1) as? Int
                    if (noteId != null && fromIdx != null) {
                        deletedFromIndexCalls.add(noteId to fromIdx)
                        pages.removeAll { it.noteId == noteId && it.index >= fromIdx }
                        revisionFlow.value++
                    }
                    Unit
                }
                "setPdfSource" -> {
                    val noteId = args?.getOrNull(0) as? String
                    val idx = args?.getOrNull(1) as? Int
                    val src = args?.getOrNull(2) as? String
                    val pdfIdx = args?.getOrNull(3) as? Int
                    val updatedAt = args?.getOrNull(4) as? Long
                    if (noteId != null && idx != null && src != null && pdfIdx != null && updatedAt != null) {
                        setPdfSourceCalls.add(Triple(noteId, idx, src))
                        val pos = pages.indexOfFirst { it.noteId == noteId && it.index == idx }
                        if (pos >= 0) {
                            pages[pos] = pages[pos].copy(
                                pdfSourcePath = src,
                                pdfPageIndex = pdfIdx,
                                updatedAt = updatedAt
                            )
                            revisionFlow.value++
                        }
                    }
                    Unit
                }
                else -> defaultProxyReturn(method.returnType)
            }
        } as PageDao

        private val settingsDao: SettingsDao = Proxy.newProxyInstance(
            SettingsDao::class.java.classLoader,
            arrayOf(SettingsDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getSettings" -> settingsState
                "getSettingsDirect" -> settingsState.value
                "insertOrUpdate", "update" -> {
                    val s = args?.getOrNull(0) as? SettingsEntity
                    if (s != null) settingsState.value = s
                    Unit
                }
                else -> defaultProxyReturn(method.returnType)
            }
        } as SettingsDao

        val settingsRepository = SettingsRepository(settingsDao)

        val repository: NoteRepository = NoteRepository(
            noteDao = noteDao,
            noteBlockDao = stubNoteBlockDao(),
            noteTagDao = stubNoteTagDao(),
            noteRefDao = stubNoteRefDao(),
            folderDao = stubInterfaceDao(FolderDao::class.java),
            pageDao = pageDao,
            templateDao = stubInterfaceDao(TemplateDao::class.java),
            blockRefDao = stubBlockRefDao(),
            fileStore = fileStore,
            database = null
        )

        fun seedNoteAndPages(note: NoteEntity, notePages: List<PageEntity>) {
            notes[note.id] = note
            pages.removeAll { it.noteId == note.id }
            pages.addAll(notePages)
            revisionFlow.value++
        }
    }

    fun createInMemoryNoteEnv(filesDir: File): InMemoryNoteEnv = InMemoryNoteEnv(filesDir)

    private fun defaultProxyReturn(returnType: Class<*>): Any = when (returnType) {
        Void.TYPE -> Unit
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        else -> Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stubInterfaceDao(clazz: Class<T>): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, _ ->
            defaultProxyReturn(method.returnType)
        } as T

    private fun stubNoteBlockDao() = object : NoteBlockDao() {
        override suspend fun getBlocksForNote(noteId: String): List<NoteBlockEntity> = emptyList()
        override suspend fun countForNote(noteId: String): Int = 0
        override suspend fun insertAll(blocks: List<NoteBlockEntity>) = Unit
        override suspend fun deleteForNote(noteId: String) = Unit
        override suspend fun deleteForRecycledNotes(): Int = 0
        override suspend fun searchBlocks(query: String, excludeBlockId: String, limit: Int): List<BlockCandidate> = emptyList()
        override fun observeQueryCandidates(): Flow<List<BlockCandidate>> = emptyFlow()
        override suspend fun getAllForLiveNotes(): List<NoteBlockEntity> = emptyList()
    }

    private fun stubNoteTagDao() = object : NoteTagDao() {
        override fun observeAll(): Flow<List<NoteTagEntity>> = emptyFlow()
        override suspend fun getForNote(noteId: String): List<NoteTagEntity> = emptyList()
        override suspend fun insertAll(tags: List<NoteTagEntity>) = Unit
        override suspend fun deleteForNote(noteId: String) = Unit
        override suspend fun deleteForRecycledNotes(): Int = 0
        override suspend fun deleteOrphans(): Int = 0
        override fun observeTagCounts(): Flow<List<TagCount>> = emptyFlow()
    }

    private fun stubNoteRefDao() = object : NoteRefDao() {
        override suspend fun insertAll(refs: List<NoteRefEntity>) = Unit
        override suspend fun getForNote(noteId: String): List<NoteRefEntity> = emptyList()
        override suspend fun deleteForNote(noteId: String) = Unit
        override suspend fun deleteForRecycledNotes(): Int = 0
        override suspend fun deleteOrphans(): Int = 0
        override fun observeBacklinks(targetKey: String, selfId: String): Flow<List<NoteEntity>> = emptyFlow()
        override fun observeOutgoing(noteId: String): Flow<List<RefTarget>> = emptyFlow()
    }

    private fun stubBlockRefDao() = object : BlockRefDao() {
        override suspend fun insertAll(refs: List<BlockRefEntity>) = Unit
        override suspend fun getForNote(noteId: String): List<BlockRefEntity> = emptyList()
        override suspend fun deleteForNote(noteId: String) = Unit
        override suspend fun deleteForRecycledNotes(): Int = 0
        override suspend fun getGraphEdges(): List<NoteGraphEdge> = emptyList()
        override fun observeOutgoing(noteId: String): Flow<List<BlockRefTarget>> = emptyFlow()
        override fun observeBacklinks(noteId: String): Flow<List<BlockBacklink>> = emptyFlow()
    }

    /**
     * Resolves the repository root regardless of whether `./gradlew` or an IDE
     * invoked tests from `<repo>` or `<repo>/app`.
     */
    fun locateRepoRoot(startDir: File = File(System.getProperty("user.dir") ?: ".")): File {
        var probe: File? = startDir.absoluteFile
        while (probe != null) {
            if (File(probe, "settings.gradle").isFile &&
                File(probe, "app/src/main/AndroidManifest.xml").isFile
            ) {
                return probe
            }
            probe = probe.parentFile
        }
        error("Repository root not found walking up from ${startDir.absolutePath}")
    }

    /** Creates an isolated `filesDir/note/<noteId>/layers/` directory tree. */
    fun createTempNoteWorkspace(rootDir: File, noteId: String = "e2e-note-1"): NoteDiskWorkspace {
        val noteDir = File(File(rootDir, "note"), noteId).apply { mkdirs() }
        val layersDir = File(noteDir, "layers").apply { mkdirs() }
        val tmpDir = File(noteDir, "tmp").apply { mkdirs() }
        return NoteDiskWorkspace(rootDir, noteId, noteDir, layersDir, tmpDir)
    }

    /** Genuine Room NoteEntity + PageEntity fixtures matching schema v15. */
    fun pdfNoteFixture(
        noteId: String = "pdf-note-1",
        title: String = "Lecture 01: Architecture",
        pageCount: Int = 3,
        pdfRelativePath: String = "note/$noteId/pdf_1700000000000.pdf"
    ): Pair<NoteEntity, List<PageEntity>> {
        val note = NoteEntity(
            id = noteId,
            title = title,
            pageCount = pageCount,
            pageType = "pdf"
        )
        val pages = (0 until pageCount).map { idx ->
            PageEntity(
                id = "$noteId-p$idx",
                noteId = noteId,
                index = idx,
                bitmapPath = "note/$noteId/page_$idx.png",
                pdfSourcePath = pdfRelativePath,
                pdfPageIndex = idx
            )
        }
        return note to pages
    }

    /** Genuine normalized ContinuousStroke + InkOp.Stroke (`shape = null` stays Android-Path-free). */
    fun strokeOp(
        layer: PageLayer,
        points: List<InkPoint> = listOf(
            InkPoint(0.2f, 0.25f, 0.005f),
            InkPoint(0.6f, 0.75f, 0.005f)
        ),
        tool: CanvasTool = CanvasTool.PEN,
        color: Int = 0xFF1E1E1E.toInt(),
        widthNorm: Float = 0.005f,
        autoShape: Boolean = false
    ): InkOp.Stroke {
        val stroke = ContinuousStroke(tool, color, widthNorm, autoShape, points.toMutableList())
        return InkOp.Stroke(stroke, shape = null, layer = layer)
    }

    /** Genuine InkOp.Erase with pure-JVM InkPatch (`target = null`). */
    fun eraseOp(
        layer: PageLayer,
        points: List<InkPoint> = listOf(
            InkPoint(0.2f, 0.25f, 0.02f),
            InkPoint(0.3f, 0.35f, 0.02f)
        ),
        patches: List<InkPatch> = listOf(
            InkPatch(0, 0, 0, 0, 4, 4, IntArray(16) { 1 }, IntArray(16) { 2 }, target = null)
        )
    ): InkOp.Erase {
        val stroke = ContinuousStroke(CanvasTool.ERASER, 0, 0.02f, false, points.toMutableList())
        return InkOp.Erase(stroke, patches, layer)
    }

    fun tocFixture(): List<TocItem> = listOf(
        TocItem(id = "toc-0", title = "Cover", pageIndex = 0, level = 0),
        TocItem(id = "toc-1", title = "Section 1.1", pageIndex = 2, level = 1),
        TocItem(id = "toc-2", title = "Sub-section 1.1.1", pageIndex = 3, level = 2)
    )

    fun scanKtFilesForToken(dir: File, token: String): List<String> {
        if (!dir.exists()) return emptyList()
        val hits = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (line.contains(token)) {
                        hits.add("${file.path}:${idx + 1}: $line")
                    }
                }
            }
        return hits
    }

    data class NoteDiskWorkspace(
        val filesDir: File,
        val noteId: String,
        val noteDir: File,
        val layersDir: File,
        val tmpDir: File
    ) {
        fun annotMetaFile(pageIndex: Int): File =
            File(layersDir, "page_${pageIndex}_annot.json")

        fun annotOpsFile(pageIndex: Int, layerIndex: Int): File =
            File(layersDir, "page_${pageIndex}_annot_${layerIndex}_ops.json")

        fun annotTileFile(pageIndex: Int, layerIndex: Int, tx: Int, ty: Int): File =
            File(layersDir, "page_${pageIndex}_annot_${layerIndex}_${LayerTileGrid.tileName(tx, ty)}.png")

        fun pagePreviewFile(pageIndex: Int): File =
            File(noteDir, "page_${pageIndex}.png")

        fun strokesFile(pageIndex: Int): File =
            File(noteDir, "strokes_${pageIndex}.png")

        fun writeContinuousPage(
            pageIndex: Int,
            stack: PageLayerStack,
            opsByLayer: Map<Int, List<InkOp>>,
            tiles: Map<Int, PageLayerMeta.LayerTilesInfo> = emptyMap()
        ) {
            annotMetaFile(pageIndex).writeText(PageLayerMeta.encode(stack, tiles))
            for ((layerIdx, ops) in opsByLayer) {
                annotOpsFile(pageIndex, layerIdx).writeText(ContinuousOpCodec.encode(ops))
            }
        }

        fun readContinuousPage(pageIndex: Int): Pair<PageLayerStack, Map<Int, List<InkOp>>> {
            val metaText = annotMetaFile(pageIndex).readText()
            val stack = PageLayerMeta.decode(metaText) ?: PageLayerStack.base()
            val opsByLayer = stack.layers.associate { layer ->
                val f = annotOpsFile(pageIndex, layer.index)
                val decoded = if (f.exists()) ContinuousOpCodec.decode(f.readText()).orEmpty() else emptyList()
                layer.index to decoded
            }
            return stack to opsByLayer
        }
    }
}
