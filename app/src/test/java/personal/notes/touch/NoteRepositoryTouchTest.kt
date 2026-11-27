package personal.notes.touch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
import personal.notes.touch.data.db.dao.TagCount
import personal.notes.touch.data.db.dao.TemplateDao
import personal.notes.touch.data.db.entity.BlockRefEntity
import personal.notes.touch.data.db.entity.NoteBlockEntity
import personal.notes.touch.data.db.entity.NoteEntity
import personal.notes.touch.data.db.entity.NoteRefEntity
import personal.notes.touch.data.db.entity.NoteTagEntity
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.data.repo.NoteRepository
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.PageLayer
import java.lang.reflect.Proxy

/**
 * Pure-JVM unit tests verifying `PDF-06`: `NoteRepository` continuous annotation
 * persistence/deletion methods bump `NoteDao.touchUpdatedAt` so Home screen
 * library previews update reactively.
 */
class NoteRepositoryTouchTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Suppress("UNCHECKED_CAST")
    private fun <T> stubInterfaceDao(clazz: Class<T>): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, _ ->
            when (method.returnType) {
                Void.TYPE -> Unit
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> Unit
            }
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

    @Test
    fun layerOpsMetaAndTileWrites_touchNoteUpdatedAt() = runTest {
        val touchedCalls = mutableListOf<Pair<String, Long>>()
        val noteDao = Proxy.newProxyInstance(
            NoteDao::class.java.classLoader,
            arrayOf(NoteDao::class.java)
        ) { _, method, args ->
            if (method.name == "touchUpdatedAt" && args != null && args.size >= 2) {
                val id = args[0] as? String
                val ts = args[1] as? Long
                if (id != null && ts != null) {
                    touchedCalls.add(id to ts)
                }
            }
            Unit
        } as NoteDao

        val fileStore = NoteFileStore(tempFolder.newFolder("files"))
        val repo = NoteRepository(
            noteDao = noteDao,
            noteBlockDao = stubNoteBlockDao(),
            noteTagDao = stubNoteTagDao(),
            noteRefDao = stubNoteRefDao(),
            folderDao = stubInterfaceDao(FolderDao::class.java),
            pageDao = stubInterfaceDao(PageDao::class.java),
            templateDao = stubInterfaceDao(TemplateDao::class.java),
            blockRefDao = stubBlockRefDao(),
            fileStore = fileStore,
            database = null
        )

        val noteId = "pdf-note-1"
        val layer = PageLayer(1, "Layer1", true)
        val stroke = ContinuousStroke(
            tool = CanvasTool.PEN,
            color = 0xFF000000.toInt(),
            baseWidthNorm = 0.005f,
            points = mutableListOf(InkPoint(0.5f, 0.5f, 0.005f))
        )

        assertTrue(repo.savePageLayerOps(noteId, 0, 1, listOf(InkOp.Stroke(stroke, null, layer))))
        assertEquals(1, touchedCalls.size)
        assertEquals(noteId, touchedCalls.last().first)

        assertTrue(repo.savePageLayerMeta(noteId, 0, """{"version":1,"current":1,"layers":[]}"""))
        assertEquals(2, touchedCalls.size)

        val tileFile = fileStore.getAnnotationLayerTileFile(noteId, 0, 1, 0, 0)
        tileFile.parentFile?.mkdirs()
        tileFile.writeText("tile")
        assertTrue(repo.deletePageLayerTile(noteId, 0, 1, 0, 0))
        assertEquals(3, touchedCalls.size)

        val tile2 = fileStore.getAnnotationLayerTileFile(noteId, 0, 1, 1, 0)
        tile2.writeText("tile2")
        assertTrue(repo.deletePageLayerTiles(noteId, 0, 1))
        assertEquals(4, touchedCalls.size)

        assertTrue(repo.deletePageLayerOps(noteId, 0, 1))
        assertEquals(5, touchedCalls.size)

        repo.touchNoteUpdatedAt(noteId)
        assertEquals(6, touchedCalls.size)
        assertTrue(touchedCalls.all { it.first == noteId && it.second > 0L })
    }
}
