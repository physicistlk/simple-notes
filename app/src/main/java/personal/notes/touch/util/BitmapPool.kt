package personal.notes.touch.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Bounded, thread-safe [Bitmap] pool for [BitmapFactory.Options.inBitmap] reuse.
 *
 * Primarily serves uniform `1024x1024` ARGB_8888 annotation tiles and downsampled
 * thumbnail intermediates so rapid scrolling, tile decoding, and layer composition
 * avoid repeated native heap allocations and GC pauses.
 */
object BitmapPool {

    private const val DEFAULT_MAX_BYTES: Long = 24L * 1024L * 1024L // 24 MiB (~6 1024x1024 ARGB_8888 tiles)
    private const val MAX_ENTRY_RATIO: Long = 2L // avoid reusing a bitmap more than 2x larger than needed

    private val lock = Any()
    private val pool = ArrayDeque<Bitmap>()
    private var currentBytes: Long = 0L

    @Volatile
    private var maxBytes: Long = DEFAULT_MAX_BYTES

    /** Returns the approximate byte size of [bitmap] safely across Android and JVM/Robolectric. */
    internal fun bitmapBytes(bitmap: Bitmap): Long {
        return try {
            val alloc = bitmap.allocationByteCount.toLong()
            if (alloc > 0L) alloc else fallbackBytes(bitmap.width, bitmap.height, bitmap.config)
        } catch (_: Throwable) {
            fallbackBytes(bitmap.width, bitmap.height, bitmap.config)
        }
    }

    internal fun requiredBytes(width: Int, height: Int, config: Bitmap.Config): Long {
        if (width <= 0 || height <= 0) return 0L
        return fallbackBytes(width, height, config)
    }

    private fun fallbackBytes(width: Int, height: Int, config: Bitmap.Config?): Long {
        val w = width.coerceAtLeast(0).toLong()
        val h = height.coerceAtLeast(0).toLong()
        val bpp = when (config) {
            Bitmap.Config.ALPHA_8 -> 1L
            Bitmap.Config.RGB_565 -> 2L
            Bitmap.Config.RGBA_F16 -> 8L
            else -> 4L
        }
        return w * h * bpp
    }

    /**
     * Acquires a mutable, non-recycled [Bitmap] suitable for `inBitmap` decoding of a
     * `[width] x [height]` image with [config], or `null` if no compatible bitmap is pooled.
     */
    fun acquire(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        val needed = requiredBytes(width, height, config)
        if (needed <= 0L) return null
        val maxAccepted = needed * MAX_ENTRY_RATIO
        synchronized(lock) {
            var bestIdx = -1
            var bestBytes = Long.MAX_VALUE
            var idx = 0
            while (idx < pool.size) {
                val candidate = pool[idx]
                if (candidate.isRecycled) {
                    pool.removeAt(idx)
                    currentBytes = (currentBytes - bitmapBytes(candidate)).coerceAtLeast(0L)
                    continue
                }
                if (candidate.isMutable && candidate.config == config) {
                    val bytes = bitmapBytes(candidate)
                    if (bytes in needed..maxAccepted && bytes < bestBytes) {
                        bestBytes = bytes
                        bestIdx = idx
                        if (bytes == needed) break
                    }
                }
                idx++
            }
            if (bestIdx < 0) return null
            val chosen = pool.removeAt(bestIdx)
            currentBytes = (currentBytes - bitmapBytes(chosen)).coerceAtLeast(0L)
            return chosen
        }
    }

    /**
     * Returns a no-longer-used mutable [Bitmap] to the pool for future `inBitmap` reuse.
     *
     * Callers MUST ensure no Canvas, ImageBitmap, or UI composable still references [bitmap].
     * Non-mutable or oversized bitmaps are recycled immediately.
     */
    fun put(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        val bytes = bitmapBytes(bitmap)
        val budget = maxBytes
        if (!bitmap.isMutable || bytes <= 0L || bytes > budget / 2L) {
            bitmap.recycle()
            return
        }
        synchronized(lock) {
            if (pool.any { it === bitmap }) return
            pool.addLast(bitmap)
            currentBytes += bytes
            while (currentBytes > budget && pool.isNotEmpty()) {
                val evicted = pool.removeFirst()
                currentBytes = (currentBytes - bitmapBytes(evicted)).coerceAtLeast(0L)
                if (!evicted.isRecycled) evicted.recycle()
            }
        }
    }

    /**
     * Decodes [file] as a mutable `ARGB_8888` [Bitmap] at [sampleSize], reusing a pooled
     * bitmap via [BitmapFactory.Options.inBitmap] when available and falling back gracefully
     * if the platform decoder rejects `inBitmap`.
     */
    fun decodeFileMutableArgb(file: File, sampleSize: Int = 1): Bitmap? {
        if (!file.isFile) return null
        val safeSample = sampleSize.coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetW = ((bounds.outWidth + safeSample - 1) / safeSample).coerceAtLeast(1)
        val targetH = ((bounds.outHeight + safeSample - 1) / safeSample).coerceAtLeast(1)
        val reusable = acquire(targetW, targetH, Bitmap.Config.ARGB_8888)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = safeSample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            if (reusable != null && !reusable.isRecycled) {
                inBitmap = reusable
            }
        }
        return try {
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
            if (decoded == null && reusable != null) {
                put(reusable)
            }
            decoded
        } catch (_: IllegalArgumentException) {
            // Platform decoder rejected inBitmap (e.g. non-reconfigurable size/format): retry without it.
            if (reusable != null) put(reusable)
            val fallbackOpts = BitmapFactory.Options().apply {
                inSampleSize = safeSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            try {
                BitmapFactory.decodeFile(file.absolutePath, fallbackOpts)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            if (reusable != null) put(reusable)
            null
        }
    }

    /** Current byte footprint of pooled bitmaps (for diagnostics and tests). */
    fun currentByteSize(): Long = synchronized(lock) { currentBytes }

    /** Current number of pooled bitmaps (for diagnostics and tests). */
    fun pooledCount(): Int = synchronized(lock) { pool.size }

    /** Updates the pool byte budget (primarily for tests) and trims excess entries. */
    internal fun setMaxBytesForTesting(limitBytes: Long) {
        maxBytes = limitBytes.coerceAtLeast(0L)
        synchronized(lock) {
            while (currentBytes > maxBytes && pool.isNotEmpty()) {
                val evicted = pool.removeFirst()
                currentBytes = (currentBytes - bitmapBytes(evicted)).coerceAtLeast(0L)
                if (!evicted.isRecycled) evicted.recycle()
            }
        }
    }

    /** Restores default pool budget and releases all pooled bitmaps. */
    fun clear(resetBudget: Boolean = false) {
        if (resetBudget) maxBytes = DEFAULT_MAX_BYTES
        synchronized(lock) {
            while (pool.isNotEmpty()) {
                val bmp = pool.removeFirst()
                if (!bmp.isRecycled) bmp.recycle()
            }
            currentBytes = 0L
        }
    }
}
