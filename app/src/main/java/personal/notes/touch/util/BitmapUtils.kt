package personal.notes.touch.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedHashMap

object BitmapUtils {

    private val thumbnailCache: MutableMap<String, Bitmap> =
        Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
                return size > 64
            }
        })

    fun getCachedThumbnail(key: String): Bitmap? = thumbnailCache[key]

    fun putCachedThumbnail(key: String, bitmap: Bitmap) {
        thumbnailCache[key] = bitmap
    }

    fun invalidateThumbnail(key: String) {
        thumbnailCache.remove(key)
    }

    suspend fun loadThumbnail(file: File, targetWidth: Int = 360, targetHeight: Int = 500): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        getCachedThumbnail(cacheKey)?.let { return@withContext it }

        try {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null

            var sample = 1
            while ((w / (sample * 2)) >= targetWidth && (h / (sample * 2)) >= targetHeight) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            val out = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(raw, 0f, 0f, null)
            raw.recycle()
            putCachedThumbnail(cacheKey, out)
            out
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Same downsampling as [loadThumbnail] but keeps the alpha channel.
     *
     * [loadThumbnail] decodes RGB_565 for memory, which flattens transparency
     * to black — fine for a page, wrong for an overlay such as the saved ink
     * layer. Cached under a distinct key so the two configs cannot collide.
     */
    suspend fun loadAlphaThumbnail(
        file: File,
        targetWidth: Int = 360,
        targetHeight: Int = 500
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "alpha:${file.absolutePath}:${file.lastModified()}"
        getCachedThumbnail(cacheKey)?.let { return@withContext it }
        try {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null
            var sample = 1
            while ((w / (sample * 2)) >= targetWidth && (h / (sample * 2)) >= targetHeight) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            putCachedThumbnail(cacheKey, decoded)
            decoded
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val imageCache: MutableMap<String, Bitmap> =
        Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(12, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
                return size > 12
            }
        })

    fun getCachedImage(path: String, lastModified: Long): Bitmap? =
        imageCache["$path:$lastModified"]

    /**
     * Decodes an image block attachment downsampled so its longest side is at
     * most [maxDimension] px (RGB_565). Small bounded cache: editor rows are
     * recycled by the LazyColumn and 12 entries cover what is on screen.
     */
    suspend fun loadImage(file: File, maxDimension: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        imageCache[cacheKey]?.let { return@withContext it }
        try {
            if (!file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null

            var sample = 1
            val longest = maxOf(w, h)
            while (longest / (sample * 2) >= maxDimension) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            val out = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(raw, 0f, 0f, null)
            raw.recycle()
            imageCache[cacheKey] = out
            out
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveBitmapToPng(bitmap: Bitmap, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            // A unique temp name: two writers (the debounced save and a page
            // flip) must never write into the same temp file, or the second
            // one truncates the first one's stream and the published page
            // file ends up half-written.
            val tempFile = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
            val compressed = FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            if (!compressed || !tempFile.exists()) {
                // Nothing was written: keep the file that is already there.
                tempFile.delete()
                return@withContext false
            }
            // Rename over the old file: the old content stays readable until
            // the new one is in place (deleting it first would leave the
            // page empty if the process dies here).
            if (!tempFile.renameTo(file)) {
                // Some filesystems refuse to rename onto an existing file.
                if (!file.delete() || !tempFile.renameTo(file)) {
                    tempFile.delete()
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadBitmapFromFile(file: File): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)?.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createSafeBitmap(width: Int, height: Int, maxWidth: Int = 4096, maxHeight: Int = 4096): Bitmap {
        val w = width.coerceIn(1, maxWidth)
        val h = height.coerceIn(1, maxHeight)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }
}
