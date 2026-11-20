package com.lito.a5launcher.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lito.a5launcher.ui.components.dialTargetFraction
import com.lito.a5launcher.ui.components.oemStartupPresentation
import kotlin.math.floor
import kotlin.math.roundToInt

/** Value scale of a linear gauge: labels every [labelStep], [minorPerLabel] ticks between labels. */
internal data class GaugeScale(
    val maxValue: Int,
    val labelStep: Int,
    val minorPerLabel: Int,
    val labelDivisor: Int = 1,
)

internal val SpeedGaugeScale = GaugeScale(maxValue = 240, labelStep = 40, minorPerLabel = 4)
internal val RpmGaugeScale = GaugeScale(maxValue = 6000, labelStep = 1000, minorPerLabel = 2, labelDivisor = 1000)

/** Fraction of the RPM scale where the red zone starts (4500 rpm, as the classic dial). */
internal const val RPM_RED_ZONE_FRACTION = 4.5f / 6f

internal data class GaugeLabel(val fraction: Float, val text: String)

internal fun gaugeLabels(scale: GaugeScale): List<GaugeLabel> =
    (0..scale.maxValue step scale.labelStep).map { value ->
        GaugeLabel(value.toFloat() / scale.maxValue, (value / scale.labelDivisor).toString())
    }

internal fun gaugeTickFractions(scale: GaugeScale): List<Pair<Float, Boolean>> {
    val intervals = scale.maxValue / scale.labelStep * scale.minorPerLabel
    return (0..intervals).map { index ->
        index.toFloat() / intervals to (index % scale.minorPerLabel == 0)
    }
}

/** Lit segments for a fraction; any visible value lights at least one segment. */
internal fun gaugeSegmentsLit(fraction: Float, segments: Int): Int {
    if (segments <= 0) return 0
    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped <= 0f) return 0
    return (clamped * segments).roundToInt().coerceIn(1, segments)
}

internal fun redZoneStartIndex(redZoneFrom: Float, segments: Int): Int =
    floor(redZoneFrom.coerceIn(0f, 1f) * segments + 1e-4f).toInt().coerceIn(0, segments)

/** Same motion as the circular dials: critically damped RPM, short tween for speed. */
@Composable
internal fun animatedGaugeFraction(value: State<Int>, maxValue: Int, rpm: Boolean): State<Float> {
    val spec: AnimationSpec<Float> = remember(rpm) {
        if (rpm) spring(dampingRatio = 1f, stiffness = 400f, visibilityThreshold = .0002f) else tween(160)
    }
    return animateFloatAsState(
        targetValue = dialTargetFraction(value.value, maxValue),
        animationSpec = spec,
        label = if (rpm) "rpmBar" else "speedBar",
    )
}

private fun labelPaint(color: Color, size: Float, typeface: Typeface, align: Paint.Align) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = align
        textSize = size
        this.typeface = typeface
        this.color = color.toArgb()
    }

private val GaugeLabelTypeface: Typeface by lazy { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }

/**
 * Horizontal bar with ticks and labels underneath. Continuous when
 * [segments] is 0; otherwise segmented, optionally coloured per segment.
 */
@Composable
internal fun HorizontalBarGauge(
    fraction: State<Float>,
    scale: GaugeScale,
    startupProgress: State<Float?>,
    barHeight: Dp,
    labelSize: TextUnit,
    modifier: Modifier = Modifier,
    segments: Int = 0,
    redZoneFrom: Float? = null,
    segmentColor: ((index: Int, segments: Int) -> Color)? = null,
    showMinorTicks: Boolean = true,
    marker: Boolean = false,
    brightTip: Boolean = false,
    slantedSegments: Boolean = false,
) {
    val tokens = LocalHomeTokens.current
    Canvas(
        modifier.drawWithCache {
            val bar = barHeight.toPx()
            val fillColors = if (tokens.gaugeFill.size > 1) tokens.gaugeFill else tokens.gaugeFill + tokens.gaugeFill
            val tipWidth = 2.5.dp.toPx()
            val tipColor = lerp(tokens.gaugeFill.last(), Color.White, .75f)
            val radius = CornerRadius(bar / 2f, bar / 2f)
            val labelPx = labelSize.toPx()
            val paint = labelPaint(tokens.label, labelPx, GaugeLabelTypeface, Paint.Align.CENTER)
            val baselineOffset = -(paint.ascent())
            val tickTop = bar + 4.dp.toPx()
            val majorLength = 6.dp.toPx()
            val minorLength = 3.dp.toPx()
            val ticks = gaugeTickFractions(scale)
            val labels = gaugeLabels(scale)
            val gap = 2.dp.toPx()
            val slantedPaths = if (slantedSegments && segments > 0) {
                val skew = minOf(bar * .32f, size.width * .02f)
                val step = (size.width - skew + gap) / segments
                List(segments) { index ->
                    val x = index * step
                    Path().apply {
                        moveTo(x + skew, 0f)
                        lineTo(x + skew + step - gap, 0f)
                        lineTo(x + step - gap, bar)
                        lineTo(x, bar)
                        close()
                    }
                }
            } else emptyList()
            val fillBrush = Brush.horizontalGradient(
                colors = if (tokens.gaugeFill.size > 1) tokens.gaugeFill else tokens.gaugeFill + tokens.gaugeFill,
                startX = 0f,
                endX = size.width,
            )
            onDrawBehind {
                val startup = oemStartupPresentation(startupProgress.value)
                val shown = fraction.value.coerceIn(0f, 1f) * startup.depthProgress
                if (segments <= 0) {
                    drawRoundRect(tokens.gaugeTrack, Offset.Zero, Size(size.width, bar), radius, alpha = startup.dialOutlineAlpha)
                    val filled = size.width * shown
                    if (shown > 0f) {
                        clipRect(right = filled) {
                            // With a bright tip the fill always ends in its lightest colour.
                            val brush = if (brightTip) {
                                Brush.horizontalGradient(fillColors, startX = 0f, endX = filled.coerceAtLeast(1f))
                            } else {
                                fillBrush
                            }
                            drawRoundRect(brush, Offset.Zero, Size(size.width, bar), radius)
                        }
                    }
                    redZoneFrom?.let { from ->
                        drawRoundRect(
                            tokens.redZone,
                            Offset(size.width * from, 0f),
                            Size(size.width * (1f - from), bar),
                            radius,
                            alpha = if (shown >= from) 1f else .78f * startup.depthProgress,
                        )
                    }
                    if (brightTip && filled > bar) {
                        drawRect(tipColor, Offset((filled - tipWidth).coerceAtMost(size.width - bar / 2f), 0f), Size(tipWidth, bar))
                    }
                } else {
                    val lit = gaugeSegmentsLit(shown, segments)
                    val redStart = redZoneFrom?.let { redZoneStartIndex(it, segments) } ?: segments
                    val segmentWidth = (size.width - gap * (segments - 1)) / segments
                    for (index in 0 until segments) {
                        val active = index < lit
                        val base = segmentColor?.invoke(index, segments)
                            ?: if (index >= redStart) tokens.redZone else tokens.accent
                        val color = when {
                            active -> base
                            index >= redStart && segmentColor == null -> base.copy(alpha = .40f * startup.dialOutlineAlpha)
                            segmentColor != null -> base.copy(alpha = .32f * startup.dialOutlineAlpha)
                            else -> tokens.gaugeTrack.copy(alpha = tokens.gaugeTrack.alpha * startup.dialOutlineAlpha)
                        }
                        if (slantedPaths.isNotEmpty()) {
                            drawPath(slantedPaths[index], color)
                        } else {
                            drawRoundRect(
                                color,
                                Offset(index * (segmentWidth + gap), 0f),
                                Size(segmentWidth, bar),
                                CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                }
                if (marker && shown > 0f) {
                    val markerWidth = 3.dp.toPx()
                    val x = (size.width * shown - markerWidth / 2f).coerceIn(0f, size.width - markerWidth)
                    drawRect(
                        Color.White,
                        Offset(x, -4.dp.toPx()),
                        Size(markerWidth, bar + 8.dp.toPx()),
                    )
                }
                val tickAlpha = startup.scaleProgress
                ticks.forEach { (position, major) ->
                    if (!major && !showMinorTicks) return@forEach
                    val x = (position * size.width).coerceIn(.5f, size.width - .5f)
                    drawLine(
                        tokens.label.copy(alpha = tokens.label.alpha * tickAlpha),
                        Offset(x, tickTop),
                        Offset(x, tickTop + if (major) majorLength else minorLength),
                        strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
                paint.alpha = (255 * tickAlpha).toInt()
                labels.forEach { label ->
                    val width = paint.measureText(label.text)
                    val x = (label.fraction * size.width).coerceIn(width / 2f, size.width - width / 2f)
                    drawContext.canvas.nativeCanvas.drawText(
                        label.text,
                        x,
                        tickTop + majorLength + 3.dp.toPx() + baselineOffset,
                        paint,
                    )
                }
            }
        },
    ) { }
}

/** Facets instrument: a tall straight band filled from the bottom with ticks on one side. */
@Composable
internal fun VerticalBandGauge(
    fraction: State<Float>,
    scale: GaugeScale,
    startupProgress: State<Float?>,
    bandWidth: Dp,
    labelSize: TextUnit,
    labelsOnEnd: Boolean,
    modifier: Modifier = Modifier,
    redZoneFrom: Float? = null,
) {
    val tokens = LocalHomeTokens.current
    Canvas(
        modifier.drawWithCache {
            val band = bandWidth.toPx()
            val bandLeft = if (labelsOnEnd) 0f else size.width - band
            val corner = CornerRadius(6.dp.toPx())
            val labelPx = labelSize.toPx()
            val paint = labelPaint(
                tokens.content.copy(alpha = .86f),
                labelPx,
                GaugeLabelTypeface,
                if (labelsOnEnd) Paint.Align.LEFT else Paint.Align.RIGHT,
            )
            val textMiddle = -(paint.ascent() + paint.descent()) / 2f
            val majorLength = 10.dp.toPx()
            val minorLength = 5.dp.toPx()
            val tickGap = 4.dp.toPx()
            val tickStart = if (labelsOnEnd) bandLeft + band + tickGap else bandLeft - tickGap
            val direction = if (labelsOnEnd) 1f else -1f
            val ticks = gaugeTickFractions(scale)
            val labels = gaugeLabels(scale)
            val inset = 3.dp.toPx()
            val innerHeight = size.height - inset * 2f
            // Warm end at the current value, deep end at the base of the band.
            val fillColors = tokens.gaugeFill.reversed()
            val facetSpacing = 9.dp.toPx()
            val outline = Stroke(1.5.dp.toPx())
            val glow = Stroke(5.dp.toPx())
            fun yFor(position: Float) = size.height - inset - innerHeight * position
            onDrawBehind {
                val startup = oemStartupPresentation(startupProgress.value)
                val shown = fraction.value.coerceIn(0f, 1f) * startup.depthProgress
                drawRoundRect(tokens.gaugeTrack, Offset(bandLeft, 0f), Size(band, size.height), corner)
                redZoneFrom?.let { from ->
                    drawRect(
                        tokens.redZone.copy(alpha = .22f * startup.depthProgress),
                        Offset(bandLeft + inset, inset),
                        Size(band - inset * 2f, innerHeight * (1f - from)),
                    )
                }
                if (shown > 0f) {
                    val top = yFor(shown)
                    clipRect(bandLeft + inset, top, bandLeft + band - inset, size.height - inset) {
                        drawRect(
                            Brush.verticalGradient(fillColors, startY = top, endY = size.height - inset),
                            Offset(bandLeft + inset, top),
                            Size(band - inset * 2f, size.height - inset - top),
                        )
                        redZoneFrom?.let { from ->
                            if (shown > from) {
                                drawRect(
                                    tokens.redZone,
                                    Offset(bandLeft + inset, top),
                                    Size(band - inset * 2f, yFor(from) - top),
                                )
                            }
                        }
                        var y = size.height - inset
                        while (y > top) {
                            drawLine(
                                tokens.background.copy(alpha = .16f),
                                Offset(bandLeft + inset, y),
                                Offset(bandLeft + band - inset, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                            y -= facetSpacing
                        }
                    }
                    drawLine(
                        Color.White.copy(alpha = .92f),
                        Offset(bandLeft + inset, top),
                        Offset(bandLeft + band - inset, top),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                drawRoundRect(
                    tokens.gaugeOutline.copy(alpha = .16f * startup.dialOutlineAlpha),
                    Offset(bandLeft, 0f),
                    Size(band, size.height),
                    corner,
                    style = glow,
                )
                drawRoundRect(
                    tokens.gaugeOutline.copy(alpha = .85f * startup.dialOutlineAlpha),
                    Offset(bandLeft, 0f),
                    Size(band, size.height),
                    corner,
                    style = outline,
                )
                val tickAlpha = startup.scaleProgress
                ticks.forEach { (position, major) ->
                    val y = yFor(position)
                    val color = if (redZoneFrom != null && position > redZoneFrom + 1e-4f) tokens.redZone else tokens.gaugeOutline
                    drawLine(
                        color.copy(alpha = .85f * tickAlpha),
                        Offset(tickStart, y),
                        Offset(tickStart + direction * if (major) majorLength else minorLength, y),
                        strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
                paint.alpha = (255 * tickAlpha).toInt()
                labels.forEach { label ->
                    val y = yFor(label.fraction).coerceIn(labelPx / 2f, size.height - labelPx / 2f)
                    drawContext.canvas.nativeCanvas.drawText(
                        label.text,
                        tickStart + direction * (majorLength + 5.dp.toPx()),
                        y + textMiddle,
                        paint,
                    )
                }
            }
        },
    ) { }
}

/** Shift-light colours: green to 2000 rpm, amber to 4500 rpm, red from there. */
internal fun shiftLightColor(index: Int, segments: Int): Color {
    val position = (index + .5f) / segments
    return when {
        position >= RPM_RED_ZONE_FRACTION -> Color(0xFFEF4444)
        position >= 1f / 3f -> Color(0xFFFACC15)
        else -> Color(0xFF22C55E)
    }
}

internal fun DrawScope.drawPanelBorder(color: Color, corner: Dp) {
    drawRoundRect(
        color,
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(1.dp.toPx()),
    )
}
