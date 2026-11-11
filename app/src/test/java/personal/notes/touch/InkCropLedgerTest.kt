package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.InkCropLedger

/**
 * The bookkeeping of the margin crops of a reader page: the strokes that
 * left the page image.
 *
 * The ledger is pure (the bitmap is an opaque payload), so the ids, the
 * order and the undo/redo of the crops can be tested without a device.
 */
class InkCropLedgerTest {

    @Test
    fun aNewLedgerIsEmpty() {
        val ledger = InkCropLedger<String>()
        assertEquals(0, ledger.count)
        assertTrue(ledger.all().isEmpty())
    }

    @Test
    fun aCropGetsItsOwnId() {
        val ledger = InkCropLedger<String>()
        val first = ledger.add(10f, 20f, 30, 40, "a")
        val second = ledger.add(50f, 60f, 70, 80, "b")

        assertEquals(1, first.id)
        assertEquals(2, second.id)
        assertEquals(2, ledger.count)
        assertEquals("a", ledger.crop(first.id)?.payload)
        assertEquals(50f, ledger.crop(second.id)!!.x, 0.0001f)
    }

    @Test
    fun theCropsKeepTheirOrder() {
        val ledger = InkCropLedger<String>()
        ledger.add(1f, 1f, 10, 10, "a")
        ledger.add(2f, 2f, 10, 10, "b")
        ledger.add(3f, 3f, 10, 10, "c")

        assertEquals(listOf("a", "b", "c"), ledger.all().map { it.payload })
    }

    @Test
    fun anUndoTakesTheCropOutAndARedoPutsItBack() {
        val ledger = InkCropLedger<String>()
        val crop = ledger.add(10f, 20f, 30, 40, "a")

        val removed = ledger.remove(crop.id)
        assertNotNull(removed)
        assertEquals(0, ledger.count)

        ledger.restore(removed!!)
        assertEquals(1, ledger.count)
        assertEquals("a", ledger.crop(crop.id)?.payload)
        // The id is stable: the crop comes back under the same name.
        assertEquals(crop.id, ledger.crop(crop.id)?.id)
    }

    @Test
    fun removingACropThatIsNotThereDoesNothing() {
        val ledger = InkCropLedger<String>()
        assertNull(ledger.remove(99))
        assertEquals(0, ledger.count)
    }

    @Test
    fun thePixelSpaceOfTheCropsIsKept() {
        val ledger = InkCropLedger<String>()
        assertEquals(0, ledger.spaceWidth)
        ledger.setSpace(2160, 3056)
        assertEquals(2160, ledger.spaceWidth)
        assertEquals(3056, ledger.spaceHeight)

        // A space that is already known is kept: the ink layer of a page does
        // not change its size.
        ledger.setSpace(1080, 1528)
        assertEquals(2160, ledger.spaceWidth)
    }

    @Test
    fun clearingTheLedgerDropsTheCrops() {
        val ledger = InkCropLedger<String>()
        ledger.add(1f, 1f, 10, 10, "a")
        ledger.add(2f, 2f, 10, 10, "b")
        ledger.clear()
        assertEquals(0, ledger.count)
        assertTrue(ledger.all().isEmpty())
    }
}
