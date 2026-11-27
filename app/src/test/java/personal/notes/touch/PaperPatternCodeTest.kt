package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import personal.notes.touch.ui.canvas.InfiniteGridPreset
import personal.notes.touch.ui.canvas.PaperPattern
import personal.notes.touch.ui.canvas.infiniteGridPresetForPaperBackground

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

    @Test
    fun default_background_seeds_the_infinite_grid() {
        // Settings default 1/2/3 -> the same paper the A4 pages get.
        assertEquals(InfiniteGridPreset.GRAPH, infiniteGridPresetForPaperBackground(1))
        assertEquals(InfiniteGridPreset.LINED, infiniteGridPresetForPaperBackground(2))
        assertEquals(InfiniteGridPreset.DOTS, infiniteGridPresetForPaperBackground(3))
    }

    @Test
    fun blank_default_leaves_the_infinite_grid_alone() {
        // 0 = unset: an infinite note keeps its own default (Graph) instead of
        // being forced to a blank grid.
        assertNull(infiniteGridPresetForPaperBackground(0))
        assertNull(infiniteGridPresetForPaperBackground(99))
    }

    @Test
    fun continuous_current_page_follows_the_live_canvas_state() {
        // Settings > Default Paper Background Pattern = Grid while the page row
        // is still unset: the page on screen must show the grid immediately.
        assertEquals(
            PaperPattern.GRID.code,
            PaperPattern.effectiveContinuousCode(
                pagePatternCode = 0,
                isCurrentPage = true,
                defaultPatternCode = PaperPattern.GRID.code
            )
        )
        // The Template row cycles to Lines: same immediate update.
        assertEquals(
            PaperPattern.LINES.code,
            PaperPattern.effectiveContinuousCode(
                pagePatternCode = PaperPattern.GRID.code,
                isCurrentPage = true,
                defaultPatternCode = PaperPattern.LINES.code
            )
        )
    }

    @Test
    fun continuous_other_pages_keep_their_stored_pattern() {
        assertEquals(
            PaperPattern.DOTTED.code,
            PaperPattern.effectiveContinuousCode(
                pagePatternCode = PaperPattern.DOTTED.code,
                isCurrentPage = false,
                defaultPatternCode = PaperPattern.GRID.code
            )
        )
    }
}
