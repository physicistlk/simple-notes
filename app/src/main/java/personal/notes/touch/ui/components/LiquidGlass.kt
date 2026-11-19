package personal.notes.touch.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The pixels of the page that sit under the glass.
 *
 * `bitmap` is the page in page pixels, `pageOriginX/Y` is the page coordinate
 * of the glass' own top-left corner and `invScale` says how many page pixels
 * one screen pixel covers.
 *
 * `sampleScale` says how much the backdrop is scaled down before the shader
 * samples it: the shader reads a small copy of the backdrop, and the copy of
 * a page is a quarter of it. A backdrop that is already that small (the
 * tiles of an annotation layer, composed at that size) says `1f` and is
 * sampled as it is.
 */
data class GlassBackdrop(
    val bitmap: Bitmap?,
    val pageOriginX: Float,
    val pageOriginY: Float,
    val invScale: Float,
    val sampleScale: Float = SAMPLE_SCALE,
    /** Screen-space grid spacing in pixels (0 when no procedural grid is active). */
    val gridStepPx: Float = 0f,
    /** Screen-space pan X for procedural grid refraction. */
    val gridOriginX: Float = 0f,
    /** Screen-space pan Y for procedural grid refraction. */
    val gridOriginY: Float = 0f,
    /** 0 = none, 1 = lined, 2 = graph, 3 = dots/isometric. */
    val gridMode: Int = 0
)

/** Where a floating element sits on the screen (root pixels). */
class GlassOrigin {
    var x by androidx.compose.runtime.mutableFloatStateOf(0f)
    var y by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/**
 * Everything that changes the look of a piece of glass, in one place.
 *
 * LIGHT (the pen dock) and DARK (the HUD pills) are the two presets.
 *
 *  tint        colour + alpha laid over the refracted page. THE most important
 *              knob: 0.15-0.30 reads as glass, above 0.5 it becomes a white card.
 *  fallback    what is drawn below Android 13 (no shader).
 *  refract     how far the page is bent at the very rim.
 *  bezel       how wide the refracting rim is. The centre stays flat.
 *  blur        frost radius in the CORE. The rim is frosted only 35% as much,
 *              so the bezel stays clear and the middle protects the icons.
 *  dispersion  the chromatic split on the rim.
 *  highlight   strength of the specular glint (lit top-left, bounce bottom-right).
 *  paper       colour of the page (the ink layer is transparent where empty).
 *  outside     colour of the canvas around the page (the grey margin).
 */
@Immutable
data class LiquidGlassStyle(
    val tint: Color,
    val fallback: Color,
    val refract: Dp = 6.dp,
    val bezel: Dp = 14.dp,
    val blur: Dp = 3.8.dp,
    val dispersion: Dp = 1.6.dp,
    val highlight: Float = 0.78f,
    val paper: Color = Color.White,
    val outside: Color = Color(0xFFEFEFEF)
) {
    companion object {
        val Light = LiquidGlassStyle(
            tint = Color(0xFFEAF0FA).copy(alpha = 0.34f),
            fallback = Color(0xFFEEF3FA).copy(alpha = 0.82f)
        )
        val Dark = LiquidGlassStyle(
            tint = Color(0xFF14161E).copy(alpha = 0.56f),
            fallback = Color(0xFF14161E).copy(alpha = 0.82f),
            refract = 8.dp,
            bezel = 10.dp,
            blur = 2.5.dp,
            highlight = 0.48f
        )
    }
}

/** How much smaller the sampled page is than the page itself. */
private const val SAMPLE_SCALE = 0.25f

/**
 * AGSL: a real rounded-rectangle lens.
 *
 *  - The distance to the pill's edge is a proper signed-distance function, so
 *    the bezel has the same width on every side, long or short, vertical or
 *    horizontal (the old shader measured from the centre and broke on tall
 *    pills).
 *  - Only the bezel bends the page; the core stays flat and just frosted.
 *  - The result is OPAQUE: the page under the dock is composited on the paper
 *    colour, so the real ink behind the dock can no longer ghost through next
 *    to the bent copy.
 *  - The tint and the light (rim shade, specular, bounce) are added here, so
 *    no extra white slabs are needed in Compose.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader backdrop;
uniform float2 uSize;
uniform float  uRadius;
uniform float2 uPageOrigin;
uniform float  uInvScale;
uniform float2 uBitmapSize;
uniform float  uHasBitmap;
uniform float  uBezel;
uniform float  uRefract;
uniform float  uBlur;
uniform float  uDisp;
uniform float  uHighlight;
uniform half3  uPaper;
uniform half3  uOutside;
uniform half4  uTint;
uniform float  uGridStep;
uniform float2 uGridOrigin;
uniform float  uGridMode;

half3 synthGrid(float2 screenPt, half3 baseCol) {
    if (uGridMode < 0.5 || uGridStep < 8.0) return baseCol;
    float gx = mod(mod(screenPt.x - uGridOrigin.x, uGridStep) + uGridStep, uGridStep);
    float gy = mod(mod(screenPt.y - uGridOrigin.y, uGridStep) + uGridStep, uGridStep);
    float dx = min(gx, uGridStep - gx);
    float dy = min(gy, uGridStep - gy);
    half3 gridTint = half3(0.835, 0.855, 0.890);
    if (uGridMode < 1.5) {
        // Lined
        float lineY = 1.0 - smoothstep(0.4, 1.4, dy);
        return mix(baseCol, gridTint, half(lineY * 0.65));
    } else if (uGridMode < 2.5) {
        // Graph
        float line = max(1.0 - smoothstep(0.4, 1.4, dx), 1.0 - smoothstep(0.4, 1.4, dy));
        return mix(baseCol, gridTint, half(line * 0.65));
    } else {
        // Dots / Isometric
        float dist = length(float2(dx, dy));
        float dotMask = 1.0 - smoothstep(1.0, 2.4, dist);
        return mix(baseCol, gridTint, half(dotMask * 0.75));
    }
}

half3 pageAt(float2 p, float2 screenPt) {
    half3 base = uPaper;
    if (uHasBitmap > 0.5) {
        if (p.x < 0.0 || p.y < 0.0 || p.x >= uBitmapSize.x || p.y >= uBitmapSize.y) {
            base = uOutside;
        } else {
            half4 c = backdrop.eval(p);
            base = c.rgb + uPaper * (1.0 - c.a);
        }
    }
    return synthGrid(screenPt, base);
}

half3 frost(float2 p, float2 screenPt, float r, float screenR) {
    half3 s = pageAt(p, screenPt);
    for (int i = 0; i < 4; i++) {
        float a = float(i) * 1.570796;
        float2 o1 = float2(cos(a), sin(a));
        float2 o2 = float2(cos(a + 0.785398), sin(a + 0.785398));
        s += pageAt(p + o1 * r * 0.5, screenPt + o1 * screenR * 0.5);
        s += pageAt(p + o2 * r, screenPt + o2 * screenR);
    }
    return s / 9.0;
}

half4 main(float2 fragCoord) {
    float2 hs = uSize * 0.5;
    float2 p = fragCoord - hs;

    // Signed distance to the rounded rectangle (negative inside).
    float r = min(uRadius, min(hs.x, hs.y));
    float2 q = abs(p) - hs + float2(r);
    float d = min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;

    // t = 1 on the very rim, 0 at the inner edge of the bezel and in the core.
    float depth = clamp(-d, 0.0, uBezel);
    float tLinear = 1.0 - depth / max(uBezel, 1.0);
    // Smooth meniscus lens profile
    float t = tLinear * tLinear * (3.0 - 2.0 * tLinear);

    // Smooth outward normal of the rounded-rect rim.
    float2 cornerVec = max(q, float2(0.0));
    float cornerLen = length(cornerVec);
    float2 n;
    if (cornerLen > 0.001) {
        n = cornerVec / cornerLen;
    } else if (q.x > q.y) {
        n = float2(1.0, 0.0);
    } else {
        n = float2(0.0, 1.0);
    }
    n *= sign(p);

    // Snell's-law meniscus refraction: flat core (0px shift across the main body
    // so strokes align 1:1 with the canvas), bending only on the curved outer rim.
    float rimT = clamp(1.0 - (-d) / max(uBezel * 0.34, 1.0), 0.0, 1.0);
    float bend = rimT * rimT * rimT;
    float2 refractOffset = -n * bend * uRefract;
    float2 srcScreen = fragCoord + refractOffset;
    float2 base = uPageOrigin + srcScreen * uInvScale;

    // Clear refractive rim, silky frosted core.
    float screenRad = mix(uBlur * 0.30, uBlur, 1.0 - t);
    float rad = screenRad * uInvScale;
    float2 dvScreen = n * (uDisp * (0.35 + 0.65 * t));
    float2 dv = dvScreen * uInvScale;

    // Frosted body + chromatic dispersion split across R/G/B channels.
    half3 col = frost(base, srcScreen, rad, screenRad);
    if (tLinear >= 0.015) {
        half rCh = pageAt(base + dv, srcScreen + dvScreen).r;
        half bCh = pageAt(base - dv, srcScreen - dvScreen).b;
        col = half3(mix(col.r, rCh, half(0.82)), col.g, mix(col.b, bCh, half(0.82)));
    }

    // Apple Liquid Glass optical body depth:
    // Give light glass a cool quartz-silver body density so pure-white (1.0)
    // specular glints and inner caustics have real headroom over white paper.
    float isDarkGlass = step(0.5, 1.0 - float(uTint.r));
    float minorAxis = (hs.x < hs.y) ? (fragCoord.x / max(uSize.x, 1.0)) : (fragCoord.y / max(uSize.y, 1.0));
    float cylCurve = sin(clamp(minorAxis, 0.0, 1.0) * 3.14159265);
    if (isDarkGlass < 0.5) {
        // Light Liquid Glass: cool silver-sky quartz body + cylindrical sheen
        half3 quartzTop = half3(0.915, 0.942, 0.982);
        half3 quartzBot = half3(0.845, 0.885, 0.948);
        half3 quartzBody = mix(quartzTop, quartzBot, half(clamp(minorAxis, 0.0, 1.0)));
        col = mix(col, quartzBody, half(0.42));
        col = mix(col, uTint.rgb, uTint.a * half(0.65));
        // Soft bright crown along the upper-center cylinder
        float crown = smoothstep(0.55, 0.12, minorAxis) * (1.0 - tLinear * 0.6);
        col += half3(0.055, 0.062, 0.072) * half(crown);
    } else {
        col = mix(col, uTint.rgb, uTint.a);
    }

    // Boost chroma/vibrance of anything refracted underneath
    half luma = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(luma), col, half(1.22));

    // 1. Outer Fresnel thickness shadow right inside the boundary (defines edge on white paper)
    float outerEdge = pow(clamp(1.0 - (-d) / max(uBezel * 0.24, 1.0), 0.0, 1.0), 2.0);
    col *= half(1.0 - 0.15 * outerEdge);

    // 2. Inner Caustic Meniscus Ring (signature Apple Liquid Glass inner luminous ridge)
    float causticBand = exp(-pow((tLinear - 0.36) / 0.16, 2.0));
    col += half3(0.065, 0.082, 0.105) * half(causticBand * (0.65 + 0.35 * cylCurve));

    // 3. Subtle iridescent prism tint along curved corners
    float cornerFactor = clamp(cornerLen / max(r, 1.0), 0.0, 1.0) * tLinear;
    half3 prismTint = mix(
        half3(0.74, 0.91, 1.00),
        half3(0.96, 0.82, 1.00),
        half(0.5 + 0.5 * n.x * n.y)
    );
    col = mix(col, prismTint, half(cornerFactor * 0.14));

    // 4. Directional anisotropic specular glint (top-left primary + bottom-right sky bounce)
    float2 L = normalize(float2(-0.52, -0.85));
    float facing = dot(n, L);
    float rimSpec = pow(tLinear, 2.6);
    float topGlint = rimSpec * pow(max(facing, 0.0), 1.45);
    float botBounce = rimSpec * 0.48 * pow(max(-facing, 0.0), 1.75);
    col += half3(1.0, 1.0, 1.0) * half(uHighlight * topGlint);
    col += half3(0.82, 0.91, 1.0) * half(uHighlight * botBounce);

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

/**
 * Holds the AGSL program and the bitmap it samples.
 *
 * The program is compiled once; when the page bitmap changes only the input
 * shader is swapped. Use one instance per floating element.
 */
class LiquidGlassShader {
    private var runtime: RuntimeShader? = null
    private var input: BitmapShader? = null
    private var inputBitmap: Bitmap? = null

    /**
     * The texture the shader samples: a quarter size copy of the page.
     *
     * The page bitmap is 26 MB for an A4 sheet, and the glass samples it
     * every frame - the samples missed the texture cache and the dock lagged
     * behind the page while it scrolled. The small copy is built in the
     * background, so the sampling turns into cache hits.
     */
    private var sampleBitmap: Bitmap? = null
    private var sampleSource: Bitmap? = null
    private var sampleGeneration: Int = -1
    private var sampleScale: Float = SAMPLE_SCALE
    private var samplePendingSource: Bitmap? = null
    private var samplePendingGeneration: Int = -1
    private var samplePendingScale: Float = SAMPLE_SCALE

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyBackdropBitmap: Bitmap by lazy {
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    }

    /** The paint that draws the refracted backdrop. */
    val shaderPaint: Paint get() = paint

    /**
     * The small copy of the page the shader samples.
     *
     * The copy is built in the background: building it on the draw thread
     * would cost tens of milliseconds on a 26 MB page.
     *
     * A backdrop that is sampled as it is (a scale of 1) needs no copy: it
     * is used directly.
     */
    private fun sampleFor(source: Bitmap, scale: Float): PageSample? {
        val generation = try {
            source.generationId
        } catch (e: Exception) {
            0
        }
        if (scale >= 0.999f) {
            // No copy: the backdrop is sampled as it is.
            return PageSample(source)
        }
        if (sampleSource === source && sampleGeneration == generation && sampleScale == scale) {
            val bitmap = sampleBitmap ?: return null
            return PageSample(bitmap)
        }
        if (samplePendingSource !== source || samplePendingGeneration != generation ||
            samplePendingScale != scale
        ) {
            samplePendingSource = source
            samplePendingGeneration = generation
            samplePendingScale = scale
            SampleBuilder.execute {
                val w = (source.width * scale).toInt().coerceAtLeast(1)
                val h = (source.height * scale).toInt().coerceAtLeast(1)
                val small = try {
                    Bitmap.createScaledBitmap(source, w, h, true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } ?: return@execute
                sampleBitmap = small
                sampleSource = source
                sampleGeneration = generation
                sampleScale = scale
            }
        }
        val bitmap = sampleBitmap ?: return null
        return PageSample(bitmap)
    }

    /**
     * The shader for the current backdrop, or null when the glass cannot be
     * refractive (Android below 13, or no page bitmap yet).
     */
    fun obtain(
        backdrop: GlassBackdrop?,
        width: Float,
        height: Float,
        radiusPx: Float,
        style: LiquidGlassStyle,
        density: Float
    ): Shader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (width <= 0f || height <= 0f) return null
        val k = (backdrop?.sampleScale ?: 1f).coerceAtLeast(0.01f)
        val rawInvScale = backdrop?.invScale ?: 1f
        val invScale = if (rawInvScale > 0f && rawInvScale.isFinite()) rawInvScale else 1f
        val bmp = backdrop?.bitmap
        val sample = if (bmp != null && !bmp.isRecycled) sampleFor(bmp, k) else null
        val activeBitmap = sample?.bitmap ?: emptyBackdropBitmap
        val hasBitmap = if (sample != null) 1f else 0f
        return try {
            if (inputBitmap !== activeBitmap) {
                input = BitmapShader(activeBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                inputBitmap = activeBitmap
            }
            val shaderIn = input ?: return null
            val rs = runtime ?: RuntimeShader(LIQUID_GLASS_AGSL).also { runtime = it }
            rs.setFloatUniform("uSize", width, height)
            rs.setFloatUniform("uRadius", radiusPx)
            rs.setFloatUniform(
                "uPageOrigin",
                (backdrop?.pageOriginX ?: 0f) * k,
                (backdrop?.pageOriginY ?: 0f) * k
            )
            rs.setFloatUniform("uInvScale", invScale * k)
            rs.setFloatUniform(
                "uBitmapSize",
                activeBitmap.width.toFloat(),
                activeBitmap.height.toFloat()
            )
            rs.setFloatUniform("uHasBitmap", hasBitmap)
            rs.setFloatUniform("uBezel", style.bezel.value * density)
            rs.setFloatUniform("uRefract", style.refract.value * density)
            rs.setFloatUniform("uBlur", style.blur.value * density)
            rs.setFloatUniform("uDisp", style.dispersion.value * density)
            rs.setFloatUniform("uHighlight", style.highlight)
            rs.setFloatUniform("uPaper", style.paper.red, style.paper.green, style.paper.blue)
            rs.setFloatUniform(
                "uOutside", style.outside.red, style.outside.green, style.outside.blue
            )
            rs.setFloatUniform(
                "uTint", style.tint.red, style.tint.green, style.tint.blue, style.tint.alpha
            )
            rs.setFloatUniform("uGridStep", backdrop?.gridStepPx ?: 0f)
            rs.setFloatUniform(
                "uGridOrigin",
                backdrop?.gridOriginX ?: 0f,
                backdrop?.gridOriginY ?: 0f
            )
            rs.setFloatUniform("uGridMode", (backdrop?.gridMode ?: 0).toFloat())
            rs.setInputShader("backdrop", shaderIn)
            rs
        } catch (e: Exception) {
            // A recycled bitmap must not take the canvas down.
            e.printStackTrace()
            null
        }
    }
}

/**
 * The small copy of the page.
 *
 * It is rebuilt in the background when the page changes: a new stroke
 * changes the bitmap's generation id, and the old copy stays in use until
 * the new one is ready.
 */
private class PageSample(val bitmap: Bitmap)

/** Builds the small page copies off the main thread. */
private object SampleBuilder {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "liquid-glass-sample").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    fun execute(block: () -> Unit) {
        try {
            executor.execute(block)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Draws the glass: the page under the element, bent, frosted, tinted and lit.
 * Drawn before the content, so the icons stay on top.
 *
 * This is the whole material. Do NOT add a white `.background(...)` or an
 * inner "lens" capsule on top of it - that is what made the dock look like a
 * flat white card.
 *
 * Below Android 13 (or without a page bitmap) a plain translucent tint is used.
 */
fun Modifier.liquidGlassBackdrop(
    shader: LiquidGlassShader,
    cornerRadius: Dp,
    style: LiquidGlassStyle = LiquidGlassStyle.Light,
    backdrop: () -> GlassBackdrop?
): Modifier = this.drawWithContent {
    val r = cornerRadius.toPx()
    val sh = shader.obtain(backdrop(), size.width, size.height, r, style, density)
    if (sh != null) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f, 0f, size.width, size.height,
                shader.shaderPaint.apply { this.shader = sh }
            )
        }
    } else {
        drawRoundRect(color = style.fallback, cornerRadius = CornerRadius(r, r))
    }
    drawContent()
}

private val GlassRimBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color.White.copy(alpha = 0.98f), // crisp specular glint on top-left corner
        0.22f to Color(0xFFD8ECFF).copy(alpha = 0.65f), // cool prism rim
        0.52f to Color(0xFF1E293B).copy(alpha = 0.18f), // subtle glass edge definition over white paper
        0.78f to Color(0xFFE8D8FF).copy(alpha = 0.52f), // subtle lavender-sky transition
        1.00f to Color.White.copy(alpha = 0.88f)  // bright bounce on bottom-right corner
    )
)

private val GlassInnerMeniscusBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color.White.copy(alpha = 0.72f),
        0.35f to Color.White.copy(alpha = 0.16f),
        0.70f to Color(0xFFB8DCFF).copy(alpha = 0.20f),
        1.00f to Color.White.copy(alpha = 0.50f)
    )
)

/**
 * Dual-ring Apple Liquid Glass specular outline:
 * - Outer 1.2dp directional specular + prism rim
 * - Inner inset meniscus highlight ring that gives the capsule 3D lens thickness
 */
fun Modifier.liquidGlassRim(cornerRadius: Dp, shape: Shape): Modifier =
    this
        .border(width = 1.2.dp, brush = GlassRimBrush, shape = shape)
        .drawWithContent {
            drawContent()
            val inset = 2.2.dp.toPx()
            val innerR = (cornerRadius.toPx() - inset).coerceAtLeast(2f)
            if (size.width > inset * 2f && size.height > inset * 2f) {
                drawRoundRect(
                    brush = GlassInnerMeniscusBrush,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f
                    ),
                    cornerRadius = CornerRadius(innerR, innerR),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
        }

/**
 * A soft "liquid light" bloom that follows a hovering stylus (or mouse) over
 * the dock.
 */
@Composable
fun Modifier.liquidStylusSpotlight(): Modifier {
    var position by remember { mutableStateOf<Offset?>(null) }
    var visible by remember { mutableStateOf(false) }
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    val isStylusOrCursor = change.type == PointerType.Stylus ||
                        change.type == PointerType.Mouse ||
                        change.type == PointerType.Eraser
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> {
                            if (isStylusOrCursor) {
                                position = change.position
                                visible = true
                            }
                        }
                        PointerEventType.Exit -> visible = false
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val p = position
            if (visible && p != null) {
                val radius = 95.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color(0xFFB8E2FF).copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = p,
                        radius = radius
                    ),
                    radius = radius,
                    center = p,
                    blendMode = BlendMode.Overlay
                )
            }
        }
}

/**
 * A tool button that floats on the glass: the selected one lifts and sits on
 * a bright bubble, a hovered one tilts towards the pointer.
 */
@Composable
fun LiquidGlassToolSlot(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var tilt by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue = when {
            isSelected && isHovered -> 1.22f
            isSelected -> 1.14f
            isHovered -> 1.16f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toolScale"
    )

    val offsetY by animateFloatAsState(
        targetValue = when {
            isSelected -> -6f
            isHovered -> -4f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.6f),
        label = "toolLift"
    )

    val tiltX by animateFloatAsState(
        targetValue = if (isHovered) -tilt.y * 0.25f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tiltX"
    )
    val tiltY by animateFloatAsState(
        targetValue = if (isHovered) tilt.x * 0.25f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tiltY"
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offsetY
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 12f * density
            }
            .then(
                when {
                    isSelected -> Modifier
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    Color.White.copy(alpha = 0.32f)
                                )
                            )
                        )
                        .border(1.dp, Color.White, CircleShape)
                    isHovered -> Modifier
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    else -> Modifier
                }
            )
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * AGSL Signed-Distance-Field (SDF) smooth-minimum (`smin`) liquid glass morphing
 * shader for two adjacent tool droplets. As `uMorph` springs from 0 -> 1, the
 * second droplet pulls out horizontally to the right of the anchor droplet with
 * a true liquid bridge, meniscus refraction tint, and directional specular glint.
 */
private const val LIQUID_MORPH_DROPLETS_AGSL = """
uniform float2 uSize;
uniform float  uSlotSize;
uniform float  uRadius;
uniform float  uGap;
uniform float  uMorph;
uniform float  uHoverSecond;
uniform float  uExpandDown;
uniform half4  uAccent;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sceneSdf(float2 p) {
    float halfS = uSlotSize * 0.5;
    float2 c1 = float2(halfS, halfS);
    float m = max(uMorph, 0.0);
    float sep = (uSlotSize + uGap) * m;
    float2 c2 = (uExpandDown > 0.5)
        ? float2(halfS, halfS + sep)
        : float2(halfS + sep, halfS);
    float s2 = mix(0.68, 1.0, clamp(m, 0.0, 1.0));
    float d1 = sdRoundBox(p - c1, float2(halfS), uRadius);
    float d2 = sdRoundBox(p - c2, float2(halfS * s2), uRadius * s2);
    float k = mix(16.0, 5.5, clamp(m, 0.0, 1.0)) * smoothstep(0.01, 0.15, m);
    if (m < 0.01) return d1;
    return smin(d1, d2, k);
}

half4 main(float2 fragCoord) {
    float d = sceneSdf(fragCoord);
    float aa = 1.25;
    float alpha = 1.0 - smoothstep(-aa, aa, d);
    if (alpha <= 0.001) return half4(0.0);

    float eps = 1.2;
    float2 n = normalize(float2(
        sceneSdf(fragCoord + float2(eps, 0.0)) - sceneSdf(fragCoord - float2(eps, 0.0)),
        sceneSdf(fragCoord + float2(0.0, eps)) - sceneSdf(fragCoord - float2(0.0, eps))
    ) + float2(0.0001));

    float bezel = 9.0;
    float rimT = clamp(1.0 - (-d) / bezel, 0.0, 1.0);
    float rimSmooth = rimT * rimT * (3.0 - 2.0 * rimT);

    float vy = clamp(fragCoord.y / max(uSize.y, 1.0), 0.0, 1.0);
    half3 col = mix(half3(0.975, 0.985, 1.0), half3(0.895, 0.925, 0.968), half(vy));

    float axisCoord = (uExpandDown > 0.5) ? fragCoord.y : fragCoord.x;
    float secondMask = smoothstep(uSlotSize * 0.85, uSlotSize * 1.15, axisCoord);
    col = mix(col, uAccent.rgb, half(uHoverSecond * secondMask * 0.24));

    // Refracted chromatic edge tint along the liquid meniscus.
    half3 prism = mix(half3(0.75, 0.90, 1.0), half3(0.98, 0.82, 0.95), half(0.5 + 0.5 * n.x));
    col = mix(col, prism, half(rimSmooth * 0.20));

    // Outer Fresnel rim definition + inner caustic ring.
    float edgeDark = pow(clamp(1.0 - (-d) / 2.6, 0.0, 1.0), 2.0);
    col *= half(1.0 - 0.14 * edgeDark);
    float caustic = exp(-pow((rimT - 0.35) / 0.18, 2.0));
    col += half3(0.08, 0.09, 0.11) * half(caustic);

    // Directional top-left specular glint + bottom-right bounce.
    float2 L = normalize(float2(-0.55, -0.83));
    float spec = pow(rimT, 2.4) * pow(max(dot(n, L), 0.0), 1.4);
    float bounce = pow(rimT, 2.4) * 0.42 * pow(max(-dot(n, L), 0.0), 1.7);
    col += half3(1.0) * half(0.85 * spec) + half3(0.85, 0.92, 1.0) * half(0.55 * bounce);

    half bodyAlpha = half(alpha * 0.93);
    return half4(clamp(col, 0.0, 1.0) * bodyAlpha, bodyAlpha);
}
"""

/**
 * AGSL SDF smooth-minimum (`smin`) liquid glass segmented pill toggle shader.
 * As `uProgress` springs between 0 (left) and 1 (right), two internal capsules
 * stretch and merge via `smin` like a fluid droplet sliding inside a glass track.
 */
private const val LIQUID_TOGGLE_AGSL = """
uniform float2 uSize;
uniform float  uProgress;
uniform float  uStretch;
uniform half3  uAccent;
uniform float  uEnabled;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float thumbSdf(float2 p) {
    float pad = 2.5;
    float halfH = uSize.y * 0.5 - pad;
    float halfW = (uSize.x * 0.5 - pad) * 0.5;
    float leftX = pad + halfW;
    float rightX = uSize.x - pad - halfW;
    float cx = mix(leftX, rightX, clamp(uProgress, 0.0, 1.0));
    // Trailing/leading droplet split during mid-flight for fluid elongation.
    float split = uStretch * halfW * 0.42;
    float d1 = sdRoundBox(p - float2(cx - split, uSize.y * 0.5), float2(halfW * (1.0 - uStretch * 0.10), halfH * (1.0 - uStretch * 0.08)), halfH);
    float d2 = sdRoundBox(p - float2(cx + split, uSize.y * 0.5), float2(halfW * (1.0 - uStretch * 0.10), halfH * (1.0 - uStretch * 0.08)), halfH);
    return smin(d1, d2, 10.0 * uStretch + 1.0);
}

half4 main(float2 fragCoord) {
    float rTrack = uSize.y * 0.5;
    float dTrack = sdRoundBox(fragCoord - uSize * 0.5, uSize * 0.5, rTrack);
    float trackAlpha = 1.0 - smoothstep(-1.0, 1.0, dTrack);
    if (trackAlpha <= 0.001) return half4(0.0);

    // Subtle frosted glass track.
    half3 trackCol = mix(half3(0.94, 0.955, 0.975), half3(0.985, 0.99, 1.0), half(fragCoord.y / max(uSize.y, 1.0)));
    float trackRim = clamp(1.0 - (-dTrack) / 2.2, 0.0, 1.0);
    half3 borderCol = (uEnabled > 0.5) ? mix(trackCol, uAccent, half(0.45)) : half3(0.82, 0.84, 0.87);
    half3 outCol = mix(trackCol, borderCol, half(trackRim * 0.75));

    // Liquid glass thumb droplet.
    float dThumb = thumbSdf(fragCoord);
    float thumbMask = 1.0 - smoothstep(-1.1, 1.1, dThumb);
    if (thumbMask > 0.001 && uEnabled > 0.5) {
        float eps = 1.1;
        float2 n = normalize(float2(
            thumbSdf(fragCoord + float2(eps, 0.0)) - thumbSdf(fragCoord - float2(eps, 0.0)),
            thumbSdf(fragCoord + float2(0.0, eps)) - thumbSdf(fragCoord - float2(0.0, eps))
        ) + float2(0.0001));
        float rimT = clamp(1.0 - (-dThumb) / 8.0, 0.0, 1.0);
        float vy = clamp(fragCoord.y / max(uSize.y, 1.0), 0.0, 1.0);
        half3 thumbCol = mix(uAccent * half(1.08), uAccent * half(0.88), half(vy));
        // Meniscus specular glint & inner caustic glow.
        float2 L = normalize(float2(-0.45, -0.89));
        float spec = pow(rimT, 2.2) * pow(max(dot(n, L), 0.0), 1.3);
        float caustic = exp(-pow((rimT - 0.35) / 0.20, 2.0));
        thumbCol += half3(1.0) * half(0.55 * spec + 0.14 * caustic);
        outCol = mix(outCol, clamp(thumbCol, 0.0, 1.0), half(thumbMask));
    }

    return half4(outCol * half(trackAlpha), half(trackAlpha));
}
"""

/**
 * Draws the SDF `smin`-blended liquid glass background behind two horizontally
 * adjacent droplets (used by `SelectionToolSlot`). Uses AGSL `RuntimeShader` on
 * Android 13+ and a smooth Canvas fallback on older API levels.
 */
@Composable
fun Modifier.liquidMorphDropletsBackground(
    slotSizePx: Float,
    gapPx: Float,
    radiusPx: Float,
    morphProgress: Float,
    hoverSecond: Float,
    accentColor: Color,
    expandDown: Boolean = false
): Modifier {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LIQUID_MORPH_DROPLETS_AGSL) }.getOrNull()
        } else {
            null
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    return this.drawWithContent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && size.width > 0f && size.height > 0f) {
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uSlotSize", slotSizePx)
            shader.setFloatUniform("uRadius", radiusPx)
            shader.setFloatUniform("uGap", gapPx)
            shader.setFloatUniform("uMorph", morphProgress)
            shader.setFloatUniform("uHoverSecond", hoverSecond)
            shader.setFloatUniform("uExpandDown", if (expandDown) 1f else 0f)
            shader.setFloatUniform("uAccent", accentColor.red, accentColor.green, accentColor.blue, accentColor.alpha)
            paint.shader = shader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        } else {
            val m = morphProgress.coerceAtLeast(0f)
            val extW = if (expandDown) slotSizePx else slotSizePx + (slotSizePx + gapPx) * m
            val extH = if (expandDown) slotSizePx + (slotSizePx + gapPx) * m else slotSizePx
            drawRoundRect(
                color = Color(0xFFF1F5FB).copy(alpha = 0.94f),
                size = androidx.compose.ui.geometry.Size(extW.coerceAtMost(size.width), extH.coerceAtMost(size.height)),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                size = androidx.compose.ui.geometry.Size(extW.coerceAtMost(size.width), extH.coerceAtMost(size.height)),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
            )
        }
        drawContent()
    }
}

/**
 * Liquid Glass Morphing two-option segmented toggle (used for Vertical/Horizontal
 * and Continuous/Single page turning controls). Uses AGSL SDF `smin` liquid
 * droplet elongation + low-damping spring physics.
 */
@Composable
fun LiquidMorphSegmentedToggle(
    leftLabel: String,
    rightLabel: String,
    isRightSelected: Boolean,
    accentColor: Color,
    enabled: Boolean = true,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (isRightSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.56f,
            stiffness = 340f
        ),
        label = "liquidToggleProgress"
    )
    val stretch = (4f * progress.coerceIn(0f, 1f) * (1f - progress.coerceIn(0f, 1f))).coerceIn(0f, 1f)
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(LIQUID_TOGGLE_AGSL) }.getOrNull()
        } else {
            null
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .drawWithContent {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && size.width > 0f && size.height > 0f) {
                    shader.setFloatUniform("uSize", size.width, size.height)
                    shader.setFloatUniform("uProgress", progress)
                    shader.setFloatUniform("uStretch", stretch)
                    shader.setFloatUniform("uAccent", accentColor.red, accentColor.green, accentColor.blue)
                    shader.setFloatUniform("uEnabled", if (enabled) 1f else 0f)
                    paint.shader = shader
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                    }
                } else {
                    val r = size.height * 0.5f
                    drawRoundRect(
                        color = Color(0xFFF3F6FA),
                        cornerRadius = CornerRadius(r, r)
                    )
                    if (enabled) {
                        val pad = 2.5.dp.toPx()
                        val thumbW = size.width * 0.5f - pad
                        val thumbX = pad + (size.width * 0.5f - pad) * progress.coerceIn(0f, 1f)
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(thumbX, pad),
                            size = androidx.compose.ui.geometry.Size(thumbW, size.height - pad * 2f),
                            cornerRadius = CornerRadius(r - pad, r - pad)
                        )
                    }
                }
                drawContent()
            }
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .matchParentSize()
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSelectLeft
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = leftLabel,
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = if (enabled && !isRightSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                    color = when {
                        !enabled -> Color(0xFFBBBBBB)
                        !isRightSelected -> Color.White
                        else -> accentColor
                    }
                )
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSelectRight
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = rightLabel,
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = if (enabled && isRightSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                    color = when {
                        !enabled -> Color(0xFFBBBBBB)
                        isRightSelected -> Color.White
                        else -> accentColor
                    }
                )
            }
        }
    }
}
