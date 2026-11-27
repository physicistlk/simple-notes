package personal.notes.touch.data.files

import personal.notes.touch.ui.canvas.MiniJson
import java.io.File

/**
 * Workspace preferences that are not part of a note: the open-notes tab bar
 * state, the pen dock orientation, the canvas preferences and the zoom lock.
 *
 * They live in a small JSON file instead of the settings table, so the Room
 * schema (and its migrations) stays untouched by what is only a UI choice.
 * Reads are cheap and happen once per screen; writes are done off the main
 * thread by the caller and are atomic (a temp file plus a rename), so a crash
 * mid-write cannot leave a half-written file behind.
 */
class UiPrefs(private val file: File) {

    private val lock = Any()

    private fun read(): LinkedHashMap<String, Any?> {
        return try {
            if (!file.exists()) return LinkedHashMap()
            val parsed = MiniJson.parse(file.readText()) as? Map<*, *> ?: return LinkedHashMap()
            val out = LinkedHashMap<String, Any?>(parsed.size)
            for ((k, v) in parsed) {
                if (k is String) out[k] = v
            }
            out
        } catch (e: Exception) {
            e.printStackTrace()
            LinkedHashMap()
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) {
        (read()[key] as? Boolean) ?: default
    }

    fun putBoolean(key: String, value: Boolean) {
        synchronized(lock) {
            val map = read()
            map[key] = value
            write(map)
        }
    }

    fun getFloat(key: String, default: Float): Float = synchronized(lock) {
        val raw = (read()[key] as? Number)?.toFloat()
        if (raw != null && raw.isFinite()) raw else default
    }

    fun putFloat(key: String, value: Float) {
        if (!value.isFinite()) return
        synchronized(lock) {
            val map = read()
            map[key] = value.toDouble()
            write(map)
        }
    }

    fun getString(key: String, default: String): String = synchronized(lock) {
        val s = read()[key] as? String
        if (s.isNullOrEmpty()) default else s
    }

    fun putString(key: String, value: String) {
        synchronized(lock) {
            val map = read()
            map[key] = value
            write(map)
        }
    }

    /** Writes the file atomically: a temp file plus a rename. */
    private fun write(map: Map<String, Any?>) {
        var tmp: File? = null
        try {
            val jsonText = encodeMap(map)
            file.parentFile?.mkdirs()
            tmp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
            tmp.writeText(jsonText)
            if (!tmp.renameTo(file)) {
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    // The rename can fail on some filesystems: fall back to a
                    // plain write rather than losing the preference.
                    file.writeText(jsonText)
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            tmp?.let { if (it.exists()) it.delete() }
            e.printStackTrace()
        }
    }

    private fun encodeMap(map: Map<String, Any?>): String {
        val sb = StringBuilder()
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            appendJsonString(sb, k)
            sb.append(':')
            when (v) {
                null -> sb.append("null")
                is Boolean -> sb.append(if (v) "true" else "false")
                is Number -> {
                    val d = v.toDouble()
                    if (d.isFinite()) {
                        if (v is Int || v is Long) sb.append(v.toLong()) else sb.append(d)
                    } else {
                        sb.append("null")
                    }
                }
                is String -> appendJsonString(sb, v)
                else -> appendJsonString(sb, v.toString())
            }
        }
        sb.append('}')
        return sb.toString()
    }

    private fun appendJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    companion object {
        const val KEY_EXPORT_INCLUDE_MARGINS = "export_include_margins"
    }
}
