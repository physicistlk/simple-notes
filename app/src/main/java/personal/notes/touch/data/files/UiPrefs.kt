package personal.notes.touch.data.files

import org.json.JSONObject
import java.io.File

/**
 * Workspace preferences that are not part of a note: the open-notes tab bar
 * state and the pen dock orientation.
 *
 * They live in a small JSON file instead of the settings table, so the Room
 * schema (and its migrations) stays untouched by what is only a UI choice.
 * Reads are cheap and happen once per screen; writes are done off the main
 * thread by the caller.
 */
class UiPrefs(private val file: File) {

    private fun read(): JSONObject {
        return try {
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean = read().optBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        try {
            val json = read()
            json.put(key, value)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getString(key: String, default: String): String = read().optString(key, default)

    fun putString(key: String, value: String) {
        try {
            val json = read()
            json.put(key, value)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
