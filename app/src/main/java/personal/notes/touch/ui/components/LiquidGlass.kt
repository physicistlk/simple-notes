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
 */
data class GlassBackdrop(
    val bitmap: Bitmap?,
    val pageOriginX: Float,
    val pageOriginY: Float,
    val invScale: Float
)

/** Where a floating element sits on the screen (root pixels). */
class GlassOrigin {
    var x = 0f
    var y = 0f
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
    val refract: Dp = 10.dp,
    val bezel: Dp = 12.dp,
    val blur: Dp = 3.dp,
    val dispersion: Dp = 1.1.dp,
    val highlight: Float = 0.55f,
    val paper: Color = Color.White,
    val outside: Color = Color(0xFFEFEFEF)
) {
    companion object {
        val Light = LiquidGlassStyle(
            tint = Color(0xFFF4F6FA).copy(alpha = 0.30f),
            fallback = Color(0xFFF4F6FA).copy(alpha = 0.74f)
        )
        val Dark = LiquidGlassStyle(
            tint = Color(0xFF16161A).copy(alpha = 0.50f),
            fallback = Color(0xFF16161A).copy(alpha = 0.78f),
            refract = 6.dp,
            bezel = 9.dp,
            blur = 2.dp,
            highlight = 0.35f
        )
    }
}

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
uniform float  uBezel;
uniform float  uRefract;
uniform float  uBlur;
uniform float  uDisp;
uniform float  uHighlight;
uniform half3  uPaper;
uniform half3  uOutside;
uniform half4  uTint;

half3 pageAt(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x >= uBitmapSize.x || p.y >= uBitmapSize.y) {
        return uOutside;
    }
    half4 c = backdrop.eval(p);
    return c.rgb + uPaper * (1.0 - c.a);
}

// Nine taps on two rings: thin ink strokes blur into a haze instead of
// turning into a row of ghost copies.
//
// The tap count is the whole performance story: the dock is redrawn with
// every stroke (the window repaints), and the glass is 130 000 pixels wide.
// Nine taps are enough to read as frost; the old 17 taps, evaluated three
// times for the colour split, cost 51 samples per pixel and made the pen
// lag behind the finger.
half3 frost(float2 p, float r) {
    half3 s = pageAt(p);
    for (int i = 0; i < 4; i++) {
        float a = float(i) * 1.570796;
        float2 o1 = float2(cos(a), sin(a));
        float2 o2 = float2(cos(a + 0.785398), sin(a + 0.785398));
        s += pageAt(p + o1 * r * 0.5);
        s += pageAt(p + o2 * r);
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
    float t = 1.0 - depth / uBezel;

    // Outward normal of the rim.
    float2 n;
    if (q.x > 0.0 && q.y > 0.0) {
        n = normalize(q);
    } else if (q.x > q.y) {
        n = float2(1.0, 0.0);
    } else {
        n = float2(0.0, 1.0);
    }
    n *= sign(p);

    // Refraction: only the bezel bends the page.
    float bend = t * t;
    float2 src = fragCoord - n * bend * uRefract;
    float2 base = uPageOrigin + src * uInvScale;

    // Clear rim, frosted core.
    float rad = mix(uBlur * 0.35, uBlur, 1.0 - t) * uInvScale;
    float2 dv = n * (uDisp * t) * uInvScale;

    // One frosted pass for the body; the colour split takes two cheap single
    // samples instead of two more full frosts.
    half3 col = frost(base, rad);
    if (t >= 0.02) {
        col = half3(pageAt(base + dv).r, col.g, pageAt(base - dv).b);
    }

    // Material: tint, a little vibrance.
    col = mix(col, uTint.rgb, uTint.a);
    half luma = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(luma), col, 1.12);

    // Light: the rim is shaded (thickness), then a glint on the lit
    // top-left edge and a weaker bounce on the opposite one.
    float2 L = normalize(float2(-0.55, -0.83));
    float facing = dot(n, L);
    float rim = pow(t, 3.0);
    col *= 1.0 - 0.08 * rim;
    float spec = rim * (max(facing, 0.0) + 0.35 * max(-facing, 0.0));
    col += half3(uHighlight * spec + 0.04 * t * t);

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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The paint that draws the refracted backdrop. */
    val shaderPaint: Paint get() = paint

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
        val bmp = backdrop?.bitmap ?: return null
        if (width <= 0f || height <= 0f || backdrop.invScale <= 0f) return null
        return try {
            if (inputBitmap !== bmp) {
                input = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                inputBitmap = bmp
            }
            val shaderIn = input ?: return null
            val rs = runtime ?: RuntimeShader(LIQUID_GLASS_AGSL).also { runtime = it }
            rs.setFloatUniform("uSize", width, height)
            rs.setFloatUniform("uRadius", radiusPx)
            rs.setFloatUniform("uPageOrigin", backdrop.pageOriginX, backdrop.pageOriginY)
            rs.setFloatUniform("uInvScale", backdrop.invScale)
            rs.setFloatUniform("uBitmapSize", bmp.width.toFloat(), bmp.height.toFloat())
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
        0.00f to Color.White.copy(alpha = 0.95f), // glint on the lit corner
        0.30f to Color.White.copy(alpha = 0.18f),
        0.55f to Color.Black.copy(alpha = 0.10f), // gives the edge a line over white paper
        1.00f to Color.White.copy(alpha = 0.70f)  // bounce on the far corner
    )
)

/** A thin specular outline. (cornerRadius is kept so old call sites compile.) */
@Suppress("UNUSED_PARAMETER")
fun Modifier.liquidGlassRim(cornerRadius: Dp, shape: Shape): Modifier =
    this.border(width = 1.dp, brush = GlassRimBrush, shape = shape)

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
