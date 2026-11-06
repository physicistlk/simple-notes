package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.text.NoteTemplates
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Template variables resolve against the creation time, never against the time
 * the template was written; unknown placeholders stay visible instead of
 * silently disappearing.
 */
class NoteTemplatesTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun instant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone = utc
    ): Long = GregorianCalendar(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    @Test
    fun dailyTitleIsTheIsoDate() {
        assertEquals("2026-11-06", NoteTemplates.dailyTitle(instant(2026, 11, 6, 9, 30), utc))
    }

    @Test
    fun dailyTitleUsesTheGivenZone() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        // 2026-11-06 23:30 UTC is already 2026-11-07 in Tokyo.
        assertEquals("2026-11-07", NoteTemplates.dailyTitle(instant(2026, 11, 6, 23, 30), tokyo))
    }

    @Test
    fun dateAndTimeVariablesExpand() {
        val now = instant(2026, 11, 6, 9, 5)
        assertEquals(
            "2026-11-06 at 09:05 (2026-11-06 09:05)",
            NoteTemplates.expand("{{date}} at {{time}} ({{datetime}})", "Any", now, utc)
        )
    }

    @Test
    fun titleAndTimestampExpand() {
        val now = instant(2026, 11, 6, 9, 5)
        assertEquals(
            "Meeting notes 1793955900000",
            NoteTemplates.expand("{{title}} {{timestamp}}", "Meeting notes", now, utc)
        )
    }

    @Test
    fun customDatePatternUsesSimpleDateFormat() {
        val now = instant(2026, 11, 6, 9, 5)
        assertEquals(
            "2026/11 · Q4",
            NoteTemplates.expand("{{date:yyyy/MM}} · Q4", "Any", now, utc)
        )
    }

    @Test
    fun unknownVariablesStayVisible() {
        val now = instant(2026, 11, 6, 9, 5)
        // An unknown token and an unterminated one both stay as typed.
        assertEquals(
            "{{nope}} and {{not a var",
            NoteTemplates.expand("{{nope}} and {{not a var", "T", now, utc)
        )
    }

    @Test
    fun contentWithoutVariablesIsUntouched() {
        val content = "# Plain\n\n- [ ] task"
        assertEquals(content, NoteTemplates.expand(content, "T", 0L, utc))
    }

    @Test
    fun defaultDailyTemplateCarriesTheUsualSections() {
        val content = NoteTemplates.expand(
            NoteTemplates.DEFAULT_DAILY_CONTENT,
            "2026-11-06",
            instant(2026, 11, 6, 9, 5),
            utc
        )
        assertEquals(true, content.contains("## Notes"))
        assertEquals(true, content.contains("- [ ]"))
    }

    @Test
    fun calendarConstantsAreNotAccidentallyUsed() {
        // Guards the month handling in [instant]: Calendar.MONTH is 0-based.
        val c = GregorianCalendar(utc).apply { timeInMillis = instant(2026, 11, 6, 9, 5) }
        assertEquals(Calendar.NOVEMBER, c.get(Calendar.MONTH))
        assertEquals(6, c.get(Calendar.DAY_OF_MONTH))
    }
}
