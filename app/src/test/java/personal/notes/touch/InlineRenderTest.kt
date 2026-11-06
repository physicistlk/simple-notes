package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.text.InlineKind
import personal.notes.touch.text.InlineRender
import personal.notes.touch.text.InlineRenderer
import personal.notes.touch.text.InlineSpan

class InlineRenderTest {

    @Test
    fun visibleMarkersKeepTheText() {
        val render = InlineRenderer.render("a **b** c", hideMarkers = false)
        assertEquals("a **b** c", render.text)
        assertEquals(listOf(InlineSpan(4, 5, InlineKind.BOLD)), render.spans)
        assertEquals(listOf(2..3, 5..6), render.markers)
        assertTrue(render.identity)
        assertNull(render.origToTrans)
    }

    @Test
    fun hiddenMarkersRemoveOnlyTheDelimiters() {
        val render = InlineRenderer.render("a **b** c", hideMarkers = true)
        assertEquals("a b c", render.text)
        assertEquals(listOf(InlineSpan(2, 3, InlineKind.BOLD)), render.spans)
        assertTrue(render.markers.isEmpty())
        assertFalse(render.identity)
    }

    @Test
    fun hiddenNestedMarkersKeepEveryVisibleCharacter() {
        val render = InlineRenderer.render("*a **b** c*", hideMarkers = true)
        assertEquals("a b c", render.text)
        assertEquals(
            listOf(
                InlineSpan(0, 5, InlineKind.ITALIC),
                InlineSpan(2, 3, InlineKind.BOLD)
            ),
            render.spans
        )
    }

    @Test
    fun hiddenCodeSpanDropsBackticks() {
        val render = InlineRenderer.render("use `x = 1` now", hideMarkers = true)
        assertEquals("use x = 1 now", render.text)
        assertEquals(listOf(InlineSpan(4, 9, InlineKind.CODE)), render.spans)
    }

    @Test
    fun sourcePositionsRoundTripThroughTheMapping() {
        val render = InlineRenderer.render("a **b** c", hideMarkers = true)
        for (source in listOf(0, 1, 4, 7, 8)) {
            val shown = InlineRenderer.originalToTransformed(render, source)
            assertEquals(source, InlineRenderer.transformedToOriginal(render, shown))
        }
        assertEquals(0, InlineRenderer.originalToTransformed(render, 0))
        assertEquals(5, InlineRenderer.originalToTransformed(render, 9))
        assertEquals(9, InlineRenderer.transformedToOriginal(render, 5))
    }

    @Test
    fun markerPositionsCollapseToTheNextVisibleChar() {
        val render = InlineRenderer.render("**b**", hideMarkers = true)
        assertEquals("b", render.text)
        assertEquals(0, InlineRenderer.originalToTransformed(render, 0))
        assertEquals(0, InlineRenderer.originalToTransformed(render, 1))
        assertEquals(0, InlineRenderer.originalToTransformed(render, 2))
        assertEquals(2, InlineRenderer.transformedToOriginal(render, 0))
        assertEquals(5, InlineRenderer.transformedToOriginal(render, 1))
    }

    @Test
    fun hiddenUnderlineTagDropsTheTag() {
        val render = InlineRenderer.render("x <u>y</u> z", hideMarkers = true)
        assertEquals("x y z", render.text)
        assertEquals(listOf(InlineSpan(2, 3, InlineKind.UNDERLINE)), render.spans)
        assertEquals(2, InlineRenderer.originalToTransformed(render, 5))
        assertEquals(5, InlineRenderer.transformedToOriginal(render, 2))
    }

    @Test
    fun noMarkersMeansIdentity() {
        val render = InlineRenderer.render("plain", hideMarkers = true)
        assertEquals("plain", render.text)
        assertTrue(render.identity)
        assertEquals(3, InlineRenderer.originalToTransformed(render, 3))
        assertEquals(3, InlineRenderer.transformedToOriginal(render, 3))
    }

    @Test
    fun unmatchedMarkersAreLeftVisible() {
        val render = InlineRenderer.render("**not bold", hideMarkers = true)
        assertEquals("**not bold", render.text)
        assertTrue(render.identity)
        assertTrue(render.spans.isEmpty())
    }

    @Test
    fun emptyPairsMeansIdentity() {
        val render = InlineRenderer.render("``", hideMarkers = true)
        assertEquals("``", render.text)
        assertTrue(render.identity)
    }
}
