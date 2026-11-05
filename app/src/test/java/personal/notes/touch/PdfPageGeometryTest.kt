package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import personal.notes.touch.util.PdfPageGeometry

/**
 * Defect F-10 (vector PDF import): pure coordinate math between pdfium user
 * space (bottom-left origin, y up) and the canvas (top-left origin, y down).
 * This is the part of the pdfium integration that is testable on the JVM.
 */
class PdfPageGeometryTest {

    @Test
    fun canvasScale_maps_page_points_to_rendered_pixels() {
        // A4 page is 595 pt wide; rendered at 1080 px.
        assertEquals(1080f / 595f, PdfPageGeometry.canvasScale(595f, 1080), 1e-4f)
        // Guard rails: degenerate inputs fall back to identity.
        assertEquals(1f, PdfPageGeometry.canvasScale(0f, 1080), 0f)
        assertEquals(1f, PdfPageGeometry.canvasScale(595f, 0), 0f)
    }

    @Test
    fun userRectToCanvas_flips_y_axis_and_keeps_box_ordered() {
        // User space rect (y up): top=700, bottom=690 → canvas y: 842-700=142,
        // 842-690=152 (y down).
        val canvas = PdfPageGeometry.userRectToCanvas(
            floatArrayOf(100f, 700f, 200f, 690f),
            pageHeightPts = 842f,
            pxPerPt = 1f
        )
        assertEquals(100f, canvas[0], 1e-4f)
        assertEquals(142f, canvas[1], 1e-4f)
        assertEquals(200f, canvas[2], 1e-4f)
        assertEquals(152f, canvas[3], 1e-4f)
        // Normalized ordering is guaranteed even for swapped input.
        assertEquals(true, canvas[0] <= canvas[2])
        assertEquals(true, canvas[1] <= canvas[3])
    }

    @Test
    fun userRectToCanvas_scales_by_pixels_per_point() {
        val canvas = PdfPageGeometry.userRectToCanvas(
            floatArrayOf(10f, 20f, 30f, 15f),
            pageHeightPts = 100f,
            pxPerPt = 2f
        )
        // left=20, right=60; top=(100-20)*2=160, bottom=(100-15)*2=170.
        assertEquals(20f, canvas[0], 1e-4f)
        assertEquals(160f, canvas[1], 1e-4f)
        assertEquals(60f, canvas[2], 1e-4f)
        assertEquals(170f, canvas[3], 1e-4f)
    }

    @Test
    fun canvasToUserPoint_is_inverse_of_the_forward_map() {
        val user = PdfPageGeometry.canvasToUserPoint(
            x = 100f,
            y = 142f,
            pageHeightPts = 842f,
            pxPerPt = 1f
        )
        assertEquals(100f, user[0], 1e-4f)
        assertEquals(700f, user[1], 1e-4f)
    }

    @Test
    fun unionRect_covers_all_rects_and_handles_empty_input() {
        assertNull(PdfPageGeometry.unionRect(emptyList()))
        val union = PdfPageGeometry.unionRect(
            listOf(
                floatArrayOf(10f, 20f, 30f, 40f),
                floatArrayOf(5f, 60f, 80f, 70f)
            )
        )
        assertEquals(5f, union!![0], 1e-4f)
        assertEquals(20f, union[1], 1e-4f)
        assertEquals(80f, union[2], 1e-4f)
        assertEquals(70f, union[3], 1e-4f)
    }
}
