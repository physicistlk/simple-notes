package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import personal.notes.touch.data.files.NoteFileStore
import personal.notes.touch.util.PdfiumDocument
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

sealed class InfiniteCanvasItem {
    abstract val id: String
    abstract val centerX: Float
    abstract val centerY: Float
    abstract val width: Float
    abstract val height: Float
    abstract val rotationDeg: Float
    abstract val pageIndex: Int
    abstract val layerIndex: Int
    abstract val flipHorizontal: Boolean

    data class ImageItem(
        override val id: String,
        val fileName: String,
        override val centerX: Float,
        override val centerY: Float,
        override val width: Float,
        override val height: Float,
        override val rotationDeg: Float = 0f,
        override val pageIndex: Int = 0,
        override val layerIndex: Int = 1,
        override val flipHorizontal: Boolean = false
    ) : InfiniteCanvasItem()

    data class PdfPageItem(
        override val id: String,
        val pdfFileName: String,
        val pdfPageIndex: Int,
        override val centerX: Float,
        override val centerY: Float,
        override val width: Float,
        override val height: Float,
        override val rotationDeg: Float = 0f,
        override val pageIndex: Int = 0,
        override val layerIndex: Int = 1,
        override val flipHorizontal: Boolean = false
    ) : InfiniteCanvasItem()

    fun withTransform(
        centerX: Float = this.centerX,
        centerY: Float = this.centerY,
        width: Float = this.width,
        height: Float = this.height,
        rotationDeg: Float = this.rotationDeg,
        pageIndex: Int = this.pageIndex,
        layerIndex: Int = this.layerIndex,
        flipHorizontal: Boolean = this.flipHorizontal
    ): InfiniteCanvasItem = when (this) {
        is ImageItem -> copy(
            centerX = centerX,
            centerY = centerY,
            width = width,
            height = height,
            rotationDeg = rotationDeg,
            pageIndex = pageIndex,
            layerIndex = layerIndex,
            flipHorizontal = flipHorizontal
        )
        is PdfPageItem -> copy(
            centerX = centerX,
            centerY = centerY,
            width = width,
            height = height,
            rotationDeg = rotationDeg,
            pageIndex = pageIndex,
            layerIndex = layerIndex,
            flipHorizontal = flipHorizontal
        )
    }

    fun duplicateWithNewId(): InfiniteCanvasItem = when (this) {
        is ImageItem -> copy(id = UUID.randomUUID().toString())
        is PdfPageItem -> copy(id = UUID.randomUUID().toString())
    }
}

enum class InfiniteItemHandle {
    NONE,
    MOVE,
    DELETE,
    ROTATE,
    MIRROR,
    COPY,
    RESIZE,
    SCALE_LEFT,
    SCALE_RIGHT,
    SCALE_TOP,
    SCALE_BOTTOM
}

object InfiniteItemsStore {
    private const val ITEMS_FILE = "infinite_items.json"

    fun load(fileStore: NoteFileStore, noteId: String): List<InfiniteCanvasItem> {
        val file = File(fileStore.getNoteDir(noteId), ITEMS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val out = mutableListOf<InfiniteCanvasItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("type")
                val id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() }
                val cx = obj.optDouble("cx", 0.0).toFloat()
                val cy = obj.optDouble("cy", 0.0).toFloat()
                val w = obj.optDouble("w", 400.0).toFloat().coerceAtLeast(40f)
                val h = obj.optDouble("h", 400.0).toFloat().coerceAtLeast(40f)
                val rot = obj.optDouble("rot", 0.0).toFloat()
                val notePageIdx = obj.optInt("pageIndex", 0)
                val layerIdx = obj.optInt("layerIndex", 1)
                val flipH = obj.optBoolean("flipH", false)
                when (type) {
                    "image" -> {
                        val fileName = obj.optString("file")
                        if (fileName.isNotEmpty()) {
                            out.add(
                                InfiniteCanvasItem.ImageItem(
                                    id = id,
                                    fileName = fileName,
                                    centerX = cx,
                                    centerY = cy,
                                    width = w,
                                    height = h,
                                    rotationDeg = rot,
                                    pageIndex = notePageIdx,
                                    layerIndex = layerIdx,
                                    flipHorizontal = flipH
                                )
                            )
                        }
                    }
                    "pdf_page" -> {
                        val pdfFile = obj.optString("pdf")
                        val pageIdx = obj.optInt("page", 0)
                        if (pdfFile.isNotEmpty()) {
                            out.add(
                                InfiniteCanvasItem.PdfPageItem(
                                    id = id,
                                    pdfFileName = pdfFile,
                                    pdfPageIndex = pageIdx,
                                    centerX = cx,
                                    centerY = cy,
                                    width = w,
                                    height = h,
                                    rotationDeg = rot,
                                    pageIndex = notePageIdx,
                                    layerIndex = layerIdx,
                                    flipHorizontal = flipH
                                )
                            )
                        }
                    }
                }
            }
            val pdfItems = out.filterIsInstance<InfiniteCanvasItem.PdfPageItem>()
            if (pdfItems.isNotEmpty() && pdfItems.all { kotlin.math.abs(it.width - 760f) < 1f }) {
                val scaleFactor = 1440f / 760f
                val anchorX = pdfItems.first().centerX
                val anchorY = pdfItems.first().centerY
                val migrated = out.map { item ->
                    if (item is InfiniteCanvasItem.PdfPageItem) {
                        item.copy(
                            centerX = anchorX + (item.centerX - anchorX) * scaleFactor,
                            centerY = anchorY + (item.centerY - anchorY) * scaleFactor,
                            width = item.width * scaleFactor,
                            height = item.height * scaleFactor
                        )
                    } else item
                }
                save(fileStore, noteId, migrated)
                return migrated
            }
            out
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun save(fileStore: NoteFileStore, noteId: String, items: List<InfiniteCanvasItem>) {
        try {
            val dir = fileStore.getNoteDir(noteId)
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("cx", item.centerX.toDouble())
                    put("cy", item.centerY.toDouble())
                    put("w", item.width.toDouble())
                    put("h", item.height.toDouble())
                    put("rot", item.rotationDeg.toDouble())
                    put("pageIndex", item.pageIndex)
                    put("layerIndex", item.layerIndex)
                    put("flipH", item.flipHorizontal)
                    when (item) {
                        is InfiniteCanvasItem.ImageItem -> {
                            put("type", "image")
                            put("file", item.fileName)
                        }
                        is InfiniteCanvasItem.PdfPageItem -> {
                            put("type", "pdf_page")
                            put("pdf", item.pdfFileName)
                            put("page", item.pdfPageIndex)
                        }
                    }
                }
                arr.put(obj)
            }
            val tmp = File(dir, "$ITEMS_FILE.tmp")
            val target = File(dir, ITEMS_FILE)
            tmp.writeText(arr.toString())
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                target.writeText(arr.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun copyPdfToNoteDir(
        context: android.content.Context,
        fileStore: NoteFileStore,
        noteId: String,
        sourceUri: Uri
    ): String? {
        return try {
            val fileName = "inf_pdf_${UUID.randomUUID()}.pdf"
            val target = File(fileStore.getNoteDir(noteId), fileName)
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
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
}

/**
 * Caches decoded image bitmaps and zoom-adaptive vector-rendered PDF page bitmaps
 * for [InfiniteCanvasItem]s. PDF pages are re-rendered via [PdfiumDocument] whenever
 * the screen-space pixel size diverges from the cached render size, keeping text
 * and vector artwork crisp at any zoom level.
 */
class InfiniteItemBitmapCache(
    private val fileStore: NoteFileStore,
    private val noteId: String,
    private val scope: CoroutineScope
) : AutoCloseable {

    var renderTick by mutableIntStateOf(0)
        private set

    private data class CachedPdfBitmap(
        val bitmap: Bitmap,
        val renderedMaxPx: Int,
        var lastAccessTick: Long = System.nanoTime()
    )

    private val imageBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val loadingImages = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    /** Lightweight 1024px overview bitmaps (kept for all pages for instant fallback & zoomed-out grids). */
    private val overviewPdfBitmaps = ConcurrentHashMap<String, CachedPdfBitmap>()
    /** High-definition 2048px / 3200px bitmaps (up to 16 pages cached in 6GB RAM). */
    private val crispPdfBitmaps = ConcurrentHashMap<String, CachedPdfBitmap>()
    private val renderingPdfs = ConcurrentHashMap<String, Int>()
    /**
     * Pool of up to 4 independent [PdfiumDocument] handles per PDF file so the
     * 4 Kryo 485 Gold cores on Snapdragon 860 can render visible pages in parallel
     * without serializing on a single native document mutex.
     */
    private val pdfDocPools = HashMap<String, ArrayDeque<PdfiumDocument>>()
    private val allOpenedDocs = mutableListOf<PdfiumDocument>()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val pdfRenderDispatcher = Dispatchers.IO.limitedParallelism(4)

    private fun acquirePdfDoc(pdfFileName: String): PdfiumDocument? {
        synchronized(pdfDocPools) {
            val pool = pdfDocPools.getOrPut(pdfFileName) { ArrayDeque() }
            while (pool.isNotEmpty()) {
                val doc = pool.removeFirst()
                if (doc.isOpen) return doc
            }
        }
        val file = fileStore.getNoteAsset(noteId, pdfFileName) ?: return null
        val opened = PdfiumDocument.open(file) ?: return null
        synchronized(pdfDocPools) {
            allOpenedDocs.add(opened)
        }
        return opened
    }

    private fun releasePdfDoc(pdfFileName: String, doc: PdfiumDocument) {
        synchronized(pdfDocPools) {
            if (!doc.isOpen) return
            val pool = pdfDocPools.getOrPut(pdfFileName) { ArrayDeque() }
            if (pool.size < 4) {
                pool.addLast(doc)
            } else {
                allOpenedDocs.remove(doc)
                doc.close()
            }
        }
    }

    fun getImageBitmap(fileName: String): Bitmap? {
        val existing = imageBitmaps[fileName]
        if (existing != null && !existing.isRecycled) return existing
        if (loadingImages.add(fileName)) {
            scope.launch(Dispatchers.IO) {
                try {
                    val file = fileStore.getNoteAsset(noteId, fileName)
                    if (file != null) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        val maxSide = max(opts.outWidth, opts.outHeight)
                        val sample = if (maxSide > 2048) {
                            Integer.highestOneBit(maxSide / 2048).coerceAtLeast(1)
                        } else {
                            1
                        }
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                        if (bmp != null) {
                            imageBitmaps[fileName] = bmp
                            withContext(Dispatchers.Main) { renderTick++ }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    loadingImages.remove(fileName)
                }
            }
        }
        return null
    }

    /**
     * Non-allocating lookup used by the Liquid Glass backdrop builder so
     * composing the low-res glass backdrop never triggers a high-res PDF render.
     */
    fun getExistingBitmapForBackdrop(item: InfiniteCanvasItem): Bitmap? {
        return when (item) {
            is InfiniteCanvasItem.ImageItem -> imageBitmaps[item.fileName]?.takeIf { !it.isRecycled }
            is InfiniteCanvasItem.PdfPageItem -> {
                val key = "${item.pdfFileName}#${item.pdfPageIndex}"
                overviewPdfBitmaps[key]?.bitmap?.takeIf { !it.isRecycled }
                    ?: crispPdfBitmaps[key]?.bitmap?.takeIf { !it.isRecycled }
            }
        }
    }

    fun getPdfPageBitmap(
        item: InfiniteCanvasItem.PdfPageItem,
        canvasScale: Float
    ): Bitmap? {
        val key = "${item.pdfFileName}#${item.pdfPageIndex}"
        // Three hardware-accelerated tiers tailored for Snapdragon 860 (Adreno 640, 6GB RAM):
        // - 1024px (< 0.65x zoom): fast multi-column overview
        // - 2048px (0.65x..1.45x zoom): native 100% reading resolution (no re-render crossing 100%)
        // - 3200px (> 1.45x zoom): ultra-crisp ~350 DPI vector render for close-up inspection,
        //   debounced by 100ms so active pinch/pan gestures stay locked at 120Hz.
        val desiredMaxPx = when {
            canvasScale < 0.65f -> 1024
            canvasScale <= 1.45f -> 2048
            else -> 3200
        }
        val wantCrisp = desiredMaxPx >= 2048

        val now = System.nanoTime()
        val crisp = crispPdfBitmaps[key]?.takeIf { !it.bitmap.isRecycled }
        val overview = overviewPdfBitmaps[key]?.takeIf { !it.bitmap.isRecycled }

        if (wantCrisp && crisp != null && crisp.renderedMaxPx >= desiredMaxPx) {
            crisp.lastAccessTick = now
            return crisp.bitmap
        }
        if (!wantCrisp && overview != null) {
            overview.lastAccessTick = now
            return overview.bitmap
        }

        val prevRequested = renderingPdfs[key]
        if (prevRequested != desiredMaxPx && renderingPdfs.put(key, desiredMaxPx) == null) {
            val isUpgrade = (crisp != null || overview != null) && desiredMaxPx == 3200
            scope.launch(pdfRenderDispatcher) {
                try {
                    if (isUpgrade) {
                        // Let pinch-zoom / pan settle before upgrading from 2048px -> 3200px
                        kotlinx.coroutines.delay(100)
                    }
                    val targetW: Int
                    val targetH: Int
                    if (item.width >= item.height) {
                        targetW = desiredMaxPx
                        targetH = (desiredMaxPx * (item.height / item.width)).roundToInt().coerceAtLeast(1)
                    } else {
                        targetH = desiredMaxPx
                        targetW = (desiredMaxPx * (item.width / item.height)).roundToInt().coerceAtLeast(1)
                    }
                    val doc = acquirePdfDoc(item.pdfFileName)
                    val rendered = try {
                        doc?.renderPage(item.pdfPageIndex, targetW, targetH)
                    } finally {
                        if (doc != null) releasePdfDoc(item.pdfFileName, doc)
                    }
                    if (rendered != null) {
                        val entry = CachedPdfBitmap(rendered, desiredMaxPx, System.nanoTime())
                        if (wantCrisp) {
                            crispPdfBitmaps[key] = entry
                            // Keep up to 16 crisp pages cached in RAM (6GB device with largeHeap);
                            // preserve a 1024px overview copy whenever evicting an older page.
                            while (crispPdfBitmaps.size > 16) {
                                val oldest = crispPdfBitmaps.entries
                                    .filter { it.key != key }
                                    .minByOrNull { it.value.lastAccessTick }
                                    ?: break
                                crispPdfBitmaps.remove(oldest.key)
                                if (!overviewPdfBitmaps.containsKey(oldest.key) && !oldest.value.bitmap.isRecycled) {
                                    val bmp = oldest.value.bitmap
                                    val scaleDown = 1024f / max(bmp.width, bmp.height).coerceAtLeast(1)
                                    if (scaleDown < 0.9f) {
                                        val ow = (bmp.width * scaleDown).roundToInt().coerceAtLeast(1)
                                        val oh = (bmp.height * scaleDown).roundToInt().coerceAtLeast(1)
                                        try {
                                            val small = Bitmap.createScaledBitmap(bmp, ow, oh, true)
                                            overviewPdfBitmaps[oldest.key] = CachedPdfBitmap(small, 1024)
                                        } catch (_: OutOfMemoryError) {}
                                    }
                                }
                            }
                        } else {
                            overviewPdfBitmaps[key] = entry
                        }
                        withContext(Dispatchers.Main) { renderTick++ }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    renderingPdfs.remove(key)
                }
            }
        }
        if (crisp != null) crisp.lastAccessTick = now
        if (overview != null) overview.lastAccessTick = now
        return (crisp ?: overview)?.bitmap
    }

    override fun close() {
        synchronized(pdfDocPools) {
            pdfDocPools.clear()
            allOpenedDocs.forEach { it.close() }
            allOpenedDocs.clear()
        }
        imageBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        imageBitmaps.clear()
        overviewPdfBitmaps.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        overviewPdfBitmaps.clear()
        crispPdfBitmaps.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        crispPdfBitmaps.clear()
    }
}

fun worldToItemLocal(
    wx: Float,
    wy: Float,
    item: InfiniteCanvasItem
): Pair<Float, Float> {
    val dx = wx - item.centerX
    val dy = wy - item.centerY
    val rad = Math.toRadians(-item.rotationDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    return Pair(dx * c - dy * s, dx * s + dy * c)
}

fun hitTestInfiniteItemHandle(
    wx: Float,
    wy: Float,
    item: InfiniteCanvasItem,
    canvasScale: Float
): InfiniteItemHandle {
    val (lx, ly) = worldToItemLocal(wx, wy, item)
    val hw = item.width / 2f
    val hh = item.height / 2f
    val inv = 1f / canvasScale.coerceAtLeast(0.1f)
    val handleRadiusWorld = 28f * inv
    val mirrorOffsetX = (-hw + 38.4f * inv).coerceAtMost(0f)

    // Corner 2 (Top-right): Delete / Cut
    if (kotlin.math.hypot((lx - hw).toDouble(), (ly + hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.DELETE
    }
    // Corner 1 (Top-left): Rotate
    if (kotlin.math.hypot((lx + hw).toDouble(), (ly + hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.ROTATE
    }
    // Beside Corner 1 (Top-left + offset): Mirror horizontal flip
    if (kotlin.math.hypot((lx - mirrorOffsetX).toDouble(), (ly + hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.MIRROR
    }
    // Corner 3 (Bottom-left): Copy (tap & drag)
    if (kotlin.math.hypot((lx + hw).toDouble(), (ly - hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.COPY
    }
    // Corner 4 (Bottom-right): 1:1 Resize
    if (kotlin.math.hypot((lx - hw).toDouble(), (ly - hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.RESIZE
    }
    // Edge midpoints: single-axis stretch/squeeze
    if (kotlin.math.hypot((lx + hw).toDouble(), ly.toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.SCALE_LEFT
    }
    if (kotlin.math.hypot((lx - hw).toDouble(), ly.toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.SCALE_RIGHT
    }
    if (kotlin.math.hypot(lx.toDouble(), (ly + hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.SCALE_TOP
    }
    if (kotlin.math.hypot(lx.toDouble(), (ly - hh).toDouble()) <= handleRadiusWorld) {
        return InfiniteItemHandle.SCALE_BOTTOM
    }
    // Interior: Move
    if (lx in -hw..hw && ly in -hh..hh) {
        return InfiniteItemHandle.MOVE
    }
    return InfiniteItemHandle.NONE
}

fun hitTestInfiniteItems(
    wx: Float,
    wy: Float,
    items: List<InfiniteCanvasItem>,
    pageIndex: Int? = null,
    isLayerVisible: ((Int) -> Boolean)? = null
): InfiniteCanvasItem? {
    for (i in items.indices.reversed()) {
        val item = items[i]
        if (pageIndex != null && item.pageIndex != pageIndex) continue
        if (isLayerVisible != null && !isLayerVisible(item.layerIndex)) continue
        val (lx, ly) = worldToItemLocal(wx, wy, item)
        if (lx in -(item.width / 2f)..(item.width / 2f) &&
            ly in -(item.height / 2f)..(item.height / 2f)
        ) {
            return item
        }
    }
    return null
}
