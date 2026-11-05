package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.ui.canvas.PaperPattern

/** Defect F9: PaperPattern DB-code contract (`pages.paperPattern` column). */
class PaperPatternCodeTest {

    @Test
    fun code_roundtrip_all_values() {
        for (p in PaperPattern.entries) {
            assertEquals(p, PaperPattern.fromCode(p.code))
        }
    }

    @Test
    fun unset_code_is_zero_and_maps_to_blank() {
        assertEquals(0, PaperPattern.BLANK.code)
        assertEquals(PaperPattern.BLANK, PaperPattern.fromCode(0))
    }

    @Test
    fun codes_match_settings_defaultBackground_semantics() {
        // SettingsRepository maps defaultBackground 1->GRID, 2->LINES, 3->DOTTED.
        assertEquals(1, PaperPattern.GRID.code)
        assertEquals(2, PaperPattern.LINES.code)
        assertEquals(3, PaperPattern.DOTTED.code)
    }

    @Test
    fun unknown_codes_fall_back_to_blank() {
        assertEquals(PaperPattern.BLANK, PaperPattern.fromCode(99))
        assertEquals(PaperPattern.BLANK, PaperPattern.fromCode(-1))
    }
}
