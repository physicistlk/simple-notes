package personal.notes.touch.text

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Template variables and the daily-note title.
 *
 * A template stores its placeholders verbatim, so "today" is resolved when the
 * note is created, never when the template was written.
 *
 * - `{{date}}`      → 2026-11-06
 * - `{{time}}`      → 09:30
 * - `{{datetime}}`  → 2026-11-06 09:30
 * - `{{date:yyyy/MM/dd}}` → any `SimpleDateFormat` pattern
 * - `{{title}}`     → the new note's title
 * - `{{timestamp}}` → epoch milliseconds
 *
 * An unknown `{{...}}` is left as typed, so typos are visible instead of
 * silently vanishing. Pure Kotlin with an injectable [TimeZone], so the tests
 * are deterministic and minSdk 21 stays safe (no `java.time`).
 */
object NoteTemplates {

    const val DAILY_TITLE_PATTERN = "yyyy-MM-dd"
    const val DEFAULT_DAILY_NAME = "Daily notes"
    const val DEFAULT_DAILY_CONTENT = "## Notes\n\n## Tasks\n- [ ] "
    const val STARTER_MEETING_NAME = "Meeting notes"
    const val STARTER_MEETING_CONTENT =
        "## Attendees\n\n## Agenda\n- \n\n## Decisions\n- \n\n## Actions\n- [ ] "

    private val VARIABLE = Regex("""\{\{\s*([^{}]+?)\s*}}""")
    private val DATE_WITH_PATTERN = Regex("""^date:(.+)$""")

    fun dailyTitle(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        format(nowMillis, DAILY_TITLE_PATTERN, zone)

    fun expand(
        content: String,
        title: String,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        if (!content.contains("{{")) return content
        return VARIABLE.replace(content) { match ->
            val token = match.groupValues[1]
            when {
                token == "date" -> format(nowMillis, "yyyy-MM-dd", zone)
                token == "time" -> format(nowMillis, "HH:mm", zone)
                token == "datetime" -> format(nowMillis, "yyyy-MM-dd HH:mm", zone)
                token == "title" -> title
                token == "timestamp" -> nowMillis.toString()
                else -> {
                    val custom = DATE_WITH_PATTERN.matchEntire(token)
                    if (custom != null) {
                        runCatching { format(nowMillis, custom.groupValues[1], zone) }
                            .getOrDefault(match.value)
                    } else {
                        match.value
                    }
                }
            }
        }
    }

    private fun format(nowMillis: Long, pattern: String, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(nowMillis))
}
