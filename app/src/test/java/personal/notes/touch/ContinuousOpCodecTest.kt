package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import personal.notes.touch.ui.canvas.CanvasTool
import personal.notes.touch.ui.canvas.ContinuousOpCodec
import personal.notes.touch.ui.canvas.ContinuousStroke
import personal.notes.touch.ui.canvas.InkOp
import personal.notes.touch.ui.canvas.InkPoint
import personal.notes.touch.ui.canvas.PageLayer
import personal.notes.touch.ui.canvas.PageLayerStack

/**
 * Tests of the op codec (audit C50): the strokes and the erases of a layer
 * are written to the disk as vectors, so they have to survive the round trip.
 */
class ContinuousOpCodecTest {

    private val layer = PageLayer(1, "Layer1", true)

    private fun stroke(
        tool: CanvasTool = CanvasTool.PEN,
        color: Int = 0xFF123456.toInt(),
        width: Float = 0.004f,
        autoShape: Boolean = false
    ): ContinuousStroke {
        val s = ContinuousStroke(tool, color, width, autoShape)
        s.points.add(InkPoint(0.1f, 0.2f, 0.004f))
        s.points.add(InkPoint(0.3f, 0.4f, 0.005f))
        return s
    }

    @Test
    fun aStrokeSurvivesTheRoundTrip() {
        val op = InkOp.Stroke(stroke(), null, layer)
        val text = ContinuousOpCodec.encode(listOf(op))
        val back = ContinuousOpCodec.decode(text) ?: error("decode failed")
        assertEquals(1, back.size)
        val s = (back[0] as InkOp.Stroke).stroke
        assertEquals(CanvasTool.PEN, s.tool)
        assertEquals(0xFF123456.toInt(), s.color)
        assertEquals(0.004f, s.baseWidthNorm, 0f)
        assertEquals(2, s.points.size)
        assertEquals(0.1f, s.points[0].x, 0f)
        assertEquals(0.2f, s.points[0].y, 0f)
        assertEquals(0.004f, s.points[0].w, 0f)
    }

    @Test
    fun anEraseSurvivesTheRoundTrip() {
        val op = InkOp.Erase(stroke(CanvasTool.ERASER), emptyList(), layer)
        val text = ContinuousOpCodec.encode(listOf(op))
        val back = ContinuousOpCodec.decode(text) ?: error("decode failed")
        assertEquals(1, back.size)
        assertTrue(back[0] is InkOp.Erase)
    }

    @Test
    fun anEmptyListGivesAnEmptyList() {
        val text = ContinuousOpCodec.encode(emptyList())
        val back = ContinuousOpCodec.decode(text) ?: error("decode failed")
        assertEquals(0, back.size)
    }

    @Test
    fun aBrokenFileGivesNull() {
        assertNull(ContinuousOpCodec.decode("not json"))
        assertNull(ContinuousOpCodec.decode("{\"version\":1,\"ops\":[{\"k\":\"x\"}]}"))
        assertNull(ContinuousOpCodec.decode("{\"version\":1,\"ops\":[{\"k\":\"s\",\"t\":\"PEN\",\"c\":0,\"w\":0.004,\"a\":false,\"p\":[]}]}".replace("\"p\":[]", "\"p\":\"bad\"")))
    }

    @Test
    fun theFloatsKeepTheirPrecision() {
        val s = stroke()
        s.points.clear()
        s.points.add(InkPoint(0.123456789f, 0.987654321f, 0.0012345678f))
        val op = InkOp.Stroke(s, null, layer)
        val text = ContinuousOpCodec.encode(listOf(op))
        val back = ContinuousOpCodec.decode(text) ?: error("decode failed")
        val p = (back[0] as InkOp.Stroke).stroke.points[0]
        assertEquals(0.123456789f, p.x, 1e-6f)
        assertEquals(0.987654321f, p.y, 1e-6f)
        assertEquals(0.0012345678f, p.w, 1e-6f)
    }
}
