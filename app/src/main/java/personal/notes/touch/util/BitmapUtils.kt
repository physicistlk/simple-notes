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
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            putCachedThumbnail(cacheKey, decoded)
            decoded
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
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
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

    fun createSafeBitmap(width: Int, height: Int, maxWidth: Int = 2048, maxHeight: Int = 2048): Bitmap {
        val w = width.coerceIn(1, maxWidth)
        val h = height.coerceIn(1, maxHeight)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }
}
