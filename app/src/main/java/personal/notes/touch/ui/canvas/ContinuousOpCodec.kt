package personal.notes.touch.ui.canvas


/**
 * Pure codec for the ops of one annotation layer (audit C50, stage 2b).
 *
 * Strokes stay vectors on disk: the codec turns the in-memory [InkOp] list
 * into a JSON text and back. A stroke keeps its tool, colour, base width,
 * auto-shape flag and its points; an erase keeps the same stroke data (its
 * patches are NOT stored - they are recomputed on load is not needed, the
 * tiles hold the pixels).
 *
 * The codec is free of android.* types and uses [MiniJson] to parse.
 */
internal object ContinuousOpCodec {

    fun encode(ops: List<InkOp>): String {
        val sb = StringBuilder()
        sb.append('[')
        var first = true
        for (op in ops) {
            if (!first) sb.append(',')
            first = false
            when (op) {
                is InkOp.Stroke -> {
                    sb.append("{\"type\":\"stroke\"")
                    appendStroke(sb, op.stroke)
                    sb.append('}')
                }
                is InkOp.Erase -> {
                    sb.append("{\"type\":\"erase\"")
                    appendStroke(sb, op.stroke)
                    sb.append('}')
                }
            }
        }
        sb.append(']')
        return sb.toString()
    }

    private fun appendStroke(sb: StringBuilder, s: ContinuousStroke) {
        sb.append(",\"tool\":\"").append(s.tool.name).append('"')
        sb.append(",\"color\":").append(s.color)
        sb.append(",\"baseWidthNorm\":").append(s.baseWidthNorm)
        sb.append(",\"autoShapeEnabled\":").append(s.autoShapeEnabled)
        sb.append(",\"points\":[")
        var firstP = true
        for (p in s.points) {
            if (!firstP) sb.append(',')
            firstP = false
            sb.append("{\"x\":").append(p.x)
                .append(",\"y\":").append(p.y)
                .append(",\"w\":").append(p.w)
                .append('}')
        }
        sb.append(']')
    }

    fun decode(text: String): List<InkOp>? {
        val parsed = MiniJson.parse(text) ?: return null
        val arr = parsed as? List<*> ?: return null
        val out = mutableListOf<InkOp>()
        val placeholder = PageLayer(1, "Layer1", true)
        for (entry in arr) {
            val obj = entry as? Map<*, *> ?: return null
            val type = obj["type"] as? String ?: return null
            val stroke = decodeStroke(obj) ?: return null
            when (type) {
                "stroke" -> out.add(InkOp.Stroke(stroke, null, placeholder))
                "erase" -> out.add(InkOp.Erase(stroke, emptyList(), placeholder))
                else -> return null
            }
        }
        return out
    }

    private fun decodeStroke(obj: Map<*, *>): ContinuousStroke? {
        val toolName = obj["tool"] as? String ?: return null
        val tool = try { CanvasTool.valueOf(toolName) } catch (e: Exception) { return null }
        val color = (obj["color"] as? Number)?.toInt() ?: return null
        val baseWidthNorm = (obj["baseWidthNorm"] as? Number)?.toFloat() ?: return null
        val autoShapeEnabled = obj["autoShapeEnabled"] as? Boolean ?: return null
        val ptsRaw = obj["points"] as? List<*> ?: return null
        val points = mutableListOf<InkPoint>()
        for (p in ptsRaw) {
            val pm = p as? Map<*, *> ?: return null
            val x = (pm["x"] as? Number)?.toFloat() ?: return null
            val y = (pm["y"] as? Number)?.toFloat() ?: return null
            val w = (pm["w"] as? Number)?.toFloat() ?: return null
            points.add(InkPoint(x, y, w))
        }
        val stroke = ContinuousStroke(tool, color, baseWidthNorm, autoShapeEnabled, points)
        return stroke
    }
}