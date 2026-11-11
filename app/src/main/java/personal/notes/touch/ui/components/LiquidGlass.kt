package personal.notes.touch.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The pixels of the page that sit under the dock.
 *
 * `bitmap` is the page in page pixels (the ink layer in single-page mode, the
 * page image in the reader), `pageOriginX/Y` is the page coordinate of the
 * dock's own top-left corner and `invScale` says how many page pixels one
 * screen pixel covers.
 */
data class GlassBackdrop(
    val bitmap: Bitmap?,
    val pageOriginX: Float,
    val pageOriginY: Float,
    val invScale: Float
)

/**
 * Where a floating element sits on the screen (root pixels).
 *
 * The glass needs it to know which part of the page lies under the element:
 * the page is sampled in page pixels, the element is placed in root pixels.
 */
class GlassOrigin {
    var x = 0f
    var y = 0f
}

/**
 * AGSL: the bezel of the dock acts as a lens.
 *
 * The pixels near the rim are bent towards the middle of the pill, the glass
 * is frosted with a nine tap disc, and the rim splits the light into its
 * colours - the way a thick glass edge does.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader backdrop;
uniform float2 uSize;
uniform float2 uPageOrigin;
uniform float uInvScale;
uniform float uRefract;
uniform float uBlur;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float2 center = float2(0.5, 0.5);
    float2 delta = uv - center;
    float aspect = uSize.y / max(uSize.x, 1.0);
    float dist = length(delta * float2(1.0, aspect));

    // The further from the middle, the stronger the bend.
    float edge = smoothstep(0.16, 0.60, dist);
    float2 bent = fragCoord - delta * edge * uRefract;

    // Frosted glass: a small disc of taps around the bent point.
    float2 base = uPageOrigin + bent * uInvScale;
    float blur = uBlur * uInvScale;

    half4 c = backdrop.eval(base);
    c += backdrop.eval(base + float2(blur, 0.0));
    c += backdrop.eval(base - float2(blur, 0.0));
    c += backdrop.eval(base + float2(0.0, blur));
    c += backdrop.eval(base - float2(0.0, blur));
    c += backdrop.eval(base + float2(blur * 0.707, blur * 0.707));
    c += backdrop.eval(base - float2(blur * 0.707, blur * 0.707));
    c += backdrop.eval(base + float2(blur * 0.707, -blur * 0.707));
    c += backdrop.eval(base - float2(blur * 0.707, -blur * 0.707));
    c /= 9.0;

    // Chromatic dispersion on the rim.
    float disp = 1.6 * edge;
    half r = backdrop.eval(base + float2(disp, 0.0)).r;
    half b = backdrop.eval(base - float2(disp, 0.0)).b;
    return half4(r, c.g, b, c.a);
}
"""

/**
 * Holds the AGSL program and the bitmap it samples.
 *
 * The program is compiled once and only the uniforms are updated per frame;
 * the input shader is rebuilt only when the page bitmap changes.
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
    fun obtain(backdrop: GlassBackdrop?, width: Float, height: Float): Shader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val bmp = backdrop?.bitmap ?: return null
        if (width <= 0f || height <= 0f || backdrop.invScale <= 0f) return null
        return try {
            if (inputBitmap !== bmp) {
                input = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                inputBitmap = bmp
                runtime = null
            }
            val shaderIn = input ?: return null
            val rs = runtime ?: RuntimeShader(LIQUID_GLASS_AGSL).also { runtime = it }
            rs.setFloatUniform("uSize", width, height)
            rs.setFloatUniform("uPageOrigin", backdrop.pageOriginX, backdrop.pageOriginY)
            rs.setFloatUniform("uInvScale", backdrop.invScale)
            rs.setFloatUniform("uRefract", 18f)
            rs.setFloatUniform("uBlur", 5f)
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
 * Draws the page under the dock through the refractive glass.
 *
 * The backdrop is drawn *before* the content of the modifier chain, so the
 * tool icons and the tint of the glass stay on top of it.
 */
fun Modifier.liquidGlassBackdrop(
    shader: LiquidGlassShader,
    backdrop: () -> GlassBackdrop?
): Modifier = this.drawWithContent {
    val sh = shader.obtain(backdrop(), size.width, size.height)
    if (sh != null) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f, 0f, size.width, size.height,
                shader.shaderPaint.apply { this.shader = sh }
            )
        }
    }
    drawContent()
}

/**
 * The wet meniscus along the top edge of a thick piece of glass.
 */
fun Modifier.liquidGlassMeniscus(cornerRadius: Dp): Modifier = this.drawWithContent {
    drawContent()
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.38f
        ),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()),
        blendMode = BlendMode.Overlay
    )
}

/**
 * The specular rim: a bright glint on the lit edge, a cooler tint where the
 * light falls away.
 */
@Composable
fun Modifier.liquidGlassRim(
    cornerRadius: Dp,
    shape: androidx.compose.ui.graphics.Shape
): Modifier {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.92f),
            Color.White.copy(alpha = 0.20f),
            Color(0xFFB8D4FF).copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.60f)
        ),
        start = Offset(0f, 0f),
        end = Offset(width, height)
    )
    return this
        .onSizeChanged { width = it.width.toFloat(); height = it.height.toFloat() }
        .border(width = 1.25.dp, brush = rimBrush, shape = shape)
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
