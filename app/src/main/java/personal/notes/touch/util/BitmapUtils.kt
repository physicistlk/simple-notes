package personal.notes.touch.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object BitmapUtils {

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
