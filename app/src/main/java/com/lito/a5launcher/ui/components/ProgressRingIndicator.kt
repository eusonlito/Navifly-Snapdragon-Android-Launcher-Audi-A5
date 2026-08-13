package com.lito.a5launcher.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.a5launcher.ui.theme.TitaniumSilver
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val RPM_RED_ZONE_START = 4_500f
private const val DIAL_START_ANGLE = 135f
private const val DIAL_SWEEP_ANGLE = 270f

private data class DialTickGeometry(
    val fraction: Float,
    val start: Offset,
    val end: Offset,
    val major: Boolean,
)

private data class DialLabelGeometry(
    val fraction: Float,
    val text: String,
    val center: Offset,
)

enum class DialType(val unit: String) {
    SPEED("km/h"),
    RPM("x1000"),
}

internal fun dialTargetFraction(value: Int, maxValue: Int): Float =
    if (maxValue > 0) value.coerceIn(0, maxValue).toFloat() / maxValue else 0f

internal fun formatDialValue(value: Int, dialType: DialType): String = when (dialType) {
    DialType.RPM -> String.format(Locale.US, "%.1f", value / 1000f)
    DialType.SPEED -> value.toString()
}

@Composable
internal fun ProgressRingIndicator(
    modifier: Modifier = Modifier,
    value: Int,
    maxValue: Int,
    dialType: DialType,
    label: String,
    centerGear: String? = null,
    startupProgress: State<Float?>? = null,
) {
    val isRpm = dialType == DialType.RPM
    val targetPercentage = dialTargetFraction(value, maxValue)
    val arcAnimationSpec = remember(isRpm) {
        if (isRpm) {
            spring<Float>(
                dampingRatio = 1f,
                stiffness = 400f,
                visibilityThreshold = .0002f,
            )
        } else {
            tween<Float>(160)
        }
    }
    val animatedPercentage = animateFloatAsState(
        targetValue = targetPercentage,
        animationSpec = arcAnimationSpec,
        label = "${dialType.name}Arc",
    )
    val activeColor = OemCockpitTokens.Cyan
    val gearAnimation = remember { Animatable(1f) }
    var previousGear by remember { mutableStateOf(centerGear) }

    LaunchedEffect(centerGear) {
        val shouldPulse = centerGear != null && previousGear != null && centerGear != previousGear
        previousGear = centerGear
        if (shouldPulse) {
            gearAnimation.snapTo(0f)
            gearAnimation.animateTo(1f, tween(220))
        } else {
            gearAnimation.snapTo(1f)
        }
    }

    Box(
        modifier = modifier.size(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f
                    val arcRadius = radius * .895f
                    val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    val arcStroke = 6.dp.toPx()
                    val labelIntervals = if (isRpm) 6 else 14
                    val tickCount = if (isRpm) 48 else 56
                    val ticksPerLabel = tickCount / labelIntervals
                    val outerTickRadius = radius * .952f
                    val labelRadius = radius * .755f
                    val pendingTypeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                    val reachedTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    fun labelPaint(typeface: Typeface, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 20.sp.toPx()
                        this.typeface = typeface
                        this.color = color
                    }
                    val pendingLabelPaint = labelPaint(
                        pendingTypeface,
                        Color.White.copy(alpha = .90f).toArgb(),
                    )
                    val reachedLabelPaint = labelPaint(reachedTypeface, activeColor.toArgb())
                    val pendingLabelBaseline =
                        -(pendingLabelPaint.ascent() + pendingLabelPaint.descent()) / 2f
                    val reachedLabelBaseline =
                        -(reachedLabelPaint.ascent() + reachedLabelPaint.descent()) / 2f
                    val hubChrome = oemChromeBrush(
                        start = Offset(center.x - radius * .5f, center.y - radius * .5f),
                        end = Offset(center.x + radius * .5f, center.y + radius * .5f),
                    )
                    val microtextureLines = List(28) { index ->
                        val angle = Math.toRadians((index * (360f / 28f)).toDouble())
                        Offset(
                            center.x + radius * .16f * cos(angle).toFloat(),
                            center.y + radius * .16f * sin(angle).toFloat(),
                        ) to Offset(
                            center.x + radius * .47f * cos(angle).toFloat(),
                            center.y + radius * .47f * sin(angle).toFloat(),
                        )
                    }
                    val ticks = List(tickCount + 1) { index ->
                        val fraction = index.toFloat() / tickCount
                        val radians = Math.toRadians(
                            (DIAL_START_ANGLE + DIAL_SWEEP_ANGLE * fraction).toDouble(),
                        )
                        val major = index % ticksPerLabel == 0
                        val halfStep = (ticksPerLabel / 2).coerceAtLeast(1)
                        val medium = index % halfStep == 0
                        val length = when {
                            major -> radius * .074f
                            medium -> radius * .046f
                            else -> radius * .026f
                        }
                        val inner = outerTickRadius - length
                        DialTickGeometry(
                            fraction = fraction,
                            start = Offset(
                                center.x + inner * cos(radians).toFloat(),
                                center.y + inner * sin(radians).toFloat(),
                            ),
                            end = Offset(
                                center.x + outerTickRadius * cos(radians).toFloat(),
                                center.y + outerTickRadius * sin(radians).toFloat(),
                            ),
                            major = major,
                        )
                    }
                    val labels = List(labelIntervals + 1) { index ->
                        val fraction = index.toFloat() / labelIntervals
                        val radians = Math.toRadians(
                            (DIAL_START_ANGLE + DIAL_SWEEP_ANGLE * fraction).toDouble(),
                        )
                        DialLabelGeometry(
                            fraction = fraction,
                            text = if (isRpm) index.toString() else (index * 20).toString(),
                            center = Offset(
                                center.x + labelRadius * cos(radians).toFloat(),
                                center.y + labelRadius * sin(radians).toFloat(),
                            ),
                        )
                    }

                    onDrawBehind {
                        val startup = oemStartupPresentation(startupProgress?.value)
                        // Solid, layered construction: depth without a decorative gradient.
                        drawCircle(Color.Black, radius, center)
                        drawCircle(
                            OemCockpitTokens.DialHub.copy(alpha = startup.depthProgress),
                            radius * .49f,
                            center,
                        )
                        drawCircle(
                            brush = hubChrome,
                            radius = radius * .50f,
                            center = center,
                            alpha = .50f * startup.depthProgress,
                            style = Stroke(.7.dp.toPx()),
                        )

                        // Microtexture lives only in the recessed hub.
                        microtextureLines.forEach { (start, end) ->
                            drawLine(
                                color = Color.White.copy(alpha = .018f * startup.depthProgress),
                                start = start,
                                end = end,
                                strokeWidth = .6.dp.toPx(),
                            )
                        }

                        drawArc(
                            color = OemCockpitTokens.DialChannel.copy(
                                alpha = startup.dialOutlineAlpha,
                            ),
                            startAngle = DIAL_START_ANGLE,
                            sweepAngle = DIAL_SWEEP_ANGLE,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(9.dp.toPx(), cap = StrokeCap.Butt),
                        )
                        val visiblePercentage = animatedPercentage.value * startup.depthProgress
                        if (visiblePercentage > 0f) {
                            val activeSweep = DIAL_SWEEP_ANGLE * visiblePercentage
                            drawArc(
                                color = activeColor.copy(alpha = .14f),
                                startAngle = DIAL_START_ANGLE,
                                sweepAngle = activeSweep,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(13.dp.toPx(), cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = activeColor,
                                startAngle = DIAL_START_ANGLE,
                                sweepAngle = activeSweep,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(arcStroke, cap = StrokeCap.Round),
                            )
                            val endpointAngle = Math.toRadians(
                                (DIAL_START_ANGLE + activeSweep).toDouble(),
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = .86f),
                                radius = 1.7.dp.toPx(),
                                center = Offset(
                                    center.x + arcRadius * cos(endpointAngle).toFloat(),
                                    center.y + arcRadius * sin(endpointAngle).toFloat(),
                                ),
                            )
                        }

                        if (isRpm && startup.depthProgress > 0f) {
                            val redStart = (RPM_RED_ZONE_START / maxValue).coerceIn(0f, 1f)
                            drawArc(
                                color = Color(0xFFE23036).copy(alpha = startup.depthProgress),
                                startAngle = DIAL_START_ANGLE + DIAL_SWEEP_ANGLE * redStart,
                                sweepAngle = DIAL_SWEEP_ANGLE * (1f - redStart),
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(arcStroke, cap = StrokeCap.Butt),
                            )
                        }

                        ticks.forEach { tick ->
                            val reachedByStartup = tick.fraction <= startup.scaleProgress
                            val reachedByValue = tick.fraction <= visiblePercentage
                            val tickColor = if (startup.isActive) {
                                val alpha = when {
                                    !reachedByStartup -> .04f * startup.dialOutlineAlpha
                                    tick.major -> .96f
                                    else -> .62f
                                }
                                Color.White.copy(
                                    alpha = alpha,
                                )
                            } else if (reachedByValue) {
                                activeColor.copy(alpha = if (tick.major) 1f else .76f)
                            } else {
                                Color.White.copy(alpha = if (tick.major) .90f else .32f)
                            }
                            drawLine(
                                tickColor,
                                tick.start,
                                tick.end,
                                if (tick.major) 1.8.dp.toPx() else .75.dp.toPx(),
                                StrokeCap.Butt,
                            )
                        }

                        drawIntoCanvas { canvas ->
                            labels.forEach { labelGeometry ->
                                val reachedByStartup = labelGeometry.fraction <= startup.scaleProgress
                                val reachedByValue = labelGeometry.fraction <= visiblePercentage
                                val paint = if (startup.isActive) {
                                    pendingLabelPaint.apply {
                                        alpha = if (reachedByStartup) 230 else 0
                                    }
                                } else if (reachedByValue) {
                                    reachedLabelPaint
                                } else {
                                    pendingLabelPaint.apply { alpha = 230 }
                                }
                                val baseline = if (!startup.isActive && reachedByValue) {
                                    reachedLabelBaseline
                                } else {
                                    pendingLabelBaseline
                                }
                                canvas.nativeCanvas.drawText(
                                    labelGeometry.text,
                                    labelGeometry.center.x,
                                    labelGeometry.center.y + baseline,
                                    paint,
                                )
                            }
                        }
                    }
                },
        ) { }

        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF68767E),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-58).dp)
                    .graphicsLayer {
                        alpha = oemStartupPresentation(startupProgress?.value).contentAlpha
                    },
            )
        }
        Text(
            text = formatDialValue(value, dialType),
            color = TitaniumSilver,
            fontSize = 76.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = if (isRpm) (-5).sp else (-2).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = oemStartupPresentation(startupProgress?.value).contentAlpha
                },
        )
        Text(
            text = dialType.unit.uppercase(),
            color = activeColor.copy(alpha = .82f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 44.dp)
                .graphicsLayer {
                    alpha = oemStartupPresentation(startupProgress?.value).contentAlpha
                },
        )

        if (centerGear != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 102.dp)
                    .size(68.dp)
                    .graphicsLayer {
                        alpha = oemStartupPresentation(startupProgress?.value).contentAlpha
                        val scale = oemGearPulseScale(gearAnimation.value)
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF0C1115)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val pulseProgress = gearAnimation.value
                    val pulse = sin(Math.PI.toFloat() * pulseProgress).coerceAtLeast(0f)
                    drawCircle(
                        OemCockpitTokens.Titanium.copy(alpha = .50f + pulse * .25f),
                        style = Stroke(1.dp.toPx()),
                    )
                    drawCircle(
                        activeColor.copy(alpha = .46f + pulse * .34f),
                        radius = size.minDimension * .43f,
                        style = Stroke((1.5f + pulse).dp.toPx()),
                    )
                }
                Text(
                    text = centerGear,
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}
