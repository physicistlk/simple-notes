package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import personal.notes.touch.ui.canvas.PageLayerMeta

class MetaDecodeTest {
    @Test
    fun decodesTheFileOfTheDevice() {
        val text = """{"version":1,"current":2,"layers":[{"index":1,"name":"Layer1","visible":true},{"index":2,"name":"Layer2","visible":true}]}"""
        val stack = PageLayerMeta.decode(text)
        assertNotNull("the metadata of the device must decode", stack)
        val s = requireNotNull(stack)
        assertEquals(2, s.size)
        assertEquals(2, s.currentIndex)
        assertEquals("Layer2", s.layers[1].name)
    }
}
