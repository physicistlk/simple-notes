package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.PageLayerMeta
import personal.notes.touch.ui.canvas.PageLayerStack

/**
 * Tests of the layer stack of a page.
 *
 * The stack is the pure part of the layer panel: the base layer is always
 * there, a page has three layers at most, a hidden layer is masked (not
 * deleted) and the metadata of the page survives the round trip.
 */
class PageLayerStackTest {

    @Test
    fun aPageAlwaysHasTheBaseLayer() {
        val stack = PageLayerStack.base()
        assertEquals(1, stack.size)
        assertEquals(1, stack.currentIndex)
        assertEquals("Layer1", stack.layers[0].name)
        assertTrue(stack.layers[0].visible)
    }

    @Test
    fun theBaseLayerCannotBeDeleted() {
        val stack = PageLayerStack.base()
        stack.add()
        // The base layer is layer 1: it can only be hidden.
        assertNull(stack.remove(1))
        assertEquals(2, stack.size)
        assertNotNull(stack.layers[0])
        assertEquals(1, stack.layers[0].index)
    }

    @Test
    fun aPageHasThreeLayersAtMost() {
        val stack = PageLayerStack.base()
        assertTrue(stack.canAdd)
        assertNotNull(stack.add())
        assertNotNull(stack.add())
        assertEquals(3, stack.size)
        assertFalse(stack.canAdd)
        assertNull(stack.add())
        assertEquals(3, stack.size)
    }

    @Test
    fun theNewLayerIsTheCurrentOne() {
        val stack = PageLayerStack.base()
        val second = stack.add()
        assertNotNull(second)
        assertEquals(2, stack.currentIndex)
        assertTrue(stack.current === second)

        // Tapping a row makes that layer the current one.
        assertTrue(stack.select(1))
        assertEquals(1, stack.currentIndex)
    }

    @Test
    fun aHiddenLayerIsMaskedAndNotDrawn() {
        val stack = PageLayerStack.base()
        stack.add()
        assertEquals(2, stack.visibleLayers().size)

        assertTrue(stack.setVisible(2, false))
        // The layer is still there (nothing was deleted), but it is not
        // drawn on the page.
        assertEquals(2, stack.size)
        assertEquals(1, stack.visibleLayers().size)
        assertFalse(stack.layers[1].visible)

        // The eye brings it back.
        assertTrue(stack.setVisible(2, true))
        assertEquals(2, stack.visibleLayers().size)
    }

    @Test
    fun aDeletedLayerRenumbersTheOnesAboveIt() {
        val stack = PageLayerStack.base()
        stack.add()
        val third = stack.add() ?: return
        assertEquals(3, third.index)
        assertEquals("Layer3", third.name)

        assertNotNull(stack.remove(2))
        assertEquals(2, stack.size)
        assertEquals(1, stack.layers[0].index)
        assertEquals(2, stack.layers[1].index)
        assertEquals("Layer2", stack.layers[1].name)
        // The layer that was layer 3 is layer 2 now.
        assertTrue(stack.layers[1] === third)
    }

    @Test
    fun aRemovedLayerComesBackAtItsPlace() {
        val stack = PageLayerStack.base()
        stack.add()
        val third = stack.add()
        val second = stack.layers[1]

        stack.remove(2)
        assertEquals(2, stack.size)

        assertTrue(stack.restore(second, 2))
        assertEquals(3, stack.size)
        assertEquals(1, stack.layers[0].index)
        assertTrue(stack.layers[1] === second)
        assertEquals(3, stack.layers[2].index)
        assertTrue(stack.layers[2] === third)
    }

    @Test
    fun theMetadataSurvivesTheRoundTrip() {
        val stack = PageLayerStack.base()
        stack.add()
        stack.add()
        stack.setVisible(2, false)
        stack.select(3)

        val text = PageLayerMeta.encode(stack)
        val read = PageLayerMeta.decode(text)
        assertNotNull(read)
        val back = read ?: return

        assertEquals(3, back.size)
        assertEquals(3, back.currentIndex)
        assertEquals("Layer1", back.layers[0].name)
        assertEquals("Layer2", back.layers[1].name)
        assertEquals("Layer3", back.layers[2].name)
        assertTrue(back.layers[0].visible)
        assertFalse(back.layers[1].visible)
        assertTrue(back.layers[2].visible)
        assertEquals(1, back.layers[0].index)
        assertEquals(2, back.layers[1].index)
        assertEquals(3, back.layers[2].index)
    }

    @Test
    fun brokenMetadataLeavesTheBaseLayerOnly() {
        assertNull(PageLayerMeta.decode("this is not json"))
        assertNull(PageLayerMeta.decode("{\"version\":1,\"layers\":[]}"))
        // A page without a metadata file has the base layer.
        val stack = PageLayerStack.base()
        assertEquals(1, stack.size)
    }
}
