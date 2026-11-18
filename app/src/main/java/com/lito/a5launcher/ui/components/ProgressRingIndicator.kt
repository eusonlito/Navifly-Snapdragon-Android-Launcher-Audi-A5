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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
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

internal const val SPEED_DIAL_MAX_KMH = 240
private const val SPEED_LABEL_STEP = 20

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
    // The red zone lights up while the engine is in it.
    val redZoneGlow = animateFloatAsState(
        targetValue = if (isRpm && value >= RPM_RED_ZONE_START) 1f else 0f,
        animationSpec = tween(260),
        label = "redZoneGlow",
    )
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
                    // RPM labels every 1000; speed labels every 20 km/h with a tick every 5.
                    val labelIntervals = if (isRpm) 6 else (maxValue / SPEED_LABEL_STEP).coerceAtLeast(1)
                    val tickCount = if (isRpm) 48 else labelIntervals * 4
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
                    val redLabelPaint = labelPaint(reachedTypeface, OemCockpitTokens.RedZone.toArgb())
                    val pendingLabelBaseline =
                        -(pendingLabelPaint.ascent() + pendingLabelPaint.descent()) / 2f
                    val reachedLabelBaseline =
                        -(reachedLabelPaint.ascent() + reachedLabelPaint.descent()) / 2f
                    val hubFill = Brush.radialGradient(
                        colors = listOf(OemCockpitTokens.DialHubLight, OemCockpitTokens.DialHub, OemCockpitTokens.DialHubEdge),
                        center = Offset(center.x, center.y - radius * .18f),
                        radius = radius * .62f,
                    )
                    // Soft reflection along the top of the hub rim.
                    val rimReflection = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            .58f to Color.Transparent,
                            .75f to Color.White.copy(alpha = .16f),
                            .92f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        center = center,
                    )
                    val redStart = (RPM_RED_ZONE_START / maxValue).coerceIn(0f, 1f)
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
                            text = if (isRpm) index.toString() else (index * SPEED_LABEL_STEP).toString(),
                            center = Offset(
                                center.x + labelRadius * cos(radians).toFloat(),
                                center.y + labelRadius * sin(radians).toFloat(),
                            ),
                        )
                    }

                    onDrawBehind {
                        val startup = oemStartupPresentation(startupProgress?.value)
                        // Layered construction: black bezel, a softly lit hub and a reflection on its rim.
                        drawCircle(Color.Black, radius, center)
                        drawCircle(hubFill, radius * .49f, center, alpha = startup.depthProgress)
                        drawCircle(
                            rimReflection,
                            radius * .485f,
                            center,
                            alpha = startup.depthProgress,
                            style = Stroke(1.5.dp.toPx()),
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
                            drawActiveArc(
                                center = center,
                                arcRadius = arcRadius,
                                fraction = visiblePercentage,
                                stroke = arcStroke,
                                glow = 13.dp.toPx(),
                                color = activeColor,
                                tipColor = lerp(activeColor, OemCockpitTokens.RedZone, redZoneGlow.value),
                            )
                        }
                        if (isRpm && startup.depthProgress > 0f) {
                            val redSweepStart = DIAL_START_ANGLE + DIAL_SWEEP_ANGLE * redStart
                            val redSweep = DIAL_SWEEP_ANGLE * (1f - redStart)
                            if (redZoneGlow.value > 0f) {
                                drawArc(
                                    color = OemCockpitTokens.RedZone.copy(alpha = .22f * redZoneGlow.value),
                                    startAngle = redSweepStart,
                                    sweepAngle = redSweep,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(15.dp.toPx(), cap = StrokeCap.Butt),
                                )
                            }
                            drawArc(
                                color = OemCockpitTokens.RedZone.copy(alpha = (.86f + .14f * redZoneGlow.value) * startup.depthProgress),
                                startAngle = redSweepStart,
                                sweepAngle = redSweep,
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
                            } else if (isRpm && tick.fraction >= redStart) {
                                // The red zone stays red, brighter where the value reaches it.
                                OemCockpitTokens.RedZone.copy(
                                    alpha = when {
                                        reachedByValue -> 1f
                                        tick.major -> .80f
                                        else -> .40f
                                    },
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
                                } else if (isRpm && labelGeometry.fraction > redStart) {
                                    redLabelPaint
                                } else if (reachedByValue) {
                                    reachedLabelPaint
                                } else {
                                    pendingLabelPaint.apply { alpha = 230 }
                                }
                                val bold = reachedByValue || (isRpm && labelGeometry.fraction > redStart)
                                val baseline = if (!startup.isActive && bold) {
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

/**
 * Lit part of the dial: deeper at the start and brightest at the current value,
 * with a halo that grows towards the tip and a small bloom on it.
 */
private fun DrawScope.drawActiveArc(
    center: Offset,
    arcRadius: Float,
    fraction: Float,
    stroke: Float,
    glow: Float,
    color: Color,
    tipColor: Color = color,
) {
    val sweep = DIAL_SWEEP_ANGLE * fraction
    val tipStop = sweep / 360f
    val deep = lerp(OemCockpitTokens.CyanDeep, color, .25f)
    val arc = Brush.sweepGradient(
        colorStops = arrayOf(0f to deep, tipStop to color, 1f to color),
        center = center,
    )
    val halo = Brush.sweepGradient(
        colorStops = arrayOf(0f to color.copy(alpha = 0f), tipStop to color.copy(alpha = .26f), 1f to color.copy(alpha = .26f)),
        center = center,
    )
    val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
    fun pointAt(angle: Float): Offset {
        val radians = Math.toRadians(angle.toDouble())
        return Offset(center.x + arcRadius * cos(radians).toFloat(), center.y + arcRadius * sin(radians).toFloat())
    }
    // The gradients start at 0°, so the arc is drawn from there and rotated into place.
    rotate(DIAL_START_ANGLE, center) {
        drawArc(halo, 0f, sweep, false, topLeft, arcSize, style = Stroke(glow, cap = StrokeCap.Butt))
        drawArc(arc, 0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
    // Round ends drawn by hand so each keeps its own colour.
    drawCircle(deep, stroke / 2f, pointAt(DIAL_START_ANGLE))
    val tip = pointAt(DIAL_START_ANGLE + sweep)
    drawCircle(color, stroke / 2f, tip)
    drawCircle(
        Brush.radialGradient(listOf(tipColor.copy(alpha = .45f), tipColor.copy(alpha = 0f)), center = tip, radius = glow * 1.3f),
        glow * 1.3f,
        tip,
    )
    drawCircle(Color.White.copy(alpha = .9f), stroke * .3f, tip)
}
