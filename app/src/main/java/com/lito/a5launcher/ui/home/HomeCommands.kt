package com.lito.a5launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lito.a5launcher.R
import com.lito.a5launcher.assistant.AssistantRobotButton
import com.lito.a5launcher.ui.components.CommandSurface
import com.lito.a5launcher.ui.components.TopCommandItem

/** Visual metrics of one command group; layouts choose sizes and padding. */
internal data class CommandMetrics(
    val buttonSize: Dp,
    val iconSize: Dp,
    val horizontalPadding: Dp = 16.dp,
)

@Composable
internal fun HomeCommandButton(
    item: TopCommandItem,
    state: HomeState,
    actions: HomeActions,
    metrics: CommandMetrics,
) {
    val tokens = LocalHomeTokens.current
    val buttonSize = metrics.buttonSize
    val iconSize = metrics.iconSize
    val padding = metrics.horizontalPadding
    when (item) {
        TopCommandItem.ASSISTANT -> AssistantRobotButton(
            buttonSize = buttonSize,
            iconSize = iconSize,
            active = state.assistantActive.value,
            onClick = actions.onAssistant,
            plateColor = tokens.dragPlate,
            horizontalPadding = padding,
        )
        TopCommandItem.NAVIGATION -> NavigationCommandButton(
            buttonSize, iconSize, padding, state.navigationEnabled.value, actions.onNavigation,
        )
        TopCommandItem.APPS -> AppsCommandButton(buttonSize, iconSize, padding, actions.onApps)
        TopCommandItem.LAUNCHER_SETTINGS -> LauncherSettingsCommandButton(
            buttonSize, iconSize, padding, actions.onLauncherSettings,
        )
        TopCommandItem.DEVICE_SETTINGS -> CommandButton(
            Icons.Default.Settings,
            stringResource(R.string.device_settings_description),
            buttonSize,
            iconSize,
            padding,
            .88f,
            actions.onDeviceSettings,
        )
        TopCommandItem.RECENTS -> RecentAppsCommandButton(buttonSize, iconSize, padding, actions.onRecents)
        TopCommandItem.MMI -> CarCommandButton(buttonSize, iconSize * 1.05f, padding, actions.onMmi)
    }
}

/**
 * Reorderable command group shared by every theme. Horizontal groups keep the
 * original thin dividers; vertical rails stack the same buttons.
 */
@Composable
internal fun HomeCommandGroup(
    state: HomeState,
    actions: HomeActions,
    metrics: CommandMetrics,
    axis: ReorderAxis,
    modifier: Modifier = Modifier,
    dividers: Boolean = true,
    spacing: Dp = 0.dp,
) {
    val tokens = LocalHomeTokens.current
    val order = state.topCommandOrder.value
    val assistantEnabled = state.assistantEnabled.value
    val visibleCommands = remember(order, assistantEnabled) {
        order.filter { assistantEnabled || it != TopCommandItem.ASSISTANT }
    }
    val visibleSet = remember(visibleCommands) { visibleCommands.toSet() }
    val threshold = metrics.buttonSize * .55f
    val plateShape = RoundedCornerShape(8.dp)
    val items: @Composable () -> Unit = {
        visibleCommands.forEachIndexed { index, item ->
            key(item) {
                CommandReorderSlot(
                    item = item,
                    axis = axis,
                    dragThreshold = threshold,
                    plateShape = plateShape,
                    onMove = { direction ->
                        actions.onTopCommandOrderChanged(
                            moveVisibleItem(state.topCommandOrder.value, item, direction, visibleSet),
                        )
                    },
                ) {
                    HomeCommandButton(item, state, actions, metrics)
                }
            }
            if (dividers && index != visibleCommands.lastIndex) {
                if (axis == ReorderAxis.HORIZONTAL) {
                    Box(Modifier.width(1.dp).height(metrics.iconSize * .68f)) {
                        Box(Modifier.fillMaxSize().background(tokens.commandDivider))
                    }
                } else {
                    Box(Modifier.height(1.dp).width(metrics.iconSize * .68f)) {
                        Box(Modifier.fillMaxSize().background(tokens.commandDivider))
                    }
                }
            }
        }
    }
    if (axis == ReorderAxis.HORIZONTAL) {
        Row(
            modifier = modifier.wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.CenterHorizontally) else Arrangement.Center,
        ) { items() }
    } else {
        Column(
            modifier = modifier.wrapContentHeight(unbounded = true),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.CenterVertically) else Arrangement.Center,
        ) { items() }
    }
}

/** Icons with holes are drawn offscreen so the holes show whatever surface lies behind. */
private val CutoutLayer = Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)

@Composable
private fun LauncherSettingsCommandButton(
    buttonSize: Dp,
    iconSize: Dp,
    padding: Dp,
    onClick: () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    CommandSurface(buttonSize, onClick, plateColor = tokens.dragPlate, horizontalPadding = padding) {
        Canvas(CutoutLayer.size(iconSize * .72f)) {
            val strokeWidth = 1.2.dp.toPx()
            val knobRadius = 2.8.dp.toPx()
            val lineInset = strokeWidth / 2f
            val positions = listOf(
                .30f to .15f,
                .68f to .50f,
                .42f to .85f,
            )
            positions.forEach { (knobX, lineY) ->
                drawLine(
                    color = tokens.content,
                    start = Offset(lineInset, size.height * lineY),
                    end = Offset(size.width - lineInset, size.height * lineY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Color.Black,
                    radius = knobRadius + strokeWidth,
                    center = Offset(size.width * knobX, size.height * lineY),
                    blendMode = BlendMode.Clear,
                )
                drawCircle(
                    color = tokens.content,
                    radius = knobRadius,
                    center = Offset(size.width * knobX, size.height * lineY),
                )
            }
        }
    }
}

@Composable
private fun CommandButton(
    icon: ImageVector,
    label: String,
    buttonSize: Dp,
    iconSize: Dp,
    padding: Dp,
    opticalScale: Float,
    onClick: () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    CommandSurface(buttonSize, onClick, plateColor = tokens.dragPlate, horizontalPadding = padding) {
        Icon(
            icon,
            label,
            tint = tokens.content,
            modifier = Modifier.size(iconSize * opticalScale),
        )
    }
}

@Composable
private fun NavigationCommandButton(
    buttonSize: Dp,
    iconSize: Dp,
    padding: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    CommandSurface(
        buttonSize,
        onClick,
        enabled = enabled,
        plateColor = tokens.dragPlate,
        horizontalPadding = padding,
    ) {
        Canvas(
            CutoutLayer.size(
                width = iconSize * .56f,
                height = iconSize * .84f,
            ),
        ) {
            val width = size.width
            val height = size.height
            val capHeight = width
            val center = Offset(width / 2f, capHeight / 2f)
            val pin = Path().apply {
                moveTo(width / 2f, 0f)
                cubicTo(width * .79f, 0f, width, capHeight * .25f, width, capHeight * .52f)
                cubicTo(width, height * .66f, width * .67f, height * .85f, width / 2f, height)
                cubicTo(width * .33f, height * .85f, 0f, height * .66f, 0f, capHeight * .52f)
                cubicTo(0f, capHeight * .25f, width * .21f, 0f, width / 2f, 0f)
                close()
            }
            drawPath(pin, tokens.content)
            drawCircle(Color.Black, radius = width * .16f, center = center, blendMode = BlendMode.Clear)
        }
    }
}

@Composable
private fun CarCommandButton(
    buttonSize: Dp,
    iconSize: Dp,
    padding: Dp,
    onClick: () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    CommandSurface(buttonSize, onClick, plateColor = tokens.dragPlate, horizontalPadding = padding) {
        Canvas(
            Modifier.size(
                width = iconSize * 1.28f,
                height = iconSize * .48f,
            ),
        ) {
            val strokeWidth = size.height * .09f
            val ringDiameter = size.width * .26f
            val ringRadius = ringDiameter / 2f
            val ringSpacing = size.width * .21f
            val leftInset = size.width * .055f
            val centerY = size.height / 2f
            repeat(4) { index ->
                drawCircle(
                    color = tokens.content,
                    radius = ringRadius,
                    center = Offset(
                        x = leftInset + ringRadius + index * ringSpacing,
                        y = centerY,
                    ),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

@Composable
private fun AppsCommandButton(
    buttonSize: Dp,
    iconSize: Dp,
    padding: Dp,
    onClick: () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    CommandSurface(buttonSize, onClick, plateColor = tokens.dragPlate, horizontalPadding = padding) {
        Canvas(Modifier.size(iconSize * .72f)) {
            val stroke = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            val inset = stroke.width / 2f
            val available = size.minDimension - stroke.width
            val gap = available * .18f
            val cell = (available - gap) / 2f
            listOf(
                Offset(inset, inset),
                Offset(inset + cell + gap, inset),
                Offset(inset, inset + cell + gap),
                Offset(inset + cell + gap, inset + cell + gap),
            ).forEach { origin ->
                drawRoundRect(
                    tokens.content,
                    topLeft = origin,
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * .12f),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
private fun RecentAppsCommandButton(
    buttonSize: Dp,
    iconSize: Dp,
    padding: Dp,
    onClick: () -> Unit,
) {
    val tokens = LocalHomeTokens.current
    CommandSurface(buttonSize, onClick, plateColor = tokens.dragPlate, horizontalPadding = padding) {
        Canvas(Modifier.size(iconSize * .74f)) {
            val stroke = Stroke(width = 1.8.dp.toPx())
            val corner = 2.dp.toPx()
            val inset = stroke.width / 2f
            drawRoundRect(
                color = tokens.content.copy(alpha = tokens.content.alpha * .55f),
                topLeft = Offset(size.width * .22f, inset),
                size = Size(size.width * .78f - inset, size.height * .72f),
                cornerRadius = CornerRadius(corner),
                style = stroke,
            )
            drawRoundRect(
                color = tokens.content,
                topLeft = Offset(inset, size.height * .28f),
                size = Size(size.width * .78f, size.height * .72f - inset),
                cornerRadius = CornerRadius(corner),
                style = stroke,
            )
        }
    }
}
