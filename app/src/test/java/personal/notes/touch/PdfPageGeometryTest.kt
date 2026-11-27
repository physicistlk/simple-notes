package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.ui.canvas.ContinuousPageGeometry
import personal.notes.touch.util.PageAspectCache
import personal.notes.touch.util.PdfPageGeometry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
        assertNotNull(union)
        val u = requireNotNull(union)
        assertEquals(5f, u[0], 1e-4f)
        assertEquals(20f, u[1], 1e-4f)
        assertEquals(80f, u[2], 1e-4f)
        assertEquals(70f, u[3], 1e-4f)
    }

    @Test
    fun aspect_computes_portrait_landscape_and_square_ratios() {
        assertEquals(595f / 842f, PdfPageGeometry.aspect(595f, 842f), 1e-5f)
        assertEquals(16f / 9f, PdfPageGeometry.aspect(1920f, 1080f), 1e-5f)
        assertEquals(1f, PdfPageGeometry.aspect(500f, 500f), 1e-5f)
    }

    @Test
    fun aspect_returns_fallback_on_degenerate_inputs() {
        val def = PdfPageGeometry.DEFAULT_ASPECT
        assertEquals(def, PdfPageGeometry.aspect(0f, 0f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(0f, 842f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(595f, 0f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(-595f, 842f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(595f, -842f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(Float.NaN, 842f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(Float.POSITIVE_INFINITY, 842f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(595f, Float.NEGATIVE_INFINITY), 0f)
        assertEquals(def, PdfPageGeometry.aspect(Float.MAX_VALUE, 1e-30f), 0f)
        assertEquals(def, PdfPageGeometry.aspect(1e-40f, 1e30f), 0f)
        assertEquals(1.5f, PdfPageGeometry.aspect(0f, 0f, fallback = 1.5f), 0f)
    }

    @Test
    fun exportPdfPageSize_sizes_portrait_landscape_square_and_clamps_extremes() {
        assertEquals(595 to 842, PdfPageGeometry.exportPdfPageSize(595f / 842f))
        assertEquals(595 to 842, PdfPageGeometry.exportPdfPageSize(0.707f))
        assertEquals(842 to 474, PdfPageGeometry.exportPdfPageSize(16f / 9f))
        assertEquals(842 to 632, PdfPageGeometry.exportPdfPageSize(4f / 3f))
        assertEquals(842 to 842, PdfPageGeometry.exportPdfPageSize(1f))
        // Extreme narrow/wide aspects still clamp to >= 1
        val ultraNarrow = PdfPageGeometry.exportPdfPageSize(0.0001f)
        assertTrue(ultraNarrow.first >= 1 && ultraNarrow.second == 842)
        val ultraWide = PdfPageGeometry.exportPdfPageSize(10000f)
        assertTrue(ultraWide.first == 842 && ultraWide.second >= 1)
        // Degenerate aspect/longSidePts fallbacks
        assertEquals(595 to 842, PdfPageGeometry.exportPdfPageSize(Float.NaN))
        assertEquals(595 to 842, PdfPageGeometry.exportPdfPageSize(-1f))
        assertEquals(1 to 1, PdfPageGeometry.exportPdfPageSize(16f / 9f, longSidePts = 0))
    }

    @Test
    fun thumbnailSize_matches_aspect_and_preserves_a4() {
        assertEquals(240 to 340, PdfPageGeometry.thumbnailSize(0.707f, longSidePx = 340))
        assertEquals(340 to 191, PdfPageGeometry.thumbnailSize(16f / 9f, longSidePx = 340))
        assertEquals(340 to 340, PdfPageGeometry.thumbnailSize(1f, longSidePx = 340))
        assertEquals(240 to 340, PdfPageGeometry.thumbnailSize(0f, longSidePx = 340))
    }

    @Test
    fun pageAspectCache_keys_by_source_and_page_index_for_mixed_pdfs() {
        val cache = PageAspectCache()
        cache.put("a.pdf", 0, 595f, 842f)
        cache.put("a.pdf", 1, 1920f, 1080f)
        cache.put("b.pdf", 0, 500f, 500f)

        val a0 = cache.get("a.pdf", 0)
        val a1 = cache.get("a.pdf", 1)
        val b0 = cache.get("b.pdf", 0)
        assertNotNull(a0)
        assertNotNull(a1)
        assertNotNull(b0)
        assertEquals(595f / 842f, requireNotNull(a0), 1e-5f)
        assertEquals(16f / 9f, requireNotNull(a1), 1e-5f)
        assertEquals(1f, requireNotNull(b0), 1e-5f)
        assertNull(cache.get("a.pdf", 2))
        assertNull(cache.get("", 0))
        assertNull(cache.get("a.pdf", -1))
    }

    @Test
    fun pageAspectCache_getOrLoad_caches_valid_and_does_not_cache_fallback() {
        val cache = PageAspectCache()
        val calls = AtomicInteger(0)

        // Failed lookup (null) returns fallback and does NOT poison cache
        val first = cache.getOrLoad("slides.pdf", 0, fallback = 0.707f) {
            calls.incrementAndGet()
            null
        }
        assertEquals(0.707f, first, 0f)
        assertNull(cache.get("slides.pdf", 0))

        // 0x0 lookup also does NOT poison cache
        val second = cache.getOrLoad("slides.pdf", 0, fallback = 0.707f) {
            calls.incrementAndGet()
            floatArrayOf(0f, 0f)
        }
        assertEquals(0.707f, second, 0f)
        assertNull(cache.get("slides.pdf", 0))

        // Subsequent valid load succeeds and caches
        val third = cache.getOrLoad("slides.pdf", 0, fallback = 0.707f) {
            calls.incrementAndGet()
            floatArrayOf(1280f, 720f)
        }
        assertEquals(16f / 9f, third, 1e-5f)
        assertEquals(3, calls.get())

        // Subsequent lookup hits cache without invoking loadSize
        val fourth = cache.getOrLoad("slides.pdf", 0, fallback = 0.707f) {
            calls.incrementAndGet()
            floatArrayOf(100f, 100f)
        }
        assertEquals(16f / 9f, fourth, 1e-5f)
        assertEquals(3, calls.get())
    }

    @Test
    fun pageAspectCache_invalidate_and_clear_and_concurrent_access() {
        val cache = PageAspectCache()
        cache.put("a.pdf", 0, 595f, 842f)
        cache.put("a.pdf", 1, 1920f, 1080f)
        cache.put("b.pdf", 0, 1000f, 500f)

        cache.invalidate("a.pdf")
        assertNull(cache.get("a.pdf", 0))
        assertNull(cache.get("a.pdf", 1))
        assertNotNull(cache.get("b.pdf", 0))

        cache.clear()
        assertEquals(0, cache.size())

        val pool = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)
        val errors = AtomicInteger(0)
        try {
            repeat(4) { worker ->
                pool.submit {
                    try {
                        for (i in 0 until 200) {
                            val path = "doc_${worker % 2}.pdf"
                            cache.put(path, i % 10, 1920f, 1080f)
                            cache.getOrLoad(path, i % 10) { floatArrayOf(1920f, 1080f) }
                            if (i % 50 == 0) cache.invalidate("doc_0.pdf")
                        }
                    } catch (_: Throwable) {
                        errors.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            assertEquals(0, errors.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun a4_layer_size_remains_unchanged_while_pdf_adapts_to_aspect() {
        assertEquals(
            ContinuousPageGeometry.LayerSize(2160, 3056),
            ContinuousPageGeometry.layerSize("a4", ContinuousPageGeometry.DEFAULT_PAGE_RATIO)
        )
        assertEquals(
            ContinuousPageGeometry.LayerSize(6480, 1215),
            ContinuousPageGeometry.layerSize("pdf", 16f / 9f)
        )
        assertEquals(
            ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            ContinuousPageGeometry.boxRatio("pdf", Float.NaN),
            1e-5f
        )
        assertEquals(
            ContinuousPageGeometry.DEFAULT_PAGE_RATIO,
            ContinuousPageGeometry.boxRatio("pdf", Float.POSITIVE_INFINITY),
            1e-5f
        )
    }

    @Test
    fun pageAspectCache_16ThreadStress_neverExceedsMaxEntries() {
        val maxEntries = 32
        val cache = PageAspectCache(maxEntries = maxEntries)
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val startGate = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val maxObserved = AtomicInteger(0)
        val errors = AtomicInteger(0)
        try {
            repeat(threads) { tid ->
                pool.submit {
                    try {
                        startGate.await()
                        for (i in 0 until 500) {
                            val keyIdx = (tid * 37 + i) % 128
                            cache.put("stress_${tid % 4}.pdf", keyIdx, 1920f, 1080f)
                            val sz = cache.size()
                            var prev = maxObserved.get()
                            while (sz > prev && !maxObserved.compareAndSet(prev, sz)) {
                                prev = maxObserved.get()
                            }
                            if (sz > maxEntries) {
                                errors.incrementAndGet()
                            }
                            cache.get("stress_${tid % 4}.pdf", keyIdx)
                        }
                    } catch (_: Throwable) {
                        errors.incrementAndGet()
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startGate.countDown()
            assertTrue(doneLatch.await(15, TimeUnit.SECONDS))
            assertEquals(0, errors.get())
            assertTrue("maxObserved=${maxObserved.get()} exceeded $maxEntries", maxObserved.get() <= maxEntries)
        } finally {
            pool.shutdownNow()
        }
    }
}
