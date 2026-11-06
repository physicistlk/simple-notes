package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import personal.notes.touch.ui.editor.ReorderDrag

/**
 * Reorder drag policy: one block per 44 dp bucket, remainder carried between
 * drag events so slow drags still step and a fast flick does not skip rows.
 */
class ReorderDragTest {

    @Test
    fun travelBelowTheBucketDoesNothing() {
        val drag = ReorderDrag(stepPx = 44f)
        assertEquals(0, drag.add(10f))
        assertEquals(0, drag.add(10f))
    }

    @Test
    fun remainderCarriesToTheNextEvent() {
        val drag = ReorderDrag(stepPx = 44f)
        assertEquals(0, drag.add(30f))
        assertEquals(1, drag.add(30f)) // 60 px total: one step, 16 px left over
        assertEquals(0, drag.add(10f)) // 26 px left over
        assertEquals(1, drag.add(20f)) // 46 px: second step
    }

    @Test
    fun upwardTravelStepsBackwards() {
        val drag = ReorderDrag(stepPx = 44f)
        assertEquals(-1, drag.add(-44f))
        assertEquals(-1, drag.add(-100f)) // one step per event, remainder kept
        assertEquals(1, drag.add(200f)) // leftover -12 + 200 is one downward step
    }

    @Test
    fun resetStartsAFreshGesture() {
        val drag = ReorderDrag(stepPx = 44f)
        assertEquals(0, drag.add(40f))
        drag.reset()
        assertEquals(0, drag.add(40f)) // the earlier 40 px is gone
        assertEquals(1, drag.add(40f))
    }

    @Test
    fun nonFiniteDeltasAreIgnored() {
        val drag = ReorderDrag(stepPx = 44f)
        assertEquals(0, drag.add(Float.NaN))
        assertEquals(0, drag.add(Float.POSITIVE_INFINITY))
        assertEquals(1, drag.add(44f))
    }
}
