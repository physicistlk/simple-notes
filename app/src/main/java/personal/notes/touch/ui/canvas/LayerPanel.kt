package personal.notes.touch.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.ui.theme.OrangePrimary

/**
 * The layer panel of the reader.
 *
 * It is the floating card of the original TouchNotes: a handle on the top,
 * a row per layer (the eye that masks the layer, its name, a small preview
 * of it and the menu with its actions) and a bottom row with the button
 * that adds a layer and the one that closes the panel.
 *
 * The panel shows the layers of one page: the base layer is always there
 * and cannot be removed (it can only be hidden), and a page has three
 * layers at most.
 */
@Composable
fun LayerPanel(
    /** The layers of the page, from the base layer upwards. */
    stack: PageLayerStack,
    /** A small preview of every layer (null when the layer is empty). */
    thumbnails: Map<Int, android.graphics.Bitmap?>,
    /** The layer whose menu is open (-1 when no menu is open). */
    actionIndex: Int = -1,
    /** Called with the number of the layer the user tapped. */
    onSelect: (Int) -> Unit = {},
    /** Called with the number of the layer whose eye was tapped. */
    onToggleVisible: (Int) -> Unit = {},
    /** Called with the number of the layer whose menu was opened. */
    onMore: (Int) -> Unit = {},
    /** The "+" button: a new layer (three at most). */
    onAdd: () -> Unit = {},
    /** The "Merge" action: the layer goes into the base layer. */
    onMerge: (Int) -> Unit = {},
    /** The "Delete" action: the layer is removed. */
    onDelete: (Int) -> Unit = {},
    /** Closes the menu of the layer. */
    onCloseActions: () -> Unit = {},
    /** The "x" on the top: the panel closes. */
    onClose: () -> Unit = {},
    /** The panel is dragged by its handle. */
    onDrag: (Offset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E5E5)),
        modifier = modifier.width(216.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // The head of the card: the close button on the left and the
            // handle the card is dragged by.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close the layers",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(Color(0xFFCCCCCC), RoundedCornerShape(2.dp))
                    )
                }
                Spacer(modifier = Modifier.width(26.dp))
            }

            Spacer(modifier = Modifier.height(2.dp))

            // One row per layer.
            stack.layers.forEach { layer ->
                LayerRow(
                    layer = layer,
                    isCurrent = layer.index == stack.currentIndex,
                    thumbnail = thumbnails[layer.index],
                    onSelect = { onSelect(layer.index) },
                    onToggleVisible = { onToggleVisible(layer.index) },
                    onMore = { onMore(layer.index) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // The bottom row: the menu of a layer, or the buttons of the card.
            if (actionIndex >= 0 && stack.layerAt(actionIndex) != null) {
                LayerActions(
                    isBase = actionIndex == PageLayerStack.BASE_LAYER_INDEX,
                    onMerge = { onMerge(actionIndex) },
                    onDelete = { onDelete(actionIndex) },
                    onClose = onCloseActions
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAdd,
                        enabled = stack.canAdd,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add a layer",
                            tint = if (stack.canAdd) OrangePrimary else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = OrangePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One layer of the panel: the eye that masks the layer, its name, a small
 * preview of it and the button that opens its menu.
 */
@Composable
private fun LayerRow(
    layer: PageLayer,
    isCurrent: Boolean,
    thumbnail: android.graphics.Bitmap?,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The eye: the layer is masked when it is crossed out. The writing
        // of a masked layer is not drawn on the page; nothing is deleted.
        IconButton(onClick = onToggleVisible, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (layer.visible) "Hide the layer" else "Show the layer",
                tint = if (layer.visible) Color.DarkGray else Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = layer.name,
            fontSize = 14.sp,
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            color = if (layer.visible) Color(0xFF222222) else Color(0xFFBBBBBB),
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        )
        LayerThumbnail(
            thumbnail = thumbnail,
            isCurrent = isCurrent,
            modifier = Modifier.size(34.dp)
        )
        IconButton(onClick = onMore, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "The actions of ${layer.name}",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * The preview of a layer.
 *
 * An empty layer shows a dashed outline, the layer the writing goes on has
 * an orange one - the same marks the original uses.
 */
@Composable
private fun LayerThumbnail(
    thumbnail: android.graphics.Bitmap?,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Canvas(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFFFAFAFA))
    ) {
        val stroke = if (isCurrent) 2.5.dp.toPx() else 1.5.dp.toPx()
        val color = if (isCurrent) OrangePrimary else Color(0xFFBBBBBB)
        if (thumbnail != null && !thumbnail.isRecycled) {
            val scale = minOf(size.width / thumbnail.width, size.height / thumbnail.height)
            val w = thumbnail.width * scale
            val h = thumbnail.height * scale
            drawImage(
                image = thumbnail.asImageBitmap(),
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(thumbnail.width, thumbnail.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    ((size.width - w) / 2f).toInt(),
                    ((size.height - h) / 2f).toInt()
                ),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())
            )
            drawRoundRect(
                color = color,
                style = Stroke(width = stroke),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
        } else {
            // An empty layer: a dashed outline.
            val inset = stroke / 2f
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = inset,
                        top = inset,
                        right = size.width - inset,
                        bottom = size.height - inset,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                )
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
                )
            )
        }
    }
}

/**
 * The menu of a layer: it merges the layer into the base layer or it
 * removes it.
 *
 * The base layer is never removed - it can only be hidden - so it has no
 * delete.
 */
@Composable
private fun LayerActions(
    isBase: Boolean,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onMerge, enabled = !isBase) {
            Text(
                text = "Merge",
                fontSize = 13.sp,
                color = if (isBase) Color.LightGray else Color(0xFF333333)
            )
        }
        if (!isBase) {
            TextButton(onClick = onDelete) {
                Text(text = "Delete", fontSize = 13.sp, color = Color(0xFFE0523F))
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close the menu",
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
