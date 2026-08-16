package com.lito.a5launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object OemCockpitTokens {
    val Titanium = Color(0xFFB9C3C8)
    val TitaniumDim = Color(0xFF6E7B82)
    val GraphitePressed = Color(0xFF1C292F)
    val Cyan = Color(0xFF00DDF4)
    val DialHub = Color(0xFF11171B)
    val DialChannel = Color(0xFF29343B)
}

internal const val OEM_STARTUP_DURATION_MS = 2_200
internal const val OEM_LEFT_DIAL_CENTER_FRACTION = .1587f
internal const val OEM_RIGHT_DIAL_CENTER_FRACTION = .8413f

internal fun oemGearPulseScale(progress: Float): Float =
    1f + sin(PI.toFloat() * progress.coerceIn(0f, 1f)) * .10f

internal data class OemStartupPresentation(
    val isActive: Boolean,
    val dialOutlineAlpha: Float,
    val scaleProgress: Float,
    val depthProgress: Float,
    val contentAlpha: Float,
)

private fun phaseProgress(currentMs: Float, startMs: Int, endMs: Int): Float {
    return ((currentMs - startMs) / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
}

internal fun oemStartupPresentation(progress: Float?): OemStartupPresentation =
    if (progress == null) {
        OemStartupPresentation(false, 1f, 1f, 1f, 1f)
    } else {
        val currentMs = progress.coerceIn(0f, 1f) * OEM_STARTUP_DURATION_MS
        OemStartupPresentation(
            isActive = true,
            dialOutlineAlpha = phaseProgress(currentMs, 0, 250),
            scaleProgress = phaseProgress(currentMs, 250, 1_250),
            depthProgress = phaseProgress(currentMs, 1_050, 1_750),
            contentAlpha = phaseProgress(currentMs, 1_700, OEM_STARTUP_DURATION_MS),
        )
    }

internal fun oemChromeBrush(start: Offset, end: Offset): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to OemCockpitTokens.TitaniumDim.copy(alpha = .70f),
        .20f to Color.White.copy(alpha = .94f),
        .48f to OemCockpitTokens.Titanium.copy(alpha = .78f),
        .76f to Color.White.copy(alpha = .46f),
        1f to OemCockpitTokens.Titanium.copy(alpha = .72f),
    ),
    start = start,
    end = end,
)

private val processOemStartupGate = AtomicBoolean(false)

@Composable
internal fun rememberOemStartupProgress(): State<Float?> {
    val progress = remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        if (processOemStartupGate.compareAndSet(false, true)) {
            val animation = Animatable(0f)
            var completed = false
            try {
                progress.value = 0f
                animation.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(OEM_STARTUP_DURATION_MS, easing = LinearEasing),
                ) {
                    progress.value = value
                }
                completed = true
            } finally {
                progress.value = null
                if (!completed) processOemStartupGate.set(false)
            }
        }
    }
    return progress
}

/**
 * Keeps MapLibre untouched and visually seats both instruments above it. The
 * black exterior follows the real dial circumference without altering the map.
 */
@Composable
internal fun CockpitMapIntegrationOverlay(
    dialDiameter: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.drawWithCache {
            val radius = dialDiameter.toPx() / 2f
            val centerY = size.height / 2f
            val leftCenter = Offset(size.width * OEM_LEFT_DIAL_CENTER_FRACTION, centerY)
            val rightCenter = Offset(size.width * OEM_RIGHT_DIAL_CENTER_FRACTION, centerY)

            fun arcPoints(center: Offset, left: Boolean): List<Offset> =
                List(49) { step ->
                    val angle = (if (left) {
                        -90f - 180f * step / 48f
                    } else {
                        -90f + 180f * step / 48f
                    }) * PI.toFloat() / 180f
                    Offset(
                        center.x + radius * cos(angle),
                        center.y + radius * sin(angle),
                    )
                }

            fun boundaryPath(points: List<Offset>): Path = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }

            fun exteriorPath(
                center: Offset,
                left: Boolean,
                points: List<Offset>,
            ): Path = Path().apply {
                if (left) {
                    moveTo(0f, 0f)
                    lineTo(center.x, 0f)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(0f, size.height)
                } else {
                    moveTo(size.width, 0f)
                    lineTo(center.x, 0f)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(size.width, size.height)
                }
                close()
            }

            val leftPoints = arcPoints(leftCenter, left = true)
            val rightPoints = arcPoints(rightCenter, left = false)
            val leftExterior = exteriorPath(leftCenter, left = true, points = leftPoints)
            val rightExterior = exteriorPath(rightCenter, left = false, points = rightPoints)
            val leftBoundary = boundaryPath(leftPoints)
            val rightBoundary = boundaryPath(rightPoints)
            onDrawBehind {
                drawPath(leftExterior, Color.Black)
                drawPath(rightExterior, Color.Black)
                drawPath(leftBoundary, Color.Black, style = Stroke(6.dp.toPx()))
                drawPath(rightBoundary, Color.Black, style = Stroke(6.dp.toPx()))
            }
        },
    ) { }
}

@Composable
internal fun CommandSurface(
    buttonSize: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val plateAlpha = animateFloatAsState(
        targetValue = if (pressed.value) 1f else 0f,
        animationSpec = tween(120),
        label = "commandPlate",
    )
    Box(
        Modifier
            .height(buttonSize)
            .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = .35f })
            // The command group is centred in one third of the header. Its
            // touch padding must never squeeze the final icon when the group
            // is a few dp wider than that nominal third.
            .wrapContentWidth(unbounded = true)
            .clip(RoundedCornerShape(8.dp))
            .drawBehind {
                drawRect(OemCockpitTokens.GraphitePressed.copy(alpha = plateAlpha.value))
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
