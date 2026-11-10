package personal.notes.touch.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * The small stickers the Tools menu can drop onto a page.
 *
 * They are drawn in code instead of being shipped as image assets: a sticker
 * is a handful of paths, and drawing them keeps the APK small and lets the
 * size follow the canvas resolution.
 */
object StickerArt {

    /** The stickers in the order the menu cycles through them. */
    enum class Kind { STAR, HEART, SMILEY }

    /** Draws the sticker of [kind] as a bitmap of [size] pixels. */
    fun draw(kind: Kind, size: Int = 96, color: Int = Color.rgb(0xFB, 0x64, 0x43)): Bitmap {
        val side = size.coerceIn(16, 512)
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.FILL
        }
        when (kind) {
            Kind.STAR -> canvas.drawPath(starPath(side), paint)
            Kind.HEART -> canvas.drawPath(heartPath(side), paint)
            Kind.SMILEY -> {
                canvas.drawCircle(side / 2f, side / 2f, side / 2f * 0.92f, paint)
                // The eyes and the mouth are cut out of the disc.
                val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                canvas.drawCircle(side * 0.34f, side * 0.38f, side * 0.07f, hole)
                canvas.drawCircle(side * 0.66f, side * 0.38f, side * 0.07f, hole)
                val mouthHole = Paint(hole).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = side * 0.06f
                    strokeCap = Paint.Cap.ROUND
                }
                val mouth = Path().apply {
                    moveTo(side * 0.3f, side * 0.62f)
                    quadTo(side * 0.5f, side * 0.8f, side * 0.7f, side * 0.62f)
                }
                canvas.drawPath(mouth, mouthHole)
            }
        }
        return bmp
    }

    /** A five-pointed star that fills the square. */
    private fun starPath(side: Int): Path {
        val path = Path()
        val cx = side / 2f
        val cy = side / 2f
        val outer = side / 2f * 0.95f
        val inner = outer * 0.42f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * Math.cos(angle)).toFloat()
            val y = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** A heart that fills the square. */
    private fun heartPath(side: Int): Path {
        val path = Path()
        val w = side.toFloat()
        path.moveTo(w / 2f, w * 0.92f)
        path.cubicTo(w * 0.06f, w * 0.58f, w * 0.18f, w * 0.12f, w * 0.5f, w * 0.32f)
        path.cubicTo(w * 0.82f, w * 0.12f, w * 0.94f, w * 0.58f, w / 2f, w * 0.92f)
        path.close()
        return path
    }
}
