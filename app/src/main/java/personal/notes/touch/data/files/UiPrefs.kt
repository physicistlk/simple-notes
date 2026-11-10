package personal.notes.touch.data.files

import org.json.JSONObject
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

    private fun read(): JSONObject {
        return try {
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) {
        read().optBoolean(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        synchronized(lock) {
            val json = read()
            json.put(key, value)
            write(json)
        }
    }

    fun getFloat(key: String, default: Float): Float = synchronized(lock) {
        read().optDouble(key, default.toDouble()).toFloat()
    }

    fun putFloat(key: String, value: Float) {
        synchronized(lock) {
            val json = read()
            json.put(key, value.toDouble())
            write(json)
        }
    }

    /** Writes the file atomically: a temp file plus a rename. */
    private fun write(json: JSONObject) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                // The rename can fail on some filesystems: fall back to a
                // plain write rather than losing the preference.
                file.writeText(json.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
