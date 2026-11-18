package com.lito.a5launcher.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.lito.a5launcher.ui.components.oemStartupPresentation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular scale of an illustrated dial. Radii are fractions of half the
 * canvas width; angles follow Compose (0° = east, clockwise).
 */
@Immutable
internal data class ArcGaugeSpec(
    val scale: GaugeScale,
    val startAngle: Float,
    val sweep: Float,
    val radius: Float,
    val width: Float,
    val labelRadius: Float,
    val labelSize: Float,
    /** Ticks drawn on the outer side of the arc instead of the inner one. */
    val ticksOutside: Boolean = false,
    val redZoneFrom: Float? = null,
    /** Labels already passed by the value take the arc colour. */
    val litLabels: Boolean = false,
    val roundCaps: Boolean = true,
    /** Centre of the arc as a fraction of the canvas height. */
    val centerY: Float = .5f,
)

private val GaugeTypeface: Typeface by lazy { Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }

private fun angleOffset(center: Offset, radius: Float, degrees: Float): Offset {
    val a = degrees * PI.toFloat() / 180f
    return Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
}

/**
 * Arc, ticks, labels and the optional pointer of an illustrated dial. The
 * value is read while drawing, so a new speed or RPM only redraws this canvas.
 */
@Composable
internal fun IllustratedArcGauge(
    fraction: State<Float>,
    startupProgress: State<Float?>,
    spec: ArcGaugeSpec,
    palette: IllustratedPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.drawWithCache {
            val half = size.width / 2f
            val center = Offset(size.width / 2f, size.height * spec.centerY)
            val radius = half * spec.radius
            val stroke = half * spec.width
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val cap = if (spec.roundCaps) StrokeCap.Round else StrokeCap.Butt
            val labelPx = half * spec.labelSize
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = labelPx
                typeface = GaugeTypeface
            }
            val textMiddle = -(paint.ascent() + paint.descent()) / 2f
            val ticks = gaugeTickFractions(spec.scale)
            val labels = gaugeLabels(spec.scale)
            val tickBase = if (spec.ticksOutside) radius + stroke * .9f else radius - stroke * .9f
            val direction = if (spec.ticksOutside) 1f else -1f
            val majorLength = half * .07f
            val minorLength = half * .035f
            val needleLength = half * .13f
            onDrawBehind {
                val startup = oemStartupPresentation(startupProgress.value)
                val shown = fraction.value.coerceIn(0f, 1f) * startup.depthProgress
                val outline = startup.dialOutlineAlpha
                drawArc(
                    palette.arcTrack, spec.startAngle, spec.sweep, false, arcTopLeft, arcSize,
                    alpha = outline, style = Stroke(stroke, cap = cap),
                )
                spec.redZoneFrom?.let { from ->
                    drawArc(
                        palette.redZone, spec.startAngle + spec.sweep * from, spec.sweep * (1f - from), false,
                        arcTopLeft, arcSize, alpha = .45f * outline, style = Stroke(stroke, cap = cap),
                    )
                }
                if (shown > 0f) {
                    val red = spec.redZoneFrom
                    val normal = if (red != null) minOf(shown, red) else shown
                    drawArc(
                        palette.arc, spec.startAngle, spec.sweep * normal, false, arcTopLeft, arcSize,
                        style = Stroke(stroke, cap = cap),
                    )
                    if (red != null && shown > red) {
                        drawArc(
                            palette.redZone, spec.startAngle + spec.sweep * red, spec.sweep * (shown - red), false,
                            arcTopLeft, arcSize, style = Stroke(stroke, cap = cap),
                        )
                    }
                }
                val scaleAlpha = startup.scaleProgress
                ticks.forEach { (position, major) ->
                    val angle = spec.startAngle + spec.sweep * position
                    val red = spec.redZoneFrom != null && position > spec.redZoneFrom + 1e-4f
                    val color = if (red) palette.redZone else palette.label
                    val length = if (major) majorLength else minorLength
                    drawLine(
                        color.copy(alpha = (if (major) .95f else .6f) * scaleAlpha),
                        angleOffset(center, tickBase, angle),
                        angleOffset(center, tickBase + direction * length, angle),
                        strokeWidth = if (major) half * .018f else half * .011f,
                        cap = StrokeCap.Round,
                    )
                }
                labels.forEach { label ->
                    val angle = spec.startAngle + spec.sweep * label.fraction
                    val p = angleOffset(center, half * spec.labelRadius, angle)
                    val lit = spec.litLabels && shown + 1e-4f >= label.fraction && shown > 0f
                    val red = spec.redZoneFrom != null && label.fraction > spec.redZoneFrom + 1e-4f
                    paint.color = when {
                        red -> palette.redZone
                        lit -> palette.arc
                        else -> palette.label
                    }.toArgb()
                    paint.alpha = (255 * scaleAlpha).toInt()
                    drawContext.canvas.nativeCanvas.drawText(label.text, p.x, p.y + textMiddle, paint)
                }
                palette.needle?.let { needle ->
                    if (startup.dialOutlineAlpha > 0f) {
                        val angle = spec.startAngle + spec.sweep * shown
                        val tip = angleOffset(center, radius + stroke * .2f, angle)
                        val back = angleOffset(center, radius - needleLength, angle)
                        val side = needleLength * .09f
                        val normal = angleOffset(Offset.Zero, side, angle + 90f)
                        val path = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(back.x + normal.x, back.y + normal.y)
                            lineTo(back.x - normal.x, back.y - normal.y)
                            close()
                        }
                        drawPath(path, Color.Black.copy(alpha = .35f * outline), style = Stroke(side * .9f))
                        drawPath(path, needle.copy(alpha = outline))
                        drawCircle(needle.copy(alpha = outline), side * 1.15f, back)
                    }
                }
            }
        },
    ) { }
}
