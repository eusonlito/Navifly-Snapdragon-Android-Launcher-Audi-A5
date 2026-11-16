package com.lito.a5launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex

internal enum class ReorderAxis { HORIZONTAL, VERTICAL }

/**
 * Reorders [item] among [visibleItems] only; hidden or pinned entries keep
 * their position in the shared persisted order.
 */
internal fun <T> moveItemToVisibleIndex(
    order: List<T>,
    item: T,
    targetVisibleIndex: Int,
    visibleItems: Set<T>,
): List<T> {
    if (item !in visibleItems) return order
    val visible = order.filter(visibleItems::contains)
    val from = visible.indexOf(item)
    if (from < 0) return order
    val to = targetVisibleIndex.coerceIn(0, visible.lastIndex)
    if (from == to) return order
    val reordered = visible.toMutableList().apply { add(to, removeAt(from)) }
    val iterator = reordered.iterator()
    return order.map { entry -> if (entry in visibleItems) iterator.next() else entry }
}

internal fun <T> moveVisibleItem(
    order: List<T>,
    item: T,
    direction: Int,
    visibleItems: Set<T>,
): List<T> {
    if (direction == 0) return order
    val from = order.filter(visibleItems::contains).indexOf(item)
    if (from < 0) return order
    return moveItemToVisibleIndex(order, item, from + direction.coerceIn(-1, 1), visibleItems)
}

/** Distance between the centres of two adjacent visible items along the drag axis. */
internal fun <T> reorderSwapDistancePx(
    visibleOrder: List<T>,
    item: T,
    direction: Int,
    sizes: Map<T, Int>,
    gapPx: Int = 0,
): Float? {
    val from = visibleOrder.indexOf(item)
    val targetIndex = from + direction.coerceIn(-1, 1)
    if (from < 0 || targetIndex !in visibleOrder.indices) return null
    val currentSize = sizes[item] ?: return null
    val targetSize = sizes[visibleOrder[targetIndex]] ?: return null
    return (currentSize + targetSize) / 2f + gapPx
}

internal data class CellBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/** Cell under the point, or the nearest cell centre when the point is in a gap. */
internal fun gridTargetIndex(cells: List<CellBounds?>, x: Float, y: Float): Int? {
    cells.forEachIndexed { index, cell -> if (cell?.contains(x, y) == true) return index }
    return cells.withIndex()
        .filter { it.value != null }
        .minByOrNull { (_, cell) ->
            val dx = cell!!.centerX - x
            val dy = cell.centerY - y
            dx * dx + dy * dy
        }?.index
}

/**
 * Long-press drag for command buttons: moves one position each time the finger
 * travels past the threshold, as the original top bar did.
 */
@Composable
internal fun CommandReorderSlot(
    item: Any,
    axis: ReorderAxis,
    dragThreshold: Dp,
    plateShape: Shape,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    var dragging by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    Box(
        Modifier
            .graphicsLayer {
                if (axis == ReorderAxis.HORIZONTAL) translationX = dragDistance else translationY = dragDistance
                scaleX = if (dragging) 1.08f else 1f
                scaleY = if (dragging) 1.08f else 1f
            }
            .zIndex(if (dragging) 4f else 0f)
            .pointerInput(item, dragThreshold, axis) {
                val threshold = dragThreshold.toPx()
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragDistance = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += if (axis == ReorderAxis.HORIZONTAL) amount.x else amount.y
                        when {
                            dragDistance > threshold -> {
                                currentOnMove(1)
                                dragDistance -= threshold * 1.8f
                            }
                            dragDistance < -threshold -> {
                                currentOnMove(-1)
                                dragDistance += threshold * 1.8f
                            }
                        }
                    },
                )
            }
            .then(
                if (dragging) {
                    Modifier
                        .clip(plateShape)
                        .background(tokens.dragPlate)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Long-press drag for data blocks laid out in a row or a column. A swap happens
 * once the finger passes the neighbour's centre, keeping the block under it.
 */
@Composable
internal fun <T> LinearReorderSlot(
    item: T,
    order: List<T>,
    visibleItems: Set<T>,
    axis: ReorderAxis,
    sizes: MutableMap<T, Int>,
    gapPx: Int,
    plateShape: Shape?,
    onMove: (List<T>) -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalHomeTokens.current
    var dragging by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOrder by rememberUpdatedState(order)
    val currentSizes by rememberUpdatedState(sizes)
    val currentVisible by rememberUpdatedState(visibleItems)
    var dragOrder by remember { mutableStateOf(order) }
    Box(
        modifier
            .onSizeChanged {
                sizes[item] = if (axis == ReorderAxis.HORIZONTAL) it.width else it.height
            }
            .graphicsLayer {
                if (axis == ReorderAxis.HORIZONTAL) translationX = dragDistance else translationY = dragDistance
                scaleX = if (dragging) 1.035f else 1f
                scaleY = if (dragging) 1.035f else 1f
            }
            .zIndex(if (dragging) 4f else 0f)
            .pointerInput(item, axis) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragDistance = 0f
                        dragOrder = currentOrder
                    },
                    onDragCancel = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragDistance = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += if (axis == ReorderAxis.HORIZONTAL) amount.x else amount.y
                        // Keep the active drag order local so an immediate
                        // second pointer event sees the block's new neighbour.
                        val visibleOrder = dragOrder.filter(currentVisible::contains)
                        val forward = reorderSwapDistancePx(visibleOrder, item, 1, currentSizes, gapPx)
                        val backward = reorderSwapDistancePx(visibleOrder, item, -1, currentSizes, gapPx)
                        when {
                            forward != null && dragDistance > forward -> {
                                dragOrder = moveVisibleItem(dragOrder, item, 1, currentVisible)
                                currentOnMove(dragOrder)
                                dragDistance -= forward
                            }
                            backward != null && dragDistance < -backward -> {
                                dragOrder = moveVisibleItem(dragOrder, item, -1, currentVisible)
                                currentOnMove(dragOrder)
                                dragDistance += backward
                            }
                        }
                    },
                )
            }
            .then(
                when {
                    !dragging -> Modifier
                    plateShape == null -> Modifier.background(tokens.dragPlate)
                    else -> Modifier.clip(plateShape).background(tokens.dragPlate)
                },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Long-press drag for equal tiles in a grid; the tile follows the finger in both axes. */
@Composable
internal fun <T> GridReorderSlot(
    item: T,
    index: Int,
    order: List<T>,
    visibleItems: Set<T>,
    slots: MutableMap<Int, CellBounds>,
    plateShape: Shape,
    onMove: (List<T>) -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalHomeTokens.current
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOrder by rememberUpdatedState(order)
    val currentVisible by rememberUpdatedState(visibleItems)
    var dragOrder by remember { mutableStateOf(order) }
    val currentIndex by rememberUpdatedState(index)
    Box(
        modifier
            .onPlaced { coordinates ->
                val position = coordinates.positionInRoot()
                // Equal tiles: each grid slot keeps its bounds whichever item fills it.
                slots[currentIndex] = CellBounds(
                    position.x,
                    position.y,
                    position.x + coordinates.size.width,
                    position.y + coordinates.size.height,
                )
            }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = if (dragging) 1.04f else 1f
                scaleY = if (dragging) 1.04f else 1f
            }
            .zIndex(if (dragging) 4f else 0f)
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragOffset = Offset.Zero
                        dragOrder = currentOrder
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = Offset.Zero
                    },
                    onDragEnd = {
                        dragging = false
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                        val visibleOrder = dragOrder.filter(currentVisible::contains)
                        val from = visibleOrder.indexOf(item)
                        val cells = visibleOrder.indices.map { slots[it] }
                        val origin = cells.getOrNull(from) ?: return@detectDragGesturesAfterLongPress
                        val target = gridTargetIndex(
                            cells,
                            origin.centerX + dragOffset.x,
                            origin.centerY + dragOffset.y,
                        )
                        if (target != null && target != from) {
                            val destination = cells[target] ?: return@detectDragGesturesAfterLongPress
                            dragOrder = moveItemToVisibleIndex(dragOrder, item, target, currentVisible)
                            currentOnMove(dragOrder)
                            dragOffset -= Offset(
                                destination.left - origin.left,
                                destination.top - origin.top,
                            )
                        }
                    },
                )
            }
            .then(
                if (dragging) Modifier.clip(plateShape).background(tokens.dragPlate) else Modifier,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
